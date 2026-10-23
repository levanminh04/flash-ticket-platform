# Payment P2 — Query API, Repositories and Security Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Cung cấp GET `/api/v1/orders/{orderId}/payment` đọc đúng trạng thái của chủ đơn từ PostgreSQL, có JWT/role/ownership, lỗi Problem nhất quán và không thay đổi dữ liệu.

**Architecture:** Controller mỏng gọi PaymentQueryService trong transaction chỉ đọc; service dùng repository cục bộ và mapper đã có. Security tại Payment xác minh JWT độc lập với Gateway; inbox chỉ được đọc ở P2, consumer/relay thuộc P3. Giữ controller/service/repository theo kiến trúc đã thống nhất.

**Tech Stack:** Java 21, Spring Boot 4.1.1, Spring Security servlet Resource Server, JPA/PostgreSQL, Jackson 3, MapStruct, JUnit/AssertJ và Maven Wrapper hiện có.

**Spec:** `docs/superpowers/specs/2026-10-23-payment-service-runtime-design.md`, mục 3–4, 6, 8, 12–14; tiếp nối P1 đã hoàn thành tại `31b67de`.

## Global Constraints

- “Giữ Java 21, Spring Boot/Cloud và dependency trong pom hiện tại; không nâng stack trong đợt này.”
- “Payment chỉ truy cập datastore của chính mình.”
- “API không serialize entity.”
- “Không thêm public POST start/refund hoặc endpoint sửa state cho dev tại Payment.”
- “Không chỉ tin Gateway.”
- “GET không freeze, tạo attempt, gọi provider hoặc enqueue tác vụ.”
- “DDL là nguồn schema, Hibernate chỉ validate.”
- “Không log JWT, raw callback, secret, full email hoặc SQL bind nhạy cảm.”
- Làm trực tiếp trên worktree `C:/Users/WEOHWOEH/.codex/worktrees/payment-runtime/flash-ticket-platform`, nhánh `codex/payment-runtime`; giữ checkout gốc và toàn bộ thay đổi P1.
- Test database phải tách biệt; không dùng H2/mock để nghiệm thu JPA/PostgreSQL. Không thêm dependency ngoài các starter/test library đang có; test JWT ký thật dùng Nimbus JOSE đang có qua oauth2-jose.

## Review Focus

1. requestId tồn tại nhưng thuộc đơn khác không được tiết lộ kết quả hoặc trả CREATED của latestAttempt; Task 2.
2. Hai attempt có cùng startedAt phải có thứ tự ổn định; GET nhiều query không được ghép dữ liệu từ các thời điểm commit khác nhau; Task 1–2.
3. HTTP test dùng mock JWT có thể bỏ qua issuer/audience/chữ ký/expiry; phải có phép kiểm bearer token ký thật qua decoder thật; Task 3.
4. Header correlationId giả, URI orderId sai và lỗi trước controller vẫn phải trả Problem hợp lệ mà không lộ đầu vào nguy hiểm; Task 4.
5. Smoke context hiện loại JPA; thêm repository/security không được vô tình làm smoke phụ thuộc database hoặc Keycloak đang chạy; Task 3–5.

## Phạm vi và quyết định đã cụ thể hóa

- P2 hoàn thành read use case, không triển khai repository cho mọi bảng. Event financial summary và internal credential thuộc P8; provider callbacks P7; P2 chưa mở route này.
- P2 có repository đọc inbox để thể hiện kết quả được lưu bởi consumer tương lai. Không cần broker để thử: test tạo fixture inbox hợp lệ trong DB riêng. Consumer name cố định `payment.start-requested.v1`, khớp spec P3.
- Snapshot không có: 404 PAYMENT_NOT_READY; khác chủ: 404 RESOURCE_NOT_FOUND. Không nhận buyerSubject từ query/header để đổi người đọc. JWT cần subject không rỗng và role BUYER.
- Query không requestId: startRequest=null. Có requestId nhưng chưa có inbox: WAITING, không phụ thuộc latestAttempt. Có inbox: đọc StartPaymentResult qua ContractCodec/MessageJsonMapper; result.requestId phải khớp query, result.orderId khớp order. CREATED có attemptId phải tồn tại và thuộc đúng order; REJECTED giữ failureCode gốc.
- requestId đã biết thuộc đơn khác: 404 RESOURCE_NOT_FOUND, không trả payload. Payload inbox hỏng hoặc CREATED trỏ attempt không tồn tại là 503 PAYMENT_STATE_UNAVAILABLE, không dựng trạng thái giả. Lỗi này log code/correlation đã lọc, không log response_payload.
- PaymentQueryService dùng `@Transactional(readOnly=true, isolation=REPEATABLE_READ)` để các SELECT của một response thấy cùng snapshot DB; không khóa ghi. Entity/association không được dùng ngoài transaction.
- latestAttempt sắp `startedAt DESC, id DESC` theo thứ tự UUID PostgreSQL; không dùng finishedAt hoặc insertion order. Refunds sắp `createdAt ASC, id ASC` để response ổn định.
- Owner access đọc bằng projection scalar orderId/buyerSubject/expiresAt; tránh load các inverse association không cần thiết. DTO response giữ nguyên B13; không thêm trường trạng thái Saga vào PaymentView.

## File map

`main` = `services/payment-service/src/main/java/com/flashticket/payment_service`; `test` = `services/payment-service/src/test/java/com/flashticket/payment_service`.

| Create/modify | Trách nhiệm |
| --- | --- |
| Create `main/repository/OrderPaymentSnapshotRepository.java` | Projection `OrderPaymentAccess` và lookup quyền đọc |
| Create `main/repository/PaymentAttemptRepository.java` | Latest attempt, lookup id+order cho result CREATED |
| Create `main/repository/PaymentConfirmationRepository.java` | Lookup accepted chargeId theo order |
| Create `main/repository/RefundRepository.java` | Refund list có thứ tự |
| Create `main/repository/InboxMessageRepository.java` | Inbox theo composite ID |
| Create `main/service/payment/PaymentQueryService.java` | Ownership, request result và dựng PaymentView |
| Create `main/controller/PaymentQueryController.java` | GET endpoint và principal subject |
| Create `main/exception/PaymentQueryException.java` | Status/code của các lỗi read use case |
| Create `main/exception/PaymentExceptionHandler.java` | HTTP application errors → Problem |
| Create `main/security/PaymentJwtProperties.java` | Issuer/JWK/audience bắt buộc từ cấu hình |
| Create `main/security/PaymentJwtValidators.java` | Issuer/time/audience và subject không rỗng |
| Create `main/security/KeycloakRealmRoleConverter.java` | Role BUYER/ORGANIZER/ADMIN theo quy ước Gateway |
| Create `main/config/PaymentSecurityConfiguration.java` | JwtDecoder, servlet SecurityFilterChain, converter |
| Create `main/security/PaymentSecurityProblemHandler.java` | AuthenticationEntryPoint/AccessDeniedHandler cùng shape Problem |
| Create `main/web/PaymentCorrelationFilter.java` | Correlation attribute/header/MDC trước security |
| Create `main/web/PaymentProblemFactory.java` | Dựng Problem từ correlation đã xác minh và violation |
| Create `test/entity/PaymentReadFixtures.java` | Persist graph Payment hợp lệ, không endpoint dev |
| Create `test/repository/PaymentReadRepositoriesTest.java` | PostgreSQL query/order/projection |
| Create `test/service/PaymentQueryServiceTest.java` | Rule đọc, ownership và result consistency |
| Create `test/controller/PaymentQueryControllerTest.java` | MVC mapping, response/error, principal delegation |
| Create `test/security/PaymentJwtSecurityTest.java` | JWT ký thật, JWK loopback và HTTP authorization |
| Create `test/security/KeycloakRealmRoleConverterTest.java` | Role claim hợp lệ/sai kiểu |
| Create `test/web/PaymentCorrelationFilterTest.java` | Header/path/error correlation |
| Create `test/config/PaymentReadRepositoryTestConfiguration.java` | Test-only mock repositories cho smoke context đã loại JPA |
| Modify `test/PaymentServiceApplicationTests.java`, `test/mapper/MapperBeansTest.java`, `test/mapper/PaymentMappersTest.java`, `test/config/PaymentContractComponentsTest.java` | Import test-only repository beans, không làm smoke cần DB |
| Modify `services/payment-service/pom.xml` | Mở rộng profile DB test riêng, không giảm gate mặc định |
| Modify `services/payment-service/src/test/resources/application.yaml` | Test-only issuer/JWK/audience không có secret, không gọi network khi init |
| Modify `services/payment-service/src/test/resources/application-mapping-test.yaml` | Giữ mapping-test bật JPA và config test ổn định |
| Modify `infrastructure/config-server/src/main/resources/config/payment-service.yaml` | JWT runtime từ environment, chỉ Payment |
| Modify `services/payment-service/README.md` | Chạy API, fixture, kết quả test và phần chưa tích hợp |

## Task 1: Read repositories verified on isolated PostgreSQL

**Interfaces:** `OrderPaymentSnapshotRepository.findAccessByOrderId(UUID): Optional<OrderPaymentAccess>` với projection `UUID getOrderId(); String getBuyerSubject(); Instant getExpiresAt();`. `PaymentAttemptRepository.findFirstByOrderIdOrderByStartedAtDescIdDesc(UUID): Optional<PaymentAttempt>` và `findByIdAndOrderId(UUID,UUID): Optional<PaymentAttempt>`. `PaymentConfirmationRepository.findChargeIdByOrderId(UUID): Optional<UUID>`. `RefundRepository.findAllByOrderIdOrderByCreatedAtAscIdAsc(UUID): List<Refund>`. `InboxMessageRepository.findById(InboxMessageId): Optional<InboxMessage>`.

- [ ] **Step 1 — Test đỏ:** tạo PaymentReadFixtures trong package entity để truy cập constructor protected đúng phạm vi. Tạo hai order khác owner; attempts terminal cho cùng order có startedAt bằng nhau, UUID cố định ...0010/...0020 và assert latest=...0020; order khác không ảnh hưởng. Test confirmation có/không, refunds đúng order/thứ tự và inbox composite key đúng consumer.
- [ ] **Step 2 — Thiết lập DB test:** dùng PostgreSQL container mới, tên/port riêng, không mount volume deploy; áp 30-payment.sql rồi table prefix và app grants theo README hiện có. Lấy password ngẫu nhiên qua environment, không in secret. Trước tạo kiểm tên/port không trùng. Không dùng database đang có vì fixture concurrency có commit.
- [ ] **Step 3 — Chạy đỏ:** `.\mvnw.cmd -Pquery-tests "-Dtest=PaymentReadRepositoriesTest" test -B -ntp`; profile này phải chạy với PostgreSQL/JPA thật và fail khi thiếu PAYMENT_TEST_DB_*; không skip thiếu DB. Cần skeleton repository trước test assertion nếu compile đỏ do thiếu class; quan sát test fail vì query thiếu/sai trước hiện thực.
- [ ] **Step 4 — Triển khai:** chỉ tạo năm repository cần đọc, JPQL scalar alias rõ cho access/chargeId, reuse entity mapping. Không query datastore Booking hoặc thêm association/cascade. Profile query-tests chỉ gồm test DB query và có thể mapping suite; default exclude riêng các test bắt buộc DB giống cơ chế EntityMappingTest hiện có.
- [ ] **Step 5 — Chạy xanh:** cùng lệnh; thêm test projection không load confirmation/refund/attempt association qua Hibernate statistics khi cần xác minh N+1. Không viết test chỉ kiểm tên method/repository interface.
- [ ] **Step 6 — Commit:** `feat(payment): add read repositories for payment status` cùng test/profile liên quan.

## Task 2: Ownership-aware payment query service

**Interfaces:** `PaymentQueryService.getPayment(UUID orderId, UUID requestId, String buyerSubject): PaymentView`. Constructor inject năm repository, PaymentViewMapper, PaymentStartMapper, RefundMapper và MessageJsonMapper/ContractCodec. `PaymentQueryException(HttpStatus status,String code)` chỉ mang code/message an toàn, không payload hoặc subject của owner khác.

- [ ] **Step 1 — Test đỏ rule:** mock repository để kiểm orchestration trên entity fixture, dùng mapper thật khi kiểm wire money/URL. Owner đúng đọc DTO; snapshot thiếu 404 PAYMENT_NOT_READY; owner khác 404 RESOURCE_NOT_FOUND; blank subject không đọc dữ liệu. Assert acceptedChargeId từ confirmation, refunds mapped đúng và UNKNOWN luôn paymentUrl=null.
- [ ] **Step 2 — Test đỏ request:** không requestId→null; chưa có inbox→WAITING dù latestAttempt đã có; REJECTED giữ code; CREATED của request cũ vẫn CREATED ngay cả latestAttempt khác. Inbox order khác→404; result requestId khác, payload sai hoặc CREATED attempt thiếu→503. Không trả hoặc log payload lỗi.
- [ ] **Step 3 — Triển khai:** kiểm owner trước các lookup nhạy cảm; dùng consumerName cố định và query attempt theo id+order khi CREATED. Map result state bằng enum tường minh; không dùng enum.valueOf từ trường JSON chưa validate. `@Transactional(readOnly=true,isolation=REPEATABLE_READ)` trên public method do controller gọi qua Spring proxy.
- [ ] **Step 4 — Chạy xanh unit:** `.\mvnw.cmd "-Dtest=PaymentQueryServiceTest" test -B -ntp`; assert không có save/delete/flush hoặc provider/outbox dependency. Mock chỉ dùng kiểm rule của query service, không tuyên bố đó là kiểm transaction DB.
- [ ] **Step 5 — Kiểm DB:** thêm case vào PaymentReadRepositoriesTest gọi service qua Spring bean và kiểm repeatable-read bằng hai transaction với barrier: transaction đối thủ insert attempt/confirmation sau snapshot SELECT đầu; response đầu không ghép state mới, lần GET sau đọc state đã commit. So dữ liệu/tổng bản ghi trước/sau GET, không dựa readOnly annotation để kết luận không ghi.
- [ ] **Step 6 — Commit:** `feat(payment): query payment state with ownership and request results`.

## Task 3: Payment Resource Server and real-token security tests

**Interfaces:** `PaymentJwtProperties` là validated configuration properties prefix `flashticket.payment.jwt`, gồm issuerUri/jwkSetUri/audience nonblank. `PaymentJwtValidators.create(PaymentJwtProperties): OAuth2TokenValidator<Jwt>`. `KeycloakRealmRoleConverter.convert(Jwt): Collection<GrantedAuthority>`. Bean `JwtDecoder paymentJwtDecoder(PaymentJwtProperties)` và servlet SecurityFilterChain; không dùng reactive config Gateway tại Payment.

- [ ] **Step 1 — Test đỏ:** audience thiếu/sai bị reject, issuer sai bị reject, expired quá clock skew bị reject, subject null/blank bị reject; valid token có BUYER được qua. Role converter chỉ lấy realm_access.roles thuộc BUYER/ORGANIZER/ADMIN, ignore claim sai kiểu/default Keycloak/role lạ; không lấy role do header browser gửi.
- [ ] **Step 2 — Triển khai:** NimbusJwtDecoder với JWK set URI tường minh và JwtValidators default time/issuer cộng audience/subject validator. Chỉ GET `/api/v1/orders/*/payment` có role BUYER; stateless bearer auth, tắt form login/basic/session, CSRF không cần cho stateless bearer API. Các route internal/callback/management chưa được mở bởi P2; default deny. Không cho ADMIN bypass owner của buyer API.
- [ ] **Step 3 — Cấu hình:** Config Server Payment thêm flashticket.payment.jwt.* từ KEYCLOAK_ISSUER_URI/KEYCLOAK_JWK_SET_URI/KEYCLOAK_AUDIENCE; không có fallback permissive production. Test YAML dùng issuer/JWK loopback giả và audience fixture; decoder không tải network lúc context init. Vì smoke YAML đã loại JPA, tạo @TestConfiguration với năm repository mock bằng Mockito.mock trong @Bean methods và @Import vào bốn smoke test class trong file map; không import config này vào PostgreSQL tests và không thêm mock/conditional fallback vào production. Giữ mọi smoke/P1 test chạy được không DB/Keycloak.
- [ ] **Step 4 — Test bearer ký thật:** PaymentJwtSecurityTest dùng RSA key ephemeral, public JWK phục vụ bởi JDK HttpServer bind 127.0.0.1 port động; token ký bằng Nimbus JOSE từ dependency hiện có. HTTP MockMvc gửi Authorization Bearer qua filter/decoder thật, không dùng `.with(jwt())` cho các case signature/issuer/audience/expiry. Test valid owner 200, no bearer/invalid signature/wrong audience/wrong issuer/expired/blank subject 401, role thiếu hoặc ADMIN-only 403. Đóng HttpServer trong test cleanup; không log private key/token.
- [ ] **Step 5 — Chạy xanh:** `.\mvnw.cmd "-Dtest=PaymentJwtSecurityTest,KeycloakRealmRoleConverterTest,PaymentContractComponentsTest,MapperBeansTest" test -B -ntp`; xác nhận default contexts không bị network dependency mới. Authorization test dùng fixture/mocked query service có chủ ý; repository DB test độc lập ở Task 1–2.
- [ ] **Step 6 — Commit:** `feat(payment): secure status API with validated buyer JWTs` cùng config/test.

## Task 4: GET controller and consistent Problem responses

**Interfaces:** `PaymentQueryController.getPayment(UUID orderId, UUID requestId, Jwt jwt): PaymentView`, mapping GET `/api/v1/orders/{orderId}/payment`, requestId query optional. `PaymentProblemFactory` dựng Problem từ request correlation/code/message/violations; dùng chung cho advice và security handler. `PaymentCorrelationFilter` chạy trước BearerTokenAuthenticationFilter để lỗi 401 cũng có correlation.

- [ ] **Step 1 — Test đỏ endpoint:** JSON PaymentView đúng B13, UUID query/path sai→400, no requestId đúng null, GET không body price/owner override; controller truyền jwt.subject vào service, không đọc buyerSubject của browser. POST cùng path không được xử lý như start; không đăng ký CRUD khác.
- [ ] **Step 2 — Correlation:** orderId hợp lệ trong path là correlationId ưu tiên. Nếu path chưa có orderId hợp lệ, nhận X-Correlation-Id chỉ khi UUID canonical; nếu sai/missing tạo UUID mới. Không echo raw header vào Problem/log. Đặt response X-Correlation-Id và request attribute; MDC được restore/remove trong finally.
- [ ] **Step 3 — Lỗi:** PaymentExceptionHandler map PaymentQueryException; UUID/type mismatch→400 INVALID_REQUEST với Violation; ContractValidationException từ HTTP application boundary→422 nếu đúng caller input. Hỏng inbox đã chuyển thành query 503 ở service, không biến thành lỗi người dùng 422. Lỗi không dự kiến→500 INTERNAL_ERROR với message chung; không lộ SQL/stacktrace/subject. Security entry point/denied handler trả 401 AUTHENTICATION_REQUIRED hoặc 403 ACCESS_DENIED theo cùng Problem shape. Có thể dùng application/json theo DTO B13, không bọc thêm ApiResponse.
- [ ] **Step 4 — Chạy xanh:** `.\mvnw.cmd "-Dtest=PaymentQueryControllerTest,PaymentCorrelationFilterTest,PaymentJwtSecurityTest,PaymentQueryServiceTest" test -B -ntp`; test lỗi trước controller, header sai, requestId khác order và assertion cache headers. GET PaymentView dùng `Cache-Control: no-store` vì dữ liệu cá nhân/payment URL, không đưa buyer data vào shared cache.
- [ ] **Step 5 — Commit:** `feat(payment): expose read-only payment status and Problem errors`.

## Task 5: Runtime verification, documentation and handoff

- [ ] **Step 1 — Gateway check:** xác nhận route GET `/api/v1/orders/*/payment` vào Payment và POST `/api/v1/orders/*/payment-attempts` vào Booking đã có; không sửa route rộng nếu đang đúng. Nếu sai, sửa đúng route/method và thêm test tại Gateway với scope rõ; không đổi nghiệp vụ Booking.
- [ ] **Step 2 — Verify mặc định:** từ Payment chạy `.\mvnw.cmd verify -B -ntp` với JDK 21/UTC; mọi P1 và P2 unit/MVC/security test PASS, Spotless/SpotBugs PASS. Không đổi default suite thành bắt buộc DB.
- [ ] **Step 3 — Verify DB:** `.\mvnw.cmd -Pquery-tests verify -B -ntp`, rồi `-Pmapping-tests verify` khi profile test DB đã khớp; schema chỉ validate, fixture rollback hoặc commit trong DB riêng. Cả repository/transaction và 13 entity mappings được kiểm thực. Test bị chặn môi trường phải báo FAIL/NOT_RUN cụ thể, không skip làm xanh.
- [ ] **Step 4 — Runtime smoke:** với Config Server + PostgreSQL test, chạy Payment ở local-foundation và credential test; request JWT sai bị 401, endpoint không bearer trả Problem. Happy path có thể kiểm bằng server test JWK và DB fixture có vòng đời rõ; không tạo endpoint dev. Tắt process đúng PID/session vừa tạo khi xong, không tắt service người dùng đang chạy.
- [ ] **Step 5 — Tài liệu:** README ghi file/endpoint/role/config, command chạy test, dữ liệu fixture, expected/actual, commit và giới hạn WAITING trước P3 consumer. Không dùng token/secret thật trong ví dụ; không công bố internal/management route. PostgreSQL/Rabbit/VNPay/E2E phân biệt rõ kết quả từng phần; P2 không chứng minh Saga đã chạy.
- [ ] **Step 6 — Review độc lập:** review toàn range P2 so với plan/spec; mỗi lỗi quan trọng có test RED→GREEN rồi full verify. Commit kết quả, giữ nhánh/worktree; không tự merge/push/deploy.

## Execution and handoff

Giữ cách thực thi đã chọn: làm trực tiếp trong chat hiện tại trên nhánh codex/payment-runtime, TDD từng task và một review độc lập cuối gói. Không tạo worktree mới, không hỏi lại execution method.

Plan P2 này cần người dùng review trước khi sửa product code theo gate brainstorming/writing-plans. Sau khi duyệt, thực hiện tất cả task P2 liên tục, không dừng hỏi từng lớp. P3–P8 tiếp tục theo spec đã có với kế hoạch và checkpoint riêng; không xem P2 là toàn bộ Payment hoàn thành.
