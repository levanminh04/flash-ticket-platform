# ft84 — preview cutover Eureka và Keycloak

**Trạng thái: chỉ xem trước, chưa áp dụng.** Phạm vi production: namespace `flash-ticket` trên `ft84`, Eureka trước, Keycloak sau. Giữ nguyên DNS/TLS, issuer, cổng và data services. Không chạy CD tổng, không gộp thay đổi đang làm dở của các workload khác.

## Thay đổi và tiêu chí dừng

| Hạng mục | Trước → sau | Chỉ chuyển bước khi |
|---|---|---|
| Eureka | StatefulSet 2 → 1; tắt tự đăng ký/fetch và bỏ probe peer; giữ Service `8761` | Pod `0` Ready, instance UP trong baseline đã trở lại, API đọc qua Gateway vẫn đúng |
| Keycloak | Compose → Deployment 1 Pod `Recreate`, `Service keycloak:8080`, giữ cổng host `8080` cho consumer cũ | Pod Ready, giữ issuer/JWKS, Pod nội bộ truy cập được Service và đường IP:8080 cũ, không lỗi xác thực DB/RabbitMQ |
| Traefik | Hai backend auth `keycloak-external` → `keycloak`, vẫn port `8080` | Chỉ đổi khi Keycloak K3s đạt các kiểm tra ở trên; sau apply, OIDC discovery/JWKS qua HTTPS phải tương đương baseline |

## Preflight bắt buộc trước khi đổi runtime

- Đối chiếu context, node label `ft84`, workload/image hiện tại, các Ingress thực tế, owner của cổng `8080`, resource headroom, image pull và quyền đọc theme. Nếu `hostPort` xung đột hoặc endpoint cũ không hoạt động, dừng; không đổi port. Render riêng hai workload từ nguồn đã rà soát và dùng Kubernetes server-side dry-run, không xuất Secret ra terminal. Chỉ stage release sau khi được phép, không ghi đè release hiện có bằng working tree chưa commit.
- Ghi lại revision/image và baseline pod/registry Eureka, status API đọc, issuer và `kid` công khai, Ingress backend. Phải có revision Eureka hai Pod để phục hồi. Kiểm môi trường không có lần deploy đang chạy; đặt maintenance và dùng khóa deploy hiện có, không giữ khóa bên ngoài khi gọi script tự lấy cùng khóa. Không để CD cũ ghi env/deploy giữa hai bước.
- Keycloak pin `ghcr.io/levanminh04/flash-ticket-platform/keycloak@sha256:5f5c136f4cfda97569f1c158a1d3ba2d987f6e792c7e4fccd8eb5961719c9c54`; xác minh vẫn là image live lúc chuẩn bị chạy. Đổi **chỉ hostname kết nối** trong env-file bảo vệ trên server: `KC_DB_URL` từ `postgres:5432/keycloak_db` sang `172.31.41.236:5432/keycloak_db` (giữ nguyên tham số URL nếu có), `FT_IDENTITY_RABBITMQ_HOST` từ `rabbitmq` sang `172.31.41.236`, port `5672` không đổi. Giữ credentials, broker vhost, Outbox bật, proxy `xforwarded`, trusted addresses đã xác minh và theme mount chỉ đọc. Lưu env Compose cũ an toàn trên server trước khi thay, không in Secret hay sao chép về máy local. TCP từ Pod Payment tới hai cổng đã qua; đó không phải kiểm xác thực từ Keycloak.

## Trình tự áp dụng sau phê duyệt

1. **Eureka trước.** Dùng [script deploy K3s](../scripts/deploy-k8s-service.sh) và digest đang chạy để apply cấu hình mới rồi template/replicas; không chỉ scale. Kiểm registry sau các lần client đăng ký lại và thực hiện smoke API. Nếu chưa phục hồi trong timeout của rollout/registry hoặc API baseline sai, phục hồi revision hai Pod (gồm cả ConfigMap). Nếu StatefulSet kẹt vì Pod Unready của template lỗi dùng cùng digest, so sánh release annotation, xác nhận controller đã nhận template rollback và owner trước khi thay đúng Pod lỗi. Không chuyển Keycloak nếu Eureka chưa ổn định.
2. **Keycloak sau.** Chuẩn bị env và manifests, dừng đúng container `flash-ticket-keycloak-keycloak-1`, xác nhận không còn giữ port rồi bật marker `state/k8s/keycloak-enabled` và deploy đúng digest. Không chạy hai runtime song song trên cùng DB; không import realm. Kiểm Pod, log lỗi đã lọc, realm discovery/JWKS và đường host:8080 từ các phía còn dùng. Chỉ sau đó chuyển hai route auth và kiểm lại HTTPS TLS, issuer/JWKS cùng Gateway. Nếu chưa kiểm được login → refresh → logout, ghi rõ; không dùng dữ liệu hoặc tài khoản ngoài phạm vi. Có thể gián đoạn đăng nhập trong lúc chuyển.
3. **Rollback Keycloak nếu bất kỳ gate nào không đạt.** Giữ maintenance, scale Deployment về 0, xác minh Pod đã dừng; phục hồi env Compose nếu đã đổi, bật đúng container cũ và kiểm port `8080`, phục hồi cả hai backend `keycloak-external`, kiểm HTTPS và dọn marker/trạng thái K3s phù hợp. Không xóa image/container/volume hay Secret chứa dữ liệu của dịch vụ khác.
4. **Đóng thay đổi.** Đối chiếu cấu hình GitHub `ENV_KEYCLOAK` để lần CD sau không ghi lại hostname Docker; không cập nhật secret hoặc merge/push trong phạm vi preview này. Chỉ mở lại CD khi đã chốt nguồn release và cách giữ env nhất quán. Không xóa đường rollback.
