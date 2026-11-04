# Plan — Booking tạo đơn và giữ chỗ

- Ngày: 28/10/2026. Pha: **WRITING-PLANS**. Phiên bản: v0.2 — cập nhật phương án khóa theo yêu cầu user.
- Thiết kế đầu vào: [booking-create-order-design v0.1](../specs/2026-10-28-booking-create-order-design.md).
- Task: [API đặt ghế và tạo order, reservation, allocations](https://app.clickup.com/t/z8qgvc7vbb).
- Checkout đã đối chiếu: `D:/DATN/flash-ticket-platform`, nhánh `dev/son`; Git common directory `D:/DATN/flash-ticket-platform/.git`.
- Gate WRITING-PLANS: **PENDING** cho C1–C18, M1–M17 gồm M3a/M3b, H1–H6, D1–D20, F1–F3 và các annotation/API sinh/ngoại lệ đi kèm.
- Nguồn chỉ đạo: user yêu cầu “viết plan theo spec này”. Đây là quyền viết bản plan; chưa có câu xác nhận danh sách của pha WRITING-PLANS, chưa đánh dấu thiết kế hoặc plan APPROVED thay user.
- Delta v0.2: user yêu cầu “ok update plan cho tôi” sau khi thảo luận snapshot đọc chia sẻ và khóa ghi riêng inventory. Chi tiết khóa trong plan này thay đề xuất khóa ghi chung ở spec v0.1; danh sách method, test và ngoại lệ mới vẫn PENDING, chưa có xác nhận duyệt riêng.
- Thời gian giữ chỗ: **15 phút**, cấu hình YAML. Nguồn: user chọn “15 phút (đề xuất)” khi xác nhận pha thực thi.
- Chưa viết Java, sửa cấu hình/schema hoặc chạy các lệnh build/test được liệt kê dưới đây. Mọi task P1–P4 chỉ sẵn sàng thực thi sau khi item liên quan đã được duyệt.

## 1. Kết quả và giới hạn

`POST /api/v1/orders` nhận request hiện có và Idempotency-Key UUID của BUYER; lấy chủ đơn từ JWT. Thành công trả 201 OrderView sau commit, giữ đúng nguồn cung/hạn mức và tạo một order PENDING_PAYMENT, một reservation HELD cùng các allocations. Replay cùng key/input trả cùng order, không giữ thêm hay gia hạn.

Giữ cả ba nhánh QUANTITY, STANDING, SEATED; không thêm DTO, mapper hoặc class production mới. Dùng controller → service interface → implementation → repository sẵn có; Jackson/Bean Validation và MapStruct hiện có tiếp tục phụ trách boundary/response.

Voucher, Payment, outbox thanh toán, hủy và worker release hết hạn thuộc task khác. Task này chỉ lưu expiresAt; chưa giải phóng tài nguyên bằng thời gian một cách tự động.

## 2. Quyết định kỹ thuật từ spec — chờ duyệt cùng plan

| Nội dung | Cách thực hiện và lý do |
| --- | --- |
| Transaction | M3b dùng @Transactional qua Spring proxy; map DTO bên trong transaction, HTTP chỉ thành công sau commit. Không gọi mạng trong transaction |
| Khóa snapshot | Order dùng method mới M13 `PESSIMISTIC_READ`; các order có thể cùng đọc. Consumer giữ nguyên `findByEventId` với `PESSIMISTIC_WRITE`, phải chờ order kết thúc trước khi sửa/hủy snapshot |
| Khóa nguồn cung | M14–M17 dùng `PESSIMISTIC_WRITE` cho purchase limit và từng seat/pool cần giữ; lock nhiều ID theo ORDER BY trong query. Giữ `@Version` đã có; khóa cùng một row vẫn phải chờ nhau |
| Idempotency | `(actorSubject, CREATE_ORDER, key)`; hash DTO bằng Jackson + SHA-256. Tra trước khóa và đọc lại sau các lần có thể chờ, đặc biệt trước khi từ chối limit/inventory; version null cho receipt mới để INSERT, PK chặn đua |
| Retry HTTP | `@Retryable(includes=DataIntegrityViolationException.class, maxRetries=1, delay=0)` từ Spring 7; retry ở ngoài transaction. Tối đa hai lần, đọc lại receipt trong transaction mới; lỗi FK/CHECK cũng có thể thử thêm một lần nhưng không được coi thành công |
| Clock | M11 là native SELECT clock_timestamp duy nhất, đọc sau khi lấy xong các khóa cần phân xử; không dùng now() hoặc đồng hồ FE. Kiểm conversion timestamptz → Instant với PostgreSQL thật |
| Hold | Field @Value kiểu Duration theo Shop App; đề xuất PT15M, phải dương. `expiresAt = decisionAt + duration` chung cho order/reservation; không tự cắt theo saleEndsAt; retry cùng key giữ expiry cũ |
| Ghi dữ liệu | Setter + repository save/saveAll, UUID do Hibernate sinh, cascade PERSIST hiện có; ngoại lệ `saveAndFlush` đúng một lần khi tạo purchase limit chưa có row để phân xử PK trước khi khóa inventory. Không EntityManager hoặc native INSERT/upsert trong production |
| Schema | Thêm riêng receipt row_version; baseline SQL có default 0, runtime vẫn ddl-auto update. Không thêm Liquibase/Flyway |
| Lỗi mới | EVENT_NOT_ON_SALE 409; INVALID_SELECTION 422; ORDER_AMOUNT_EXCEEDED 422; INVALID_RESERVATION_DURATION 500. Dùng BusinessException/global handler có sẵn |

Nguồn kỹ thuật và GitHub đã đối chiếu nằm ở [spec §2](../specs/2026-10-28-booking-create-order-design.md#2-nguồn-đã-đọc-và-cách-áp-dụng). Pha plan đã đọc lại source controller/service/repository/receipt và mẫu Shop App `OrderController.createOrder`, `OrderService.createOrder`, field @Value ở JwtTokenUtil. Giữ package/tên hiện có; không sao chép ModelMapper hoặc catch-all của mẫu.

Đối chiếu thêm [PostgreSQL row locks](https://www.postgresql.org/docs/17/explicit-locking.html#LOCKING-ROWS) và [Hibernate PostgreSQLDialect 7.4.5](https://github.com/hibernate/hibernate-orm/blob/7.4.5/hibernate-core/src/main/java/org/hibernate/dialect/PostgreSQLDialect.java#L1309): nhiều `FOR SHARE` cùng tồn tại và xung đột với cập nhật; cần kiểm SQL Hibernate sinh và race thực tế bằng PostgreSQL. `lock_timeout` và cách giới hạn request/connection chưa được user chốt: **PENDING**, cần quyết định và trình delta cấu hình/API nếu phát sinh trước khi triển khai; không hứa chịu tải 1.000 request.

## 3. Bảng class/file của plan

Đường dẫn Java bên dưới tính từ `services/booking-service/src/main/java/com/flashticket/booking_service/`, trừ file test/config được ghi rõ.

| ID | Class/file | NEW/EXISTING | Vai trò, annotation/API sinh | Mẫu hoặc ngoại lệ |
| --- | --- | --- | --- | --- |
| C1 | order/controller/OrderController.java | EXISTING | Thêm POST; giữ @RestController/@RequestMapping/@RequiredArgsConstructor và dependency final hiện có | Shop App OrderController; lấy Jwt subject thay userId do FE gửi; không catch-all |
| C2 | order/service/OrderService.java | EXISTING | Thêm một method nghiệp vụ, giữ interface thường | Shop App IOrderService; giữ tên/package hiện có |
| C3 | order/service/impl/OrderServiceImpl.java | EXISTING | Giữ @Service/@RequiredArgsConstructor; 10 dependency final nêu M3a; property `@Value("${booking.reservation.hold-duration}") private Duration reservationHoldDuration`, không sinh getter/setter cho property | Shop App OrderService + property theo JwtTokenUtil; @Transactional và retry là ngoại lệ có căn cứ Spring |
| C4 | order/repository/OrderRepository.java | EXISTING | Thêm đúng query DB time ở M11; findById/save kế thừa | Ngoại lệ native SELECT chỉ để lấy clock_timestamp theo B14, không native INSERT/upsert |
| C5 | entity/IdempotencyRequest.java | EXISTING | Thêm field `@Version @ColumnDefault("0") @Column(name="row_version",nullable=false) private Long rowVersion`; @Getter/@Setter sẵn có sinh đúng hai API M12; constructor no-args giữ nguyên | Cùng kiểu InboxMessage đã được chọn; không thêm Persistable/lifecycle helper |
| C6 | BookingServiceApplication.java | EXISTING | Thêm @EnableResilientMethods, giữ main hiện có | Spring 7; tương tự cách enable ở IdentityMessagingConfig, chỉ retry method có annotation |
| C7 | exception/ErrorCode.java | EXISTING | Thêm bốn enum constant ở §2 của plan, giữ constructor/status/message/from hiện có | Convention error code cụ thể; không tạo exception class mới |
| C8 | DTO/request, DTO/response, snapshot, OrderMapper và MappingValueConverter | EXISTING, REUSE | Không thêm type, mapper method, annotation hoặc API sinh | Giữ contract cũ đã duyệt, dù một số DTO/record khác mẫu Shop App class |
| C9 | Order/OrderItem/OrderItemSeat/Reservation/ReservationAllocation/PurchaseLimit và ba inventory | EXISTING, REUSE | Giữ entity/quan hệ/getter/setter/constructor; dùng addItem/addSeatAssignment/addAllocation và repository save/saveAll | Không thêm repository wrapper, generic service hoặc EntityManager |
| C10 | infrastructure/config-server/src/main/resources/config/booking-service.yaml | EXISTING | Thêm booking.reservation.hold-duration, giá trị đang chờ chọn | Property ở field như convention Shop App; chưa sửa .env |
| C11 | docs/architecture/data/postgres/20-booking.sql | EXISTING | Cập nhật baseline receipt row_version bigint NOT NULL DEFAULT 0; runtime vẫn ddl-auto update | Không tạo migration framework; kiểm schema thật khi thực thi |
| C12 | services/booking-service/src/test/java/com/flashticket/booking_service/order/controller/OrderControllerTests.java | **NEW** | Package-private test class, @WebMvcTest(OrderController.class), @Import SecurityConfig/BookingJsonConfig/GlobalExceptionHandler, @MockitoBean OrderService; MockMvc/JsonMapper inject cho test; implicit constructor no-args | Ngoại lệ test JUnit/Spring, H1–H6 |
| C13 | services/booking-service/src/test/java/com/flashticket/booking_service/persistence/BookingPersistenceIT.java | EXISTING | Dùng PostgreSQL Testcontainers và initializer có sẵn; import OrderServiceImpl, mapper/converter và enable resilience để gọi proxy thật; thêm field OrderService; D1–D20 | Giữ EntityManager/TransactionTemplate sẵn có chỉ trong test; không đưa vào production |
| C14 | availability/repository/EventSalesSnapshotRepository.java | EXISTING | Thêm M13 @Lock(PESSIMISTIC_READ) cho order; giữ nguyên `findByEventId` @Lock(PESSIMISTIC_WRITE) của consumer | Spring Data repository, ngoại lệ read lock PostgreSQL |
| C15 | availability/repository/PurchaseLimitRepository.java | EXISTING | Thêm M14 @Lock(PESSIMISTIC_WRITE), giữ find/save/saveAndFlush kế thừa | Query method theo tên; flush chỉ cho INSERT row limit chưa có |
| C16 | availability/repository/TicketTypeInventoryRepository.java | EXISTING | Thêm M15 @Lock(PESSIMISTIC_WRITE), query nhiều ID có ORDER BY | Spring Data query method |
| C17 | availability/repository/SectorInventoryRepository.java | EXISTING | Thêm M16 @Lock(PESSIMISTIC_WRITE) theo sector ID | Spring Data query method |
| C18 | availability/repository/SeatInventoryRepository.java | EXISTING | Thêm M17 @Lock(PESSIMISTIC_WRITE), query nhiều ID có ORDER BY | Spring Data query method |

Không tạo class production mới. Không thêm dependency Java hoặc framework quản lý idempotency/retry. Nếu test cần thêm nested config/type/method ngoài bảng, trình delta trước khi viết.

## 4. Bảng constructor/method của plan

Các chữ ký dưới là thiết kế để duyệt, không phải Java đã triển khai. Method không ghi throws không khai báo checked exception.

| ID | Class | Chữ ký đầy đủ | NEW/REUSE | Cách viết/mục đích |
| --- | --- | --- | --- | --- |
| M0 | OrderController | `public OrderController(OrderService orderService)` | REUSE | Constructor Lombok hiện có, không đổi |
| M1 | OrderController | `public ResponseEntity<OrderView> createOrder(@AuthenticationPrincipal Jwt jwt, @RequestHeader("Idempotency-Key") UUID idempotencyKey, @Valid @RequestBody CreateOrderRequest request)` | NEW | @PostMapping; gọi service, trả 201 sau commit |
| M2 | OrderService | `public abstract OrderView createOrder(String buyerSubject, UUID idempotencyKey, CreateOrderRequest request)` | NEW | Method trong **interface**, không tạo abstract class |
| M3a | OrderServiceImpl | `public OrderServiceImpl(OrderRepository orderRepository, IdempotencyRequestRepository idempotencyRequestRepository, OrderMapper orderMapper, EventSalesSnapshotRepository eventSalesSnapshotRepository, PurchaseLimitRepository purchaseLimitRepository, TicketTypeInventoryRepository ticketTypeInventoryRepository, SectorInventoryRepository sectorInventoryRepository, SeatInventoryRepository seatInventoryRepository, ReservationRepository reservationRepository, JsonMapper objectMapper)` | CHANGE | Constructor @RequiredArgsConstructor sinh từ 10 dependency final; property @Value không nằm trong constructor |
| M3b | OrderServiceImpl | `public OrderView createOrder(String buyerSubject, UUID idempotencyKey, CreateOrderRequest request)` | NEW | @Override, @Transactional, Spring 7 @Retryable như §2 của plan; nghiệp vụ trực tiếp gọi repository |
| M4 | OrderServiceImpl | `private String calculateCreateOrderRequestHash(CreateOrderRequest request)` | NEW | Jackson serialization + SHA-256 bằng JDK; không decoder/canonicalizer tự viết |
| M5 | OrderServiceImpl | `private Optional<OrderView> findReplayedOrder(IdempotencyRequestId requestId, String requestHash)` | NEW | Tra receipt, so hash, load order đúng chủ; dùng trước khóa và sau các lần chờ trước guard có thể từ chối |
| M6 | OrderServiceImpl | `private void populateOrderItems(Order order, CreateOrderRequest request, SalesConfiguration configuration)` | NEW | Kiểm selection theo snapshot, nhóm item/type, đặt giá và tổng tiền bằng setter; không dùng DTO mapper cho field do server quyết định |
| M7 | OrderServiceImpl | `private void reserveQuantityTickets(Order order, Reservation reservation, OrderSelection.QuantitySelection selection, List<TicketTypeInventory> lockedInventories)` | CHANGE từ v0.1 | Kiểm/giữ các pool đã khóa bởi M15, thêm allocations |
| M8 | OrderServiceImpl | `private void reserveStandingTickets(Order order, Reservation reservation, OrderSelection.StandingSelection selection, SectorInventory lockedSector)` | CHANGE từ v0.1 | Kiểm/giữ pool sector đã khóa bởi M16, allocation theo item |
| M9 | OrderServiceImpl | `private void reserveSeatedTickets(Order order, Reservation reservation, OrderSelection.SeatedSelection selection, List<SeatInventory> lockedSeats)` | CHANGE từ v0.1 | Kiểm/lưu ghế đã khóa bởi M17, generation, seat assignments và allocations |
| M10 | OrderServiceImpl | `private void addReservationAllocation(Reservation reservation, OrderItem item, ResourceKind resourceKind, UUID resourceId, int quantity)` | NEW | Điền field liên kết chung và đúng một nhóm resource FK, gọi addAllocation; helper dùng cho ba nhánh |
| M11 | OrderRepository | `public abstract Instant currentDatabaseTime()` | NEW | @Query(value="select clock_timestamp()", nativeQuery=true); query chỉ đọc; kiểm conversion timestamptz→Instant với PostgreSQL thật |
| M12 | IdempotencyRequest | `public Long getRowVersion()`; `public void setRowVersion(Long rowVersion)` | NEW, Lombok-generated | Chỉ API của field mới; không tự set/tăng version trong nghiệp vụ |
| M13 | EventSalesSnapshotRepository | `public abstract Optional<EventSalesSnapshot> findForBookingByEventId(UUID eventId)` | NEW | @Lock(PESSIMISTIC_READ); query method riêng, không đổi method ghi của consumer |
| M14 | PurchaseLimitRepository | `public abstract Optional<PurchaseLimit> findLockedById(PurchaseLimitId id)` | NEW | @Lock(PESSIMISTIC_WRITE); chỉ khóa row đã tồn tại |
| M15 | TicketTypeInventoryRepository | `public abstract List<TicketTypeInventory> findLockedByTicketTypeIdInOrderByTicketTypeIdAsc(Collection<UUID> ticketTypeIds)` | NEW | @Lock(PESSIMISTIC_WRITE); ORDER BY ở query, không dựa vào sort Java sau khi khóa |
| M16 | SectorInventoryRepository | `public abstract Optional<SectorInventory> findLockedBySectorId(UUID sectorId)` | NEW | @Lock(PESSIMISTIC_WRITE) cho pool STANDING |
| M17 | SeatInventoryRepository | `public abstract List<SeatInventory> findLockedBySeatIdInOrderBySeatIdAsc(Collection<UUID> seatIds)` | NEW | @Lock(PESSIMISTIC_WRITE); ORDER BY ở query |
| R1 | Existing entities/repos/mapper | `Order.addItem`, `OrderItem.addSeatAssignment`, `Reservation.addAllocation`, `OrderMapper.toView/toItemView`, entity getters/setters, `findById/findAllById/save/saveAll/saveAndFlush`, `EventSalesSnapshotRepository.findByEventId` | REUSE | Method consumer không đổi; `saveAndFlush` chỉ dùng cho row limit mới trước inventory |

### Test class/method — đề xuất cụ thể, chưa viết

Mọi test method instance dưới đây là package-private void, `@Test` trừ method ghi parameterized; default constructor `OrderControllerTests()` package-private, không thêm @BeforeEach/helper/config class mới trong lớp này.

| ID | Class | Chữ ký đầy đủ | Mục đích |
| --- | --- | --- | --- |
| H1 | OrderControllerTests | `void createsOrderUsingAuthenticatedSubjectAndIdempotencyKey() throws Exception` | 201, Location, truyền đúng subject/key/DTO, không nhận chủ đơn từ body |
| H2 | OrderControllerTests | `void rejectsMissingIdempotencyKey() throws Exception` | Header bắt buộc, chưa gọi service |
| H3 | OrderControllerTests | `void rejectsInvalidIdempotencyKey(String key) throws Exception` | @ParameterizedTest/@ValueSource, UUID sai/rỗng |
| H4 | OrderControllerTests | `void rejectsInvalidCreateOrderBody(String body) throws Exception` | @ParameterizedTest/@ValueSource JSON inline: field lạ/email/quantity/list/ID trùng, không thêm MethodSource |
| H5 | OrderControllerTests | `void requiresAuthenticationAndBuyerRole() throws Exception` | 401/403 qua security filter thật với fixture JWT của Spring Security test |
| H6 | OrderControllerTests | `void returnsBusinessProblemFromOrderService() throws Exception` | Lỗi service đi qua global handler, không raw exception hoặc 201 giả |
| D1 | BookingPersistenceIT | `void createsQuantityOrderAndHoldsTicketTypeInventory()` | Graph, giá, counters, receipt cùng commit |
| D2 | BookingPersistenceIT | `void standingTicketTypesShareOneSectorPool()` | Hai type 2+3 vé chỉ giữ pool chung 5, không có type pool bị trừ |
| D3 | BookingPersistenceIT | `void seatedOrderGroupsItemsAndHoldsIndividualSeats()` | Nhiều type trong một sector, mapping thông tin ghế, assignments, generation và allocations |
| D4 | BookingPersistenceIT | `void rejectsInvalidSelectionsWithoutPartialHolds()` | Ghế ẩn/đã giữ, sai event/sector/type, missing inventory và thiếu pool; không commit một phần |
| D5 | BookingPersistenceIT | `void rejectsOrdersOutsidePublishedSalesWindow()` | PUBLISHED/window và biên đóng bán |
| D6 | BookingPersistenceIT | `void replayReturnsTheSameOrderWithoutExtendingExpiry()` | Cùng key/data không giữ thêm, giữ orderId/expiry kể cả điều kiện bán đã đổi |
| D7 | BookingPersistenceIT | `void reusedKeyWithDifferentInputIsRejected()` | Đổi event/email/selection cùng key ⇒ conflict |
| D8 | BookingPersistenceIT | `void idempotencyKeysAreScopedByBuyerAndOperation()` | Cùng UUID khác actor/operation không lấy nhầm receipt của nhau |
| D9 | BookingPersistenceIT | `void concurrentSameKeyCreatesOnlyOneOrder() throws Exception` | Hai lời gọi proxy/connection cùng event, một graph và cùng orderId |
| D10 | BookingPersistenceIT | `void concurrentSameKeyAcrossEventsCannotOverwriteReceipt() throws Exception` | Khác event/same actor/key ⇒ PK rollback + retry transaction mới + so hash; không để orphan/giữ thêm |
| D11 | BookingPersistenceIT | `void concurrentRequestsCannotHoldTheSameSeat() throws Exception` | Hai buyer/key khác nhau, chỉ một thành công |
| D12 | BookingPersistenceIT | `void concurrentRequestsCannotOversellQuantityOrStandingPool() throws Exception` | Tranh phần pool cuối ở cả hai mode |
| D13 | BookingPersistenceIT | `void concurrentOrdersCannotExceedBuyerPurchaseLimit() throws Exception` | Cùng buyer/key khác nhau, không vượt held+purchased limit |
| D14 | BookingPersistenceIT | `void failedCommitRollsBackOrderReservationInventoryAndReceipt()` | Gây constraint lỗi cuối transaction, kể cả thử thêm một lần vẫn không còn dữ liệu/counter dở dang |
| D15 | BookingPersistenceIT | `void saleDecisionUsesDatabaseTimeAfterWaitingForSnapshotLock() throws Exception` | Kiểm cả chờ snapshot và chờ inventory qua thời điểm đóng bán; DB clock sau khóa cuối phải từ chối |
| D16 | BookingPersistenceIT | `void changingConfigurationDoesNotRepriceExistingOrder()` | Snapshot mới không thay giá/tên/seat snapshot/expiry của đơn cũ |
| D17 | BookingPersistenceIT | `void differentSeatsCanBeReservedConcurrently() throws Exception` | Giữ khóa ghế A để order A chờ sau snapshot share; order B khác buyer/ghế commit trước khi thả A |
| D18 | BookingPersistenceIT | `void configurationUpdateWaitsForActiveBooking() throws Exception` | Writer consumer chờ order giữ snapshot read; sau commit cập nhật mới tiếp tục |
| D19 | BookingPersistenceIT | `void concurrentFirstOrdersCreateOnePurchaseLimit() throws Exception` | Cùng buyer/event chưa có limit, khác key: PK phân xử, rollback/retry, limit/counters đúng |
| D20 | BookingPersistenceIT | `void replayAfterWaitingForPurchaseLimitReturnsOriginalOrder() throws Exception` | Cùng key và limit=1: request chờ phải đọc receipt sau khóa và trả lại order, không báo vượt limit |
| F1 | BookingPersistenceIT | `private SalesConfiguration seedOrderCreationConfiguration(SalesMode mode, SectorType sectorType, long capacity, int purchaseLimit)` | Helper fixture duy nhất cho config; UUID riêng từng test, window theo DB time, gọi consumer/service có sẵn |
| F2 | BookingPersistenceIT | `private CreateOrderRequest createOrderRequest(SalesConfiguration configuration, int quantity)` | Dựng request theo mode/kind; không tính expected outcome từ code production |
| F3 | BookingPersistenceIT | `private Map<String, Object> captureOrderCreationState(UUID eventId)` | Chụp graph/counters/receipt để so trước–sau rollback, không chỉ đếm HTTP success |

F1–F3 cũng là method mới **PENDING**, không phải phê duyệt mở cho helper khác. Test cạnh tranh gọi service proxy thật từ các thread/connection riêng; không bọc cả lời gọi trong transaction của test. Không lấy việc unit test gọi trực tiếp object làm bằng chứng retry/commit hoạt động.

## 5. Giai đoạn triển khai sau khi được duyệt

Các đường dẫn production Java dưới đây tính từ `services/booking-service/src/main/java/com/flashticket/booking_service/`; test có đường dẫn riêng. Không ghi stub trả thành công hoặc UnsupportedOperationException vào API đang dùng để đánh dấu xong một pha.

### P1 — Chuẩn bị receipt, cấu hình và clock

**Item:** C4–C7, C10–C11, C14–C18; M11–M17. Chờ duyệt, chưa thực thi.

**File:**

- `entity/IdempotencyRequest.java`
- `order/repository/OrderRepository.java`
- `availability/repository/EventSalesSnapshotRepository.java`
- `availability/repository/PurchaseLimitRepository.java`
- `availability/repository/TicketTypeInventoryRepository.java`
- `availability/repository/SectorInventoryRepository.java`
- `availability/repository/SeatInventoryRepository.java`
- `BookingServiceApplication.java`
- `exception/ErrorCode.java`
- `infrastructure/config-server/src/main/resources/config/booking-service.yaml`
- `docs/architecture/data/postgres/20-booking.sql`

**Làm:**

1. Thêm boxed rowVersion với @Version/@ColumnDefault(0), giữ null trên entity mới. Giữ PK actor/operation/key và constructor/getter/setter cũ.
2. Thêm cột default 0 vào baseline SQL; ddl-auto update phục vụ schema hiện có khi startup. Không viết migration hoặc tác động server trong pha viết plan.
3. Thêm M11 lấy DB time bằng một SELECT. Thêm M13–M17 để khóa snapshot/limit/inventory theo bảng; giữ nguyên query `findByEventId` ghi của consumer và không ghi native SQL khác.
4. Bật @EnableResilientMethods ở application hiện có. Annotation này chỉ tác động method được gắn @Retryable, không tự thay listener.
5. Thêm bốn ErrorCode cụ thể, giữ global handler. Thêm booking.reservation.hold-duration vào YAML sau khi user chốt giá trị; không thêm secret hoặc biến .env mới.

**Kiểm chứng khi thực thi:** compile JDK 23; ErrorCodeTests và RuntimeConfigurationTests hiện có vẫn đạt. Tại P2/P4, test Postgres xác minh M11 trả thời điểm hợp lệ và receipt có row_version khởi tạo 0; cặp entity mới/version null phải INSERT, không merge receipt cũ. Nếu startup DB thật được yêu cầu, kiểm cột NOT NULL/default bằng catalog sau startup, không suy ra từ annotation.

**Xong pha:** schema baseline/entity đồng bộ, query clock và cấu hình dùng được; chưa công bố API tạo đơn đã hoạt động.

### P2 — Tạo đơn và giữ chỗ nguyên tử cho ba selection

**Phụ thuộc:** P1 và quyết định hold/khóa/retry được duyệt.

**Item:** C2–C3, C8–C9, C13–C18; M2, M3a/M3b, M4–M10, M13–M17, R1; D1–D8, F1–F3.

**File sửa:**

- `order/service/OrderService.java`
- `order/service/impl/OrderServiceImpl.java`
- `services/booking-service/src/test/java/com/flashticket/booking_service/persistence/BookingPersistenceIT.java`

**Làm theo thứ tự trong một transaction:**

1. Thêm interface M2, implementation M3b và constructor Lombok M3a. Chỉ dùng 10 dependency final đã liệt kê; field @Value không thuộc constructor. Không tạo thêm service điều phối hoặc đưa repository vào controller.
2. M4 hash DTO đã parse; M5 trả Optional OrderView từ receipt đúng actor/operation/key/hash/chủ đơn. Có receipt nhưng không có order là lỗi nhất quán, không tạo đơn mới; không gán mọi constraint thành idempotency conflict.
3. Gọi M5 trước khóa → M13 khóa chia sẻ snapshot → gọi lại M5. Nếu chưa có snapshot trả CONFIG_NOT_READY; consumer vẫn lấy khóa ghi riêng trước khi cập nhật snapshot/inventory. Từ snapshot đã khóa, xác định mode và ID cần khóa; các guard có thể từ chối vì dữ liệu đang thay đổi phải chờ bước 6.
4. Dùng M14 lấy khóa ghi purchase limit `(eventId,buyerSubject)`. Nếu row chưa tồn tại: tạo entity với counters 0, configuredLimit/sourceConfigVersion từ snapshot và rowVersion null, gọi `saveAndFlush` **trước khi khóa inventory**. PK buộc hai transaction cùng tạo row phân xử tại bước này; bên thua rollback và retry toàn transaction theo cấu hình. Không tạo order/reservation trước flush. Đây là ngoại lệ cần duyệt vì `save()` một mình có thể hoãn INSERT tới sau khi đã khóa inventory, đảo thứ tự khóa. Nếu row đã tồn tại, dùng row có khóa và giữ counters cũ.
5. Gọi lại M5 sau khi đã chờ hoặc tạo limit. Tiếp đó gọi M15/M16/M17 theo selection để khóa các row inventory. Với nhiều ID, query có ORDER BY ID tăng dần ở DB; không chỉ sort kết quả Java. Trường hợp thiếu row vẫn phải kiểm lại receipt trước khi trả lỗi. Mọi flow ghi limit/inventory trong tương lai phải tuân thủ cùng thứ tự khóa để giảm deadlock.
6. Sau khi có các khóa cần thiết, gọi lại M5 trước khi áp guard limit/availability để request trùng đang chờ có thể nhận order vừa commit. Lấy M11 `clock_timestamp()` **sau khóa cuối**, kiểm PUBLISHED và cửa sổ `[saleStartsAt, saleEndsAt)`; sau đó kiểm limit, mode/kind/sector/type/seat và dữ liệu inventory. Không dùng row inventory cũ để bán ID đã bị bỏ khỏi snapshot.
7. M6 nhóm item theo type; không nhận giá từ FE. Tính quantity bằng long trước khi kiểm int32; tiền dùng BigDecimal, kiểm miền 19 chữ số. Kiểm `held + purchased + requestedQuantity <= purchaseLimit` bằng phép tính không tràn; không reset counters hoặc tự release đơn hết hạn.
8. Tạo order với giá/tên/SelectionSnapshot lấy từ cấu hình, discount=0, total=subtotal, state PENDING_PAYMENT, paymentFrozenAt/paymentSnapshotVersion/frozenPurchaseSnapshot null. Tạo reservation HELD chung expiry và timestamps DB. Save order/items rồi reservation để lấy ID Hibernate sinh; dùng object trả về từ save. Child mới trên root managed dùng cascade PERSIST tại flush; kiểm thực tế bằng test, không đổi ID generator/cascade để ép khớp.
9. M7: QUANTITY kiểm và tăng held trên các pool đã khóa. M8: STANDING cộng tổng quantity của mọi item và giữ sector đã khóa một lần; allocation theo item. M9: SEATED kiểm tất cả ghế đã khóa còn AVAILABLE, không hidden, đúng liên kết, rồi gắn reservation, tăng generation, chuyển HELD; không tăng pool sector/type.
10. M10 gắn allocation đúng một loại FK: ticketTypeId hoặc sectorId hoặc seatId; state HELD, quantity đúng item/ghế. Lưu inventory/limit/receipt bằng repository; receipt mới rowVersion null, resourceId đúng orderId, status 201. Map response trước khi rời transaction; commit lỗi đi ra retry/handler.

**Test D1–D8:** dùng PostgreSQL hiện có trong BookingPersistenceIT; bổ sung @Import service/mapper/converter và enable resilience cho proxy thật. Mỗi case có UUID riêng; test gọi service commit thật dùng NOT_SUPPORTED, không để transaction mặc định của @DataJpaTest che mất commit. F1–F3 chỉ dựng fixture/chụp dữ liệu, không tính expected outcome bằng thuật toán production.

**Mong đợi:** quantity giữ đúng type; standing 2+3 vé làm sector held tăng 5; seated tạo đúng assignments/generation và không tăng sector held. Thiếu inventory hợp lệ trong config trả CONFIG_NOT_READY, hết tài nguyên trả lỗi tương ứng. Tổng held/allocations/items khớp; replay giữ orderId/expiry, đổi input conflict; key của actor/operation khác không lấy nhầm đơn. Kiểm DB sau khi service đã trả về.

### P3 — Mở POST orders và kiểm boundary HTTP

**Phụ thuộc:** P2 đã tạo đơn thật, không nối controller vào method stub.

**Item:** C1, C8, C12; M0 REUSE, M1; H1–H6.

**File:**

- `order/controller/OrderController.java`
- `services/booking-service/src/test/java/com/flashticket/booking_service/order/controller/OrderControllerTests.java` — file mới duy nhất

**Làm:**

1. Thêm M1 với @PostMapping, @AuthenticationPrincipal Jwt, @RequestHeader UUID và @Valid @RequestBody hiện có. Lấy buyerSubject từ jwt.getSubject(); service từ chối subject trống. Giữ rule BUYER trong SecurityConfig.
2. Trả ResponseEntity 201 và Location sau khi OrderService proxy hoàn tất commit; view dùng OrderMapper hiện có. Controller không catch-all hoặc thao tác repository.
3. Test @WebMvcTest với @MockitoBean OrderService, security/config JSON/global handler đã có; không thêm nested config, controller probe, DTO hoặc setup/helper mới.
4. H1 kiểm subject/key/selection truyền đúng và response/Location. H2–H4 kiểm header/JSON/email/list/quantity, bao gồm cả ba selection; request hỏng chưa gọi service. H5 kiểm 401/403; H6 kiểm Problem từ BusinessException.

**Mong đợi:** đúng DTO B13, tiền/version là chuỗi, expiresAt biểu diễn UTC; body thêm buyerSubject/price bị strict Jackson từ chối. Test slice có mock service chỉ chứng minh HTTP, không dùng thay bằng chứng commit/DB của P2/P4.

### P4 — Kiểm cạnh tranh, rollback và bàn giao

**Phụ thuộc:** P1–P3.

**Item:** C13–C18; D9–D20, F1–F3; kiểm tích hợp M3b/M5/M11–M17.

**File:** `services/booking-service/src/test/java/com/flashticket/booking_service/persistence/BookingPersistenceIT.java`.

**Làm:**

1. D9/D10: gọi proxy từ thread/connection riêng để tranh cùng key, cả cùng và khác event. Cùng input trả cùng order; khác input conflict. Kiểm receipt/hash/resourceId không bị đè và không còn order/reservation/counter của transaction thua. Kiểm advisor retry nằm ngoài transaction; không gọi trực tiếp object new để kết luận retry hoạt động.
2. D11–D13: hai request/key khác nhau tranh ghế cuối, pool quantity/standing cuối và hạn mức buyer. Chỉ số request hợp lệ được commit; purchased không đổi, held không vượt capacity/limit, không có allocation/seat assignment trùng sai.
3. D14: tạo lỗi constraint ở receipt khi flush/commit, sau các bước tạo graph. Để lỗi đi ra proxy, kể cả lần thử thêm; so trạng thái DB trước/sau bằng F3 để chứng minh rollback cả graph/counter. Dọn constraint test trong finally; không sửa constraint môi trường người dùng.
4. D15: dùng transaction/connection test giữ khóa snapshot hoặc inventory, cho request bắt đầu chờ, thả khóa sau khi DB time đã qua saleEndsAt. Phải từ chối đơn, chứng minh clock được lấy sau khóa cuối; đồng bộ bằng latch/điều kiện DB, không dựa vào sleep mù.
5. D16: nhận config mới sau tạo đơn, kiểm giá/tên/selection snapshot/expiry của order cũ không bị đổi. Rerun consumer tests hiện có để bảo vệ cấu hình bán đã triển khai.
6. D17: connection test giữ khóa ghi ghế A; chờ order A thực sự đang chờ ghế sau khi lấy snapshot share. Order B khác buyer/ghế phải commit trước khi thả A. Kiểm SQL/lock thực tế, không chỉ dựa vào thời gian chạy.
7. D18: trong khi booking đang giữ snapshot share, consumer cập nhật cấu hình phải chờ; sau commit booking, consumer tiếp tục. D19: hai đơn đầu cùng buyer/event tạo đúng một purchase-limit row, bên thua PK rollback/retry trong transaction mới; kiểm cả trường hợp limit đủ và không đủ. D20: cùng key/limit=1, request đang chờ đọc lại receipt trước guard và nhận đúng order gốc.
8. Dùng executor/latch và công cụ JUnit/JDBC hiện có trong thân các test đã liệt kê. Nếu cần thêm class/method/callback override hoặc đổi annotation ngoài bảng để điều khiển race, trình delta; không mở rộng ngầm danh sách test helper.
9. Chạy kiểm tra toàn module ở §6, lưu đúng số test và kết quả thực tế. Không dùng số test của task consumer trước làm kết quả task này. Khi có môi trường tải riêng, đo các workload: nhiều buyer/ghế khác nhau, cùng ghế, cùng pool và cùng buyer; ghi throughput, p95/p99, lock wait, timeout và rollback. Chưa có số đo thì không khẳng định khả năng chịu tải.

**Mong đợi:** proxy commit/retry, FK/cascade PERSIST, timestamp DB và PK race được kiểm bằng DB thật trong môi trường test hiện có. Không claim đã chạy API trên server hoặc toàn luồng Payment.

## 6. Lệnh xác minh dự kiến

Thực hiện trong `D:/DATN/flash-ticket-platform/services/booking-service`, dùng JDK 23 đang có. Đây là lệnh cho pha thực thi, chưa được chạy khi viết plan.

Sau khi sửa Java, chạy `spotless:apply` trước lệnh verify để formatter check không chặn pha; rà diff để giữ thay đổi đúng phạm vi.

```powershell
$env:JAVA_HOME = 'C:\Users\sondimid\.jdks\openjdk-23'
$env:Path = "$env:JAVA_HOME\bin;$env:Path"

# P1: compile và regression nhỏ có sẵn
rtk proxy .\mvnw.cmd -B -ntp '-DskipTests' compile
rtk proxy .\mvnw.cmd -B -ntp '-Dtest=ErrorCodeTests,RuntimeConfigurationTests' test

# P2/P4: PostgreSQL, transaction và concurrency của Booking
rtk proxy .\mvnw.cmd -B -ntp -Pintegration '-Dit.test=BookingPersistenceIT' verify

# P3: HTTP boundary mới
rtk proxy .\mvnw.cmd -B -ntp '-Dtest=OrderControllerTests' test

# Bàn giao: toàn bộ unit + integration profile, formatter và SpotBugs trong lifecycle
rtk proxy .\mvnw.cmd -B -ntp spotless:apply
rtk proxy .\mvnw.cmd -B -ntp -Pintegration verify
rtk git diff --check
```

Profile integration và Testcontainers đã có trong dự án; cần runtime container khả dụng để chạy bộ đó, không thêm container/service triển khai hoặc đổi hệ thống server chỉ để viết plan. Nếu pha thực thi không có runtime test, báo phần chưa kiểm, không coi build/unit là bằng chứng race/rollback. Sau khi full verify đạt, chỉ chạy lại nếu có thay đổi hoặc lỗi cần kiểm tiếp.

Nếu user yêu cầu chạy ứng dụng như IntelliJ: chạy main class thật cùng Config Server và `.env` hiện tại, kiểm HTTP + DB với dữ liệu tổng hợp riêng; ghi rõ VM option/override thực tế. Không tự đổi `.env`, tác động process đang chạy, hoặc công bố đã deploy từ kết quả test.

## 7. Gate WRITING-PLANS và bàn giao

- Danh sách plan v0.2: **C1–C18; M1–M17 (M3a/M3b, M12 API sinh); H1–H6; D1–D20; F1–F3**. M0/R1 là tái sử dụng; không thêm class production.
- Delta cần duyệt: C14–C18/M13–M17 (lock repository), chữ ký M7–M9 nhận dữ liệu đã khóa, test D17–D20 và nội dung D15 mở rộng. Ngoại lệ `saveAndFlush` cho purchase limit mới phải được duyệt vì thay phương án chỉ save ở v0.1.
- Ngoại lệ khác: receipt @Version/cột row_version; Spring 7 retry một lần sau rollback; native SELECT clock_timestamp; khóa snapshot chia sẻ; class test/JUnit và annotation test đã nêu.
- Quyết định nghiệp vụ: user chọn hold duration 15 phút trong pha thực thi; chung expiry cho order/reservation, theo công thức của plan.
- Quyết định vận hành còn chờ: thời gian chờ lock và cách giới hạn request/connection; chưa giả định giá trị hoặc cách cấu hình áp dụng cho toàn Booking. Cần trình delta nếu thêm method/annotation/cấu hình.
- Pha hiện tại: **WRITING-PLANS — PENDING**. Nguồn hiện có chỉ là yêu cầu viết plan. Chưa có phản hồi xác nhận danh sách của pha này.
- Khi user xác nhận, ghi nguyên phạm vi/câu xác nhận và cập nhật trạng thái ngay tại mục này. Nếu chỉnh duration thì cập nhật con số trong design/plan; nếu đổi class/method/phong cách thì trình phần chênh lệch trước khi chốt.
- Chỉ chuyển sang shopapp-java-executing-plans khi user yêu cầu triển khai; pha đó có xác nhận riêng theo gate hiện hành. Plan này không tự cấp quyền commit/push/deploy hoặc sửa dữ liệu server.

## 8. Gate EXECUTING-PLANS

- Nguồn yêu cầu: user gọi `shopapp-java-executing-plans` và yêu cầu triển khai theo plan v0.2.
- Danh sách cần xác nhận cho pha thực thi: C1–C18; M1–M17 gồm M3a/M3b và API Lombok M12; H1–H6; D1–D20; F1–F3; annotation, thay đổi chữ ký M7–M9 và ngoại lệ `saveAndFlush`, retry, DB clock như các bảng trong plan.
- Trạng thái: **APPROVED** cho C1–C18, M1–M17 gồm M3a/M3b, M12 và H1–H6, D1–D20, F1–F3, kèm annotation/ngoại lệ trong plan. Nguồn: user trả lời “Xác nhận toàn bộ” cho câu hỏi pha EXECUTING-PLANS.
- Thời hạn giữ chỗ: user chọn **15 phút**. User chọn timeout chờ khóa **5 giây**; cách áp dụng riêng transaction tạo order là delta ở §9, chưa được duyệt.

## 9. Delta EXECUTING-PLANS — timeout khóa 5 giây

- Nguồn: user chọn “5 giây” cho thời gian chờ khóa của API order. Phạm vi cấu hình được đề xuất là **transaction tạo order**, giữ nguyên cấu hình consumer và các API khác.
- Hiện trạng đọc từ phiên DB Booking: `lock_timeout=0`; PostgreSQL giải thích `set_config(..., true)` chỉ áp dụng trong transaction hiện tại và tự hết sau commit/rollback: [PostgreSQL configuration functions](https://www.postgresql.org/docs/17/functions-admin.html#FUNCTIONS-ADMIN-SET).
- **L18 — NEW, PENDING:** `public abstract String configureTransactionLockTimeout(@Param("timeout") String timeout)` trong `OrderRepository`, annotation `@Query(value = "select set_config('lock_timeout', :timeout, true)", nativeQuery = true)`. Method nhận `5s`, không tự tạo class hoặc dùng EntityManager.
- **C3/C10 — CHANGE, PENDING:** thêm field `@Value("${booking.reservation.lock-timeout}") private String reservationLockTimeout` trong `OrderServiceImpl` và cấu hình YAML `booking.reservation.lock-timeout: 5s`. Field này không thay constructor Lombok và không sinh getter/setter. Gọi L18 đầu mỗi lần `createOrder` qua proxy, trước các query khóa; mỗi retry có transaction mới và tự cài lại timeout.
- **D21 — NEW, PENDING:** `void lockTimeoutIsScopedToOrderTransaction() throws Exception` trong `BookingPersistenceIT`, @Test package-private. Giữ khóa đối nghịch qua connection khác, kiểm request thất bại sau giới hạn và rollback; kiểm setting về lại giá trị phiên sau commit/rollback và không thay phiên consumer. Xác minh lỗi được global handler ánh xạ `RESOURCE_BUSY`; nếu loại exception thực tế không khớp, trình delta sửa cách ánh xạ trước khi viết.
- L18 là native SELECT **thứ hai**, ngoại lệ bổ sung ngoài M11 DB clock; chỉ dùng cho timeout transaction, không thực hiện INSERT/upsert. Không cấu hình timeout trên role/database hoặc toàn pool.
- Trạng thái delta L18/C3/C10/D21: **APPROVED**. Nguồn: user trả lời “Duyệt delta timeout” sau khi xem mã giả. Test D21 và kiểm ánh xạ exception vẫn chưa thực hiện.


## 10. Kết quả thực thi 2026-10-28

- P1–P3: đã viết repository, service và API theo danh sách được duyệt; cấu hình giữ chỗ 15 phút, lock timeout 5 giây.
- Bộ Maven test thông thường: 348 lượt đạt. Các test PostgreSQL đã chạy theo nhóm trên database test riêng ở server 119 (PostgreSQL 16) qua tunnel; database test đã được xóa sau khi kiểm tra.
- D1–D16, D18, D19 và D21: đạt khi chạy theo nhóm với timeout 5 giây; D11 chạy lại sau khi chấp nhận RESOURCE_BUSY hợp lệ khi chờ quá 5 giây.
- D17 và D20: không đạt với 5 giây trên đường kết nối 119 do timeout chờ khóa trong test; chạy lại đạt khi chỉ tăng cấu hình test lên 30 giây. Cấu hình ứng dụng vẫn 5 giây. Cần chạy lại hai test này với timeout 5 giây trong môi trường PostgreSQL ít độ trễ trước khi kết luận toàn bộ plan đã được xác nhận.
- Bộ integration đầy đủ với Testcontainers PostgreSQL 17 chưa chạy: Docker engine đang dừng.
