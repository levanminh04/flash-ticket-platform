# Payment GOV-156 / GOV-157 — thiết kế nháp theo Shop App

- Soạn/cập nhật: 30/10/2026. Phiên bản **v2 — BRAINSTORMING — APPROVED**; đã triển khai phần Payment theo [plan SA-PAY-v2.1](../../java-workflow/plans/2026-10-30-gov-156-payment-plan.md).
- Phạm vi được người dùng làm rõ: **chỉ công việc Payment**. Không triển khai, sửa cấu hình, schema, tài liệu hay test nội bộ Booking/Ticket hoặc service khác. Các service đó chỉ xuất hiện như hợp đồng bên ngoài mà Payment sử dụng.
- Nguồn nghiệp vụ: yêu cầu người dùng trong task này, GOV-156 và GOV-157 được xác nhận/duyệt ngày **29/10/2026**, cùng ảnh `Đặt vé.png` đính kèm. Ảnh danh sách có năm subtask là phạm vi phân rã, không phải hợp đồng kỹ thuật.
- Nhánh: `codex/gov-156-payment-flow`, từ `dev/tuan`, HEAD nền `e47b1f6`.
- Skill hiện hành: `shopapp-java-brainstorming`; giữ nguyên đường dẫn tài liệu cũ. Shop App là **nguồn phong cách code**, không phải service hay bên duyệt bên ngoài. Người dùng duyệt bảng class/phương thức trong tài liệu này.
- Nguồn xác nhận: yêu cầu ban đầu, phạm vi “Chỉ làm các công việc của payment các servixe khác không quan tâm”, và câu **“oke triển khai đi”** sau câu hỏi duyệt rõ C01–C20/M01–M54/E01–E06 cho cả thiết kế, kế hoạch và thực thi. Phạm vi/phong cách cùng delta trong plan SA-PAY-v2.1 đã được duyệt; không suy thành quyền sửa service khác.
- Lượt chuẩn bị v2 trước đó chỉ sửa spec. Lượt triển khai hiện tại sửa Java/test/config Payment theo plan; không tạo nhánh khác, không stage/commit/push và không ghi đè các phần work-in-progress không thuộc task.

## 1. Mục tiêu, ưu tiên nguồn và hiện trạng

Frontend tạo thanh toán trực tiếp tại Payment. Booking là owner của đơn, giá, voucher, reservation và inventory; Payment giữ bản PurchaseSnapshot bất biến và điều phối **một** Saga mua vé. Payment chỉ hoàn tất Saga sau bằng chứng Booking đã commit order COMPLETED.

Trong phạm vi task, yêu cầu GOV-156/GOV-157 mới được người dùng cung cấp ưu tiên hơn hướng start/success cũ trong B13/B14 và GOV-149/GOV-150. Không tự đánh dấu toàn bộ tài liệu kiến trúc cũ là APPROVED hoặc sửa các quyết định lịch sử. Không đưa việc đồng bộ tài liệu kiến trúc toàn hệ thống hoặc mock service riêng vào kế hoạch Payment này.

Hiện trạng trước triển khai, được đối chiếu qua CodeGraph khi soạn thiết kế:

| Thành phần | Hiện trạng quan sát | Phải thay đổi sau duyệt |
| --- | --- | --- |
| PaymentController | Có `POST /api/v1/payments`, lấy buyerSubject từ JWT | Giữ entrypoint trực tiếp Payment; chốt hợp đồng idempotency |
| PaymentService.createPayment | Khóa snapshot đã có; thiếu snapshot thì PAYMENT_NOT_READY; tạo/tái dùng attempt và URL | Gọi Booking chuẩn bị snapshot trước transaction Payment; lưu snapshot lần đầu cùng attempt |
| PaymentService.handleIPN | Xác minh VNPay, lưu charge và cập nhật attempt | Ghi Saga WAITING_BOOKING và outbox AcceptPayment cùng commit trước phản hồi IPN |
| PaymentSagaService / MessagingService | Interface rỗng; implementation mới chỉ có repository | Bổ sung handler, relay, recovery và quyết định trạng thái bền |
| RefundServiceImpl / FinanceServiceImpl | Mới có dependency repository, chưa có method nghiệp vụ | Không tuyên bố refund/cancellation runtime đã hoàn thiện; giữ các invariant trong thiết kế, không mở rộng sang payout hoặc service khác |
| PurchaseSnapshotValidator / ContractHashService | Đã có validator và canonical hash | Tận dụng; không lấy giá FE hoặc tính lại snapshot ở Payment |
| DTO / tài liệu cũ | StartPayment/inbox start-requested; Ticket success gửi Booking; BookingIssuanceResult chỉ FAILED | Ngừng dùng start-requested trong luồng mới; success phải qua Payment; mở rộng hợp đồng kết quả để chuyển success tới Booking |

Phạm vi chuẩn bị và triển khai sau duyệt: API, client gọi ra ngoài, adapter VNPay hiện có, DTO, repository, transaction, consumer, inbox/outbox, Saga, recovery và kiểm thử **trong Payment** cho năm subtask. Ngoài phạm vi: mọi thay đổi tại service khác, frontend, triển khai production, đổi payout/finance, đổi token QR vé hoặc thêm gateway mới. Kiểm thử Payment dùng stub/fake ở test source Payment cho các hợp đồng Booking/Ticket; không chờ hai service đó hoàn thiện mới làm Payment, không tạo mock service riêng và không truy cập DB chéo. Chỉ báo cáo bằng chứng phía Payment, không nhận nghiệm thu E2E toàn hệ thống.

## 2. Phương án

Chỉ một hướng đáp ứng các ràng buộc đã chốt: **Payment gọi một POST nội bộ Booking để chuẩn bị PurchaseSnapshot, sau đó điều phối bằng inbox/outbox và phản hồi nghiệp vụ**. Tận dụng PostgreSQL/RabbitMQ và năm trạng thái Saga hiện có, không thêm orchestrator hay Saga khác.

Booking start/outbox `payment.start-requested.v1`, freeze riêng rồi GET snapshot, GET có ghi freeze, và Ticket → Booking trực tiếp ở nhánh success đều bị loại bởi yêu cầu. Gọi mạng trong transaction giữ khóa cũng không phù hợp ranh giới giao dịch hiện tại. Đánh đổi của phương án chọn: phải lưu kết quả trung gian, chống lặp và phục hồi message; đổi lại crash hoặc broker confirm không làm mất bước nghiệp vụ.

Phương án này và các khai báo phía Payment đã được người dùng duyệt cùng plan SA-PAY-v2.1; việc triển khai không chứng nhận các service đối tác đã hỗ trợ hợp đồng.

Mẫu đã đọc là snapshot Shop App đóng gói cùng skill, không phải checkout toàn bộ ứng dụng. Dùng `OrderController.createOrder` cho controller mỏng/constructor injection, `IOrderService` + `OrderService.createOrder/updateOrder` cho method nghiệp vụ gọi repository trực tiếp, `OrderRepository.findByUserId` cho query theo tên, `CategoryDTO` cho DTO mới và `JwtTokenUtil` cho property `@Value`. Không sao chép `catch(Exception)` rồi trả thông tin lỗi thô, Float/Date hoặc các annotation Lombok trùng nhau.

Convention: đã đọc `AGENTS.md` Payment và convention chung của skill. Git common directory là `C:/Users/WEOHWOEH/Documents/DATN 2026/Docs/flash-ticket-platform/.git`; chưa thấy kho convention riêng được khai báo cho repository. Giữ Java 21/Spring Boot 4.1.1, UUID/Instant/BigDecimal, Keycloak và MapStruct hiện có; không đổi version/package hàng loạt để giống Shop App.

## 3. Hợp đồng đã triển khai phía Payment theo SA-PAY-v2.1

| Biên | Tên hợp đồng Payment | Đầu vào / đầu ra và điều kiện |
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

Public POST đề xuất giữ HTTP 200 + ApiResponse<PaymentStart> hiện có cho cả tạo mới và tái dùng, sau commit; không thêm response wrapper chỉ để phân biệt 201/200 như bản trước. Trả 409 nếu key cũ có input khác hoặc đơn không payable; 503 khi Booking tạm chưa xác định. Key được scope theo `(orderId, requestKey)` và luôn kiểm actor. Request hash gồm actor/orderId/provider; clientIp không được làm thay đổi định danh thanh toán của retry. Cùng key không bao giờ tạo attempt thứ hai; muốn attempt mới sau FAILED phải có key mới và Booking vẫn xác nhận payable. Nếu đã có unfinished attempt của key khác, trả 409 PAYMENT_IN_PROGRESS; không gán ngầm key mới vào attempt cũ rồi làm mất receipt cho lần retry sau.

Snapshot giữ field/money/version/canonical hash theo B13 và validator hiện có. Payment đối chiếu orderId/owner/expiry/currency/total, không sửa snapshot lỗi để cho qua. Receipt công khai lấy `PaymentAttempt.request_key/request_hash` hiện có; không còn dùng inbox `payment.start-requested.v1` làm nguồn kết quả của POST mới. Cùng key sau expiry/cancel không mở lại thanh toán hoặc trả URL có thể trả tiền; truy vấn lịch sử vẫn chỉ đọc. Mọi request cần mở/tái sử dụng URL phải qua Booking kiểm payable, kể cả Payment đã có snapshot.

Không thêm PaymentTransactionService, OutboxRelay hay PaymentSagaRecoveryService riêng. Dùng TransactionTemplate trong PaymentService để gọi Booking trước rồi mới mở transaction lưu; tái sử dụng PaymentSagaService/Impl và MessagingService/Impl. Một PaymentWorker gọi relay/recovery, một PaymentMessageListener nhận ba loại phản hồi. Bảng C/M ở dưới là danh sách đề xuất thay cho các tên rời rạc của bản trước. GET lịch sử hiện có vẫn chỉ đọc; requestId/inbox start cũ không được dùng làm receipt cho POST mới.

## 4. Trách nhiệm Payment khi lấy snapshot

Payment gọi **một POST nội bộ** bằng client của mình, ngoài transaction Payment, gửi orderId và buyerSubject lấy từ JWT đã xác thực. Không thêm API freeze riêng, không gọi GET có tác dụng ghi và không đọc DB Booking.

- Response thành công: kiểm orderId/subject, cấu trúc, tổng tiền, currency, frozenAt, version, expiry và canonical hash; dùng nguyên snapshot để lưu và tạo attempt. Không áp voucher hoặc tính lại giá ở Payment.
- Retry: gọi lại cùng hợp đồng và chấp nhận bản snapshot đã lưu, không tự thay frozenAt/version/amount/expiresAt. Nếu khác snapshot Payment đã lưu thì từ chối xung đột.
- Lỗi hết hạn/hủy/không payable: không tạo attempt mới và không trả URL để tiếp tục trả tiền. Timeout/response lỗi không được coi là đã đủ điều kiện thanh toán.
- GOV-157 về voucher là bảo đảm của hợp đồng bên ngoài: voucher commit trước thì snapshot có giảm giá; snapshot commit trước thì voucher bị PRICE_FROZEN. Payment chỉ dùng số tiền trong snapshot; không triển khai khóa order, voucher usage hoặc race test bên Booking.
- Mất response sau khi bên ngoài đã lưu snapshot: Payment retry POST, không phát `payment.start-requested` hoặc tự dựng snapshot thay thế. Stub trong test Payment mô phỏng snapshot ổn định và các lỗi có thể trả về.

### Dữ liệu PurchaseSnapshot Payment cần

Giữ DTO đã có, không tạo PurchaseSnapshot thứ hai hoặc chuyển toàn bộ record sang Lombok. Snapshot không chứa paymentUrl, chargeId, attemptId hoặc Saga state.

| Nhóm | Field hiện có | Cách Payment dùng |
| --- | --- | --- |
| Định danh | schemaVersion=1, orderId, eventId, buyerSubject | Kiểm hợp đồng và khớp order/actor |
| Phiên bản/thời gian | sourceConfigVersion, sourceOrderVersion, frozenAt, expiresAt | Nhận diện snapshot bất biến; không gia hạn; kiểm lại deadline trước attempt |
| Tiền | currency=VND, subtotal, discount, total | total là expectedAmount; tiền/version trên wire là String, không float |
| Chuyển cho Ticket | customerEmail, eventTitle, eventStartsAt, eventEndsAt, venueName, mode, sectorId, items | Lưu nguyên snapshot, gửi lại qua IssueTickets; venueName có thể null |
| Voucher | promotion: promotionId, code, discount; hoặc null | Chỉ lưu phần giảm giá đã có, không áp mã tại Payment |
| Mỗi item | orderItemId, ticketTypeId, ticketTypeName, quantity, unitPrice, lineSubtotal, sectorId, seats | Kiểm tổng tiền và tổng quantity; expectedTicketCount tính từ items |
| Mỗi ghế | seatId, seatLabel, rowName, seatNumber | Giữ đúng lựa chọn; seats=[] khi không có ghế; QUANTITY không có sector/ghế |

Gọi PurchaseSnapshotValidator.validate và ContractHashService.purchaseHash hiện có. Kiểm cấu trúc bằng ContractCodec, quan hệ amount/quantity/discount bằng validator; đối chiếu actor/order/deadline tại service. Payment tự tính snapshotHash, tự ghi receivedAt, map total → expectedAmount và lưu full JSON qua OrderPaymentSnapshotMapper.toEntity. Không yêu cầu Booking trả hash hoặc FE gửi giá.

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

## 8. Danh sách class và API sinh — đã duyệt cùng plan SA-PAY-v2.1

Đây là thiết kế, không phải plan chi tiết hoặc danh sách đã được phép viết. Đường dẫn trong hai bảng tính từ `services/payment-service/src/main/java/com/flashticket/payment_service/`; test tính từ package tương ứng trong `src/test/java`. Giữ tên interface hiện có, không đổi hàng loạt thành I...Service. Với interface/Impl ghi chung, method NEW được đề xuất ở cả interface và public @Override của Impl, không phải chỉ một phía.

| ID | Class/file | NEW hoặc EXISTING | Vai trò, field/annotation/API sinh | Mẫu Shop App hoặc ngoại lệ |
| --- | --- | --- | --- | --- |
| C01 | controller/PaymentController.java | EXISTING | Giữ @RestController, @RequiredArgsConstructor, @PreAuthorize, @Valid; thêm header key vào createPayment. Constructor vẫn nhận PaymentService | OrderController.createOrder; giữ ApiResponse/Keycloak của đích |
| C02 | service/PaymentService.java | EXISTING | Giữ @Service/@RequiredArgsConstructor và dependency hiện có; thêm final BookingPurchaseClient, TransactionTemplate, OrderPaymentSnapshotMapper, PaymentSagaService. Duyệt nhóm constructor Lombok nhận 12 dependency hiện tại + 4 dependency mới; không thêm getter/setter/builder | OrderService; TransactionTemplate là E01 bên dưới |
| C03 | service/PaymentSagaService.java; service/impl/PaymentSagaServiceImpl.java | EXISTING | Giữ interface/Impl, @Service/@RequiredArgsConstructor. Constructor Impl nhận PaymentSagaRepository hiện có, thêm OrderPaymentSnapshotRepository, PaymentConfirmationRepository, ChargeRepository, EventFinanceSnapshotRepository, OutboxMessageRepository, MessagingService, RefundService, ContractCodec, PurchaseSnapshotValidator. Không sinh API khác | IOrderService/OrderService; không đổi prefix/package |
| C04 | service/MessagingService.java; service/impl/MessagingServiceImpl.java | EXISTING | Giữ @Service/@RequiredArgsConstructor; constructor nhận InboxMessageRepository, OutboxMessageRepository hiện có + ContractCodec, ContractHashService mới. Không getter/setter | IOrderService/OrderService; inbox/outbox E03 |
| C05 | client/BookingPurchaseClient.java | NEW | @Component/@RequiredArgsConstructor; private final RestClient bookingRestClient, ContractCodec codec; @Value payment.booking.snapshot-path. API sinh chỉ constructor(RestClient, ContractCodec), không getter/setter | Constructor injection như OrderService; HTTP client là E02 |
| C06 | dto/request/PreparePurchaseSnapshotRequest.java | NEW | Class, field @NotBlank String buyerSubject; @Getter/@Setter/@NoArgsConstructor/@AllArgsConstructor. API sinh: public ctor(), ctor(String), getBuyerSubject(), setBuyerSubject(String); không @Data/builder/equals/hashCode/toString | CategoryDTO, bỏ annotation trùng; DTO nội bộ Payment gửi đi |
| C07 | config/PaymentRuntimeConfig.java | NEW | @Configuration(proxyBeanMethods=false)/@EnableScheduling; @Value cho booking base-url/connect-timeout/read-timeout. Không Lombok; ba @Bean M21–M23, không custom constructor | JwtTokenUtil dùng @Value; hạ tầng E01/E02/E03 |
| C08 | messaging/PaymentMessageListener.java | NEW | @Component/@RequiredArgsConstructor; final ContractCodec, MessageInvariantValidator, PaymentSagaService; constructor sinh đúng ba dependency. Ba @RabbitListener với ackMode=MANUAL, không tạo listener cho start-requested | Listener E03; không đưa nghiệp vụ vào adapter |
| C09 | worker/PaymentWorker.java | NEW | @Component/@RequiredArgsConstructor; final MessagingService, PaymentSagaService, RabbitTemplate; constructor sinh đúng ba dependency. @Value exchange/batch-size/lease/confirm-timeout; hai @Scheduled M19/M20. Không getter/setter | Constructor/@Value theo mẫu; worker E03 |
| C10 | config/RabbitMqConfig.java | EXISTING | Giữ hai @Bean hiện có; thêm paymentSagaTopology() trả Declarables cho ba queue phản hồi Payment và binding tương ứng; không khai báo queue Booking/Ticket | E03; không sửa broker binding của service khác |
| C11 | dto/common/ContractCodec.java; validation/MessageInvariantValidator.java | EXISTING | Giữ codec/validator đã có; thêm typed envelope reader và ba kiểm tra message cụ thể. Không tạo parser/validator framework mới | Tái sử dụng ngoại lệ đã có ở đích E04 |
| C12 | entity/PaymentSaga.java; PaymentConfirmation.java; OutboxMessage.java; Refund.java | EXISTING | Giữ field/JPA/getter/setter/protected no-arg hiện tại; chỉ thêm bốn static factory M25–M28. Không đổi @Version, association hoặc public hóa constructor | Setter/save như OrderService; factory theo PaymentAttempt.pending hiện có |
| C13 | dto/message/BookingIssuanceResult.java | EXISTING | Giữ record; đổi thành 6 field orderId, chargeId, issuanceId, state, ticketCount, failureCode. API record: canonical public ctor(UUID,UUID,UUID,BookingIssuanceState,Integer,String), accessor ticketCount(), equals/hashCode/toString theo field mới; thêm isConsistent() M29 | E04: giữ record của hợp đồng hiện có, không tạo DTO thay thế |
| C14 | dto/message/BookingIssuanceState.java; exception/PaymentErrorCode.java | EXISTING | Enum đầu thêm COMPLETED; enum lỗi thêm PAYMENT_IN_PROGRESS, IDEMPOTENCY_CONFLICT, PURCHASE_SNAPSHOT_CONFLICT, BOOKING_UNAVAILABLE. Giữ ctor/code/status/message hiện có; không type mới | Dùng exception hiện có, không tạo exception wrapper |
| C15 | repository/PaymentSagaRepository.java; OutboxMessageRepository.java; RefundRepository.java; EventFinanceSnapshotRepository.java | EXISTING | Spring Data interface, query/lock cụ thể ở M30–M36; không thêm repository wrapper hoặc native upsert mặc định | OrderRepository.findByUserId; pessimistic lock E01 |
| C16 | service/RefundService.java; service/impl/RefundServiceImpl.java | EXISTING | Giữ constructor bốn repository hiện có và @RequiredArgsConstructor; thêm ensureRefund M13 để ghi một nghĩa vụ/charge trong TX đang chạy, không triển khai lại toàn bộ gateway refund/payout | IOrderService/OrderService; không tuyên bố engine refund đã xong |
| C17 | client/BookingPurchaseClientTest.java | NEW TEST | Package-private class, @RestClientTest(BookingPurchaseClient.class)/@Import(PaymentRuntimeConfig.class); inject client/MockRestServiceServer, @MockitoBean OAuth2AuthorizedClientManager trả token giả; dùng Jackson/fixture hiện có; không nested TestConfiguration hoặc fake service type | Ngoại lệ kiểm thử E05; methods M40–M43 |
| C18 | service/PaymentFlowIntegrationTest.java | NEW TEST | Package-private class, @SpringBootTest/@ActiveProfiles("payment-flow-test"); @MockitoBean BookingPurchaseClient; dependency DB/broker và service qua @Autowired trong test. Tận dụng fixture có sẵn; không anonymous/nested class, không generated test API | E05; methods M44–M53 |
| C19 | service/CinemaVNPayServiceTest.java (test) | EXISTING | Sửa chỗ khởi tạo PaymentService theo constructor mới và gọi createPayment theo signature mới; reuse setup/close/callback/sign/test hiện có, không thêm class/helper/test method ở file này | Giữ kiểu test của repo |

Tái sử dụng nguyên bản, không tạo type/method mới: PurchaseSnapshot/PurchaseItem/PurchaseSeat/PurchasePromotion, CreatePaymentRequest, PaymentStart/PaymentView, Envelope, AcceptPayment/PaymentDecision/IssueTickets/IssuanceResult/OrderCompleted, PurchaseSnapshotValidator, ContractHashService, MapStruct mapper, PaymentAttempt.pending, Charge.received, InboxMessage.processed và repository snapshot/attempt/charge/confirmation/inbox hiện có. Getter/setter/ctor đã có không phải khai báo mới; thay đổi ngoài danh sách phải trình delta.

## 9. Danh sách phương thức — đã duyệt cùng plan SA-PAY-v2.1

REUSE nghĩa dùng/sửa thân method đã có; DELTA nghĩa chữ ký/annotation thay đổi và vẫn phải duyệt. Method không ghi throws không khai báo checked exception; dùng PaymentException/ContractValidationException hiện có. Constructor/API sinh được duyệt theo field/annotation ở bảng C, không có quyền bổ sung helper hoặc overload ngoài bảng.

| ID | Class | Constructor/method đầy đủ | NEW hoặc REUSE | Cách viết và lý do cần |
| --- | --- | --- | --- | --- |
| M01 | PaymentController | public ApiResponse<PaymentStart> createPayment(CreatePaymentRequest request, UUID requestKey, HttpServletRequest httpRequest, Jwt jwt) | REUSE + DELTA | @PostMapping("/payments"), @PreAuthorize BUYER, @Valid body; @RequestHeader("Idempotency-Key") UUID; lấy subject từ JWT |
| M02 | PaymentService | public PaymentStart createPayment(CreatePaymentRequest request, HttpServletRequest httpRequest, String buyerSubject, UUID requestKey) | REUSE + DELTA | @Transactional(propagation=NEVER) để không gọi mạng trong TX; gọi client rồi TransactionTemplate.execute; commit xong mới trả. Cạnh tranh unique rollback hoàn toàn, retry hữu hạn bằng TX mới |
| M03 | PaymentService | private PaymentStart savePaymentAttempt(PurchaseSnapshot incomingPurchase, String buyerSubject, UUID requestKey, String clientIp) | NEW | Chỉ được gọi trong TransactionTemplate; validate, khóa, so hash/key, map snapshot, PaymentAttempt.pending/setPaymentUrl/save; không @Transactional trên private helper |
| M04 | PaymentService | public String handleIPN(Map<String,String> params) | REUSE | Giữ @Transactional; verify rồi charge+SUCCEEDED+recordSuccessfulCharge/outbox cùng commit. Không nuốt lỗi commit thành ACK thành công |
| M05 | PaymentService | private String buildVNPayUrl(OrderPaymentSnapshot snapshot, PaymentAttempt payment, String clientIp); private String resolveClientIp(CreatePaymentRequest request, HttpServletRequest httpRequest) | REUSE | Giữ helper ký URL cục bộ; retry trả URL đã lưu, không ký lại theo clientIp mới |
| M06 | BookingPurchaseClient | public PurchaseSnapshot preparePurchaseSnapshot(UUID orderId, String buyerSubject) | NEW | Một RestClient POST, body C06, credential service; nhận JSON String rồi codec.read(...,PurchaseSnapshot.class), không tin số tiền FE |
| M07 | PaymentSagaService + Impl | public void recordSuccessfulCharge(Charge charge) | NEW | Interface + @Override; Impl @Transactional(MANDATORY). Chọn candidate, tạo Saga WAITING_BOOKING + outbox; charge thừa đi ensureRefund, không thay candidate |
| M08 | PaymentSagaService + Impl | public void recordPaymentDecision(Envelope<PaymentDecision> incomingEnvelope) | NEW | Impl @Transactional; khóa theo thứ tự, so hash/dedupe; ACCEPTED → confirmation + WAITING_TICKETS + IssueTickets; từ chối đi nghĩa vụ hoàn đúng nguyên nhân |
| M09 | PaymentSagaService + Impl | public void recordIssuanceResult(Envelope<IssuanceResult> incomingEnvelope) | NEW | Impl @Transactional; kiểm count/charge/issuance, checkpoint + Booking success outbox; vẫn WAITING_TICKETS. FAILED hợp lệ mới bù trừ |
| M10 | PaymentSagaService + Impl | public void recordOrderCompleted(Envelope<OrderCompleted> incomingEnvelope) | NEW | Impl @Transactional; chỉ khớp confirmation/checkpoint và chưa compensation/cancel mới COMPLETED |
| M11 | PaymentSagaService + Impl | public List<UUID> findRecoverableOrderIds(Instant dueAt, int limit) | NEW | Impl @Transactional(readOnly=true); chọn order đến hạn bằng repository, không gọi mạng |
| M12 | PaymentSagaService + Impl | public void recoverSaga(UUID orderId) | NEW | Impl @Transactional; khóa/recheck state, lấy snapshot/checkpoint/lệnh đã lưu, enqueue recovery đúng bước, tăng nextAttemptAt; không dùng broker ACK để tiến Saga |
| M13 | RefundService + Impl | public Refund ensureRefund(Charge charge, RefundReason reason, Instant createdAt) | NEW | Impl @Transactional(MANDATORY); findByChargeId hoặc Refund.pending + save, giữ reason cũ. Parent order lock + unique(charge) chặn nghĩa vụ lặp |
| M14 | MessagingService + Impl | public boolean recordInbox(String consumerName, Envelope<?> incomingEnvelope, JsonNode resultPayload) | NEW | Impl @Transactional(MANDATORY); hash trước dedupe, save InboxMessage.processed, cùng ID khác hash thì lỗi; checkpoint bền theo consumer/type |
| M15 | MessagingService + Impl | public void enqueue(Envelope<?> outgoingEnvelope) | NEW | Impl @Transactional(MANDATORY); codec.toTree + OutboxMessage.pending + save, không publish trực tiếp |
| M16 | MessagingService + Impl | public List<OutboxMessage> claimOutbox(Instant now, Instant leaseUntil, int limit) | NEW | Impl @Transactional(REQUIRES_NEW); query có khóa, tăng attempts, đặt nextAttemptAt=leaseUntil, commit rồi mới publish |
| M17 | MessagingService + Impl | public void markPublished(UUID messageId, int claimedAttempt, Instant publishedAt) | NEW | Impl @Transactional(REQUIRES_NEW); khóa row và so attempts với token claim; chỉ mark đúng claim, không ghi Saga |
| M18 | MessagingService + Impl | public void rescheduleOutbox(UUID messageId, int claimedAttempt, Instant retryAt, String errorCode) | NEW | Impl @Transactional(REQUIRES_NEW); cùng token check, lưu backoff/lỗi lọc; không xóa message khi hết số retry |
| M19 | PaymentWorker | public void publishOutbox() | NEW | @Scheduled(fixedDelayString="${payment.outbox.poll-delay}"); claim → RabbitTemplate gửi persistent + mandatory, CorrelationData future confirm và không returned → markPublished; timeout/nack/no-route → reschedule. Không giữ TX khi đợi broker |
| M20 | PaymentWorker | public void recoverSagas() | NEW | @Scheduled(fixedDelayString="${payment.saga.poll-delay}"); gọi M11/M12 qua service proxy; mỗi Saga TX riêng |
| M21 | PaymentRuntimeConfig | public TransactionTemplate paymentTransactionTemplate(PlatformTransactionManager transactionManager) | NEW | @Bean, template REQUIRES_NEW; dependency qua tham số, không tự viết TransactionManager |
| M22 | PaymentRuntimeConfig | public OAuth2AuthorizedClientManager paymentAuthorizedClientManager(ClientRegistrationRepository registrations, OAuth2AuthorizedClientService authorizedClients) | NEW | @Bean; Spring AuthorizedClientServiceOAuth2AuthorizedClientManager + client_credentials provider. Không tự viết JWT/token cache |
| M23 | PaymentRuntimeConfig | public RestClient bookingRestClient(RestClient.Builder builder, OAuth2AuthorizedClientManager paymentAuthorizedClientManager) | NEW | @Bean; base URL/timeout từ @Value; OAuth2 interceptor + principal payment-service/registration booking, JDK request factory. Không relay JWT buyer làm service credential |
| M24 | RabbitMqConfig | public Declarables paymentSagaTopology() | NEW | @Bean; ba durable queue Payment nhận decision/result/completed + binding; không tạo/đổi queue service khác |
| M25 | PaymentSaga | public static PaymentSaga waitingBooking(UUID orderId, UUID chargeId, UUID commandId, Instant createdAt) | NEW | Factory gán field hiện có, state WAITING_BOOKING; rowVersion để null cho entity mới |
| M26 | PaymentConfirmation | public static PaymentConfirmation accepted(Charge charge, Instant confirmedAt) | NEW | Factory dùng order/charge/amount/currency của charge đã được Booking nhận; không public hóa no-arg ctor |
| M27 | OutboxMessage | public static OutboxMessage pending(Envelope<?> envelope, JsonNode payload) | NEW | Factory copy metadata, attempts=0, nextAttemptAt=occurredAt; không sinh messageId lần nữa |
| M28 | Refund | public static Refund pending(Charge charge, RefundReason reason, Instant createdAt) | NEW | Factory giữ đúng amount/currency, sinh refundId một lần, state theo enum hiện có; không thực hiện gateway refund |
| M29 | BookingIssuanceResult | public boolean isConsistent() | NEW | @AssertTrue; COMPLETED đòi ticketCount>0/failureCode null; FAILED đòi ticketCount=0/failureCode không rỗng. Service còn kiểm expected count |
| M30 | PaymentSagaRepository | public Optional<PaymentSaga> findForUpdateByOrderId(UUID orderId) | NEW | @Lock(PESSIMISTIC_WRITE)/@Query JPQL/@Param; không wrapper storage |
| M31 | PaymentSagaRepository | public List<UUID> findRecoverableOrderIds(Instant dueAt, Pageable pageable) | NEW | JPQL loại COMPLETED/COMPENSATED, thứ tự nextAttemptAt/orderId; REQUIRES_NEW không đặt ở repository để thay service TX |
| M32 | OutboxMessageRepository | public List<OutboxMessage> findDueForUpdate(Instant dueAt, Pageable pageable) | NEW | @Lock(PESSIMISTIC_WRITE)/JPQL; unpublished và đến hạn, thứ tự nextAttemptAt/messageId. Khóa ngắn; chưa đề xuất native SKIP LOCKED |
| M33 | OutboxMessageRepository | public Optional<OutboxMessage> findForUpdateByMessageId(UUID messageId) | NEW | @Lock(PESSIMISTIC_WRITE)/JPQL; kiểm token claim trước cập nhật |
| M34 | OutboxMessageRepository | public Optional<OutboxMessage> findFirstByAggregateIdAndMessageTypeOrderByOccurredAtDescMessageIdDesc(UUID aggregateId, String messageType) | NEW | Derived query; phục hồi đọc payload/checkpoint đã lưu theo order/type, vẫn kiểm charge/issuance |
| M35 | RefundRepository | public Optional<Refund> findByChargeId(UUID chargeId) | NEW | Derived query, unique(charge) là hàng rào cuối |
| M36 | EventFinanceSnapshotRepository | public Optional<EventFinanceSnapshot> findForUpdateByEventId(UUID eventId) | NEW | @Lock(PESSIMISTIC_WRITE)/JPQL; đọc cancellation marker trước khóa snapshot/order; không tự tạo finance config giả |
| M37 | ContractCodec | public <T> Envelope<T> readEnvelope(String json, Class<T> payloadType) | NEW | Dùng JsonMapper.constructParametricType + decode hiện có; không TypeReference anonymous class hoặc decoder mới |
| M38 | MessageInvariantValidator | public void validatePaymentDecision(Envelope<PaymentDecision> envelope); public void validateIssuanceResult(Envelope<IssuanceResult> envelope); public void validateOrderCompleted(Envelope<OrderCompleted> envelope) | NEW | Ba method cùng ID nhóm; kiểm messageType/schema/correlation/aggregate/order và quan hệ outcome/count. Giữ fail helper hiện có |
| M39 | PaymentMessageListener | public void onPaymentDecision(Message message, Channel channel) throws IOException; public void onIssuanceResult(Message message, Channel channel) throws IOException; public void onOrderCompleted(Message message, Channel channel) throws IOException | NEW | Ba @RabbitListener riêng, ackMode=MANUAL; UTF-8 → codec M37 → validator M38 → service → basicAck sau service commit. Không catch lỗi rồi ACK thành công |
| M40 | BookingPurchaseClientTest | void setUp() | NEW | @BeforeEach; MockRestServiceServer và fixture, không tạo mock server type mới |
| M41 | BookingPurchaseClientTest | void postsSnapshotRequestWithServiceIdentity() | NEW | @Test: đúng một POST/order/subject và Authorization; authorized-client manager trả token giả, không gọi Keycloak thật và không tuyên bố đã kiểm token endpoint |
| M42 | BookingPurchaseClientTest | void rejectsInvalidSnapshotResponses() | NEW | @Test: invalid JSON/field/money; client không sửa payload để cho qua |
| M43 | BookingPurchaseClientTest | void mapsRejectedAndUnavailableBookingResponses() | NEW | @Test: 401/403/404/409/5xx/timeout, không mở attempt từ lỗi client |
| M44 | PaymentFlowIntegrationTest | void setUp() | NEW | @BeforeEach; dùng DB/broker test profile và fixture có sẵn, reset dữ liệu test Payment; không outer test TX che commit thật |
| M45 | PaymentFlowIntegrationTest | void storesSnapshotAndReusesAttemptWithoutRepricing() | NEW | @Test: client ngoài TX; owner/deadline/hash; URL sau commit, giữ cùng key/URL/expiry |
| M46 | PaymentFlowIntegrationTest | void serializesConcurrentPaymentRequests() throws Exception | NEW | @Test: cùng/khác key, unique loser rollback rồi TX mới, UNKNOWN/FAILED; không tạo thêm unfinished attempt |
| M47 | PaymentFlowIntegrationTest | void commitsChargeAndWaitingBookingBeforeWebhookResponse() | NEW | @Test: charge/attempt/Saga/outbox atomic; duplicate/late/extra callbacks; rollback không ACK thành công |
| M48 | PaymentFlowIntegrationTest | void issuesTicketsOnlyAfterBookingAcceptance() | NEW | @Test: chỉ ACCEPTED mới có confirmation/IssueTickets; LATE/CANCELLED/DUPLICATE giữ nghĩa vụ hoàn |
| M49 | PaymentFlowIntegrationTest | void forwardsTicketSuccessAndWaitsForBookingCompletion() | NEW | @Test: checkpoint+Booking success outbox atomic, broker confirm không đổi Saga; đúng OrderCompleted mới COMPLETED |
| M50 | PaymentFlowIntegrationTest | void rejectsMismatchedOrReplayedSagaResults() | NEW | @Test: replay/hash mismatch/order/charge/issuance/count sai; không tạo hiệu ứng nghiệp vụ lặp |
| M51 | PaymentFlowIntegrationTest | void recoversSagaAfterCommitAndDeliveryFailures() throws Exception | NEW | @Test: crash/replay quanh các commit Payment, lease/confirm/no-route, mất result/completion; không mất bước 47 |
| M52 | PaymentFlowIntegrationTest | void keepsExpiryCancellationAndRefundGuards() | NEW | @Test: expiry/cancel response, cancellation marker đã có, terminal FAILED; timeout không tự hoàn, compensation không hồi sinh |
| M53 | PaymentFlowIntegrationTest | void acknowledgesMessagesOnlyAfterPaymentCommit() throws Exception | NEW | @Test: listener manual ACK đúng thứ tự, DB commit lỗi không ACK; chỉ kiểm Payment |

Các method REUSE khác giữ nguyên signature: PaymentController.handleIPN/handleReturn/getPayment, PaymentService.handleReturn/buildAppReturnUri/getPayment và helper đọc lịch sử. Không biến GET thành freeze, tạo attempt hoặc enqueue. Các constructor/service @Override mới và API Lombok trong C01–C19 là một phần danh sách duyệt, không bỏ qua vì framework sinh.

## 10. Mapping, transaction, lỗi và điểm khác Shop App — đã duyệt cùng plan SA-PAY-v2.1

- **E01 — Transaction và cạnh tranh:** annotation @Transactional trên method nghiệp vụ qua service proxy; TransactionTemplate chỉ tại đoạn lưu sau HTTP của PaymentService để tránh thêm service chỉ chứa một TX và tránh self-invocation. Service khóa theo thứ tự mục 5; save() lỗi lúc commit phải thoát TX, không cố đọc winner trong TX PostgreSQL đã lỗi. JPA derived query/save được ưu tiên; JPQL @Lock chỉ ở các query thật sự cần phân xử. Ngoại lệ này cần duyệt, không phải mẫu OrderService có sẵn. Đã đối chiếu [Spring TransactionTemplate](https://docs.spring.io/spring-framework/reference/data-access/transaction/programmatic.html) và [source execute/rollback của Spring](https://github.com/spring-projects/spring-framework/blob/main/spring-tx/src/main/java/org/springframework/transaction/support/TransactionTemplate.java).
- **E02 — HTTP và credential service:** RestClient gửi một POST; credential client_credentials của Payment, buyerSubject trong body là actor được lấy từ JWT đã kiểm. Dùng OAuth2AuthorizedClientManager/interceptor của Spring Security, không tự ký token hoặc dùng secret trong source. Đề xuất thêm Spring Boot OAuth2 client starter thuộc BOM hiện tại, chỉ cấu hình registration/token-uri/credential ở Payment; provisioning Keycloak không thuộc task. Thiếu credential/response hợp lệ thì fail closed, test giả lập. Đã đối chiếu [Spring Security RestClient và principal theo ứng dụng](https://docs.spring.io/spring-security/reference/servlet/oauth2/client/authorized-clients.html). Đây là dependency/phong cách mới PENDING.
- **E03 — Messaging/Saga:** Shop App snapshot không có mẫu inbox/outbox/Saga để sao chép. Dùng RabbitTemplate, CorrelationData future và ba listener cụ thể, không tạo handler registry/strategy/framework điều phối. Claim lease bằng attempts/nextAttemptAt hiện có; mark/reschedule phải so claimedAttempt. Chỉ published_at sau confirm ACK **và không returned**, mandatory=true/returns=true; consumer ACK sau DB commit; cả hai không tiến Saga. Đã đối chiếu [Spring AMQP confirms/returns](https://docs.spring.io/spring-amqp/reference/amqp/template.html). Recovery có thể tạo envelope operation mới với causation cũ nhưng giữ order/charge/issuance/input; broker redelivery giữ nguyên ID/payload. Không gọi message lặp là tác động nghiệp vụ lặp.
- **E04 — DTO/mapping:** DTO mới C06 theo class Lombok; giữ record/Envelope/ContractCodec/MapStruct đã có vì đổi toàn bộ sẽ mở rộng task và thay hợp đồng. Không thêm ModelMapper hoặc generic BaseResponse. ContractCodec phải thật sự validate Bean Validation, sau đó validator kiểm quan hệ các field; không cho rằng Jackson mapping tự chạy validation. Với C13, bỏ @NotBlank bắt buộc vô điều kiện trên failureCode và chuyển sang @AssertTrue theo state, thêm @NotNull/@PositiveOrZero ticketCount. Enum lỗi chỉ thêm các nguyên nhân cụ thể C14, giữ Problem/ApiResponse hiện có.
- **E05 — Test:** Spring/JUnit/Mockito/PostgreSQL/RabbitMQ là cách kiểm chứng của repo, không phải source mẫu Shop App. Hai class test mới và các method/helper đã kê đầy đủ ở C17–C18/M40–M53; không tạo test infrastructure hoặc anonymous class ngoài danh sách. Stub ở test Payment, không viết service giả độc lập; DB/broker profile thật chỉ chứng minh transaction/delivery của Payment. Không tuyên bố đã chạy test hoặc nghiệm thu service khác trong pha thiết kế.

Property mới dùng @Value trên field bean theo JwtTokenUtil, dependency dùng constructor injection. @Value field không để final và không nằm trong constructor Lombok. Chỉ đề xuất property Payment: booking URL/path/timeouts/OAuth2 registration, ba queue phản hồi Payment, exchange, batch/lease/confirm timeout/poll delay; không sửa Config Server/Gateway hoặc cấu hình service khác trong task. Chốt giá trị theo môi trường ở pha plan, không ghi secret trong tài liệu.

Không bỏ nhánh expiry/cancel/refund để giống mẫu CRUD: marker hủy đã có phải chặn success đến trễ; refund unique(charge) giữ nguyên; FAILED Ticket phải có terminal fence từ hợp đồng. Ghi nghĩa vụ hoàn ở M13 không phải bằng chứng gateway đã hoàn tiền. Nghiệp vụ nhận cancellation marker, refund execution/query và finance trong workspace còn scaffold: giữ yêu cầu/điểm kết nối, không tự đánh dấu đã triển khai hoặc duyệt mở toàn bộ refund engine. Nếu lập plan phát hiện cần khai báo bổ sung để đáp ứng các nhánh này, phần bổ sung vẫn PENDING và phải trình delta cụ thể.

## 11. Nghiệm thu thiết kế và chuyển pha

Phạm vi vẫn là năm subtask Payment: (1) tạo URL/QR từ snapshot, (2) webhook lưu tiền/Saga, (3) gửi AcceptPayment và nhận quyết định, (4) yêu cầu Ticket + nhận result, (5) báo Booking và chỉ kết thúc Saga khi nhận OrderCompleted. Mọi client/consumer/test chỉ nằm trong Payment; không lấy việc triển khai service khác làm điều kiện hoàn thành phần Payment.

Danh sách nền SA-PAY-v2 đã được duyệt cùng bản cuối **SA-PAY-v2.1: C01–C20, M01–M54, E01–E06** trong plan. Nguồn: câu “oke triển khai đi” sau câu hỏi xác nhận đúng danh sách cho ba pha. Các method nhóm M05/M38/M39 và constructor/API sinh nằm trong phạm vi này. Phần phát sinh ngoài danh sách vẫn phải trình delta, không xem đây là phê duyệt mở.

Các gate thiết kế/plan/thực thi đã có xác nhận đúng phạm vi ở trên. Xem plan để biết file thực thi, bằng chứng kiểm thử và giới hạn; tài liệu này không chứng nhận Booking/Ticket, provisioning credential hoặc gateway VNPay thật đã được tích hợp.
