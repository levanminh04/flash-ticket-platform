# Chạy PostgreSQL và Keycloak

Mở terminal trong folder `deploy`, dùng file `.env.dev` đã cấu hình.

PostgreSQL tự tạo tài khoản và database từ `POSTGRES_USER`, `POSTGRES_PASSWORD`,
`POSTGRES_DB` khi volume dữ liệu còn trống. Keycloak dùng chung các giá trị này,
mặc định kết nối tới database `POSTGRES_DB` với schema `public`; không cần script init.

Keycloak khởi động trực tiếp bằng entrypoint của image. Compose truyền mật khẩu qua
`KCRAW_DB_PASSWORD` để giữ nguyên ký tự `$`, không cần script khởi động riêng.

SMTP được cấu hình trong `keycloak/import/flash-ticket-realm.json`, không truyền
qua Compose. Secret của client `internal-service` trong realm là placeholder
`${INTERNAL_SERVICE_CLIENT_SECRET}`, nên Compose vẫn truyền biến này từ file env
để import trên database mới. Realm đã tồn tại sẽ không được import lại khi restart.

Nếu đang dùng dữ liệu Keycloak cũ trong `keycloak_db`, schema `keycloak`, giữ
`KC_DB_URL=jdbc:postgresql://postgres:5432/keycloak_db` và `KC_DB_SCHEMA=keycloak`
trong file env. `KC_DB_USERNAME` và `KC_DB_PASSWORD` riêng không còn được Compose
sử dụng. Đổi `POSTGRES_*` không cập nhật tài khoản/database đã tồn tại trong volume.

Chạy PostgreSQL trước, đợi container healthy rồi chạy Keycloak:

```powershell
docker compose --env-file .env.dev -f docker-compose.postgresql.yml up -d --build
docker compose --env-file .env.dev -f docker-compose.keycloak.yml up -d --build
```

Admin Console: http://localhost:8080/admin/flash-ticket/console/

## PostgreSQL dùng chung cho các service

Chỉ dùng **một instance PostgreSQL** trong `docker-compose.postgresql.yml`,
cổng `127.0.0.1:5432`. Các database nghiệp vụ `event_db`, `booking_db`,
`payment_db`, `ticket_db` cùng tồn tại trong instance này với database Keycloak.
MongoDB của User chạy riêng; không tạo thêm PostgreSQL container ở từng service.

Trước lần khởi tạo volume mới, chuẩn bị bốn file thông tin đăng nhập từ các
`services/<service>-service/.env.<service>-db.example`. Compose đọc các file
`.env.<service>-db` tương ứng; giữ mật khẩu ngoài Git.
Script `postgres/00-business-databases.sh` tạo database/role riêng, áp dụng B12,
cấp quyền chỉ đọc và thêm tiền tố `tbl_`. Script chỉ chạy tự động với volume
trống và từ chối ghi đè business database/role đã có.

Các DB local trước đây đã được chuyển bằng `pg_dump`/`pg_restore` vào volume
dùng chung. Bản sao lưu nằm trong `postgres/backups/` (không đưa vào Git).
Thông tin kết nối chi tiết: [services/LOCAL-DATABASES.md](../services/LOCAL-DATABASES.md).
