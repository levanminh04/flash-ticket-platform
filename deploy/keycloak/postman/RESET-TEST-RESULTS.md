# Kiểm tra reset và import Keycloak — 11/10/2026

Đã xóa volume `deploy_postgres-data`, khởi tạo PostgreSQL mới bằng `deploy/.env`, rồi chạy Keycloak với `--import-realm`. Không seed thủ công sau reset.

## Kết quả

- Realm `flash-ticket` khôi phục đúng 3 user: `admin@gmail.com` (ADMIN), `buyer@gmail.com` (BUYER), `organizer@gmail.com` (ORGANIZER), cùng 2 service accounts.
- Cả 3 user đăng nhập thành công qua Authorization Code + PKCE S256 của client `frontend`, dùng mật khẩu `123456789a`. UserInfo trả đúng danh tính và token chứa business role tương ứng.
- Admin realm `master` đăng nhập được bằng `admin@gmail.com` / `123456789a`. PostgreSQL xác thực mật khẩu mới qua TCP thành công.
- Postman CLI 1.73.0: **28 request, 63 assertion, 0 lỗi**, exit code 0. Lượt cuối chạy khoảng 3 giây.

| Nhóm API | Kết quả |
|---|---|
| Discovery và JWKS | HTTP 200, issuer và signing keys đúng |
| UserInfo của 3 user đã import | HTTP 200, identity và role đúng |
| UserInfo, đọc và cập nhật Account | HTTP 200/204 |
| Token client user-management | HTTP 200 |
| Token client internal-service | HTTP 200; audience duy nhất flash-ticket-api, role duy nhất INTERNAL_SERVICE, không có client roles |
| Tạo, liệt kê, đếm, tìm theo username/email, đọc user và session | HTTP 201/200 |
| Cập nhật, disable/enable, reset password, gán BUYER, logout và xóa user | HTTP 204; đọc lại xác nhận BUYER |
| Admin API không có token | HTTP 401 |
| Admin API dùng token BUYER hoặc INTERNAL_SERVICE | HTTP 403 |

Các request hiện có được lấy từ `deploy/keycloak/postman/import.json` vào bản chạy thử tạm, sắp xếp lại thứ tự phụ thuộc và dùng user riêng `postman.reset.test@example.com` cho thao tác quản lý user. User thử nghiệm đã được xóa; sau test realm vẫn có đúng 3 user người dùng. Luồng chọn token tương tác của Postman được thay bằng token thu được qua PKCE thật trong bản chạy thử. Collection gốc không bị sửa.

Lượt đầu có lỗi ở phần chuẩn bị token/kiểm tra của bản test tạm (token admin-cli không chứa business roles và script chọn token tương tác chưa có token); sau khi dùng token frontend qua PKCE, lượt cuối đạt toàn bộ 63 kiểm tra.

## Phạm vi chưa kiểm tra

- Postman MCP không chạy được: connector yêu cầu reauthentication; MCP dùng API key trả 401 Invalid API Key. Các kết quả trên đến từ Postman CLI chạy local, không phải MCP.
- Không gửi email qua SMTP; chưa chứng minh gửi verify-email hoặc password-action email thành công.
- Các endpoint Booking/Payment/Event/Ticket/User trong collection là mẫu chưa triển khai, chưa kiểm tra các API nghiệp vụ hoặc S2S của các service này.
- Export/import không khôi phục session hay lịch sử sự kiện. Không đánh giá hiệu năng tải lớn.
