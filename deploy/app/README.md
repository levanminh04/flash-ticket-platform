# Deploy 9 ứng dụng lên máy 84

Push lên `main` → `cd.yml` chỉ verify, build và deploy lại service có file đổi. Image luôn được deploy bằng digest.

| Service | Thư mục | Port | Health check sau deploy |
|---|---|---|---|
| discovery-server | `infrastructure/discovery-server` | 8761 | `/actuator/health` = UP |
| api-gateway | `infrastructure/api-gateway` | 8081 | instance UP trong Eureka |
| user-service | `services/user-service` | 8085 | như trên |
| event-service | `services/event-service` | 8086 | như trên |
| ticket-service | `services/ticket-service` | 8084 | như trên |
| booking-service | `services/booking-service` | 8082 | như trên |
| payment-service (+ ngrok) | `services/payment-service` | 8083 | như trên |
| keycloak | `services/keycloak-spi` | 8080 | `/realms/flash-ticket/.well-known/openid-configuration` = 200 |
| frontend | `frontend` | 5173 | `GET /` trả HTML có `id="root"` |

"Như trên" nghĩa là instance của service trong Eureka (`/eureka/apps/<APP>`) có `status=UP`, đúng port và đăng ký **sau** khi container mới start.

## Việc hằng ngày

```bash
gh workflow run cd.yml -f action=deploy   -f service=user-service      # build commit hiện tại của main và deploy
gh workflow run cd.yml -f action=deploy   -f service=all               # cả 9 ứng dụng, tuần tự
gh workflow run cd.yml -f action=deploy   -f service=frontend              # frontend: build lại image từ commit main
gh workflow run cd.yml -f action=restart  -f service=payment-service   # sau khi cập nhật secret ENV_PAYMENT_SERVICE
gh workflow run cd.yml -f action=rollback -f service=ticket-service -f image_digest=sha256:<64 hex>
```
Digest cũ nằm trên server: `/opt/flash-ticket-app/state/<svc>.previous`.

```bash
ssh ft84 'cat /opt/flash-ticket-app/state/user-service.current'      # digest đang chạy
ssh ft84 'docker logs --tail 100 flash-ticket-user-service-user-service-1'
ssh ft84 'curl -s -H "Accept: application/json" http://127.0.0.1:8761/eureka/apps | jq -r ".applications.application[] | .name + \" \" + .instance[0].status"'
```

## Cấu trúc trên server

```
/opt/flash-ticket-app/
├── compose/             # CI ghi đè mỗi lần deploy (từ deploy/app/)
├── env/<svc>.env        # CD ghi tự động từ secret ENV_<SVC>, mode 600
├── secrets/ticket-qr/   # key-ring.json của ticket, mount read-only
├── state/<svc>.current, <svc>.previous
└── deploy-service.sh
```

## Quản lý cấu hình Env qua GitHub Secrets

Mỗi service có một GitHub Secret chứa toàn bộ nội dung file env, đặt ở **cấp repo** (không phải Environment `production`). Job deploy (`environment: production`) vẫn đọc được secret cấp repo; GitHub tự tạo Environment ở lần chạy đầu. Khi CD chạy action `deploy` hoặc `restart`, pipeline sẽ tự động ghi nội dung secret xuống `/opt/flash-ticket-app/env/<svc>.env` (mode 600, ghi atomic) trước khi chạy `deploy-service.sh`.

### Bảng tên Secret tương ứng các service

| Service | Tên GitHub Secret (cấp repo) |
|---|---|
| `discovery-server` | `ENV_DISCOVERY_SERVER` |
| `api-gateway` | `ENV_API_GATEWAY` |
| `user-service` | `ENV_USER_SERVICE` |
| `event-service` | `ENV_EVENT_SERVICE` |
| `ticket-service` | `ENV_TICKET_SERVICE` |
| `booking-service` | `ENV_BOOKING_SERVICE` |
| `payment-service` | `ENV_PAYMENT_SERVICE` |
| `keycloak` | `ENV_KEYCLOAK` |

### Quy trình tạo hoặc cập nhật cấu hình env

1. Tạo hoặc sửa secret cấp repo:
   ```bash
   gh secret set ENV_BOOKING_SERVICE -R levanminh04/flash-ticket-platform < /path/to/booking.env
   ```
   Ticket-service cần thêm file `/opt/flash-ticket-app/secrets/ticket-qr/key-ring.json` (owner 10001, mode 0440).
2. Kích hoạt cập nhật trên máy 84 và khởi động lại container:
   ```bash
   gh workflow run cd.yml -f action=restart -f service=booking-service
   ```

> [!WARNING]
> **Tuyệt đối không commit file `.env` vào repository!** Tất cả cấu hình bí mật và môi trường runtime chỉ được lưu trong GitHub Secrets hoặc máy local của người vận hành ngoài git tracking.

### Các giá trị biến trên máy 84 khác bản local

| Biến | Giá trị trên 84 |
|---|---|
| `EUREKA_CLIENT_ENABLED` | `true` (mọi service Spring trừ discovery) |
| `EUREKA_SERVER_URL` | `http://127.0.0.1:8761/eureka/` |
| `SERVER_ADDRESS` | `127.0.0.1`; riêng api-gateway `0.0.0.0` |
| Host Postgres/RabbitMQ/Redis/Mongo/Keycloak | `127.0.0.1` |
| payment: `NGROK_DOMAIN`, `NGROK_AUTHTOKEN` | domain và token ngrok |
| payment: `VNPAY_RETURN_URL`, `VNPAY_IPN_URL` | `https://<NGROK_DOMAIN>/api/v1/payments/vnpay/return` và `/ipn` |
| ticket: `TICKET_QR_KEY_RING_LOCATION` | `file:/run/ticket-qr/key-ring.json` |
| keycloak | `KC_DB`, `KC_DB_URL`, `KC_DB_USERNAME`, `KC_DB_PASSWORD`, `KC_HOSTNAME`, `KC_HTTP_ENABLED`, `KC_BOOTSTRAP_ADMIN_USERNAME`, `KC_BOOTSTRAP_ADMIN_PASSWORD`, `KC_DB_POOL_MIN_SIZE`, `KC_DB_POOL_INITIAL_SIZE`, `KC_DB_POOL_MAX_SIZE`, `KC_HTTP_MAX_QUEUED_REQUESTS`, `FT_IDENTITY_OUTBOX_ENABLED`, `FT_IDENTITY_OUTBOX_INTERVAL_MS`, `FT_IDENTITY_RABBITMQ_HOST`, `FT_IDENTITY_RABBITMQ_PORT`, `FT_IDENTITY_RABBITMQ_VHOST`, `FT_IDENTITY_RABBITMQ_USERNAME`, `FT_IDENTITY_RABBITMQ_PASSWORD` (host DB/broker là `postgres`/`rabbitmq` vì Keycloak chạy trong network `flash-ticket-infra`) |

## Lưu ý về Ngrok (payment-service)

Ngrok local và ngrok CD trên FT84 dùng **chung một `NGROK_DOMAIN`** và 1 URL IPN VNPay. Do đó, **trước khi CD deploy `payment-service`/`ngrok` lên máy 84, phải tắt ngrok/payment local** (`docker compose --env-file .env -f deploy/docker-compose.payment.yml down`) để tránh xung đột domain và tranh chấp webhook IPN.

## Cutover Keycloak thủ công trên máy 84

Trước lần deploy CD đầu tiên cho Keycloak:
1. Dừng và xóa container Keycloak hạ tầng cũ trên máy 84:
   ```bash
   ssh ft84 'cd /opt/flash-ticket-infra && docker compose down keycloak' # KHÔNG dùng -v
   ```
2. Dữ liệu Keycloak nằm trong PostgreSQL (`keycloak_db`) nên hoàn toàn được bảo toàn.
3. CD pipeline (`deploy/app/keycloak.yml`) sẽ quản lý Keycloak với port 8080, gắn vào network `flash-ticket-infra` và mount các volume/theme/realm import từ `/opt/flash-ticket-infra/keycloak/keycloak/`.
*(Lưu ý: W2 không trực tiếp SSH/thao tác trên máy 84; bước này do người vận hành/Sơn thực hiện).*

## Ý nghĩa kết quả của script

| Dòng in ra | Ý nghĩa |
|---|---|
| `DEPLOY_SUCCESS` | Container mới đúng image, UP, không restart |
| `ROLLED_BACK <lý do>` | Bản mới lỗi, đã chạy lại digest trước đó (job vẫn đỏ) |
| `DEPLOY_FAILED <lý do>` | Lần deploy đầu lỗi (không có gì để rollback) hoặc rollback cũng lỗi: xử lý tay |
| `DEPLOY_REJECTED INSUFFICIENT_MEMORY` | Máy không đủ RAM trống; container cũ vẫn chạy nguyên |
| `DEPLOY_REJECTED …` khác | Sai tên, image, env file hoặc token; chưa đụng container |
| lý do `OOM_KILLED` | Container bị kernel giết vì hết bộ nhớ: tăng `mem_limit` hoặc RAM, không phải lỗi code |
| lý do `CRASH_LOOP` / `HEALTH_TIMEOUT` | Container restart liên tục / không UP trong 180 s: xem log |

## Thêm service mới

Services có trường `kind`: `maven` cho service Spring/Keycloak, `node` cho frontend. Mục mới phải khai báo `kind`.

1. Thêm một mục vào `.github/services.json` (đúng thứ tự deploy).
2. Thêm `deploy/app/<svc>.yml` (sao chép `user-service.yml`, đổi tên và `mem_limit`).
3. Thêm `[<svc>]=<port>` vào bảng `PORT` của `deploy/scripts/deploy-service.sh`.
4. Dùng Dockerfile mẫu (sao chép từ discovery-server).
5. Chạy ShellCheck + actionlint phải xanh.

## Lên 2 máy / Kubernetes

- 2 máy: thêm trường `host` cho từng mục trong `services.json` và secret SSH thứ hai; `deploy-service.sh` không đổi.
- Kubernetes: giữ job build + GHCR; thay bước SSH bằng bước ghi digest vào repo manifest cho Argo CD; `env/<svc>.env` thành Secret/ConfigMap; Eureka có thể thay bằng Service DNS.

## Frontend

- Image `frontend` là nginx (`nginxinc/nginx-unprivileged`) phục vụ `dist/` đã build, cổng 5173, không có env runtime (không `env/frontend.env`, không secret `ENV_FRONTEND`).
- `VITE_*` được build cứng vào bundle. Lấy từ **repo Variables** (`vars.VITE_API_GATEWAY_URL`, `VITE_KEYCLOAK_URL`, `VITE_KEYCLOAK_REALM`, `VITE_KEYCLOAK_CLIENT_ID`), không phải secret. Đổi giá trị thì phải chạy `action=deploy` (không phải `restart`) để build lại.
- Workflow và script bỏ qua bước ghi/kiểm tra env khi service là frontend (trường `kind: node` trong `.github/services.json`).


## Gói a–i: đầu vào vận hành sau khi duyệt riêng

Bản sửa local không cập nhật Secrets/Variables, tài khoản hay server. Các lệnh dưới
đây mô tả bước người vận hành hoặc agent được ủy quyền sẽ làm sau khi Minh duyệt
riêng đúng thao tác. Chi tiết môi trường thử ở `../rca-test/README.md`; bản đồ file
và bằng chứng kiểm ở `../observability/README.md`.

1. **GitHub:** tài khoản có quyền ghi Actions Secrets/Variables của repo, chạy Actions
   và xuất package GHCR; Environment `production` cần reviewer nếu cấu hình yêu cầu.
   Secret SSH là `FT84_SSH_PRIVATE_KEY`; Variables là `FT84_SSH_HOST`,
   `FT84_SSH_USER`, `FT84_SSH_PORT`, `FT84_SSH_KNOWN_HOSTS`. Known-host fingerprint
   phải nhận qua kênh tin cậy; không tắt StrictHostKeyChecking.
2. **Env từng ứng dụng:** cập nhật đúng 8 secret cấp repo trong bảng phía trên:
   `ENV_DISCOVERY_SERVER`, `ENV_API_GATEWAY`, `ENV_USER_SERVICE`, `ENV_EVENT_SERVICE`,
   `ENV_TICKET_SERVICE`, `ENV_BOOKING_SERVICE`, `ENV_PAYMENT_SERVICE`, `ENV_KEYCLOAK`.
   Dùng `.env.example` tại từng ứng dụng làm tên đầu vào; thay bằng thông tin thật
   ở nơi riêng. Không đưa secret value vào diff hay log hỗ trợ.
3. **Java agent:** đặt `OTEL_AGENT_ENABLED=true`, `ENVIRONMENT`, `HOST_ID`,
   `OTEL_EXPORTER_OTLP_ENDPOINT`, `OTEL_EXPORTER_OTLP_PROTOCOL=http/protobuf`,
   `OTEL_TRACES_SAMPLER`, `OTEL_METRICS_EXPORTER=otlp`, `OTEL_LOGS_EXPORTER=none`.
   `SERVICE_NAME` do image cung cấp, `SERVICE_VERSION` do CI build bằng github.sha,
   `INSTANCE_ID` sinh lại mỗi lần container chạy; bỏ override version cũ trong env
   để không làm sai identity. Chuyển identity từ các biến EVENT_*/USER_*/TICKET_*
   cũ sang bộ tên chung. Không khai lặp identity trong `OTEL_RESOURCE_ATTRIBUTES`.
   Bỏ `-javaagent:` cũ khỏi `JAVA_TOOL_OPTIONS` để tránh agent kép; JVM options khác giữ.
   Booking/Gateway giữ `SPRING_AUTOCONFIGURE_EXCLUDE` cho LettuceObservationAutoConfiguration;
   Event/Booking/Payment/Ticket đặt
   `OTEL_INSTRUMENTATION_RABBITMQ_EXPERIMENTAL_SPAN_ATTRIBUTES=true`.
4. **Keycloak:** `KC_LOG_CONSOLE_OUTPUT=json`, `KC_LOG_CONSOLE_INCLUDE_TRACE=true`,
   `KC_TRACING_ENABLED=true`, `KC_TRACING_SERVICE_NAME=keycloak`,
   `KC_TRACING_ENDPOINT` (OTLP gRPC collector), `KC_TRACING_SAMPLER_TYPE=parentbased_traceidratio`,
   `KC_TRACING_SAMPLER_RATIO` theo tỷ lệ thu đã duyệt. Image build bật native tracing;
   không gắn Java agent Spring vào Keycloak. Các biến DB/hostname/broker ở bảng trên
   vẫn phải có. Trace ID chỉ có trên dòng thuộc request có span.
5. **Server:** SSH key được cấp cho đúng user, đường dẫn `/opt/flash-ticket-app`
   có compose/env/state và script như quy trình hiện có; Docker/Compose, quyền kéo
   package GHCR, endpoint OTLP và mạng tới DB/Redis/RabbitMQ/Keycloak phải hoạt động.
   Cấp file QR key-ring đúng owner 10001/mode 0440. Chưa biết credentials/endpoint
   thật thì giữ OPEN; không suy từ file mẫu hay tự cấp thêm quyền tài khoản.
6. **PostgreSQL:** trên host hạ tầng quản lý file env có
   `PG_LOG_MIN_DURATION_STATEMENT=500`, `PG_LOG_LOCK_WAITS=on`,
   `PG_DEADLOCK_TIMEOUT=1s`; compose tắt giá trị bind parameter trong diagnostic log.
   Chỉ áp bằng lượt recreate đã duyệt. Init SQL mới chỉ tự chạy khi PGDATA rỗng;
   server có dữ liệu cần kiểm grants và áp hai SQL reader qua kế hoạch riêng,
   không xóa volume để ép init lại.
7. **Nghiệm thu sau deploy:** lưu digest đa kiến trúc, chạy đúng digest trên x86 và
   ARM; kiểm identity/log JSON trong Loki, span Redis và queue RabbitMQ, slow/lock
   PostgreSQL; tải một giờ kiểm OOM/restart/RSS. Có config/build local không tự đạt
   các điều kiện nghiệm thu runtime này.

Agent có thể thực hiện các bước GitHub/SSH khi có công cụ đăng nhập, quyền phù hợp,
kết nối mạng và phê duyệt riêng. Phiên local hiện có SSH nhưng chưa có `gh`;
có thể chuẩn bị CLI/API sau khi được duyệt. MFA, cấp quyền tổ chức hoặc cung cấp
khóa/chứng thực tin cậy vẫn có thể cần chủ tài khoản tương tác. Cấp quyền không
thay cho phê duyệt commit/push, cập nhật GitHub hay deploy.
