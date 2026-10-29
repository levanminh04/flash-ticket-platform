# Plan Booking — Kết quả phát vé COMPLETED / FAILED

- Phiên bản v0.2; tài liệu pha **WRITING-PLANS**. Các nhãn PENDING bên dưới lưu lịch sử pha lập kế hoạch; user sau đó đã xác nhận EXECUTING-PLANS cho toàn bộ danh sách, kết quả triển khai/kiểm chứng ghi ở §11. Không suy ra duyệt hồi tố pha khác.
- Design: [spec v0.4](../specs/2026-10-29-booking-issuance-result-design.md). Task: [Nhận thông báo phát hành vé, cập nhật order COMPLETED và ACK](https://app.clickup.com/t/z8qgvc7w2u).
- Source khảo sát: `D:/DATN/flash-ticket-platform`, `dev/son`, HEAD `d2bb7b0`. Working tree có B14/spec đang chỉnh; giữ các tài liệu này, không reset. Chưa có yêu cầu tạo branch/worktree cho task mới.
- Nguồn phạm vi đã chốt: “làm cả 2 nhánh đi”; “k cần check issuanceId”; “có thể check cũng đc” cho ticketCount; “cứ đưa về. còn khi đặt thì check event rồi nên ko lo” cho FAILED release. User yêu cầu “viết plan theo spec này”. Chưa có xác nhận gộp danh sách khai báo BRAINSTORMING; không tự ghi APPROVED pha trước. Plan này trình đầy đủ danh sách cuối để duyệt WRITING-PLANS.
- Delta v0.2 theo user **“vậy dùng 2 queue, sửa plan, spec”**: tái sử dụng hai queue/binding đang có trên119; M2 đổi thành handleTicketsIssued, thêm M23 handleIssuanceFailed trong cùng class. Hai entry point dùng chung DTO/service và inbox consumerName; bổ sung C14 đổi contract map. Quyết định hai queue đã xác nhận, khai báo Java vẫn PENDING.

## 1. Kết quả cần đạt

Payment → hai queue dưới đây → hai listener method AUTO trong cùng class → service transaction chung → inbox + thay đổi domain + outbox → commit → broker ACK. Worker hiện có gửi thêm `booking.order-completed.v1` khi thành công; Payment phải nhận xác nhận nghiệp vụ này mới hoàn tất Saga.

| Nhánh | Queue | messageType = routing key tại business.ex | DLQ |
| --- | --- | --- | --- |
| COMPLETED | booking.tickets-issued.q | ticket.issued.v1 | booking.tickets-issued.dlq |
| FAILED | booking.issuance-failed.q | booking.issuance-failed.v1 | booking.issuance-failed.dlq |

Nguồn gửi vẫn Payment, dù key thành công mang tên ticket.issued.v1 theo binding hiện có. Trước live phải đối chiếu producer/quyền; key không tự xác minh publisher identity. Không thêm queue chung và không bind cùng routing key vào cả hai queue. Payload hai nhánh giữ Envelope<BookingIssuanceResult> theo spec.

- COMPLETED: chỉ ISSUING → COMPLETED sau kiểm accepted charge, frozen snapshot và số vé; không thay counter đã chốt.
- FAILED: trả purchased/used đúng một lần, reservation/allocations → RELEASED, order → ISSUANCE_FAILED; order/event đã hủy giữ/đưa về CANCELLED. Không kiểm sale window hoặc hạn giữ để quyết định release.
- Không lưu/so issuanceId trước đó trên Order; chỉ giữ yêu cầu UUID không null theo contract và truyền vào OrderCompleted. Không thêm cột/table/schema/dependency, HTTP API, worker thứ hai hoặc xử lý refund/phát vé.
- Cùng message ID/cùng hash không làm lại; cùng ID/khác hash conflict. ID mới cùng charge và trạng thái terminal phù hợp là replay; COMPLETED↔FAILED trái nhau conflict. Không so failureCode của hai message ID khác nhau.
- Bảo đảm tạo đủ vé và chỉ một issuance/order thuộc Ticket; linkage issuance và refund thuộc Payment. Booking không kiểm chứng các nhánh upstream bằng việc xử lý result.

## 2. Task 1 — Đồng bộ DTO và topology trong source

**Item:** C3/C8/C11/C12/C14, M15–M16, U4; sửa các caller DTO hiện có, không thêm helper. Phụ thuộc: duyệt plan.

**File:** `services/booking-service/src/main/java/com/flashticket/booking_service/dto/message/BookingIssuanceResult.java`, `exception/ErrorCode.java`, `validation/ContractRules.java`; `src/test/java/.../dto/DtoValidationTests.java`, `support/BookingFixtures.java`; `deploy/rabbitmq/definitions.json` (đối chiếu topology, chỉ sửa nếu lệch bảng); `docs/architecture/B13-api-and-event-contracts.md`.

1. Thêm State.COMPLETED; ticketCount `@NotNull @PositiveOrZero`; failureCode không còn @NotBlank vô điều kiện. Đổi isValidFailure → isConsistent, giữ @AssertTrue/@JsonIgnore: COMPLETED yêu cầu count>0 và failureCode=null; FAILED count=0 và failureCode không blank. Không đổi record sang class trong task này.
2. Cập nhật constructor và envelope key FAILED tại BookingFixtures/DtoValidationTests. Sửa PAYLOAD_TYPES trong ContractRules: ticket.issued.v1 và booking.issuance-failed.v1 cùng map BookingIssuanceResult.class, thay booking.issuance-result.v1. Giữ OrderCompleted map và các method contract hiện có; không thêm type registry. M2/M23 và service kiểm cặp type/state, không dựa riêng việc DTO parse thành công.
3. Thêm ErrorCode ISSUANCE_RESULT_NOT_READY và ISSUANCE_RESULT_CONFLICT, HTTP CONFLICT cho đồng bộ bảng ErrorCode hiện có; listener phân loại theo mã, không theo HTTP status. Giữ RESERVATION_DATA_CONFLICT cho dữ liệu tài nguyên không nhất quán.
4. Tái sử dụng booking.tickets-issued.q/.dlq và booking.issuance-failed.q/.dlq, binding/key theo bảng §1. Đối chiếu quorum/policy mỗi queue: delivery-limit20, dead-letter at-least-once, reject-publish; dlx.ex/key DLQ tương ứng. Không tạo/xóa/purge queue hoặc sửa binding runtime trong pha lập plan. Không tạo booking.issuance-result.q.
5. B13 đồng bộ danh mục hai type/key thay type chung, giữ payload BookingIssuanceResult; nguồn gửi success/failed đều Payment → Booking. Xóa yêu cầu Booking so issuanceId đã xử lý, giữ field trong contract và trách nhiệm kiểm phía Payment/Ticket. B14 nhánh FAILED đã đổi theo user; giữ đúng lựa chọn này, đồng bộ tên messageType trong flow khi triển khai docs.

**Kết quả kiểm:** U4 đủ hai state và null/zero/count/failureCode sai; map hai type đúng DTO; JSON definitions parse được, hai routing key về đúng queue/DLQ. Đã quan sát read-only119 có đủ hai queue, không suy producer/policy/quyền đã đúng từ việc queue tồn tại. Thiếu quyền runtime cần đồng bộ trước bật listener vì spring.rabbitmq.dynamic=false.

## 3. Task 2 — Nhận message và chuyển trạng thái an toàn

**Item:** C1/C2/C4/C9/C10, M1–M6/M13–M14/M22–M23, U1–U3, D1–D5/D10–D12. Phụ thuộc Task1.

**File:** main `messaging/BookingIssuanceResultListener.java`, `order/service/OrderService.java`, `order/service/impl/OrderServiceImpl.java`; test `messaging/BookingIssuanceResultListenerTests.java`, `persistence/BookingPersistenceIT.java`.

1. Một class listener dùng final OrderService, @RequiredArgsConstructor. M2 @RabbitListener booking.tickets-issued.q chỉ nhận ticket.issued.v1/COMPLETED; M23 @RabbitListener booking.issuance-failed.q chỉ nhận booking.issuance-failed.v1/FAILED. Cả hai ackMode AUTO, concurrency1, kiểm nhánh rồi gọi cùng service, reject mã permanent; không Channel/tag, không catch lỗi DB rồi return. Không tạo helper listener khác.
2. M4 @Transactional: Validator kiểm nguyên envelope, schema/type/aggregate ID/version; kiểm đúng hai cặp type/state ngay cả khi service được gọi trực tiếp. Sai cặp/nhầm queue → INVALID_MESSAGE. M5 hash nguyên envelope bằng JsonMapper + SHA-256 như calculateAcceptPaymentHash. Không gọi lại helper AcceptPayment với payload giả.
3. Dùng configureTransactionLockTimeout `5s`; scalar tìm eventId, snapshot PESSIMISTIC_READ, order PESSIMISTIC_WRITE rồi đọc inbox key `(booking.issuance-result,messageId)`. consumerName này là tên nghiệp vụ chung, giữ cho cả hai queue để cùng messageId qua hai nhánh vẫn bị so hash. Hash trùng thì return; khác thì MESSAGE_ID_CONFLICT. Hai queue có thể chạy đồng thời/khác thứ tự; khóa order và state guard phân xử, không dựa thứ tự broker. Comment giải thích đọc sau lock vì receipt có thể vừa commit trong lúc chờ.
4. M6 kiểm acceptedChargeId, acceptedAt, frozen snapshot hợp lệ/thuộc order-buyer-event, frozenAt/version khớp field đã lưu. aggregateVersion dùng sourceOrderVersion đã freeze. COMPLETED yêu cầu tổng frozen items quantity đúng ticketCount; FAILED count0. Không kiểm deadline một lần nữa trên charge đã được nhận.
5. Order/snapshot chưa có hoặc PENDING_PAYMENT chưa được accept → NOT_READY để broker giao lại có giới hạn. Order đã có accepted charge khác → conflict. EXPIRED chưa được accept, snapshot hỏng hoặc trạng thái không hợp lệ → permanent conflict; không tự tạo acceptance từ result.
6. Bảng state dưới đây quyết định trước khi gọi release; tất cả lấy từ order đã khóa. Với lần đổi state, updatedAt lấy giờ DB sau các bước chờ; replay không save order và không tăng rowVersion vô ích.

| Order / event | COMPLETED tới | FAILED tới |
| --- | --- | --- |
| ISSUING, event chưa hủy | COMPLETED + inbox + OrderCompleted outbox | Task3 release + ISSUANCE_FAILED + inbox |
| COMPLETED, event chưa hủy | Inbox + response mới nếu message ID mới; tài nguyên/order không đổi | Conflict, rollback |
| ISSUANCE_FAILED, event chưa hủy | Conflict, rollback | Inbox, không release lần hai |
| Order CANCELLED, charge đã được accept | Inbox; giữ CANCELLED, không OrderCompleted | Release phần COMMITTED/USED còn lại + inbox; giữ CANCELLED |
| Event CANCELLED, order chưa bị hủy | Không OrderCompleted; chỉ ISSUING có thể đưa CANCELLED như spec; không hồi sinh terminal khác | ISSUING đưa CANCELLED + release; ISSUANCE_FAILED chỉ ghi inbox; COMPLETED vẫn conflict |

Event hủy không cấp quyền đổi COMPLETED thành FAILED. Fan-out hủy hoàn chỉnh là task khác. Nếu result COMPLETED bị chặn bởi cancellation thì không tự release purchased vì vẫn có thể đã phát vé; FAILED dứt điểm mới đi nhánh trả của task này.

7. M13 save inbox với version=null. M14 tạo OrderCompleted từ dữ liệu message đã được validate, dùng ID xác định từ `booking.order-completed:` + input messageId; giữ correlationId/traceparent, causationId=input ID, aggregateVersion giữ snapshot version; occurredAt giờ DB. Tái sử dụng OutboxMessageMapper.toEntity + save. FAILED không có success outbox hoặc loại ACK event mới.
8. Domain/inbox/outbox commit chung. Lỗi unique/save/commit đi ra ngoài proxy; không catch rồi tiếp tục transaction rollback-only. Container ACK sau service return; mất ACK redelivery đọc inbox. Message ID mới báo COMPLETED có thể dùng issuanceId khác; Booking không kiểm đợt cũ, Payment kiểm xác nhận trước khi đóng Saga.

**Kết quả kiểm:** U1–U3 và D1–D5/D10–D12 chứng minh state/receipt/response/rollback. Bao gồm snapshot sai, charge sai, count sai, duplicate metadata, accepted trước expiry nhưng success tới sau expiry, message tới trước acceptance, terminal trái nhau và cancelled không hồi sinh. Giữ logging ErrorCode qua handler hiện có.

## 4. Task 3 — Trả phần đã chốt khi FAILED

**Item:** C2/C10, M7–M12, D6–D11. Phụ thuộc Task2; Task2 chỉ sẵn sàng nghiệm thu FAILED sau khi Task3 xong.

**File:** `order/service/impl/OrderServiceImpl.java`, `persistence/BookingPersistenceIT.java`. Không sửa các helper HELD của AcceptPayment.

1. M7 đọc reservation và allocations của đúng order. Kiểm event/buyer/item ownership, quantities/tổng từng item và loại resource theo frozen selection. Guard state: reservation + allocations phải đồng nhất COMMITTED để release; đồng nhất RELEASED thì bỏ qua tài nguyên đã trả; mixed/hỏng thì RESERVATION_DATA_CONFLICT và rollback.
2. Giữ thứ tự snapshot READ → order WRITE ở M4; M7 tiếp tục purchase limit WRITE → đúng inventory WRITE theo ID tăng dần → promotion WRITE. Chỉ khóa tài nguyên liên quan, không khóa WRITE cả event. Query đã có sẵn, không native SQL hoặc EntityManager mới.
3. QUANTITY M8 gom số lượng allocation theo ticketTypeId; danh sách locked inventory phải khớp tài nguyên, thuộc event và purchased đủ. Giảm purchased, giữ nguyên held/configuredCapacity.
4. STANDING M9 giảm purchased của một pool sector bằng tổng quantity, không giảm thêm pool theo ticket type. Kiểm sector/type/event và counter đủ.
5. SEATED M10 chỉ PURCHASED + đúng currentReservationId/reservation, seat/type/sector/item và generation>0 mới được đổi. Trả AVAILABLE, hidden thì UNAVAILABLE; xóa currentReservationId, không tự reset/tăng generation. R1 không được sửa ghế đã thuộc R2; allocation hiện không có generation riêng nên không giả định có so sánh hai generation.
6. M11 giảm purchased của purchase limit đúng buyer/event bằng quantity được trả, giữ held. M12 trả promotion USED → RELEASED và used giảm1, giữ held; RELEASED bỏ qua. Đối chiếu usage với reservation/snapshot/promotion và không cho counter âm; không tự tạo usage khi thiếu.
7. Inventory/limit trả xong thì allocations và reservation COMMITTED → RELEASED. Promotion độc lập có thể đã RELEASED trước, không giảm lại. Nếu reservation RELEASED nhưng allocations vẫn COMMITTED hoặc ngược lại thì lỗi nhất quán, không sửa chữa đoán.
8. Không chặn bước3–7 bằng eventStatus/sale window/expiresAt. Order CANCELLED vẫn giữ CANCELLED. Tất cả rollback cùng inbox nếu ghi bất kỳ bảng nào lỗi. Không gọi Payment/refund hoặc Ticket từ transaction này.

**Kết quả kiểm:** đủ ba selection; failed replay cùng ID/ID mới, tranh chấp đồng thời, promotion đã trả, counter không đủ, ownership sai, R1/R2 và persistence lỗi. D8 chứng minh hết bán/hủy vẫn trả counter nhưng request createOrder vẫn bị event gate chặn. Không hứa cancellation worker đã triển khai; fixture chỉ kiểm guard tương thích.

## 5. Task 4 — Mở rộng relay sẵn có, không thêm worker

**Item:** C5/C6/C7/C10/C13, M18–M21, D13; sửa caller test cũ. Phụ thuộc Task1; có thể làm sau Task3 để giữ thứ tự thực thi đơn giản.

**File:** đổi `messaging/PaymentDecisionOutboxWorker.java` → `messaging/BookingOutboxWorker.java`; `messaging/service/MessagingService.java`, `impl/MessagingServiceImpl.java`, `repository/OutboxMessageRepository.java`; test `messaging/AcceptPaymentListenerTests.java`, `persistence/BookingPersistenceIT.java`.

1. Đổi tên worker/constructor/method, giữ @ConditionalOnProperty/@Scheduled và field @Value. Query M21 theo Collection hai type, giới hạn batch **tổng** như cấu hình, không nhân batch theo type. Giữ order occurredAt/messageId.
2. Worker truyền ID cho `MessagingService.publishBookingMessage` qua proxy. M20 giữ @Transactional, timeout5s, khóa WRITE từng outbox row, kiểm type whitelist/publishedAt/nextAttemptAt sau khóa.
3. Chọn PaymentDecision.class hoặc OrderCompleted.class theo hai type; dùng mapper hiện có. Không raw decoder, unchecked cast, strategy interface hoặc map handler mới; có thể dùng switch hai nhánh trong method đang có.
4. Giữ converter, persistent, mandatory/returns/correlated confirm, exchange business.ex từ @Value. ACK/no-return mới save publishedAt; errors giữ pending, cập nhật attempts/error/nextAttemptAt theo code cũ. Không thêm cơ chế retry song song.
5. Giữ messageId khi gửi lại, ID CorrelationData riêng cho attempt. TX gửi chỉ giữ row outbox + connection DB, không giữ order/inventory lock. Crash sau publish trước commit vẫn có thể duplicate; downstream dedupe bắt buộc.
6. Sửa caller/Mockito stubbing cũ theo rename, không đổi signature các test cũ. Binding test thêm OrderCompleted routing vào queue cách ly. Không consume/purge `payment.order-completed.q` nghiệp vụ để quan sát test.

**Kết quả kiểm:** D13 nhận đúng persistent envelope/metadata của OrderCompleted và PaymentDecision, skip published, retry khi return/timeout; giữ các test confirm/rollback-resend/concurrent publisher cũ. Đổi tên không làm mất worker hoặc tắt default scheduling.

## 6. Task 5 — Kiểm chứng và bàn giao

**Item:** U1–U4/D1–D13 và các test cũ bị sửa caller; không method/helper mới ngoài bảng.

- JDK23, Maven Enforcer giữ nguyên. Chỉ format Java thay đổi; không format cả repo để tránh diff ngoài task.
- Từ root, sau khi nạp env vào process an toàn (Maven không tự đọc `.env`): `rtk mvn -f services/booking-service/pom.xml -B -ntp -Dtest=BookingIssuanceResultListenerTests,DtoValidationTests,AcceptPaymentListenerTests test`.
- Sau unit đúng: `rtk mvn -f services/booking-service/pom.xml -B -ntp -P integration verify` cho PostgreSQL transaction, broker, full regression và Spotless/SpotBugs theo lifecycle hiện có. Đây là lệnh dự kiến, chưa có kết quả mới.
- Dùng hạ tầng test hiện có khi sẵn sàng; không áp đặt cài Docker mới. Nếu môi trường test không chạy được, báo điều kiện thực tế; không chạy fixture/truncate chung trên DB119. Kiểm Rabbit119 nếu được giao: exchange/queue test cách ly, admin test credential chỉ để tạo/xóa resource riêng, không đổi quyền/resource nghiệp vụ.
- Expected: hai nhánh đúng state/counters/receipt/outbox, không release hai lần hoặc cập nhật R2, commit thất bại không ACK thành công, relay gửi lại giữ ID. Không dùng tổng số test cũ làm bằng chứng code mới đã đạt.
- Kiểm tĩnh definitions, policy/key, DTO field và không có schema Order mới. Nếu producer Payment dùng format khác thì dừng bước live phụ thuộc và đồng bộ contract, không đoán message.
- Cập nhật `services/booking-service/TESTING.md` và `HANDOFF.md`: kết quả thực, lệnh, giới hạn, runtime prerequisite; giữ lịch sử task trước. Đề xuất skill writing-docs sau execute hoàn tất theo rule; không tự thêm tài liệu hướng dẫn nếu chưa được yêu cầu.
- Commit/push/merge/deploy/ClickUp theo yêu cầu riêng, không tự coi việc viết plan đã cấp quyền các bước đó.

## 7. Bảng duyệt class — WRITING-PLANS PENDING

Base main: `services/booking-service/src/main/java/com/flashticket/booking_service/`; base test tương ứng `src/test/java/`. Mẫu S1 = Shop App `services/impl/OrderService.updateOrder` (đã đọc trực tiếp); S2 = Shop App `components/JwtTokenUtil` theo style map cho field @Value; N = NGOÀI MẪU S1, tái sử dụng cơ chế đích đã có.

| ID | File/class | Loại | Field/annotation/API và mẫu |
| --- | --- | --- | --- |
| C1 | main `messaging/BookingIssuanceResultListener.java` | NEW | @Component/@RequiredArgsConstructor, final OrderService, constructor M1; hai @RabbitListener M2/M23. Injection theo S1, listener theo N/AcceptPaymentListener |
| C2 | main `order/service/OrderService.java`, `impl/OrderServiceImpl.java` | EXISTING | M3–M14; @Override/@Transactional public impl; giữ dependencies/constructor hiện có; S1 + N/AcceptPayment transaction |
| C3 | main `dto/message/BookingIssuanceResult.java` và State | EXISTING | M15–M16, thêm enum member COMPLETED, ticketCount @NotNull/@PositiveOrZero, failureCode nullable; giữ UUID @NotNull; N/record đã có, không type mới |
| C4 | main `entity/Order.java` | REUSE UNCHANGED | Dùng state/acceptedChargeId/acceptedAt/frozen snapshot/paymentSnapshotVersion/updatedAt/@Version; không thêm field/API |
| C5 | main `messaging/BookingOutboxWorker.java` | RENAME | @Component/@RequiredArgsConstructor/@ConditionalOnProperty/@Scheduled giữ; final OutboxMessageRepository/MessagingService, field batchSize @Value; M18–M19; S1/S2 + N/worker hiện có |
| C6 | main `messaging/service/MessagingService.java`, `impl/MessagingServiceImpl.java` | EXISTING | M20 đổi tên, @Override/@Transactional giữ; constructor final deps và @Value hiện tại không đổi; N/relay hiện có |
| C7 | main `messaging/repository/OutboxMessageRepository.java` | EXISTING | M21 derived IN query; giữ lookup ID @Lock(PESSIMISTIC_WRITE); Spring Data theo Shop App repository + N/khóa đã có |
| C8 | main `exception/ErrorCode.java` | EXISTING | Hai enum member NOT_READY/CONFLICT; không constructor/method mới, fixed message tiếng Việt |
| C9 | test `messaging/BookingIssuanceResultListenerTests.java` | NEW | @ExtendWith(MockitoExtension.class), @Mock OrderService, @InjectMocks listener; constructor M22; U1–U3. N/test mẫu AcceptPaymentListenerTests |
| C10 | test `persistence/BookingPersistenceIT.java` | EXISTING | D1–D13, giữ test config/container/constructor/fixtures; không @Bean hoặc type mới. N/hạ tầng test hiện có |
| C11 | test `dto/DtoValidationTests.java` | EXISTING | U4 @Test; cập nhật thân method/call constructor cũ, constructor class giữ nguyên; N/test hiện có |
| C12 | test `support/BookingFixtures.java` | EXISTING | Chỉ sửa BookingIssuanceResult constructor call (FAILED có count0) trong fixture có sẵn; không thêm field/method/type |
| C13 | test `messaging/AcceptPaymentListenerTests.java` | EXISTING | Sửa worker/relay/query caller theo rename trong các test hiện có, giữ chữ ký/annotation/constructor |
| C14 | main `validation/ContractRules.java` | EXISTING | Sửa PAYLOAD_TYPES map hai key tới BookingIssuanceResult.class thay key chung; giữ mọi method/constructor, không thêm type |

C12–C14 đã đồng bộ kiểm kê với spec v0.4, không phát sinh method Java ở ba file này. OrderCompleted, InboxMessage/Id, các entity/repository còn lại và mapper được REUSE không thêm API. Không dùng query JSON inbox theo order, không thêm cột lưu issuance.

## 8. Bảng duyệt method và API sinh — WRITING-PLANS PENDING

Tất cả method dưới đây không `throws` checked exception trừ test ghi rõ. Các ID giữ theo spec; M17 đã bỏ, không triển khai.

| ID | Class | Chữ ký đầy đủ | Cách viết / mẫu |
| --- | --- | --- | --- |
| M1 | C1 | `public BookingIssuanceResultListener(OrderService orderService)` | NEW, Lombok constructor; S1 |
| M2 | C1 | `public void handleTicketsIssued(Envelope<BookingIssuanceResult> message)` | NEW @RabbitListener(queues="booking.tickets-issued.q", ackMode="AUTO", concurrency="1"); chỉ ticket.issued.v1/COMPLETED, N/AcceptPaymentListener |
| M3 | OrderService | `void processBookingIssuanceResult(Envelope<BookingIssuanceResult> message)` | NEW interface public; S1/interface nghiệp vụ |
| M4 | OrderServiceImpl | `public void processBookingIssuanceResult(Envelope<BookingIssuanceResult> message)` | NEW @Override/@Transactional; S1 setter/save + N/transaction inbox/outbox |
| M5 | OrderServiceImpl | `private String calculateIssuanceResultHash(Envelope<BookingIssuanceResult> message)` | NEW Jackson + SHA-256, N/helper hash hiện có |
| M6 | OrderServiceImpl | `private void validateIssuanceResult(Order lockedOrder, Envelope<BookingIssuanceResult> message)` | NEW Validator/if + BusinessException(ErrorCode); S1 + N/snapshot guards |
| M7 | OrderServiceImpl | `private void releaseResourcesAfterIssuanceFailure(Order lockedOrder)` | NEW repository/lock/setter/save; S1 + N/release COMMITTED |
| M8 | OrderServiceImpl | `private void releasePurchasedQuantityInventory(Order lockedOrder, Reservation storedReservation, List<TicketTypeInventory> lockedInventories)` | NEW gom qty theo type + giảm purchased; N/đối ứng helper inventory cũ |
| M9 | OrderServiceImpl | `private void releasePurchasedStandingInventory(Order lockedOrder, Reservation storedReservation, SectorInventory lockedSector)` | NEW giảm pool sector; N/đối ứng helper cũ |
| M10 | OrderServiceImpl | `private void releasePurchasedSeatInventory(Order lockedOrder, Reservation storedReservation, List<SeatInventory> lockedSeats)` | NEW guard ownership + setter; N/đối ứng helper ghế cũ |
| M11 | OrderServiceImpl | `private void releasePurchasedLimit(Order lockedOrder, Reservation storedReservation, PurchaseLimit lockedPurchaseLimit)` | NEW guard tổng/ownership + purchased giảm; S1 setter/save, N/counter |
| M12 | OrderServiceImpl | `private void releaseUsedPromotion(Order lockedOrder, PromotionUsage storedUsage, PromotionInventory lockedPromotion)` | NEW USED→RELEASED, used giảm; S1 setter/save, N/idempotent release |
| M13 | OrderServiceImpl | `private void persistIssuanceResultReceipt(Envelope<BookingIssuanceResult> message, String payloadHash, Instant processedAt)` | NEW save receipt, version null; N/inbox cũ |
| M14 | OrderServiceImpl | `private void enqueueOrderCompleted(Envelope<BookingIssuanceResult> message, Instant occurredAt)` | NEW record DTO + envelope + mapper/save; N/outbox cũ |
| M15 | BookingIssuanceResult | `public BookingIssuanceResult(UUID orderId, UUID chargeId, UUID issuanceId, State state, Integer ticketCount, String failureCode)`; `public Integer ticketCount()` | CHANGED canonical record constructor, NEW accessor; compiler sinh, N/record đã tồn tại |
| M16 | BookingIssuanceResult | `public boolean isConsistent()` | RENAME isValidFailure; @AssertTrue/@JsonIgnore; N/validation DTO cũ |
| M17 | Order | Không thêm method | REMOVED, không có approval item để viết |
| M18 | BookingOutboxWorker | `public BookingOutboxWorker(OutboxMessageRepository outboxMessageRepository, MessagingService messagingService)` | CHANGED tên constructor Lombok; S1 |
| M19 | BookingOutboxWorker | `public void publishDueBookingMessages()` | RENAME @Scheduled(fixedDelayString="${booking.outbox.poll-interval:PT2S}"); N/worker cũ |
| M20 | MessagingService / Impl | `void publishBookingMessage(UUID messageId)` / `public void publishBookingMessage(UUID messageId)` | RENAME publishPaymentDecision; @Override/@Transactional giữ; N/relay cũ |
| M21 | OutboxMessageRepository | `List<OutboxMessage> findByMessageTypeInAndPublishedAtIsNullAndNextAttemptAtLessThanEqualOrderByOccurredAtAscMessageIdAsc(Collection<String> messageTypes, Instant now, Pageable pageable)` | CHANGED query MessageType→MessageTypeIn; Spring Data, không SQL native |
| M22 | BookingIssuanceResultListenerTests | `BookingIssuanceResultListenerTests()` | NEW constructor package-private noarg ngầm; N/JUnit class hiện có |
| M23 | C1 | `public void handleIssuanceFailed(Envelope<BookingIssuanceResult> message)` | NEW delta v0.2, @RabbitListener(queues="booking.issuance-failed.q", ackMode="AUTO", concurrency="1"); chỉ booking.issuance-failed.v1/FAILED; N/AcceptPaymentListener |

Record giữ accessor orderId/chargeId/issuanceId/state/failureCode và equals/hashCode/toString do compiler sinh, nay tính thêm ticketCount. Không thêm getter/setter/builder. Lombok C1/C5 chỉ constructor cho final dependencies đã liệt kê, không sinh API khác. Không thêm bean hoặc annotation framework chưa nêu.

## 9. Bảng duyệt test method — WRITING-PLANS PENDING

U1–U4 có @Test, không checked throws. D1–D13 có @Test/@Transactional(propagation=Propagation.NOT_SUPPORTED), mỗi method `throws Exception` theo mẫu BookingPersistenceIT để tự điều khiển các transaction/luồng cạnh tranh; không thêm nested class/anonymous subclass/helper.

| ID | Class | Chữ ký đầy đủ | Task / kết quả chính |
| --- | --- | --- | --- |
| U1 | BookingIssuanceResultListenerTests | `void delegatesIssuanceResultToTransactionalService()` | 2, cả M2/M23 gọi đúng service với type/state đúng |
| U2 | cùng class | `void rejectsPermanentIssuanceResultErrors()` | 2, permanent/sai type/state/nhầm queue → reject không requeue |
| U3 | cùng class | `void propagatesIssuanceDependencyAndCommitFailures()` | 2, lỗi dependency/DB/commit đi ra container |
| U4 | DtoValidationTests | `void validatesBookingIssuanceResultBothOutcomes()` | 1, validation 2 state |
| D1 | BookingPersistenceIT | `void completesOrderAfterSuccessfulIssuance() throws Exception` | 2, COMPLETE + receipt/outbox; expiry đã qua vẫn đúng; inventory nguyên |
| D2 | cùng class | `void replaysIssuanceResultWithoutRepeatingEffects() throws Exception` | 2–3, same/new ID, 2 state; new ID khác issuanceId vẫn hợp lệ |
| D3 | cùng class | `void rejectsConflictingIssuanceResults() throws Exception` | 2, hash/charge/count/version/state sai, toàn bộ rollback |
| D4 | cùng class | `void waitsForPaymentAcceptanceBeforeIssuanceResult() throws Exception` | 2, NOT_READY không tự accept |
| D5 | cùng class | `void preservesCancellationWhenIssuanceResultArrives() throws Exception` | 2–3, success không hồi sinh/outbox; FAILED vẫn release |
| D6 | cùng class | `void releasesPurchasedInventoryAfterIssuanceFailure() throws Exception` | 3, 3 mode, ownership/counter và reservation/allocation |
| D7 | cùng class | `void releasesUsedPromotionOnlyOnceAfterIssuanceFailure() throws Exception` | 3, USED/RELEASED guards |
| D8 | cùng class | `void releasesPurchasedInventoryAfterSalesClose() throws Exception` | 3, hết bán/hủy vẫn release, đặt mới bị chặn |
| D9 | cùng class | `void protectsNewSeatReservationFromStaleIssuanceFailure() throws Exception` | 3, R1 không sửa R2 |
| D10 | cùng class | `void serializesConcurrentIssuanceResults() throws Exception` | 2–3, concurrent same/different ID, receipt race giữa hai order |
| D11 | cùng class | `void rollsBackIssuanceResultWhenPersistenceFails() throws Exception` | 2–3, rollback cả COMPLETED/FAILED/inbox/outbox |
| D12 | cùng class | `void acknowledgesIssuanceResultOnlyAfterCommit() throws Exception` | 2, hai queue với hai listener, ACK sau commit, nhầm nhánh vào đúng DLQ riêng và redelivery |
| D13 | cùng class | `void relaysOrderCompletedThroughRabbitMq() throws Exception` | 4, dual type, persistence/confirm/retry/resend |

Các test cũ chỉ sửa thân/caller theo rename, không đổi tên hay signature. Nếu cần method fixture/helper/test mới ngoài bảng thì trình delta, không nhét generator/lambda để lách gate.

## 10. Nguồn đối chiếu và trạng thái duyệt

- Đã xem source đích bằng CodeGraph: BookingIssuanceResult, OrderCompleted, OrderServiceImpl/validatePaymentSnapshot, MessagingServiceImpl, worker và outbox/order repository; tìm caller bằng rg sau đó. Mẫu Shop App OrderService.updateOrder đọc trực tiếp; field @Value theo style map và cách dùng hiện tại của Booking.
- Nghiên cứu framework đã có trong spec: [Spring AMQP AUTO](https://docs.spring.io/spring-amqp/reference/amqp/containerAttributes.html) và [sample confirms/returns của Spring](https://github.com/spring-projects/spring-amqp-samples/blob/main/spring-rabbit-confirms-returns/src/main/java/org/springframework/amqp/samples/confirms/SpringRabbitConfirmsReturnsApplication.java). Plan tái sử dụng các cơ chế này, không thêm framework/kiểu bean mới.
- Tất cả task1–5 chưa sẵn sàng execute khi gate **WRITING-PLANS PENDING**. Danh sách duyệt: C1–C14 (C4 không đổi), M1–M23 trừ M17, U1–U4, D1–D13. Delta v0.2: M2 đổi tên/queue, thêm M23 và C14 đổi map type, U1/U2/D12 cover hai listener/DLQ. Chọn hai queue đã được user xác nhận, chưa coi là duyệt tất cả khai báo Java.
- Nguồn xác nhận pha này: **chưa có**; không lấy các câu duyệt nghiệp vụ làm duyệt Java. Sau phản hồi user mới ghi đúng item/version APPROVED; nếu user yêu cầu triển khai thì skill executing-plans vẫn có gate riêng.

## 11. Thực thi

EXECUTING-PLANS **APPROVED**: user trả lời “Xác nhận toàn bộ danh sách thực thi” cho C1–C14, M1–M23 trừ M17, U1–U4/D1–D13 của spec v0.4/plan v0.2. Đây là nguồn duyệt thực thi; các nhãn PENDING phía trên giữ lịch sử ở pha lập plan, không phải xác nhận hồi tố các pha trước. Đang triển khai trên dev/son; chưa commit/push/deploy.

Kết quả thực thi 29/10/2026:

| Task | Trạng thái / bằng chứng |
| --- | --- |
| 1 — DTO/contract/topology | DONE: DTO hai state/ticketCount, map hai messageType; fixture/caller và B13/B14 đồng bộ. definitions.json đã có đủ quorum queues/bindings/policy, không sửa broker hoặc file definitions cho đủ diff |
| 2 — Listener/state/inbox/outbox | DONE: hai listener AUTO gọi cùng service @Transactional; charge/version/count/type guards, idempotency, success/cancellation và outbox sau commit |
| 3 — FAILED release | DONE: purchased/used giảm một lần cho QUANTITY/STANDING/SEATED, limit/promotion; closed/cancelled vẫn release, R2 không bị sửa, transaction rollback giữ nguyên dữ liệu |
| 4 — Relay | DONE: một BookingOutboxWorker/relay, batch chung hai type; giữ row lock, persistent confirm/return/retry/resend ID |
| 5 — Kiểm chứng/bàn giao | DONE trong phạm vi component của Booking: compile, test-compile, unit, full verify, source topology và git diff check; TESTING/HANDOFF cập nhật |

- Production compile và test-compile đã đạt. Targeted unit/DTO/listener + real119 connection smoke: 101/101 đạt.
- Lần đầu chạy 13 IT mới: 12 đạt, 1 lỗi fixture promotion maximumUsesPerUser=2 trái contract chỉ cho1; handler chưa chạy ở lỗi đó. Sửa fixture trong đúng D7 đã duyệt, rerun riêng D7 đạt. Không đổi production validation hoặc yêu cầu khai báo mới.
- D10 bổ sung tranh chấp hai outcome khác nhau và receipt cùng messageId giữa hai order; D13 bổ sung return/retry OrderCompleted sau khi bỏ binding trong broker test. Các bổ sung nằm trong thân method D10/D13 đã duyệt, không thêm method/type.
- Full verify cuối: **364 unit +94 integration**, failures/errors/skips=0, **BUILD SUCCESS**, kết thúc **2026-10-29 21:28:58 +07**, 2 phút47 giây. Log `services/booking-service/target/issuance-verify.log`; các log checkpoint `issuance-unit.log`, `issuance-it.log`, `issuance-repair.log` trong cùng target.
- Giai đoạn verify ở trên dùng PostgreSQL/Rabbit Testcontainers hiện có; chỉ test DB/queue cách ly bị thay đổi. Smoke dùng connection thật119 nhưng listener/worker/declarations tắt. Giai đoạn đó chưa gửi synthetic result vào hai queue nghiệp vụ119; kiểm tra runtime bổ sung được ghi bên dưới. Không xác nhận Payment/Ticket branch khác hoặc Saga/refund E2E.
- Không field/schema Order mới, không dependency/@Bean/test helper phát sinh; git diff check sạch. Chưa commit/push/deploy; không cập nhật ClickUp trong yêu cầu thực thi này.

Kiểm tra runtime theo yêu cầu tiếp theo của user, 29/10/2026:

- **10/10 case đạt** trên Booking JAR local8082 (PID11008, timezone UTC), Config Server8888, DB119 qua tunnel5432 và Rabbit119 với credential app. Tạo order/áp voucher/PurchaseSnapshot bằng HTTP và token BUYER/INTERNAL_SERVICE thật; gửi SalesConfigured/AcceptPayment/COMPLETED/FAILED vào exchange/queue nghiệp vụ hiện có.
- Case: COMPLETED + promotion/relay; replay same/new ID và hash conflict; FAILED QUANTITY + promotion; FAILED replay; STANDING; SEATED/R1 bảo vệ R2; event CANCELLED; sale closed; sai charge/count/queue-state; hai outcome đồng thời. Case concurrent quan sát COMPLETED thắng, FAILED đối nghịch vào DLQ. Đối chiếu DB state/counters/inbox/outbox và broker; không chỉ kiểm publish API trả thành công.
- Audit cuối: health UP, bốn queue được dùng có 1 consumer/queue và ready=unacked=0; 13 outbox published khớp 9 PaymentDecision +4 OrderCompleted persistent ở broker; 4 success-DLQ +1 failure-DLQ test message có reason rejected. Giữ nguyên 11 message cũ sales-configured.dlq; không purge. Giữ dữ liệu test để đối chiếu.
- Lượt kiểm tra ban đầu dừng script quan sát để tái sử dụng DB connection; lượt sau gặp một ngắt HTTP quản trị Rabbit ở case1 nên case2 phụ thuộc chưa chạy. Case3–10 đạt, chạy lại riêng1–2 đạt; không sửa production để vượt test. Nhật ký giữ các lượt trước trong bằng chứng.
- Bằng chứng: `services/booking-service/target/issuance-live119-20261029/results.json`, log Booking cùng thư mục; chi tiết ở TESTING.md. Chưa chạy producer/consumer Payment/Ticket, chưa chứng minh phát vé/refund/Saga E2E. Booking local đang chạy, chưa deploy lên119 hoặc commit/push task này.
