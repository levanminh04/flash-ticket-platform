# Payment GOV-156 / GOV-157 — spec và phân rã triển khai để duyệt

- Soạn: 30/10/2026. Trạng thái: **REVIEW_READY; chưa triển khai Java**.
- Phạm vi được người dùng làm rõ: **chỉ công việc Payment**. Không triển khai, sửa cấu hình, schema, tài liệu hay test nội bộ Booking/Ticket hoặc service khác. Các service đó chỉ xuất hiện như hợp đồng bên ngoài mà Payment sử dụng.
- Nguồn nghiệp vụ: yêu cầu người dùng trong task này, GOV-156 và GOV-157 được xác nhận/duyệt ngày **29/10/2026**, cùng ảnh `Đặt vé.png` đính kèm. Ảnh danh sách có năm subtask là phạm vi phân rã, không phải hợp đồng kỹ thuật.
- Nhánh: `codex/gov-156-payment-flow`, từ `dev/tuan`, HEAD nền `e47b1f6`.
- Các tên route/class/method/query/test bên dưới đều là **CANDIDATE**, chờ duyệt spec/plan và Shop App. Không coi việc duyệt GOV-157 là duyệt các tên này.
- Chỉ tài liệu này được thêm trong lượt chuẩn bị; giữ nguyên các thay đổi Java/test chưa commit đã có. Tài liệu không tuyên bố các thay đổi có sẵn là phần triển khai task này.

## 1. Mục tiêu, ưu tiên nguồn và hiện trạng

Frontend tạo thanh toán trực tiếp tại Payment. Booking là owner của đơn, giá, voucher, reservation và inventory; Payment giữ bản PurchaseSnapshot bất biến và điều phối **một** Saga mua vé. Payment chỉ hoàn tất Saga sau bằng chứng Booking đã commit order COMPLETED.

Trong phạm vi task, yêu cầu GOV-156/GOV-157 mới được người dùng cung cấp ưu tiên hơn hướng start/success cũ trong B13/B14 và GOV-149/GOV-150. Không tự đánh dấu toàn bộ tài liệu kiến trúc cũ là APPROVED hoặc sửa các quyết định lịch sử. Không đưa việc đồng bộ tài liệu kiến trúc toàn hệ thống hoặc mock service riêng vào kế hoạch Payment này.

Đối chiếu workspace ngày soạn qua CodeGraph:

| Thành phần | Hiện trạng quan sát | Phải thay đổi sau duyệt |
| --- | --- | --- |
| PaymentController | Có `POST /api/v1/payments`, lấy buyerSubject từ JWT | Giữ entrypoint trực tiếp Payment; chốt hợp đồng idempotency |
| PaymentService.createPayment | Khóa snapshot đã có; thiếu snapshot thì PAYMENT_NOT_READY; tạo/tái dùng attempt và URL | Gọi Booking chuẩn bị snapshot trước transaction Payment; lưu snapshot lần đầu cùng attempt |
| PaymentService.handleIPN | Xác minh VNPay, lưu charge và cập nhật attempt | Ghi Saga WAITING_BOOKING và outbox AcceptPayment cùng commit trước phản hồi IPN |
| PaymentSagaService / MessagingService | Interface rỗng; implementation mới chỉ có repository | Bổ sung handler, relay, recovery và quyết định trạng thái bền |
| PurchaseSnapshotValidator / ContractHashService | Đã có validator và canonical hash | Tận dụng; không lấy giá FE hoặc tính lại snapshot ở Payment |
| DTO / tài liệu cũ | StartPayment/inbox start-requested; Ticket success gửi Booking; BookingIssuanceResult chỉ FAILED | Ngừng dùng start-requested trong luồng mới; success phải qua Payment; mở rộng hợp đồng kết quả để chuyển success tới Booking |

Phạm vi chuẩn bị và triển khai sau duyệt: API, client gọi ra ngoài, adapter VNPay hiện có, DTO, repository, transaction, consumer, inbox/outbox, Saga, recovery và kiểm thử **trong Payment** cho năm subtask. Ngoài phạm vi: mọi thay đổi tại service khác, frontend, triển khai production, đổi payout/finance, đổi token QR vé hoặc thêm gateway mới. Kiểm thử Payment dùng stub/fake ở test source Payment cho các hợp đồng Booking/Ticket; không chờ hai service đó hoàn thiện mới làm Payment, không tạo mock service riêng và không truy cập DB chéo. Chỉ báo cáo bằng chứng phía Payment, không nhận nghiệm thu E2E toàn hệ thống.

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

Hợp đồng response bên ngoài mà client Payment dự kiến nhận: 200 kèm snapshot đã commit; 404 cho không có/khác chủ đơn, 409 ORDER_EXPIRED/ORDER_NOT_PAYABLE; 401/403 cho credential sai. Chỉ triển khai cách client Payment gửi request, kiểm response và ánh xạ lỗi; không viết endpoint Booking. Timeout/5xx không cho Payment mở attempt mới khi chưa nhận snapshot hợp lệ.

Public POST candidate: 201 khi tạo attempt mới, 200 khi tái dùng; 409 nếu key cũ có input khác hoặc đơn không payable; 503 khi Booking/provider tạm chưa xác định. Key được scope theo `(orderId, requestKey)` và luôn kiểm actor. Request hash gồm actor/orderId/provider; clientIp không được làm thay đổi định danh thanh toán của retry. Cùng key không bao giờ tạo attempt thứ hai; muốn attempt mới sau FAILED phải có key mới và Booking vẫn xác nhận payable. Nếu đã có unfinished attempt của key khác, trả 409 PAYMENT_IN_PROGRESS; không gán ngầm key mới vào attempt cũ rồi làm mất receipt cho lần retry sau.

Snapshot giữ field/money/version/canonical hash theo B13 và validator hiện có. Payment đối chiếu orderId/owner/expiry/currency/total, không sửa snapshot lỗi để cho qua. Receipt công khai lấy `PaymentAttempt.request_key/request_hash` hiện có; không còn dùng inbox `payment.start-requested.v1` làm nguồn kết quả của POST mới. Cùng key sau expiry/cancel không mở lại thanh toán hoặc trả URL có thể trả tiền; truy vấn lịch sử vẫn chỉ đọc. Mọi request cần mở/tái sử dụng URL phải qua Booking kiểm payable, kể cả Payment đã có snapshot.

Tên lớp/phương thức còn lại đề xuất: `PaymentTransactionService.persistAttempt`, `PaymentSagaService.recordPaymentDecision`, `recordIssuanceResult`, `recordOrderCompleted`, `OutboxRelay.publishDue`, `PaymentSagaRecoveryService.recoverDue`. Query cần khóa snapshot/order, lookup attempt theo order/key, lookup unfinished attempt, khóa Saga theo order, lookup inbox theo consumer/messageId và outbox đến hạn. Chốt signature/query/test cụ thể trong plan sau duyệt; không sinh Java từ danh sách này.

## 4. Trách nhiệm Payment khi lấy snapshot

Payment gọi **một POST nội bộ** bằng client của mình, ngoài transaction Payment, gửi orderId và buyerSubject lấy từ JWT đã xác thực. Không thêm API freeze riêng, không gọi GET có tác dụng ghi và không đọc DB Booking.

- Response thành công: kiểm orderId/subject, cấu trúc, tổng tiền, currency, frozenAt, version, expiry và canonical hash; dùng nguyên snapshot để lưu và tạo attempt. Không áp voucher hoặc tính lại giá ở Payment.
- Retry: gọi lại cùng hợp đồng và chấp nhận bản snapshot đã lưu, không tự thay frozenAt/version/amount/expiresAt. Nếu khác snapshot Payment đã lưu thì từ chối xung đột.
- Lỗi hết hạn/hủy/không payable: không tạo attempt mới và không trả URL để tiếp tục trả tiền. Timeout/response lỗi không được coi là đã đủ điều kiện thanh toán.
- GOV-157 về voucher là bảo đảm của hợp đồng bên ngoài: voucher commit trước thì snapshot có giảm giá; snapshot commit trước thì voucher bị PRICE_FROZEN. Payment chỉ dùng số tiền trong snapshot; không triển khai khóa order, voucher usage hoặc race test bên Booking.
- Mất response sau khi bên ngoài đã lưu snapshot: Payment retry POST, không phát `payment.start-requested` hoặc tự dựng snapshot thay thế. Stub trong test Payment mô phỏng snapshot ổn định và các lỗi có thể trả về.

Không có atomic transaction xuyên Booking/Payment: đơn vẫn có thể hết hạn/hủy sau POST trước khi Payment commit. Payment kiểm expiresAt lần nữa trước ghi attempt, không hứa đóng hết khoảng đua cancellation. Quyền nhận tiền để phát vé cuối cùng do Booking AcceptPayment phân xử; tiền tới muộn vẫn vào nhánh hoàn có sẵn.

## 5. Các transaction và thứ tự theo ảnh

| Bước ảnh | Công việc phía Payment | Sau commit / điều kiện chờ |
| --- | --- | --- |
| 15–21 | Nhận FE POST; gọi POST Booking bằng client ngoài transaction Payment, kiểm response snapshot | Nhận snapshot hợp lệ mới bắt đầu transaction ghi Payment |
| 22–26 | Payment validate; khóa theo thứ tự event finance → snapshot → attempt; upsert snapshot bất biến + attempt với request_key/hash. Snapshot xung đột hash/version phải fail. Unique unfinished/order và unique(order,key) phân xử cạnh tranh | Sau commit mới trả dữ liệu URL/QR cho FE |
| 28–33 | Verify IPN; trong Payment TX ghi charge identity duy nhất, attempt SUCCEEDED, candidate Saga WAITING_BOOKING và outbox AcceptPayment | Commit thành công rồi phản hồi cổng. Không chờ Booking/Ticket để ACK IPN |
| 34–38 | Relay AcceptPayment tới Booking; giữ WAITING_BOOKING và chờ PaymentDecision | Broker confirm không thay thế quyết định nghiệp vụ; không triển khai transaction Booking |
| 39–42 | Payment inbox + confirmation(order,charge) + Saga WAITING_TICKETS + outbox IssueTickets trong cùng TX | Relay yêu cầu Ticket phát vé |
| 43–46 | Relay IssueTickets; nhận IssuanceResult **tại Payment**, xác minh nguồn và dữ liệu | Chỉ coi success là kết quả nghiệp vụ Ticket đã commit; không triển khai phát vé |
| 47 | Payment inbox result, kiểm order/charge/issuance/count và lưu kết quả bền cùng outbox BookingIssuanceResult COMPLETED | Saga vẫn WAITING_TICKETS; relay thông báo Booking |
| 48–51 | Gửi thông báo phát vé thành công tới Booking; giữ WAITING_TICKETS và chờ OrderCompleted | Không cập nhật DB/order thay Booking; broker ACK không đóng Saga |
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

## 7. Giữ các nhánh expiry/cancel/refund phía Payment

- Không gia hạn expiresAt khi lấy snapshot/retry; Payment kiểm `now < expiresAt` trước attempt mới. Dùng quyết định ACCEPTED/LATE/CANCELLED/DUPLICATE nhận từ Booking, không tự sửa đơn hoặc kiểm tra inventory thay Booking.
- Buyer cancel và expiry được mô phỏng bằng response/decision bên ngoài trong test Payment. Khi nhận kết quả không payable, Payment không mở attempt mới; charge hợp lệ tới muộn vẫn lưu và xử lý nghĩa vụ hoàn.
- Xử lý event cancellation ở Payment: giữ marker, chặn điều phối phát hành/completion tới trễ và hội tụ hoàn tiền. Fee config tới sau không xóa cancellation marker. Không sửa fan-out hoặc handler của các service khác, không mở thêm Saga.
- Refund vẫn unique(charge), theo thứ tự nguyên nhân đã có: charge thừa → DUPLICATE_PAYMENT; accepted charge bị hủy event → EVENT_CANCELLED; Ticket có terminal FAILED fence → TICKET_ISSUANCE_FAILED; từ chối do deadline/buyer cancel → LATE_PAYMENT. Trigger sau không tạo nghĩa vụ hoàn thứ hai.
- Timeout/404/PENDING từ Ticket không chứng minh phát vé thất bại. Chỉ terminal FAILED bảo đảm không còn bộ vé có hiệu lực và worker không thể commit success sau fence mới cho phép bù trừ lỗi phát hành.
- Provider refund ngoài transaction; mất kết quả thì query/retry cùng request key. COMPENSATED theo rule hiện hành, không đồng nhất broker ACK với hội tụ toàn hệ thống.

## 8. Phân rã năm subtask và kế hoạch dự kiến

Đây là phân rã để duyệt, chưa phải quyền triển khai. Sau duyệt thiết kế, lập plan chi tiết theo skill writing-plans với signature/query/test và contract Shop App đã chốt. Mỗi phần có kiểm thử độc lập và bằng chứng trước chuyển phần sau.

| Thứ tự / subtask trong ảnh | Thay đổi dự kiến | Điều kiện nghiệm thu |
| --- | --- | --- |
| 0. Chốt hợp đồng phía Payment | Chốt DTO/client/consumer và fixture trong test Payment; chốt naming, auth/error/idempotency và tương thích | Shop App duyệt tên và hợp đồng Payment sử dụng; không có task sửa service khác hoặc tài liệu toàn hệ thống |
| 1. API tạo link/QR | Public POST, client POST Booking ngoài TX, lưu snapshot+attempt, reuse/concurrency, adapter URL hiện có | Giá chỉ từ snapshot; kiểm owner/deadline và lỗi client đúng; commit trước response; retry không tạo thêm attempt |
| 2. Webhook xác nhận | Verify và transaction charge+attempt+Saga+AcceptPayment outbox, idempotency callback | Rollback bất kỳ ghi nào không trả ACK thành công; replay không tăng charge/outbox |
| 3. Gọi Booking xác nhận | Outbox relay, inbox PaymentDecision, confirmation+WAITING_TICKETS+IssueTickets | Broker ACK không tiến Saga; chỉ Booking ACCEPTED sau commit mới phát lệnh Ticket |
| 4. Yêu cầu phát vé | IssueTickets relay, nhận Ticket result tại Payment, checkpoint+success outbox tới Booking, giữ nhánh FAILED | Replay giữ business identity; count/charge/hash mismatch không phát completion; restart không mất bước 47; không triển khai tạo vé |
| 5. Thông báo Booking và hoàn tất Saga | Gửi success tới Booking; nhận OrderCompleted, so bằng chứng và đóng Saga; phục hồi và integration tests Payment | Ticket success/broker ACK chưa đủ; chỉ OrderCompleted hợp lệ mới đóng Saga; compensation không hồi sinh |

Không sửa/chuyển các file Payment đang dở của người dùng trong lúc chuẩn bị. Khi được phép triển khai, kiểm tra lại diff và baseline trước refactor; không ghi đè việc tách controller/service đang có. Mọi thay đổi runtime/test nằm trong `services/payment-service`; schema hay cấu hình dùng chung nếu thật sự cần chỉ đề xuất phần dành cho Payment. Không thêm test, endpoint hoặc consumer vào Booking/Ticket và không lấy tiến độ các service đó làm điều kiện hoàn thành phần Payment.

## 9. Ma trận kiểm thử bắt buộc sau duyệt

Tên test là CANDIDATE để Shop App xem cùng plan, không phải file đã tạo.

| Nhóm / test đề xuất | Tình huống và bằng chứng cần có |
| --- | --- |
| BookingPurchaseClientContractTest | Stub HTTP tại test Payment kiểm một POST, credential + actor; ánh xạ 401/403/404/409/5xx/timeout; validate snapshot thành công và retry; không gọi freeze riêng/GET hoặc DB chéo |
| DirectPaymentStartTest | Không có local snapshot vẫn gọi Booking; client chạy khi Payment TX không active; FE không điều khiển giá; snapshot invalid/hash conflict bị từ chối; commit rồi mới trả URL |
| PaymentAttemptConcurrencyTest | Hai POST cùng/khác key; một unfinished attempt; cùng key khác input conflict; retry FAILED key cũ không tạo mới; UNKNOWN không tạo mới; unique loser rollback rồi đọc winner bằng TX mới |
| PaymentWebhookSagaTest | Sai chữ ký/merchant/amount/currency; duplicate provider identity; rollback charge/attempt/Saga/outbox; ACK chỉ sau commit; late/extra linked charges vẫn xử lý đúng |
| PaymentBookingDecisionTest | ACCEPTED commit mới có confirmation/IssueTickets; LATE/CANCELLED/DUPLICATE hoàn theo baseline; duplicate/mismatched decision không phát thêm vé; publisher confirm không đổi state |
| TicketSuccessThroughPaymentTest | Bơm Ticket result vào consumer Payment; inbox/checkpoint+Booking success outbox atomic; Payment gửi success tới Booking; thiếu/sai count/issuance/charge không hoàn tất |
| BookingCompletionSagaTest | Ticket success và broker ACK vẫn WAITING_TICKETS; chưa nhận OrderCompleted thì không COMPLETED; đúng completion mới hoàn tất; sai nguồn/correlation hoặc cancellation bị chặn |
| PaymentSagaRecoveryTest | Crash sau mỗi commit Payment và trước/sau publish/ACK; mô phỏng mất result/completion từ bên ngoài; restart/replay giữ identity và không tăng charge, confirmation, lệnh nghiệp vụ hoặc refund |
| PaymentCompensationRegressionTest | Boundary deadline; stub báo buyer cancel/expiry sau snapshot; event cancel tại Payment; nhận terminal FAILED hợp lệ; timeout không tự refund; refund duplicate/unknown/retry giữ rule cũ |
| PaymentFlowContractIntegrationTest | Chạy Payment + DB/broker với HTTP stub và message fixture bên ngoài: nhận snapshot → webhook → acceptance → Ticket result → Booking completion; kiểm mọi commit/state/outbox thuộc Payment, không gọi đây là E2E toàn hệ thống |

Chỉ chạy unit/slice test Payment, client contract test bằng stub, repository/transaction tests PostgreSQL Payment và broker tests consumer/relay Payment có kiểm ACK/rollback. Không yêu cầu chạy ứng dụng hoặc kiểm thử nội bộ Booking/Ticket. Chốt lệnh chạy và profile trong plan sau duyệt dựa trên cấu hình repo tại thời điểm đó. Lượt spec này không chạy/khẳng định Java tests vì không có thay đổi Java và đang có work-in-progress chưa thuộc task.

## 10. Các điểm cần duyệt trước Java

1. Phạm vi đã được người dùng làm rõ: chỉ Payment. Booking/Ticket là biên giao tiếp được mô phỏng trong test Payment, không phải công việc triển khai hoặc nghiệm thu của task.
2. Shop App chốt các tên CANDIDATE ở mục 3 và mục 9, auth actor propagation, HTTP status/idempotency và mở rộng BookingIssuanceResult cho success.
3. Duyệt checkpoint dùng inbox/outbox trong thời gian WAITING_TICKETS và yêu cầu retention/recovery; plan chi tiết phải thể hiện query thực thi được.
4. Sau đó mới chốt plan chi tiết và triển khai Java. Chưa có bằng chứng Shop App duyệt trong chat này; không ghi trạng thái APPROVED hoặc tự gửi thông báo ra ngoài.

Không có thay đổi Java, DDL, broker binding, gateway hoặc cấu hình runtime trong bản spec này.
