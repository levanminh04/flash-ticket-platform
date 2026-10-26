# VNPay Sandbox — cấu hình và adapter

## Trạng thái bàn giao

Có adapter Java/Spring cho tạo URL PAY, kiểm IPN, querydr và yêu cầu refund toàn phần có cờ bật riêng. Có controller IPN/Return và route Gateway được bảo vệ bằng cờ mặc định tắt. Không thêm SDK hay dependency mới.

**Chưa có runtime P3–P6:** consumer StartPayment, inbox/outbox relay, handler ghi charge/Saga và worker hoàn tiền. Vì vậy chưa có API mua vé thực sự tạo URL qua adapter trong sản phẩm. Chưa nghiệm thu PAYMENT_SANDBOX_VERIFIED hoặc PLATFORM_E2E_VERIFIED. Không bật callbacks chỉ vì đã điền được merchant.

`queryRefund` hiện trả UNKNOWN/REFUND_LOOKUP_UNAVAILABLE và không gọi mạng: API PAY querydr không tự chứng minh kết quả của một refund request identity. Sau timeout hoàn tiền phải giữ nghĩa vụ và đối soát, không tạo request mới để thử lại.

## Chuẩn bị

- JDK 21, Maven, Config Server và nền PostgreSQL/JWT của Payment theo README.
- Merchant Sandbox được cấp qua [đăng ký VNPay](https://sandbox.vnpayment.vn/devreg/): TmnCode, HashSecret và quyền API query/refund.
- HTTPS origin public của Gateway cho hai callback. Đăng ký IPN URL với merchant; đổi cấu hình local không tự đổi đăng ký phía VNPay.
- Không dùng merchant production; không đo tải trên Sandbox.

Spring không tự đọc `.env`. Điền environment của tiến trình Payment qua IDE/terminal/secret store. Config Server giữ placeholder và không nhận secret merchant vào environment của nó.

### Nạp cấu hình Sandbox cục bộ trên `dev/tuan`

Lưu thông tin merchant được cấp trong `.env` tại gốc repository (được Git bỏ qua),
không điền khóa thật vào `.env.example` hoặc YAML. Từ gốc repository, nạp các biến
VNPAY vào đúng terminal sẽ chạy Payment:

```powershell
Get-Content -LiteralPath .env | ForEach-Object {
    if ($_ -match '^(PAYMENT_VNPAY_[A-Z_]+)=(.*)$') {
        [Environment]::SetEnvironmentVariable($Matches[1], $Matches[2], 'Process')
    }
}
```

Nếu dùng IDE, cấu hình IDE nạp file `.env` này cho tiến trình Payment. File local
không được chuyển theo khi clone, checkout hoặc push nhánh.

Khi chưa có HTTPS public, để trống `PAYMENT_VNPAY_RETURN_URL` và
`PAYMENT_VNPAY_IPN_URL`, giữ `PAYMENT_VNPAY_ENABLED=false` cùng
`PAYMENT_VNPAY_CALLBACKS_ENABLED=false`. Các IP `127.0.0.1` chỉ dành cho kiểm local.
Khi có domain/tunnel, điền hai URL theo bảng bên dưới và IP phù hợp; gửi IPN URL
cho VNPAY đăng ký. Chỉ bật adapter sau khi đủ cấu hình; chỉ bật callback ở cả
Payment/Gateway sau khi có handler lưu bền như phần Điều kiện bật IPN.
Chưa có domain/handler thì chưa thể kiểm giao dịch Sandbox đầu cuối.

## Biến môi trường

| Biến | Giá trị |
| --- | --- |
| PAYMENT_VNPAY_ENABLED | true để tạo bean PaymentGateway; mặc định false |
| PAYMENT_VNPAY_CALLBACKS_ENABLED | Giữ false cho tới khi P3–P6 cung cấp handler thật; phải đồng nhất ở Payment/Gateway |
| PAYMENT_VNPAY_ENVIRONMENT | sandbox; prod/production bị từ chối khi adapter bật |
| PAYMENT_VNPAY_TMN_CODE | Merchant PAY Sandbox được cấp, 8 chữ/số |
| PAYMENT_VNPAY_HASH_SECRET | Secret được cấp; không trim, không log, không commit |
| PAYMENT_VNPAY_PAYMENT_URL | Mặc định https://sandbox.vnpayment.vn/paymentv2/vpcpay.html |
| PAYMENT_VNPAY_API_URL | Mặc định https://sandbox.vnpayment.vn/merchant_webapi/api/transaction |
| PAYMENT_VNPAY_RETURN_URL | https://your-domain/api/v1/payments/vnpay/return |
| PAYMENT_VNPAY_IPN_URL | https://your-domain/api/v1/payments/vnpay/ipn |
| PAYMENT_VNPAY_SANDBOX_PAYER_IP | IP test được merchant chấp nhận, ví dụ 127.0.0.1 cho kiểm local |
| PAYMENT_VNPAY_API_CLIENT_IP | IP máy chủ gửi query/refund theo deployment merchant |
| PAYMENT_VNPAY_CONNECT_TIMEOUT | Mặc định 2s, dương và không quá 30s |
| PAYMENT_VNPAY_READ_TIMEOUT | Mặc định 5s, dương và không quá 30s |
| PAYMENT_VNPAY_REFUND_ENABLED | Mặc định false; chỉ true sau khi được cấp quyền và sẵn sàng quản lý nghĩa vụ hoàn |

Return/IPN phải cùng HTTPS origin, đúng path, không query/fragment/userinfo. Hai endpoint VNPay chỉ chấp nhận host/path Sandbox đã định, không tự theo redirect.

Ví dụ PowerShell dùng placeholder; không lưu bản có secret thật vào Git:

```powershell
$env:JAVA_HOME = '<duong-dan-JDK-21>'
$env:JAVA_TOOL_OPTIONS = '-Duser.timezone=UTC'
$env:PAYMENT_VNPAY_ENABLED = 'true'
$env:PAYMENT_VNPAY_CALLBACKS_ENABLED = 'false'
$env:PAYMENT_VNPAY_TMN_CODE = '<merchant-duoc-cap>'
$env:PAYMENT_VNPAY_HASH_SECRET = '<secret-sandbox>'
$env:PAYMENT_VNPAY_RETURN_URL = 'https://<gateway-domain>/api/v1/payments/vnpay/return'
$env:PAYMENT_VNPAY_IPN_URL = 'https://<gateway-domain>/api/v1/payments/vnpay/ipn'
$env:PAYMENT_VNPAY_SANDBOX_PAYER_IP = '127.0.0.1'
$env:PAYMENT_VNPAY_API_CLIENT_IP = '<ip-may-chu>'
# Nạp thêm DB/JWT/Config Server theo README trước khi chạy ứng dụng.
mvn spring-boot:run
```

Không dùng profile production/prod với bộ adapter này. Profile local-foundation chỉ kiểm nền, không chứng minh consumer, relay hoặc Saga đang chạy.

## Dùng gateway trong application service

### Cách chia mã theo trách nhiệm

| Vị trí/lớp | Trách nhiệm |
| --- | --- |
| `controller/VnpayCallbackController` | Nhận request, đặt HTTP status/header và trả response |
| `service/vnpay/VnpayCallbackService` | Xử lý luồng IPN/Return, gọi xác minh và handler, quyết định ACK |
| `dto/response/VnpayAck` | Hình dạng JSON RspCode/Message |
| `web/VnpayReturnPage` | Dựng trang Return trung tính từ kết quả service |
| `gateway/vnpay/VnpayGateway` | Cài đặt port PaymentGateway, chuyển từng thao tác đến lớp phụ trách |
| `VnpayPaymentUrlBuilder` / `VnpayNotificationVerifier` | Tạo URL PAY / xác minh IPN |
| `VnpayPaymentQuery` / `VnpayRefundClient` | Query thanh toán / refund và giới hạn tra cứu hoàn |
| `VnpayApiRequestFactory` / `VnpayPaymentResultMapper` | Dựng metadata yêu cầu / kiểm tra và ánh xạ kết quả provider |
| `VnpayApiClient` / `VnpayApiSignature` / `VnpayHttpTransport` | JSON API / chữ ký API / HTTP có giới hạn dung lượng và deadline |
| `VnpayWireFormat` / `VnpayCodec` | Tiền, thời gian, tham chiếu / encoding và chữ ký PAY |

Các lớp provider phụ trợ ở cùng package là chi tiết adapter. Nghiệp vụ tiếp nhận callback nằm ở service; controller không xử lý chữ ký, gọi handler hoặc quyết định kết quả thu tiền.

Test chia thành URL, notification, payment query, refund và HTTP transport. `VnpayTestFixtures`/`VnpayApiFixtures` chỉ chứa dữ liệu và signer độc lập dùng trong test; không có test suite phụ thuộc helper của test suite khác.

Inject `PaymentGateway` qua constructor khi adapter bật. Chỉ gọi sau khi nghiệp vụ đã kiểm owner/guard/snapshot và persist attempt:

```java
var context = new PaymentContext(
    attempt.getId(), attempt.getOrderId(), attempt.getMerchantAccount(),
    attempt.getExpectedAmount(), attempt.getCurrency(),
    attempt.getStartedAt(), snapshot.getExpiresAt());
var url = gateway.createPayment(context);
// Lưu URL có điều kiện theo state/version trước khi trả nó qua GET PaymentView.
```

TxnRef là attempt UUID bỏ dấu gạch ngang. Số tiền VND chỉ nhân 100 trong adapter; giới hạn PAY là 12 chữ số wire, không ép số thập phân hoặc làm tròn. CreateDate lấy startedAt, ExpireDate lấy deadline snapshot, format +07:00; không dùng thời điểm retry làm ngày giao dịch mới. Với URL đã lưu, luôn trả lại bản đó; không tái ký URL theo một merchant/IP mới.

Query dùng cùng context gốc và ID/thời điểm của lần query. Kết quả đã ký vẫn phải `validateAgainst(context)`; adapter thực hiện kiểm này cho query. Request query thành công chưa nghĩa giao dịch đã thu tiền. Timeout, sai chữ ký, mã lạ, đảo/gian lận không được tự biến thành FAILED/paid.

B13 chưa truyền IP buyer trong StartPayment. `sandbox-payer-ip` là dữ liệu kiểm Sandbox, không phải IP thật của khách. Trước production cần quyết định contract riêng; không tự lấy X-Forwarded-For không tin cậy và không tự thêm field làm lệch hash.

## Điều kiện bật IPN

P3–P6 phải cung cấp bean `VerifiedPaymentHandler`, không có implementation giả trong product code. Handler cần:

1. Re-read/khóa context đã persist, gọi `result.validateAgainst(context)` và kiểm liên kết order/provider/merchant.
2. Dedupe theo `(provider, merchant_account, provider_charge_id)` **và nội dung**; một charge ID khác trên attempt đã thành công vẫn là khoản thu thêm cần xử lý.
3. Ghi business state/inbox/outbox nguyên tử; hoàn tất commit trước khi `handle` trả về RECORDED/ALREADY_RECORDED. Không để transaction bao ngoài controller khiến commit xảy ra sau ACK.
4. Giữ nghĩa vụ refund cho thu muộn/thừa; xử lý quan sát chưa rõ theo runtime spec, không coi VNPay 00 là Booking/Saga đã hoàn tất.

Sau đó đặt CALLBACKS_ENABLED=true ở Payment/Gateway và chạy kiểm runtime/HTTPS thật. Bật mà thiếu handler thì Payment fail startup có chủ ý. Route dùng GET chính xác, không qua retry/fallback chuẩn của API buyer; IPN dùng chữ ký provider thay JWT buyer. POST payment-attempts vẫn thuộc Booking.

ACK HTTP 200 với RspCode: 00 đã commit, 02 đã xử lý cùng identity/nội dung, 01 không thấy attempt, 04 sai tiền, 97 sai chữ ký/merchant, 99 dữ liệu/lỗi xử lý. Không log raw query hoặc exception provider. Return chỉ trả trang trung tính và không đọc PaymentView công khai, không query provider hoặc ghi charge.

## Kiểm và bằng chứng

Từ thư mục module, với JDK 21/UTC:

```powershell
mvn test '-Dtest=Vnpay*Test' -B -ntp
mvn verify -B -ntp
```

Chạy verify cả Gateway và Config Server nếu thay cấu hình các module này. Kiểm thông thường không gọi VNPay; HTTP client tests dùng loopback và provider fixtures. Fixture chữ ký PAY/query/refund được tính độc lập bằng Python stdlib trong `src/test/resources/vnpay/generate_vectors.py`; chỉ dùng khóa `key` của test.

DB/Rabbit/concurrency/crash và giao dịch Sandbox cần môi trường riêng cùng handler P3–P6 thật. Không dùng test handler trong HTTP tests để tuyên bố đã kiểm commit PostgreSQL. Khi thiếu điều kiện, ghi NOT_RUN và biến/quyền/phụ thuộc còn thiếu. Không đưa secret, JWT, raw IPN hoặc URL thanh toán đầy đủ vào evidence.

Nguồn đặc tả dùng cho adapter:

- [PAY/IPN/Return 2.1.0](https://sandbox.vnpayment.vn/apis/docs/thanh-toan-pay/pay.html).
- [Query/refund](https://sandbox.vnpayment.vn/apis/docs/truy-van-hoan-tien/querydr%26refund.html): query response ký thêm PromotionCode/PromotionAmount theo tài liệu web; refund response dùng chuỗi riêng. Không tự thử nhiều thuật toán/canonical để “cho qua”.
- [Bảng mã lỗi](https://sandbox.vnpayment.vn/apis/docs/bang-ma-loi/).

Trạng thái thu tiền từ IPN chỉ SUCCEEDED khi cả ResponseCode và TransactionStatus bằng 00 và có transaction ID/PayDate hợp lệ. API query ResponseCode 00 phải xét TransactionStatus riêng. Quyền/API thực của merchant cần đối chiếu lại khi nghiệm thu Sandbox.

## Kết quả kiểm ngày 25/10/2026

- Payment `mvn verify`: 112 test, 0 failure/error/skip; Spotless và SpotBugs PASS.
- Gateway `mvn verify`: 15 test, 0 failure/error/skip; Spotless và SpotBugs PASS.
- Config Server `mvn verify`: 1 test, 0 failure/error/skip; quality gates PASS.
- Review độc lập đã phát hiện timeout response body trên Java 21. Regression gửi header/một phần body trước khi treo đã tái hiện lỗi; client được sửa để chờ hoàn tất bất đồng bộ với deadline và hủy request khi hết hạn. Xem [ghi chú JDK HTTP client của Oracle](https://inside.java/2026/03/04/jdk-26-http-client/).
- Đã kiểm hồi quy rằng context khác VND bị từ chối trước khi gửi refund; khóa trạng thái PAY/query bằng bảng kiểm riêng. Không còn finding chưa xử lý của lượt review.
- Mức đạt: **CONFIG_ADAPTER_READY**. Kiểm transaction/concurrency DB/Rabbit, runtime P3–P6, HTTPS callback với merchant và giao dịch VNPay Sandbox/E2E: **NOT_RUN**. QueryRefund vẫn có giới hạn UNKNOWN đã mô tả ở đầu tài liệu.
- Không commit/push; giữ nguyên các thay đổi có sẵn của người dùng. Không chạy giao dịch production.
