# Quan sát: log B16 và OpenTelemetry Java agent

Chuẩn quan sát của FlashTicket ở [B16](../../docs/architecture/B16-observability-baseline.md). Thư mục này chứa hợp đồng log B16 và bộ kiểm dùng trong CI. Phần dưới mô tả cách các service ghi log, cách agent được đóng gói, và cách bật/tắt agent trên K8s qua CD.

## File trong thư mục

| File | Vai trò |
|---|---|
| `b16-services.txt` | Danh sách service có hợp đồng log B16. CI chỉ kiểm B16 cho các service này |
| `log.schema.json` | Schema một dòng log JSON B16 |
| `verify_logs.py`, `requirements.txt` | Bộ kiểm mẫu log `target/b16-log-samples.jsonl` do `ObservabilityTests` của từng service sinh ra |

## Log B16

- 7 service trong `b16-services.txt` ghi log JSON (logstash) ra stdout, theo `application.yaml` của từng service: trường B16 qua `LogFields`, stacktrace không chứa nội dung exception qua `SafeStackTracePrinter`, mức log thư viện WARN.
- Gateway ghi một dòng khi request kết thúc. Điểm nghiệp vụ ghi sau commit hoặc sau khi xác nhận kết quả (`BusinessTelemetry`).
- Booking, Payment, Ticket bổ sung tên queue vào consumer span (`RabbitTelemetry`, OTel API).
- CI (`.github/workflows/_maven-verify.yml`): sau `verify`, chạy `verify_logs.py` cho service trong danh sách. Service trong danh sách mà thiếu file mẫu là lỗi. Đổi file trong thư mục này thì `detect-changes.sh` build lại đúng các service trong danh sách.
- `assistant-service`, `keycloak`, `frontend` chưa có hợp đồng B16.

## Java agent trong image

- 7 service Spring có agent (api-gateway, user, event, ticket, booking, payment, assistant) và `discovery-server`: Dockerfile tải `opentelemetry-javaagent.jar` 2.33.1 có kiểm SHA-256, chạy qua `docker-entrypoint.sh`.
- `SERVICE_VERSION` là commit build (CD truyền `github.sha`). Entrypoint gắn `service.name`, `service.version`, `service.instance.id` (mới mỗi lần JVM khởi động), `deployment.environment` (`ENVIRONMENT`), `host.id` (`HOST_ID`) vào thuộc tính tài nguyên OTel. Không đặt các thuộc tính này qua `OTEL_RESOURCE_ATTRIBUTES` (entrypoint từ chối).
- **Agent chỉ chạy khi `OTEL_AGENT_ENABLED=true`.** Không đặt thì chạy `java -jar` như cũ.
- Khi bật, agent gửi trace và metric. **Log không gửi qua agent** (`OTEL_LOGS_EXPORTER=none`); log vẫn ở stdout.

## Agent trên K8s: đang tắt, cấu hình sẵn để bật sau

**Tắt cứng.** `deploy/k8s/<service>/deployment.yaml` đặt `OTEL_AGENT_ENABLED: "false"` trong `env:` của container. Theo quy tắc K8s, `env:` thắng mọi khóa cùng tên từ ConfigMap hoặc Secret (`envFrom`), nên agent không chạy dù `ENV_<SERVICE>` có khóa này. Khi agent tắt, các biến `OTEL_*` dưới không được đọc; chỉ `ENVIRONMENT` và `HOST_ID` có tác dụng (entrypoint ghi vào log B16).

Cấu hình đặt sẵn ở `deploy/k8s/<service>/configmap.yaml` (khối "OpenTelemetry Java agent"), 7 service: api-gateway, user, event, ticket, booking, payment, assistant.

| Biến | Giá trị | Ghi chú |
|---|---|---|
| `ENVIRONMENT` | `production` | `deployment.environment` và trường `environment` của log |
| `HOST_ID` | tên node K8s | `deployment.yaml`, downward API `spec.nodeName` |
| `OTEL_EXPORTER_OTLP_ENDPOINT` | `http://otel-collector:4318` | Service Collector trong cụm; **chưa có**, thêm khi bật |
| `OTEL_EXPORTER_OTLP_PROTOCOL` | `http/protobuf` | |
| `OTEL_TRACES_SAMPLER`, `OTEL_TRACES_SAMPLER_ARG` | `parentbased_traceidratio`, `0.1` | 10% trace |
| `OTEL_METRICS_EXPORTER`, `OTEL_LOGS_EXPORTER` | `otlp`, `none` | Log vẫn ở stdout |
| `OTEL_METRICS_EXEMPLAR_FILTER` | `always_off` | Exemplar mang thuộc tính span (có thể có query) |
| `OTEL_INSTRUMENTATION_SANITIZATION_URL_EXPERIMENTAL_SENSITIVE_QUERY_PARAMETERS` | `q` và 28 tham số `vnp_*` | Agent thay giá trị bằng `REDACTED`. Chỉ là lớp giảm dữ liệu theo tên; lọc chính đặt ở Collector |
| `OTEL_INSTRUMENTATION_COMMON_LOGGING_*_ID_KEY` | `traceId`, `spanId` | Khớp trường log B16 |
| `OTEL_INSTRUMENTATION_RABBITMQ_EXPERIMENTAL_SPAN_ATTRIBUTES` | `true` | event, booking, payment, ticket |

`deploy/k8s/examples/*.secret.env.example` không có khóa nào của agent: TLS và token gửi ra ngoài cụm sẽ nằm ở Collector, không ở từng pod.

**Khi bật (việc sau, chỉ thêm):**
1. Thêm Collector 1 pod (`deploy/k8s/otel-collector/`, Service tên `otel-collector`): nhận OTLP trong cụm, lọc như môi trường thử RCA (bỏ health check, xóa query khỏi `url.query`/`url.full`, xóa `exception.message`, lọc `exception.stacktrace`), nén, gửi ra máy quan sát qua TLS + token; thêm đường CD triển khai image dựng sẵn.
2. Thêm `SPRING_AUTOCONFIGURE_EXCLUDE=org.springframework.boot.data.redis.autoconfigure.observation.LettuceObservationAutoConfiguration` vào ConfigMap của service dùng Redis (gateway, booking, assistant), tránh span Redis trùng với agent.
3. Đổi `OTEL_AGENT_ENABLED` thành `"true"` và nâng giới hạn bộ nhớ trong `deployment.yaml` của service bật (agent tốn thêm RAM).
4. Bật từng service một qua CD; kiểm pod Ready, không restart/OOM, dữ liệu tới máy nhận rồi mới sang service kế.

`discovery-server` có agent trong image nhưng chưa có cấu hình K8s cho agent.

## Môi trường thử RCA

Hook tiêm lỗi ở `services/{booking,event}-service/src/rca-test/` chỉ vào build khi bật profile Maven `rca-test`; build production không chứa. Môi trường thử RCA (cấu hình Compose, tiêm lỗi, thu dữ liệu) nằm ở repo riêng `flashticket-rca-lab`.
