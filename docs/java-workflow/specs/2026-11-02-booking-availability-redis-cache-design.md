# Redis cache cho GET availability theo eventId

- Ngày tạo: 02/11/2026; cập nhật: 04/11/2026. Phiên bản: v0.4, gom thao tác Redis vào RedisService.
- Pha: **BRAINSTORMING — APPROVED**.
- Yêu cầu: sử dụng Redis cache cho API GET thông tin ghế theo ID.
- Phạm vi đã xác nhận: user chọn **“GET availability theo eventId hiện có (đề xuất)”** trong câu trả lời ngày 02/11/2026. Đây chỉ là xác nhận API đích.
- Yêu cầu nghiệp vụ đã xác nhận ngày 03/11: **“cache sẽ dài tầm 10 15p sẽ update khi inventory được update”**. Luồng v0.4 với mặc định PT15M và invalidation theo generation sau commit được user đồng ý ngày 04/11; danh sách khai báo được xác nhận riêng như dưới đây. Chưa triển khai Java hoặc chuyển pha.
- Yêu cầu cách tổ chức ngày 04/11: **“nên viết 1 cái redis service rồi set các method sẵn cho clear”**, sau đó user yêu cầu continue. v0.4 đề xuất concrete RedisService gom operation bên dưới; đây chưa phải duyệt toàn bộ class/method hoặc quyền viết Java.
- Xác nhận luồng ngày 04/11: user trả lời **“đồng ý”** ngay sau phần giải thích “về luồng thì sao?”. Phạm vi FLOW APPROVED: GET qua RedisService/generation, miss đọc DB và nạp JSON TTL 15 phút; inventory/config/event status thay đổi phát event trong transaction; AFTER_COMMIT đổi generation, rollback không invalidation; GET tiếp theo nạp lại; clear một key cụ thể và generation chống stale refill. Phần giải thích đã nêu giới hạn callback không bền khi crash/Redis lỗi; chưa đề xuất hoặc duyệt outbox/retry bền. Không suy xác nhận luồng thành duyệt toàn bộ C1–C10/M1–M49, test hoặc quyền triển khai.
- Xác nhận danh sách ngày 04/11: sau khi user hỏi **“các class/method nào”**, assistant trình 3 class nghiệp vụ mới, method/constructor/Lombok tương ứng, 3 class nghiệp vụ sửa, các helper tái sử dụng, test mới/sửa và dẫn bảng đầy đủ của spec; user trả lời **“xác nhận”**. Phạm vi APPROVED cho pha BRAINSTORMING: C1–C10/M1–M49 v0.4, annotation, API sinh, ngoại lệ cách viết và test đã trình. Kết hợp với xác nhận luồng ở trên, thiết kế v0.4 được chốt. Xác nhận này không cấp quyền chuyển pha lập plan/thực thi, commit/push hoặc thêm retry/outbox chưa thiết kế.

## 1. Mục tiêu và hiện trạng

Giảm truy vấn PostgreSQL khi nhiều client gọi `GET /api/v1/events/{eventId}/availability` gần nhau. Cache toàn bộ DTO `Availability` theo `eventId`, gồm `sourceConfigVersion`, `asOf`, `ticketTypes`, `sectors`, `seats`.

Implementation làm nền cho thiết kế nằm trên local branch `dev/son`, commit `ff72b286df051458a7e4635412e816b1402ce6a2`, đã đọc lại bằng `git show` ngày 03/11. Có controller và `AvailabilityServiceImpl.getAvailability(UUID)` đọc snapshot/inventory trong `@Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)`. Method trả `CONFIG_NOT_READY` khi chưa có snapshot, `EVENT_NOT_ON_SALE` khi event không PUBLISHED; dùng inventory hiện hành để tính số còn.

**Điều kiện tích hợp:** workspace hiện ở `dev/tuyen`, commit `9a8bac5`, nơi AvailabilityController chỉ có dependency và AvailabilityService còn rỗng. Các mục REUSE bên dưới chỉ đúng với baseline `dev/son` nói trên. Task cache cần checkout/worktree đúng baseline hoặc đưa implementation GET vào trước; tài liệu này không tự chuyển nhánh, merge hay mở rộng task để viết lại GET. Đã đọc trạng thái nhánh hiện tại bằng CodeGraph và đối chiếu `dev/son` bằng `git show`.

Booking đã có `spring-boot-starter-data-redis`; Config Server đã khai báo host, port, password. Baseline `dev/son` dùng Spring Boot 4.1.1, Java 23, Jackson 3; không đổi version của dự án theo repo tham khảo hoặc nhánh workspace khác.

## 2. Hướng thiết kế — APPROVED

Thêm concrete service `AvailabilityCacheService`: method `getAvailability(UUID eventId)` cho đường đọc và listener `invalidateAvailabilityCache(AvailabilityChangedEvent event)` chạy sau commit. Controller gọi service này; khi cần đọc DB, service cache gọi `AvailabilityService.getAvailability(eventId)` qua Spring proxy hiện có. Hai service ghi hiện có chỉ thêm publish event nội bộ, không gọi ngược cache service nên không tạo dependency cycle.

```text
AvailabilityController
  → AvailabilityCacheService.getAvailability(eventId)
      → lấy generation cache của event → GET dữ liệu thuộc generation đó
      → có JSON → trả DTO đã cache
      → miss / quá hạn / lỗi cache
          → AvailabilityService.getAvailability(eventId)
              → transaction REPEATABLE_READ hiện tại → PostgreSQL
          → Redis SET JSON dưới generation đã đọc, kèm TTL 15 phút
          → trả DTO

Inventory/config/event status thay đổi trong transaction DB
  → publish AvailabilityChangedEvent(eventId) trong transaction
  → commit thành công → listener đổi generation Redis của event
  → GET tiếp theo miss generation mới → đọc DB và nạp cache mới
```

Lý do cần class riêng: method đọc hiện tại mang transaction DB. Đặt thao tác Redis trực tiếp trong method đó vẫn đi qua transaction dù cache hit; tách method transaction sang cùng class lại gặp self-invocation. C1 đặt cache ngoài transaction và tái sử dụng nguyên thân method đọc hiện tại. Không thêm cặp interface/implementation, Redis repository, mapper hay helper riêng.

Tạo concrete `RedisService` dùng `StringRedisTemplate` do Spring Boot cung cấp. AvailabilityCacheService gọi RedisService cho thao tác Redis và dùng JsonMapper hiện có để ánh xạ thẳng Availability.class. JSON, TTL, key theo event và generation thuộc AvailabilityCacheService; RedisService chỉ thao tác key/value chuỗi. Theo cache-aside Shop App public và service Redis của mall đã đọc ở §10, thu gọn theo operation task cần. Không tạo thêm interface/implementation.

`@Cacheable` + `RedisCacheManager` là phương án hợp lệ và đã đối chiếu ở §10. Với yêu cầu JSON DTO + Redis hỏng vẫn đọc DB + cache ngoài transaction, phương án đó còn cần cấu hình serializer, xử lý CacheErrorHandler và xác minh thứ tự cache/transaction advice. Với task hiện tại, C1 điều phối cache và gọi operation thông qua C10 RedisService. Đây là lựa chọn cho task này, không phải quy tắc cấm Spring Cache.

## 3. Chính sách cache và độ nhất quán — APPROVED

| Nội dung | Đề xuất |
| --- | --- |
| Đơn vị cache | Toàn bộ Availability của một event, áp dụng cả QUANTITY và SEAT_MAP |
| Key | Generation: `keyPrefix + eventId + ":generation"`; dữ liệu: `keyPrefix + eventId + ":data:" + generation`; prefix v2 để không dùng lại key cũ |
| Value | JSON DTO hiện có; giữ nguyên `asOf`, version dạng chuỗi, counter dạng chuỗi và mảng |
| Thời hạn | JSON mặc định `PT15M` từ SET, cấu hình được PT10M; GET không gia hạn. Generation là UUID ngẫu nhiên, không TTL |
| Tắt cache | TTL bằng 0 hoặc âm: gọi service DB trực tiếp, không truy cập Redis |
| Lỗi nghiệp vụ/DB | Không cache exception hay response lỗi |
| Invalidation | Sau commit inventory/config/event status: thay generation bằng UUID mới; GET sau invalidation thành công không dùng namespace dữ liệu cũ |

**Hành vi theo yêu cầu mới:** cache tồn tại lâu khi dữ liệu không đổi. Inventory/config/trạng thái event thay đổi sẽ làm cache mất hiệu lực sau commit; không đợi 15 phút. GET tiếp theo tải snapshot DB mới và cache lại. Không rebuild toàn bộ availability sau từng write vì có thể gây nhiều query khi liên tục giữ/nhả ghế.

Giữ nguyên `asOf` từ response DB. TTL là thời gian sống của JSON từ SET, không phải lịch làm mới định kỳ. Không có request sau invalidation thì không query/rebuild. Vẫn có cửa sổ từ DB commit tới listener Redis hoàn tất và các response đã bắt đầu trước invalidation; không coi đây là strong consistency giữa DB và Redis.

Ghi JSON bằng `redisService.set(dataKey, json, cacheTtl)`; get thường không gia hạn. Invalidation dùng `redisService.set(generationKey, UUID.randomUUID().toString())`. RedisService chuyển các lời gọi này tới StringRedisTemplate tương ứng. Không dùng sourceConfigVersion vì đặt/nhả ghế không tăng version cấu hình; không dùng reservationGeneration vì nó chỉ thuộc một ghế.

**Chặn stale refill:** reader lấy G1 trước khi đọc DB và chỉ ghi `data:G1`. Inventory commit rồi listener đổi generation thành G2. Dù reader cũ ghi sau listener, GET mới lấy G2 nên không dùng `data:G1`. Không reread generation mới rồi gắn nó cho kết quả DB cũ. Hai miss cùng generation vẫn có thể cùng đọc DB; không thêm distributed lock hoặc Lua.

Nếu generation bị thiếu/Redis evict metadata, reader tạo UUID mới bằng SET NX, rồi GET generation thực tế; nếu vẫn thiếu thì đọc DB và không cache request đó. Không tái sử dụng UUID cũ hoặc dùng số mặc định 0 vì có thể khôi phục namespace cũ. Các JSON generation cũ tự hết hạn sau 15 phút; không quét KEYS/flushDb. Generation không TTL là một key metadata nhỏ mỗi event đã truy cập; dọn metadata theo vòng đời event là ngoài task hiện tại.

**Giới hạn lỗi cần duyệt:** AFTER_COMMIT là callback trong tiến trình, không có giao dịch phân tán hoặc retry bền. Nếu Redis lỗi đúng lúc đổi generation, hoặc tiến trình dừng sau DB commit trước callback, cache cũ có thể còn hiệu lực đến TTL. GET khi Redis đang lỗi vẫn fallback DB; Redis phục hồi không tự chứng minh invalidation đã chạy. Bản đề xuất này không tự thêm outbox mới cho cache. Nếu yêu cầu cập nhật cache vẫn được bảo đảm sau crash/Redis outage, phần retry bền phải bổ sung và duyệt trước khi triển khai; không tuyên bố bản AFTER_COMMIT đơn thuần đáp ứng bảo đảm đó.

Cache chỉ phục vụ hiển thị. POST tạo order tiếp tục đọc DB, lấy khóa inventory và kiểm trạng thái/sale window. Redis không quyết định còn quyền mua hoặc giữ ghế thành công.

## 4. Luồng chính và lỗi

1. MVC parse UUID như hiện tại; ID sai trả 400 trước khi gọi cache service. Giữ public GET và xử lý JWT hiện tại.
2. Nếu TTL không dương, gọi thẳng `AvailabilityService.getAvailability(eventId)`.
3. Qua RedisService đọc generation; thiếu thì khởi tạo UUID bằng setIfAbsent rồi get lại giá trị thực tế. Có generation thì get dataKey tương ứng, đọc JSON bằng JsonMapper; DTO khác null thì trả lại. Key thiếu là miss, GET không gia hạn. JSON null được xử lý như bản JSON lỗi ở bước 4.
4. Redis read ném DataAccessException: ghi WARN, chuyển sang DB và bỏ qua cache write để tránh chờ thêm timeout. JSON ném tools.jackson.core.JacksonException hoặc đọc ra null: clear đúng dataKey lỗi qua RedisService rồi coi là miss. Clear ném DataAccessException thì log, bỏ cache write; DB vẫn được gọi ngoài các catch cache. Không xóa generation hoặc key event khác.
5. Gọi service đọc DB **ngoài các khối catch lỗi cache**. `CONFIG_NOT_READY` giữ HTTP 503, `EVENT_NOT_ON_SALE` giữ HTTP 409; lỗi DB/commit đi theo handler cũ. Không chuyển chúng thành response thành công.
6. Khi DB thành công, Redis read không lỗi và đã có generation trước khi đọc DB, serialize và SET vào dataKey của generation đó với TTL cấu hình. Chỉ bắt `DataAccessException`/`JacksonException` của thao tác cache; lỗi ghi/serialize không làm mất response DB đã có. Không catch toàn bộ `RuntimeException`.
7. Controller trả 200 cùng JSON contract cũ và `Cache-Control: no-store`. Header này vẫn ngăn client/proxy lưu response, còn Redis là cache nội bộ do Booking quản lý.

WARN dùng logger chữ mặc định của Boot, nêu thao tác, eventId và loại lỗi; không log value JSON, thông tin kết nối hoặc credentials. Không tạo schema log mới.

## 5. Mapping, validation, transaction và cấu hình

- Dùng lại `Availability` cùng ba nested DTO, constructor/getter Lombok hiện có. Không thêm response, setter, builder hoặc DTO cache. Jackson 3 hỗ trợ nhận tên tham số constructor; parent Boot hiện bật compiler `parameters=true`. Round-trip JSON thật của cả DTO/nested DTO là điều kiện kiểm chứng, không mặc định fallback liên tục vẫn được coi là cache hoạt động.
- Không chạy lại validation quan hệ sales configuration, query inventory hoặc kiểm tra tuổi snapshot để xác minh cache hit. Các kiểm tra ghi thuộc writer baseline; Redis quản lý thời gian sống của key. Không thêm helper verify/validate.
- C1 không có `@Transactional`. Service đọc DB hiện tại giữ nguyên annotation, thuật toán projection và lỗi. Lệnh Redis write diễn ra sau khi proxy đọc DB trả về thành công; Redis không tham gia transaction DB.
- Đối chiếu writer baseline và các helper ở §11: giữ nguyên guard/khóa/transaction/ACK; thêm publish event tại điểm đã cập nhật inventory hoặc gate/config. Listener AFTER_COMMIT chỉ sửa Redis, không gọi repository DB hoặc ghi DB trong transaction đã kết thúc. Rollback không đổi generation.
- Chỉ đề xuất thêm cấu hình vào `infrastructure/config-server/src/main/resources/config/booking-service.yaml`:

```yaml
booking:
  availability-cache:
    ttl: ${BOOKING_AVAILABILITY_CACHE_TTL:PT15M}
    key-prefix: ${BOOKING_AVAILABILITY_CACHE_KEY_PREFIX:flash-ticket:dev:booking:availability:v2:}
```

- Khi triển khai, đồng bộ hai tên biến với `.env.example`; các môi trường dùng chung Redis phải có prefix riêng. C1 dùng `@Value("${booking.availability-cache.ttl:PT15M}")` cho `Duration cacheTtl` và `@Value("${booking.availability-cache.key-prefix:flash-ticket:dev:booking:availability:v2:}")` cho `String keyPrefix`. Dependencies vẫn constructor injection. Không thêm dependency hoặc bean Redis riêng nếu auto-configuration hiện tại đáp ứng.
- Kết nối tiếp tục lấy `REDIS_HOST`, `REDIS_PORT`, `REDIS_PASSWORD` từ env dự án qua Config Server. Baseline dev/son có timeout kết nối/lệnh 2s; fallback vẫn có thể chịu thời gian chờ Redis, không cam kết bỏ độ trễ này. Chưa đổi timeout hoặc thêm circuit breaker trong task.
- Khi nghiệm thu kết nối, phải xác thực/PING Redis thực tế. Tài liệu này chưa xác nhận server đang truy cập được và không đọc/in secret.

## 6. Đối chiếu convention và nguồn Shop App

Đã đọc `common.md` và convention Flash Ticket ứng với Git common directory `D:/DATN/flash-ticket-platform/.git`.

| Rule ảnh hưởng | Cách áp dụng cụ thể |
| --- | --- |
| Tái sử dụng trước khi tạo tầng mới | C3/M5 giữ projection/transaction đọc; C1 quản lý cache/listener; C10 gom 5 operation Redis theo yêu cầu user ngày 04/11. Dùng lại 9 helper ghi inventory, không thêm validation; event C7 chỉ mang UUID event |
| Không thêm validation thừa | Dùng UUID boundary baseline; không đọc DB/xác minh sales config trên hit. Bỏ kiểm tuổi snapshot riêng của v0.1; mô tả đúng giới hạn TTL |
| Constructor injection và field @Value | C1 theo `ProductService`/`JwtTokenUtil`; C2 giữ constructor Lombok, thay dependency tương ứng |
| Mapping Jackson có sẵn | Inject `tools.jackson.databind.json.JsonMapper`, tái sử dụng `BookingJsonConfig`; test dùng `BookingFixtures.jsonMapper()` |
| Giữ nghiệp vụ inventory/khóa | Không đổi QUANTITY/STANDING/SEATED, AUTO ACK, cancellation worker, transaction ghi hoặc khóa order/inventory |
| Logging Booking | `@Slf4j`, log chữ mặc định; chỉ log nhánh cache hỏng, không thêm formatter/helper |
| Env dự án | Chỉ mô tả thêm tên biến; chưa sửa `.env`/`.env.example`, không override giá trị khi run/test |

Đã đọc source Shop App tại `C:/Users/sondimid/IdeaProjects/shopapp-backend/shopapp-backend/shopapp/src/main/java/com/project/shopapp/`:

- `controllers/ProductController.java#getProducts`: controller mỏng, gọi service qua dependency final và `@RequiredArgsConstructor`.
- `services/impl/ProductService.java#getProductById`: service dùng dependency final, gọi storage trực tiếp, giữ exception; không thêm tầng generic.
- `components/JwtTokenUtil.java`: property `@Value` trên field Spring bean.

**Khác mẫu cần duyệt:** service cache cụ thể không tạo thêm interface; generation UUID, Spring ApplicationEventPublisher/@TransactionalEventListener(AFTER_COMMIT); Redis/Jackson 3; TTL/fallback; @Slf4j; test Mockito và transaction thật baseline. Local Shop App không có Redis/event invalidation; nguồn tham khảo nằm ở §10–11, không gán chúng cho bản local.

## 7. Bảng duyệt class/file — APPROVED cho BRAINSTORMING v0.4

Đường dẫn Java tính từ `services/booking-service/src/main/java/com/flashticket/booking_service/`; test dùng `src/test/java` với cùng package gốc.

| ID | Class/file | NEW hoặc EXISTING | Vai trò, field/annotation/API sinh | Mẫu Shop App hoặc ngoại lệ |
| --- | --- | --- | --- | --- |
| C1 | `availability/service/AvailabilityCacheService.java` | NEW | `public class`; @Service/@RequiredArgsConstructor/@Slf4j; final AvailabilityService availabilityService, RedisService redisService, JsonMapper jsonMapper; @Value Duration cacheTtl, String keyPrefix. Lombok sinh constructor ba dependency và static final logger; listener M22; không getter/setter/builder/equals/hashCode/toString | ProductService + JwtTokenUtil; cache/generation/listener là ngoại lệ §6 |
| C2 | `availability/controller/AvailabilityController.java` | EXISTING, sửa | Thay final `AvailabilityService` bằng `AvailabilityCacheService availabilityCacheService`; giữ annotation hiện có; constructor Lombok đổi kiểu tham số | ProductController; controller vẫn chỉ gọi service |
| C3 | `availability/service/AvailabilityService.java`, `availability/service/impl/AvailabilityServiceImpl.java` | EXISTING, sửa impl | Giữ interface và đường đọc M5; impl thêm final ApplicationEventPublisher applicationEventPublisher vào constructor Lombok, publish C7 ở hai writer M27/M28; giữ annotations cũ | ProductService; event Spring là ngoại lệ |
| C4 | `dto/response/Availability.java` cùng `TicketTypeAvailability`, `SectorAvailability`, `SeatAvailability` | EXISTING, REUSE | Giữ field final, `@Getter`, `@AllArgsConstructor`, annotation JSON/validation hiện có; không sinh thêm API | DTO contract dự án |
| C5 | Test `availability/service/AvailabilityCacheServiceTests.java` | NEW | Package-private; @ExtendWith(MockitoExtension.class); @Mock AvailabilityService, @Mock StringRedisTemplate, @Mock ValueOperations<String,String>; field JsonMapper thật từ fixture, RedisService thật bọc template mock, cacheService; không Lombok. Khởi tạo hai service trong setup M7, ReflectionTestUtils gán TTL/prefix test | Ngoài Shop App; giữ test cache-aside/generation và lỗi qua RedisService thật, không thêm unit test chỉ lặp operation wrapper |
| C6 | Test `availability/controller/AvailabilityControllerTests.java` | EXISTING, sửa | Đổi `@MockitoBean AvailabilityService` thành `@MockitoBean AvailabilityCacheService`; giữ MockMvc/security/handler và năm test hiện có | MVC tests dự án; không thêm test method |

## 8. Bảng duyệt constructor/method — APPROVED cho BRAINSTORMING v0.4

Chữ ký dưới đây không thêm `throws` trừ nơi ghi rõ; method không `static` trừ M21. Đây là đề xuất khai báo để duyệt, chưa phải quyền tạo Java.

| ID | Class | Constructor/method đầy đủ | NEW hoặc REUSE | Cách viết và lý do cần |
| --- | --- | --- | --- | --- |
| M1 | C1 | `public AvailabilityCacheService(AvailabilityService availabilityService, RedisService redisService, JsonMapper jsonMapper)` | NEW, đổi so với v0.3; Lombok sinh | Constructor injection; thay template trực tiếp bằng C10, giữ JSON/đường đọc DB |
| M2 | C1 | `public Availability getAvailability(UUID eventId)` | NEW | Cache-aside §3–4; không transaction/override/helper; được controller gọi, chỉ gọi DB service khi cần |
| M3 | C2 | `public AvailabilityController(AvailabilityCacheService availabilityCacheService)` | EXISTING, đổi chữ ký Lombok | Constructor cũ nhận AvailabilityService; đổi dependency để GET đi qua cache |
| M4 | C2 | `public ResponseEntity<Availability> getAvailability(@PathVariable UUID eventId)` | REUSE, sửa thân | Giữ `@GetMapping`, gọi C1 thay service cũ; giữ no-store và schema |
| M5 | C3 implementation/interface | `public Availability getAvailability(UUID eventId)` | REUSE nguyên trạng | Impl giữ `@Override`, transaction readOnly/REPEATABLE_READ; interface không thêm method |
| M6 | C5 | `AvailabilityCacheServiceTests()` | NEW, constructor mặc định package-private | Không khai báo constructor riêng hoặc API sinh |
| M7 | C5 | `void setUp()` | NEW | `@BeforeEach`; tạo C1, mapper fixture, gán TTL/prefix bằng ReflectionTestUtils; stub trong từng test để tránh stub thừa |
| M8 | C5 | `void returnsCachedAvailabilityWithoutLoadingDatabase()` | NEW | `@Test`; JSON thật round-trip đủ nested DTO/UUID/Instant/enum/counter, giữ asOf; verify không gọi DB hoặc SET |
| M9 | C5 | `void loadsAndCachesAvailabilityOnMiss()` | NEW | `@Test`; đọc generation trước DB, ghi key event/generation đúng TTL PT15M; hai event không dùng chung key |
| M10 | C5 | `void usesConfiguredTtlWithoutRefreshingOnHit()` | NEW, đổi so với v0.1 | `@Test`; SET truyền đúng Duration; hit chỉ GET, không SET/EXPIRE/GETEX. Hết hạn thật kiểm bằng Redis ở §9 |
| M11 | C5 | `void fallsBackToDatabaseWhenRedisReadFails()` | NEW | `@Test`; DataAccessException khi GET → DB, không thử SET; không thay response |
| M12 | C5 | `void returnsDatabaseResponseWhenCacheWriteFails()` | NEW | `@Test`; SET lỗi → vẫn trả DTO DB. Lỗi serialize cũng giữ response; dùng spy mapper trong chính test nếu cần, không helper mới |
| M13 | C5 | `void reloadsAvailabilityWhenCachedJsonIsInvalid()` | NEW | @Test; JSON lỗi/null → clear đúng dataKey lỗi qua C10, rồi DB và ghi DTO hợp lệ kèm TTL. Clear lỗi Redis thì vẫn fallback DB và bỏ ghi Redis trong request; verify không xóa generation/key event khác |
| M14 | C5 | `void propagatesSourceErrorsWithoutCachingThem()` | NEW | `@Test`; service ném BusinessException 503/409 hoặc lỗi DB → giữ nguyên exception, không SET |
| M15 | C5 | `void bypassesRedisWhenCacheTtlIsNotPositive()` | NEW | `@Test`; TTL 0/âm → DB, không thao tác Redis |
| M16 | C6 | `void returnsPublicAvailabilityWithoutJwt() throws Exception` | REUSE, sửa mock | Giữ payload B13/public/no-store; mock C1 |
| M17 | C6 | `void rejectsMalformedEventId() throws Exception` | REUSE, sửa mock | 400 trước cache service |
| M18 | C6 | `void returnsConfigNotReadyProblem() throws Exception` | REUSE, sửa mock | Giữ 503 CONFIG_NOT_READY |
| M19 | C6 | `void returnsEventNotOnSaleProblem() throws Exception` | REUSE, sửa mock | Giữ 409 EVENT_NOT_ON_SALE |
| M20 | C6 | `void rejectsInvalidBearerToken() throws Exception` | REUSE, sửa mock | Giữ 401 và không gọi cache service |
| M21 | BookingFixtures | `public static JsonMapper jsonMapper()` | REUSE nguyên trạng | Tạo mapper test theo JacksonAutoConfiguration + BookingJsonConfig hiện có |

Không đề xuất private helper, lifecycle method, method `@Bean`, subclass hoặc anonymous class mới. API DTO/framework đã tồn tại được gọi lại nguyên trạng. Delta v0.3 bổ sung ở §11 là một phần danh sách cần duyệt; nếu triển khai cần khai báo/annotation khác phải trình delta theo gate của pha tương ứng.

## 9. Tiêu chí nghiệm thu cho pha triển khai sau

1. Cache hit trả đúng contract và giữ asOf; không gọi service DB. Miss đọc DB, lưu JSON có thời hạn; key theo event/môi trường không lẫn nhau.
2. Redis read/write lỗi hoặc JSON lỗi không làm hỏng response DB; lỗi nghiệp vụ/DB vẫn đi ra đúng handler, không được cache. Tắt cache bằng TTL không dương hoạt động.
3. GET không gia hạn JSON; SET JSON có TTL 15 phút. Generation dùng UUID mới mỗi invalidation, không TTL. DTO round-trip thật hoạt động; giữ test tính tồn kho, transaction đọc và guard ghi baseline.
4. Test MVC hiện tại vẫn chứng minh public access, JWT, UUID, lỗi và no-store. Unit tests cache không thay thế kiểm chứng tích hợp Redis.
5. Redis thật: xác thực/PING; hai GET dùng cùng generation/JSON; PTTL JSON gần cấu hình, GET không gia hạn. Sau inventory transaction commit, generation đổi; GET mới thấy DB mới dù key cũ chưa hết 15 phút. Rollback không đổi generation. Test reader bị giữ lại ở G1, writer commit đổi G2 rồi reader ghi G1: GET mới vẫn đọc G2. Test cấu hình/hủy event và cả QUANTITY/STANDING/SEATED; tạo order vẫn dùng DB/khóa. Chưa thực hiện trong pha thiết kế.

## 10. Nguồn tham khảo và trạng thái xác nhận

### Kết quả nghiên cứu ngày 03/11/2026

Đã đọc source thật qua GitHub connector, không chỉ README/search snippets. Đây là ví dụ cách viết; chưa chạy hoặc kiểm chứng độ đúng/chịu tải của các repo đó.

| Repo/source | Cách dùng Redis đã thấy | Áp dụng vào task |
| --- | --- | --- |
| Shop App local chuẩn tại đường dẫn §6 | Tìm trong src/POM không thấy Redis/Cacheable; ProductController/ProductService là CRUD thông thường | Dùng làm mẫu injection, bố cục controller/service; không coi là bằng chứng Redis |
| [hoangtien2k3/shopapp-backend — ProductRegisServiceImpl](https://github.com/hoangtien2k3/shopapp-backend/blob/a427288fd722d8379f8d7fd20cf65252cc6886ff/src/main/java/com/hoangtien2k3/shopappbackend/services/impl/ProductRegisServiceImpl.java), [ProductController](https://github.com/hoangtien2k3/shopapp-backend/blob/a427288fd722d8379f8d7fd20cf65252cc6886ff/src/main/java/com/hoangtien2k3/shopappbackend/controllers/ProductController.java) | RedisTemplate GET JSON; Jackson đọc DTO; miss trả null, controller gọi productService rồi saveAllProducts. Key gồm filter/page/sort. SET trong method này không có TTL; clear() gọi flushDb() | Học cache-aside, DTO JSON, key theo đầu vào. Với availability thêm TTL; không mang flushDb, cache vô hạn hoặc metadata phân trang của repo sang task |
| [bezkoder/spring-boot-redis-example — TutorialService](https://github.com/bezkoder/spring-boot-redis-example/blob/f72f5a07824c68a27ffc06436df7d55e18b3b0f5/src/main/java/com/bezkoder/spring/redis/service/TutorialService.java), [RedisConfig](https://github.com/bezkoder/spring-boot-redis-example/blob/f72f5a07824c68a27ffc06436df7d55e18b3b0f5/src/main/java/com/bezkoder/spring/redis/config/RedisConfig.java) | @Cacheable ở service; @CacheEvict ở update/delete; RedisCacheManager đặt TTL theo cache, JSON serializer Jackson 2 | Phương án annotation hợp lệ. Không chép TTL theo phút, field injection hoặc Jackson 2 sang Booking; cần xử lý lỗi cache riêng nếu chọn hướng này |
| [macrozheng/mall — RedisServiceImpl](https://github.com/macrozheng/mall/blob/dcaa93b3150352e5044708d7211b5bed0af4509f/mall-common/src/main/java/com/macro/mall/common/service/impl/RedisServiceImpl.java), [UmsMemberCacheServiceImpl](https://github.com/macrozheng/mall/blob/dcaa93b3150352e5044708d7211b5bed0af4509f/mall-portal/src/main/java/com/macro/mall/portal/service/impl/UmsMemberCacheServiceImpl.java) | RedisTemplate GET/SET/DEL; SET có timeout; key gồm namespace, loại dữ liệu, username; TTL lấy từ property. BaseRedisConfig đồng thời có RedisCacheManager | Học namespace + ID + TTL cấu hình. Không mang toàn bộ generic RedisService/hash/list API hoặc config polymorphic Jackson 2 vào một endpoint |
| [spring-projects/spring-data-examples — KeyOperationsTests](https://github.com/spring-projects/spring-data-examples/blob/7747029e6157cb780862826b6ae87c88d7a4df3c/redis/example/src/test/java/example/springdata/redis/commands/KeyOperationsTests.java) | Phân biệt KEYS chặn và SCAN chia lượt; dùng RedisConnection ở test | Không cần quét key cho task: biết sẵn key từng event và để TTL hết hạn; không thêm clear-all |

**Lựa chọn sau đối chiếu:** RedisTemplate và Spring Cache đều được các repo dùng; không có cơ sở nói một hướng luôn tốt hơn. Task này chọn StringRedisTemplate + mapper có sẵn và một service cache cụ thể. TTL chuyển thành 15 phút, invalidation theo inventory ở v0.3. Học mô hình của Shop App public và cách đặt TTL/key của mall; giữ controller một lời gọi service.

Tài liệu chính thức xác nhận RedisCacheManager mặc định không đặt expiration, dùng JDK serializer và chưa transaction-aware; vì vậy thêm @Cacheable đơn lẻ chưa đáp ứng JSON/TTL của task. Spring Cache mặc định ném lỗi cache lại caller; muốn fallback cần CacheErrorHandler phù hợp. Các vấn đề này cấu hình được, không phải hạn chế cố hữu khiến annotation không dùng được.

### Lịch sử v0.2 so với v0.1

- Giữ C1–C6 và chữ ký M1–M9/M11–M21; M10 đổi chữ ký/nội dung như bảng. Không tạo thêm class/helper.
- TTL đổi sang tính từ SET, Redis tự hết hạn; bỏ kiểm tuổi theo asOf và tính TTL còn lại. Vì vậy bỏ cam kết tuổi snapshot tối đa 2 giây; asOf vẫn giữ nguyên để hiển thị thời điểm dữ liệu.
- Bổ sung đối chiếu ba repo ứng dụng và một repo mẫu chính thức; xác định đúng baseline dev/son, không nói API đã có trên dev/tuyen.
- Không ghi APPROVED cho lựa chọn kỹ thuật nào từ yêu cầu nghiên cứu lại.

- Source dự án: `AvailabilityController#getAvailability`, `AvailabilityServiceImpl#getAvailability/processEventCancellation/synchronizeSalesConfiguration`, `OrderServiceImpl#createOrder/expireOrder`, DTO Availability, BookingJsonConfig, BookingFixtures, AvailabilityControllerTests; đọc bằng CodeGraph và đối chiếu cấu hình/POM hiện tại.
- [Contract B13](../../architecture/B13-api-and-event-contracts.md): API availability, schema và ý nghĩa tồn kho.
- [Spring Data Redis — RedisTemplate/StringRedisTemplate](https://docs.spring.io/spring-data/redis/reference/redis/template.html): chọn thao tác chuỗi và JSON của ứng dụng; không sao chép cấu hình bean thủ công khi Boot đã cung cấp.
- [ValueOperations API](https://docs.spring.io/spring-data-redis/reference/api/java/org/springframework/data/redis/core/ValueOperations.html): GET/SET với Duration cho value có TTL.
- [Ví dụ mã nguồn mở Spring Data Redis 4.1.1](https://github.com/spring-projects/spring-data-redis/blob/4.1.1/src/test/java/org/springframework/data/redis/core/DefaultValueOperationsIntegrationTests.java): đối chiếu cách ghi value có expiration và kiểm tra thời hạn; chỉ áp dụng thao tác cần dùng, không mang nguyên test harness.
- [Jackson 3 — constructor parameter names và Java time tích hợp](https://github.com/FasterXML/jackson/wiki/Jackson-Release-3.0): tái sử dụng mapper/DTO hiện có, không thêm module Jackson 2.
- [Spring Data Redis — RedisCacheManager](https://docs.spring.io/spring-data/redis/reference/redis/redis-cache.html): expiration, serializer, prefix và transaction-aware là cấu hình riêng.
- [Spring Cache — annotation/error handler/advice order](https://docs.spring.io/spring-framework/reference/integration/cache/annotations.html): đối chiếu @Cacheable, CacheErrorHandler và thứ tự advice.
- [Spring Boot — caching](https://docs.spring.io/spring-boot/reference/io/caching.html): auto-configuration và các property spring.cache.redis; không thêm cấu hình connection factory thủ công theo repo demo.

## 11. Invalidation khi cập nhật inventory — delta v0.3, APPROVED trong v0.4

User đã chọn thời gian cache khoảng 10–15 phút và cache phản ánh thay đổi inventory. Chính sách đó thay thế phương án TTL 2 giây đơn thuần của v0.2. Mặc định đề xuất PT15M. TTL là cơ chế dọn cache và dự phòng, invalidation sau commit là đường cập nhật chính.

### Điểm phát event và tác động tới luồng cũ

| Luồng thực tế | Điểm tái sử dụng để publish AvailabilityChangedEvent(eventId) |
| --- | --- |
| createOrder giữ chỗ QUANTITY/STANDING/SEATED | Cuối từng reserveQuantityTickets/reserveStandingTickets/reserveSeatedTickets sau phần cập nhật inventory hiện có |
| Buyer hủy, expiry, event-cancellation worker nhả HELD; acceptPayment chốt HELD→COMMITTED hoặc release | Cuối updateQuantityInventoryForPaymentDecision/updateStandingInventoryForPaymentDecision/updateSeatedInventoryForPaymentDecision. Các caller dùng chung helper nên không thêm event ở từng controller/worker |
| Phát vé FAILED hoặc hủy event trả COMMITTED | Cuối releasePurchasedQuantityInventory/releasePurchasedStandingInventory/releasePurchasedSeatInventory |
| synchronizeSalesConfiguration | Publish một lần trong nhánh áp dụng sourceConfigVersion mới, sau khi cập nhật snapshot/inventory; version cũ/replay không publish |
| processEventCancellation | Publish khi tạo gate CANCELLED hoặc đổi trạng thái sang CANCELLED; JSON thương mại có thể vẫn PUBLISHED nên phải dựa trên cột trạng thái như implementation hiện có |

Không publish tại setter/entity listener: không đủ eventId/ngữ cảnh transaction và dễ bỏ sót bulk update. Không thay guard/khóa/transaction của các helper. Event chỉ mang eventId, không mang inventory/entity JPA. Replay không đi vào nhánh ghi thì không phát event; một nhánh release không đổi row vẫn có thể phát invalidation thừa an toàn, không thêm verify helper chỉ để tránh một lần đổi generation. Promotion/purchase limit hoặc order metadata thuần túy không thay payload Availability thì không cần invalidation riêng.

ApplicationEventPublisher là dependency Spring có sẵn; publisher gọi trong transaction hiện tại. C1 listener dùng `@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = false)`, không @Async, không DB transaction mới. Sau commit thành công, listener đổi generation UUID ở Redis. Rollback không chạy listener. Một transaction phát nhiều event có thể đổi generation nhiều lần; không thêm tầng gom event. Khi bắt DataAccessException của Redis, log WARN ngắn và không giả vờ có thể rollback DB đã commit. Các lỗi cache sau commit không được biến thành lỗi nghiệp vụ khiến caller hiểu nhầm inventory chưa ghi.

### Bổ sung bảng class/file — APPROVED cho BRAINSTORMING v0.4

| ID | Class/file | NEW hoặc EXISTING | Vai trò, field/annotation/API sinh | Mẫu Shop App hoặc ngoại lệ |
| --- | --- | --- | --- | --- |
| C7 | `availability/event/AvailabilityChangedEvent.java` | NEW | `public final class`, @Getter/@AllArgsConstructor; một field `private final UUID eventId`; Lombok sinh public constructor và getEventId(); không setter/builder/equals/hashCode/toString | Hình dạng class Lombok theo Shop App; application event là ngoại lệ Spring |
| C8 | `order/service/impl/OrderServiceImpl.java` | EXISTING, sửa | Giữ @Service/@RequiredArgsConstructor và dependency cũ; thêm final ApplicationEventPublisher applicationEventPublisher, constructor Lombok đổi tương ứng. Chỉ chèn publish event trong 9 helper ghi hiện có | Reuse helper đúng trách nhiệm; không tách logic ghi/validation mới |
| C9 | Test `persistence/BookingPersistenceIT.java` | EXISTING, sửa | Giữ DataJpaTest/PostgreSQL/transaction test hiện có; @Import thêm AvailabilityCacheService và RedisService; @Autowired ApplicationEventPublisher và AvailabilityCacheService; @MockitoBean(answers=Answers.RETURNS_DEEP_STUBS) StringRedisTemplate. Không thêm test configuration/helper/constructor | RedisService thật, template mock; kiểm callback commit/rollback và writer, không thay runtime Redis thật |

C3 đã có thay đổi dependency trong §7; M26 dưới đây liệt kê constructor đổi. C1/M1 giữ ba dependency; listener không inject ngược service ghi. Constructor C5/C9 không thêm khai báo. Các setup/test hiện có gọi constructor C3/C8 phải truyền thêm publisher mock/Spring sẵn có, không tạo helper hoặc constructor overload mới. Bảng C1–C6 ở §7 được bổ sung C7–C9 tại đây; đã được duyệt cùng v0.4 cho pha BRAINSTORMING.

### Bổ sung bảng method/constructor — APPROVED cho BRAINSTORMING v0.4

Tất cả chữ ký không static và không thêm throws; các method REUSE giữ visibility/annotation/chữ ký hiện có, chỉ thêm event ở thân.

| ID | Class | Constructor/method đầy đủ | NEW hoặc REUSE | Cách viết và lý do cần |
| --- | --- | --- | --- | --- |
| M22 | C1 | `public void invalidateAvailabilityCache(AvailabilityChangedEvent event)` | NEW | @TransactionalEventListener(phase=TransactionPhase.AFTER_COMMIT, fallbackExecution=false); SET generation UUID mới; catch DataAccessException/log WARN. Không gọi DB, không rebuild availability |
| M23 | C7 | `public AvailabilityChangedEvent(UUID eventId)` | NEW, Lombok sinh | Mang ID event bị tác động, không serialize thành message RabbitMQ |
| M24 | C7 | `public UUID getEventId()` | NEW, Lombok sinh | Listener lấy eventId để đổi generation đúng event |
| M25 | C8 | `public OrderServiceImpl(OrderRepository orderRepository, IdempotencyRequestRepository idempotencyRequestRepository, OrderMapper orderMapper, EventSalesSnapshotRepository eventSalesSnapshotRepository, PurchaseLimitRepository purchaseLimitRepository, TicketTypeInventoryRepository ticketTypeInventoryRepository, SectorInventoryRepository sectorInventoryRepository, SeatInventoryRepository seatInventoryRepository, ReservationRepository reservationRepository, JsonMapper objectMapper, PromotionUsageRepository promotionUsageRepository, Validator validator, InboxMessageRepository inboxMessageRepository, OutboxMessageRepository outboxMessageRepository, OutboxMessageMapper outboxMessageMapper, PromotionInventoryRepository promotionInventoryRepository, ApplicationEventPublisher applicationEventPublisher)` | EXISTING, đổi constructor Lombok | Dependency cũ giữ thứ tự; thêm publisher ở cuối, không thêm cache-service dependency |
| M26 | C3 impl | `public AvailabilityServiceImpl(EventSalesSnapshotRepository eventSalesSnapshotRepository, TicketTypeInventoryRepository ticketTypeInventoryRepository, SectorInventoryRepository sectorInventoryRepository, SeatInventoryRepository seatInventoryRepository, PurchaseLimitRepository purchaseLimitRepository, PromotionInventoryRepository promotionInventoryRepository, InboxMessageRepository inboxMessageRepository, JsonMapper jsonMapper, Validator validator, ApplicationEventPublisher applicationEventPublisher)` | EXISTING, đổi constructor Lombok | Thêm publisher để báo config/gate thay đổi; interface không đổi |
| M27 | C3 impl | `public void synchronizeSalesConfiguration(Envelope<SalesConfiguration> message)` | REUSE, sửa thân | Giữ @Override/@Transactional; phát event ở nhánh áp dụng cấu hình mới |
| M28 | C3 impl | `public void processEventCancellation(Envelope<EventCancellation> message)` | REUSE, sửa thân | Giữ @Override/@Transactional; phát event khi gate mới/đổi CANCELLED |
| M29 | C8 | `private void reserveQuantityTickets(Order order, Reservation reservation, OrderSelection.QuantitySelection selection, List<TicketTypeInventory> lockedInventories)` | REUSE, sửa thân | Publish cuối phần cập nhật pool QUANTITY; giao dịch createOrder sở hữu commit |
| M30 | C8 | `private void reserveStandingTickets(Order order, Reservation reservation, OrderSelection.StandingSelection selection, SectorInventory lockedSector)` | REUSE, sửa thân | Publish sau giữ pool sector STANDING |
| M31 | C8 | `private void reserveSeatedTickets(Order order, Reservation reservation, OrderSelection.SeatedSelection selection, List<SeatInventory> lockedSeats)` | REUSE, sửa thân | Publish sau cập nhật ghế HELD |
| M32 | C8 | `private void updateQuantityInventoryForPaymentDecision(Order lockedOrder, Reservation storedReservation, List<TicketTypeInventory> lockedInventories, ReservationState targetState)` | REUSE, sửa thân | Các caller chốt/nhả HELD dùng chung; không thêm event ở caller |
| M33 | C8 | `private void updateStandingInventoryForPaymentDecision(Order lockedOrder, Reservation storedReservation, SectorInventory lockedSector, ReservationState targetState)` | REUSE, sửa thân | Như M32 cho STANDING |
| M34 | C8 | `private void updateSeatedInventoryForPaymentDecision(Order lockedOrder, Reservation storedReservation, List<SeatInventory> lockedSeats, ReservationState targetState)` | REUSE, sửa thân | Như M32 cho SEATED; PURCHASED/AVAILABLE/UNAVAILABLE được phản ánh |
| M35 | C8 | `private void releasePurchasedQuantityInventory(Order lockedOrder, Reservation storedReservation, List<TicketTypeInventory> lockedInventories)` | REUSE, sửa thân | Phát event sau trả counter purchased |
| M36 | C8 | `private void releasePurchasedStandingInventory(Order lockedOrder, Reservation storedReservation, SectorInventory lockedSector)` | REUSE, sửa thân | Phát event sau trả pool STANDING |
| M37 | C8 | `private void releasePurchasedSeatInventory(Order lockedOrder, Reservation storedReservation, List<SeatInventory> lockedSeats)` | REUSE, sửa thân | Phát event sau xử lý trả ghế COMMITTED theo guard hiện có |
| M38 | C5 | `void initializesGenerationWithoutReusingOldDataKeys()` | NEW | @Test; SET NX + đọc generation thực tế khi có race khởi tạo; metadata bị evict dùng UUID mới; vẫn thiếu generation thì không cache request |
| M39 | C5 | `void staleReaderCannotPopulateTheNewGeneration()` | NEW | @Test; mock điều phối reader G1 bị giữ, invalidate G2 rồi tiếp tục: reader chỉ SET data:G1, GET tiếp theo không dùng nó |
| M40 | C5 | `void invalidatesOnlyTheAffectedEventAndHandlesRedisFailure()` | NEW | @Test; listener SET đúng generationKey với UUID mới mỗi lần, không flush/KEYS/rebuild, Redis lỗi được log/catch |
| M41 | C9 | `void availabilityCacheInvalidationRunsAfterCommitAndNotAfterRollback()` | NEW | @Test, @Transactional(propagation=Propagation.NOT_SUPPORTED); dùng TransactionTemplate/transaction manager hiện có, publish trong transaction thật; verify chưa SET trước commit, có SET sau commit, không có SET ở rollback |
| M42 | C9 | `void inventoryWritersInvalidateAvailabilityForAllSalesModes()` | NEW | @Test, @Transactional(propagation=Propagation.NOT_SUPPORTED); dùng fixture/transaction hiện có qua create/cancel/expiry/payment/FAILED/event cancellation cho QUANTITY/STANDING/SEATED; không chỉ tự publish giả thay writer |
| M43 | C9 | `void salesConfigurationAndCancellationInvalidateAvailabilityCache()` | NEW | @Test, @Transactional(propagation=Propagation.NOT_SUPPORTED); config mới/gate CANCELLED đổi generation; old version/replay không phát lại |

M1–M21 giữ danh sách §8 và cập nhật logic test generation ở v0.3; không duyệt ngầm test helper mới. Chưa có Java nào được tạo/sửa.

### Nguồn và đối chiếu convention cho delta

- Đã đọc writer/helper, OutboxMessage/OutboxMessageRepository/BookingOutboxWorker, và @DataJpaTest/@Import của BookingPersistenceIT bằng `git show dev/son:<path>`. Outbox hiện relay payment/order-completed sang AMQP theo whitelist messageType; không nhét invalidation local vào relay đó hoặc tuyên bố sẵn có retry Redis.
- [Spring — Transaction-bound Events](https://docs.spring.io/spring-framework/reference/data-access/transaction/event.html): AFTER_COMMIT/fallbackExecution; áp dụng callback sau transaction thành công, không chuyển transaction nghiệp vụ.
- [ShopLink — ShopCacheInvalidationListener](https://github.com/yachtywen/ShopLink/blob/7c349a7c518179812a767d0ac6ef0d247b1903b8/src/main/java/com/hmdp/cache/ShopCacheInvalidationListener.java) và [event](https://github.com/yachtywen/ShopLink/blob/7c349a7c518179812a767d0ac6ef0d247b1903b8/src/main/java/com/hmdp/cache/ShopCacheInvalidationEvent.java): đã đọc source, học event mang ID + @TransactionalEventListener(AFTER_COMMIT); không mang broadcast, local cache hoặc field injection vào task.
- [Redis SET](https://redis.io/docs/latest/commands/set/): SET NX cho khởi tạo metadata, SET có expiration cho JSON. Cách ghép generation vào key là thiết kế đề xuất của task để xử lý stale refill, không tuyên bố sao chép sẵn từ Shop App/ShopLink.
- Convention: tái sử dụng các helper hiện có vì nhiều luồng ghi thật cùng dùng; không thêm kiểm tra validation/DB chỉ phục vụ cache; giữ constructor injection và @Value field; event class Lombok thay vì record; ghi chú tiếng Việt tại publish/AFTER_COMMIT để phân biệt đã save với đã commit.

## 12. RedisService — delta v0.4 / APPROVED cho BRAINSTORMING

User muốn có service Redis với method sẵn cho đọc/ghi/clear. Đã tra CodeGraph và danh sách Java baseline dev/son: chưa tìm thấy RedisService Booking để dùng lại. Thêm C10 phục vụ cả đường đọc cache và listener invalidation của C1, theo yêu cầu hiện tại; không suy rộng thành thư viện dùng chung giữa microservice.

| ID | Class/file | NEW hoặc EXISTING | Vai trò, field/annotation/API sinh | Mẫu Shop App hoặc ngoại lệ |
| --- | --- | --- | --- | --- |
| C10 | `redis/service/RedisService.java` | NEW | public class; @Service/@RequiredArgsConstructor; một field private final StringRedisTemplate stringRedisTemplate; Lombok sinh public constructor M44. Không getter/setter/builder/logger, không property riêng | ProductService cho injection; thu gọn operation từ RedisServiceImpl của mall §10, bỏ interface và API hash/list chưa cần |

| ID | Class | Constructor/method đầy đủ | NEW hoặc REUSE | Cách viết và lý do cần |
| --- | --- | --- | --- | --- |
| M44 | C10 | `public RedisService(StringRedisTemplate stringRedisTemplate)` | NEW, Lombok sinh | Một dependency template Spring hiện có |
| M45 | C10 | `public String get(String key)` | NEW | Gọi opsForValue().get(key); dùng đọc generation và JSON; trả null chỉ khi key không có, Redis lỗi đi ra caller |
| M46 | C10 | `public void set(String key, String value, Duration ttl)` | NEW | Gọi opsForValue().set(key,value,ttl); JSON dataKey cần SET cùng TTL, không tách expire |
| M47 | C10 | `public void set(String key, String value)` | NEW | Gọi opsForValue().set(key,value); generation metadata không TTL, listener dùng để đổi namespace |
| M48 | C10 | `public boolean setIfAbsent(String key, String value)` | NEW | Gọi SET NX thông qua template, Boolean.TRUE.equals kết quả; generation được khởi tạo nguyên tử; caller vẫn get lại generation thực tế. Task không dùng pipeline/Redis transaction |
| M49 | C10 | `public void clear(String key)` | NEW | Gọi stringRedisTemplate.delete(key); xóa đúng một key, không scan/flushDb. C1 dùng khi JSON cache lỗi/null để bỏ bản lỗi; xóa key đã mất vẫn hợp lệ |

M44–M49 public, không static, không khai báo checked throws, không method-level annotation. RedisService không nuốt DataAccessException hoặc đổi lỗi GET thành null, vì C1 cần phân biệt cache miss với mất kết nối để quyết định có thử ghi lại không. JSON mapping và log/fallback giữ ở C1; không truyền Object/Class<T> hoặc thêm generic decoder.

Trong M2, lỗi JSON/null: gọi clear(dataKey) trong khối try/catch DataAccessException riêng; clear lỗi thì bỏ bước ghi Redis, vẫn gọi service DB ngoài khối catch cache. Clear chỉ nhằm bỏ bản JSON lỗi; invalidation inventory M22 tiếp tục đổi generation bằng set không TTL. Không thay đổi cơ chế generation thành DEL một dataKey vì như vậy lại mở stale-refill race đã giải thích §3.

Đường gọi sau tách:

```text
Controller → AvailabilityCacheService
               → RedisService.get / set / setIfAbsent / clear
               → AvailabilityService.getAvailability → DB

Inventory commit → listener trong AvailabilityCacheService
                    → RedisService.set(generationKey, UUID mới)
```

Không thêm class test cho wrapper năm operation. C5 chạy RedisService thật cùng template mock để giữ các kiểm chứng có ý nghĩa: key/generation, TTL, miss vs lỗi kết nối, clear đúng key JSON lỗi và stale refill; cập nhật setup M7, test M13 và wiring C9 theo bảng. M1 đổi chữ ký; các constructor C3/C8 và điểm publish event không đổi do việc tách RedisService. Không có Java đã được tạo/sửa.

Đã ghi lựa chọn tổ chức Redis Booking vào convention tầng dự án Flash Ticket ngày 04/11; không sửa rule chung. Các primitive đối chiếu lại [ValueOperations](https://docs.spring.io/spring-data/redis/reference/api/java/org/springframework/data/redis/core/ValueOperations.html) và [RedisTemplate.delete](https://docs.spring.io/spring-data/redis/reference/api/java/org/springframework/data/redis/core/RedisTemplate.html). Giới hạn AFTER_COMMIT §3 vẫn còn và đã được nêu trong luồng user đồng ý; xác nhận này không bổ sung cơ chế retry/outbox bền.

**Trạng thái v0.4: APPROVED — BRAINSTORMING.** Nguồn: user “đồng ý” với luồng và “xác nhận” sau danh sách class/method ngày 04/11. Phạm vi: C1–C10/M1–M49 cùng annotation/API sinh/test; bao gồm delta RedisService C10/M44–M49, dependency M1, setup/test C5/C9 và clear JSON lỗi. Không còn mục chờ duyệt trong pha thiết kế. Chưa tự chuyển pha lập plan/thực thi, commit/push hoặc triển khai; thay đổi khai báo/cách viết ngoài danh sách phải trình delta.
