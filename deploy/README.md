# Chạy PostgreSQL và Keycloak

Chạy các lệnh từ thư mục gốc repository. Chỉ dùng `.env` ở thư mục gốc; tạo
từ `.env.example` rồi điền cấu hình ứng dụng, PostgreSQL/MongoDB và Keycloak.
File local bị Git bỏ qua; không tạo env riêng trong `deploy` hoặc từng service.

PostgreSQL tự tạo tài khoản và database từ `POSTGRES_USER`, `POSTGRES_PASSWORD`,
`POSTGRES_DB` trong `.env` khi volume dữ liệu còn trống. Mặc định `POSTGRES_DB=keycloak_db`,
nên PostgreSQL tự tạo database riêng cho Keycloak trước khi chạy các script init nghiệp vụ.
Keycloak dùng chung tài khoản PostgreSQL và kết nối tới database này với schema `public`.

Keycloak khởi động trực tiếp bằng entrypoint của image. Compose truyền mật khẩu qua
biến chuẩn `KC_DB_PASSWORD`, không cần script khởi động riêng.

SMTP được cấu hình trong `keycloak/import/flash-ticket-realm.json`, không truyền
qua Compose. Secret của client `internal-service` trong realm là placeholder
`${INTERNAL_SERVICE_CLIENT_SECRET}`, nên Compose vẫn truyền biến này từ file env
để import trên database mới. Realm đã tồn tại sẽ không được import lại khi restart.

Trong `.env`, giữ `POSTGRES_DB=keycloak_db` và
`KC_DB_URL=jdbc:postgresql://postgres:5432/keycloak_db` đồng bộ với nhau (thêm
`?currentSchema=keycloak` vào URL nếu dữ liệu cũ dùng schema `keycloak`).
Đổi `POSTGRES_*` không cập nhật tài khoản/database đã tồn tại trong volume;
với volume cũ chưa có database đích, cần tạo database trước khi chạy Keycloak.

Chạy PostgreSQL trước, đợi container healthy rồi chạy Keycloak:

```powershell
docker compose --env-file .env -f deploy/docker-compose.postgresql.yml -f deploy/docker-compose.keycloak.yml up -d --wait postgres
docker compose --env-file .env -f deploy/docker-compose.postgresql.yml -f deploy/docker-compose.keycloak.yml up -d --wait keycloak
```

Admin Console: http://localhost:8080/admin/flash-ticket/console/

## PostgreSQL dùng chung cho các service

Chỉ dùng **một instance PostgreSQL** trong `docker-compose.postgresql.yml`,
cổng `127.0.0.1:5432`. Các database nghiệp vụ `event_db`, `booking_db`,
`payment_db`, `ticket_db` cùng tồn tại trong instance này với database Keycloak.
MongoDB của User chạy riêng; không tạo thêm PostgreSQL container ở từng service.

Trước lần khởi tạo volume mới, điền `POSTGRES_*`, tám biến
`<SERVICE>_DB_ADMIN_PASSWORD`/`<SERVICE>_DB_VIEWER_PASSWORD` và các biến `USER_DB_*`
trong `.env`. PostgreSQL Compose truyền trực tiếp tám mật khẩu cho script init.
Trong cùng `.env`, đặt `<SERVICE>_DB_PASSWORD` bằng `<SERVICE>_DB_ADMIN_PASSWORD` tương ứng;
`MONGO_URI` dùng mật khẩu `USER_DB_ADMIN_PASSWORD` đã URL-encode, với `authSource=admin`.
Script `postgres/00-business-databases.sh` tạo database/role riêng, áp dụng B12,
cấp quyền chỉ đọc và thêm tiền tố `tbl_`. Script chỉ chạy tự động với volume
trống và từ chối ghi đè business database/role đã có.

Các DB local trước đây đã được chuyển bằng `pg_dump`/`pg_restore` vào volume
dùng chung. Bản sao lưu nằm trong `postgres/backups/` (không đưa vào Git).
Thông tin kết nối chi tiết: [services/LOCAL-DATABASES.md](../services/LOCAL-DATABASES.md).

MongoDB cũng dùng `.env` gốc:

```powershell
docker compose --env-file .env -f services/user-service/compose.db.yaml up -d --wait user-db
```
