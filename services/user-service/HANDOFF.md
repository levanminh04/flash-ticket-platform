# Handoff — User Service scaffold

## Đã có

Scaffold theo Booking Service: bốn Mongo document/repository, DTO và MapStruct,
strict JSON, Problem/exception boundary, safe JSON log, JWT resource server,
constructor DI feature shells và Rabbit converter/error handler.
Controller/service chưa có business method. Chưa có consumer/publisher hoặc Keycloak Admin client.
Kết quả và lệnh kiểm chứng: [TESTING](TESTING.md).

Runtime YAML đã áp dụng tại [user-service.yaml](../../infrastructure/config-server/src/main/resources/config/user-service.yaml).
Boot 4.1.1 dùng `spring.mongodb.uri`; `auto-index-creation=false`, collection `tbl_*` do deploy quản lý.
Rabbit dùng manual ACK, prefetch 20, connection timeout 2s, `dynamic=false`; không tự tạo topology.
Common Eureka/management lấy từ central `application.yaml`. Không có mail, Redis, Feign hoặc OAuth2 Client mới.

## Chạy local với hạ tầng hiện có

1. Cấu hình JDK 21; chạy Config Server và Eureka. Nếu đổi central YAML thì build/chạy lại Config Server.
2. Nếu dùng Mongo trên 119, mở hoặc dùng lại tunnel:

   ```bash
   ssh -N -o ExitOnForwardFailure=yes -L 127.0.0.1:17017:127.0.0.1:27017 ft119
   ```

   `ft119` là alias SSH của máy thành viên; giữ terminal tunnel mở.
3. Nạp env vào **process** User bằng IDE/shell; Spring không tự đọc `.env` gốc.

| Env | Ý nghĩa / mặc định |
|---|---|
| `CONFIG_SERVER_URL` | Mặc định `http://localhost:8888`; production import bắt buộc |
| `EUREKA_SERVER_URL` | URL Eureka theo common config |
| `USER_SERVICE_PORT` | Mặc định 8085 |
| `MONGO_URI` | URI database `user_db`, authSource đúng tài khoản; URL-encode password |
| `KEYCLOAK_ISSUER_URI` | Issuer realm flash-ticket |
| `KEYCLOAK_JWK_SET_URI` | JWKS của cùng realm |
| `KEYCLOAK_AUDIENCE` | Mặc định `flash-ticket-api` |
| `RABBITMQ_HOST`, `RABBITMQ_PASSWORD` | Host và secret broker hiện có |
| `RABBITMQ_PORT`, `RABBITMQ_USERNAME`, `RABBITMQ_VHOST` | Mặc định 5672, `flash_ticket_app`, `/` |
| `USER_SERVICE_VERSION`, `USER_INSTANCE_ID`, `USER_ENVIRONMENT` | Metadata log tùy chọn |

Ví dụ cấu trúc Mongo URI, thay placeholder bằng secret ngoài Git:
`mongodb://USER:URL_ENCODED_PASSWORD@127.0.0.1:17017/user_db?authSource=admin&maxPoolSize=5`.
Chọn `authSource` theo nơi tạo user: `user_erd` chỉ đọc ở `user_db`, không dùng làm tài khoản ghi của service.

```powershell
# Từ services/user-service, sau khi env đã được nạp
rtk proxy .\mvnw.cmd -B -ntp spring-boot:run
```

Kiểm tên `user-service`, port 8085 (hoặc override), config được tải và đăng ký Eureka;
gọi `GET /actuator/health`. Health có thể báo DOWN khi dependency chưa sẵn sàng.
Smoke hạ tầng chỉ đọc: Mongo `ping`/list metadata, Rabbit mở/đóng connection,
không declare/publish/consume hoặc ghi fixture vào server119. API business chưa có handler để smoke.
Smoke này **chưa chạy** trong đợt scaffold vì Config Server/Eureka local chưa mở.

## Mongo deploy và phần tiếp theo

- [Compose](../../deploy/mongodb/compose.yaml), [init](../../deploy/mongodb/10-user.js),
  [prefix](../../deploy/mongodb/99-collection-prefix.js),
  [schema chuẩn](../../docs/architecture/data/mongodb/schema.cjs).
- Mongo trên server119 đã được kiểm tra chỉ đọc và healthy ngày 25/10/2026;
  việc chuyển file sang `deploy/mongodb/` mới áp dụng trong repository, chưa đồng bộ path trên server.
  Scaffold không chạy lại init hoặc sửa dữ liệu/validator trên server.
- [Guide Keycloak Admin API cho task sau](../../docs/guides/user-service-keycloak-admin-integration.md):
  có dependency/config/code mẫu, chưa đưa vào runtime và chưa yêu cầu secret `user-management`.
- Gateway anonymous profile, identity publisher và payload, B13 runtime approval/recovery,
  B15 V17/V18 và RCA end-to-end còn để task sau. JWT route policy đã có nhưng không thay business implementation.
- Tham khảo [Rabbit topology](../../deploy/rabbitmq/definitions.json) và
  [Keycloak internal handoff](../../deploy/keycloak/postman/INTERNAL-SERVICE-HANDOFF.md).
