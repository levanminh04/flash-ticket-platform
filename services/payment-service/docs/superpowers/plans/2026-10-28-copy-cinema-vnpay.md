# Chuyển VNPay từ Cinema sang Payment

Yêu cầu: copy cách triển khai VNPay của `Cinema-Booking-System` sang payment-service.

Nguồn: `Config/VNPayConfig`, `Shared/enums/VNPayUtil` (đúng import của Cinema PaymentService), các phương thức VNPay trong PaymentService và PaymentController. Giữ constructor injection/Lombok, service cụ thể và helper tĩnh như Cinema.

- [x] Copy config và util; kiểm HMAC/URL/callback bằng fixture độc lập.
- [x] Đưa createPayment/buildVNPayUrl/handleIPN/handleReturn/buildAppReturnUri vào PaymentService; controller chỉ điều phối.
- [x] Nối dữ liệu hiện có: booking -> order snapshot, payment -> attempt, transaction -> charge. Không sửa schema hoặc đưa entity Booking/notification/email của monolith vào microservice.
- [x] Nối API `/api/v1/payments`, `/api/v1/payments/vnpay/ipn`, `/api/v1/payments/vnpay/return`, config môi trường, security và Gateway.
- [x] Chạy test, formatter, verify và review diff.

Các điều chỉnh bắt buộc: dùng attempt UUID làm TxnRef để retry của cùng order không nhận nhầm callback; khóa dữ liệu trong giao dịch; giữ ownership/JWT; IPN trả JSON ACK trực tiếp cho provider. Amount lấy từ snapshot, không nhận từ client. IPN lưu receipt và trạng thái attempt; confirmation/Saga xác nhận Booking vẫn là phần tích hợp liên service chưa có. Không copy `refund` của Cinema vì nó chỉ đổi trạng thái local, không thực hiện refund VNPay.

Giữ working tree `dev/tuan` hiện tại vì đang chứa refactor và các thay đổi gỡ code cũ của người dùng. Không restore các file đã xóa hoặc commit thay người dùng.

Kiểm trọng tâm: đúng helper Cinema đang dùng; URL encode trước ký; chữ ký/merchant/amount sai không ghi dữ liệu; IPN lặp không tạo charge trùng; return chỉ đọc; create không vượt ownership/hạn snapshot và chỉ có một attempt chưa kết thúc.

## Bằng chứng và quyết định triển khai

- Baseline trước thay đổi: Maven test bằng Java 21/UTC, 101 tests, 0 failure/error/skip.
- RED: test util không compile vì chưa có VNPayUtil. Test HTTP mới nhận 401 thay vì ACK 200; test Gateway mới thiếu route và callback bị từ chối. Đã quan sát các thất bại trước khi nối endpoint/security.
- GREEN ban đầu: 14 tests VNPay về HMAC RFC 4231, fixture URL ký độc lập bằng .NET, tampering, IPN/receipt và redirect.
- Review độc lập xác nhận dùng đúng helper import của Cinema. Phát hiện Important: kết quả chưa rõ bị biến thành FAILED có thể cho phép mở lần trả tiền mới. Đã đối chiếu [bảng mã VNPay](https://sandbox.vnpayment.vn/apis/docs/bang-ma-loi/), thêm 4 trường hợp RED (expected UNKNOWN nhưng nhận FAILED), rồi giữ UNKNOWN/finishedAt=null và cho IPN cuối hoàn tất UNKNOWN.
- SpotBugs phát hiện 4 nhánh bắt NullPointerException; sửa thành kiểm tham số thiếu trước parse, giữ ACK tương ứng. Không giảm quality gate.
- Formatter chỉ áp dụng các file của lần chuyển VNPay này; verify cuối không giới hạn spotlessFiles để kiểm toàn bộ module.
- Giới hạn: không chạy PostgreSQL thật, sandbox merchant thật, Booking acceptance, outbox/Saga, email hoặc phát hành vé. Các phần đó không được coi là đã nghiệm thu qua test mock.
- Minor hoãn: khi tạo lại URL đồng thời IPN, optimistic locking có thể trả HTTP 500. @Version bảo vệ trạng thái tài chính khỏi bị ghi đè; chưa thêm khóa attempt vào luồng tạo URL.

## Kiểm tra cuối — 2026-10-29

`mvn verify -B -ntp` bằng JDK 21/UTC đã chạy trên cả ba module:

| Module | Tests | Failure/error/skip | Spotless | SpotBugs | Build |
| --- | ---: | --- | --- | --- | --- |
| payment-service | 120 | 0/0/0 | PASS, 134 file | 0 bug/error | SUCCESS |
| api-gateway | 14 | 0/0/0 | PASS, 11 file | 0 bug/error | SUCCESS |
| config-server | 1 | 0/0/0 | PASS, 2 file | 0 bug/error | SUCCESS |

Log mỗi module: `target/vnpay-cinema-final.log`. `git diff --check` đạt. Các test PostgreSQL bị loại theo cấu hình Maven hiện có không được tính là đã chạy. Giữ toàn bộ thay đổi ở working tree để người dùng xem; không commit hoặc push.
