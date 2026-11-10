# API Gateway

Gateway WebFlux chuyển tiếp URL, body, JWT `Authorization`, `Idempotency-Key` và
query string tới service đăng ký trong Eureka. Không rewrite prefix `/api/v1`.

## Event routes

Đã thêm route theo method/path cụ thể cho catalog public `/api/v1/events`,
`/categories`, `/venues`; organizer collection/detail, submit/publish, seat-map,
ticket-types, sales-mode, cancel/cancellation-requests, statistics; admin Event
collection/detail/approve/return-to-draft/cancel và cancellation request review.
Xem [Event README](../../services/event-service/README.md) cho body/quyền nghiệp vụ.
Prefix organizer/events yêu cầu ORGANIZER, admin Event/cancellation yêu cầu ADMIN;
Event tiếp tục kiểm ownership. Giữ nguyên availability→Booking và check-ins→Ticket.
Không route internal summary qua Gateway. Source có23 route definitions, trong đó
8 definitions mới nhóm các method/path Event; không mở wildcard events/** chung.

Các route có trong source/JAR đã build không tự thay image đang chạy. Áp dụng
Gateway Docker cần build/test JAR rồi rebuild image/recreate riêng api-gateway.
Chỉ restart container cũ không nạp Java mới; không cần restart Config/Discovery.
Lượt coding Event bước5–7 chưa recreate/deploy container Gateway.

## Booking routes

| Method | Path | Service |
|---|---|---|
| GET | `/api/v1/events/{eventId}/availability` | booking-service; public |
| POST | `/api/v1/orders` | booking-service; BUYER |
| GET | `/api/v1/orders` | booking-service; BUYER |
| GET | `/api/v1/orders/{orderId}` | booking-service; BUYER |
| POST | `/api/v1/orders/{orderId}/promotion` | booking-service; BUYER |
| POST | `/api/v1/orders/{orderId}/cancel` | booking-service; BUYER |
| POST | `/api/v1/orders/{orderId}/payment-attempts` | booking-service; BUYER |
| GET | `/api/v1/organizer/events/{eventId}/sales-summary` | booking-service; ORGANIZER (route `booking` order 20 đứng trước route `event` order 50) |

GET `/api/v1/orders/{orderId}/payment` vẫn vào Payment. Theo
[B13](../../docs/architecture/B13-api-and-event-contracts.md), Booking tiếp nhận
POST thanh toán, freeze snapshot và phát command; Payment xử lý command.
Gateway không triển khai các handler nghiệp vụ còn thiếu trong Booking.

## Chạy local

1. Nạp `.env` ở root vào môi trường chạy của từng ứng dụng; Spring không tự đọc file này.
2. Chạy Config Server (`8888`), Discovery Server (`8761`), rồi Booking và Gateway.
3. Kiểm tra Eureka có instance `booking-service` reachable từ Gateway.
4. Dùng `API_GATEWAY_PORT` trong `.env`; mặc định cấu hình là `8081`.
   Máy local đang dùng `8088` vì `8081` đã bị ứng dụng khác chiếm.

`KEYCLOAK_ISSUER_URI` phải khớp chính xác claim `iss` trong token; JVM phải truy cập
được metadata và JWKS do Keycloak công bố để xác thực token.
Gateway và Booking kiểm JWT độc lập. Các credential và địa chỉ Redis lấy từ `.env`.
Service nội bộ gọi thẳng `/internal/v1/**` của service đích bằng JWT nội bộ;
Gateway public chặn `/internal/v1/**`, `/fallback/**` và `/actuator/**`.

## Lỗi và correlation

Gateway chuẩn hóa UUID `X-Correlation-Id`, chuyển tiếp xuống service và trả
`X-Correlation-Id`/`X-Request-Id`; giữ correlation do Booking trả lại khi đã gắn orderId.
CORS expose cả hai header cho frontend. ID là dữ liệu truy vết, không phải quyền truy cập.

Lỗi do Gateway sinh dùng `{code,message,correlationId,violations:[]}`:
401 `UNAUTHENTICATED`, 403 `ACCESS_DENIED`, 404 `ROUTE_NOT_FOUND`,
429 `RATE_LIMIT_EXCEEDED`, 503 `DEPENDENCY_UNAVAILABLE`, 504 `DEPENDENCY_TIMEOUT`.
Response nghiệp vụ từ downstream được giữ nguyên. Log JSON ghi route/service đích,
status, reasonCode, loại exception, duration và các ID; không ghi JWT/body/query.

Không tự retry HTTP. Timeout kết nối là 2s, response 8s, circuit breaker 10s;
POST timeout không chứng minh transaction downstream đã rollback. Client cần đọc
trạng thái hoặc retry cùng idempotency key theo hợp đồng nghiệp vụ.
Redis rate limiter dùng địa chỉ kết nối trực tiếp: 10 request/s, burst 20;
chưa dùng header forwarded để xác định IP qua reverse proxy. RedisRateLimiter
của Spring Cloud Gateway cho request đi tiếp khi Redis lỗi; rate limit không thay
JWT, idempotency hoặc kiểm tồn kho tại Booking.

## Kiểm tra

Với JDK 21, tại thư mục này chạy `.\mvnw.cmd -B -ntp verify`.
`GatewayHttpTests` chạy Gateway thật trên random port, JWT ký RSA và HTTP downstream
fixture; thay thế Eureka/Redis để kiểm route, security, header, lỗi và timeout.
Đây không phải nghiệm thu end-to-end với Keycloak/Redis và API Booking nghiệp vụ thật.

## Route thống kê bán vé (2026-11-10)

Bảy GET reporting được khai báo path cụ thể trên route Booking order 20, trước User order 40 và Event order 50:

- /api/v1/admin/sales/summary, /api/v1/admin/sales/chart, /api/v1/admin/events/sales-statistics.
- /api/v1/organizers/{subject}/sales/summary, /api/v1/organizers/{subject}/sales/chart, /api/v1/organizers/{subject}/events/sales-statistics.
- /api/v1/events/{eventId}/sales-statistics.

Booking kiểm role bằng method security và owner trước cache, kể cả gọi HTTP thẳng Booking. Các API organizer/profile và event khác vẫn route như cũ. [Contract và param](../../docs/api-documentation-frontend.md#thống-kê-bán-vé-dùng-chung--2026-11-10). Gateway tests: 112 passed, gồm 7 case mới.
