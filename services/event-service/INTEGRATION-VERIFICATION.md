# Event integration verification — 30/10/2026

> Điều chỉnh sau lượt kiểm này, ngày31/10: approve không còn ghi outbox cấu hình;
> organizer publish mới ghi đủ Finance/Sales/Access cùng transaction. Finance vẫn
> giữ phiên bản phí đã duyệt. Bằng chứng30/10 bên dưới là lịch sử, không chứng minh
> việc triển khai lên EC2 sau thay đổi. Xem README/HANDOFF cho hành vi hiện tại.

Đây là bằng chứng triển khai/kiểm tra, không sửa yêu cầu đã duyệt hoặc tự phê
duyệt contract đề xuất. Source of truth: B8 FR01–15/57/58/62–64/67/68/70,
B13 §5/7/9, B14 luồng hủy, B15 V09/V12/V16, B16 §3–5.

## 1. Nhánh nguồn và phần còn thiếu

Đã đọc GitHub branches API trực tiếp ngày 30/10, đối chiếu SHA với tracking refs,
không checkout/merge/pull nhánh khác:

- Booking `dev/son`: `987e1f050c59464930314c915c423eb2190d06fe`.
  Đọc controller/listener, `SalesConfiguration`, `ContractRules`, HANDOFF.
  Có consumer sales-configured; chưa có sales-summary API hoặc consumer
  EventCancellation. Handoff expiry cũng xác định event-wide cancellation là task riêng.
- Payment `dev/tuan`: `e47b1f65db555ee43783e1cda0922b23edfc8334`.
  Đọc controller, DTO EventCancellation/FinanceConfiguration, README và purchase
  runtime design; chưa có financial-summary API, consumer finance/cancellation
  hoặc event refund run. DTO có sẵn không đồng nghĩa đã có consumer xử lý.

Danh sách field, endpoint dự kiến và điều kiện bật nằm tại
[INTEGRATION-CONTRACTS](INTEGRATION-CONTRACTS.md). Event giữ read-model disabled,
không dùng query DB owner khác hoặc giá trị giả để vượt dependency.

## 2. Kiểm trực tiếp EC2

Note AWS hiện ở `D:\Note Important\(DATN) Note AWS.txt`; Elastic IP khớp hai
máy được giao. SSH dùng private key PPK tương ứng, chuyển tạm có ACL riêng,
giữ host-key verification và xóa bản chuyển sau mỗi phiên. Không sửa key gốc.

### Transaction — 13:31 ngày 30/10 (UTC+7)

- SSH thành công. PostgreSQL16.15, RabbitMQ4.3.6, Redis7.4.11 healthy; có Keycloak
  và MongoDB. Booking và Config Server đang chạy container network=host.
- Đọc HTTP từ chính server: Booking `127.0.0.1:8082/actuator/health` và Config
  `127.0.0.1:8888/actuator/health` trả200. Không có kết nối ở8083/8761 tại thời điểm
  probe; không suy ra service đó không tồn tại ở mọi địa chỉ/môi trường.
- Event DB: transaction `READ ONLY`, SET ROLE event_owner,12 bảng canonical;
  không có event/outbox lúc kiểm; kết thúc ROLLBACK. Query dùng schema tường minh.
- `business.ex` là topic durable. Binding sales→Booking, access→Ticket,
  finance→Payment và cancellation→cả ba queue đều có.
- `booking.sales-configured.q` có1 consumer. Ba cancellation queue, Ticket access
  và Payment finance có0 consumer. Broker còn backlog/DLQ thuộc những luồng khác;
  không đọc payload, không consume/purge/requeue hoặc sửa các queue đó.
- Booking/Config đã được deploy lên EC2 là quan sát mới, không thay quyết định
  placement của platform và không buộc Event phải deploy lên server.

### Control-Diagnosis

- SSH `52.64.237.84:22` từ laptop timeout trước bước xác thực; key chuẩn bị là
  `FlashTicket-Control-Diagnosis.ppk`, chưa thể xác minh key được server chấp nhận.
- Kiểm TCP từ Transaction: public52.64.237.84:22 timeout, private172.31.41.236:22
  không kết nối được. Chưa kiểm được cấu hình/health bên trong Control.
- Sau probe, anh xác nhận EC2 #2 hiện đã tắt. Đây là giới hạn có chủ đích của lượt
  kiểm, không còn là nghi vấn lỗi SSH/key hoặc thiếu hạ tầng nghiệp vụ Event.
  Transaction đủ để làm và kiểm Event; không cần bật Control cho các phần đó.
  B16 Collector/Loki/Tempo/Prometheus/Grafana vẫn chưa được nghiệm thu lượt này;
  kiểm đường quan sát sẽ thực hiện khi Control được bật lại.

## 3. Kiểm publisher trên RabbitMQ thật

`RabbitTransportTests` chạy production RabbitMessagePublisher và full Event
snapshot/envelope, không mock RabbitTemplate. Local dùng container RabbitMQ4.3.6
với quorum queue. Đã chạy cùng6 ca trên RabbitMQ Transaction qua SSH tunnel:

- Bốn loại sales/access/finance/cancelled được confirm và nhận đúng JSON,
  persistent delivery, messageId/type/correlation/causation/traceparent; version
  lớn hơn giới hạn số nguyên chính xác của JavaScript vẫn là string.
- Mandatory return khi không có binding không tính là thành công; khôi phục
  binding rồi retry vẫn cùng messageId.
- Đóng/reconnect connection rồi replay giữ nguyên bytes/messageId, thể hiện giao
  nhận at-least-once; không tuyên bố consumer đã dedupe hay nghiệp vụ đã hội tụ.

EC2 test cuối kết thúc13:56:45, đã có assertion exchange/routing key và toàn bộ
envelope/data: **6 PASS,0 failure/error/skip**. Dùng vhost/user tạm
`event-verify-fb6f237652a94ca18a3fd021e98ae0c7`, chỉ quyền trong vhost đó. Queue
và `business.ex` test thuộc vhost riêng; không dùng vhost nghiệp vụ `/`. Vhost và
user tạm đã được xóa, kiểm lại không còn; tunnel/process/key tạm được dọn.
Không ghi Event DB, không thay topology/quyền của tài khoản ứng dụng dùng chung.

Kết quả này chưa bao phủ broker restart/crash recovery, crash giữa ACK–ghi
published_at trên broker EC2, hoặc consumer Booking/Ticket/Payment thật.

## 4. Phần Event đã xử lý và giới hạn bàn giao

- Sửa lỗi discovery exception thoát khỏi boundary: Booking/Payment lookup lỗi
  trả503 UPSTREAM_UNAVAILABLE, không lộ message/cause của registry và không gọi
  HTTP counterparty. Test đã tái hiện RED trước sửa, GREEN sau sửa; có kiểm đường
  lookup thành công để không biến mọi discovery thành lỗi.
- Review độc lập phát hiện thiếu assertion routing key khi test queue bind
  `event.#`. Đã bổ sung exchange/routing key theo từng loại, aggregateId/occurredAt
  và dữ liệu nguyên vẹn. Mutation cố tình route cả4 loại thành SALES bị3 test
  access/finance/cancelled bắt đúng; publisher đã được khôi phục trước chạy GREEN.
- Sửa README còn ghi public chỉ QUANTITY; cập nhật tình trạng Gateway đã
  rebuild/recreate và phân biệt kết quả lịch sử trong HANDOFF.
- Chỉ sửa Event source/tests/docs. Không sửa Booking/Payment/Ticket/User code,
  không thay Config/Gateway trong lượt follow-up, không commit/push.
- Chưa thể bật organizer cancel/request hoặc statistics tới khi các owner cung
  cấp API và thống nhất contract. Admin cancel và phần cấu hình Event độc lập vẫn
  có code; hậu quả đóng bán/vô hiệu vé/hoàn tiền vẫn cần consumer và E2E theo B14.

## 5. Kết quả verification cuối

- Event verify kết thúc13:59:54 ngày30/10: **120 test PASS**,0 failure/error/skip;
  build/Spotless PASS, SpotBugs0 bug/0 error. PostgreSQL và RabbitMQ test cô lập.
- Gateway hiện tại (gồm route User đã có) verify kết thúc13:50:13: **26 test PASS**,
  build/Spotless/SpotBugs PASS; lượt này không sửa source Gateway.
  SHA256 của hai class route/security lấy trực tiếp từ container khớp class đã
  verify. Đối chiếu này tập trung hai class route/security; không khẳng định hai
  JAR byte-identical (hash toàn JAR đang chạy và JAR verify khác nhau).
- Smoke Event JAR mới kết thúc14:04:31: DB/Rabbit UP, Event đăng ký Eureka UP,
  JWT thiếu/sai401, machine JWT hợp lệ vào path deny403. Public list trực tiếp200
  và đi qua Gateway200, correlation header giữ đúng. JDBC event_admin→event_owner,
  event_schema,12 bảng, transaction read-only/ROLLBACK. Outbox và read-model tắt;
  không có ghi dữ liệu nghiệp vụ. Đã dừng JVM/tunnel và xóa key tạm do smoke tạo.
- Metadata RabbitMQ xác nhận tài khoản Event cấu hình hiện tại có quyền write
  business.ex và topic permission cho đủ4 routing key; không cần sửa quyền.
  Kiểm lại còn0 vhost/user mang prefix test event-verify-. Quyền này là kiểm
  metadata, không phải gửi thử vào vhost nghiệp vụ.
- Bằng chứng broker thật vẫn chỉ chứng minh transport. B16 backend và luồng
  Booking/Ticket/Payment xử lý/hủy/hoàn/thống kê vẫn có giới hạn tại các mục trên.

Log và JSON sanitized của lượt này ở `.superpowers/sdd/event-integration/`
(ignored trên workstation); đây không phải file có sẵn trong clone mới.

## 6. Điều chỉnh approve/publish — 31/10/2026

- Theo yêu cầu mới, approve chỉ chốt phí/metadata/trạng thái; publish mới ghi đủ
  Finance/Sales/Access trong transaction chuyển PUBLISHED. Finance giữ sourceFeeVersion
  và envelope aggregateVersion lúc duyệt; Sales/Access dùng phiên bản công bố.
- Test RED tái hiện hành vi cũ; sau sửa, toàn bộ **124 test PASS**,0 failure/error/skip;
  build/Spotless/SpotBugs PASS,0 bug/0 error, kết thúc22:50 Việt Nam ngày31/10.
  Có kiểm không ghi outbox khi approve, rollback khi từng append lỗi, retry/replay,
  concurrent publish và hủy trước publish không tạo configuration message.
- Docker local ban đầu tắt, đã khởi động để chạy PostgreSQL/RabbitMQ test cô lập.
  Không dùng DB/queue nghiệp vụ EC2 cho kiểm thử; không sửa worker/binding/schema,
  Gateway/Config hoặc code service khác. Review độc lập không có finding.
- Bằng chứng runtime/EC2 ở các mục trước thuộc30/10; chưa deploy/restart Event
  hoặc kiểm consumer E2E cho thay đổi31/10, không di chuyển/xóa message cũ.
  Log và baseline tại `.superpowers/sdd/event-publish-config/` (ignored).
