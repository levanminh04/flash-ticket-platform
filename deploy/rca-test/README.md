# Kiểm local cho gói a–i

Phạm vi: source và môi trường thử riêng. Không dùng cấu hình thật, không ghi GitHub,
không deploy lên máy 84/Oracle. Đây là công cụ chuẩn bị và kiểm cơ chế; kết quả tải
một giờ, nghiệm thu ARM, Loki và kết quả RCA phải báo riêng theo bằng chứng thực chạy.

## `.local/rca-test` là gì?

Thư mục đầu ra local, nằm ngoài Git qua `.gitignore`. Không phải service hay module
nghiệp vụ. `private/` chứa token thử, QR key thử, fixture, lệnh tiêm lỗi, nhãn/thời gian
lỗi, audit mock và probe đối chứng. `public/` chỉ chứa log/trace/metric ứng dụng.
Collector chỉ đọc `public/logs` và nhận OTLP; không mount `private/`. Không dùng
control, audit, fixture hay probe làm đầu vào RCA. Không xóa lượt hỏng khi chạy lại.

## Container, volume và cổng

Project Compose `ft-bi`, bridge `ft-bi-network`. Mặc định 16 container:
`postgres`, `rabbitmq`, `redis`, `mongodb`, `keycloak`, `mock-vnpay`, `toxiproxy`,
`mailpit`, `otel-collector`, `discovery-server`, `api-gateway`, `booking-service`,
`event-service`, `payment-service`, `ticket-service`, `user-service`.
Tên container do Compose sinh dạng `ft-bi-<service>-1`; nhãn `ft.bi.test=true`.
Profile `netem` thêm `netem-gateway`, `netem-payment`; profile `load` chạy `k6`.

Volume riêng: `ft-bi-postgres-data`, `ft-bi-mongodb-data`, `ft-bi-rabbitmq-data`,
`ft-bi-redis-data`. Phép kiểm init dùng container `ft-bi-init-postgres`, volume
`ft-bi-init-postgres-data`, giữ lại sau khi dừng. Maven đã dùng broker CI riêng
`ft-bi-rabbitmq-ci`, dữ liệu tmpfs, không có persistent volume. Kiểm build và
startup dùng thêm `ft-bi-shellcheck`, `ft-bi-agent-permission`,
`ft-bi-discovery-diagnostic` và `ft-bi-arm-discovery-smoke`, không tạo volume.
Giữ các volume và log để đối chiếu sau kiểm.

Cổng publish chỉ bind loopback: Gateway 18081, Keycloak 18080, Discovery 18761,
PostgreSQL 15439, RabbitMQ 15679/15689, Redis 16389, Toxiproxy API 18474,
mock VNPay 19090 (`FT_MOCK_HTTP_PORT`), Mailpit 18025, OTLP HTTP 14318. Không dùng ngrok hay VNPay thật.

## Chuẩn bị và chạy

Chạy từ gốc repo, Python 3.12 có `jsonschema` (xem `../observability/requirements.txt`).
File `.env.example` chỉ chứa thông tin xác thực giả, dành riêng cho môi trường này.
Có thể chọn bản sao riêng qua `--env-file` và `FT_TEST_ENV_FILE`; không trỏ đến env thật.

```powershell
$env:FT_TEST_ENVIRONMENT = 'true'
py -3 deploy/rca-test/verify.py setup
py -3 deploy/rca-test/verify.py check-config
docker compose --env-file deploy/rca-test/.env.example -p ft-bi -f deploy/rca-test/compose.yml build
docker compose --env-file deploy/rca-test/.env.example -p ft-bi -f deploy/rca-test/compose.yml up -d
py -3 deploy/rca-test/verify.py tokens
py -3 deploy/rca-test/verify.py probes
py -3 deploy/rca-test/verify.py inventory
```

Trước `build`, chạy `mvn verify` trong cả 7 ứng dụng Spring và `services/keycloak-spi`
để có JAR đã kiểm. Keycloak còn cần `target/dependency/amqp-client-*.jar` do Maven tạo.
Image Spring tải agent đã pin checksum ngay lúc build, không tải ở runtime.

Trên Linux: chạy `setup` bằng người vận hành có quyền tạo thư mục cho UID/GID 10001,
hoặc chuẩn bị group 10001 và quyền thư mục tương ứng trước. File control dùng 0640,
owner/group 10001 khi chạy root; tiến trình ghi không phải root phải thuộc group 10001.
Ứng dụng đọc control read-only, chỉ ghi audit vào mount riêng. K6 chạy UID/GID 10001.
Quyền file Windows phụ thuộc ACL của máy và Docker Desktop.

## Fixture và mock thanh toán

```powershell
py -3 deploy/rca-test/fixtures.py catalog
# Hoặc dùng draft rồi điền categoryId/venueId có sẵn trong Event thử.
py -3 deploy/rca-test/fixtures.py prepare --draft .local/rca-test/private/draft.json
py -3 deploy/rca-test/fixtures.py purchase --count 2
py -3 deploy/rca-test/fixtures.py cancel
```

Dùng API có xác thực và idempotency key thật. Không sửa thẳng trạng thái DB.
Mock kiểm chữ ký checkout, gọi IPN có chữ ký, kiểm tiền/mã giao dịch, phục vụ refund
và querydr theo hai công thức chữ ký hiện hành của client. Đặt `FT_MOCK_REFUND_POLL=true`
để mock trả refund đang xử lý, sau đó trả thành công ở querydr. Replay phải giữ cùng
order/charge/ticket. Manifest lưu từng checkpoint; lỗi giữ nguyên để đối chiếu.
(Tạm chưa hỗ trợ, xem mục tiêm lỗi.) `--terminal-failure` phải đạt Ticket FAILED với 0 vé, Booking ISSUANCE_FAILED và
refund SUCCEEDED; không coi ném exception/retry RabbitMQ là terminal failure.

## Tiêm lỗi có thời hạn

Hook tiêm lỗi **không nằm trong code nghiệp vụ**: chỉ Booking và Event có, đặt trong
`services/<service>/src/rca-test/` (bộ chặn HTTP theo route và `RcaFaults`). Thư mục này chỉ
được build khi chạy Maven với profile `rca-test`:

```powershell
cd services/booking-service; ./mvnw clean -P rca-test -DskipTests package   # jar cho môi trường thử
cd services/event-service;   ./mvnw clean -P rca-test -DskipTests package
```

Build thường (`./mvnw clean package`, CI, production) không chứa hook. Luôn `clean` khi đổi giữa hai kiểu build để không sót class cũ trong `target/`. Route nhận lỗi khai trong
`src/rca-test/resources/application-rca-test.yaml` (`ft.rca.http.*`). Ticket không còn hook;
ô thất bại phát vé sẽ dùng dữ liệu gây thất bại nghiệp vụ thật (đang mở).
Khi chạy, cần đồng thời profile Spring `rca-test` và
`FT_FAULTS_ENABLED=true`. Mặc định cờ false. Muốn thử hook, đổi cờ trong env thử
và recreate đúng ba service. Không có endpoint quản trị hay header/query tiêm lỗi.
Lease tối đa 300 giây, delay tối đa 30 giây, pool hold tối đa 5 connection.

```powershell
py -3 deploy/rca-test/faultctl.py --service booking-service --action http.delay --seconds 30 --value 1000 --target <orderId> --subject <buyerSubject> --fixture .local/rca-test/private/fixtures.json
py -3 deploy/rca-test/faultctl.py --service booking-service --action off
```

`http.error`/`http.delay`: Booking.getOrder hoặc Event.publicEvent, trước transaction;
đối tượng phải có trong fixture, Booking phải đúng chủ đơn. `pool.hold`: Booking,
không SQL/khóa hàng; watchdog trả connection khi hết hạn hoặc tắt, kể cả acquisition
trả về muộn. 

Toxiproxy luôn có trong cả baseline, fault, recovery:

| Proxy | Listen trong bridge | Upstream |
|---|---|---|
| booking-jdbc | 15432 | postgres:5432 |
| event-jdbc | 15433 | postgres:5432 |
| booking-redis | 16379 | redis:6379 |
| gateway-redis | 16380 | redis:6379 |
| payment-vnpay | 19091 | mock-vnpay:9090 |

Dùng API `http://127.0.0.1:18474` thêm/xóa toxic đúng proxy, lưu request và timestamp
vào `private/probes`. Chỉ đổi một yếu tố mỗi lượt. `verify.py probes` đối chiếu
proxy/direct, không thay thế bằng chứng đường đi của JVM: cần byte counter proxy,
JDBC/Redis/client span và địa chỉ đích thực tế khi có request ứng dụng.

Các script Bash chạy tại host thử, từ gốc repo:

```bash
export FT_TEST_ENVIRONMENT=true
# Sidecar chỉ có NET_ADMIN trong network namespace của đúng caller.
docker compose --env-file deploy/rca-test/.env.example -p ft-bi --profile netem -f deploy/rca-test/compose.yml up -d netem-gateway netem-payment
bash deploy/rca-test/netem.sh gateway booking-service 1000 30
bash deploy/rca-test/cpu.sh booking-service 30 2
bash deploy/rca-test/db-down.sh 30
```

Netem lọc IP/port đích, kiểm bridge và qdisc trước khi làm; trap gỡ qdisc khi xong.
CPU chạy trong cgroup của JVM với quota cố định; lưu cpu.max/cpu.stat trước/sau.
DB-down chỉ cấm kết nối booking_db và kết thúc session cũ, kiểm event_db còn chạy,
trap mở lại booking_db. Không dừng PostgreSQL dùng chung. Nếu host/script chết,
người vận hành phải kiểm và phục hồi qdisc/ALLOW_CONNECTIONS trước lượt mới.

## Tải và thu dữ liệu

K6 đọc fixture/token riêng; tỷ lệ request đọc Event/Order/Payment/Ticket cố định
50/25/15/10, không thay theo nhãn lỗi. Giao dịch mua/hủy là lịch riêng đã khai báo
qua fixtures.py, phải giữ giống nhau giữa các phase, tránh thay workload theo lỗi.

```powershell
docker compose --env-file deploy/rca-test/.env.example -p ft-bi --profile load -f deploy/rca-test/compose.yml run --rm k6
py -3 deploy/rca-test/verify.py collect
```

`collect` kiểm JSON Spring theo schema, tách dòng JVM/native ra private, giữ container
ID và manifest hash source. Keycloak có schema native riêng; không ép thành B16 Spring.
Traces/metrics/logs collector ở `public/telemetry`. Không coi file log có traceId là
bằng chứng đã nối thành công toàn bộ trace; phải đối chiếu trace export thực tế.

Dừng sau khi kiểm bằng `docker compose ... stop`, giữ volume và bằng chứng.
Không mặc định chạy tải một giờ; kết quả/giới hạn thực tế xem README observability.
