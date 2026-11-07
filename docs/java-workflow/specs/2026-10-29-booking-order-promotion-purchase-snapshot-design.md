# Booking — Áp voucher vào order và chuẩn bị PurchaseSnapshot

- Phiên bản: v0.2 — nghiệp vụ USER_CONFIRMED; đã chuyển sang thực thi theo plan được duyệt.
- Nghiệp vụ đã chốt: GOV-156/157 và hướng giữ order/cố định tiền khi Payment yêu cầu snapshot ngày 29/10. Các nhãn PENDING bên dưới lưu trạng thái bản thiết kế ban đầu; nguồn xác nhận khai báo và kết quả thực thi được ghi trong [plan §12–13](../plans/2026-10-29-booking-order-promotion-purchase-snapshot-plan.md), gồm câu user “ok đồng ý, xác nhận triển khai”.
- Task: [API áp dụng voucher và cung cấp PurchaseSnapshot cho Payment](https://app.clickup.com/t/z8qgvc7w2r).
- Baseline đối chiếu khi thiết kế: `dev/son` tại `4ff2886`, cùng delta tài liệu chưa commit. Sau yêu cầu lập spec/plan, user đã duyệt triển khai trên cùng nhánh; xem nhật ký thực thi trong plan.

## 1. Phạm vi và căn cứ

Theo [ảnh ClickUp 61 bước](../../evidence/use-case-diagrams/2026-10-29-clickup-booking-flow.md), [B13 §3–4](../../architecture/B13-api-and-event-contracts.md), [B14 §2](../../architecture/B14-main-flow-sequences.md):

1. FE xác nhận voucher → Booking áp mã vào order, trả tổng tiền mới (9–14).
2. FE bắt đầu thanh toán tại Payment → Payment gọi Booking một lần để lấy PurchaseSnapshot (15–21).
3. GOV-157 bổ sung: lần gọi nội bộ là POST, gộp kiểm đơn + cố định tiền + lưu/trả snapshot; không có API freeze riêng.
4. Payment lưu snapshot + attempt và trả dữ liệu thanh toán (22–26), thuộc task Payment.

Không triển khai adapter cổng/QR, PaymentAttempt, AcceptPayment, phát vé, hủy/expiry worker, tháo/đổi voucher, Redis lock, outbox start hoặc API GET order tổng quát. Hạn giữ chỗ vẫn 15 phút từ lúc tạo đơn; không gia hạn khi áp mã hoặc chuẩn bị snapshot. Có expiresAt không đồng nghĩa đã có worker giải phóng.

Ticketbox có API voucher riêng trong [frontend công khai](https://ticketbox.vn/_next/static/chunks/7853.7ce4d286dcf0917e.js): redeem và apply; chưa quan sát checkout đăng nhập/runtime nên không suy ra cơ chế khóa tiền của backend Ticketbox.

## 2. Hiện trạng và hướng ít thay đổi nhất

- Có `OrderController`, `InternalOrderController`, `OrderService/Impl`, `PromotionService/Impl` và các repository; ngoài createOrder, phần nghiệp vụ này chưa có implementation.
- Có `ApplyPromotionRequest`, `FreezePaymentRequest`, `OrderView`, `PurchaseSnapshot` cùng nested DTO, mapper và ErrorCode. **Không tạo class production mới**; tên FreezePaymentRequest giữ lại để tái sử dụng, không có nghĩa tạo endpoint freeze riêng.
- Có `PromotionInventory`, `PromotionUsage`, các field frozen trên Order và `@Version`; không thêm bảng/cột/migration. Không xóa field/validation snapshot của Booking/Ticket.
- DDL baseline có unique active usage theo reservation và theo promotion/buyer. Chưa kiểm schema server trong lượt này; phải xác minh constraint/index thật khi test, không coi DDL file là bằng chứng DB live đã có.
- Dùng controller → service → Spring Data repository, constructor injection, setter + save, BusinessException(ErrorCode). Không thêm generic service, mapper mới, query native mới, EntityManager trong application hoặc annotation retry cho hai use case này.

Mẫu Shop App đã đọc: `controllers/OrderController.java` và `services/impl/OrderService.java` tại `C:/Users/sondimid/IdeaProjects/shopapp-backend/shopapp-backend/shopapp/src/main/java/com/project/shopapp/`. Học cách controller chuyển DTO tới service và service gọi repository trực tiếp; giữ global exception của Booking thay cho catch mọi Exception như một số đoạn mẫu.

## 3. Hai API đề xuất — PENDING tên route/chữ ký

| API | Quyền/input | Thành công |
|---|---|---|
| `POST /api/v1/orders/{orderId}/promotion` | JWT BUYER; subject từ token, body `{"code":"SAVE10"}` qua ApplyPromotionRequest | 200 OrderView: subtotal/discount/total mới; items và expiresAt giữ nguyên |
| `POST /internal/v1/orders/{orderId}/purchase-snapshot` | JWT INTERNAL_SERVICE; body `{"buyerSubject":"..."}` qua FreezePaymentRequest; buyerSubject do Payment lấy từ JWT buyer đã xác thực | 200 PurchaseSnapshot sau commit Booking |

API nội bộ không đi qua Gateway công khai. Không nhận giá/discount/owner tùy ý từ FE. Order không tồn tại hoặc không thuộc buyer trả ORDER_NOT_FOUND, tránh tiết lộ đơn người khác.

Tái sử dụng trust boundary hiện có: SecurityConfig yêu cầu INTERNAL_SERVICE; các service dùng client nội bộ được tin cậy. Quyền này hiện không phân biệt riêng Payment với từng service nội bộ khác; không tuyên bố đã có credential/role chỉ dành Payment. Không tạo client/secret hay sửa Keycloak trong task. Nếu cần cô lập riêng từng caller, trình delta bảo mật trước triển khai.

Không bắt buộc Idempotency-Key mới cho hai API này: áp cùng mã trên cùng order còn hợp lệ trả lại OrderView, không giữ thêm usage; chuẩn bị snapshot dùng snapshot đã lưu trên chính order. Đây là idempotency theo tài nguyên, không phải receipt lịch sử như createOrder.

## 4. Transaction, khóa và thời gian

Hai public service method dùng `@Transactional` mặc định readOnly=false, gọi từ controller qua Spring proxy. Tái sử dụng cấu hình lock-timeout 5s hiện có bằng field `@Value` và `OrderRepository.configureTransactionLockTimeout`; không thêm biến cấu hình khác. Cấu hình chỉ có hiệu lực trong transaction này, không đổi consumer/global session.

Thứ tự chung: đọc scalar eventId theo orderId + buyer → snapshot Event **PESSIMISTIC_READ** → order **PESSIMISTIC_WRITE** → reservation → promotion inventory **PESSIMISTIC_WRITE** khi áp mã → usage. Đường chuẩn bị snapshot chỉ đọc usage, không cần khóa promotion để trừ lượt lần nữa. Snapshot READ dùng chung với createOrder, consumer WRITE phải chờ; order khác nhau vẫn xử lý đồng thời, cùng promotion thì tranh chấp tại promotion inventory.

Đọc eventId bằng scalar query, không load Order vào persistence context trước lúc chờ khóa, tránh lấy lại entity cũ sau khi request khác commit. Sau khóa order luôn đọc lại trạng thái/voucher/freeze; sau khóa cuối cần thiết lấy `clock_timestamp()` qua method có sẵn để phân xử. Reservation hiện một/order, đọc sau khóa order; mọi writer reservation tương lai phải giữ cùng thứ tự/order lock.

Không gọi HTTP/RabbitMQ khi giữ transaction Booking. Commit lỗi phải đi ra controller/global handler; không catch rồi return thành công. Không catch lỗi unique rồi tiếp tục trong transaction rollback-only. Dùng save thông thường; không ép saveAndFlush cho hai luồng này.

## 5. Luồng áp voucher

1. Validate body, chuẩn hóa `requestedPromotionCode = trim().toUpperCase(Locale.ROOT)`; khóa snapshot/order như §4. Check snapshot không CANCELLED, owner, order PENDING_PAYMENT và reservation HELD, hạn chung còn hiệu lực.
2. Nếu paymentFrozenAt đã có → PRICE_FROZEN. Tìm usage HELD/USED theo reservation sau khóa order. Cùng mã chuẩn hóa, usage HELD và đơn còn hợp lệ → trả OrderView, không tăng counter; mã khác → ORDER_ALREADY_HAS_PROMOTION. Không cho đổi/tháo mã.
3. Tìm mã trong **danh sách promotion hiện hành của SalesConfiguration** theo code chuẩn hóa; không query JSON bằng native SQL. Không có → PROMOTION_NOT_FOUND. Nhiều promotion trùng code chuẩn hóa → SALES_CONFIGURATION_CONFLICT, không chọn ngẫu nhiên; chỉ thay đổi consumer validation nếu có delta được duyệt riêng.
4. Khóa PromotionInventory theo promotionId, check event ownership và config/version khớp snapshot hiện hành. Row thiếu → CONFIG_NOT_READY, không tự tạo. Check thời gian `startsAt <= decisionAt < endsAt`, `held + used < configuredLimit` và buyer chưa có usage HELD/USED của cùng promotion trên order khác.
5. Tính giảm từ subtotal đã lưu: FIXED lấy amount; PERCENT lấy subtotal × percent / 100 và HALF_UP đúng một lần. Ví dụ subtotal100005, 10% → discount10001, total90004. Total <=0 trả NON_POSITIVE_TOTAL; không tự cap giảm giá, không nhận total từ FE.
6. Tạo PromotionUsage: reservation/event/buyer chính xác, generation=1 cho lượt đầu của reservation, state HELD, discount/VND, expiresAt bằng reservation/order. Tăng held thêm 1; used giữ nguyên. Lưu usage + inventory + order discount/total/updatedAt cùng transaction, trả OrderMapper.toView(order).

maximumUsesPerUser hiện bằng 1 theo contract/DDL; không thêm cơ chế quota mới. Promotion inventory lock serialize hai order cùng mã, nên exists usage sau khóa có hiệu lực khi mọi apply writer dùng giao thức này; unique/constraint DB là hàng rào cuối. Usage HELD quá hạn vẫn chiếm lượt nếu worker chưa release: không tự bỏ qua hoặc giảm counter trong task này. Không có thao tác release rồi áp lại trên cùng reservation trong phạm vi này; nếu dữ liệu lịch sử vi phạm giả định generation=1 và DB từ chối, để transaction rollback, không bắt duplicate thành thành công.

## 6. Luồng chuẩn bị PurchaseSnapshot

1. Sau xác thực internal caller và validate buyerSubject, khóa snapshot/order; check buyer, event chưa CANCELLED, order PENDING_PAYMENT, reservation HELD và `decisionAt < expiresAt`.
2. Đơn còn payable và đã frozen → trả **đúng** frozenPurchaseSnapshot đã lưu, không đổi timestamp/version, không dựng lại từ config Event mới. Đơn đã hết hạn/hủy/ISSUING/COMPLETED không được dùng snapshot cũ để mở attempt mới. Payment phải replay attempt đã có trước khi yêu cầu tạo attempt mới.
3. Lần đầu: check nhất quán order/items/reservation/promotion usage và số tiền. Lấy giá, quantity, tên loại vé, selection/seat labels từ OrderItem đã lưu, không lấy giá mới. Promotion lấy từ usage đã giữ và snapshot config của inventory đang được bảo vệ bởi held; không tính lại giảm giá và không tăng held.
4. **Nghiệp vụ đã được user chốt:** không hủy hoặc từ chối order chỉ vì EventSalesSnapshot.sourceConfigVersion khác Order.sourceConfigVersion. Giá/tên vé/ghế lấy từ OrderItem đã lưu. Cấu hình thương mại khóa từ gửi duyệt theo B3 §2.3/FR-07; không tạo nghiệp vụ sửa giá sau mở bán, không thêm lịch sử config hoặc hủy hàng loạt. Event CANCELLED vẫn chặn theo luồng hủy riêng. Chưa kiểm implementation Event ở branch của đội khác: bảo đảm này là hợp đồng tích hợp phải kiểm, không phải bằng chứng runtime đã đạt.
5. Dựng PurchaseSnapshot hiện có: buyer/email từ order, items từ OrderItem, promotion null nếu không dùng; sourceOrderVersion="1" lần freeze đầu, frozenAt=decisionAt, expiresAt giữ nguyên. **Chi tiết kỹ thuật trình duyệt cùng plan:** metadata event lấy từ SalesConfiguration đang đọc dưới khóa; PurchaseSnapshot.sourceConfigVersion ghi version của cấu hình dùng tại lần freeze này, còn Order.sourceConfigVersion giữ nguồn lúc tạo đơn, không sửa OrderItem hoặc tính lại tiền theo config. Snapshot đã freeze được lưu nguyên bản, retry không dùng metadata/version mới hơn. Không tuyên bố hai version bắt buộc bằng nhau. Sort items theo orderItemId, seats theo seatId cho đầu ra ổn định; không tuyên bố sort là khóa DB.
6. Validate snapshot thực bằng Jakarta Validator (bao gồm nested DTO + quy tắc quan hệ có sẵn). Vi phạm dữ liệu nội bộ → CONFIG_NOT_READY; rollback, không lưu snapshot nửa vời. Lưu paymentFrozenAt, paymentSnapshotVersion=1, frozenPurchaseSnapshot, updatedAt cùng transaction; commit xong controller mới trả Payment. `rowVersion` do JPA quản lý, không dùng thay paymentSnapshotVersion.

Hai POST snapshot đồng thời trên cùng order → cùng một snapshot/version sau khi chờ khóa. Voucher thắng khóa/commit trước → snapshot có giảm giá; snapshot thắng trước → voucher PRICE_FROZEN. Không rollback freeze chỉ vì Payment chưa tạo attempt hoặc mất response; retry vẫn dùng bản cũ, expiry/cancel vẫn xử lý theo điều kiện hiện hành.

## 7. Ca lỗi và đầu ra

| Tình huống | Mã có sẵn / xử lý |
|---|---|
| Body sai/blank, JWT thiếu/quyền thiếu | Bean Validation/global handler; 400/401/403 |
| Đơn không có/không thuộc buyer | ORDER_NOT_FOUND, 404 |
| Đến đúng hoặc quá expiresAt | ORDER_EXPIRED, 409; không gia hạn hoặc hồi sinh |
| Sai lifecycle/reservation, event đã hủy | ORDER_NOT_PAYABLE, 409 |
| Giá đã freeze | PRICE_FROZEN, 409 |
| Mã không có/ngoài hiệu lực/hết lượt/buyer đã dùng/order có mã khác/total<=0 | Sáu ErrorCode promotion có sẵn, 422; không raw string, không INTERNAL_ERROR chung |
| Thiếu dữ liệu snapshot/inventory, dữ liệu quan hệ sai | CONFIG_NOT_READY, 503 |
| Chỉ config tăng version, dữ liệu vẫn hợp lệ | Cho chuẩn bị snapshot; không tự hủy hoặc trả conflict |
| Mã trùng trong config hoặc quan hệ cấu hình/inventory thực sự sai | SALES_CONFIGURATION_CONFLICT, 409; không chọn mã ngẫu nhiên |
| Chờ khóa/optimistic/DB lỗi | Để global handler hiện tại phân loại RESOURCE_BUSY/CONCURRENT_MODIFICATION/DATASTORE_*; không tự retry annotation hoặc biến mọi lỗi unique thành thành công |

Không thêm error enum/exception mới trong spec này. Guard/race dùng transaction thật; unit test/mock không đủ chứng minh.

## 8. Bảng class và phong cách cần duyệt — toàn bộ PENDING

Đường dẫn production tính từ `services/booking-service/src/main/java/com/flashticket/booking_service/`.

| ID | Class/file | Loại | Thay đổi / annotation và API sinh | Mẫu / ngoại lệ |
|---|---|---|---|---|
| C1 | order/controller/OrderController | EXISTING | Thêm apply route; final PromotionService, giữ @RequiredArgsConstructor; constructor mới ở M15 | Controller Shop App |
| C2 | order/controller/InternalOrderController | EXISTING | Thêm POST chuẩn bị snapshot; giữ constructor hiện có | Controller nội bộ có sẵn |
| C3 | promotion/service/PromotionService | EXISTING | Thêm method nghiệp vụ | Interface đơn giản |
| C4 | promotion/service/impl/PromotionServiceImpl | EXISTING | @Service/@RequiredArgsConstructor; thêm final OrderRepository, EventSalesSnapshotRepository, ReservationRepository, OrderMapper, giữ 2 repository promotion; field @Value lock-timeout | Service/setter/save + @Value Shop App; TX/lock khác CRUD |
| C5 | order/service/OrderService | EXISTING | Thêm method chuẩn bị snapshot | Interface có sẵn |
| C6 | order/service/impl/OrderServiceImpl | EXISTING | Giữ @Service/@RequiredArgsConstructor; thêm final PromotionUsageRepository và jakarta.validation.Validator; dùng @Value timeout cũ | Service có sẵn; không đổi createOrder |
| C7 | order/repository/OrderRepository | EXISTING | Thêm scalar lookup và derived query @Lock(PESSIMISTIC_WRITE); giữ DB clock/set_config có sẵn | Spring Data; scalar JPQL tránh entity cũ |
| C8 | promotion/repository/PromotionInventoryRepository | EXISTING | Thêm derived query @Lock(PESSIMISTIC_WRITE) | Cùng mẫu repository Booking |
| C9 | promotion/repository/PromotionUsageRepository | EXISTING | Thêm derived find/exists | Spring Data, không storage wrapper |
| C10 | reservation/repository/ReservationRepository | EXISTING | Thêm derived findByOrderId | Spring Data |
| C11 | DTO/entity/mapper/ErrorCode/SecurityConfig hiện có | REUSE | Không đổi field/getter/setter/constructor DTO, không thêm mapper/security class; @Valid tại controller, Validator tại snapshot boundary | Dùng lại record có sẵn, không tạo record mới |
| C12 | OrderControllerTests, BookingPersistenceIT | EXISTING TEST | Bổ sung method §10; controller test bao gồm InternalOrderController và mock PromotionService, IT import PromotionServiceImpl | Mẫu test hiện có; không tạo test framework/type mới |

## 9. Toàn bộ method/constructor production cần duyệt — PENDING

Interface method có visibility public ngầm định; implementation public có @Override. Không có throws nghiệp vụ checked. Các helper private không có annotation transaction riêng.

| ID | Class | Chữ ký đầy đủ đề xuất | Annotation / trách nhiệm |
|---|---|---|---|
| M1 | OrderController | `public ResponseEntity<OrderView> applyPromotionToOrder(Jwt jwt, UUID orderId, ApplyPromotionRequest request)` | @PostMapping("/{orderId}/promotion"); param @AuthenticationPrincipal, @PathVariable, @Valid @RequestBody |
| M2 | PromotionService | `OrderView applyPromotionToOrder(String buyerSubject, UUID orderId, ApplyPromotionRequest request)` | Interface |
| M3 | PromotionServiceImpl | `public OrderView applyPromotionToOrder(String buyerSubject, UUID orderId, ApplyPromotionRequest request)` | @Override @Transactional; toàn bộ apply transaction |
| M4 | InternalOrderController | `public ResponseEntity<PurchaseSnapshot> preparePurchaseSnapshot(UUID orderId, FreezePaymentRequest request)` | @PostMapping("/{orderId}/purchase-snapshot"); @PathVariable, @Valid @RequestBody; security filter internal |
| M5 | OrderService | `PurchaseSnapshot preparePurchaseSnapshot(String buyerSubject, UUID orderId)` | Interface |
| M6 | OrderServiceImpl | `public PurchaseSnapshot preparePurchaseSnapshot(String buyerSubject, UUID orderId)` | @Override @Transactional; cố định + lưu/trả snapshot |
| M7 | OrderServiceImpl | `private PurchaseSnapshot buildPurchaseSnapshot(Order lockedOrder, SalesConfiguration storedConfiguration, PromotionUsage storedPromotionUsage, Instant decisionAt)` | Mapping trực tiếp constructor record hiện có; usage có thể null; Validator validate trong M6 |
| M8 | PromotionServiceImpl | `private BigDecimal calculatePromotionDiscount(BigDecimal subtotal, PromotionConfig promotionConfiguration)` | FIXED/PERCENT, HALF_UP, không cap ngầm |
| M9 | OrderRepository | `Optional<UUID> findEventIdByIdAndBuyerSubject(UUID orderId, String buyerSubject)` | @Query JPQL `select o.eventId ...`; @Param cho hai param; không entity/lock ở bước scalar |
| M10 | OrderRepository | `Optional<Order> findLockedByIdAndBuyerSubject(UUID id, String buyerSubject)` | @Lock(PESSIMISTIC_WRITE), derived query; không fetch join collection |
| M11 | PromotionInventoryRepository | `Optional<PromotionInventory> findLockedByPromotionId(UUID promotionId)` | @Lock(PESSIMISTIC_WRITE), derived query |
| M12 | ReservationRepository | `Optional<Reservation> findByOrderId(UUID orderId)` | Derived query; gọi sau order lock |
| M13 | PromotionUsageRepository | `Optional<PromotionUsage> findByReservationIdAndStateIn(UUID reservationId, Collection<PromotionUsageState> states)` | Derived query; active usage HELD/USED |
| M14 | PromotionUsageRepository | `boolean existsByPromotionPromotionIdAndBuyerSubjectAndStateIn(UUID promotionId, String buyerSubject, Collection<PromotionUsageState> states)` | Check buyer usage sau promotion lock |
| M15 | OrderController | `public OrderController(OrderService orderService, PromotionService promotionService)` | Lombok sinh thay constructor cũ; không viết constructor tay |
| M16 | PromotionServiceImpl | `public PromotionServiceImpl(PromotionInventoryRepository promotionInventoryRepository, PromotionUsageRepository promotionUsageRepository, OrderRepository orderRepository, EventSalesSnapshotRepository eventSalesSnapshotRepository, ReservationRepository reservationRepository, OrderMapper orderMapper)` | Lombok sinh; 6 final dependency; @Value không đưa vào constructor |
| M17 | OrderServiceImpl | `public OrderServiceImpl(OrderRepository orderRepository, IdempotencyRequestRepository idempotencyRequestRepository, OrderMapper orderMapper, EventSalesSnapshotRepository eventSalesSnapshotRepository, PurchaseLimitRepository purchaseLimitRepository, TicketTypeInventoryRepository ticketTypeInventoryRepository, SectorInventoryRepository sectorInventoryRepository, SeatInventoryRepository seatInventoryRepository, ReservationRepository reservationRepository, JsonMapper objectMapper, PromotionUsageRepository promotionUsageRepository, Validator validator)` | Lombok sinh; giữ dependency cũ, thêm usage + Validator; không thêm getter/setter/builder |

Mẫu code khác Shop App: @Transactional qua proxy, pessimistic lock theo resource, clock_timestamp/set_config tái sử dụng, @Version, DTO record/nested DTO/MapStruct sẵn có, Jakarta Validator cho snapshot tạo nội bộ. Không thêm dependency, custom annotation, interceptor hoặc native query mới. Test có thể dùng TransactionTemplate/JdbcTemplate/EntityManager **đã có trong IT** để quan sát transaction; không mang EntityManager vào production.

## 10. Test method đề xuất — PENDING, chưa chạy

Tất cả method package-private `void`, @Test; controller methods `throws Exception`, IT methods `throws Exception` để đồng bộ thread/future. Không thêm test/helper/constructor/fixture type mới ngoài bảng nếu chưa duyệt delta; dùng fixtures/transaction helpers hiện có, setup ngắn trực tiếp trong test. Nếu dữ liệu setup cần helper mới, trình tên/chữ ký trước khi viết.

| ID | Class | Method | Kết quả bắt buộc |
|---|---|---|---|
| H1 | OrderControllerTests | `void appliesPromotionUsingAuthenticatedBuyer() throws Exception` | Subject từ JWT, code DTO, trả 200 tiền mới |
| H2 | OrderControllerTests | `void rejectsInvalidPromotionRequest() throws Exception` | Blank/thiếu code → 400, không gọi service |
| H3 | OrderControllerTests | `void rejectsUnauthorizedPromotionRequest() throws Exception` | Không JWT/không BUYER → 401/403 |
| H4 | OrderControllerTests | `void preparesSnapshotForInternalCaller() throws Exception` | INTERNAL_SERVICE gọi đúng POST, trả DTO |
| H5 | OrderControllerTests | `void rejectsInvalidSnapshotRequest() throws Exception` | Thiếu/blank buyerSubject → 400 |
| H6 | OrderControllerTests | `void rejectsBuyerOnInternalSnapshotRoute() throws Exception` | JWT BUYER không gọi được internal |
| H7 | OrderControllerTests | `void doesNotExposeSeparateFreezeOrSnapshotGet() throws Exception` | Không route freeze, GET không ghi snapshot |
| D1 | BookingPersistenceIT | `void appliesFixedAndPercentPromotionAtomically() throws Exception` | FIXED/PERCENT, HALF_UP, HELD/used/counter/expiry khớp |
| D2 | BookingPersistenceIT | `void rejectsPromotionBusinessViolations() throws Exception` | Sáu mã lỗi riêng; DB không đổi khi fail |
| D3 | BookingPersistenceIT | `void replaysSamePromotionWithoutHoldingAgain() throws Exception` | Code trim/case tương đương, chỉ một usage/counter |
| D4 | BookingPersistenceIT | `void rejectsPromotionForUnpayableOrForeignOrder() throws Exception` | Sai owner, hết hạn, frozen, sai reservation/state |
| D5 | BookingPersistenceIT | `void serializesLastPromotionUseAcrossOrders() throws Exception` | Hai buyer/order tranh lượt cuối: chỉ một thắng |
| D6 | BookingPersistenceIT | `void preventsSameBuyerHoldingPromotionOnTwoOrders() throws Exception` | Cùng buyer, hai order: một usage active |
| D7 | BookingPersistenceIT | `void rollsBackPromotionWhenTransactionFails() throws Exception` | Lỗi DB/commit không để held/usage/tiền cập nhật một phần |
| D8 | BookingPersistenceIT | `void preparesSnapshotForEverySelectionMode() throws Exception` | QUANTITY/STANDING/SEATED; items/seat/tiền từ order; optional promotion đúng |
| D9 | BookingPersistenceIT | `void replaysPreparedSnapshotWithoutChangingMoneyOrVersion() throws Exception` | Lặp và hai request đồng thời: cùng snapshot/frozenAt/version |
| D10 | BookingPersistenceIT | `void serializesPromotionAndSnapshotPreparation() throws Exception` | Test cả hai thứ tự thắng khóa, không mixed total |
| D11 | BookingPersistenceIT | `void rejectsSnapshotForExpiredCancelledOrForeignOrder() throws Exception` | Kể cả đã frozen: terminal không mở attempt mới |
| D12 | BookingPersistenceIT | `void preparesSnapshotWhenConfigurationVersionChangesWithoutRepricingOrder() throws Exception` | Config version tăng nhưng thương mại không đổi: snapshot hợp lệ, giá order giữ nguyên; replay sau freeze giữ nguyên bản cũ |
| D13 | BookingPersistenceIT | `void rollsBackInvalidSnapshotPreparation() throws Exception` | Data/validation lỗi → không ghi bộ field frozen dở |
| D14 | BookingPersistenceIT | `void checksDeadlineAfterWaitingForOrderLock() throws Exception` | Khóa chờ qua expiresAt thì từ chối bằng DB clock |
| D15 | BookingPersistenceIT | `void scopesPromotionAndSnapshotLockTimeoutToTransaction() throws Exception` | Chờ khóa đúng cấu hình 5s phía PostgreSQL, rollback sạch, không ảnh hưởng consumer/tx sau |
| D16 | BookingPersistenceIT | `void rejectsMissingAmbiguousOrInconsistentPromotionConfiguration() throws Exception` | Mã đã bỏ khỏi config, code trùng, inventory thiếu/version sai: không áp row cũ |

Chạy regression createOrder/sales-config hiện có, DTO/mapper tests. Concurrency tests dùng transaction/connection độc lập và latch quan sát khóa, không dùng sleep để suy thứ tự; không nới 5s thành 30s để tuyên bố đạt. Test DB dùng dữ liệu cách ly, không sửa/xóa dữ liệu đội khác trên server 119. Chưa yêu cầu deploy trong task spec.

## 11. Nguồn tham khảo và duyệt

- [Spring Data JPA locking](https://docs.spring.io/spring-data/jpa/reference/jpa/locking.html): @Lock trên query/CRUD; áp dụng đúng repository có sẵn, không tạo tầng khóa riêng. Trang đọc là 4.1.2; project Boot4.1.1, không đổi BOM để theo trang.
- [Spring @Transactional](https://docs.spring.io/spring-framework/reference/data-access/transaction/declarative/annotations.html): service transaction qua proxy; không tự gọi nội bộ để mong annotation tạo transaction mới. Trang đọc Framework7.0.10; giữ phiên bản project quản lý.
- [Spring Data JPA mã nguồn mở, SimpleJpaRepository](https://github.com/spring-projects/spring-data-jpa/blob/main/spring-data-jpa/src/main/java/org/springframework/data/jpa/repository/support/SimpleJpaRepository.java): đối chiếu repository save/lock metadata, không chép implementation thư viện. Đã đọc nhánh main ngày 29/10, không pin runtime/khẳng định cùng commit dependency.
- [DDL Booking](../../architecture/data/postgres/20-booking.sql), [OrderServiceImpl hiện có](../../../services/booking-service/src/main/java/com/flashticket/booking_service/order/service/impl/OrderServiceImpl.java), [PromotionServiceImpl khung](../../../services/booking-service/src/main/java/com/flashticket/booking_service/promotion/service/impl/PromotionServiceImpl.java), [DTO snapshot](../../../services/booking-service/src/main/java/com/flashticket/booking_service/dto/snapshot/PurchaseSnapshot.java).

Nguồn hướng giữ dữ liệu order/cố định tiền: [B3, cấu hình khóa từ gửi duyệt](../../domain/B3-business-processes.md), [commercetools về order snapshot và freeze](https://docs.commercetools.com/api/carts-orders-overview). Tham khảo để chọn hướng phù hợp; không chép cơ chế catalog/history của hệ thống khác.

**Ghi nhận duyệt:** user đã đồng ý hướng nghiệp vụ và yêu cầu viết plan. C1–C12, M1–M17, H1–H7, D1–D16 còn PENDING khai báo theo gate; D12 đã đổi so với v0.1, không có chấp thuận ngầm cho method cũ. [Plan v0.1](../plans/2026-10-29-booking-order-promotion-purchase-snapshot-plan.md) trình danh sách cuối ở pha WRITING-PLANS; giữ plan nháp tới khi user duyệt. Chưa chuyển EXECUTING-PLANS.
