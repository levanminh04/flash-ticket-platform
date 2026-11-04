# Booking — Plan triển khai worker hết hạn order

- Phiên bản: **v0.3**. WRITING-PLANS: **APPROVED**. **EXECUTING-PLANS — COMPLETE**, gate APPROVED theo user “xác nhận” sau danh sách thực thi; T1–T5 hoàn tất lúc 10:59 +07 ngày 30/10/2026.
- Design: [spec v0.4](../specs/2026-10-30-booking-order-expiry-design.md), kế thừa v0.2 BRAINSTORMING APPROVED; user xác nhận bỏ M6 bằng “nếu v thì bỏ đi”.
- Task: [Worker tự hết hạn order và trả tài nguyên](https://app.clickup.com/t/z8qgvc9hdz). Deadline đã chốt: 30/10/2026, 23:00 Asia/Saigon.
- Checkout: `D:/DATN/flash-ticket-platform`. Đã triển khai T1–T5 theo danh sách duyệt; final verify BUILD SUCCESS, 372 unit + 106 integration tests, zero failures/errors/skips.
- Giữ ID ổn định; M6/M7 đã bỏ. Danh sách C1–C8, M1–M5, R1–R7, U1–U8/D1–D12 và các ngoại lệ đã duyệt ở pha WRITING-PLANS theo yêu cầu “triển khai theo plan đã duyệt ở trên”; **EXECUTING-PLANS APPROVED** theo user “xác nhận” sau danh sách thực thi. Không có class/method bổ sung so với spec.

## 1. Kết quả cần đạt và phần code bị tác động

Worker quét UUID của order PENDING_PAYMENT đến hạn, rồi gọi service qua Spring proxy để từng order có transaction riêng. Service khóa và kiểm lại dữ liệu, đọc DB clock sau khi chờ khóa; khi decisionAt >= expiresAt, trả phần HELD của inventory, purchase limit, promotion, reservation và allocations cùng với chuyển order EXPIRED. Nếu event đã CANCELLED, order chuyển CANCELLED. Luồng phải an toàn khi chạy lặp, chạy nhiều instance hoặc tranh khóa với AcceptPayment/áp mã/chuẩn bị snapshot.

| Loại thay đổi | Class/method và tác động |
|---|---|
| Class production mới | C1 OrderExpiryWorker: lập lịch, query batch, giữ cursor, gọi service và log lỗi từng order sau rollback. Không có transaction bao cả batch. |
| Entry nghiệp vụ mới | M3/M4 expireOrder(UUID) trong OrderService/OrderServiceImpl: kiểm deadline/trạng thái và điều phối release từng order. |
| Validation allocation | Không tạo validateReservationAllocations; giữ các kiểm tra allocation trong settlePendingPayment. Expiry dùng các guard release hiện có của helper inventory/limit. |
| Code cũ phải sửa | R6 settlePendingPayment giữ kiểm allocation tại chỗ. R7 updatePromotionUsageForPaymentDecision dựa trên usage/inventory; phần so voucher với frozen snapshot chuyển về R6. Giữ chữ ký R6/R7, phân xử charge/deadline và kết quả PaymentDecision. |
| Code cũ dùng nguyên trạng | R2–R5 release inventory và purchase limit với target RELEASED; query khóa, DB clock, timeout, save/saveAll hiện có. Không thêm releaseHeldPromotion. |
| Query/schema/config mới | M5 query UUID theo state/cutoff/cursor có limit; @Index trong Order; cấu hình expiry và scheduler pool2. |
| Kiểm thử | C6 mới cho worker; thêm test ở C7/C8 hiện có. C7 dùng spy clock cho biên equality, query/transaction còn lại chạy thật. |

Phạm vi là expiry phần HELD của order chờ thanh toán. Buyer cancel API, event cancellation fan-out, refund và Payment/Ticket E2E thuộc task khác. Expiry không tạo message/inbox/outbox mới, không release phần PURCHASED/USED của order đã accepted.

## 2. Nguồn và phong cách triển khai

Đã đối chiếu source hiện tại: OrderRepository, OrderService, OrderServiceImpl.settlePendingPayment và các helper inventory/promotion, Order, BookingOutboxWorker, BookingPersistenceIT, RuntimeConfigurationTests.configuredRunner; central booking-service.yaml và pom.xml. Query repo hiện có trả scalar eventId, khóa order, dùng clock_timestamp và set_config lock_timeout. Order chưa có expiry index; scheduler mẫu đang dùng @Scheduled gọi service khác. RuntimeConfigurationTests đã có runner đọc central YAML.

Mẫu Shop App đã đọc trực tiếp: `services/impl/OrderService.updateOrder` cho constructor injection/find/check/setter/save; `components/JwtTokenUtil` cho @Value trên field cấu hình. Dependency là private final + @RequiredArgsConstructor; entity cập nhật qua setter/repository; giữ naming/package của FlashTicket. Không thêm mapper, DTO hoặc abstraction. Validation quan hệ dữ liệu/state nằm trong service, dùng BusinessException/ErrorCode hiện có.

**NGOÀI MẪU Shop App:** scheduler/cursor, @ConditionalOnProperty, pool2, pessimistic locks, @Transactional, @Index, @MockitoSpyBean, Testcontainers và concurrency tests. Áp dụng đúng cơ chế đã chốt trong spec; giữ Boot4.1.1/Java23 của đích. Tài liệu chính thức và ví dụ Spring Guides đã được đối chiếu tại spec §6; plan không thêm cơ chế framework mới.

## 3. Danh sách class và method trình duyệt

Alias `main/` = `services/booking-service/src/main/java/com/flashticket/booking_service/`; `test/` = `services/booking-service/src/test/java/com/flashticket/booking_service/`. Các đường dẫn dưới đây ghép với root checkout ghi ở đầu plan.

| ID | Class/file | NEW / EXISTING | Vai trò, field/annotation/API sinh | Mẫu hoặc ngoại lệ |
|---|---|---|---|---|
| C1 | `main/order/OrderExpiryWorker.java` — `OrderExpiryWorker` | NEW | `@Component`, `@RequiredArgsConstructor`, `@ConditionalOnProperty(prefix="booking.expiry",name="enabled",havingValue="true",matchIfMissing=false)`; final `OrderRepository orderRepository`, `OrderService orderService`; `@Value("${booking.expiry.batch-size:20}") int batchSize`; `UUID lastOrderId`. Lombok chỉ sinh constructor M1; không getter/setter/builder/equals/hashCode/toString. | Constructor/field @Value theo Shop App; scheduler/cursor theo nhu cầu ngoài mẫu, dùng cách tổ chức BookingOutboxWorker. |
| C2 | `main/order/service/OrderService.java` | EXISTING | Thêm M3; không interface mới. | Shop App IOrderService; giữ tên/package đích. |
| C3 | `main/order/service/impl/OrderServiceImpl.java` | EXISTING | Thêm M4; giữ kiểm allocation trong settlePendingPayment; chuyển kiểm promotion frozen về R6 và sửa R7 để Payment/expiry dùng chung. Giữ constructor, dependency và annotation hiện có. | Shop App OrderService.updateOrder: tìm/kiểm/setter/save. Khóa/transaction là ngoại lệ cần nghiệp vụ. |
| C4 | `main/order/repository/OrderRepository.java` | EXISTING | Thêm query M5; không storage wrapper/native query mới. | Spring Data JPQL + Pageable, cùng repo đích. |
| C5 | `main/entity/Order.java` | EXISTING | Chỉ bổ sung @Index vào @Table; không field/API Lombok mới. | Entity JPA theo Shop App; composite index là bổ sung của task. |
| C6 | `test/order/OrderExpiryWorkerTests.java` | NEW | Package-private class, `@ExtendWith(MockitoExtension.class)`; mocks OrderRepository/OrderService và `@InjectMocks OrderExpiryWorker`; dùng ReflectionTestUtils cho field cấu hình, không helper/setup/constructor explicit mới. | Test theo JUnit/Mockito hiện có, ngoài mẫu nghiệp vụ Shop App. |
| C7 | `test/persistence/BookingPersistenceIT.java` | EXISTING | Thêm D1–D12; reuse fixture/container/TransactionTemplate và constructor hiện có. Đổi field `@Autowired OrderRepository orders` thành `@MockitoSpyBean OrderRepository orders` để kiểm biên clock ở D8; chỉ stub clock trong ca biên, các query/khóa còn lại chạy thật. | Kiểm PostgreSQL/transaction thật theo suite hiện tại; test spy là ngoại lệ trình duyệt cùng danh sách. |
| C8 | `test/config/RuntimeConfigurationTests.java` | EXISTING | Thêm U7 kiểm bật/tắt bean và U8 kiểm scheduler auto-config; dùng configuredRunner có sẵn, không @Bean hoặc configuration class mới. | ApplicationContextRunner đã có. |

| ID | Class | Constructor/method đầy đủ | NEW / REUSE | Cách viết và lý do |
|---|---|---|---|---|
| M1 | C1 | `public OrderExpiryWorker(OrderRepository orderRepository, OrderService orderService)` | NEW, Lombok | Constructor injection, chỉ hai dependency final. |
| M2 | C1 | `public void expireDueOrders()` | NEW | @Scheduled như T4; không @Transactional/@Async; batch/cursor/catch từng ID sau service return. |
| M3 | C2 | `public abstract void expireOrder(UUID orderId)` | NEW | Entry nghiệp vụ cho worker qua proxy; không trả response DTO. |
| M4 | C3 | `public void expireOrder(UUID orderId)` | NEW | @Override, @Transactional; luồng T3, không declared checked throws. |
| M5 | C4 | `public abstract List<UUID> findExpiredOrderIds(OrderState state, Instant cutoff, UUID afterOrderId, Pageable pageable)` | NEW | @Query JPQL T2; @Param("state"), @Param("cutoff"), @Param("afterOrderId"); Pageable là special parameter; không @Lock/count. |
| R1 | C4 | `public abstract Optional<UUID> findEventIdById(UUID orderId)`; `public abstract Optional<Order> findLockedById(UUID id)`; `public abstract Instant currentDatabaseTime()`; `public abstract String configureTransactionLockTimeout(String timeout)` | REUSE | Không đổi chữ ký/annotation, giữ native clock/timeout đã có. |
| R2 | C3 | `private void updateQuantityInventoryForPaymentDecision(Order lockedOrder, Reservation storedReservation, List<TicketTypeInventory> lockedInventories, ReservationState targetState)` | REUSE | Truyền RELEASED; giữ tên hiện tại để không refactor lan rộng. |
| R3 | C3 | `private void updateStandingInventoryForPaymentDecision(Order lockedOrder, Reservation storedReservation, SectorInventory lockedSector, ReservationState targetState)` | REUSE | Truyền RELEASED. |
| R4 | C3 | `private void updateSeatedInventoryForPaymentDecision(Order lockedOrder, Reservation storedReservation, List<SeatInventory> lockedSeats, ReservationState targetState)` | REUSE | Truyền RELEASED; giữ guard reservation/generation. |
| R5 | C3 | `private void updatePurchaseLimitForPaymentDecision(Order lockedOrder, Reservation storedReservation, PurchaseLimit lockedPurchaseLimit, ReservationState targetState)` | REUSE | Truyền RELEASED. |
| R6 | C3 | `private PaymentDecision settlePendingPayment(Order lockedOrder, Reservation storedReservation, EventSalesSnapshot storedSnapshot, AcceptPayment requestedPayment)` | EXISTING, sửa thân | Giữ kiểm allocation tại chỗ, kiểm snapshot, phân xử charge/deadline và inbox/outbox. Nhận phần kiểm promotion ID khớp frozen snapshot từ helper R7 trước khi gọi helper chung. |
| R7 | C3 | `private void updatePromotionUsageForPaymentDecision(Order lockedOrder, PromotionUsage storedUsage, PromotionInventory lockedPromotion, ReservationState targetState)` | EXISTING, sửa thân | Giữ chữ ký; kiểm dữ liệu qua usage/inventory theo spec §4/T1, không đọc frozen snapshot. Payment dùng COMMITTED hoặc RELEASED; expiry dùng RELEASED. Hướng thay đổi promotion đã được user đồng ý; không thêm helper release riêng. |

Tất cả production method trên là instance method, không checked throws. R1–R5 giữ chữ ký/annotation; R6/R7 sửa thân theo phạm vi đã nêu. API Lombok mới chỉ là constructor M1; C5 không thêm field/getter/setter/builder. C6 có constructor mặc định package-private do Java cấp; không viết constructor/setup/helper test mới. Không thêm @Bean, callback, anonymous/nested type hoặc dependency ngoài danh sách.

Test mới đều package-private, non-static, @Test. U1–U6 ở C6, U7–U8 ở C8; D1–D12 ở C7, kèm @Transactional(propagation=Propagation.NOT_SUPPORTED) để gọi service qua proxy với commit thật. D-methods throws Exception như bảng; U8 cũng throws Exception. Annotation và phong cách test là NGOÀI MẪU, theo suite đích.

| ID | Chữ ký đầy đủ | Hành vi cần chứng minh |
|---|---|---|
| U1 | `void pollsExpiredOrderIdsUsingDatabaseTime()` | Dùng cutoff từ DB, state pending, batch giới hạn, chỉ chuyển UUID vào service. |
| U2 | `void continuesAfterOrderExpiryFailure()` | Service/commit lỗi một ID không bỏ các ID còn lại hoặc ghi nhận thành công giả. |
| U3 | `void advancesAndWrapsExpiryCursorAfterFailures()` | Batch lỗi đủ size không kẹt đầu hàng; cursor tiến và quay lại các ID lỗi khi hết vòng; tạo worker mới quét từ đầu. |
| U4 | `void preservesExpiryCursorWhenCandidateQueryFails()` | Lỗi DB khi query không làm bỏ qua ID; lần sau dùng lại cursor. |
| U5 | `void stopsExpiryBatchWhenThreadIsInterrupted()` | Dừng giữa ID, không tiến cursor qua phần chưa thử; dọn interrupted flag của test trong finally. |
| U6 | `void rejectsNonPositiveExpiryBatchSize()` | Không truy DB khi batch-size không hợp lệ. |
| U7 | `void enablesOrderExpiryWorkerOnlyWhenConfigured()` | Missing/false không tạo bean; true tạo bean với mock dependencies bằng API runner có sẵn; không kết nối DB/server. |
| U8 | `void configuresTwoSchedulerThreadsForBookingWorkers() throws Exception` | Boot auto-config thực tạo ThreadPoolTaskScheduler pool2 từ YAML; dùng task chặn có latch để chứng minh job khác vẫn chạy. Không kiểm chỉ bằng so chuỗi cấu hình, không tạo scheduler @Bean/helper mới. |
| D1 | `void selectsExpiredPendingOrderIdsWithBoundedCursor() throws Exception` | JPQL thực trên PostgreSQL: cutoff inclusive, UUID cursor/limit, không chọn terminal/chưa đến hạn. |
| D2 | `void expiresUnfrozenQuantityOrderAndReleasesHeldResources() throws Exception` | Đơn không snapshot, không callback vẫn EXPIRED; tất cả allocation/limit/type pool đúng, không inbox/outbox mới. |
| D3 | `void expiresFrozenOrderAndReleasesHeldPromotion() throws Exception` | Có frozen/promotion: trả HELD một lần qua R7, giữ used/purchased/tiền và frozen snapshot. Bao gồm promotion đã hết hiệu lực; helper không phụ thuộc frozen snapshot. |
| D4 | `void expiresStandingOrderWithoutChangingTicketTypePool() throws Exception` | Chỉ trả pool sector, tổng allocation đúng. |
| D5 | `void expiresSeatedOrderWithoutReleasingAnotherReservation() throws Exception` | HELD→AVAILABLE/hidden→UNAVAILABLE; replay sau R2 giữ ghế không thay R2; ownership lệch gây rollback. |
| D6 | `void skipsUnexpiredAndTerminalOrdersDuringExpiry() throws Exception` | Chưa đến hạn, ISSUING, COMPLETED, EXPIRED, CANCELLED, ISSUANCE_FAILED không đổi resource/updatedAt. |
| D7 | `void serializesConcurrentExpiryForTheSameOrder() throws Exception` | Hai transaction cùng ID chỉ trả một lần; không counter âm. |
| D8 | `void arbitratesExpiryAgainstAcceptPaymentAfterLockWait() throws Exception` | Khống chế hai thứ tự giữ khóa/commit; Payment thắng trước hạn → ISSUING, expiry thắng → LATE; chờ qua deadline không nhận ACCEPTED sai; gồm biên bằng hạn với clock có kiểm soát tại test. |
| D9 | `void rollsBackExpiryWhenReservationDataConflicts() throws Exception` | Guard còn giữ: accepted metadata, reservation không HELD, limit/inventory thiếu hoặc counter không đủ, seat ownership sai, usage USED/sai buyer/generation → rollback toàn bộ. Không thêm kỳ vọng expiry kiểm tổng allocation theo item. |
| D10 | `void rollsBackExpiryWhenCommitFailsAndRetriesSafely() throws Exception` | Gây lỗi constraint/commit thật bằng fixture hiện có; order/counter cùng rollback, lời gọi transaction mới có thể xử lý lại. |
| D11 | `void preservesEventCancellationDuringOrderExpiry() throws Exception` | Đơn đến hạn + snapshot CANCELLED → CANCELLED và release một lần, không gate theo sale window; không xử lý đơn còn hạn/fan-out cả event. |
| D12 | `void serializesExpiryWithPromotionAndSnapshotPreparation() throws Exception` | Áp mã/chuẩn bị snapshot tranh chấp expiry: toàn bộ tác động được commit theo thứ tự khóa, không usage HELD mồ côi hoặc freeze thành công sau expiry. |

## 4. Các bước triển khai sau khi đủ gate

### T1 — Dùng chung helper promotion

- Item: C3, R2–R7; kiểm hồi quy qua C7. Phụ thuộc: duyệt plan và gate EXECUTING-PLANS trước khi triển khai.
- File: `main/order/service/impl/OrderServiceImpl.java`; test hiện có tại `test/persistence/BookingPersistenceIT.java`.
- Giữ nguyên kiểm tra allocation/lifecycle/COMMITTED và item/giá/ghế khớp snapshot trong R6; không trích helper hoặc chép bộ kiểm tra này sang expiry. Spec §4 ghi rõ phạm vi đã bỏ theo yêu cầu user.
- R7 không đọc frozen snapshot. Usage lấy theo reservation đang xử lý; đối chiếu promotion ID/event với inventory, event/buyer/currency/expiry/discount với order, generation>0, HELD, held đủ. usage=null chỉ hợp lệ khi discount=0. COMMITTED: held--, used++, usage USED; RELEASED: held--, used giữ nguyên, usage RELEASED. Không gate release bằng thời gian hiệu lực promotion.
- R6 kiểm sự hiện diện promotion khớp snapshot và ID voucher snapshot khớp usage/inventory trước khi gọi R7. Giữ guard nhánh reservation đã RELEASED để không giảm counter lần nữa. Không dời phân xử acceptedCharge/deadline hoặc ghi inbox/outbox vào helper chung.
- Xác minh khi execute: các test AcceptPayment hiện có vẫn cho ACCEPTED/LATE/CANCELLED đúng, replay charge đã accepted không đổi do deadline, snapshot mismatch rollback. Nếu cần thêm method test ngoài U/D trong bảng thì trình delta trước.

### T2 — Query ứng viên có giới hạn và index

- Item: C4/C5/C7, M5, R1, D1. Phụ thuộc: gate; độc lập logic T1.
- File: `main/order/repository/OrderRepository.java`, `main/entity/Order.java`, `test/persistence/BookingPersistenceIT.java`.
- Thêm @Query JPQL chọn UUID với state=:state, expiresAt<=:cutoff, (:afterOrderId is null or id>:afterOrderId), order by id asc; @Param cho ba tham số, Pageable để giới hạn. Không @Lock/count/offset tăng dần; không nạp entity ở bước quét.
- Thêm @Index(name="tbl_orders_expiry_idx", columnList="lifecycle_state,expires_at,id") vào @Table hiện có. Giữ ddl-auto:update; không thêm migration.
- D1 dùng PostgreSQL test để kiểm cutoff inclusive, cursor UUID theo thứ tự DB, batch limit và loại terminal/chưa đến hạn. Kiểm index thực trong schema test có tạo DDL; nếu suite dùng schema SQL sẵn thì dùng DDL entity trên DB test riêng khi nghiệm thu, không sửa schema server. Index giúp lọc, không khẳng định loại bỏ sort ID hoặc đã đo hiệu năng.

### T3 — Transaction hết hạn từng order

- Item: C2/C3/C7, M3/M4, R1–R7, D2–D12. Phụ thuộc: T1; T2 cần cho vòng worker, không cần để gọi service trực tiếp.
- File: `main/order/service/OrderService.java`, `main/order/service/impl/OrderServiceImpl.java`, `test/persistence/BookingPersistenceIT.java`.
- M4 @Override/@Transactional REQUIRED: null ID là INVALID_REQUEST; cấu hình lock timeout, lấy scalar eventId, snapshot PESSIMISTIC_READ rồi order PESSIMISTIC_WRITE. Order không còn thì return; thiếu snapshot CONFIG_NOT_READY. Không nạp Order trước lúc chờ khóa.
- Sau order lock: không còn pending thì bỏ qua; pending có acceptedChargeId/acceptedAt hoặc reservation không HELD là conflict. Không thêm helper kiểm quan hệ/tổng quantity allocation; dùng các guard release hiện có của R2–R5. Không yêu cầu paymentFrozenAt/frozen snapshot.
- Khóa purchase limit → inventory được chọn theo ID tăng dần → promotion inventory nếu có usage, theo thứ tự hiện tại của AcceptPayment. Đọc lại DB clock sau mọi bước có thể chờ khóa; chưa đến hạn return; bằng hạn xử lý.
- Gọi R2/R3/R4 theo đúng QUANTITY/STANDING/SEATED, R5 và R7 với RELEASED. Qty/standing giảm held, giữ purchased; seat phải thuộc reservation hiện tại, trả AVAILABLE/hidden UNAVAILABLE, xóa currentReservationId, giữ generation. Reservation/allocations HELD→RELEASED; order EXPIRED hoặc CANCELLED theo marker event; updatedAt=decisionAt. Giữ tiền và frozen metadata.
- Counter thiếu/ownership sai gây BusinessException và rollback; không clamp, không catch lỗi commit trong service. Không phát PaymentDecision/sự kiện expiry.
- D2 bao gồm order chưa freeze, không voucher và order đã giữ voucher nhưng chưa freeze (áp mã bằng API hiện có): chứng minh R7 không cần snapshot. D3 kiểm có frozen/voucher và promotion hết hiệu lực. D4–D6 kiểm mọi mode/terminal/replay/R2 giữ lại ghế. D7–D12 kiểm cạnh tranh, marker hủy, dữ liệu sai và rollback.
- D8 dùng latch/transaction độc lập cho cả hai thứ tự thắng khóa với Payment, gồm chờ qua deadline. Chỉ ca equality stub currentDatabaseTime bằng doReturn trên @MockitoSpyBean OrderRepository; giữ query/khóa/proxy thật. Spy reset giữa test; không thêm Clock bean.
- D9 kiểm các guard còn giữ theo bảng; không tạo kỳ vọng kiểm quan hệ/tổng quantity allocation đã bỏ khỏi expiry. Snapshot mismatch vẫn được test hồi quy ở Payment. D10 gây lỗi constraint/commit trên DB test, xác nhận transaction mới nhìn toàn bộ dữ liệu rollback và thử lại thành công. D12 áp mã/prepare snapshot tranh expiry không để usage mồ côi hoặc freeze sau expiry.
- Xác minh: D2–D12 cùng test Payment hiện có pass; không có inbox/outbox expiry, không thay inventory PURCHASED của đơn accepted. Ca lỗi phải kiểm state của cả order/reservation/allocation/counter sau rollback.

### T4 — Worker, cấu hình và scheduler

- Item: C1/C4/C6/C8, M1/M2/M4/M5, R1, U1–U8. Phụ thuộc: T2/T3.
- File mới: `main/order/OrderExpiryWorker.java`, `test/order/OrderExpiryWorkerTests.java`; sửa `test/config/RuntimeConfigurationTests.java` và `infrastructure/config-server/src/main/resources/config/booking-service.yaml`.
- Worker theo annotation/field trong bảng C1; constructor M1 do Lombok sinh. M2 @Scheduled fixedDelay PT5S, không @Transactional/@Async. batchSize<=0 ném IllegalArgumentException trước query; cutoff lấy bằng DB clock; PageRequest.of(0,batchSize), state PENDING_PAYMENT.
- Duyệt UUID, gọi orderService.expireOrder qua proxy. Sau mỗi ID đã thử, kể cả lỗi, tiến lastOrderId; log orderId và ErrorCode.from(exception), tiếp tục ID khác sau rollback. Batch rỗng/ngắn reset cursor; query/cutoff lỗi giữ cursor; thread interrupted dừng trước ID chưa thử. Cursor là bộ nhớ điều phối, không receipt thành công; restart quét lại an toàn.
- YAML: booking.expiry.enabled=true, poll-interval=PT5S, batch-size=20; spring.task.scheduling.pool.size=2. Giữ reservation.lock-timeout=5s. Bean matchIfMissing=false. Dùng scheduler auto-config sẵn có, không custom @Bean hay virtual threads.
- U1–U6 kiểm batch/cursor/error/restart/interruption; ReflectionTestUtils inject property trong unit test. U7 kiểm missing/false/true: dùng runner không nạp central YAML cho ca missing để không bị enabled=true che kết quả, dùng runner central YAML cho ca cấu hình thực. U8 dùng runner central YAML + scheduler auto-config, latch chứng minh hai job có thể chạy đồng thời; đóng context/executor và giải phóng latch trong finally.
- Xác minh: U1–U8 pass; lỗi một order không chặn batch còn lại, nhóm ID lỗi đầu hàng không gây bỏ đói phần sau; outbox không bị chia duy nhất một thread với expiry. Không coi pool2 là bảo đảm độ trễ khi DB bận.

### T5 — Kiểm chứng gộp và bàn giao

- Item: C1–C8, M1–M5, R1–R7, U1–U8/D1–D12. Phụ thuộc: T1–T4 hoàn tất.
- Tắt booking.expiry.enabled trong cấu hình test/context smoke có thể nạp central YAML; rà `services/booking-service/src/test/resources/` và `test/BookingServiceApplicationTests.java` trước khi sửa property test hiện có. Không thêm Java method/type ở bước này.
- Chạy bằng JDK23, từ checkout root, với môi trường test hiện có; PostgreSQL/broker IT dùng Testcontainers của suite. Không đọc/in secret ra log. Chỉ chạy các lệnh dưới đây lúc execute, không chạy trong pha plan:

```powershell
rtk mvn -f services/booking-service/pom.xml -B -ntp '-Dtest=OrderExpiryWorkerTests,RuntimeConfigurationTests,AcceptPaymentListenerTests' test
rtk mvn -f services/booking-service/pom.xml -B -ntp -P integration '-Dit.test=BookingPersistenceIT' verify
rtk git diff --check
```

- Lệnh verify chạy lifecycle unit tests, format/static checks và persistence IT đã chọn; đọc kết quả Maven của lần chạy, không cộng XML cũ. Nếu phát hiện ảnh hưởng ngoài persistence thì chạy suite IT liên quan; không lặp full suite khi không có thay đổi/lỗi mới. Môi trường/container thiếu thì ghi rõ chưa xác minh, không thay bằng test giả cho semantics PostgreSQL.
- Ghi kết quả thực, số test/lỗi/giới hạn và đường dẫn log vào `services/booking-service/TESTING.md`; cập nhật `services/booking-service/HANDOFF.md` về worker/config, state guards và phạm vi còn lại. Đây là tài liệu bàn giao hiện có, không tạo API Java mới.
- Chỉ báo hoàn tất khi test/format/static checks yêu cầu pass, không còn khai báo ngoài bảng, query/index được đối chiếu và code Payment giữ đầy đủ guards. Không coi các số test 29/10 trong TESTING.md là kết quả của task này.

## 5. Hồ sơ duyệt và sẵn sàng thực thi

- **WRITING-PLANS v0.3: APPROVED** cho C1–C8, M1–M5, R1–R7, U1–U8/D1–D12, annotations/Lombok và ngoại lệ đã liệt kê. Nguồn: user gọi skill executing-plans với yêu cầu “triển khai theo plan đã duyệt ở trên”.
- Delta bỏ M6: **APPROVED**, nguồn user “nếu v thì bỏ đi”. Bỏ method, lời gọi và việc trích/chép kiểm tra allocation sang expiry; giữ kiểm tra Payment hiện có và guard release của helper inventory/limit. D9 đồng bộ phạm vi, không thêm type/method. D2 giữ coverage voucher chưa freeze trong cùng method. M7 vẫn đã bỏ.
- **EXECUTING-PLANS: APPROVED / COMPLETE** cho chính danh sách trên; nguồn user “xác nhận” sau khi agent trình danh sách thực thi. T1–T5 hoàn tất, kết quả kiểm chứng ghi tại §6.
- Đối chiếu convention trước gate thực thi: dùng OrderRepository.currentDatabaseTime/configureTransactionLockTimeout/findLockedById và helper inventory/limit hiện có; BookingOutboxWorker là mẫu scheduler gọi service qua proxy. Chỉ thêm worker/entry expiry/query vì chưa có cơ chế tự hết hạn khi không có Payment callback. Không tạo helper validation đã bỏ; kiểm allocation của Payment giữ tại chỗ. R7 hiện đọc frozen snapshot ở OrderServiceImpl dòng 837, nên chuyển kiểm promotion frozen về R6 và dùng usage/inventory trong R7 như đã duyệt để phục vụ expiry chưa freeze. Giữ constructor injection, field @Value, transaction riêng từng order và AUTO ACK của listener hiện tại; không tác động server/ClickUp.
- Khi phát sinh chữ ký/type/annotation/cách viết mới, trình delta cụ thể, đồng bộ plan và chờ duyệt phần phụ thuộc. Không tự thêm helper/test setup vì thuận tiện, không tự deploy hoặc sửa ClickUp từ plan.

## 6. Kết quả thực thi

- T1 COMPLETE: R7 dùng usage/inventory; R6 giữ kiểm allocation tại chỗ và nhận kiểm promotion frozen từ helper. M6/M7 không được tạo.
- T2 COMPLETE: query UUID JPQL/cursor/Pageable và @Index đã thêm. D1 kiểm cutoff/cursor/limit trên PostgreSQL17 và chạy DDL index từ annotation trên schema test SQL, đối chiếu pg_indexes.
- T3 COMPLETE: expireOrder transaction riêng, tái sử dụng guard/resource helpers, DB clock sau khóa, terminal skip, EXPIRED/CANCELLED, rollback và không outbox mới. D2–D12 pass.
- T4 COMPLETE: worker/cursor/log lỗi từng ID, central expiry config PT5S/batch20/pool2, test isolation enabled=false. U1–U8 pass; không thêm scheduler bean hay helper/type ngoài bảng.
- T5 COMPLETE: final command `rtk mvn -f services/booking-service/pom.xml -B -ntp -P integration -Dit.test=BookingPersistenceIT verify` với .env nạp an toàn vào child process: **372 unit + 106 integration tests**, zero failures/errors/skips, BUILD SUCCESS lúc **10:59:29 +07, 30/10/2026**. Spotless/SpotBugs/JaCoCo lifecycle checks pass; git diff --check pass.
- Log final: `services/booking-service/target/expiry-final-verify.log`. Lịch sử lỗi môi trường và sửa observer race cùng giới hạn schema/index ghi trong `services/booking-service/TESTING.md`; không dùng tổng từ XML cũ làm kết quả final.
- Hai observer race ban đầu timeout khi dùng pg_stat_activity; dùng pg_locks để quan sát trực tiếp lock chưa được cấp, giữ nguyên production locking. Focused repair 2/2 pass rồi full verify pass. Chỉ biên equality stub DB clock; các lock/race còn lại chạy thật.
- Giữ nguyên constraint hidden seat của SQL test: không tạo fixture hidden+HELD trái constraint; kiểm normal release, replay giữ holder mới và rollback khi seat hiện tại không còn HELD. Index DDL đã kiểm trên DB test, chưa kiểm auto schema update trên server119.
- Đã cập nhật TESTING.md/HANDOFF.md. Unit context smoke chỉ mở kết nối Rabbit119 như trước; DB/broker business integration dùng Testcontainers riêng. Không push/deploy, không thay business topology/queue119 hay trạng thái ClickUp; Payment/Ticket E2E ngoài scope.
