# Plan — API GET thông tin ghế theo eventId

- Ngày: 2026-11-01. Phiên bản: **v0.1 — WRITING-PLANS / APPROVED**.
- Task: [API GET thông tin ghế theo eventId](https://app.clickup.com/t/z8qgvc9wfq).
- Design: [spec v0.1](../specs/2026-11-01-booking-event-availability-design.md). Spec còn ghi BRAINSTORMING PENDING; nguồn duyệt plan là user trả lời **“xác nhận”** ngay sau câu hỏi duyệt C1–C9/M1–M17 và các lựa chọn kỹ thuật trong chat ngày 2026-11-01. Không tự đổi lịch sử phê duyệt spec.
- Checkout đã đối chiếu: `D:/DATN/flash-ticket-platform`, Git common directory `D:/DATN/flash-ticket-platform/.git`, branch `dev/son`, HEAD `5db324dbd22000b1379d041e443dc7103cb5bfe4`. Spec là file untracked có sẵn, giữ nguyên nội dung.
- Phạm vi thực thi: C1–C9, M1–M17 như spec, không thêm khai báo Java ngoài danh sách. Java/test đã triển khai; kết quả local và giới hạn ở §8–10. User đã giao commit/push dev/son và theo dõi deploy; checkpoint bàn giao ở §11. Không tự đổi ClickUp từ yêu cầu Git/deploy này.

## 1. Kết quả cần đạt

`GET /api/v1/events/{eventId}/availability` trả HTTP 200 với DTO Availability hiện có và `Cache-Control: no-store`. Public theo rule SecurityConfig đang có; bearer sai vẫn 401. Controller binding UUID sai trả 400; thiếu snapshot trả 503 CONFIG_NOT_READY; entity snapshot không PUBLISHED trả 409 EVENT_NOT_ON_SALE.

Các lựa chọn của spec đã được duyệt ở pha WRITING-PLANS: PUBLISHED được đọc cả trước mở/sau đóng bán; REPEATABLE_READ chỉ đọc; no-store. GET không giữ/nhả ghế, không tính lại trạng thái HELD theo deadline, không trả metadata sơ đồ/giá/promotion hoặc gọi Event Service. POST tạo order tiếp tục phân xử điều kiện mua và cạnh tranh như hiện tại.

Luồng mới: controller → AvailabilityService.getAvailability → AvailabilityServiceImpl.getAvailability → findById snapshot + findAllById inventory → constructor DTO. Snapshot, inventory phải được đọc trong cùng transaction service qua proxy.

## 2. Source và convention đã đối chiếu

- Shop App live source: `C:/Users/sondimid/IdeaProjects/shopapp-backend/shopapp-backend/shopapp/src/main/java/com/project/shopapp/controllers/OrderController.java` (GET + ResponseEntity, constructor injection); `services/IOrderService.java`; `services/impl/OrderService.java` (repository trực tiếp); `repositories/OrderRepository.java` (JpaRepository). Giữ tên/package đang có ở Booking; không sao chép catch rộng/return null hay thêm ModelMapper.
- Code đích: AvailabilityController mới có base mapping/dependency; AvailabilityService chưa có method đọc; AvailabilityServiceImpl có sẵn dependency inventory. DTO Availability cùng ba nested DTO có constructor/getter; không thêm Lombok/API sinh.
- `EventSalesSnapshotRepository.findById` kế thừa không gắn @Lock. Hai method riêng `findByEventId`/`findForBookingByEventId` có WRITE/READ lock phục vụ writer/booking, không dùng cho GET này. Ba repository inventory đã kế thừa findAllById.
- Writer `synchronizeSalesConfiguration` validate boundary, ghi snapshot và synchronizeTicketTypeInventory/synchronizeSectorInventory/synchronizeSeatInventory trong cùng transaction; bảo toàn counter và ghế đang giữ. `processEventCancellation` có thể chỉ đổi eventStatus trên entity, giữ JSON PUBLISHED cũ. GET phải đọc gate entity.
- Áp common.md: chỉ thêm use case đọc chưa tồn tại; không tạo mapper/validator/helper, không xác minh lại uniqueness/ownership/capacity đã được writer bảo đảm. Các transaction ghi cũ không bảo đảm nhiều SELECT ở READ_COMMITTED nhìn cùng thời điểm; dùng isolation cho vấn đề đọc thực tế này, không tách helper verify.
- Áp convention dự án: QUANTITY theo loại vé, STANDING pool sector chung, SEATED theo từng ghế; không reset counter, sửa locks/AUTO ACK, thêm SQL/index/schema/dependency hoặc thay consumer cũ. Dependency constructor injection hiện có giữ nguyên; task không thêm cấu hình @Value.
- Nguồn chính thức: [PostgreSQL Repeatable Read](https://www.postgresql.org/docs/17/transaction-iso.html#XACT-REPEATABLE-READ), [Spring Data JPA transaction ở service](https://docs.spring.io/spring-data/jpa/reference/jpa/transactions.html), [SimpleJpaRepository](https://github.com/spring-projects/spring-data-jpa/blob/main/spring-data-jpa/src/main/java/org/springframework/data/jpa/repository/support/SimpleJpaRepository.java). Áp transaction bao quanh nhiều repository call; readOnly riêng lẻ không tạo snapshot nhất quán hoặc cấm ghi tuyệt đối. Nguồn main dùng đối chiếu cách làm, không coi là đúng ref dependency đang cài.

## 3. Bảng class/file — WRITING-PLANS APPROVED

Production path dưới `services/booking-service/src/main/java/com/flashticket/booking_service/`; test path dưới `services/booking-service/src/test/java/com/flashticket/booking_service/`.

| ID | Class/file | Loại | Field, annotation, API sinh và thay đổi | Mẫu/ngoại lệ |
|---|---|---|---|---|
| C1 | availability/controller/AvailabilityController.java | EXISTING, sửa | Thêm M1; giữ @RestController/@RequestMapping/@RequiredArgsConstructor và final AvailabilityService; không thêm field/constructor | OrderController Shop App |
| C2 | availability/service/AvailabilityService.java | EXISTING, sửa | Thêm M2 vào interface hiện có; không thêm field/annotation | IOrderService Shop App |
| C3 | availability/service/impl/AvailabilityServiceImpl.java | EXISTING, sửa | Thêm M3; giữ @Service/@RequiredArgsConstructor, dependency/constructor hiện có; không sửa thân writer | OrderService Shop App; isolation ngoài mẫu |
| C4 | availability/controller/AvailabilityControllerTests.java | NEW, test | Package-private; @WebMvcTest(AvailabilityController.class); @Import({SecurityConfig.class, BookingJsonConfig.class, GlobalExceptionHandler.class}); @Autowired MockMvc mvc; @MockitoBean AvailabilityService availabilityService; @MockitoBean JwtDecoder jwtDecoder. Constructor mặc định M4; không Lombok/API sinh khác | OrderControllerTests Booking; NGOÀI MẪU Shop App |
| C5 | persistence/BookingPersistenceIT.java | EXISTING, sửa test | Thêm M10–M17; dùng salesService proxy/JdbcTemplate/transactionManager/PostgreSQL17 và fixture hiện có; không thêm field/config/@Bean/type | BookingPersistenceIT; NGOÀI MẪU Shop App |
| C6 | dto/response/Availability.java + TicketTypeAvailability/SectorAvailability/SeatAvailability | EXISTING, REUSE | Giữ @Getter/@AllArgsConstructor và mọi field; gọi constructor/getter có sẵn, không thay DTO | B13, response có sẵn |
| C7 | availability/repository/EventSalesSnapshotRepository.java | EXISTING, REUSE | findById(UUID) kế thừa; không redeclare/query/@Lock mới | JpaRepository Shop App |
| C8 | availability/repository/TicketTypeInventoryRepository.java, SectorInventoryRepository.java, SeatInventoryRepository.java | EXISTING, REUSE | findAllById(Iterable<UUID>) kế thừa; không thêm method/query | JpaRepository Shop App |
| C9 | config/SecurityConfig.java; exception/BusinessException.java, ErrorCode.java, GlobalExceptionHandler.java | EXISTING, REUSE | Giữ public rule, UUID binding và Problem/error mapping; không sửa class/method/enum | Hạ tầng Booking, NGOÀI MẪU |

## 4. Bảng constructor/method — WRITING-PLANS APPROVED

M1–M3 là ba khai báo production mới trong class/interface cũ; không tách logic writer. M4 là constructor mặc định của class test mới; M5–M17 là 13 method test. Không có private helper, constructor production, nested/local/anonymous type, callback khai báo riêng hoặc method @Bean mới. Dùng callback API/lambda nội bộ theo fixture đồng bộ đã có, không tạo type/helper để né gate.

| ID | Class | Chữ ký đầy đủ | Loại | Cách viết/trách nhiệm |
|---|---|---|---|---|
| M1 | C1 | public ResponseEntity<Availability> getAvailability(@PathVariable UUID eventId) | NEW | @GetMapping không lặp base path; gọi M2, ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(...); mẫu controller Shop App, header ngoài mẫu |
| M2 | C2 | public Availability getAvailability(UUID eventId) | NEW | Interface use case cho controller; public ngầm định |
| M3 | C3 | public Availability getAvailability(UUID eventId) | NEW | @Override, @Transactional(readOnly=true, isolation=Isolation.REPEATABLE_READ); query/group/map trực tiếp §5; NGOÀI MẪU isolation |
| M4 | C4 | AvailabilityControllerTests() | NEW, mặc định | Package-private, không khai báo constructor riêng hoặc Lombok |
| M5 | C4 | void returnsPublicAvailabilityWithoutJwt() throws Exception | NEW | @Test; public 200, payload B13, chuỗi số/zero/mảng, no-store và không lộ field |
| M6 | C4 | void rejectsMalformedEventId() throws Exception | NEW | @Test; 400 INVALID_REQUEST, verifyNoInteractions(service) |
| M7 | C4 | void returnsConfigNotReadyProblem() throws Exception | NEW | @Test; 503 CONFIG_NOT_READY qua advice thật |
| M8 | C4 | void returnsEventNotOnSaleProblem() throws Exception | NEW | @Test; 409 EVENT_NOT_ON_SALE qua advice thật |
| M9 | C4 | void rejectsInvalidBearerToken() throws Exception | NEW | @Test; JwtDecoder mock ném JwtException/BadJwtException, bearer thật qua filter trả 401; không dùng jwt() postprocessor cho ca invalid |
| M10 | C5 | void availabilityReturnsQuantityCounters() | NEW | @Test, @Transactional(propagation=Propagation.NOT_SUPPORTED); capacity-held-purchased và zero |
| M11 | C5 | void availabilityUsesSharedStandingPool() | NEW | Cùng annotation M10; nhiều type cùng sector nhận cùng số còn |
| M12 | C5 | void availabilityCountsAvailableSeatsInMixedSectors() | NEW | Cùng annotation M10; mixed standing/seated, bốn state, hidden và type hết ghế trả zero |
| M13 | C5 | void availabilityExcludesRetiredAndOtherEventResources() | NEW | Cùng annotation M10; chỉ current IDs, thứ tự config, không lẫn event khác |
| M14 | C5 | void availabilityDoesNotChangePersistentState() | NEW | Cùng annotation M10; HELD quá hạn không tự release; so dữ liệu trước/sau |
| M15 | C5 | void availabilityRejectsMissingOrUnpublishedSnapshots() | NEW | Cùng annotation M10; thiếu snapshot và mọi trạng thái ngoài PUBLISHED; gate CANCELLED với JSON cũ |
| M16 | C5 | void availabilityRemainsReadableOutsideSaleWindow() | NEW | Cùng annotation M10; trước mở/sau đóng vẫn đọc PUBLISHED; createOrder vẫn bị guard |
| M17 | C5 | void availabilityReadsOneCommittedSnapshotDuringConcurrentChanges() throws Exception | NEW | Cùng annotation M10; điều phối GET và writer commit bằng lock/latch thật, không hook production; §6 |

## 5. Các bước triển khai và kết quả kiểm được

### P1 — Khai báo use case và handler GET (C1–C3, M1–M3)

Danh sách và lựa chọn của plan đã được user xác nhận ở pha WRITING-PLANS; execute phải có gate riêng trước viết Java.

1. Thêm import Availability/UUID và M2 vào interface. Trong C1 thêm M1, @GetMapping/@PathVariable/ResponseEntity/CacheControl; giữ base mapping và constructor.
2. Thêm M3 trong C3; không sửa annotation transaction trên các method writer. Lấy asOf bằng Instant.now() trước SELECT snapshot đầu tiên; findById(eventId).orElseThrow(CONFIG_NOT_READY), rồi gate entity PUBLISHED. Không thêm Validator.validate(snapshot), helper check allocation, query lock, check sale window hoặc catch rộng.
3. Dùng configurationSnapshot để lấy current ID theo mode. QUANTITY đọc ticket type IDs bằng một findAllById; SEAT_MAP đọc inventory STANDING theo sector ID và ghế theo current seat IDs, bỏ query nhóm rỗng. Không gọi findAll toàn bảng/findById từng seat. Map kết quả theo ID; map sector kind từ cấu hình.
4. QUANTITY: mỗi type lấy configuredCapacity-held-purchased, dùng Long.toString; sectors/seats rỗng. STANDING: availableBySectorId từ counter sector; mỗi type tham chiếu sectorId nhận cùng giá trị. SEATED: lặp danh sách seat config một lần, lấy state inventory; chỉ AVAILABLE tăng đếm theo type và sector, khởi tạo count 0 để không bỏ type/sector hết vé. Trả seatId/state cho đủ current seats theo thứ tự config.
5. Tạo ba list DTO theo thứ tự ticketTypes/sectors/seats của config, rồi constructor Availability với sourceConfigVersion entity dạng chuỗi, eventId và asOf. Không trả tên/giá/label/promotion/holder. Không ép số âm thành 0 để che sai dữ liệu, không trả entity trực tiếp.
6. Không phát sinh save/saveAll/setter entity, flush, ghi inbox/outbox hoặc gọi worker. Các method synchronize*/cancel/accept/expiry cũ tái sử dụng nguyên trạng bởi luồng của chúng, GET không gọi chúng.

Kết quả kiểm P1: compile qua test build ở P2; chỉ ba khai báo mới production. GET không làm thay đổi phương thức/khóa hiện có; không đổi security để mở public vì rule đã tồn tại.

### P2 — Kiểm HTTP và security (C4/C6/C9, M4–M9)

Tạo đúng test class C4 theo bảng; dùng nested DTO constructor hiện có để stub service. Viết năm test M5–M9, không test helper mới. M5 có thể lặp các payload đại diện và zero trong cùng method; assert field chính, mảng rỗng, no-store và không có reservationId/buyerSubject/configurationSnapshot. M6–M8 dùng handler thật; M9 gửi Authorization header, decoder từ chối và service không được gọi.

Chạy từ `services/booking-service` với JDK23: `rtk mvn -q -Dtest=AvailabilityControllerTests test`. Mong đợi **5 test mới pass**, đúng 200/400/401/409/503 và header. Đây là test MVC với service mock, chưa chứng minh tính toán/transaction DB.

### P3 — Kiểm tính toán và không ghi DB (C5–C8, M10–M16)

Dùng PostgreSQL17 Testcontainers của BookingPersistenceIT, schema chuẩn và fixture đã có: seedOrderCreationConfiguration, with, createOrderRequest, captureOrderCreationState. Commit fixture bằng service proxy hoặc TransactionTemplate riêng; gọi salesService.getAvailability ngoài outer test transaction. Không thêm field/container/JDBC wrapper/helper; các fixture đa sector/multi-type dựng inline trong từng method.

- M10: QUANTITY qua tạo order và accept hợp lệ để có held/purchased; đối chiếu SQL independent, capacity bằng tổng held+purchased trả "0".
- M11: một standing sector, ít nhất hai type; tạo reservation cho một type, cả hai type cùng giảm theo pool; sector không bằng tổng hai type.
- M12: cấu hình mixed bằng envelope thật, ghế AVAILABLE/HELD/PURCHASED/UNAVAILABLE tạo từ writer/order/payment phù hợp. Assert count per type/sector, hidden không được tính; mảng seats đủ bốn state.
- M13: hai event, cấu hình mới loại tài nguyên rảnh của event thứ nhất nhưng DB còn row lịch sử; chỉ current IDs xuất hiện. Thứ tự trả theo config được cố tình khác thứ tự ID/sort DB; không cần giả lập repository đảo thứ tự.
- M14: fixture order HELD đến hạn nhưng worker tắt theo test config. Chụp SQL các bảng Booking liên quan theo khóa ổn định trước GET và so sau GET: snapshot row_version/JSON, inventory, order, reservation/allocation, limits, promotion, idempotency/inbox/outbox giữ nguyên. Test không dựa vào annotation readOnly làm bằng chứng không ghi.
- M15: unknown UUID 503; lặp DRAFT/PENDING_APPROVAL/APPROVED/CANCELLED 409. Ca hủy dùng processEventCancellation để entity gate CANCELLED trong khi JSON thương mại còn PUBLISHED, chứng minh M3 không nhìn nhầm JSON.
- M16: PUBLISHED trước mở/sau đóng, GET 200 nhưng createOrder bị EVENT_NOT_ON_SALE. Không sửa production sale window; dùng fixture hợp lệ.

Mỗi method cleanup inline trong finally theo IT đang có: JdbcTemplate thực hiện TRUNCATE snapshot/inbox/outbox CASCADE trên DB Testcontainers riêng, sau khi mọi thread kết thúc; không xóa dữ liệu119. Không có helper clearBookingData hiện hữu và không đề xuất tạo helper cleanup mới. Các phép sửa fixture có kiểm soát chỉ là dữ liệu test, không thay API hoặc bất biến production.

### P4 — Chứng minh snapshot nhất quán dưới cập nhật đồng thời (C5, M17)

Tái dùng TransactionTemplate/transactionManager/JdbcTemplate, CountDownLatch, executor, Awaitility và pg_blocking_pids đang có trong IT. Không cần MockitoSpyBean mới, BeanPostProcessor, datasource proxy, trigger hoặc type/hook production.

1. Seed và commit QUANTITY version1/capacity10. Chuẩn bị version2/capacity20 cùng IDs qua with hiện có; giá/owner/mode không đổi.
2. Transaction writer ở thread riêng dùng JDBC `LOCK TABLE booking_schema.tbl_ticket_type_inventory IN ACCESS EXCLUSIVE MODE`, lấy pg_backend_pid, báo latch đã giữ lock. Tên bảng là canonical `ticket_type_inventory` từ `docs/architecture/data/postgres/20-booking.sql` sau script `services/99-table-prefix.sql`. Chỉ dùng table lock trong DB test để chặn query inventory ở một điểm xác định; không phải cơ chế production.
3. Thread khác gọi salesService.getAvailability qua proxy, không bọc TransactionTemplate bên ngoài khiến isolation bị kế thừa sai. GET đọc snapshot version1 rồi chờ SELECT inventory. Awaitility kiểm session chờ lock có writer PID trong pg_blocking_pids; không dò query text của session khác hoặc sleep ngẫu nhiên.
4. Khi biết GET đang chờ inventory, cho writer gọi synchronizeSalesConfiguration(version2) trong transaction writer đang giữ table lock; đặt local lock_timeout hữu hạn cho writer. Writer phải update snapshot và commit được trong lúc GET chưa xong. Nếu GET khóa snapshot bằng READ/WRITE, writer sẽ timeout và test fail rõ, không treo.
5. Writer commit trả table lock; GET tiếp tục và phải trả version1 + available10. Gọi GET mới sau đó trả version2 + available20. Ghép version1/capacity20 phải fail; kết quả cũ trong GET đang chạy là nhất quán theo REPEATABLE_READ, không hứa dữ liệu mới nhất tại lúc response.
6. Trong finally nhả latch kể cả assertion lỗi; đợi/huỷ future có timeout, đóng executor và cleanup sau khi transaction kết thúc. Dùng đúng tên bảng từ canonical DDL; nếu cơ chế điều phối đòi khai báo mới ngoài M17, trình delta trước khi thêm.

Kết quả: 1 test thật kiểm writer không bị GET giữ row lock snapshot và response không trộn hai commit. Table-level AccessShare vốn có của SELECT vẫn tồn tại; không khẳng định PostgreSQL đọc hoàn toàn không có lock.

### P5 — Verify và cập nhật bằng chứng (toàn bộ item, không khai báo Java mới)

1. Focused sau P3/P4: `rtk mvn -q -P integration '-Dit.test=BookingPersistenceIT#availability*' verify`. Lệnh vẫn chạy unit và các check Maven; chọn 8 IT mới bằng prefix availability. Cấu hình RABBITMQ_HOST/PORT/USERNAME/PASSWORD cho contextLoads hiện có trong file env của môi trường test cô lập, nạp bằng env-file theo convention hiện hành; không gán biến tạm/đối số để bù cấu hình và không ghi secret vào lệnh/tài liệu.
2. Khi focused pass, chạy một lần đầy đủ `rtk mvn -q -P integration '-Dit.test=BookingPersistenceIT' verify` với cùng môi trường. Mong đợi mọi unit/IT, format/enforcer/SpotBugs pass; không skip lỗi để đạt xanh. Baseline đã có 383 unit +129 IT; nếu không có thay đổi khác, dự kiến thêm 5 unit +8 IT, chỉ ghi tổng thật từ report sau chạy.
3. Rà diff: production chỉ C1–C3/M1–M3; DTO/repository/security/writer không đổi. `rtk git diff --check`; không Java helper/annotation/field/dependency phát sinh ngoài bảng. Test mới phải phân biệt được triển khai sai count/đọc trộn snapshot, không chỉ kiểm method gọi nhau.
4. Cập nhật `services/booking-service/TESTING.md` bằng lệnh, JDK/môi trường, kết quả và giới hạn thật; writing-docs sau execute theo yêu cầu/quy trình. Không tự gán kết quả unit/IT thành HTTP runtime, deploy119 hoặc E2E Payment/Ticket. Không yêu cầu chạy thêm 10 curl nếu user chưa giao; runtime test khi được yêu cầu dùng handler thật, không mock service.

## 6. Bàn giao và gate

- Danh sách của plan **không có delta khai báo so với spec**: C1–C9, M1–M17; annotation/field/API sinh/ngoại lệ kèm theo ở §3–4. Trạng thái thực thi và bằng chứng ở §8.
- **WRITING-PLANS: APPROVED**. Nguồn: user trả lời **“xác nhận”** ngay sau câu hỏi duyệt plan trong chat ngày 2026-11-01; áp dụng toàn bộ C1–C9/M1–M17 và annotation/field/API sinh/ngoại lệ trong bảng.
- Đã duyệt gate PUBLISHED/409, đọc ngoài sale window, no-store, REPEATABLE_READ và cách test M17. Không ghi BRAINSTORMING đã duyệt hồi tố; nguồn xác nhận EXECUTING-PLANS ở §7.
- Trước thực thi, user gọi executing-plans và skill đó trình danh sách thực thi để xác nhận riêng. Nếu phát sinh helper/test hook/method/type mới, dừng phần phụ thuộc và trình delta cụ thể; không ghi “thêm helper nếu cần”.
- Quyền duyệt WRITING-PLANS ban đầu chỉ chốt tài liệu; thực thi Java theo nguồn xác nhận riêng ở §7. Không tự đổi server, deadline/assignee/status ClickUp hoặc push.

## 7. Gate thực thi — EXECUTING-PLANS APPROVED

- User yêu cầu “triển khai” sau khi plan được duyệt ngày 2026-11-01. Đã đọc lại skill executing-plans, reference chung, convention và source controller/interface/DTO/repository; HEAD vẫn là 5db324d, chưa có thay đổi Java.
- Danh sách thực thi dùng nguyên bảng §3–4: C1–C9, M1–M17; không có delta. Production thêm M1–M3 trong ba file hiện có; test tạo C4/M4–M9 và thêm M10–M17 vào C5. C6–C9 dùng lại nguyên trạng.
- Giữ constructor injection/Lombok hiện có; ngoại lệ gồm transaction readOnly/REPEATABLE_READ, no-store và hạ tầng MVC/PostgreSQL concurrency test theo plan. Không thêm validator/helper hoặc kiểm lại bất biến đã được writer bảo đảm.
- **EXECUTING-PLANS: APPROVED** — nguồn: user trả lời **“triển khai cho tôi”** ngay sau câu hỏi xác nhận C1–C9/M1–M17 cho pha EXECUTING-PLANS trong chat ngày 2026-11-01. Phạm vi gồm annotation/field/API sinh/ngoại lệ trong bảng, không có delta. Các xác nhận trước vẫn thuộc WRITING-PLANS.

## 8. Tiến độ và bằng chứng thực thi — 2026-11-01

- P1 complete: M1–M3 đã thêm đúng ba file production; read-only REPEATABLE_READ, gate entity PUBLISHED và no-store; mapping QUANTITY/STANDING/SEATED theo config hiện hành. Không thêm helper/DTO/query/field/annotation ngoài bảng. Đối chiếu source xác nhận toàn bộ thân processEventCancellation và synchronize* trong service giữ nguyên.
- P2 complete: class C4 có đúng 5 test M5–M9, constructor mặc định M4; `rtk mvn -q -Dtest=AvailabilityControllerTests test` exit0, 5/5 pass.
- P3/P4 complete: đúng 8 method M10–M17, không thêm field/helper/type vào IT. Focused verify exit0: 388 unit +8 IT, zero failure/error/skip; SpotBugs0. M17 điều phối table lock/latch thật: reader cũ trả version1/10, reader sau commit trả version2/20, writer không bị reader khóa hàng snapshot.
- **P5 / full verify chưa đạt:** bốn lượt full trên Windows đều có lỗi ở test cũ; lượt Windows cuối exit1, 388 unit pass, 137 IT có 2 failure/0 error/0 skip ở deliversAcceptPaymentDecisionThroughRealRabbitMq và relaysOrderCompletedThroughRabbitMq. Lượt Windows thứ ba có 1 failure +2 error. Tám test availability mới đều pass. Relay cũ deliversAcceptPaymentDecisionThroughRealRabbitMq có lần receive null sau một lượt worker; log có outbox INSERT và query chọn due message nhưng chưa có query khóa row publish. Lượt thứ hai checksPaymentDeadlineAfterInventoryLockWait timeout4s. Relay/deadline đều pass khi chạy riêng nhưng paired recheck tái hiện deadline timeout; không coi isolated pass là full pass.
- Lượt Windows thứ ba thêm orders_check5 ở protectsNewSeatReservationFromStaleIssuanceFailure và arbitratesExpiryAgainstAcceptPaymentAfterLockWait: failed row có payment_frozen_at nhỏ hơn created_at khoảng0.582/0.611s. Đây là bằng chứng bất thường thời gian trong đường DB-clock cũ; nguyên nhân clock/runtime cụ thể chưa chốt. Giữ nguyên constraint, production writer và toàn bộ thân test cũ, không tăng timeout/bỏ assert hoặc thêm Java ngoài danh sách để ép xanh. Chưa đánh EXECUTING-PLANS COMPLETE vì còn P5.
- Lượt focused đầu bị lỗi môi trường: Docker Desktop không khởi động do stale runtime socket, sau phục hồi broker disposable đầu tiên lỗi quyền cookie. Broker dựng lại với tmpfs cho dữ liệu test và xác nhận ping, không sửa production/test Java để bỏ kiểm. Sau full lỗi, đã thử restart riêng docker-desktop WSL (không terminate Ubuntu), gặp lại stale socket và khôi phục engine bằng cách giữ thư mục runtime lỗi thành backup. Dữ liệu/volume cũ không bị xóa; runtime Docker cũ lưu tại `C:/Users/sondimid/AppData/Local/Docker/run.before-availability-20261101` và `run.before-availability-restart-20261101` cùng thư mục cha.
- Full verify trên Linux Maven3.9.9/Temurin23.0.2+7 trong container: exit1; **388 unit pass, 137 IT có 0 failure +1 error/0 skip**, SpotBugs0. Đủ 8 test availability mới pass, hai relay cũ pass. Lỗi duy nhất là releasesExpiredReservationWhenPaymentArrivesLate tại preparePurchaseSnapshot (dòng4235): orders_check5, created_at=2026-11-01T15:05:14.201712Z nhưng payment_frozen_at=2026-11-01T15:05:13.736130Z (sớm hơn0.465582s). createOrder gán created_at từ OrderRepository.currentDatabaseTime tại dòng1433/1452; preparePurchaseSnapshot lấy cùng method tại dòng1223/1276; query là select clock_timestamp(). Lỗi vẫn xảy ra khi JVM và DB cùng Linux; chưa xác định tác nhân làm thời gian lùi, không kết luận lỗi riêng Windows hoặc thay đổi giờ hệ thống. Probe DB độc lập 600 rồi4.800 mẫu không thấy clock lùi trong cửa sổ đo; không phủ nhận failed-row evidence. Không lặp full verify tiếp khi chưa có thay đổi có căn cứ.
- Các lượt xác minh đã dùng biến process cho broker disposable; cách cấp cấu hình đó chưa phù hợp convention env-file hiện hành. Đã sửa hướng dẫn P5; lượt chạy sau phải khai báo trong file env test và nạp bằng env-file. Không thay file env runtime hoặc nguồn secret của dự án.
- Rà cuối: GET chỉ thêm M1–M3, các writer hiện có giữ nguyên; DTO/repository/security không đổi, không thêm verify/helper hoặc dependency. Không đánh EXECUTING-PLANS COMPLETE khi P5 chưa đạt. Dọn broker disposable do task tạo sau khi kết thúc kiểm; Docker engine tiếp tục chạy. .env.example có thay đổi ngoài phạm vi task, giữ nguyên.
- Log/report cục bộ trong target (ignored): availability-focused-verify.log, availability-focused-it-evidence.xml, availability-full-first-failure.log/.xml, availability-relay-recheck-verify.log, availability-full-verify.log, availability-windows-last-*.xml/.log, availability-linux-full-verify.log và failsafe/surefire/SpotBugs XML của lượt Linux. Một XML BookingPersistenceIT cũ còn trong surefire-reports có2 test; tổng unit388 chỉ tính22 report của JVM23.0.2+7, không cộng artifact cũ này. Không seed DB119, không gửi message vào queue nghiệp vụ119, Tại thời điểm full Maven chưa kiểm HTTP runtime; kết quả curl bổ sung ở §9. Chưa deploy hoặc kiểm E2E Payment/Ticket cho task GET này.

## 9. User test HTTP/curl bổ sung — 2026-11-01

- Nguồn phạm vi: user yêu cầu **“test curl chuẩn cho tôi”**. Thêm harness Python và template scripts/.env.example; không phát sinh Java hoặc đổi production code. Chạy từ root bằng `rtk proxy python -X utf8 services/booking-service/scripts/test-event-availability-curl.py`.
- **20/20 case PASS, exit0**, 22:18:58–22:21:19 +07: 36 request nghiệp vụ bằng curl thật, gồm22 GET availability. HTTP200 x19,201 x8,400 x1,401 x1,409 x6,503 x1; không cộng readiness/token/broker management vào36. Chi tiết A01–A20 trong services/booking-service/TESTING.md và target/availability-curl/results.json (ignored).
- Dùng production JAR hiện tại với PostgreSQL17/Rabbit4.3.6/Redis7.4.11/Keycloak26.8 thật; so ba class availability trong JAR khớp bytecode build hiện tại. Seed snapshot qua consumer, tạo/giữ/apply voucher/freeze/hủy qua HTTP và JWT thật, commit thanh toán/hủy event qua consumer thật. SQL chỉ dựng schema/role/index và kiểm độc lập; không sửa tay trạng thái inventory/order.
- Bao phủ quantity mới/held/purchased/sold out; standing chung pool; mixed seated/standing và hidden; buyer cancel; entity CANCELLED với JSON PUBLISHED; ba trạng thái chưa publish; đọc trước/sau giờ bán; config version/order/retired/other-event; GET lặp không ghi DB; HELD quá hạn không tự release; restart bật worker expiry thật rồi GET phản ánh release và trả voucher.
- Runtime value đi từ env-file test dưới target/availability-curl; tên/config mẫu tại services/booking-service/scripts/.env.example. Không nạp root .env, không dùng env override/JVM property để bù cấu hình. Config Server/Eureka tắt, nạp production booking-service.yaml trực tiếp, schema canonical và ddl-auto=none; hold30s, expiry tắt khi staging rồi bật qua env-file lúc restart. Các worker khác giữ config production.
- Đã dọn đúng năm container/volume tạm, network và file chứa credential/realm; report/log còn local, Docker vẫn chạy. Không tác động119, không deploy/push/update ClickUp. **P5/full regression vẫn chưa đạt** theo §8; curl xanh không thay kết quả full Maven và chưa chốt tác nhân clock lùi.

## 10. Sửa đồng bộ giờ local và retest theo yêu cầu user — 2026-11-01

- Nguồn: user yêu cầu “trước mắt hãy fix cho tôi ở máy win local này rồi test lại case đó”. Không có delta Java hoặc đổi timeout/constraint/test body; không push/deploy/update ClickUp.
- Đã sao lưu W32Time registry/start mode/triggers và full-run XML cũ trong target/windows-time-fix. Bật W32Time, đặt Automatic (Delayed Start), giữ peer time.windows.com,0x9; resync xác nhận Leap Indicator0/Last Sync Error0/peer Active. Độ lệch NTP đo ngoài giảm từ khoảng1.29s xuống vài ms.
- Xác định Ubuntu24.04.4 có systemd-timesyncd active/enabled. Tắt và disable theo hướng implicit Hyper-V do Canonical hướng dẫn; lưu trạng thái trước/sau. Gọi systemctl qua namespace Ubuntu đã xác minh từ WSL system distro vì CLI Ubuntu bình thường lỗi/timeout; không reset/restart distro hoặc đổi WSL/Docker backend.
- Case releasesExpiredReservationWhenPaymentArrivesLate **PASS6/6 lượt**, zero failure/error/skip: Windows3 +Linux1 sau sửa Windows, thêm Windows1 +Linux1 sau tắt NTP riêng Ubuntu. Mỗi lượt giữ nguyên test với ba mode QUANTITY/STANDING/SEATED. Chỉ chạy goals failsafe:integration-test/failsafe:verify trên compiled tests, không nhận là đã rerun full137IT.
- **Chưa sửa triệt để clock stepping WSL:** Windows probe300s không lùi; Linux vẫn lùi nhỏ. Sau tắt timesyncd, probe180s/161,933 mẫu có6 lần lùi, delta nhỏ nhất khoảng-17.738ms; correction cuối cửa sổ còn khoảng-1.79ms. Trace95s thấy comm=init gọi sys_settimeofday thành công khoảng30s/lần; chưa xác định executable cụ thể. Có tái hiện độc lập không chạy PostgreSQL; không suy chắc actor của lỗi0.465582s cũ.
- Đã dọn container/probe và trace instance, unmount tracefs do task mount; backup, XML/log/report còn local. TESTING.md cập nhật kết quả và giới hạn. **P5/full regression vẫn mở**; không đổi gate thành COMPLETE chỉ vì focused retest pass.

## 11. Bàn giao dev/son theo yêu cầu user — 2026-11-01

- Nguồn quyền: user yêu cầu **“commit rồi push + theo dõi deploy xem ok chưa”**. Phạm vi hiện tại là code/test/docs của availability → origin/dev/son → kiểm CI/deploy đúng SHA. Không tự ghi ClickUp hoặc bắt đầu task mới.
- Đã fetch và xác nhận HEAD khớp origin/dev/son tại5db324d trước bàn giao; index ban đầu rỗng. Loại thay đổi root .env.example ngoài task, mọi target log/credential/fixture và cấu hình giờ máy cá nhân khỏi commit.
- Đã đọc lại source hiện hành, convention và mẫu Shop App; bổ sung bản giải thích tại explanations/2026-11-01-booking-event-availability-impl-explained.md. Không phát sinh Java hoặc sửa writer/DTO/repository/security ngoài phạm vi đã duyệt.
- Dùng bằng chứng local đã ghi; full verification sẽ chạy bắt buộc trong job Verify Booking của Deploy Booking to119 trước build/publish/deploy. Không bỏ test hoặc đổi assertion để vượt pipeline. P5 và deploy chỉ được kết luận từ run đúng SHA; SHA/run/kết quả sau push được lưu trong checkpoint bàn giao và chat, không tạo commit vòng để ghi SHA của chính commit.
