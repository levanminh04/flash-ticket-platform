# Thiết kế runtime mua vé của Payment

> Phần tích hợp cổng thanh toán đã được người dùng yêu cầu gỡ ngày 2026-10-28. Các bước cấu hình/callback/refund của cổng trong tài liệu này là lịch sử, không được thực hiện hoặc khôi phục. Hợp đồng DTO/message và mô hình tài chính hiện có được giữ nguyên.


> **Thay đổi phạm vi ngày 2026-10-27:** Người dùng yêu cầu xóa toàn bộ simulator trước. Các mô tả hiện trạng và đề xuất tái sử dụng Processor/Store hoặc giữ test simulator bên dưới là lịch sử thiết kế, cần cập nhật trước khi triển khai runtime; không khôi phục simulator theo tài liệu này.

- Ngày: 2026-10-27.
- Trạng thái: ARCHITECTURE_ACCEPTED — người dùng đã chọn HTTP lấy URL và RabbitMQ sau thu tiền, yêu cầu triển khai; kế hoạch chi tiết cần duyệt theo AGENTS.md trước khi sửa Java.
- Nơi thực hiện: module `services/payment-service`, trực tiếp trên nhánh `dev/tuan` tại checkout hiện tại.
- Quy mô: một chức năng liên service có lưu bền, nhiều transaction và phục hồi; cần thiết kế đầy đủ.

## 1. Mục đích và quyết định đã chốt

Hoàn thiện phần trách nhiệm của Payment trong hình người dùng cung cấp: nhận yêu cầu thanh toán, cung cấp đường dẫn VNPay, xác nhận khoản thu, gửi yêu cầu và xử lý response/message từ các service ngoài. Người dùng đã làm rõ chỉ quan tâm phần Payment gửi/nhận và xử lý trạng thái của mình. Booking/Ticket là dependency bên ngoài; kiểm thử chỉ cấp response/message định trước, không xây nghiệp vụ hoặc hệ thống giả lập có trạng thái của các service đó.

Người dùng đã chọn hướng 1: nâng cấp luồng hiện có thành runtime dùng chung và thực hiện trực tiếp trên `dev/tuan`. Sau khi đọc bản thiết kế đầu, người dùng xác nhận luồng khởi tạo theo hình: frontend gọi Payment lấy URL, Payment mới gọi Booking lấy PurchaseSnapshot. Sau khi so sánh hai cách, người dùng trả lời "oke vậy triển khai đi" với đề xuất HTTP cho khởi tạo, RabbitMQ cùng saga/outbox sau khi nhận tiền. Đây là quyết định kiến trúc để lập kế hoạch triển khai; các chi tiết kỹ thuật được trình trong plan, không yêu cầu chọn lại kiến trúc.

Các lựa chọn chi tiết, giới hạn của đợt và giá trị mặc định bên dưới là đề xuất của người viết, để người dùng kiểm tra khi duyệt. Không suy diễn rằng người dùng đã duyệt spec runtime ngày 23/10 hoặc spec Booking HTTP mock ngày 27/10; cả hai vẫn ghi chờ review.

Nguồn ưu tiên cho khởi tạo là xác nhận của người dùng và hình sequence bước 15–26: frontend → Payment → Booking → Payment trả URL/QR. B13/B14 hiện mô tả một đầu vào khác (POST vào Booking rồi MQ sang Payment); không dùng đoạn đó để ghi đè luồng đã chọn. Các invariant tiền, snapshot, idempotency và message sau thu tiền tiếp tục dùng B13/B14, ADR-002 và DDL hiện có. Với nhánh thành công sau phát vé, giữ contract Ticket → Booking → OrderCompleted → Payment; không thêm một success-message Payment → Booking chưa có trong B13. Khác biệt với mũi tên bước 47 trong hình được ghi rõ để kiểm khi duyệt plan. Không tự sửa tài liệu baseline toàn repo hoặc nhận đã đồng bộ contract participant.

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

Baseline ngày 2026-10-27: Maven test-compile bằng JDK 21.0.12/UTC thất bại ở main compile vì PaymentSimulatorConfiguration import package dev.booking đã xóa và không resolve QUEUE. Log ở target/payment-runtime-baseline.log. Gỡ phụ thuộc này trong Payment; không xây lại Booking mock để sửa test công cụ bên ngoài phạm vi. Giữ nguyên bốn test legacy thiếu source, chọn profile payment-only-tests với danh sách loại trừ tường minh chỉ bốn file đó và báo riêng default whole-module baseline. Không loại test Payment hoặc nhận toàn bộ repo đã pass.

## 3. Phạm vi và tiêu chí thành công

Trong đợt đề xuất:

- Public POST tại Payment nhận orderId/provider từ frontend, gọi Booking lấy snapshot đã freeze và kiểm quyền, lưu snapshot/attempt/URL rồi trả URL ngay trong response.
- Tiếp nhận IPN/query VNPay qua một use case ghi kết quả dùng chung; ghi charge thực và chọn một candidate/order.
- Một saga/order: WAITING_BOOKING → WAITING_TICKETS → COMPLETED; compensation tối thiểu cho tiền muộn, thu thừa và phát vé FAILED dứt điểm.
- Inbox/outbox, consumer ACK sau commit, relay có confirm/return, retry hữu hạn và recovery sau restart.
- Nghĩa vụ refund toàn phần/charge và worker dùng adapter hiện có. Mặc định tắt tác động refund thật; khi bật phải giữ nguyên định danh yêu cầu và có bằng chứng kết quả.
- Kiểm thử Payment với PostgreSQL/RabbitMQ riêng; HTTP response và MQ message đầu vào được định trước, quan sát request/command Payment gửi ra. Giữ API đọc và bảo mật hiện có.

Không thuộc đợt này: mọi implementation Booking/Ticket/Event, kể cả mock có tạo/hủy đơn, state/store, giữ chỗ, tính/chốt giá hoặc freeze logic; giao diện/ảnh QR thanh toán; phát vé/email/check-in; payout/đối soát organizer; financial-summary API; finance-config projection; hủy sự kiện hàng loạt/event refund run; workflow orphan/sai tiền Q-07. JSON fixture và response stub thuần để kiểm phần gửi/nhận của Payment được phép. Không nhận đã triển khai toàn bộ B13/B14 chỉ từ đợt này.

Không bind/ACK `event.cancelled.v1` hoặc `event.finance-configured.v1` bằng handler rỗng. Hủy sự kiện đầu cuối là phần chưa hỗ trợ của đợt; Booking vẫn phân xử tình trạng đơn khi nhận AcceptPayment. Các handler Payment phải tôn trọng cancellation marker nếu dữ liệu cục bộ đã có, không xóa marker hoặc phát vé sau marker. Không tuyên bố đã xử lý đầy đủ một sự kiện hủy sau khi mua đã COMPLETED.

Thành công nghĩa là cùng một đầu vào/retry không tạo khoản thu, confirmation, nghĩa vụ refund hoặc lệnh phát vé mới; restart tiếp tục từ dữ liệu đã commit; chỉ Booking completion hợp lệ mới kết thúc saga; GET vẫn kiểm chủ đơn. Có lớp/bean hoặc health UP không thay cho các bằng chứng này.

## 4. Các hướng đã cân nhắc

1. **Nâng cấp runtime dùng chung — người dùng chọn.** Giữ StartPaymentProcessor/Store, codec, mapper và VNPay; bổ sung các use case thiếu. Tách adapter dev khỏi nghiệp vụ dùng chung. Giảm logic trùng, nhưng phải kiểm lại profile và regression của các caller hiện có.
2. **Viết runtime mới cạnh simulator.** Ít đụng simulator, nhưng lặp rule attempt, snapshot và idempotency, tăng chi phí bảo trì. Không chọn.

Giữ cấu trúc theo tầng và service chia theo nghiệp vụ của Payment hiện tại. Không chuyển toàn bộ sang một kiến trúc/package mới, không tạo interface/Impl cho mọi service. Chỉ dùng port ở ranh giới thực sự cần thay adapter.

## 5. API và contract

Theo sequence đã xác nhận, POST khởi tạo thuộc **Payment**. Đường dẫn cụ thể đề xuất là `POST /api/v1/orders/{id}/payment-attempts`, body `{provider:'VNPAY'}`, bearer JWT BUYER và Idempotency-Key UUID. Frontend không cung cấp giá hoặc purchase snapshot. Hình chốt hướng lời gọi, không chốt tên route; đường dẫn này là đề xuất của người viết.

Payment xác thực JWT → replay yêu cầu đã xử lý nếu có → gửi request lấy snapshot từ Booking → lưu snapshot/attempt/URL → trả `200 ApiResponse<PaymentStart>` trực tiếp. data dùng DTO PaymentStart hiện có: orderId, attemptId, attemptState, amount, currency, paymentUrl và expiresAt. HTTP timeout/lỗi hạ tầng trả 503 ApiResponse<Problem>, lỗi nghiệp vụ dùng 404/409/422 đúng nguyên nhân; không trả 202 receipt rồi buộc frontend đợi message start.

GET `/api/v1/orders/{id}/payment` giữ vai trò đọc trạng thái sau đó và sau Return; trả ApiResponse<PaymentView> theo yêu cầu mới, giữ DTO trong data, JWT, ownership và transaction đọc. Query requestId hiện có được giữ để tương thích các kết quả MQ/dev trước đây; POST mới không yêu cầu frontend dùng requestId để lấy URL, startRequest có thể null.

Theo mẫu controller người dùng yêu cầu, API nghiệp vụ dùng ApiResponse<T> gồm data/success/message, xây bằng builder qua helper chung; lỗi dùng success=false và data=Problem, giữ HTTP status/code/correlation/violations. Đây là thay đổi có chủ đích đối với lớp bọc response HTTP, frontend đọc data.paymentUrl; không bọc message MQ hay dữ liệu inbox. Mọi endpoint có Tag/Operation summary/description tiếng Việt và tài liệu request/response/status đầy đủ. Buyer APIs khai báo bearerAuth, PreAuthorize BUYER và bật method security, tiếp tục kiểm chủ đơn. Log chỉ định danh an toàn. IPN VNPay giữ chính xác ACK `{RspCode,Message}`, Return giữ HTML; cả hai có tài liệu OpenAPI riêng và không yêu cầu bearer của người mua. Bổ sung springdoc tương thích Boot 4, cấu hình tài liệu local/test và kiểm bằng OpenAPI thực sự sinh ra.

Không bật consumer production `payment.start-requested.v1` làm đầu vào thứ hai cho cùng luồng; không giả một message có nguồn Booking để gọi lại processor cũ. Có thể giữ adapter MQ trong test/dev nếu còn caller, nhưng phải tái sử dụng core tạo attempt và tách consumer namespace. Các message bên dưới chỉ thuộc phần điều phối sau khi nhận tiền.

| Hướng | messageType | Tác dụng tại Payment |
| --- | --- | --- |
| Gửi Booking | booking.accept-payment.v1 | Xin chấp nhận candidate charge |
| Nhận từ Booking | booking.payment-decision.v1 | ACCEPTED cho phép gửi phát vé; LATE/DUPLICATE/CANCELLED xử lý refund |
| Gửi Ticket | ticket.issue.v1 | IssueTickets dùng đúng frozen purchase |
| Nhận từ Ticket | ticket.issuance-result.v1 | Chỉ FAILED dứt điểm kích hoạt compensation; COMPLETED được validate rồi inbox/no-op |
| Gửi Booking | booking.issuance-result.v1 | Chỉ FAILED theo contract hiện có |
| Nhận từ Booking | booking.order-completed.v1 | Kiểm đủ tuple và hoàn tất saga |

StartPaymentResult/Envelope<StartPayment> hiện có được giữ cho caller MQ/dev nếu cần; không ép HTTP POST thành hợp đồng Booking phát StartPayment. Money/version vẫn là string, timestamp UTC, IDs UUID. Tái sử dụng canonical hash hiện có; không thêm messageType kết quả.

Phía Payment dựng HTTP request lấy snapshot, với endpointPath cấu hình được (default đề xuất `/internal/v1/orders/{id}/freeze-payment`) và buyerSubject lấy từ JWT đã xác minh; gửi ngoài transaction Payment rồi nhận `{purchase: PurchaseSnapshot}`. Route/shape này chỉ mô tả contract client cần gửi/nhận ở bước 17–21. Cách Booking tra dữ liệu, kiểm đơn, giữ chỗ hoặc freeze không được triển khai hay tái hiện trong phạm vi này. Payment giữ snapshot/expiresAt hợp lệ đã nhận và xử lý retry của mình.

Decode strict, kiểm invariant, orderId phải khớp path và buyerSubject phải khớp JWT; snapshotHash so với bản Payment đã lưu nếu đã tồn tại, không tự tính lại giá từ frontend. 404 RESOURCE_NOT_FOUND và 409 ORDER_EXPIRED/ORDER_NOT_PAYABLE được chuyển thành lỗi nghiệp vụ tương ứng; 401/403 từ service credential, 5xx, timeout hoặc response sai là lỗi hạ tầng/contract, trả 503 an toàn và cho retry cùng key, không tạo attempt hoặc trả URL thành công giả. Booking đã freeze nhưng Payment chưa commit thì retry gọi lại cùng frozen snapshot.

Đề xuất service identity: bearer access token client-credentials lấy từ token endpoint cấu hình, cache tới trước hạn, timeout hữu hạn; không dùng token BUYER hoặc secret mặc định. Fake issuer/Booking chỉ dùng test. Audience/scope cụ thể cấu hình theo deployment Booking; không khẳng định hệ thống IAM/Booking đã provision. Khi bật runtime mà thiếu cấu hình credential/URL bắt buộc, fail startup.

## 6. Khởi tạo thanh toán và profile

HTTP dedupe dùng namespace riêng `payment.http-start.v1` trên bảng inbox hiện có, messageId bằng Idempotency-Key UUID. payloadHash là requestHash canonical của actor từ JWT + orderId từ path + provider; không phải envelopeHash của một message MQ. response_payload lưu record nội bộ gồm actor/order, HTTP status, mã lỗi hoặc PaymentStart đã trả. Không đưa record nội bộ nguyên khối ra frontend. Actor khác cùng key nhận lỗi không tiết lộ tài nguyên; cùng actor đổi order/provider dưới key cũ bị 409 IDEMPOTENCY_CONFLICT. Plan phải bổ sung enum/mapping lỗi cần thiết, không dùng lại namespace của MQ.

Replay bản đã commit chạy trước gọi Booking, kể cả Booking hiện đã hết hạn hoặc không truy cập được; trả cùng attempt identity/URL/kết quả đã lưu. Khi chưa có record, Booking được gọi ngoài transaction. Sau đó transaction khóa dedupe key rồi event/order, đọc lại record để chống hai request cạnh tranh, kiểm snapshot/key/state, tạo attempt/URL và lưu response cùng commit. Snapshot hợp lệ do Booking trả được lưu kể cả business rejection tiếp theo tại Payment để GET kiểm ownership. Lỗi xác thực, input sai hoặc lỗi Booking chưa có snapshot không cần tạo record thành công; không persist lỗi hạ tầng thành rejection vĩnh viễn.

Giữ các hàng rào hiện có: cùng order/requestKey khác requestHash bị chặn; một unfinished attempt/order; confirmation đã tồn tại thì request mới bị từ chối. Các adapter MQ/dev nếu giữ vẫn kiểm messageId/envelopeHash riêng. Booking không gia hạn expiresAt; callback của attempt cũ vẫn xử lý dù POST mới bị reject.

Tạo/lưu attempt và URL VNPay cùng transaction hiện có là hợp lệ vì `createPayment` hiện chỉ ký URL cục bộ, không gọi mạng. Không thay bằng provider khác có network trong transaction này. URL replay giữ nguyên attempt identity. Request mới trong PENDING/UNKNOWN trả PAYMENT_IN_PROGRESS; không tạo lần thu mới chỉ vì timeout.

Tách core snapshot/attempt/URL từ Processor/Store để HTTP service và adapter dev còn cần có thể gọi chung; HTTP controller không dựng một StartPayment giả từ Booking. Booking client phải trả snapshot thay cho port guard chỉ trả rejection hiện tại. Kích hoạt đúng core/runtime theo cấu hình, không còn chỉ bị khóa trong simulator. Runtime thật và adapter Booking giả không được cùng hoạt động. local-foundation tiếp tục tắt nghiệp vụ HTTP start, consumer/relay/workers. payment-simulator chỉ dùng cho dev nếu còn giữ; không tự phục hồi CLI/container/init SQL đã xóa. Tách các tham chiếu dev cũ khỏi cấu hình runtime trong plan.

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

Đối với message điều phối, inbox dùng consumer name/version và envelopeHash hiện có; commit inbox + domain effect + response/outbox trước ACK. Đối với POST HTTP, dùng namespace/requestHash/response riêng ở mục 6 và commit trước trả response. Không trộn hai hash/record codec. Conflict hash/poison contract không retry như lỗi hạ tầng. Source message dựa trên queue, exchange/binding và tài khoản/ACL đã provision; payload messageType phải khớp consumer. Không mở consumer tài chính lên exchange mà browser ghi được.

Outbox.payload lưu toàn Envelope JSONB; các cột metadata phải khớp envelope. Relay publish persistent, mandatory, correlated confirm và xử lý return. Chỉ đặt publishedAt sau confirm ACK và không có return. Confirm timeout/NACK/unroutable giữ pending. Crash sau confirm trước update có thể phát lặp; đây là at-least-once và inbox xử lý trùng.

Không giữ transaction/row lock khi chờ broker. Claim batch bằng SKIP LOCKED, tăng attempts và đặt nextAttemptAt làm lease ngắn trong transaction; dùng attempts + due value đã claim làm token CAS khi ghi kết quả. Lease hết có thể claim lại; worker cũ không được ghi đè claim mới. MessageId/envelope giữ nguyên khi retry; header delivery retry tách khỏi hash.

Queue runtime chỉ cho phần sau thu tiền: payment.decision.q, payment.ticket-issuance-failed.q, payment.order-completed.q; không bật payment.start.q cho đường khởi tạo mới và không coi tên queue chứa failed là bằng chứng queue chỉ nhận FAILED. Exchange lấy spring.rabbitmq.template.exchange/RABBITMQ_BUSINESS_EXCHANGE, mặc định hiện có business.ex; routing key là messageType B13. Không hardcode đổi cả hệ thống sang flash-ticket.v1 từ ví dụ tài liệu.

Không có broker definitions trên checkout này. Plan phải cung cấp mô tả topology/ACL và một artifact cấu hình mẫu trong module Payment, có queue retry riêng từng consumer và DLQ/parking. App runtime không tự tạo/xóa binding/policy trên broker đang dùng chung. Người vận hành phải provision trước khi bật runtime; integration test tự dùng vhost/topology riêng, không unbind queue dùng chung.

Consumer retry transient bằng republish tới retry queue 5s, 30s, 300s rồi parking, giữ envelope và destination consumer; ACK bản gốc chỉ sau republish confirm và không return. Poison input park ngay. Broker/DB outage diện rộng pause/backoff listener, không vòng lặp NACK tức thì hoặc biến lỗi thành REJECTED. Parking có cảnh báo và runbook replay nguyên IDs; không tự đổi payload cùng ID. Mức retry là đề xuất cấu hình, không bằng chứng nghiệp vụ đã thất bại.

Saga recovery đọc nextAttemptAt, ưu tiên relay pending; khi cần gửi lại command đã publish, sử dụng bản outbox và cùng commandId. Kiểm thử inject response/message định trước và duplicate để xác minh Payment hội tụ; không viết quyết định/outbox hay recovery của participant. Broker confirm chỉ chứng minh việc gửi, không chứng minh service ngoài đã thực hiện nghiệp vụ. WAITING_TICKETS chờ OrderCompleted; không suy FAILED từ timeout, 404 hoặc PENDING. Không thêm probe Ticket HTTP trong đợt này.

Worker query payment dùng context persist và kết quả qua RecordPaymentResultService. Query result quá hạn không tự suy FAILED nếu provider chưa có bằng chứng. Mạng chạy ngoài transaction; chống các worker cạnh tranh bằng operation lock/session có vòng đời rõ ràng và state/version CAS. Lock giải phóng khi session chết, không rò vào pool. Lịch due của saga/outbox lưu DB; tuổi pending payment/refund suy từ attempt history bền. Mặc định poll 5s, batch 20, backoff trần 300s, cảnh báo pending trên 15 phút; đây là lựa chọn người viết để kiểm thử, không cam kết SLA.

## 10. Transaction, schema và tổ chức code

Khóa dedupe/message nếu có trước, rồi nhất quán event → order → saga/attempt/charge → refund. Dùng advisory namespace khi chưa có row, sau đó row lock cần thiết; không chèn một event_finance_snapshot rỗng trái CHECK chỉ để lấy khóa. Event marker và command phát vé phải được đọc/quyết định dưới cùng ranh giới khóa. Mọi writer tương lai xử lý cancellation phải tuân cùng thứ tự.

Không gọi Booking, token endpoint, broker, query/refund provider khi đang giữ transaction khóa hàng. Tách orchestration và phương thức transaction sang Spring bean/TransactionTemplate; private helper không tự tạo transaction qua self-invocation. Unique/optimistic-lock conflict rollback xong mới đọc lại bản thắng bằng transaction mới; không tiếp tục trên transaction PostgreSQL đã aborted.

Tái sử dụng entity/UUID/Instant/enum/DDL và tên tbl_ hiện có. Không dùng Hibernate update/init, không đổi tiền sang double, không tạo saga thứ hai. DDL hiện có đủ cho thiết kế đề xuất; plan phải kiểm chứng persist context/CAS/completion lookup bằng PostgreSQL. Nếu thật sự cần migration thì trình phần thay đổi schema có mục tiêu trước khi code, không âm thầm đổi database đang dùng.

Các nhóm thêm/mở rộng dự kiến: client/booking, service/start, service/payment (record result), service/saga, service/refund, messaging/consumer/inbox/outbox, repository và worker. Giữ gateway/vnpay làm adapter. Controller/consumer/worker mỏng; mapper không query hoặc sinh ID/time; service dùng helper chung cho dedupe, transition, outbox, context và reason arbitration khi cùng quy tắc thực sự lặp.

Theo cinema-payment-style: constructor injection với RequiredArgsConstructor/FieldDefaults; Service/Transactional đúng ranh giới; Slf4j khi có log; record/constraint cho DTO; MapStruct shared config; entity giữ factory/constructor JPA và Version. Không thêm Swagger annotation khi pom chưa hỗ trợ, không refactor phong cách toàn module ngoài phạm vi.

## 11. Cấu hình, bảo mật và giới hạn sửa file

Thêm cấu hình có namespace Payment trong module, gắn env và cờ runtime/worker tường minh. Khi bật runtime cần gateway thật, Booking client/credential và messaging topology phù hợp; thiếu dependency phải lỗi rõ, không fallback sang guard thời gian hoặc fake. Không tự bật callbacks/refund hoặc thay merchant thật trong lượt code. Fake gateway/Booking/Ticket/issuer chỉ trong test hoặc dev cô lập, không production.

Các timeout, queue names, worker batch/backoff và exchange cấu hình được; secret chỉ từ environment/secret store. Giữ Config Server import hiện có. Đề xuất các default runtime properties trong application.yaml của Payment, không sửa shared config hay module Config Server/Gateway; giá trị remote có thể override. Runtime flag không được ghi đè từ simulator bằng cách bật đồng thời hai chế độ; validation profile phải chặn cấu hình đó.

Bổ sung public POST tạo attempt tại Payment và matcher JWT BUYER tương ứng; giữ bảo mật các route khác, không thêm endpoint sửa state/public refund. Gateway hiện được tài liệu mô tả route POST sang Booking: khi tích hợp cần chuyển route duy nhất sang Payment và gỡ POST start cũ tại Booking để tránh hai nơi khởi tạo. Việc sửa module Gateway/Booking nằm ngoài phạm vi chỉ Payment; ghi điều kiện tích hợp này trong runbook và kiểm trực tiếp endpoint Payment trước, không tuyên bố E2E qua Gateway khi route chưa đổi. Log chỉ IDs/correlation và mã lỗi an toàn, không raw IPN/token/URL có chữ ký, email hay snapshot đầy đủ. Chỉ truy cập DB Payment bằng application role, không đọc DB participant.

Chỉ sửa trong services/payment-service, gồm tài liệu/config/test/artifact topology của module. Không dùng git reset/restore/clean lên thay đổi có sẵn, không stage/commit file của người dùng. Tài liệu này có thể commit riêng bằng path, giữ nguyên staged deletions đang có. Chưa có quyền push/deploy hoặc provision broker/DB dùng chung.

## 12. Kiểm chứng và đầu ra

1. Ghi baseline branch/status; giữ nguyên test công cụ Booking ngoài phạm vi. Profile payment-only-tests tách đúng bốn test legacy đã ghi trong plan, không thay default profile hoặc che lỗi bằng claim whole-module verify xanh. Không triển khai BookingMockServer/MockBookingStore để giải quyết phần ngoài scope.
2. Unit/service tests kiểm HTTP replay trước Booking call, key/hash conflict, deadline/rejection, từng transition, paidAt/content conflict, callback trùng và charge khác trên cùng attempt.
3. HTTP controller/client tests kiểm JWT BUYER, Idempotency-Key, không nhận giá từ frontend, token service, timeout, 401/403/404/409/5xx, JSON/snapshot mismatch, response trả URL trực tiếp và không giữ transaction khi gọi mạng. JWT/ownership GET và Return không ghi state có regression.
4. PostgreSQL thật kiểm unique/partial indexes, transaction rollback, concurrent callback/command, atomic inbox/outbox, CAS lease, completion tuple với messageId khác và không tăng obligations khi lặp.
5. RabbitMQ thật trên vhost riêng kiểm ACK sau nfirm timcommit, return/NACK/coeout, retry/parking đúng consumer và duplicate khi crash sau confirm. Không lấy unit mock confirm làm bằng chứng broker.
6. Test flow trong Payment: frontend POST → fixture HTTP trả snapshot → Payment lưu attempt/URL → signed IPN/query result → quan sát AcceptPayment gửi ra → inject PaymentDecision ACCEPTED → quan sát IssueTickets → inject Ticket COMPLETED (no-op) và OrderCompleted → saga COMPLETED. Kiểm mất response, retry/restart Payment và request đồng thời không tạo attempt/command identity mới. Có các nhánh message LATE, DUPLICATE, Ticket FAILED và refund UNKNOWN; không suy ra số vé thực tế hay đúng đắn của Booking/Ticket từ test này.
7. Maven verify với JDK 21/UTC, profile payment-only-tests (thêm runtime-tests khi có DB/broker), Spotless/SpotBugs giữ nguyên. Báo PASS/FAIL/NOT_RUN và chính xác tập test/profile; ghi riêng bốn test Booking legacy ngoài scope. Test contract phía Payment không chứng minh Booking/Ticket thật, VNPay thật hoặc platform E2E.

Đầu ra sau triển khai: code/test trong module, hướng dẫn cấu hình/chạy/poll, topology mẫu và runbook retry/refund UNKNOWN, báo cáo các kiểm và giới hạn thực. Không tự dựng lại DB hoặc broker của người dùng.

## 13. Chỗ người viết đề xuất để người dùng kiểm tra

- Gộp compensation tối thiểu (muộn/trùng/phát vé thất bại) vào đợt mua vé để không bỏ quên khoản tiền đã thu; refund thật vẫn có cờ riêng và giới hạn tra cứu của VNPay phải hiện rõ.
- Để hủy sự kiện hàng loạt, finance summary và payout sang đợt riêng; không nhận đã hỗ trợ trọn vẹn phần đó của B13/B14.
- Public POST tại Payment lấy snapshot từ Booking, rồi MQ/saga sau thu tiền là hướng đã được người dùng chọn. Route cụ thể, HTTP response ApiResponse<PaymentStart>, dedupe HTTP và freeze-payment wire shape/client-credentials được làm rõ trong kế hoạch và contract test.
- Giữ schema và nghiệp vụ API đọc; bọc response HTTP bằng ApiResponse theo yêu cầu người dùng, thêm dependency OpenAPI phù hợp, điều chỉnh runtime activation và tách phụ thuộc simulator cũ trong phạm vi Payment.
- Thông số poll/batch/retry là mặc định thử nghiệm, builder có thể điều chỉnh có kiểm chứng; không được thay quy tắc tiền, transaction hoặc quyền.

Builder check bản đầu ngày 2026-10-27 trả về nothing important left nhưng chọn đầu vào Booking/MQ, nên không áp dụng cho revision hiện tại. Reviewer mới đã kiểm revision và chỉ còn câu hỏi về HTTP hay MQ sau thu tiền; người dùng đã chọn đề xuất dùng MQ. Không còn câu hỏi kiến trúc cần hỏi lại; review không thay bằng chứng code đã chạy hay tích hợp participant thật.

Dùng superpowers:writing-plans để viết plan file/class/helper/annotation cụ thể theo kiến trúc đã chọn. Theo AGENTS.md của Payment, trình plan này để người dùng duyệt trước khi sửa Java; không coi một bản plan chưa được trình là đã được duyệt.
