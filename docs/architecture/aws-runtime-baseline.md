# AWS runtime baseline theo giai đoạn

- **Mục đích:** phân biệt placement phát triển hiện tại với topology AWS đích đã duyệt, và bàn giao các đầu vào runtime còn thiếu cho TEAM-AWS.
- **Phạm vi:** chỉ tài liệu runtime. Tài liệu này không tạo, sửa, kiểm tra hay xác nhận tài nguyên AWS, deployment, credential hoặc runtime.
- **Căn cứ:** [B11-C](B11-C-target-architecture.md), [ADR-003](../adr/ADR-003-rca-rieng-chi-doc-kho-quan-sat.md), [ADR-004](../adr/ADR-004-bo-tri-hai-may.md), [B12](B12-data-ownership-and-schema.md), [B16](B16-observability-baseline.md), [B15](B15-verification-plan.md), và xác nhận hiện hành của chủ đồ án về Stage 1.

## 1. Cách đọc thẩm quyền và trạng thái runtime

USER_CONFIRMED, DECIDED và OPEN là trạng thái thẩm quyền của dự án. CURRENT, TEMPORARY_CURRENT, TARGET, DERIVED_TARGET, NOT_DEPLOYED, NOT_VALIDATED, RUNTIME_OPEN và RUNTIME_VALIDATED bên dưới chỉ mô tả placement/runtime; chúng không thay thế trạng thái thẩm quyền.

| Nội dung | Thẩm quyền | Hệ quả runtime |
|---|---|---|
| Two-machine transaction/control-diagnosis plane, không HA, ngân sách mục tiêu và quyền nâng instance tạm thời | DECIDED — B11-C §6, ADR-004, GOV-090, GOV-096–099 | Placement target không là bằng chứng deployment hay chịu tải |
| PostgreSQL, MongoDB và keycloak_db topology | DECIDED — B12 §1–2, GOV-104–107 | Vị trí target không thay thế kiểm tài nguyên thật |
| OpenTelemetry, Prometheus, Loki, Tempo và Grafana | DECIDED — B16 §2, GOV-117–121 | Chưa pin image/version hay chứng minh runtime |
| Local JVM không chạy Docker infrastructure; EC2 #1 t3.small hiện tại; Gateway/Eureka/Config Server local khi cần; public-IP:port hợp lệ ở Stage 1 | USER_CONFIRMED | CURRENT/TEMPORARY_CURRENT; không phải live inventory do tài liệu này tự kiểm |
| OTel Collector, Prometheus, Loki, Tempo và Grafana ở EC2 #2 | DERIVED_IMPLEMENTATION từ B11-C §6 + B16 §2 | DERIVED_TARGET; không phải quyết định kiến trúc mới hay runtime đã xác nhận |
| Capacity, storage, image/version/tag/digest, resource limit, retention, exporter, telemetry loss và restart behavior | OPEN | RUNTIME_OPEN tới khi có evidence chạy thật |

RUNTIME_VALIDATED chỉ được dùng khi có evidence runtime tương ứng. Không component nào trong tài liệu này được gán trạng thái đó.

## 2. Stage 1 — development foundation (CURRENT)

Stage 1 là **development/runtime connectivity smoke setup**, không phải nghiệm thu B16. Mỗi thành viên chạy chỉ Spring application mình đang làm từ IDE/local JVM; developer laptop không chạy Docker infrastructure. Gateway, Eureka và Config Server chạy local khi cần. Chưa có Spring business application nào bắt buộc phải triển khai lên AWS.

EC2 #1 t3.small hiện chứa PostgreSQL, MongoDB, RabbitMQ, Redis và Keycloak theo xác nhận của chủ đồ án. Đây là TEMPORARY_CURRENT có chủ ý: Spring application chưa chạy trên AWS nên chưa có lý do trả chi phí cho target 8 GiB. Nó không phải architecture defect và không thay topology Stage 2–3.

Theo B11-C §6 và B16 §2, EC2 #2 là **DERIVED_TARGET placement** cho OTel Collector, Prometheus, Loki, Tempo và Grafana. Placement này còn phụ thuộc RUNTIME_OPEN: capacity, persistent storage, image/version/tag/digest, resource limit, retention, exporter behavior, telemetry loss và restart behavior. Tài liệu này không khẳng định thành phần observability nào đã deploy, chạy hoặc đạt B16.

### Kết nối local và giới hạn Stage 1

- Application local lấy endpoint shared infrastructure và Collector từ cấu hình môi trường ngoài Git; không ghi IP, password, key hay token vào repository.
- Nếu endpoint đã được người vận hành cấu hình và reachable, local JVM có thể export trace/log qua public-IP:port tới remote OTel Collector. Đường local Spring JVM → OTel instrumentation → Collector → trace/log backend, nếu được chạy và có evidence, chỉ chứng minh smoke connectivity của đường đó.
- Prometheus là pull-based. Prometheus ở EC2 #2 không tự scrape được Spring application trên laptop sau NAT khi application không reachable. Bằng chứng application metric cho topology local này là NOT_RUN; baseline không chọn Pushgateway hay một đường metric khác.
- Stage 1 không coi VPC Peering là blocker. Public-IP:port là kết nối phát triển hợp lệ nếu route/port được người vận hành xác nhận; điều đó không chọn cơ chế cho Stage 2. Endpoint management/metrics không public theo B16 §6.
- Grafana/dashboard không là điều kiện để developer bắt đầu code và không là evidence B16.

## 3. Stage 2 — AWS integration (TARGET)

Stage 2 bắt đầu khi Spring service đầu tiên được deploy lên AWS để tạo shared integration. Placement sau đây là target cần hiện thực theo B11-C + B12. PostgreSQL, RabbitMQ và Redis hiện có ở EC2 #1 được mang sang target placement; việc move/deploy còn lại là RUNTIME_OPEN cho tới khi người vận hành ghi evidence.

- EC2 #1: PostgreSQL, RabbitMQ, Redis, API Gateway, booking-service, payment-service, ticket-service.
- EC2 #2: MongoDB, Keycloak runtime, event-service, user-service, Config Server, Eureka, chatbot adapter và observability plane derived target.
- keycloak_db vẫn là database riêng trong PostgreSQL ở EC2 #1. Keycloak runtime ở EC2 #2 truy cập qua endpoint theo môi trường; đây là dependency xuyên máy có chủ ý trong B11-C/B12.
- OTel Java Agent đi cùng JVM được instrumentation; nó khác OTel Collector. Agent sinh/xuất telemetry cùng JVM, còn Collector là thành phần tập trung ở placement derived target EC2 #2.

### Kết nối xuyên hai EC2

Khi Stage 2 tạo dependency liên tục xuyên máy, **năng lực kết nối private xuyên máy ổn định và được ghi nhận là bắt buộc**. Cách hiện thực năng lực này là OPEN; baseline không chọn VPC Peering, Transit Gateway, VPN, public-IP-only hay cơ chế khác.

Trước khi một two-EC2 integration run, benchmark hoặc final demo được xem là authoritative, cơ chế đã chọn phải được hiện thực, ghi lại và kiểm chứng. Quy tắc này không biến cơ chế đó thành Stage-1 blocker.

## 4. Stage 3 — final demo / load / RCA environment (TARGET)

Stage 3 dùng two-machine target đã duyệt. Mỗi máy target 8 GiB có ngân sách khoảng 6 GiB cho process/container; heap và memory limit phải công bố cùng run manifest. Không có HA. Nếu evidence cho thấy cấu hình không đủ trong test/demo hữu hạn, GOV-098 cho phép nâng instance tạm thời; điều đó không chứng minh cấu hình ban đầu đã chịu tải.

RCA là deployment unit riêng, chỉ đọc kho quan sát ở EC2 #2. Gemini là external API downstream của lớp giải thích sau ranking bằng evidence đã lọc; Gemini không tham gia hay thay đổi ranking.

Phân tách thời điểm:

- **Sớm / song song:** service owner thực hiện B16; telemetry generation; graph/schema/interface design tại track RCA khi đã có nguồn quản trị riêng; SERVICE/OPERATION/RESOURCE identification ổn định ở nơi track đó quản trị.
- **Muộn, sau evidence telemetry thật và fault injection:** runtime RCA integration, experimental tuning, weights, anomaly/ranking thresholds, ABSTAIN/rejection behavior và evaluation results.

Tài liệu runtime này không thay đổi phương pháp RCA. Trong Stage 1–2, RCA runtime vẫn NOT_DEPLOYED; placement và quyền read-only là DECIDED, còn kết quả runtime là NOT_VALIDATED.

## 5. Ma trận placement theo giai đoạn

Trừ các ô ghi rõ CURRENT/TEMPORARY_CURRENT, các ô Stage 2–3 mô tả placement dự kiến. Deployment RUNTIME_OPEN nghĩa là phase không tự xác nhận component đó đã được deploy; NOT_DEPLOYED chỉ dùng khi baseline khẳng định chưa triển khai. NOT_VALIDATED/RUNTIME_OPEN nghĩa là chưa có evidence vận hành.

| Component | Stage 1 — development foundation | Stage 2 — AWS integration | Stage 3 target | Authority và runtime state |
|---|---|---|---|---|
| PostgreSQL | EC2 #1 t3.small — TEMPORARY_CURRENT | EC2 #1 — TARGET; CURRENT deployment carried forward; NOT_VALIDATED | EC2 #1 — TARGET; NOT_VALIDATED | B12 §2 DECIDED; capacity RUNTIME_OPEN |
| MongoDB | EC2 #1 — TEMPORARY_CURRENT | EC2 #2 — TARGET; deployment RUNTIME_OPEN | EC2 #2 — TARGET; NOT_VALIDATED | B12 §2, GOV-107 DECIDED; capacity RUNTIME_OPEN |
| RabbitMQ | EC2 #1 — TEMPORARY_CURRENT | EC2 #1 — TARGET; CURRENT deployment carried forward; NOT_VALIDATED | EC2 #1 — TARGET; NOT_VALIDATED | B11-C §6 DECIDED; runtime RUNTIME_OPEN |
| Redis | EC2 #1 — TEMPORARY_CURRENT | EC2 #1 — TARGET; CURRENT deployment carried forward; NOT_VALIDATED | EC2 #1 — TARGET; NOT_VALIDATED | B11-C §6 DECIDED; runtime RUNTIME_OPEN |
| Keycloak | Runtime EC2 #1 — TEMPORARY_CURRENT | Runtime EC2 #2 — TARGET; deployment RUNTIME_OPEN. keycloak_db PostgreSQL EC2 #1 — CURRENT carried forward; NOT_VALIDATED | Same target placement; runtime NOT_VALIDATED | B11-C §6 + B12 §2 DECIDED; runtime RUNTIME_OPEN |
| API Gateway | Developer laptop/local JVM when needed — CURRENT; AWS NOT_DEPLOYED | EC2 #1 — TARGET; deployment RUNTIME_OPEN | EC2 #1 — TARGET; NOT_VALIDATED | B11-C §6 DECIDED; runtime RUNTIME_OPEN |
| Config Server | Developer laptop/local JVM when needed — CURRENT; AWS NOT_DEPLOYED | EC2 #2 — TARGET; deployment RUNTIME_OPEN | EC2 #2 — TARGET; NOT_VALIDATED | B11-C §6 DECIDED; runtime RUNTIME_OPEN |
| Eureka | Developer laptop/local JVM when needed — CURRENT; AWS NOT_DEPLOYED | EC2 #2 — TARGET; deployment RUNTIME_OPEN | EC2 #2 — TARGET; NOT_VALIDATED | B11-C §6 DECIDED; runtime RUNTIME_OPEN |
| event-service | Developer laptop/local JVM when a working service is run — CURRENT; AWS NOT_DEPLOYED | EC2 #2 — TARGET; deployment RUNTIME_OPEN | EC2 #2 — TARGET; NOT_VALIDATED | B11-C §6 DECIDED; runtime RUNTIME_OPEN |
| booking-service | Developer laptop/local JVM when a working service is run — CURRENT; AWS NOT_DEPLOYED | EC2 #1 — TARGET; deployment RUNTIME_OPEN | EC2 #1 — TARGET; NOT_VALIDATED | B11-C §6 DECIDED; runtime RUNTIME_OPEN |
| payment-service | Developer laptop/local JVM when a working service is run — CURRENT; AWS NOT_DEPLOYED | EC2 #1 — TARGET; deployment RUNTIME_OPEN | EC2 #1 — TARGET; NOT_VALIDATED | B11-C §6 DECIDED; runtime RUNTIME_OPEN |
| ticket-service | Developer laptop/local JVM when a working service is run — CURRENT; AWS NOT_DEPLOYED | EC2 #1 — TARGET; deployment RUNTIME_OPEN | EC2 #1 — TARGET; NOT_VALIDATED | B11-C §6 DECIDED; runtime RUNTIME_OPEN |
| user-service | Developer laptop/local JVM when a working service is run — CURRENT; AWS NOT_DEPLOYED | EC2 #2 — TARGET; deployment RUNTIME_OPEN | EC2 #2 — TARGET; NOT_VALIDATED | B11-C §6 + B12 §2 DECIDED; runtime RUNTIME_OPEN |
| Chatbot adapter | NOT_DEPLOYED | EC2 #2 — TARGET; deployment RUNTIME_OPEN | EC2 #2 — TARGET; NOT_VALIDATED | B11-C §6 DECIDED; runtime RUNTIME_OPEN |
| OTel Java Agent | Cùng local JVM khi service được instrumentation — DERIVED_IMPLEMENTATION; NOT_VALIDATED | Cùng mỗi Spring JVM EC2 #1/#2 — DERIVED_TARGET; RUNTIME_OPEN | Same placement — DERIVED_TARGET; NOT_VALIDATED | B16 §2; agent ≠ Collector |
| OTel Collector | EC2 #2 — DERIVED_TARGET; NOT_DEPLOYED; RUNTIME_OPEN | EC2 #2 — DERIVED_TARGET; deployment RUNTIME_OPEN | EC2 #2 — DERIVED_TARGET; NOT_VALIDATED | B11-C §6 + B16 §2; per-tool placement is derived |
| Prometheus | EC2 #2 — DERIVED_TARGET; NOT_DEPLOYED; laptop-NAT metric NOT_RUN | EC2 #2 — DERIVED_TARGET; deployment RUNTIME_OPEN | EC2 #2 — DERIVED_TARGET; NOT_VALIDATED | B16 §2; per-tool placement is derived |
| Loki | EC2 #2 — DERIVED_TARGET; NOT_DEPLOYED; RUNTIME_OPEN | EC2 #2 — DERIVED_TARGET; deployment RUNTIME_OPEN | EC2 #2 — DERIVED_TARGET; NOT_VALIDATED | B11-C §6 + B16 §2; per-tool placement is derived |
| Tempo | EC2 #2 — DERIVED_TARGET; NOT_DEPLOYED; RUNTIME_OPEN | EC2 #2 — DERIVED_TARGET; deployment RUNTIME_OPEN | EC2 #2 — DERIVED_TARGET; NOT_VALIDATED | B11-C §6 + B16 §2; per-tool placement is derived |
| Grafana | EC2 #2 — DERIVED_TARGET; NOT_DEPLOYED; not a coding prerequisite | EC2 #2 — DERIVED_TARGET; deployment RUNTIME_OPEN | EC2 #2 — DERIVED_TARGET; NOT_VALIDATED | B11-C §6 + B16 §2; dashboard is not B16 evidence |
| RCA | NOT_DEPLOYED | NOT_DEPLOYED | EC2 #2, separate read-only unit — TARGET; NOT_VALIDATED | B11-C §5–6, ADR-003 DECIDED; runtime RUNTIME_OPEN |
| Gemini API | FlashTicket–Gemini integration NOT_DEPLOYED; no runtime call asserted | FlashTicket–Gemini integration deployment RUNTIME_OPEN; no runtime call asserted | External downstream call from RCA after ranking — TARGET; NOT_VALIDATED | B11-C §5, ADR-003, GOV-093 DECIDED; runtime RUNTIME_OPEN |

## 6. B16 từ lát cắt đầu và giới hạn smoke

B16 là hợp đồng cross-cutting từ lát cắt dọc đầu tiên, không phải phần bổ sung sau khi service hoàn tất. Mỗi service owner thực hiện cùng feature của mình, khi áp dụng, các yêu cầu tại [B16 §3–5](B16-observability-baseline.md): log cấu trúc đã duyệt, correlation/trace/message propagation, metric cần thiết, bằng chứng business state transition, và quan sát retry/outbox/inbox. Baseline này chỉ trỏ về B16, không đổi log schema hoặc correlation model.

Stage-1 smoke trace/log nêu ở §2, nếu có evidence, không chứng minh bộ evidence B16 §8. Các phần còn NOT_RUN gồm:

- full-order correlation qua HTTP, message consumer và payment callback;
- timeout/retry outbox, duplicate consumer và expiry với log state/reason;
- application metrics của local JVM không reachable từ Prometheus qua NAT;
- sentinel secret và quyền RCA read-only / từ chối DB business và write API quan sát;
- run manifest gồm version/host/limit/clock offset/sampling/retention/exporter, telemetry drop/loss, và kết quả B15/B16.

Dashboard reachable không thay các evidence trên.

## 7. Chưa triển khai và các đầu vào runtime còn OPEN

Stage 1 chưa triển khai lên AWS: Spring business services, API Gateway, Config Server, Eureka, RCA và Gemini integration. Không thành phần nào trong OTel Collector/Prometheus/Loki/Tempo/Grafana được tài liệu này khẳng định đang chạy.

| Đầu vào runtime còn OPEN | Cần có trước khi nào |
|---|---|
| Non-secret inventory EC2 #1/#2: instance type, RAM, disk, persistent storage, container runtime nếu dùng | Trước khi deploy observability runtime hoặc AWS integration |
| Image/version/tag/digest, persistent volume, provisional resource limit; áp dụng retention B16 (log/trace 7 ngày, metric 14 ngày) và dung lượng thực | Trước khi xem observability placement là sustainable |
| Collector ingest endpoint, telemetry route, access scope và health thực tế | Trước khi developer dựa vào telemetry tập trung |
| Cơ chế private cross-EC2, behavior health/recovery đã kiểm | Trước authoritative Stage-2 integration, benchmark hoặc demo |
| Resource use, exporter behavior, dropped/export-failure signal, restart behavior, clock offset, backup/restore và B15/B16 run manifest | Trước final demo/load/RCA evidence |

## 8. TEAM-AWS HANDOFF

Các mục dưới đây là công việc của người vận hành. Task này chỉ thay đổi tài liệu.

### NOW

1. Xác nhận inventory không chứa secret của EC2 #1/#2: instance type, RAM, disk, persistent storage và container runtime nếu dùng; công bố các fact endpoint/inventory không chứa secret cần cho developer.
2. Sau inventory, chọn/pin image version/tag/digest cần có theo baseline runtime và ghi chúng như runtime fact.
3. Xác định provisional resource limit và persistent storage.
4. Chỉ khi các đầu vào trên đã rõ, deploy minimum observability runtime cần cho trace/log smoke; Collector, telemetry storage và instrumentation correctness được ưu tiên trước dashboard completeness.
5. Thực hiện telemetry smoke hẹp và lưu evidence; không gọi đó là B16 acceptance.
6. Đo resource use, exporter/drop signal và restart behavior trước khi xem placement EC2 #2 là sustainable.

Grafana không là điều kiện để developer bắt đầu code hay để B16 được chấp nhận.

### BEFORE AWS SERVICE INTEGRATION

- Move MongoDB và Keycloak runtime tới EC2 #2 nếu chúng còn ở temporary placement; giữ PostgreSQL và keycloak_db topology B12 ở EC2 #1.
- Deploy Spring applications tới máy target đã duyệt và cấu hình Java Agent/Collector endpoint theo môi trường.
- Chọn, implement, document và validate cơ chế private cross-EC2; phương thức cụ thể vẫn OPEN.
- Validate đường service-to-service, service-to-broker, service-to-database và telemetry; không giả định Docker service DNS hoạt động xuyên máy.

### BEFORE FINAL DEMO / LOAD / RCA EVIDENCE

- Publish run manifest, instance details và runtime resource limits; chỉ resize tạm khi observed evidence cho thấy cần.
- Validate clock synchronization/offset recording; correlation B16 qua HTTP/message/callback; telemetry loss/drop signals; retention/export settings; backup/restore nơi B12/B16 yêu cầu.
- Execute B15/B16 runtime evidence. Execute RCA/fault-injection work riêng theo scope đã duyệt, giữ RCA read-only và Gemini downstream sau ranking.

## 9. Liên kết liên quan

- Target architecture and placement: [B11-C](B11-C-target-architecture.md), [ADR-004](../adr/ADR-004-bo-tri-hai-may.md), [deployment diagram](../diagrams/src/B11-C-02-deployment-two-machines.puml).
- Datastore ownership and recovery: [B12](B12-data-ownership-and-schema.md).
- RCA boundary: [ADR-003](../adr/ADR-003-rca-rieng-chi-doc-kho-quan-sat.md).
- Observability contract and acceptance evidence: [B16](B16-observability-baseline.md), [B15](B15-verification-plan.md).
- Implementation sequencing and shared runtime inputs: [implementation readiness](implementation-readiness.md).
