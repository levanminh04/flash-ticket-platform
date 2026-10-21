# Chạy PostgreSQL và Keycloak

Mở terminal trong folder `deploy`. Chỉ dùng hai file env local:

- `../.env`: cấu hình chạy ứng dụng và Keycloak, tạo từ `../.env.example`.
- `.env.dev`: chỉ khởi tạo PostgreSQL/MongoDB bằng Compose, tạo từ `.env.example`.

Điền các secret còn trống. Không cần `deploy/.env` hoặc env riêng trong từng service.
Mật khẩu DB trong hai file phải khớp nhau; cả hai file local đều bị Git ignore.

PostgreSQL tự tạo tài khoản và database từ `POSTGRES_USER`, `POSTGRES_PASSWORD`,
`POSTGRES_DB` khi volume dữ liệu còn trống. Keycloak dùng chung các giá trị này,
mặc định kết nối tới database `POSTGRES_DB` với schema `public`; không cần script init.

Keycloak khởi động trực tiếp bằng entrypoint của image. Compose truyền mật khẩu qua
`KCRAW_DB_PASSWORD` để giữ nguyên ký tự `$`, không cần script khởi động riêng.

SMTP được cấu hình trong `keycloak/import/flash-ticket-realm.json`, không truyền
qua Compose. Secret của client `internal-service` trong realm là placeholder
`${INTERNAL_SERVICE_CLIENT_SECRET}`, nên Compose vẫn truyền biến này từ file env
để import trên database mới. Realm đã tồn tại sẽ không được import lại khi restart.

Nếu đang dùng dữ liệu Keycloak cũ trong `keycloak_db`, giữ
`KC_DB_URL=jdbc:postgresql://postgres:5432/keycloak_db` trong `../.env` (thêm
`?currentSchema=keycloak` vào URL nếu dùng schema `keycloak`). Compose dùng
`POSTGRES_USER`/`POSTGRES_PASSWORD` từ `../.env` để Keycloak kết nối DB; các giá trị này
phải khớp `.env.dev`. Đổi `POSTGRES_*` không cập nhật tài khoản/database đã tồn tại trong volume.

Chạy PostgreSQL trước, đợi container healthy rồi chạy Keycloak:

```powershell
docker compose --env-file .env.dev -f docker-compose.postgresql.yml up -d --build
docker compose --env-file ../.env -f docker-compose.keycloak.yml up -d --build
```

Admin Console: http://localhost:8080/admin/flash-ticket/console/

## PostgreSQL dùng chung cho các service

Chỉ dùng **một instance PostgreSQL** trong `docker-compose.postgresql.yml`,
cổng `127.0.0.1:5432`. Các database nghiệp vụ `event_db`, `booking_db`,
`payment_db`, `ticket_db` cùng tồn tại trong instance này với database Keycloak.
MongoDB của User chạy riêng; không tạo thêm PostgreSQL container ở từng service.

Trước lần khởi tạo volume mới, điền `POSTGRES_*`, tám biến
`<SERVICE>_DB_ADMIN_PASSWORD`/`<SERVICE>_DB_VIEWER_PASSWORD` và các biến `USER_DB_*`
trong `.env.dev`. PostgreSQL Compose truyền trực tiếp tám mật khẩu cho script init.
Trong `../.env`, đặt `<SERVICE>_DB_PASSWORD` bằng `<SERVICE>_DB_ADMIN_PASSWORD` tương ứng;
`MONGO_URI` dùng mật khẩu `USER_DB_ADMIN_PASSWORD` đã URL-encode, với `authSource=admin`.
Script `postgres/00-business-databases.sh` tạo database/role riêng, áp dụng B12,
cấp quyền chỉ đọc và thêm tiền tố `tbl_`. Script chỉ chạy tự động với volume
trống và từ chối ghi đè business database/role đã có.

Các DB local trước đây đã được chuyển bằng `pg_dump`/`pg_restore` vào volume
dùng chung. Bản sao lưu nằm trong `postgres/backups/` (không đưa vào Git).
Thông tin kết nối chi tiết: [services/LOCAL-DATABASES.md](../services/LOCAL-DATABASES.md).

MongoDB dùng cùng `.env.dev`, chạy từ thư mục `deploy`:

```powershell
docker compose --env-file .env.dev -f ../services/user-service/compose.db.yaml up -d --wait
```
