# Plan Redis cache cho GET availability theo eventId

- Ngày: 04/11/2026. Phiên bản plan: v0.1.
- Pha: **WRITING-PLANS — APPROVED**.
- Pha thực thi: **EXECUTING-PLANS — APPROVED / COMPLETED**. Nguồn ngày 04/11: user chọn “Xác nhận thực thi đúng danh sách đã duyệt” trong câu hỏi cho C1–C10/M1–M49 và P0–P6, trước lần ghi Java đầu tiên.
- Checkout thực thi: D:/DATN/flash-ticket-platform-redis-cache, branch codex/booking-availability-redis-cache từ dev/son ff72b286df051458a7e4635412e816b1402ce6a2; giữ workspace dev/tuyen và file Payment/Postman có sẵn. JDK 23.0.1, Docker và dependency test đã xác minh; kết quả P0–P6 ghi tại §8. Phạm vi bàn giao ghi tại §9.
- Thiết kế: [spec v0.4](../specs/2026-11-02-booking-availability-redis-cache-design.md), **BRAINSTORMING APPROVED** theo user “đồng ý” với luồng và “xác nhận” với class/method ngày 04/11.
- Phê duyệt pha WRITING-PLANS ngày 04/11/2026: user trả lời **“đồng ý plan”** sau phần làm rõ cơ chế invalidation/generation. Phạm vi APPROVED: C1–C10/M1–M49, annotation/API Lombok/ngoại lệ/test và các bước P0–P6 của plan v0.1. Không có delta so với danh sách đã trình.
- Không có delta chữ ký/class/annotation so với spec v0.4 đã duyệt. Không tạo Java, chạy build/runtime, checkout/merge, commit/push hoặc deploy trong lượt lập plan.

## 1. Kết quả cần đạt và baseline

GET /api/v1/events/{eventId}/availability đọc cache Redis trước, miss mới gọi service DB hiện có; giữ JSON Availability và Cache-Control: no-store. JSON TTL mặc định PT15M, cấu hình PT10M được. Inventory/config/event status thay đổi trong transaction phát AvailabilityChangedEvent; listener AFTER_COMMIT đổi generation của đúng event. GET sau invalidation thành công dùng generation mới, không đọc lại JSON của generation cũ.

RedisService chỉ gom get/set/setIfAbsent/clear key chuỗi. AvailabilityCacheService giữ JSON, TTL, key, generation, log và fallback. Không thêm Spring Cache abstraction, schema/migration, dependency, distributed lock, Lua, outbox hoặc retry Redis bền.

Baseline đã kiểm ngày 04/11:

| Nguồn | Phát hiện và hệ quả |
| --- | --- |
| Workspace D:/DATN/flash-ticket-platform | Đang ở dev/tuyen, commit 9a8bac5; CodeGraph cho thấy controller/service availability còn scaffold. Không triển khai plan trực tiếp trên trạng thái này |
| Local dev/son | Commit ff72b286df051458a7e4635412e816b1402ce6a2; đã đọc getAvailability, hai writer config/cancellation, helper inventory, DTO và fixture/test bằng git show |
| Git common directory | D:/DATN/flash-ticket-platform/.git; áp convention Flash Ticket cho cả checkout/worktree của repo |
| Version baseline | Boot 4.1.1, Java 23, Jackson 3; Redis starter đã có; không lấy Java 21 trên dev/tuyen làm version đích |
| Test/build | Maven wrapper; Surefire unit/MVC, Failsafe profile integration với BookingPersistenceIT, Spotless/SpotBugs/JaCoCo đã có |
| Tài liệu plan hiện tại | Chưa có plan Redis cache trùng tên để cập nhật; tạo file này, giữ nguyên đường dẫn spec |

**Điều kiện trước thực thi:** dùng checkout đang có baseline dev/son nói trên hoặc hậu duệ chứa GET/writer tương đương; xác minh lại diff nếu baseline đổi. Không tự viết lại GET trên scaffold dev/tuyen hoặc merge toàn bộ nhánh khác để bù thiếu. Không bắt buộc tạo worktree; việc chọn checkout phù hợp là bước chuẩn bị của pha execute. Không tác động các file Payment/Postman/.codex-attachments đang có trong workspace.

## 2. Đường dẫn và đối chiếu convention

Trong bảng bên dưới, tên file Java được resolve như sau:

- Main root: D:/DATN/flash-ticket-platform/services/booking-service/src/main/java/com/flashticket/booking_service/.
- Test root: D:/DATN/flash-ticket-platform/services/booking-service/src/test/java/com/flashticket/booking_service/.
- Khi dùng checkout khác đã có baseline, thay phần root workspace tương ứng; package/relative path giữ nguyên.

Đã đọc common.md, projects/INDEX.md, projects/flash-ticket-platform.md tại C:/Users/sondimid/.codex/conventions/. Những rule ảnh hưởng trực tiếp được áp dụng:

| Rule/mẫu đã đọc | Áp dụng và source đối chiếu |
| --- | --- |
| Tái sử dụng đúng trách nhiệm, RedisService theo yêu cầu ngày 04/11 | C10 chỉ 5 operation phục vụ C1; không Redis repository/interface/adapter khác. C3/M5 vẫn là đường DB có transaction, không copy projection sang cache |
| Constructor final dependency, property field @Value | Shop App services/impl/ProductService.java và controllers/ProductController.java; components/JwtTokenUtil.java. C1/C10 dùng @RequiredArgsConstructor; M25/M26 thêm publisher vào cuối dependency |
| Class Lombok thay record, annotation đủ dùng | Shop App DTO/CategoryDTO.java làm mẫu class; C7 chỉ @Getter/@AllArgsConstructor cho UUID eventId, không sao chép @Data/@Setter/annotation trùng |
| Không thêm validation/query/helper thừa | Writer baseline đã kiểm ID/quan hệ/khóa; M27–M37 chỉ thêm publish sau thay đổi. Cache hit không query DB để xác minh inventory |
| Mapper có sẵn | Dùng JsonMapper/BookingJsonConfig và BookingFixtures.jsonMapper(); round-trip DTO thật trong M8/M9, không Object mapper mới/generic decoder/Jackson 2 |
| Transaction/locks/ACK | M5 giữ REPEATABLE_READ readOnly; M27–M37 không sửa transaction/lock/replay/AUTO ACK. Event chỉ chờ transaction bao ngoài commit |
| Error policy/logging | C10 để DataAccessException đi ra C1; C1 catch lỗi cache riêng, không nuốt BusinessException/lỗi DB. WARN chữ mặc định Boot, không JSON formatter/stack/value/secret |
| Env runtime/test | Nạp file env bằng cơ chế env-file/IDE/runtime hiện có; không gán process env, truyền CLI/JVM property để bù thiếu. Thiếu giá trị phải bổ sung đúng env hoặc hỏi, không dùng hướng dẫn override cũ trong TESTING.md |
| Ngoài mẫu Shop App cần duyệt | StringRedisTemplate, generation UUID, ApplicationEventPublisher, @TransactionalEventListener, Mockito/MVC/JPA integration. Local Shop App không có implementation Redis; source public và tài liệu chính thức đã dẫn ở spec §10–12 |

Ngoài delta wiring, không thay signature/guard của helper cũ, UUID/Instant/counter chuỗi, schema API, auth hoặc workflow thanh toán. Prefix cache v2 tách key cũ; không clear toàn DB.

## 3. Bảng class/file — WRITING-PLANS APPROVED

Đây là danh sách triển khai đầy đủ của plan; giữ ID từ spec. Các class EXISTING/REUSE phải có trên baseline mục 1.

| ID | Class/file | NEW hoặc EXISTING | Vai trò, field/annotation/API sinh | Mẫu Shop App hoặc ngoại lệ |
| --- | --- | --- | --- | --- |
| C1 | `availability/service/AvailabilityCacheService.java` | NEW | `public class`; @Service/@RequiredArgsConstructor/@Slf4j; final AvailabilityService availabilityService, RedisService redisService, JsonMapper jsonMapper; @Value Duration cacheTtl, String keyPrefix. Lombok sinh constructor ba dependency và static final logger; listener M22; không getter/setter/builder/equals/hashCode/toString | ProductService + JwtTokenUtil; cache/generation/listener là ngoại lệ §6 |
| C2 | `availability/controller/AvailabilityController.java` | EXISTING, sửa | Thay final `AvailabilityService` bằng `AvailabilityCacheService availabilityCacheService`; giữ annotation hiện có; constructor Lombok đổi kiểu tham số | ProductController; controller vẫn chỉ gọi service |
| C3 | `availability/service/AvailabilityService.java`, `availability/service/impl/AvailabilityServiceImpl.java` | EXISTING, sửa impl | Giữ interface và đường đọc M5; impl thêm final ApplicationEventPublisher applicationEventPublisher vào constructor Lombok, publish C7 ở hai writer M27/M28; giữ annotations cũ | ProductService; event Spring là ngoại lệ |
| C4 | `dto/response/Availability.java` cùng `TicketTypeAvailability`, `SectorAvailability`, `SeatAvailability` | EXISTING, REUSE | Giữ field final, `@Getter`, `@AllArgsConstructor`, annotation JSON/validation hiện có; không sinh thêm API | DTO contract dự án |
| C5 | Test `availability/service/AvailabilityCacheServiceTests.java` | NEW | Package-private; @ExtendWith(MockitoExtension.class); @Mock AvailabilityService, @Mock StringRedisTemplate, @Mock ValueOperations<String,String>; field JsonMapper thật từ fixture, RedisService thật bọc template mock, cacheService; không Lombok. Khởi tạo hai service trong setup M7, ReflectionTestUtils gán TTL/prefix test | Ngoài Shop App; giữ test cache-aside/generation và lỗi qua RedisService thật, không thêm unit test chỉ lặp operation wrapper |
| C6 | Test `availability/controller/AvailabilityControllerTests.java` | EXISTING, sửa | Đổi `@MockitoBean AvailabilityService` thành `@MockitoBean AvailabilityCacheService`; giữ MockMvc/security/handler và năm test hiện có | MVC tests dự án; không thêm test method |
| C7 | `availability/event/AvailabilityChangedEvent.java` | NEW | `public final class`, @Getter/@AllArgsConstructor; một field `private final UUID eventId`; Lombok sinh public constructor và getEventId(); không setter/builder/equals/hashCode/toString | Hình dạng class Lombok theo Shop App; application event là ngoại lệ Spring |
| C8 | `order/service/impl/OrderServiceImpl.java` | EXISTING, sửa | Giữ @Service/@RequiredArgsConstructor và dependency cũ; thêm final ApplicationEventPublisher applicationEventPublisher, constructor Lombok đổi tương ứng. Chỉ chèn publish event trong 9 helper ghi hiện có | Reuse helper đúng trách nhiệm; không tách logic ghi/validation mới |
| C9 | Test `persistence/BookingPersistenceIT.java` | EXISTING, sửa | Giữ DataJpaTest/PostgreSQL/transaction test hiện có; @Import thêm AvailabilityCacheService và RedisService; @Autowired ApplicationEventPublisher và AvailabilityCacheService; @MockitoBean(answers=Answers.RETURNS_DEEP_STUBS) StringRedisTemplate. Không thêm test configuration/helper/constructor | RedisService thật, template mock; kiểm callback commit/rollback và writer, không thay runtime Redis thật |
| C10 | `redis/service/RedisService.java` | NEW | public class; @Service/@RequiredArgsConstructor; một field private final StringRedisTemplate stringRedisTemplate; Lombok sinh public constructor M44. Không getter/setter/builder/logger, không property riêng | ProductService cho injection; thu gọn operation từ RedisServiceImpl của mall §10, bỏ interface và API hash/list chưa cần |

C1 có @Value("${booking.availability-cache.ttl:PT15M}") trên Duration cacheTtl và @Value("${booking.availability-cache.key-prefix:flash-ticket:dev:booking:availability:v2:}") trên String keyPrefix. C10 không cấu hình TTL/key/JSON. Ngoài constructor/logger/getter được nêu, không sinh thêm API Lombok. Các nested DTO C4 giữ nguyên API cũ.

## 4. Bảng method/constructor — WRITING-PLANS APPROVED

Chữ ký, annotation/cách viết và lý do cần giữ nguyên spec. Method không static và không thêm throws trừ nơi ghi rõ. NEW là chưa có trên baseline; REUSE sửa thân không phát sinh một khai báo mới. C5 constructor mặc định package-private được ghi rõ để không bỏ sót gate.

| ID | Class | Constructor/method đầy đủ | NEW hoặc REUSE | Cách viết và lý do cần | Mẫu Shop App hoặc ngoại lệ |
| --- | --- | --- | --- | --- | --- |
| M1 | C1 | `public AvailabilityCacheService(AvailabilityService availabilityService, RedisService redisService, JsonMapper jsonMapper)` | NEW, đổi so với v0.3; Lombok sinh | Constructor injection; thay template trực tiếp bằng C10, giữ JSON/đường đọc DB | Shop App ProductService/ProductController: constructor final dependency; kiểu dependency theo Booking |
| M2 | C1 | `public Availability getAvailability(UUID eventId)` | NEW | Cache-aside §3–4; không transaction/override/helper; được controller gọi, chỉ gọi DB service khi cần | NGOÀI MẪU: cache-aside/generation/AFTER_COMMIT; injection theo ProductService |
| M3 | C2 | `public AvailabilityController(AvailabilityCacheService availabilityCacheService)` | EXISTING, đổi chữ ký Lombok | Constructor cũ nhận AvailabilityService; đổi dependency để GET đi qua cache | Shop App ProductService/ProductController: constructor final dependency; kiểu dependency theo Booking |
| M4 | C2 | `public ResponseEntity<Availability> getAvailability(@PathVariable UUID eventId)` | REUSE, sửa thân | Giữ `@GetMapping`, gọi C1 thay service cũ; giữ no-store và schema | Shop App ProductController#getProducts; giữ response/no-store của Booking |
| M5 | C3 implementation/interface | `public Availability getAvailability(UUID eventId)` | REUSE nguyên trạng | Impl giữ `@Override`, transaction readOnly/REPEATABLE_READ; interface không thêm method | REUSE Booking baseline; NGOÀI MẪU: transaction/inventory/event; không đổi guard |
| M6 | C5 | `AvailabilityCacheServiceTests()` | NEW, constructor mặc định package-private | Không khai báo constructor riêng hoặc API sinh | NGOÀI MẪU Shop App: JUnit/Mockito/MVC/PostgreSQL; dùng BookingFixtures/BookingPersistenceIT baseline |
| M7 | C5 | `void setUp()` | NEW | `@BeforeEach`; tạo C1, mapper fixture, gán TTL/prefix bằng ReflectionTestUtils; stub trong từng test để tránh stub thừa | NGOÀI MẪU Shop App: JUnit/Mockito/MVC/PostgreSQL; dùng BookingFixtures/BookingPersistenceIT baseline |
| M8 | C5 | `void returnsCachedAvailabilityWithoutLoadingDatabase()` | NEW | `@Test`; JSON thật round-trip đủ nested DTO/UUID/Instant/enum/counter, giữ asOf; verify không gọi DB hoặc SET | NGOÀI MẪU Shop App: JUnit/Mockito/MVC/PostgreSQL; dùng BookingFixtures/BookingPersistenceIT baseline |
| M9 | C5 | `void loadsAndCachesAvailabilityOnMiss()` | NEW | `@Test`; đọc generation trước DB, ghi key event/generation đúng TTL PT15M; hai event không dùng chung key | NGOÀI MẪU Shop App: JUnit/Mockito/MVC/PostgreSQL; dùng BookingFixtures/BookingPersistenceIT baseline |
| M10 | C5 | `void usesConfiguredTtlWithoutRefreshingOnHit()` | NEW, đổi so với v0.1 | `@Test`; SET truyền đúng Duration; hit chỉ GET, không SET/EXPIRE/GETEX. Hết hạn thật kiểm bằng Redis ở §9 | NGOÀI MẪU Shop App: JUnit/Mockito/MVC/PostgreSQL; dùng BookingFixtures/BookingPersistenceIT baseline |
| M11 | C5 | `void fallsBackToDatabaseWhenRedisReadFails()` | NEW | `@Test`; DataAccessException khi GET → DB, không thử SET; không thay response | NGOÀI MẪU Shop App: JUnit/Mockito/MVC/PostgreSQL; dùng BookingFixtures/BookingPersistenceIT baseline |
| M12 | C5 | `void returnsDatabaseResponseWhenCacheWriteFails()` | NEW | `@Test`; SET lỗi → vẫn trả DTO DB. Lỗi serialize cũng giữ response; dùng spy mapper trong chính test nếu cần, không helper mới | NGOÀI MẪU Shop App: JUnit/Mockito/MVC/PostgreSQL; dùng BookingFixtures/BookingPersistenceIT baseline |
| M13 | C5 | `void reloadsAvailabilityWhenCachedJsonIsInvalid()` | NEW | @Test; JSON lỗi/null → clear đúng dataKey lỗi qua C10, rồi DB và ghi DTO hợp lệ kèm TTL. Clear lỗi Redis thì vẫn fallback DB và bỏ ghi Redis trong request; verify không xóa generation/key event khác | NGOÀI MẪU Shop App: JUnit/Mockito/MVC/PostgreSQL; dùng BookingFixtures/BookingPersistenceIT baseline |
| M14 | C5 | `void propagatesSourceErrorsWithoutCachingThem()` | NEW | `@Test`; service ném BusinessException 503/409 hoặc lỗi DB → giữ nguyên exception, không SET | NGOÀI MẪU Shop App: JUnit/Mockito/MVC/PostgreSQL; dùng BookingFixtures/BookingPersistenceIT baseline |
| M15 | C5 | `void bypassesRedisWhenCacheTtlIsNotPositive()` | NEW | `@Test`; TTL 0/âm → DB, không thao tác Redis | NGOÀI MẪU Shop App: JUnit/Mockito/MVC/PostgreSQL; dùng BookingFixtures/BookingPersistenceIT baseline |
| M16 | C6 | `void returnsPublicAvailabilityWithoutJwt() throws Exception` | REUSE, sửa mock | Giữ payload B13/public/no-store; mock C1 | NGOÀI MẪU Shop App: JUnit/Mockito/MVC/PostgreSQL; dùng BookingFixtures/BookingPersistenceIT baseline |
| M17 | C6 | `void rejectsMalformedEventId() throws Exception` | REUSE, sửa mock | 400 trước cache service | NGOÀI MẪU Shop App: JUnit/Mockito/MVC/PostgreSQL; dùng BookingFixtures/BookingPersistenceIT baseline |
| M18 | C6 | `void returnsConfigNotReadyProblem() throws Exception` | REUSE, sửa mock | Giữ 503 CONFIG_NOT_READY | NGOÀI MẪU Shop App: JUnit/Mockito/MVC/PostgreSQL; dùng BookingFixtures/BookingPersistenceIT baseline |
| M19 | C6 | `void returnsEventNotOnSaleProblem() throws Exception` | REUSE, sửa mock | Giữ 409 EVENT_NOT_ON_SALE | NGOÀI MẪU Shop App: JUnit/Mockito/MVC/PostgreSQL; dùng BookingFixtures/BookingPersistenceIT baseline |
| M20 | C6 | `void rejectsInvalidBearerToken() throws Exception` | REUSE, sửa mock | Giữ 401 và không gọi cache service | NGOÀI MẪU Shop App: JUnit/Mockito/MVC/PostgreSQL; dùng BookingFixtures/BookingPersistenceIT baseline |
| M21 | BookingFixtures | `public static JsonMapper jsonMapper()` | REUSE nguyên trạng | Tạo mapper test theo JacksonAutoConfiguration + BookingJsonConfig hiện có | NGOÀI MẪU Shop App: JUnit/Mockito/MVC/PostgreSQL; dùng BookingFixtures/BookingPersistenceIT baseline |
| M22 | C1 | `public void invalidateAvailabilityCache(AvailabilityChangedEvent event)` | NEW | @TransactionalEventListener(phase=TransactionPhase.AFTER_COMMIT, fallbackExecution=false); SET generation UUID mới; catch DataAccessException/log WARN. Không gọi DB, không rebuild availability | NGOÀI MẪU: cache-aside/generation/AFTER_COMMIT; injection theo ProductService |
| M23 | C7 | `public AvailabilityChangedEvent(UUID eventId)` | NEW, Lombok sinh | Mang ID event bị tác động, không serialize thành message RabbitMQ | Shop App CategoryDTO: class Lombok, chỉ @Getter/@AllArgsConstructor; NGOÀI MẪU: Spring event |
| M24 | C7 | `public UUID getEventId()` | NEW, Lombok sinh | Listener lấy eventId để đổi generation đúng event | Shop App CategoryDTO: class Lombok, chỉ @Getter/@AllArgsConstructor; NGOÀI MẪU: Spring event |
| M25 | C8 | `public OrderServiceImpl(OrderRepository orderRepository, IdempotencyRequestRepository idempotencyRequestRepository, OrderMapper orderMapper, EventSalesSnapshotRepository eventSalesSnapshotRepository, PurchaseLimitRepository purchaseLimitRepository, TicketTypeInventoryRepository ticketTypeInventoryRepository, SectorInventoryRepository sectorInventoryRepository, SeatInventoryRepository seatInventoryRepository, ReservationRepository reservationRepository, JsonMapper objectMapper, PromotionUsageRepository promotionUsageRepository, Validator validator, InboxMessageRepository inboxMessageRepository, OutboxMessageRepository outboxMessageRepository, OutboxMessageMapper outboxMessageMapper, PromotionInventoryRepository promotionInventoryRepository, ApplicationEventPublisher applicationEventPublisher)` | EXISTING, đổi constructor Lombok | Dependency cũ giữ thứ tự; thêm publisher ở cuối, không thêm cache-service dependency | Shop App ProductService/ProductController: constructor final dependency; kiểu dependency theo Booking |
| M26 | C3 impl | `public AvailabilityServiceImpl(EventSalesSnapshotRepository eventSalesSnapshotRepository, TicketTypeInventoryRepository ticketTypeInventoryRepository, SectorInventoryRepository sectorInventoryRepository, SeatInventoryRepository seatInventoryRepository, PurchaseLimitRepository purchaseLimitRepository, PromotionInventoryRepository promotionInventoryRepository, InboxMessageRepository inboxMessageRepository, JsonMapper jsonMapper, Validator validator, ApplicationEventPublisher applicationEventPublisher)` | EXISTING, đổi constructor Lombok | Thêm publisher để báo config/gate thay đổi; interface không đổi | Shop App ProductService/ProductController: constructor final dependency; kiểu dependency theo Booking |
| M27 | C3 impl | `public void synchronizeSalesConfiguration(Envelope<SalesConfiguration> message)` | REUSE, sửa thân | Giữ @Override/@Transactional; phát event ở nhánh áp dụng cấu hình mới | REUSE Booking baseline; NGOÀI MẪU: transaction/inventory/event; không đổi guard |
| M28 | C3 impl | `public void processEventCancellation(Envelope<EventCancellation> message)` | REUSE, sửa thân | Giữ @Override/@Transactional; phát event khi gate mới/đổi CANCELLED | REUSE Booking baseline; NGOÀI MẪU: transaction/inventory/event; không đổi guard |
| M29 | C8 | `private void reserveQuantityTickets(Order order, Reservation reservation, OrderSelection.QuantitySelection selection, List<TicketTypeInventory> lockedInventories)` | REUSE, sửa thân | Publish cuối phần cập nhật pool QUANTITY; giao dịch createOrder sở hữu commit | REUSE Booking baseline; NGOÀI MẪU: transaction/inventory/event; không đổi guard |
| M30 | C8 | `private void reserveStandingTickets(Order order, Reservation reservation, OrderSelection.StandingSelection selection, SectorInventory lockedSector)` | REUSE, sửa thân | Publish sau giữ pool sector STANDING | REUSE Booking baseline; NGOÀI MẪU: transaction/inventory/event; không đổi guard |
| M31 | C8 | `private void reserveSeatedTickets(Order order, Reservation reservation, OrderSelection.SeatedSelection selection, List<SeatInventory> lockedSeats)` | REUSE, sửa thân | Publish sau cập nhật ghế HELD | REUSE Booking baseline; NGOÀI MẪU: transaction/inventory/event; không đổi guard |
| M32 | C8 | `private void updateQuantityInventoryForPaymentDecision(Order lockedOrder, Reservation storedReservation, List<TicketTypeInventory> lockedInventories, ReservationState targetState)` | REUSE, sửa thân | Các caller chốt/nhả HELD dùng chung; không thêm event ở caller | REUSE Booking baseline; NGOÀI MẪU: transaction/inventory/event; không đổi guard |
| M33 | C8 | `private void updateStandingInventoryForPaymentDecision(Order lockedOrder, Reservation storedReservation, SectorInventory lockedSector, ReservationState targetState)` | REUSE, sửa thân | Như M32 cho STANDING | REUSE Booking baseline; NGOÀI MẪU: transaction/inventory/event; không đổi guard |
| M34 | C8 | `private void updateSeatedInventoryForPaymentDecision(Order lockedOrder, Reservation storedReservation, List<SeatInventory> lockedSeats, ReservationState targetState)` | REUSE, sửa thân | Như M32 cho SEATED; PURCHASED/AVAILABLE/UNAVAILABLE được phản ánh | REUSE Booking baseline; NGOÀI MẪU: transaction/inventory/event; không đổi guard |
| M35 | C8 | `private void releasePurchasedQuantityInventory(Order lockedOrder, Reservation storedReservation, List<TicketTypeInventory> lockedInventories)` | REUSE, sửa thân | Phát event sau trả counter purchased | REUSE Booking baseline; NGOÀI MẪU: transaction/inventory/event; không đổi guard |
| M36 | C8 | `private void releasePurchasedStandingInventory(Order lockedOrder, Reservation storedReservation, SectorInventory lockedSector)` | REUSE, sửa thân | Phát event sau trả pool STANDING | REUSE Booking baseline; NGOÀI MẪU: transaction/inventory/event; không đổi guard |
| M37 | C8 | `private void releasePurchasedSeatInventory(Order lockedOrder, Reservation storedReservation, List<SeatInventory> lockedSeats)` | REUSE, sửa thân | Phát event sau xử lý trả ghế COMMITTED theo guard hiện có | REUSE Booking baseline; NGOÀI MẪU: transaction/inventory/event; không đổi guard |
| M38 | C5 | `void initializesGenerationWithoutReusingOldDataKeys()` | NEW | @Test; SET NX + đọc generation thực tế khi có race khởi tạo; metadata bị evict dùng UUID mới; vẫn thiếu generation thì không cache request | NGOÀI MẪU Shop App: JUnit/Mockito/MVC/PostgreSQL; dùng BookingFixtures/BookingPersistenceIT baseline |
| M39 | C5 | `void staleReaderCannotPopulateTheNewGeneration()` | NEW | @Test; mock điều phối reader G1 bị giữ, invalidate G2 rồi tiếp tục: reader chỉ SET data:G1, GET tiếp theo không dùng nó | NGOÀI MẪU Shop App: JUnit/Mockito/MVC/PostgreSQL; dùng BookingFixtures/BookingPersistenceIT baseline |
| M40 | C5 | `void invalidatesOnlyTheAffectedEventAndHandlesRedisFailure()` | NEW | @Test; listener SET đúng generationKey với UUID mới mỗi lần, không flush/KEYS/rebuild, Redis lỗi được log/catch | NGOÀI MẪU Shop App: JUnit/Mockito/MVC/PostgreSQL; dùng BookingFixtures/BookingPersistenceIT baseline |
| M41 | C9 | `void availabilityCacheInvalidationRunsAfterCommitAndNotAfterRollback()` | NEW | @Test, @Transactional(propagation=Propagation.NOT_SUPPORTED); dùng TransactionTemplate/transaction manager hiện có, publish trong transaction thật; verify chưa SET trước commit, có SET sau commit, không có SET ở rollback | NGOÀI MẪU Shop App: JUnit/Mockito/MVC/PostgreSQL; dùng BookingFixtures/BookingPersistenceIT baseline |
| M42 | C9 | `void inventoryWritersInvalidateAvailabilityForAllSalesModes()` | NEW | @Test, @Transactional(propagation=Propagation.NOT_SUPPORTED); dùng fixture/transaction hiện có qua create/cancel/expiry/payment/FAILED/event cancellation cho QUANTITY/STANDING/SEATED; không chỉ tự publish giả thay writer | NGOÀI MẪU Shop App: JUnit/Mockito/MVC/PostgreSQL; dùng BookingFixtures/BookingPersistenceIT baseline |
| M43 | C9 | `void salesConfigurationAndCancellationInvalidateAvailabilityCache()` | NEW | @Test, @Transactional(propagation=Propagation.NOT_SUPPORTED); config mới/gate CANCELLED đổi generation; old version/replay không phát lại | NGOÀI MẪU Shop App: JUnit/Mockito/MVC/PostgreSQL; dùng BookingFixtures/BookingPersistenceIT baseline |
| M44 | C10 | `public RedisService(StringRedisTemplate stringRedisTemplate)` | NEW, Lombok sinh | Một dependency template Spring hiện có | Shop App ProductService/ProductController: constructor final dependency; kiểu dependency theo Booking |
| M45 | C10 | `public String get(String key)` | NEW | Gọi opsForValue().get(key); dùng đọc generation và JSON; trả null chỉ khi key không có, Redis lỗi đi ra caller | NGOÀI MẪU: Spring Data Redis; operation thu gọn từ mall RedisServiceImpl; bố cục ProductService |
| M46 | C10 | `public void set(String key, String value, Duration ttl)` | NEW | Gọi opsForValue().set(key,value,ttl); JSON dataKey cần SET cùng TTL, không tách expire | NGOÀI MẪU: Spring Data Redis; operation thu gọn từ mall RedisServiceImpl; bố cục ProductService |
| M47 | C10 | `public void set(String key, String value)` | NEW | Gọi opsForValue().set(key,value); generation metadata không TTL, listener dùng để đổi namespace | NGOÀI MẪU: Spring Data Redis; operation thu gọn từ mall RedisServiceImpl; bố cục ProductService |
| M48 | C10 | `public boolean setIfAbsent(String key, String value)` | NEW | Gọi SET NX thông qua template, Boolean.TRUE.equals kết quả; generation được khởi tạo nguyên tử; caller vẫn get lại generation thực tế. Task không dùng pipeline/Redis transaction | NGOÀI MẪU: Spring Data Redis; operation thu gọn từ mall RedisServiceImpl; bố cục ProductService |
| M49 | C10 | `public void clear(String key)` | NEW | Gọi stringRedisTemplate.delete(key); xóa đúng một key, không scan/flushDb. C1 dùng khi JSON cache lỗi/null để bỏ bản lỗi; xóa key đã mất vẫn hợp lệ | NGOÀI MẪU: Spring Data Redis; operation thu gọn từ mall RedisServiceImpl; bố cục ProductService |

C3/M26 và C8/M25 dùng @RequiredArgsConstructor, giữ thứ tự final field cũ rồi thêm ApplicationEventPublisher. C2/M3 đổi một dependency sang AvailabilityCacheService. Không tạo helper, callback class ẩn, @Bean, overload ngoài bảng hoặc test config mới. Nếu cần thêm khi execute, trình delta trước khi viết.

## 5. Các bước triển khai và kết quả kiểm được

Các bước P0–P6 đã được duyệt ở pha WRITING-PLANS theo nguồn xác nhận đầu tài liệu. Pha EXECUTING-PLANS vẫn có gate riêng khi user yêu cầu triển khai. Các bước tham chiếu đúng danh sách C/M, không cấp phê duyệt mở.

### P0 — Chọn đúng baseline và kiểm điều kiện chạy

- Phụ thuộc: gate hiện tại và gate execute; không phát sinh Java.
- File đối chiếu: C2/C3/C8/C9; services/booking-service/pom.xml; infrastructure/config-server/src/main/resources/config/booking-service.yaml; services/booking-service/TESTING.md.
- Kiểm Git common directory/branch/diff; baseline phải có M4/M5/M27–M37. Giữ file user đang sửa và tài liệu spec/plan. Xác minh Java 23 bằng môi trường chuẩn, giữ Maven Enforcer.
- Xác minh cơ chế nạp root env hoặc env-file test hiện có. Chỉ kiểm tên biến, không in giá trị secrets. Xác nhận Docker/dependency readiness khi chạy testcontainers hoặc runtime, không coi Docker là yêu cầu để viết code.
- Kết quả: xác định đúng checkout, GET/writer tái sử dụng có thật, env và JDK đáp ứng; không tạo khai báo hoặc thay baseline ngoài scope.

### P1 — Gom operation Redis và tạo event nội bộ

- Item: C10/M44–M49, C7/M23–M24.
- File tạo: main redis/service/RedisService.java và availability/event/AvailabilityChangedEvent.java.
- RedisService 5 operation trực tiếp trên StringRedisTemplate: get, hai set overload, SET NX, clear một key. Giữ atomic SET kèm Duration; metadata dùng set không TTL. Không catch để biến lỗi thành miss; setIfAbsent dùng Boolean.TRUE.equals, caller sẽ get lại giá trị thật.
- Event final class một UUID eventId; annotation/API Lombok đúng bảng. Publisher được gọi từ writer đang có eventId hợp lệ; không thêm validate method.
- Kiểm: compile/test cùng P2 sau khi có consumer; không tạo suite wrapper chỉ verify từng lời gọi template.
- Kết quả: common operation phục vụ reader/listener, event có constructor/getter đã duyệt; không cấu hình connectionFactory hoặc mapper mới.

### P2 — Đọc cache, fallback và listener sau commit

- Phụ thuộc: P1. Item: C1/M1–M2/M22; C5/M6–M15/M38–M40; REUSE C4/M21.
- File tạo: main availability/service/AvailabilityCacheService.java; test availability/service/AvailabilityCacheServiceTests.java.
- getAvailability: TTL không dương → DB ngay. get generation; thiếu thì setIfAbsent(UUID mới) rồi get lại. Thiếu generation sau đó → DB và không ghi cache request đó. Key JSON cố định từ generation lấy trước DB.
- Hit: JsonMapper thật đọc Availability; giữ asOf/contract. Miss: M5 qua proxy, sau khi service trả thành công mới serialize/set JSON PT15M vào đúng dataKey cũ. Không đọc generation mới để gắn vào response DB đã đọc.
- JSON lỗi/null: clear đúng dataKey trong catch DataAccessException riêng; clear lỗi thì bỏ ghi cache nhưng vẫn gọi DB. Redis GET lỗi cũng bỏ SET. Serialize/SET lỗi vẫn trả DTO DB. Gọi M5 ngoài toàn bộ catch cache, lỗi nghiệp vụ/commit giữ nguyên.
- Listener M22 @TransactionalEventListener(AFTER_COMMIT, fallbackExecution=false), không @Async/@Transactional mới: set generation UUID mới, catch Redis DataAccessException/WARN. Không gọi ngược repository hoặc nạp lại full availability.
- C5 dùng RedisService thật + template/valueOperations mock, mapper thật từ M21. Không stub opsForValue chung ở setup nếu case bypass không sử dụng; fixtures/time dữ liệu nằm trong test method, không helper mới.
- Test M39 điều phối thứ tự bằng Mockito Answer/latch ngay trong method: reader giữ G1 → invalidation thành G2 → reader tiếp tục SET data:G1 → request sau GET data:G2. Không chỉ assert UUID khác nhau; không sleep để đoán thứ tự, giải phóng latch/executor trong finally.
- Kết quả: 11 test cache M8–M15/M38–M40 xanh; JSON đủ nested DTO thật round-trip, key/TTL không lẫn event, lỗi read/clear/write có fallback đúng và request cũ không nạp vào generation mới.

### P3 — Nối controller và cấu hình TTL/prefix

- Phụ thuộc: P2. Item: C2/M3–M4, C6/M16–M20; C3/M5 REUSE nguyên trạng.
- File sửa: main availability/controller/AvailabilityController.java; test availability/controller/AvailabilityControllerTests.java; infrastructure/config-server/src/main/resources/config/booking-service.yaml; workspace .env.example.
- Controller giữ route/public GET, ResponseEntity<Availability>, no-store; chỉ chuyển dependency/call sang C1. Không chuyển transaction đọc về controller.
- YAML thêm booking.availability-cache.ttl và key-prefix, default PT15M và flash-ticket:dev:booking:availability:v2:. Dùng BOOKING_AVAILABILITY_CACHE_TTL, BOOKING_AVAILABILITY_CACHE_KEY_PREFIX; đồng bộ template, không ghi secret. Runtime dùng prefix riêng cho môi trường dùng chung Redis.
- Không đổi host/port/password/timeout Redis hay bộ config/test khác. Không cần POM dependency mới.
- C6 đổi @MockitoBean sang C1; giữ năm case contract/public/UUID/503/409/401. Không thêm test method hoặc probe controller.
- Kết quả: GET bên ngoài giữ schema/headers/auth/error cũ, config binding dùng @Value field, service DB được gọi qua proxy khi miss.

### P4 — Publish ở đúng đường ghi inventory/config/gate

- Phụ thuộc: P1–P3. Item: C3/M26–M28; C8/M25/M29–M37.
- File sửa: main availability/service/impl/AvailabilityServiceImpl.java và order/service/impl/OrderServiceImpl.java.
- Thêm final ApplicationEventPublisher ở cuối field dependency của hai implementation, để Lombok sinh constructor đúng M25/M26; không inject cache service vào writer.
- Hai writer config: publish M27 chỉ khi apply version mới, sau nhóm synchronize inventory/promotion; version cũ/replay không phát. M28 publish trong hai nhánh tạo gate CANCELLED/đổi cột eventStatus, không dựa trên JSON thương mại và không phát lại từ receipt replay.
- Ba reserve helper M29–M31: publish eventId từ order sau save/saveAll cập nhật inventory. Sáu update/release helper M32–M37: publish eventId từ lockedOrder sau phần ghi của helper. Nếu phần còn lại của transaction thất bại, callback không chạy.
- Không nhân đôi publish ở public create/cancel/expiry/payment/FAILED/worker đã dùng helper chung; không tách validator hoặc thêm query/lock để chỉ phục vụ cache. Đặt comment tiếng Việt phân biệt publish lúc trong transaction với invalidation sau commit.
- Kết quả: QUANTITY/STANDING/SEATED, buyer cancel/expiry/acceptPayment/issuance failure/event cancellation đi qua điểm phát event chung, guard/khóa/counter/idempotency/ACK cũ giữ đúng.

### P5 — Kiểm chứng transaction thật và hồi quy

- Phụ thuộc: P4. Item: C9/M41–M43, wiring C1/C10, các method/constructor C3/C8 đã duyệt.
- File sửa: test persistence/BookingPersistenceIT.java; giữ configuration/helper/nested class sẵn có. @Import thêm C1/C10, @Autowired publisher/cacheService, @MockitoBean StringRedisTemplate với Answers.RETURNS_DEEP_STUBS như bảng.
- M41: @Test và @Transactional(propagation=NOT_SUPPORTED), dùng transaction manager/TransactionTemplate sẵn có. Trong transaction thật publish event, verify Redis chưa SET trước commit; commit xong có đổi generation, rollback không đổi. Không dùng mặc định rollback của @DataJpaTest để kết luận AFTER_COMMIT hoạt động.
- M42: dùng API service public và fixture hiện có để đi qua reserve/update/release của cả ba kiểu bán; cache listener thực gọi RedisService thực/template mock. Kiểm inventory DB và invalidation; không thay writer bằng publishEvent giả. Bao phủ buyer cancel, expiry, acceptPayment, FAILED và event-cancellation release trong cùng method qua dữ liệu ID riêng.
- M43: config mới và gate cancellation đổi generation; old version/replay không phát invalidation mới. Clear invocation history sau phần seed bằng Mockito hiện có để không nhầm seed commit với thao tác đang kiểm.
- Fixtures/timestamps của transaction dùng giờ phù hợp runtime và deadline DB; tạo dữ liệu test riêng, dọn/rollback theo hạ tầng sẵn có, không sửa đồng hồ hệ thống/DDL production. Không thêm test helper mới nếu chưa qua delta.
- Rerun suite liên quan và verify toàn module theo §6. Thực thi test đủ để phát hiện lỗi wiring/constructor ở context cũ; không mặc định các test cũ sẽ tự đúng vì class vẫn compile.
- Kết quả: commit/rollback và writer thật được chứng minh, không đổi AMQP disposition/idempotency/lock behavior; không có failure/error/skip không được giải thích.

### P6 — Nghiệm thu Redis thật và ghi bằng chứng

- Phụ thuộc: P5. Item: các C/M ở P1–P5; không thêm Java/helper/suite mới.
- File nguồn hướng dẫn: services/booking-service/TESTING.md; scripts/.env.example và scripts/test-event-availability-curl.py trên baseline là nguồn tham chiếu cách tạo môi trường cô lập/fixture. Không coi harness cũ đã chứng minh cache mới hoặc tự mở rộng harness thành chương trình mới.
- Chạy JAR với env-file chuẩn trong môi trường test riêng có PostgreSQL/Redis và các dependency thật cần thiết. Tránh sửa inventory DB bằng SQL để giả luồng: dùng API/message đầu vào đúng contract của baseline; eventId test riêng.
- Redis auth/PING; chỉ kiểm đúng key do task tạo. Hai GET cùng generation/asOf và data JSON round-trip; PTTL JSON trong khoảng (0,900000] ms với PT15M; metadata PTTL=-1; GET không tăng thời hạn. Không chờ 15 phút chỉ để đo TTL.
- Sau mỗi nhóm giữ/nhả/chốt mua/trả purchased/config/cancellation: transaction hoàn tất, generation đổi; GET mới tải DB mới dù JSON G1 chưa hết hạn. Kiểm event khác vẫn dùng generation trước. Với event CANCELLED, GET mới trả 409, không cache response lỗi.
- Kiểm hết hạn key bằng một key test riêng có TTL ngắn qua RedisService.set đã duyệt hoặc Redis CLI trong môi trường test; không override property TTL bằng JVM/process env tạm. Không đổi TTL metadata.
- Lỗi Redis có thể kiểm bằng fixture/mock đã có; nếu cần fault test runtime, chỉ dừng Redis của môi trường test do task sở hữu, để DB/transaction vẫn hoạt động; không dừng Redis119 dùng chung. Mất invalidation do crash không tự phục hồi: báo đúng giới hạn thiết kế.
- Ghi kết quả vào TESTING.md và log/report dưới target: tên case, baseline/commit, thời gian, HTTP status, generation/TTL, số query hoặc không gọi DB ở hit; không credentials/token/value chứa thông tin buyer. Không tạo bảng đo hiệu năng/chịu tải từ vài request.
- Kết quả: bằng chứng cache có hoạt động thật, after-commit wiring và consistency ở điều kiện bình thường; chưa claim deploy119 hoặc retry bền.

## 6. Lệnh kiểm chứng dự kiến

Chạy từ service directory của checkout đúng baseline, JDK 23 và env đã được nạp đúng cơ chế dự án. Đây là lệnh cho pha execute; lượt lập plan chưa chạy test/build.

```powershell
rtk proxy .\mvnw.cmd -B -ntp "-Dtest=AvailabilityCacheServiceTests,AvailabilityControllerTests" test
rtk proxy .\mvnw.cmd -B -ntp -Pintegration "-Dit.test=BookingPersistenceIT" verify
```

Lệnh integration verify đã chạy Surefire toàn bộ, Failsafe BookingPersistenceIT cùng Spotless/SpotBugs/JaCoCo theo POM hiện có; không lặp lại full test chỉ để có thêm một lần xanh. Nếu Maven dừng trước Failsafe do failure mới, sửa trong scope và chạy lại phần cần chứng minh. Không dùng enforcer.skip hoặc skipTests để báo pass. Kiểm Surefire/Failsafe XML để xác nhận số case thực chạy và zero unexplained failures/errors/skips, không chỉ dựa BUILD SUCCESS.

Chỉ chạy spotless:apply trên các Java file task thay đổi nếu formatter cần; kiểm lại diff để không định dạng file ngoài scope. P6 có runtime test riêng sau build. Không dùng hướng dẫn process env override/Enforcer skip lịch sử trong TESTING.md để vượt convention hiện hành.

## 7. Giới hạn, nguồn và gate cuối

- Source đã đọc: CodeGraph trên workspace; git show baseline dev/son cho C2/C3/C4/C8/C9, POM, config, TESTING.md; source Shop App ProductService/ProductController/CategoryDTO/JwtTokenUtil.
- Nguồn chính thức và repo public: spec §10–12 đã có đường dẫn RedisTemplate/ValueOperations, Spring Transaction-bound Events, ShopLink event/listener và mall RedisServiceImpl. Plan dùng lại kết luận đã kiểm; không thêm cơ chế framework ngoài phạm vi đã duyệt.
- Không có outbox/retry bền cho invalidation. Redis lỗi hoặc process crash giữa commit và callback có thể để cache cũ tới TTL; invalidation không đồng nghĩa strong consistency hay exactly-once. Cache không quyết định đặt vé.
- C1–C10/M1–M49 đầy đủ; không phát sinh C/M mới hoặc signature delta so với spec v0.4. Fixture/config/test annotation làm theo bảng; phần thân test cụ thể không cấp quyền thêm helper/type mới.
- Xác nhận làm rõ cơ chế ngày 04/11: sau phần giải thích generation chính là cách listener thực hiện invalidation sau commit, user trả lời **“ok đồng ý”**. Giữ nguyên luồng inventory commit → listener đổi generation → GET sau nạp DB/cache mới; không đổi thành DEL key trực tiếp, không phát sinh delta class/method. Phản hồi này xác nhận lựa chọn kỹ thuật đang được thảo luận, không tự coi là phê duyệt toàn bộ bảng khai báo và P0–P6 ở pha WRITING-PLANS.
- **Gate WRITING-PLANS: APPROVED.** Nguồn: user **“đồng ý plan”**, ngày 04/11/2026. Chốt C1–C10/M1–M49, annotation/API sinh/ngoại lệ/test và P0–P6 đúng plan v0.1; không còn mục chờ duyệt ở pha lập plan. Các xác nhận kỹ thuật trước đó là lịch sử; phản hồi này là xác nhận riêng cho toàn bộ plan.
- Khi chốt pha lập plan chưa triển khai Java. Sau đó user yêu cầu triển khai và xác nhận riêng pha EXECUTING-PLANS theo nguồn đầu tài liệu; kết quả thực thi ở §8. Phê duyệt này không cấp quyền commit/push/deploy hoặc thêm class/method ngoài danh sách.

## 8. Kết quả thực thi — 04/11/2026

| Bước | Trạng thái và bằng chứng |
| --- | --- |
| P0 | DONE: worktree từ dev/son đúng baseline; JDK 23.0.1, Enforcer bật; Docker/dependency dùng fixture riêng và env-file lưu trước khi chạy |
| P1 | DONE: RedisService 5 operation; AvailabilityChangedEvent final UUID, Lombok theo danh sách |
| P2 | DONE: cache-aside/generation, TTL không gia hạn, JSON/fallback, listener AFTER_COMMIT; 11 cache test pass, gồm stale reader điều phối bằng latch |
| P3 | DONE: controller gọi cache, giữ contract/no-store; 5 MVC test pass; YAML và .env.example bổ sung hai biến TTL/prefix |
| P4 | DONE: hai writer config/cancellation và chín helper inventory cũ phát event; chỉ thêm publisher vào cuối dependency, giữ transaction/khóa/guard |
| P5 | DONE: 395 unit/MVC + 140 PostgreSQL integration = 535 pass, 0 failures/errors/skips theo XML; Spotless toàn module pass, SpotBugs 0 findings, JaCoCo report, BUILD SUCCESS |
| P6 | DONE: JAR thật với PostgreSQL/RabbitMQ/Redis/Keycloak riêng, 27/27 runtime case pass; TTL/JSON/hit/generation/invalidation/expiry theo TESTING.md; dọn fixture và secret env-file sau chạy |

Đối chiếu convention trước kết thúc: controller → cache → service DB qua proxy;
dependency constructor và property @Value; RedisService concrete chỉ operation
cần thiết; JsonMapper có sẵn; không thêm validator/query/lock/helper/dependency;
comment tiếng Việt ở nhánh transaction/lỗi; mặc định log chữ. C1–C10/M1–M49 đúng
danh sách đã duyệt, không có delta khai báo/chữ ký/annotation.

Runtime/test config nạp từ env-file. Hai tên mới BOOKING_AVAILABILITY_CACHE_TTL
và BOOKING_AVAILABILITY_CACHE_KEY_PREFIX đã ghi trong root .env.example. File
fixture target/cache-verify/maven.env lưu bốn biến RABBITMQ_HOST/PORT/USERNAME/PASSWORD
của broker riêng; các secret env-file được dọn sau chạy, không sửa root .env.
Redis runtime fixture riêng không đặt password, đã PING thực tế; không claim đã
xác thực Redis119. Không tác động shared119.

Chi tiết lệnh, ca và giới hạn: [Booking TESTING.md](../../../services/booking-service/TESTING.md).
Log/report nằm trong target/cache-verify/, target/surefire-reports/,
target/failsafe-reports/ và target/availability-curl/results.json của worktree.
JAR đã nghiệm thu có SHA256 a65dd9746f071ff7742bbe031708ef901c88fbf4a99274c49a05562499579515.
Không dùng enforcer.skip/skipTests để báo pass. Runtime hit dùng JSON/asOf/TTL;
unit test chứng minh không gọi service DB, không claim đo query/load runtime.

Ngoại lệ framework vẫn đúng những phần đã duyệt trong bảng: StringRedisTemplate,
generation UUID, ApplicationEventPublisher, TransactionalEventListener, Mockito/
MVC/PostgreSQL. Callback không bền: crash/Redis lỗi ở invalidation có thể để cache
cũ đến TTL; chưa thêm outbox/retry. Theo yêu cầu tiếp theo
của user, đã viết [giải thích implementation](../explanations/2026-11-04-booking-availability-redis-cache-impl-explained.md)
từ source/report hiện tại, không sửa Java hoặc chạy lại test trong lượt viết docs.

## 9. Bản bàn giao

User gọi shopapp-java-finishing-task ngày 04/11/2026, cho phép commit/push
origin/dev/son, theo dõi CI/deploy và cập nhật ClickUp. Task liên quan đã đọc:
[API GET thông tin ghế theo eventId](https://app.clickup.com/t/z8qgvc9wfq), hiện
complete; bàn giao cache là phần bổ sung, không tạo task hay đổi description.

Sau fetch, origin/dev/son ở 1d5bb553417e134ab08964bb9b6a4949c6672088.
dev/son local có ba commit Payment/merge chưa push. Bản bàn giao chỉ cherry-pick
commit cache lên baseline remote, giữ các commit Payment local nguyên trạng.
Đã so sánh toàn bộ Java Booking, cấu hình booking-service.yaml, .env.example và
workflow deploy: baseline tương đương với phần đã test; không cần viết lại code.
Config payment-service.yaml ngoài scope không được đưa theo bản bàn giao.

Kết quả local hiện hành: 535 test pass, 27 runtime pass, JAR khớp SHA256 của report.
Pipeline bắt buộc: Deploy Booking to119, theo đúng head SHA trên dev/son, gồm
Verify Booking integration, Verify Config Server, build/push image và deploy/health.
Trạng thái push/run/ClickUp được ghi bằng checkpoint sau khi có bằng chứng; không
coi việc chuẩn bị commit hoặc health check đơn lẻ là cả chuỗi đã hoàn tất.
