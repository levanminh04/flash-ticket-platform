# Logic Payment — GOV-156/GOV-157

Code ngày 30/10/2026 trên nhánh `codex/gov-156-payment-flow`, gồm thay đổi chưa commit trong `services/payment-service`. Chỉ giải thích phía Payment; đối chiếu phong cách với snapshot 19 file Shop App đi kèm skill, không suy rộng thành khảo sát toàn bộ Shop App.

Flow: FE POST Payment → lấy PurchaseSnapshot → lưu snapshot/attempt, trả URL → webhook ghi tiền/Saga → Booking chấp nhận → yêu cầu Ticket → nhận kết quả vé, báo Booking → nhận OrderCompleted → Saga COMPLETED.

## 1. Lấy snapshot trước, mở transaction sau

[PaymentController.createPayment](<C:/Users/WEOHWOEH/Documents/DATN 2026/Docs/flash-ticket-platform/services/payment-service/src/main/java/com/flashticket/payment_service/controller/PaymentController.java:39>) nhận `orderId`, `Idempotency-Key` và subject JWT. [BookingPurchaseClient](<C:/Users/WEOHWOEH/Documents/DATN 2026/Docs/flash-ticket-platform/services/payment-service/src/main/java/com/flashticket/payment_service/client/BookingPurchaseClient.java:29>) gửi một POST nội bộ với actor đó và credential service Payment; không nhận tiền từ FE hoặc truy DB Booking.

[PaymentService.createPayment](<C:/Users/WEOHWOEH/Documents/DATN 2026/Docs/flash-ticket-platform/services/payment-service/src/main/java/com/flashticket/payment_service/service/PaymentService.java:68>) có `Propagation.NEVER`: nếu caller đang có transaction thì từ chối trước khi gọi HTTP. Sau response snapshot, đoạn ghi chạy riêng:

```java
try {
  return paymentTransactionTemplate.execute(
      status -> savePaymentAttempt(incomingPurchase, buyerSubject, requestKey, clientIp));
} catch (DataIntegrityViolationException ex) {
  // Unique race: TX trước đã rollback; đọc bản thắng bằng TX hoàn toàn mới.
  return paymentTransactionTemplate.execute(
      status -> savePaymentAttempt(incomingPurchase, buyerSubject, requestKey, clientIp));
}
```

[Template được cấu hình REQUIRES_NEW](<C:/Users/WEOHWOEH/Documents/DATN 2026/Docs/flash-ticket-platform/services/payment-service/src/main/java/com/flashticket/payment_service/config/PaymentRuntimeConfig.java:35>); callback trả DTO chưa có nghĩa đã commit, `execute` hoàn tất commit rồi controller mới trả response. Có đúng một lần thử lại khi gặp xung đột integrity; không tiếp tục dùng transaction PostgreSQL đã lỗi.

**Khác Shop App:** giữ đường Controller → Service → Repository như [OrderService mẫu](<C:/Users/WEOHWOEH/.codex/skills/shopapp-java-brainstorming/references/shopapp-source/src/main/java/com/project/shopapp/services/impl/OrderService.java>), nhưng thêm ranh giới HTTP/TX để không giữ khóa khi chờ Booking. Cấu hình credential dùng Spring OAuth2 client; dependency qua constructor, property dùng `@Value` như [JwtTokenUtil mẫu](<C:/Users/WEOHWOEH/.codex/skills/shopapp-java-brainstorming/references/shopapp-source/src/main/java/com/project/shopapp/components/JwtTokenUtil.java>).

## 2. Snapshot bất biến và retry theo key

[savePaymentAttempt](<C:/Users/WEOHWOEH/Documents/DATN 2026/Docs/flash-ticket-platform/services/payment-service/src/main/java/com/flashticket/payment_service/service/PaymentService.java:93>) kiểm tổng tiền qua validator/hash, owner, deadline và VND; khóa snapshot theo order. Chưa có thì lưu full JSON cùng attempt trong transaction; đã có thì hash phải khớp. `total` trở thành `expectedAmount`; Payment không áp lại voucher hay đọc giá hiện tại.

```java
if (storedAttempt != null) {
  if (!requestHash.equals(storedAttempt.getRequestHash()))
    throw new PaymentException(PaymentErrorCode.IDEMPOTENCY_CONFLICT);
  if (storedAttempt.getState() == PaymentAttemptState.UNKNOWN)
    throw new PaymentException(PaymentErrorCode.PAYMENT_IN_PROGRESS);
  return starts.toDto(storedAttempt, incomingPurchase.expiresAt());
}
```

Key được scope theo `(orderId, requestKey)`. Cùng key PENDING trả cùng attempt/URL đã lưu; cùng key FAILED trả kết quả cũ, không mở attempt khác. Key mới chỉ được tạo sau FAILED khi Booking vẫn xác nhận payable; có unfinished attempt hoặc latest SUCCEEDED thì từ chối. Booking lỗi/hủy/hết hạn không cho tạo mới; snapshot hết hạn cũng bị chặn tại Payment. Tiền tới sau hạn vẫn xử lý ở webhook, không bị bỏ.

Response dùng `expiresAt` nguyên bản trong snapshot, tránh sai lệch do timestamp DB làm tròn. **Khác Shop App:** giữ DTO record/MapStruct và canonical hash đã có của Payment; không chuyển toàn bộ DTO sang class hoặc thêm ModelMapper chỉ để giống mẫu. Unique constraint là hàng rào cạnh tranh, kiểm tra tồn tại trước `save` chưa đủ.

## 3. Webhook lưu tiền và lệnh tiếp theo trong cùng transaction

[handleIPN](<C:/Users/WEOHWOEH/Documents/DATN 2026/Docs/flash-ticket-platform/services/payment-service/src/main/java/com/flashticket/payment_service/service/PaymentService.java:146>) kiểm chữ ký, attempt, merchant, amount, kết quả và định danh giao dịch. Nó khóa snapshot rồi attempt trước khi lưu charge; [recordSuccessfulCharge](<C:/Users/WEOHWOEH/Documents/DATN 2026/Docs/flash-ticket-platform/services/payment-service/src/main/java/com/flashticket/payment_service/service/impl/PaymentSagaServiceImpl.java:39>) tham gia cùng transaction bằng `MANDATORY`:

```java
var receivedCharge =
    charges.save(Charge.received(payment, transactionNo, chargedAt, Instant.now()));
paymentSagaService.recordSuccessfulCharge(receivedCharge);
```

Transaction này còn ghi attempt SUCCEEDED, Saga WAITING_BOOKING và outbox `booking.accept-payment.v1`. Chỉ sau commit thành công mới trả ACK thành công cho cổng; không gọi Booking ngay trong webhook. Provider identity đã lưu được dedupe; charge khác cùng order không thay candidate, mà tạo nghĩa vụ hoàn tiền thu thừa. Callback non-success đến trễ không hạ SUCCEEDED hoặc mở lại FAILED thành UNKNOWN.

**Khác Shop App:** đây là transaction tài chính kèm outbox/Saga, không chỉ `repository.save` trong luồng đơn hàng mẫu đã đọc. Return page chỉ xác minh/redirect theo trạng thái đã lưu, không ghi nhận thu tiền.

## 4. Điều kiện Payment tiến Saga

[PaymentSagaServiceImpl](<C:/Users/WEOHWOEH/Documents/DATN 2026/Docs/flash-ticket-platform/services/payment-service/src/main/java/com/flashticket/payment_service/service/impl/PaymentSagaServiceImpl.java:74>) khóa event marker nếu có → snapshot → Saga, kiểm charge và ghi inbox cùng transaction với hiệu ứng nghiệp vụ.

| Phản hồi nhận tại Payment | Điều kiện chính | Kết quả commit |
| --- | --- | --- |
| PaymentDecision ACCEPTED | Saga WAITING_BOOKING, charge khớp, chưa có cancellation marker | confirmation + WAITING_TICKETS + outbox IssueTickets |
| IssuanceResult COMPLETED | Charge/confirmation khớp, đủ tổng quantity, issuance không mâu thuẫn | Inbox giữ kết quả + outbox báo Booking; vẫn WAITING_TICKETS |
| OrderCompleted | Khớp confirmation và kết quả vé đã lưu: charge, issuanceId, ticketCount | Saga COMPLETED + inbox |

[recordOrderCompleted](<C:/Users/WEOHWOEH/Documents/DATN 2026/Docs/flash-ticket-platform/services/payment-service/src/main/java/com/flashticket/payment_service/service/impl/PaymentSagaServiceImpl.java:199>) thiếu checkpoint thì chưa đóng Saga; sai dữ liệu thì lỗi, còn trạng thái COMPENSATING/COMPENSATED không hồi sinh:

```java
if (saga.getState() != PaymentSagaState.WAITING_TICKETS)
  throw new ContractValidationException("state", "SAGA_STATE_MISMATCH");
saga.setState(PaymentSagaState.COMPLETED);
```

**Khác Shop App:** mẫu OrderService đã đọc xử lý đơn cục bộ; ở đây Payment phải chờ các kết quả liên service đã lưu bền. Payment dựa vào hợp đồng OrderCompleted phát sau commit Booking, không kiểm trực tiếp DB Booking; kết quả Ticket riêng không đủ kết thúc Saga.

## 5. Inbox/outbox, ACK và phục hồi

[recordInbox](<C:/Users/WEOHWOEH/Documents/DATN 2026/Docs/flash-ticket-platform/services/payment-service/src/main/java/com/flashticket/payment_service/service/impl/MessagingServiceImpl.java:30>) so hash trước dedupe: cùng consumer/messageId và cùng hash bỏ tác động lặp; khác hash thì lỗi. [Listener](<C:/Users/WEOHWOEH/Documents/DATN 2026/Docs/flash-ticket-platform/services/payment-service/src/main/java/com/flashticket/payment_service/messaging/PaymentMessageListener.java:27>) parse/validate → gọi service → service commit → `basicAck`; lỗi hợp đồng chuyển thành MessageConversionException để error handler xử lý.

[Worker gửi message](<C:/Users/WEOHWOEH/Documents/DATN 2026/Docs/flash-ticket-platform/services/payment-service/src/main/java/com/flashticket/payment_service/worker/PaymentWorker.java:49>) claim bằng transaction ngắn, commit lease rồi mới publish và chờ broker ngoài transaction:

```java
var confirm = correlation.getFuture().get(confirmTimeout.toMillis(), TimeUnit.MILLISECONDS);
if (!confirm.isAck() || correlation.getReturned() != null)
  throw new IllegalStateException("Message not routed");
messaging.markPublished(
    storedMessage.getMessageId(), storedMessage.getAttempts(), Instant.now());
```

[markPublished](<C:/Users/WEOHWOEH/Documents/DATN 2026/Docs/flash-ticket-platform/services/payment-service/src/main/java/com/flashticket/payment_service/service/impl/MessagingServiceImpl.java:68>) kiểm token `claimedAttempt`, nên worker của lease cũ không ghi đè lease mới. Timeout/nack/no-route được hẹn lại; broker confirm chỉ cập nhật outbox, không tiến Saga.

[recoverSaga](<C:/Users/WEOHWOEH/Documents/DATN 2026/Docs/flash-ticket-platform/services/payment-service/src/main/java/com/flashticket/payment_service/service/impl/PaymentSagaServiceImpl.java:259>) xét `nextAttemptAt`: chờ Booking thì gửi lại AcceptPayment; chờ vé thì IssueTickets; đã có checkpoint success thì gửi lại thông báo Booking. Outbox chưa publish vẫn để relay xử lý; recovery sau publish tạo envelope mới giữ business identity/causation. **Khác Shop App:** `@RabbitListener`, `@Scheduled`, lease và inbox/outbox là cơ chế bổ sung cho mất message/restart, không có trong các luồng Order mẫu đã đối chiếu.

## 6. Hết hạn, hủy và nghĩa vụ hoàn tiền

| Trường hợp có trong nhánh Payment | Xử lý |
| --- | --- |
| Booking trả LATE/CANCELLED, chưa có event marker hủy | LATE_PAYMENT, Saga COMPENSATING |
| Charge thừa hoặc Booking trả DUPLICATE | DUPLICATE_PAYMENT; không thay candidate |
| Có event cancellation marker khi xử lý quyết định/kết quả/recovery | EVENT_CANCELLED, chặn phát hành/completion mới |
| Ticket báo terminal FAILED | TICKET_ISSUANCE_FAILED; timeout riêng không được coi là FAILED |

[ensureRefund](<C:/Users/WEOHWOEH/Documents/DATN 2026/Docs/flash-ticket-platform/services/payment-service/src/main/java/com/flashticket/payment_service/service/impl/RefundServiceImpl.java:27>) giữ một nghĩa vụ/charge và reason đã lưu. **Khác Shop App:** ghi Refund PENDING chưa đồng nghĩa cổng đã hoàn tiền; refund execution/query và phần nhận cancellation còn scaffold ngoài năm subtask này. Deadline dùng để chặn attempt mới, không tự chạy worker giải phóng ghế của Booking.

## 7. Bằng chứng và giới hạn

Kết quả đã chạy trong lượt triển khai: **123 test mặc định + 9 integration test PostgreSQL/RabbitMQ đạt**, `spotless:check` và `verify` có package/SpotBugs đạt; xem [plan và kết quả](<C:/Users/WEOHWOEH/Documents/DATN 2026/Docs/flash-ticket-platform/docs/java-workflow/plans/2026-10-30-gov-156-payment-plan.md:194>). Lượt viết MD chỉ kiểm source/link/đoạn trích, không chạy lại suite.

Đối tác Booking/Ticket và credential/token endpoint được mô phỏng trong test; chưa chứng minh E2E ba service hoặc giao dịch VNPay thật. Kết nối thật cần Booking URL và credential service trong [application.yaml](<C:/Users/WEOHWOEH/Documents/DATN 2026/Docs/flash-ticket-platform/services/payment-service/src/main/resources/application.yaml>); QR thanh toán độc lập chưa được thêm, adapter hiện tạo URL VNPay.
