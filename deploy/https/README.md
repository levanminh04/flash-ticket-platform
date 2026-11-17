# Domain + HTTPS trên máy 84

## Trạng thái ngày 17/11/2026

**Đã triển khai TLS edge trên máy84; chưa cutover cấu hình ứng dụng.** DNS hai host
đã trỏ về `52.64.237.84`. Traefik đã nhận chứng chỉ Let's Encrypt, HTTP redirect
HTTPS và route `/api` qua Gateway; legacy HTTP `/api` vẫn được giữ.

Kiểm chứng ngày17/11/2026:
- HTTPS web và discovery Keycloak trả200, xác thực chứng chỉ thành công cả từ
  server và máy ngoài server, không bỏ qua kiểm tra TLS. SAN đúng từng host;
  chứng chỉ hết hạn ngày15/02/2027 UTC. Traefik cấu hình tự gia hạn qua HTTP-01.
- API `https://flashticket.sondimid.cloud/api/v1/events` trả JSON200; API qua
  `http://52.64.237.84/api/v1/events` vẫn trả JSON200. Chưa kiểm trực tiếp URL Ngrok.
- Traefik Ready1/1, Pod không restart; PVC Bound, `/data/acme.json` mode0600,
  owner/group65532. Chưa thử restart riêng để kiểm persistence hoặc gia hạn thực tế.
- Các path `/headlamp`, `/internal`, `/actuator`, `/metrics`, `/health` trên web
  trả HTML SPA, không phải dữ liệu quản trị; trên auth trả404. Đây là kiểm tra
  các path cụ thể, không thay thế kiểm toán bảo mật toàn bộ endpoint.
- Maintenance gate `/opt/flash-ticket-app/state/maintenance` do đợt TLS tạo vẫn
  đang giữ, nội dung `https-20261117-1322`. Chưa gỡ được vì thao tác bị chặn quyền;
  CD có kiểm gate sẽ chưa deploy cho tới khi người vận hành cho phép gỡ gate này.
- Keycloak vẫn quảng bá issuer HTTP cũ; root auth vẫn redirect về địa chỉ admin
  HTTP cũ. Chưa restart Keycloak, rollout issuer mới hoặc rebuild frontend.
  Login/logout và mixed-content trong trình duyệt **chưa nghiệm thu**.

HelmChartConfig được lưu qua auto-deploy K3s. Bộ manifest HTTPS được quản lý trên
nhánh `dev/son`, chưa hợp nhất vào `main` là nguồn auto-CD; deploy Gateway bằng
revision cũ có thể khôi phục Ingress HTTP.
Không rollout các ConfigMap issuer mới trước khi Keycloak sẵn sàng; không push main
để thử DNS. Source CD, Keycloak và frontend phải được đồng bộ ở bước cutover tiếp.

## 1. Cài DNS tại Nhân Hòa — phần bạn cần thao tác

Đăng nhập trang quản lý dịch vụ Nhân Hòa, chọn domain **sondimid.cloud**, mở
**Quản lý DNS / Quản trị bản ghi** (hệ thống ZoneDNS). Nếu tài khoản domain và tài
khoản quản trị DNS khác nhau, dùng thông tin quản trị DNS do Nhân Hòa cung cấp;
không gửi mật khẩu vào chat. Tên menu có thể khác theo giao diện.

Domain hiện có nameserver `ns1.zonedns.vn` đến `ns4.zonedns.vn`.
**Không cần đổi nameserver hoặc chuyển sang Cloudflare.**

Thêm hai bản ghi:

| Loại | Tên / Host | Địa chỉ IP / Giá trị | TTL |
| --- | --- | --- | --- |
| A | `flashticket` | `52.64.237.84` | Mặc định, hoặc 300 nếu được chọn |
| A | `auth.flashticket.sondimid.cloud` | `52.64.237.84` | Mặc định, hoặc 300 nếu được chọn |

Giao diện Nhân Hòa đã chấp nhận tên ngắn một nhãn `flashticket`, nhưng từ chối
`auth.flashticket`: với subdomain nhiều nhãn, nhập đầy đủ
`auth.flashticket.sondimid.cloud`, không thêm dấu chấm cuối. Nếu dùng FQDN cho web,
nhập `flashticket.sondimid.cloud`. Kiểm tên cuối cùng để không bị lặp đuôi domain.

- Không ghi `https://`, dấu `/` hoặc port vào tên bản ghi.
- Không sửa bản ghi `@`, `www`, MX/TXT email hoặc các subdomain khác.
- Nếu host đã có CNAME hoặc IP cũ, đối chiếu trước khi thay; không tạo CNAME cùng A.
- Chưa tạo AAAA: hiện chưa xác minh đường IPv6. AAAA sai có thể làm ACME thất bại.
- Nếu domain có CAA hạn chế CA, cần cho phép `letsencrypt.org`; không xóa CAA khác
  một cách máy móc. Không cần thêm CAA nếu chưa có chính sách hạn chế.
- DNS thường cập nhật sau vài phút; NXDOMAIN có thể được cache lâu hơn.
- IP phải ổn định. Nếu đây là public IP EC2 tự cấp, stop/start có thể đổi IP.
  Kiểm tra Elastic IP trước sử dụng lâu dài; không tự tạo/gán EIP có tính phí.

Kiểm tra từ máy bạn, mỗi lệnh phải thấy IP `52.64.237.84`:

```bash
nslookup flashticket.sondimid.cloud 1.1.1.1
```

```bash
nslookup auth.flashticket.sondimid.cloud 1.1.1.1
```

Lưu xong, báo lại để tiếp tục xác minh và cutover. Không cần mua SSL riêng.

## 2. Kiến trúc được giữ nguyên

| URL | Đích |
| --- | --- |
| `https://flashticket.sondimid.cloud/` | Frontend Compose, private `172.31.41.236:5173` |
| `https://flashticket.sondimid.cloud/api` | Gateway Service K3s, port 8081; giữ nguyên path |
| `https://auth.flashticket.sondimid.cloud/` | Keycloak Compose, private `172.31.41.236:8080` |

Traefik hiện có terminate TLS cổng443. Let's Encrypt HTTP-01 dùng cổng80; không
đổi port nội bộ. Hai host HTTP redirect sang HTTPS bằng middleware riêng.
**Không redirect toàn entrypoint web:** Ngrok vẫn gọi HTTP `/api` và Headlamp hiện
có vẫn ngoài phạm vi task. `legacy-api.yaml` giữ route HTTP cũ cho Ngrok.

Ingress HTTPS chỉ ở entrypoint `websecure`; API priority200 thắng frontend
priority100. Redirect HTTP priority1000 thắng route hostless; không thêm middleware
rewrite cho API. Headlamp hiện chỉ ở `web`, không route dưới hai domain mới.

Service không selector + EndpointSlice nối Traefik tới hai container ngoài cluster.
IP private này gắn với máy84; khi đổi máy phải cập nhật EndpointSlice, nguồn tin cậy
proxy và security group tương ứng. Không đưa management Keycloak9000 ra ngoài.

## 3. Preflight và backup trước triển khai

Dành một cửa sổ vận hành ngắn: Traefik chuyển sang `Recreate`, Keycloak cần restart;
backend cần rollout và frontend cần build lại. Phiên đăng nhập cũ cần login lại.

Kiểm tra:

- DNS A/AAAA/CAA công khai đúng; cả máy84 và Pod resolve/truy cập được issuer HTTPS.
- AWS Security Group/NACL/route cho TCP80 và443 từ Internet. Không mở DB/cache,
  Kubernetes6443/10250 hoặc UDP8472 ra Internet.
- SSH `ft84`, kubeconfig và quyền sudo hoạt động; đủ disk/RAM. Lần kiểm17/11 còn
  khoảng5.3GiB disk và2.8GiB memory available, cần đo lại trước build/rollout.
- Chart máy84 là `41.6.101+up41.6.1`, Traefik `3.7.15`, một replica,
  storageclass `local-path`. Đối chiếu lại nếu version đã đổi.
- Không có deploy CD cạnh tranh; dùng maintenance gate và khóa deploy hiện có,
  không giữ cùng flock rồi gọi script deploy gây deadlock.

Backup vào thư mục riêng mode0700 trên server, file0600, ngoài Git:

- HelmChart/HelmChartConfig hiện có; Ingress; sáu ConfigMap; deployment image refs.
- `/opt/flash-ticket-app/env/keycloak.env`, client realm đang dùng, nguồn env backend.
- Frontend digest/current/previous, bốn GitHub Variables VITE hiện tại.
- State deploy K3s/Compose hiện tại và source release tương ứng.

Backup client/env/state có thể chứa secret: không in, đính kèm hoặc upload artifact.
Giữ nguyên DB, volume, realm, key-ring QR, RabbitMQ và Ngrok/VNPay.

## 4. Thứ tự cài đặt — sau DNS và backup

Các lệnh `kubectl` sau chạy trên84, với cây `deploy/` của revision HTTPS đã chuẩn bị.
Không apply cả thư mục `deploy/https` một lượt trước preflight.

### 4.1. Kiểm cấu hình không thay runtime

Tại repo, cần PyYAML đã có trong môi trường kiểm thử:

```bash
python .github/scripts/test-https-config.py
```

Trên84:

```bash
kubectl apply --dry-run=server -f deploy/https/traefik-config.yaml -f deploy/https/external-services.yaml -f deploy/https/ingress.yaml -f deploy/https/legacy-api.yaml -f deploy/k8s/api-gateway/ingress.yaml
```

Dry-run chỉ xác nhận API/schema; không render Helm hoặc chứng minh kết nối runtime.
Render chart đang cài với valuesContent, kiểm strategy/Recreate, volume PVC,
securityContext và ACME args trước apply thật. Không sửa manifest Traefik gốc của
K3s trong `/var/lib/rancher/k3s/server/manifests/traefik.yaml`.

### 4.2. Bật TLS, giữ issuer cũ tới khi chứng chỉ hợp lệ

Cài HelmChartConfig bền vững qua cơ chế auto-deploy của K3s:

```bash
sudo install -m 600 deploy/https/traefik-config.yaml /var/lib/rancher/k3s/server/manifests/flash-ticket-traefik-config.yaml
```

Chờ Helm controller reconcile thành công, rồi kiểm PVC/Pod mới. Không chỉ dựa vào
`rollout status` ngay sau copy vì controller có thể chưa tạo revision mới.

```bash
kubectl -n kube-system get helmchartconfig,helmchart,pvc,pods
```

`acme.json` nằm trong PVC `/data`, UID65532, mode0600; init container dùng `touch`
và `chmod`, không ghi đè nội dung cũ. Chart giữ PVC khi uninstall nhưng không thay
backup. Một replica + Recreate tránh hai process đồng thời ghi file ACME.

```bash
kubectl apply -f deploy/https/external-services.yaml -f deploy/https/ingress.yaml -f deploy/https/legacy-api.yaml -f deploy/k8s/api-gateway/ingress.yaml -f deploy/k8s/api-gateway/ingress.yaml
```

Chờ HTTPS hợp lệ cả hai host trước bước sau. Giai đoạn này frontend và discovery
vẫn chứa URL HTTP cũ: TLS đã có **chưa có nghĩa ứng dụng đã cutover xong**.

### 4.3. Đồng bộ Keycloak và các nguồn cấu hình

`public.env.example` chỉ chứa giá trị public, **không phải file env thay thế**.
Ghép những key cần thiết vào file env đầy đủ đang có; không làm mất DB/broker
credentials, không truyền password bằng command line hoặc in token.

- Keycloak: `KC_HOSTNAME=https://auth.flashticket.sondimid.cloud`,
  `KC_HTTP_ENABLED=true`, `KC_PROXY_HEADERS=xforwarded`.
- `KC_PROXY_TRUSTED_ADDRESSES`: đối chiếu nguồn thật từ Traefik. Template cho
  Pod CIDR `10.42.0.0/16` và node84 `/32`; không bật trust mọi địa chỉ.
- Cập nhật `/opt/flash-ticket-app/env/keycloak.env` **và GitHub Secret
  `ENV_KEYCLOAK`**, vì CD ghi lại file này. Restart đúng Keycloak bằng luồng Compose
  hiện có, giữ image/volume/network và env-file.
- Trong realm `flash-ticket`, client `frontend` đang tồn tại: thêm
  Valid Redirect URIs `https://flashticket.sondimid.cloud/*`, Web Origins
  `https://flashticket.sondimid.cloud`, logout redirect phù hợp nếu client đang
  dùng allowlist logout. Giữ các entry localhost còn dùng; không mở `*` cho origin.
- Đối chiếu realm frontendUrl/client root/base URLs nếu đang set URL cũ.
  **Import JSON không cập nhật realm đã tồn tại**: phải sửa client/realm thật qua
  Admin Console hoặc Admin API bảo vệ; backup trước khi PUT.
- Đồng bộ `KEYCLOAK_ISSUER_URI` trong nguồn `ENV_*` backend để tránh drift, dù K3s
  hiện lấy key này từ ConfigMap. Không thêm cơ chế JWKS/audience mới.

### 4.4. Backend và frontend

Deploy sáu backend bằng manifest HTTPS, đúng digest hiện tại và quy trình deploy
K3s đang có để lưu revision/rollback. Gateway ingress trong `deploy/k8s` được script
snapshot cùng app; không dùng config cũ để redeploy sau cutover.

- Issuer sáu backend: `https://auth.flashticket.sondimid.cloud/realms/flash-ticket`.
- Gateway CORS thêm `https://flashticket.sondimid.cloud`; giữ origin local hiện có.
- Không đổi token/admin/JWKS URL private khi chưa có nhu cầu; không đổi payment flags.

GitHub **repo Variables** cần cập nhật:

| Variable | Giá trị |
| --- | --- |
| `VITE_API_GATEWAY_URL` | `https://flashticket.sondimid.cloud` |
| `VITE_KEYCLOAK_URL` | `https://auth.flashticket.sondimid.cloud` |
| `VITE_KEYCLOAK_REALM` | `flash-ticket` |
| `VITE_KEYCLOAK_CLIENT_ID` | `frontend` |

Rebuild/deploy frontend bằng CD hiện có. VITE được build cứng vào bundle;
**restart Nginx không đổi URL**. Workflow frontend build/push GHCR: chỉ kích hoạt
sau khi đã được phép publish image. Không commit/push source tự động trong task
này; cần đưa manifest HTTPS vào nhánh CD dùng trước khi mở deploy thường trở lại.
Chưa đồng bộ nguồn CD thì deployment mới chỉ là tạm thời, chưa bàn giao hoàn tất.

Không đổi Ngrok domain/upstream, VNPay return/IPN hoặc thực hiện giao dịch để smoke.

## 5. Nghiệm thu

```bash
curl --fail --show-error https://flashticket.sondimid.cloud/
```

```bash
curl --fail --show-error https://auth.flashticket.sondimid.cloud/realms/flash-ticket/.well-known/openid-configuration
```

```bash
curl --head http://flashticket.sondimid.cloud/
```

- Chứng chỉ trusted, SAN đúng host, chain/expiry hợp lệ; không dùng `-k` để cho qua.
- Discovery issuer, authorization/token/logout endpoints dùng HTTPS domain mới.
- Web/asset/deep link hoạt động; browser không mixed-content; login/logout bằng
  tài khoản test được phép, JWT mới được API chấp nhận; thiếu token vẫn bị chặn.
- Sáu backend Ready/đúng issuer, không restart bất thường; Pod truy cập issuer được.
- `/api` đi Gateway, không rewrite; API public read-only vẫn hoạt động.
- `/internal`, `/actuator`, `/headlamp` không trả dữ liệu quản trị qua domain mới.
  Frontend SPA fallback có thể trả HTML200: không coi status200 đơn lẻ là API lộ
  hoặc là bằng chứng endpoint nghiệp vụ hoạt động.
- Keycloak `/metrics`, `/health` không lộ management9000 qua proxy8080.
- HTTP host Ngrok vẫn tới Gateway; không redirect nó về domain web.
- PVC Bound, file ACME0600. Kiểm storage giữ nguyên sau restart trong cửa sổ được
  phép; không xóa acme.json để thử, không ép cấp production nhiều lần.

Traefik tự gia hạn khi tiếp tục chạy, storage còn nguyên, DNS và port80 thông.
Theo dõi lỗi ACME và hạn chứng chỉ; email tài khoản không thay giám sát expiry.
Thiết kế một Traefik/local-path không có HA khi node84 lỗi.

## 6. Rollback và bảo mật sau cutover

Giữ maintenance, phục hồi bộ cấu hình cũ đồng nhất: frontend vars/image,
Keycloak client/hostname/env, backend issuer/CORS và Ingress từ snapshot. Dùng quy
trình rollback service hiện có; snapshot trước HTTPS có thể khôi phục issuer HTTP
và bỏ TLS, nên đây là rollback cả cutover chứ không chỉ đổi một image.

Khi bỏ cấu hình Traefik phải xử lý cả file auto-deploy và HelmChartConfig live theo
runbook Helm/K3s, kiểm values thực sau reconcile. Chỉ xóa file không chứng minh Helm
đã trở về cấu hình cũ. Không xóa PVC/chứng chỉ, DB, namespace hoặc volume.

Sau nghiệm thu HTTPS, hạn chế đường public5173/8080 để người dùng không đi vòng
TLS; giữ private access từ Pod tới Keycloak8080 và frontend5173. Không bind Keycloak
loopback vì Traefik/Pod cần gọi private IP. Thay Security Group phải đối chiếu nguồn
đang dùng trước, không đóng cổng mù quáng. Các cổng Redis/RabbitMQ/management đang
listen public là rủi ro hạ tầng hiện hữu, không tự thay trong task domain này.

## Nguồn áp dụng

- [K3s packaged Traefik / HelmChartConfig](https://docs.k3s.io/networking/networking-services)
- [Traefik ACME, HTTP-01, storage và giới hạn nhiều replica](https://doc.traefik.io/traefik/reference/install-configuration/tls/certificate-resolvers/acme/)
- [Traefik Helm chart](https://github.com/traefik/traefik-helm-chart)
- [Keycloak reverse proxy / trusted forwarded headers](https://www.keycloak.org/server/reverseproxy)

Áp dụng đúng chart trên máy84; không thêm cert-manager hoặc proxy mới cho một node.
