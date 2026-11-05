# Đồng bộ contract Booking–Payment

- Phiên bản: v0.1 — **BRAINSTORMING / PENDING**. Chỉ thiết kế, chưa cho phép tạo Java/test Java, lập implementation plan, commit/push hay đổi broker/dữ liệu.
- Yêu cầu: “lập spec cho task này… Sửa phù hợp giữa 2 service”.
- USER_CONFIRMED phạm vi: “tạm thời chưa cần impl hủy sự kiên, hoàn tiền ở payment service”. Không thêm consumer hủy, refund worker/provider API hoặc cơ chế hoàn tiền mới. Giữ các nhánh bảo vệ/nghĩa vụ refund đã có.
- Nguồn code: Booking `origin/dev/son` **1d5bb553417e134ab08964bb9b6a4949c6672088**; Payment `origin/dev/tuan` **ed10c36c6c2f05ba4cb8574ecb99ccab782b5c83**. Checkout Booking hiện ở 8bfb241; diff tới ref trên chỉ đổi config Event, không đổi Booking. Không lấy Payment scaffold của dev/son làm nguồn implementation.
- Trạng thái duyệt thiết kế C1–C9, M1–M10, U1–U2 và các lựa chọn dưới đây: **PENDING**. Xác nhận phạm vi trên không phải duyệt khai báo Java.
- Khảo sát bổ sung theo yêu cầu user: Ticket `origin/dev/tuyen` **4c328fd8c78e503cf9cdbd1d0a464a24c2413c71**, implementation `contract/TicketContracts`, `application/TicketService`, `worker/TicketOutboxPublisher`; không dùng Ticket scaffold ở dev/son để kết luận tương thích.

## 1. Mục tiêu và giới hạn

Payment lấy snapshot Booking, nhận tiền, yêu cầu Booking accept, phát lệnh Ticket, chuyển kết quả Ticket về Booking và chỉ hoàn tất Saga sau khi Booking xác nhận COMPLETED đúng checkpoint. Payload, routing key, version và recovery phải thống nhất cả hai chiều.

Sửa chủ yếu ở Payment và definitions dùng chung. Booking giữ API/DTO, listener, validation snapshot, transaction và relay hiện tại; không hạ guard Booking để chấp nhận message sai. Không thêm DTO dùng chung giữa module, dependency chéo service, bảng/cột, migration framework, endpoint hoặc queue nghiệp vụ mới.

Không nghiệm thu hủy event/hoàn tiền toàn hệ thống trong task này. Payment hiện chưa có consumer cập nhật cancellation marker; `ensureRefund` hiện tạo nghĩa vụ PENDING, không chứng minh đã hoàn tiền qua VNPay. Giữ nguyên giới hạn đó và không đưa timeout thành FAILED.

## 2. Bằng chứng và các lệch cần xử lý

| Điểm | FACT trong code | Thiết kế đề xuất PENDING |
| --- | --- | --- |
| Kết quả phát vé | `PaymentSagaServiceImpl.recordIssuanceResult` gửi `booking.issuance-result.v1` cho cả hai state | Chọn hai key hiện hành ở §3, cùng DTO BookingIssuanceResult |
| Version kết quả | Payment dùng `saga.rowVersion + 2`; Booking `validateIssuanceResult` so với frozen.sourceOrderVersion | Lấy `tbl_order_payment_snapshots.source_order_version` đã lưu theo order, không lấy rowVersion Saga |
| Tìm checkpoint/recovery | Ba method Payment còn truy vấn type cũ | Lookup cả hai type kết quả mới; legacy chỉ để nhận diện dữ liệu cũ theo §7 |
| Hoàn tất Saga | `recordOrderCompleted` cho phép checkpoint Ticket vắng mặt, comment ghi flow Ticket→Booking trực tiếp | Bắt buộc có checkpoint COMPLETED đã được Payment lưu/chuyển tiếp, đối chiếu trước khi đóng Saga |
| Topology Ticket→Payment | Definitions bind `ticket.issuance-failed.v1`; consumer yêu cầu `ticket.issuance-result.v1` | Definitions bind key contract mới, không tạo queue thứ hai |
| Queue declaration | Bean Payment tạo queue không chỉ định quorum; Config Server `spring.rabbitmq.dynamic=false` | Giữ dynamic=false ở runtime; definitions quản lý topology. Đồng bộ bean bằng QueueBuilder.quorum để test/explicit admin không tạo topology khác |
| Snapshot HTTP | Hai phía POST cùng path/body; JSON tương thích | Giữ route/DTO; sửa phân loại lỗi response không hợp lệ thành lỗi dependency, kiểm bằng payload Booking thật |

Đính chính review trước: bean `Declarables` không tự chứng minh broker sẽ được bổ sung binding khi dynamic=false. Nguy cơ classic/quorum chỉ có hiệu lực nếu có RabbitAdmin khai báo bean; không khẳng định đây là nguyên nhân runtime119 hiện tại.

## 3. Contract đích

Exchange dùng chung `business.ex` qua cấu hình hiện có. Queue `.q` không có hậu tố `.v1`; `.v1` nằm trong messageType/routing key.

| Hướng | messageType = routing key | Queue nhận | Payload |
| --- | --- | --- | --- |
| Payment→Booking | booking.accept-payment.v1 | booking.accept-payment.q | AcceptPayment |
| Booking→Payment | booking.payment-decision.v1 | payment.decision.q | PaymentDecision |
| Payment→Ticket (giữ contract hiện có) | ticket.issue.v1 | ticket.issuance.q | IssueTickets |
| Ticket→Payment | ticket.issuance-result.v1 | payment.ticket-issuance-failed.q | IssuanceResult |
| Payment→Booking, COMPLETED | ticket.issued.v1 | booking.tickets-issued.q | BookingIssuanceResult |
| Payment→Booking, FAILED | booking.issuance-failed.v1 | booking.issuance-failed.q | BookingIssuanceResult |
| Booking→Payment | booking.order-completed.v1 | payment.order-completed.q | OrderCompleted |

- `BookingIssuanceResult`: orderId, chargeId, issuanceId, state, ticketCount, failureCode. COMPLETED: count>0, failureCode=null; FAILED: count=0, failureCode không rỗng. Không thêm field.
- AcceptPayment và hai kết quả Payment→Booking: aggregateId=orderId, correlationId=orderId, aggregateVersion=snapshot.sourceOrderVersion dạng chuỗi. Booking response giữ version snapshot. Fix AcceptPayment ở ed10c36 được tái sử dụng.
- schemaVersion=1; UUID đúng định dạng; tiền và version là string; thời gian UTC ISO-8601; các field nullable vẫn hiện diện với null. Không đưa tên class Java vào header hoặc payload.
- Không dùng quy tắc version snapshot cho mọi loại event. Trong task này không đổi semantics lệnh ticket.issue.v1; recovery của lệnh đó giữ aggregateVersion của command đã lưu, không tự tăng chỉ vì retry.
- Transport retry giữ messageId/envelope/body. Business recovery tạo messageId/occurredAt mới, causationId trỏ command trước, giữ payload nghiệp vụ và version snapshot khi gửi Booking. Không thay occurrence của sự kiện Ticket bằng kết quả suy đoán.

## 4. Luồng chính và transaction

### 4.1 Lấy snapshot và chấp nhận thanh toán

1. Payment dùng machine token gọi POST `/internal/v1/orders/{orderId}/purchase-snapshot`, body `{buyerSubject}` lấy từ user đã xác thực, không relay user token làm credential nội bộ.
2. Booking giữ khóa/guard hiện có, freeze lần đầu hoặc trả lại snapshot cũ trên order còn payable; không gia hạn giữ chỗ, không tăng snapshot version vì retry.
3. Response 200 là PurchaseSnapshot trực tiếp, không bọc thêm data. Payment mapping DTO bằng ContractCodec hiện có, validator/hash hiện có kiểm tiền/seat/time/buyer/order trước lưu snapshot + attempt cùng transaction Payment. Không thêm parser hoặc validation helper.
4. Nhận callback tiền thành công: charge + Saga WAITING_BOOKING + outbox AcceptPayment commit cùng nhau. Booking accept chuyển ISSUING và gửi PaymentDecision; Payment chỉ phát Ticket sau ACCEPTED và không có cancellation marker theo logic đã có.

### 4.2 Nhận và chuyển tiếp kết quả Ticket

1. Listener Payment đọc Envelope<IssuanceResult>, validate messageType/shape/state, gọi service transaction như hiện tại.
2. Giữ thứ tự khóa finance marker→payment snapshot→Saga; xác thực charge và count theo snapshot.
3. Lookup checkpoint kết quả cho order bằng M1 trên cả hai type mới (và legacy để phát hiện dữ liệu cần xử lý). Kết quả đã lưu cùng issuanceId/charge/state/count/failureCode: replay không tạo side effect mới; bất kỳ khác biệt nghiệp vụ nào là conflict, kể cả đổi COMPLETED↔FAILED. Lookup này trước early-return theo state để không im lặng ACK kết quả trái nhau sau COMPLETED/COMPENSATING. Replay inbox cùng messageId/hash vẫn theo cơ chế cũ.
4. COMPLETED hợp lệ: enqueue `ticket.issued.v1`, version snapshot, giữ WAITING_TICKETS. FAILED: giữ nhánh ensureRefund/COMPENSATING đã có, enqueue `booking.issuance-failed.v1` cùng transaction; không thêm thực thi hoàn tiền.
5. Inbox, state và outbox commit trước MANUAL ACK. Commit/DB lỗi phải rollback và đi ra listener; lỗi contract/conflict vào đường reject/DLQ hiện có. Không nuốt lỗi để ACK.
6. Booking nhận hai queue bằng AUTO ACK sau commit. COMPLETED: kiểm snapshot/charge/count, order COMPLETED + inbox + outbox OrderCompleted cùng transaction. FAILED: giữ release tài nguyên và chống lặp hiện có; không chờ refund mới release.

### 4.3 Xác nhận hoàn tất Saga

- `recordOrderCompleted` lookup checkpoint COMPLETED bằng M1, decode DTO hiện có, so orderId/chargeId/issuanceId/ticketCount, state=COMPLETED và version snapshot. Không đòi `publishedAt` đã commit: response Booking có thể đến trước worker Payment kịp ghi publishedAt.
- Không có checkpoint: ném `ContractValidationException("issuanceId", "ORDER_COMPLETION_MISMATCH")` hiện có, không hoàn tất Saga theo nhánh Ticket gửi thẳng Booking. Outbox checkpoint được tạo trước khi publish nên không có cuộc đua hợp lệ bắt buộc bỏ guard này.
- Chỉ WAITING_TICKETS→COMPLETED; replay COMPLETED khớp checkpoint không lặp hiệu ứng. Giữ checkpoint inbox order-level hiện có để phát hiện completion mâu thuẫn.
- Giữ guard COMPENSATING/COMPENSATED và cancellation marker hiện có; completion đến muộn không hồi sinh Saga hoặc hoàn tác nghĩa vụ refund. Không triển khai fan-out hủy trong task.

### 4.4 Recovery

- WAITING_BOOKING: retry AcceptPayment như fix hiện có, version từ stored snapshot.
- WAITING_TICKETS chưa có result checkpoint: retry ticket.issue.v1 từ payload cũ; giữ version của command cũ.
- WAITING_TICKETS có checkpoint COMPLETED: resend kết quả tới Booking bằng ticket.issued.v1 và version snapshot; không phát lại ticket.issue.v1.
- FAILED đang COMPENSATING tiếp tục dùng outbox transport retry hiện có; không thêm recovery/refund worker.
- Chưa publish hoặc đang retry transport: không nhân thêm command. Broker ACK không thay business acceptance/completion. Crash sau publish trước markPublished vẫn at-least-once, hai phía phải dedupe như hiện tại.

## 5. Snapshot HTTP và cấu hình

- Giữ DTO record mỗi service và nested fields hiện có; không import DTO Booking vào ứng dụng Payment.
- `BookingPurchaseClient.preparePurchaseSnapshot`: giữ POST/body/OAuth; response rỗng/sai JSON/sai DTO bắt ContractValidationException ở boundary client, chuyển BOOKING_UNAVAILABLE (503), thay vì để lỗi dependency thành lỗi nội bộ/đầu vào buyer. Validation liên field vẫn tại purchaseHash như code hiện có.
- Giữ mapping 404→RESOURCE_NOT_FOUND, 409→PAYMENT_STATE_INVALID, 401/403 của machine credential và timeout/5xx→BOOKING_UNAVAILABLE. Phân biệt ORDER_EXPIRED trong body409 là cải tiến UX riêng, không cần DTO lỗi/parser mới để thông luồng này.
- Application Payment mặc định client ID internal-service cho khớp Compose và realm repo. `PAYMENT_SERVICE_CLIENT_SECRET`, `PAYMENT_SERVICE_TOKEN_URI`, Booking URL cấu hình qua .env/.env.example; không copy secret vào spec hoặc tự suy giá trị còn thiếu. Config hiện có đã dùng issuer; task không thêm audience/JWKS override hoặc hạ quyền INTERNAL_SERVICE.
- `RabbitMqConfig.paymentSagaTopology`: giữ @Bean/chữ ký/field @Value, dùng QueueBuilder.durable(name).quorum().build() cho ba queue hiện có, giữ binding mới và dynamic=false runtime. Policies/DLQ tiếp tục ở definitions, không thêm hardcoded DLX args vào queue Java.
- Definitions đổi binding Ticket→Payment sang ticket.issuance-result.v1. Không bind lại key cũ như phương án dự phòng; không để Ticket success đi thẳng Booking. Trước nghiệm thu phải xác nhận publisher Ticket thực tế dùng key mới; task không tự sửa implementation Ticket ngoài phạm vi.

## 6. Bảng duyệt class và cách viết — PENDING

Java Payment nằm dưới `services/payment-service/src/{main,test}/java/com/flashticket/payment_service/`. Java Booking giữ nguyên trong task.

| ID | Class/file | Loại | Vai trò/API sinh và mẫu áp dụng |
| --- | --- | --- | --- |
| C1 | main/service/impl/PaymentSagaServiceImpl.java | EXISTING sửa thân method | Sửa result/checkpoint/recovery; giữ @Service, @RequiredArgsConstructor, dependency và constructor. Theo Shop App OrderService.updateOrder: repository→guard→setter→save; không helper mới |
| C2 | main/repository/OutboxMessageRepository.java | EXISTING thêm M1 | Spring Data query theo tên để tìm checkpoint trên hai outcome; giữ method single-type đang có vì các caller khác còn dùng |
| C3 | main/config/RabbitMqConfig.java | EXISTING sửa thân M6 | QueueBuilder từ Spring AMQP đã có; không thêm dependency/type/method, không đổi bean signature |
| C4 | main/client/BookingPurchaseClient.java | EXISTING sửa thân M7 | Giữ constructor injection, @Value field và ContractCodec; chỉ map lỗi decode response, không thêm DTO/helper |
| C5 | test/service/PaymentFlowIntegrationTest.java | EXISTING + U1/U2 | Giữ @SpringBootTest, @ActiveProfiles và hạ tầng DB/Rabbit hiện có; hai test mới vì checkpoint thiếu và conflict hai outcome là hành vi độc lập |
| C6 | test/config/PaymentConfigurationTest.java | EXISTING | Mở rộng test cấu hình hiện có để so binding/key/type queue, không chỉ kiểm queue name |
| C7 | test/client/BookingPurchaseClientTest.java | EXISTING | Cập nhật expected error của response JSON sai; giữ test credential/HTTP mapping |
| C8 | DTO/message/snapshot, mapper, validator, PaymentMessageListener, PaymentWorker; Booking API/listener/OrderService/relay | REUSE | Giữ API sinh, annotation và constructor; không đổi style record hoặc ACK mode. Caller hiện có gọi service sau sửa |
| C9 | deploy/rabbitmq/definitions.json, Payment application.yaml/.env.example; B13/B14 | EXISTING non-Java | Đồng bộ binding/client default, ghi version convention/flow thực tế; không sửa luật hủy/refund đã được chốt |

**Không tạo class/interface/record/enum/constructor mới; không đổi Lombok API.** Ngoại lệ khác Shop App là cơ chế đã có: Envelope record, transaction/khóa, inbox/outbox, codec nghiêm ngặt và manual ACK Payment; không mở rộng framework đó.

## 7. Bảng method — PENDING

| ID | Class | Chữ ký đầy đủ / annotation | NEW / EXISTING, nơi gọi và lý do |
| --- | --- | --- | --- |
| M1 | OutboxMessageRepository | `public Optional<OutboxMessage> findFirstByAggregateIdAndMessageTypeInOrderByOccurredAtDescMessageIdDesc(UUID aggregateId, List<String> messageTypes)`; không annotation mới | NEW, Spring Data implement; C1 recordIssuanceResult/recordOrderCompleted/recoverSaga cùng dùng. Method single-type không tìm được conflict khác outcome. Khóa Saga của caller bảo vệ quyết định, không thêm khóa repo này |
| M2 | PaymentSagaServiceImpl | `public void recordIssuanceResult(Envelope<IssuanceResult> incomingEnvelope)`; giữ @Override/@Transactional | EXISTING, listener gọi; chọn key/version, so checkpoint hai outcome trước early-return |
| M3 | PaymentSagaServiceImpl | `public void recordOrderCompleted(Envelope<OrderCompleted> incomingEnvelope)`; giữ @Override/@Transactional | EXISTING, listener gọi; bắt buộc checkpoint đã lưu, bỏ bypass flow trực tiếp |
| M4 | PaymentSagaServiceImpl | `public void recoverSaga(UUID orderId)`; giữ @Override/@Transactional | EXISTING, worker gọi; retry đúng command theo checkpoint, không tăng snapshot version |
| M5 | PaymentSagaServiceImpl | `public void recordSuccessfulCharge(Charge charge)`; giữ @Override/@Transactional(MANDATORY) | REUSE UNCHANGED fix ed10c36; webhook caller giữ transaction/lock cũ |
| M6 | RabbitMqConfig | `public Declarables paymentSagaTopology()`; giữ @Bean | EXISTING; khai báo queue quorum khớp baseline ở môi trường có admin |
| M7 | BookingPurchaseClient | `public PurchaseSnapshot preparePurchaseSnapshot(UUID orderId, String buyerSubject)` | EXISTING, PaymentService.createPayment gọi; decode response lỗi thành lỗi dependency |
| M8 | PaymentConfigurationTest | `void bindsDatabaseWithoutAllowingSchemaMutation() throws Exception`; giữ @Test | EXISTING; assert cả key và queue type trong Java/definitions, dynamic=false runtime |
| M9 | BookingPurchaseClientTest | `void rejectsInvalidSnapshotResponses()`; giữ @Test | EXISTING; expected PaymentException BOOKING_UNAVAILABLE thay ContractValidationException |
| M10 | PaymentFlowIntegrationTest | `void forwardsTicketSuccessAndWaitsForBookingCompletion()`, `void recoversSagaAfterCommitAndDeliveryFailures() throws Exception`, `void keepsExpiryCancellationAndRefundGuards()`; giữ @Test | EXISTING; đổi fixture/key/query, bỏ case success không checkpoint, assert version/result recovery. Không bổ sung hành vi refund |
| U1 | PaymentFlowIntegrationTest | `void rejectsOrderCompletionWithoutForwardedTicketResult()`; @Test, package-private, không throws | NEW; chứng minh OrderCompleted không thể đóng Saga khi chưa có Ticket checkpoint và không ghi completion inbox sai |
| U2 | PaymentFlowIntegrationTest | `void rejectsConflictingTicketResultsAcrossOutcomes()`; @Test, package-private, không throws | NEW; cùng order/charge nhưng đổi outcome/issuance/count/failureCode phải conflict, không tạo outbox/nghĩa vụ lặp; chạy cả thứ tự success→failed và failed→success |

Các test còn lại chỉ sửa string/fixture/expected assertion liên quan, giữ chữ ký hiện có. Nếu thực thi cần test/helper/class khác ngoài bảng, phải trình delta trước; danh sách này không cấp phép mở cho “helper cần thiết”.

## 8. Dữ liệu cũ và triển khai

- M1 có thể nhận danh sách ba type gồm legacy `booking.issuance-result.v1` để **phát hiện** checkpoint cũ và giữ kiểm conflict; Booking không được nhận type legacy trên wire mới.
- Với checkpoint legacy đã published và Saga vẫn WAITING_TICKETS: recovery tạo message mới key theo state, version snapshot và payload cũ, causation trỏ message trước; không đổi body/messageId cũ và không phát vé lại.
- Outbox legacy chưa published hoặc message lỗi đã vào DLQ: trước deploy phải dừng/kiểm soát publisher, thống kê trạng thái và trình danh sách cụ thể để xử lý. Không có SQL/purge/replay/migration dữ liệu nào được cho phép bởi spec. Không bật publisher mới khi còn legacy pending chưa có phương án được duyệt; nếu không có dữ liệu loại này thì không thêm cơ chế migration.
- Binding mới trong definitions không chứng minh broker119 đã đổi; thay file import có thể không xóa binding cũ. Khi triển khai phải kiểm binding đang tồn tại, quyền publish/read, queue type/policy/DLQ và consumer thực tế. Không xóa queue để đổi type.
- Không merge toàn bộ dev/son↔dev/tuan. Pha implementation phải xác định ref mới nhất, áp đúng các file liên quan và tránh ghi đè worktree/đổi code Event/User. Chưa có quyền commit/push/deploy cho spec này.

## 9. Tiêu chí nghiệm thu

| Case | Kết quả bắt buộc |
| --- | --- |
| Snapshot QUANTITY/SEAT_MAP đứng/ngồi × có/không voucher | JSON do Booking thật serialize đọc/validate được bằng Payment codec, đầy đủ nullable fields; tiền/version không ép số |
| HTTP snapshot | POST/body/machine credential đúng; sai buyer/order không tạo attempt; JSON sai/rỗng→503; 404/409/credential/timeout theo §5 |
| AcceptPayment mới/recovery | aggregateVersion=data.sourceOrderVersion=stored snapshot version; Booking accept được, retry không tăng version |
| COMPLETED | Payment lưu result→ticket.issued.v1/version snapshot→Booking COMPLETED→OrderCompleted đúng checkpoint→Saga COMPLETED |
| FAILED dứt điểm | booking.issuance-failed.v1/count0/code→Booking ISSUANCE_FAILED và release đúng một lần; logic refund có sẵn không bị thay đổi |
| Replay/đổi outcome | Cùng messageId/hash hoặc business replay không lặp side effect; payload conflict khác outcome/issuance/count/code không bị ACK thành công bởi early-return |
| Chưa có Ticket checkpoint | OrderCompleted bị từ chối, Saga chưa COMPLETED; loại case fixture bỏ qua Ticket→Payment |
| Recovery result | Mất xác nhận Booking: resend result đúng key/version; không có ticket.issue mới. Bao gồm nhận diện checkpoint legacy đã publish |
| Transaction/ACK | Commit lỗi rollback inbox/state/outbox; Payment không ACK trước commit, Booking AUTO ACK sau commit; sai contract đi đúng DLQ |
| Topology | Binding đúng key/type ở cả definitions/bean Java; test không chỉ so queue name hoặc chỉ thấy publisher confirm mà kết luận consumer xử lý xong |

Tái sử dụng suite Booking và Payment; chạy Payment default verify cùng integration profile thực sự (suite default hiện loại PaymentFlowIntegrationTest). Test liên service phải dùng JSON do producer thật tạo và consumer thật đọc; không chỉ hai bên tự dùng fixture riêng. Có thể mở rộng runner `run-payment-scenarios.mjs` hiện có để điều phối hai runtime, không thêm dependency/module DTO chéo.

Bằng chứng trước spec: 6/6 phép thử snapshot serialize Booking→Payment đã pass, lưu tại `services/booking-service/target/check-snapshot-compat.log`; đó là kiểm DTO bằng class đã compile, không phải HTTP/Saga E2E hoặc bằng chứng fix ở spec đã triển khai. Trước commit thực thi phải chạy lại checks trên ref cuối; live119 chỉ thực hiện khi được giao, không gửi message làm thay đổi dữ liệu từ lượt viết spec.

Khảo sát Ticket bổ sung: 8/8 phép kiểm wire pass, lưu `services/booking-service/target/ticket-payment-check/results.log`. Sáu payload do Payment DTO/codec serialize (QUANTITY/STANDING/SEATED × voucher) đi qua `TicketContracts.decode/validateIssue` nguyên bản dev/tuyen; hai envelope result dựng theo code publisher Ticket (COMPLETED/FAILED) đi qua Payment codec + MessageInvariantValidator. Phần result không gọi transaction phát vé/publisher/DB thật. Ticket không yêu cầu aggregateVersion lệnh bằng sourceOrderVersion; case 3/1 pass. `TicketService.appendResult` tự lấy version snapshot và giữ chargeId/issuanceId/count/state, publisher ghi application/json/persistent, không sinh Java type header.

Definitions của dev/tuyen và dev/tuan đều còn binding cũ ticket.issuance-failed.v1; producer và consumer code hai phía đã dùng ticket.issuance-result.v1. Đây là lệch topology cần sửa, không phải lệch DTO. Runtime Ticket còn phụ thuộc TICKET_BUSINESS_ENABLED/TICKET_LISTENERS_ENABLED/TICKET_WORKERS_ENABLED (Compose mặc định false), cấu hình access event, exchange và routing key thực tế; chưa xác nhận deployment bật đúng hay binding119 hiện tại từ kiểm wire này. Phát hiện này bổ sung bằng chứng, không thay phạm vi hoặc tự duyệt Java.

## 10. Đối chiếu convention và nguồn

- Đã đọc common.md và project rule theo Git common dir `D:/DATN/flash-ticket-platform/.git`. Áp cụ thể: sửa method đúng trách nhiệm; M1 phục vụ ba caller thực tế, không helper query wrapper; giữ constructor injection/@Value; không thay codec/validator hoặc thêm kiểu DTO chung; không thêm flush/native SQL; không đổi logging Booking; run/test sau này chỉ qua env-file.
- Đã đọc source mẫu Shop App `src/main/java/com/project/shopapp/services/impl/OrderService.java` (updateOrder) và `components/JwtTokenUtil.java`: repository/guard/setter/save trực tiếp, constructor injection và @Value field. Không sao chép các đoạn trả null/bắt lỗi không phù hợp contract hiện tại.
- [B13](../../architecture/B13-api-and-event-contracts.md) §PurchaseSnapshot, Envelope, message và §5.2; [B14](../../architecture/B14-main-flow-sequences.md) §3; [spec Booking nhận kết quả](2026-10-29-booking-issuance-result-design.md) v0.4; [spec AcceptPayment](2026-10-29-booking-accept-payment-design.md).
- Payment source tại ref ed10c36: `PaymentSagaServiceImpl`, `OutboxMessageRepository`, `BookingPurchaseClient`, `PaymentRuntimeConfig`, `RabbitMqConfig`, `PaymentMessageListener`, `PaymentWorker`; Booking source tại ref trên: `InternalOrderController`, `OrderServiceImpl.validateIssuanceResult`, `BookingIssuanceResultListener`, `GlobalExceptionHandler`, `PurchaseSnapshot`.
- Framework tham khảo: [RabbitMQ queue equivalence/quorum](https://www.rabbitmq.com/docs/quorum-queues), [Spring AMQP QueueBuilder.quorum](https://docs.spring.io/spring-amqp/api/org/springframework/amqp/core/QueueBuilder.html), [Spring AMQP RabbitAdmin source](https://github.com/spring-projects/spring-amqp/blob/main/spring-rabbit/src/main/java/org/springframework/amqp/rabbit/core/RabbitAdmin.java). Chỉ dùng builder/admin hiện có; không thêm framework topology hoặc tự nới queue equivalence check để che lệch cấu hình.

## 11. Xác nhận và chuyển pha

- Phạm vi không triển khai hủy/refund: USER_CONFIRMED bằng câu user ghi đầu tài liệu.
- Thiết kế, lựa chọn checkpoint bắt buộc, legacy handling, C1–C9/M1–M10/U1–U2: PENDING, chưa có xác nhận.
- Sau user duyệt mới cập nhật trạng thái thiết kế. Chỉ chuyển writing-plans khi user yêu cầu; pha plan và thực thi giữ gate riêng theo skill. Không coi duyệt spec là quyền sửa broker/dữ liệu/Git.
