# CI/CD cho FlashTicket — thiết kế

- Ngày: 2026-11-07 (cập nhật lần 3)
- Trạng thái: **DRAFT — chờ người dùng duyệt**
- Người yêu cầu: Sơn (`dev/son`)
- Phạm vi: CI cho PR và CD tự động 8 ứng dụng (7 Spring + Keycloak) lên máy 84 qua GitHub Actions

## 1. Ý định

Hiện mỗi thành viên tự deploy từ nhánh riêng (`dev/son`, `dev/tuyen`) bằng 4 workflow và 4 script
copy-paste, nhắm 2 máy khác nhau, thiếu gateway/discovery/payment. Mục tiêu:

> Push code lên `main` → chỉ service bị sửa được verify, build image, deploy lại đúng container
> của nó trên máy 84. Chuẩn như dự án doanh nghiệp vận hành, nhưng dễ triển khai và dễ đọc; sau
> này lên 2 máy hoặc Kubernetes không phải làm lại phần build.

## 2. Mục tiêu / Không mục tiêu / Phản mục tiêu

**Mục tiêu**

1. Thay đổi trong thư mục một service → chỉ service đó được verify, build và recreate.
2. Image bất biến: build một lần, deploy bằng digest `@sha256:...`, tra lại commit được.
3. Health check sau deploy; fail thì tự rollback về digest trước đó.
4. Một cách làm chung cho cả 7 service: một reusable workflow, một script deploy.
5. Mỗi service một file env riêng (repo: `<service>/.env.example`; server: `env/<svc>.env`).
6. Deploy lại / rollback / restart một service bằng tay qua `workflow_dispatch`.
7. PR vào `main` chỉ verify service bị đổi, có check tổng `CI / Required`.

**Không mục tiêu**

- Deploy tự động hạ tầng (Postgres, Mongo, RabbitMQ, Redis, Keycloak) và stack quan sát.
- Deploy frontend.
- Zero-downtime / nhiều replica / HA. Recreate một container gián đoạn vài chục giây.
- CI ghi hoặc quản lý secret trên server.
- Rollback schema/dữ liệu.
- Máy 119: **bỏ hẳn**, không deploy, không dọn (người dùng xác nhận 2026-11-07).

**Phản mục tiêu** (có chạy được vẫn là thất bại)

- Một push làm restart service không liên quan.
- Server build từ source, hoặc chạy image không truy được về commit.
- Hai lần deploy chồng nhau làm hỏng container đang chạy.
- Thêm service mới phải copy cả workflow lẫn script.
- Secret xuất hiện trong log Actions, artifact hoặc Git.
- Rollback tự động "cứu" một bản tốt chỉ vì máy hết RAM (OOM bị đọc nhầm là bản lỗi) mà không báo rõ.

## 3. Ràng buộc và hiện trạng (kiểm 2026-11-07)

| Mục | Hiện trạng |
|---|---|
| Máy đích | 84 (`ip-172-31-41-236`, Ubuntu 24.04, kernel 7.0, x86_64, Docker 29.1.3, Compose 2.40.3, đĩa trống 7,8 GB). **RAM 1,9 GiB** — người dùng sẽ nâng lên ~8 GiB |
| Hạ tầng trên 84 | `/opt/flash-ticket-infra`: Postgres (`127.0.0.1:5432`, đủ `event_db`, `booking_db`, `payment_db`, `ticket_db`, `keycloak_db`), RabbitMQ (`:5672`, ~30 queue, user `flash_ticket_app`, `flash_ticket_identity`), Redis (`:6379`). MongoDB đã chạy lại bằng `mongo:7.0` trên volume mới `flash-ticket-mongo7-data` (2026-11-07, chưa pin digest, dữ liệu 8.0 cũ bỏ theo ý Sơn; volume cũ còn nguyên) |
| Keycloak | đang chạy từ `/opt/flash-ticket-infra/keycloak` (project `keycloak`, build ngay trên server, 512m) — sẽ chuyển sang CD quản lý |
| Ứng dụng cũ trên 84 | `flash-ticket-event-event-service-1`, `flash-ticket-user-user-service-1` đã dừng (Exited); thư mục `/home/ubuntu/flash-ticket-{event,user,ticket}`; QR key ring của ticket ở `/home/ubuntu/flash-ticket-ticket/runtime/qr` |
| Port (không đổi) | gateway 8081, booking 8082, payment 8083, ticket 8084, user 8085, event 8086, discovery 8761 |
| Repo | Monorepo, mỗi ứng dụng một project Maven độc lập có `mvnw`, không module chung |
| Config | **Không service nào dùng Config Server** (đã bỏ `spring.config.import` và `spring-cloud-starter-config`). Mỗi service tự giữ `application.yaml` |
| Env | Mỗi ứng dụng đã có `.env.example` cạnh source (`infrastructure/{api-gateway,discovery-server}`, `services/<svc>`); root `.env` chỉ còn hạ tầng + `VITE_*` |
| Java | api-gateway, discovery, event, user, payment = 21; **booking, ticket = 23** (pom + Dockerfile). Đã thử compile main+test của cả hai với `--release 21`: đạt |
| Eureka | Gateway route bằng `lb://<service>`; compose của booking/payment đang `EUREKA_CLIENT_ENABLED=false` |
| Nhánh | `main` là nhánh deploy duy nhất. `main` hiện chỉ có bộ khung; toàn bộ code nằm ở `dev/integration` (49 file còn chưa commit) |
| Registry | GHCR `ghcr.io/levanminh04/flash-ticket-platform/<svc>`, dùng `GITHUB_TOKEN` |
| Schema | Không có Flyway/Liquibase; image cũ khi rollback phải còn tương thích schema hiện tại |

## 4. Các cách đã cân nhắc

| Cách | Mô tả | Đánh đổi | Kết luận |
|---|---|---|---|
| **A. GitHub-hosted runner + SSH + Compose** | Runner build/push image, SSH vào 84 chạy script deploy dùng chung | Đơn giản, đã có tiền lệ trong repo, dễ debug; không zero-downtime | **Được chọn** |
| B. Self-hosted runner trên 84 | Job deploy chạy trên máy đích, không cần SSH vào | Runner ăn RAM của máy chạy service, thêm agent phải bảo trì | Loại |
| C. k3s + Argo CD ngay | GitOps đầy đủ | Quá nặng cho 1 máy chạy cả hạ tầng, phải viết lại toàn bộ deploy | Loại (để giai đoạn sau) |

Lựa chọn con trong A: **một `cd.yml` + matrix** thay vì 8 workflow riêng có `paths:`, để xếp được thứ tự
(discovery trước) khi một push đụng nhiều service và map được "sửa compose của X thì chỉ deploy X".

## 5. Thiết kế

### 5.1 Luồng

```
PR vào main
  └─ ci.yml: detect-changes → _maven-verify.yml (matrix service bị đổi) → CodeQL, Dependency Review → "CI / Required"

push main  (hoặc workflow_dispatch: service | all, action = deploy | restart | rollback, image_digest?)
  └─ cd.yml
       detect-changes ──► danh sách service + cờ "chỉ recreate, không build"
       build (matrix)  ──► _maven-verify.yml → docker build (copy JAR) → push GHCR: tag sha-<commit>, lấy digest
       deploy          ──► discovery trước, sau đó các service còn lại lần lượt (max-parallel: 1)
                           _deploy-service.yml (Environment "production")
                             scp deploy/app/* + deploy/scripts/deploy-service.sh → /opt/flash-ticket-app
                             ssh: deploy-service.sh <svc> <image@sha256> <commit>
```

- `concurrency` theo từng service, `cancel-in-progress: false`.
- Deploy tuần tự trên 84 (max-parallel 1) để không dồn RAM khi nhiều JVM khởi động cùng lúc.

### 5.2 Map thay đổi → hành động

| File thay đổi | Hành động |
|---|---|
| `<service path>/**` (trừ `**/*.md`, `**/.env.example`) | verify + build + deploy service đó |
| `deploy/app/<svc>.yml` | recreate service đó với digest đang chạy (không build) |
| `deploy/app/_common.yml`, `deploy/scripts/deploy-service.sh` | recreate cả 7 service với digest đang chạy, tuần tự |
| `.github/**` | chỉ chạy CI, không deploy |
| `*.md`, `.env.example` | bỏ qua |

Path của từng service nằm trong `.github/services.json` + `.github/scripts/detect-changes.sh`.
Thêm service mới = 1 mục ở đây + 1 file compose.

| Service | Path | Image GHCR | Port |
|---|---|---|---|
| discovery-server | `infrastructure/discovery-server/**` | `.../discovery-server` | 8761 |
| api-gateway | `infrastructure/api-gateway/**` | `.../api-gateway` | 8081 |
| user-service | `services/user-service/**` | `.../user-service` | 8085 |
| event-service | `services/event-service/**` | `.../event-service` | 8086 |
| ticket-service | `services/ticket-service/**` | `.../ticket-service` | 8084 |
| booking-service | `services/booking-service/**` | `.../booking-service` | 8082 |
| payment-service | `services/payment-service/**` | `.../payment-service` | 8083 |
| keycloak | `services/keycloak-spi/**` | `.../keycloak` | 8080 |

### 5.3 Cấu trúc file

**`.github/`** (5 workflow + 1 services.json + 2 scripts)

| File | Trạng thái | Vai trò |
|---|---|---|
| `services.json` | mới | định nghĩa 8 service, path, port, context, Dockerfile |
| `scripts/detect-changes.sh` | mới | phát hiện service thay đổi từ git diff |
| `scripts/plan-deploy.sh` | mới | tính danh sách service cần deploy/recreate theo thứ tự |
| `workflows/ci.yml` | sửa | PR vào `main`: chỉ verify service bị đổi; giữ `CI / Required`; bỏ config-server |
| `workflows/codeql.yml` | sửa | bỏ config-server khỏi danh sách compile |
| `workflows/cd.yml` | mới | push `main` / dispatch |
| `workflows/_maven-verify.yml` | mới | reusable: `mvnw -B -ntp verify` Java 21, kèm Postgres/RabbitMQ tạm khi service cần (ticket, booking như workflow cũ) |
| `workflows/_deploy-service.yml` | mới | reusable: build image, push, SSH deploy |
| `workflows/deploy-{booking,event,ticket,user}.yml` | **xóa** | |

**`deploy/`**

| File | Vai trò |
|---|---|
| `app/_common.yml` | khung chung qua `extends`: host network, user non-root, `read_only` + tmpfs `/tmp`, `cap_drop: ALL`, `no-new-privileges`, `restart: unless-stopped`, log json-file 10m×3 |
| `app/<svc>.yml` ×8 | `image: ${IMAGE}`, `env_file: ../env/<svc>.env`, `mem_limit`, phần riêng (ticket: mount read-only QR key ring vào `/run/ticket-qr`; payment: thêm service `ngrok`; keycloak: không `extends _common.yml`, dùng network bridge `flash-ticket-infra`, mount theme/realm từ `/opt/flash-ticket-infra/keycloak/keycloak`, mem_limit 768m) |
| `scripts/deploy-service.sh` | script deploy duy nhất |
| **Xóa** | `docker-compose.{api-gateway,discovery-server,config-server,booking,event,ticket,user,payment,payment.ft119}.yml`, `deploy-{booking,event,ticket,user}.sh`, `README.payment.md` phần 119, `Dockerfile.integration-java21` nếu không còn dùng |
| **Sửa** | `docker-compose.integration.local.yml`: bỏ config-server, bỏ `CONFIG_SERVER_URL` |
| **Xóa module** | `infrastructure/config-server/` |

**Ngân sách bộ nhớ khởi điểm** (chỉnh sau khi đo `docker stats`; tổng ~5,2 GiB, cần máy ~8 GiB):

| Service | mem_limit | Service | mem_limit |
|---|---|---|---|
| api-gateway | 768m | ticket | 768m |
| discovery | 384m | booking | 512m |
| user | 768m | payment | 512m |
| event | 512m | ngrok | 128m |
| keycloak | 768m | | |

### 5.4 Image

- Một mẫu Dockerfile cho 7 Spring service: CI đã chạy `mvnw verify` → Dockerfile chỉ `COPY target/*.jar`
  (như `event-service` hiện tại), base `eclipse-temurin:21-jre-alpine` pin digest, user `10001`,
  `HEALTHCHECK` không cần (script tự kiểm health).
- Keycloak giữ `services/keycloak-spi/Dockerfile` (Keycloak 26.8.0 + 2 JAR).
- Nhãn OCI `source`, `revision=<commit>` do `build-push-action` gắn. Tag `sha-<commit>`; deploy luôn bằng digest. Không build trên server.

### 5.5 Trên máy 84

```
/opt/flash-ticket-app/
├── compose/            # CI ghi đè mỗi lần deploy (từ deploy/app/)
├── env/<svc>.env       # người vận hành tạo một lần từ <service>/.env.example, mode 600; CI không bao giờ ghi
├── secrets/ticket-qr/  # QR key ring của ticket (chuyển từ /home/ubuntu/flash-ticket-ticket/runtime/qr), read-only
├── state/<svc>.current, <svc>.previous   # digest đang chạy / trước đó
└── deploy-service.sh
```

- Mỗi service là một Compose project `flash-ticket-<svc>` (vd. `flash-ticket-event-service`), không
  trùng tên project cũ (`flash-ticket-event`, `flash-ticket-user`).
- Giá trị khác local: `EUREKA_SERVER_URL=http://127.0.0.1:8761/eureka/`, `EUREKA_CLIENT_ENABLED=true`
  cho mọi service, `SERVER_ADDRESS=127.0.0.1` trừ gateway (`0.0.0.0` để nhận traffic ngoài),
  DB/Rabbit/Redis/Keycloak qua `127.0.0.1`.

### 5.6 `deploy-service.sh <svc> <image@sha256> <commit>`

1. Kiểm tham số: tên service trong danh sách cho phép; image đúng repo GHCR + digest hợp lệ;
   `env/<svc>.env` tồn tại, mode 600.
2. `flock` theo service (chờ tối đa 5 phút).
3. `docker login ghcr.io` bằng token qua stdin vào `DOCKER_CONFIG` tạm, xóa sau khi xong.
4. Kiểm RAM khả dụng (`MemAvailable`) ≥ `mem_limit` của service; thiếu → dừng, báo `INSUFFICIENT_MEMORY`, **không** recreate.
5. `docker compose config --quiet` → `pull` → `up -d --no-deps --no-build <svc>` (payment: cả `payment-service` và `ngrok`).
6. Chờ health, tối đa 180 s; kiểm container đúng image ID, `RestartCount=0`. Nếu `OOMKilled=true` → báo
   `OOM_KILLED` (không coi là bản lỗi), vẫn rollback. Cách đo health:
   - **discovery-server**: `GET 127.0.0.1:8761/actuator/health` = `UP` (không có Spring Security).
   - **keycloak**: `GET 127.0.0.1:8080/realms/flash-ticket/.well-known/openid-configuration` = 200.
   - **6 service Spring còn lại**: instance của service trong Eureka (`GET 127.0.0.1:8761/eureka/apps/<APP>`, header
     `Accept: application/json`) có `status=UP`, đúng `port` của service và có `registrationTimestamp` ≥ lúc container start. Lý do: `/actuator/health` của
     gateway bị `denyAll`, của event/ticket cần role `INTERNAL_SERVICE`, payment không mở — gọi không token sẽ
     401/403. Eureka phản ánh health thật nhờ `eureka.client.healthcheck.enabled: true` (§5.8).
7. Đạt → `previous ← current`, `current ← image mới`; in `DEPLOY_SUCCESS`.
8. Fail → in tail log đã lọc (không in env), recreate digest trong `current`, chờ health, in
   `ROLLED_BACK <lý do>`, thoát mã lỗi. Lần deploy đầu (chưa có `current`) thì chỉ fail.
9. Dọn image cùng repo, chỉ giữ `current` và `previous`.

`workflow_dispatch`:
- `deploy` + service/all: build từ commit chọn và deploy.
- `restart` + service: recreate với digest `current` (dùng sau khi sửa `env/<svc>.env`).
- `rollback` + service + `image_digest`: bỏ qua build, gọi thẳng script.

### 5.7 Ngrok (payment)

- Nằm trong `deploy/app/payment-service.yml`, `network_mode: host`, trỏ `http://127.0.0.1:8083` (payment);
  image ngrok pin digest như compose hiện có;
  domain `NGROK_DOMAIN` và `NGROK_AUTHTOKEN` trong `env/payment-service.env`.
- `VNPAY_RETURN_URL`/`VNPAY_IPN_URL` = `https://${NGROK_DOMAIN}/api/v1/payments/vnpay/{return,ipn}`.
- Deploy payment recreate cả ngrok. Sau này có domain chuẩn thì bỏ service `ngrok`, cho domain trỏ vào gateway.

### 5.8 Chuẩn hóa code đi kèm (giai đoạn 1)

- Booking, ticket: `java.version` 21, enforcer `[21,22)`, Dockerfile theo mẫu §5.4.
- Booking, payment: bật Eureka client (bỏ `EUREKA_CLIENT_ENABLED=false` khỏi compose cũ).
- 6 service đăng ký Eureka: thêm `eureka.client.healthcheck.enabled: true` để trạng thái trong Eureka theo
  `/actuator/health` thật (phục vụ health check deploy §5.6).
- Xóa `infrastructure/config-server/` và mọi tham chiếu (`ci.yml`, `codeql.yml`, compose local, README, `CONFIG_SERVER_*`).

### 5.9 GitHub cần cấu hình

| Loại | Tên | Ghi chú |
|---|---|---|
| Environment | `production` | Gắn nhánh `main`; không yêu cầu duyệt tay |
| Secret (env) | `FT84_SSH_PRIVATE_KEY` | Đã có ở mức repo; chuyển vào environment |
| Variable (env) | `FT84_SSH_HOST`, `FT84_SSH_USER`, `FT84_SSH_PORT`, `FT84_SSH_KNOWN_HOSTS` | Đã có; chuyển vào environment |
| Xóa | `FT119_*` | 119 bỏ hẳn |
| Branch ruleset `main` | yêu cầu `CI / Required` | |

Action bên thứ ba pin theo commit SHA (như workflow hiện tại).

### 5.10 Đường lên 2 máy / Kubernetes

- 2 máy: thêm cột `host` cho từng service trong `service-filters.yml` và secret SSH thứ hai; script không đổi.
- Kubernetes: giữ job build + GHCR; thay `_deploy-service.yml` bằng bước ghi digest vào repo manifest
  (Helm/Kustomize) để Argo CD sync; `env/<svc>.env` → Secret/ConfigMap của từng Deployment; Eureka có thể
  thay bằng Service DNS.

## 6. Điều kiện tiên quyết (người dùng/vận hành làm, ngoài CI)

1. **MongoDB trên 84 chạy lại được** — **ĐÃ XONG** (2026-11-07: MongoDB đã chạy lại bằng `mongo:7.0` trên volume mới `flash-ticket-mongo7-data`, DB trống).
2. **RAM 84 ≥ ~8 GiB.**
3. Các thay đổi đang dở trên `dev/integration` được commit bởi người sở hữu; PR `dev/integration` → `main` có `CI / Required` xanh.
4. Tạo `/opt/flash-ticket-app/env/<svc>.env` cho 8 service (từ `.env.example` + secret thật), mode 600; chép QR key ring.
5. Kiểm Keycloak có client `internal-service` / `user-management` và secret khớp env.
6. **Chuyển Keycloak sang pipeline CD:** `docker compose down` project `keycloak` (không `-v`), bỏ khai báo keycloak khỏi `/opt/flash-ticket-infra/docker-compose.yml`. Dữ liệu Keycloak trong PostgreSQL được bảo toàn.

## 7. Thứ tự triển khai

1. **Chuẩn hóa code** — §5.8 + Dockerfile mẫu. Verify từng service.
2. **Runtime 84** — `deploy/app/*`, `deploy-service.sh`; chạy thử tay trên 84 với image build từ nhánh.
3. **Pipeline** — `service-filters.yml`, `_maven-verify.yml`, `_deploy-service.yml`, `cd.yml`, sửa `ci.yml`/`codeql.yml`; xóa workflow/script cũ.
4. **Cutover** — sau §6: PR vào `main`; `workflow_dispatch deploy all` lần đầu (discovery → gateway → user → event →
   ticket → booking → payment); smoke qua gateway; xóa 2 container cũ đã dừng và thư mục `/home/ubuntu/flash-ticket-{event,user,ticket}`
   **sau khi người dùng xác nhận**; README vận hành (deploy, restart, rollback, log).

## 8. Để lại cho người làm

- Cú pháp chi tiết workflow, tên job, cách truyền matrix.
- Cách khai service phụ (Postgres/RabbitMQ) trong `_maven-verify.yml`.
- Giá trị `mem_limit`/`MaxRAMPercentage` cuối cùng sau khi đo.
- Nội dung README vận hành.

## 9. Kết quả builder check (lần 1) và cách giải quyết

| Câu hỏi | Giải quyết |
|---|---|
| Cutover có được dừng container cũ? | 119 bỏ hẳn; trên 84 container cũ đã dừng, project name mới không trùng (§5.5), xóa sau khi người dùng xác nhận |
| Hạ tầng 84 đủ chưa, có cần dữ liệu từ 119? | Dữ liệu đã ở 84 (DB, queue, user Rabbit, QR key ring). Mongo đang lỗi → điều kiện tiên quyết §6 |
| Thay đổi chưa commit nào lên `main`? | Ngoài CI: người sở hữu commit; CD chỉ bật sau PR xanh (§6.3) |
| Ngrok trỏ payment hay gateway? | Payment, nằm trong compose payment (người dùng chọn) — §5.7 |
| RAM | Người dùng nâng; script chặn deploy khi thiếu RAM và tách OOM khỏi "bản lỗi" (§5.6) |

**Tự đọc lại với vai builder (lần 2, không dùng subagent):** phát hiện `/actuator/health` của gateway, event,
ticket, payment không gọi được không token → health check deploy đổi sang trạng thái Eureka (§5.6, §5.8);
ngrok cần `network_mode: host` để tới `127.0.0.1:8083` (§5.7). Không còn câu hỏi nào chỉ người dùng trả lời được.
