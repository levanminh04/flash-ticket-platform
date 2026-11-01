# Logic AcceptPayment và gửi PaymentDecision

Đối chiếu code ngày 29/10/2026 trên branch `codex/booking-accept-payment` tại `D:/DATN/flash-ticket-platform`.

Flow: Payment → RabbitMQ `booking.accept-payment.q` → Booking chốt hoặc nhả tài nguyên + lưu inbox/outbox → worker gửi `booking.payment-decision.v1` → Payment.

## 1. Listener nhận message, service xử lý trong transaction

[AcceptPaymentListener.java:18](D:/DATN/flash-ticket-platform/services/booking-service/src/main/java/com/flashticket/booking_service/messaging/AcceptPaymentListener.java:18)

```java
@RabbitListener(queues = "booking.accept-payment.q", ackMode = "AUTO", concurrency = "1")
public void handleAcceptPayment(Envelope<AcceptPayment> message) {
  try {
    // Service qua proxy phải commit xong; listener trả về mới được AUTO ACK.
    orderService.acceptPayment(message);
```

`OrderServiceImpl.acceptPayment()` có `@Transactional`. Listener gọi service qua Spring proxy; DB commit thành công rồi listener mới trả về để container ACK. `ackMode="AUTO"` tại listener ghi đè cấu hình mặc định `manual` trong YAML.

**Khác luồng `OrderService.createOrder` mẫu Shop App:** đầu vào là message và có ACK của RabbitMQ; service xử lý đồng bộ trước khi ACK.

## 2. Khóa dữ liệu rồi kiểm tra message đã xử lý chưa

[OrderServiceImpl.java:79](D:/DATN/flash-ticket-platform/services/booking-service/src/main/java/com/flashticket/booking_service/order/service/impl/OrderServiceImpl.java:79)

Service kiểm Bean Validation, message type, `aggregateId == orderId`, `aggregateVersion == sourceOrderVersion`, rồi hash **toàn bộ envelope**. Lấy khóa READ snapshot event và WRITE order trước khi đọc inbox, vì message khác có thể vừa commit trong lúc chờ khóa.

```java
var receipt = inboxMessageRepository.findById(receiptId);
if (receipt.isPresent()) {
  if (!payloadHash.equals(receipt.get().getPayloadHash()))
    throw new BusinessException(ErrorCode.MESSAGE_ID_CONFLICT);
```

Inbox có key `(consumerName="booking.accept-payment", messageId)`: cùng ID/cùng hash trả decision đã lưu, không cập nhật tài nguyên hay tạo thêm outbox. Cùng ID/khác hash bị từ chối. Service còn kiểm decision lưu trong inbox có khớp order/charge không.

**Khác Shop App mẫu đã đối chiếu:** inbox + hash chống xử lý lại message; khóa pessimistic bảo vệ các transaction cạnh tranh. Khóa DB có thời hạn chờ cấu hình `5s`.

## 3. Kiểm tiền đã freeze, phân xử charge và thời hạn

[validatePaymentSnapshot:154](D:/DATN/flash-ticket-platform/services/booking-service/src/main/java/com/flashticket/booking_service/order/service/impl/OrderServiceImpl.java:154) kiểm snapshot đã freeze, thông tin order/buyer/event, hạn giữ, version, currency và amount. Sai dữ liệu trả `PAYMENT_SNAPSHOT_MISMATCH`, không coi là thanh toán trễ.

[acceptPayment:120](D:/DATN/flash-ticket-platform/services/booking-service/src/main/java/com/flashticket/booking_service/order/service/impl/OrderServiceImpl.java:120)

```java
sameCharge ? lockedOrder.getAcceptedAt() : orderRepository.currentDatabaseTime(),
sameCharge ? PaymentDecision.Outcome.ACCEPTED : PaymentDecision.Outcome.DUPLICATE,
lockedOrder.getAcceptedChargeId());
```

Nếu order đã nhận charge, cùng charge vẫn trả `ACCEPTED` với thời điểm nhận cũ; charge khác trả `DUPLICATE` và ID charge được nhận trước. Nhánh này chạy trước kiểm hạn giữ để replay không bị đổi thành `LATE`.

Nếu chưa nhận charge, [settlePendingPayment:237](D:/DATN/flash-ticket-platform/services/booking-service/src/main/java/com/flashticket/booking_service/order/service/impl/OrderServiceImpl.java:237) khóa purchase limit → inventory được chọn → promotion, kiểm reservation/allocation khớp snapshot rồi mới đọc giờ DB:

```java
Instant decisionAt = orderRepository.currentDatabaseTime();
boolean cancelled =
    storedSnapshot.getEventStatus() == EventState.CANCELLED
        || lockedOrder.getLifecycleState() == OrderState.CANCELLED;
boolean late =
    lockedOrder.getLifecycleState() == OrderState.EXPIRED
        || !decisionAt.isBefore(lockedOrder.getExpiresAt());
boolean accepted = !cancelled && !late;
```

| Điều kiện | Decision | Tác động chính |
| --- | --- | --- |
| Chưa hủy, còn hạn, reservation HELD | ACCEPTED | Chốt tài nguyên; lưu charge; order → ISSUING |
| Event hoặc order đã hủy | CANCELLED | Nhả phần còn HELD; order PENDING_PAYMENT → CANCELLED |
| Order EXPIRED hoặc giờ DB ≥ hạn giữ | LATE | Nhả phần còn HELD; order PENDING_PAYMENT → EXPIRED |

Nếu vừa hủy vừa hết hạn, kết quả là `CANCELLED`. Reservation đã RELEASED không bị trừ counter lần nữa. Dữ liệu giữ chỗ không nhất quán bị lỗi `RESERVATION_DATA_CONFLICT` và rollback.

**Khác Shop App mẫu:** dùng giờ DB sau khi chờ khóa để quyết định còn hạn; thời điểm user click không quyết định kết quả.

## 4. Chốt hoặc nhả đúng tài nguyên của order

[Các helper inventory:344](D:/DATN/flash-ticket-platform/services/booking-service/src/main/java/com/flashticket/booking_service/order/service/impl/OrderServiceImpl.java:344)

```java
lockedInventory.setHeld(lockedInventory.getHeld() - quantity);
if (targetState == ReservationState.COMMITTED)
  lockedInventory.setPurchased(Math.addExact(lockedInventory.getPurchased(), quantity));
```

| Dữ liệu | ACCEPTED | LATE/CANCELLED khi còn HELD |
| --- | --- | --- |
| QUANTITY | Pool ticket type: held giảm, purchased tăng | held giảm |
| STANDING | Pool sector: held giảm, purchased tăng | held giảm |
| SEATED | Ghế → PURCHASED; ghế hidden bị từ chối | Ghế → AVAILABLE, hoặc UNAVAILABLE nếu hidden; bỏ liên kết giữ |
| Purchase limit của buyer | held giảm, purchased tăng | held giảm |
| Promotion nếu có | held giảm, used tăng; usage → USED | held giảm; usage → RELEASED |
| Reservation + allocations | COMMITTED | RELEASED |

Các thay đổi này nằm cùng transaction với order, inbox và outbox. `ISSUING` là bước chờ phát vé; chuyển `COMPLETED` thuộc task tiếp theo.

## 5. Lưu kết quả trước, gửi RabbitMQ sau

[persistPaymentDecision:479](D:/DATN/flash-ticket-platform/services/booking-service/src/main/java/com/flashticket/booking_service/order/service/impl/OrderServiceImpl.java:479)

```java
inboxMessageRepository.save(receipt);
UUID responseId =
    UUID.nameUUIDFromBytes(
        ("booking.payment-decision:" + message.messageId()).getBytes(StandardCharsets.UTF_8));
```

Inbox lưu hash và decision để replay; outbox lưu envelope `booking.payment-decision.v1`. ID response được tạo cố định từ message ID đầu vào, giữ correlation ID và đặt causation ID bằng message ID đầu vào. Receipt mới giữ `rowVersion=null` để INSERT; PK chặn hai receipt trùng. Lỗi save/commit phải đi ra ngoài và rollback toàn bộ transaction.

**Khác Shop App mẫu:** outbox ghi cùng DB transaction giúp không mất việc cần gửi sau khi order đã commit; transaction này chưa gọi broker.

## 6. Worker gửi từng row outbox trong transaction riêng

[PaymentDecisionOutboxWorker:29](D:/DATN/flash-ticket-platform/services/booking-service/src/main/java/com/flashticket/booking_service/messaging/PaymentDecisionOutboxWorker.java:29) chạy mặc định mỗi `2s`, lấy tối đa `20` row chưa gửi và đến hạn thử lại, rồi truyền từng `messageId` sang service qua proxy.

[MessagingServiceImpl:48](D:/DATN/flash-ticket-platform/services/booking-service/src/main/java/com/flashticket/booking_service/messaging/service/impl/MessagingServiceImpl.java:48) khóa WRITE row outbox và kiểm lại row còn cần gửi. Khóa giữ đến hết transaction gửi, dùng một kết nối DB; không giữ khóa order/inventory.

```java
rabbitTemplate.send(exchange, envelope.messageType(), message, correlation);
var confirm = correlation.getFuture().get(confirmTimeout.toMillis(), TimeUnit.MILLISECONDS);
if (!confirm.ack() || correlation.getReturned() != null)
  failure = ErrorCode.MESSAGE_PUBLISH_FAILED;
```

Message persistent được gửi tới `business.ex`, routing key là message type; YAML bật mandatory, publisher returns và correlated confirms. Broker ACK và không trả message lại thì đặt `publishedAt`; lỗi thì tăng `attempts`, lưu mã lỗi, hẹn thử lại sau `5s`. Confirm timeout mặc định `5s`; ID correlation cho attempt thay đổi nhưng message ID nghiệp vụ giữ nguyên.

**Khác Shop App mẫu:** publisher confirm chỉ xác nhận broker, chưa xác nhận Payment xử lý thành công. Crash sau publish nhưng trước DB commit vẫn có thể gửi lại cùng ID; Payment cần idempotency.

## 7. Lỗi nhận message và bằng chứng kiểm thử

Listener reject không requeue với `INVALID_MESSAGE`, `MESSAGE_ID_CONFLICT`, `PAYMENT_SNAPSHOT_MISMATCH`, `RESERVATION_DATA_CONFLICT`; topology khai báo đưa vào `booking.accept-payment.dlq`. Các lỗi khác đi ra container để xử lý lại theo broker/container; policy queue khai báo delivery-limit `20`, không thêm vòng application retry ở listener này.

Kết quả đã chạy: full suite **360 unit + 81 integration** đạt; lần chạy chọn riêng **13 integration case** cũng đạt. Năm bài test relay với RabbitMQ 119 đạt trên resource cách ly; message publish/read thủ công trên 119 xác nhận broker giữ nguyên payload, chưa phải Booking consumer xử lý live. Xem [TESTING.md](D:/DATN/flash-ticket-platform/services/booking-service/TESTING.md). Lượt viết docs này chỉ đối chiếu source; chưa kiểm chứng consumer Payment thật hoặc luồng phát vé hoàn chỉnh.
