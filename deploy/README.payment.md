# Chạy Payment cùng ngrok bằng Docker

Chạy lệnh bên dưới tại thư mục gốc repository. Compose chỉ chạy Payment và ngrok;
Booking, PostgreSQL, RabbitMQ và Keycloak phải sẵn sàng trước.
Payment dùng Java 21 bên trong image, máy không cần cài Java để build/chạy container.

## Cấu hình một lần

1. Lấy authtoken và **domain được cấp cho tài khoản** trong dashboard ngrok.
   Gói miễn phí có một dev domain cố định, không đổi khi khởi động lại agent.
   Dùng đúng hostname được cấp, không tự đặt tên domain.
2. Giữ các biến Payment đang chạy được trong `.env` ở thư mục gốc. Thêm:

   ```dotenv
   NGROK_DOMAIN=your-assigned-domain.ngrok-free.app
   NGROK_AUTHTOKEN=your-own-token
   ```

   `NGROK_DOMAIN` chỉ chứa hostname, không có `https://`, đường dẫn hoặc dấu `/` cuối.
   Không commit `.env`. Token chỉ được truyền cho container ngrok, không đưa vào image Payment.
3. Đăng ký IPN với VNPay cho đúng merchant `VNPAY_TMN_CODE`:

   ```text
   https://<NGROK_DOMAIN>/api/v1/payments/vnpay/ipn
   ```

   Compose tự tính `VNPAY_RETURN_URL` và `VNPAY_IPN_URL` từ domain mỗi lần tạo container,
   ghi đè các URL cũ trong `.env` mà không sửa file. Compose không đăng nhập hoặc thay
   cấu hình merchant bên VNPay. Đăng ký IPN một lần nếu giữ nguyên domain và merchant.

## Chạy

> [!WARNING]
> Ngrok local và ngrok CD trên FT84 dùng **chung một `NGROK_DOMAIN`** và 1 URL IPN VNPay. Do đó, trước khi CD deploy `payment-service`/`ngrok` lên máy 84, phải tắt ngrok/payment local (`docker compose --env-file .env -f deploy/docker-compose.payment.yml down`) để tránh xung đột domain và tranh chấp webhook IPN.

Dừng Payment trong IntelliJ trước để tránh trùng cổng và hai bản cùng xử lý queue.
Giữ Booking ở cổng 8082.
Nếu đang dùng SSH tunnel PostgreSQL/Booking, giữ các tunnel chạy.

```powershell
docker compose --env-file .env -f deploy/docker-compose.payment.yml up -d --build
docker compose --env-file .env -f deploy/docker-compose.payment.yml ps
docker compose --env-file .env -f deploy/docker-compose.payment.yml logs -f payment-service ngrok
```

Ngrok chỉ khởi động sau khi Payment qua healthcheck HTTP. Chờ log ngrok báo endpoint
đã mở thành công; container đang chạy chưa chứng minh tunnel đã kết nối tài khoản.
Lần sau chạy lại cùng lệnh; Docker tái sử dụng cache build và domain giữ nguyên.
`restart: unless-stopped` cho phép tự khởi động lại khi Docker khởi động; sau `down`
hoặc dừng thủ công, cần chạy `up` để bật lại.

- API Postman: `http://localhost:8083/api/v1/payments`.
- Swagger UI: `http://localhost:8083/swagger-ui/index.html`; chọn **Authorize**
  và nhập JWT để gọi API có bảo vệ. GET tài liệu/schema được public; API nghiệp vụ
  vẫn kiểm tra JWT, role và chủ đơn.
- Theo dõi IPN/Return: `http://localhost:4040`.
- Dừng cả hai: `docker compose --env-file .env -f deploy/docker-compose.payment.yml down`.

### Schema cho bản Payment có Finance và Refund

Image cần được build từ toàn bộ source để Swagger có đủ ba nhóm Payment,
Finance và Refund (12 operation). Image `swagger-local` tạm trước đây chỉ có
bốn operation của Payment; không dùng image đó để chạy bản mới.

Với database cũ thiếu `settled_by` và `external_reference` trong
`payment_schema.tbl_refunds`, áp dụng một lần script
[`postgres/migrations/2026-11-04-payment-refund-settlement-columns.sql`](postgres/migrations/2026-11-04-payment-refund-settlement-columns.sql)
trên `payment_db` bằng tài khoản có quyền `SET ROLE payment_owner`.
Script chạy trong transaction, thêm hai cột nullable và ràng buộc metadata
hoàn ngoài hệ thống, giữ nguyên dữ liệu cũ. Database mới đã tạo bằng DDL B12
hiện tại có sẵn hai cột, không chạy lại migration này.

Sau khi schema tương thích, chỉ build và chạy lại Payment nếu ngrok đã có
endpoint ở nơi khác:

```powershell
docker compose --env-file .env -f deploy/docker-compose.payment.yml build payment-service
docker compose --env-file .env -f deploy/docker-compose.payment.yml up -d --no-build --no-deps --wait payment-service
```

Giữ `PAYMENT_IMAGE` ở mặc định `flash-ticket/payment-service:local` khi dùng
image build local. Chỉ chạy ngrok khi domain không được một endpoint khác sử dụng.

### Luồng phát vé qua Payment

Ticket gửi cả hai kết quả qua `ticket.issuance-result.v1` vào queue
`payment.ticket-issuance-failed.q`. Payment gửi Booking `ticket.issued.v1` khi
thành công hoặc `booking.issuance-failed.v1` khi thất bại. Khi phát vé thành công,
Payment đợi `booking.order-completed.v1` trước khi đóng Saga.

Payment dùng `spring.rabbitmq.dynamic=false`. Trước khi bật consumer,
kiểm tra binding thực tế và outbox Ticket còn chờ theo cấu hình cũ.

## Dịch vụ ngoài Docker

`localhost` bên trong container là container đó. Các đường dẫn mặc định của Compose:

| Dịch vụ | Địa chỉ Payment dùng |
| --- | --- |
| Booking trên máy hoặc SSH tunnel | `http://host.docker.internal:8082` |
| PostgreSQL trên máy hoặc SSH tunnel | `jdbc:postgresql://host.docker.internal:5432/payment_db` |
| RabbitMQ/Keycloak | Theo `.env`, dùng địa chỉ container truy cập được |

Có thể đổi `PAYMENT_DOCKER_BOOKING_URL`,
`PAYMENT_DOCKER_DB_URL` nếu dùng trực tiếp server hoặc network khác.
Nếu RabbitMQ hoặc Keycloak chạy trên máy, dùng hostname Docker truy cập được;
giữ `KEYCLOAK_ISSUER_URI` khớp chính xác issuer của token, còn URL JWK/token phải
truy cập được từ container. Với Docker Desktop, kiểm tra firewall và tunnel nếu
container không tới được cổng trên Windows. Eureka bị tắt trong Compose này;
Postman gọi thẳng Payment, không qua discovery/Gateway.

`PAYMENT_DOCKER_PORT` chỉ đổi cổng truy cập trên máy; bên trong Payment vẫn là 8083.
`NGROK_INSPECTOR_PORT` đổi cổng theo dõi trên máy nếu 4040 đang bận.

## Test callback

1. Gọi GET `https://<NGROK_DOMAIN>/api/v1/payments/vnpay/ipn` không kèm tham số.
   Kết quả mong đợi là HTTP 200, JSON `RspCode=97` vì thiếu chữ ký. Lệnh này không
   cập nhật giao dịch; nó xác nhận tunnel đã tới đúng endpoint. Nếu trình duyệt
   hiện trang cảnh báo ngrok, chọn tiếp tục; dùng Postman để xem JSON trực tiếp.
2. Tạo order còn hạn, dùng JWT BUYER và `Idempotency-Key` để POST tạo thanh toán.
   Kiểm tra `vnp_ReturnUrl` trong link mới dùng đúng domain ngrok.
3. Thanh toán sandbox, xem ngrok inspector có request `/ipn` và response JSON
   `RspCode=00`, rồi gọi GET `/api/v1/orders/{orderId}/payment` với JWT BUYER.

Link đã lưu trong attempt cũ không tự đổi khi thay domain. Cùng idempotency key
trả lại link cũ; key mới trên order vẫn có attempt PENDING có thể trả 409.
Khi đổi cấu hình callback, dùng order mới còn hạn để test rõ ràng.
Nếu trình duyệt không mở được `flash-ticket://payment-return`, đó là app deep link;
kiểm tra IPN và API trạng thái. Return không tự xác nhận thu tiền.

Nếu chỉ có `/return`, kiểm tra đăng ký IPN bên VNPay. Nếu nhận tiền nhưng order chưa
hoàn tất/phát vé, kiểm tra worker, queue và hợp đồng Ticket → Booking; tunnel không
sửa khác biệt routing giữa các service.

## Nguồn

- [ngrok Docker](https://ngrok.com/download/docker)
- [Domain cố định của gói miễn phí](https://ngrok.com/docs/pricing-limits/free-plan-limits)
- [Cấu hình agent v3](https://ngrok.com/docs/gateway/agent/config/v3)
- [VNPay IPN và Return](https://sandbox.vnpayment.vn/apis/docs/thanh-toan-pay/pay.html)
