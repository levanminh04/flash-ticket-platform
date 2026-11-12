# K3s trên máy 84

Phạm vi: một server đồng thời chạy workload, thêm agent sau. Chưa triển khai trên
server chỉ bằng việc merge các file này. Version pin: **v1.37.2+k3s1** (Kubernetes
1.37.2). Xem [release](https://github.com/k3s-io/k3s/releases/tag/v1.37.2%2Bk3s1).

## Thông tin đã kiểm tra

- Máy 84: public `52.64.237.84`, private `172.31.41.236`, Ubuntu 24.04 x86_64.
- Region `ap-southeast-2`, instance `i-074b3bad4b8de92a0`.
- Security Group `sg-002103a96c5f83da4`, VPC `vpc-051c715b89e046bc6`.
- 2 vCPU, khoảng 7,6 GiB RAM. Root khoảng 30 GB, còn 6,5 GiB sau cleanup.
- Đã kiểm route cục bộ: Pod CIDR `10.42.0.0/16`, Service CIDR `10.43.0.0/16`
  chưa trùng Docker/VPC của máy. Còn phải đối chiếu route peering/VPN.
- Chưa chứng minh disk đủ cho cả image Docker đang giữ và image containerd.

## Gửi người quản lý AWS

Ảnh user cung cấp ngày 12/11/2026 cho thấy: inbound 16 rule TCP, tất cả Source
`0.0.0.0/0`: 22, 27017, 4317, 5672, 9090, 5173, 3000, 15672, 8080, 6379, 443,
3200, 3100, 8081, 4318, 80. Outbound một rule All traffic đến `0.0.0.0/0`.
Sau đó user đã cung cấp ảnh rule mới: TCP 5432 và TCP 27017 đều tham chiếu Source
`sg-002103a96c5f83da4` (`launch-wizard-3`). Khi chuyển từ rule IPv4 CIDR sang SG,
AWS yêu cầu thêm rule mới rồi xóa rule cũ; không đổi loại Source ngay trên dòng
cũ. Kiểm thử từ máy ngoài khi có listener tạm trên private IP xác nhận cả hai
cổng bị chặn. PostgreSQL/MongoDB đã thêm private binding, giữ localhost và giữ
đúng image/volume; kiểm từ Pod bằng credential thật thành công.
Thiếu các rule node-to-node 6443,
8472/UDP, 10250 và PostgreSQL 5432 cho thiết kế nhiều node; bổ sung trước join.

Vào **EC2 → Instances → máy 52.64.237.84 → Security → Security groups →
sg-002103a96c5f83da4**. Gửi danh sách **Inbound rules và Outbound rules**,
gồm Protocol, Port range, Source/Destination; không cần access key/password.
Trước tiên chỉ đối chiếu rules; thay rules theo phạm vi triển khai đã thống nhất.

| Inbound đến | Protocol/cổng | Nguồn dự kiến | Mục đích |
| --- | --- | --- | --- |
| 84 | TCP 22 | IP quản trị/nguồn SSH CI hiện có | SSH |
| Node chạy Traefik | TCP 80 | Internet theo yêu cầu public HTTP | API `/api` |
| Server 84 | TCP 6443 | SG của các node, IP quản trị riêng nếu cần | Agent → API server |
| Mọi node | UDP 8472 | Chỉ SG các node | Flannel VXLAN |
| Mọi node | TCP 10250 | Chỉ SG các node | Kubelet/metrics |
| 84 | TCP 5432, 27017 | Chỉ SG các node | PostgreSQL/MongoDB ngoài cluster |
| 84 | TCP 5672, 6379, 8080 | Chỉ SG các node cho đường private | RabbitMQ/Redis/Keycloak |

Không mở UDP 8472, TCP 6443/10250/DB/cache ra Internet. Dùng SG tham chiếu SG
các node khi cùng VPC; không mở cả VPC nếu chưa cần. Khi thêm node có SG khác,
thêm đúng SG đó vào rules liên quan. Không cần etcd 2379/2380 cho thiết kế một
server SQLite này. Port 443 chưa cần public khi chưa cấu hình TLS.

Frontend 5173/Keycloak public 8080 vẫn theo thiết lập hiện hành; phải kiểm tra
riêng nguồn cho người dùng. Sau cutover, cổng Java 8081–8086/8761 không cần
public. Không xóa rule đang dùng trước khi kiểm request qua 80.

Outbound phải cho phép node liên lạc private với nhau/dependency, DNS theo
resolver VPC, HTTPS 443 để tải image/call dịch vụ, SMTP 587 của Ticket, đường
Ngrok/VNPay/Cloudinary hiện dùng. Nếu outbound đang allow-all thì ghi nhận thực
tế; không tự siết lại trong đợt chuyển đổi. Đối chiếu NACL/route khi bị timeout.

Nguồn: [K3s inbound requirements](https://docs.k3s.io/installation/requirements).

## Cài server sau khi qua preflight

Chỉ thực hiện trong cửa sổ vận hành đã duyệt: SG/route phù hợp, port 80/443 trống,
backup cấu hình và volume xác định đúng, disk/RAM có headroom. Đo lại `df -h /`,
`free -m`, `sudo ss -lntup`, `ip route`; không dọn image/volume tự động.

Trên **84**, copy `server-config.yaml.example` thành
`/etc/rancher/k3s/config.yaml`, owner root, mode 600. Kiểm lại private IP/CIDR.

```bash
sudo install -d -m 700 /etc/rancher/k3s
sudo install -m 600 server-config.yaml.example /etc/rancher/k3s/config.yaml
curl --fail --location --proto '=https' --tlsv1.2 \
  'https://raw.githubusercontent.com/k3s-io/k3s/v1.37.2%2Bk3s1/install.sh' -o /tmp/ft-k3s-install.sh
sudo env INSTALL_K3S_VERSION='v1.37.2+k3s1' sh /tmp/ft-k3s-install.sh
sudo k3s kubectl get nodes -o wide
sudo k3s kubectl get pods -A
```

Installer kiểm checksum binary. Giữ kubeconfig `/etc/rancher/k3s/k3s.yaml` mode
600; cấp bản copy mode 600 cho đúng SSH deploy user ở `~/.kube/config` trên node
nếu pipeline dùng `kubectl`. Không commit hoặc gửi kubeconfig/token ra log.
Chưa apply Deployment ứng dụng ở bước này.

## Thêm agent sau này

Config nằm **trên node mới**, không sửa `node-ip` của 84 thành IP node mới.
Mỗi node có tên và private IP riêng, cùng route tới server/dependency, CIDR không
trùng, version tương thích (đợt đầu dùng cùng version pin).

1. Trên 84, đọc token `/var/lib/rancher/k3s/server/node-token` qua kênh quản trị
   bảo vệ, chuyển vào `/etc/rancher/k3s/join-token` trên agent, root:root mode 600.
   Không gửi token vào chat/commit/lệnh có lịch sử.
2. Trên agent, copy `agent-config.yaml.example` vào
   `/etc/rancher/k3s/config.yaml`, mode 600; thay tên và private IP đúng node đó.
3. Tải installer cùng tag như server rồi chạy:

   ```bash
   sudo env INSTALL_K3S_VERSION='v1.37.2+k3s1' INSTALL_K3S_EXEC=agent sh /tmp/ft-k3s-install.sh
   ```

4. Từ 84 kiểm node Ready, Pod DNS, pull GHCR, kết nối DB/Rabbit/Redis/Keycloak từ
   Pod trên **node mới**. Chỉ chạy Pod chẩn đoán không có consumer nghiệp vụ.
5. App không có nodeName/hostPath nên có thể được schedule sang node mới. Giữ
   một replica tới khi kiểm chứng scheduler/consumer/idempotency khi chạy đồng
   thời. Thêm agent không tạo HA cho control plane hoặc DB trên 84.

Tham khảo [cấu hình](https://docs.k3s.io/installation/configuration),
[join agent](https://docs.k3s.io/quick-start),
[registry riêng](https://docs.k3s.io/installation/private-registry).
