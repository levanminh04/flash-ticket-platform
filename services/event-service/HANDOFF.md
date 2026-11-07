# Handoff — Event Service

## Cấu hình riêng service — 04/11/2026

Runtime YAML nằm trong [application.yaml](src/main/resources/application.yaml),
đóng gói cùng JAR/image; Event không còn Config Client/import hoặc yêu cầu
`CONFIG_SERVER_URL`. Giữ nguyên env/default nghiệp vụ từ central Event YAML và
default Eureka/Actuator, cùng multipart/Tomcat giới hạn đã có trong module.
Deploy kiểm candidate label `org.flashticket.configuration=service-local-v1`
trước thay container; giữ digest/revision, JWT health, lock, snapshot và rollback.
Không đổi env AWS/root, schema, worker flags, contract, Config Server hay service khác.
Thay YAML cần image mới. Bản central cũ chỉ còn phục vụ image cũ/rollback.
Java 21/UTC `mvnw verify` PASS: 144 test, 0 failure/error/skip; Spotless và
SpotBugs PASS (0 bug/error). Test mới nạp production YAML bằng Spring ConfigData,
không cần Config Server; kiểm role/schema DB, JWT/OAuth, broker và worker flags.
JAR chứa đúng YAML của module, không còn Config Client JAR. Bash syntax,
ShellCheck và actionlint PASS. 27 ca deploy/tunnel CLI đều đã PASS: lượt đầy đủ
có 1 ca health-failure vượt 120s dưới tải song song; chạy lại riêng ca đó PASS
72.7s, không đổi timeout/loop/assertion. Bằng chứng AWS phía dưới là lịch sử; chưa deploy.
Lượt Maven đầu thiếu `-Duser.timezone=UTC` bị PostgreSQL từ chối `Asia/Saigon`;
lượt đầy đủ sau dùng đúng UTC như CI đã PASS, không sửa DB/timezone hệ thống.

## Ảnh Event — 02/11/2026

Bổ sung API Cloudinary upload/thay/list/gỡ/sắp xếp ảnh, owner + DRAFT + rowVersion;
public Event thêm images. Chi tiết contract/giới hạn và rollout trong README mục
Ảnh Event. Giữ schema/reference cũ, không thêm metadata/imageType hoặc migrate.
Normal detach không destroy asset cũ. HTTP provider ngoài DB transaction; chỉ
compensate khi chắc chắn attach bị từ chối trước ghi, giữ asset khi COMMIT chưa rõ.
Gateway có route riêng/CORS PATCH/timeout upload; Config YAML riêng Event và
Compose nhận EVENT_IMAGES_* private. Chưa deploy/test Cloudinary thật.
Kiểm cuối02/11/2026: Event142 test, Gateway27 test PASS,0 failure/error/skip;
Spotless/SpotBugs PASS. HTTP multipart POST/PUT/oversize413 qua servlet thật và
CORS PATCH preflight đã kiểm; Cloudinary là fixture. Build image local PASS và
ImageIO PNG/JPEG PASS trong chính JRE Alpine/nonroot/read-only/tmpfs của image.
Lỗi thiếu RestClient.Builder thấy ở snapshot audit trước đó đã được xử lý bằng
bean conditional/prototype, context tests và verify cuối đều qua. Chưa kiểm
truyền bytes xuyên Gateway thực hoặc upload provider thật. Log workstation:
`.superpowers/sdd/event-images-20261102/` (ignored), không phải bằng chứng AWS rollout.

## Bộ deploy đồng bộ Booking/User — 02/11/2026

Bộ deploy và CI/CD của Event đã được hợp nhất vào pipeline chung, xem tài liệu tại [deploy/app/README.md](../../deploy/app/README.md).
Bash đọc snapshot của bộ Python cũ để rollback; giữ `event_owner`, `business.ex` và JWT health.
Test/tunnel/mẫu env/README thao tác giữ local tại `deploy/`, theo convention User; không upload
các file đó trong workflow. Sửa source lần này không tự thay container hoặc cấu hình AWS.

## Điều chỉnh thời điểm phát cấu hình — 31/10/2026

Theo yêu cầu mới: approve chỉ chốt APPROVED, tỷ lệ phí và người/mốc duyệt, không
tạo outbox. Organizer publish mới ghi Finance/Sales/Access cùng transaction chuyển
PUBLISHED. Finance giữ phiên bản phí lúc duyệt; Sales/Access dùng phiên bản công bố.
Publish lặp không sinh thêm message; lỗi bất kỳ append nào rollback toàn bộ.
Không thay worker, binding RabbitMQ, schema, Gateway/Config hoặc service khác.
Các kết quả kiểm30/10 bên dưới là lịch sử trước điều chỉnh này; chưa deploy hoặc
can thiệp các message đã có trên server.
Verification31/10 lúc22:50 Việt Nam: **124 test PASS**,0 failure/error/skip;
build/Spotless PASS, SpotBugs0 bug/0 error. Review độc lập không có finding.
Log máy hiện tại: `.superpowers/sdd/event-publish-config/verify.log` (ignored).

## Trạng thái đối chiếu hiện tại — 30/10/2026

Xem [INTEGRATION-VERIFICATION](INTEGRATION-VERIFICATION.md) cho bằng chứng và
[INTEGRATION-CONTRACTS](INTEGRATION-CONTRACTS.md) cho việc cần Son/Tuan phối hợp.
Các mục kết quả ngày 27–29/10 bên dưới là lịch sử, không phải trạng thái mới nhất.
Gateway đã được rebuild/recreate ngày 30/10. EC2 Transaction hiện chạy thêm Booking
và Config Server; Event vẫn dùng JVM local. Không tự đổi topology của tài liệu.
EC2 Control-Diagnosis timeout từ laptop/Transaction; anh đã xác nhận máy đang tắt.
Không chặn nghiệp vụ Event; kiểm đường observability khi máy được bật lại.
Lượt kiểm mới: Event120 test, Gateway26 test PASS;6 ca AMQP trên RabbitMQ EC2 trong
vhost tạm PASS và đã dọn; public Event qua Gateway200 khi smoke. Không gửi message
vào vhost nghiệp vụ hoặc ghi Event DB trong smoke. Chi tiết/giới hạn ở verification.

## Bổ sung nghiệp vụ bước 5–7

Đã thêm SEAT_MAP STANDING/SEATED, lưu layout/sector/seat và ticket types theo
sector, explicit đổi mode, public view/snapshot đầy đủ. Đã thêm hủy trực tiếp,
yêu cầu hủy, danh sách/lịch sử, duyệt/từ chối và EventCancellation nguyên tử.
Đã có statistics endpoint + HTTP read adapters có kiểm scope/asOf/money và
client-credentials. API/payload: [README](README.md).

**Hợp đồng read model còn PROPOSED**: B13 chưa có DTO financial-summary và
sales-summary đầy đủ, nên [INTEGRATION-CONTRACTS](INTEGRATION-CONTRACTS.md) ghi
chính xác Event mong đợi, không ghi là các owner đã duyệt/triển khai. Default
EVENT_READ_MODEL_ENABLED=false; các luồng cần read này trả503 rõ ràng. Không
gán false/0 khi nguồn lỗi; không sửa code/schema của Booking/Payment/Ticket.

Source Gateway đã thêm route/security Event và test hồi quy; đã rebuild/recreate
container ngày 30/10. Config Server chỉ sửa YAML riêng Event. Không apply DDL,
migration hoặc test ghi dữ liệu/message vào vhost/DB nghiệp vụ dùng chung.

## Nghiệp vụ bước 1–4

Đã triển khai luồng QUANTITY độc lập: cấu hình bản nháp trọn vẹn, quyền/ownership,
rowVersion, submit, trả về nháp, duyệt đóng băng phí, publish idempotent và
list/detail/search công khai. Chi tiết API/payload, snapshot, các biến outbox và
giới hạn tích hợp ở [README](README.md).

Approve chỉ chốt phí; publish ghi FinanceConfiguration, SalesConfiguration và
AccessConfiguration vào outbox cùng transaction thay đổi trạng thái. Relay dùng
`business.ex`, confirm + mandatory return, retry cùng messageId/data và lease
attempt fence; không gọi HTTP hoặc chờ broker trong transaction nghiệp vụ.

Phần bước1–4 tiếp tục được kiểm hồi quy khi mở rộng bước5–7 ở trên. Chưa xác nhận
nghiệp vụ từ consumer thật; Gateway Docker đã áp image mới ngày 30/10. Không
đổi cấu hình chung, schema/quyền/topology EC2 hoặc service nghiệp vụ khác.

## Cấu hình đã áp dụng

Cấu hình runtime hiện nằm trong [application.yaml](src/main/resources/application.yaml).
Đã tích hợp PostgreSQL/schema/role, Hibernate không tạo DDL, RabbitMQ publisher
confirm/return, JWT issuer/JWKS/audience và OAuth client `internal-service`.
POM có OAuth2 client starter để nạp registration `internal` dùng client credentials.
Security chain xác thực Bearer JWT/stateless, tránh default OAuth client chuyển
API sang trang login web. Đã map `realm_access.roles`, áp policy public/ORGANIZER/
ADMIN/INTERNAL_SERVICE và kiểm ownership cục bộ; path khác bị từ chối mặc định.
Đã có outbound read adapter cho bước6–7; mặc định tắt tới khi hợp đồng nguồn được
thống nhất và nguồn triển khai, không đồng nghĩa đã có tích hợp runtime thành công.

Endpoint và secret lấy từ môi trường process Event, không ghi IP/secret vào YAML.
Các override dưới đây cho phép Event dùng hạ tầng EC2 mà không đổi cấu hình chung:

| Biến riêng Event | Khi không khai báo |
|---|---|
| `EVENT_DB_URL`, `EVENT_DB_PASSWORD` | Bắt buộc cấp cho Event |
| `EVENT_DB_USERNAME` | `event_admin` |
| `EVENT_RABBITMQ_HOST` | `RABBITMQ_HOST` |
| `EVENT_RABBITMQ_PORT` | `RABBITMQ_PORT`, mặc định 5672 |
| `EVENT_RABBITMQ_USERNAME` | `RABBITMQ_USERNAME`, mặc định `flash_ticket_app` |
| `EVENT_RABBITMQ_PASSWORD` | `RABBITMQ_PASSWORD` |
| `EVENT_RABBITMQ_VHOST` | `RABBITMQ_VHOST`, mặc định `/` |
| `EVENT_RABBITMQ_BUSINESS_EXCHANGE` | `business.ex` |
| `EVENT_KEYCLOAK_ISSUER_URI` | `KEYCLOAK_ISSUER_URI` |
| `EVENT_KEYCLOAK_JWK_SET_URI` | `KEYCLOAK_JWK_SET_URI` |
| `EVENT_KEYCLOAK_AUDIENCE` | `KEYCLOAK_AUDIENCE`, mặc định `flash-ticket-api` |
| `EVENT_KEYCLOAK_TOKEN_URI` | `KEYCLOAK_TOKEN_URI` |
| `EVENT_INTERNAL_SERVICE_CLIENT_SECRET` | `INTERNAL_SERVICE_CLIENT_SECRET` |

Không khai báo override rỗng. Runtime Stage 1: Event chạy JVM local, PostgreSQL,
RabbitMQ và Keycloak dùng endpoint EC2 đã được người vận hành cung cấp. Redis
chưa thêm vào Event vì chưa có use case sử dụng; không cài lại hạ tầng trên laptop.

## 1. Mở tunnel khi chạy service từ máy local

```bash
ssh -N -o ExitOnForwardFailure=yes -L 127.0.0.1:15432:127.0.0.1:5432 ft119
```

Giữ terminal mở. Nếu tunnel đã chạy thì dùng lại. `ft119` là alias SSH của máy bạn;
thành viên khác cần cấu hình alias riêng tới EC2 Transaction bằng key của mình.
Windows OpenSSH không đọc trực tiếp PPK v2 của PuTTY: dùng PuTTY hoặc bản khóa
OpenSSH với ACL phù hợp. Không sửa/commit khóa gốc.

## 2. YAML kết nối

Block dưới minh họa kết nối theo handoff. Cấu hình thực đã được tích hợp trong YAML
ở trên bằng placeholder và override Event; không chép thêm một block `spring`.
Endpoint EC2 thực tế lấy từ môi trường, không chép IP từ ví dụ vào code.

```yaml
spring:
  datasource:
    url: jdbc:postgresql://127.0.0.1:15432/event_db?currentSchema=event_schema
    username: event_admin
    password: ${EVENT_DB_PASSWORD}
    hikari:
      connection-init-sql: SET ROLE event_owner
      maximum-pool-size: 5
      minimum-idle: 1
  jpa:
    hibernate:
      ddl-auto: none
  rabbitmq:
    host: ${EVENT_RABBITMQ_HOST}
    port: 5672
    username: flash_ticket_app
    password: ${EVENT_RABBITMQ_PASSWORD}
    publisher-confirm-type: correlated
    publisher-returns: true
    template:
      exchange: business.ex
      mandatory: true
  security:
    oauth2:
      resourceserver:
        jwt:
          issuer-uri: ${EVENT_KEYCLOAK_ISSUER_URI}
          jwk-set-uri: ${EVENT_KEYCLOAK_JWK_SET_URI}
          audiences:
            - flash-ticket-api
```

## 3. Điền biến môi trường

Điền trong `.env` gốc, rồi cấu hình IDE/runtime nạp biến vào process Event:

```dotenv
EUREKA_SERVER_URL=http://localhost:8761/eureka/
EVENT_SERVICE_PORT=8086
EVENT_DB_URL=jdbc:postgresql://127.0.0.1:15432/event_db?currentSchema=event_schema
EVENT_DB_USERNAME=event_admin
EVENT_DB_PASSWORD=DIEN_EVENT_DB_ADMIN_PASSWORD_TREN_SERVER
EVENT_RABBITMQ_HOST=DIEN_ENDPOINT_RABBITMQ_TREN_SERVER
EVENT_RABBITMQ_PASSWORD=DIEN_RABBITMQ_PASSWORD_TREN_SERVER
EVENT_KEYCLOAK_ISSUER_URI=DIEN_ISSUER_THUC_TE
EVENT_KEYCLOAK_JWK_SET_URI=DIEN_JWKS_ENDPOINT_THUC_TE
EVENT_KEYCLOAK_TOKEN_URI=DIEN_TOKEN_ENDPOINT_THUC_TE
EVENT_INTERNAL_SERVICE_CLIENT_SECRET=DIEN_SECRET_INTERNAL_SERVICE
```

- Lấy mật khẩu từ quản trị/`.env` server; không commit secret. Spring không tự đọc `.env`.
- Khi bật Eureka, chạy Discovery trước Event. Event dùng YAML trong JAR;
  sửa YAML phải build lại Event. Không cần chạy/restart Config Server cho Event.
- Giữ `SET ROLE event_owner` vì tài khoản là `NOINHERIT`; bảng dùng tiền tố `tbl_`.
- Chạy JVM Event với VM option `-Duser.timezone=UTC`. Đã tái hiện PostgreSQL từ chối
  timezone `Asia/Saigon` do JDBC gửi từ JVM Windows. Hibernate JDBC cũng dùng UTC;
  không đổi timezone Windows hoặc PostgreSQL chung.
- Gateway đang chạy Docker local: Event đăng ký Eureka bằng
  `--eureka.instance.hostname=host.docker.internal --eureka.instance.prefer-ip-address=false`.
- RabbitMQ đã có topology; `dynamic=false`, không tự declare bằng tài khoản app.
  Dùng `business.ex`, chờ confirm và xử lý return khi publish. Không tạo exchange khác.
- JWT đã có `SecurityFilterChain`, mapping `realm_access.roles` và policy từng
  endpoint theo README; health/info chỉ cho INTERNAL_SERVICE.
  Chi tiết OAuth client nội bộ xem link Keycloak bên dưới.

## 4. Công cụ chạy local trên máy developer hiện tại

Đã chuẩn bị các file `.env.event-service.*` ở root, được Git ignore cùng `.env`.
Đây là công cụ riêng workstation, không được mang theo khi clone repo. Thành viên
khác tự cấp endpoint/credential theo bảng trên hoặc dùng cấu hình IDE tương đương.

```powershell
# Chạy tại root repo; Discovery cần hoạt động nếu đăng ký Eureka được bật.
.\.env.event-service.runtime.ps1 -Smoke

# Chạy Event và tunnel; giữ PowerShell mở, Ctrl+C để dừng hai process này.
.\.env.event-service.runtime.ps1

# Chỉ mở tunnel để debug Event từ IDE.
.\.env.event-service.runtime.ps1 -TunnelOnly

# Nạp .env vào chính PowerShell hiện tại; IDE đã mở không tự nhận env mới.
. .\.env.event-service.runtime.ps1 -EnvironmentOnly
```

Script dùng JDK 21, Python và SSH đã cài; chuyển PPK v2/RSA không mã hóa vào file
tạm có ACL riêng rồi xóa khi kết thúc. Giữ host-key verification, không sửa key gốc.
Không dừng/restart Gateway, Discovery, Config Server hoặc process có sẵn.
Endpoint/credential/key path nằm trong biến `EVENT_*` ngoài Git; có bản sao `.env`
trước cấu hình trong thư mục `.env.event-service.backups` với ACL riêng.

Smoke dùng JDBC transaction read-only/ROLLBACK; kiểm health DB/Rabbit bằng JWT thật,
đăng ký Eureka và đọc public Event list. Khi smoke, outbox bị tắt để không phát
backlog dùng chung. Không chạy initializer/migration, ghi fixture hoặc publish
message. Path bị deny trả 403 với machine JWT hợp lệ, thiếu/sai JWT trả 401.
Đây là kiểm kết nối runtime, không thay test nghiệp vụ hoặc tích hợp consumer.
Log và kết quả smoke nằm trong `.env.event-service.runtime`, ngoài Git.

## 5. Kết quả kiểm ngày 27/10/2026 — chuẩn bị trước coding nghiệp vụ

- JDK 21: `mvnw.cmd -B -ntp spotless:apply verify` PASS; 4 test, không failure/error/
  skip; Spotless PASS; SpotBugs 0 bug/0 error. Mockito agent được truyền vào JVM
  trong lượt kiểm Windows, không bỏ test hoặc quality gate.
- Ba test hồi quy đã tái hiện HTTP 302 sang `/login` trước khi thêm security chain,
  rồi PASS sau khi bật Bearer JWT/stateless cùng outbound OAuth client.
- Config Server Docker local trả đúng YAML Event mới qua bind mount; giữ nguyên
  container Gateway/Discovery/Config Server và cấu hình các service khác.
- JDBC thật qua SSH tunnel: đăng nhập `event_admin`, SET ROLE `event_owner`, schema
  `event_schema`, 12 bảng; đọc trong transaction read-only rồi ROLLBACK.
- Event khởi động cổng 8086; health PostgreSQL và RabbitMQ UP với credential ứng
  dụng. Keycloak cấp machine token thành công; JWT thật qua xác thực, request thiếu
  hoặc sai JWT trả 401. JWT hợp lệ tới route chưa triển khai trả 404, không phải
  bằng chứng API nghiệp vụ hoạt động hay policy role/ownership đã hoàn tất.
- Eureka ghi Event UP với hostname `host.docker.internal`; từ container Gateway
  gọi health Event nhận 401, xác nhận đường mạng tới Event mà không mở health public.
- Script smoke dừng Event/tunnel do chính nó tạo và xóa khóa OpenSSH tạm. Không sửa
  cấu hình EC2, DDL, quyền database hoặc broker topology; không ghi dữ liệu nghiệp vụ.
- Chưa kiểm publish/consume, confirm/return thực tế, outbound business call,
  validation âm issuer/audience, Redis, tích hợp các service khác hoặc nghiệm thu B16.
- Build còn cảnh báo cache Maven `jspecify:1.0.1` có POM rỗng; gate vẫn PASS. Không
  sửa cache chung hoặc đổi dependency version ngoài phạm vi Event để che cảnh báo.

## 6. Kết quả kiểm nghiệp vụ bước 1–4 — 27/10/2026

- Lượt verify cuối hoàn tất 04:00:21 giờ Việt Nam: **55 test PASS**, 0 failure/
  error/skip; build, Spotless PASS, SpotBugs 0 bug/0 error. Có real PostgreSQL
  16.15 cô lập với canonical SQL/prefix; assert current_user=event_owner và
  current_schema=event_schema, JDBC đăng nhập event_admin NOINHERIT.
- Kiểm qua HTTP toàn luồng tạo/sửa/submit/approve/publish và publish lặp, role/
  ownership/version, ẩn bản nháp, strict JSON/UUID/correlation; rollback giữa lúc
  sửa children hoặc ghi outbox; concurrent save/submit/publish; outbox lease fence.
- JWT ký RSA thật với JWKS cô lập: đúng issuer/audience/role vào health; sai issuer,
  audience, hết hạn và malformed role bị từ chối. Publisher mock ACK/NACK/return/
  timeout/mất kết nối; chưa gửi business message hoặc kiểm consumer ở broker EC2.
- Rà độc lập phát hiện guard mode đứng trước owner/status có thể làm lộ event ẩn;
  đã tái hiện test RED, sửa thứ tự guard, test GREEN và reviewer xác nhận tĩnh.
  Test HTTP cũng tái hiện UUID rút gọn lọt default-editor fallback của Spring;
  đã dùng strict UUID binder và kiểm lại cả path/query/body.
- Runtime 03:59 giờ Việt Nam với hạ tầng EC2 hiện hữu: DB/Rabbit UP, role/schema
  đúng, Keycloak machine JWT hợp lệ, Eureka UP, Docker Gateway reach health 401;
  public Event list 200, hiện có 0 event công khai. Thiếu/sai JWT 401; machine JWT
  vào path bị deny trả 403. Outbox bị tắt suốt smoke; không ghi fixture/DDL/message.
- Script đã dừng process Event/tunnel do nó tạo và xóa key tạm; giữ nguyên ba
  container Gateway/Discovery/Config local. Cấu hình được sửa trong repo chỉ là
  YAML riêng Event; không sửa cấu hình chung hoặc code service khác.
- Log kiểm module: `.superpowers/sdd/event-quantity/verify.log` (ignored ở root).
  Kết quả runtime sanitized: `.env.event-service.runtime/smoke-result.json`
  (ignored). Các file này thuộc workstation, không có trong clone mới.
- Còn giới hạn: route mới chưa qua Gateway; chưa end-to-end với Booking/Ticket/
  Payment, broker confirm/return thực tế và chưa nghiệm thu B16. Không commit/push.

Nguồn: [init/quyền DB](../../deploy/postgres/00-business-databases.sh),
[topology RabbitMQ](../../deploy/rabbitmq/definitions.json),
[Keycloak/Spring](../../deploy/keycloak/postman/INTERNAL-SERVICE-HANDOFF.md),
[Spring Boot properties](https://docs.spring.io/spring-boot/appendix/application-properties/index.html).

## 7. Kiểm chứng bước 5–7 — 29/10/2026

- Event:111 test PASS, 0 failure/error/skip; build/Spotless PASS; SpotBugs0 bug/0 error. Verify cuối2026-10-29T15:30:20Z (22:30:20 Việt Nam). Có hồi quy QUANTITY và test SEATED/STANDING, geometry/SVG, mode reset, hidden/type references, snapshot/public/admin detail, transaction rollback, concurrent cancel/review, HTTP summary invalid/missing/timeout/scope/asOf.
- Gateway:22 test PASS,0 failure/error/skip; build/Spotless/SpotBugs PASS. Route và security Event được test, đường Booking/Ticket cũ không bị chuyển nhầm. Docker Gateway hiện chưa được recreate bằng image mới.
- Review độc lập tìm2 vấn đề: capacity SEATED và financial summary hết hạn trong lúc chờ parent lock rollback. Đã có test RED tái hiện và GREEN sau sửa; vẫn giữ HTTP ngoài transaction. Admin preview thiếu sơ đồ cũng có test RED→GREEN.
- Runtime smoke2026-10-29T15:31:33Z: Event JAR mới khởi động với Config Server Docker, PostgreSQL EC2 đúng event_admin→event_owner/event_schema,12 bảng, DB/Rabbit UP, Keycloak machine JWT hợp lệ, Eureka UP, public list200. Outbox và read adapters tắt; không test ghi fixture/DDL/business message lên EC2.
- Chưa kiểm runtime financial-summary/sales-summary hoặc consumer cancellation/sales/access/finance ở service khác. Contract read response/path sales-summary là đề xuất Event-side, không bằng chứng các owner đã duyệt. Không bật flag đó chỉ để có happy path khi nguồn chưa có.
- Không sửa schema/quyền/topology/server, không commit/push. Giữ thay đổi có sẵn của anh. Source thay đổi: Event, YAML riêng Event, route/security/test/docs Event trong Gateway; không sửa code business service khác.
- Log máy hiện tại (ignored): .superpowers/sdd/event-extended/verify.log, gateway-verify.log, review-red.log và .env.event-service.runtime/smoke-result.json.
