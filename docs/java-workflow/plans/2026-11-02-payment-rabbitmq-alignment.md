# Đồng bộ Payment với RabbitMQ đã triển khai

Trạng thái: **PENDING — chờ duyệt khai báo mới và cách xử lý queue start.**

Phạm vi: mã Payment và test riêng; không đổi Booking/Ticket, không cấp thêm quyền broker, không purge hoặc tự replay DLQ của hệ thống đang chạy.

## Bằng chứng đã kiểm tra

- Exchange nghiệp vụ: `business.ex`.
- Sáu queue Payment: `payment.finance-configured.q`, `payment.event-cancelled.q`, `payment.start.q`, `payment.decision.q`, `payment.ticket-issuance-failed.q`, `payment.order-completed.q`.
- PaymentMessageListener hiện chỉ có ba consumer cuối; FinanceService chưa có phương thức nghiệp vụ.
- Listener ACK thủ công chỉ ACK sau service thành công; ngoại lệ nghiệp vụ không thuộc nhóm fatal có thể đi ra mà chưa ACK/NACK. Thread dump của JVM Payment hiện tại cho thấy consumer đang chờ message, không mắc trong một lời gọi nghiệp vụ tại thời điểm lấy dump. Điều này chưa xác định nội dung hoặc nguyên nhân riêng của 13 message Unacked.
- Deployed routing: `ticket.issuance-failed.v1` vào Payment; `ticket.issued.v1` và `booking.issuance-failed.v1` vào Booking. Payment đang dùng `ticket.issuance-result.v1` và gửi `booking.issuance-result.v1`, nên chỉ sửa tên queue chưa đủ.
- Booking phát `booking.order-completed.v1` sau khi commit. Payment hiện yêu cầu một checkpoint outbox kết quả Ticket trước đó; điều kiện này không phù hợp đường thành công của topology đã triển khai.
- Thiết kế Payment hiện dùng HTTP để khởi tạo thanh toán. StartPayment DTO cũ có purchase/requestKey nhưng không có client IP để tạo URL VNPay cho người dùng. Không tự bịa địa chỉ IP hoặc âm thầm bật lại luồng khởi tạo cũ.

## Hành vi sau sửa

1. Sáu consumer dùng đúng sáu queue trên; ACK tự động của container chỉ diễn ra sau khi service proxy đã commit. Payload/hợp đồng sai được reject vào DLQ; lỗi DB/broker tạm thời requeue theo cơ chế broker. Không ACK vô điều kiện và không đưa dữ liệu payment nhạy cảm vào log.
2. Finance configured: kiểm tra Envelope và event/version, cập nhật projection theo phiên bản, chống replay bằng inbox; không xóa cancellation marker khi finance đến muộn.
3. Event cancelled: ghi cancellation marker và inbox trong cùng TX; worker quét theo batch để tạo nghĩa vụ refund PENDING cho charge chưa có refund, kể cả charge commit sau cancellation. Không gọi hoàn tiền thật hoặc triển khai payout trong task này.
4. Start queue: phương án đề xuất giữ HTTP là đường khởi tạo đã chốt; consumer ghi kết quả REJECTED/HTTP_START_REQUIRED có idempotency cho lệnh legacy, không tạo URL bằng IP giả. Nếu chọn hỗ trợ MQ tạo payment đầy đủ, cần chốt thêm hợp đồng client IP trước khi viết phần phụ thuộc.
5. Decision: xác thực acceptedChargeId/outcome, xử lý idempotent; thiếu dữ liệu payment gốc là lỗi có mã rõ ràng, không dựng charge/saga giả từ message và không để message treo Unacked.
6. Ticket failure: chấp nhận `ticket.issuance-failed.v1` khi state FAILED; giữ kiểm tra charge/issuance và ghi nghĩa vụ refund một lần. Kết quả gửi Booking dùng routing/messageType hiện có (`ticket.issued.v1` hoặc `booking.issuance-failed.v1`).
7. Order completed: xác nhận charge, trạng thái saga và số vé theo purchase snapshot. Checkpoint Ticket cục bộ được đối chiếu nếu có; không bắt buộc một checkpoint mà topology thành công không đi qua Payment. Không đảo trạng thái compensation đã chốt.
8. Chỉ khai báo topology tự động trong môi trường test được bật rõ ràng; production dùng queue/exchange đã provision. Test dùng tên queue riêng và DB riêng, không đọc/ACK message thật của user.

## Danh sách khai báo mới cần duyệt

Không đề xuất class, record, enum hoặc dependency mới. Dùng DTO/entity/repository hiện có.

| ID | Vị trí | Chữ ký và annotation |
| --- | --- | --- |
| M01 | PaymentMessageListener | `public void onFinanceConfigured(Message message)`; `@RabbitListener(queues="${payment.queues.finance:payment.finance-configured.q}", ackMode="AUTO", concurrency="1")` |
| M02 | PaymentMessageListener | `public void onEventCancelled(Message message)`; `@RabbitListener(queues="${payment.queues.cancelled:payment.event-cancelled.q}", ackMode="AUTO", concurrency="1")` |
| M03 | PaymentMessageListener | `public void onStartPayment(Message message)`; `@RabbitListener(queues="${payment.queues.start:payment.start.q}", ackMode="AUTO", concurrency="1")` |
| M04 | FinanceService và FinanceServiceImpl | `public void recordFinanceConfiguration(Envelope<FinanceConfiguration> incomingEnvelope)`; interface + override; implementation `@Transactional` |
| M05 | FinanceService và FinanceServiceImpl | `public void recordEventCancellation(Envelope<EventCancellation> incomingEnvelope)`; interface + override; implementation `@Transactional` |
| M06 | MessageInvariantValidator | `public void validateFinanceConfiguration(Envelope<FinanceConfiguration> envelope)` |
| M07 | MessageInvariantValidator | `public void validateEventCancellation(Envelope<EventCancellation> envelope)` |
| M08 | PaymentService | `public void recordStartPayment(Envelope<StartPayment> incomingEnvelope)`; `@Transactional`; hành vi HTTP-only/legacy rejection như mục 4, chờ xác nhận lựa chọn |
| M09 | RefundService và RefundServiceImpl | `public int recordCancelledChargeRefunds(int batchSize)`; interface + override; implementation `@Transactional`, xử lý batch charge của event đã cancelled và chưa có refund |
| M10 | ChargeRepository | `List<Charge> findCancelledChargesWithoutRefund(Pageable pageable)`; `@Query` trên dữ liệu Payment cục bộ, khóa charge để tránh tạo refund trùng |
| M11 | PaymentWorker | `public void refundCancelledCharges()`; `@Scheduled(fixedDelayString="${payment.refund.poll-delay:5000}", initialDelayString="${payment.worker.initial-delay:0}")` |
| M12 | OutboxMessageRepository | `Optional<OutboxMessage> findFirstByAggregateIdAndMessageTypeInOrderByOccurredAtDescMessageIdDesc(UUID aggregateId, List<String> messageTypes)`; đọc cả checkpoint tên cũ và tên đã đồng bộ |
| T01 | PaymentFlowIntegrationTest | `void consumesAllSixDeployedQueuesAndCommitsBeforeAcknowledging() throws Exception`; `@Test` |
| T02 | PaymentFlowIntegrationTest | `void transientFailureRequeuesAndPermanentFailureDeadLetters() throws Exception`; `@Test` |
| T03 | PaymentFlowIntegrationTest | `void financeReplayAndStaleVersionsPreserveCancellation() throws Exception`; `@Test` |
| T04 | PaymentFlowIntegrationTest | `void cancellationRefundScanIncludesLateChargesOnce() throws Exception`; `@Test` |
| T05 | PaymentFlowIntegrationTest | `void startQueueRejectsLegacyRequestsIdempotently() throws Exception`; `@Test`, theo phương án HTTP-only |
| T06 | PaymentFlowIntegrationTest | `void ticketFailureAndBookingCompletionUseDeployedRoutingKeys() throws Exception`; `@Test` |

### Delta ở khai báo hiện có và API sinh

- Ba listener hiện có chuyển `ackMode` MANUAL → AUTO; bỏ `Channel` khỏi tham số và bỏ basicAck thủ công. Giữ tên phương thức và Message đầu vào. Lỗi permanent được chuyển thành exception reject/DLQ cụ thể; lỗi transient phải đi ra để container requeue.
- `RabbitMqConfig.paymentSagaTopology()` chỉ được bật bởi thuộc tính test tường minh; bổ sung topology sáu queue cho test và giữ production không tự khai báo.
- `PaymentMessageListener` giữ `@Component/@RequiredArgsConstructor`; constructor sinh gồm các field hiện có `codec`, `validator`, `sagaService` và thêm `FinanceService financeService`, `PaymentService paymentService`.
- `FinanceServiceImpl` giữ `@Service/@RequiredArgsConstructor`; constructor sinh gồm `eventFinanceSnapshotRepository`, `eventPayoutRepository` hiện có và thêm `MessagingService messagingService`, `MessageInvariantValidator validator`.
- `PaymentService` giữ annotation và các field hiện có; thêm dependency `MessagingService messagingService`, `MessageInvariantValidator validator` nếu cần cho M08; constructor Lombok được cập nhật theo nhóm field này.
- `RefundServiceImpl` giữ bốn repository hiện có; thêm `ChargeRepository chargeRepository` vào constructor Lombok.
- `PaymentWorker` giữ constructor injection hiện có; thêm `RefundService refundService` vào constructor Lombok.
- Không đổi precision/scale/domain, schema, chữ ký JWT hoặc quyền broker; không thay đổi tiền/saga chỉ để làm queue về 0.
- Test tái sử dụng class/helper có sẵn. Nếu cần thêm type/helper/chữ ký ngoài danh sách, trình delta trước khi viết phần phụ thuộc.

## Kiểm chứng dự kiến

- Tái hiện lỗi ACK bằng consumer trên RabbitMQ riêng; kiểm tra ready/unacked, redelivery và DLQ theo từng loại lỗi.
- PostgreSQL riêng áp schema chuẩn; kiểm tra inbox/outbox, rollback, duplicate ID khác payload, stale version, cancellation-before-finance và charge đến muộn.
- Kiểm tra từng consumer nhận đúng routing/messageType và không tự provision trên broker thật.
- Chạy unit, mapping và integration phù hợp bằng JDK 21; không dùng kết quả unit thay cho bằng chứng xử lý message thật.
- Sau khi code được kiểm chứng, user khởi động lại Payment để broker redeliver Unacked còn giữ bởi kết nối cũ. Không purge, ACK tay hoặc replay DLQ khi chưa phân loại payload.

Nguồn yêu cầu: user ngày 02/11/2026: “các queue tôi đã triển khai fix hết trong payment cho tôi”. Danh sách M01–M12/T01–T06 và các delta hiện **chưa được duyệt**.
