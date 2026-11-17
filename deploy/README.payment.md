# Payment Docker và callback VNPay qua HTTPS

Production nhận callback qua Traefik → Gateway → Payment trên ft84, không cần
Ngrok, DNS mới hoặc thay port. Source đã bỏ Ngrok; việc này **không tự dừng
container cũ hoặc đổi đăng ký IPN bên VNPay**.

## Callback production

| Biến | Giá trị |
| --- | --- |
| `VNPAY_RETURN_URL` | `https://flashticket.sondimid.cloud/api/v1/payments/vnpay/return` |
| `VNPAY_IPN_URL` | `https://flashticket.sondimid.cloud/api/v1/payments/vnpay/ipn` |
| `VNPAY_APP_RETURN_URL` | `flash-ticket://payment-return` (giữ deep link hiện tại) |

- ReturnURL được gắn vào link thanh toán mới, xác minh chữ ký rồi redirect về app;
  Return không xác nhận thu tiền. IPN mới là thông báo cập nhật thanh toán.
- Cung cấp IPN URL trên cho VNPay theo đúng merchant `VNPAY_TMN_CODE`, qua kênh
  quản trị/hỗ trợ sandbox được cấp. Chỉ sửa env không cập nhật đăng ký phía VNPay.
- Không gửi merchant secret/authtoken vào chat, source hoặc log.

## Cutover ft84

1. Kiểm TLS và GET IPN không tham số như mục kiểm tra bên dưới trước khi thay đổi.
2. Đổi hai URL trong `deploy/k8s/payment-service/configmap.yaml`, đồng bộ
   `/opt/flash-ticket-app/env/payment-service.env` (mode600) và GitHub Secret
   `ENV_PAYMENT_SERVICE`; bảo toàn các key/secret khác. Template public nằm tại
   [`https/public.env.example`](https/public.env.example), không dùng thay toàn env.
3. Áp dụng ConfigMap và rollout riêng Deployment `payment-service` theo quy trình
   K3s hiện có, giữ image/flags. Cập nhật Compose và deploy script server từ source
   mới để lần deploy sau không tái tạo Ngrok; không chạy lại Compose Payment.
4. Xác nhận VNPay đã cập nhật IPN mới. Giữ Ngrok cũ trong giai đoạn chuyển tiếp:
   link đã lưu trong attempt không tự đổi; cùng idempotency key có thể trả link cũ.
   Không sửa DB để viết lại URL giao dịch. Code hiện giới hạn link mới tối đa15 phút;
   VNPay có thể thử IPN tối đa10 lần, cách5 phút. Đối chiếu giao dịch/callback tồn
   trước khi tháo, không coi một khoảng chờ cố định là bằng chứng đã hết giao dịch.
5. Chỉ khi provider đã đổi và giao dịch/callback cũ đã drain, stop/remove **riêng**
   container `flash-ticket-payment-service-ngrok-1` sau khi kiểm label thực tế.
   Không `compose down`, xóa volume/image hoặc thu hồi token tài khoản Ngrok.
   Sau khi không còn consumer mới loại key `NGROK_*` khỏi env server/GitHub.

Nếu chưa xác nhận đăng ký/drain thì giữ Ngrok chạy và ghi nhận cutover chưa hoàn tất.
Rollback trước tháo: khôi phục URL/env/ConfigMap cũ, rollout Payment và khôi phục
đăng ký IPN bên VNPay nếu đã đổi. Không khôi phục toàn Compose hoặc thay dữ liệu.
Không xóa legacy HTTP ingress chỉ vì bỏ Ngrok: có thể còn client khác đang dùng.

## Chạy Payment local bằng Docker

Compose chỉ chạy Payment; Booking, PostgreSQL, RabbitMQ và Keycloak phải sẵn sàng.
Dùng `.env` của workspace qua `--env-file`, điền các secret và hai biến callback
trước khi chạy. Xem [`../services/payment-service/.env.example`](../services/payment-service/.env.example).
Không override biến trực tiếp trên CLI để bù cấu hình thiếu.

Localhost không nhận được IPN Internet. **Không dùng callback ft84 cho giao dịch
local**: request sẽ tới Payment production, không phải instance local. E2E local
cần endpoint công khai và merchant test tách biệt được cấu hình riêng; hướng dẫn
này không cung cấp tunnel thay thế. Giữ URL localhost cho kiểm thử local không có
provider delivery, không coi đó là cấu hình E2E.

Dừng Payment trong IDE trước để tránh trùng cổng/hai worker; giữ Booking8082 và
Payment8083. Nếu xung đột cổng, dừng và kiểm tra, không tự đổi port.

```bash
docker compose --env-file .env -f deploy/docker-compose.payment.yml up -d --build payment-service
```

```bash
docker compose --env-file .env -f deploy/docker-compose.payment.yml ps
```

```bash
docker compose --env-file .env -f deploy/docker-compose.payment.yml logs -f payment-service
```

- API local: `http://localhost:8083/api/v1/payments`.
- Swagger: `http://localhost:8083/swagger-ui/index.html`; API nghiệp vụ vẫn kiểm
  JWT/role/chủ đơn, không tắt bảo mật để test callback.
- Compose mới không tự dọn container Ngrok cũ; không dùng `--remove-orphans` để
  dọn hàng loạt. Đối chiếu callback cũ trước khi dừng riêng container đã xác định.

### Schema Finance và Refund

Build toàn bộ source để Swagger có đủ Payment, Finance và Refund; không dùng image
`swagger-local` tạm cũ. Với DB cũ thiếu `settled_by` và `external_reference` trong
`payment_schema.tbl_refunds`, xem script
[`postgres/migrations/2026-11-04-payment-refund-settlement-columns.sql`](postgres/migrations/2026-11-04-payment-refund-settlement-columns.sql).
Script thêm hai cột nullable và ràng buộc metadata hoàn ngoài hệ thống trong
transaction, cần quyền `SET ROLE payment_owner`. DB mới từ DDL B12 đã có cột thì
không chạy lại. Thay đổi callback không cần chạy migration này.

## Kiểm tra callback

```bash
curl --fail --silent --show-error https://flashticket.sondimid.cloud/api/v1/payments/vnpay/ipn
```

Mong đợi HTTP200, JSON `RspCode=97` do thiếu chữ ký. Không dùng `-k`; probe này
không cập nhật giao dịch, chỉ chứng minh TLS/route và từ chối chữ ký, **không chứng
minh VNPay đã đăng ký hoặc đã gửi IPN thật**.

Chỉ khi được phép tạo dữ liệu test: dùng order mới còn hạn, JWT BUYER và
Idempotency-Key để tạo thanh toán; kiểm `vnp_ReturnUrl` trong link mới, thanh toán
sandbox rồi đối chiếu IPN thành công và API trạng thái theo order/attempt. Việc này
có thể phát vé/gửi email; không chạy mặc định như smoke cấu hình.

Nếu trình duyệt không mở `flash-ticket://payment-return`, đó là deep link app hiện
có, không phải lỗi TLS/IPN. Nếu chỉ có Return, kiểm đăng ký IPN bên VNPay. Nếu đã
nhận tiền nhưng chưa phát vé, kiểm worker/queue và trạng thái saga, không gửi lại
callback tự ký hoặc sửa DB để giả thành công.

## Nguồn

- [VNPay IPN, Return và retry](https://sandbox.vnpayment.vn/apis/docs/thanh-toan-pay/pay.html)
- [HTTPS ft84](https/README.md)
- [Deploy K3s](k8s/README.md)
