# 7 ứng dụng Flash Ticket trên K3s

Gateway/Eureka và Booking/Payment/Ticket/Event/User chạy trong namespace
`flash-ticket`. PostgreSQL, MongoDB, RabbitMQ, Redis, Keycloak, Frontend và Ngrok
tiếp tục chạy Compose trên 84. Eureka trả địa chỉ Pod; HTTP nghiệp vụ đi trực
tiếp đến Pod đã chọn. Hai Kubernetes Service dành cho Eureka và Gateway.

## File và cấu hình

```text
deploy/k3s/                       config node, hướng dẫn mạng/join agent
deploy/k8s/namespace.yaml
deploy/k8s/<app>/deployment.yaml  một replica, Recreate, probes/resources
deploy/k8s/<app>/configmap.yaml   endpoint private và cấu hình không nhạy cảm
deploy/k8s/discovery-server/service.yaml
deploy/k8s/api-gateway/service.yaml
deploy/k8s/api-gateway/ingress.yaml
deploy/k8s/examples/              danh sách Secret; không apply thư mục này
deploy/scripts/deploy-k8s-service.sh
```

**Không `kubectl apply -R -f deploy/k8s`.** Deployment chứa image
`:render-required`; script render digest đã verify trước khi apply. Không render
bằng envsubst: `${POD_IP}`, `${POD_UID}`, `${server.port}` trong
`SPRING_APPLICATION_JSON` do Spring resolve. Pod IP/UID lấy bằng Downward API.

Liveness kiểm tiến trình còn hoạt động; readiness kiểm trạng thái nhận request.
Startup dùng liveness, tối đa khoảng 180 giây trước khi chuyển sang probe thường.
Hai health group chỉ gồm availability state, không gộp DB/Rabbit/Eureka vào
liveness. Readiness UP không chứng minh nghiệp vụ/dependency đã hoạt động.
Mỗi Pod chờ shutdown tối đa 90 giây; Spring cho 30 giây mỗi phase. Cần kiểm HTTP,
scheduler, AMQP và đăng ký Eureka thực tế khi nghiệm thu.

Ingress Traefik chỉ route `/api` đến Gateway:8081. `/actuator` và `/internal`
không có public route. Chỉ GET hai đường probe được mở chính xác trong các chain
liên quan; không bỏ JWT của API nội bộ. Issuer Keycloak giữ public URL đang dùng;
token/JWKS dùng đường private theo cấu hình hiện tại của từng app.

## Secrets và image pull

Nguồn credential vẫn là GitHub `ENV_<SERVICE>` → file mode 600 trên server
`/opt/flash-ticket-app/env/<service>.env`. Script chỉ lấy key trong template
`examples/<service>.secret.env.example`, không `source`/eval env và không nhập
host localhost cũ vào Secret. Key thiếu được báo bằng **tên**, không in giá trị.
Với User, host Mongo loopback được đổi thành private IP, giữ credential/database/
query. Kiểm lại URI nếu dùng multi-host/SRV thay vì URI một host hiện tại.

Ticket giữ **key-ring thật hiện tại** ở
`/opt/flash-ticket-app/secrets/ticket-qr/key-ring.json`. Template có dạng
`{"keys":{"v1":""}}`; giá trị thật là Base64 của khóa AES 32 byte. Không sinh
khóa mới thay khóa cũ khi chuyển môi trường vì sẽ mất khả năng giải mã vé cũ.
Secret `ticket-qr-key-ring` mount read-only `/run/ticket-qr/key-ring.json`, mode
0440, fsGroup/UID 10001. Không đưa file thật vào Git/artifact.

Tài khoản triển khai `ubuntu` cũng phải đọc được file nguồn để script tạo Secret.
Trên 84, giữ owner UID 10001 của thư mục/file QR, dùng group `ubuntu`, thư mục
0750 và file 0440. Cách này giữ quyền đọc của container cũ và cấp quyền đọc cho
operator; không sinh khóa thay thế hoặc mở file cho mọi tài khoản. Backup trước
cutover giữ nguyên nội dung khóa và quyền hạn ở thời điểm sao lưu.

Kiểm pull đúng digest bằng Pod chẩn đoán. Nếu GHCR private, tạo credential đọc
package bền vững trong Secret `ghcr-pull` ở namespace và gắn vào default Service
Account trước khi bootstrap:

```bash
kubectl -n flash-ticket patch serviceaccount default \
  -p '{"imagePullSecrets":[{"name":"ghcr-pull"}]}'
```

Tạo Secret từ dockerconfigjson được cấp an toàn, không truyền token vào command
line/log. GITHUB_TOKEN của job có thời hạn ngắn nên không dùng làm credential
pull lâu dài. Không cần tạo pull Secret nếu đã kiểm digest truy cập public.

## Deploy thường và rollback một service

CD push main dùng `.github/services.json.runtime`: bảy app `k8s`, Keycloak và
Frontend `compose`. Source đổi → verify/build/publish GHCR/deploy; chỉ manifest
đổi → dùng digest K8s current, tạo Pod mới. Node template/Secret example/docs
không tự cài cluster hoặc xoay Secret. Khi chỉ sửa `ENV_*`, dùng action restart
hiện có để nạp lại, thay Secret không tự cập nhật process đang chạy.

Input mỗi commit nằm `/opt/flash-ticket-app/releases/<sha>/deploy/`. Script:

```bash
bash /opt/flash-ticket-app/releases/<sha>/deploy/scripts/deploy-k8s-service.sh \
  <service> ghcr.io/levanminh04/flash-ticket-platform/<service>@sha256:<64hex> \
  <40hex-commit> deploy
```

Mode `recreate` dùng cùng digest nhưng annotation release mới; `rollback` chỉ
chấp nhận digest **previous** có đủ snapshot cấu hình. Không rollback một digest
tùy ý mà không biết cấu hình tương ứng.

Script khóa chung `/opt/flash-ticket-app/state/k8s/deploy.lock`, yêu cầu file
`state/k8s/enabled`, namespace sẵn và không có `state/maintenance`. Không tự cài
K3s hoặc fallback Compose. Compose deploy cũng dùng khóa này và từ chối Java đã
chuyển K8s khi gate enabled.

State K8s riêng `state/k8s/<service>/`: current/previous trỏ revision, mỗi revision
có image, Deployment/ConfigMap/Service/Ingress và snapshot Secret. Thư mục mode
700/file 600, chỉ người vận hành được đọc; **không upload artifact hoặc in cả
thư mục**. Đây là dữ liệu nhạy cảm cần backup/retention theo chính sách vận hành.
State Compose `state/<service>.current/.previous` được giữ nguyên cho rollback
cutover. Không ghi đè hai loại state.

Sau apply, script đợi rollout (300s), kiểm đúng image ref, một Pod Ready không
restart và Eureka có instance đúng Pod UID/IP/port. Không so digest multi-arch
index với imageID platform như hai giá trị buộc phải bằng nhau. Lỗi rollout vẫn
trả job failure, thử khôi phục toàn bộ revision trước rồi kiểm lại; lần đầu chưa
có revision tốt cần runbook rollback Compose bên dưới.

## Bootstrap/cutover: chưa tự động thực hiện

Để có image mới trước khi dừng Compose, CD có input `publish_only=true` cùng
`action=deploy`, `service=k8s`: chỉ verify/build/publish đúng bảy ứng dụng, bỏ
toàn bộ job SSH deploy. Dùng revision code đã được phép push; lấy digest từ
GHCR tag `sha-<commit>` để bootstrap. Không bật runtime gate hoặc tạo consumer
chỉ để pipeline build được image. Push main bình thường vẫn deploy như cũ.

Chỉ thực hiện sau khi đã có kết quả verify, image đã publish được phép, SG/route
đã xác minh, cluster/dependency/pull sẵn, đủ tài nguyên và cửa sổ gián đoạn đã duyệt.

1. Khóa deploy cạnh tranh bằng `state/maintenance`; chờ các job đang chạy kết
   thúc và lấy khóa chung trước khi thao tác. Backup env/Compose/state/digest,
   nguồn QR, Ngrok upstream, frontend build vars; giữ backup mode 600 ngoài Git.
2. Theo [hướng dẫn K3s](../k3s/README.md), chuẩn bị cluster, namespace/pull auth.
   Dùng `kubectl apply --dry-run=server` với manifest đã render đúng digest trước
   khi apply thật. Không khởi động business Pod khi Compose còn consumer cũ.
3. PostgreSQL hiện Compose
   `/opt/flash-ticket-infra/postgres/docker-compose.yml`, Mongo hiện
   `/opt/flash-ticket-infra/mongodb/docker-compose.yml` **Mongo 7.0**. Chỉnh đúng
   file đang dùng: giữ binding loopback và bổ sung `172.31.41.236:5432:5432` /
   `172.31.41.236:27017:27017`; không đổi image/volume. Kiểm Compose render, rồi
   recreate từng dependency trong cửa sổ duyệt, health và dữ liệu sau đó. Không
   áp template Mongo 8 trong repo lên volume Mongo 7. Kiểm quyền/pg_hba nếu Pod
   kết nối bị từ chối; không đổi DB auth để bỏ qua lỗi mạng.
4. Dùng Pod chẩn đoán tạm kiểm DNS, DB, Rabbit, Redis, Keycloak private từ đúng
   Pod network; xóa đúng Pod tạm sau kiểm. Không ghi credential vào manifest log.
5. Ngừng nhận request mới, drain rồi dừng **đúng 7 container Java** (90 giây grace
   ban đầu). Kiểm không còn consumer/scheduler cũ. Không down cả dependency stack.
6. Upload cây `releases/<sha>` và nguồn env đã xác minh. Tạo `state/k8s/enabled`.
   Bootstrap thủ công theo thứ tự Discovery → Booking/User/Event/Payment/Ticket
   → Gateway. Khi maintenance còn bật, lệnh vận hành phải đặt
   `K8S_BOOTSTRAP_SHA=<sha>` đúng SHA cho script; CI không truyền override này.
   Mỗi lệnh script tự giữ khóa chung, người vận hành không giữ cùng flock khi
   gọi script để tránh tự deadlock.
7. Kiểm từng instance Eureka, loại registry/cache cũ ra khỏi đường gọi; health
   không chứng minh HTTP nghiệp vụ đúng. Kiểm public `http://52.64.237.84/api/...`,
   JWT/role và public `/actuator/health` `/internal/...` không route được.
8. Ngrok giữ nguyên domain; thêm `NGROK_UPSTREAM=http://127.0.0.1:80` vào env
   Payment trên server qua cơ chế env-file, cập nhật Compose file mới rồi chỉ
   recreate `ngrok` với `--no-deps`. Không gọi deploy Compose Payment. Ví dụ:

   ```bash
   IMAGE="$(cat /opt/flash-ticket-app/state/payment-service.current)" \
     docker compose --env-file /opt/flash-ticket-app/env/payment-service.env \
     -p flash-ticket-payment-service \
     -f /opt/flash-ticket-app/compose/payment-service.yml \
     up -d --no-deps --force-recreate ngrok
   ```

9. Đổi repo Variable `VITE_API_GATEWAY_URL` sang public port 80 và rebuild
   Frontend bằng luồng hiện có; restart Nginx không đổi bundle. Kiểm CORS và
   redirect Keycloak thực tế. Không tự đổi issuer/realm/client.
10. Kiểm callback tới đúng route. Payment runtime hiện hai flag VNPay/callback
    false; giữ nguyên. Muốn chạy E2E sandbox phải xác nhận cấu hình/dữ liệu test
    và bật flag theo thay đổi riêng đã duyệt; không coi callback 404 khi flag tắt
    là bằng chứng routing hỏng, không thực hiện thanh toán thật để smoke.
11. Kiểm shutdown HTTP/scheduler/AMQP/Eureka, trạng thái order/message/vé không
    trùng với fixture nghiệp vụ phù hợp. Chỉ gỡ maintenance và bật CD thường khi
    các kiểm tra đạt. Ghi lại digest/revision và kết quả, không chỉ Pod Running.

## Rollback cutover về Compose

Giữ maintenance, đóng nhận giao dịch mới. Scale cả bảy Deployment ứng dụng về 0,
đợi Pod kết thúc và kiểm consumer dừng trước khi khởi động Compose. Bỏ gate
enabled chỉ khi đã xác nhận K8s app dừng; giữ snapshot K8s để điều tra.
Khôi phục đúng Compose/env/image snapshot trước chuyển đổi: Discovery trước,
business apps, Gateway cuối. Đổi Ngrok upstream về giá trị cũ và recreate riêng
Ngrok; rebuild Frontend về URL cũ nếu bundle đã đổi. Kiểm registry/callback/API
và công việc còn dở trước khi gỡ maintenance.

Không rollback schema/data, purge queue, xóa volume hoặc giả kết quả Saga chỉ
bằng đổi image. Không `docker compose down -v`, delete namespace hoặc prune-all
để xử lý lỗi deploy. Rollback thật còn cần được diễn tập trong cửa sổ được phép.

## Quan sát

```bash
kubectl -n flash-ticket get pods -o wide
kubectl -n flash-ticket get deployments,services,ingress
kubectl -n flash-ticket describe pod <pod>
kubectl -n flash-ticket get events --sort-by=.lastTimestamp
kubectl -n flash-ticket logs deployment/<service> --tail=100
kubectl -n flash-ticket rollout status deployment/<service> --timeout=300s
kubectl -n flash-ticket port-forward service/discovery-server 8761:8761 --address=127.0.0.1
```

Không publish Eureka/Actuator ra Internet để debug. Kiểm registry qua localhost
port-forward; log có thể có dữ liệu nghiệp vụ, chỉ chia sẻ phần đã lọc.

## Nghiệm thu trên 84 ngày 12/11/2026

Đã chuyển đủ bảy ứng dụng sang K3s tại revision ứng dụng
`03039e7d388c14aa37528a149d14bf52371e44f2`. Bảy container Java Compose đã dừng;
dependency, Frontend và Ngrok tiếp tục chạy Compose. Image mới đều pull public,
có amd64/arm64; không cần bổ sung GHCR credential cho các digest đã kiểm.

Đã đạt 39 kiểm tra runtime: đúng digest, bảy Pod Ready không restart, probe GET,
Eureka đúng sáu instance Pod, API qua Ingress, JWT/role, CORS, financial summary
nội bộ với token thật và callback không chữ ký. API/Frontend cũng đã kiểm từ máy
ngoài; Ngrok chỉ một tunnel trỏ port 80, frontend bundle bỏ URL Gateway port 8081.

Đã diễn tập config-only và rollback riêng Gateway: đảo thứ tự cùng danh sách
CORS origin trong fixture vận hành, xác nhận Pod mới nhận giá trị rồi rollback
image/cấu hình cũ. Source manifest gốc được khôi phục sau kiểm; API trả 200.
Đây là kiểm rollback một service, chưa diễn tập toàn bộ cutover về Compose.

Report nằm trong thư mục release trên server: `runtime-smoke.json` và
`gateway-rollout-rollback.json`. Snapshot env/Secret chỉ lưu trên server với
quyền bảo vệ; không upload chúng để làm evidence. VNPay/callback giữ flag false;
chưa chạy giao dịch sandbox/Saga E2E hoặc chứng minh chịu tải. CD phải giữ khóa
cho tới khi thay đổi runtime trong PR #19 được phép tích hợp vào main.
