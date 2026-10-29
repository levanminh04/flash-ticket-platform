# Plan Booking — AcceptPayment và worker outbox

- v0.1; pha **EXECUTING-PLANS — APPROVED** cho C1–C13/M1–M22/U1–U5/D1–D17. Nguồn: sau bảng xác nhận thực thi và phần giải thích pessimistic lock/relay, user trả lời “ok đồng ý triển khai”. Các nhãn PENDING bên dưới lưu trạng thái bản nháp, được xác nhận này thay cho phạm vi thực thi hiện tại.
- [Spec v0.2](../specs/2026-10-29-booking-accept-payment-design.md). User yêu cầu “viết plan theo spec này”; user đã xác nhận làm thêm worker gửi outbox. Chưa có câu xác nhận riêng toàn bộ khai báo BRAINSTORMING; giữ lịch sử đó PENDING, không tự ghi APPROVED. Plan này là bản nháp đầy đủ để duyệt ở pha WRITING-PLANS.
- Checkout `D:/DATN/flash-ticket-platform`, nhánh `codex/booking-accept-payment`, baseline `a6b9ffc`; không tạo thêm worktree/branch.
- Task: [Nhận AcceptPayment, xác nhận reservation và cập nhật inventory, đơn hàng](https://app.clickup.com/t/z8qgvc7w2t).

## 1. Kết quả cần đạt

`booking.accept-payment.v1` → listener AUTO → transaction Booking chốt/release tài nguyên + inbox + outbox → commit → worker gửi `booking.payment-decision.v1` → broker confirm → commit publishedAt.

Giữ đủ ACCEPTED/DUPLICATE/LATE/CANCELLED, không chuyển COMPLETED hay phát vé tại Booking. Sai tiền/version không giả thành LATE. Tiền/vé từ frozen snapshot; không tính lại theo config mới. Không HTTP AcceptPayment, EntityManager, native SQL mới, schema mới, Redis lock, generic framework, application retry chồng broker retry.

Hai transaction tách biệt: transaction accept không gọi mạng; transaction relay chỉ giữ row outbox trong lúc gửi/chờ confirm. Receiver Payment cần idempotency vì cửa sổ crash sau publish trước commit có thể gửi lại cùng messageId. Chỉ công bố nghiệm thu Payment khi kiểm consumer Payment thật; queue nhận không đồng nghĩa Payment đã xử lý.

## 2. Đối chiếu nguồn và cách code

- Shop App `services/impl/OrderService.java#updateOrder`: repository → orElseThrow → setter → save; `components/JwtTokenUtil.java`: @Value trên field. Giữ @RequiredArgsConstructor cho dependency; không copy lỗi của mẫu hoặc thêm ModelMapper cho thay đổi counter.
- Code đích đã đọc: OrderServiceImpl/OrderRepository, Reservation/ReservationAllocation, các query khóa inventory/limit/promotion, InboxMessage/OutboxMessage và mapper, SalesConfigurationListener, MessagingServiceImpl, BookingPersistenceIT.
- `MessagingServiceImpl` hiện là scaffold; `OutboxMessageRepository` chưa có query. Tái dùng chúng thay vì tạo service/DAO khác.
- AMQP JAR local4.1.1 đã kiểm bằng javap: CorrelationData.getFuture(), getReturned(); Confirm.isAck()/getReason() đều có. Không thêm global callback hay anonymous class. Sample tham khảo trong spec §11.
- Các cơ chế NGOÀI luồng Shop App đã đối chiếu: AUTO listener, typed Envelope/record có sẵn, inbox/outbox, database clock, khóa repository, scheduler, confirm future. Từng khai báo bên dưới được trình riêng; không đổi dependency/framework version.

## 3. Bước A — Nhận command, query khóa, validation

Item C1–C5, M1–M7; test U1–U3/D8. File Java theo bảng §8–9.

1. Thêm hai mã lỗi PAYMENT_SNAPSHOT_MISMATCH/RESERVATION_DATA_CONFLICT (409, message cố định); ErrorCode.from hiện có tự giữ BusinessException, không thay global handler bằng catch chung.
2. M4 JPQL `select o.eventId from Order o where o.id=:orderId`; M5 @Lock(PESSIMISTIC_WRITE) derived query. Không sửa findById mặc định hoặc nạp Order trước khi chờ khóa.
3. M1 nhận `Envelope<AcceptPayment>` bằng converter sẵn có, chỉ gọi service proxy và phân loại lỗi. @RabbitListener queues booking.accept-payment.q, ackMode AUTO, concurrency1. Service phải chạy Validator trên envelope (cascades payload), kiểm type/schema/aggregateId/version; annotation DTO không tự chạy vì chỉ deserialize.
4. M7 kiểm frozen snapshot hợp lệ, owner/event/order IDs, expiry/frozenAt/money/currency/paymentSnapshotVersion nhất quán; amount>0 và bằng total, sourceOrderVersion khớp. Không gọi preparePurchaseSnapshot để lén freeze khi chưa freeze. Không truy DB Payment để kiểm attempt.
5. Lỗi vĩnh viễn INVALID_MESSAGE/MESSAGE_ID_CONFLICT/hai mã mới reject no-requeue; CONFIG_NOT_READY và lỗi DB/commit đi ra broker redelivery hữu hạn theo topology. Không bắt exception rồi return.

Checkpoint: compile, unit listener đạt đúng delegate/exception; D8 không ghi tài nguyên/inbox/outbox khi dữ liệu invalid. Chưa có quyền ghi Java trước gate thực thi.

## 4. Bước B — Chốt/release tài nguyên và idempotency

Item C3/C7, M3/M6/M8–M14; D1–D11.

Thứ tự trong M3:

1. Validator + hash M6; timeout5s transaction-local; scalar eventId → snapshot READ → order WRITE → đọc inbox (consumerName booking.accept-payment).
2. Receipt có cùng hash: đọc PaymentDecision bằng JsonMapper, validate và kiểm orderId/chargeId, trả bản cũ; không tạo outbox mới. Khác hash báo MESSAGE_ID_CONFLICT. Receipt mới version null, save để INSERT; unique tranh chấp rollback toàn TX, redelivery mở TX mới.
3. M7 kiểm frozen input; acceptedChargeId đã có: cùng charge ACCEPTED, khác charge DUPLICATE, không thay acceptedAt/counter. Check này trước deadline và trạng thái terminal hiện tại. Message mới có decision/receipt/outbox mới; same-message replay giữ response cũ.
4. Chưa có accepted charge: lấy reservation/allocations, M8 khóa limit → inventory → promotion bằng query hiện có. Chỉ load entity promotion sau query khóa inventory promotion để tránh persistence context giữ counter cũ trước khi chờ; có thể đọc promotionId từ usage/proxy ID trước khóa. Sau toàn bộ khóa mới lấy DB clock và chọn outcome; không setter counter trước khi còn phải chờ khóa.
5. CANCELLED ưu tiên trước LATE. Event/order hủy → CANCELLED; expired/đúng expiresAt hoặc sau hạn → LATE. Nếu reservation vẫn HELD thì release; đã RELEASED chỉ xác minh nhất quán, không trừ lại. COMMITTED nhưng order chưa accepted là bất nhất. Pending hợp lệ/còn hạn → ACCEPTED.
6. M9–M13 nhận entity đã khóa, kiểm allocations khớp OrderItem/frozen selection, đủ quantity/held, đúng owner và state, rồi cập nhật như bảng dưới. Không dùng max(0) hoặc chỉ held toàn pool để chứng minh ownership.
7. Order/reservation/allocations/usage và counter cùng commit với M14. Không cascade MERGE mới: allocations đã được load managed, setter được dirty checking ghi; save() không đồng nghĩa commit. Mọi lỗi flush/commit đi khỏi service proxy.

| Đối tượng | ACCEPTED | LATE/CANCELLED nếu còn HELD |
| --- | --- | --- |
| QUANTITY | held-=q, purchased+=q theo type | held-=q |
| STANDING | tổng q theo sector, held→purchased một lần | giảm held sector một lần |
| SEATED | HELD→PURCHASED; giữ reservationId/generation | AVAILABLE hoặc UNAVAILABLE nếu hidden; xóa reservationId, giữ generation |
| PurchaseLimit | tổng vé held→purchased | giảm held, không giảm purchased |
| PromotionUsage/counter | HELD→USED; held--/used++ | HELD→RELEASED; held-- |
| Reservation/allocations | COMMITTED | RELEASED |
| Order | ISSUING + acceptedChargeId/acceptedAt | EXPIRED hoặc CANCELLED; không hồi sinh terminal |

M14 lưu inbox.responsePayload và outbox decisionEnvelope cùng TX: messageId=UUID.nameUUIDFromBytes của `booking.payment-decision:`+incoming.messageId UTF-8; giữ correlationId/traceparent, causationId=input.messageId, aggregateId=orderId; version=paymentSnapshotVersion, không phải JPA version/sequence các decision. PayloadHash hash toàn Envelope qua mapper ổn định, không chuỗi tự ghép JSON.

Checkpoint: các D1–D11 kiểm 3 mode, promo, receipt/outbox, replay quá hạn, hai charge cạnh tranh, deadline sau khóa, rollback constraint. Khóa 5s không nới thành30s để cho test đạt; test observe wait/DB clock, không suy thời điểm bằng sleep.

## 5. Bước C — Worker gửi outbox bền

Item C8–C12, M15–M20; U4–U5/D13–D16.

- M15 non-transactional: query tối đa20 ID due thuộc đúng booking.payment-decision.v1, thứ tự occurredAt/messageId; gọi M18 qua MessagingService proxy cho từng ID. Không giữ list entity cũ để save về sau; lỗi một row log mã lỗi rồi tiếp tục, interrupt dừng batch.
- M18 @Transactional: set lock-timeout5s hiện có → khóa đúng row outbox → kiểm lại type/publishedAt/due. Mất row hoặc đã published/not due thì return, không publish.
- Mapper.toEnvelope(row,PaymentDecision.class); dựng AMQP Message qua Jackson converter từ RabbitTemplate; deliveryMode PERSISTENT, messageId/correlationId đúng envelope. Dùng RabbitTemplate.send(exchange, messageType, message, correlationData) và CorrelationData với ID mới riêng cho từng attempt; envelope/messageId không thay đổi.
- Chờ future tối đa confirmTimeout5s. Chỉ isAck() && getReturned()==null mới ghi publishedAt=DB time, lastErrorCode=null và save. Returned dù ACK vẫn là thất bại routing. Future không xong là UNKNOWN delivery, phải retry cùng ID.
- Nack/return/timeout/connect/conversion-invalid: publishedAt vẫn null, attempts tăng không tràn Integer, nextAttemptAt=DB now+retryDelay, lastErrorCode cố định. Dùng MESSAGE_PUBLISH_FAILED, MESSAGE_BROKER_UNAVAILABLE, DEPENDENCY_TIMEOUT, INVALID_MESSAGE có sẵn theo nhóm lỗi; không thêm loại lỗi mới ngoài C5.
- Bắt exception gửi chỉ quanh phần encode/send/wait, không bao trùm save/commit lỗi DB rồi giả thành công. InterruptedException restore flag; retry metadata nếu TX còn dùng được. Khi chính DB update thất bại thì TX rollback, scheduler để row pending cho lần sau.
- Một TX relay/row giữ outbox lock và connection trong lúc gọi broker; không giữ order/inventory. Confirm timeout không phải bound tổng connect/send. Một worker tuần tự dùng pool5 hiện có, chưa tuyên bố chịu tải lớn; không lease/claim column/Redis/CDC/SKIP LOCKED.
- Crash sau publish trước publishedAt commit gây gửi lại cùng envelope; không tạo ID mới, không xóa row sau N lỗi. Payment phải dedupe bằng inbox. Chưa hỗ trợ publish outbox loại khác.

Checkpoint: broker thật nhận Envelope persistent đúng routing, ACK/no-return mới published; nack mô phỏng/return thật/timeout/broker-down giữ pending; rollback sau publish gửi lại cùng messageId; hai publisher serialize qua lock. Tách rõ ca fake confirm và ca broker thật trong kết quả.

## 6. Bước D — Cấu hình, test broker và nghiệm thu

Item C7/C12/C13, D12–D17 và toàn bộ regression.

Files cấu hình: `infrastructure/config-server/src/main/resources/config/booking-service.yaml`, `services/booking-service/src/main/java/com/flashticket/booking_service/BookingServiceApplication.java`.

```yaml
booking:
  outbox:
    enabled: true
    poll-interval: PT2S
    batch-size: 20
    confirm-timeout: PT5S
    retry-delay: PT5S
spring:
  rabbitmq:
    connection-timeout: 5s
```

Merge vào cây YAML hiện có, không tạo key trùng. Giữ exchange env hiện tại, correlated confirms/returns/mandatory và property reservation lock-timeout5s. C12 thêm @EnableScheduling; C8 @ConditionalOnProperty cho phép tắt worker trong test/local. Không chỉnh secret/.env để viết plan; runtime nếu thiếu cấu hình thì báo đúng thiếu, không giả “chạy đúng env”.

**Delta test so với spec — PENDING:** @DataJpaTest hiện chưa có RabbitTemplate bean cho MessagingServiceImpl. Thêm C13 nested @TestConfiguration với hai @Bean cụ thể M21–M22, dùng broker Testcontainers có sẵn pattern và image rabbitmq:4.3.6-management. C7 thêm một static @Container GenericContainer<?> dùng chung cho relay tests; không yêu cầu thêm dependency/Docker setup mới. C13 không @EnableScheduling; worker gọi trực tiếp. CachingConnectionFactory có lifecycle destroy, confirm CORRELATED/returns; RabbitTemplate dùng converter JsonMapper có sẵn và mandatory=true. Không dùng mock RabbitTemplate rồi gọi đó là integration publish thật.

Tái dùng IT initializer/schema/fixtures; thêm @Import MessagingServiceImpl, OutboxMessageMapperImpl, C13 và inject MessagingService. Không tạo lifecycle/test helper ngoài bảng này; test setup nằm trong methods đã trình. Giữ test isolation/cleanup của suite; test schema DROP/ADD cột hiện có vẫn loại pooled prepared plans cũ. D14 dùng RabbitTemplate mock cục bộ cho nack/timeout khi broker thật khó điều khiển, không thay toàn context; mỗi lần thay dependency qua ReflectionTestUtils phải restore finally.

E2E command→Booking→DB→relay→Payment-bound queue chạy trong broker cách ly, không consume trộm queue Payment live. Nếu test server119 thì dùng dữ liệu test có ID riêng, theo dõi queue chính qua số liệu/log, không purge/truncate/update dữ liệu đội khác. Runtime Payment branch chưa hiện diện: ghi “queue delivery đạt; Payment handler E2E chưa kiểm chứng”, không dựng handler giả để kết luận thành công Payment. Không triển khai Ticket/completion/expiry worker.

## 7. Gate và kiểm chứng

- Gate WRITING-PLANS: **PENDING C1–C13, M1–M22, U1–U5, D1–D17**; bổ sung C13/M21–M22 chỉ cho test wiring. Các bước A–D tham chiếu chính những ID này; chưa bước nào được phép thực thi Java.
- Ngoại lệ ngoài Shop App: @RabbitListener AUTO, @Lock, @Transactional, @Scheduled/@EnableScheduling/@ConditionalOnProperty, inbox/outbox, confirm future, typed DTO có sẵn, @TestConfiguration/@Bean trong test. Không thêm production class ngoài C1/C8.
- Chưa chạy test ở pha plan. Baseline task trước 355 unit +64 IT chỉ là mốc so regression, không là bằng chứng chức năng mới.
- Sau duyệt plan, chỉ chuyển executing-plans khi user yêu cầu; pha đó vẫn duyệt riêng danh sách trước ghi Java. Chưa commit/push/deploy; phê duyệt khai báo không thay quyền Git/server.

Lệnh kiểm khi thực thi từ `D:/DATN/flash-ticket-platform`, JDK23, Enforcer giữ bật:

```powershell
rtk mvn -f services/booking-service/pom.xml -B -ntp -DskipTests compile
rtk mvn -f services/booking-service/pom.xml -B -ntp -Dtest=AcceptPaymentListenerTests test
rtk mvn -f services/booking-service/pom.xml -B -ntp -P integration verify
rtk git diff --check
```

Compile checkpoint A; unit mới sau A/C; full verify một lần sau D, chỉ chạy lại khi sửa/fail mới. Full verify gồm tests và quality gates hiện có; không format module khác. Khi broker/runtime không sẵn sàng ghi giới hạn, không coi skip là pass.

Hoàn tất phần Booking khi cả 3 mode/4outcome, rollback/idempotency/deadline/timeout, inbox/outbox/confirm/retry/crash-gap đều đạt; D17 thực sự nhận ở queue. Phần Payment E2E là kiểm chứng thêm cần source/runtime phía Payment, nêu riêng trong bàn giao. Cập nhật TESTING.md/HANDOFF.md kết quả thực tế; sau execution đề xuất MD giải thích ngắn theo skill writing-docs, không tự tạo trước.

## 8. Danh sách class, field, API sinh — PENDING

Delta C14 EXECUTING-PLANS APPROVED: user trả lời “Duyệt C14 dùng RabbitMQ 119 thật”. BookingServiceApplicationTests hiện có import RabbitAutoConfiguration, inject RabbitTemplate, contextLoads mở/đóng kết nối thật lấy cấu hình RABBITMQ_* từ môi trường; worker/listener/dynamic declarations tắt. Không method/class mới. Bản full verify phải nạp env cho test này; không thêm secret vào source/test YAML hoặc log. Các IT PostgreSQL/broker fault injection vẫn cách ly để không truncate/purge dữ liệu chung.

Các đường dẫn trong bảng nối với `services/booking-service/src/main/java/com/flashticket/booking_service/`; C6/C7/C13 ở `src/test/java/com/flashticket/booking_service/`. Mẫu Shop App cụ thể tại §2; các callback messaging/worker/test wiring ghi NGOÀI MẪU.

| ID | Class/file | NEW/EXISTING | Field/annotation/API sinh và mẫu |
| --- | --- | --- | --- |
| C1 | messaging/AcceptPaymentListener.java | NEW | @Component, @RequiredArgsConstructor; final OrderService orderService; đúng hình dạng SalesConfigurationListener, ngoài luồng HTTP Shop App |
| C2 | order/service/OrderService.java | EXISTING | thêm chữ ký M2; không tạo cặp service mới |
| C3 | order/service/impl/OrderServiceImpl.java | EXISTING | @Service/@RequiredArgsConstructor; thêm final InboxMessageRepository, OutboxMessageRepository, OutboxMessageMapper, PromotionInventoryRepository; giữ các dependency hiện có, JsonMapper, Validator và @Value lock-timeout |
| C4 | order/repository/OrderRepository.java | EXISTING | thêm scalar JPQL và derived query khóa, không EntityManager/native mới |
| C5 | exception/ErrorCode.java | EXISTING | thêm PAYMENT_SNAPSHOT_MISMATCH và RESERVATION_DATA_CONFLICT với message cố định, HTTP409; không type exception mới, không constructor mới |
| C6 | messaging/AcceptPaymentListenerTests.java | NEW | JUnit/Mockito @ExtendWith(MockitoExtension.class); @Mock OrderService; @InjectMocks listener; không Spring test configuration/bean/helper mới |
| C7 | persistence/BookingPersistenceIT.java | EXISTING | thêm D1–D12; dùng context/fixtures/helpers đã có, không nested class/new callback method/helper mới |
| C8 | messaging/PaymentDecisionOutboxWorker.java | NEW | @Component/@RequiredArgsConstructor/@ConditionalOnProperty(prefix="booking.outbox", name="enabled", havingValue="true", matchIfMissing=true); final OutboxMessageRepository, MessagingService; @Value int batchSize. Lombok sinh `public PaymentDecisionOutboxWorker(OutboxMessageRepository outboxMessageRepository, MessagingService messagingService)` |
| C9 | messaging/service/MessagingService.java | EXISTING | thêm M17, tái dùng interface hiện có |
| C10 | messaging/service/impl/MessagingServiceImpl.java | EXISTING | thêm final RabbitTemplate, OrderRepository cho DB clock/timeout; giữ final InboxMessageRepository, OutboxMessageRepository, OutboxMessageMapper; constructor Lombok sinh với năm dependency. @Value Duration confirmTimeout/retryDelay, String exchange/lockTimeout; @Transactional trên method public |
| C11 | messaging/repository/OutboxMessageRepository.java | EXISTING | thêm M19–M20; derived candidate query + @Lock, không native query/schema mới |
| C12 | BookingServiceApplication.java | EXISTING | thêm @EnableScheduling, main giữ nguyên; khác luồng mẫu Shop App đã đọc |
| C13 | persistence/BookingPersistenceIT.RelayRabbitTestConfiguration | NEW nested public static | @TestConfiguration(proxyBeanMethods=false); implicit public no-arg constructor; M21/M22 @Bean; NGOÀI MẪU Shop App, chỉ test |

C1 constructor Lombok: `public AcceptPaymentListener(OrderService orderService)`. C8 constructor Lombok: `public PaymentDecisionOutboxWorker(OutboxMessageRepository outboxMessageRepository, MessagingService messagingService)`.

C3 constructor Lombok giữ đúng thứ tự final field hiện có: OrderRepository, IdempotencyRequestRepository, OrderMapper, EventSalesSnapshotRepository, PurchaseLimitRepository, TicketTypeInventoryRepository, SectorInventoryRepository, SeatInventoryRepository, ReservationRepository, JsonMapper, PromotionUsageRepository, Validator; nối thêm InboxMessageRepository, OutboxMessageRepository, OutboxMessageMapper, PromotionInventoryRepository. C10 constructor gồm InboxMessageRepository, OutboxMessageRepository, OutboxMessageMapper, RabbitTemplate, OrderRepository. Tên tham số theo field camelCase. Không getter/setter/builder mới; @Value không phải tham số constructor. C6 implicit no-arg; C7 giữ constructor mặc định hiện có. M16 trùng API sinh C8, không tạo constructor thứ hai. C13 implicit public no-arg, không constructor viết tay.

Mẫu: C2–C4/C9–C11 là interface/service/repository cụ thể kiểu Shop App; khóa/relay là NGOÀI MẪU. C1/C8/C12/C13 là NGOÀI MẪU flow OrderService Shop App. C5 giữ BusinessException/ErrorCode có sẵn ở đích. C6/C7 dùng test infrastructure đích, không tuyên bố Shop App dùng Testcontainers. C7 thêm D1–D17/@Import như §6; C6 thêm U1–U5 và @Mock đã nêu; không type test khác.

## 9. Danh sách method production và test wiring — PENDING

| ID | Class | Chữ ký / annotation | NEW hoặc cách viết |
| --- | --- | --- | --- |
| M1 | AcceptPaymentListener | `public void handleAcceptPayment(Envelope<AcceptPayment> message)`; @RabbitListener như §5 | NEW |
| M2 | OrderService | `PaymentDecision acceptPayment(Envelope<AcceptPayment> message)` | NEW |
| M3 | OrderServiceImpl | `public PaymentDecision acceptPayment(Envelope<AcceptPayment> message)`; @Override @Transactional | NEW |
| M4 | OrderRepository | `Optional<UUID> findEventIdById(UUID orderId)`; @Query JPQL scalar + @Param("orderId") | NEW |
| M5 | OrderRepository | `Optional<Order> findLockedById(UUID id)`; @Lock(PESSIMISTIC_WRITE) | NEW |
| M6 | OrderServiceImpl | `private String calculateAcceptPaymentHash(Envelope<AcceptPayment> message)` | NEW; JsonMapper + SHA-256 theo mẫu hash hiện có |
| M7 | OrderServiceImpl | `private void validatePaymentSnapshot(Order lockedOrder, AcceptPayment requestedPayment)` | NEW; Validator + so amount/currency/version/IDs |
| M8 | OrderServiceImpl | `private PaymentDecision settlePendingPayment(Order lockedOrder, Reservation storedReservation, EventSalesSnapshot storedSnapshot, AcceptPayment requestedPayment)` | NEW; lấy toàn bộ khóa, DB clock, chọn outcome; điều phối M9–M13 và state allocation |
| M9 | OrderServiceImpl | `private void updateQuantityInventoryForPaymentDecision(Order lockedOrder, Reservation storedReservation, List<TicketTypeInventory> lockedInventories, ReservationState targetState)` | NEW; cập nhật pool type đã khóa |
| M10 | OrderServiceImpl | `private void updateStandingInventoryForPaymentDecision(Order lockedOrder, Reservation storedReservation, SectorInventory lockedSector, ReservationState targetState)` | NEW; cập nhật sector đã khóa |
| M11 | OrderServiceImpl | `private void updateSeatedInventoryForPaymentDecision(Order lockedOrder, Reservation storedReservation, List<SeatInventory> lockedSeats, ReservationState targetState)` | NEW; kiểm owner và cập nhật seat đã khóa |
| M12 | OrderServiceImpl | `private void updatePurchaseLimitForPaymentDecision(Order lockedOrder, Reservation storedReservation, PurchaseLimit lockedPurchaseLimit, ReservationState targetState)` | NEW; cập nhật limit đã khóa |
| M13 | OrderServiceImpl | `private void updatePromotionUsageForPaymentDecision(Order lockedOrder, PromotionUsage storedUsage, PromotionInventory lockedPromotion, ReservationState targetState)` | NEW; cập nhật usage/counter đã khóa |
| M14 | OrderServiceImpl | `private void persistPaymentDecision(Envelope<AcceptPayment> message, String payloadHash, PaymentDecision decision)` | NEW; inbox + outbox qua mapper/repository |
| M15 | PaymentDecisionOutboxWorker | `public void publishDuePaymentDecisions()`; @Scheduled(fixedDelayString="${booking.outbox.poll-interval:PT2S}") | NEW |
| M16 | PaymentDecisionOutboxWorker | constructor Lombok đã nêu C8, không constructor viết tay | NEW |
| M17 | MessagingService | `void publishPaymentDecision(UUID messageId)` | NEW |
| M18 | MessagingServiceImpl | `public void publishPaymentDecision(UUID messageId)`; @Override/@Transactional; send + wait confirm + save metadata bên trong cùng transaction relay riêng | NEW |
| M19 | OutboxMessageRepository | `List<OutboxMessage> findByMessageTypeAndPublishedAtIsNullAndNextAttemptAtLessThanEqualOrderByOccurredAtAscMessageIdAsc(String messageType, Instant now, Pageable pageable)` | NEW |
| M20 | OutboxMessageRepository | `Optional<OutboxMessage> findLockedByMessageId(UUID messageId)`; @Lock(PESSIMISTIC_WRITE) | NEW |
| M21 | C13 | `public CachingConnectionFactory relayRabbitConnectionFactory()`; @Bean(destroyMethod="destroy") | NEW; host/port từ static test broker, CORRELATED/returns |
| M22 | C13 | `public RabbitTemplate relayRabbitTemplate(CachingConnectionFactory relayRabbitConnectionFactory, JsonMapper objectMapper)`; @Bean | NEW; converter Jackson, mandatory=true |

M1/M15 là framework callbacks đã liệt kê. Không thêm ConfirmCallback/MessagePostProcessor/anonymous class. M3 gọi private M6–M14 trong transaction service; M18 qua proxy từ worker, không self-invocation. M4 có @Param; M19 dùng PageRequest.of(0,batchSize), không method paging mới. R1 trong spec REUSE nguyên signature. C13 implicit constructor nêu §8 cũng thuộc gate.

## 10. Danh sách test method đầy đủ — PENDING

U1–U5 ở C6, @Test, package-private void, không param/throws. D1–D17 ở C7, @Test + @Transactional(propagation=NOT_SUPPORTED), package-private void, không param, throws Exception. Field/context thay đổi như §6/C7/C13.

| ID | Class | Chữ ký đầy đủ | Kiểm chứng |
| --- | --- | --- | --- |
| U1 | AcceptPaymentListenerTests | `void delegatesAcceptPaymentToTransactionalService()` | gọi service đồng bộ |
| U2 | AcceptPaymentListenerTests | `void rejectsPermanentAcceptPaymentErrorsWithoutRequeue()` | nhóm lỗi dữ liệu §5 |
| U3 | AcceptPaymentListenerTests | `void propagatesDatabaseAndCommitFailures()` | không ACK thành công giả |
| D1 | BookingPersistenceIT | `void acceptsPaymentForEveryInventoryMode() throws Exception` | QUANTITY/STANDING/SEATED, counter/allocations/limit/ISSUING |
| D2 | BookingPersistenceIT | `void commitsPromotionUsageWithAcceptedPayment() throws Exception` | HELD→USED, counter một lần |
| D3 | BookingPersistenceIT | `void replaysAcceptPaymentWithoutChangingResources() throws Exception` | receipt/hash/outbox; cùng charge/new message; replay qua hạn |
| D4 | BookingPersistenceIT | `void rejectsConflictingAcceptPaymentMessages() throws Exception` | cùng messageId khác hash/order, concurrent PK race |
| D5 | BookingPersistenceIT | `void returnsDuplicateForAnotherAcceptedCharge() throws Exception` | hai charge cạnh tranh, chỉ một accept |
| D6 | BookingPersistenceIT | `void releasesExpiredReservationWhenPaymentArrivesLate() throws Exception` | LATE, release cả 3 mode/promo/limit đúng một lần |
| D7 | BookingPersistenceIT | `void rejectsPaymentForCancelledEventOrOrder() throws Exception` | CANCELLED không hồi sinh; release HELD/replay |
| D8 | BookingPersistenceIT | `void rejectsMismatchedFrozenPaymentSnapshot() throws Exception` | chưa freeze/sai amount/currency/version, không đổi tài nguyên |
| D9 | BookingPersistenceIT | `void rollsBackAcceptPaymentWhenPersistenceFails() throws Exception` | lỗi constraint/commit rollback nghiệp vụ/inbox/outbox |
| D10 | BookingPersistenceIT | `void checksPaymentDeadlineAfterInventoryLockWait() throws Exception` | deadline sau chờ, đúng hạn là late; timeout5s transaction-local |
| D11 | BookingPersistenceIT | `void rejectsInconsistentReservationResources() throws Exception` | allocations/counter/seat owner sai không bán hoặc release nhầm |
| D12 | BookingPersistenceIT | `void acknowledgesAcceptPaymentOnlyAfterDatabaseCommit() throws Exception` | broker thật; commit, redelivery/inbox, permanent→DLQ; không giả Payment nhận outbox |
| U4 | AcceptPaymentListenerTests | `void publishesOnlyDuePaymentDecisionOutboxIds()` | lọc loại/due/batch, delegate từng ID |
| U5 | AcceptPaymentListenerTests | `void continuesOutboxBatchAfterOneRowFails()` | lỗi một row không mất phần còn lại; interrupt dừng vòng |
| D13 | BookingPersistenceIT | `void publishesPaymentDecisionWithConfirmAndPersistentEnvelope() throws Exception` | PostgreSQL + Rabbit thật; Payment-bound test queue nhận envelope đúng, persistent/messageId/causation, publishedAt chỉ sau confirm |
| D14 | BookingPersistenceIT | `void retriesUnconfirmedOrReturnedPaymentDecision() throws Exception` | nack/return/confirm-timeout/broker-down không published, attempts/nextAttemptAt; confirm mô phỏng cần ghi rõ phần nào mock |
| D15 | BookingPersistenceIT | `void replaysPublishedButUncommittedOutboxMessageWithSameId() throws Exception` | rollback DB sau gửi broker; gửi lại cùng ID/body, receiver idempotent không tác động lần hai |
| D16 | BookingPersistenceIT | `void serializesConcurrentOutboxPublishersAndSkipsPublishedRows() throws Exception` | hai worker cùng row không đè metadata; row committed published không gửi lại |
| D17 | BookingPersistenceIT | `void deliversAcceptPaymentDecisionThroughRealRabbitMq() throws Exception` | command→consumer→DB commit→relay→Payment-bound queue; ACCEPTED/LATE/DUPLICATE/CANCELLED và restart backlog; phân biệt queue nhận với Payment service đã xử lý |

Nguồn duyệt: user yêu cầu lập plan; chưa xác nhận gate WRITING-PLANS. Nếu cần helper/test lifecycle/annotation/signature khác các bảng thì trình delta trước khi viết, không lách bằng callback hoặc generator.

## 11. Nhật ký thực thi

- EXECUTING-PLANS APPROVED: user “ok đồng ý triển khai” sau khi trình C1–C13/M1–M22/U1–U5/D1–D17 và giải thích relay. C14 real Rabbit smoke được duyệt riêng theo nguồn ở §8; đề xuất mock RabbitTemplate không được áp dụng.
- A–D đã triển khai: consumer AUTO, chốt/release ba mode, bốn quyết định, receipt/outbox transaction, relay pessimistic row lock + persistent Envelope + confirm/no-return + retry metadata. Không class/method production ngoài danh sách; không bảng/cột/dependency mới, không code Payment.
- Production compile đạt. Test mới đúng U1–U5/D1–D17; test wiring C13/M21–M22 và smoke C14 đúng phạm vi duyệt.
- Full `-P integration verify` đạt lúc 17:23:47 ngày 29/10/2026: **360 unit +81 integration tests**, 0 failure/error/skip, BUILD SUCCESS. Log `services/booking-service/target/accept-verify-final.log`; report đủ81 test lưu `target/accept-full-81-tests.xml`. DB/broker fault tests dùng Testcontainers; smoke thực sự kết nối119 bằng app credential, không consume queue nghiệp vụ.
- Các lỗi trước khi đạt: Mockito re-stub exception, fixture QUANTITY có nhầm seat, kiểm sai property delivery mode nhận, pg_stat_activity observation không ổn định; đã sửa đúng thân test. JAR process Booking cũ34232 do agent khởi động từ task trước giữ file trên Windows; đã xác minh và dừng riêng process đó để Maven đóng gói. Không nới timeout production5s.
- Hai lượt5/5 relay methods đạt với Rabbit119 riêng exchange/queue test và cleanup, DB vẫn cách ly. Lượt cuối dùng bản D17 bốn outcome; log `target/accept-remote119.log`. Queue/exchange `booking.accept-payment.test.1d36822b890a` và queue hậu tố `.q` đã cleanup HTTP204. Test topology cần quyền admin, không đổi permission app hoặc topology live. Không purge/consume queue Payment nghiệp vụ.
- Chưa nghiệm thu consumer Payment thật, chưa deploy, chưa commit/push. Khi hoàn tất chỉ đề xuất skill writing-docs theo workflow; không tự thêm giải thích MD mới.
