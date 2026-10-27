# Thiết kế runtime mua vé của Payment

- Ngày: 2026-10-27.
- Trạng thái: REVIEW_READY — builder check đã xong, chờ người dùng duyệt thiết kế; chưa cho phép code.
- Nơi thực hiện: module `services/payment-service`, trực tiếp trên nhánh `dev/tuan` tại checkout hiện tại.
- Quy mô: một chức năng liên service có lưu bền, nhiều transaction và phục hồi; cần thiết kế đầy đủ.

## 1. Mục đích và quyết định đã chốt

Hoàn thiện phần trách nhiệm của Payment trong hình người dùng cung cấp: nhận yêu cầu thanh toán, cung cấp đường dẫn VNPay, xác nhận khoản thu, phối hợp Booking chấp nhận đơn và Ticket phát vé, rồi kết thúc tiến trình mua. Chỉ triển khai Payment; các participant được thay bằng test double trong kiểm thử khi chưa có triển khai thật.

Người dùng đã chọn hướng 1: nâng cấp luồng hiện có thành runtime dùng chung, giữ contract B13/B14 và thực hiện trực tiếp trên `dev/tuan`. Đây chưa phải phê duyệt thiết kế này hoặc kế hoạch triển khai sẽ viết sau.

Các lựa chọn chi tiết, giới hạn của đợt và giá trị mặc định bên dưới là đề xuất của người viết, để người dùng kiểm tra khi duyệt. Không suy diễn rằng người dùng đã duyệt spec runtime ngày 23/10 hoặc spec Booking HTTP mock ngày 27/10; cả hai vẫn ghi chờ review.

Nguồn quy tắc: `docs/architecture/B13-api-and-event-contracts.md`, `B14-main-flow-sequences.md`, `docs/adr/ADR-002-mot-saga-thu-tien-phat-hanh-bu-tru.md`, DDL `docs/architecture/data/postgres/30-payment.sql` và migration đổi tên `services/99-table-prefix.sql` tính từ root repo. B13/B14 v0.2 vẫn REVIEW_READY; sử dụng làm mục tiêu tương thích, không thay trạng thái duyệt toàn hệ thống.

## 2. Hiện trạng đã đối chiếu

| Thành phần | Hiện trạng trên checkout |
| --- | --- |
| DTO, codec, invariant, canonical hash | Đã có; đang có thay đổi chưa commit của người dùng, cần tái sử dụng |
| GET Payment | Có JWT BUYER, ownership và `PaymentView`; poll theo requestId đọc inbox |
| StartPaymentProcessor/Store | Đã xử lý snapshot, replay, khóa PostgreSQL, attempt và URL; chỉ bật trong `payment-simulator` |
| BookingPaymentGuard | Port đã có; implementation hiện tại chỉ kiểm thời gian, chưa có HTTP guard runtime |
| VNPay | Có tạo URL cục bộ, xác minh IPN, query thanh toán, refund và callback controller có cờ bật |
| VerifiedPaymentHandler | Chưa có implementation lưu bền; không bật callback chỉ vì adapter có thể kiểm chữ ký |
| Charge, confirmation, saga, refund, inbox/outbox | Có entity/schema; còn thiếu các repository/use case/relay/worker cần thiết |
| Simulator | CLI/dev files đang được xóa trong index; configuration và các test còn tham chiếu lớp đã xóa hoặc chưa tồn tại |
| Broker | Config Server hiện mặc định exchange `business.ex`; đường dẫn definitions được README nhắc tới không tồn tại tại checkout này |

Chưa chạy build trong lượt thiết kế. Các tham chiếu thiếu là quan sát source, chưa phải kết luận từ compiler. Khi lập plan phải đo baseline, phân biệt lỗi có sẵn với lỗi mới; không xóa/skip test của người dùng để làm build xanh, không tự khôi phục các file đã được người dùng xóa.

## 3. Phạm vi và tiêu chí thành công

Trong đợt đề xuất:

- Runtime nhận `payment.start-requested.v1`, HTTP guard Booking, lưu snapshot/attempt/URL và kết quả inbox.
- Tiếp nhận IPN/query VNPay qua một use case ghi kết quả dùng chung; ghi charge thực và chọn một candidate/order.
- Một saga/order: WAITING_BOOKING → WAITING_TICKETS → COMPLETED; compensation tối thiểu cho tiền muộn, thu thừa và phát vé FAILED dứt điểm.
- Inbox/outbox, consumer ACK sau commit, relay có confirm/return, retry hữu hạn và recovery sau restart.
- Nghĩa vụ refund toàn phần/charge và worker dùng adapter hiện có. Mặc định tắt tác động refund thật; khi bật phải giữ nguyên định danh yêu cầu và có bằng chứng kết quả.
- Kiểm thử Payment với PostgreSQL/RabbitMQ riêng, Booking HTTP giả và participant MQ giả trong test; giữ API đọc và bảo mật hiện có.

Không thuộc đợt này: nghiệp vụ thật của Booking/Ticket/Event; giao diện hoặc ảnh QR thanh toán; phát vé/email/check-in; payout/đối soát organizer; financial-summary API; finance-config projection; luồng hủy sự kiện hàng loạt và event refund run; công cụ Booking HTTP mock độc lập; workflow orphan/sai tiền Q-07. Không nhận đã triển khai toàn bộ B13/B14 chỉ từ đợt này.

Không bind/ACK `event.cancelled.v1` hoặc `event.finance-configured.v1` bằng handler rỗng. Hủy sự kiện đầu cuối là phần chưa hỗ trợ của đợt; Booking vẫn phân xử tình trạng đơn khi nhận AcceptPayment. Các handler Payment phải tôn trọng cancellation marker nếu dữ liệu cục bộ đã có, không xóa marker hoặc phát vé sau marker. Không tuyên bố đã xử lý đầy đủ một sự kiện hủy sau khi mua đã COMPLETED.

Thành công nghĩa là cùng một đầu vào/retry không tạo khoản thu, confirmation, nghĩa vụ refund hoặc lệnh phát vé mới; restart tiếp tục từ dữ liệu đã commit; chỉ Booking completion hợp lệ mới kết thúc saga; GET vẫn kiểm chủ đơn. Có lớp/bean hoặc health UP không thay cho các bằng chứng này.

## 4. Các hướng đã cân nhắc

1. **Nâng cấp runtime dùng chung — người dùng chọn.** Giữ StartPaymentProcessor/Store, codec, mapper và VNPay; bổ sung các use case thiếu. Tách adapter dev khỏi nghiệp vụ dùng chung. Giảm logic trùng, nhưng phải kiểm lại profile và regression của các caller hiện có.
2. **Viết runtime mới cạnh simulator.** Ít đụng simulator, nhưng lặp rule attempt, snapshot và idempotency, tăng chi phí bảo trì. Không chọn.

Giữ cấu trúc theo tầng và service chia theo nghiệp vụ của Payment hiện tại. Không chuyển toàn bộ sang một kiến trúc/package mới, không tạo interface/Impl cho mọi service. Chỉ dùng port ở ranh giới thực sự cần thay adapter.

## 5. API và contract

POST công khai `/api/v1/orders/{id}/payment-attempts` thuộc Booking. Payment nhận Envelope<StartPayment> qua broker, không thêm POST công khai trùng đường dẫn. GET `/api/v1/orders/{id}/payment?requestId=...` giữ nguyên wire format, JWT, ownership, mã lỗi và transaction đọc nhất quán.

| Hướng | messageType | Tác dụng tại Payment |
| --- | --- | --- |
| Nhận từ Booking | payment.start-requested.v1 | Tạo/reject attempt, kết quả lưu inbox để poll |
| Gửi Booking | booking.accept-payment.v1 | Xin chấp nhận candidate charge |
| Nhận từ Booking | booking.payment-decision.v1 | ACCEPTED cho phép gửi phát vé; LATE/DUPLICATE/CANCELLED xử lý refund |
| Gửi Ticket | ticket.issue.v1 | IssueTickets dùng đúng frozen purchase |
| Nhận từ Ticket | ticket.issuance-result.v1 | Chỉ FAILED dứt điểm kích hoạt compensation; COMPLETED được validate rồi inbox/no-op |
| Gửi Booking | booking.issuance-result.v1 | Chỉ FAILED theo contract hiện có |
| Nhận từ Booking | booking.order-completed.v1 | Kiểm đủ tuple và hoàn tất saga |

StartPaymentResult chỉ lưu `inbox.response_payload`, không phát một messageType kết quả mới. Money/version vẫn là string, timestamp UTC, IDs UUID. Tái sử dụng canonical hash hiện có; không sửa hợp đồng để dễ ghép fake.

HTTP guard gọi `POST /internal/v1/orders/{id}/freeze-payment` với buyerSubject từ snapshot, ngoài transaction Payment. Đề xuất adapter nhận `{purchase: PurchaseSnapshot}`; đây là wire shape đã dùng trong spec mock nhưng chưa được Booking thật xác nhận. Decode strict, kiểm invariant và so orderId, buyerSubject, canonical hash với message; không tự tính lại giá. 404 RESOURCE_NOT_FOUND và 409 ORDER_EXPIRED/ORDER_NOT_PAYABLE là rejection bền; 401/403, 5xx, timeout, response sai là lỗi hạ tầng/contract để retry, không tạo REJECTED giả.

Đề xuất service identity: bearer access token client-credentials lấy từ token endpoint cấu hình, cache tới trước hạn, timeout hữu hạn; không dùng token BUYER hoặc secret mặc định. Fake issuer/Booking chỉ dùng test. Audience/scope cụ thể cấu hình theo deployment Booking; không khẳng định hệ thống IAM/Booking đã provision. Khi bật runtime mà thiếu cấu hình credential/URL bắt buộc, fail startup.

## 6. Khởi tạo thanh toán và profile

Replay inbox cùng ID/hash chạy trước guard, kể cả Booking hiện đã hết hạn hoặc không truy cập được. Sau guard, transaction khóa message và order, kiểm lại snapshot/hash, key và state để tránh cạnh tranh. Giữ snapshot hợp lệ kể cả business rejection để GET kiểm ownership được.

Giữ các hàng rào hiện có: cùng requestId khác hash bị chặn; cùng order/requestKey khác requestHash bị chặn; một unfinished attempt/order; confirmation đã tồn tại thì request mới bị từ chối. Guard không gia hạn `expiresAt`; callback của attempt cũ vẫn xử lý dù start mới bị reject.

Tạo/lưu attempt và URL VNPay cùng transaction hiện có là hợp lệ vì `createPayment` hiện chỉ ký URL cục bộ, không gọi mạng. Không thay bằng provider khác có network trong transaction này. URL replay giữ nguyên attempt identity. Request mới trong PENDING/UNKNOWN trả PAYMENT_IN_PROGRESS; không tạo lần thu mới chỉ vì timeout.

Processor/Store trở thành nghiệp vụ dùng chung, kích hoạt khi runtime hoặc chế độ test/dev tương ứng được chọn, không còn chỉ bị khóa trong simulator. Runtime thật và adapter guard giả không được cùng hoạt động. `local-foundation` tiếp tục tắt consumer/relay/workers. `payment-simulator` chỉ dùng cho dev nếu còn được giữ; không tự phục hồi CLI/container/init SQL đã xóa. Phần cấu hình còn tham chiếu class đã xóa phải được xử lý bằng tách topology dev khỏi nghiệp vụ dùng chung trong plan, không kéo logic simulator vào runtime.

## 7. Kết quả provider, charge và saga

`RecordPaymentResultService` (tên đề xuất) implements VerifiedPaymentHandler. IPN và worker query gọi cùng service, xác minh lại với attempt/snapshot đã persist. Adapter xác thực chữ ký chưa thay cho các kiểm merchant, attempt/order, amount/currency và kết quả.

- SUCCEEDED: lưu charge bằng unique(provider, merchantAccount, providerChargeId); kiểm identity và nội dung trước coi là duplicate. Cùng identity khác attempt/order/amount/currency/paidAt là conflict, rollback và không ACK success.
- Callback trùng đúng nội dung không tạo hiệu ứng mới. Một providerChargeId khác vẫn là khoản thu thực cần lưu dù attempt đã SUCCEEDED; thu thừa có nghĩa vụ refund riêng, không thay candidate saga.
- FAILED có bằng chứng dứt điểm: đóng attempt khi chưa có khoản thu thành công. PENDING/UNKNOWN không tạo charge/refund; UNKNOWN giữ unfinished. Kết quả cũ không hạ SUCCEEDED về FAILED/PENDING.
- Callback success đến sau kết quả FAILED vẫn phải được tiếp nhận như bằng chứng thu thực nếu liên kết/tiền hợp lệ; Booking phân xử deadline. Không bỏ tiền thực do state cục bộ đã terminal.
- Browser Return chỉ hiển thị kết quả xác thực trung tính và hướng poll; không ghi charge/saga.

Một candidate charge được chọn một lần/order dưới khóa; charge khác hoàn riêng, kể cả saga candidate đã kết thúc. Candidate chỉ có confirmation sau Booking ACCEPTED.

| Trạng thái và đầu vào | Cùng transaction nghiệp vụ + inbox/outbox |
| --- | --- |
| Chưa saga + charge hợp lệ | WAITING_BOOKING, commandId bền, outbox AcceptPayment |
| WAITING_BOOKING + ACCEPTED đúng candidate | Confirmation; WAITING_TICKETS; commandId mới và outbox IssueTickets |
| WAITING_BOOKING + LATE/CANCELLED/DUPLICATE hợp lệ | Nghĩa vụ refund đúng reason; candidate saga COMPENSATING |
| WAITING_TICKETS + OrderCompleted hợp lệ | COMPLETED; lưu tuple completion để đối chiếu replay sau |
| WAITING_TICKETS + IssuanceResult FAILED dứt điểm | Refund TICKET_ISSUANCE_FAILED; outbox BookingIssuanceResult FAILED; COMPENSATING |
| COMPENSATING + candidate refund SUCCEEDED và lệnh Booking cần thiết đã bền | COMPENSATED |
| Replay khớp | No-op; không tạo message/confirmation/refund lần hai |

PaymentDecision phải khớp order/candidate và invariant acceptedChargeId. DUPLICATE phải trỏ charge khác; không đổi candidate sang acceptedChargeId không có bằng chứng cục bộ. Nếu kết quả participant mâu thuẫn với dữ liệu Payment, giữ trạng thái hiện tại và báo conflict, không phát vé theo suy đoán. Nếu có cancellation marker cục bộ, chuyển xử lý EVENT_CANCELLED và không phát IssueTickets mới; saga đang COMPLETED không bị hồi sinh.

OrderCompleted phải đến từ đường consumer dành cho Booking, khớp confirmation, charge và ticketCount bằng tổng quantity frozen purchase. Lưu order/charge/issuanceId/ticketCount đã nhận trong inbox.response_payload. MessageId khác với cùng tuple là replay nghiệp vụ; tuple khác là conflict, kể cả saga đã COMPLETED. Giữ lịch sử completion phục vụ đối chiếu, không thêm cột issuanceId ngầm vào saga.

Thông điệp hợp lệ tới trước dependency chưa commit được retry. Không ACK bỏ vì chưa thấy saga. COMPENSATING/COMPENSATED không trở lại COMPLETED bởi completion tới trễ. Ticket COMPLETED qua queue/probe không được tự kết thúc saga.

## 8. Refund tối thiểu và giới hạn VNPay

Một Refund/charge, full amount/currency của charge; giữ reason đã persist. Charge thừa dùng DUPLICATE_PAYMENT; candidate bị Booking từ chối vì hết hạn/buyer hủy dùng LATE_PAYMENT; nếu có marker event cancellation thì theo B14; thất bại phát vé phải là FAILED có terminal fence, không phải timeout.

Worker claim refund trong transaction ngắn, tạo RefundAttempt và request identity trước khi gọi provider ngoài transaction. Dùng requestId UUID đã lưu, requestKey tương ứng, startedAt làm requestedAt và operator cố định `payment-service`; context retry không thay theo đồng hồ hoặc env mới. Unique unfinished refund_attempt ngăn hai yêu cầu đang chưa rõ cho cùng nghĩa vụ.

Provider SUCCEEDED mới đóng obligation và đánh saga COMPENSATED khi đủ điều kiện. FAILED đã xác định đóng attempt và giữ nghĩa vụ FAILED để retry có backoff trên cùng refund. Timeout/PROCESSING/UNKNOWN giữ unfinished attempt; lần sau query cùng identity trước, không gửi refund mới. Không reset nghĩa vụ chỉ vì tắt/bật worker.

Adapter hiện tại chưa tra cứu được refund theo định danh (`queryRefund` trả UNKNOWN/REFUND_LOOKUP_UNAVAILABLE). Vì vậy sau refund không rõ kết quả, hệ thống giữ PROCESSING, báo cần đối soát và không hứa tự hoàn tất. Không thêm API admin đánh thành công thủ công trong đợt này. Refund-disabled vẫn lưu nghĩa vụ và hiển thị qua GET, không đánh COMPENSATED giả. Với provider fake có kết quả xác định, test phải chứng minh đúng các nhánh trạng thái; không dùng test đó để kết luận VNPay refund thật đã nghiệm thu.

## 9. Inbox, outbox, broker và recovery

Inbox dùng cùng consumer name/version và canonical hash hiện có; commit inbox + domain effect + response/outbox trước ACK. Conflict hash/poison contract không retry như lỗi hạ tầng. Source không phải một field do client tự khai: dựa trên queue, exchange/binding và tài khoản/ACL đã provision; payload messageType phải khớp endpoint consumer. Không mở consumer tài chính lên exchange mà browser ghi được.

Outbox.payload lưu toàn Envelope JSONB; các cột metadata phải khớp envelope. Relay publish persistent, mandatory, correlated confirm và xử lý return. Chỉ đặt publishedAt sau confirm ACK và không có return. Confirm timeout/NACK/unroutable giữ pending. Crash sau confirm trước update có thể phát lặp; đây là at-least-once và inbox xử lý trùng.

Không giữ transaction/row lock khi chờ broker. Claim batch bằng SKIP LOCKED, tăng attempts và đặt nextAttemptAt làm lease ngắn trong transaction; dùng attempts + due value đã claim làm token CAS khi ghi kết quả. Lease hết có thể claim lại; worker cũ không được ghi đè claim mới. MessageId/envelope giữ nguyên khi retry; header delivery retry tách khỏi hash.

Queue mặc định cấu hình theo các tên đã dùng trong README: payment.start.q, payment.decision.q, payment.ticket-issuance-failed.q, payment.order-completed.q; không coi tên queue chứa failed là bằng chứng queue chỉ nhận FAILED. Exchange lấy `spring.rabbitmq.template.exchange`/RABBITMQ_BUSINESS_EXCHANGE, mặc định hiện có business.ex; routing key là messageType B13. Không hardcode đổi cả hệ thống sang flash-ticket.v1 từ ví dụ tài liệu.

Không có broker definitions trên checkout này. Plan phải cung cấp mô tả topology/ACL và một artifact cấu hình mẫu trong module Payment, có queue retry riêng từng consumer và DLQ/parking. App runtime không tự tạo/xóa binding/policy trên broker đang dùng chung. Người vận hành phải provision trước khi bật runtime; integration test tự dùng vhost/topology riêng, không unbind queue dùng chung.

Consumer retry transient bằng republish tới retry queue 5s, 30s, 300s rồi parking, giữ envelope và destination consumer; ACK bản gốc chỉ sau republish confirm và không return. Poison input park ngay. Broker/DB outage diện rộng pause/backoff listener, không vòng lặp NACK tức thì hoặc biến lỗi thành REJECTED. Parking có cảnh báo và runbook replay nguyên IDs; không tự đổi payload cùng ID. Mức retry là đề xuất cấu hình, không bằng chứng nghiệp vụ đã thất bại.

Saga recovery đọc nextAttemptAt, ưu tiên relay pending; khi cần gửi lại command đã publish, sử dụng bản outbox và cùng commandId, không tạo lệnh phát vé với ID mới. Participant fake phải mô phỏng quyết định/outbox bền và việc replay response khi command lặp; broker confirm đơn thuần không chứng minh participant đã xử lý. WAITING_TICKETS chờ Booking OrderCompleted; không suy FAILED từ timeout, 404 hoặc PENDING. Không thêm probe Ticket HTTP trong đợt này để tránh phụ thuộc một API phục hồi chưa hoàn thiện.

Worker query payment dùng context persist và kết quả qua RecordPaymentResultService. Query result quá hạn không tự suy FAILED nếu provider chưa có bằng chứng. Mạng chạy ngoài transaction; chống các worker cạnh tranh bằng operation lock/session có vòng đời rõ ràng và state/version CAS. Lock giải phóng khi session chết, không rò vào pool. Lịch due của saga/outbox lưu DB; tuổi pending payment/refund suy từ attempt history bền. Mặc định poll 5s, batch 20, backoff trần 300s, cảnh báo pending trên 15 phút; đây là lựa chọn người viết để kiểm thử, không cam kết SLA.

## 10. Transaction, schema và tổ chức code

Khóa ghi nhất quán theo event → order → saga/attempt/charge → refund. Dùng advisory namespace event/order/message khi chưa có row, sau đó row lock cần thiết; không chèn một event_finance_snapshot rỗng trái CHECK chỉ để lấy khóa. Event marker và command phát vé phải được đọc/quyết định dưới cùng ranh giới khóa. Mọi writer tương lai xử lý cancellation phải tuân cùng thứ tự.

Không gọi Booking, token endpoint, broker, query/refund provider khi đang giữ transaction khóa hàng. Tách orchestration và phương thức transaction sang Spring bean/TransactionTemplate; private helper không tự tạo transaction qua self-invocation. Unique/optimistic-lock conflict rollback xong mới đọc lại bản thắng bằng transaction mới; không tiếp tục trên transaction PostgreSQL đã aborted.

Tái sử dụng entity/UUID/Instant/enum/DDL và tên tbl_ hiện có. Không dùng Hibernate update/init, không đổi tiền sang double, không tạo saga thứ hai. DDL hiện có đủ cho thiết kế đề xuất; plan phải kiểm chứng persist context/CAS/completion lookup bằng PostgreSQL. Nếu thật sự cần migration thì trình phần thay đổi schema có mục tiêu trước khi code, không âm thầm đổi database đang dùng.

Các nhóm thêm/mở rộng dự kiến: client/booking, service/start, service/payment (record result), service/saga, service/refund, messaging/consumer/inbox/outbox, repository và worker. Giữ gateway/vnpay làm adapter. Controller/consumer/worker mỏng; mapper không query hoặc sinh ID/time; service dùng helper chung cho dedupe, transition, outbox, context và reason arbitration khi cùng quy tắc thực sự lặp.

Theo cinema-payment-style: constructor injection với RequiredArgsConstructor/FieldDefaults; Service/Transactional đúng ranh giới; Slf4j khi có log; record/constraint cho DTO; MapStruct shared config; entity giữ factory/constructor JPA và Version. Không thêm Swagger annotation khi pom chưa hỗ trợ, không refactor phong cách toàn module ngoài phạm vi.

## 11. Cấu hình, bảo mật và giới hạn sửa file

Thêm cấu hình có namespace Payment trong module, gắn env và cờ runtime/worker tường minh. Khi bật runtime cần gateway thật, Booking client/credential và messaging topology phù hợp; thiếu dependency phải lỗi rõ, không fallback sang guard thời gian hoặc fake. Không tự bật callbacks/refund hoặc thay merchant thật trong lượt code. Fake gateway/Booking/Ticket/issuer chỉ trong test hoặc dev cô lập, không production.

Các timeout, queue names, worker batch/backoff và exchange cấu hình được; secret chỉ từ environment/secret store. Giữ Config Server import hiện có. Đề xuất các default runtime properties trong application.yaml của Payment, không sửa shared config hay module Config Server/Gateway; giá trị remote có thể override. Runtime flag không được ghi đè từ simulator bằng cách bật đồng thời hai chế độ; validation profile phải chặn cấu hình đó.

Không thay public route/security, không thêm endpoint sửa state hoặc public refund. Log chỉ IDs/correlation và mã lỗi an toàn, không raw IPN/token/URL có chữ ký, email hay snapshot đầy đủ. Chỉ truy cập DB Payment bằng application role, không đọc DB participant.

Chỉ sửa trong services/payment-service, gồm tài liệu/config/test/artifact topology của module. Không dùng git reset/restore/clean lên thay đổi có sẵn, không stage/commit file của người dùng. Tài liệu này có thể commit riêng bằng path, giữ nguyên staged deletions đang có. Chưa có quyền push/deploy hoặc provision broker/DB dùng chung.

## 12. Kiểm chứng và đầu ra

1. Ghi baseline branch/status và build sau khi plan được duyệt. Các test BookingMockServer/MockBookingStore đang chưa có implementation là dependency công việc có sẵn; không xóa/loại test. Plan phải nêu cách phối hợp phần chưa hoàn thiện trước khi hứa whole-module verify xanh.
2. Unit/service tests kiểm replay trước guard, key/hash conflict, deadline/rejection, từng transition, paidAt/content conflict, callback trùng và charge khác trên cùng attempt.
3. HTTP client tests kiểm token/credential, timeout, 401/403/404/409/5xx, JSON/snapshot mismatch và không giữ transaction khi gọi mạng. JWT/ownership GET và Return không ghi state có regression.
4. PostgreSQL thật kiểm unique/partial indexes, transaction rollback, concurrent callback/command, atomic inbox/outbox, CAS lease, completion tuple với messageId khác và không tăng obligations khi lặp.
5. RabbitMQ thật trên vhost riêng kiểm ACK sau commit, return/NACK/confirm timeout, retry/parking đúng consumer và duplicate khi crash sau confirm. Không lấy unit mock confirm làm bằng chứng broker.
6. Test flow Payment đầy đủ: StartPayment → URL đã lưu → signed IPN/query result → AcceptPayment → PaymentDecision ACCEPTED → IssueTickets → Ticket COMPLETED chỉ no-op tại Payment → Booking OrderCompleted → saga COMPLETED; lặp/restart từng ranh giới không phát hành lần hai. Có các nhánh LATE, DUPLICATE, Ticket FAILED và refund UNKNOWN không gửi mới.
7. Maven verify với JDK 21/UTC, Spotless/SpotBugs và profile integration phù hợp. Không giảm quality gate. Tài liệu chỉ ghi PASS/FAIL/NOT_RUN theo lần chạy thực; test double không chứng minh Booking/Ticket thật, VNPay thật hoặc platform E2E.

Đầu ra sau triển khai: code/test trong module, hướng dẫn cấu hình/chạy/poll, topology mẫu và runbook retry/refund UNKNOWN, báo cáo các kiểm và giới hạn thực. Không tự dựng lại DB hoặc broker của người dùng.

## 13. Chỗ người viết đề xuất để người dùng kiểm tra

- Gộp compensation tối thiểu (muộn/trùng/phát vé thất bại) vào đợt mua vé để không bỏ quên khoản tiền đã thu; refund thật vẫn có cờ riêng và giới hạn tra cứu của VNPay phải hiện rõ.
- Để hủy sự kiện hàng loạt, finance summary và payout sang đợt riêng; không nhận đã hỗ trợ trọn vẹn phần đó của B13/B14.
- HTTP guard wire shape `{purchase: ...}` và client-credentials là adapter đề xuất, chưa được participant thật xác nhận; cần contract test trước tích hợp.
- Giữ schema, API đọc và dependency stack hiện có; điều chỉnh runtime activation và tách phụ thuộc simulator cũ trong phạm vi Payment.
- Thông số poll/batch/retry là mặc định thử nghiệm, builder có thể điều chỉnh có kiểm chứng; không được thay quy tắc tiền, transaction hoặc quyền.

Builder check ngày 2026-10-27: một reviewer độc lập chỉ đọc đã trả về `nothing important left`; không phát sinh câu hỏi cần người dùng quyết định để lập kế hoạch. Đây là kiểm tính đủ của thiết kế, không phải bằng chứng code đã chạy hoặc các adapter giả đã tích hợp với participant thật.

Chỉ sau khi thiết kế được người dùng duyệt mới dùng superpowers:writing-plans để viết plan file/class/helper/annotation cụ thể; theo AGENTS.md của Payment, plan cũng cần được duyệt trước khi sửa Java.
