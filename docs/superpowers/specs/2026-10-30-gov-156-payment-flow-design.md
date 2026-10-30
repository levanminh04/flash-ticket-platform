# Payment GOV-156 / GOV-157 — spec và phân rã triển khai để duyệt

- Soạn: 30/10/2026. Trạng thái: **REVIEW_READY; chưa triển khai Java**.
- Nguồn nghiệp vụ: yêu cầu người dùng trong task này, GOV-156 và GOV-157 được xác nhận/duyệt ngày **29/10/2026**, cùng ảnh `Đặt vé.png` đính kèm. Ảnh danh sách có năm subtask là phạm vi phân rã, không phải hợp đồng kỹ thuật.
- Nhánh: `codex/gov-156-payment-flow`, từ `dev/tuan`, HEAD nền `e47b1f6`.
- Các tên route/class/method/query/test bên dưới đều là **CANDIDATE**, chờ duyệt spec/plan và Shop App. Không coi việc duyệt GOV-157 là duyệt các tên này.
- Chỉ tài liệu này được thêm trong lượt chuẩn bị; giữ nguyên các thay đổi Java/test chưa commit đã có. Tài liệu không tuyên bố các thay đổi có sẵn là phần triển khai task này.

## 1. Mục tiêu, ưu tiên nguồn và hiện trạng

Frontend tạo thanh toán trực tiếp tại Payment. Booking là owner của đơn, giá, voucher, reservation và inventory; Payment giữ bản PurchaseSnapshot bất biến và điều phối **một** Saga mua vé. Payment chỉ hoàn tất Saga sau bằng chứng Booking đã commit order COMPLETED.

Trong phạm vi task, yêu cầu GOV-156/GOV-157 mới được người dùng cung cấp ưu tiên hơn hướng start/success cũ trong B13/B14 và GOV-149/GOV-150. Không tự đánh dấu toàn bộ tài liệu kiến trúc cũ là APPROVED hoặc sửa các quyết định lịch sử. Khi spec được duyệt, đồng bộ B13/B14/B14-01/B15 và mock prompt trước khi tích hợp.

Đối chiếu workspace ngày soạn qua CodeGraph:

| Thành phần | Hiện trạng quan sát | Phải thay đổi sau duyệt |
| --- | --- | --- |
| PaymentController | Có `POST /api/v1/payments`, lấy buyerSubject từ JWT | Giữ entrypoint trực tiếp Payment; chốt hợp đồng idempotency |
| PaymentService.createPayment | Khóa snapshot đã có; thiếu snapshot thì PAYMENT_NOT_READY; tạo/tái dùng attempt và URL | Gọi Booking chuẩn bị snapshot trước transaction Payment; lưu snapshot lần đầu cùng attempt |
| PaymentService.handleIPN | Xác minh VNPay, lưu charge và cập nhật attempt | Ghi Saga WAITING_BOOKING và outbox AcceptPayment cùng commit trước phản hồi IPN |
| PaymentSagaService / MessagingService | Interface rỗng; implementation mới chỉ có repository | Bổ sung handler, relay, recovery và quyết định trạng thái bền |
| PurchaseSnapshotValidator / ContractHashService | Đã có validator và canonical hash | Tận dụng; không lấy giá FE hoặc tính lại snapshot ở Payment |
| Booking / Ticket | Source ứng dụng hiện tại chỉ là scaffold | Cần owner thực hiện hợp đồng và contract fixture; Payment test với fake không chứng minh E2E |
| DTO / tài liệu cũ | StartPayment/inbox start-requested; Ticket success gửi Booking; BookingIssuanceResult chỉ FAILED | Ngừng dùng start-requested trong luồng mới; success phải qua Payment; mở rộng hợp đồng kết quả để chuyển success tới Booking |

Phạm vi chuẩn bị: spec, hợp đồng đề xuất, transaction, phục hồi và tiêu chí kiểm thử cho năm subtask. Ngoài phạm vi: frontend, triển khai production, tự viết toàn bộ Booking/Ticket, đổi payout/finance, đổi token QR vé hoặc thêm gateway mới. Khi triển khai Payment, Booking/Ticket phải có contract được Shop App duyệt hoặc fixture tương thích; không thay bằng truy cập DB chéo.

## 2. Phương án

Chỉ một hướng đáp ứng các ràng buộc đã chốt: **Payment gọi một POST nội bộ Booking để chuẩn bị PurchaseSnapshot, sau đó điều phối bằng inbox/outbox và phản hồi nghiệp vụ**. Tận dụng PostgreSQL/RabbitMQ và năm trạng thái Saga hiện có, không thêm orchestrator hay Saga khác.

Booking start/outbox `payment.start-requested.v1`, freeze riêng rồi GET snapshot, GET có ghi freeze, và Ticket → Booking trực tiếp ở nhánh success đều bị loại bởi yêu cầu. Gọi mạng trong transaction giữ khóa cũng không phù hợp ranh giới giao dịch hiện tại. Đánh đổi của phương án chọn: phải lưu kết quả trung gian, chống lặp và phục hồi message; đổi lại crash hoặc broker confirm không làm mất bước nghiệp vụ.

Đây là phương án kỹ thuật của tài liệu theo luồng người dùng đã chốt; tên endpoint/message và chi tiết lưu bền vẫn chờ duyệt.

## 3. Hợp đồng đề xuất để duyệt cùng Shop App

| Biên | Tên CANDIDATE | Đầu vào / đầu ra và điều kiện |
| --- | --- | --- |
| FE → Payment | `POST /api/v1/payments`; `CreatePaymentRequest`; `PaymentService.createPayment` | JWT BUYER; body orderId, giữ clientIp hiện có nếu cần tương thích; header `Idempotency-Key` UUID. Không nhận amount/currency/voucher/subject từ FE. PaymentStart trả attemptId, trạng thái, expiresAt gốc và URL đã lưu; QR chỉ từ dữ liệu adapter hỗ trợ |
| Payment → Booking | **Một** `POST /internal/v1/orders/{orderId}/purchase-snapshot`; `BookingPurchaseClient.preparePurchaseSnapshot` | Credential service Payment và buyerSubject lấy từ JWT đã kiểm; Booking kiểm cả service caller và chủ đơn. Trả PurchaseSnapshot v1 nguyên bản đã commit. Đây là thao tác chuẩn bị snapshot duy nhất, không thêm API freeze hoặc GET để ghi |
| Payment → Booking | `booking.accept-payment.v1` / AcceptPayment | orderId, chargeId, attemptId, amount, currency, sourceOrderVersion; messageId/commandId ổn định |
| Booking → Payment | `booking.payment-decision.v1` / PaymentDecision | ACCEPTED/LATE/DUPLICATE/CANCELLED, acceptedChargeId, decisionAt; phát cùng transaction Booking |
| Payment → Ticket | `ticket.issue.v1` / IssueTickets | orderId, chargeId, full PurchaseSnapshot đã lưu; chỉ sau Booking ACCEPTED |
| Ticket → Payment | `ticket.issuance-result.v1` / IssuanceResult | orderId, chargeId, issuanceId, COMPLETED hoặc FAILED, ticketCount, failureCode; phát sau/cùng commit vé bằng outbox |
| Payment → Booking | `booking.issuance-result.v1` / BookingIssuanceResult mở rộng | Success: orderId, chargeId, issuanceId, state COMPLETED, ticketCount, failureCode null. Failed giữ nghĩa hiện có; chốt DTO và validation tương thích trước đổi producer/consumer |
| Booking → Payment | `booking.order-completed.v1` / OrderCompleted | orderId, chargeId, issuanceId, ticketCount; chỉ được tạo cùng commit order COMPLETED |

Route POST nội bộ trả 200 sau commit; lỗi candidate: 404 cho không có/khác chủ đơn, 409 ORDER_EXPIRED/ORDER_NOT_PAYABLE; 401/403 cho credential sai. Timeout/5xx không cho Payment mở attempt mới khi chưa nhận snapshot hợp lệ. Không mô tả response đã nhận nhưng snapshot còn trong RAM.

Public POST candidate: 201 khi tạo attempt mới, 200 khi tái dùng; 409 nếu key cũ có input khác hoặc đơn không payable; 503 khi Booking/provider tạm chưa xác định. Key được scope theo `(orderId, requestKey)` và luôn kiểm actor. Request hash gồm actor/orderId/provider; clientIp không được làm thay đổi định danh thanh toán của retry. Cùng key không bao giờ tạo attempt thứ hai; muốn attempt mới sau FAILED phải có key mới và Booking vẫn xác nhận payable. Nếu đã có unfinished attempt của key khác, trả 409 PAYMENT_IN_PROGRESS; không gán ngầm key mới vào attempt cũ rồi làm mất receipt cho lần retry sau.

Snapshot giữ field/money/version/canonical hash theo B13 và validator hiện có. Payment đối chiếu orderId/owner/expiry/currency/total, không sửa snapshot lỗi để cho qua. Receipt công khai lấy `PaymentAttempt.request_key/request_hash` hiện có; không còn dùng inbox `payment.start-requested.v1` làm nguồn kết quả của POST mới. Cùng key sau expiry/cancel không mở lại thanh toán hoặc trả URL có thể trả tiền; truy vấn lịch sử vẫn chỉ đọc. Mọi request cần mở/tái sử dụng URL phải qua Booking kiểm payable, kể cả Payment đã có snapshot.

Tên lớp/phương thức còn lại đề xuất: `PaymentTransactionService.persistAttempt`, `PaymentSagaService.recordPaymentDecision`, `recordIssuanceResult`, `recordOrderCompleted`, `OutboxRelay.publishDue`, `PaymentSagaRecoveryService.recoverDue`. Query cần khóa snapshot/order, lookup attempt theo order/key, lookup unfinished attempt, khóa Saga theo order, lookup inbox theo consumer/messageId và outbox đến hạn. Chốt signature/query/test cụ thể trong plan sau duyệt; không sinh Java từ danh sách này.

## 4. Booking chuẩn bị snapshot và phân xử voucher

POST nội bộ làm **một transaction Booking**: xác thực nguồn/subject → lấy khóa theo thứ tự hiện có (event snapshot → order → tài nguyên cần thiết) → kiểm ownership, cancellation, order PENDING_PAYMENT và reservation còn hợp lệ → lấy đồng hồ DB sau khóa, đòi `decisionAt < expiresAt` → lưu hoặc trả snapshot.

- Chưa có snapshot: dựng từ dữ liệu Booking đã có, bao gồm voucher HELD đã commit, ghi full frozen_purchase_snapshot + payment_frozen_at + payment_snapshot_version cùng transaction. Không tạo order/reservation/allocations lần nữa.
- Có snapshot, đơn còn payable: trả **đúng bản đã lưu**, giữ frozenAt/version/amount/expiresAt; không tính giá mới hoặc gia hạn giữ chỗ.
- Đơn EXPIRED/CANCELLED/ISSUING/COMPLETED hoặc đã đến deadline: từ chối, dù snapshot còn lưu. Không cho snapshot lịch sử trở thành quyền mở attempt.
- Áp voucher và chuẩn bị snapshot phải lấy **cùng khóa order**, rồi kiểm trạng thái/freeze dưới khóa. Voucher commit trước: snapshot có discount đó. Snapshot commit trước: voucher rollback với `PRICE_FROZEN`, không giữ usage/counter mới.
- Nếu response POST mất sau commit, retry trên đơn còn payable đọc bản cũ. Nếu retry đã hết hạn/hủy thì từ chối. Không tạo message start Payment hoặc thực hiện freeze qua GET.

Không có atomic transaction xuyên Booking/Payment: đơn vẫn có thể hết hạn/hủy sau POST trước khi Payment commit. Payment kiểm expiresAt lần nữa trước ghi attempt, không hứa đóng hết khoảng đua cancellation. Quyền nhận tiền để phát vé cuối cùng do Booking AcceptPayment phân xử; tiền tới muộn vẫn vào nhánh hoàn có sẵn.

## 5. Các transaction và thứ tự theo ảnh

| Bước ảnh | Hành động và commit bắt buộc | Sau commit |
| --- | --- | --- |
| 15–21 | FE POST Payment; Payment gọi POST Booking ngoài transaction Payment. Booking commit kiểm payable/freeze/snapshot | Payment mới bắt đầu transaction ghi |
| 22–26 | Payment validate; khóa theo thứ tự event finance → snapshot → attempt; upsert snapshot bất biến + attempt với request_key/hash. Snapshot xung đột hash/version phải fail. Unique unfinished/order và unique(order,key) phân xử cạnh tranh | Sau commit mới trả dữ liệu URL/QR cho FE |
| 28–33 | Verify IPN; trong Payment TX ghi charge identity duy nhất, attempt SUCCEEDED, candidate Saga WAITING_BOOKING và outbox AcceptPayment | Commit thành công rồi phản hồi cổng. Không chờ Booking/Ticket để ACK IPN |
| 34–38 | Booking inbox + khóa order/tài nguyên, kiểm charge đã nhận trước deadline mới; ACCEPTED ghi reservation/allocations COMMITTED, inventory/hạn mức/voucher và order ISSUING, kèm outbox PaymentDecision | Commit rồi relay trả quyết định nghiệp vụ |
| 39–42 | Payment inbox + confirmation(order,charge) + Saga WAITING_TICKETS + outbox IssueTickets trong cùng TX | Relay yêu cầu Ticket phát vé |
| 43–46 | Ticket tạo đủ bộ vé + issuance COMPLETED + outbox IssuanceResult **tới Payment** trong cùng TX | Sau commit mới báo success |
| 47 | Payment inbox result, kiểm order/charge/issuance/count và lưu kết quả bền cùng outbox BookingIssuanceResult COMPLETED | Saga vẫn WAITING_TICKETS; relay thông báo Booking |
| 48–51 | Booking kiểm source Payment, acceptedCharge/issuance/count; inbox + order COMPLETED + outbox OrderCompleted trong cùng TX | Commit rồi báo Payment |
| 52–54 | Payment nhận OrderCompleted khớp confirmation và issuance result đã lưu; inbox + Saga COMPLETED cùng TX | Sau commit mới ACK message |

Adapter URL hiện có là ký URL VNPay cục bộ: có thể tính/lưu URL trong TX lưu attempt nếu không gọi mạng, nhưng FE chỉ nhận sau commit. Nếu adapter tương lai gọi mạng: commit attempt trước, gọi ngoài TX với cùng provider identity, lưu URL trong TX riêng rồi trả. Timeout phải dùng UNKNOWN/query cùng identity; không sinh attempt mới để thử mù. Ảnh không xác nhận có API QR riêng; không tự thêm QR gateway hoặc fake QR thành tính năng đã tích hợp.

Webhook hợp lệ tới sau expiry/cancel vẫn phải lưu bằng chứng tiền thật và đi nhánh hoàn. Charge khác đã liên kết đúng cùng order không thay candidate/confirmation: tạo refund DUPLICATE_PAYMENT riêng. Sai chữ ký/merchant/amount/currency/identity không ghi charge hoặc phát vé; giữ giới hạn Q-07 hiện có. Return page chỉ hiển thị/redirect, không chứng minh thu tiền.

## 6. Saga, lưu kết quả và phục hồi

Giữ enum hiện có: WAITING_BOOKING → WAITING_TICKETS → COMPLETED; COMPENSATING/COMPENSATED dành cho bù trừ. WAITING_TICKETS bao gồm chờ Ticket và chờ Booking completion; không tự thêm trạng thái DB mới.

Candidate lưu checkpoint kết quả vé: trong TX xử lý IssuanceResult, lưu payload đã validate vào `inbox_messages.response_payload`, với consumer riêng và hash envelope, đồng thời ghi outbox gửi Booking. Outbox payload mang issuanceId/count/charge; row outbox và inbox phải được giữ khi Saga còn chờ. Không cần thêm cột issuanceId vào Saga trong phương án này. Kế hoạch query/recovery phải đọc checkpoint hoặc outbox theo order/type, không chỉ theo messageId trong RAM; đánh giá index trên dữ liệu thực trước đề xuất migration. Không xóa checkpoint sớm rồi phụ thuộc Ticket để dựng lại bằng chứng đã nhận.

| Checkpoint | Retry/phục hồi |
| --- | --- |
| Booking đã freeze, Payment chưa nhận response | Retry POST cùng order/actor; trả snapshot cũ nếu còn payable |
| Payment đã commit attempt nhưng FE mất response | Retry cùng key: cùng attempt/URL nếu Booking vẫn payable. UNKNOWN không mở attempt mới; dùng query/recovery |
| IPN TX commit nhưng response mất | Provider callback lặp được dedupe; không thêm charge/Saga/outbox cho cùng tác động |
| WAITING_BOOKING, mất decision | Relay outbox hoặc recovery replay cùng AcceptPayment; Booking trả quyết định bền, không nhận tiền/inventory lần hai |
| WAITING_TICKETS chưa có result | Replay IssueTickets cùng logical identity; Ticket cùng input trả cùng issuance/bộ vé |
| Đã lưu Ticket success, Booking chưa completed | Replay outbox Payment → Booking từ checkpoint; không chỉ replay IssueTickets vô hạn |
| Booking COMPLETED nhưng response mất | Booking outbox khôi phục OrderCompleted; Payment chưa nhận thì giữ WAITING_TICKETS |
| Broker publisher confirm/consumer ACK | Chỉ cập nhật trạng thái vận chuyển của message; **không cập nhật Saga** |

Inbox so hash trước dedupe; cùng ID khác payload là lỗi hợp đồng. Một operation recovery mới có thể có messageId mới và causation tới lệnh cũ, nhưng giữ business identity order/charge/issuance và input hash; participant phát lại quyết định bền, không thực hiện tác động lần hai. Mọi ghi outbox/inbox cùng domain TX; consumer ACK sau commit. Relay/recovery chạy ngoài TX giữ khóa nghiệp vụ khi gọi broker/HTTP; claim có lease/CAS, backoff có trần và mã lỗi đã lọc.

OrderCompleted chỉ nhận từ Booking, phải khớp order/charge/issuance/count đã lưu và confirmation; kết quả Ticket riêng hoặc probe không đủ để kết thúc Saga. Replay trên COMPLETED không đổi gì. COMPENSATING/COMPENSATED và marker event cancellation không bị hồi sinh bởi success đến trễ. Completion chưa có checkpoint phù hợp phải retry/cảnh báo, không tự điền dữ liệu để đóng Saga.

## 7. Giữ các nhánh expiry/cancel/refund

- Không gia hạn expiresAt khi freeze/retry. Tại Booking, bằng deadline là expired; confirm/expire/cancel tranh chấp cùng khóa. Accepted charge cũ được replay ACCEPTED trước xét thời gian hiện tại; đơn chưa accepted đã hết hạn/hủy không hồi sinh.
- Buyer cancel vẫn hợp lệ khi PENDING_PAYMENT theo baseline, kể cả đã snapshot. Release theo reservation/generation/state đúng một lần. ISSUING/COMPLETED không cho buyer cancel.
- Event cancellation đóng gate ở từng owner, chặn phát hành/completion tới trễ và hội tụ hoàn tiền. Fee config tới sau không xóa cancellation marker. Không thay fan-out hoặc mở thêm Saga.
- Refund vẫn unique(charge), theo thứ tự nguyên nhân đã có: charge thừa → DUPLICATE_PAYMENT; accepted charge bị hủy event → EVENT_CANCELLED; Ticket có terminal FAILED fence → TICKET_ISSUANCE_FAILED; từ chối do deadline/buyer cancel → LATE_PAYMENT. Trigger sau không tạo nghĩa vụ hoàn thứ hai.
- Timeout/404/PENDING từ Ticket không chứng minh phát vé thất bại. Chỉ terminal FAILED bảo đảm không còn bộ vé có hiệu lực và worker không thể commit success sau fence mới cho phép bù trừ lỗi phát hành.
- Provider refund ngoài transaction; mất kết quả thì query/retry cùng request key. COMPENSATED theo rule hiện hành, không đồng nhất broker ACK với hội tụ toàn hệ thống.

## 8. Phân rã năm subtask và kế hoạch dự kiến

Đây là phân rã để duyệt, chưa phải quyền triển khai. Sau duyệt thiết kế, lập plan chi tiết theo skill writing-plans với signature/query/test và contract Shop App đã chốt. Mỗi phần có kiểm thử độc lập và bằng chứng trước chuyển phần sau.

| Thứ tự / subtask trong ảnh | Thay đổi dự kiến | Điều kiện nghiệm thu |
| --- | --- | --- |
| 0. Chốt hợp đồng | Đồng bộ B13/B14/B14-01/B15, thay mock start-requested; fixture POST Booking và message ba owner; chốt naming/compatibility | Shop App duyệt tên, DTO, auth/error/idempotency và hướng routing; không còn tài liệu triển khai mới hướng Ticket → Booking success |
| 1. API tạo link/QR | Public POST, client POST Booking ngoài TX, lưu snapshot+attempt, reuse/concurrency, adapter URL hiện có | Giá chỉ từ Booking; owner/deadline/voucher race đúng; commit trước response; retry không tạo thêm attempt |
| 2. Webhook xác nhận | Verify và transaction charge+attempt+Saga+AcceptPayment outbox, idempotency callback | Rollback bất kỳ ghi nào không trả ACK thành công; replay không tăng charge/outbox |
| 3. Gọi Booking xác nhận | Outbox relay, inbox PaymentDecision, confirmation+WAITING_TICKETS+IssueTickets | Broker ACK không tiến Saga; chỉ Booking ACCEPTED sau commit mới phát lệnh Ticket |
| 4. Yêu cầu phát vé | IssueTickets relay, nhận Ticket result tại Payment, checkpoint+success outbox tới Booking, giữ FAILED/fence | Cùng request chỉ một bộ vé; count/charge/hash mismatch không phát completion; restart không mất bước 47 |
| 5. Hoàn tất đơn và Saga | Nhận OrderCompleted, so bằng chứng và đóng Saga; phục hồi, integration tests | Booking COMPLETED trước Payment COMPLETED; mất message/restart vẫn hội tụ; compensation không hồi sinh |

Không sửa/chuyển các file Payment đang dở của người dùng trong lúc chuẩn bị. Khi được phép triển khai, kiểm tra lại diff và baseline trước refactor; không ghi đè việc tách controller/service đang có. Test build Payment không đủ nghiệm thu Booking voucher race hoặc Ticket commit; các owner phải chạy contract/race tests tương ứng.

## 9. Ma trận kiểm thử bắt buộc sau duyệt

Tên test là CANDIDATE để Shop App xem cùng plan, không phải file đã tạo.

| Nhóm / test đề xuất | Tình huống và bằng chứng cần có |
| --- | --- |
| PreparePurchaseSnapshotContractTest | Service auth + actor; sai owner/state; expired/cancelled; retry snapshot byte/canonical hash/version/expiry không đổi; GET không ghi |
| PreparePurchaseSnapshotConcurrencyTest (Booking owner) | Hai lịch commit voucher/snapshot có barrier; voucher trước có discount, snapshot trước trả PRICE_FROZEN và không rò usage; expiry/cancel cùng khóa |
| DirectPaymentStartTest | Không có local snapshot vẫn gọi Booking; client chạy khi Payment TX không active; FE không điều khiển giá; snapshot invalid/hash conflict bị từ chối; commit rồi mới trả URL |
| PaymentAttemptConcurrencyTest | Hai POST cùng/khác key; một unfinished attempt; cùng key khác input conflict; retry FAILED key cũ không tạo mới; UNKNOWN không tạo mới; unique loser rollback rồi đọc winner bằng TX mới |
| PaymentWebhookSagaTest | Sai chữ ký/merchant/amount/currency; duplicate provider identity; rollback charge/attempt/Saga/outbox; ACK chỉ sau commit; late/extra linked charges vẫn xử lý đúng |
| PaymentBookingDecisionTest | ACCEPTED commit mới có confirmation/IssueTickets; LATE/CANCELLED/DUPLICATE hoàn theo baseline; duplicate/mismatched decision không phát thêm vé; publisher confirm không đổi state |
| TicketSuccessThroughPaymentTest | Ticket success gửi Payment; inbox/checkpoint+Booking success outbox atomic; không có direct success binding Ticket → Booking; thiếu/sai count/issuance/charge không hoàn tất |
| BookingCompletionSagaTest | Ticket success và broker ACK vẫn WAITING_TICKETS; Booking chưa commit không được phát OrderCompleted; đúng completion mới COMPLETED; sai nguồn/correlation hoặc cancellation bị chặn |
| PaymentSagaRecoveryTest | Crash sau mỗi commit và trước/sau publish/ACK, gồm bước 47 và 51; restart/replay cùng identity không tăng charge, inventory, voucher, ticket hoặc refund |
| PaymentCompensationRegressionTest | Boundary deadline; buyer cancel sau snapshot; event cancel trước/sau acceptance/phát vé; terminal FAILED fence; timeout không tự refund; refund duplicate/unknown/retry giữ rule cũ |
| PurchaseFlowContractIntegrationTest | Fixture thực ba owner + DB/broker: bước 15–54 đúng thứ tự commit, Ticket → Payment → Booking → Payment; ghi rõ fake vs service thật và giới hạn bằng chứng |

Chạy unit/slice test Payment, repository/transaction tests PostgreSQL và broker tests có kiểm ACK/rollback; sau đó contract/race tests Booking/Ticket và E2E môi trường tích hợp. Chốt lệnh chạy và profile trong plan sau duyệt dựa trên cấu hình repo tại thời điểm đó. Lượt spec này không chạy/khẳng định Java tests vì không có thay đổi Java và đang có work-in-progress chưa thuộc task.

## 10. Các điểm cần duyệt trước Java

1. Duyệt thiết kế và phạm vi Payment ở đây; Booking/Ticket là hợp đồng phối hợp do owner tương ứng thực hiện.
2. Shop App chốt các tên CANDIDATE ở mục 3 và mục 9, auth actor propagation, HTTP status/idempotency và mở rộng BookingIssuanceResult cho success.
3. Duyệt checkpoint dùng inbox/outbox trong thời gian WAITING_TICKETS và yêu cầu retention/recovery; plan chi tiết phải thể hiện query thực thi được.
4. Sau đó mới chốt plan chi tiết và triển khai Java. Chưa có bằng chứng Shop App duyệt trong chat này; không ghi trạng thái APPROVED hoặc tự gửi thông báo ra ngoài.

Không có thay đổi Java, DDL, broker binding, gateway hoặc cấu hình runtime trong bản spec này.
