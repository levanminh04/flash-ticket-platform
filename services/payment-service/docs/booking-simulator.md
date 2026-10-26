# Booking simulator qua RabbitMQ

Dùng khi Booking chưa chạy nhưng cần thử luồng tạo URL VNPay trong Payment.
Simulator tạo `Envelope<StartPayment>` đúng codec/hash hiện có, gửi message bền
qua RabbitMQ và lưu bản gốc để replay. Payment ghi PostgreSQL thật; bộ dựng URL
VNPay Sandbox thật được dùng, không gọi API mạng khi dựng URL.

Luồng: CLI simulator → `payment.simulator.ex` → `payment.simulator.start.q` →
consumer → snapshot + attempt + URL + inbox commit → API GET có JWT.

## 1. Chạy PostgreSQL/RabbitMQ riêng

Cần Docker Desktop và JDK 21. Từ thư mục repository:

```powershell
docker compose -f services/payment-service/dev/compose.yaml up -d --wait
```

Compose chỉ mở cổng loopback và dùng volume riêng của project
`payment-simulator`. Không chạy initializer trên DB hiện có.

| Thành phần | Địa chỉ | Tài khoản local |
| --- | --- | --- |
| PostgreSQL | `127.0.0.1:5443/payment_db` | `payment_app` / `simulator-local-only` |
| RabbitMQ | `127.0.0.1:5679` | `payment_simulator` / `simulator-local-only` |
          | RabbitMQ Management | `http://127.0.0.1:15679` | cùng tài khoản broker |

Các mật khẩu trên chỉ dành cho container local mới này. Không dùng lại cho môi
trường triển khai. Schema được tạo từ DDL Payment hiện có và migration tên `tbl_`.
Profile dùng `currentSchema=pg_catalog` để JDBC trả tên domain có schema khớp
mapping Hibernate; bảng vẫn được truy cập qua `payment_schema` tường minh.

## 2. Chạy Payment ở terminal thứ nhất

Từ `services/payment-service`, đặt environment của chính tiến trình Payment:

```powershell
$env:JAVA_HOME = '<duong-dan-JDK-21>'
$env:JAVA_TOOL_OPTIONS = '-Duser.timezone=UTC'
$env:SPRING_PROFILES_ACTIVE = 'payment-simulator'
$env:PAYMENT_VNPAY_ENABLED = 'true'
$env:PAYMENT_VNPAY_TMN_CODE = '<merchant-sandbox-duoc-cap>'
$env:PAYMENT_VNPAY_HASH_SECRET = '<secret-sandbox-duoc-cap>'
$env:PAYMENT_VNPAY_RETURN_URL = 'https://<gateway-domain>/api/v1/payments/vnpay/return'
$env:PAYMENT_VNPAY_IPN_URL = 'https://<gateway-domain>/api/v1/payments/vnpay/ipn'
$env:KEYCLOAK_ISSUER_URI = '<issuer-trong-token>'
$env:KEYCLOAK_JWK_SET_URI = '<dia-chi-jwks-Keycloak>'
$env:KEYCLOAK_AUDIENCE = '<audience-cua-Payment>'
.\mvnw.cmd spring-boot:run
```

Profile này tự cấu hình DB/broker local, tắt Config Client và Eureka, giữ JWT.
Không cần Config Server. Không dùng kèm `local-foundation` vì profile đó tắt
listener; không dùng kèm `prod`/`production`. Callback/refund mặc định tắt.
Không ghi secret vào Java hoặc file Git. Cấu hình profile ghi đè mặc định Java.

Nếu cần override: `PAYMENT_DB_URL`, `PAYMENT_DB_USERNAME`, `PAYMENT_DB_PASSWORD`,
`RABBITMQ_HOST`, `RABBITMQ_PORT`, `RABBITMQ_USERNAME`, `RABBITMQ_PASSWORD`,
`RABBITMQ_VHOST`, `PAYMENT_SERVICE_PORT`. Giữ DB/broker thử nghiệm tách biệt.

## 3. Gửi StartPayment ở terminal thứ hai

Lấy claim `sub` từ access token BUYER của bạn. Subject phải trùng token dùng GET.
Từ `services/payment-service`:

```powershell
$env:JAVA_HOME = '<duong-dan-JDK-21>'
.\dev\booking-simulator.ps1 -BuyerSubject '<JWT-sub>' -Amount '100000'
```

Script build bằng Maven Wrapper rồi chạy CLI. Mặc định tạo một vé QUANTITY,
100000 VND, hạn 15 phút; có thể đặt `-LifetimeSeconds` từ 5 đến 3600.
CLI in đường dẫn file command, requestId, orderId và đường dẫn poll. Chỉ in
"Broker confirmed" sau publisher confirm và kiểm message không bị unroutable.
Điều này xác nhận broker nhận, chưa xác nhận Payment đã xử lý.

Command được ghi trước publish vào `target/booking-simulator/<requestId>.json`.
Không sửa file này nếu muốn retry. Chạy lại lệnh tạo mới sẽ tạo đơn/request mới.
Không cần copy số tiền, hash hoặc envelope bằng tay.

## 4. Lấy URL bằng Postman

Gọi trực tiếp Payment ở cổng 8083 để không phụ thuộc routing các service khác:

```http
GET http://localhost:8083/api/v1/orders/<orderId>/payment?requestId=<requestId>
Authorization: Bearer <BUYER-access-token>
```

Trước khi nhận snapshot có thể gặp `404 PAYMENT_NOT_READY`; poll lại có backoff.
Khi `startRequest.state=CREATED`, đọc `latestAttempt.paymentUrl` và mở bằng browser.
Response còn có attemptId, amount, state và expiresAt. `CREATED` không có nghĩa
đã trả tiền. Khi bị từ chối, `startRequest.state=REJECTED` có `failureCode`, ví dụ
`ORDER_EXPIRED` hoặc `PAYMENT_IN_PROGRESS`.

## 5. Replay và kiểm lỗi

```powershell
.\dev\booking-simulator.ps1 -Replay 'target/booking-simulator/<requestId>.json' -SkipBuild
```

Replay giữ nguyên bytes, IDs, hash và thời gian. Payment trả kết quả inbox cũ,
không tạo attempt/URL mới, kể cả replay sau hạn. Nếu chưa có kết quả mà đơn đã
hết hạn, Payment lưu REJECTED. Không dùng file cũ để gia hạn đơn.

Nếu publish lỗi: sửa kết nối/routing, rồi replay file đã lưu thay vì tạo request
mới. Khởi động Payment trước để topology được khai báo. Trong Management UI,
xem queue `payment.simulator.start.dlq` khi input sai hoặc xử lý vẫn lỗi sau ba
lần thử. Message trong DLQ giữ nguyên để điều tra; nó chưa có kết quả xử lý thành
công. Sau khi sửa lỗi có thể replay file gốc. Không log raw payload/URL thanh toán.

## 6. Kiểm tự động

Với hai container trên đang healthy và Java 21:

Trước khi chạy suite, dừng tiến trình Payment ở terminal thứ nhất bằng Ctrl+C
và dừng gửi command từ simulator. Suite tự khởi động Payment với merchant test
và dùng chính DB/queue local này; nó cần là consumer duy nhất. Ca kiểm routing
có tạm tháo rồi gắn lại binding, nên không gửi command khác trong lúc test.

```powershell
.\mvnw.cmd verify -Psimulator-tests -B -ntp
```

Suite này kiểm PostgreSQL/RabbitMQ thật: tạo URL, replay, cạnh tranh, đơn hết hạn,
rollback và DLQ. Test dùng merchant giả/khóa `key` để kiểm chữ ký cục bộ; không
gọi Sandbox hoặc chứng minh merchant thật hoạt động. `verify` thường không chạy
test cần hai container. Không chạy suite trên DB dùng chung.

## Giới hạn

- Guard Booking giả chỉ kiểm thời gian snapshot; không chứng minh đơn/giữ chỗ có
  thật, chưa hủy hay còn hàng. Consumer và guard chỉ có ở `payment-simulator`
  và bị loại khi có `prod`/`production`. Không phải public API khởi tạo.
- Dùng topology riêng, không kiểm tích hợp outbox/queue nghiệp vụ của Booking.
  Simulator chưa có Booking outbox; file command và publisher confirm hỗ trợ
  replay có kiểm soát trong phát triển.
- Attempt, URL và inbox cùng transaction; ACK sau commit. Việc tạo URL VNPay
  là cục bộ, nên không có khoảng chờ mạng giữa persist attempt và lưu URL.
- Chưa nhận thanh toán/IPN, ghi charge, phát vé, Saga hoặc hoàn tiền. Không bật
  callback bằng handler giả. Xem `vnpay-sandbox.md` để biết phần còn thiếu.
- Thay Booking giả bằng client thật cần triển khai guard/producer đúng contract
  và mở runtime nghiệp vụ riêng; không chỉ bật profile này ngoài local.

Dừng container khi xong (giữ dữ liệu replay):

```powershell
docker compose -f services/payment-service/dev/compose.yaml stop
```

## Bằng chứng kiểm ngày 26/10/2026

- `verify -Psimulator-tests`: 134 test, không failure/error/skip; Spotless và
  SpotBugs qua, không finding của SpotBugs.
- Đã chạy launcher PowerShell ở chế độ build mặc định, gửi command qua broker,
  đọc URL đã lưu bằng HTTP GET với JWT ký RSA và JWKS thử nghiệm local.
  Replay giữ đúng một attempt/cùng URL; request không token trả 401, buyer khác
  trả 404. Đây không phải kiểm kết nối Keycloak thật.
- Đã kiểm hồi quy khởi động profile simulator khi không có Config Server.
- Review độc lập đã được xử lý: yêu cầu dừng Payment trước suite để tránh hai
  consumer cạnh tranh cùng queue local. Không thực hiện thanh toán hoặc gọi
  mạng tới VNPay; merchant Sandbox thật và callback chưa được nghiệm thu.
