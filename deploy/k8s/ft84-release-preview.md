# ft84 — chuẩn bị phát hành Eureka / Keycloak

**Chỉ xem trước.** Eureka đang chạy 1 Pod đã kiểm registry/Gateway. Keycloak vẫn Compose, image đúng digest đã có trong K3s. `main` vẫn khai báo Keycloak chạy Compose, nên không chuyển traffic trước khi kiểm soát được CD. Không thay branch protection, không gộp thay đổi khác.

## Phạm vi PR

- Base: `origin/main` mới nhất, dùng branch phát hành riêng; không lấy toàn bộ working tree hoặc `git add .`.
- Eureka: `deploy/k8s/discovery-server/{configmap,statefulset}.yaml`, phần đếm replica/kiểm registry trong `deploy/scripts/migrate-k8s-discovery.py`, fixture Eureka và startup hook.
- Keycloak: `deploy/k8s/keycloak/`, template env Keycloak, route auth trong `deploy/https/ingress.yaml`, phần chọn runtime, gate cutover và fixture liên quan. Không thay đổi frontend route hoặc manifest các app khác.
- **File có hunk chung:** `.github/workflows/_deploy-service.yml`, `deploy/scripts/deploy-k8s-service.sh`, `deploy/scripts/migrate-k8s-discovery.py`, fixture routing/discovery và `deploy/k8s/README.md` phải tách riêng các hunk Assistant/Headlamp và những thay đổi có sẵn. Không đưa `.env` thật, chatbot, Gateway security, diagram hay artefact hệ thống vào PR. Rà diff so với `main`, không chỉ so với HEAD hiện tại.

## Gate trước khi merge

1. **CI:** chạy fixture trên diff đã tách sạch. Kiểm riêng routing CD: thay shared scripts theo logic hiện có có thể lên lịch recreate mọi app K3s; vì vậy **không merge như một lần CD thông thường**. Kiểm cả runtime của Keycloak, guard khi chưa có marker và 2 patch Ingress. Chuẩn bị rollback source và revision Eureka hai Pod đã xác minh.
2. **Cửa sổ phát hành:** sau phê duyệt maintenance, khóa deploy trên ft84, xác minh không còn deploy đang chạy. Giữ gate trong lúc merge và chạy cutover thủ công với release SHA được duyệt. Workflow CD tự kích hoạt bởi merge phải không được chạm runtime: kiểm job bị maintenance chặn trước khi ghi env, hoặc dừng đợt CD đó theo phê duyệt riêng. Không mở gate khi đợt CD đó còn có thể tới pha SSH. Đây là chặn có chủ đích; không báo đợt CD là deploy thành công.
3. **Env:** sau khi các job Compose cũ không còn chạy và trước khi mở CD, đồng bộ riêng `ENV_KEYCLOAK` với env-file K3s trên server: chỉ hai hostname PostgreSQL/RabbitMQ thay đổi. Giữ bản env Compose rollback trên ft84 với quyền hạn chế. Không tải secret thật về máy local, không in giá trị nhạy cảm hoặc ghi vào Git. Việc cập nhật GitHub secret cần quyền riêng; chỉ xác nhận ghi thành công, không tuyên bố đọc lại được giá trị.

## Cutover và đóng phát hành

- Không rollout lại Eureka chỉ để đổi nhãn release: đối chiếu manifests/digest và registry 1 Pod đang chạy với release được duyệt. Nếu khác, dừng và rà trước khi áp dụng. Giữ revision 2 Pod làm rollback.
- Chuyển **riêng Keycloak** theo [preview cutover](ft84-cutover-preview.md): dừng Compose, bật marker, deploy đúng digest đã cache, kiểm Service và `IP:8080`, sau đó đổi đúng hai backend auth. So sánh issuer/kid và Events API với baseline; xác minh login/refresh/logout nếu có cách được phép. Không import realm hoặc đổi dữ liệu/port.
- Chỉ gỡ maintenance sau khi **CD cũ đã kết thúc + `main` đúng runtime + env đã đồng bộ + smoke đạt**. Nếu Keycloak phải quay về Compose, khôi phục backend/env theo preview và xử lý lệch runtime trên `main`/GitHub secret trước khi mở lại đường deploy Keycloak; không chạy đồng thời hai bản. Không giữ maintenance rộng một cách vô thời hạn: nếu thiếu quyền merge hay đồng bộ env, dừng **trước khi** bật gate/cutover.
