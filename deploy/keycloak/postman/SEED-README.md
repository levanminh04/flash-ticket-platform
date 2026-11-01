# Tài khoản realm flash-ticket

Đã cập nhật và kiểm tra đăng nhập ngày 11/10/2026. Realm có đúng 3 tài khoản người dùng; service accounts được giữ nguyên.

| Username | Password | Realm role | Trạng thái |
|---|---|---|---|
| admin@gmail.com | 123456789a | ADMIN | Enabled |
| buyer@gmail.com | 123456789a | BUYER | Enabled |
| organizer@gmail.com | 123456789a | ORGANIZER | Enabled |

- Cả 3 user có email đã xác minh và không yêu cầu đổi mật khẩu lần đầu.
- Các role mặc định của Keycloak vẫn được giữ. Tài khoản admin@gmail.com trong flash-ticket còn có quyền realm-management: manage-users, query-users, view-users.
- Tài khoản admin@gmail.com thuộc realm master là tài khoản riêng, được giữ nguyên.
- Đã export toàn bộ realm kèm user và thông tin mật khẩu đã băm, rồi cập nhật `deploy/keycloak/import/flash-ticket-realm.json` ngày 11/10/2026. File import chứa đủ 3 user để khôi phục khi khởi tạo database mới. Bản export realm `master` nằm ở `deploy/keycloak/export/master-realm.json`.
- Đã kiểm tra xóa volume và import lại ngày 11/10/2026: đủ 3 user và role, đăng nhập PKCE thành công. Postman CLI chạy 28 request / 63 assertion, không lỗi. Xem `RESET-TEST-RESULTS.md` về kết quả và phạm vi chưa kiểm tra.

## Export tất cả realm và user

Chạy PowerShell trong `D:\DATN\flash-ticket-platform\deploy`. PostgreSQL phải đang chạy.
Không truyền `--realm` để export tất cả realm, gồm `flash-ticket` và `master`.
`--users realm_file` lưu user, role và thông tin mật khẩu đã băm cùng file cấu hình realm.

```powershell
New-Item -ItemType Directory -Force -Path ./keycloak/export | Out-Null
docker compose --env-file ../.env -f docker-compose.keycloak.yml stop keycloak
if ($LASTEXITCODE -ne 0) { throw "Không dừng được Keycloak" }
try {
    docker compose --env-file ../.env -f docker-compose.keycloak.yml run --rm --no-deps -v "${PWD}/keycloak/export:/opt/keycloak/data/export" keycloak export --dir /opt/keycloak/data/export --users realm_file
    if ($LASTEXITCODE -ne 0) { throw "Export thất bại" }
} finally {
    docker compose --env-file ../.env -f docker-compose.keycloak.yml up -d keycloak
}
```

Kết quả nằm trong `deploy/keycloak/export`, gồm `flash-ticket-realm.json` và `master-realm.json`.
Để file import tự khôi phục 3 user khi khởi tạo database mới, sau khi export thành công chạy:

```powershell
Copy-Item ./keycloak/export/flash-ticket-realm.json ./keycloak/import/flash-ticket-realm.json
```

Export không gồm session và lịch sử sự kiện; các file chứa thông tin nhạy cảm như client secret và mật khẩu đã băm.
