# 8 ứng dụng Flash Ticket trên K3s — nguồn triển khai

Gateway/Eureka và Booking/Payment/Ticket/Event/User chạy trong namespace
`flash-ticket`. Nguồn FE nay dùng K3s; **chưa bootstrap/cutover FE live** trong lượt
triển khai source ngày 17/11/2026. PostgreSQL, MongoDB, RabbitMQ, Redis và Keycloak
vẫn ở ngoài K3s; FE Compose giữ làm fallback, Ngrok theo runbook Payment riêng.
Eureka trả địa chỉ Pod; HTTP nghiệp vụ đi trực
tiếp đến Pod đã chọn. Gateway có một Service; Eureka có Service client và thêm
headless Service để hai peer có địa chỉ DNS ổn định.

## Cutover Keycloak và Eureka trên ft84

Nguồn mới chuyển Eureka sang 1 Pod standalone và Keycloak sang 1 Pod K3s; không tự áp dụng vào runtime. Hai bước thực hiện riêng để cô lập lỗi.

- **Eureka:** chụp revision hiện tại và danh sách instance UP, sau đó deploy revision mới bằng `deploy-k8s-service.sh` với digest hiện tại. Không chỉ scale: probe cũ cần Pod thứ hai. Giữ Service 8761 và headless DNS. Chờ registry phục hồi đủ instance và kiểm API qua Gateway. Nếu lỗi, phục hồi cả ConfigMap và StatefulSet hai replica từ revision trước; nếu controller kẹt do một Pod chưa Ready của revision lỗi, xác minh owner và template đã phục hồi trước khi thay Pod đó theo runbook. Cụm còn Deployment legacy phải chuyển qua revision hai peer tương thích trước. Một Pod không có dự phòng khi restart.
- **Keycloak:** kiểm trước đường Pod tới PostgreSQL/RabbitMQ, issuer nội bộ, digest, theme/mount, dải Traefik được trust và khả năng giữ cổng host 8080. Dùng file `/opt/flash-ticket-app/env/keycloak.env` và danh sách biến trong `examples/keycloak.secret.env.example`; chỉnh địa chỉ Docker-only sang endpoint nội bộ đã xác minh trước khi dùng, giữ nguyên credentials và cấu hình realm. Không import realm, không nâng phiên bản trong lần chuyển.
- Trong cửa sổ bảo trì, giữ deploy/CD không chạy đồng thời. Dừng đúng Keycloak Compose trước, tạo marker `state/k8s/keycloak-enabled` để cho phép script K3s và khóa đường Compose, deploy đúng digest, kiểm Pod Ready, OIDC/JWKS và đường host 8080 trước khi apply Traefik Ingress mới. Đối chiếu `ENV_KEYCLOAK` với quy trình phát hành trước khi mở CD để một lần recreate không ghi đè endpoint đã chuyển. Giữ container Compose và `keycloak-external` làm rollback; không chạy đồng thời hai runtime trên cùng DB.
- **Rollback Keycloak:** khóa CD, scale Deployment về 0 và chờ Pod dừng/hết giữ port, khởi động đúng container Compose, xác minh endpoint 8080 rồi phục hồi hai backend Ingress về `keycloak-external`; gỡ marker sau khi đã xác minh và lưu lại trạng thái. Không apply lại Ingress mới khi còn dùng Compose. Không xóa dữ liệu hay volume.

## Domain và HTTPS

Cấu hình cutover và hướng dẫn DNS Nhân Hòa nằm tại
[Domain + HTTPS](../https/README.md). **Không deploy riêng issuer/Ingress mới trước
khi hoàn tất DNS, TLS và Keycloak theo đúng thứ tự trong runbook.**

## File và cấu hình

```text
deploy/k3s/                       config node, hướng dẫn mạng/join agent
deploy/k8s/namespace.yaml
deploy/k8s/<app>/deployment.yaml  RollingUpdate cho Gateway và năm service
deploy/k8s/<app>/configmap.yaml   endpoint private và cấu hình không nhạy cảm
deploy/k8s/<core>/hpa.yaml        autoscaling/v2 cho năm service nghiệp vụ
deploy/k8s/discovery-server/service.yaml
deploy/k8s/discovery-server/statefulset.yaml   một Pod standalone
deploy/k8s/discovery-server/peer-service.yaml  headless DNS cho peer
deploy/k8s/api-gateway/service.yaml
deploy/k8s/api-gateway/ingress.yaml
deploy/k8s/examples/              danh sách Secret; không apply thư mục này
deploy/scripts/deploy-k8s-service.sh
deploy/scripts/migrate-k8s-discovery.py
```

**Không `kubectl apply -R -f deploy/k8s`.** Workload chứa image
`:render-required`; script render digest đã verify trước khi apply. Không render
bằng envsubst: `${POD_IP}`, `${POD_UID}`, `${server.port}` trong
`SPRING_APPLICATION_JSON` do Spring resolve. Pod IP/UID lấy bằng Downward API.

Liveness kiểm tiến trình, readiness kiểm trạng thái nhận request. Startup của
sáu ứng dụng chờ health readiness và chính instance UID/IP có status UP trong
Eureka. Đây là gate một lần, không khiến toàn bộ Pod đang phục vụ mất Ready khi
Eureka tạm gián đoạn. Hai health group chỉ gồm availability state; không gộp
DB/Rabbit/Eureka vào liveness. Các image có curl/jq để chạy hook và đọc JSON.
Gateway/core có 180 giây shutdown, Eureka 120 giây; Spring cho 30 giây mỗi phase.
Health/registry đạt chưa chứng minh luồng nghiệp vụ đã chạy đúng.

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

## Policy tạm một Pod/service — 28/11/2026

- **Trạng thái live 28/11/2026, kiểm lúc 04:06 UTC:** đã khóa HPA User1/1, giảm Payment2→1 rồi Booking2→1 dưới global deploy lock; script exit0. Audit riêng exit0: đủ10 Deployment/StatefulSet desired/Ready1, năm HPA1/1, không Pod dư/Terminating. Payment giữ Pod ởft26, Booking/User ởft84; hai Pod Booking/Payment tạift70 đã kết thúc. Các app vốn1 không rollout lại.
- **Bảo toàn và kiểm sau scale:** UID survivor, template hash/current revision giữ nguyên; readerOFF, PDBmin1 và anti-affinity không đổi, annotation deletion-cost tạm đã khôi phục. Ba service readiness/livenessUP, bảy app Eureka mỗi app1UP; public root200, orders không token401. Node Ready/không MemoryPressure ở mẫu, không phải capacity/load proof. Queue Booking/Payment ready/unacked/DLQ đều0 ở mẫu; không gửi giao dịch hoặc message test.
- **Rollback/live override:** metadata gốc nằm trênft84 tại `/opt/flash-ticket-app/state/k8s/one-pod-20261128T040221Z-2297807` (dir700/file600); không có Secret. Chưa cần rollback. Chỉ phục hồi đúng HPA/replica đã snapshot khi revision/template chưa đổi và đủ capacity, không `rollout undo` hoặc ghi đè revision mới. Snapshot revisionHA2 cũ giữ nguyên; source một Pod **chưa push/publish**, CD/rollback bản cũ có thể đưa về2.
- **RAM:** ft84 lúc04:07:21UTC available4246MiB/total7776MiB, swapused265MiB. SSH đọcft70/ft26 bị công cụ từ chối nên chưa có số MemAvailable sau scale ở hai node đó; không suy RAM tiết kiệm từ số Pod đã thu. Snapshotft70~65MiB trước đây chỉ là lịch sử, không đo cùng cửa sổ.
- **Kiểm chứng local:** full 7 nhóm deploy fixture exit0, gồm các nhánh one-Pod mới trong Booking/Payment; targeted writer-only1, reader rejection, live drift giả lập và restore2→1 đều đạt. Routing, 17 discovery tests, runtime hooks, YAML invariants và shell syntax đã đạt. Các fixture không chứng minh continuity/capacity/freshness production.
- Booking, Payment và User dùng HPA `minReplicas=maxReplicas=1`; Event/Ticket đã 1/1. Gateway, Assistant, Frontend, Keycloak và Eureka đã một Pod thì không rollout lại chỉ để đổi số lượng. Không thay datastore, K3s control plane hoặc DaemonSet.
- Booking/Payment giữ PDB `minAvailable=1`, hard anti-affinity, image, reader flags, probes/drain và rolling0/1. Một Pod thường trực **không là HA** khi Pod/node lỗi; PDB có thể chặn eviction/drain sole Pod. Surge tạm khi rollout vẫn được phép, phải đủ capacity.
- Script giữ hai profile hạ tầng1/PDB1 và2/PDB1 cho restore lịch sử. Reader Booking ON vẫn cần HA2/baseline tương thích. Payment source mới dùng transaction routing/PG_* ngay ở1Pod, không còn reader flag hoặc baseline activationHA2; xem mục Payment. Không tự scale lên2 để vượt gate.
- Scale-down vận hành cần khóa deploy chung, snapshot HPA/scale metadata, recheck image/revision, theo dõi preStop và một survivor Ready/Eureka. Có thể gợi ý `pod-deletion-cost` tạm trên Pod để ưu tiên thu Pod ở node thiếu RAM; đây là best-effort, không pin node hoặc quyền force-delete. Không thay Pod template/image/Secret.
- Live override1 sẽ khác snapshot HA2 cũ đến khi release source mới được publish. Giữ nguyên revision lịch sử; không dispatch CD/shared recreate hoặc gọi rollback HA2 là vẫn một Pod. Ghi baseline/override để CD không âm thầm nâng lại2. Lượt scale-down không tự cấp quyền commit/push/merge/CD.

### Booking: đọc phụ Admin Chart và 5 báo cáo Organizer/event

- **Mặc định tắt:** Ba flag `BOOKING_SALES_CHART_REPLICA_ENABLED`, `BOOKING_SALES_ORGANIZER_REPORT_REPLICA_ENABLED`, `BOOKING_SALES_EVENT_REPORT_REPLICA_ENABLED` chỉ từ env-file/Secret revision, mặc định false; không đặt trùng ConfigMap/Deployment. Hai group flag cần base flag bật. Writer-only không cần credential/timing reader.
- **Whitelist:** base mở Admin Chart; Organizer mở summary/chart/events sales-statistics theo organizer; Event mở `/api/v1/events/{eventId}/sales-statistics` và `/api/v1/organizer/events/{eventId}/sales-summary`. Modern event vẫn lookup owner trên writer trước cache; legacy giữ owner/key/default ngày riêng. Admin Summary/ranking, order/availability và mọi worker vẫn writer. Một pool/permit reader dùng chung cho cả 6 API mỗi Pod.
- **Freshness:** trần độ cũ end-to-end **60 giây gồm cache**, không phải request timeout/chu kỳ sync. Phải cộng tuổi mẫu, chờ pool/query, chờ cache write, TTL và biên an toàn; TTL mặc định 30s không tự chứng minh đạt. Mẫu hết hạn/lag/slot bận chọn writer trước đọc; đã thử reader mà lỗi thì invalidate và trả lỗi, không writer retry/cache lỗi.
- **TLS chỉ cho pilot Booking:** URL chỉ có một đích private `ft70:5432/booking_db` đã đối chiếu trong preflight, với đúng `sslmode=require` và `targetServerType=secondary`. Không nhúng credential hoặc thêm tham số JDBC khác. TLS mã hóa nhưng **không xác minh danh tính máy chủ**, nên không tương đương `verify-full`; không đóng gói/mount CA phía Booking. Giữ nguyên chứng thư/TLS phía PostgreSQL, HBA và Security Group; không mặc định áp dụng chính sách này cho service khác.
- **Baseline rồi từng nhóm:** Deploy writer-only trước, cả 3 flag off, cùng image với bản sẽ bật reader; kiểm 2 Pod khác hostname, HPA/PDB, readiness/Eureka và luồng writer. Sau khi budget 60s và điều kiện production đạt, bật Chart → Organizer → Event qua từng revision cùng image/placement/HPA/PDB. Script kiểm URL/snapshot, không tự xác minh host là `ft70` hay chứng minh freshness. Readiness chỉ kiểm app/writer.
- **Rollback:** Revision active lưu `writer-baseline` đã kiểm; active→active kế thừa và xác minh lại, không đòi tắt reader xen kẽ. `previous` giữ writer-only tương thích, không trở thành pilot trước. Auto/manual rollback khôi phục Secret/ConfigMap/Deployment/Service/HPA/PDB và cập nhật pointer đúng baseline **2 Pod**, không chỉ đổi image hoặc `rollout undo`. Snapshot cũ absent PDB được xử lý khi rollback hạ tầng riêng; không dùng nó làm baseline bật reader. Kiểm revision, cả 3 flag và luồng writer sau hoàn tác. Chưa được phép vận hành thì chỉ chuẩn bị source, không tự rollout.

### Payment: transaction read/write routing, một Pod

- **Cấu hình mới:** `writeDs`/`readDs` bind `app.db.write`/`app.db.read`; một primary lazy datasource chọn read khi transaction readOnly, mặc định write. Xóa marker/facade/WAL health/permit và mọi `PAYMENT_REPORT_REPLICA_*`. Hướng này là source mới, chưa tự xác nhận đã deploy production.
- **Biến triển khai:** `PG_RW_HOST`, `PG_RO_HOST`, `PG_DB`, `PG_USER`, `PG_PASSWORD`, `PG_WRITE_POOL_SIZE`, `PG_READ_POOL_SIZE` từ env-file `payment-service.env`, render vào Secret qua envFrom có sẵn. Không còn `PAYMENT_DB_URL/USERNAME/PASSWORD`; không đặt lại PG_* trong ConfigMap/CLI/JVM/JSON. Deploy từ chối khóa cũ còn sót, endpoint sai hoặc pool không dương trước apply; không in giá trị Secret.
- **Budget/quyền/TLS:** env-file production đặt rõ pool5/2 để không tăng ngầm từ budget cũ; mặc định ứng dụng10/10. Writer giữ schema/SET ROLE payment_owner, reader không SET ROLE; cùng PG_USER/PG_PASSWORD, TLS require và target primary/secondary. Require mã hóa nhưng không verify server identity, cùng account không phải least privilege. Không tự GRANT/HBA/CA hoặc thay endpoint.
- **Nghiệp vụ:** chỉ GET Admin refund list và refund-progress giữ readOnly=true → replica. Reconciliation/owner, buyer status, VNPay return/IPN, financial-summary, bốn worker scans và ghi/consumer dùng primary. REQUIRED đọc trong outer write TX vẫn primary; repository tự mở readOnly có thể đi reader, không coi đó là SQL ngoài TX.
- **Lag/lỗi:** không health gate, permit, writer fallback hoặc cam kết freshness60s. Refund GET có thể trả trạng thái/count cũ hoặc lỗi500 khi replica lỗi. POST trả snapshot primary, GET báo cáo sau đó có thể trễ. Writer DB health không chứng minh reader sẵn sàng. Hai report giữ isolation mặc định của annotation cũ; không còn facade RR bao ngoài.
- **Rollout/rollback:** giữ HPA1/1/PDBmin1, hard anti-affinity, rolling0/1, probes/Eureka/drain/resources và port. Một Pod không là HA; PDB có thể chặn drain, surge cần capacity. Phát hành image và bộ PG_* cùng revision, không chỉ sửa Secret trên image cũ. `previous` là revision trước thực tế, không ép writer-baseline. Auto/manual restore khôi phục đúng image+ConfigMap+Secret+HPA+PDB snapshot; snapshot writer cũ không bị đổi thành PG_*. Legacy absent-PDB giữ pinning/HPA lịch sử và chỉ xóa Payment PDB; không dùng `rollout undo` thay snapshot.
- **Kiểm local:** fixture kiểm actual deploy script, source override/pool/key/rollback và HPA/PDB/placement; Maven physical tests kiểm connection JPA thật. Không suy capacity, role/ACL/TLS hoặc production acceptance từ fixture. Không tự deploy, giao dịch tiền hay fault drill production.

### Event: transaction read/write routing, recovery-1

- **Cấu hình:** một EventDataSourceConfig, raw Hikari writeDs/readDs tại app.db.write/read; primary LazyConnectionDataSourceProxy bọc anonymous router. Transaction readOnly chọn read, còn lại write; không marker/whitelist facade/WAL probe/permit/activation flag. Source mới chỉ được kiểm local, chưa tự triển khai production.
- **Secret/env-file:** PG_RW_HOST, PG_RO_HOST, PG_DB, PG_USER, PG_PASSWORD, PG_WRITE_POOL_SIZE, PG_READ_POOL_SIZE trong event-service.env, render qua canonical envFrom. Release đặt rõ pool5/2; guard từ chối thiếu key, host/database sai, pool không nguyên dương/vượt5/2, khóa EVENT_DB_*/EVENT_READ_REPLICA_* cũ và nguồn app.db/PG_*/spring.datasource override ở ConfigMap/inline env/JSON/JVM/CLI. Không ghi password vào ConfigMap/Deployment/log.
- **Quyền/TLS:** cả hai pool dùng cùng PG_USER/PG_PASSWORD và schema event_schema. Writer SET ROLE event_owner, target primary; reader readOnly, target secondary, acquire/connect/socket5s và không SET ROLE owner. TLS require mã hóa nhưng không verify server identity. Account chung không phải least privilege; cần xác minh ACL/HBA/TLS thật trước release, không suy từ event_reader cũ hoặc tự GRANT/tắt TLS.
- **Replica:** public list/detail, homeDiscovery, categories/venues, Organizer/Admin event lists. **Primary:** readOwned/readAdmin, images/seat-map, cả ba cancellation history/worklists, cancellation prechecks, image prepare/referenced, background no-TX, mọi ghi và outbox. Giữ owner/role/status/version/SQL; outer REQUIRED write giữ nested read trên writer. JDBC repository cụ thể không tự tạo transaction readOnly.
- **Lag/lỗi/cache:** không WAL admission, fallback writer hoặc bảo đảm freshness60s. Public có thể thấy PUBLISHED đã bị hủy hoặc chưa thấy event vừa publish. Cache home30s có thể refill từ standby còn trễ sau invalidation; TTL không giới hạn replication lag. Replica SQL lỗi đi ra error contract hiện có, không retry writer. Readiness DB health chỉ writer; UP không chứng minh reader sẵn sàng. Timeout transaction5s không là deadline HTTP tuyệt đối.
- **Một Pod:** HPA1/1, không spec.replicas, không PDB; hard hostname anti-affinity tách surge khỏi old Pod, rolling0/1, minReady75s/drain75s/grace180s giữ nguyên. Rollout cần hai node đủ capacity; sole Pod/node crash vẫn gián đoạn, không gọi HA. Guard giữ từ chối live pinning/PDB unmanaged, không tự xóa chúng hoặc đổi port.
- **Release/rollback:** phát hành image và bộ config cùng revision. previous là successful current thực tế, không ép writer-baseline/cùng-image-OFF. Restore dùng đúng image+ConfigMap+Secret+Service+HPA của snapshot, không render bằng env-file hiện tại. Legacy writer-only/reader-on được kiểm theo format cũ và giữ nguyên file; legacy HPA1/2 chỉ là rollback hạ tầng lịch sử, không template routine deploy. Không trộn image cũ/env PG_* mới hoặc dùng rollout undo thay snapshot.
- **Bằng chứng/giới hạn:** Maven physical standby tests kiểm business SQL và JPA/JDBC cùng transaction qua recovery/PID; mock lookup hoặc psql riêng không chứng minh Spring routing. Deploy fixture kiểm actual embedded validator và snapshot old↔new/new↔new, không chứng minh production capacity/ACL/TLS hoặc thời gian gián đoạn.

### Ticket: một Pod ổn định, surge CD và list đọc phụ

- **Recovery-1, không PDB:** HPA `min=max=1`, Deployment không sở hữu replicas. Required hostname anti-affinity đúng Ticket/namespace `flash-ticket` bao old/new revision; không pin node. Giữ rolling0/1, minReady75s, drain75s và grace180s. Rollout cần **hai node schedulable** đủ request; surge Pending là NO-GO, giữ/restore Pod cũ, không hạ anti-affinity hoặc tự resize. Một Pod/node crash vẫn có thể gián đoạn; đây không phải HA tầng Pod/đầu-cuối. No-PDB không bảo vệ eviction sole Pod; không tự xóa PDB unmanaged.
- **Default-off:** chỉ `GET /api/v1/orders/{orderId}/tickets` được xét reader. QR JSON/PNG, check-in, gửi lại vé, issuance probe, consumer và worker vẫn writer. Readiness gồm `readinessState,db` (writer), liveness chỉ `livenessState`; reader down không là lý do restart/liveness fail.
- **Nguồn duy nhất:** tám `TICKET_LIST_REPLICA_*` keys trong Secret revision; không ConfigMap/container env/JSON/JVM/CLI override. Bật reader cần business=true, login `ticket_reader`, URL một host:5432/ticket_db khác writer với đúng `sslmode=require&targetServerType=secondary`. Script không xác minh hostname thực là ft70 hoặc tự GRANT. TLS require mã hóa nhưng không verify server identity. Reader không `SET ROLE ticket_owner`; SELECT hai bảng issuance/ticket có snapshot/ciphertext nên không coi là dữ liệu public.
- **Budget15s chưa nghiệm thu runtime:** probe1s/query1s/sample2s/acquire5s/transaction5s, pgJDBC connect/socket5s trong datasource properties. Deploy guard chặn timing lớn hơn allocation; cộng cấu hình không chứng minh horizon15s hoặc timeout hard. Lag/sample hết hạn/bận chọn writer trước đọc; sau bắt đầu reader thì lỗi đi ra, invalidate và release sau transaction, **không retry writer**. Ambient transaction giữ nguồn caller; không đổi business SQL/owner/count guards.
- **Pool/capacity:** mỗi process writer max5/min1 và reader max2/min0, slot request reader1; probe có scheduler riêng. Overlap old/new là tối đa10+4 connection, cộng Pod đang terminate và dependencies; không chỉ tính7 connection steady-state. Redis/RabbitMQ/discovery/writer vẫn có giới hạn riêng.
- **Baseline/rollback:** writer-only cùng image/profile1/HPA/placement/probes và mọi business config/secret ngoài tám reader keys, **cùng QR ring**, trước activation riêng. Snapshot bắt buộc image/Deployment/ConfigMap/HPA/env Secret/QR Secret; Service optional, không dựng PDB/Service giả. Active→active kế thừa/revalidate baseline; `previous` giữ writer-compatible. Thay QR/business config cần writer baseline mới, không nới semantic compare. Auto/manual restore cập nhật current/image/previous bằng temp+mv; legacy HPA1/2/pinning là rollback hạ tầng riêng. Live-only stale nodeAffinity được null trong đúng effective apply recovery-1, canonical snapshot giữ sạch; nếu còn pinning thì fail trước rollout wait. Không đụng rule service khác.
- **Trước activation production:** cần quyền riêng để kiểm capacity/network/role/ACL/TLS/LSN, horizon15s, traffic/Eureka/drain và overlap worker. Giữ cơ chế bridge legacy thiếu preStop trong migration helper; không bỏ drain gate. Fixture local chỉ kiểm script/state semantics, chưa chứng minh continuity hoặc freshness live. Không log credential, QR/key-ring, snapshot, URL có thông tin đăng nhập; không bật shell tracing.

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

CD push main dùng `.github/services.json.runtime`: tám app `k8s` (gồm frontend),
Keycloak `compose`. Source đổi → verify/build/publish GHCR/deploy; chỉ manifest
đổi → dùng digest K8s current, tạo Pod mới. Node template/Secret example/docs
không tự cài cluster hoặc xoay Secret. Khi chỉ sửa `ENV_*`, dùng action restart
hiện có để nạp lại, thay Secret không tự cập nhật process đang chạy.

Input mỗi commit nằm `/opt/flash-ticket-app/releases/<sha>/deploy/`. Script:

```bash
bash /opt/flash-ticket-app/releases/<sha>/deploy/scripts/deploy-k8s-service.sh \
  <service> ghcr.io/levanminh04/flash-ticket-platform/<service>@sha256:<64hex> \
  <40hex-commit> deploy
```

Mode `recreate` giữ tên CLI cũ: dùng cùng digest nhưng annotation release mới,
vẫn chạy RollingUpdate theo manifest. `rollback` chỉ
chấp nhận digest **previous** có đủ snapshot cấu hình. Không rollback một digest
tùy ý mà không biết cấu hình tương ứng.

Script khóa chung `/opt/flash-ticket-app/state/k8s/deploy.lock`, yêu cầu file
`state/k8s/enabled`, namespace sẵn và không có `state/maintenance`. Không tự cài
K3s hoặc fallback Compose. Compose deploy cũng dùng khóa này và từ chối Java đã
chuyển K8s khi gate enabled. Riêng frontend chỉ bị chặn Compose khi đã có
`state/k8s/frontend.current`, không chặn bởi marker enabled chung trước bootstrap.

State K8s riêng `state/k8s/<service>/`: current/previous trỏ revision, mỗi revision
có image, Deployment hoặc StatefulSet, ConfigMap/Service/Ingress/HPA/PDB (nếu có) và snapshot Secret. Thư mục mode
700/file 600, chỉ người vận hành được đọc; **không upload artifact hoặc in cả
thư mục**. Đây là dữ liệu nhạy cảm cần backup/retention theo chính sách vận hành.
State Compose `state/<service>.current/.previous` được giữ nguyên cho rollback
cutover. Không ghi đè hai loại state.

Sau apply, script đợi rollout (600s), đọc lại số replica mong muốn, kiểm đủ Pod
Ready không restart và Eureka có instance đúng UID/IP/port cho từng Pod. HPA có
thể đổi replica trong khi kiểm nên script lấy lại trạng thái mỗi lượt. Không so digest multi-arch
index với imageID platform như hai giá trị buộc phải bằng nhau. Lỗi rollout vẫn
trả job failure, thử khôi phục toàn bộ revision trước rồi kiểm lại; lần đầu chưa
có revision tốt cần runbook rollback Compose bên dưới.

## Lịch Pod Booking và các service khác

**Booking/Payment hiện hành:** writer-only một Pod, HPA1/1 theo policy tạm ở trên; User khóa1/1. Đoạn HA2 sau chỉ là profile rollback/reader lịch sử, không ghi đè policy tạm.

**Profile Booking HA2:** HPA giữ `min=2,max=2`; scheduler tự chọn mọi node đủ điều kiện,
không pin hostname hoặc mặc định loại `ft70`. Hard pod anti-affinity theo hostname
và selector Booking trong namespace `flash-ticket` đặt hai Pod khác node, bao phủ
cả old/new revision. PDB `booking-service` có `minAvailable=1`, chỉ giới hạn eviction
tự nguyện; không bảo vệ node crash. Giữ RollingUpdate `maxUnavailable=0,maxSurge=1`,
readiness/Eureka và drain hiện có: rollout cần **node thứ ba** schedulable đủ request.
Thiếu capacity ⇒ NO-GO/Pending, không tự co-locate, hạ anti-affinity hoặc resize.
Writer/Redis/RabbitMQ ở `ft84` vẫn là dependency SPOF; đây chỉ là HA tầng Pod.

**Profile Payment HA2:** khi được chọn rõ ràng dùng HPA2/2, hard anti-affinity/PDB và placement động như
mục Payment riêng; không còn ghim `ft84`/`server-2`. Đây chưa là xác nhận rollout live.

## Frontend: bootstrap, cutover và rollback riêng

**Trạng thái:** chỉ source/config/tests; chưa publish image, SSH preflight hoặc đổi
route live. Không chạy CD `all`/`k8s` hay merge main để thử FE: đổi shared deploy script
chọn recreate toàn bộ K8s app, có thể áp callback Payment của `0ed5baf` đang hoãn.
Lần chuyển đầu chỉ chọn `frontend`; phát hành CD main cần quyết định riêng.

### Runtime và state

- `Deployment/frontend`: ổn định 1 replica, không HPA; RollingUpdate
  maxUnavailable0/maxSurge1, minReadySeconds10, progressDeadlineSeconds300.
  Pod mới chưa Ready thì giữ Pod cũ trong rollout bình thường. Không phải HA khi
  node/Traefik/network hỏng hoặc Pod duy nhất crash; terminating Pod vẫn tốn RAM.
- Port5173, UID/GID10001, root filesystem read-only, không service-account token,
  drop ALL/no privilege escalation; `/tmp` emptyDir memory64Mi.
  Request25m/32Mi, limit64Mi không CPU limit. Cần đo OOM/capacity với cả surge và
  terminating Pod; tmpfs cũng tính vào memory, không chỉ dựa requests.
- Probe HTTP `/index.html` không phụ thuộc Keycloak/API; preStop đợi5s,
  grace30s và image `STOPSIGNAL SIGQUIT`. HTTP200 chưa chứng minh app/JS hoạt động.
- Không ConfigMap, Secret hay runtime env FE; VITE lấy repo Variables lúc build.
  Restart giữ bundle cũ, đổi URL cần rebuild. Script bỏ toàn bộ discovery/Eureka
  preparation/check cho FE, vẫn kiểm digest/1 Pod Ready/không restart và rollback.
- Digest `state/k8s/frontend.current`; revision snapshots/current/previous dưới
  `state/k8s/frontend/`. Compose state `state/frontend.current/.previous` giữ riêng.
  Lỗi rollout trả nonzero dù restore thành công; deadline không tự rollback.

### Trình tự khi được phép ghi production

1. Read-only preflight ft84: node/capacity/requests/RAM thực, Compose FE digest và
   state, hai Ingress FE/static EndpointSlice, gate/lock, GHCR pull credentials
   (chỉ tồn tại/quyền). Lưu snapshot routing public và digest; không in env/token.
2. Khi có quyền Git/publish, dùng SHA thật đã kiểm với CD `publish_only=true`,
   `action=deploy`, **service=frontend** trên ref được duyệt; không build trên84.
   Kiểm multiarch/digest, SIGQUIT và bundle có app/auth HTTPS đúng.
3. Upload đúng release tree; khi được phép bootstrap, cài bản `deploy-service.sh`
   có guard FE vào `/opt/flash-ticket-app/deploy-service.sh` để caller Compose cũ
   dùng guard (workflow K8s không tự upload script Compose này).
   Gọi deploy script frontend mode `deploy` để seed
   state, không `recreate` trước current. Chưa sửa Ingress, giữ Compose phục vụ.
   Script tự giữ deploy.lock; không giữ cùng khóa ngoài khi gọi gây deadlock.
4. Chờ Ready1/1 đúng digest, không restart; kiểm Service selector/PodIP và GET qua
   ClusterIP: index có root, asset thật đúng MIME, SPA deep route, missing asset404.
5. Sau khi script trả, lấy deploy.lock cho đổi route, kiểm không CD cạnh tranh.
   Patch **chỉ hai refs FE** trong `frontend-https` và rule app-host của
   `domain-http-redirect`: `frontend-external:5173` → `frontend:5173`.
   Không apply toàn bộ HTTPS/Traefik/Keycloak trong lần chuyển này.
6. Kiểm TLS trusted, redirect, HTML/asset/deep route; `/api` vẫn Gateway, protected
   API vẫn từ chối không token, auth discovery không đổi. Đối chiếu backend refs,
   EndpointSlice PodIP và Pod access log để chứng minh request qua Pod, không Compose.
7. Đo đọc hữu hạn tối đa2 request/s bao trùm recreate cùng digest: timestamp,
   status/error/latency/content-type; quan sát cũ+mới/minReadySeconds/endpoints/RAM.
   Cuối cùng Ready1/1, không restart/OOM; báo số mẫu/lỗi, không gọi đó là SLA tuyệt đối.
8. Sau checks đạt, dưới deploy.lock stop **riêng** container FE Compose đã xác minh,
   giữ image/config (không compose down/volume). Chứng minh không còn Ingress refs
   tới FE external rồi delete riêng Service và EndpointSlice `frontend-external`.
   Xóa document nguồn không prune object live. Keycloak/Ngrok không đụng.
9. Kiểm public sau stop và cleanup; ghi SHA/digest/state/routes/kết quả vào plan.
   Không yêu cầu host port5173 còn listen khi FE đã dùng Pod network.

### Đường lui và giới hạn assets

- Trước cleanup: restore đúng hai refs từ snapshot về frontend-external, kiểm
  public; Compose vẫn chạy. Bootstrap chưa có previous K8s thì không có automatic
  K8s rollback; nếu bất định giữ cả hai workloads, không xóa namespace/Pod hàng loạt.
- Sau cleanup: dưới deploy.lock phục hồi Compose **trực tiếp**, dùng digest snapshot
  và `compose/frontend.yml` + `_common.yml` hiện có (không runtime env), kiểm host
  listener; restore đúng Service/EndpointSlice FE static từ snapshot, rồi đổi refs
  Ingress về cũ và kiểm public. Không gọi deploy-service.sh hoặc lách guard bằng
  override/xóa state. Không khởi động Java Compose, đổi Keycloak/Payment hay xóa dữ liệu.
- Rollback K8s bình thường dùng digest **previous** có snapshot qua script; diễn tập
  live chỉ với revision đã kiểm VITE HTTPS và quyền riêng, không cố deploy bản lỗi.
- Mỗi image chỉ giữ dist riêng. HTML A/chunk B trong rollout khác version hoặc tab
  cũ có thể chunk404; no-cache index/immutable assets không giải quyết version skew.
  Chưa có CDN/asset retention/session affinity. Kiểm assets theo từng index, không
  dùng root200 để khẳng định mọi SPA session zero-error.

### Kiểm chứng source ngày17/11/2026

Flow: registry runtime k8s → deploy script/render digest → Service/Deployment FE
→ rollout/Pod check → atomic current/previous; Ingress đổi riêng sau bootstrap.
Không có Java class/method hoặc annotation mới; Shop App Java không áp vào YAML/Bash.
Dùng lại `apply_revision`/`check_revision` và fixtures, không thêm framework/helper.

- PASS: HTTPS static contract, deploy fixtures backend+FE (deploy/recreate/rollback,
  failed rollout không advance state, validation và Compose guard), 15 discovery tests.
- PASS: routing fixtures/ShellCheck trong container Alpine, shell syntax và diff check.
- PASS: container hooks sáu backend/Eureka; nginx FE runtime stage thật với dist mẫu,
  UID10001/read-only/tmpfs/64Mi, HTML/SPA/assets/MIME/cache/404, SIGQUIT stop exit0.
  Đây không phải bundle VITE thật, test tải hoặc chứng minh continuity trên Traefik/K3s.
- Chưa chạy actionlint1.7.12/kubeconform0.8.0: host thiếu Go, lệnh Go trong container
  bị chặn quyền chạy code GitHub bên ngoài. Không bỏ qua hoặc ghi nhận là PASS.
- Chưa commit/push/publish/preflight/cutover production. CI trên GitHub chưa chạy
  cho tree này. Hai spec/plan local vẫn bị `/docs/java-workflow/` exclude, không force-add.

## Assistant: bootstrap riêng

- [Deployment](assistant-service/deployment.yaml): 1 replica, surge 1,
  `maxUnavailable: 0`, pod mới phải Ready 75 giây mới Available. Startup cần
  health local và Eureka `UP` đúng UID/IP/port. `preStop` đánh dấu `OUT_OF_SERVICE`
  rồi chờ 75 giây trước graceful shutdown. Không phải HA khi pod/node hỏng.
- [ConfigMap](assistant-service/configmap.yaml): port `8089`, Redis prefix
  `flash-ticket:assistant:k8s:v1`, proxy OpenAI-compatible. Gateway tiếp tục gọi
  `lb://assistant-service`, không thêm Service/Ingress cho Assistant.
  `ENV_ASSISTANT_SERVICE` chỉ gồm `REDIS_PASSWORD` và `ASSISTANT_MODEL_API_KEY`;
  không đưa giá trị thật vào manifest hay log.
  Redis dùng một instance standalone ở `ft84` qua `REDIS_HOST`/`REDIS_PORT`;
  không dùng Sentinel. RabbitMQ cũng giữ một broker hiện có ở `ft84`.
- **Preflight FT84:** Xác nhận Discovery StatefulSet có đúng một pod Ready và
  Service trỏ đúng pod đó; dừng nếu topology khác, không tự scale Discovery.
  Kiểm RAM/capacity cho pod surge và pod đang drain, GHCR pull, issuer, Redis
  private/auth và route Gateway đúng revision. Xác nhận hai ClusterIP Event:8086
  và Booking:8082 có EndpointSlice Ready; nếu thiếu, chỉ apply hai manifest Service
  hiện có sau khi được phép, không restart backend. Kiểm chính sách log/retention
  của proxy và upstream trước bootstrap: buyer có thể gửi dữ liệu thật ngay khi
  Assistant đăng ký Eureka. Không bootstrap bằng credential đã lộ/chưa được xoay vòng.
- **CD lần đầu:** Sau khi được phép deploy, chạy manual CD trên ref chứa manifest
  và registry với `action=deploy`, `service=assistant-service`, `publish_only=false`.
  Workflow dùng đúng digest đã verify/build và release cùng SHA; không dùng
  `restart`, `rollback`, `all`, `k8s` hay merge `main` để bootstrap. Chỉ đổi
  manifest sẽ ra `recreate`, cần digest đã chạy; tính matrix từ đúng `before`/`head`
  trước một lần merge sau này.
- **Nghiệm thu:** Kiểm 1 pod khỏe, đúng digest và Eureka `UP`; kiểm Gateway với
  BUYER test bằng GET read-only, không xác nhận đặt vé. Với rollout sau bootstrap,
  kiểm pod cũ phục vụ tới khi pod mới Available, rồi drain/terminate và không
  còn record cũ `UP`. Fixture không chứng minh continuity trên cluster thật.
- **Lần đầu thất bại:** Không có revision `previous`, không fallback Compose,
  không xóa Redis. Dừng để tìm nguyên nhân; chỉ dọn tài nguyên Assistant sau phê duyệt.

## Rollout giữ ứng dụng phục vụ trên node 84

Gateway và năm core dùng `RollingUpdate`, `maxUnavailable: 0`, `maxSurge: 1`,
`minReadySeconds: 75`. Policy tạm giữ một Pod/service, HPA core1/1;
chỉ thêm Pod surge tạm khi thay phiên bản. Pod mới phải đăng ký Eureka trước khi Ready.
Pod cũ chạy preStop: PUT status OUT_OF_SERVICE, chờ 75 giây để client bỏ địa chỉ,
rồi mới graceful shutdown. Không bật retry request ghi để che lỗi chuyển Pod.

Client mới fetch registry mỗi 5 giây và tắt cache LoadBalancer vì Eureka client
đã cache. Khoảng 75 giây còn bao phủ client cũ fetch 30 giây/cache LB 35 giây
trong lần chuyển đầu, sau khi hai peer đã tắt read-only response cache.
`maxSurge` không giới hạn cứng tổng số Pod kể cả các Pod đang Terminating;
cần tính cả RAM của Pod còn drain. Trước rollout mới script yêu cầu host còn
ít nhất 1536Mi; rollback không bị chặn bởi ngưỡng capacity này.

Pod cũ trước tính năng này chưa có preStop. Lần chuyển đầu script chỉ chấp nhận
một Pod legacy Ready/service và hai Eureka peer Ready; không ép HPA giảm replica.
Script thêm readiness gate tạm: đợi container mới sẵn sàng/đăng ký đủ hai peer,
đưa instance legacy OUT_OF_SERVICE trên cả hai peer, đợi 75 giây rồi mở gate.
Nếu script bị ngắt trước đó, Pod cũ vẫn được giữ, không dựa vào một timer tự hết.
Gateway còn đợi endpoint mới Ready, bỏ riêng nhãn phục vụ Service của Pod cũ và
chờ Traefik cập nhật. Sau đó script bỏ gate khỏi template, thêm một lượt rollout
để các Pod HPA sau này không phụ thuộc host controller. Nếu legacy đã scale 2, dừng rollout trước đổi
workload và chờ cửa sổ tải thấp; không chọn ngẫu nhiên Pod chưa drain để xóa.

Eureka dùng StatefulSet 2 Pod và headless Service. Peer URLs trỏ hai DNS cố định,
hostname theo POD_NAME; client tiếp tục dùng discovery-server:8761. Update từng
ordinal, chờ Ready/Available trước khi thay ordinal tiếp theo. Startup so local
registry với các instance UP trên peer còn phục vụ; readiness thường không phụ
thuộc peer để Pod sống sót vẫn phục vụ khi peer kia đang được thay. Startup
không bỏ qua đồng bộ khi peer lỗi DNS/HTTP. Cold start cả node dùng Parallel
và headless publishNotReadyAddresses: hai HTTP server có thể liên lạc trước
khi Pod Ready; hai registry rỗng vẫn qua probe để app đăng ký lại.

Lần đầu script tự chuyển Deployment Eureka cũ sang StatefulSet dưới deploy.lock:
snapshot tài nguyên, giữ client Service riêng Pod legacy, tạo hai peer có URL
bootstrap tới registry cũ, kiểm mọi app Ready đã có trên cả hai peer. Giữ client
trên legacy trong cả lượt gỡ URL tạm/rollout từng peer; kiểm lại đủ registry trên
cấu hình cuối rồi mới cutover Service. Chỉ xóa Deployment cũ sau khi
check cuối đạt. Khi lỗi trước hoàn tất, chỉ quay selector về Pod cũ sau khi xác
nhận registry cũ đủ dữ liệu; giữ cả workload nếu không thể khôi phục an toàn.

Mỗi lần so registry ghi `peer-sync-diagnostics.json` trong revision được bảo vệ:
peer Ready/restart, instance khớp/thiếu và lỗi truy vấn. Timeout giữ nguyên failure
và ghi nguyên nhân cuối; không hạ điều kiện để cho deploy qua. Image có label
`io.flashticket.runtime-tools=curl,jq` để nhận diện bộ công cụ probe/hook; đợt
khôi phục sau CD thất bại phải build đủ bảy image, vì sáu app còn lại chưa được
publish ở run lỗi, không apply probe curl/jq lên digest cũ thiếu công cụ.

Backup standalone nằm trong revision migration với prefix `legacy-`. Sau chuyển
thành công, baseline rollback mới giữ topology 2 peer và image có curl/jq; không
đưa image standalone cũ thiếu công cụ vào startup probe mới. Quay về standalone
là phục hồi topology từ backup trong cửa sổ vận hành riêng. Những lần image
rollback sau đó dùng snapshot StatefulSet bình thường. Script xử lý Pod lỗi của
StatefulSet chỉ sau khi restore template, chỉ xóa Pod chưa Ready/sai image và còn
một peer Ready; không xóa peer khỏe để thúc rollout.

Rollback core về revision trước tính năng vẫn khôi phục manifest/probes cũ,
kể cả strategy Recreate nếu snapshot cũ dùng nó; không cam kết rollback xuyên
mốc migration cũng không gián đoạn. Cần diễn tập trường hợp phù hợp trước dùng.

Phải nghiệm thu request liên tục qua Gateway trong lúc thay từng ứng dụng,
đối chiếu UID/image thực tế và thử đăng ký/hủy một instance chẩn đoán trên hai
Eureka peer để chứng minh replication. Fixture/schema/dry-run không thay thế
thử thật. Cấu hình này phục vụ cập nhật trên một máy, không chịu lỗi node 84.

Tham khảo: [Deployment strategy](https://kubernetes.io/docs/concepts/workloads/controllers/deployment/#strategy),
[Pod readiness gates](https://kubernetes.io/docs/concepts/workloads/pods/pod-lifecycle/#pod-readiness),
[Eureka peer awareness](https://docs.spring.io/spring-cloud-netflix/reference/spring-cloud-netflix.html).

## HPA cho năm service nghiệp vụ

Booking, Payment, Ticket, Event và User có HPA riêng; Gateway giữ một replica,
Eureka source hiện standalone một Pod. Cả năm HPA nghiệp vụ tạm min=max1;
Booking/Payment giữ PDBmin1, Event/Ticket giữ recovery-1/no-PDB.
Đây là cấu hình source, không khẳng định đã apply lên cluster. CPU trung bình mục tiêu
70% **CPU request**, tăng tối đa một Pod/60s, giảm tối đa một Pod/60s và giữ
khuyến nghị scale-down trong 300s. Ví dụ Booking request 200m nên mục tiêu 70%
tương ứng trung bình 140m/Pod, không phải 70% CPU toàn node.

Metrics-server cung cấp resource metrics hiện có. Chỉ dùng CPU ở đợt đầu;
heap JVM có thể giữ memory sau khi tải giảm nên không dùng memory làm điều kiện
scale-down. CPU HPA không đo được trực tiếp backlog RabbitMQ; đây chưa phải
queue autoscaling. HPA thêm Pod trên các node hiện có, không tự tạo thêm EC2.

Deployment core bỏ `spec.replicas` để HPA làm chủ số Pod; policy tạm dùng HPA1/1. Script deploy bỏ cả
giá trị replicas mà kubectl có thể default khi render, chuyển last-applied
ownership trước apply và snapshot HPA cùng revision. Rollback về revision có
HPA khôi phục policy cũ; rollback về revision **chưa có HPA** xóa HPA của service
đó và khôi phục số replica cố định trong revision. Đây là thao tác có chủ đích,
không dùng `kubectl scale` một lần để giữ số Pod khi HPA còn hoạt động.

Policy tạm không autoscale lên2: cả năm HPA1/1. RollingUpdate vẫn có surge tạm như phần trên.
Hai cơ chế độc lập, không làm hệ thống chịu được lỗi máy 84.

Nếu cả năm core đạt max 2, tổng request K8s ở cấu hình hiện tại khoảng 1700m CPU,
5388Mi memory, gồm Gateway/hai Eureka peer và system Pods có request, chưa cộng
surge/Terminating Pod khi rollout. DB/Redis/Rabbit/
Keycloak ngoài K8s vẫn dùng chung RAM của máy 84. Các request này không bảo đảm
đủ RAM khi tải cao; theo dõi node memory/eviction/Pending, giữ max 2 và đo lại
trước khi tăng giới hạn hoặc thêm HPA memory.

Rà code đã thấy cơ chế khóa/lease và state guard trong relay Booking, outbox
Event/Payment/Ticket, refund Payment và recovery User. Message/SMTP vẫn có thể
được gửi lại khi crash hoặc mất phản hồi; không khẳng định exactly-once. Thử HPA
bằng CPU có giới hạn thời gian và GET/health/Eureka, không tạo thanh toán thật,
purge queue hoặc thay topology để thử autoscaling.

```bash
kubectl -n flash-ticket get hpa
kubectl -n flash-ticket describe hpa booking-service
kubectl -n flash-ticket top pods
kubectl top nodes
```

Nguồn: [Kubernetes HPA](https://kubernetes.io/docs/concepts/workloads/autoscaling/horizontal-pod-autoscale/),
[mẫu autoscaling/v2](https://github.com/kubernetes/website/blob/main/content/en/examples/application/hpa/php-apache.yaml).

## Bootstrap/cutover: chưa tự động thực hiện

Để có image mới trước khi dừng Compose, CD có input `publish_only=true` cùng
`action=deploy`, `service=k8s`: source hiện chọn tám ứng dụng, bỏ
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
8. Callback VNPay dùng HTTPS domain:
   `https://flashticket.sondimid.cloud/api/v1/payments/vnpay/ipn` và `/return`.
   Đồng bộ ConfigMap Payment, env server và `ENV_PAYMENT_SERVICE`, rollout riêng
   Payment rồi cập nhật đăng ký IPN bên VNPay. Compose/script mới không tạo Ngrok;
   chỉ tháo container cũ sau khi provider đã đổi và callback/link cũ đã drain.
   Không gọi deploy Compose Payment đã migrate K3s. Xem
   [trình tự cutover và kiểm tra](../README.payment.md).

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

Giữ maintenance, đóng nhận giao dịch mới. Scale sáu Deployment ứng dụng và
StatefulSet discovery-server về 0 (hoặc Deployment Eureka nếu chưa migration),
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

Addon tùy chọn [Headlamp](../addons/headlamp/README.md) cung cấp giao diện quản lý
Kubernetes tại `http://52.64.237.84/headlamp/` qua Traefik cổng80; SSH/loopback là
cách dự phòng. HTTP tạm thời **không mã hóa token quản trị**, có nguy cơ bị chặn
lấy quyền `cluster-admin`; chưa cấu hình domain/HTTPS. Pod Headlamp dùng tài khoản
riêng không được cấp quyền admin. Thêm manifest không đồng nghĩa đã cài addon;
xem preflight, token ngắn hạn và cách thu hồi quyền trong runbook trước triển khai.

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
