# Spec: Queue RabbitMQ và bàn giao tích hợp backend FlashTicket

- Mã: `FT-MQ-v0.2`; ngày 2026-10-12; cập nhật sau yêu cầu research cách tách queue.
- Trạng thái: **REVIEW_READY — đề xuất kỹ thuật để review, chưa phê duyệt hoặc triển khai**.
- Phân loại: kiến trúc; soạn theo `superpowers:brainstorming`.
- Yêu cầu: đọc docs nghiệp vụ, xác định queue cần thiết và lý do, đặc tả setup Docker Compose RabbitMQ, tài liệu bàn giao tích hợp Spring Boot cho các service.
- Người dùng xác nhận trong phiên này: **bao gồm đồng bộ Keycloak → RabbitMQ → User Service**.
- Các tên queue, retry policy, image, resource limit và identity message dưới đây là đề xuất của spec; không ghi chúng thành quyết định người dùng đã chốt.
- Phạm vi lượt soạn: chỉ tài liệu. Các khối cấu hình là mẫu cho bước triển khai sau review; chưa tạo/sửa Compose, provider Keycloak hoặc mã Spring.

## 1. Mục tiêu và đầu ra

Backend có một bản thiết kế đủ cụ thể để biết service nào publish/consume gì, vì sao cần queue đó, cấu hình broker thế nào, xử lý lặp/lỗi ra sao và dùng tiêu chí nào nghiệm thu. Queue phục vụ luồng đã có trong nghiệp vụ; không thêm service hoặc Saga chỉ để sử dụng RabbitMQ.

Ba deliverable của task triển khai:

| Deliverable | Nội dung bắt buộc | Nơi dự kiến bàn giao |
| --- | --- | --- |
| D1 — Danh mục queue và lý do | Tám queue chính theo nhóm xử lý có owner; mapping routing key, producer/consumer; retry/DLQ; những tác vụ không cần queue; bảo đảm giao nhận | `docs/architecture/messaging/rabbitmq-topology.md` |
| D2 — Setup Docker Compose | Broker pin image, env mẫu không secret, volume/network, healthcheck, topology JSON, provisioning quyền, lệnh start/stop/kiểm tra | `deploy/docker-compose.rabbitmq.yml`, `deploy/rabbitmq/`, cập nhật `deploy/.env.example` và `deploy/README.md` |
| D3 — Bàn giao backend | Spring Boot 4.1.1/Java 21, env và Config Server, listener/publisher, outbox/inbox, retry/replay, bảng trách nhiệm, checklist tích hợp | `docs/architecture/messaging/spring-boot-handoff.md` |

Spec này chứa nội dung chuẩn cho cả ba deliverable. Việc tách thành các file trên, cấu hình broker chạy thật và tích hợp service là bước triển khai tiếp theo. Không coi chỉ có file Compose là đã nghiệm thu messaging đầu-cuối.

## 2. Căn cứ và thứ tự ưu tiên

| Nguồn trong repository | Điều rút ra cho queue |
| --- | --- |
| [B3 — Quy trình nghiệp vụ](../../domain/B3-business-processes.md) §2–5 | Mua vé, thu muộn/thừa, phát hành lỗi, hủy sự kiện, hoàn tiền, email và check-in có ý nghĩa khác nhau |
| [B4 — Bản đồ sự kiện miền](../../domain/B4-domain-event-map.md) §1, §4–8 | Sự kiện miền là đầu vào phân tích; **không phải mỗi dòng B4 phải có một queue** |
| [B5 — Bounded context](../../domain/B5-bounded-context-map.md), [B7 — Bất biến](../../domain/B7-aggregates-and-invariants.md) | Giữ ranh giới nghiệp vụ và bất biến tồn kho/giữ chỗ/tiền/vé khi xử lý lặp, lệch thứ tự |
| [B8 — Yêu cầu](../../domain/B8-requirements.md) §2 | Không bỏ các nhánh hoàn muộn/thừa và không coi lỗi gửi email là lỗi phát hành |
| [B11-C](../../architecture/B11-C-target-architecture.md), [ADR-002](../../adr/ADR-002-mot-saga-thu-tien-phat-hanh-bu-tru.md) | Năm service; một Saga do Payment điều phối; hủy event là phản ứng một chiều có retry |
| [B12](../../architecture/B12-data-ownership-and-schema.md), [Mongo baseline](../../architecture/data/mongodb/README.md) | SQL owner đã có outbox/inbox; User dùng MongoDB, unique `identitySubject`, không mặc nhiên có SQL inbox hoặc transaction nhiều document |
| [B13-v0.2](../../architecture/B13-api-and-event-contracts.md) §4–7 | Exchange `flash-ticket.v1`, 11 messageType, envelope, frozen snapshot và quyền xử lý |
| [B14-v0.2](../../architecture/B14-main-flow-sequences.md) §3–7 | Start qua Booking; thành công Ticket → Booking → Payment; worker expiry/refund/delivery; timeout không chứng minh phát hành thất bại |
| [B16](../../architecture/B16-observability-baseline.md) §3–6 | Correlation/trace qua outbox/consumer/retry, log đã lọc, quan sát queue và lỗi giao nhận |
| [Phân công](../../project/roles.md) | Sơn phụ trách Booking và đồng bộ Keycloak–RabbitMQ; Tuấn Payment/Ticket; Tuyến Event/User; Minh hạ tầng/review |
| [AWS runtime baseline](../../architecture/aws-runtime-baseline.md), [ADR-004](../../adr/ADR-004-bo-tri-hai-may.md) | Một broker trên transaction plane; không HA; phân biệt môi trường hiện tại và target hai máy |
| [Spec danh tính nội bộ](2026-10-10-shared-internal-service-identity-design.md) | Client `internal-service` dành cho HTTP S2S; không thay credential/ACL RabbitMQ |

**Trạng thái nguồn:** B13/B14 v0.2 vẫn `REVIEW_READY`; GOV-149/150 đã xác nhận hướng luồng nhưng các tên/DTO sửa là CANDIDATE. Spec này theo hướng mới nhất để tránh thiết kế ngược luồng; không tự nâng B13/B14 hoặc hợp đồng identity lên APPROVED. Nếu review thay tên message, đồng bộ bảng binding và contract trong cùng thay đổi.

**Hiện trạng mã/config đã khảo sát:** các service có `spring-boot-starter-amqp`, parent Boot `4.1.1`, Java `21`; Config Server đã có host/port/username/password RabbitMQ riêng trong năm file service. CodeGraph chỉ cho thấy application skeleton trong các vùng messaging được khảo sát, chưa có pipeline queue/Saga/Keycloak provider hoàn chỉnh. `services/keycloak-spi` hiện là skeleton Spring Boot, không phải bằng chứng provider Keycloak đã hoạt động. Repo chưa có Compose RabbitMQ trong `deploy`.

## 3. Kết quả research và lựa chọn đề xuất lại

### 3.1 Không có quy tắc chung một service = một queue

Tài liệu RabbitMQ mô tả queue, binding và consumer nhưng không quy định mỗi service phải có đúng một queue hoặc mỗi sự kiện miền phải thành queue. **Kết luận thiết kế của spec: queue nên đại diện một nhóm xử lý/subscription có nhu cầu vận hành riêng; service xác định owner.** Đây là suy luận từ các nguyên tắc dưới đây và luồng FlashTicket, không phải một tiêu chuẩn bắt buộc về số queue.

| Nguồn chính thức / nguồn gốc mẫu | Nội dung có bằng chứng | Ý nghĩa cho FlashTicket |
| --- | --- | --- |
| [RabbitMQ Routing](https://www.rabbitmq.com/tutorials/tutorial-four-java#multiple-bindings) | Cùng routing key có thể bind nhiều queue; mỗi queue phù hợp nhận một bản | Event hủy có ba subscription owner; không dùng một queue cancellation chung cho ba service |
| [RabbitMQ Consumers](https://www.rabbitmq.com/docs/consumers) | Consumer active của cùng queue chia lượt nhận message | Chỉ các instance/worker có cùng khả năng xử lý mới share một queue; broker không chọn listener theo Java DTO |
| [RabbitMQ Priority — alternatives](https://www.rabbitmq.com/docs/priority#before-adopting-priority-queues-consider-the-alternatives) | Tách queue là một phương án tránh head-of-line blocking; ưu tiên message vẫn chịu giới hạn prefetch | Không để phát vé/khởi tạo payment làm backlog che khuất cập nhật gate hủy hoặc phản hồi Saga |
| [Enterprise Integration Patterns — Datatype Channel](https://www.enterpriseintegrationpatterns.com/patterns/messaging/DatatypeChannel.html) | Kênh theo kiểu dữ liệu giúp receiver biết cách xử lý | Hợp đồng rõ là tiêu chí tách; không gom message chỉ vì cùng process service |
| [MassTransit — endpoint mapping](https://masstransit.massient.com/configuration#endpoint-mapping-strategies) | Khuyến nghị bắt đầu theo consumer; có thể gộp nhóm liên quan khi tương tự về tải/retry/tài nguyên; gộp toàn bộ consumer của service bị khuyến cáo hạn chế | Bằng chứng về cách tổ chức endpoint thực tế, không yêu cầu dùng MassTransit/.NET trong dự án Spring |

MassTransit dùng từ consumer theo **loại xử lý**, không phải mỗi instance ứng dụng một queue. Các tài liệu này không chứng minh cứ một messageType là tối ưu; cùng một Saga có thể nhận nhiều loại kết quả trên một endpoint. Một routing key mô tả điều đã xảy ra/lệnh cần xử lý; một queue mô tả bên nhận và nhóm công việc. Hai chiều này không đồng nhất.

### 3.2 Tiêu chí tách/gộp

Tách khi khác yêu cầu về độ trễ/độ quan trọng, external dependency, chi phí xử lý, concurrency/prefetch, retry, hoặc cần dừng/replay/scale độc lập. Gộp khi cùng owner, nhóm công việc liên quan chặt, transaction/dependencies và yêu cầu vận hành tương tự; mỗi worker của queue phải xử lý được toàn bộ type đã bind qua dispatcher.

Tên đề xuất: `flash-ticket.<owner>.<nhóm-xử-lý>.v1`. Prefix owner làm rõ trách nhiệm/ACL; nhóm xử lý quyết định ranh giới backlog. Không dùng tên producer làm owner của queue. Tách queue không tách service, không thêm Saga và không tự tạo thứ tự xử lý giữa các queue.

| Phương án | Đánh giá |
| --- | --- |
| A. Một queue cho toàn bộ message của một service — 4 main ở v0.1 | Đơn giản và hợp lệ với tải nhỏ, nhưng chỉ owner chưa đủ căn cứ gom: Payment start có network dependency khác phản hồi Saga; Ticket issue khác cập nhật gate; khó pause/tune riêng |
| **B. Nhóm xử lý có owner — 8 main, đề xuất v0.2** | Tách các workload khác nhau thấy được từ flow, gộp kết quả cùng vòng đời/Saga; có đủ cách ly ban đầu mà không tách cơ học từng messageType |
| C. Queue cho mỗi messageType × mỗi owner nhận — 15 main | Contract/cấu hình riêng rõ, nhưng tăng listener và queue phụ; tách các kết quả cùng Saga chưa có lợi ích bắt buộc trong phạm vi hiện tại |

Đổi đề xuất từ A sang B. Con số tám là lựa chọn cho FlashTicket theo ranh giới xử lý, **không phải con số chuẩn của RabbitMQ**. Bản v0.1 không sai semantics giao nhận, nhưng chưa phù hợp bằng B về cách ly công việc. Chính sách retry hiện giữ phương án TTL để giới hạn phạm vi cập nhật; số queue retry là quyết định độc lập với cách nhóm nghiệp vụ.

## 4. Danh mục queue chính và lý do

Vhost riêng theo môi trường; tên đề xuất cho local: `flash-ticket`. Mọi exchange/queue bên dưới nằm trong vhost này.

| Queue chính | Owner | Nhóm xử lý / lý do tách hoặc gộp |
| --- | --- | --- |
| `flash-ticket.booking.event-control.v1` | booking-service | Nhận sales snapshot và cancellation marker; transaction cập nhật cấu hình/gate cục bộ, không chờ xử lý giao dịch đơn |
| `flash-ticket.booking.order-lifecycle.v1` | booking-service | Acceptance, hoàn tất sau phát vé và kết quả lỗi cùng vòng đời đơn, cùng order lock/inbox/outbox; gộp tại một endpoint có dispatcher |
| `flash-ticket.payment.event-control.v1` | payment-service | Finance snapshot và cancellation marker; không để yêu cầu tạo payment hoặc backlog Saga chiếm listener cập nhật gate |
| `flash-ticket.payment.start.v1` | payment-service | Khởi tạo attempt có guard HTTP và tương tác provider; tune/pause độc lập để không giữ phản hồi Saga trong cùng backlog |
| `flash-ticket.payment.saga.v1` | payment-service | PaymentDecision, IssuanceResult, OrderCompleted cùng cập nhật Saga/confirmation/refund obligation; gộp các kết quả cùng tiến trình phối hợp |
| `flash-ticket.ticket.event-control.v1` | ticket-service | Access snapshot và cancellation marker; cập nhật quyền/window/gate độc lập với tải phát vé |
| `flash-ticket.ticket.issuance.v1` | ticket-service | Phát vé/QR, crypto, transaction tạo đủ tập vé và terminal fence; concurrency riêng, không chiếm listener control |
| `flash-ticket.user.identity.v1` | user-service | Ensure user tối thiểu; Mongo/Keycloak reconciliation độc lập với giao dịch tiền/vé |

Booking có hai queue, Payment ba, Ticket hai, User một. Event chỉ publish trong phạm vi này, **không cần queue nhận riêng**. Gateway, Eureka, Config Server, Redis, frontend và RCA không có queue nghiệp vụ trong spec. Nhiều instance của cùng nhóm xử lý share queue đó; các service khác nhau không share một queue nghiệp vụ.

`event-control` gộp config + cancellation vì consumer chỉ validate và commit snapshot/marker bằng transaction ngắn, còn hủy hàng loạt/hoàn/vô hiệu vé chạy bằng worker bền riêng. Không chạy batch dài hoặc chờ provider ngay trong control listener. Queue control vẫn có thể backlog khi config lớn/nhiều; thiết kế này không cam kết cancellation đến tức thời. Nếu phép thử cancellation propagation không đạt yêu cầu đã chốt, tách cancellation thành queue riêng cho từng owner (từ 8 lên 11 main), không chữa bằng một priority flag rồi bỏ kiểm độ trễ.

### 4.1 Exchange và binding chính

| Exchange | Type / thuộc tính | Mục đích |
| --- | --- | --- |
| `flash-ticket.v1` | topic, durable | Giữ đúng exchange nghiệp vụ B13 |
| `flash-ticket.identity.v1` | topic, durable | Tách hợp đồng đồng bộ danh tính mới khỏi B13 bán vé |
| `flash-ticket.retry.v1` | topic, durable | Chuyển một lần xử lý lỗi tới retry queue của đúng owner |
| `flash-ticket.retry-return.v1` | direct, durable, internal | Broker trả retry về đúng queue gốc, không broadcast lại |
| `flash-ticket.dlx.v1` | direct, durable, internal | Broker chuyển bản tin lỗi kỹ thuật vào DLQ của đúng owner |

Các exchange không auto-delete. Chỉ provisioner tạo exchange/binding. Không dùng wildcard `#` để một service nhận toàn bộ business messages.

| Routing key = messageType | Producer | Bind tới queue chính (bỏ prefix `flash-ticket.` và suffix `.v1` để dễ đọc) | Quy tắc xử lý bắt buộc |
| --- | --- | --- | --- |
| `event.sales-configured.v1` | Event | `booking.event-control` | Full snapshot có version; không reset tồn kho đang giữ/đã mua |
| `event.access-configured.v1` | Event | `ticket.event-control` | Lưu owner/window/status; bản cũ không mở lại event đã hủy |
| `event.finance-configured.v1` | Event | `payment.event-control` | Lưu phí nguồn; thiếu phí không default 0; marker hủy không mất |
| `event.cancelled.v1` | Event | `booking.event-control` + `payment.event-control` + `ticket.event-control` | Mỗi owner nhận một bản; commit marker trước fan-out công việc cục bộ |
| `payment.start-requested.v1` | Booking | `payment.start` | Sau freeze + outbox commit; cùng requestId chỉ một attempt; guard payable theo B13 |
| `booking.accept-payment.v1` | Payment | `booking.order-lifecycle` | Khóa đơn, deadline và chọn charge đúng một lần |
| `booking.payment-decision.v1` | Booking | `payment.saga` | ACCEPTED mới được phát lệnh vé; muộn/thừa/hủy xử lý theo B13/B14 |
| `ticket.issue.v1` | Payment | `ticket.issuance` | Snapshot bất biến + order unique + terminal fence |
| `ticket.issuance-result.v1` | Ticket | `booking.order-lifecycle` + `payment.saga` | Booking xử lý COMPLETED; Payment xử lý FAILED; nhánh còn lại validate rồi ACK, không đổi nghiệp vụ |
| `booking.order-completed.v1` | Booking | `payment.saga` | Chỉ message này đóng Saga thành công, sau Booking commit COMPLETED |
| `booking.issuance-result.v1` | Payment | `booking.order-lifecycle` | Chỉ FAILED; không dùng cho happy path |
| `identity.user-observed.v1` | Keycloak / reconciler | `user.identity`, qua exchange identity | Ensure tồn tại user tối thiểu; không cấp role hoặc đồng bộ password |

Tổng: **11 messageType nghiệp vụ B13, 14 binding nghiệp vụ**, thêm **1 messageType / 1 binding identity**. Một routing key có thể vào nhiều queue; một queue có thể nhận nhiều routing key.

### 4.2 Những queue không cần trong baseline

| Queue thường được nghĩ tới | Quyết định và căn cứ |
| --- | --- |
| `reservation.expired` / delayed order expiry | Không tạo. Booking quét DB theo expiresAt và phân xử bằng đồng hồ sau khóa; TTL broker không phải đồng hồ quyết định tồn kho. B14 §4 |
| `refund.requested` / `refund.process` / `refund.succeeded` | Không thêm message liên service mới. Payment có refund/refund_attempt và worker lưu bền; cùng charge một nghĩa vụ, query khi UNKNOWN. B14 §4–5 |
| `ticket.email` / notification-service | Không tạo trong baseline. Ticket có ticket_deliveries + worker; lỗi SMTP không làm Saga thất bại. B13 §6, B14 §6 |
| `inventory.release` giữa service | Không tạo. Inventory, reservation, promotion và order cùng Booking; release là transaction owner, tránh message cũ đụng reservation generation mới |
| `check-in` | Không tạo. Check-in cần trả kết quả trực tuyến, khóa và audit nguyên tử tại Ticket; queue không thay quyết định VALID→USED |
| `payment.ipn` công khai | Không mở broker cho VNPay/browser. IPN vào Payment HTTP, xác minh và lưu bền trước ACK theo hợp đồng provider; xử lý tiếp bằng outbox/Saga |
| Queue tìm kiếm, follow-notification, payout, analytics, RCA | Chưa có hợp đồng bắt buộc. Đối soát/đánh paid ở Payment, follow ở User, RCA đọc telemetry; không dựng thêm subsystem |
| Queue organizer role granted | Baseline B13 dùng workflow cục bộ User ↔ Keycloak + recovery; không thêm Saga/queue cấp role chỉ vì đã có RabbitMQ |

## 5. Lưu bền, retry và DLQ

### 5.1 Số lượng và thuộc tính queue

Với mỗi queue chính `Q` ở §4, tạo đúng bốn queue phụ:

| Tên | Vai trò | Thuộc tính khác biệt |
| --- | --- | --- |
| `Q.retry.5s` | Lỗi tạm thời lần 1 | Queue TTL `5000` ms, hết TTL trả về Q |
| `Q.retry.30s` | Lỗi tạm thời lần 2 | Queue TTL `30000` ms, hết TTL trả về Q |
| `Q.retry.300s` | Lỗi tạm thời lần 3 trở đi | Queue TTL `300000` ms, hết TTL trả về Q |
| `Q.dlq` | Cách ly lỗi schema/contract, lỗi không phân loại, hoặc delivery-limit | Không TTL, không tự replay hoặc tự purge |

**Tổng baseline TTL: 8 main + 24 retry + 8 DLQ = 40 queue vật lý.** Tám main là nhóm nhận nghiệp vụ/tích hợp; 32 queue phụ là cơ chế giao nhận. Không liệt kê retry/DLQ thành service hay workflow nghiệp vụ mới. Số queue phụ tăng là chi phí cụ thể của phương án TTL cần đo tài nguyên; không suy từ tám main rằng nghiệp vụ bắt buộc có 40 queue.

Ba tier TTL là lựa chọn kỹ thuật của baseline, không phải yêu cầu nghiệp vụ buộc phải có 24 retry queue. Đổi lại số queue lớn hơn, đường đi và backlog từng tier quan sát được riêng, dùng các cơ chế TTL/DLX tiêu chuẩn. [RabbitMQ 4.3 native delayed retry](https://www.rabbitmq.com/docs/quorum-queues#delayed-retry) có thể bỏ các queue TTL để còn 8 main + 8 DLQ; đây là phương án retry khác, cần review return/failure semantics, delivery-count và bộ phép thử trước khi chọn. Lượt research này thay grouping, chưa tự chuyển thuật toán retry hoặc bật đồng thời hai pipeline.

Tất cả queue durable, non-exclusive, không auto-delete, `x-queue-type=quorum`. Một broker, mỗi queue một member (`x-quorum-initial-group-size=1`); lựa chọn quorum phục vụ dead-letter transfer có confirm, **không mang lại HA với một node**. Message persistent (`delivery_mode=2`). Không đặt TTL lên main/DLQ, không dùng drop-head hoặc purge để xử lý backlog.

Giới hạn ban đầu để kiểm tải: `max-length-bytes=134217728` mỗi queue, `overflow=reject-publish`; đây là backpressure kỹ thuật có thể điều chỉnh, không phải ASR đã đo. Broker còn có disk/memory alarm; dung lượng volume phải tính cả WAL/overhead, không chỉ tổng payload. Bật theo dõi trước khi chạy tải.

### 5.2 Binding retry phải trả về đúng owner

Với mỗi Q, provisioner tạo:

1. Binding `flash-ticket.retry.v1` → `Q.retry.5s|30s|300s`, routing key chính bằng tên queue retry tương ứng.
2. Binding `flash-ticket.retry-return.v1` → Q, routing key Q.
3. Binding `flash-ticket.dlx.v1` → `Q.dlq`, routing key Q.
4. Policy main Q: DLX=`flash-ticket.dlx.v1`, dead-letter-routing-key=Q, dead-letter-strategy=`at-least-once`, overflow=`reject-publish`, delivery-limit=`20`, max-length-bytes như trên.
5. Policy mỗi retry: message-ttl theo tier; DLX=`flash-ticket.retry-return.v1`, dead-letter-routing-key=Q, dead-letter-strategy=`at-least-once`, overflow=`reject-publish`, delivery-limit=`20`, max-length-bytes như trên.
6. Policy DLQ: overflow=`reject-publish`, delivery-limit=`-1`, max-length-bytes như trên; không DLX. Không chạy consumer tự động trên DLQ.

Policy match tên queue bằng regex có escape dấu chấm và neo `^…$`, `apply-to=quorum_queues`. Mỗi queue có một policy đầy đủ, không trông chờ broker merge nhiều policy thường. Queue arguments chỉ giữ type/group-size; retry/DLX/limit dùng policy để có thể điều chỉnh. Dead-letter từ main/retry dùng at-least-once; target unavailable/full thì giữ tại nguồn và cảnh báo, không bỏ message. [RabbitMQ quorum/dead-letter](https://www.rabbitmq.com/docs/quorum-queues)

Ví dụ: retry `event.cancelled.v1` tại Booking phải về **chỉ `flash-ticket.booking.event-control.v1`** qua retry-return/Q, không publish lại `event.cancelled.v1` lên exchange business làm Payment/Ticket nhận thêm một vòng. Tương tự với `ticket.issuance-result.v1`: retry kết quả tại Booking chỉ về booking.order-lifecycle, không sang payment.saga hoặc queue control của Booking.

### 5.3 Thuật toán xử lý

- Thành công: commit inbox + hiệu ứng + response outbox cùng transaction rồi `basicAck(tag,false)`. Duplicate hợp lệ đã commit: ACK, không tạo hiệu ứng/response outbox lần hai.
- Lỗi nghiệp vụ có kết quả xác định: lưu kết quả từ chối/nhánh lỗi theo B13 trong transaction, phát response qua outbox nếu hợp đồng yêu cầu, rồi ACK. Ví dụ đơn hết hạn không phải poison message.
- Lỗi tạm thời đã phân loại, như DB unavailable/lock timeout/thiếu snapshot phụ thuộc: rollback; publish **raw body gốc** sang retry tier, persistent + mandatory + correlated confirm. Chỉ ACK bản gốc sau confirm ACK **và không có return**. Giữ messageId/body; chỉ tăng AMQP header `x-ft-retry-count`, lưu `x-ft-original-routing-key`, `x-ft-original-queue`, mã lỗi đã lọc.
- Retry count 0→5s, 1→30s, ≥2→300s. Không bỏ lỗi transient chỉ vì đạt N lần. Cảnh báo khi ≥3 lần hoặc trễ kéo dài; operator có thể cách ly có ghi nhận, nghĩa vụ nghiệp vụ vẫn tồn tại.
- Lỗi schema/type/hash khác với cùng messageId: rollback và reject `requeue=false` vào DLQ; phát cảnh báo. Không thay money/type để message được chấp nhận.
- Lỗi lập trình/không phân loại: DLQ + cảnh báo, tránh loop vô hạn. Delivery-limit 20 bảo vệ trường hợp crash/requeue lặp; vào DLQ là cần xử lý kỹ thuật, **không suy thành issuance FAILED/refund**.
- Retry publish lỗi/NACK/return/timeout: **không ACK bản gốc**. Suspend listener có backoff rồi đóng channel để broker redeliver; không tạo vòng `nack(requeue=true)` chạy nóng. Nếu crash sau retry confirm trước ACK, có thể có hai bản; inbox/business key hấp thụ.

Không dùng Spring retry in-memory đồng thời với pipeline trên. Không ACK trong `finally`. Không ACK `multiple=true` làm vô tình xác nhận những delivery chưa commit.

## 6. Hợp đồng và các luồng phải giữ

### 6.1 Envelope và serialization

Theo B13 §5.1: `messageId`, `messageType`, `schemaVersion=1`, `aggregateId`, `aggregateVersion` dạng chuỗi nguyên dương, `occurredAt` UTC, `correlationId`, `causationId`, `traceparent`, `data`. Không thêm field vào envelope v1 khi chưa cập nhật contract nguồn. JSON UTF-8, `content_type=application/json`; AMQP message_id phải khớp envelope.messageId, type khớp messageType. Lần publish business đầu có routing key khớp messageType; retry-return dùng Q nên consumer dispatch theo envelope và kiểm routing gốc đã lưu.

Wire contract là JSON có version, không Java serialization/Java class name. DTO kiểm đủ field, UUID, money/version chuỗi, unknown fields và các tổng snapshot theo B13. Không deserialize đa hình tùy ý theo `__TypeId__`; chọn DTO bằng registry messageType allowlist. Nếu dùng Jackson converter, phải cấu hình type mapping/validation rõ; `JacksonJsonMessageConverter` thuộc dòng Jackson 3 phù hợp Boot 4, không sao chép converter Jackson 2 từ ví dụ cũ. [Spring AMQP converters](https://docs.spring.io/spring-amqp/reference/amqp/message-converters.html)

Hash dedupe áp dụng trên envelope/payload canonical bất biến theo B13, không bao gồm retry counter, x-death hoặc trace header của một lần delivery. Inbox `consumer_name` xác định **logical subscription/handler**, ví dụ `booking.event-control`, `booking.order-lifecycle`, `payment.saga`; không chứa hostname/instance ID hoặc tên queue retry. Queue rename/retry không đổi consumer_name. Nếu tương lai cùng message có hai tác dụng độc lập trong cùng service, mỗi logical subscription có consumer_name riêng; không dedupe toàn service rồi vô tình bỏ một tác dụng.

### 6.2 Luồng mua thành công

```mermaid
sequenceDiagram
    participant B as Booking
    participant P as Payment
    participant T as Ticket
    B->>P: payment.start-requested.v1
    Note over P: Guard Booking qua HTTP; tạo attempt; IPN đã xác minh
    P->>B: booking.accept-payment.v1
    B->>P: booking.payment-decision.v1 (ACCEPTED)
    P->>T: ticket.issue.v1
    T->>B: ticket.issuance-result.v1 (COMPLETED)
    Note over B: Commit order COMPLETED + outbox
    B->>P: booking.order-completed.v1
    Note over P: Saga COMPLETED
```

Ticket result cũng được route sang Payment, nhưng Payment validate/ACK bản COMPLETED, không đóng Saga từ đó. Provider/HTTP/SMTP gọi ngoài transaction giữ khóa. Giao nhận at-least-once; hiệu ứng idempotent qua transaction, business unique key và fence, không hứa exactly-once toàn hệ thống.

### 6.3 Phát hành thất bại và hủy sự kiện

- Chỉ Ticket FAILED đã commit với terminal fence mới kích hoạt `TICKET_ISSUANCE_FAILED`: Ticket → Payment bằng issuance-result; Payment ghi refund và outbox `booking.issuance-result.v1` FAILED; Booking release đúng điều kiện. Timeout, DLQ hoặc hết retry không phải bằng chứng vé thất bại.
- Event hủy: Event commit CANCELLED + cancellationId + outbox; một publish routed tới ba queue. Mỗi owner commit marker trước batch xử lý bền. Message config cũ không xóa marker. Refund/void/release tiếp tục bằng trạng thái owner, không cursor trong RAM.
- Check-in có thể lọt trước khi marker hủy đến Ticket theo tradeoff B14/GOV-134/135; không tự undo USED. Broker không giải quyết độ trễ nhất quán này bằng FIFO toàn cục.

## 7. Đồng bộ Keycloak → User Service

### 7.1 Phạm vi tối thiểu và message mới

Mục tiêu: ensure có bản ghi `users` cho human user của realm FlashTicket khi đăng ký/đăng nhập. Không mirror role, password, token, email hoặc thông tin thanh toán; không tự cấp ORGANIZER/ADMIN. Nguồn quyền vẫn là Keycloak/JWT và workflow B13. B12 hiện không có email/roles trong users nên không thêm chúng qua payload.

Message mới `identity.user-observed.v1`, exchange `flash-ticket.identity.v1`, envelope cùng hình dạng §6, data đóng:

```json
{
  "realmId": "flash-ticket-realm-id",
  "identitySubject": "26c35a9f-9e05-461a-a7d7-8e4e24f769ac"
}
```

Đây là data minh họa; realmId thực lấy từ cấu hình realm theo môi trường, không đoán từ realm name. Consumer kiểm realmId bằng allowlist. `identitySubject` giữ nguyên `sub`, không dùng email/username. `aggregateId` là UUID xác định bằng UUIDv5 từ `realmId + ':' + identitySubject`, namespace chuẩn DNS `6ba7b810-9dad-11d1-80b4-00c04fd430c8`; đây chỉ là ID tích hợp, không thay subject. `aggregateVersion="1"` vì ensure-exists không biểu diễn lịch sử profile. Mỗi observation có messageId/correlationId UUID; causationId nullable, traceparent nullable theo B13.

Provider lọc realm và **REGISTER / LOGIN thành công**; bỏ login lỗi, realm master, client-credentials/service-account. Chụp dữ liệu immutable khi session còn hợp lệ; dùng transaction completion callback để chỉ enqueue sau commit thành công. Callback offer không chặn vào executor có giới hạn, worker mới gọi broker; không giữ KeycloakSession sang thread khác hoặc chờ confirm trên đường trả login. Hàng đợi RAM đầy/lỗi publish thì ghi tín hiệu mất observation đã lọc để reconciliation bù. Không gửi trước commit; broker lỗi không làm login thất bại. [Keycloak transaction completion](https://www.keycloak.org/docs-api/latest/javadocs/org/keycloak/events/EventListenerProvider.html)

**Bảo đảm giới hạn:** phát sau commit bằng SPI đơn thuần không atomic với RabbitMQ; có cửa sổ mất observation. Không gọi nó transactional outbox hoặc cam kết mọi event login đều được lưu. Để phục hồi sự tồn tại user, User Service chạy reconciliation định kỳ mặc định 5 phút, phân trang danh sách human users từ Keycloak bằng quyền đọc cần thiết, loại service accounts, publish cùng ensure-exists contract qua identity exchange. Lượt quét có thể chạy lại từ đầu, không tăng version chỉ vì scan lại; lỗi scan giữ trạng thái/cảnh báo và thử lại. Nếu yêu cầu audit mọi lần login hoặc đồng bộ profile/delete/role, cần hợp đồng và cơ chế lưu bền khác, ngoài spec này.

Reconciliation có thể tạo bản ghi tối thiểu cho human user được admin/import tạo dù chưa login. Đây là chủ ý để không phụ thuộc sự kiện quá khứ; bản ghi users không cấp quyền đăng nhập hoặc thay enabled/role của Keycloak. Không tự xóa user ứng dụng khi user biến mất khỏi một trang scan.

### 7.2 Idempotency trên MongoDB

User handler dùng atomic upsert theo `identitySubject` và unique index `uq_users_identity_subject`. `$setOnInsert` ghi `_id` UUID, identitySubject, createdAt/updatedAt BSON Date và version BSON integer/long `0`; không tự thêm field ngoài schema. Nếu đã tồn tại, không đổi profile/version/timestamp từ observation đến muộn. Hai upsert chạy đua gặp duplicate key thì đọc lại đúng subject, xác nhận tồn tại rồi coi thành công.

ACK sau Mongo acknowledged write thành công; connection/write timeout là kết quả chưa rõ, retry cùng observation hoặc observation mới vẫn chỉ một user. Mất ACK sau insert được hấp thụ bằng unique key. Không tạo collection inbox thứ năm hoặc giả định transaction nhiều document trên Mongo standalone. Nếu sau này một observation có nhiều hiệu ứng, thiết kế idempotency mới trước khi mở rộng handler.

Keycloak provider thật cần packaging theo Keycloak Event Listener SPI, transaction completion callback, lifecycle AMQP client và credential riêng. Không deploy Spring Boot fat JAR skeleton `services/keycloak-spi` vào thư mục providers rồi coi là SPI. Owner Sơn bàn giao producer này cùng phép thử fail-open/reconciliation; User owner Tuyến bàn giao consumer/job quét. [Keycloak Event Listener SPI](https://www.keycloak.org/docs/latest/server_development/#_events)

## 8. Đặc tả Docker Compose RabbitMQ

### 8.1 File và cấu hình đề xuất

File đích `deploy/docker-compose.rabbitmq.yml`; chạy từ `deploy`, dùng chung network `flash-ticket-infra` của PostgreSQL/Keycloak. Network phải có trước; có thể tạo độc lập khi chỉ chạy RabbitMQ. Không phụ thuộc PostgreSQL để start broker.

```yaml
services:
  rabbitmq:
    image: rabbitmq:4.3.6-management
    hostname: flash-ticket-rabbitmq
    restart: unless-stopped
    mem_limit: ${RABBITMQ_MEMORY_LIMIT:-1g}
    cpus: ${RABBITMQ_CPUS:-1.0}
    stop_grace_period: 60s
    environment:
      RABBITMQ_NODENAME: rabbit@flash-ticket-rabbitmq
      RABBITMQ_DEFAULT_USER: ${RABBITMQ_ADMIN_USER:?Set RABBITMQ_ADMIN_USER}
      RABBITMQ_DEFAULT_PASS: ${RABBITMQ_ADMIN_PASSWORD:?Set RABBITMQ_ADMIN_PASSWORD}
      RABBITMQ_DEFAULT_VHOST: flash-ticket
    ports:
      - "${RABBITMQ_AMQP_BIND_ADDRESS:-127.0.0.1}:${RABBITMQ_AMQP_PORT:-5672}:5672"
      - "127.0.0.1:${RABBITMQ_MANAGEMENT_PORT:-15672}:15672"
    volumes:
      - rabbitmq-data:/var/lib/rabbitmq
      - ./rabbitmq/rabbitmq.conf:/etc/rabbitmq/rabbitmq.conf:ro
      - ./rabbitmq/enabled_plugins:/etc/rabbitmq/enabled_plugins:ro
      - ./rabbitmq/definitions.json:/etc/rabbitmq/definitions.json:ro
    healthcheck:
      test: ["CMD", "rabbitmq-diagnostics", "-q", "check_running"]
      interval: 10s
      timeout: 5s
      retries: 6
      start_period: 45s
    networks:
      - ticket

volumes:
  rabbitmq-data:
    name: flash-ticket-rabbitmq-data

networks:
  ticket:
    external: true
    name: flash-ticket-infra
```

Tag `4.3.6-management` có trong [Docker Official Image](https://hub.docker.com/_/rabbitmq) khi tra cứu ngày soạn; đây là image ứng viên pin cho task, không lệnh nâng broker AWS đang có. Khi triển khai ghi digest thực vào evidence; đổi version/volume của broker có dữ liệu phải theo quy trình upgrade tương ứng.

Nội dung `deploy/rabbitmq/rabbitmq.conf`:

```ini
listeners.tcp.default = 5672
management.tcp.port = 15672
vm_memory_high_watermark.relative = 0.6
disk_free_limit.absolute = 1GB
prometheus.tcp.port = 15692
```

Nội dung `deploy/rabbitmq/enabled_plugins`:

```erlang
[rabbitmq_management,rabbitmq_prometheus].
```

Port metrics 15692 không publish ra host bằng mẫu local này. Collector/scraper trên cùng network dùng `rabbitmq:15692`; target hai máy cần đường private/ACL riêng theo AWS baseline và B16. Memory/CPU trên là ngân sách khởi đầu cần kiểm cùng tổng ngân sách máy, không chứng minh đã chịu tải.

### 8.2 Env và khởi tạo

Bổ sung có kiểm soát vào env example hiện có, không ghi secret thật:

```dotenv
RABBITMQ_ADMIN_USER=flash-ticket-admin
RABBITMQ_ADMIN_PASSWORD=
RABBITMQ_AMQP_BIND_ADDRESS=127.0.0.1
RABBITMQ_AMQP_PORT=5672
RABBITMQ_MANAGEMENT_PORT=15672
RABBITMQ_MEMORY_LIMIT=1g
RABBITMQ_CPUS=1.0
# Provisioner đọc các secret sau từ file ignored/secret store.
RABBITMQ_EVENT_PASSWORD=
RABBITMQ_BOOKING_PASSWORD=
RABBITMQ_PAYMENT_PASSWORD=
RABBITMQ_TICKET_PASSWORD=
RABBITMQ_USER_PASSWORD=
RABBITMQ_KEYCLOAK_PASSWORD=
```

Admin là tài khoản bootstrap/provisioning, không đưa vào env Spring hoặc provider. Các biến `RABBITMQ_*_PASSWORD` ở block này phục vụ provisioner, không tự được Spring ánh xạ theo tên service. Mỗi process Spring nhận `RABBITMQ_USERNAME` và `RABBITMQ_PASSWORD` của chính mình theo §9.

`definitions.json` chứa vhost, **5 exchange, 40 queue, 55 binding** (15 domain/identity + 24 retry ingress + 8 retry return + 8 DLQ), và **40 policy** đầy đủ theo §5.2 cho phương án TTL. Không chứa users/password/password_hash/permissions có secret. Provisioner quản lý account/ACL riêng. Mỗi exchange/queue entry có `vhost: "flash-ticket"`; bindings có destination_type=`queue`, arguments rỗng; mỗi queue có durable=true, auto_delete=false, arguments type/group-size theo §5.1.

Topology JSON phải được sinh/soát từ một manifest để tránh danh mục docs và binding thực lệch nhau; không dùng Spring tự tạo topology thứ hai. File JSON commit được vì không có credential. Ví dụ một policy main (các tên khác mở rộng đúng bảng §4):

```json
{
  "vhost": "flash-ticket",
  "name": "booking-order-lifecycle-main-v1",
  "pattern": "^flash-ticket\\.booking\\.order-lifecycle\\.v1$",
  "apply-to": "quorum_queues",
  "priority": 10,
  "definition": {
    "dead-letter-exchange": "flash-ticket.dlx.v1",
    "dead-letter-routing-key": "flash-ticket.booking.order-lifecycle.v1",
    "dead-letter-strategy": "at-least-once",
    "overflow": "reject-publish",
    "delivery-limit": 20,
    "max-length-bytes": 134217728
  }
}
```

**Import sau khi broker boot**, không cấu hình boot-time definitions import trong mẫu này. Như vậy default admin/vhost được tạo trước trên volume mới, rồi mới import topology không chứa users. Boot-time import trên node trống có quy tắc khác về việc tạo default user, cần tránh trộn hai cơ chế. [RabbitMQ definition import](https://www.rabbitmq.com/docs/definitions)

Lệnh bàn giao dự kiến, chỉ chạy sau khi các file D2 đã hiện thực:

```powershell
# Từ deploy; nếu network chưa tồn tại mới tạo bằng lệnh bên dưới.
docker network inspect flash-ticket-infra
# Chỉ chạy khi inspect cho biết network chưa tồn tại:
# docker network create flash-ticket-infra

docker compose --env-file .env.dev -f docker-compose.rabbitmq.yml config --quiet
docker compose --env-file .env.dev -f docker-compose.rabbitmq.yml up -d --wait
docker compose --env-file .env.dev -f docker-compose.rabbitmq.yml exec -T rabbitmq rabbitmqctl import_definitions /etc/rabbitmq/definitions.json
pwsh -NoProfile -File rabbitmq/provision.ps1 -EnvFile .env.dev
docker compose --env-file .env.dev -f docker-compose.rabbitmq.yml exec -T rabbitmq rabbitmqctl list_queues -p flash-ticket name type durable messages_ready messages_unacknowledged
```

`provision.ps1` là deliverable D2 cần viết sau duyệt: đọc env mà không in secret, gọi Management API qua loopback, tạo/cập nhật sáu application accounts, resource/topic permissions theo §8.3; chạy lại cho cùng trạng thái, trả lỗi nếu thiếu secret hoặc ACL không khớp. Không truyền secret qua command-line/log, không dùng `set_permissions .* .* .*` cho application. Hoàn tất provisioning và kiểm bindings trước khi bật service publishers/listeners.

Management local: `http://localhost:15672`. JVM local: `127.0.0.1:5672`; app trong network: `rabbitmq:5672`; vhost=`flash-ticket`. Không dùng `localhost` để app container gọi container khác. Start/restart/stop giữ nguyên data volume và nodename; không có bước `down -v` trong runbook thường ngày. Env default user/password chỉ bootstrap volume mới, không đổi password đã tồn tại; rotation dùng provisioner/API và refresh connection.

Mẫu Compose là **local development**. Với môi trường shared/AWS, dùng endpoint theo runtime baseline, AMQPS với kiểm certificate/hostname và credential riêng, management/metrics chỉ qua đường quản trị/private. Không áp bind loopback local lên ứng dụng ở máy khác, và không mở `0.0.0.0:15672` để chữa lỗi kết nối. Cơ chế network/TLS cụ thể ghi trong run manifest trước tích hợp hai máy.

### 8.3 Ma trận quyền broker

| Principal | Business/identity routing key được publish | Queue được consume |
| --- | --- | --- |
| `ft-event` | `event.sales-configured.v1`, `event.access-configured.v1`, `event.finance-configured.v1`, `event.cancelled.v1` | Không |
| `ft-booking` | `payment.start-requested.v1`, `booking.payment-decision.v1`, `booking.order-completed.v1` | `flash-ticket.booking.event-control.v1`, `flash-ticket.booking.order-lifecycle.v1` |
| `ft-payment` | `booking.accept-payment.v1`, `ticket.issue.v1`, `booking.issuance-result.v1` | `flash-ticket.payment.event-control.v1`, `flash-ticket.payment.start.v1`, `flash-ticket.payment.saga.v1` |
| `ft-ticket` | `ticket.issuance-result.v1` | `flash-ticket.ticket.event-control.v1`, `flash-ticket.ticket.issuance.v1` |
| `ft-user` | Chỉ `identity.user-observed.v1` trên identity exchange cho reconciliation | `flash-ticket.user.identity.v1` |
| `ft-keycloak` | Chỉ `identity.user-observed.v1` trên identity exchange | Không |

Application configure permission=`^$`; read regex là allowlist đúng các queue main sở hữu (producer-only read=`^$`). Write regex chỉ business/identity exchange có quyền và retry exchange nếu có consumer. Mọi regex neo đầu/cuối và escape dấu chấm; không cấp write default exchange hoặc retry-return/DLX internal. Runtime không được tạo queue/bind, đọc DLQ của owner khác hoặc consume retry queue.

**Resource permissions không đủ phân biệt routing key trong một exchange.** Provisioner phải đặt thêm **topic permissions** cho business, identity và retry exchanges. Write pattern business/identity từ allowlist trên; retry write pattern là allowlist ba tier của từng queue main owner sở hữu (Booking 6 key, Payment 9, Ticket 6, User 3). Đặt deny topic pattern cho exchange ứng dụng không có quyền; không trông chờ topic authorization bật mặc định. Quyền read queue được kiểm riêng, topic read pattern không thay nó. [RabbitMQ access control](https://www.rabbitmq.com/docs/access-control)

Publisher set AMQP `user_id` bằng broker username của connection để broker kiểm người gửi. Consumer ở business/identity ingress kiểm user_id theo allowlist producer ở bảng. Retry giữ nguồn gốc vào `x-ft-origin-user` rồi set user_id thành username owner đang republish; consumer chỉ tin origin header trên đường retry-return và user_id của chính owner, hoặc replay do provisioner được kiểm soát. Phân quyền retry ingress ngăn service khác gửi vào đường retry của owner. Header/source check không thay broker ACL; credential owner bị lộ vẫn cho phép giả mạo trong quyền của owner đó.

## 9. Tài liệu bàn giao Spring Boot

### 9.1 Dependencies và Config Server

Năm service đã có starter AMQP; không thêm version client/Jackson riêng trái dependency management Boot `4.1.1`. Java 21. Maven dependency chuẩn khi cần đối chiếu:

```xml
<dependency>
  <groupId>org.springframework.boot</groupId>
  <artifactId>spring-boot-starter-amqp</artifactId>
</dependency>
```

Giữ host/port/username/password ở các file Config Server service hiện có để không mất placeholder riêng. Bổ sung cấu hình chung vào `infrastructure/config-server/src/main/resources/config/application.yaml`, merge vào cây spring hiện có; **không tạo hai key spring ngang cấp**:

```yaml
spring:
  rabbitmq:
    virtual-host: ${RABBITMQ_VHOST:flash-ticket}
    dynamic: false
    connection-timeout: 5s
    requested-heartbeat: 30s
    publisher-confirm-type: correlated
    publisher-returns: true
    template:
      mandatory: true
      observation-enabled: true
      retry:
        enabled: false
    listener:
      type: simple
      simple:
        acknowledge-mode: manual
        default-requeue-rejected: false
        missing-queues-fatal: true
        concurrency: 1
        max-concurrency: 2
        prefetch: 10
        observation-enabled: true
        retry:
          enabled: false
```

`dynamic=false` để không dùng RabbitAdmin auto-declare; listener tham chiếu queue đã provision, không `queuesToDeclare` hoặc bean khai topology. `default-requeue-rejected=false` là fallback; manual listener vẫn phải phân loại/ACK/reject đúng §5.3. Concurrency/prefetch là giá trị bắt đầu, đo trước khi tăng; không bảo đảm order processing tuần tự theo aggregate.

Env của từng process, ví dụ Booking local:

```dotenv
RABBITMQ_HOST=127.0.0.1
RABBITMQ_PORT=5672
RABBITMQ_VHOST=flash-ticket
RABBITMQ_USERNAME=ft-booking
RABBITMQ_PASSWORD=
FLASH_TICKET_EVENT_CONTROL_QUEUE=flash-ticket.booking.event-control.v1
FLASH_TICKET_ORDER_LIFECYCLE_QUEUE=flash-ticket.booking.order-lifecycle.v1
```

Service property của Booking để hai listener dùng tên ổn định:

```yaml
flash-ticket:
  messaging:
    queues:
      event-control: ${FLASH_TICKET_EVENT_CONTROL_QUEUE}
      order-lifecycle: ${FLASH_TICKET_ORDER_LIFECYCLE_QUEUE}
```

Payment dùng các property queues.event-control/start/saga; Ticket dùng event-control/issuance; User dùng identity; mỗi tên queue đúng §4, cấu hình trong file service tương ứng. Event không có listener nên không cần property queue; không tạo listener rỗng để nhận lại event do mình phát. Mọi secret resolve ở process/service environment, không copy file admin env vào container Spring. Cấu hình remote còn cần `spring.rabbitmq.ssl.enabled`, trust material và port phù hợp; không tắt certificate/hostname validation. [Spring Boot AMQP](https://docs.spring.io/spring-boot/reference/messaging/amqp.html), [property reference](https://docs.spring.io/spring-boot/appendix/application-properties/index.html)

**Cách ly queue phải đi cùng cách ly listener.** Tạo một `@RabbitListener`/listener container riêng cho từng nhóm; không đặt toàn bộ queue của service trong cùng một annotation rồi dùng chung capacity. Các factory kế thừa Boot config bằng `SimpleRabbitListenerContainerFactoryConfigurer`, sau đó chỉnh prefetch/concurrency cho nhóm cụ thể, vẫn giữ manual ACK/observation/error boundary. Không dùng một executor giới hạn chung khiến start/issuance chiếm hết thread của control/Saga; DB connection pool và tài nguyên cùng service vẫn có thể là điểm tranh chấp cần đo.

| Nhóm endpoint | Concurrency khởi đầu / tối đa | Prefetch khởi đầu | Ý nghĩa |
| --- | --- | --- | --- |
| event-control của mỗi owner | 1 / 1 | 1 | Commit marker/snapshot ngắn; không claim batch hoặc chờ mạng ở listener |
| booking.order-lifecycle, payment.saga | 1 / 2 | 10 | Xử lý state/response cục bộ; scale có inbox/aggregate lock |
| payment.start, ticket.issuance | 1 / 2 | 1 | Hạn chế giữ nhiều tác vụ có network/CPU/transaction nặng trước khi xử lý |
| user.identity | 1 / 2 | 10 | Atomic ensure/upsert; tách khỏi worker reconciliation |

Đây là điểm bắt đầu để kiểm thử, không cam kết throughput hoặc priority cứng. Dùng chung một queue không tạo transaction xuyên message, tách queue cũng không bảo đảm thứ tự giữa control và command; version/marker/guard/fence của B13/B14 vẫn bắt buộc.

### 9.2 Publisher và outbox

Event/Booking/Payment/Ticket ghi state + outbox cùng SQL transaction. Relay claim bản chưa published bằng lease/row lock ngắn; kết thúc transaction claim **trước** chờ mạng. Publish JSON envelope dựng từ cột outbox + payload, không lặp hai nguồn envelope trái nhau. Giữ messageId/data khi retry. SQL relay đánh published_at bằng transaction mới chỉ khi broker confirm ACK và không return. NACK, timeout, channel close, NO_ROUTE giữ pending và backoff; crash giữa confirm/mark-published gây duplicate là bình thường.

Claim có thể dùng cột B12 sẵn có: chọn due row với `FOR UPDATE SKIP LOCKED`, tăng attempts và đẩy next_attempt_at tới hết lease rồi commit. Worker chết trước send thì row lại due sau lease; lease hết khi send còn chạy có thể gây publish lặp nhưng không mất message. Không thêm claimed_until/worker_id hoặc giữ row lock qua network nếu chưa có thay đổi schema được review.

Mẫu transport Java minh họa API confirm cho **raw JSON đã validate**; không phải mã relay/DAO hoàn chỉnh:

```java
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageDeliveryMode;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

final class ConfirmedPublisher {
  private final RabbitTemplate template;

  ConfirmedPublisher(RabbitTemplate template) {
    this.template = template;
  }

  void send(String exchange, String routingKey, Message rawJson) throws Exception {
    rawJson.getMessageProperties().setDeliveryMode(MessageDeliveryMode.PERSISTENT);
    var id = rawJson.getMessageProperties().getMessageId();
    var receipt = new CorrelationData(id + ":" + UUID.randomUUID());
    template.send(exchange, routingKey, rawJson, receipt);
    var confirm = receipt.getFuture().get(10, TimeUnit.SECONDS);
    if (!confirm.isAck() || receipt.getReturned() != null) {
      throw new IllegalStateException("AMQP_PUBLISH_NOT_CONFIRMED_OR_UNROUTABLE");
    }
  }
}
```

Caller phải set contentType, messageId, type, userId và trace headers theo §6/§8.3 trước send. Unique CorrelationData ID cho từng lần send, độc lập logical messageId. Timeout không chứng minh chưa gửi; retry dùng cùng logical ID. Có thể triển khai async thay cho chờ future, nhưng giữ cùng điều kiện mark-published. Không gọi `sendAndReceive` để dựng Saga RPC. [Spring AMQP confirms/returns](https://docs.spring.io/spring-amqp/reference/4.0/amqp/template.html)

**Giới hạn quan trọng:** mandatory/return chỉ phát hiện không route tới queue nào, không chứng minh đã đủ ba binding cancellation hoặc hai binding issuance-result. Do đó kiểm topology/binding đủ trước publisher startup và trong vận hành là điều kiện nghiệm thu bắt buộc; confirm không thay kiểm broadcast completeness. Không xóa binding tùy ý khi vẫn đang publish.

### 9.3 Consumer, transaction và xử lý lỗi

Listener nhận `org.springframework.amqp.core.Message` và `com.rabbitmq.client.Channel`. Ví dụ Booking có hai endpoint: `@RabbitListener(id="booking-event-control", queues="${flash-ticket.messaging.queues.event-control}", containerFactory="controlListenerFactory")` và `@RabbitListener(id="booking-order-lifecycle", queues="${flash-ticket.messaging.queues.order-lifecycle}", containerFactory="orderLifecycleListenerFactory")`. Mỗi endpoint có method/dispatcher riêng và chỉ xử lý key đã bind ở §4.1. Đọc raw bytes để kiểm JSON/source, không tin Java type header hoặc để broker tự chọn listener theo DTO.

Trình tự cho SQL owner:

1. Validate envelope/source/DTO; restore correlation và trace context.
2. Gọi **bean handler khác** có transaction proxy; không self-invoke phương thức `@Transactional` trong cùng listener bean.
3. Trong transaction: insert/khóa inbox `(consumer_name,message_id)`, so canonical hash, khóa aggregate theo B14, ghi hiệu ứng + response outbox + response_payload. Concurrent duplicate phải đợi/đọc bản committed; không coi row chưa commit là đã thành công.
4. Handler trả về sau transaction commit thành công, listener ACK một delivery. Transaction rollback thì đi nhánh lỗi §5.3; không ghi inbox thành công trước hiệu ứng.
5. Sai hash với cùng ID vào DLQ, giữ bản ghi inbox cũ; replay hợp lệ không ghi thêm response outbox. MessageType không dành cho queue này vào DLQ thay vì bỏ qua âm thầm. Ngoại lệ có chủ ý là hai nhánh issuance-result đã quy định tại §4.1.

Đối với User, thay bước SQL inbox/transaction bằng atomic ensure/upsert ở §7.2. Không copy JPA transaction sang Mongo handler rồi coi hai collection atomic.

Listener phải có retry transport/error boundary theo §5.3; đưa vào tài liệu handoff cả cách pause/resume container khi retry publish thất bại. Chỉ đặt manual ACK trong YAML hoặc thêm `@RabbitListener` chưa hoàn tất tích hợp.

### 9.4 Bảng bàn giao theo service

| Thành phần / người phụ trách theo docs | Phải hiện thực | Bằng chứng bàn giao |
| --- | --- | --- |
| Hạ tầng / Minh | Compose/topology/provisioner/ACL, metric broker, runbook restart và replay | 40 queue/55 binding/40 policy với TTL; phép thử deny quyền; restart giữ data; ghi resource footprint thực |
| Event / Tuyến | 4 event contracts, versioned snapshots, outbox relay, cancellation atomic | Một cancellation tới đủ 3 owner; snapshot đảo thứ tự không hồi sinh |
| Booking / Sơn | Consumer 5 key, freeze/start, accept, completed/failed, inbox/outbox; expiry worker | Giữ chỗ/deadline/release một lần; success Ticket→Booking→Payment |
| Payment / Tuấn | Consumer 6 key, IPN đã xác minh, Saga/outbox/inbox, refund worker | Late/duplicate refund; Ticket COMPLETED không đóng Saga; completion Booking đóng Saga |
| Ticket / Tuấn | Consumer 3 key, issue/fence/result, snapshot/access gate, delivery worker | Đủ vé mới COMPLETED; terminal FAILED không bị lệnh cũ đảo; SMTP lỗi không refund |
| User / Tuyến | Identity consumer/upsert và reconciliation qua Keycloak Admin API | Không duplicate subject; observation mất được quét bù; không sync role/email |
| Keycloak / Sơn | Provider REGISTER/LOGIN after-commit, lọc realm/service accounts, fail-open | Broker down không chặn login; rollback Keycloak không phát observation thành công |

Số key consumer bao gồm broadcast và issuance-result cả hai nhánh: Booking=5, Payment=6, Ticket=3, User=1. Quyền Admin API read cho reconciler dùng credential được quản lý cho User/Keycloak Admin API, **không suy từ token HTTP `internal-service` rằng đã có quyền đọc users**. Việc xác minh quyền đó thuộc tích hợp identity, không đổi grant/role business trong spec queue.

## 10. Quan sát, replay và vận hành

Từ lát cắt đầu tiên, giữ correlationId=orderId cho mua vé, messageId bất biến và causation theo B13/B16. Event cancellation giữ correlation thao tác hủy; khi batch xử lý từng order giữ correlation đơn và liên kết cancellation. Restore context từ outbox/body, không phụ thuộc MDC của request đã kết thúc. Observation enable trong Spring chỉ là phần cấu hình; cần instrumentation/tracing backend và bằng chứng context thực, tránh agent + thư viện tạo hai bộ span trùng.

Theo dõi tối thiểu: queue ready/unacked, số consumer, tuổi message/outbox pending, publisher NACK/return/timeout, inbox duplicate/hash-conflict, retry count/age, DLQ depth, cancellation propagation lag, Saga chờ và nghĩa vụ refund; broker CPU/RAM/disk alarm/restart. Trace/message/order IDs nằm trong structured log, không làm metric labels. Không log payload, email, JWT, credential, raw QR hoặc raw IPN. DLQ chứa payload nghiệp vụ nên chỉ owner/operator có quyền đọc; không chụp UI payload vào báo cáo.

Ngưỡng vận hành khởi đầu: DLQ >0 hoặc missing expected binding/consumer phải cảnh báo; retry từ lần 3 hoặc outbox pending >60s phát tín hiệu để điều tra. Giá trị này không thay deadline nghiệp vụ/SLA và có thể chỉnh sau đo tải. Không xóa inbox/outbox/DLQ theo lịch tùy tiện trước khi xác định cửa sổ replay/audit của dự án.

Runbook replay:

1. Xác định Q, messageId, mã lỗi, outbox/inbox và trạng thái nghiệp vụ; không suy có lỗi tiền chỉ vì DLQ tăng.
2. Sửa nguyên nhân config/handler/topology. Thử trên fixture trước; không sửa raw message cùng ID thành nội dung khác.
3. Operator lấy bản tin DLQ bằng manual ACK, publish raw body gốc về **đường retry owner** (có confirm/mandatory, nguồn gốc được kiểm theo §8.3), rồi mới ACK DLQ. Không republish business broadcast.
4. Giữ logical ID/correlation/body, tăng metadata replay riêng. Nếu body sai contract không thể xử lý, lưu evidence, yêu cầu producer tạo correction message có ID mới và causation tham chiếu bản lỗi sau rà trạng thái; không thay payload của ID cũ.
5. Kiểm hiệu ứng/inbox/Saga và count; chưa khớp thì giữ case điều tra. Không bulk purge để dashboard trở về 0.

Broker restart giữ volume chỉ bảo vệ dữ liệu đã lưu trên volume còn nguyên. Mất volume/máy là rủi ro được công bố; không có node dự phòng. Có runbook backup/restore data và secret riêng trước demo. Boot healthcheck không thay kiểm topology/permissions, còn queue tồn tại không chứng minh consumer đã xử lý.

Đối chiếu [R0 §3](../../research-rca/R0-boi-canh-va-rang-buoc.md#3-ràng-buộc-gửi-tới-các-gate-hạ-nguồn--phần-chống-cản-trở) qua [cửa liên kết RCA](../../project/lien-ket-rca.md): spec giữ định danh service, trace/correlation qua async/retry và tín hiệu lỗi theo B16. Span links/retention/khả năng dựng đồ thị thực vẫn cần bằng chứng runtime; không tuyên bố đã đạt R0 hoặc sinh API/Saga mới từ yêu cầu RCA.

## 11. Tiêu chí nghiệm thu

Tách kết quả tài liệu, broker và ứng dụng; mỗi phép thử ghi môi trường, image/digest, input tổng hợp, kết quả state/message/correlation và người chạy.

| ID | Phép thử | Điều kiện đạt |
| --- | --- | --- |
| AC-01 | Review danh mục/nguồn | Đủ 11 B13 messageType + identity; lý do 8 main theo nhóm xử lý/owner; phân biệt nguyên tắc nguồn và suy luận cho FlashTicket; không thêm worker queue vô căn cứ |
| AC-02 | Compose config và boot volume mới | Resolve env hợp lệ; secret thiếu fail rõ; broker healthy, admin/vhost có, image/resource/network đúng |
| AC-03 | Provision topology hai lần | Cả hai lần thành công, đúng 5 exchange/40 queue/55 binding/40 policy theo TTL, không duplicate; type/TTL/DLX/limits đúng; ghi footprint so với budget 1 GiB |
| AC-04 | ACL âm/dương | Mỗi principal publish/consume đúng bảng; ft-event giả `ticket.issue.v1` bị từ chối; app không consume queue owner khác hoặc publish default/internal exchange |
| AC-05 | Routing từng key | 12 key đến đúng queue; cancellation có 3 bản, issuance-result có 2; xóa một binding trong môi trường test phát hiện thiếu dù publisher có thể ACK |
| AC-06 | Restart có pending message | Persistent message còn sau restart cùng volume/nodename; consumer quay lại nhận và xử lý một hiệu ứng |
| AC-07 | Publish unroutable/NACK/timeout | Không mark outbox published khi return/NACK/unknown; retry cùng messageId, không tạo charge/vé mới |
| AC-08 | Crash tại ranh giới | Crash sau DB commit trước ACK, sau broker confirm trước published_at, sau retry confirm trước ACK: redelivery có thể lặp, business effect một lần |
| AC-09 | Retry isolation và backoff | Hai message broadcast chỉ retry owner lỗi; tiers khoảng ≥5/30/300s, không exact-time guarantee; không hot requeue; lỗi transient kéo dài không bị bỏ |
| AC-10 | DLQ/failure transfer | Schema/hash-conflict vào đúng DLQ, business không đổi; DLQ đầy/unavailable giữ tại source với at-least-once DLX; replay sau sửa không nhân hiệu ứng |
| AC-11 | Happy path | Start qua Booking; Ticket COMPLETED→Booking COMPLETED→Payment Saga COMPLETED; chặn/làm chậm OrderCompleted khiến Saga chưa hoàn tất |
| AC-12 | Nhánh tiền/vé | Late/duplicate charge hoàn đúng một nghĩa vụ/charge; terminal issuance FAILED mới bù trừ; timeout/DLQ/SMTP lỗi không tự refund |
| AC-13 | Hủy và config lệch thứ tự | Ba marker hội tụ; cũ không mở lại CANCELLED; scan/refund/release retry không lặp tác dụng; công bố độ trễ B14 |
| AC-14 | Identity dedupe/phục hồi | REGISTER/LOGIN thành công ensure một users/subject; lặp/đảo thứ tự không ghi đè; broker down login vẫn được; bỏ observation rồi reconciliation tạo bù; bỏ master/service-account |
| AC-15 | Trace/log/metric | Nối được một lượt success, retry và failure qua outbox/queue; service naming thống nhất; không PII/secret/raw token; không ID high-cardinality labels |
| AC-16 | Handoff thực dụng | Backend khác làm theo docs từ env mẫu có thể connect/publish/consume fixture đúng quyền; README ghi restart/replay/diagnosis và giới hạn local/AWS |
| AC-17 | Cách ly nhóm xử lý | Pause/làm chậm payment.start vẫn consume payment.saga/control; pause ticket.issuance vẫn consume ticket.control. Đo lag và tranh chấp tài nguyên, không chỉ kiểm queue đã tách tên |

Phép thử broker dùng test vhost và dữ liệu tổng hợp; không gửi thử command tiền/vé vào queue shared/AWS đang có nghiệp vụ. Phép thử ứng dụng phải xác nhận state/unique key và ranh giới commit, không chỉ nhìn message được consume hoặc queue về 0.

## 12. Phạm vi không triển khai và các điểm cần review

Ngoài phạm vi: HA/cluster nhiều node, delayed-message plugin, Kubernetes/managed broker, OAuth plugin RabbitMQ, notification-service mới, schema DB mới cho retry/identity, đồng bộ role/profile/delete toàn diện, thay payment provider, viết lại nghiệp vụ Saga hoặc triển khai AWS đang chạy.

Các quyết định cần reviewer xác nhận trên bản viết này: grouping theo nhóm xử lý có owner, 8 main và 40 queue vật lý nếu giữ TTL; contract identity ensure-exists + quét bù; budget broker và image pin ứng viên; ACL/service credentials và topology provisioning thuộc hạ tầng. Phương án native retry còn 16 queue là lựa chọn riêng chưa thay thế thuật toán TTL. Đây là điểm review kỹ thuật có đề xuất cụ thể, không phải câu hỏi nghiệp vụ còn thiếu khiến spec không dùng được.

QR thanh toán độc lập ở B13-OPEN-01 vẫn theo B13; không cần một queue mới để giải quyết. B13/B14 v0.2 cần review tương ứng trước khi coi contract sửa là baseline APPROVED; không dùng task queue để âm thầm phê duyệt thay.

### 12.1 Tự kiểm và trạng thái bằng chứng khi soạn

- Đã đối chiếu các luồng trong B3/B4/B5/B7/B8 với hợp đồng B13/B14 và ownership B11/B12; chỉ B13 messageType là binding nghiệp vụ.
- Giữ một Saga, success qua Booking, timeout khác terminal failure, refund/delivery/expiry là worker owner; User idempotency phù hợp Mongo standalone.
- Đã research tài liệu RabbitMQ, EIP và endpoint mapping MassTransit; thay grouping v0.1 theo service bằng 8 endpoint v0.2; ghi rõ điểm suy luận, giới hạn của nhóm event-control và điều kiện tách thêm cancellation.
- Đã xác định topology count, retry về đúng endpoint, confirm+return, ACK sau commit, topic ACL và cửa sổ mất observation Keycloak cùng cơ chế quét bù. Cập nhật listener isolation/ACL/policy/acceptance đồng bộ với topology mới.
- Kiểm tĩnh v0.2: parse thành công 3 khối YAML và 2 khối JSON, fence cân bằng, 18 liên kết file nguồn nội bộ tồn tại; đủ 8 main, 12 messageType và 15 binding nghiệp vụ/identity; tất cả binding trỏ đúng 8 queue; 11 type gốc khớp B13. Kiểm phép đếm TTL ra 40 queue/55 binding; tên queue/env v0.1 đã gỡ khỏi cấu hình hiện hành; không có mục để trống. Mẫu Compose không đổi so với mẫu đã qua `docker compose config --quiet` ở v0.1. Đây là kiểm tài liệu/model, không thay runtime.
- **Broker/application runtime: NOT RUN.** Khi soạn v0.1 Docker CLI không kết nối được Docker Desktop Linux engine; chưa start container, import topology, compile ví dụ Java hoặc chạy AC-02–17 trong lượt cập nhật này. Đây là giới hạn bằng chứng, không kết luận cấu hình đã hoạt động.
- Không thay trạng thái phê duyệt B13/B14/B16, không sửa các thay đổi có sẵn của người dùng và không triển khai hạ tầng trong lượt viết spec.

Sau review spec, bước tiếp theo nếu người dùng muốn triển khai là viết implementation plan theo `superpowers:writing-plans`, rồi chọn cách thực thi. Việc review tài liệu này không tự được coi là đã chọn cách thực thi hoặc nghiệm thu runtime.
