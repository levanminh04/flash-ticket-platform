# Deploy 8 service lên máy 84

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

"Như trên" nghĩa là instance của service trong Eureka (`/eureka/apps/<APP>`) có `status=UP`, đúng port và đăng ký **sau** khi container mới start.

## Việc hằng ngày

```bash
gh workflow run cd.yml -f action=deploy   -f service=user-service      # build commit hiện tại của main và deploy
gh workflow run cd.yml -f action=deploy   -f service=all               # cả 8 service, tuần tự
gh workflow run cd.yml -f action=restart  -f service=payment-service   # sau khi sửa env/payment-service.env
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
├── env/<svc>.env        # người vận hành tạo, mode 600, CI không bao giờ ghi
├── secrets/ticket-qr/   # keys.json của ticket, mount read-only
├── state/<svc>.current, <svc>.previous
└── deploy-service.sh
```

## Env: giá trị khác bản local

Tạo `env/<svc>.env` từ `<thư mục service>/.env.example`, rồi đổi:

| Biến | Giá trị trên 84 |
|---|---|
| `EUREKA_CLIENT_ENABLED` | `true` (mọi service Spring trừ discovery) |
| `EUREKA_SERVER_URL` | `http://127.0.0.1:8761/eureka/` |
| `SERVER_ADDRESS` | `127.0.0.1`; riêng api-gateway `0.0.0.0` |
| Host Postgres/RabbitMQ/Redis/Mongo/Keycloak | `127.0.0.1` |
| payment: `NGROK_DOMAIN`, `NGROK_AUTHTOKEN` | domain và token ngrok |
| payment: `VNPAY_RETURN_URL`, `VNPAY_IPN_URL` | `https://<NGROK_DOMAIN>/api/v1/payments/vnpay/return` và `/ipn` |
| ticket: `TICKET_QR_KEY_RING_LOCATION` | `file:/run/ticket-qr/keys.json` |
| keycloak | mẫu trong `services/keycloak-spi/.env.example` (host DB/broker là `postgres`/`rabbitmq` vì Keycloak chạy trong network `flash-ticket-infra`) |

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

1. Thêm một mục vào `.github/services.json` (đúng thứ tự deploy).
2. Thêm `deploy/app/<svc>.yml` (sao chép `user-service.yml`, đổi tên và `mem_limit`).
3. Thêm `[<svc>]=<port>` vào bảng `PORT` của `deploy/scripts/deploy-service.sh`.
4. Dùng Dockerfile mẫu (sao chép từ discovery-server).
5. `wsl bash deploy/tests/run-all.sh` phải xanh.

## Lên 2 máy / Kubernetes

- 2 máy: thêm trường `host` cho từng mục trong `services.json` và secret SSH thứ hai; `deploy-service.sh` không đổi.
- Kubernetes: giữ job build + GHCR; thay bước SSH bằng bước ghi digest vào repo manifest cho Argo CD; `env/<svc>.env` thành Secret/ConfigMap; Eureka có thể thay bằng Service DNS.
