# Sơ đồ

Tệp nguồn sơ đồ thay đổi thường xuyên được đặt trong `src/`. Nháp bằng PlantUML hoặc Structurizr DSL; chỉ dựng lại trên Visual Paradigm khi sơ đồ đã đủ ổn định để đưa vào báo cáo.

Tên tệp theo quy ước `<mã-chương>-<loại>-<chủ-đề>`. Không khóa mã chương cho tới khi nhận mẫu báo cáo chính thức; trong giai đoạn nháp có thể dùng mã nội bộ mô tả loại và chủ đề.

## Nháp kiến trúc triển khai và chuyển giao

| Sơ đồ | Nguồn chỉnh sửa | Hình xem trực tiếp |
| --- | --- | --- |
| Kubernetes Deployment | [draw.io](src/deployment-kubernetes-flashticket.drawio) | [SVG](exports/deployment-kubernetes-flashticket.svg) |
| K3s nhiều node · phân bố Pod minh họa | [draw.io](src/deployment-k3s-multi-node-flashticket.drawio) | [SVG](exports/deployment-k3s-multi-node-flashticket.svg) |
| DevOps Delivery Flow | [draw.io](src/flow-devops-delivery-flashticket.drawio) | [SVG](exports/flow-devops-delivery-flashticket.svg) |

Mở tệp nguồn bằng diagrams.net. Bộ nháp dùng bố cục 2D chia vùng và tên tệp theo loại/chủ đề. Icon tài nguyên lấy từ [bộ icon Kubernetes chính thức](https://github.com/kubernetes/community/tree/master/icons).

- **Kubernetes.** Đối chiếu [manifest và runbook K8s](../../deploy/k8s/README.md), [hướng dẫn K3s](../../deploy/k3s/README.md) và [Ingress API](../../deploy/k8s/api-gateway/ingress.yaml). Hai vùng K3s và Compose dùng chung máy 84. Các kết nối tới phụ thuộc Compose được gộp theo nhóm. Tài liệu [AWS runtime baseline](../architecture/aws-runtime-baseline.md) ghi topology của các giai đoạn trước.
- **K3s nhiều node.** Bản minh họa đối chiếu cấu hình trên `dev/son` tại HEAD `fd65853`. Giả định thêm hai agent vào server 84 và vẽ một cách xếp chín Pod của bảy ứng dụng, trong đó Booking có hai Pod. Tên A/B và cách xếp là ví dụ. Manifest ứng dụng hiện không khai báo quy tắc bắt buộc tách replica sang node khác. Vị trí Pod hệ thống được lược bỏ. Máy 84 vẫn giữ control plane duy nhất và toàn bộ Compose. [Hướng dẫn thêm agent](../../deploy/k3s/README.md#th%C3%AAm-agent-sau-n%C3%A0y) · [Kubernetes Scheduler](https://kubernetes.io/docs/concepts/scheduling-eviction/kube-scheduler/) · [Horizontal Pod Autoscaling](https://kubernetes.io/docs/concepts/workloads/autoscaling/horizontal-pod-autoscale/)
- **DevOps.** Đối chiếu [CI](../../.github/workflows/ci.yml), [CD](../../.github/workflows/cd.yml), [job triển khai](../../.github/workflows/_deploy-service.yml), [danh mục service](../../.github/services.json) và [script triển khai K8s](../../deploy/scripts/deploy-k8s-service.sh). Hình mô tả các trigger PR/push chính. Nhánh manifest K3s dùng digest hiện hành. Khôi phục sau lỗi rollout cần revision trước đủ điều kiện.
- **Giới hạn kiểm chứng.** Đọc working tree `dev/son` tại HEAD `1c03e23`. Runbook ghi nghiệm thu ngày 12/11/2026. Trạng thái máy 84 và các rollout sau mốc đó chưa được xác minh trong lượt này. VNPay/callback giữ flag `false` ở mốc nghiệm thu. Addon Headlamp là tùy chọn theo runbook.

## B3 — Biểu đồ hoạt động có swimlane

Bốn tệp nguồn bắt buộc của B3 nằm trong `src/`:

- `B3-01-event-lifecycle.puml`
- `B3-02-order-payment-ticket.puml`
- `B3-03-refund-reconciliation-payout.puml`
- `B3-04-online-check-in.puml`

Các sơ đồ này mô tả tác nhân và kết quả nghiệp vụ, không biểu diễn service, schema, Saga hoặc giao thức. Chỉ dựng lại trên Visual Paradigm/đánh số theo chương sau khi B3 ổn định và mẫu báo cáo được xác nhận.

## B5 — Biểu đồ gói cho Bản đồ Bounded Context

Nguồn PlantUML của bản đồ đã duyệt nằm trong `src/`:

- `B5-01-bounded-context-map.puml`

Mỗi package trong sơ đồ là một **bounded context ứng viên**, không mặc nhiên là service hoặc schema vật lý. Đường nét đứt chỉ biểu diễn phụ thuộc ngữ nghĩa đã được truy vết trong B5 §5.1; không được đọc thành API, topic, Saga hay hướng gọi đồng bộ. Bản Visual Paradigm chỉ cần dựng khi sơ đồ được chọn đưa vào báo cáo.
