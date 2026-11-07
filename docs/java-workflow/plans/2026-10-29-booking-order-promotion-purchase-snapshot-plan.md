# Plan Booking — Áp voucher và chuẩn bị PurchaseSnapshot

- Phiên bản v0.1; plan đã duyệt. Gate **EXECUTING-PLANS: APPROVED** cho C1–C12/M1–M17/H1–H7/D1–D16, nguồn: user “ok đồng ý, xác nhận triển khai” sau khi giải thích hai API và trình danh sách thực thi. Các nhãn PENDING ở bảng là trạng thái lúc soạn, được thay bởi xác nhận thực thi này; không cấp quyền cho khai báo ngoài danh sách.
- [Spec v0.2](../specs/2026-10-29-booking-order-promotion-purchase-snapshot-design.md); nghiệp vụ được user chốt: “ok, đồng ý hướng này, update vào spec rồi viết plan cho tôi”.
- User duyệt hướng nghiệp vụ, chưa xác nhận rõ toàn bộ chữ ký Java ở brainstorming. Ghi nguyên giới hạn này; việc yêu cầu viết plan cho phép chuẩn bị plan nháp, không tự chuyển danh sách thành APPROVED hay viết Java.
- Làm trên `D:/DATN/flash-ticket-platform`, `dev/son`, baseline Java `4ff2886`; delta tài liệu đã có được giữ nguyên. Không tự tạo branch/worktree, commit/push/deploy hoặc sửa runtime/credential trong pha lập plan.

## 1. Kết quả cần đạt

1. `POST /api/v1/orders/{orderId}/promotion`: áp mã vào order và giữ usage cùng transaction, trả OrderView đã cập nhật.
2. `POST /internal/v1/orders/{orderId}/purchase-snapshot`: Payment gọi một lần; Booking kiểm đơn, cố định tiền và lưu/trả snapshot sau commit.
3. Không API freeze riêng, không write qua GET, không Booking start/outbox, không thêm class production/bảng/cột/framework mới.
4. Giữ giá/vé trong OrderItem; không hủy/chặn đơn chỉ vì version config tăng. Tổng tiền/voucher chỉ bị cố định khi thực hiện POST chuẩn bị snapshot.

## 2. Ranh giới và giả định tích hợp

- Giữ createOrder và consumer hiện tại. Giá/tên vé/ghế từ order đã lưu; mã mới áp trước freeze phải hợp lệ theo config hiện hành. Dùng cùng order lock để phân xử hai API.
- Event đã khóa cấu hình thương mại từ gửi duyệt theo [B3](../../domain/B3-business-processes.md); chưa kiểm branch Event của người khác. Task không mở chức năng sửa giá sau công bố hoặc tự triển khai kiểm duyệt lại Event.
- Event CANCELLED, order hết hạn/sai trạng thái vẫn bị chặn; không có mass-cancel theo version. Expiry/cancel/refund thuộc các task sau, không xóa giữ chỗ khi chưa đủ nghiệp vụ release.
- Nội bộ tái dùng SecurityConfig và role INTERNAL_SERVICE; Payment gửi buyerSubject lấy từ JWT buyer mà Payment đã kiểm. Boundary hiện tin cậy các internal service, chưa có role/client dành riêng Payment. Không mở route nội bộ qua Gateway.
- **Chi tiết nguồn dữ liệu đề xuất để duyệt:** lần freeze đầu lấy metadata event và sourceConfigVersion từ snapshot Event đọc dưới khóa; Order.sourceConfigVersion vẫn là nguồn lúc tạo và không sửa. Tiền/items từ order, không reprice. Bản snapshot lưu lại là bất biến; lần sau không cập nhật metadata/version theo Event mới.
- Không coi schema file là DB live đã có index. Kiểm partial unique usage theo reservation và promotion/buyer trước khi tuyên bố race test đạt; DDL baseline có sẵn, không tự thêm migration/DDL vào môi trường chung.

## 3. Giai đoạn A — Repository và hợp đồng service

**Item:** C7–C10; M9–M14. Hợp đồng M2/M5 được mô tả tại đây nhưng chỉ ghi cùng implementation ở B/C để không làm compile hỏng giữa giai đoạn.

File production đều dưới `services/booking-service/src/main/java/com/flashticket/booking_service/`:

- `order/repository/OrderRepository.java`: M9 scalar JPQL eventId theo orderId/buyer, M10 derived query @Lock(PESSIMISTIC_WRITE). Không đổi findById thường thành khóa cho mọi caller.
- `promotion/repository/PromotionInventoryRepository.java`: M11 derived locked query; `promotion/repository/PromotionUsageRepository.java`: M13/M14 active lookup và exists.
- `reservation/repository/ReservationRepository.java`: M12 findByOrderId.
- Chốt chữ ký dự kiến ở `promotion/service/PromotionService.java`, `order/service/OrderService.java`: M2/M5. Chưa thêm abstract method cho tới B/C, không tạo stub/default method để vượt compile.

Mẫu: repository Spring Data có sẵn ở Booking và repository/service interface Shop App. M9 là JPQL scalar có @Param, không native SQL/EntityManager; M10/M11 dùng @Lock đã có mẫu trong EventSalesSnapshotRepository. Tránh fetch join collection cùng lock.

Kiểm sau giai đoạn: compile module; IT ở §10 xác nhận scalar không đưa Order cũ vào persistence context và query khóa chạy đúng PostgreSQL. Không coi compile là chứng minh chống race.

## 4. Giai đoạn B — Áp voucher vào order

**Item:** C3/C4; M2/M3/M8/M16; D1–D7/D14–D16. Thêm M2 vào interface cùng lượt có M3 implementation.
**File:** `promotion/service/impl/PromotionServiceImpl.java`.

- Thêm final dependency theo M16, field `@Value("${booking.reservation.lock-timeout}") String reservationLockTimeout`; không thêm YAML/property khác.
- M3 public @Override @Transactional. Gọi `configureTransactionLockTimeout`, đọc scalar eventId → khóa shared snapshot → khóa write order → reservation → khóa write promotion → usage. Đọc DB clock sau bước có thể chờ cuối cùng, kiểm deadline lại trước ghi.
- Kiểm ownership/state/reservation/freeze, chuẩn hóa code Locale.ROOT. Cùng mã đã HELD, chưa freeze và còn hợp lệ trả OrderView không giữ lượt lần hai; mã khác trả ORDER_ALREADY_HAS_PROMOTION.
- Tìm promotion trong snapshot hiện hành; khóa inventory và check event/config liên quan. Khác config version của **order** không phải lý do từ chối; config inventory thực sự không khớp snapshot hoặc dữ liệu thiếu vẫn là lỗi.
- Kiểm cửa sổ mã `[startsAt, endsAt)`, tổng lượt và buyer active usage sau promotion lock. Dùng `held < configuredLimit - used` sau khi bảo đảm counter hợp lệ, tránh cộng tràn.
- M8 tính FIXED/PERCENT BigDecimal; PERCENT HALF_UP một lần. Total<=0 từ chối, không clamp discount. Tạo usage HELD, generation=1 lần đầu, expiry cùng order; tăng held1, không tăng used. Cập nhật order discount/total/updatedAt.
- `save()` usage/inventory/order trong cùng transaction, `orderMapper.toView` trong transaction để lazy items truy cập được. Không ép flush, catch duplicate rồi return, hoặc thêm retry/backoff.

Kiểm: unit/controller sau giai đoạn D, IT B test cả lỗi/race/rollback; tiêu chí tiền/counter/usage đồng bộ, thất bại không giữ lượt một phần. Không cần class calculator/validator riêng.

## 5. Giai đoạn C — Chuẩn bị snapshot và cố định tiền

**Item:** C5/C6; M5/M6/M7/M17; D8–D15. Thêm M5 vào interface cùng lượt có M6 implementation.
**File:** `order/service/impl/OrderServiceImpl.java`.

- Thêm final PromotionUsageRepository và jakarta.validation.Validator; Lombok constructor M17. Dùng timeout field cũ, không thay annotation/chính sách retry của createOrder.
- M6 public @Override @Transactional: scalar eventId → snapshot READ → order WRITE → reservation/usage. Sau khóa lấy DB clock; kiểm owner, CANCELLED, PENDING_PAYMENT, HELD và deadline.
- Check payable **trước** trả snapshot cũ. Snapshot đã có trả nguyên bản, không tăng mốc/version. Bộ field frozen nửa vời hoặc snapshot quan hệ sai trả CONFIG_NOT_READY, không dựng lại âm thầm.
- Lần đầu gọi M7 dựng DTO từ OrderItem/order/usage và config metadata theo §2. Không so `currentVersion != orderVersion` để trả conflict, không đọc giá mới để tính total. SourceOrderVersion="1", paymentSnapshotVersion=1; rowVersion để JPA quản lý.
- M7 dùng constructor record PurchaseSnapshot/Item/AppliedPromotionSnapshot sẵn có; sort item/seat cho serialization ổn định; monetary string lấy số nguyên đã lưu. Không thêm mapper/decoder/hash framework; M6 gọi Validator để chạy field/nested/cross-field validation thực.
- Lưu cả frozenPurchaseSnapshot/paymentFrozenAt/paymentSnapshotVersion/updatedAt bằng save, trả sau commit qua controller. Không gọi Payment/cổng/broker trong transaction Booking. Mất response: retry cùng resource trên đơn còn payable lấy cùng snapshot.

Kiểm: ba selection mode, có/không voucher, hai POST đồng thời, race với áp mã cả hai thứ tự, phiên bản config tăng không reprice, terminal/cancelled/mất response. Guard callback/hoàn muộn không nằm trong implementation task này.

## 6. Giai đoạn D — HTTP boundary và regression

**Item:** C1/C2/C11/C12; M1/M4/M15; H1–H7.

- `order/controller/OrderController.java`: thêm final PromotionService, M1 @PostMapping("/{orderId}/promotion") dùng JWT subject và @Valid body; ResponseEntity.ok(service result), không catch BusinessException tại controller.
- `order/controller/InternalOrderController.java`: M4 POST "/{orderId}/purchase-snapshot", @Valid FreezePaymentRequest có sẵn. Không thêm /freeze-payment hoặc GET snapshot có write.
- `src/test/java/com/flashticket/booking_service/order/controller/OrderControllerTests.java`: WebMvcTest bao gồm hai controller, thêm @MockitoBean PromotionService, H1–H7. Không giả lập “role BUYER đủ quyền internal”. Kiểm 400/401/403/business error envelope và DTO response.
- Chạy unit/regression createOrder, DTO/mapper, security; nếu test startup bị ảnh hưởng constructor thì chỉnh injection/import/mock trong các test hiện có, không tạo callback/test type ngoài danh sách chưa duyệt.

Mẫu Shop App: controller ResponseEntity + DTO @Valid + constructor final dependency. Khác mẫu là resource server JWT/global problem handler của Booking; không copy catch mọi Exception/raw message từ Shop App.

## 7. Giai đoạn E — PostgreSQL, tài liệu và bàn giao

**Item:** C12/D1–D16; nguồn test `services/booking-service/src/test/java/com/flashticket/booking_service/persistence/BookingPersistenceIT.java`.

- Tái sử dụng IT/fixtures/config hiện có, import PromotionServiceImpl và dependency, không tạo test container framework mới. Setup ngắn trong từng test; helper mới nếu thực sự cần phải trình delta cụ thể trước khi tạo.
- Transaction/connection độc lập, latch và quan sát lock; khóa phải thực sự được giữ bởi transaction A trước khi B bắt đầu chờ. Không kết luận timeout do tunnel chỉ từ tổng thời gian wall-clock; kiểm lỗi PostgreSQL/rollback và không nâng timeout5s để “pass”.
- Kiểm index thật và scope môi trường. Mặc định test container cách ly như TESTING.md; không tự yêu cầu Docker nếu đã có cách kiểm DB cách ly phù hợp. Server119 chỉ dùng khi cấu hình test được kiểm đúng schema/data cách ly và đã có quyền; không chạy DDL baseline/truncate/cleanup toàn schema chung.
- Update `services/booking-service/TESTING.md`, `HANDOFF.md` khi thực thi: APIs, retry theo tài nguyên, provenance version, lệnh/kết quả đã chạy và giới hạn. Không ghi FULL PASS nếu chỉ chạy unit hoặc thiếu IT; không đánh dấu Payment E2E đã đạt khi chỉ test Booking.
- Kết thúc execute-plan: đề xuất dùng `$shopapp-java-writing-docs` viết MD ngắn vào explanations theo rule; chỉ tạo khi user yêu cầu/đã yêu cầu trước đó.

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

## 11. Lệnh và điều kiện hoàn tất khi thực thi

Từ `D:/DATN/flash-ticket-platform`, lệnh theo Maven/POM hiện có:

```powershell
rtk mvn -f services/booking-service/pom.xml -B -ntp -DskipTests compile
rtk mvn -f services/booking-service/pom.xml -B -ntp test
rtk mvn -f services/booking-service/pom.xml -B -ntp -P integration test-compile failsafe:integration-test failsafe:verify
rtk mvn -f services/booking-service/pom.xml -B -ntp -P integration verify
rtk git diff --check
```

Compile là checkpoint A; unit/full verify chạy sau khi giai đoạn liên quan hoàn tất, không lặp mọi lệnh sau từng file. Khi chỉ cần feedback controller: `-Dtest=OrderControllerTests test`. Full verify bao gồm Spotless/SpotBugs của module; chỉ sửa vấn đề thuộc task, không format/refactor service khác để xử lý lỗi baseline không liên quan. Nếu runtime test phụ thuộc Docker/broker/cấu hình khác chưa có, nêu rõ điều kiện chặn; không giấu skip hoặc thay môi trường để tuyên bố đạt.

Hoàn tất khi hai API chạy qua security/validation/service proxy thật; H1–H7/D1–D16 và regression phù hợp đạt, counter/tổng tiền/snapshot đúng, deadline/lock timeout giữ cấu hình gốc, không thay đổi tài nguyên đội khác. Đây là tiêu chí **chưa thực thi**, không phải kết quả của lượt lập plan.

## 12. Gate WRITING-PLANS và delta

- Danh sách: **C1–C12, M1–M17, H1–H7, D1–D16** được chép đầy đủ ở §8–10. Nguồn duyệt plan: user gọi shopapp-java-executing-plans và yêu cầu “triển khai theo plan đã duyệt”. Các nhãn PENDING trong bảng lưu trạng thái lúc soạn; plan nay đã được xác nhận, nhưng chưa thay cho gate EXECUTING-PLANS theo skill user gọi.
- Delta so với spec v0.1: bỏ chặn toàn bộ order theo khác version; D12 đổi sang `preparesSnapshotWhenConfigurationVersionChangesWithoutRepricingOrder`; nguồn metadata/version snapshot được trình rõ ở §2, không tăng class/method production.
- Chỉ chốt plan sau duyệt. Khi user yêu cầu execute, skill executing-plans vẫn có gate riêng; không triển khai Java trong lượt plan.
- Nếu cần field/type/query/helper/test/signature mới, hỏi duyệt delta theo skill; không ghi “helper cần thiết” như quyền mở.
- Quyền commit từng giai đoạn hoặc deploy không suy ra từ plan; làm theo yêu cầu Git/runtime trực tiếp của user.

Nguồn style/research chi tiết tại spec §2/§11: Shop App OrderController/OrderService, Spring Data locking, Spring transactional, code Spring Data trên GitHub. Không thêm dependency vì ví dụ bên ngoài có dùng.

## 13. Nhật ký thực thi và kiểm chứng

- A–D đã triển khai theo danh sách; không class production/dependency/schema mới. Giữ constructor injection, property @Value, repository save và comment tiếng Việt; không đổi createOrder hoặc consumer để mở rộng scope.
- Bổ sung đủ H1–H7/D1–D16, dùng lại fixtures/transaction helpers hiện có. Test data PostgreSQL nằm trong Testcontainers dùng riêng; Docker hiện hoạt động, không cần server119/tunnel.
- Compile đạt; unit 355/355 đạt. Targeted PostgreSQL 19/19 đạt (16 mới + 3 test cũ khớp filter), không skip. Lượt đầu fail hai test cũ do dữ liệu test mới chưa cleanup, đã thêm finally cleanup theo mẫu IT trên database cách ly và chạy lại đạt.
- Kiểm tiền gồm vector subtotal100005 ×10% → discount10001,total90004; race apply/snapshot có quan sát wait trong pg_stat_activity trước khi nhả khóa; timeout5s thật không đổi thành30s.
- Full module verify cuối đạt lúc 13:52:40 ngày 29/10/2026: **355 unit + 64 integration tests**, không failure/error/skip; BUILD SUCCESS, gồm các bước kiểm tra trong lifecycle verify của module. Log local: `services/booking-service/target/promotion-verify-final.log`. `git diff --check` đạt.
- Đã sửa isolation của các test cũ đếm toàn bảng trên database dùng riêng; test DROP/ADD cột loại connection có prepared plan cũ sau khi chạy. Test DB clock cũ có một lượt timeout khi chờ mốc 3s; chỉnh nhánh snapshot đặt giờ đóng bán sau khi quan sát request bị khóa rồi mới commit, không tăng timeout production 5s. Full verify sau sửa đạt; không nới assertion để chấp nhận lỗi kỹ thuật.
- A–D và H1–H7/D1–D16 hoàn tất trong phạm vi Booking component/database. Chưa deploy, chưa test Payment E2E/cổng thanh toán thật, chưa commit/push.
