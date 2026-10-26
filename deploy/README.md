# Chạy hạ tầng local

Chạy các lệnh từ thư mục gốc repository. Chỉ dùng `.env` ở thư mục gốc; tạo
từ `.env.example` rồi điền cấu hình ứng dụng, PostgreSQL/MongoDB và Keycloak.
File local bị Git bỏ qua; không tạo env riêng trong `deploy` hoặc từng service.

PostgreSQL và MongoDB chỉ publish cổng trên `127.0.0.1`; từ máy khác dùng SSH
tunnel tới host chạy database. RabbitMQ (AMQP và Management UI), Redis và Keycloak
publish trên `0.0.0.0`; khả năng truy cập từ xa còn phụ thuộc firewall/Security Group.
Khi truy cập Keycloak từ xa, đặt `KC_HOSTNAME` trong `.env` thành URL thực tế.

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
docker compose --env-file .env -f deploy/docker-compose.postgresql.yml -f deploy/docker-compose.keycloak.yml -f deploy/docker-compose.redis.yml -f deploy/docker-compose.rabbitmq.yml up -d --wait postgres
docker compose --env-file .env -f deploy/docker-compose.postgresql.yml -f deploy/docker-compose.keycloak.yml -f deploy/docker-compose.redis.yml -f deploy/docker-compose.rabbitmq.yml up -d --wait keycloak
```

Admin Console: http://localhost:8080/admin/flash-ticket/console/

## RabbitMQ

Điền ba cặp tài khoản trong `.env` gốc: `RABBITMQ_ADMIN_USERNAME/PASSWORD`,
`RABBITMQ_USERNAME/PASSWORD` cho ứng dụng và `RABBITMQ_IDENTITY_USERNAME/PASSWORD`
cho publisher Keycloak. Username phải khác nhau, không dùng `guest`; bắt đầu bằng chữ,
tối đa 64 ký tự chữ, số, `_`, `-`. Password không rỗng hoặc chứa ký tự điều khiển.
Compose yêu cầu phiên bản từ 2.30 để chạy lifecycle hook.

```powershell
docker compose --env-file .env -f deploy/docker-compose.postgresql.yml -f deploy/docker-compose.keycloak.yml -f deploy/docker-compose.redis.yml -f deploy/docker-compose.rabbitmq.yml up -d --wait rabbitmq
```

Lệnh này tự tạo topology và tài khoản ngay lần đầu, không cần init thủ công.
Các lệnh dùng cùng bộ Compose file để tránh cảnh báo orphan; mỗi lệnh chỉ target
service cần chạy. Management UI mặc định: http://localhost:15672, đăng nhập tài khoản admin.
Ứng dụng từ IDE dùng `RABBITMQ_HOST=localhost`, cổng `RABBITMQ_PORT` mặc định 5672;
Nếu cổng mặc định bị chiếm, đổi cả hai biến port trong root env rồi dùng port mới;
container cùng network dùng host `rabbitmq`, cổng 5672. Management port cấu hình qua
`RABBITMQ_MANAGEMENT_PORT`; memory limit qua `RABBITMQ_MEMORY_LIMIT` (mặc định `1g`).

`rabbitmq/rabbitmq.conf` bật import native từ `rabbitmq/definitions.json` khi broker
boot: vhost `/`, `business.ex` (topic), `identity.ex` (direct), `dlx.ex` (direct),
15 queue xử lý đơn nhiệm và 15 DLQ, toàn bộ durable/quorum. Mỗi main queue có một
routing key đầu vào và policy DLX riêng. JSON không chứa tài khoản hoặc secret.
RabbitMQ bỏ qua bước tạo default user khi boot import trên node mới, nên hook
`post_start` ngay trong Compose dùng `rabbitmqctl` để tạo/cập nhật user và quyền từ env.

Admin có toàn quyền và Management UI. App chỉ publish `business.ex`/`dlx.ex`, consume
15 main queue; identity chỉ publish `identity.ex`. Hai tài khoản này không có quyền
Management UI hoặc tự khai báo topology. Listener ứng dụng cần dùng topology đã tạo,
không tự declare/bind. Đây là tài khoản app dùng chung cho local, chưa tách theo service.

Reject/nack không requeue đưa message sang DLQ tương ứng. Policy dùng dead-letter
`at-least-once`, `reject-publish`, delivery-limit 20; không phải retry có backoff.
DLQ không có TTL, không tự phát lại và chưa có consumer xử lý lỗi. Broker một node
không có HA; volume `rabbitmq-data` giữ message/tài khoản khi restart.

Docker daemon tự restart container không chạy lại hook Compose; tài khoản đã provision
được lưu trong volume. Khi đổi credentials hoặc hook lỗi, sửa `.env`/config rồi chạy:

```powershell
docker compose --env-file .env -f deploy/docker-compose.postgresql.yml -f deploy/docker-compose.keycloak.yml -f deploy/docker-compose.redis.yml -f deploy/docker-compose.rabbitmq.yml up -d --force-recreate --wait rabbitmq
```

Lệnh giữ nguyên volume và cập nhật ba tài khoản. Import không xóa tài nguyên cũ;
đổi type/argument queue đã có cần kế hoạch chuyển riêng. Không dùng `down -v`, purge
hoặc `--remove-orphans` để xử lý lỗi vì có thể mất dữ liệu hạ tầng.

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

MongoDB có Compose và script khởi tạo riêng trong `deploy/mongodb/`, dùng `.env` gốc:

```powershell
docker compose --env-file .env -f deploy/mongodb/compose.yaml up -d --wait user-db
```

## Redis local

Chạy từ root với `.env` gốc:

```powershell
docker compose --env-file .env -f deploy/docker-compose.redis.yml up -d --wait redis
```

Redis publish trên mọi interface IPv4 của host, password bắt buộc và có healthcheck. Hướng dẫn
Spring Boot, env, lệnh kiểm tra và kết quả: [redis/handoff.md](redis/handoff.md).

## Build Booking, Ticket, User trên server 119

Mỗi service có Dockerfile riêng: [Booking](../services/booking-service/Dockerfile),
[Ticket](../services/ticket-service/Dockerfile), [User](../services/user-service/Dockerfile).
Cache Maven, runtime JRE 23, non-root, heap tối đa 75% giới hạn 768 MiB/container,
UTC và log rotation 10 MiB × 3. Mỗi build context chỉ gửi POM và source của service đó.

Từ root repo trên **server 119 Linux**, dùng `.env` gốc đã điền credential:

```bash
docker compose --env-file .env -f deploy/docker-compose.services.yml build
docker compose --env-file .env -f deploy/docker-compose.services.yml up -d
```

Host network truy cập DB bind loopback; port mặc định Booking/Ticket/User là 8082/8084/8085.
Config Server (8888) và Eureka (8761) phải chạy trên host; Config Server phục vụ YAML mới nhất.
DB dùng `127.0.0.1:5432`, Mongo `127.0.0.1:27017/user_db`; điền `MONGO_URI` có credential
URL-encode và `authSource=admin`. Rabbit/Redis/JWKS/token endpoint có thể dùng localhost;
`KEYCLOAK_ISSUER_URI` khớp issuer public. Booking cần `INTERNAL_SERVICE_CLIENT_SECRET` thật
và `KEYCLOAK_TOKEN_URI`; SMTP Ticket theo cấu hình hiện có. Host network này dành cho Linux 119.

Build image bỏ test; chạy Maven verify riêng trước deploy.

### Chỉ build/chạy service vừa sửa

Chạy từ root repo trên server 119; chọn **một** lệnh tương ứng:

```bash
docker compose --env-file .env -f deploy/docker-compose.services.yml up -d --build --no-deps ticket-service
docker compose --env-file .env -f deploy/docker-compose.services.yml up -d --build --no-deps booking-service
docker compose --env-file .env -f deploy/docker-compose.services.yml up -d --build --no-deps user-service
```

Compose chỉ build và tạo/cập nhật container của service được chọn. Các service còn lại
giữ nguyên trạng thái; service chưa chạy sẽ không tự bật. Config Server, Eureka và các
tài nguyên service cần phải chạy sẵn. Không cần tách Compose hoặc thêm profile/script.

Xem log hoặc dừng riêng Ticket (thay tên tương tự cho Booking/User):

```bash
docker compose --env-file .env -f deploy/docker-compose.services.yml logs -f --tail 100 ticket-service
docker compose --env-file .env -f deploy/docker-compose.services.yml stop ticket-service
```

Khi cập nhật một service, dùng `up` như trên; `down` sẽ dừng toàn bộ project Compose.
Các lệnh này build từ source trên server. Khi CI build/push image lên registry,
thêm `image:` cho từng service rồi deploy bằng `pull <service>` và
`up -d --no-deps --no-build <service>`.
