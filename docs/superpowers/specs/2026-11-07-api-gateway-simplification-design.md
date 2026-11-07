# API Gateway — rút gọn route, security và xử lý lỗi

- Ngày: 2026-11-07
- Trạng thái: **APPROVED — người dùng yêu cầu viết plan ngày 2026-11-07**
- Người yêu cầu: Sơn
- Phạm vi: `infrastructure/api-gateway` (code, YAML, test). Không đổi service nghiệp vụ.

## 1. Ý định

Gateway hiện ~1.120 dòng Java vì khai báo **từng endpoint**: 32 route (mỗi route lặp lại cùng
khối rate limit + circuit breaker, ràng buộc method) và ~30 `pathMatchers` lặp lại phân quyền mà
5 service đã tự kiểm (cả 5 đều là OAuth2 resource server và có `hasRole`/`@PreAuthorize`).
Hệ quả: mỗi endpoint mới phải sửa gateway, và luật quyền ở gateway dễ lệch với service.

Mục tiêu: gateway làm đúng việc của edge — **định tuyến theo prefix của service, xác thực JWT,
phân quyền thô theo khu vực, rate limit, circuit breaker, correlation id, lỗi JSON thống nhất** —
gọn, dễ đọc, theo cách dự án thực tế hay làm. Phân quyền chi tiết thuộc về service.

## 2. Mục tiêu / Không mục tiêu / Phản mục tiêu

**Mục tiêu**

1. Route khai báo trong YAML, mỗi service một route (cộng vài route đặc biệt), không ràng buộc method.
2. Mọi request không thuộc danh sách công khai phải có JWT hợp lệ (401 nếu thiếu/sai).
3. Gateway chỉ kiểm role theo khu vực: `/api/v1/admin/**` → ADMIN, `/api/v1/organizer/**` → ORGANIZER.
4. Bỏ fallback controller; lỗi circuit breaker/timeout đi qua một bộ ghi lỗi chung.
5. Tách `GatewayHttpHandler` thành filter correlation id và bộ ghi lỗi.
6. Resilience4j dùng cấu hình mặc định chung, không lặp 5 khối.
7. Java chính còn khoảng ~350 dòng (từ ~1.120).

**Không mục tiêu**

- Đổi API, path, port hoặc service nghiệp vụ.
- Đổi định dạng lỗi JSON hay header correlation (frontend và service đang dựa vào).
- Sửa rate limit theo IP khi đứng sau proxy/ngrok (ghi nhận ở §7, làm sau).
- Thêm tính năng mới (auth token relay, header user-id, caching…).

**Phản mục tiêu** (chạy được vẫn là thất bại)

- Một endpoint đang chạy qua gateway bị 404/route nhầm service sau thay đổi.
- `/internal/**` hoặc `/actuator/**` truy cập được từ ngoài.
- Callback VNPay (`return`, `ipn`) bị yêu cầu JWT hoặc bị rate limit/circuit breaker chặn.
- Format lỗi (`code`, `message`, `correlationId`, `violations`) hoặc header `X-Correlation-Id`/`X-Request-Id` thay đổi.

## 3. Ràng buộc

- Spring Boot 4.1.1, Spring Cloud 2025.1.3, Spring Cloud Gateway **server-webflux**; Java 21.
- Định tuyến qua Eureka (`lb://<service>`), port gateway 8081 giữ nguyên.
- Một agent khác **đang** chuyển YAML từ config-server vào `src/main/resources/application.yaml`
  của gateway và bỏ config client. Việc này phải **làm sau** khi phần đó đã merge, trên nền file
  mới; giữ nguyên tên biến `${...}` (vd. `INTEGRATION_*_GATEWAY_TIME_LIMIT`, `EVENT_IMAGES_GATEWAY_TIME_LIMIT`,
  `CORS_ALLOWED_ORIGINS`, `REDIS_*`, `KEYCLOAK_*`).
- Build phải qua `./mvnw -B -ntp verify` (Spotless, SpotBugs, JaCoCo).

## 4. Các cách đã cân nhắc

| Cách | Mô tả | Đánh đổi | Kết luận |
|---|---|---|---|
| **A. Route theo prefix trong YAML + security thô** | ~7 route trong YAML, ~10 luật security | Gọn nhất, thêm endpoint không sửa gateway; request sai role trong vùng "đã đăng nhập" đi tới service mới bị 403 | **Được chọn** |
| B. Giữ Java DSL, chỉ gom theo prefix | Route vẫn trong Java với helper | Type-safe nhưng vẫn dài hơn YAML, không theo mẫu config phổ biến | Loại |
| C. Giữ nguyên route/luật theo endpoint, chỉ refactor helper | Ít rủi ro hành vi | Không giải quyết lý do gốc (phải sửa gateway mỗi endpoint) | Loại |

## 5. Thiết kế

### 5.1 Bảng route (thứ tự = độ ưu tiên; route cụ thể đứng trước vì nằm dưới prefix của service khác)

| # | id | Service | Path | Filter |
|---|---|---|---|---|
| 1 | `payment-vnpay-callbacks` | payment | `GET /api/v1/payments/vnpay/return`, `/api/v1/payments/vnpay/ipn` | **không** rate limit, **không** circuit breaker (như hiện tại) |
| 2 | `payment` | payment | `/api/v1/payments/**`, `/api/v1/orders/*/payment`, `/api/v1/events/*/reconciliation`, `/api/v1/admin/events/*/payout`, `/api/v1/admin/events/*/reconciliation`, `/api/v1/admin/events/*/refund-progress`, `/api/v1/admin/refunds/**` | chuẩn |
| 3 | `ticket` | ticket | `/api/v1/tickets/**`, `/api/v1/events/*/check-ins`, `/api/v1/orders/*/tickets`, `/api/v1/orders/*/ticket-deliveries` | chuẩn |
| 4 | `booking` | booking | `/api/v1/events/*/availability`, `/api/v1/orders/**` | chuẩn |
| 5 | `event-images` | event | `/api/v1/organizer/events/*/images`, `/api/v1/organizer/events/*/images/**` | chuẩn, breaker `eventImageServiceBreaker`, `metadata.response-timeout` = `${gateway.event-images.response-timeout-ms:45000}` |
| 6 | `user` | user | `/api/v1/me`, `/api/v1/organizers/**`, `/api/v1/organizer-applications/**`, `/api/v1/admin/organizer-applications/**` | chuẩn |
| 7 | `event` | event | `/api/v1/events/**`, `/api/v1/categories/**`, `/api/v1/venues/**`, `/api/v1/organizer/**`, `/api/v1/admin/events/**`, `/api/v1/admin/cancellation-requests/**` | chuẩn |

"Chuẩn" = `RequestRateLimiter` (Redis, 10/20/1, key resolver theo địa chỉ IP trực tiếp — giữ bean
`remoteAddressKeyResolver` hiện có) + `CircuitBreaker=<service>ServiceBreaker` (không `fallbackUri`).
Không ràng buộc method; service tự trả 405. `/internal/**`, `/actuator/**` không có route.

Lý do thứ tự: `/api/v1/admin/events/*/payout|reconciliation|refund-progress` thuộc payment nhưng
nằm dưới `/api/v1/admin/events/**` của event; `/api/v1/events/*/reconciliation|check-ins|availability`
thuộc payment/ticket/booking nhưng nằm dưới `/api/v1/events/**` của event; `/api/v1/orders/*/payment|tickets|ticket-deliveries`
nằm dưới `/api/v1/orders/**` của booking.

### 5.2 Security (`SecurityWebFilterChain`)

Theo thứ tự:

| Luật | Kết quả |
|---|---|
| `OPTIONS /**` | permitAll |
| `GET /api/v1/payments/vnpay/return`, `/ipn` | permitAll |
| `GET /api/v1/events`, `/api/v1/events/*`, `/api/v1/events/*/availability`, `/api/v1/categories`, `/api/v1/venues`, `/api/v1/organizers/*` | permitAll |
| `/internal/**`, `/actuator/**` | denyAll |
| `/api/v1/admin/**` | ADMIN |
| `/api/v1/organizer/**` | ORGANIZER |
| còn lại | authenticated |

- Giữ: CSRF tắt, CORS từ `GatewayCorsProperties`, `KeycloakRealmRoleConverter` (đọc `realm_access.roles`),
  entry point 401 (`WWW-Authenticate: Bearer` / `Bearer error="invalid_token"`) và 403 (`insufficient_scope`).
- Bỏ: luật `/fallback/**`, mọi luật theo endpoint còn lại, code/comment đã comment-out.

### 5.3 Xử lý lỗi và correlation

- `CorrelationIdFilter` (`WebFilter`, `HIGHEST_PRECEDENCE`): nhận `X-Correlation-Id` hợp lệ (UUID) hoặc
  sinh mới, gắn vào request downstream; sinh `X-Request-Id`; trước commit giữ giá trị downstream nếu hợp lệ
  (Booking có thể gắn correlation theo orderId); log WARN có cấu trúc khi status lỗi (giữ các key log hiện có).
- `GatewayErrorWriter` (`WebExceptionHandler` + được entry point/access denied/429 gọi): giữ nguyên bảng
  status → `code` → `message` tiếng Việt và body `{code, message, correlationId, violations: []}`,
  `Content-Type: application/problem+json`.
- Map thêm khi không còn fallback: `TimeoutException`/`ReadTimeoutException` (kể cả trong cause) → 504,
  `CallNotPermittedException` (breaker mở) → 503, lỗi kết nối tới service / không có instance → 503.
- Giữ cơ chế viết lại body cho 429 của rate limiter.

### 5.4 Resilience4j

- `resilience4j.circuitbreaker.configs.default` chứa thông số chung hiện có (count-based 10, min 5,
  50%, open 10s, half-open 3, auto transition, health indicator).
- `instances`: `eventServiceBreaker`, `eventImageServiceBreaker`, `bookingServiceBreaker`, `paymentServiceBreaker`,
  `ticketServiceBreaker`, `userServiceBreaker` → `base-config: default`.
- `timelimiter`: giữ timeout và tên biến như file YAML sau khi agent kia gộp
  (`INTEGRATION_*_GATEWAY_TIME_LIMIT`, `EVENT_IMAGES_GATEWAY_TIME_LIMIT`).

### 5.5 Cấu trúc code sau khi đổi

| File | Trạng thái |
|---|---|
| `routing/GatewayRouteConfiguration.java` | chỉ còn bean `RedisRateLimiter` + `remoteAddressKeyResolver` |
| `routing/UserGatewayRoutes.java` | xóa |
| `routing/GatewayFallbackController.java` | xóa |
| `security/GatewaySecurityConfiguration.java` | rút gọn theo §5.2 |
| `security/KeycloakRealmRoleConverter.java`, `GatewayCorsProperties.java` | giữ |
| `web/GatewayHttpHandler.java` | tách thành `web/CorrelationIdFilter.java` + `web/GatewayErrorWriter.java` |
| `application.yaml` | thêm `routes` (§5.1), gộp resilience4j (§5.4) |

### 5.6 Đối chiếu với API của service (đã kiểm 2026-11-07)

Nguồn: toàn bộ `@*Mapping` trong controller của 5 service và `SecurityConfig` của từng service.

- **Route:** cả **66** endpoint công khai (`/api/v1/**`, không tính `/internal/**`) đều tới đúng service
  theo bảng §5.1, kể cả các ca chồng prefix. 0 sai.
- **Endpoint công khai:** danh sách permitAll của gateway (§5.2) **trùng khớp** hợp các permitAll của service:
  event (`GET events`, `events/*`, `categories`, `venues`), booking (`GET events/*/availability`),
  user (`GET organizers/*`), payment (`vnpay/return`, `vnpay/ipn`). Gateway không mở thêm gì.
- **Role:** gateway không chặt hơn service ở bất kỳ endpoint nào — mọi endpoint dưới `/api/v1/admin/**` đều
  yêu cầu ADMIN ở service, mọi endpoint dưới `/api/v1/organizer/**` đều yêu cầu ORGANIZER ở event.
  Các role khác (BUYER cho orders/tickets/payments/follow/applications, ORGANIZER cho check-ins và logo/banner,
  ADMIN|ORGANIZER cho `GET events/*/reconciliation`) do service kiểm.
- **Route chết bị bỏ:** gateway hiện route `GET /api/v1/orders`, `GET /api/v1/orders/*`,
  `POST /api/v1/orders/*/payment-attempts` sang booking nhưng booking không có các endpoint này.
  Sau thay đổi chúng vẫn tới booking (prefix) và nhận 404/405 từ booking — không ảnh hưởng client.
- **Frontend:** 13 path frontend đang gọi đều có endpoint thật và route đúng.

Test route (§5.7) phải chạy trên **toàn bộ 66 endpoint** này (bảng method + path → service), để endpoint mới
của service mà rơi nhầm prefix sẽ làm test đỏ.

### 5.7 Test

- Viết lại `GatewayRouteConfigurationTests`: bảng 66 endpoint ở §5.6 tới đúng route id/URI, gồm các ca
  chồng prefix (payment admin vs event admin, reconciliation/check-ins/availability vs event, orders/* con vs booking).
- Viết lại `GatewaySecurityConfigurationTests`: công khai → không cần token; thiếu token → 401; ADMIN/ORGANIZER
  vùng tương ứng → 403 khi sai role; `/internal/**`, `/actuator/**` → bị chặn.
- `GatewayHttpTests`: format lỗi, correlation/request id, 429, 503 khi breaker mở, 504 khi timeout.
- Xóa `GatewayFallbackControllerTests`.

## 6. Thay đổi hành vi (người dùng cần biết)

| Thay đổi | Trước | Sau |
|---|---|---|
| **Endpoint mới đi qua gateway** | Không route: `POST /api/v1/admin/events/*/payout`, `POST .../reconciliation`, `GET .../refund-progress`, `GET /api/v1/admin/refunds`, `POST /api/v1/admin/refunds/*/manual-settlement`, `POST .../retry`, `GET /api/v1/events/*/reconciliation` | Có route; admin cần ADMIN ở gateway; tất cả còn qua kiểm role của payment |
| Role BUYER cho `/orders/**`, `/tickets/**`, `POST /payments`, follow, organizer-applications | Gateway chặn 403 | Gateway chỉ yêu cầu đăng nhập; service trả 403 |
| ORGANIZER cho `POST /events/*/check-ins`, `/organizers/me/logo|banner` | Gateway chặn | Service chặn |
| Method sai (vd. `DELETE /api/v1/orders`) | Gateway 404 | Service 405 (hoặc 404), body theo format của service; payment hiện có thể trả body mặc định Spring (§9) |
| Lỗi breaker/timeout | Qua `/fallback/{service}` | Bộ ghi lỗi chung; cùng status 503/504 và cùng body |

## 7. Ghi nhận để làm sau

- Rate limit theo IP nguồn: khi đứng sau ngrok/reverse proxy mọi client chung một IP. Cần tin
  `X-Forwarded-For` từ proxy đáng tin, hoặc key theo `sub` của JWT cho request đã đăng nhập.
- Có thể thêm 2–3 luật chặn sớm ở gateway cho luồng quan trọng (vd. `/api/v1/orders/**` → BUYER) nếu cần.

## 8. Để lại cho người làm

- Cú pháp YAML cụ thể (shortcut hay `name/args`), cách loại callback khỏi rate limit (không dùng `default-filters` hoặc tách route).
- Tên class/package cụ thể nếu muốn khác §5.5.
- Cách phát hiện "không có instance" từ load balancer để map 503.

## 9. Kết quả builder check

- **Nền để làm:** trạng thái working tree hiện tại của `dev/integration`. Gateway đã bỏ
  `spring.config.import: configserver` và `spring-cloud-starter-config`; cấu hình đầy đủ nằm trong
  `src/main/resources/application.yaml` của gateway. Bản `config-server/.../api-gateway.yaml` không còn
  được gateway đọc; xóa nó thuộc việc bỏ config-server (ngoài phạm vi spec này), không sửa ở đây.
  (Quyết định của người viết spec.)
- **Test phải nạp YAML chính:** `src/test/resources/application.yaml` hiện che file chính, và
  `GatewayHttpTests` còn import file của config-server. Phải chuyển test sang profile `test` để route YAML
  được kiểm thật. (Quyết định của người viết spec.)
- **Body lỗi 404/405 từ service:** event, user, booking, ticket đã trả đúng format lỗi chung; **payment chưa có**
  handler 404/405 nên có thể trả body mặc định của Spring. Gateway **không** viết lại body của service
  (giữ nguyên hành vi hiện tại). Việc bổ sung handler cho payment là quyết định của người dùng (§6).

## 10. Bằng chứng kiểm tra (2026-11-07)

- `mvnw clean verify` (Spotless, SpotBugs, JaCoCo): **103 test, 0 lỗi**. Chạy với `-Denforcer.skip=true` vì máy
  local chỉ có JDK 23; target vẫn là Java 21, CI chạy JDK 21 đúng enforcer.
- Test route chạy trên **66 endpoint** của 5 service + 12 ca khác (callback, filter, timeout ảnh, `/internal`, `/fallback`, cấu hình breaker).
- Gateway dựng lại trong stack local (cổng 8081), Eureka thấy lại `API-GATEWAY`:
  `GET /events`, `/categories`, `/venues` → 200; `/me`, `POST /orders`, `/admin/refunds`, `/organizer/events`,
  `/internal/**`, `/actuator/health`, `/fallback/**` không token → 401 body chuẩn; VNPay `ipn` → 200 (`RspCode 97`),
  `return` → 302; preflight `OPTIONS /admin/refunds` → 200.
- Java trong `src/main`: 501 dòng (từ ~1.120). Ít hơn mục tiêu ~350 vì `CorrelationIdFilter` (log lỗi có cấu trúc) và
  `GatewayErrorWriter` (bảng mã lỗi tiếng Việt) giữ nguyên hành vi cũ.
- Phát hiện: `ServiceUnavailableException` của Spring Cloud Gateway (breaker mở) không phải `ResponseStatusException`
  nên trước đây rơi vào 500; đã map sang 503 `DEPENDENCY_UNAVAILABLE`.
- Phát hiện: breaker `eventImageServiceBreaker` trước đây không có cấu hình nên dùng mặc định thư viện
  (cửa sổ 100 lần gọi); nay dùng chung cấu hình mặc định như 5 breaker còn lại.
- Ghi nhận ngoài phạm vi: log gateway local báo `Error calling rate limiter lua` (không kết nối được Redis từ
  container); rate limiter fail-open nên request vẫn đi qua. Booking cũng có lỗi Redis tương tự — là vấn đề kết nối
  Redis của môi trường local, không do thay đổi này.
