# Headlamp — quản lý Kubernetes qua IP FT84

Addon tùy chọn, tách khỏi bảy ứng dụng và pipeline deploy nghiệp vụ. Các file này
**chưa đồng nghĩa đã cài lên FT84**. Chỉ thực hiện lệnh trên server sau khi được
duyệt đúng máy/context và phạm vi triển khai.

URL tạm: **http://52.64.237.84/headlamp/**, dùng Traefik cổng80 hiện có, không cần
tunnel. Theo lựa chọn HTTP tạm thời, **token `cluster-admin` không được mã hóa
trên đường truyền, có thể bị nghe lén/đánh cắp để quản trị toàn cluster**. Login
và token15m không khắc phục nguy cơ này. SSH ở mục3 là cách truy cập mã hóa dự phòng;
domain/HTTPS chưa cấu hình.

## Cấu hình và quyền

- Namespace: `headlamp`; image: `ghcr.io/headlamp-k8s/headlamp:v0.46.0`.
- Một replica, Service `ClusterIP` port `80` → container `4466`; Ingress Traefik
  chỉ entrypoint `web`, path Prefix `/headlamp`, không rule host. Không thêm
  NodePort, hostPort, PVC, Helm hoặc metrics-server.
- Headlamp dùng `-base-url=/headlamp`; hai probe `/headlamp/` theo chart upstream.
  Ingress giữ nguyên prefix, không StripPrefix; Gateway `/api` không thay đổi.
- Pod chạy bằng ServiceAccount `headlamp`, có projected token cho kết nối
  in-cluster nhưng không được cấp RoleBinding/ClusterRoleBinding quản trị.
- ServiceAccount đăng nhập `headlamp-admin` được bind `cluster-admin` theo lựa
  chọn quản trị: có thể đọc Secret, sửa/xóa workload và thay đổi RBAC toàn cluster.
  **Không dùng tài khoản này để chạy Pod, không chia sẻ token.**
- Không bật `unsafe-use-service-account-token`; người dùng phải đăng nhập riêng.
  Không tạo Secret token lâu dài; token đăng nhập được sinh qua TokenRequest.
- Budget khởi điểm: requests `50m CPU / 128Mi RAM`, limits `500m / 256Mi`.
  Kiểm headroom trước cài và theo dõi sau rollout. RollingUpdate có thể chạy hai
  Pod tạm thời.

## 1. Preflight trên FT84

Chạy từ root bản sao repo **trên server**, với kubeconfig của operator mode `600`
như [runbook K3s](../../k3s/README.md). Theo tài liệu, FT84 có public IP
`52.64.237.84`, private IP `172.31.41.236`; phải xác minh lại đích và SSH host key,
không tắt host-key checking hoặc copy kubeconfig về Windows để dùng UI.

```bash
kubectl config current-context
```

```bash
kubectl get nodes -o wide
```

```bash
kubectl version
```

```bash
kubectl auth can-i '*' '*' --all-namespaces
```

```bash
free -m
```

```bash
df -h /
```

```bash
kubectl top nodes
```

Kiểm tài nguyên trùng tên trước lần cài mới; `NotFound` là bình thường:

```bash
kubectl get namespace headlamp
```

```bash
kubectl get clusterrolebinding headlamp-admin
```

```bash
kubectl get ingress -A
```

Nếu đã tồn tại, kiểm ownership và tài nguyên trong namespace trước khi quyết
định cập nhật; không ghi đè addon của người khác. Traefik phải phục vụ `web` trên
cổng80, không redirect HTTP toàn cục. Sai target/context, thiếu quyền, thiếu RAM/disk
hoặc xung đột route/port thì dừng và báo; **không tự đổi port, dừng tiến trình khác,
dọn image/volume hoặc sửa ứng dụng**. `kubectl top` cần metrics API hoạt động;
thiếu metrics không phải lý do tự cài thêm metrics-server. Ghi baseline Gateway
`/api` và Pod nghiệp vụ trước cập nhật. Khi cập nhật addon đã cài, lưu snapshot
Deployment/Service/Ingress riêng, không lấy Secret hoặc token.

## 2. Cài sau khi được duyệt

Chỉ apply RBAC khi cài mới hoặc chủ động cấp lại quyền; cập nhật route không cần
reapply binding:

```bash
kubectl apply -f deploy/addons/headlamp/rbac.yaml
```

```bash
kubectl apply --dry-run=server -f deploy/addons/headlamp/headlamp.yaml
```

```bash
kubectl apply --dry-run=server -f deploy/addons/headlamp/ingress.yaml
```

```bash
kubectl apply -f deploy/addons/headlamp/headlamp.yaml
```

```bash
kubectl -n headlamp rollout status deployment/headlamp --timeout=180s
```

Chỉ mở route sau khi workload Ready:

```bash
kubectl apply -f deploy/addons/headlamp/ingress.yaml
```

```bash
kubectl -n headlamp get pods,service,ingress -o wide
```

Kiểm phân quyền mà không ghi dữ liệu nghiệp vụ, dùng operator có quyền impersonate:

```bash
kubectl auth can-i '*' '*' --all-namespaces --as=system:serviceaccount:headlamp:headlamp
```

Kết quả mong đợi: `no`. Kiểm thêm quyền đọc Secret của Pod cũng phải là `no`:

```bash
kubectl auth can-i get secrets -n flash-ticket --as=system:serviceaccount:headlamp:headlamp
```

```bash
kubectl auth can-i '*' '*' --all-namespaces --as=system:serviceaccount:headlamp:headlamp-admin
```

Tài khoản đăng nhập phải trả `yes`. Nếu khác, điều tra binding thực tế; không cấp
admin cho Pod để chữa lỗi `403`. Các lệnh này chỉ kiểm RBAC, chưa chứng minh luồng
đăng nhập bằng bearer token hoạt động.

## 3. Mở UI từ Windows

Truy cập trực tiếp **http://52.64.237.84/headlamp/**, có slash cuối. Đường
`/headlamp` thiếu slash cuối trả404; dùng đúng link trên, chưa thêm middleware
redirect. Đây là HTTP public không mã hóa, chỉ là cách tạm theo yêu cầu; không
truyền token trong URL, không bật trace/debug hoặc chia sẻ HAR chứa header/token.

### Dự phòng: SSH mã hóa đường truyền

Trước khi mở tunnel, kiểm port local bằng Git Bash; nếu có listener, dừng và báo
PID, không tự đổi port hoặc kill tiến trình:

```bash
powershell.exe -NoProfile -Command "Get-NetTCPConnection -LocalPort 4466 -State Listen -ErrorAction SilentlyContinue | Select-Object LocalAddress,LocalPort,OwningProcess"
```

Trên **FT84**, giữ một terminal chạy forward chỉ trên loopback:

```bash
kubectl -n headlamp port-forward service/headlamp 4466:80 --address=127.0.0.1
```

Trên **Windows**, thay `SSH_ALIAS_FT84` bằng alias SSH đang dùng đã xác minh đúng
máy FT84 và host key. Không dùng nguyên placeholder, không đoán user/private key:

```bash
ssh -N -o StrictHostKeyChecking=yes -o ExitOnForwardFailure=yes -L 127.0.0.1:4466:127.0.0.1:4466 SSH_ALIAS_FT84
```

Mở **http://127.0.0.1:4466/headlamp/**. HTTP này chỉ chạy trên loopback và qua tunnel
SSH; không bind `0.0.0.0`, không thêm rule mở port4466, không nối vào Ngrok/Gateway.
Service ClusterIP vẫn truy cập được từ trong cluster; không phải ranh giới cách
ly mạng. Xác thực/RBAC vẫn bắt buộc với mọi client.

## 4. Đăng nhập và nghiệm thu

Trong terminal quản trị riêng **trên server**, tự tạo token và dán vào UI:

```bash
kubectl -n headlamp create token headlamp-admin --duration=15m
```

Thời hạn thực tế do API server quyết định. Khi hết hạn, tạo token mới. Không chạy
lệnh tạo token qua công cụ thu thập output/log hoặc ghi token vào chat, Git, file
evidence, screenshot hay shell tracing. Không dùng token của Pod để đăng nhập.

Checklist thủ công:

- `/headlamp/` và JS/CSS trả200 đúng content type; refresh trang con vẫn hoạt động,
  request Headlamp không thoát prefix sang `/api` của Gateway.
- Phiên mới chưa đăng nhập: proxy `/headlamp/clusters/main/api/v1/namespaces`
  trả401/403, không trả tài nguyên hoặc HTML fallback. Trang tĩnh trả200 không
  có nghĩa API được truy cập không xác thực.
- Gateway `/api` giữ hành vi baseline, Pod nghiệp vụ Ready; path
  `/headlamp-other` không vào Headlamp. Ingress chỉ có entrypoint `web`.
- Đăng nhập token: xem được namespace, Node, Pod, Deployment và Service.
- Xem log container, events; kiểm watch/stream và WebSocket nếu dùng. Không chia
  sẻ log chứa dữ liệu nhạy cảm; chưa thử thì ghi rõ chưa nghiệm thu luồng đó.
- Xem CPU/RAM khi metrics API và quyền tương ứng hoạt động.
- Kiểm Pod Ready, restart count và mức dùng tài nguyên; cần tăng budget thì
  cập nhật manifest theo số liệu thực tế, không tự sửa trực tiếp chỉ để bỏ lỗi.

```bash
kubectl -n headlamp top pods
```

Không sửa/scale/restart/xóa business workload để thử quyền admin. Sửa trên UI
có thể làm lệch cấu hình Git, ảnh hưởng HPA và quy trình rollout/Eureka. Tiếp tục
triển khai ứng dụng bằng pipeline hiện hành. Chưa kiểm đủ checklist thì không
kết luận đã nghiệm thu UI.

## 5. Rollback route, thu hồi quyền và gỡ addon

Nếu cập nhật HTTP thất bại, chỉ xóa Ingress vừa tạo sau khi đối chiếu ownership:

```bash
kubectl -n headlamp delete ingress headlamp
```

Khôi phục Deployment Headlamp từ snapshot trước cập nhật, chờ rollout Ready và
truy cập SSH theo base URL của snapshot (bản trước subpath dùng `/`). Không xóa
namespace/SA/binding hoặc rollback ứng dụng nghiệp vụ để chữa lỗi route.

Dùng xong: logout, xóa token khỏi clipboard. Nếu dùng SSH dự phòng, dừng tunnel
và port-forward bằng `Ctrl+C`. Logout/đóng tunnel **không thu hồi token** đã cấp,
và không đóng Ingress public.

Chỉ thực hiện thao tác xóa sau khi được duyệt và kiểm lại ownership. Để cắt quyền
admin qua binding do addon này tạo:

```bash
kubectl delete clusterrolebinding headlamp-admin
```

Nếu tài khoản có quyền từ binding khác, xóa binding này không thu hồi các quyền
đó. Token còn hạn cũng không tự hết hiệu lực vì logout; xóa ServiceAccount đăng
nhập sẽ vô hiệu token của tài khoản đó. Khi được duyệt gỡ toàn bộ addon, sau khi
xóa binding và xác nhận namespace không chứa tài nguyên ngoài addon:

```bash
kubectl delete namespace headlamp
```

Namespace chứa cả hai ServiceAccount nên token tương ứng không còn dùng được.
Không xóa namespace `flash-ticket`, không prune rộng. Apply lại `rbac.yaml` sẽ
cấp lại quyền admin; không reapply khi đang chủ động thu hồi quyền.

## Tham khảo

- [Cài Headlamp trong cluster](https://headlamp.dev/docs/latest/installation/in-cluster/)
- [Chart và cấu hình release v0.46.0](https://github.com/kubernetes-sigs/headlamp/tree/v0.46.0/charts/headlamp)
- [RBAC Kubernetes](https://kubernetes.io/docs/reference/access-authn-authz/rbac/)

Các manifest giữ cấu trúc Deployment/Service và base URL/probes upstream, nhưng
pin image, khai báo resource/hardening, tách tài khoản Pod khỏi tài khoản đăng
nhập và không dùng token Secret lâu dài. Chưa tích hợp HTTPS/OIDC hoặc tự động deploy.
