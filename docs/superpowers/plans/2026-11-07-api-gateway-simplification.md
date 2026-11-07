# API Gateway Simplification Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Thay 32 route theo endpoint và ~30 luật security theo endpoint của API Gateway bằng ~7 route theo prefix service trong YAML, security thô theo khu vực, và một bộ xử lý lỗi chung — giữ nguyên API, format lỗi và correlation header.

**Architecture:** Route khai báo trong `application.yaml` (Spring Cloud Gateway server-webflux), mỗi route có `RequestRateLimiter` + `CircuitBreaker` không `fallbackUri`. Security chỉ phân biệt công khai / ADMIN / ORGANIZER / chặn / đã đăng nhập; quyền chi tiết do service. `GatewayHttpHandler` tách thành `CorrelationIdFilter` và `GatewayErrorWriter`.

**Tech Stack:** Java 21, Spring Boot 4.1.1, Spring Cloud 2025.1.3 (Gateway server-webflux, LoadBalancer, Resilience4j reactor), Spring Security WebFlux OAuth2 resource server, Redis reactive, JUnit 5, WebTestClient.

**Spec:** `docs/superpowers/specs/2026-11-07-api-gateway-simplification-design.md`

## Global Constraints

- Thư mục làm việc: `infrastructure/api-gateway`. Không sửa service nghiệp vụ, không sửa `infrastructure/config-server`.
- Nền: working tree hiện tại của `dev/integration` (gateway đã bỏ `spring.config.import: configserver` và `spring-cloud-starter-config`). Trước Task 1, xác nhận: `grep -n "import:" src/main/resources/application.yaml` không có `configserver`, và `pom.xml` không có `spring-cloud-starter-config`. Nếu còn → dừng, agent gộp config chưa xong.
- Giữ nguyên tên biến `${...}` đang có trong `application.yaml` (`API_GATEWAY_PORT`, `KEYCLOAK_ISSUER_URI`, `REDIS_*`, `CORS_ALLOWED_ORIGINS`, `INTEGRATION_*_GATEWAY_TIME_LIMIT`, `INTEGRATION_GATEWAY_HTTP_RESPONSE_TIMEOUT`, `EVENT_IMAGES_GATEWAY_TIME_LIMIT`).
- Port 8081 giữ nguyên. Không đổi path API.
- Body lỗi gateway giữ nguyên: `{"code","message","correlationId","violations":[]}`, `Content-Type: application/problem+json`, bảng status → code → message tiếng Việt như `GatewayHttpHandler.write` hiện tại.
- Header `X-Correlation-Id` (UUID, giữ giá trị downstream nếu hợp lệ) và `X-Request-Id` giữ nguyên hành vi.
- Gateway không viết lại body lỗi do service trả.
- Mỗi task kết thúc bằng `./mvnw -B -ntp verify` xanh (Spotless: chạy `./mvnw spotless:apply` trước khi verify).
- Commit chỉ khi người dùng cho phép commit trong phiên thực thi; nếu chưa, bỏ qua bước commit và ghi lại.

## Review Focus

1. **Callback VNPay** (`GET /api/v1/payments/vnpay/return|ipn`) không token, không bị rate limit/circuit breaker → vẫn tới payment (Task 2 test `callbacksBypassFiltersAndAuth`, Task 4).
2. **Path chồng prefix**: `/api/v1/admin/events/{id}/payout` phải tới payment chứ không event; `/api/v1/events/{id}/check-ins` tới ticket; `/api/v1/orders/{id}/payment` tới payment (Task 2 bảng 66 endpoint).
3. **Path gốc không có hậu tố**: `POST /api/v1/payments`, `POST /api/v1/orders`, `GET /api/v1/events` khớp `/**` của route tương ứng (Task 2 bảng 66 endpoint đã chứa).
4. **Service chết / từ chối kết nối** → 503 `DEPENDENCY_UNAVAILABLE` chứ không 500 (Task 3 test `connectionRefusedIs503`).
5. **CORS preflight** tới path trước đây không có route riêng (vd. `OPTIONS /api/v1/admin/refunds`) vẫn 200 với origin cấu hình (Task 4 test `preflightOnNewlyRoutedPaths`).

---

### Task 1: Test nạp `application.yaml` chính

Hiện `src/test/resources/application.yaml` che file chính, và `GatewayHttpTests` import file của config-server. Route YAML ở Task 2 sẽ không được test thấy nếu không sửa.

**Files:**
- Rename: `src/test/resources/application.yaml` → `src/test/resources/application-test.yaml` (bỏ hai khóa `spring.config.import` và `spring.cloud.config.enabled`)
- Modify: `src/test/java/com/flashticket/api_gateway/ApiGatewayApplicationTests.java`, `GatewayHttpTests.java`, `routing/GatewayRouteConfigurationTests.java`, `security/GatewaySecurityConfigurationTests.java` — thêm `@ActiveProfiles("test")`
- Modify: `GatewayHttpTests.java` — bỏ property `spring.config.import=file:../config-server/src/main/resources/config/api-gateway.yaml`

**Interfaces:**
- Produces: profile `test` với `application-test.yaml` = `{eureka.client.enabled: false, spring.security.oauth2.resourceserver.jwt.issuer-uri: https://issuer.invalid/realms/flashticket, REDIS_HOST: 127.0.0.1, REDIS_PORT: 1, REDIS_PASSWORD: fixture, CORS_ALLOWED_ORIGINS: http://localhost:5173}`; mọi `@SpringBootTest` sau này dùng `@ActiveProfiles("test")`.

- [ ] **Step 1:** Đổi tên file, thêm các giá trị placeholder ở Interfaces để `application.yaml` chính resolve được mà không cần env.
- [ ] **Step 2:** Thêm `@ActiveProfiles("test")` vào 4 class test; bỏ property import config-server trong `GatewayHttpTests`.
- [ ] **Step 3:** Chạy `./mvnw -B -ntp verify`. Expected: PASS với số test như trước (đây là baseline — chưa đổi hành vi).
- [ ] **Step 4:** Commit `test(gateway): nạp application.yaml chính qua profile test`.

---

### Task 2: Route theo prefix trong YAML

**Files:**
- Modify: `src/main/resources/application.yaml` — thêm `spring.cloud.gateway.server.webflux.routes`
- Modify: `src/main/java/com/flashticket/api_gateway/routing/GatewayRouteConfiguration.java` — chỉ còn 2 bean
- Delete: `src/main/java/com/flashticket/api_gateway/routing/UserGatewayRoutes.java`
- Rewrite: `src/test/java/com/flashticket/api_gateway/routing/GatewayRouteConfigurationTests.java`

**Interfaces:**
- Consumes: profile `test` (Task 1).
- Produces: bean `RedisRateLimiter redisRateLimiter()` (10, 20, 1 — giữ hằng số hiện có) và `KeyResolver remoteAddressKeyResolver()` (giữ thân hàm hiện có). Route id: `payment-vnpay-callbacks`, `payment`, `ticket`, `booking`, `event-images`, `user`, `event`. Circuit breaker name: `paymentServiceBreaker`, `ticketServiceBreaker`, `bookingServiceBreaker`, `eventImageServiceBreaker`, `userServiceBreaker`, `eventServiceBreaker`.

- [ ] **Step 1: Viết test bảng route (fail)**

`routesEveryServiceEndpointToItsOwner` — `@ParameterizedTest` + `@CsvSource` gồm đúng 66 dòng `METHOD,path,expectedUri` của spec §5.6 (danh sách endpoint lấy từ controller, `{id}` thay bằng `00000000-0000-4000-8000-000000000001`). Thuật toán chọn route (giống `RoutePredicateHandlerMapping`):

```java
Route resolve(HttpMethod method, String path) {
  var exchange = MockServerWebExchange.from(MockServerHttpRequest.method(method, path));
  return routeLocator.getRoutes()
      .sort(Comparator.comparingInt(Route::getOrder))
      .concatMap(r -> Mono.from(r.getPredicate().apply(exchange)).filter(b -> b).map(b -> r))
      .blockFirst();
}
```

Assert: `resolve(m, p).getUri().toString()` == `lb://<service>`.

Thêm:
- `callbacksBypassFiltersAndAuth`: route `payment-vnpay-callbacks` có `getFilters()` rỗng; `GET` callback resolve vào route này, `POST /api/v1/payments/vnpay/ipn` **không** resolve vào route này.
- `standardRoutesRateLimitAndBreak`: 6 route còn lại mỗi route có đúng 2 filter (RequestRateLimiter, CircuitBreaker).
- `eventImagesUseLongTimeout`: `event-images` metadata `response-timeout` == `45000L`.
- `internalAndActuatorAreNotRouted`: `resolve(GET, "/internal/v1/users/x")` và `resolve(GET, "/actuator/health")` == `null`.
- `routeCountIsSeven`.

- [ ] **Step 2:** Chạy `./mvnw -B -ntp test -Dtest=GatewayRouteConfigurationTests`. Expected: FAIL (route id mới chưa có).

- [ ] **Step 3: Thêm route vào `application.yaml`**

Thứ tự bằng `order`: callbacks `-10`, payment `0`, ticket `10`, booking `20`, event-images `30`, user `40`, event `50`. Path đúng spec §5.1. Filter của mỗi route chuẩn (không dùng `default-filters` để callbacks không bị lọc):

```yaml
filters:
  - name: RequestRateLimiter
    args:
      rate-limiter: "#{@redisRateLimiter}"
      key-resolver: "#{@remoteAddressKeyResolver}"
  - name: CircuitBreaker
    args:
      name: paymentServiceBreaker
```

`event-images`: `metadata.response-timeout: ${gateway.event-images.response-timeout-ms:45000}`.

- [ ] **Step 4:** Rút `GatewayRouteConfiguration` còn 2 bean ở Interfaces; xóa `customRouteLocator`, `standardFilters`, `UserGatewayRoutes`.
- [ ] **Step 5:** Chạy lại test Task 2. Expected: PASS.
- [ ] **Step 6:** Chạy `./mvnw -B -ntp verify`. Expected: test route PASS; `GatewayHttpTests`/security tests có thể fail ở ca phụ thuộc `/fallback/**` — sửa ở Task 3/4, ghi lại tên test fail. Không được fail ở ca định tuyến.
- [ ] **Step 7:** Commit `refactor(gateway): định tuyến theo prefix service trong YAML`.

---

### Task 3: Correlation filter + bộ ghi lỗi chung, bỏ fallback

**Files:**
- Create: `src/main/java/com/flashticket/api_gateway/web/CorrelationIdFilter.java`
- Create: `src/main/java/com/flashticket/api_gateway/web/GatewayErrorWriter.java`
- Delete: `src/main/java/com/flashticket/api_gateway/web/GatewayHttpHandler.java`
- Delete: `src/main/java/com/flashticket/api_gateway/routing/GatewayFallbackController.java`, `src/test/java/com/flashticket/api_gateway/routing/GatewayFallbackControllerTests.java`
- Modify: `src/main/java/com/flashticket/api_gateway/security/GatewaySecurityConfiguration.java` — entry point / access denied gọi `GatewayErrorWriter`
- Modify: `src/test/java/com/flashticket/api_gateway/GatewayHttpTests.java`

**Interfaces:**
- Produces:
  - `@Component @Order(Ordered.HIGHEST_PRECEDENCE) public final class CorrelationIdFilter implements WebFilter` — toàn bộ phần `filter(...)` của `GatewayHttpHandler` hiện tại (correlation/request id, `beforeCommit`, log WARN, decorator 429), gọi `GatewayErrorWriter.write` cho 429. Hằng `CORRELATION = "X-Correlation-Id"`, `REQUEST = "X-Request-Id"` public static.
  - `@Component @Order(-2) public final class GatewayErrorWriter implements WebExceptionHandler` với `public Mono<Void> write(ServerWebExchange exchange, HttpStatusCode status, Throwable cause)` (thân = `write` hiện tại) và `handle(...)`.
- Quy tắc `handle` (tìm trong cause chain tối đa 8 tầng):
  - `ResponseStatusException` → status của nó (Gateway đã map `TimeoutException`→504, `CallNotPermittedException`→503, không có instance→503 thành `ResponseStatusException`).
  - `java.util.concurrent.TimeoutException`, `io.netty.handler.timeout.ReadTimeoutException` → 504.
  - `java.net.ConnectException`, `io.netty.channel.ConnectTimeoutException`, `io.github.resilience4j.circuitbreaker.CallNotPermittedException` → 503.
  - Còn lại → 500.

- [ ] **Step 1: Sửa/viết test (fail)** trong `GatewayHttpTests`:
  - Giữ các test hiện có, đổi ca "fallback" thành: `timesOutAs504WithCorrelatedProblem` (backend chậm hơn `timeoutDuration=1s` → 504, `code=DEPENDENCY_TIMEOUT`, header `X-Correlation-Id` là UUID).
  - `openBreakerIs503`: đặt `resilience4j.circuitbreaker.instances.bookingServiceBreaker.minimumNumberOfCalls=100` như cũ; transition breaker sang OPEN qua `CircuitBreakerRegistry` bean trong test → `GET /api/v1/events/{id}/availability` trả 503 `DEPENDENCY_UNAVAILABLE`, backend không bị gọi (`CALLS == 0`).
  - `connectionRefusedIs503`: service id không có backend sống (instance trỏ port đã đóng) → 503 `DEPENDENCY_UNAVAILABLE`.
  - `fallbackPathIsGone`: `GET /fallback/booking` → 404 `ROUTE_NOT_FOUND` (không route, không controller).
- [ ] **Step 2:** Chạy `./mvnw -B -ntp test -Dtest=GatewayHttpTests`. Expected: FAIL ở các ca mới.
- [ ] **Step 3:** Tạo 2 class theo Interfaces, xóa `GatewayHttpHandler`, `GatewayFallbackController` và test của nó; trỏ `GatewaySecurityConfiguration` sang `GatewayErrorWriter`.
- [ ] **Step 4:** Chạy `./mvnw -B -ntp verify`. Expected: PASS toàn bộ trừ các assertion security theo endpoint cũ (sửa ở Task 4) — ghi lại nếu có.
- [ ] **Step 5:** Commit `refactor(gateway): tách correlation filter và bộ ghi lỗi, bỏ fallback controller`.

---

### Task 4: Security thô theo khu vực

**Files:**
- Modify: `src/main/java/com/flashticket/api_gateway/security/GatewaySecurityConfiguration.java`
- Rewrite: `src/test/java/com/flashticket/api_gateway/security/GatewaySecurityConfigurationTests.java`
- Modify: `src/test/java/com/flashticket/api_gateway/GatewayHttpTests.java` — các ca yêu cầu BUYER ở gateway

**Interfaces:**
- Consumes: `GatewayErrorWriter.write(...)` (Task 3), `KeycloakRealmRoleConverter`, `GatewayCorsProperties` (không đổi).
- Produces: `SecurityWebFilterChain springSecurityFilterChain(...)` với đúng thứ tự luật spec §5.2.

- [ ] **Step 1: Viết test (fail)** dùng `WebTestClient.bindToApplicationContext(...)` + `SecurityMockServerConfigurers.mockJwt().authorities(...)` như test hiện có:
  - `publicEndpointsNeedNoToken`: không token → không phải 401 cho `GET /api/v1/events`, `/api/v1/events/{id}`, `/api/v1/events/{id}/availability`, `/api/v1/categories`, `/api/v1/venues`, `/api/v1/organizers/{id}`, `/api/v1/payments/vnpay/return`, `/api/v1/payments/vnpay/ipn`.
  - `everythingElseNeedsToken`: không token → 401 `UNAUTHENTICATED` + `WWW-Authenticate: Bearer` cho `GET /api/v1/events/{id}/reconciliation`, `POST /api/v1/orders`, `GET /api/v1/me`, `POST /api/v1/payments`, `GET /api/v1/tickets/{id}/qr`.
  - `adminAreaNeedsAdmin`: `ROLE_BUYER` → 403 `ACCESS_DENIED` cho `GET /api/v1/admin/refunds`, `GET /api/v1/admin/events`, `GET /api/v1/admin/organizer-applications`; `ROLE_ADMIN` → không 401/403.
  - `organizerAreaNeedsOrganizer`: `ROLE_BUYER` → 403 cho `GET /api/v1/organizer/events`; `ROLE_ORGANIZER` → không 401/403.
  - `fineGrainedRolesAreLeftToServices`: `ROLE_ORGANIZER` gọi `POST /api/v1/orders` → **không** bị gateway trả 403 (request được chuyển đi).
  - `internalAndActuatorDenied`: `ROLE_ADMIN` → 403 cho `/internal/v1/users/x`, `/actuator/health`.
  - `preflightOnNewlyRoutedPaths`: `OPTIONS /api/v1/admin/refunds` với `Origin: http://localhost:5173`, `Access-Control-Request-Method: POST` → 200 và `Access-Control-Allow-Origin: http://localhost:5173`.
- [ ] **Step 2:** Chạy `./mvnw -B -ntp test -Dtest=GatewaySecurityConfigurationTests`. Expected: FAIL ở `fineGrainedRolesAreLeftToServices` và `adminAreaNeedsAdmin` (refunds).
- [ ] **Step 3:** Thay `authorizeExchange` bằng đúng bảng luật spec §5.2; xóa luật `/fallback/**`, mọi luật theo endpoint còn lại, khối comment-out và Javadoc cũ ở cuối class.
- [ ] **Step 4:** Trong `GatewayHttpTests`, đổi ca `requiresBuyerForAllOrderPathsAndBlocksInternalAndFallbackPaths` và `blocksBuyerApprovalBeforeProxyingAndAllowsAdminApproval` sang kỳ vọng mới: thiếu token → 401; sai role ngoài vùng admin/organizer → request tới backend; `/internal/**` → 403.
- [ ] **Step 5:** Chạy `./mvnw -B -ntp verify`. Expected: PASS.
- [ ] **Step 6:** Commit `refactor(gateway): phân quyền thô theo khu vực, chi tiết để service kiểm`.

---

### Task 5: Gộp cấu hình Resilience4j

**Files:**
- Modify: `src/main/resources/application.yaml` (`resilience4j.*`)
- Test: `src/test/java/com/flashticket/api_gateway/routing/GatewayRouteConfigurationTests.java`

**Interfaces:**
- Produces: `resilience4j.circuitbreaker.configs.default` = `{registerHealthIndicator: true, slidingWindowType: COUNT_BASED, slidingWindowSize: 10, minimumNumberOfCalls: 5, failureRateThreshold: 50, waitDurationInOpenState: 10s, permittedNumberOfCallsInHalfOpenState: 3, automaticTransitionFromOpenToHalfOpenEnabled: true}`; `instances.<6 breaker>.base-config: default`. `timelimiter.instances` giữ nguyên giá trị và biến đang có.

- [ ] **Step 1: Test (fail trước khi gộp chỉ nếu thiếu breaker)** `breakersShareDefaultConfig`: với `CircuitBreakerRegistry`, cả 6 breaker có `slidingWindowSize == 10`, `minimumNumberOfCalls == 5`, `failureRateThreshold == 50f`; `TimeLimiterRegistry` cho `eventImageServiceBreaker` = 50s mặc định, `bookingServiceBreaker` = giá trị của `INTEGRATION_BOOKING_GATEWAY_TIME_LIMIT` mặc định trong YAML.
- [ ] **Step 2:** Thay 5–6 khối lặp bằng `configs.default` + `base-config`; thêm `eventImageServiceBreaker` nếu chưa có instance; xóa khối comment giải thích tham số ở cuối file (đã nằm trong spec).
- [ ] **Step 3:** Chạy `./mvnw -B -ntp verify`. Expected: PASS.
- [ ] **Step 4:** Commit `refactor(gateway): gộp cấu hình circuit breaker dùng chung`.

---

### Task 6: Kiểm trên stack local

**Files:** không sửa code (chỉ chạy).

- [ ] **Step 1:** `cd D:/DATN/flash-ticket-platform && docker compose --env-file .env -f deploy/docker-compose.integration.local.yml up -d --build api-gateway`. Expected: container `running`, Eureka vẫn thấy 6 app.
- [ ] **Step 2:** Smoke qua `http://127.0.0.1:8081`:
  - `GET /api/v1/events` → 200; `GET /api/v1/categories` → 200; `GET /api/v1/events/{id thật}/availability` → 200 hoặc lỗi nghiệp vụ của booking (không phải 404 gateway).
  - `GET /api/v1/me`, `POST /api/v1/orders` không token → 401 body gateway.
  - `GET /api/v1/admin/refunds` không token → 401.
  - `GET /internal/v1/users/x` → 401/403, không tới user-service.
  - `GET /api/v1/payments/vnpay/ipn` → body payment (`"RspCode":"97"`), không 401.
- [ ] **Step 3:** Đếm Java chính: `find src/main/java -name '*.java' | xargs wc -l | tail -1`. Expected: ≤ ~400 dòng.
- [ ] **Step 4:** Ghi kết quả smoke vào cuối spec (mục mới "Bằng chứng kiểm tra") và commit `docs(gateway): ghi bằng chứng kiểm tra rút gọn gateway`.
