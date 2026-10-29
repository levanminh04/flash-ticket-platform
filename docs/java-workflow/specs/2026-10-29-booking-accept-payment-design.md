# Booking — Nhận AcceptPayment và chốt reservation

- Phiên bản v0.2; tài liệu thiết kế BRAINSTORMING. Các nhãn PENDING lưu trạng thái lúc thiết kế; user sau đó đã duyệt EXECUTING-PLANS và phần triển khai/kiểm chứng được ghi trong [plan §11](../plans/2026-10-29-booking-accept-payment-plan.md). C14 real Rabbit smoke là delta đã duyệt ở plan.
- Nhánh riêng theo yêu cầu user: `codex/booking-accept-payment`, từ `dev/son` tại `a6b9ffc`; checkout `D:/DATN/flash-ticket-platform`.
- Task: [Nhận AcceptPayment, xác nhận reservation và cập nhật inventory, đơn hàng](https://app.clickup.com/t/z8qgvc7w2t). Nội dung task đã đọc trong cuộc trò chuyện: bước 34–38. Connector ClickUp hiện không còn khả dụng; lần này đối chiếu contract/ảnh đã lưu trong repo, không tuyên bố đã đọc lại dashboard.
- Nguồn yêu cầu thiết kế: user gọi shopapp-java-brainstorming, “lập spec cho task này. làm trên branch riêng”. Nguồn thực thi sau đó: user “ok đồng ý triển khai” sau bảng gate thực thi; chưa commit/push/deploy trong lượt thực thi.

## 1. Phạm vi và điểm cần chốt

Payment đã ghi nhận khoản thu → gửi `booking.accept-payment.v1` → Booking phân xử và chốt tài nguyên → lưu `booking.payment-decision.v1` để Payment tiếp tục phát vé hoặc hoàn tiền.

Phạm vi: consumer + transaction nghiệp vụ + inbox/outbox quyết định + worker gửi quyết định tới RabbitMQ. Không thêm HTTP AcceptPayment song song vì B13/B14 hiện chọn message; “commit rồi xác nhận” là quyết định nghiệp vụ bền, khác ACK giao message của RabbitMQ.

**SCOPE-01 — USER_CONFIRMED:** user trả lời “Làm thêm worker gửi outbox để Payment nhận quyết định qua RabbitMQ trong task này”. Bổ sung §11 và C8–C12/M15–M20/U4–U5/D13–D17 để duyệt. Không thể nghiệm thu “Payment nhận quyết định” chỉ bằng row outbox hoặc publisher confirm: cần consumer Payment thật để xác minh xử lý phía Payment; nếu branch Payment chưa sẵn sàng, chỉ báo đạt tới queue RabbitMQ và giữ E2E Payment chưa kiểm chứng.

Không triển khai phát vé, hoàn tiền, order COMPLETED, worker expiry định kỳ, buyer cancel API, consumer hủy event, hoặc sửa các service do người khác phụ trách. Tuy nhiên **xử lý release cục bộ khi AcceptPayment tới muộn/hủy nằm trong phạm vi**, theo B14 B2; không chờ worker mới có thể trả LATE/CANCELLED đúng.

## 2. Input và quyết định

Tái dùng `Envelope<AcceptPayment>` hiện có; converter Jackson + `Validator` thực sự kiểm envelope/payload, không tự decode field.

```text
AcceptPayment: orderId, chargeId, attemptId, amount, currency, sourceOrderVersion
PaymentDecision: orderId, chargeId, decisionAt,
                 outcome=ACCEPTED|DUPLICATE|LATE|CANCELLED, acceptedChargeId
```

- `messageType=booking.accept-payment.v1`, schemaVersion=1, aggregateId=orderId. Đề xuất aggregateVersion khớp sourceOrderVersion, tức version snapshot thanh toán; không dùng rowVersion để so message.
- amount là chuỗi tiền nguyên dương, bằng total trong frozen snapshot và order; currency và sourceOrderVersion khớp bản đã freeze. Kiểm frozen snapshot hợp lệ/nhất quán trước mọi quyết định theo charge; không tính lại giá bằng config Event mới.
- Payment sở hữu quan hệ attemptId/chargeId và kiểm chứng khoản thu; Booking kiểm UUID/contract và số tiền/version, không truy DB Payment hay tự chứng minh attempt tồn tại. Broker phải giới hạn quyền publish, Envelope không phải chữ ký xác thực producer.

| Điều kiện, xét sau receipt replay và kiểm contract | Quyết định và tác động |
| --- | --- |
| acceptedChargeId bằng chargeId | ACCEPTED; giữ acceptedAt, không trừ tài nguyên lần nữa; xét trước deadline/status hiện tại |
| acceptedChargeId đã có nhưng khác chargeId | DUPLICATE; trả acceptedChargeId cũ, không đổi đơn/tài nguyên |
| Chưa nhận charge, event/order đã CANCELLED | CANCELLED; không nhận tiền; nếu còn HELD thì release đúng một lần |
| Chưa nhận charge, order EXPIRED hoặc DB time >= expiresAt | LATE; nếu còn HELD thì release, chuyển pending order thành EXPIRED |
| Chưa nhận charge, order PENDING_PAYMENT, reservation HELD, chưa hết hạn, dữ liệu hợp lệ | ACCEPTED; chốt toàn bộ tài nguyên, order ISSUING và ghi acceptedChargeId/acceptedAt |
| ISSUING/COMPLETED/ISSUANCE_FAILED nhưng thiếu accepted charge, hoặc dữ liệu tài nguyên sai | Lỗi dữ liệu; rollback/DLQ, không bịa ACCEPTED/LATE |

Order không tồn tại/config chưa có: giữ lỗi phụ thuộc `CONFIG_NOT_READY` cho broker redelivery hữu hạn theo policy; không tạo order hoặc tự hoàn tiền từ message không đủ dữ liệu. Sai amount/currency/version là lỗi vĩnh viễn cần DLQ/đối soát, không dùng outcome LATE che lỗi.

**Đính chính tên enum:** ghế là `SeatState.PURCHASED`, không phải SOLD như cách giải thích miệng trước đó.

## 3. Một transaction, thứ tự khóa và đồng hồ

Listener gọi đồng bộ `OrderService.acceptPayment` qua Spring proxy; method `@Transactional`, không HTTP/publish RabbitMQ trong DB transaction.

Thứ tự: scalar eventId của order → snapshot event PESSIMISTIC_READ → order PESSIMISTIC_WRITE → đọc inbox → purchase limit (event,buyer) PESSIMISTIC_WRITE → inventory chọn thực sự theo ID tăng dần → promotion PESSIMISTIC_WRITE nếu có. Đọc reservation/allocations sau khóa order; không nạp Order bằng findById trước khi chờ khóa. Reservation/allocations được sửa trong persistence context, JPA dirty checking ghi row đã quản lý; không thêm cascade MERGE hay repository allocations chỉ để save.

- Tái dùng query khóa của createOrder/apply; thứ tự tài nguyên chung không đảo ngược. Sort Java không thay cho ORDER BY trong query lấy khóa.
- Lấy DB `clock_timestamp()` sau các khóa cần thiết rồi mới chốt hạn. Không dùng occurredAt, thời điểm user click hoặc giờ vào listener làm quyết định. Đúng expiresAt là muộn.
- Đề xuất tái dùng property timeout5s và native `set_config(...,true)` hiện có chỉ cho transaction này; lỗi timeout rollback/redelivery, không retry trong transaction hỏng.
- Worker expiry/cancel sau này phải tuân cùng order lock + state guard/thứ tự khóa; spec này không khẳng định worker đó đã tồn tại.

## 4. Cập nhật tài nguyên theo allocation đã lưu

Kiểm reservation thuộc order/event/buyer, hạn khớp; allocations đầy đủ, ID không trùng, khớp OrderItem/frozen snapshot và đều HELD trước lần chốt đầu. Không dựa vào held toàn pool để coi đó là phần giữ của order. Counter thiếu hoặc liên kết sai → rollback nguyên bộ, không dùng max(0) để giấu sai dữ liệu.

| Tài nguyên | ACCEPTED | Release vì LATE/CANCELLED |
| --- | --- | --- |
| QUANTITY / TICKET_TYPE | từng pool type: held-=allocation.quantity, purchased+=quantity | held-=quantity, purchased giữ nguyên |
| STANDING / STANDING_SECTOR | cộng quantity allocations cùng sector rồi chuyển một lần held→purchased | trừ held đúng tổng sector; không trừ thêm pool type |
| SEATED / SEAT | HELD→PURCHASED, giữ currentReservationId và reservationGeneration | HELD→AVAILABLE (UNAVAILABLE nếu hidden), xóa currentReservationId; giữ generation |
| PurchaseLimit của buyer/event | held-=tổng vé, purchased+=tổng vé | held-=tổng vé, purchased giữ nguyên |
| PromotionUsage nếu có | HELD→USED; promotion held--, used++ | HELD→RELEASED; promotion held--, used giữ nguyên |
| Reservation và allocations | HELD→COMMITTED | HELD→RELEASED |
| Order | PENDING_PAYMENT→ISSUING; ghi accepted charge/time | pending→EXPIRED hoặc CANCELLED; không hồi sinh terminal |

Ghế phải HELD và currentReservationId đúng reservation trước khi sửa. Không tăng generation khi commit/release; generation tăng ở lần giữ mới. Không xóa OrderItemSeat vì đó là lịch sử lựa chọn, không phải quyền bán lại. Với reservation đã RELEASED, replay late/cancel không giảm counter lại. Reservation COMMITTED mà order chưa nhận charge là bất nhất, không release.

Accept không đợi Ticket để hết hạn giữ: COMMITTED không thuộc tập HELD được expiry giải phóng. Chưa phát vé vẫn ISSUING; Ticket success rồi Payment báo Booking hoàn tất thuộc task kế tiếp. Lỗi phát vé dứt điểm/bù trừ không thuộc handler này.

## 5. Inbox, outbox và ACK

- ConsumerName cố định `booking.accept-payment`. Receipt `(consumerName,messageId)`, hash SHA-256 của Envelope serialize ổn định bằng JsonMapper sẵn có; bao gồm payload và metadata, không đọc log/string tự ghép. Cùng ID khác hash → MESSAGE_ID_CONFLICT.
- Đọc receipt sau khi khóa order: có cùng hash → trả response đã lưu, không chốt tài nguyên hay thêm outbox. Receipt mới `rowVersion=null`, repository.save để INSERT; PK là bảo vệ cuối khi cùng messageId cố tình trỏ hai order. Tranh unique phải rollback, redelivery mở transaction mới.
- MessageId mới/cùng charge đã nhận: tạo receipt/decision cho command mới nhưng không đổi acceptedAt/counter. Cùng charge có amount/version sai vẫn từ chối, không bỏ kiểm dữ liệu vì replay charge.
- Lưu `PaymentDecision` vào inbox.responsePayload và tạo OutboxMessage qua mapper có sẵn cùng transaction với nghiệp vụ. Response envelope giữ correlationId/traceparent, causationId=messageId đầu vào, aggregateId=orderId, aggregateVersion=version snapshot; decisionAt là thời điểm phân xử. Khi same-message replay giữ nguyên response/outbox. Đây là version snapshot, không phải sequence thứ tự các quyết định.
- Outbox messageId xác định từ `booking.payment-decision:` + incoming.messageId bằng UUID.nameUUIDFromBytes UTF-8. Không merge ghi lại row outbox cũ; receipt replay bỏ qua ghi. Không sửa OutboxMessage/JPA version hoặc schema chỉ cho task này.
- Listener `@RabbitListener(queues="booking.accept-payment.q", ackMode="AUTO", concurrency="1")`; không Channel/tag/manual ACK. AUTO chỉ ACK khi service commit xong và listener kết thúc; crash sau commit trước ACK được redeliver và inbox chặn tác động trùng. Không tuyên bố DB và broker là một ACID transaction.
- INVALID_MESSAGE, MESSAGE_ID_CONFLICT, PAYMENT_SNAPSHOT_MISMATCH, RESERVATION_DATA_CONFLICT → AmqpRejectAndDontRequeueException. Lỗi DB/timeout/commit đi ra ngoài; broker policy hiện có delivery-limit20 + DLQ. Không thêm @Retryable/RetryTemplate chồng broker retry.
- Quyết định LATE/DUPLICATE/CANCELLED là kết quả nghiệp vụ bình thường: lưu outbox + inbox rồi ACK, không ném exception để rollback kết quả cần Payment nhận.

## 6. Class và field đề xuất — tất cả PENDING

Đường dẫn Java dưới `services/booking-service/src/main/java/com/flashticket/booking_service/`; test dưới `src/test/java/com/flashticket/booking_service/`.

| ID | Class/file | NEW/EXISTING | API/annotation/field và mẫu |
| --- | --- | --- | --- |
| C1 | messaging/AcceptPaymentListener.java | NEW | @Component, @RequiredArgsConstructor; final OrderService orderService; đúng hình dạng SalesConfigurationListener, ngoài luồng HTTP Shop App |
| C2 | order/service/OrderService.java | EXISTING | thêm chữ ký M2; không tạo cặp service mới |
| C3 | order/service/impl/OrderServiceImpl.java | EXISTING | @Service/@RequiredArgsConstructor; thêm final InboxMessageRepository, OutboxMessageRepository, OutboxMessageMapper, PromotionInventoryRepository; giữ các dependency hiện có, JsonMapper, Validator và @Value lock-timeout |
| C4 | order/repository/OrderRepository.java | EXISTING | thêm scalar JPQL và derived query khóa, không EntityManager/native mới |
| C5 | exception/ErrorCode.java | EXISTING | thêm PAYMENT_SNAPSHOT_MISMATCH và RESERVATION_DATA_CONFLICT với message cố định, HTTP409; không type exception mới, không constructor mới |
| C6 | messaging/AcceptPaymentListenerTests.java | NEW | JUnit/Mockito @ExtendWith(MockitoExtension.class); @Mock OrderService; @InjectMocks listener; không Spring test configuration/bean/helper mới |
| C7 | persistence/BookingPersistenceIT.java | EXISTING | thêm D1–D12; dùng context/fixtures/helpers đã có, không nested class/new callback method/helper mới |

API Lombok: C1 sinh `public AcceptPaymentListener(OrderService orderService)`. C3 constructor hiện có được sinh lại với toàn bộ final field hiện tại cộng đúng bốn dependency trên; không thêm getter/setter/builder. C6 constructor mặc định, không Lombok/API sinh khác. DTO/entity/mapper và getter/setter hiện có chỉ REUSE; không thêm type/cột/index/dependency.

## 7. Toàn bộ method đề xuất — PENDING

| ID | Class | Chữ ký / annotation | NEW/REUSE |
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
| R1 | Repository/mapper/JSON | findForBookingByEventId, currentDatabaseTime, configureTransactionLockTimeout, findByOrderId, findLockedById(PurchaseLimitId), các query khóa inventory hiện có, findLockedByPromotionId, findByReservationIdAndStateIn, save/saveAll, OutboxMessageMapper.toEntity, JsonMapper đọc/ghi response, Validator.validate | REUSE; không đổi annotation/signature |

M8 lấy mọi khóa bằng repository trước khi lấy DB clock/chọn outcome, rồi truyền entity đã khóa vào M9–M13; không trừ counter trong lúc còn chờ khóa khác. Không @Transactional ở private helper; chúng chạy trong transaction M3. targetState không nhận tùy ý từ message mà được service chọn, không dựng framework transition. Không bắt exception rồi return thành công. Không đổi createOrder/applyPromotion/preparePurchaseSnapshot ngoài nhu cầu regression.

## 8. Test method đề xuất — tất cả NEW/PENDING

Tất cả method test package-private `void <tên>()`; D1–D12 thêm `throws Exception` và @Test/@Transactional(NOT_SUPPORTED) như IT hiện có. Fixture PostgreSQL cách ly; không tự sửa DB119 để chứng minh rollback/race. Không thêm helper ngoài danh sách; phát sinh chữ ký cần duyệt delta.

| ID | Class | Tên method | Bằng chứng cần |
| --- | --- | --- | --- |
| U1 | AcceptPaymentListenerTests | `delegatesAcceptPaymentToTransactionalService()` | gọi service đồng bộ |
| U2 | AcceptPaymentListenerTests | `rejectsPermanentAcceptPaymentErrorsWithoutRequeue()` | nhóm lỗi dữ liệu §5 |
| U3 | AcceptPaymentListenerTests | `propagatesDatabaseAndCommitFailures()` | không ACK thành công giả |
| D1 | BookingPersistenceIT | `acceptsPaymentForEveryInventoryMode()` | QUANTITY/STANDING/SEATED, counter/allocations/limit/ISSUING |
| D2 | BookingPersistenceIT | `commitsPromotionUsageWithAcceptedPayment()` | HELD→USED, counter một lần |
| D3 | BookingPersistenceIT | `replaysAcceptPaymentWithoutChangingResources()` | receipt/hash/outbox; cùng charge/new message; replay qua hạn |
| D4 | BookingPersistenceIT | `rejectsConflictingAcceptPaymentMessages()` | cùng messageId khác hash/order, concurrent PK race |
| D5 | BookingPersistenceIT | `returnsDuplicateForAnotherAcceptedCharge()` | hai charge cạnh tranh, chỉ một accept |
| D6 | BookingPersistenceIT | `releasesExpiredReservationWhenPaymentArrivesLate()` | LATE, release cả 3 mode/promo/limit đúng một lần |
| D7 | BookingPersistenceIT | `rejectsPaymentForCancelledEventOrOrder()` | CANCELLED không hồi sinh; release HELD/replay |
| D8 | BookingPersistenceIT | `rejectsMismatchedFrozenPaymentSnapshot()` | chưa freeze/sai amount/currency/version, không đổi tài nguyên |
| D9 | BookingPersistenceIT | `rollsBackAcceptPaymentWhenPersistenceFails()` | lỗi constraint/commit rollback nghiệp vụ/inbox/outbox |
| D10 | BookingPersistenceIT | `checksPaymentDeadlineAfterInventoryLockWait()` | deadline sau chờ, đúng hạn là late; timeout5s transaction-local |
| D11 | BookingPersistenceIT | `rejectsInconsistentReservationResources()` | allocations/counter/seat owner sai không bán hoặc release nhầm |
| D12 | BookingPersistenceIT | `acknowledgesAcceptPaymentOnlyAfterDatabaseCommit()` | broker thật; commit, redelivery/inbox, permanent→DLQ; không giả Payment nhận outbox |

Không tuyên bố đã test trong lượt thiết kế. Nếu test cần setup/helper mới phải liệt kê tại pha plan và xin duyệt; tên method trên không cấp quyền tạo helper ẩn.

## 9. Căn cứ và khác Shop App

- Source mẫu đã đọc: `C:/Users/sondimid/IdeaProjects/shopapp-backend/shopapp-backend/shopapp/src/main/java/com/project/shopapp/services/impl/OrderService.java`, updateOrder: repository→orElseThrow→setter→save; giữ service cụ thể, constructor injection, exception hiện có. Không kéo ModelMapper vào nghiệp vụ chuyển counter.
- Source đích: AcceptPayment/PaymentDecision, Envelope, Order/Reservation/Allocation/Seat/PromotionUsage, inbox/outbox/mapper, các repository khóa và SalesConfigurationListener; B13 §4/5, B14 §3 B1–B2, ảnh đã lưu `docs/evidence/use-case-diagrams/2026-10-29-clickup-booking-flow.md`.
- [Spring Data JPA query methods](https://docs.spring.io/spring-data/jpa/reference/jpa/query-methods.html): scalar JPQL cho lookup, derived query + @Lock cho entity; tái dùng queries khóa đã có.
- [Spring AMQP container AUTO](https://docs.spring.io/spring-amqp/reference/amqp/containerAttributes.html) và [source Spring AMQP trên GitHub](https://github.com/spring-projects/spring-amqp/blob/main/spring-rabbit/src/main/java/org/springframework/amqp/rabbit/listener/SimpleMessageListenerContainer.java): đối chiếu ACK sau listener/exception; dùng mẫu listener hiện có, không copy cấu hình transaction manager/channelTransacted từ ví dụ khác rồi gọi DB+broker là XA.
- Khác luồng Shop App đã đọc: consumer AUTO, inbox/outbox, DB clock và khóa cạnh tranh, typed snapshot/Envelope, Validator trong service. Đây là bảo vệ luồng message/thanh toán, không phải lý do tạo abstraction mới. Tái dùng schema/cơ chế hiện có; không migration/native upsert/EntityManager/Redis lock.

## 10. Gate BRAINSTORMING

- Danh sách xin duyệt v0.2: **C1–C12, M1–M20, U1–U5, D1–D17**, gồm constructor/API sinh và annotation được nêu rõ. Tất cả khai báo **PENDING**; chưa có xác nhận danh sách v0.1 hay v0.2.
- SCOPE-01 về relay đã chốt; lựa chọn phạm vi không tự duyệt class/method. Phần bổ sung ở §11 cũng phải được duyệt cùng danh sách.
- Sau xác nhận ghi nguồn và phạm vi thật. Chỉ lập plan khi user yêu cầu; không chuyển sang implementation trong lượt này.

## 11. Worker outbox trong cùng task — delta v0.2 / PENDING khai báo

### Luồng tối giản

1. Scheduler mỗi 2s lấy tối đa 20 row `booking.payment-decision.v1` chưa published, nextAttemptAt đã đến, theo occurredAt/messageId. Chỉ xử lý loại message này; không tự gửi các outbox khác chưa được tích hợp. Scheduler không mở transaction nghiệp vụ.
2. Mỗi ID gọi `MessagingService.publishPaymentDecision(UUID)` qua Spring proxy, **một transaction relay riêng cho một row**. Khóa ghi row outbox, kiểm lại unpublished/due/type; row đã gửi thì bỏ qua. Tái dùng timeout khóa 5s từ OrderRepository.configureTransactionLockTimeout và property đã có.
3. Dùng OutboxMessageMapper.toEnvelope(row, PaymentDecision.class), Jackson converter sẵn có, RabbitTemplate.send tới exchange property hiện tại (`business.ex`), routing key `booking.payment-decision.v1`. Tạo Message bằng converter, đặt deliveryMode=PERSISTENT, AMQP messageId bằng envelope.messageId, correlationId tương ứng. Không gửi raw data thiếu Envelope.
4. Dùng `CorrelationData` mới cho từng lần gửi, correlation token UUID riêng; **envelope.messageId giữ nguyên qua retry**. Gửi rồi chờ `getFuture().get(confirmTimeout.toMillis(), MILLISECONDS)`; thành công chỉ khi confirm ACK và `getReturned()==null`. Không cần thêm callback/anonymous class hay global ConfirmCallback.
5. Khi thành công: ghi publishedAt, xóa lastErrorCode rồi save. Chỉ khi transaction này commit thì coi row published bền. Broker confirm không chứng minh consumer Payment xử lý xong.
6. Khi nack/return/timeout/kết nối lỗi: tăng attempts (không tràn int), để publishedAt null, ghi lastErrorCode cố định phù hợp, nextAttemptAt=now+retryDelay rồi save và commit metadata retry. Không lưu token/payload hay exception message tùy ý vào lastErrorCode. InterruptedException khôi phục interrupt flag và kết thúc vòng batch; không giả gửi thành công.
7. Lỗi DB/commit không được nuốt thành published thành công. Scheduler bắt RuntimeException từng row để log mã lỗi và chuyển row khác; nếu cập nhật retry cũng rollback, row vẫn pending cho lượt sau. Không dùng @Retryable; fixed delay5s ban đầu đủ, không thêm backoff framework hay retry tối đa rồi tự xóa quyết định.

Lựa chọn đơn giản có đánh đổi rõ: relay giữ **khóa duy nhất row outbox và một DB connection** trong lúc chờ broker, tối đa confirm-timeout5s (connect/send timeout của Rabbit connection phải hữu hạn và được kiểm khi mất broker). Không giữ khóa order/inventory, không gửi Rabbit trong transaction acceptPayment. Với pool hiện tại5 connection và một scheduler tuần tự, đây là baseline để đo, không tuyên bố chịu tải lớn. Nhiều replica tranh row theo khóa, chưa thêm lease/claim column/Redis leader/SKIP LOCKED. Lỗi/đợi khóa relay không rollback đơn đã được chấp nhận.

Crash sau broker ACK nhưng trước publishedAt commit → relay gửi lại **cùng envelope/messageId**, vì vậy **at-least-once**; Payment phải có inbox/idempotency. Không hứa exactly-once, không tạo messageId mới mỗi lần retry. Outbox hiện không @Version nhưng relay cập nhật entity đã đọc bằng khóa, không merge object cũ sau khi bỏ khóa.

### Cấu hình đề xuất

Trong booking-service.yaml: `booking.outbox.enabled=true`, `poll-interval=2s` (Scheduled fixedDelay dùng dạng ISO PT2S nếu framework yêu cầu), `batch-size=20`, `confirm-timeout=PT5S`, `retry-delay=PT5S`. @Value field trên bean cho size/timeout/delay/exchange; @Scheduled đọc poll-interval; enabled dùng @ConditionalOnProperty. Mặc định poll-interval đề xuất **PT2S** để thống nhất cú pháp, không ghi cả hai giá trị vào YAML.

Giữ correlated confirms, publisher-returns=true, mandatory=true hiện có; đề xuất cấu hình `spring.rabbitmq.connection-timeout: 5s` để connect hữu hạn. Confirm-timeout không tự bao trùm mọi thời gian connect/send, nên phải kiểm thời gian thực khi mất broker; không tuyên bố toàn bộ method luôn <=5s. Không đổi topology đã có trong deploy/rabbitmq/definitions.json; test xác nhận binding tới Payment queue. Bật relay không tự provision queue hoặc xác thực consumer Payment.

### Class/constructor bổ sung

| ID | Class/file | NEW/EXISTING | Field/annotation/API sinh |
| --- | --- | --- | --- |
| C8 | messaging/PaymentDecisionOutboxWorker.java | NEW | @Component/@RequiredArgsConstructor/@ConditionalOnProperty(prefix="booking.outbox", name="enabled", havingValue="true", matchIfMissing=true); final OutboxMessageRepository, MessagingService; @Value int batchSize. Lombok sinh `public PaymentDecisionOutboxWorker(OutboxMessageRepository outboxMessageRepository, MessagingService messagingService)` |
| C9 | messaging/service/MessagingService.java | EXISTING | thêm M17, tái dùng interface hiện có |
| C10 | messaging/service/impl/MessagingServiceImpl.java | EXISTING | thêm final RabbitTemplate, OrderRepository cho DB clock/timeout; giữ final InboxMessageRepository, OutboxMessageRepository, OutboxMessageMapper; constructor Lombok sinh với năm dependency. @Value Duration confirmTimeout/retryDelay, String exchange/lockTimeout; @Transactional trên method public |
| C11 | messaging/repository/OutboxMessageRepository.java | EXISTING | thêm M19–M20; derived candidate query + @Lock, không native query/schema mới |
| C12 | BookingServiceApplication.java | EXISTING | thêm @EnableScheduling, main giữ nguyên; khác luồng mẫu Shop App đã đọc |

### Method bổ sung — NEW/PENDING

| ID | Class | Chữ ký đầy đủ và annotation |
| --- | --- | --- |
| M15 | PaymentDecisionOutboxWorker | `public void publishDuePaymentDecisions()`; @Scheduled(fixedDelayString="${booking.outbox.poll-interval:PT2S}") |
| M16 | PaymentDecisionOutboxWorker | constructor Lombok đã nêu C8, không constructor viết tay |
| M17 | MessagingService | `void publishPaymentDecision(UUID messageId)` |
| M18 | MessagingServiceImpl | `public void publishPaymentDecision(UUID messageId)`; @Override/@Transactional; send + wait confirm + save metadata bên trong cùng transaction relay riêng |
| M19 | OutboxMessageRepository | `List<OutboxMessage> findByMessageTypeAndPublishedAtIsNullAndNextAttemptAtLessThanEqualOrderByOccurredAtAscMessageIdAsc(String messageType, Instant now, Pageable pageable)` |
| M20 | OutboxMessageRepository | `Optional<OutboxMessage> findLockedByMessageId(UUID messageId)`; @Lock(PESSIMISTIC_WRITE) |

M18 dựng Message qua converter/RabbitTemplate API có sẵn, không MessagePostProcessor callback mới; chỉ exception kỹ thuật gửi được xử lý retry bên trong. DTO invalid ở outbox giữ pending với INVALID_MESSAGE và báo lỗi để sửa dữ liệu, không đánh dấu published hoặc chặn batch vĩnh viễn. Thời gian poll/retry có thể dùng Instant.now vì là lịch thử lại; decisionAt tài chính vẫn dùng DB clock như §3.

### Test bổ sung — NEW/PENDING

Giữ lớp test đã liệt kê, không thêm test class/helper. U4–U5 nằm trong AcceptPaymentListenerTests (JUnit method package-private void, không tham số); bổ sung @Mock OutboxMessageRepository/MessagingService để khởi tạo worker bằng constructor đã duyệt. C7 bổ sung @Import MessagingServiceImpl và OutboxMessageMapperImpl hiện có vào IT context; không bật scheduler trong IT mà gọi worker trực tiếp để kiểm chứng chủ động. D13–D17 nằm BookingPersistenceIT với @Test/@Transactional(NOT_SUPPORTED), chữ ký `void <tên>() throws Exception`.

| ID | Method | Điều kiện đạt |
| --- | --- | --- |
| U4 | `publishesOnlyDuePaymentDecisionOutboxIds()` | lọc loại/due/batch, delegate từng ID |
| U5 | `continuesOutboxBatchAfterOneRowFails()` | lỗi một row không mất phần còn lại; interrupt dừng vòng |
| D13 | `publishesPaymentDecisionWithConfirmAndPersistentEnvelope()` | PostgreSQL + Rabbit thật; Payment-bound test queue nhận envelope đúng, persistent/messageId/causation, publishedAt chỉ sau confirm |
| D14 | `retriesUnconfirmedOrReturnedPaymentDecision()` | nack/return/confirm-timeout/broker-down không published, attempts/nextAttemptAt; confirm mô phỏng cần ghi rõ phần nào mock |
| D15 | `replaysPublishedButUncommittedOutboxMessageWithSameId()` | rollback DB sau gửi broker; gửi lại cùng ID/body, receiver idempotent không tác động lần hai |
| D16 | `serializesConcurrentOutboxPublishersAndSkipsPublishedRows()` | hai worker cùng row không đè metadata; row committed published không gửi lại |
| D17 | `deliversAcceptPaymentDecisionThroughRealRabbitMq()` | command→consumer→DB commit→relay→Payment-bound queue; ACCEPTED/LATE/DUPLICATE/CANCELLED và restart backlog; phân biệt queue nhận với Payment service đã xử lý |

Nghiệm thu live Payment: chạy consumer Payment của branch phụ trách, verify inbox/handler của Payment thực sự nhận và xử lý quyết định. Nếu chưa có source/runtime đó, báo dependency chưa sẵn sàng, không dựng consumer giả rồi gọi là E2E Payment đạt. Không sửa code branch Payment trong task Booking.

Nguồn đối chiếu: [Spring AMQP Sending Messages](https://docs.spring.io/spring-amqp/reference/amqp/sending-messages.html), [sample chính thức confirms/returns](https://github.com/spring-projects/spring-amqp-samples/blob/main/spring-rabbit-confirms-returns/src/main/java/org/springframework/amqp/samples/confirms/SpringRabbitConfirmsReturnsApplication.java). Áp dụng CorrelationData future + returned check, không kéo thêm Spring Integration/dependency/CDC. API confirm theo version dependency thật khi lập plan (AMQP4.x có thể dùng record accessor khác sample cũ).
