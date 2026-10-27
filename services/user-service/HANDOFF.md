# Handoff — User Service scaffold

## Đã có

Scaffold theo Booking Service: bốn Mongo document/repository, DTO và MapStruct,
strict JSON, Problem/exception boundary, safe JSON log, JWT resource server,
constructor DI feature shells và Rabbit converter/error handler.
Controller/service chưa có business method. Chưa có consumer/publisher hoặc Keycloak Admin client.
Kết quả và lệnh kiểm chứng: [TESTING](TESTING.md).

Runtime YAML đã áp dụng tại [user-service.yaml](../../infrastructure/config-server/src/main/resources/config/user-service.yaml).
Boot 4.1.1 dùng `spring.mongodb.uri`; `auto-index-creation=false`, collection `tbl_*` do deploy quản lý.
Rabbit mặc định dùng manual ACK, prefetch 20, connection timeout 2s, `dynamic=false`; không tự tạo topology. Listener identity dùng factory riêng với Spring AUTO ACK và prefetch 1.
Common Eureka/management lấy từ central `application.yaml`. Không có mail, Redis, Feign hoặc OAuth2 Client mới.

## Chạy local với hạ tầng hiện có

1. Cấu hình JDK 23; chạy Config Server và Eureka. Nếu đổi central YAML thì build/chạy lại Config Server.
2. Khi dùng hạ tầng 119, mở hoặc dùng lại tunnel Mongo/Rabbit đúng port của root `.env`:

   ```bash
   ssh -N -o ExitOnForwardFailure=yes -L 127.0.0.1:17017:127.0.0.1:27017 -L 127.0.0.1:5672:127.0.0.1:5672 ft119
   ```

   `ft119` là alias SSH của máy thành viên; giữ terminal tunnel mở.
   Mongo dùng `127.0.0.1:17017`, Rabbit dùng `localhost:5672` qua tunnel.
   Dừng RabbitMQ local trước khi mở tunnel nếu Windows service này đang giữ cổng `5672`.
   Keycloak issuer discovery truy cập trực tiếp địa chỉ119, không cần tunnel Keycloak.
   Chọn local Mongo port `17017` vì máy hiện tại từ chối bind `27017` (Windows error 10013).
3. Nạp env vào **process** User bằng IDE/shell; Spring không tự đọc `.env` gốc.
   Trong IntelliJ, thêm `.env` ở root repo bằng plugin EnvFile; không tạo bản sao secret theo từng service.

| Env | Ý nghĩa / mặc định |
|---|---|
| `CONFIG_SERVER_URL` | Mặc định `http://localhost:8888`; production import bắt buộc |
| `EUREKA_SERVER_URL` | URL Eureka theo common config |
| `USER_SERVICE_PORT` | Mặc định 8085 |
| `MONGO_URI` | URI database `user_db`, authSource đúng tài khoản; URL-encode password |
| `KEYCLOAK_ISSUER_URI` | Issuer realm flash-ticket |
| `KEYCLOAK_AUDIENCE` | Mặc định `flash-ticket-api` |
| `RABBITMQ_HOST`, `RABBITMQ_PASSWORD` | Host và secret broker hiện có |
| `RABBITMQ_PORT`, `RABBITMQ_USERNAME`, `RABBITMQ_VHOST` | Mặc định 5672, `flash_ticket_app`, `/` |
| `USER_SERVICE_VERSION`, `USER_INSTANCE_ID`, `USER_ENVIRONMENT` | Metadata log tùy chọn |

JWT dùng issuer discovery: Spring tự đọc metadata và tìm JWKS, không cần
`KEYCLOAK_JWK_SET_URI` cho User. Khi dùng Keycloak119, đặt
`KEYCLOAK_ISSUER_URI=http://3.106.109.119:8080/realms/flash-ticket`.
JVM phải truy cập được issuer và endpoint JWKS được công bố; bỏ JWKS override nghĩa là
việc tải khóa không còn được chuyển riêng qua tunnel localhost.

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
Smoke bổ sung sau khi sửa root env: User `health=UP`, Mongo/Rabbit health đều UP,
đăng ký Eureka UP tại port8085, anonymous `/api/v1/me` trả401 Problem; issuer discovery
và JWKS của Keycloak119 truy cập được. Chạy bằng JDK21 với tunnel và bộ env thật.
Smoke mới nhất ngày 26/10/2026 dùng Rabbit `localhost:5672` sau khi dừng Windows service
`RabbitMQ` đang giữ cổng này. Các process User/Config Server/Eureka tạo để kiểm tra đã đóng;
tunnel Mongo17017/Rabbit5672 được giữ mở để chạy IDE. Khi mở lại phiên làm việc, kiểm tra
listener/tunnel và nạp root env theo hướng dẫn trên.

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

## Identity consumer — ObjectMapper (27/10/2026)

`IdentityUserCreatedListener` dùng `ObjectMapper.readValue(body, UserCreatedV1.class)` trực tiếp,
không còn hàm decode hoặc kiểm từng JsonNode bằng tay. Mapper kế thừa cấu hình chung để từ chối
field lạ và dữ liệu sai kiểu; routing key, giới hạn64KiB và realm vẫn được kiểm trước khi ghi Mongo.
Theo lựa chọn user, consumer nhận cả displayName null/không có, UUID Base64 và timestamp có offset
do Jackson hỗ trợ. SPI vẫn phát UUID canonical, UTC Z và bỏ displayName khi không có; schema/fixture
producer không đổi. ACK sau ghi, retry DB, DLQ và idempotency giữ nguyên. Test listener/registrar
6/6 đạt; chưa deploy thay đổi consumer này lên119.

Property `KEYCLOAK_IDENTITY_REALM_ID` được nạp trực tiếp tại field `realmId` của Listener bằng
`@Value`, theo mẫu `JwtTokenUtil`/`JwtTokenFilter` trong Shop App. Listener vẫn nhận dependency
bằng constructor; Spring inject field sau khi tạo bean từ `IdentityMessagingConfig`.

Listener identity dùng Spring `AcknowledgeMode.AUTO`, không nhận `Channel`: Spring ACK khi method
trả về thành công. Dữ liệu không hợp lệ hoặc hết retry ném `AmqpRejectAndDontRequeueException`;
message được đưa vào DLQ nếu queue có cấu hình DLX. Chưa kiểm tra thay đổi AUTO với broker thật.

`ensureRegistered` dùng `@Retryable` của Spring Framework 7, được bật bởi
`@EnableResilientMethods` trong `IdentityMessagingConfig`. Registrar là Spring bean không `final`
để Spring tạo proxy; lỗi DB trong danh sách được thử lại tối đa 2 lần, chờ 1s rồi 5s. Listener gọi
registrar qua proxy và xử lý lỗi sau khi retry hết; không dùng `RetryTemplate` trong listener.

`IdentityUserRegistrar` dùng `UserRepository.findByIdentitySubject` rồi `save` khi chưa có user.
Unique index chặn ghi trùng; nếu save gặp duplicate, registrar tìm lại user cùng subject: có thì
coi là thành công, không có thì ném lỗi tiếp. Theo yêu cầu user, không kiểm `keyPattern` hoặc
từng field của hồ sơ trong nhánh này. Repository identity được tạo từ template riêng có write concern
journal/w1/timeout5s. Test MongoDB119 đã kiểm 8 lượt
đồng thời và redelivery; code mới chưa được deploy lên server.
Unique index `uq_users_identity_subject` do setup MongoDB quản lý; registrar không kiểm index lúc
khởi động, nên cần bảo đảm setup index đã chạy trên từng môi trường.
- Tham khảo [Rabbit topology](../../deploy/rabbitmq/definitions.json) và
  [Keycloak internal handoff](../../deploy/keycloak/postman/INTERNAL-SERVICE-HANDOFF.md).
