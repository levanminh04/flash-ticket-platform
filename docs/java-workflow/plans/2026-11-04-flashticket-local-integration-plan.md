# FlashTicket Local Integration Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Hoàn tất flow buyer từ frontend local đến VNPay Sandbox và vé/QR, có đọc lại đơn/lịch sử và bằng chứng đúng bộ service local dùng hạ tầng119.

**Architecture:** Giữ năm owner và Saga Payment hiện có. Hợp nhất source chọn lọc, giữ patch tương thích, thêm GET order/list tại Booking, nối FE và kiểm chứng trên một phiên shared-runtime đã phối hợp. Config/Discovery/Gateway và năm business service chạy local; PostgreSQL/MongoDB/Redis/RabbitMQ/Keycloak dùng119 theo spec A.

**Tech Stack:** React18/Vite5/TypeScript, Keycloak JS26.2.5, Spring Boot4.1.1/Spring Cloud, JPA/MapStruct/Lombok, PostgreSQL/MongoDB/Redis/RabbitMQ, Docker Compose và Playwright CLI hiện có. Booking dùng Java23; Gateway/Payment và Event/Ticket nguồn được chọn dùng Java21; các module khác theo POM thực tế.

**Spec:** [2026-11-04-flashticket-local-integration-design.md](../specs/2026-11-04-flashticket-local-integration-design.md), v0.1; đọc cả spec và plan trước khi thực thi.

## Global Constraints

- “FE và các ứng dụng local; hạ tầng119” là phương án A user chọn; không đổi sang deploy toàn bộ backend119.
- “Root `.env` chứa cấu hình được sử dụng; `.env.example` chỉ chứa tên biến và ví dụ không bí mật.” Không bù cấu hình ứng dụng bằng CLI/JVM/biến tạm, không in rendered compose có secret.
- “Vite ở127.0.0.1:5173; Gateway qua relay127.0.0.1:18081”; relay chỉ publish loopback Windows.
- “page size 1..100”, mặc định20; thứ tự “createdAt DESC, id DESC”; response `{items:OrderView[],nextCursor:string|null}`; cursor là orderId cuối trang.
- “Không COUNT tổng, không offset trang sâu, không fetch join collection cùng pagination.” GET không ghi/freeze/expire/release hoặc phát message; response no-store, ownership từ JWT.
- “Giữ baseline JWT theo convention: issuer discovery, chữ ký, expiry, role và ownership; không thêm audience/JWKS override.” Fixture JWKS test được giữ.
- “Không thiết kế thêm Saga, queue nghiệp vụ, hệ thống refund, payout, reconciliation”; giữ worker outbox/Saga Payment baseline, không mang worker tài chính mới vào DB chung bằng merge ngầm.
- “Không xóa/purge DLQ, reset trạng thái order/Saga hoặc publish lại hàng loạt”; không đổi type/payload outbox đã commit để vượt guard.
- “Không giấu helper/test setup mới trong lambda/anonymous class.” Java delta chỉ C1–C9/M1–M10/U1–U5; mọi khai báo/signature/annotation phát sinh phải trình delta.
- “health và restart count ổn định ít nhất5 phút”; đủ5 business instances và Gateway gọi được chúng; UP/consumer count không thay bằng chứng E2E.
- “Không ký IPN giả rồi gắn nhãn Sandbox thật”; các ca không điều khiển được ở sandbox ghi đúng lớp kiểm chứng, không tự nâng thành E2E PASS.
- Một plan chung vì source, topology, runtime và FE dùng cùng contract; các task dưới có kết quả kiểm được riêng. Không chia thành các plan độc lập làm mất ràng buộc này.
- Chỉ viết plan trong lượt này. Không stage/commit/push, đổi env/runtime/broker, gửi ClickUp hoặc chạy giao dịch119. Commit checkpoints bên dưới dành cho pha thực thi khi có quyền tương ứng; không phải yêu cầu commit tài liệu ngay.

## Review Focus

1. Cursor thuộc buyer khác/anchor mất và nhiều đơn trùng createdAt: không rò dữ liệu, không lặp/bỏ sót đơn khi insert mới; U3–U5/Task2.
2. Response GET/apply/cancel cũ đến sau logout hoặc đổi buyer/order/gateway: không khôi phục dữ liệu/capability của phiên cũ; browser assertions Task5.
3. Ticket pending outbox còn type cũ khi đổi cấu hình: không bị tự viết lại hoặc bị worker phiên khác consume trước khi chuyển đổi xong; kiểm transition Task4.
4. Payment thu thành công nhưng Ticket/Booking trả chậm, return đến trước IPN: không phát lại vé/thu lại hoặc hoàn vì timeout; compatibility tests Task1 và Task8.
5. Token refresh lỗi hoặc callback giả kèm status00: không gửi token cũ, không hiển thị đã thanh toán từ URL; fixture Task6 và live Task8.

---

## Trạng thái, baseline và cách chạy kiểm chứng

- Phiên bản v0.1 — **WRITING-PLANS / PENDING**. User yêu cầu “viết plan theo spec này”; chưa có xác nhận riêng danh sách Java ở pha này. Không đổi nhãn BRAINSTORMING PENDING trong spec thành APPROVED hồi tố.
- SHA khi lập plan: HEAD `0d69b001013059db28079ee55255f1cc69643f6a`, local ahead13/behind2 theo refs đang có. So với spec có thêm commit pipeline ngoài task; giữ nguyên, không revert hoặc đưa file `tools/ticket_data_pipeline/inspect_counts.py` chưa theo dõi vào task.
- Refs nguồn: `origin/dev/son@4a1b6cb6be9c647e4876d1ccac8241524a39ba86`, Payment `75e5ea7bbc8f401e66978756d65c35be4df1a515`, Event/Ticket `1bc263a3ed9b485148f1d4fbac7102430478c5a5`. Khi execute phải fetch/so drift và ghi lại SHA thật, không checkout đè working tree.
- Gate EXECUTING-PLANS và quyền tác động runtime là các điều kiện riêng trước thực thi. Task có Java mới chưa được viết test đỏ trước gate đó. Không có công cụ subagent ở phiên lập plan; đề xuất Native khi thực thi nếu công cụ vẫn như hiện tại.
- Các đường dẫn bên dưới tính từ `D:/DATN/flash-ticket-platform`. Lệnh Maven chạy tại thư mục module chỉ định, chọn JDK theo POM bằng toolchain/IDE/runtime có sẵn; không bỏ Enforcer hoặc thay java.version để qua test.
- Lệnh mẫu Windows nạp root env như README hiện có: `rtk proxy python -m dotenv -f ../../.env run --no-override .\mvnw.cmd <goals>`. `<goals>` ở dòng này là ký hiệu hướng dẫn; từng task bên dưới có lệnh đầy đủ. Thiếu python-dotenv/JDK cần thiết thì dùng runtime env-file tương đương đã cài, không gán tạm biến ứng dụng.
- Testcontainers của Booking tạo PostgreSQL/Rabbit riêng. Trước integration, xác nhận `BOOKING_TEST_RABBIT_HOST` không trỏ119; nếu có override thì phải là fixture local thuộc lượt test. Payment `PAYMENT_TEST_DB_*`/`PAYMENT_TEST_RABBIT_*` cũng phải trỏ fixture local. Không chạy test truncate/schema-init vào DB119.
- `services/booking-service/TESTING.md` ghi lỗi timing cũ trong full integration; không hứa full green từ kết quả384/388 unit hoặc focused PASS. Ghi lại failure của đúng lần chạy, không tăng timeout/bỏ constraint/test để biến báo cáo xanh.

## File Structure

| Nhóm | File tạo/sửa và trách nhiệm |
| --- | --- |
| Nguồn tích hợp | `services/event-service/**`, `services/ticket-service/**` lấy chọn lọc từ ref đã ghi; `services/payment-service/**` giữ patch E1; Redis/config từ origin/dev/son. Đối chiếu file manifest trước khi áp patch; không merge toàn bộ nhánh Payment |
| Booking API | Tạo `services/booking-service/src/main/java/com/flashticket/booking_service/dto/response/OrderListView.java`; sửa `order/controller/OrderController.java`, `order/service/OrderService.java`, `order/service/impl/OrderServiceImpl.java`, `order/repository/OrderRepository.java` dưới cùng package |
| Booking tests | `services/booking-service/src/test/java/com/flashticket/booking_service/order/controller/OrderControllerTests.java`, `persistence/BookingPersistenceIT.java`; giữ mapper, entity, BookingFixtures và test infrastructure hiện có |
| Gateway | `infrastructure/api-gateway/src/main/java/com/flashticket/api_gateway/routing/GatewayRouteConfiguration.java`, `security/GatewaySecurityConfiguration.java`; sửa ba test route/security/GatewayHttpTests đã có |
| Runtime local | Tạo `deploy/docker-compose.integration.local.yml`: một compose riêng cho8 ứng dụng local và ngrok, không kế thừa override Eureka=false của deploy119; dùng lại Dockerfile từng module và `deploy/docker-compose.frontend.local.yml` cho relay; sửa `.env.example`, root `.env` ignored, `deploy/README.md`, `deploy/README.payment.md` |
| JWT/Ticket/topology | Config Server `infrastructure/config-server/src/main/resources/config/{event,booking,payment,ticket,user}-service.yaml` theo file tồn tại sau Task1; Ticket `services/ticket-service/src/main/resources/application.yaml`; `deploy/rabbitmq/definitions.json`. Chỉ sửa key/issuer/endpoint liên quan, không đổi policy/schema rộng |
| FE | `frontend/src/types/bookingContract.ts`, `services/bookingService.ts`, `pages/Checkout/CheckoutPage.tsx`, `pages/MyOrders/MyOrdersPage.tsx`, `pages/MyTickets/MyTicketsPage.tsx`, `i18n/locales/{vi,en}.json`, `assets/css/payment-result.css`/`checkout.css` |
| Browser/quality | Tạo `frontend/tests/order-history.browser.js`; sửa `booking-flow.browser.js`, `payment-ticket.browser.js`, `integration-live.browser.js`, `run-integration.cjs`, `tests/README.md`; tạo `frontend/.eslintrc.cjs`, sửa source bị lint báo trong phạm vi sửa tối thiểu |
| Bundle/asset | `frontend/src/App.tsx`, `src/main.tsx`, `src/assets/css/chatbot-widget.css`; tạo asset WebP từ PNG hiện có nếu encoder sẵn; không đổi thiết kế hình |
| Evidence/docs | Tạo `docs/java-workflow/reports/2026-11-04-local-integration-results.md`; JSON/ảnh/log lọc dữ liệu dưới ignored `output/playwright/`; cập nhật `frontend/docs/{integration,task-1.2-remaining,task-1.3-progress,task-1.4-results}.md`, `services/booking-service/TESTING.md`. Không tạo thêm nguồn thẩm quyền nghiệp vụ |

## Danh sách Java của plan — PENDING

Giữ ID của spec. Đường dẫn C2–C5 dưới package Booking đã ghi ở File Structure; C6/C7 dưới src/test/java. Không constructor/dependency mới ở bean hiện có, không private helper mới.

| ID | Class/file | NEW/EXISTING, annotation/API sinh | Cách áp dụng mẫu |
| --- | --- | --- | --- |
| C1 | OrderListView | NEW final class; @Getter/@AllArgsConstructor; final List<OrderView> items, final String nextCursor nullable; M7–M9 | Theo OrderView hiện có; không builder/record/setter/equals/toString |
| C2 | OrderController | EXISTING + M1/M2; giữ annotations/dependencies/constructor | Shop App OrderController.getOrder/getOrders, JWT subject và Problem của FlashTicket |
| C3 | OrderService | EXISTING + M3/M4 interface | Tên nghiệp vụ cụ thể, dùng interface sẵn |
| C4 | OrderServiceImpl | EXISTING + M3/M4 implementation; @Override/@Transactional(readOnly=true) | Repository→owner check→OrderMapper; không tạo tầng đọc riêng |
| C5 | OrderRepository | EXISTING + M5/M6; M6 @Query/@Param, không @Lock | Spring Data/JPQL theo repo hiện có; không EntityManager/native SQL mới |
| C6 | OrderControllerTests | EXISTING + U1/U2/U3 @Test | MockMvc/security đã có, không nested class/helper mới |
| C7 | BookingPersistenceIT | EXISTING + U4/U5 @Test | Testcontainers/JdbcTemplate/fixtures đã có, không test infrastructure mới |
| C8 | Gateway configs + tests | EXISTING, sửa thân method/assertion, giữ annotations/signatures | standardFilters/JWT fixture/HTTP harness hiện có |
| C9 | Payment E1, Redis E2, Event/Ticket E4, User | REUSE implementation và API hiện có; config non-Java sửa đúng phạm vi | Không viết lại source nhập theo pattern khác; khai báo mới khi conflict cần delta riêng |

| ID | Class | Chữ ký/annotation đầy đủ | Loại, lý do/caller |
| --- | --- | --- | --- |
| M1 | OrderController | `public ResponseEntity<OrderView> getBuyerOrder(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID orderId)`; `@GetMapping("/{orderId}")` | NEW, HTTP→M3, no-store |
| M2 | OrderController | `public ResponseEntity<OrderListView> getBuyerOrders(@AuthenticationPrincipal Jwt jwt, @RequestParam(required=false) UUID cursor, @RequestParam(defaultValue="20") int size)`; `@GetMapping` | NEW, boundary size1..100, HTTP→M4, no-store |
| M3 | OrderService + impl | `public OrderView getBuyerOrder(String buyerSubject, UUID orderId)`; impl `@Override @Transactional(readOnly=true)` | NEW ở hai vị trí; findById và kiểm owner, không dùng query lock của cancel |
| M4 | OrderService + impl | `public OrderListView getBuyerOrders(String buyerSubject, UUID cursor, int size)`; impl `@Override @Transactional(readOnly=true)` | NEW ở hai vị trí; anchor→size+1→DTO, không parser/cursor helper |
| M5 | OrderRepository | `public List<Order> findByBuyerSubjectOrderByCreatedAtDescIdDesc(String buyerSubject, Pageable pageable)` | NEW, derived query trang đầu |
| M6 | OrderRepository | `public List<Order> findBuyerOrdersBefore(@Param("buyerSubject") String buyerSubject, @Param("beforeCreatedAt") Instant beforeCreatedAt, @Param("beforeOrderId") UUID beforeOrderId, Pageable pageable)`; @Query | NEW, keyset trang tiếp; JPQL chính xác trong Task2 |
| M7 | OrderListView | `public OrderListView(List<OrderView> items, String nextCursor)` | NEW Lombok constructor |
| M8 | OrderListView | `public List<OrderView> getItems()` | NEW Lombok getter |
| M9 | OrderListView | `public String getNextCursor()` | NEW Lombok getter |
| M10 | OrderMapper | `public OrderView toView(Order order)` | REUSE UNCHANGED, mapper MapStruct hiện có |
| U1 | OrderControllerTests | `void readsOrderUsingAuthenticatedBuyer() throws Exception`; @Test | NEW:200/no-store/subject/404 |
| U2 | OrderControllerTests | `void listsOrdersUsingAuthenticatedBuyer() throws Exception`; @Test | NEW:default20/cursor/wire/owner |
| U3 | OrderControllerTests | `void rejectsUnauthorizedOrInvalidOrderReads() throws Exception`; @Test | NEW:401/403/400/invalid size và cursor |
| U4 | BookingPersistenceIT | `void readsBuyerOrdersWithoutChangingBookingState()`; @Test | NEW:own/foreign/missing, không write/freeze/expire |
| U5 | BookingPersistenceIT | `void paginatesBuyerOrdersWithStableCursor()`; @Test | NEW:tie/insert giữa trang/khác buyer/hết trang |

Không phát sinh delta Java so với spec. Các khác mẫu Shop App là cơ chế hiện có của FlashTicket (JWT/MapStruct/AMQP/Testcontainers) và readOnly cho API mới; không đổi UUID, tiền chuỗi, snapshot, khóa hoặc ACK để giống Shop App.

## Task 1: Bộ source tích hợp giữ đúng contract và không kéo thêm worker tài chính

**Files:** Modify/import có chọn lọc `services/event-service/**`, `services/ticket-service/**`, Redis/config trong diff HEAD..origin/dev/son; giữ `services/payment-service/src/main/java/com/flashticket/payment_service/{service/impl/PaymentSagaServiceImpl,worker/PaymentWorker,repository/OutboxMessageRepository,client/BookingPurchaseClient}.java`. Evidence: `docs/java-workflow/reports/2026-11-04-local-integration-results.md`. Java: C9, không khai báo mới.

**Interfaces:** Consumes refs/baseline ở trên. Produces Event public API và Ticket order/QR API E4; Payment `recordIssuanceResult`, `recordOrderCompleted`, `recoverSaga` giữ patch E1; Redis availability E2. Các contract/message trong spec§6–7 giữ nguyên.

- [ ] **1. Ghi baseline trước tích hợp.** Chạy `rtk git status --short --branch`, `rtk git log -1 --format=%H`, `rtk git diff --name-status`; fetch refs đã nêu và ghi SHA/diff vào báo cáo. Xác nhận FE/Gateway chưa commit và commit pipeline ngoài task được bảo toàn; chọn workspace cách ly ở execution time nếu cần, không tự stash/discard thay đổi của user.
- [ ] **2. Chạy baseline compatibility trước sửa.** Tại Payment/JDK21: `rtk proxy python -m dotenv -f ../../.env run --no-override .\mvnw.cmd -Dtest=PaymentFlowIntegrationTest test`. Dùng fixture local đã sẵn sàng; thiếu fixture ghi BLOCKED hạ tầng, không đổi endpoint sang119. Ghi kết quả của các test hiện có `forwardsTicketSuccessAndWaitsForBookingCompletion`, `rejectsOrderCompletionWithoutForwardedTicketResult`, `rejectsConflictingTicketResultsAcrossOutcomes`, `recoversSagaAfterCommitAndDeliveryFailures`.
- [ ] **3. Áp source chọn lọc.** Đưa Event/Ticket E4 và Redis/config E2 vào cùng bản tích hợp; loại scaffold bị thay thế trong đúng module, không giữ hai endpoint/bean trùng. Giữ Payment E1 về version/checkpoint/recovery và worker chỉ publishOutbox/recoverSagas; không import sendRefunds/pollRefunds/selectEventRefundOrders, controller finance hay schema mới chỉ để theo nhánh Tuấn. Không ghi đè file ngoài diff task.
- [ ] **4. Kiểm bản tích hợp.** Payment chạy lại lệnh bước2; Event/Ticket chạy `rtk proxy python -m dotenv -f ../../.env run --no-override .\mvnw.cmd verify` tại từng module/JDK21; Booking chạy tương tự với JDK23. Expected exit0, Spotless/SpotBugs theo module, compatibility assertions vẫn chặn thiếu checkpoint/conflicting outcome và giữ version command khi recovery. Ghi failure timing cũ nếu tái hiện, không sửa luật tiền/constraint để pass.
- [ ] **5. Checkpoint source đã kiểm.** Review `rtk git diff --check` và diff module/manifest, ghi các test PASS/FAIL/BLOCKED. Nếu đã có quyền commit, chỉ stage các file tích hợp thuộc task, commit `fix: đồng bộ source tích hợp và giữ contract thanh toán`; không push ở bước này.

## Task 2: API đọc order và phân trang lịch sử có ownership

**Files:** Create C1; Modify C2–C5; Test C6/C7. Dùng lại OrderMapper/OrderView/entity. Không sửa migration/schema hoặc helper fixtures.

**Interfaces:** Consumes `OrderRepository.findById(UUID)`, M10 và `ErrorCode.ORDER_NOT_FOUND/INVALID_REQUEST`. Produces M1–M9; GET order→OrderView, GET list→OrderListView; size20 mặc định/1..100; cursor UUID, no-store.

- [ ] **1. Viết U1–U3 sau gate execution.** Dùng MockMvc hiện có và response OrderView fixture, assert `get("/api/v1/orders/{id}", id)` với JWT subject buyer trả200/no-store; mock M3 ORDER_NOT_FOUND→404. U2 assert list không cursor gọi M4 `(buyer,null,20)`, có cursor/size100 được chuyển nguyên, JSON chỉ items/nextCursor. U3 kiểm guest401, ORGANIZER403, malformed UUID/cursor và size0/101 trả400, service không được gọi cho input sai. Stub/fixture trong chính test, không thêm helper Java.
  Assertion của U1 sau khi arrange `id`/`response` bằng fixture OrderView đang có:
  ```java
  when(orderService.getBuyerOrder("buyer", id)).thenReturn(response);
  mvc.perform(get("/api/v1/orders/" + id).with(jwt().jwt(t -> t.subject("buyer"))
      .authorities(new SimpleGrantedAuthority("ROLE_BUYER"))))
      .andExpect(status().isOk()).andExpect(header().string("Cache-Control", "no-store"))
      .andExpect(jsonPath("$.orderId").value(id.toString()));
  ```
- [ ] **2. Chạy RED MVC.** Tại Booking/JDK23: `rtk proxy python -m dotenv -f ../../.env run --no-override .\mvnw.cmd -Dtest=OrderControllerTests test`. Trước khai báo sẽ có compile error M3/M4/OrderListView hoặc GET404; xác nhận nguyên nhân đúng tính năng thiếu, không nhận lỗi Docker như RED hợp lệ.
- [ ] **3. Thêm M1–M9 tối thiểu.** C1 đúng hai final fields/Lombok. M1/M2 lấy jwt.getSubject(), no-store, size guard400. M3 findById→filter owner→orElseThrow ORDER_NOT_FOUND→M10. M4 đọc owned anchor nếu có,400 chung cho missing/foreign anchor; lấy size+1 và dùng M5/M6; map trong readOnly, trả tối đa size, nextCursor từ item cuối trả về chỉ khi có row dư. Không gọi thời gian DB hoặc method ghi/lock từ GET.

  M6 dùng JPQL cố định, Pageable luôn page0/size+1:
  ```sql
  select o from Order o
  where o.buyerSubject = :buyerSubject
    and (o.createdAt < :beforeCreatedAt
         or (o.createdAt = :beforeCreatedAt and o.id < :beforeOrderId))
  order by o.createdAt desc, o.id desc
  ```
- [ ] **4. Chạy GREEN MVC bằng lệnh bước2.** Expected U1–U3 và hồi quy create/promotion/cancel/snapshot PASS, không đổi các đường POST hoặc response cũ.
- [ ] **5. Viết U4/U5 kiểm DB thật.** Dùng `seedOrderCreationConfiguration`, `createOrderRequest`, `captureOrderCreationState` hiện có và các fixture được lập trong chính test. U4 so state fingerprint trước/sau M3/M4, own200 mapping đầy đủ, foreign/missing→ORDER_NOT_FOUND, foreign/missing cursor→INVALID_REQUEST; pending quá deadline vẫn được đọc mà không expire/freeze/release. U5 tạo đơn của2 buyer, chỉnh timestamp fixture hợp lệ bằng JdbcTemplate trong DB test để có tie, query lần lượt size1/2/100, thêm đơn mới trước anchor và kiểm không trùng/mất tập cũ; empty/end→nextCursor null. Assertion cốt lõi:
  ```java
  assertThat(orderService.getBuyerOrder("buyer", orderId).getOrderId()).isEqualTo(orderId);
  assertThat(orderService.getBuyerOrders("buyer", null, 1).getItems()).hasSize(1);
  assertThat(captureOrderCreationState(eventId)).isEqualTo(beforeRead);
  ```
  `orderId/eventId/beforeRead` là dữ liệu arrange của U4, không biến framework mới; giữ annotations U4/U5 như bảng, không thêm clock/mock production hook.
- [ ] **6. Chạy integration và module verify.** Tại Booking: `rtk proxy python -m dotenv -f ../../.env run --no-override .\mvnw.cmd -Pintegration -Dit.test=BookingPersistenceIT#readsBuyerOrdersWithoutChangingBookingState+paginatesBuyerOrdersWithStableCursor verify`, rồi `rtk proxy python -m dotenv -f ../../.env run --no-override .\mvnw.cmd -Pintegration verify`. Expected U4/U5 PASS và full result được báo đúng; query có giới hạn, không fetch-join collection pagination/count query. Nếu full timing lỗi cũ còn tái hiện thì task API focused có evidence nhưng toàn đợt chưa nghiệm thu đầy đủ.
- [ ] **7. Checkpoint API.** Review diff C1–C7 và no-write evidence; commit có chọn lọc `feat: bổ sung đọc đơn và lịch sử theo buyer` khi được giao commit.

## Task 3: Gateway phục vụ catalog public và giữ ranh giới bảo mật

**Files:** Hai config Gateway; `infrastructure/api-gateway/src/test/java/com/flashticket/api_gateway/routing/GatewayRouteConfigurationTests.java`, `security/GatewaySecurityConfigurationTests.java`, `GatewayHttpTests.java`. Java C8, không method/test class mới.

**Interfaces:** Consumes Event E4 và Order API Task2. Produces GET `/api/v1/events`, `/api/v1/categories`, `/api/v1/venues`→`lb://event-service`; giữ availability→Booking, Payment create/callback, order/ticket/user routes.

- [ ] **1. Mở rộng test có sẵn để RED.** Thêm3 route vào `mapsOnlyApprovedBootstrapRoutesToTheFiveServiceIds`, public assertions vào `permitsOnlyTheExplicitPublicEventReadPathsWithoutAuthentication`. Mở rộng `GatewayHttpTests.forwardsPublicAvailabilityAndNormalizesCorrelation` để stub Event/catalog trong harness sẵn có và assert forwarded q/categoryId/size/cursor. Giữ `requiresBuyerForAllOrderPathsAndBlocksInternalAndFallbackPaths` cho401/403/internal và POST catalog không public. Không thêm method Java ngoài gate.
  Trong HTTP harness sau khi cấu hình downstream fixture tương ứng, các request không JWT phải đến Event:
  ```java
  client.get().uri("/api/v1/events?size=20").exchange().expectStatus().isOk();
  client.get().uri("/api/v1/categories").exchange().expectStatus().isOk();
  client.get().uri("/api/v1/venues").exchange().expectStatus().isOk();
  ```
- [ ] **2. Chạy RED tại Gateway/JDK21.** `rtk proxy python -m dotenv -f ../../.env run --no-override .\mvnw.cmd -Dtest=GatewayRouteConfigurationTests,GatewaySecurityConfigurationTests,GatewayHttpTests test`. Expected thiếu route/public matcher, không phải lỗi issuer/network.
- [ ] **3. Sửa hai method hiện có.** Thêm3 GET route dùng standardFilters và3 permitAll chính xác; giữ thứ tự availability/detail và query/header forwarding. Không mở `/api/v1/**`, không bỏ BUYER cho Payment/orders/tickets, không cho internal qua Gateway.
- [ ] **4. Chạy GREEN/verify.** Lặp lệnh bước2 rồi `rtk proxy python -m dotenv -f ../../.env run --no-override .\mvnw.cmd verify`. Expected tất cả test0 failures/errors/skips, Spotless/SpotBugs PASS; không chỉ đổi expected route count để pass.
- [ ] **5. Checkpoint Gateway.** Review forwarding/auth assertions và commit `fix: mở catalog public qua gateway` khi được giao commit. Container runtime chưa được coi là đã nạp code này cho đến Task4.

## Task 4: Bộ runtime local và chuyển topology119 có kiểm chứng

**Files:** Create `deploy/docker-compose.integration.local.yml`; Modify `.env.example`, ignored `.env`, `deploy/README.md`, `deploy/README.payment.md`, configs và definitions trong File Structure. Reuse `deploy/ngrok/payment.yml`, relay frontend, Dockerfile module; C9, không Java mới.

**Interfaces:** Consumes source Task1, APIs Task2/3, endpoint/credential thật đã xác minh. Produces compose project `flash-ticket-integration-local`, services `config-server`, `discovery-server`, `api-gateway`, `event-service`, `booking-service`, `payment-service`, `ticket-service`, `user-service`, `ngrok`; FE proxy18081, Payment callback HTTPS→8083. Report section `runtime/topology transition` lưu trạng thái trước/sau và phục hồi.

- [ ] **1. Ghi preflight đọc trước sửa.** Theo spec§5.3/§7.2, đọc container/port/tunnel, DB/Mongo schema compatibility, Redis PING, Eureka, queue/binding/channel/consumer, pending outbox và DLQ. Ghi consumer/scheduler ngoài local cùng owner, phiên bản và hành động phục hồi. Không đọc/đưa body nhạy cảm vào báo cáo; số10/4 Unacked cũ chỉ là baseline lịch sử.
- [ ] **2. Tái hiện lỗi cấu hình bằng probe.** HTTP catalog/health, TCP/auth/query từ chính network container và so config resolved với tên biến env; ghi FAIL cho DB5432 refused/discovery thiếu, không thêm dialect. Nếu cần mạng/tunnel thì ghi endpoint đã xác minh và kiểm lại từ container, không coi DataGrip kết nối được là runtime đã được.
- [ ] **3. Viết compose/env đúng A.** Compose mới dùng network_mode:host và env_file `../.env`, build context/Dockerfile sẵn của8 module; không sửa các compose deploy119. Nạp EUREKA_CLIENT_ENABLED=true/EUREKA_SERVER_URL/CONFIG_SERVER_URL từ root env; SERVER_ADDRESS/instance advertised phải truy cập được từ Gateway. Ngrok host-network trỏ127.0.0.1:8083 và config payment.yml sẵn. Bổ sung biến chưa có vào .env.example, secret để trống, giá trị runtime chưa biết phải xin dữ liệu đúng thay vì đoán.
- [ ] **4. Chốt issuer và đường Ticket trả kết quả.** Bỏ runtime jwk-set-uri/audiences trong Ticket/config liên quan, giữ issuer và role/ownership. Root env đặt ba TICKET_*RESULT* bằng `ticket.issuance-result.v1` như spec; bật3 feature flags chỉ sau kiểm QR key ring/active version và env dependencies. VNPAY_APP_RETURN_URL=`http://127.0.0.1:5173/payment-result`; Return/IPN HTTPS domain thực→Payment xác minh chữ ký. Không tạo khóa QR mới để giải mã dữ liệu cũ.
- [ ] **5. Validate cấu hình mà chưa bật worker.** Từ root: `rtk proxy docker compose --env-file .env -f deploy/docker-compose.integration.local.yml config --quiet`; `rtk proxy python -m json.tool deploy/rabbitmq/definitions.json > $null`; build images bằng `rtk proxy docker compose --env-file .env -f deploy/docker-compose.integration.local.yml build`. Expected exit0, không in secret; image dùng đúng SHA/JDK và pending Ticket outbox không bị cấu hình mới viết lại.
- [ ] **6. Thực hiện phiên chuyển đổi đã phối hợp.** Chỉ khi có quyền/runtime owners đồng ý: tạm dừng đúng consumer/publisher/scheduler cạnh tranh, ghi việc close connection có thể requeue ngay Unacked. Đồng bộ binding `business.ex`/`ticket.issuance-result.v1`→`payment.ticket-issuance-failed.q` và gỡ binding key cũ trong cùng phiên sau khi đã phân loại pending message; dùng thao tác quản trị binding có chọn lọc, không import đè toàn definitions, không xóa queue/DLQ. Pending type cũ giữ nguyên chờ xử lý từng nhóm. Không có phối hợp thì BLOCKED bước này, không tự tạo vhost/clone DB thay A.
- [ ] **7. Khởi động có thứ tự và đo readiness.** `rtk proxy docker compose --env-file .env -f deploy/docker-compose.integration.local.yml up -d config-server discovery-server`; kiểm reachable rồi chạy `rtk proxy docker compose --env-file .env -f deploy/docker-compose.integration.local.yml up -d api-gateway event-service booking-service payment-service ticket-service user-service ngrok`. Dùng relay hiện có: `rtk proxy docker compose --env-file .env -f deploy/docker-compose.frontend.local.yml up -d gateway-local-port`. Dừng đúng các container local cũ trùng port trong danh sách chuyển đổi trước khi start; không dừng process Windows khác theo suy đoán.
- [ ] **8. Chạy GREEN runtime.** Ghi restart counts lúc bắt đầu và sau≥5 phút bằng các lần probe ngắn; đủ5 business instance cùng Eureka và Gateway thực sự gọi từng owner. Catalog guest200, profile buyer200, order Task2 theo quyền; Callback unsigned chỉ trả lỗi chữ ký dự kiến, không chứng minh thanh toán. So7 bindings trong spec và producer/config thực, test giao dịch mới không đi vào runtime ngoài manifest.
- [ ] **9. Checkpoint runtime/docs.** Ghi manifest SHA/digest/config, biến đã bổ sung (không giá trị bí mật), query timings và vấn đề DLQ theo owner. Commit file cấu hình/template/docs được phép với message `chore: cấu hình môi trường tích hợp local`; root .env/keyring/evidence riêng không stage. Khôi phục runtime dùng chung theo trạng thái đã phối hợp sau Task8, không khôi phục producer sai contract.

## Task 5: Checkout đọc server và lịch sử đơn/vé theo buyer

**Files:** FE types/service/pages/locales/CSS trong File Structure; Create `frontend/tests/order-history.browser.js`; Modify `frontend/tests/booking-flow.browser.js`, `payment-ticket.browser.js`. Không Java mới.

**Interfaces:** Consumes M1/M2 JSON; add `OrderListView = {items:OrderView[];nextCursor:string|null}` vào bookingContract.ts; `bookingService.getOrder(orderId:string, signal?:AbortSignal):Promise<OrderView>` và `getOrders(cursor?:string, size?:number, signal?:AbortSignal):Promise<OrderListView>`. Reuse `ticketService.getOrderTickets(orderId,signal)`/getQrImage, PaymentResultPage và OrderLookupForm. No Ticket list endpoint mới.

- [ ] **1. Viết fixture RED.** Trong order-history.browser.js dùng contract riêng tab test, đặt tên checks: `reload reads server without cached receipt`, `stale cached order cannot enable payment`, `owner switch ignores delayed reads and mutations`, `history uses owned cursor pages`, `tickets load only for selected order`. Assert GET order/list, default20, cursor server; no POST create/payment/cancel tự phát; size tiền `9007199254740993` giữ đúng chuỗi. Thêm delayed GET và delayed apply/cancel trả sau đổi buyer/order/gateway: UI/cached receipt của scope mới không được nhận dữ liệu cũ.
- [ ] **2. Chạy RED browser.** Vite từ frontend: `rtk proxy npm run dev -- --host 127.0.0.1`. Tại root mở session riêng `rtk proxy npx --yes --package @playwright/cli playwright-cli -s=order-history open http://127.0.0.1:5173 --headed`, rồi `rtk proxy npx --yes --package @playwright/cli playwright-cli -s=order-history run-code --filename D:/DATN/flash-ticket-platform/frontend/tests/order-history.browser.js`. Expected check đầu thất bại vì checkout chỉ cached receipt/list chưa có; không reuse tab user hoặc ghi119 bằng fixture.
- [ ] **3. Thêm đúng hai API client và sửa checkout.** GET timeout10000ms/AbortSignal, scope gồm gateway/issuer/buyer/order; loading không cho thao tác tiền. Dùng scope check cả service cache write của create/apply/cancel và page state để response cũ không vượt phiên; không đổi request body/idempotency hiện có. GET lỗi401/403/404/network xóa xác nhận hiện tại, có retry; pending hết expiresAt không payable dù worker chưa đổi state. Không fake GET hoặc tạo hold mới khi thiếu receipt.
- [ ] **4. Sửa MyOrders/MyTickets.** MyOrders dùng Booking list/tải thêm, reset cursor khi đổi scope, mở đơn với GET order và link PaymentResultPage. MyTickets dùng danh sách đơn để chọn orderId, gọi Ticket chỉ cho đơn được chọn; giữ deep link/tra cứu mã, QR only VALID, abort/revoke/focus. Giữ PaymentResultPage polling tối đa10 lượt/cách3s và UNKNOWN không thu lại. Bổ sung nhãn VI/EN/CSS hiện có, không component abstraction mới chỉ để chia tầng.
- [ ] **5. Cập nhật fixture cũ và chạy GREEN.** booking-flow/payment-ticket phải mock GET order/list thật bên cạnh POST, không giữ nhãn cached-only làm tiêu chí PASS. Chạy lệnh run-code bước2 và chạy từng file cũ trong context/session riêng theo tests/README.md. Assert400 cursor có nút tải lại từ đầu, list empty/end, wrapped Payment error, ownership, QR cleanup, 320/390/1440px, không fan-out Ticket cho mọi order. Đóng session test `... -s=order-history close` và build `rtk proxy npm run build` tại frontend; tất cả checks PASS/exit0.
- [ ] **6. Checkpoint FE.** Review request contract và ảnh đã che QR/PII, commit chọn lọc `feat: đọc đơn và hiển thị lịch sử từ booking` khi được giao commit. Chưa coi fixture là nghiệm thu backend thật.

## Task 6: Runner bao phủ Payment/history và lint chạy được

**Files:** `frontend/tests/run-integration.cjs`, `tests/README.md`, `tests/integration-live.browser.js`, `frontend/.eslintrc.cjs` mới; source TS/TSX lint báo cần sửa tối thiểu. Không thêm package/framework.

**Interfaces:** Consumes test Task5 và các suite cũ. Produces `node tests/run-integration.cjs --fixtures|--live`; fixture result có file/status/checks cho mọi suite; live giữ PASS/FAIL/BLOCKED, không tự biến readiness thành full purchase PASS.

- [ ] **1. Chạy baseline.** Tại frontend: `rtk proxy npm run lint`, `rtk proxy node tests/run-integration.cjs --fixtures`. Expected lint thiếu config và fixture results thiếu payment-ticket/order-history trước sửa. Không đếm số group cũ63/41 như số mới.
- [ ] **2. Sửa runner và thêm lint config.** Đưa payment-ticket.browser.js và order-history.browser.js vào groups với context riêng (payment test dùng clock); giữ nhóm layout/colors/polish chung context. Config ESLint8 dùng parser/plugins TS/react-hooks đã cài, browser/es2020 và node override vite.config.ts, ignore dist/node_modules/output; sửa lỗi báo thực, không maxWarnings/blanket-disable để pass. Đảm bảo runner thiếu/fail suite không exit0.
- [ ] **3. Pin các rủi ro auth/payment.** Giữ public-api check refresh lỗi không gửi token cũ, keycloak-nonce check nonce sai bị từ chối và payment-ticket check callback giả/status00, UNKNOWN, lỗi wrapper/quyền xóa xác nhận, giới hạn polling. Thêm assertion vào suite hiện có nếu chưa cover, không test framework mới.
- [ ] **4. Chạy GREEN.** Tại frontend: `rtk proxy npm run lint`, `rtk proxy npm run build`, `rtk proxy node tests/run-integration.cjs --fixtures`. Expected exit0, lint không warning, JSON ghi PASS từng file gồm2 suite mới; thử một assertion sai trong bản tạm thuộc test context rồi khôi phục để xác nhận runner nonzero, không sửa production để diễn RED.
- [ ] **5. Checkpoint quality.** Cập nhật README cách chạy/context/giới hạn, commit `test: bổ sung hồi quy lịch sử và thanh toán` khi được giao commit.

## Task 7: Giảm tải bundle và asset chatbot

**Files:** `frontend/src/App.tsx`, `src/main.tsx`, `src/assets/css/chatbot-widget.css`; optional create `src/assets/images/chatbot/chatbot-robot.webp`; browser checks header/home hiện có và order-history/payment-ticket. Không UI redesign/dependency mới.

**Interfaces:** Consumes routes hiện có, giữ nguyên URLs/role/VI/EN và nội dung ảnh. Produces lazy imports cho các page organizer/admin/chart/editor ít dùng và chatbot; ảnh tương đương với byte size nhỏ hơn nếu encoder hiện có hỗ trợ.

- [ ] **1. Đo trước đổi.** `rtk proxy npm run build` tại frontend; ghi dist entry JS/gzip và asset robot (~2.61MB/~10.09MB là số cũ cần đo lại). Browser network trang chủ/checkout không được tải editor/chart bundle chỉ vì App import tĩnh.
- [ ] **2. Tối ưu tối thiểu.** React.lazy/Suspense cho routes nặng và chatbot, giữ public buyer routes rõ loading. Dời CSS riêng chatbot ra khỏi main.tsx sang module lazy để asset không bị tải global dù component bị ẩn. Nếu encoder WebP đã cài, xuất từ đúng PNG, giữ alpha/tỷ lệ và cập nhật mọi URL CSS; không cài package hay sinh lại nhân vật. Nếu thiếu encoder, ghi phần nén asset BLOCKED thay vì tăng chunk warning hoặc bỏ ảnh.
- [ ] **3. Kiểm sau đổi.** Build lại và chạy fixtures Task6; network home/checkout không tải eager editor/chart/chatbot ngoài nhu cầu, deep link route lazy vẫn mở được, ảnh không404/đổi tỷ lệ, reduced-motion/layout/QR/auth còn đúng. Ghi byte trước-sau thực; chỉ coi đạt tối ưu khi có giảm đo được, không hứa một ngưỡng hiệu năng chưa có trong spec.
- [ ] **4. Checkpoint performance.** Commit `perf: giảm tải bundle và tài nguyên chatbot` khi được giao commit; giữ nguyên fixture với backend thật chưa được thay bởi ảnh UI.

## Task 8: Nghiệm thu thật với đúng runtime local

**Files:** `frontend/tests/integration-live.browser.js`, `frontend/tests/run-integration.cjs`, `frontend/tests/README.md`; report results MD và ignored output/playwright. Non-Java, không API/provider hook mới.

**Interfaces:** Consumes Tasks1–7, phiên runtime Task4 và test events đã xác minh cho QUANTITY/STANDING/SEATED. Produces report cases/status/evidence IDs, trạng thái Booking/Saga cuối và before/after inventory. Không tự lấy event đầu catalog để tạo giao dịch.

- [ ] **1. Ghi test inputs trong report/env đã giao.** Chốt test eventId/ticketType/sector/seat/voucher còn hiệu lực theo dữ liệu thật, hai buyer test và Sandbox merchant/domain đúng. Credentials chỉ đọc từ nguồn local được phép/giữ bộ nhớ, không ghi JSON/report hoặc argv token. Thiếu dữ liệu mode nào thì BLOCKED mode đó, không dùng seed SQL tối giản làm request hợp lệ.
- [ ] **2. Mở rộng bài live đúng khả năng.** Giữ auth/readiness cũ, bỏ các BLOCKED hardcode đã lỗi thời. Thêm bước UI read order/list/reload→create/promotion→chuyển VNPay; tương tác sandbox theo UI thật và quay về. Không mock route/JWT/Payment/Ticket, không ký IPN giả, không tự chọn và thanh toán hàng loạt event. Ca cần con người/điều kiện provider được ghi rõ để người kiểm thực hiện trong cùng phiên.
- [ ] **3. Chạy auth/catalog/profile trước ghi giao dịch.** Tại frontend: `rtk proxy node tests/run-integration.cjs --live`. Kiểm guest200 catalog/detail/availability, buyer login/reload/refresh/logout, profile200; đăng ký một tài khoản test mới qua Keycloak rồi chờ identity/projection→GET /me, không coi mở form là PASS. Nếu bước nguồn chưa sẵn sàng thì dừng phần phụ thuộc, report FAIL/BLOCKED.
- [ ] **4. Chạy matrix mua vé và quyền.** QUANTITY/STANDING/SEATED; voucher hợp lệ/not-found/ngoài cửa sổ/hết lượt; replay cùng key và mất response; hai buyer tranh nguồn cung cuối; buyer cancel và expiry release đúng một lần; foreign GET order/tickets/QR bị từ chối; list chỉ buyer hiện tại. Dùng UI/API công khai thật, đọc DB/inventory để đối chiếu; không update state bằng SQL để giả kết quả.
- [ ] **5. Nghiệm thu Sandbox success/recovery.** Ghi orderId/attemptId/chargeId/issuanceId/correlation; đối chiếu IPN đã xác minh→AcceptPayment→PaymentDecision→Ticket result qua Payment→Booking COMPLETED→OrderCompleted→Saga COMPLETED. Reload/history/QR đọc lại được; không chụp raw QR/JWT/IPN vào evidence. Kiểm return-before-IPN, callback lặp/chậm/UNKNOWN khi điều khiển được; không điều khiển được thì chỉ gắn PASS cho test thành phần Task1/6, giữ live case BLOCKED.
- [ ] **6. Kiểm tồn và phục hồi phiên dùng chung.** Test mới không tạo DLQ ngoài ca chủ động; pending/Unacked/DLQ cũ có nguyên nhân/owner/disposition theo spec. Replay từng message chỉ với quyền cụ thể và payload/ID đã xác minh; không purge hoặc ACK thủ công. Khôi phục đúng runtime/config đã phối hợp, không bật lại producer cũ trái topology.
- [ ] **7. Chốt kết quả kiểm chứng.** Mỗi case có lớp kiểm (fixture/component/live), thời điểm, manifest, trạng thái trước-sau và failure reason. Live chỉ PASS khi mọi ca bắt buộc đã làm có bằng chứng; còn BLOCKED không đổi ClickUp Complete. Chạy lại fixtures/build/lint khi Task8 thay test/runner để tránh hồi quy.

## Task 9: Bàn giao đúng evidence và quyền Git/ClickUp

**Files:** Report results MD; `frontend/docs/integration.md`, `task-1.2-remaining.md`, `task-1.3-progress.md`, `task-1.4-results.md`; `services/booking-service/TESTING.md`, README runtime/tests. Không đổi trạng thái B13/B14 thành APPROVED bằng tài liệu dẫn xuất.

**Interfaces:** Consumes kết quả Tasks1–8. Produces hướng dẫn chạy lại, danh sách OPEN còn lại, commit/source manifest, và nội dung ClickUp khớp evidence.

- [ ] **1. Đồng bộ tài liệu.** Thay ghi chú1.3 “chưa có code”/1.4 “checkout chưa nối” bằng trạng thái đúng SHA; tách phần code xong với nghiệm thu thật. Ghi biến env đã thêm, phiên runtime, topology đã áp, giới hạn fixture/sandbox/device, timing failure nếu còn; không giữ số test cũ làm số hiện tại.
- [ ] **2. Rà cuối.** Chạy `rtk git diff --check`, build/lint/fixtures và verify các module đã đổi nếu chưa có kết quả cho đúng source cuối. Không lặp toàn suite khi không có đổi mới; dùng kết quả đúng commit và không bỏ sót failure/skip/baseline timing. File ngoài task/user edits không được stage nhầm.
- [ ] **3. Chuẩn bị Git bàn giao.** Khi được giao chuỗi finishing: commit chọn lọc/docs còn lại, fetch/kiểm phân kỳ rồi push đúng nhánh được giao. Trước push tính việc workflow Booking có thể auto-deploy119 và tác động phiên shared-runtime; theo dõi CI/deploy đến kết quả đúng SHA. Khi chưa có quyền hoặc test còn blocker, bàn giao local/OPEN thay vì tự push.
- [ ] **4. Cập nhật ClickUp theo quyền được giao.** Task1.1/1.2/1.3/1.4 ghi code, test, blockers và link evidence; Complete chỉ khi tiêu chí tương ứng đạt. Connector hết quota thì giữ nội dung chờ trong report, không tuyên bố đã ghi hoặc tự gửi tin cho thành viên nhóm. Không đổi task Event của Tuyến theo suy đoán chỉ vì source tồn tại.
- [ ] **5. Báo người dùng.** Nêu kết quả chính, validation đúng bản source, rủi ro/OPEN và bước còn lại; không gọi cả hệ thống production-ready từ sandbox hoặc health check.

## Self-Review và handoff

- Spec§1–5→Tasks1/4/8; §6→Tasks1/3; §7→Task4/8; §8→Task2; §9→Task5; §10→bảng Java và Task2; §11→Tasks6–9; §12→cách viết trong Task2; §13→trạng thái/gate của plan. Không có mục triển khai bị bỏ trống hoặc câu “thêm helper cần thiết”.
- M1/M2→M3/M4→M5/M6→M7–M9/M10 giữ tên/types thống nhất. U1–U5 giữ signatures/annotations của spec, kể cả hai khai báo interface/implementation của M3/M4. Không phát sinh delta Java trong plan.
- Review Focus1→U3–U5;2→Task5 delayed reads/mutations;3→Task4 trước bật worker;4→Tasks1/8;5→Tasks6/8. Không tự nâng test thành phần thành E2E cho điều kiện provider chưa tái hiện được.
- Test code chỉ nêu assertions/contract khó suy từ interface; không chép toàn thân implementation. Task1/4 là source/config/runtime có probe-before/after, không viết test giả chỉ để đủ mẫu TDD; Task2/3/5 có RED/GREEN cho hành vi mới.
- Convention đối chiếu: constructor injection và property @Value giữ nguyên; DTO Lombok theo OrderView; repository/mapper/service sẵn được dùng lại; chỉ boundary GET mới thêm owner/input check, không kiểm allocation dư; không thêm migration/framework/DTO chung. Nguồn Shop App và tài liệu Spring Data đã ghi ở spec§12, không hạ version FlashTicket theo mẫu.
- Bản plan này đã được self-review theo spec, chưa triển khai hoặc chạy kiểm chứng ứng dụng trong lượt lập plan. Chờ duyệt **WRITING-PLANS v0.1, C1–C9/M1–M10/U1–U5** và chọn cách thực thi. Đề xuất **Native** vì Tasks1/4/8 dùng chung source, DB, queue và phiên runtime; các bước này không phù hợp chạy song song độc lập.
