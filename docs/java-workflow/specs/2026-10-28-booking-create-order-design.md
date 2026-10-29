# Draft spec — Booking tạo đơn và giữ chỗ

- Ngày: 28/10/2026. Pha: **BRAINSTORMING**. Phiên bản: v0.1.
- Task: [API đặt ghế và tạo order, reservation, allocations](https://app.clickup.com/t/z8qgvc7vbb), trạng thái ClickUp khi đọc: `to do`.
- Trạng thái thiết kế, C1–C13, M1–M12, H1–H6, D1–D16: **PENDING**. Chưa có xác nhận cho pha này.
- Nguồn yêu cầu: user yêu cầu tiếp tục viết spec bằng `shopapp-java-brainstorming`; task ClickUp yêu cầu nhận selection, giữ chỗ và tạo dữ liệu trong một transaction, trả `201 OrderView` sau commit.
- Đây là thiết kế đề xuất; chưa viết Java, thay schema, chạy test hoặc triển khai.

## 1. Mục tiêu và phạm vi

Hiện thực `POST /api/v1/orders` cho BUYER. Tái sử dụng contract hiện có với cả ba selection: QUANTITY, SEAT_MAP/STANDING và SEAT_MAP/SEATED. Một request SEAT_MAP chỉ chọn một sector, dù cấu hình event có thể chứa nhiều sector/kind.

Thành công tạo một order `PENDING_PAYMENT`, một reservation `HELD`, các order item/seat assignment và allocation tương ứng. Tăng đúng phần nguồn cung và hạn mức đang giữ; tất cả cùng commit với receipt idempotency. Mất HTTP response thì gọi lại cùng key để lấy cùng order.

Task dừng ở tạo đơn. Áp voucher, freeze tiền, phát command Payment, chấp nhận thanh toán, hủy và worker trả giữ chỗ hết hạn thuộc task khác. Task này lưu `expiresAt` nhưng chưa tự giải phóng tài nguyên khi đến hạn; chưa được coi đây là luồng mua vé hoàn chỉnh.

## 2. Nguồn đã đọc và cách áp dụng

| Nguồn | Phần dùng |
| --- | --- |
| [B13 §2–3](../../architecture/B13-api-and-event-contracts.md) | POST orders, JWT subject, Idempotency-Key, ba selection, OrderView, lỗi và giá từ Booking |
| [B14 §1–2](../../architecture/B14-main-flow-sequences.md) | Transaction Booking, khóa snapshot trước tài nguyên, đồng hồ DB sau khi chờ khóa, chống trùng và rollback |
| [OrderController](../../../services/booking-service/src/main/java/com/flashticket/booking_service/order/controller/OrderController.java), [OrderServiceImpl](../../../services/booking-service/src/main/java/com/flashticket/booking_service/order/service/impl/OrderServiceImpl.java) | Hai lớp đã có, chưa có method tạo đơn; triển khai vào đây |
| [CreateOrderRequest](../../../services/booking-service/src/main/java/com/flashticket/booking_service/dto/request/CreateOrderRequest.java), [OrderSelection](../../../services/booking-service/src/main/java/com/flashticket/booking_service/dto/request/OrderSelection.java), [OrderMapper](../../../services/booking-service/src/main/java/com/flashticket/booking_service/mapper/OrderMapper.java) | Giữ DTO/validation/Jackson và mapper response đã có |
| [Order](../../../services/booking-service/src/main/java/com/flashticket/booking_service/entity/Order.java), OrderItem, OrderItemSeat, Reservation, ReservationAllocation | Giữ entity, UUID sinh bởi Hibernate, setter và quan hệ cascade PERSIST hiện có |
| [IdempotencyRequest](../../../services/booking-service/src/main/java/com/flashticket/booking_service/entity/IdempotencyRequest.java), [DDL Booking](../../architecture/data/postgres/20-booking.sql) | Entity và DDL hiện chưa có row_version của receipt HTTP; đây là đề xuất thêm, chưa xác minh DB live trong pha này |
| Shop App `controllers/OrderController.createOrder` | Controller, `@Valid`, `@RequestBody`, ResponseEntity và constructor injection |
| Shop App `services/impl/OrderService.createOrder` | Service trực tiếp gọi repository, tạo entity/setter và save; không sao chép catch-all/raw error hoặc ModelMapper |
| [Spring Data JPA: locking](https://docs.spring.io/spring-data/jpa/reference/jpa/locking.html) | Repository `@Lock`; tái sử dụng query khóa snapshot đã có |
| [Spring Data JPA: entity persistence](https://docs.spring.io/spring-data/jpa/reference/jpa/entity-persistence.html) | Boxed version null giúp assigned/composite ID được nhận là entity mới khi save |
| [Spring Framework: retry cùng transaction](https://docs.spring.io/spring-framework/reference/core/resilience.html#resilience-retryable) | Retry nằm ngoài transaction; lần thử lại dùng transaction mới |
| [Spring trên GitHub: RetryAnnotationBeanPostProcessor v7.0.9](https://github.com/spring-projects/spring-framework/blob/v7.0.9/spring-context/src/main/java/org/springframework/resilience/annotation/RetryAnnotationBeanPostProcessor.java) | Đã đối chiếu `setBeforeExistingAdvisors(true)` với spring-context 7.0.9 trong classpath local |
| [Spring Data trên GitHub: RoleRepository 4.1.1](https://github.com/spring-projects/spring-data-jpa/blob/4.1.1/spring-data-jpa/src/test/java/org/springframework/data/jpa/repository/sample/RoleRepository.java) | Ví dụ khai báo lock tại repository; phù hợp spring-data-jpa 4.1.1 local, không lấy Querydsl hoặc tầng phụ |
| [PostgreSQL 17: clock_timestamp](https://www.postgresql.org/docs/17/functions-datetime.html#FUNCTIONS-DATETIME-CURRENT) | Lấy thời điểm thực sau khi chờ khóa, khác thời điểm bắt đầu transaction của now() |

Shop App mẫu: `C:/Users/sondimid/IdeaProjects/shopapp-backend/shopapp-backend/shopapp/src/main/java/com/project/shopapp/`. Code đích giữ Java 23, Boot 4.1.1, Jackson 3 và convention của Flash Ticket.

B13 có dòng audience cũ; runtime theo yêu cầu mới nhất của user: issuer-uri, không thêm audience/JWKS riêng.

## 3. API và dữ liệu

- Header bắt buộc: `Authorization: Bearer ...`, `Idempotency-Key: <UUID>`.
- Subject lấy từ JWT đã xác thực; request không nhận buyerSubject, userId, giá hoặc tổng tiền do FE tính.
- Controller dùng DTO và Bean Validation có sẵn. Thiếu/sai header UUID, JSON sai, field lạ, email sai, quantity không nguyên/dương, list rỗng/trùng ID bị từ chối trước khi giữ chỗ.
- Response `201 OrderView` hiện có; `Location: /api/v1/orders/{orderId}`. Replay cùng input vẫn trả 201 và cùng resource; trạng thái trong view là trạng thái đang lưu của cùng order, không hứa response byte-for-byte bất biến.

Ví dụ SEATED:

```json
{
  "eventId": "11111111-1111-4111-8111-111111111111",
  "customerEmail": "buyer@example.com",
  "selection": {
    "mode": "SEAT_MAP",
    "kind": "SEATED",
    "sectorId": "22222222-2222-4222-8222-222222222222",
    "seatIds": ["33333333-3333-4333-8333-333333333333"]
  }
}
```

QUANTITY dùng `selection={"mode":"QUANTITY","items":[{"ticketTypeId":"<UUID>","quantity":2}]}`. STANDING thêm `mode=SEAT_MAP`, `kind=STANDING`, `sectorId`, cùng danh sách items. Không tạo DTO mới cho ba nhánh.

### Thời hạn giữ chỗ — PENDING

Đề xuất `booking.reservation.hold-duration: PT15M` trong YAML Config Server, đọc bằng field `@Value` kiểu Duration ở OrderServiceImpl. User đang được hỏi chọn 15 hoặc 10 phút; chưa có câu trả lời thì 15 phút chỉ là đề xuất.

`decisionAt` lấy từ DB sau khóa; `expiresAt = decisionAt + holdDuration`. Order và reservation dùng đúng một giá trị expiresAt; replay không gia hạn. Duration bằng 0/âm bị từ chối như lỗi cấu hình. Đề xuất không tự cắt expiry theo saleEndsAt: saleEndsAt chặn tạo đơn mới, còn giữ chỗ đã tạo theo expiresAt. Các lựa chọn này cùng chờ duyệt thiết kế.

## 4. Luồng chính và transaction

Controller → OrderService qua Spring proxy → repository. Controller trả response sau khi service proxy đã commit. Không mở transaction ngoài lời gọi này và không gọi HTTP/Redis/RabbitMQ trong transaction.

1. Kiểm subject không trống; tạo composite key `(buyerSubject, "CREATE_ORDER", idempotencyKey)` và SHA-256 từ Jackson serialization của CreateOrderRequest đã parse. JSON property order từ FE không ảnh hưởng; thứ tự phần tử trong list vẫn thuộc input, đổi thứ tự với cùng key được coi khác input. Không tự chuẩn hóa email hay thêm canonicalization framework.
2. Tìm receipt đã commit. Có receipt: so hash; khác trả IDEMPOTENCY_CONFLICT. Giống: tìm đúng order và kiểm subject, map OrderView rồi trả; không kiểm lại điều kiện bán hoặc giữ thêm. Receipt có mà order thiếu không được âm thầm tạo lại đơn.
3. Chưa có receipt: dùng `EventSalesSnapshotRepository.findByEventId(eventId)` hiện có với PESSIMISTIC_WRITE; thiếu snapshot trả CONFIG_NOT_READY. Sau khi chờ khóa, đọc receipt lại để nhận kết quả request cùng event vừa commit.
4. Lấy clock_timestamp từ DB. Kiểm event PUBLISHED và `saleStartsAt <= decisionAt < saleEndsAt`, mode/sector/kind đúng snapshot. ID được chọn phải còn trong cấu hình hiện tại, không chỉ tồn tại trong row inventory cũ. Lấy tên, giá, currency và thông tin ghế từ snapshot của Booking.
5. Tính tổng quantity bằng kiểu đủ lớn, từ chối vượt int32; nhóm ghế theo ticketTypeId. Tạo order/items, tính `lineSubtotal = unitPrice × quantity`, subtotal là tổng dòng, discount=0, total=subtotal, currency=VND; tổng phải >0 và nằm trong numeric(19,0). Giá/số tiền trong response dùng mapper hiện có.
6. Đọc purchase limit theo `(eventId,buyerSubject)` sau khóa snapshot. Chưa có thì new entity với rowVersion null, held/purchased=0; đã có giữ counter. Áp hạn mức từ snapshot và kiểm `held + purchased + requestedQuantity <= purchaseLimit` bằng phép tính không tràn số. Không tự giải phóng đơn hết hạn trong API tạo đơn.
7. Tạo order PENDING_PAYMENT và reservation HELD với cùng expiry; save order/items bằng cascade PERSIST hiện có rồi save reservation để lấy UUID Hibernate sinh. Giữ giá trị trả về từ save. Các graph đã lưu đều thuộc transaction hiện tại; mọi lỗi về sau vẫn rollback toàn bộ.
8. Giữ nguồn cung theo §5, tạo seat assignments/allocations gắn đúng order/item/reservation. Với child thêm vào root managed sau save, dùng cascade PERSIST khi flush; không đổi thành cascade ALL/MERGE hàng loạt hoặc tự gán UUID cho entity đang dùng GeneratedValue. Kiểm chứng cách cascade này bằng PostgreSQL/Hibernate integration test.
9. Tăng purchaseLimit.held đúng tổng quantity, cập nhật configuredLimit/sourceConfigVersion; save limit và các inventory thay đổi bằng repository. Giữ purchased/used không đổi.
10. Save receipt với resourceId=orderId, responseStatus=201, createdAt=decisionAt, rowVersion null. Map OrderView trong transaction để không phụ thuộc open-in-view. Commit hoàn tất rồi HTTP mới trả thành công.

### Khóa và idempotency khi cạnh tranh — PENDING

- Đề xuất tái sử dụng **khóa độc quyền snapshot cho mọi writer của cùng event** ở bước đầu; nguồn cung/hạn mức được đọc và sửa dưới khóa này, đồng thời giữ @Version hiện có. Không thêm lock query cho từng repository, khóa Redis hoặc bảng khóa mới.
- Khóa snapshot ngăn hai request cùng event cùng giữ ghế/counter và chặn consumer sửa cấu hình giữa lúc tạo đơn. Đây là lựa chọn đơn giản nhưng serialize các đơn cùng event; chưa cam kết throughput flash-sale, chưa triển khai tối ưu shared lock.
- Không có snapshot row thì trả CONFIG_NOT_READY; API này không tự tạo snapshot. Purchase limit mới được tạo dưới cùng khóa event nên các đường cùng convention không đua INSERT cặp buyer/event.
- Receipt có composite ID nhưng chưa có version: đề xuất thêm `@Version Long rowVersion` + default DDL 0 như InboxMessage. Receipt mới để null để save INSERT, PK mới phân xử được hai request cùng key nhưng chọn hai event khác nhau; không dùng save/merge ghi đè receipt đã có.
- Receipt được INSERT trong cùng transaction ở cuối, không ép flush sớm. So với mô tả B14 về đăng ký/serialize key trước tài nguyên, bản này dùng **PK lúc flush/commit + rollback và đọc lại trong transaction mới**. Có thể làm thêm công việc trước khi thua PK, nhưng transaction thua không để lại giữ chỗ/đơn/counter.
- Đề xuất Spring 7 `@Retryable(includes=DataIntegrityViolationException.class, maxRetries=1, delay=0)` cùng @Transactional trên createOrder, bật @EnableResilientMethods ở application hiện có. Tối đa hai lần chạy, không catch lỗi rồi tiếp tục transaction hỏng; lần chạy mới tra receipt từ đầu. Nếu request thắng đã rollback, request còn lại vẫn có thể tạo một đơn hợp lệ.
- Hibernate gom lỗi unique và các lỗi constraint khác vào DataIntegrityViolationException. Annotation này có thể thử thêm một lần cả lỗi FK/CHECK; **không coi lỗi đó là thành công**. Chỉ trả lại order khi có receipt cùng actor/operation/key, đúng hash và đúng chủ; lỗi constraint còn lại tiếp tục thất bại qua global handler. Không thêm custom retry predicate/lớp điều phối cho task này.
- Lỗi optimistic lock, lock timeout, mất DB không thuộc retry trên. Global handler hiện có xử lý; FE thử lại cùng key khi chưa biết kết quả. Không áp annotation này lên Rabbit listener hoặc thay policy redelivery hiện có.

## 5. Cách giữ từng loại nguồn cung

| Selection | Kiểm tra và thay đổi | Dữ liệu con |
| --- | --- | --- |
| QUANTITY | Các type thuộc event và còn trong snapshot; inventory đủ `capacity - held - purchased`. Tăng held từng type đúng quantity | Một item và một TICKET_TYPE allocation mỗi type; allocation chỉ có ticketTypeId |
| STANDING | Sector đúng event/kind; mọi type thuộc sector đang chọn. Kiểm **tổng** quantity của các type với pool sector, tăng sector.held đúng một lần | Item riêng theo type, STANDING_SECTOR allocation riêng theo item; tổng quantity allocations bằng mức tăng pool; allocation có sectorId, ticketTypeId/seatId null |
| SEATED | Ghế đủ số lượng, đúng event/sector, type khớp snapshot, không hidden, state AVAILABLE và không có currentReservationId. Tăng generation và gắn reservationId, chuyển HELD | Nhóm item theo type; lưu SelectionSnapshot giữ nhãn/row/số ghế, OrderItemSeat và một SEAT allocation/ghế với quantity=1; allocation chỉ có seatId |

- SEATED không tăng held của sector hoặc type inventory. Sector SEATED giữ metadata/capacity cấu hình.
- Không reset counter, ghi đè PURCHASED/HELD, hoặc tự cướp ghế vì reservation có vẻ đã hết hạn. Việc release phải do luồng có guard riêng thực hiện.
- Duyệt resource theo UUID tăng dần để thống nhất thứ tự xử lý và giảm nguy cơ khóa ngược khi DB flush; không coi Java sort sau query là một row-lock query.
- Allocation có state HELD, orderId/eventId/item/reservation nhất quán. Đảm bảo tổng quantity allocations bằng tổng items và phần hạn mức đã giữ.
- Thiếu inventory cho ID hợp lệ trong snapshot trả CONFIG_NOT_READY; ID không thuộc selection/cấu hình trả INVALID_SELECTION; ghế hợp lệ nhưng đã bị giữ/ẩn/bán trả SEAT_UNAVAILABLE.

## 6. Lỗi và tính nguyên tử

| Trường hợp | HTTP / ErrorCode |
| --- | --- |
| Chưa đăng nhập / thiếu BUYER | 401 UNAUTHENTICATED / 403 ACCESS_DENIED, dùng security hiện có |
| Header, JSON, email, list hoặc quantity sai hình dạng | 400 INVALID_REQUEST hoặc VALIDATION_FAILED theo global handler hiện có |
| Chưa có snapshot / inventory của config chưa sẵn sàng | 503 CONFIG_NOT_READY |
| Event chưa PUBLISHED hoặc ngoài cửa sổ bán | **NEW** 409 EVENT_NOT_ON_SALE |
| Mode/sector/type/seat không khớp, tổng quantity vượt int32 | **NEW** 422 INVALID_SELECTION |
| Ghế không khả dụng / pool không đủ / vượt hạn mức | 409 SEAT_UNAVAILABLE / INSUFFICIENT_QUANTITY / PURCHASE_LIMIT_EXCEEDED |
| Cùng idempotency key nhưng khác input | 409 IDEMPOTENCY_CONFLICT |
| Tổng tiền vượt miền numeric(19,0) | **NEW** 422 ORDER_AMOUNT_EXCEEDED |
| Hold duration không dương | **NEW** 500 INVALID_RESERVATION_DURATION |
| Bất đồng version / khóa bận / DB timeout hoặc mất kết nối | CONCURRENT_MODIFICATION / RESOURCE_BUSY / DATASTORE_TIMEOUT / DATASTORE_UNAVAILABLE hiện có |

Mọi lỗi phát sinh sau save, kể cả khi commit, phải rollback order/items/seat assignments/reservation/allocations/held/receipt cùng nhau. Không bắt BusinessException rồi trả response thành công. Không ánh xạ mọi constraint DB thành SEAT_UNAVAILABLE hoặc IDEMPOTENCY_CONFLICT.

## 7. Class/file đề xuất — toàn bộ PENDING

Đường dẫn Java bên dưới tính từ `services/booking-service/src/main/java/com/flashticket/booking_service/`, trừ file test/config được ghi rõ.

| ID | Class/file | NEW/EXISTING | Vai trò, annotation/API sinh | Mẫu hoặc ngoại lệ |
| --- | --- | --- | --- | --- |
| C1 | order/controller/OrderController.java | EXISTING | Thêm POST; giữ @RestController/@RequestMapping/@RequiredArgsConstructor và dependency final hiện có | Shop App OrderController; lấy Jwt subject thay userId do FE gửi; không catch-all |
| C2 | order/service/OrderService.java | EXISTING | Thêm một method nghiệp vụ, giữ interface thường | Shop App IOrderService; giữ tên/package hiện có |
| C3 | order/service/impl/OrderServiceImpl.java | EXISTING | Giữ @Service/@RequiredArgsConstructor; 10 dependency final nêu M3; property `@Value("${booking.reservation.hold-duration}") private Duration reservationHoldDuration`, không sinh getter/setter cho property | Shop App OrderService + property theo JwtTokenUtil; @Transactional và retry là ngoại lệ có căn cứ Spring |
| C4 | order/repository/OrderRepository.java | EXISTING | Thêm đúng query DB time ở M11; findById/save kế thừa | Ngoại lệ native SELECT chỉ để lấy clock_timestamp theo B14, không native INSERT/upsert |
| C5 | entity/IdempotencyRequest.java | EXISTING | Thêm field `@Version @ColumnDefault("0") @Column(name="row_version",nullable=false) private Long rowVersion`; @Getter/@Setter sẵn có sinh đúng hai API M12; constructor no-args giữ nguyên | Cùng kiểu InboxMessage đã được chọn; không thêm Persistable/lifecycle helper |
| C6 | BookingServiceApplication.java | EXISTING | Thêm @EnableResilientMethods, giữ main hiện có | Spring 7; tương tự cách enable ở IdentityMessagingConfig, chỉ retry method có annotation |
| C7 | exception/ErrorCode.java | EXISTING | Thêm bốn enum constant ở §6, giữ constructor/status/message/from hiện có | Convention error code cụ thể; không tạo exception class mới |
| C8 | DTO/request, DTO/response, snapshot, OrderMapper và MappingValueConverter | EXISTING, REUSE | Không thêm type, mapper method, annotation hoặc API sinh | Giữ contract cũ đã duyệt, dù một số DTO/record khác mẫu Shop App class |
| C9 | Order/OrderItem/OrderItemSeat/Reservation/ReservationAllocation/PurchaseLimit và ba inventory, các repository tương ứng | EXISTING, REUSE | Giữ entity/quan hệ/getter/setter/constructor; dùng addItem/addSeatAssignment/addAllocation có sẵn và repository find/save/saveAll | Không thêm repository wrapper, generic service, EntityManager hoặc flush thủ công |
| C10 | infrastructure/config-server/src/main/resources/config/booking-service.yaml | EXISTING | Thêm booking.reservation.hold-duration, giá trị đang chờ chọn | Property ở field như convention Shop App; chưa sửa .env |
| C11 | docs/architecture/data/postgres/20-booking.sql | EXISTING | Cập nhật baseline receipt row_version bigint NOT NULL DEFAULT 0; runtime vẫn ddl-auto update | Không tạo migration framework; kiểm schema thật khi thực thi |
| C12 | src/test/java/.../order/controller/OrderControllerTests.java | **NEW** | Package-private test class, @WebMvcTest(OrderController.class), @Import SecurityConfig/BookingJsonConfig/GlobalExceptionHandler, @MockitoBean OrderService; MockMvc/JsonMapper inject cho test; implicit constructor no-args | Ngoại lệ test JUnit/Spring, H1–H6 |
| C13 | src/test/java/.../persistence/BookingPersistenceIT.java | EXISTING | Dùng PostgreSQL Testcontainers và initializer có sẵn; import OrderServiceImpl, mapper/converter và enable resilience để gọi proxy thật; thêm field OrderService; D1–D16 | Giữ EntityManager/TransactionTemplate sẵn có chỉ trong test; không đưa vào production |

Không tạo class production mới. Không thêm dependency Java hoặc framework quản lý idempotency/retry. Nếu test cần thêm nested config/type/method ngoài bảng, trình delta trước khi viết.

## 8. Constructor/method — toàn bộ NEW/CHANGE còn PENDING

Các chữ ký dưới là thiết kế để duyệt, không phải Java đã triển khai. Method không ghi throws không khai báo checked exception.

| ID | Class | Chữ ký đầy đủ | NEW/REUSE | Cách viết/mục đích |
| --- | --- | --- | --- | --- |
| M0 | OrderController | `public OrderController(OrderService orderService)` | REUSE | Constructor Lombok hiện có, không đổi |
| M1 | OrderController | `public ResponseEntity<OrderView> createOrder(@AuthenticationPrincipal Jwt jwt, @RequestHeader("Idempotency-Key") UUID idempotencyKey, @Valid @RequestBody CreateOrderRequest request)` | NEW | @PostMapping; gọi service, trả 201 sau commit |
| M2 | OrderService | `public abstract OrderView createOrder(String buyerSubject, UUID idempotencyKey, CreateOrderRequest request)` | NEW | Method trong **interface**, không tạo abstract class |
| M3a | OrderServiceImpl | `public OrderServiceImpl(OrderRepository orderRepository, IdempotencyRequestRepository idempotencyRequestRepository, OrderMapper orderMapper, EventSalesSnapshotRepository eventSalesSnapshotRepository, PurchaseLimitRepository purchaseLimitRepository, TicketTypeInventoryRepository ticketTypeInventoryRepository, SectorInventoryRepository sectorInventoryRepository, SeatInventoryRepository seatInventoryRepository, ReservationRepository reservationRepository, JsonMapper objectMapper)` | CHANGE | Constructor @RequiredArgsConstructor sinh từ 10 dependency final; property @Value không nằm trong constructor |
| M3b | OrderServiceImpl | `public OrderView createOrder(String buyerSubject, UUID idempotencyKey, CreateOrderRequest request)` | NEW | @Override, @Transactional, Spring 7 @Retryable như §4; nghiệp vụ trực tiếp gọi repository |
| M4 | OrderServiceImpl | `private String calculateCreateOrderRequestHash(CreateOrderRequest request)` | NEW | Jackson serialization + SHA-256 bằng JDK; không decoder/canonicalizer tự viết |
| M5 | OrderServiceImpl | `private Optional<OrderView> findReplayedOrder(IdempotencyRequestId requestId, String requestHash)` | NEW | Tra receipt, so hash, load order đúng chủ, map trong transaction; dùng trước và sau khóa snapshot |
| M6 | OrderServiceImpl | `private void populateOrderItems(Order order, CreateOrderRequest request, SalesConfiguration configuration)` | NEW | Kiểm selection theo snapshot, nhóm item/type, đặt giá và tổng tiền bằng setter; không dùng DTO mapper cho field do server quyết định |
| M7 | OrderServiceImpl | `private void reserveQuantityTickets(Order order, Reservation reservation, OrderSelection.QuantitySelection selection)` | NEW | Giữ pool từng type và thêm allocations |
| M8 | OrderServiceImpl | `private void reserveStandingTickets(Order order, Reservation reservation, OrderSelection.StandingSelection selection)` | NEW | Giữ một pool sector chung, allocation theo item |
| M9 | OrderServiceImpl | `private void reserveSeatedTickets(Order order, Reservation reservation, OrderSelection.SeatedSelection selection)` | NEW | Kiểm/lưu ghế HELD, generation, seat assignments và allocations |
| M10 | OrderServiceImpl | `private void addReservationAllocation(Reservation reservation, OrderItem item, ResourceKind resourceKind, UUID resourceId, int quantity)` | NEW | Điền field liên kết chung và đúng một nhóm resource FK, gọi addAllocation; helper dùng cho ba nhánh |
| M11 | OrderRepository | `public abstract Instant currentDatabaseTime()` | NEW | @Query(value="select clock_timestamp()", nativeQuery=true); query chỉ đọc; kiểm conversion timestamptz→Instant với PostgreSQL thật |
| M12 | IdempotencyRequest | `public Long getRowVersion()`; `public void setRowVersion(Long rowVersion)` | NEW, Lombok-generated | Chỉ API của field mới; không tự set/tăng version trong nghiệp vụ |
| R1 | Existing entities/repos/mapper | `Order.addItem`, `OrderItem.addSeatAssignment`, `Reservation.addAllocation`, `OrderMapper.toView/toItemView`, entity getters/setters, `findById/findAllById/save/saveAll`, `EventSalesSnapshotRepository.findByEventId` | REUSE | Không đổi chữ ký/annotation/API sinh của các method này |

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
| D15 | BookingPersistenceIT | `void saleDecisionUsesDatabaseTimeAfterWaitingForSnapshotLock() throws Exception` | Chờ khóa qua thời điểm đóng bán phải bị từ chối; không dùng timestamp đầu transaction |
| D16 | BookingPersistenceIT | `void changingConfigurationDoesNotRepriceExistingOrder()` | Snapshot mới không thay giá/tên/seat snapshot/expiry của đơn cũ |
| F1 | BookingPersistenceIT | `private SalesConfiguration seedOrderCreationConfiguration(SalesMode mode, SectorType sectorType, long capacity, int purchaseLimit)` | Helper fixture duy nhất cho config; UUID riêng từng test, window theo DB time, gọi consumer/service có sẵn |
| F2 | BookingPersistenceIT | `private CreateOrderRequest createOrderRequest(SalesConfiguration configuration, int quantity)` | Dựng request theo mode/kind; không tính expected outcome từ code production |
| F3 | BookingPersistenceIT | `private Map<String, Object> captureOrderCreationState(UUID eventId)` | Chụp graph/counters/receipt để so trước–sau rollback, không chỉ đếm HTTP success |

F1–F3 cũng là method mới **PENDING**, không phải phê duyệt mở cho helper khác. Test cạnh tranh gọi service proxy thật từ các thread/connection riêng; không bọc cả lời gọi trong transaction của test. Không lấy việc unit test gọi trực tiếp object làm bằng chứng retry/commit hoạt động.

## 9. Điểm khác mẫu và điều kiện chốt

Đề xuất giữ MapStruct/DTO/record/entity hiện có; không đổi sang ModelMapper, Long/Date hoặc đổi package chỉ để giống Shop App. Phần thêm dùng controller/service/setter/repository đơn giản.

Các ngoại lệ cần duyệt rõ: `@Version` cho receipt HTTP, Spring 7 retry tối đa một lần sau rollback, native SELECT DB clock và khóa snapshot độc quyền thay tối ưu shared/resource locks. Chỉ sửa baseline DDL và dùng ddl-auto update khi thực thi; chưa tác động DB/server ở pha spec.

Chờ user chốt:

1. Hold duration, công thức expiry và phạm vi cả ba selection như trên.
2. C1–C13, M1–M12 gồm M3a/M3b và API Lombok; H1–H6, D1–D16, F1–F3; bốn error code và ngoại lệ đã nêu.

Theo `shopapp-java-brainstorming`, phải có phản hồi xác nhận danh sách trước khi đổi trạng thái APPROVED. Duyệt thiết kế không tự cho phép chuyển sang writing-plans, viết Java, commit/push hoặc deploy; các pha tiếp theo có gate riêng theo skill.
