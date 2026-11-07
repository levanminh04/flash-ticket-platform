# Booking — Nhận kết quả phát vé COMPLETED / FAILED

- Phiên bản: v0.4; tài liệu thiết kế **BRAINSTORMING**. Các nhãn PENDING giữ lịch sử giai đoạn thiết kế; user sau đó đã duyệt riêng EXECUTING-PLANS cho toàn bộ danh sách. Phần triển khai và kết quả kiểm chứng ghi trong [plan §11](../plans/2026-10-29-booking-issuance-result-plan.md); không ghi duyệt hồi tố BRAINSTORMING.
- Task: [Nhận thông báo phát hành vé, cập nhật order COMPLETED và ACK](https://app.clickup.com/t/z8qgvc7w2u), bước 47–51 của ảnh Đặt vé. Đã đọc lại task qua ClickUp.
- Scope được user sửa: **“làm cả 2 nhánh đi”** — xử lý cả COMPLETED và FAILED. Thay lựa chọn chỉ COMPLETED trước đó. Đây là duyệt phạm vi nghiệp vụ, chưa duyệt khai báo Java.
- USER_CONFIRMED v0.2: **“nếu v t nghĩ k cần check issuanceId”**. Booking không lưu/đối chiếu đợt phát vé; bỏ đề xuất thêm issuanceResult/issuanceProcessedAt hoặc bốn cột thay thế trên Order. issuanceId vẫn được nhận và truyền vào OrderCompleted.
- USER_CONFIRMED về nghiệp vụ ticketCount: user trả lời mục 1 **“có thể check cũng đc”** — giữ kiểm tra số vé theo B13/snapshot.
- USER_CONFIRMED v0.3 về FAILED: **“cứ đưa về. còn khi đặt thì check event rồi nên ko lo”** — trả inventory/purchase limit/promotion đúng một lần kể cả event hết giờ bán hoặc CANCELLED; createOrder vẫn kiểm trạng thái và sale window. Thay điều kiện không trả nguồn cung ở bản nháp trước/B14 §3 cũ, không mở rộng thành quy tắc cho mọi luồng hủy. Danh sách khai báo Java vẫn PENDING.
- USER_CONFIRMED v0.4: **“vậy dùng 2 queue, sửa plan, spec”**. Dùng hai queue hiện có trên119: booking.tickets-issued.q và booking.issuance-failed.q, mỗi queue có DLQ riêng. Dùng binding/key hiện có trong bảng §3; không tạo queue chung. Nguồn gửi nghiệp vụ vẫn Payment → Booking. Thay đổi khai báo listener/contract được liệt kê PENDING trong bảng, không tự triển khai Java/broker.
- Checkout khảo sát: `D:/DATN/flash-ticket-platform`, branch `dev/son`, HEAD `d2bb7b0`. Lượt này chỉ lập spec, không tạo branch, sửa Java, chạy test hoặc deploy.

## 1. Mục tiêu và phần tái sử dụng

Flow: Ticket phát kết quả → **Payment** ghi nhận/chuyển tiếp theo hai routing key ở §3 → Booking nhận ở hai queue riêng, cập nhật trạng thái/tài nguyên + inbox/outbox cùng transaction → AUTO ACK. Với COMPLETED, worker gửi `booking.order-completed.v1` để Payment kết thúc Saga.

- Tái sử dụng `Envelope`, DTO `OrderCompleted`, Spring/Jackson converter, `Validator`, `OrderService/Impl`, repository, inbox/outbox, mapper và relay hiện có.
- Không thêm API HTTP, service framework, dependency, EntityManager, scheduler thứ hai, logic phát vé hoặc refund. Refund/terminal fence của Ticket do Payment/Ticket phụ trách; timeout không phải bằng chứng FAILED.
- Hình dạng code theo Shop App `services/impl/OrderService.updateOrder`: constructor injection, lấy entity → kiểm tra → setter → repository.save. Property giữ `@Value` trên field. Ngoại lệ theo nhu cầu hiện có: listener AUTO ACK, khóa pessimistic, inbox/outbox và DTO record đã tồn tại.

## 2. Các điểm lệch cần sửa trong phạm vi task

| Nguồn hiện tại | Thực trạng | Đề xuất PENDING |
| --- | --- | --- |
| `BookingIssuanceResult` | Chỉ FAILED, không có ticketCount | Mở rộng theo B13: cả COMPLETED/FAILED, thêm ticketCount, validation liên field |
| B13 §5.2 | Đoạn cũ ghi Booking nhận success trực tiếp Ticket; đoạn GOV-156 phía sau sửa sang Payment | Sửa đoạn mâu thuẫn; nguồn gửi hiện hành là Payment |
| Broker119 và definitions | Có booking.tickets-issued.q/key ticket.issued.v1 và booking.issuance-failed.q/key booking.issuance-failed.v1, kèm DLQ | Tái sử dụng hai queue/binding; đối chiếu policy/quyền. Không thêm queue chung hoặc bind chéo |
| ContractRules/B13 | Hiện map một type chung booking.issuance-result.v1 | Đổi sang hai messageType bằng routing key §3, cùng payload BookingIssuanceResult. Cập nhật producer Payment/fixture; không xử lý song song type chung chưa triển khai |
| Relay hiện có | Chỉ `booking.payment-decision.v1` | Mở rộng đúng hai type: PaymentDecision và OrderCompleted; đổi tên worker/method cho đúng vai trò, giữ cơ chế gửi đã kiểm thử |
| Order | Có acceptedChargeId, lifecycleState và frozenPurchaseSnapshot | Tái sử dụng để kiểm charge, chuyển trạng thái và chống lặp hiệu ứng; không thêm field issuance |
| B14 §3 nhánh FAILED | Trước đây chỉ trả inventory/hạn mức khi còn điều kiện bán | User chốt v0.3 trả phần đã chốt một lần cả khi hết bán/hủy; sửa mô tả FAILED, giữ gate event tại createOrder |

Không suy cập nhật definitions là broker119 đã đổi. Trước chạy live phải kiểm quyền publish/read đúng queue và đồng bộ producer Payment; triển khai runtime là bước riêng được user giao sau.

## 3. Contract vào và ra

Đầu vào hai queue đều là `Envelope<BookingIssuanceResult>`, schemaVersion=1; aggregateId=orderId. `aggregateVersion` đề xuất giữ version PurchaseSnapshot đã freeze (`sourceOrderVersion`), không dùng rowVersion JPA đang tăng; producer Payment phải gửi đúng convention này.

| Nhánh | Queue Booking | messageType = routing key tại business.ex | DLQ |
| --- | --- | --- | --- |
| COMPLETED | booking.tickets-issued.q | ticket.issued.v1 | booking.tickets-issued.dlq |
| FAILED | booking.issuance-failed.q | booking.issuance-failed.v1 | booking.issuance-failed.dlq |

Giữ nguyên key đang bind để tái sử dụng topology. Tên ticket.issued.v1 là tên contract/key hiện có, không đổi flow về Ticket gửi trực tiếp Booking; Payment chuyển tiếp bằng key này sau khi ghi nhận result. Không suy publisher identity từ tên key: trước nghiệm thu live phải đối chiếu producer/quyền thực tế, nhất là khi các service đang dùng chung credential.

M2 và M23 là hai method @RabbitListener trong cùng class; kiểm messageType/state đúng nhánh trước khi gọi cùng OrderService. Service cũng kiểm hai cặp type/state, không coi chọn nhầm queue là kết quả hợp lệ. Không dùng header custom hoặc sửa converter để suy queue từ payload. Type chung booking.issuance-result.v1 và queue chung trong bản nháp trước không còn được triển khai.

Payload gồm `orderId`, `chargeId`, `issuanceId` (UUID không null), `state`, `ticketCount`, `failureCode`:

- COMPLETED: ticketCount > 0, failureCode=null; ticketCount bằng tổng quantity trong frozen PurchaseSnapshot.
- FAILED: ticketCount=0, failureCode không rỗng; đây là kết quả thất bại dứt điểm do Ticket ghi terminal, Payment chuyển tiếp. Không chuyển timeout/retry exhausted thành FAILED.
- Cả hai phải khớp acceptedChargeId và snapshot/order đã được Booking nhận thanh toán. Không truy DB Ticket/Payment hoặc tự đoán trạng thái từ timeout.
- issuanceId chỉ kiểm đúng kiểu/không null theo contract rồi truyền lại trong xác nhận; không so với lần xử lý trước. Hai message ID mới khác issuanceId nhưng cùng charge/kết quả hợp lệ không bị từ chối vì issuanceId. Đây là giới hạn đã chọn; Ticket chịu trách nhiệm unique order/idempotency phát vé, Payment kiểm liên kết issuance của Saga.

Đầu ra thành công `Envelope<OrderCompleted>`: orderId, chargeId, issuanceId, ticketCount. response messageId tạo cố định từ `booking.order-completed:` + input messageId; correlationId giữ nguyên, causationId=input messageId, aggregateVersion giữ version snapshot. FAILED không phát OrderCompleted; AUTO ACK sau commit là xác nhận giao message, không có event giả “thành công”. Task không thêm contract xác nhận FAILED mới.

## 4. Transaction nhận kết quả

1. Validator kiểm envelope/payload, cặp messageType/state theo §3 và quan hệ ID/version. Hash toàn bộ envelope bằng Jackson + SHA-256 giống AcceptPayment.
2. Cấu hình lock_timeout=5s trong transaction. Lấy eventId → khóa READ snapshot → khóa WRITE order; sau khi chờ mới đọc inbox `(booking.issuance-result, messageId)`. Giữ consumerName nghiệp vụ chung này cho cả hai queue, không phải tên queue: cùng messageId bị dùng lại qua nhánh khác vẫn bị so hash/chặn xung đột.
3. Cùng messageId/cùng hash: return, không cập nhật hoặc thêm outbox. Cùng ID/khác hash: MESSAGE_ID_CONFLICT. PK inbox và `@Version null` cho receipt mới vẫn là bảo vệ cuối, lỗi commit rollback toàn bộ.
4. Order chưa có hoặc đang PENDING_PAYMENT chưa nhận charge: lỗi phụ thuộc `ISSUANCE_RESULT_NOT_READY`, bounded redelivery. Charge khác charge đã nhận, snapshot/version/count sai: `ISSUANCE_RESULT_CONFLICT`, reject/DLQ. Order EXPIRED hoặc trạng thái khác không hợp lệ không được hồi sinh.
5. Kiểm trạng thái đã lưu: COMPLETED nhận COMPLETED hợp lệ là replay; ISSUANCE_FAILED nhận FAILED hợp lệ là replay. COMPLETED nhận FAILED hoặc ISSUANCE_FAILED nhận COMPLETED là conflict. CANCELLED xử lý riêng, không hồi sinh. Không so issuanceId/failureCode với kết quả ở một message ID khác; cùng message ID khác bất kỳ nội dung nào vẫn bị hash guard chặn.
6. Với kết quả mới hợp lệ, xử lý §5 hoặc §6; cập nhật lifecycleState/updatedAt bằng giờ DB khi thực sự đổi order. Lưu inbox và outbox nếu có cùng transaction. Không lưu payload issuance trên Order và không gọi broker trong transaction này.

Kết quả đến sau expiresAt vẫn có thể hoàn tất vì AcceptPayment đã nhận và chốt tài nguyên trước đó; không xét lại hạn giữ để đổi một charge được nhận thành LATE. Nếu chưa nhận charge thì không dùng issuance-result để tự accept.

## 5. Nhánh COMPLETED

- ISSUING + charge/snapshot/count hợp lệ + event chưa CANCELLED: order → COMPLETED; tạo outbox OrderCompleted.
- Không đổi inventory/purchase limit/promotion/reservation: chúng đã chốt trong AcceptPayment.
- Message ID mới báo COMPLETED hợp lệ trên order COMPLETED: ghi inbox và thêm response outbox gắn causation mới, truyền issuanceId từ message mới; không đổi updatedAt của order, không tác động tài nguyên. Booking không chứng minh đợt phát vé giống lần trước; Payment phải tự kiểm trước khi kết thúc Saga. Redelivery cùng message ID không thêm response.
- Order đã CANCELLED hoặc snapshot event CANCELLED: ghi receipt để dừng redelivery, giữ trạng thái hủy (nếu cần đưa ISSUING về CANCELLED), không phát OrderCompleted, không hồi sinh. Không thực hiện fan-out hủy/refund của task khác.
- Cổng hủy ở Payment vẫn bắt buộc: OrderCompleted đã ở outbox trước khi event bị hủy có thể đến Payment muộn; task Booking này không tự ngăn mọi race ngoài service.

## 6. Nhánh FAILED và hoàn trả đúng một lần

Chỉ xử lý từ order đã nhận thanh toán. Nếu ISSUING thì trả tài nguyên đã chốt và chuyển ISSUANCE_FAILED cùng transaction; FAILED lặp trên ISSUANCE_FAILED chỉ ghi inbox, không chạy release lại. Nếu event/order đã hủy thì giữ/đưa về CANCELLED nhưng vẫn trả phần đã chốt chưa được trả. Order COMPLETED không bị hạ xuống FAILED. Trạng thái order/reservation/allocation/usage cùng inbox làm guard, không cần field issuance mới.

Khóa theo cùng thứ tự AcceptPayment: snapshot READ → order WRITE → purchase limit WRITE → inventory WRITE theo ID → promotion WRITE. Đọc giờ DB sau các bước có thể chờ khóa để ghi thời điểm xử lý.

**Quy tắc đã được user chốt v0.3:** FAILED dứt điểm trả phần purchased/used đúng một lần, không phụ thuộc eventStatus, sale window hoặc expiresAt cũ của order. Việc inventory được trả không có nghĩa event đã mở bán: createOrder tiếp tục yêu cầu event PUBLISHED và sale window hợp lệ, kiểm ghế hidden/state như hiện tại.

| Dữ liệu đang chốt | FAILED, kể cả event hết bán / bị hủy |
| --- | --- |
| QUANTITY | purchased của đúng ticket type giảm quantity |
| STANDING | purchased của pool sector giảm tổng quantity |
| SEATED | PURCHASED → AVAILABLE, hoặc UNAVAILABLE nếu hidden; xóa currentReservationId |
| Purchase limit | purchased của đúng buyer/event giảm quantity |
| Promotion | USED → RELEASED, used giảm 1 |
| Reservation/allocations | COMMITTED → RELEASED cùng transaction trả inventory/hạn mức |

- Không trừ held: AcceptPayment đã chuyển held sang purchased/used. Không gọi lại helper chỉ dành HELD trong task cũ.
- Chỉ trả phần allocation COMMITTED đúng order/reservation; kiểm tổng quantity theo item/snapshot, ownership event/type/sector và counter đủ trước khi giảm. RELEASED không được giảm counter lần hai; trạng thái không nhất quán rollback.
- Ghế chỉ được đổi khi state PURCHASED và currentReservationId đúng reservation đang xử lý, reservationGeneration > 0, đúng seat/type/sector. Allocation hiện không lưu generation riêng; không tuyên bố có so generation hai bản. Khóa row và reservation UUID không tái sử dụng ngăn release R1 đụng ghế R2; nếu không còn đúng ownership thì conflict, không sửa ghế R2.
- Promotion phải khớp snapshot/usage, USED mới giảm used; RELEASED không giảm tiếp. Guard counter âm và Math.*Exact như phần cũ.
- Event hết bán/hủy không trì hoãn release sang scheduler khác. Message lặp không trả lần hai; trường hợp luồng hủy đã release trước thì bỏ qua phần RELEASED, không trừ thêm và không đổi CANCELLED thành ISSUANCE_FAILED. FAILED sau COMPLETED vẫn conflict vì không phải bằng chứng để thu hồi vé đã phát.

## 7. Relay và retry/DLQ

- Đổi worker thành `BookingOutboxWorker`, vẫn một scheduler, cùng cấu hình batch/poll/timeout. Query theo `messageType IN` hai loại được hỗ trợ, giới hạn batch tổng, giữ thứ tự occurredAt/messageId.
- Đổi method relay thành `publishBookingMessage(UUID)`. Khóa WRITE row outbox rồi kiểm lại publishedAt/nextAttemptAt/type; chọn DTO từ contract map hiện có. Không mở relay cho mọi type.
- Giữ persistent message, mandatory + returns, correlated confirm. ACK/no-return mới lưu publishedAt; lỗi hẹn retry; crash publish trước commit vẫn at-least-once, Payment dedupe.
- Hai listener method đều AUTO/concurrency1, không Channel/tag; có thể chạy đồng thời, cùng khóa order phân xử hai nhánh. INVALID_MESSAGE (gồm sai cặp queue/type/state), MESSAGE_ID_CONFLICT, ISSUANCE_RESULT_CONFLICT, RESERVATION_DATA_CONFLICT → reject không requeue → DLQ riêng của queue nhận. NOT_READY/DB/commit → lỗi đi ra container, bounded broker redelivery; không tự thêm @Retryable. Không bảo đảm thứ tự giữa hai queue, không chọn kết quả theo thứ tự broker.

## 8. Bảng duyệt class / phong cách — tất cả PENDING

Đường dẫn Java dưới `services/booking-service/src/{main,test}/java/com/flashticket/booking_service/`.

| ID | Class/file | NEW / EXISTING | Vai trò, annotation, API sinh | Mẫu / ngoại lệ |
| --- | --- | --- | --- | --- |
| C1 | main/messaging/BookingIssuanceResultListener.java | NEW | @Component, @RequiredArgsConstructor; final OrderService; constructor M1; hai @RabbitListener M2/M23 | Như listener AcceptPayment hiện có; bean theo Shop App |
| C2 | main/order/service/OrderService.java và impl/OrderServiceImpl.java | EXISTING | Thêm method nghiệp vụ/helper §9; @Override/@Transactional trên public impl; giữ dependency constructor hiện tại | Shop App updateOrder + TX/inbox/outbox hiện có |
| C3 | main/dto/message/BookingIssuanceResult.java, nested State | EXISTING | Thêm COMPLETED và `@NotNull @PositiveOrZero Integer ticketCount`; failureCode nullable; @AssertTrue/@JsonIgnore trên isConsistent; constructor/accessor record đổi §9 | Record đã có, không tạo DTO/style mới |
| C4 | main/entity/Order.java | REUSE UNCHANGED | Dùng acceptedChargeId, acceptedAt, lifecycleState, frozenPurchaseSnapshot, paymentSnapshotVersion, updatedAt và @Version hiện có; không thêm field/API | Bỏ đề xuất JSON/timestamp theo lựa chọn v0.2 |
| C5 | main/messaging/BookingOutboxWorker.java | RENAME EXISTING | PaymentDecisionOutboxWorker đổi tên; @Component/@RequiredArgsConstructor/@ConditionalOnProperty/@Scheduled giữ như cũ, API §9 | Mở rộng đúng 2 loại, không framework registry |
| C6 | main/messaging/service/MessagingService.java, impl/MessagingServiceImpl.java | EXISTING | Đổi method relay §9, @Transactional giữ; constructor/field @Value không đổi | Cơ chế relay vừa triển khai |
| C7 | main/messaging/repository/OutboxMessageRepository.java | EXISTING | Đổi query method sang MessageTypeIn §9; giữ @Lock trên lookup ID | Spring Data derived query |
| C8 | main/exception/ErrorCode.java | EXISTING | Thêm ISSUANCE_RESULT_NOT_READY và ISSUANCE_RESULT_CONFLICT; không method/constructor mới | ErrorCode cụ thể theo convention |
| C9 | test/messaging/BookingIssuanceResultListenerTests.java | NEW | @ExtendWith(MockitoExtension.class); @Mock OrderService, @InjectMocks listener; constructor noarg ngầm; U1–U3 | Mẫu AcceptPaymentListenerTests |
| C10 | test/persistence/BookingPersistenceIT.java | EXISTING | D1–D13; giữ hạ tầng PostgreSQL/Rabbit hiện có, sửa fixture/binding trong method cũ | Không nested TestConfiguration mới |
| C11 | test/dto/DtoValidationTests.java | EXISTING | U4; cập nhật constructor call cũ bị ảnh hưởng; không helper mới | Bean Validation tests hiện có |
| C12 | test/support/BookingFixtures.java | EXISTING | Sửa envelope type FAILED và constructor có count0 trong fixture có sẵn; không method mới | Fixture hiện có |
| C13 | test/messaging/AcceptPaymentListenerTests.java | EXISTING | Sửa worker/relay/query caller theo rename; không method mới | Test hiện có |
| C14 | main/validation/ContractRules.java | EXISTING | PAYLOAD_TYPES map hai key §3 tới BookingIssuanceResult.class thay type chung; không method/type mới | Map contract hiện có |

## 9. Bảng duyệt constructor / method — tất cả PENDING

| ID | Class | Chữ ký đầy đủ | Loại / cách viết |
| --- | --- | --- | --- |
| M1 | BookingIssuanceResultListener | `public BookingIssuanceResultListener(OrderService orderService)` | NEW, Lombok sinh |
| M2 | BookingIssuanceResultListener | `public void handleTicketsIssued(Envelope<BookingIssuanceResult> message)` | NEW, @RabbitListener(queues="booking.tickets-issued.q", ackMode="AUTO", concurrency="1"); chỉ ticket.issued.v1/COMPLETED |
| M3 | OrderService | `void processBookingIssuanceResult(Envelope<BookingIssuanceResult> message)` | NEW interface |
| M4 | OrderServiceImpl | `public void processBookingIssuanceResult(Envelope<BookingIssuanceResult> message)` | NEW, @Override @Transactional; flow §4–6 |
| M5 | OrderServiceImpl | `private String calculateIssuanceResultHash(Envelope<BookingIssuanceResult> message)` | NEW, JsonMapper + SHA-256, hash nguyên envelope |
| M6 | OrderServiceImpl | `private void validateIssuanceResult(Order lockedOrder, Envelope<BookingIssuanceResult> message)` | NEW, charge/snapshot/version/count và chuyển trạng thái hợp lệ; không đối chiếu issuanceId đã xử lý |
| M7 | OrderServiceImpl | `private void releaseResourcesAfterIssuanceFailure(Order lockedOrder)` | NEW, khóa + trả tài nguyên/promotion đúng một lần, không cần snapshot để xét điều kiện bán |
| M8 | OrderServiceImpl | `private void releasePurchasedQuantityInventory(Order lockedOrder, Reservation storedReservation, List<TicketTypeInventory> lockedInventories)` | NEW, giảm purchased đúng allocation |
| M9 | OrderServiceImpl | `private void releasePurchasedStandingInventory(Order lockedOrder, Reservation storedReservation, SectorInventory lockedSector)` | NEW, trả pool sector |
| M10 | OrderServiceImpl | `private void releasePurchasedSeatInventory(Order lockedOrder, Reservation storedReservation, List<SeatInventory> lockedSeats)` | NEW, guard state/ownership trước trả ghế |
| M11 | OrderServiceImpl | `private void releasePurchasedLimit(Order lockedOrder, Reservation storedReservation, PurchaseLimit lockedPurchaseLimit)` | NEW, giảm purchased buyer/event |
| M12 | OrderServiceImpl | `private void releaseUsedPromotion(Order lockedOrder, PromotionUsage storedUsage, PromotionInventory lockedPromotion)` | NEW, USED→RELEASED, used giảm 1 |
| M13 | OrderServiceImpl | `private void persistIssuanceResultReceipt(Envelope<BookingIssuanceResult> message, String payloadHash, Instant processedAt)` | NEW, save InboxMessage version null |
| M14 | OrderServiceImpl | `private void enqueueOrderCompleted(Envelope<BookingIssuanceResult> message, Instant occurredAt)` | NEW, DTO sẵn có + OutboxMessageMapper.toEntity + save |
| M15 | BookingIssuanceResult | `public BookingIssuanceResult(UUID orderId, UUID chargeId, UUID issuanceId, State state, Integer ticketCount, String failureCode)` | CHANGED canonical record ctor; thêm `public Integer ticketCount()`, API khác giữ |
| M16 | BookingIssuanceResult | `public boolean isConsistent()` | RENAME/CHANGE isValidFailure; @AssertTrue/@JsonIgnore, 2 nhánh validation |
| M17 | Order | Không tạo getter/setter issuance | REMOVED v0.2; giữ ID lịch sử để không đổi số các item sau |
| M18 | BookingOutboxWorker | `public BookingOutboxWorker(OutboxMessageRepository outboxMessageRepository, MessagingService messagingService)` | CHANGED tên constructor Lombok |
| M19 | BookingOutboxWorker | `public void publishDueBookingMessages()` | RENAME publishDuePaymentDecisions, @Scheduled giữ cấu hình cũ |
| M20 | MessagingService / Impl | `void publishBookingMessage(UUID messageId)` / `public void publishBookingMessage(UUID messageId)` | RENAME publishPaymentDecision, @Override/@Transactional giữ |
| M21 | OutboxMessageRepository | `List<OutboxMessage> findByMessageTypeInAndPublishedAtIsNullAndNextAttemptAtLessThanEqualOrderByOccurredAtAscMessageIdAsc(Collection<String> messageTypes, Instant now, Pageable pageable)` | CHANGED query để batch chung 2 type |
| M22 | BookingIssuanceResultListenerTests | `BookingIssuanceResultListenerTests()` | NEW constructor noarg ngầm |
| M23 | BookingIssuanceResultListener | `public void handleIssuanceFailed(Envelope<BookingIssuanceResult> message)` | NEW delta v0.4, @RabbitListener(queues="booking.issuance-failed.q", ackMode="AUTO", concurrency="1"); chỉ booking.issuance-failed.v1/FAILED |

REUSE không đổi chữ ký: OrderRepository.findEventIdById/findLockedById/currentDatabaseTime/configureTransactionLockTimeout; snapshot READ query; các query inventory/purchase limit/promotion có khóa, reservation lookup, inbox find/save, outbox lookup/save và mapper. Không sửa helper AcceptPayment để trộn HELD với COMMITTED. Record DTO vẫn sinh equals/hashCode/toString/accessor như hiện tại; không thêm Lombok builder.

## 10. Method kiểm thử dự kiến — tất cả PENDING

| ID | Class | Chữ ký / coverage |
| --- | --- | --- |
| U1 | BookingIssuanceResultListenerTests | `void delegatesIssuanceResultToTransactionalService()`; gọi đủ M2/M23, đúng type/state |
| U2 | cùng class | `void rejectsPermanentIssuanceResultErrors()`; cả hai queue, gồm sai cặp type/state |
| U3 | cùng class | `void propagatesIssuanceDependencyAndCommitFailures()` |
| U4 | DtoValidationTests | `void validatesBookingIssuanceResultBothOutcomes()`; cả null/state/count/failureCode |
| D1 | BookingPersistenceIT | `void completesOrderAfterSuccessfulIssuance() throws Exception`; count/state/inbox/outbox; tài nguyên không đổi, không xét lại expiresAt |
| D2 | cùng class | `void replaysIssuanceResultWithoutRepeatingEffects() throws Exception`; cùng ID, ID mới; cả 2 state, ID mới khác issuanceId vẫn hợp lệ nếu các guard khác đạt |
| D3 | cùng class | `void rejectsConflictingIssuanceResults() throws Exception`; hash/charge/state/count/version, terminal trái nhau; không kiểm conflict theo issuanceId của message ID mới |
| D4 | cùng class | `void waitsForPaymentAcceptanceBeforeIssuanceResult() throws Exception`; NOT_READY, không tự accept |
| D5 | cùng class | `void preservesCancellationWhenIssuanceResultArrives() throws Exception`; cancellation chặn success outbox/hồi sinh order; FAILED vẫn release một lần, createOrder bị gate event chặn |
| D6 | cùng class | `void releasesPurchasedInventoryAfterIssuanceFailure() throws Exception`; 3 loại, counter/allocations/limit |
| D7 | cùng class | `void releasesUsedPromotionOnlyOnceAfterIssuanceFailure() throws Exception`; USED→RELEASED và replay |
| D8 | cùng class | `void releasesPurchasedInventoryAfterSalesClose() throws Exception`; hết bán/hủy vẫn trả inventory/limit/promotion; createOrder bị gate event chặn; replay không release lần hai |
| D9 | cùng class | `void protectsNewSeatReservationFromStaleIssuanceFailure() throws Exception`; R1 không sửa R2 |
| D10 | cùng class | `void serializesConcurrentIssuanceResults() throws Exception`; cùng order/cùng ID khác order, không double release |
| D11 | cùng class | `void rollsBackIssuanceResultWhenPersistenceFails() throws Exception`; domain/inbox/outbox cùng rollback |
| D12 | cùng class | `void acknowledgesIssuanceResultOnlyAfterCommit() throws Exception`; hai queue/nhánh, ACK sau commit, sai nhánh → đúng DLQ riêng, lỗi không bị nuốt |
| D13 | cùng class | `void relaysOrderCompletedThroughRabbitMq() throws Exception`; worker gửi đúng 2 type, confirm/retry/rollback-resend giữ ID |

Sửa caller/fixture test cũ theo rename M15–M21 (trừ M17 đã bỏ); không tự thêm helper/test class ngoài bảng. Không thay schema Order; kiểm topology và quyền broker theo resource test cách ly. Payment/Ticket branch khác chỉ là dependency: test Booking không chứng minh upstream có terminal fence hoặc Saga/refund phía Payment đã đúng.

## 11. Nguồn và điểm chờ duyệt

- Code đích: OrderServiceImpl, AcceptPaymentListener, PaymentDecisionOutboxWorker, MessagingServiceImpl, DTO/entity/repository qua CodeGraph; Shop App source `OrderService.updateOrder` đã đọc trực tiếp.
- Nghiệp vụ: B13 §5.2, B14 §3/§5, `docs/evidence/use-case-diagrams/2026-10-29-clickup-booking-flow.md`; GOV-156 xác định success đi qua Payment.
- [Spring AMQP container AUTO ACK](https://docs.spring.io/spring-amqp/reference/amqp/containerAttributes.html): container ACK khi listener xử lý thành công; giữ gọi service proxy đồng bộ để commit trước return.
- [Spring AMQP sample confirms/returns](https://github.com/spring-projects/spring-amqp-samples/blob/main/spring-rabbit-confirms-returns/src/main/java/org/springframework/amqp/samples/confirms/SpringRabbitConfirmsReturnsApplication.java): tham khảo CorrelationData/confirm/return, tái sử dụng relay hiện có, không mang lớp cấu hình mẫu sang dự án.

**Đang chờ user duyệt BRAINSTORMING:** C1–C14 (C4 chỉ reuse), M1–M23 trừ M17 đã bỏ, U1–U4, D1–D13; khai báo mở rộng DTO và rename relay. Đã chốt hai queue riêng, không đối chiếu issuanceId, giữ check ticketCount và FAILED trả tài nguyên một lần kể cả hết bán/hủy; các khai báo chưa được duyệt, không coi duyệt nghiệp vụ là duyệt khai báo Java. Delta v0.4: M2 đổi tên/queue, thêm M23, C14 sửa map messageType; C12/C13 đồng bộ kiểm kê với plan. Chỉ chuyển writing-plans khi user yêu cầu sau khi duyệt thiết kế.
