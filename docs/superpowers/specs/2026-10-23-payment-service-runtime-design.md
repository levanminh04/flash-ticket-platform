# Spec — Payment Service: controller, service, repository, RabbitMQ và Saga

- Ngày: 2026-10-23.
- Trạng thái: REVIEW_READY — chờ người dùng review tài liệu; chưa cho phép triển khai từ spec này.
- Phạm vi: hoàn thiện runtime luồng mua vé, xác minh thu tiền, Saga thành công/bù trừ, hoàn tiền và hủy sự kiện trong Payment.
- Quyết định đã thống nhất trong chat: tổ chức theo tầng giống Cinema, chia service theo nghiệp vụ; giữ entity, DTO, MapStruct và SQL hiện có.
- Đầu ra lượt hiện tại: spec để review. Các tên lớp, queue, thuộc tính mới dưới đây là thiết kế đề xuất; không mô tả code đã tồn tại.

## 1. Mục tiêu và điểm xuất phát

Người dùng cần tiếp tục Payment từ scaffold tới controller/service/repository hoạt động, cấu hình RabbitMQ đầy đủ và Saga có thể phục hồi sau restart. Hoàn thành nghĩa là giao dịch đi qua đúng owner, chống tác động tiền lặp, thể hiện trạng thái chưa rõ chính xác và có bằng chứng kiểm thử; không chỉ có đủ package hoặc ứng dụng khởi động được.

Tại commit nền `7433fda`, Payment đã có 13 entity, 2 composite ID, enum, DTO, validation hình dạng, MapStruct Spring bean, Config Client và profile kiểm mapping. Chưa có controller, repository nghiệp vụ, consumer, relay, Saga handler, worker hoặc adapter VNPay. Validation tổng nhiều dòng và canonical hashing chưa có. README ghi kết quả scaffold trước đó; lượt viết spec này không chạy lại test hoặc kiểm database/broker.

Kế hoạch `services/payment-service/IMPLEMENTATION-PLAN.md` ngày 22/10 là tham khảo thứ tự; phần hiện trạng của nó đã cũ. Spec scaffold ngày 22/10 giữ lịch sử, không dùng điều khoản mapper thủ công của nó để thay MapStruct hiện tại.

### Nguồn ưu tiên

Đường dẫn tính từ root repository:

| Nguồn | Dùng cho |
| --- | --- |
| `docs/architecture/B13-api-and-event-contracts.md` | Route, DTO, envelope, snapshot, quyền, provider boundary |
| `docs/architecture/B14-main-flow-sequences.md` | Transaction, thứ tự khóa, một Saga, hoàn tiền và hội tụ |
| `docs/architecture/B15-verification-plan.md` | Các phép thử và bằng chứng nghiệm thu |
| `docs/architecture/B16-observability-baseline.md` | Correlation, log/trace/metric và dữ liệu được phép ghi |
| `docs/architecture/data/postgres/30-payment.sql` và `services/99-table-prefix.sql` | Schema nguồn và tên bảng local `tbl_*` |
| `services/payment-service/README.md`, `database/README.md` | Nền đã có, cách chạy, quyền và test database |
| `infrastructure/config-server/src/main/resources/config/payment-service.yaml` | Cấu hình runtime do Config Server cấp |

B13/B14 v0.2 vẫn REVIEW_READY. Dùng chúng làm mục tiêu tương thích, không tự chuyển thành APPROVED hoặc coi đối tác đã hiện thực. Các đề xuất chi tiết mới trong spec này cần fixture/contract test đối chiếu trước tích hợp.

## 2. Phạm vi và cách chia công việc

Trong phạm vi: validation/hash; API đọc; repository theo use case; JWT/ownership; guard Booking; RabbitMQ/topology/ACL; inbox/outbox; start payment; provider boundary và VNPay sandbox; charge; Saga; refund; finance configuration/cancellation marker; batch hoàn tiền khi hủy; internal financial summary phục vụ Event; worker, quan sát và runbook.

Không thuộc đợt này: viết nghiệp vụ Booking/Ticket/Event, UI, QR thanh toán độc lập, chuyển khoản ngân hàng tự động, pipeline RCA, xử lý orphan/mismatch Q-07. Tổng hợp payout cho organizer, thao tác đối soát và đánh paid là một phân hệ tiếp theo với spec riêng; entity EventPayout hiện có không đồng nghĩa các API đó đã hoàn thành.

Đây là spec kiến trúc tổng thể, chia thành các gói tuần tự ở mục 14. Sau review chỉ viết kế hoạch chi tiết cho gói đầu tiên, rồi triển khai/kiểm từng gói. Không dùng một lần duyệt spec để chạy toàn bộ các gói hoặc tự sửa dịch vụ khác.

## 3. Kiến trúc và package

Chọn cấu trúc theo tầng, với service phân nhóm nghiệp vụ. So với gom mọi việc trong PaymentService, cách này giữ transaction và trách nhiệm rõ. So với Hexagonal đầy đủ, nó tận dụng scaffold và ít lớp chuyển đổi hơn. Package theo tính năng toàn bộ chỉ cân nhắc khi các nhóm nghiệp vụ thực sự lớn lên.

```text
com.flashticket.payment_service/
  controller/               PaymentQueryController, VnpayCallbackController
    internal/               EventFinancialSummaryController
  service/
    payment/                PaymentQueryService, StartPaymentService, RecordChargeService
    saga/                   PaymentSagaService, SagaRecoveryService
    refund/                 RefundService, RefundExecutionService, EventRefundService
    finance/                EventFinanceService, EventFinancialSummaryService
  repository/               JPA repository và query chuyên biệt của Payment
  entity/                   giữ mô hình hiện tại
  dto/                      common, snapshot, message, request, response
  mapper/                   giữ MapStruct và converter hiện tại
  validation/               PurchaseSnapshotValidator, MessageInvariantValidator
  hashing/                  CanonicalContractSerializer, ContractHashService
  messaging/
    consumer/               listener mỏng cho từng hợp đồng
    inbox/                  InboxService, dedupe/hash/result
    outbox/                 OutboxService, OutboxRelay
  client/
    booking/                BookingPaymentClient
    ticket/                 TicketIssuanceClient
  provider/                 PaymentGateway và kết quả có kiểu
    vnpay/                  VnpayGateway, notification verifier, wire models
  worker/                   scheduler gọi service/relay, không chứa rule tài chính
  exception/                Problem handler và phân loại lỗi
  config/                   Security, AMQP, client, scheduler, properties
```

Chỉ tạo lớp khi thực hiện use case cần nó. Service cụ thể không bắt buộc có interface/Impl. Dùng interface tại provider boundary để thay fake bằng VNPay; tên PaymentGateway tránh nhầm enum DTO PaymentProvider hiện có. Fake chỉ ở test hoặc profile dev cô lập, ứng dụng phải từ chối bật fake trong production.

Controller/consumer/worker gọi service; service dùng repository, mapper, validator và client/provider. Controller không query DB; repository không gọi HTTP/broker; mapper không query association, tính phí/hash hoặc sinh ID. Không truyền HttpServletRequest vào nghiệp vụ; controller trích dữ liệu được phép thành giá trị có kiểu. Payment chỉ truy cập datastore của chính mình.

## 4. Repository và transaction

Repository cần theo use case, không sinh REST CRUD cho 13 bảng:

| Nhóm | Trách nhiệm |
| --- | --- |
| OrderPaymentSnapshot, PaymentAttempt | Snapshot bất biến, ownership, attempt theo key, latest attempt theo startedAt/id, unfinished attempt |
| Charge, PaymentConfirmation, PaymentSaga | Provider identity, accepted charge, khóa điều phối theo order, saga đến hạn |
| Refund, RefundAttempt | Một nghĩa vụ/charge, một attempt chưa kết thúc/refund, lịch sử và phục hồi |
| EventFinanceSnapshot, EventRefundRun/Item | Version/cancellation marker, batch keyset và tiến độ bền |
| InboxMessage, OutboxMessage | Dedupe/result, insert outbox, claim/CAS relay và retry |

Không bắt buộc tạo EventPayoutRepository ở đợt này. Query đọc dùng projection hoặc fetch có chủ đích, tránh lazy loading ngoài transaction và N+1. API không serialize entity. Native SQL được dùng cho lock/claim/upsert khi JPA không diễn đạt rõ.

Thứ tự khóa theo B14: event_finance_snapshots khi biết event → order_payment_snapshots → saga/attempt/charge → refund. Tạo event row còn thiếu bằng upsert có kiểm unique rồi khóa; không bỏ cancellation guard vì chưa nhận fee config. Query đọc thuần không cần khóa ghi.

Mỗi cập nhật nghiệp vụ và outbox/inbox liên quan commit trong một transaction PostgreSQL. Không gọi HTTP, VNPay, broker hoặc SMTP trong transaction giữ khóa hàng. Chia rõ phương thức orchestration và phương thức transaction trên các Spring bean hoặc TransactionTemplate; không dựa vào self-invocation của @Transactional.

Giữ CHECK/FK/unique/partial index hiện có làm hàng rào cuối. Hai request cạnh tranh: transaction thua unique/optimistic lock phải rollback trước khi đọc bản thắng bằng transaction mới; không tiếp tục dùng transaction PostgreSQL đã lỗi. Retry xung đột có giới hạn và đo được. Entity @Version mới để version null; wire aggregateVersion phải dương, không copy rowVersion=0 trực tiếp vào outbox.

DDL là nguồn schema, Hibernate chỉ validate. Mọi thiếu hụt schema phát hiện khi lập kế hoạch phải được nêu và thiết kế migration có mục tiêu; không âm thầm thêm cột hoặc dùng ddl-auto=update.

## 5. Contract validation và hashing

ContractCodec tiếp tục kiểm hình dạng/field lạ/kiểu. Bổ sung từ chối JSON có tên field trùng trước readTree; không để last-value-wins làm hai parser hiểu khác nhau. Money VND nguyên đồng, Java BigDecimal chính xác, không float/double; wire money/version vẫn là String theo B13.

PurchaseSnapshotValidator kiểm: lineSubtotal=quantity×unitPrice; tổng lineSubtotal=subtotal; total=subtotal−discount>0; discount không vượt subtotal; promotion null thì discount=0, nếu có thì discount khớp; ID item/seat duy nhất; quy tắc seat/quantity/mode/sector; frozenAt<expiresAt; nguồn version, owner, order/event đồng nhất. expectedTicketCount được suy từ tổng quantity và kiểm với ticketCount ở thông điệp kết quả, không thêm field vào DTO purchase.

Đề xuất canonical v1: UTF-8, không whitespace ngoài string; key object sắp từ điển; giữ field null đã khai báo; UUID lowercase chuẩn; money/version dạng chuỗi chuẩn; Instant dùng ISO_INSTANT UTC Z; không trim/đổi case/Unicode-normalize subject hay nội dung hiển thị. Items sắp theo orderItemId và seats theo seatId; không tùy tiện sắp mọi array. SHA-256 dạng lowercase hex.

- snapshotHash: canonical PurchaseSnapshot v1.
- requestHash: canonical object `{actor,orderId,provider}`, actor lấy buyerSubject; field name và golden bytes/hash phải khớp fixture Booking trước nghiệm thu tích hợp.
- inbox payloadHash: canonical toàn bộ Envelope v1 đã validate, gồm metadata và data; xử lý purchase array bằng cùng quy tắc. Producer retry giữ nguyên envelope, kể cả occurredAt/traceparent; retry transport thêm header riêng, không sửa envelope.

Golden fixtures phải có JSON đầu vào, canonical bytes và expected hash độc lập; kiểm đảo thứ tự không đổi nghĩa, thay dữ liệu nghiệp vụ, duplicate field, Unicode và timestamp tương đương. Không dùng chính hàm đang kiểm để sinh expected hash trong test.

## 6. HTTP controller, quyền và lỗi

| Endpoint | Quyền và hành vi |
| --- | --- |
| GET `/api/v1/orders/{orderId}/payment?requestId=...` | JWT hợp lệ + chủ đơn từ snapshot; trả PaymentView B13, chỉ đọc |
| GET `/internal/v1/events/{eventId}/financial-summary` | Service credential của Event, không publish qua Gateway; summary theo mục 11 |
| GET `/api/v1/payments/vnpay/ipn` | Route callback đề xuất, xác minh provider thay JWT buyer; response ACK theo API merchant được dùng |
| GET `/api/v1/payments/vnpay/return` | Route đề xuất; chỉ trả/redirect về giao diện được cấu hình để poll; không ghi nhận thu tiền |

POST `/api/v1/orders/{orderId}/payment-attempts` vẫn thuộc Booking. Không thêm public POST start/refund hoặc endpoint sửa state cho dev tại Payment. Gateway dùng route theo method/path cụ thể để không chuyển nhầm POST Booking sang Payment. Khi adapter triển khai phải đối chiếu method/encoding/callback URL với tài liệu VNPay chính thức của merchant; route callback trên chưa là xác nhận sandbox.

Security Resource Server tại Payment kiểm chữ ký, issuer, audience, expiry và role/ownership; không chỉ tin Gateway. Chưa snapshot: 404 PAYMENT_NOT_READY; khác owner: 404 chung không lộ tài nguyên. requestId phải thuộc đúng order/subject. Không requestId thì startRequest=null; có requestId hợp lệ nhưng chưa xử lý thì WAITING. Không suy CREATED từ latestAttempt của request khác. Request đã xử lý đọc result bền từ inbox.

PaymentView: acceptedChargeId lấy confirmation; latestAttempt có thứ tự xác định; paymentUrl chỉ xuất cho PENDING và URL đã lưu, UNKNOWN trả null; refunds đọc nghĩa vụ cục bộ. CREATED nghĩa attempt đã được tạo, không bảo đảm đã có URL/đã thu tiền. GET không freeze, tạo attempt, gọi provider hoặc enqueue tác vụ.

Exception handler trả Problem B13 cho HTTP ứng dụng; security entry point/access denied cũng dùng shape này. Phân biệt 400/401/403/404/409/422/503; không trả stacktrace hoặc secret. ACK provider theo protocol riêng, không bọc ApiResponse/Problem nếu provider yêu cầu shape khác.

## 7. RabbitMQ topology và cấu hình

Exchange nghiệp vụ `flash-ticket.v1`: topic, durable, routing key bằng messageType. Queue riêng theo logical consumer; consumerName bền qua restart và scale-out, không gắn instance ID. Nội dung application/json là Envelope<T> v1 được decode bằng type cụ thể; không dùng Java native serialization hoặc tin type header do producer tự chọn.

| Queue đề xuất | Binding vào flash-ticket.v1 | Logical consumer |
| --- | --- | --- |
| `payment.start-requested.v1.q` | `payment.start-requested.v1` | `payment.start-requested.v1` |
| `payment.payment-decision.v1.q` | `booking.payment-decision.v1` | `payment.payment-decision.v1` |
| `payment.order-completed.v1.q` | `booking.order-completed.v1` | `payment.order-completed.v1` |
| `payment.issuance-result.v1.q` | `ticket.issuance-result.v1` | `payment.issuance-result.v1` |
| `payment.finance-configured.v1.q` | `event.finance-configured.v1` | `payment.finance-configured.v1` |
| `payment.event-cancelled.v1.q` | `event.cancelled.v1` | `payment.event-cancelled.v1` |

Ticket result hiện chưa có routing key riêng cho FAILED: queue Payment có thể nhận cả COMPLETED; handler validate rồi ghi inbox/no-op với COMPLETED, không tự kết thúc Saga. Booking vẫn có queue riêng nhận COMPLETED. Không tự đổi contract producer để lọc ở broker.

Publisher Payment chỉ phát command B13: booking.accept-payment.v1, ticket.issue.v1, booking.issuance-result.v1 nhánh FAILED. StartPaymentResult là kiểu lưu response_payload phục vụ poll, không tự tạo routing key kết quả khi B13 chưa quy định.

### Delivery và retry

- Consumer manual ACK từng delivery sau khi transaction service đã commit. Duplicate cùng hash ACK; khác hash hoặc message sai hợp đồng chuyển parking và báo lỗi. ACK/confirm là hai cơ chế độc lập; broker confirm không chứng minh nghiệp vụ consumer hoàn tất.
- Producer persistent message, correlated publisher confirm, returns và mandatory. Unroutable, NACK hoặc confirm timeout không đặt published_at. Không coi ACK confirm là thành công nếu có basic.return tương ứng.
- Queue chính, retry và parking dùng durable quorum queue, không auto-delete/exclusive. Local một node chỉ kiểm persistence/restart, không được tuyên bố HA.
- Mỗi queue có retry riêng theo 5s → 30s → 300s và parking queue; tên thêm `.retry.5s`, `.retry.30s`, `.retry.300s`, `.parking`. Direct retry exchange `payment.retry.v1`, direct redelivery exchange `payment.redelivery.v1`, direct parking exchange `payment.parking.v1` giữ đích consumer cụ thể; không dead-letter retry về topic chung gây phát lại sang consumer khác.
- Retry queue dùng TTL tương ứng và DLX redelivery; quorum DLX bật dead-letter-strategy=at-least-once, overflow=reject-publish, target bindings sẵn. Cấu hình delivery-limit và dead-letter target cho queue chính cũng phải rõ, tránh broker tự drop khi vượt giới hạn. Chọn/pin image broker hỗ trợ các policy này khi triển khai; kiểm capability thực, không chỉ khai YAML.
- Consumer rollback trước republish vào retry/parking; chỉ ACK bản gốc sau confirm thành công và không return. Crash ở giữa có thể tạo duplicate; inbox xử lý. Header retry count/code là metadata vận hành, không sửa envelope/hash. Không vòng lặp NACK requeue tức thì vô hạn.
- DB/broker mất diện rộng: backoff/pause listener có health signal, không đẩy hàng loạt giao dịch hợp lệ thành lỗi nghiệp vụ. Poison input park ngay; lỗi tạm đã thử hết các mức park kèm cảnh báo, nghĩa vụ tiền vẫn được worker theo dõi trong DB.
- Parking không tự replay vô hạn; runbook cho phép người có quyền replay đúng queue sau sửa nguyên nhân, giữ messageId/envelope, thêm header audit. Replay một lỗi được sửa không được đổi payload dưới cùng ID.

Prefetch khởi điểm 10, concurrency 1/queue, giới hạn tối đa 4, chỉnh sau đo; không dùng concurrency=1 làm hàng rào correctness. Config broker/local bổ sung trong deploy, không reset volume có sẵn. Provision exchanges/queues/policies và service accounts bằng cấu hình có version; app runtime không có quyền broker admin. Quyền topic write/read phải hạn chế theo nguồn/đích service; messageType trong payload tự nó không xác thực producer. Nếu broker triển khai chưa enforce được nguồn publish, ghi thiếu hụt và không nghiệm thu source validation chỉ bằng unit test.

Nguồn kỹ thuật đã đối chiếu ngày 23/10/2026: [RabbitMQ confirms](https://www.rabbitmq.com/docs/confirms), [reliability](https://www.rabbitmq.com/docs/reliability), [quorum queues và at-least-once dead lettering](https://www.rabbitmq.com/docs/quorum-queues). Policy/code cụ thể phải kiểm với phiên bản broker được pin.

## 8. Inbox/outbox và công việc bền

Inbox khóa (consumerName,messageId), so payloadHash trước trả kết quả cũ. processed_at chỉ ghi khi đã có hiệu ứng hoặc quyết định no-op/result bền. response_payload của start dùng StartPaymentResult; đối chiếu order/subject qua snapshot và dữ liệu request bền. Snapshot hợp lệ vẫn được lưu khi guard từ chối để chủ đơn đọc được REJECTED. Lỗi guard timeout chưa ghi inbox terminal, command còn retry được.

Outbox payload chọn lưu riêng `data` đúng DTO; các cột metadata dựng Envelope khi relay. Metadata và payload bất biến sau insert, publish lại giữ cùng envelope. Khóa/version của aggregate lấy theo nghiệp vụ, không dùng thứ tự broker để quyết định correctness; đối với version chưa có bộ đếm aggregate riêng, dùng version nguồn dương đã lưu và không coi nó là sequence toàn cục.

Relay claim batch đến hạn bằng FOR UPDATE SKIP LOCKED trong transaction ngắn, tăng attempts và đưa next_attempt_at sang thời điểm lease 60s rồi commit. Publish ngoài transaction. Đặt published_at hoặc lịch retry bằng CAS theo messageId + attempts của lần claim + published_at IS NULL; worker cũ không ghi đè claim mới. Confirm timeout 5s, batch ban đầu 20; claim hết hạn được nhận lại. Crash sau confirm trước DB update được phép publish lặp cùng ID. Lease phục vụ tiến độ, không bảo đảm delivery exactly-once.

Scheduler restart tìm công việc từ DB: outbox unpublished, attempt PENDING/UNKNOWN chưa kết thúc, saga chờ tới hạn, refund chưa xong và event refund run/items chưa hội tụ. Không dựa vào @Async, timer hoặc queue nội bộ RAM làm bằng chứng công việc đã được giữ bền.

## 9. Start payment và provider boundary

StartPaymentService đi theo trình tự:

1. Decode/validate envelope, requestId=messageId, aggregateId=orderId, correlationId=orderId; kiểm requestHash và purchase.
2. Tra inbox cũ và so hash trước kiểm unfinished attempt. Retry request cũ trả kết quả gốc kể cả đơn đã terminal.
3. Request mới gọi Booking guard `/internal/v1/orders/{id}/freeze-payment` bằng service credential ngoài transaction; buyerSubject lấy từ snapshot. Từ chối nghiệp vụ → transaction lưu snapshot hợp lệ + REJECTED/inbox; lỗi mạng → retry cùng command, không tạo attempt.
4. Sau guard, transaction lấy khóa theo mục 4, so snapshot bất biến/source version/hash, kiểm cancellation và unfinished attempt; tạo PENDING với ID/provider identity/request key/hash bền, ghi CREATED/inbox cùng commit. Trong transaction phải kiểm lại inbox/key sau cạnh tranh: cùng order/requestKey/hash dùng kết quả attempt gốc, cùng key khác hash là conflict; chỉ key mới khi còn PENDING/UNKNOWN mới REJECTED với mã phù hợp. Không trả attempt của người khác.
5. Sau commit gọi provider bằng cùng attemptId. Worker phục hồi được bước này kể cả consumer đã ACK và inbox đã xong. URL hợp lệ được lưu có điều kiện; mất kết quả → UNKNOWN, query cùng identity. Không tạo identity mới hoặc gia hạn expiresAt từ lúc retry.

PaymentGateway cung cấp createPayment, verifyNotification, queryPayment, refund và queryRefund; kết quả phân biệt PENDING/SUCCEEDED/FAILED/UNKNOWN bằng kiểu có cấu trúc, không map mọi exception thành FAILED. URL/provider host lấy allowlist cấu hình, return target cố định; không nhận redirect tùy ý từ browser.

Worker cho cùng attempt/refund dùng session advisory lock theo operation + UUID trên connection chuyên dụng; không giữ transaction DB trong lúc gọi mạng. Lock giải phóng khi session chết; kết quả ghi bằng điều kiện state/version, luôn re-read khi conflict. Lock không thay provider identity/idempotency: khi connection mất giữa request, worker sau query trước gọi lại. Nếu provider không hỗ trợ xác định an toàn kết quả, giữ UNKNOWN/PROCESSING và cảnh báo; không thử tác động tiền bằng key mới. Giới hạn worker concurrency theo pool dành riêng cho lock, kiểm không rò session lock vào pool.

VNPay adapter là nơi duy nhất chuyển VND ×100, signing/encoding, kiểm merchant, attempt/order, amount/currency/result và provider transaction identity. IPN/query hợp lệ mới trở thành verified result; Return không là bằng chứng. Tra tài liệu merchant chính thức hiện hành khi triển khai; ghi nguồn, API được cấp, test vector và sandbox evidence. Không log raw IPN/secret; không tự thêm cột QR. Thiếu credential/callback HTTPS thì ghi sandbox NOT_RUN, không gọi fake là nghiệm thu VNPay.

## 10. Charge và Saga do Payment điều phối

RecordChargeService nhận verified result từ IPN/query qua cùng đường xử lý. Insert charge unique(provider,merchantAccount,providerChargeId), kiểm các liên kết/amount/currency; callback lặp cùng identity không tạo hiệu ứng mới. Một actual charge khác vẫn được lưu dù attempt đã có kết quả trước; không bỏ khoản thu thêm chỉ vì attempt terminal. Sai tiền/không liên kết được từ chối và log code đã lọc theo Q-07, không tự phát vé hoặc tạo workflow ngoài phạm vi.

Một saga/order chọn candidate charge một lần dưới khóa; charge thừa đi refund riêng, không thay candidate. Không mở một Saga mới cho mỗi callback hoặc cho hủy event.

| Trạng thái/đầu vào | Cập nhật và outbox cùng transaction |
| --- | --- |
| Chưa saga + candidate hợp lệ | Tạo WAITING_BOOKING, chargeId/commandId bền, outbox booking.accept-payment.v1 |
| WAITING_BOOKING + PaymentDecision ACCEPTED khớp candidate | Confirmation đúng order/charge/amount/currency; WAITING_TICKETS + ticket.issue.v1; nếu đã hủy thì sang xử lý hoàn/hủy, không phát vé mới |
| WAITING_BOOKING + LATE/CANCELLED/DUPLICATE hợp lệ | Tạo nghĩa vụ refund đúng reason, COMPENSATING cho candidate của saga |
| WAITING_TICKETS + booking.order-completed hợp lệ | Kiểm order, charge, issuance và ticketCount=tổng quantity; chuyển COMPLETED |
| WAITING_TICKETS + Ticket FAILED dứt điểm | Refund TICKET_ISSUANCE_FAILED + booking.issuance-result.v1 FAILED; COMPENSATING |
| COMPENSATING + refund SUCCEEDED và lệnh Booking cần thiết đã có outbox bền | COMPENSATED |
| Replay khớp/terminal | No-op bền, không phát lệnh hoặc tạo nghĩa vụ lần hai |

Không thêm enum terminal FAILED cho Saga ngoài DDL. COMPLETED ghi nhận kết quả mua đã xong; event cancel đến sau xử lý bằng marker/run/refund, không hồi sinh luồng phát vé hoặc tạo Saga hủy mới. Các message mâu thuẫn với quyết định terminal bị chặn và cảnh báo; message hợp lệ đến sớm do reorder được retry, không ACK bỏ trước khi dependency có mặt.

WAITING_BOOKING/WAITING_TICKETS timeout chỉ lập lịch phục hồi, không suy thất bại. Recovery ưu tiên relay các outbox chưa publish; nếu cần replay command đã publish, dùng lại bản outbox với ID/envelope cũ. Participant phải trả/replay quyết định bền và relay response khi cần; đó là contract test bắt buộc với Booking/Ticket, không giả định dedupe tự tạo response mới. Probe Ticket COMPLETED chỉ kích hoạt phục hồi Ticket → Booking → Payment; không chuyển Saga COMPLETED trực tiếp. 404/PENDING/timeout không cho quyền refund.

Schema saga chỉ giữ một commandId hiện hành: khi đổi bước, ghi commandId bước mới và outbox cùng commit; outbox bước cũ giữ để replay. OrderCompleted không có trường issuance kỳ vọng trong saga: handler kiểm payload, nguồn Booking, confirmation và expected ticketCount; dedupe hash chặn cùng ID khác issuance. Completion đã chấp nhận lưu tuple order/charge/issuance/ticketCount trong inbox.response_payload cùng transaction COMPLETED; completion về sau với messageId khác cũng phải so tuple này, không ACK một issuance mâu thuẫn như replay hợp lệ. Query lịch sử này qua repository có chủ đích, không thêm cột saga ngầm. Không bịa một issuanceId chưa từng được Payment nhận.

## 11. Refund, event cancellation và financial summary

Refund là một nghĩa vụ full amount/actual charge; unique(chargeId) và composite FK là hàng rào. Reason theo B14: charge khác confirmation → DUPLICATE_PAYMENT; charge confirmation bị hủy event → EVENT_CANCELLED; phát vé FAILED dứt điểm → TICKET_ISSUANCE_FAILED; tiền đến quá hạn/buyer hủy → LATE_PAYMENT. Trigger sau giữ reason đã lưu. Một refund không đồng nghĩa tự đổi state Booking/Ticket.

Refund worker transaction claim PROCESSING và insert RefundAttempt với requestKey bền trước network. Không quá một unfinished attempt/refund. Timeout giữ PROCESSING + unfinished attempt để queryRefund cùng key. Chỉ provider success đủ bằng chứng mới SUCCEEDED/completedAt; failure đã xác định đóng attempt + FAILED. Retry sau failure tạo attempt mới trên cùng refund, không tạo nghĩa vụ mới; lịch backoff suy từ attempt history bền, không chỉ đếm trong RAM. Lỗi provider vĩnh viễn như credential/merchant bị từ chối cần cảnh báo và xử lý nguyên nhân, không gửi refund mới tự động vô hạn; nghĩa vụ vẫn mở.

Scheduler khởi điểm poll 5s, batch 20; retry nghiệp vụ/provider dùng exponential backoff 5s, nhân 2, trần 300s với jitter tối đa 20%. Saga lưu due time ở next_attempt_at; refund suy due time từ attempt history; query UNKNOWN dùng tuổi attempt và chu kỳ cấu hình. Tuổi chờ quá 15 phút phát cảnh báo vận hành, không tự chuyển thành FAILED hoặc tự hoàn vì timeout. Các giá trị là mặc định đề xuất để kiểm, chưa là cam kết SLA.

EventFinanceService nhận version mới để bổ sung phí/owner/end, không xóa cancellation marker bởi config cũ hay config tới muộn. EventCancelled consumer commit inbox + marker + run duy nhất/cancellation trước batch. Batch keyset theo orderId, cursor/selectionComplete/selectedOrderCount lưu DB; run/item cập nhật cùng transaction. Mỗi item/run/order duy nhất; charge thừa hoàn riêng không tăng mẫu số run. completedAt chỉ sau selectionComplete và mọi item đủ điều kiện đã hội tụ; lỗi cần can thiệp có NEEDS_ATTENTION và không giả vờ hoàn tất.

Charge/acceptance đến sau marker tự chuyển nhánh hoàn dù batch đã quét qua hoặc run đã completed. Nếu xuất hiện nghĩa vụ cho confirmation mới, upsert item và mở lại completedAt khi cần; selectionComplete chỉ nghĩa đã quét hết tập tại lần đó, không nghĩa biết mọi khoản provider trong tương lai. Khóa event/snapshot và unique giúp batch với callback hội tụ.

Internal financial summary phục vụ Event là hợp đồng candidate: `{eventId,currency,asOf,hasRecordedCharges,confirmedGross,succeededRefunds,paymentPendingCount,refundPendingCount,cancellationId}`. Money/counter dùng chuỗi chuẩn, asOf UTC, cancellationId nullable. Có snapshot nhưng chưa giao dịch: tổng bằng 0; không snapshot: 404 chung. hasRecordedCharges tính từ mọi charge đã xác minh, không chỉ confirmation; confirmedGross tính confirmation; succeededRefunds ở summary chỉ tổng refund SUCCEEDED của charge confirmation. Count bao gồm trạng thái chưa rõ; nghĩa vụ refund FAILED chưa thành công vẫn được tính là còn mở. Các trường khác nhau không dùng trừ lẫn để suy số tiền chi trả.

Event phải review shape/ý nghĩa trước tích hợp FR-11. Summary là snapshot đọc cục bộ, không khẳng định provider không có khoản chưa nhận và không khóa mọi charge tương lai. Phần payout/fee rounding/paid cần spec riêng đối chiếu B6/B8/B12 trước tạo public finance controller.

## 12. Cấu hình, profile và vận hành

Giữ Java 21, Spring Boot/Cloud và dependency trong pom hiện tại; không nâng stack trong đợt này. Payment bootstrap vẫn chỉ tên ứng dụng/import Config Server bắt buộc; cấu hình riêng cấp từ payment-service.yaml; không sửa shared config cho mọi service chỉ để Payment chạy.

| Nhóm | Yêu cầu cấu hình |
| --- | --- |
| DB | PAYMENT_DB_URL/USERNAME/PASSWORD; validate, OSIV=false, SQL init never; runtime app role |
| JWT | KEYCLOAK_ISSUER_URI/JWK_SET_URI/AUDIENCE; service caller credential riêng, không hardcode |
| RabbitMQ | Host/port/username/password, virtual host riêng theo môi trường, TLS theo deployment; confirm correlated, returns, mandatory, manual ACK, topology/policy provisioning |
| Clients | Booking/Ticket base URL, connect timeout 2s, response timeout 5s ban đầu; bounded retry cùng identity, client-credentials token với scope phù hợp |
| Provider | Merchant identity, secret từ môi trường, sandbox endpoint/allowlist, callback/return URL cấu hình; tắt fake trong production |
| Workers | enabled flags, concurrency, batch, poll interval, retry/backoff/lease; properties validate >0 và tương quan timeout/lease |
| Quan sát | UTC JVM, correlation propagation, health/readiness, metric endpoint nội bộ |

Các tên biến mới như PAYMENT_RABBIT_VHOST, PAYMENT_BOOKING_BASE_URL, PAYMENT_TICKET_BASE_URL, PAYMENT_SERVICE_CLIENT_ID/SECRET và PAYMENT_VNPAY_* phải thêm vào file example chỉ có tên/giá trị không bí mật khi triển khai. Credential provisioning là phần cấu hình deployment, không in response Config Server nguyên bản.

- Default/integration runtime: DB, broker và các dependency cần cho luồng được bật; không tuyên bố ready đầy đủ khi listener/worker bị tắt.
- local-foundation: giữ mục đích kiểm DB/mapping, tắt listener, relay, scheduler nghiệp vụ và Eureka; không dùng để nghiệm thu Rabbit/Saga.
- Unit/smoke test: không cần hạ tầng; báo rõ phạm vi.
- mapping-test: PostgreSQL test riêng theo README hiện có.
- runtime integration test: PostgreSQL + RabbitMQ thật tách biệt; HTTP/provider stub chỉ thay partner, không thay transaction/broker.
- e2e/sandbox: dependency thật, provider merchant test; công bố phần thiếu/NOT_RUN.

Shutdown dừng nhận việc mới, đợi transaction đang chạy trong thời gian hữu hạn; việc còn lại redelivery/worker phục hồi. Log JSON theo B16 gồm operation/outcome/state/retry/code và ID liên quan, correlationId=orderId cho giao dịch mua. Event-wide job dùng event/run correlation riêng và nối orderId ở từng item. Metrics theo tên hữu hạn: outbox age/backlog, parking depth, unknown attempt age, saga pending age, refund obligations; không gắn orderId/messageId/email vào metric label. Không log JWT, raw callback, secret, full email hoặc SQL bind nhạy cảm.

## 13. Kiểm thử và tiêu chí nghiệm thu

| Nhóm | Kiểm bắt buộc |
| --- | --- |
| Contract/hash | Golden bytes/hash, tiền giới hạn, tổng/ghế sai, field lạ/trùng, invariant message, wire UTC |
| Query/security | 401, audience sai, owner khác, snapshot thiếu, requestId khác order/subject, GET không ghi/gọi provider |
| DB/concurrency | Mapping PostgreSQL thật; key/hash conflict; hai start cạnh tranh; charge/provider identity; confirmation FK; version conflict; rollback không có outbox |
| Broker | Persistent publish, confirm NACK/timeout/return, ACK sau commit, broker restart, consumer crash sau commit trước ACK, retry/parking và DLX target tạm mất |
| Start/provider | Crash sau attempt commit trước create; mất response; query cùng identity; guard timeout/reject; URL UNKNOWN=null; restart nhiều worker |
| Saga | Acceptance replay, response mất, completion reorder, Ticket COMPLETED không kết thúc Saga, wrong charge/count/source, Ticket FAILED terminal fence |
| Refund/cancel | Một nghĩa vụ/charge; timeout sau provider refund success; hai worker; marker tới trước config; batch restart/cursor; charge/acceptance tới sau run hoàn tất |
| Security vận hành | Broker topic ACL, internal service scope, callback signature/merchant/amount, fake bị chặn production, sentinel secret không vào log |
| E2E | Booking → Payment → Booking → Ticket → Booking → Payment; thu muộn/thừa, lỗi phát vé, cancel và refund với owner thật |

Ứng với B15, đặc biệt V07–V12, V19–V22, V25 và kiểm liên quan theo gói. Unit test dùng fake cho pure rule; không dùng H2/mock để tuyên bố PostgreSQL lock/constraint hoặc Rabbit ACK đúng. Test concurrency có barrier để tái hiện race thay vì sleep đoán; failure injection có điểm crash xác định.

Mỗi gói chạy Maven verify (format/static analysis/test giữ nguyên gate) và integration suite liên quan. README/runbook lưu cách chạy, manifest commit/schema/image/môi trường, expected/actual và PASS/FAIL/NOT_RUN. Không gọi sandbox là đã kiểm nếu chưa có credential; không đo tải trên VNPay. Chỉ nghiệm thu runtime đầy đủ khi broker/DB, phục hồi và E2E thật có bằng chứng; scaffold test xanh không thay các điều kiện này.

## 14. Các gói triển khai sau review

| Gói | Nội dung | Phụ thuộc và đầu ra review được |
| --- | --- | --- |
| P1 | Validation, canonical hash và fixture | Scaffold; rule/fixture độc lập, thống nhất hash Booking |
| P2 | Repository/query/controller/security | P1; HTTP đọc trạng thái từ fixture DB, không có start công khai |
| P3 | Rabbit deployment/config/ACL + inbox/outbox/relay | P1; DB/Rabbit integration và crash/redelivery evidence |
| P4 | StartPayment/Booking guard/provider boundary + recovery | P2/P3; command → attempt → poll với fake cô lập |
| P5 | Verified charge và Saga thành công | P4; acceptance → issue → Booking completion bằng contract stubs, có restart tests |
| P6 | Refund, terminal failure, finance/cancellation batch | P5; hoàn tiền/hủy hội tụ, mọi khoản chưa rõ được giữ bền |
| P7 | VNPay adapter, callback route và sandbox | Boundary P4 + rule P5/P6; merchant docs/test vectors/query/refund evidence |
| P8 | Summary Event, tích hợp owner thật, runbook và quan sát | P6/P7; contract summary đã review, E2E và bộ evidence B15/B16 |

P1–P8 là thứ tự và ranh giới thiết kế, chưa phải implementation plan theo task/file/lệnh. Từng gói có checkpoint nghiệm thu trước gói sau. Contract với partner được review sớm ở P1/P3/P4, không đợi P8 mới phát hiện lệch. Các chỉnh sửa Gateway/Config Server/deploy liên quan trực tiếp phải có phạm vi rõ trong kế hoạch gói; không kéo theo viết lại service khác.

## 15. Điều kiện chuyển sang lập kế hoạch

Người dùng review spec này, nhất là phạm vi payout tách riêng, topology Rabbit/ACL, hash fixture, callback route và summary candidate. Sau khi được chấp thuận, dùng writing-plans để viết kế hoạch P1 trước; người dùng review kế hoạch và chọn cách thực hiện trước khi sửa product code. Không coi việc tạo/commit spec là bằng chứng đã cấu hình RabbitMQ hoặc hoàn thành Payment runtime.
