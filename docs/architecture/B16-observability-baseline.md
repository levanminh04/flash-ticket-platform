# B16 — Chuẩn log, metric và trace tối thiểu

- Phiên bản: `B16-v0.1`; ngày: 2026-09-14; trạng thái: `APPROVED`.
- Người duyệt: Lê Văn Minh; ngày duyệt: 2026-09-14 (GOV-144); phân lớp: `FORMATION`.
- Đầu vào: B8-v0.13 NFR-05/06/08/09/12 và FR-65–68, B11-C-v0.3, B13-v0.1/B14-v0.1 APPROVED; GOV-117–121/GOV-144.
- Đây là chuẩn dữ liệu quan sát để hiện thực; chưa có collector/dashboard/service chạy đạt chuẩn. Chuẩn kỹ thuật dưới đây được duyệt làm baseline triển khai theo GOV-144; các đầu vào OPEN và kiểm runtime vẫn giữ nguyên.

## 1. Phạm vi và căn cứ

FlashTicket phải cho dựng lại một giao dịch xuyên HTTP, database và message, rồi cung cấp dữ liệu đã lọc cho cơ chế RCA chỉ đọc. Nhiệm vụ hiện hành gồm xây dựng/đánh giá hệ thống và ứng dụng đồ thị để giám sát/chẩn đoán bằng **log, trace và metrics**, theo [DT18-NV1–NV3](../evidence/project-direction/2026-09-18-de-tai-va-nhiem-vu.md). Cập nhật căn cứ ngày 18/09 không đổi schema quan sát đã duyệt. B16 không quyết phương pháp dựng đồ thị, xếp hạng, bộ node được chấm hoặc cách dùng LLM.

Năm service nghiệp vụ giữ nguyên. RCA/lớp giải thích ở đơn vị triển khai riêng, đọc kho quan sát; không credential database nghiệp vụ và không đường tự sửa đơn/tiền/vé. Gateway và Keycloak, broker, database, host cũng có thể sinh tín hiệu; số node quan sát không bằng số service nghiệp vụ.

## 2. Mỗi công cụ làm gì?

| Thành phần đã được chọn | Vai trò trong triển khai | Không có nghĩa là |
|---|---|---|
| OpenTelemetry | Instrumentation và giao thức đưa trace/log ra collector | Tự hiểu nguyên nhân gốc hoặc thay log nghiệp vụ |
| Prometheus | Thu/lưu chuỗi metric: latency, throughput, queue, resource | Lưu từng đơn hàng hoặc thay trace |
| Loki | Kho log có cấu trúc, tìm theo service/thời gian/correlation | Database audit nghiệp vụ hoặc kho lưu vô hạn |
| Tempo | Kho trace và span của các bước xử lý | Một trace luôn bao trùm cả ngày tồn tại của đơn |
| Grafana | Tìm, xem dashboard, liên kết log–trace–metric | Tập dữ liệu thí nghiệm có nhãn và checksum |

Đề xuất đường đi: Java agent→OTel Collector→Tempo cho trace; log JSON→collector receiver phù hợp→Loki; Actuator/Micrometer→Prometheus cho application metric. Chỉ **một** đường xuất cho mỗi tín hiệu để tránh đếm trùng. Agent là lựa chọn mặc định được OTel khuyến nghị cho Spring Boot; không bật đồng thời một OTel starter/SDK tracing tự cấu hình và agent mà chưa loại trùng. [OpenTelemetry Java instrumentation](https://opentelemetry.io/docs/zero-code/java/spring-boot-starter/)

Không cần thêm ELK, APM thương mại hoặc agent thứ hai. Đây là stack đang có tài liệu tích hợp chính thức, nhưng tài liệu đó không chứng minh mức “phổ biến” bằng thị phần; sự phù hợp của đồ án nằm ở ba tín hiệu truy được và chi phí vận hành có thể đo. Actuator/Micrometer cho metric tùy chỉnh; manual span chỉ thêm ở ranh giới nghiệp vụ mà automatic instrumentation không biết. [Spring Boot observability](https://docs.spring.io/spring-boot/reference/actuator/observability.html)

## 3. Correlation bền qua retry, callback và outbox

**Phân biệt bốn mã:** `correlationId` nối toàn giao dịch; `traceId` nối một lần thực thi; `messageId` định danh bản tin để xử lý lặp; `requestId` định danh một request nhận được. Không dùng traceId làm khóa idempotency.

| Biên | Quy tắc đề xuất triển khai |
|---|---|
| Request bắt đầu tạo đơn | Server cấp UUID order; từ khi đơn tồn tại dùng `correlationId = orderId` theo B13, trả `X-Correlation-Id`. Request trước khi có đơn dùng correlation tạm và log liên kết khi tạo thành công; không cần thêm cột correlation vào orders. Header client không được tin như quyền truy cập; validate hình dạng/độ dài, không cho chèn dòng log |
| HTTP tiếp theo cho đơn đã có | Lấy orderId đã xác minh làm correlation; giữ nguyên qua các service. RequestId mới; trace có thể mới. Không sinh correlation mới chỉ vì buyer refresh trang |
| Gọi HTTP nội bộ | Truyền W3C `traceparent`/`tracestate` và correlation riêng. Timeouts/error code vẫn có log kết thúc; không log Authorization |
| DB transaction ghi outbox | Lưu correlation trong envelope **cùng transaction**; lưu trace context cần nối nguồn. Không phụ thuộc MDC còn sống đến lúc worker chạy |
| Publisher/consumer/retry | Khôi phục correlation từ envelope. MessageId gốc giữ theo retry; delivery attempt là trường riêng. Consumer tạo span mới/parent hoặc link đúng semantics; không giả mạo span nguồn |
| Callback cổng thanh toán | Không chờ VNPay truyền trace. Payment đối chiếu attempt hợp lệ rồi lấy correlation đã lưu; tạo trace callback mới, liên kết order/attempt/charge; tuyệt đối không lấy orderId từ payload chưa xác minh để chốt tiền |
| Scheduler expiry/refund/delivery | Mỗi lần xử lý tạo trace riêng; lấy correlation từ bản ghi công việc gốc. Khi một batch xử lý nhiều order, mỗi item giữ correlation của order đó |
| Event cancellation fan-out | Event có correlation thao tác hủy riêng. Bản tin giữ nó; xử lý từng order giữ correlation đơn và ghi thêm `causationId`/event cancellation ID để nối nguyên nhân hủy. Không thay correlation bền của đơn |

Ngay cả khi traces khác nhau hoặc span đã hết retention, log có correlation vẫn nối được chuỗi. Message batch có thể cần span links thay vì một parent tùy tiện; kiểm instrumentation thực tế trước khi kết luận trace đủ. [OTel messaging spans](https://opentelemetry.io/docs/specs/semconv/messaging/messaging-spans/)

## 4. Hình dạng log v1

Log mỗi record một JSON object, UTF-8. Schema logic dưới đây là whitelist; log không chứa toàn HTTP body, JWT hoặc event payload.

| Trường | Type / bắt buộc | Ý nghĩa |
|---|---|---|
| `schemaVersion` | integer = 1 / có | Phiên bản chuẩn log, không phải schemaVersion business message |
| `timestamp` | RFC3339 UTC string / có | Thời điểm tại nguồn; collector ghi thêm observed time riêng |
| `severity` | string enum DEBUG/INFO/WARN/ERROR / có | Lỗi nghiệp vụ dự kiến không tự bị đánh ERROR |
| `serviceName`, `serviceVersion`, `instanceId`, `environment` | string / có | Tên service ổn định; version build; instance duy nhất mỗi process; môi trường |
| `hostId` | string / có ở runtime thật | Liên kết process với máy; dùng ID không chứa credential |
| `eventName`, `operation`, `outcome` | string / có | Tên thao tác ổn định; outcome SUCCESS/REJECTED/RETRY/FAILED/UNKNOWN |
| `correlationId` | UUID string / có khi đã gắn giao dịch | Mã bền; log startup không có order thì không bịa UUID đơn |
| `requestId`, `traceId`, `spanId` | string / có khi tương ứng | Request hiện tại; ID trace/span W3C hợp lệ |
| `orderId`, `paymentAttemptId`, `chargeId`, `refundId`, `issuanceId`, `ticketId` | UUID string / tùy thao tác | ID liên kết, không dump row; chỉ ghi ID cần dùng |
| `messageId`, `causationId`, `sagaId` | UUID string / tùy thao tác | Nối phát/nhận/retry và Saga |
| `stateBefore`, `stateAfter`, `reasonCode` | string / tại quyết định trạng thái | Enum/code công bố, không câu lỗi tự do thay reason code |
| `durationMs`, `retryCount` | số không âm / khi có | Duration dùng đồng hồ monotonic |
| `configVersion` | chuỗi số nguyên không âm / khi có | Theo kiểu version trong B13 để không mất chính xác bigint qua JSON |
| `errorType`, `errorCode` | string / khi lỗi | Không chứa nội dung nhạy cảm; stack trace đã lọc khi cần |

Ví dụ dữ liệu giả, không phải log từ hệ thống đang chạy:

```json
{"schemaVersion":1,"timestamp":"2026-09-14T03:00:00Z","severity":"INFO","serviceName":"booking-service","serviceVersion":"example-build","instanceId":"booking-demo-1","environment":"local-test","hostId":"demo-host-1","eventName":"order.payment-acceptance","operation":"accept-payment","outcome":"SUCCESS","correlationId":"00000000-0000-4000-8000-000000000002","orderId":"00000000-0000-4000-8000-000000000002","stateBefore":"PENDING_PAYMENT","stateAfter":"ISSUING","reasonCode":"PAYMENT_ACCEPTED","durationMs":12}
```

Không log trước commit như thể trạng thái đã thành công. Business transition/outbox được commit rồi mới phát log outcome success tương ứng; lỗi sau commit trước log là khoảng thiếu phải nhận diện bằng event/retry, không tuyên bố logging có exactly-once.

## 5. Điểm quan sát cần có từ lát cắt đầu

| Thành phần | Span/log quyết định | Metric tối thiểu, label có giới hạn |
|---|---|---|
| Event | save-draft, publish config version, approve/cancel actor và result | request duration/count; publish backlog/age |
| Booking | reserve, promotion, freeze, accept-payment deadline, expire/release; from/to/reason | lock wait, transaction duration; reserve outcome; expiry lag; outbox/inbox retry |
| Payment | attempt tạo/query, callback validation result, charge/confirmation, Saga step, refund outcome | gateway latency/error/unknown; Saga pending age; refund backlog/age |
| Ticket | issuance toàn bộ/terminal fence, delivery, check-in outcome | issuance latency; delivery retry; check-in outcome count |
| User / Keycloak | role-grant request/result/retry; login outcome tổng hợp từ nguồn identity phù hợp | Admin API latency/error; projection retry/age; không thu password/token |
| Hạ tầng | service→DB/broker/partner endpoint theo tên logic | CPU/RAM/disk, connection pool, queue ready/unacked, restart/OOM, collector dropped/export failure |

`orderId`, `buyerId`, `traceId`, `messageId`, URL có ID, QR và email **không** là Prometheus labels hoặc Loki indexed labels. Loki label chỉ dùng tập hữu hạn như environment/service/severity; correlation ở nội dung JSON/structured metadata để query. [Loki cardinality](https://grafana.com/docs/loki/latest/get-started/labels/cardinality/)

Metric queue/deadletter không thay nhật ký business; metric tồn kho chỉ phục vụ quan sát, không được dùng ra quyết định giữ chỗ. Không bắt buộc audit vĩnh viễn cho mọi yêu cầu từ chối mua vé: danh sách audit nghiệp vụ vẫn đúng **bốn nhóm FR-65–68**, không mở rộng từ log vận hành.

## 6. Bảo mật, quyền và thời gian lưu

Whitelist trước khi ghi/export; filter ở collector là lớp dự phòng, không phải lý do log raw trước. Cấm password/app-password/client-secret/private-key/JWT/Authorization/cookie/raw QR hoặc ciphertext QR, email/điện thoại đầy đủ, tài khoản ngân hàng, toàn callback body và SQL bind values nhạy cảm. Với lỗi provider lưu code/attempt/correlation đủ dùng; không thêm intake/dashboard Q-07 đã loại theo PRJ-024.

Nếu cần nối actor, dùng business ID được cấp quyền đọc phù hợp hoặc pseudonym; không gửi hồ sơ người dùng ra Gemini. Lớp giải thích chỉ nhận gói evidence được chọn/khử nhạy cảm sau xếp hạng. Thử bằng sentinel secret giả: tìm cả log local, collector, Loki/Tempo và payload ra ngoài; không chỉ kiểm UI Grafana.

RCA dùng principal chỉ đọc kho quan sát. Endpoint write/delete/admin của Loki/Tempo/Prometheus không cấp cho principal RCA; DB business không cho CONNECT. Grafana datasource/viewer không mặc nhiên là quyền read-only ở API backend, nên phải kiểm trực tiếp allow/deny. Management/metrics endpoints không public.

**Baseline retention tối giản cho demo (GOV-144):** log/trace nóng 7 ngày, metric 14 ngày; export riêng từng run được chọn trước khi xoay vòng. Dung lượng thực phải đo trên cấu hình đã công bố; không hứa lưu đủ nếu disk đầy. Audit nghiệp vụ nằm trong datastore nghiệp vụ và theo vòng đời dữ liệu demo, không tự bị xóa theo TTL Loki. Retention dài hạn/RPO/RTO là `OPEN` của nhóm vận hành; không cản unit test nhưng phải chốt trước thu dataset cuối.

## 7. Những trở ngại RCA thực sự cần kiểm

| Rủi ro | Cách hạn chế trong hệ thống | Giới hạn phải ghi |
|---|---|---|
| Sampling làm mất trace lỗi | Demo/run hữu hạn có ngân sách dùng 100% trace; tải lớn ghi rõ sampling config và dropped count | Head sampling có thể mất lỗi xảy ra sau quyết định lấy mẫu; 100% cũng mất nếu collector quá tải |
| Đứt HTTP→message / callback | Kiểm §3 với broker redelivery, scheduler restart và callback ngoài trace | Có correlation không đồng nghĩa mọi span đều đủ |
| Lệch đồng hồ hai EC2 | Đồng bộ giờ, ghi UTC/observed time; đo offset và duration monotonic | Không suy thứ tự nhân quả chỉ bằng timestamp gần nhau |
| Một host chứa nhiều node | Gắn hostId/instanceId và metric host với trace dependency | Hai service cùng chậm có thể do tài nguyên chung |
| Cổng bên ngoài không có telemetry nội bộ | Client span + gateway response/query và deadline | Không tuyên bố xác định lỗi trong nội bộ VNPay |
| Collector/kho quan sát lỗi | Theo dõi dropped/export failure, queue, disk; business không chờ synchronous telemetry write | Thiếu log không chứng minh không có sự kiện nghiệp vụ |
| Label quá nhiều, instrumentation trùng | Allowlist, giới hạn tags, một exporter mỗi tín hiệu | Overhead phải đo, không lấy latency đã bật/tắt tracing khác nhau để so trực tiếp |
| Nhầm phụ thuộc với nguyên nhân | Hệ thống cung cấp cạnh/ID/bằng chứng; thuật toán/ranking ở bộ RCA | Đồ thị phụ thuộc không tự là đồ thị nhân quả |

Sampling là đánh đổi lượng dữ liệu/chi phí; chính sách của mỗi lượt đo phải lưu cùng kết quả. [OTel sampling](https://opentelemetry.io/docs/concepts/sampling/)

## 8. Bộ bằng chứng để nghiệm thu B16

1. Một order từ tạo→thanh toán→ISSUING→COMPLETED truy qua cùng correlation, kể cả callback và consumer; một trace mới không làm mất chuỗi.
2. Một timeout/retry outbox, một duplicate consumer và một expiry có log reason/state đủ phân biệt; không bịa outcome thành công trước commit.
3. Một secret sentinel không xuất hiện ở đầu ra; RCA đọc được dữ liệu cần và bị từ chối DB business/write API quan sát.
4. Manifest run gồm commit/build, Java/image versions, hai host và limits, dataset seed, workload, thời gian/clock offset, sampling/retention/exporter config, lỗi chủ động đã tiêm, checksum dữ liệu xuất; không secret.
5. NFR-12 còn yêu cầu kết quả RCA đến người được quyền; `RES-039` vẫn `OPEN`, không tự chọn UI hoặc người sử dụng trong B16.

Các kiểm runtime trên **chưa chạy** trong lượt tài liệu. B15 giữ kế hoạch, B12-validation giữ kết quả datastore thực. Không đóng NFR-05/06/08/09 bằng một ảnh dashboard. Phép thử độc lập: B16 triển khai yêu cầu quan sát B8 và vị trí/quyền B11-C; không dùng kết quả nghiên cứu để thay service, Saga hoặc bất biến; hai cửa nối giữ nguyên.

Phần vào báo cáo sau khi duyệt: mô hình dữ liệu quan sát, lý do correlation bền khác traceId, cách masking/giới hạn quyền, phép kiểm và chi phí đo được. Không viết kết quả RCA khi chưa có thí nghiệm.

## Phụ lục đối chiếu R0 — 2026-09-14

R0-v0.5 §3 qua cửa project/lien-ket-rca.md vẫn CANDIDATE. Mục 1/6 đối chiếu định danh; 7 đối chiếu UTC và nhịp thu cần ghi trong manifest; 8 đối chiếu tín hiệu invariant; 9 đối chiếu cửa sổ trước sự cố/retention. Chuẩn hiện tại tạo đầu vào kiểm các mục đó; giữ OPEN việc đo đủ độ phủ, nhịp thu thực và cửa sổ trước lỗi ở từng run. Không dùng R0 làm nguồn chọn stack, service hoặc bất biến; không nhận phê duyệt bộ nghiên cứu từ GOV-144.
