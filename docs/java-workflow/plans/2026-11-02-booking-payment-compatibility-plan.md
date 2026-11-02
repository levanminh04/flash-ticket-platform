# Plan đồng bộ Booking–Payment, đối chiếu Ticket

## Nhật ký thực thi 02/11/2026

- EXECUTING-PLANS: **APPROVED**, nguồn user trả lời “xác nhận” sau bảng duyệt thực thi M1/U1/U2 và toàn bộ sửa đổi theo plan C1–C10/M1–M11. Không thêm class/helper/constructor; không triển khai hủy event/refund mới. Không ghi duyệt hồi tố hai pha trước.
- T1–T4: **đã triển khai và kiểm chứng** trong worktree Payment; T5 đã cập nhật docs/bàn giao evidence, **E2E ba runtime còn OPEN**. Quyền sửa/test không bao gồm push hoặc đổi broker119.
- Production sửa đúng M1–M7; M5 giữ nguyên fix trước. Chỉ thêm Java method M1/U1/U2, không class/helper/constructor mới. Sửa thêm thân test cũ `issuesTicketsOnlyAfterBookingAcceptance()` để kiểm version recovery lệnh Ticket ở ngay fixture phù hợp; không đổi chữ ký/annotation. Test YAML đổi ba queue local sang hậu tố `.quorum` để tránh classic queue còn từ lần chạy cũ, không xóa queue.
- Đã đối chiếu lại convention: repository query IN dùng chung ba caller, service giữ khóa/inbox/outbox và setter/save hiện có; client giữ dependency constructor/@Value field; không thêm schema/dependency, không sửa Java Booking/Ticket hay runtime119.
- Test-first: U1/U2 trên code cũ **2 failures/0 errors** đúng hành vi thiếu checkpoint và kết quả mâu thuẫn; sau sửa **2/2 pass**. Log: `services/booking-service/target/payment-regression-red.log` và `payment-regression-green.log` ở workspace Booking.
- Integration lần đầu sau đổi queue bean: 10/11 pass, 1 lỗi infrastructure PRECONDITION_FAILED do queue test cũ classic. Sau dùng tên queue quorum riêng: **11/11 pass**, failures/errors/skips=0. Log: `services/booking-service/target/payment-flow-green.log`.
- Default `verify`: **123/123 pass**, failures/errors/skips=0; Spotless pass, SpotBugs **0**. Log: `services/booking-service/target/payment-verify.log`. Test trọng điểm config/client/codec pass ở `payment-focused.log`. `git diff --check` pass, definitions JSON parse hợp lệ.
- Đã lưu tám fixture trong Payment test resources và dùng codec/validator/invariant thật kiểm. Sáu snapshot từ Booking serializer; hai result envelope dựng theo Ticket publisher, không phải output transaction Ticket thật. Provenance/giới hạn ghi trong README.
- E2E chưa chạy: `.env` local thiếu PAYMENT_SERVICE_CLIENT_SECRET/PAYMENT_SERVICE_TOKEN_URI, Ticket QR key ring/active key và runtime test ba service. Các biến shared INTERNAL_SERVICE_CLIENT_SECRET/KEYCLOAK_TOKEN_URI có sẵn không được tự dùng để kết nối runtime thật thay test; chưa provision realm/credential/QR test riêng. Không tự điền giá trị chưa xác minh hoặc gửi message119. Cần cấu hình môi trường test trước để thực hiện T5, không đánh dấu toàn task E2E hoàn tất.
- Chưa stage/commit/push/deploy; source/config/docs Payment đang ở `D:/DATN/payment-snapshot-version`; design/plan giữ trong workspace Booking. Các PENDING ở phần kế hoạch bên dưới là lịch sử pha soạn plan, không ghi duyệt hồi tố.
- Kiểm bổ sung theo user yêu cầu “user test tầm 5 test case”: **5/5 pass** trên Payment JAR/HTTP/PostgreSQL/RabbitMQ local thật; Booking/OAuth/JWKS/Ticket và callback VNPay là mô phỏng. Các case: success qua đầy đủ checkpoint đến Saga COMPLETED; POST đồng thời/retry cùng key chỉ một attempt; sai chủ đơn404; hết hạn409; Booking503/JSON sai đều503 và không ghi attempt. Báo cáo [5 user-flow tests](../reports/2026-11-02-payment-user-tests.md), JSON cạnh báo cáo lưu evidence. Runtime test dùng env-file; block scenario một lần được gỡ sau chạy, không thay secret production. Đây bổ sung kiểm Payment runtime cho T5, **không đóng phần E2E ba service thật**.

- Phiên bản v0.1 — **WRITING-PLANS / PENDING**. Đây là plan nháp theo yêu cầu gọi skill của user, chưa triển khai Java, chưa commit/push/deploy hay sửa broker/dữ liệu.
- Design: [2026-11-02-booking-payment-compatibility-design.md](../specs/2026-11-02-booking-payment-compatibility-design.md), v0.1. Design đang PENDING; không coi việc yêu cầu lập plan là duyệt hồi tố danh sách BRAINSTORMING.
- USER_CONFIRMED: “tạm thời chưa cần impl hủy sự kiên, hoàn tiền ở payment service”. Giữ các guard/nhánh ensureRefund đã có; không thêm consumer hủy, refund worker hoặc gọi API hoàn tiền.
- Ref đã khảo sát: Booking `dev/son@1d5bb553417e134ab08964bb9b6a4949c6672088`; Payment `dev/tuan@ed10c36c6c2f05ba4cb8574ecb99ccab782b5c83`; Ticket `dev/tuyen@4c328fd8c78e503cf9cdbd1d0a464a24c2413c71`.
- Nguồn implementation Payment: worktree `D:/DATN/payment-snapshot-version`, không dùng scaffold Payment trong dev/son. Khi thực thi phải kiểm lại ref và diff mới, không merge cả nhánh hoặc ghi đè thay đổi ngoài scope.
- Gate pha này: C1–C10, M1–M11, U1–U2 **PENDING**. Các task dưới chỉ được thực thi sau khi có duyệt đúng pha EXECUTING-PLANS và user giao triển khai. Không có task nào đang được đánh dấu hoàn tất.

## 1. Kết quả cần đạt

Flow: Payment POST snapshot Booking → nhận tiền → Booking ACCEPTED → Payment yêu cầu Ticket → Ticket trả kết quả về Payment → Payment chuyển đúng kết quả cho Booking → Booking commit COMPLETED và gửi OrderCompleted → Payment hoàn tất Saga đúng checkpoint.

| Chiều | messageType / routing key | Queue |
| --- | --- | --- |
| Payment→Booking | booking.accept-payment.v1 | booking.accept-payment.q |
| Booking→Payment | booking.payment-decision.v1 | payment.decision.q |
| Payment→Ticket | ticket.issue.v1 | ticket.issuance.q |
| Ticket→Payment | ticket.issuance-result.v1 | payment.ticket-issuance-failed.q |
| Payment→Booking COMPLETED | ticket.issued.v1 | booking.tickets-issued.q |
| Payment→Booking FAILED | booking.issuance-failed.v1 | booking.issuance-failed.q |
| Booking→Payment | booking.order-completed.v1 | payment.order-completed.q |

Exchange là business.ex theo cấu hình. AcceptPayment và kết quả gửi Booking dùng version snapshot Payment đã lưu; không tăng version đó vì retry. Ticket hiện chấp nhận version lệnh khác snapshot; task không đổi semantics lệnh phát vé lần đầu. Khi recovery lệnh Ticket, giữ version command cũ.

## 2. Danh sách class/file — PENDING

Đường dẫn Java Payment bên dưới bắt đầu tại `services/payment-service/src/`; package gốc `com/flashticket/payment_service/`. Các ID C1–C9 giữ từ design; C10 chỉ bổ sung vị trí test cũ sẽ sửa, không thêm type/API.

| ID | File/class | Loại và annotation/API sinh | Mẫu hoặc ngoại lệ |
| --- | --- | --- | --- |
| C1 | main/java/.../service/impl/PaymentSagaServiceImpl.java | EXISTING; giữ @Service, @RequiredArgsConstructor, toàn bộ final dependencies/constructor và annotation transaction; sửa M2–M4 | Shop App OrderService.updateOrder: repository→guard→setter→save. Khóa/inbox/outbox là cơ chế hiện có, không helper/framework mới |
| C2 | main/java/.../repository/OutboxMessageRepository.java | EXISTING + M1; không @Lock mới, giữ query single-type cho caller khác | Spring Data derived query như Shop App repositories; Saga lock ở service bảo vệ quyết định |
| C3 | main/java/.../config/RabbitMqConfig.java | EXISTING sửa M6; giữ @Configuration/@Bean, field @Value, constructor hiện có | QueueBuilder.quorum từ dependency đã có; NGOÀI MẪU Shop App về AMQP, tái sử dụng bean topology hiện tại |
| C4 | main/java/.../client/BookingPurchaseClient.java | EXISTING sửa M7; giữ @Component/@RequiredArgsConstructor, RestClient/ContractCodec final, @Value field | Constructor injection và property field như JwtTokenUtil; không thêm DTO decoder hoặc dependency |
| C5 | test/java/.../service/PaymentFlowIntegrationTest.java | EXISTING; giữ @SpringBootTest/@ActiveProfiles; thêm U1/U2, sửa M10 | Giữ test infrastructure hiện tại; không test class/constructor/helper mới |
| C6 | test/java/.../config/PaymentConfigurationTest.java | EXISTING sửa M8; giữ @Test/helper configuration hiện có | Dùng assertions/config loader đã có, kiểm cả binding và queue type |
| C7 | test/java/.../client/BookingPurchaseClientTest.java | EXISTING sửa M9; giữ @RestClientTest/@Import/MockitoBean và setUp hiện có | Dùng mock HTTP hiện tại, test lỗi dependency và credential |
| C8 | DTO/validator/mapper, PaymentMessageListener, PaymentWorker; Booking API/listener/service/relay; TicketContracts/TicketService/publisher | REUSE, không đổi Java | Giữ record API, bean constructors, mapping và ACK mode. Không thêm dependency DTO giữa module |
| C9 | deploy/rabbitmq/definitions.json; services/payment-service/src/main/resources/application.yaml; .env.example; docs/architecture/B13-api-and-event-contracts.md; docs/architecture/B14-main-flow-sequences.md; services/payment-service/README.md; docs/java-workflow/explanations/2026-11-02-payment-end-to-end-flow.md | EXISTING non-Java | Đồng bộ key/default machine client, mô tả semantics/checkpoints và kết quả kiểm; không copy secret hoặc đổi nghiệp vụ hủy |
| C10 | test/java/.../dto/ContractCodecTest.java; test/resources/contracts/compatibility/*.json | EXISTING class + fixture JSON mới, sửa M11 | Lưu JSON từ producer thật để chống fixture tự khớp; không tạo Java type/test helper |

**Không thêm class/interface/record/enum/constructor, không đổi Lombok/codegen API, không thêm dependency hoặc schema.** C8 không được dùng làm quyền sửa Java Booking/Ticket ngoài danh sách; nếu phát hiện cần thay implementation hai phía thì trình delta.

## 3. Danh sách method — PENDING

| ID | Class | Chữ ký đầy đủ và annotation | Loại, trách nhiệm/caller |
| --- | --- | --- | --- |
| M1 | OutboxMessageRepository | `public Optional<OutboxMessage> findFirstByAggregateIdAndMessageTypeInOrderByOccurredAtDescMessageIdDesc(UUID aggregateId, List<String> messageTypes)`; không annotation mới | **NEW**; M2/M3/M4 cần tìm checkpoint cả hai outcome, single-type hiện tại không đáp ứng |
| M2 | PaymentSagaServiceImpl | `public void recordIssuanceResult(Envelope<IssuanceResult> incomingEnvelope)`; @Override/@Transactional giữ nguyên | EXISTING; listener gọi, sửa key/version và conflict check trước state early-return |
| M3 | PaymentSagaServiceImpl | `public void recordOrderCompleted(Envelope<OrderCompleted> incomingEnvelope)`; @Override/@Transactional giữ nguyên | EXISTING; listener gọi, bắt buộc checkpoint Ticket COMPLETED trước khi đóng Saga |
| M4 | PaymentSagaServiceImpl | `public void recoverSaga(UUID orderId)`; @Override/@Transactional giữ nguyên | EXISTING; PaymentWorker gọi, resend đúng loại command theo checkpoint |
| M5 | PaymentSagaServiceImpl | `public void recordSuccessfulCharge(Charge charge)`; @Override/@Transactional(propagation=MANDATORY) giữ nguyên | REUSE UNCHANGED fix ed10c36, chỉ chạy lại regression |
| M6 | RabbitMqConfig | `public Declarables paymentSagaTopology()`; @Bean giữ nguyên | EXISTING; QueueBuilder.durable(name).quorum().build(), binding giữ contract mới |
| M7 | BookingPurchaseClient | `public PurchaseSnapshot preparePurchaseSnapshot(UUID orderId, String buyerSubject)` | EXISTING; createPayment gọi, lỗi JSON response→BOOKING_UNAVAILABLE |
| M8 | PaymentConfigurationTest | `void bindsDatabaseWithoutAllowingSchemaMutation() throws Exception`; @Test giữ nguyên | EXISTING; assert dynamic=false, chính xác exchange/key/queue/quorum ở bean và definitions |
| M9 | BookingPurchaseClientTest | `void rejectsInvalidSnapshotResponses()`; @Test giữ nguyên | EXISTING; assert code BOOKING_UNAVAILABLE/503 cho null/rỗng/sai JSON/sai shape |
| M10a | PaymentFlowIntegrationTest | `void forwardsTicketSuccessAndWaitsForBookingCompletion()`; @Test giữ nguyên | EXISTING, thuộc M10 design; bỏ fixture thành công không có Ticket checkpoint, kiểm version/replay/recovery result |
| M10b | PaymentFlowIntegrationTest | `void recoversSagaAfterCommitAndDeliveryFailures() throws Exception`; @Test giữ nguyên | EXISTING, thuộc M10 design; giữ test AcceptPayment, thêm kiểm version lệnh Ticket recovery và legacy result đã publish trong flow phù hợp |
| M10c | PaymentFlowIntegrationTest | `void keepsExpiryCancellationAndRefundGuards()`; @Test giữ nguyên | EXISTING, thuộc M10 design; assert key/version FAILED mới và giữ guard cũ, không triển khai refund mới |
| M11 | ContractCodecTest | `void preservesSnapshotWireFormat() throws Exception`; @Test giữ nguyên | EXISTING bổ sung so với vị trí test trong design; đọc fixtures compatibility do Booking/Ticket serialize và validate bằng codec/validator thật |
| U1 | PaymentFlowIntegrationTest | `void rejectsOrderCompletionWithoutForwardedTicketResult()`; @Test, package-private, không throws | **NEW**; thiếu checkpoint không đóng Saga và không lưu receipt completion sai |
| U2 | PaymentFlowIntegrationTest | `void rejectsConflictingTicketResultsAcrossOutcomes()`; @Test, package-private, không throws | **NEW**; thử success→failed và failed→success, sai issuance/count/failureCode, bảo đảm rollback và không lặp side effect |

Không thêm helper/test method ngoài bảng. M10a/b/c chỉ tách ID của ba method đã có, không đổi chữ ký. Các cập nhật caller/fixture còn lại chỉ thay reference/string/assertion liên quan, không đổi annotation hay phong cách. Nếu test setup cần thêm API khai báo mới thì trình delta, không giấu trong lambda/class ẩn.

## 4. Các task triển khai

Tất cả task đang **PENDING**; danh sách ID bên dưới là đối tượng xin duyệt, chưa phải các item đã duyệt.

### T1 — Sửa forwarding và checkpoint của Payment

- Item: C1/C2/C5, M1/M2/M3/M10a/U1/U2. Phụ thuộc duyệt gate thực thi, đọc lại ref mới nhất.
- File chính: PaymentSagaServiceImpl.java, OutboxMessageRepository.java, PaymentFlowIntegrationTest.java.
- Viết U1/U2 trước phần sửa: trên code cũ, case thiếu checkpoint đang bị hoàn tất; conflict khác outcome có thể bị return trước kiểm. Xác nhận test thất bại vì hành vi cần sửa rồi mới đổi production.
- M1 dùng Spring Data query IN cho `ticket.issued.v1`, `booking.issuance-failed.v1`, legacy `booking.issuance-result.v1` chỉ để nhận diện lịch sử. Giữ single-type query hiện có.
- M2 giữ khóa finance→snapshot→Saga. Sau validation và inbox replay check, so checkpoint trước early-return theo Saga state; cùng issuanceId/chargeId/state/count/failureCode là replay, khác là ISSUANCE_RESULT_CONFLICT. Kết quả mâu thuẫn làm transaction rollback, không lưu inbox thành công.
- Chọn key theo COMPLETED/FAILED, dùng snapshot.getSourceOrderVersion().toString(); payload DTO và metadata giữ contract. COMPLETED giữ WAITING_TICKETS; FAILED giữ nhánh ensureRefund/COMPENSATING hiện có và outbox FAILED trong cùng transaction.
- M3 bỏ đường “checkpoint tùy chọn / Ticket→Booking trực tiếp”. Trong nhánh hoàn tất bình thường, bắt buộc checkpoint COMPLETED, so charge/issuance/count/version; thiếu/sai→ORDER_COMPLETION_MISMATCH. Không yêu cầu publishedAt vì response có thể về trước worker commit metadata publish.
- Giữ guard COMPENSATING/COMPENSATED, cancellation marker và checkpoint inbox order-level; không hồi sinh Saga khi completion tới muộn. Không đổi MANUAL ACK Payment/AUTO ACK Booking.
- Xác minh: U1/U2 pass; M10a theo flow mới pass; kết quả gửi Booking đúng key/version; replay không tạo confirmation/outbox/refund lặp; commit lỗi vẫn rollback trước ACK.

### T2 — Recovery đúng command và xử lý giới hạn dữ liệu cũ

- Item: C1/C5, M1/M4/M5/M10a/M10b. Phụ thuộc T1.
- WAITING_BOOKING: giữ fix AcceptPayment, snapshot version không tăng.
- WAITING_TICKETS chưa có checkpoint kết quả: retry ticket.issue.v1 từ payload cũ, giữ version command đã lưu.
- WAITING_TICKETS có checkpoint COMPLETED: resend ticket.issued.v1, snapshot version; không tạo ticket.issue.v1 mới. Command mới có messageId/occurredAt mới, causationId trỏ command trước.
- Command chưa published/đang transport retry: không nhân thêm recovery; giữ retry cùng messageId và data. Không thêm worker/lease hoặc cơ chế ACK mới.
- Legacy đã published và Saga còn WAITING_TICKETS: decode checkpoint cũ, tạo result mới đúng key/version khi recovery, giữ payload cũ. Không rewrite envelope/messageId cũ.
- Xác minh trong M10a/M10b: tăng rowVersion Saga nhưng version snapshot vẫn giữ; mất phản hồi Booking chỉ tăng số result command; số ticket.issue không tăng; legacy published hồi phục đúng và không phát vé lần hai. FAILED vẫn dựa outbox transport retry hiện có, không mở recovery COMPENSATING.
- Legacy pending/DLQ là điều kiện bàn giao riêng: thống kê read-only trước triển khai; nếu tồn tại chưa có phương án duyệt thì chưa bật publisher mới. Không viết SQL/purge/replay trong task này để tự xử lý dữ liệu thật.

### T3 — Đồng bộ snapshot client và topology

- Item: C3/C4/C6/C7/C9, M6/M7/M8/M9. Phụ thuộc T1 về contract chuẩn.
- BookingPurchaseClient: thêm bắt ContractValidationException từ decode response và map BOOKING_UNAVAILABLE; giữ HTTP404/409/401/403/timeout mapping, POST/body/machine token. Không tạo DTO lỗi hoặc parser riêng; không hạ validation snapshot.
- Payment application.yaml: default PAYMENT_SERVICE_CLIENT_ID=internal-service khớp Compose/realm. Rà .env.example có đủ tên secret/token URI/Booking URL; không điền secret hoặc thay cấu hình runtime ngoài scope. Giữ issuer/quyền INTERNAL_SERVICE, không thêm audience/JWKS override.
- RabbitMqConfig: đổi ba queue bean sang quorum builder, không thêm DLX hardcoded. Runtime vẫn spring.rabbitmq.dynamic=false; definitions là nguồn provision topology.
- deploy/rabbitmq/definitions.json: thay binding vào payment.ticket-issuance-failed.q từ ticket.issuance-failed.v1 sang ticket.issuance-result.v1. Giữ tên queue/quorum/DLQ/policy; không thêm route Ticket thành công thẳng Booking. Ticket implementation dev/tuyen đã gửi đúng key, không cần sửa Java Ticket.
- M8 kiểm key/exchange/destination/type, không chỉ thấy queue tồn tại; M9 đổi expected lỗi invalid response và giữ tests credential/HTTP cũ. Test dynamic=true/explicit-admin nếu có phải dùng cùng quorum config.
- Xác minh: cấu hình runtime không tự declare để che thiếu binding; bean test và definitions mô tả cùng contract; JSON sai trả503, không bị biến thành lỗi đăng nhập buyer.

### T4 — Regression wire và build

- Item: C5–C8/C10, M8/M9/M10a–c/M11/U1/U2. Phụ thuộc T1–T3.
- Lưu fixture trong `services/payment-service/src/test/resources/contracts/compatibility/`: `snapshot-quantity.json`, `snapshot-quantity-promotion.json`, `snapshot-standing.json`, `snapshot-standing-promotion.json`, `snapshot-seated.json`, `snapshot-seated-promotion.json`, `ticket-result-completed.json`, `ticket-result-failed.json`.
- Snapshot lấy từ Booking serializer thật, result theo Ticket serializer/publisher ref đã pin; ghi provenance trong services/payment-service/README.md, không thêm field metadata vào JSON nghiệp vụ. Không import Java Booking/Ticket vào Payment production/test POM.
- M11 đọc cả snapshot/result fixtures, chạy Payment codec/validator/invariant tương ứng; kiểm tiền/version string, UTC, nullable fields vẫn hiện diện. Giữ một case Payment ticket.issue version3/snapshot1 được TicketContracts thực tế chấp nhận.
- Chạy test trọng điểm trước, rồi Payment default verify và PaymentFlowIntegrationTest riêng. Default Surefire hiện loại integration suite nên default green không đủ nghiệm thu.
- Java Payment bắt buộc21 theo POM, không skip enforcer. Dùng Maven/launcher Java21 hiện có và nạp .env gốc qua python-dotenv; không truyền DB/Rabbit/secret tạm bằng JVM/CLI để bù env thiếu.
- Các goal (qua launcher đúng Java và env-file): `-f services/payment-service/pom.xml -Dtest=PaymentConfigurationTest,BookingPurchaseClientTest,ContractCodecTest test`; tiếp `-f services/payment-service/pom.xml -Pquery-tests -Dtest=PaymentFlowIntegrationTest test`; cuối `-f services/payment-service/pom.xml verify`. Thứ tự này không yêu cầu chạy lại suite đã pass nếu không có thay đổi/failure mới.
- Dùng DB/Rabbit test cô lập hiện có, không để integration test TRUNCATE/ALTER bảng business119. Môi trường có thể là container hoặc hạ tầng test khác đáp ứng config; không bắt buộc công cụ mới. Nếu thiếu giá trị env thì xác định/điền đúng trước khi run và báo tên biến, không đoán secret.
- Kết quả mong đợi: 0 failure/error/skip ngoài skip đã khai báo có lý do; Spotless pass, SpotBugs0; report ghi số test thực tế, không dùng lại số trước sửa.

### T5 — Kiểm tra phối hợp và bàn giao

- Item: C8/C9; chỉ dùng API/consumer/worker đã có. Phụ thuộc T4.
- Kiểm local hoặc môi trường test cô lập: Booking→Payment HTTP snapshot có machine auth; broker đúng bindings; có consumer Payment/Ticket/Booking; Ticket có AccessConfiguration event và ba feature flags business/listeners/workers=true. Không đổi mặc định feature flag để tự bật production.
- Cho lệnh đi qua Payment publisher→Ticket decoder/service; nhận result qua Ticket publisher→Payment listener/service→Booking listener/service; nhận OrderCompleted về Payment. Dùng payload producer thật, kiểm DB trạng thái/inbox/outbox ở từng owner; không chỉ thấy publisher confirm hoặc mock result mà gọi là E2E.
- Case tối thiểu: success, result COMPLETED gửi lại do thiếu xác nhận, replay command và replay result; kiểm số vé không tăng. Với FAILED dứt điểm kiểm key/count/code và release Booking, không chứng nhận refund provider đã thực thi.
- Không cần schema/Java test mới cho bước này: dùng runtime/API/broker clients sẵn có. Nếu không có runtime/dependency cần thiết, ghi chính xác phần chưa chạy; không thay bằng fixture rồi đánh dấu E2E pass.
- Cập nhật phần liên quan docs/architecture/B13-api-and-event-contracts.md và B14-main-flow-sequences.md: version của các message gửi Booking, flow checkpoint bắt buộc, không nhánh bypass Ticket→Booking; giữ quy tắc hủy/refund ngoài scope. Cập nhật services/payment-service/README.md và docs/java-workflow/explanations/2026-11-02-payment-end-to-end-flow.md với ref, môi trường, kết quả và giới hạn.
- Bàn giao gồm diff source/config, test results, snapshot/result wire evidence, trạng thái dữ liệu legacy và danh sách việc deploy119 còn lại. Live119, chỉnh binding, xử lý backlog, Git commit/push hoặc cập nhật ClickUp chỉ làm khi user giao riêng; không suy quyền từ task fix AcceptPayment trước.

## 5. Ràng buộc và đối chiếu convention

- Git common directory dùng chung `D:/DATN/flash-ticket-platform/.git`; đã đối chiếu common.md, project INDEX và flash-ticket-platform.md. Không đổi rule người dùng trong task lập plan này.
- Theo mẫu Shop App đã đọc: `src/main/java/com/project/shopapp/services/impl/OrderService.java#updateOrder` và `components/JwtTokenUtil.java`: method nghiệp vụ trực tiếp, constructor dependency, property @Value field. M1 có ba caller thực tế; không tạo tầng repository wrapper/helper validate mới.
- Giữ DTO record/ContractCodec/JPA lock/inbox/outbox/ACK hiện có như ngoại lệ dự án, không ép thành DTO framework mới, không thêm EntityManager/native SQL/flush cho implementation. SQL tạo lỗi có chủ đích chỉ nằm trong integration test cô lập hiện có.
- Giữ Booking plain logging UTF-8, không mở rộng đổi logging Payment/Ticket. Không sửa user `.env.example` ngoài đúng block được giao; kiểm và bảo toàn các thay đổi đang có.
- Specs và plan đang nháp; các test wire 6/6 snapshot và 8/8 Ticket–Payment trước đây chỉ là evidence khảo sát, không phải kết quả của code sẽ sửa.
- Production Payment dynamic=false khiến thiếu binding không tự được Java chữa. Definitions import không bảo đảm xóa binding cũ trên broker; rollout sau này phải kiểm thực trạng, không xóa queue để đổi queue type.
- Cancellation/refund vẫn thiếu trong Payment theo phạm vi user đã hoãn; hoàn tất task này không đồng nghĩa toàn bộ Saga hủy/refund đã hoàn thiện.

## 6. Gate và nguồn xác nhận

| Pha | Danh sách / trạng thái | Nguồn |
| --- | --- | --- |
| Phạm vi nghiệp vụ | Không triển khai hủy/refund Payment: USER_CONFIRMED | Câu user được trích đầu plan |
| BRAINSTORMING | C1–C9/M1–M10/U1–U2: PENDING trong design | Chưa có câu duyệt rõ danh sách; không tự đổi thành APPROVED |
| WRITING-PLANS | C1–C10, M1–M9/M10a–c/M11/U1–U2: **PENDING** | User gọi writing-plans là yêu cầu soạn plan, chưa phải duyệt nội dung vừa soạn |
| EXECUTING-PLANS | Chưa bắt đầu | Chỉ chuyển khi user giao triển khai; có gate riêng trước khi tạo Java mới |

Delta từ design: C10/M11 chỉ định test/fixture cũ để lưu regression; M10 tách ba method hiện có thành M10a/b/c. Không phát sinh class hoặc method Java mới ngoài M1/U1/U2. Nếu user yêu cầu đổi hướng/cắt legacy/checkpoint thì cập nhật cả design và plan trước khi thực thi.
