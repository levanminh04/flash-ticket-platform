# Payment Purchase Runtime Implementation Plan

> Phần tích hợp cổng thanh toán đã được người dùng yêu cầu gỡ ngày 2026-10-28. Các bước cấu hình/callback/refund của cổng trong tài liệu này là lịch sử, không được thực hiện hoặc khôi phục. Hợp đồng DTO/message và mô hình tài chính hiện có được giữ nguyên.


> **Cần cập nhật trước khi thực hiện:** Người dùng yêu cầu xóa toàn bộ simulator ngày 2026-10-27. Các bước giữ/tái sử dụng StartPaymentProcessor/Store, PaymentSimulatorProfileTest hoặc thêm profile payment-only-tests bên dưới đã lỗi thời; không khôi phục simulator hay loại trừ test để triển khai plan này. Lập lại các bước tạo runtime từ các thành phần Payment còn tồn tại. Việc triển khai runtime chưa bắt đầu.

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Triển khai phần gửi/nhận và nghiệp vụ cục bộ của Payment: nhận yêu cầu frontend, gửi request lấy snapshot, xử lý response, trả URL VNPay và xử lý message thanh toán/phát vé/bù trừ.

**Architecture:** Nâng cấp core tạo attempt hiện có, bổ sung HTTP controller và client gửi/nhận trong Payment. IPN/query ghi charge và một saga/order; các transition ghi outbox trong transaction, relay gọi broker ngoài transaction. Booking/Ticket là hệ thống bên ngoài; kiểm Payment bằng response/message định trước và PostgreSQL/RabbitMQ riêng, không triển khai lại nghiệp vụ của các service đó.

**Tech Stack:** Java 21, Spring Boot 4.1.1, Spring Data JPA/PostgreSQL, Spring AMQP, Spring Security JWT, Jackson 3, Jakarta Validation, Lombok, MapStruct 1.6.3, JDK HttpClient/HttpServer, JUnit/AssertJ/Mockito hiện có. Không nâng version hoặc thêm dependency nếu các thư viện hiện tại đáp ứng.

**Spec:** `services/payment-service/docs/superpowers/specs/2026-10-27-payment-purchase-runtime-design.md` (từ root repo).

**Status:** Kế hoạch đề xuất, chờ người dùng duyệt theo AGENTS.md; chưa sửa Java. Kiến trúc HTTP start + MQ sau thu tiền đã được người dùng chọn. Đề xuất thực hiện trực tiếp trong chat này bằng executing-plans, trên nhánh `dev/tuan`.

## Global Constraints

- Chỉ sửa `services/payment-service`, trực tiếp checkout hiện tại trên `dev/tuan`; không tạo worktree hoặc đổi branch.
- Public POST tại Payment: `/api/v1/orders/{orderId}/payment-attempts`, JWT BUYER, Idempotency-Key UUID, body `{provider:'VNPAY'}`, trả `200 ApiResponse<PaymentStart>` chứa URL đã persist trong data.
- Theo yêu cầu mới của người dùng, tất cả API nghiệp vụ Payment dùng ApiResponse<T> gồm data/success/message và đầy đủ Operation. GET chuyển sang ApiResponse<PaymentView>; lỗi HTTP nghiệp vụ/security dùng ApiResponse<Problem>, giữ HTTP status và chi tiết Problem. Đây là thay đổi response HTTP có chủ đích, không đổi DTO/message bên trong. IPN VNPay giữ ACK và Return giữ HTML đúng giao thức.
- Phía Payment dựng request gửi tới endpoint lấy snapshot (đề xuất `/internal/v1/orders/{orderId}/freeze-payment`), body buyerSubject từ JWT đã xác minh; đọc response `{purchase:PurchaseSnapshot}`, kiểm dữ liệu và xử lý mã lỗi/timeout. Đây chỉ là contract gửi/nhận của Payment; endpoint/path cấu hình được, không triển khai API hoặc nghiệp vụ Booking.
- Không viết Booking controller/service/database, tạo/hủy đơn, giữ chỗ, chốt giá, thuật toán freeze hay mock Booking có state/store. Trong test chỉ cung cấp HTTP response/JSON/message định trước để kiểm Payment.
- Không bật production consumer `payment.start-requested.v1`. Sau thu tiền dùng B13 messages; completion thành công là Ticket → Booking → `booking.order-completed.v1` → Payment, khác mũi tên Payment → Booking ở bước 47 của hình.
- Money/version string trên wire; Java BigDecimal/UUID/Instant; currency VND; DTO/mapper/schema hiện tại; DDL chỉ validate, không init/update DB chung.
- Một candidate saga/order; một refund/actual charge; ACK sau commit; HTTP/broker/provider network ngoài transaction khóa hàng. Tạo URL VNPay hiện là phép tính cục bộ, được phép trong transaction tạo attempt.
- Dedupe HTTP namespace `payment.http-start.v1` dùng requestHash; MQ namespace riêng dùng envelopeHash. Không trộn codec/response của hai loại.
- Hủy event hàng loạt, payout, financial-summary, UI, QR riêng và service nghiệp vụ khác ngoài scope. Tôn trọng cancellation marker nếu đã có; không claim event-cancellation E2E.
- Callback/refund có cờ riêng mặc định false; adapter hiện chỉ hỗ trợ sandbox. UNKNOWN không cho phép request thu/hoàn mới; queryRefund hiện không xác định được kết quả nên có thể cần đối soát ngoài đợt.
- Giữ thay đổi có sẵn, staged deletions và test của người dùng; không git reset/restore/clean hoặc hạ quality gate. Các test công cụ Booking cũ đang thiếu implementation được giữ nguyên và ghi rõ ngoài phạm vi, dùng profile Payment-only chọn đúng tập test thay vì viết thêm Booking để làm xanh toàn repo. Không claim default whole-module verify đã pass khi nhóm ngoài scope còn lỗi. Chỉ commit thay đổi của task khi có thể tách khỏi diff của người dùng; nếu trùng hunk thì giữ working tree để review.

## Review Focus

- Mất response từ client ngoài hoặc sau Payment commit: cùng key phải hội tụ về một attempt; Payment giữ expiresAt nhận được, không tự gia hạn — Task 3 và 9.
- Hai buyer dùng cùng Idempotency-Key: không rò URL/đơn; một buyer đổi order/provider dưới key cũ nhận conflict — Task 3.
- Hai providerChargeId trên cùng attempt, kể cả callback tới sau FAILED/SUCCEEDED: lưu từng khoản thu, chỉ một candidate và một refund/charge thừa — Task 5.
- Worker cũ nhận confirm sau lease mới: không ghi publishedAt/error lên claim mới; crash sau confirm tạo duplicate an toàn — Task 4 và 7.
- OrderCompleted tới trễ sau compensation hoặc cùng order có issuanceId khác: không hồi sinh saga hoặc ACK như replay hợp lệ — Task 6.

## Baseline, cách đọc đường dẫn và cách chạy

Mọi đường dẫn file bên dưới tính từ `services/payment-service`. Các prefix **M** = `src/main/java/com/flashticket/payment_service/`, **T** = `src/test/java/com/flashticket/payment_service/`; nối prefix với tên được liệt kê cho ra đường dẫn chính xác, không phải wildcard. Mỗi class/record có file cùng tên trừ nested types được ghi rõ.

Baseline 2026-10-27: branch dev/tuan, HEAD tài liệu ban đầu a1f2cfe. Java mặc định máy là 17; Java 21 có ở `C:/Users/WEOHWOEH/.jdks/ms-21.0.12.1`. Chạy `test-compile -DskipTests` bằng Java 21 đã FAIL ở PaymentSimulatorConfiguration.java do import dev.booking/QUEUE sau khi CLI bị xóa; chưa chạy tới testCompile. Log local: `target/payment-runtime-baseline.log`.

Chạy các lệnh Maven trong module, với môi trường process:

```powershell
$env:JAVA_HOME = 'C:\Users\WEOHWOEH\.jdks\ms-21.0.12.1'
$env:JAVA_TOOL_OPTIONS = '-Duser.timezone=UTC'
.\mvnw.cmd -B -ntp test-compile
```

Task 1 thêm profile `payment-only-tests` chỉ loại khỏi test compilation bốn test công cụ Booking đã thiếu source: dev/booking/BookingSimulatorTest.java, dev/booking/BookingMockServerTest.java, dev/booking/MockBookingStoreTest.java và service/start/StartPaymentRabbitIntegrationTest.java. Dùng đúng full package path trong testExcludes; không wildcard loại cả package hoặc test mới. Mọi test Payment còn lại giữ nguyên. Chạy từ clean output để không dùng class cũ. Default profile không bị sửa để giấu lỗi ngoài phạm vi; bàn giao báo riêng tình trạng đó.

Các lệnh test/verify trong task bên dưới phải chạy kèm `-Ppayment-only-tests`; khi cần DB/broker, dùng `-Ppayment-only-tests,runtime-tests`. Test DB dùng PAYMENT_TEST_DB_URL/USERNAME/PASSWORD; broker integration dùng PAYMENT_TEST_RABBIT_HOST/PORT/USERNAME/PASSWORD/VHOST, vhost test riêng. Không in secret hoặc unbind queue dùng chung. Thiếu môi trường integration là FAIL rõ/NOT_RUN khi báo cáo, không silently skip. Unit Payment suite không cần DB/broker thật.

## Bản đồ trách nhiệm và helper chung

| Nhóm | Trách nhiệm và annotation |
| --- | --- |
| PaymentStartController, PaymentQueryController | HTTP boundary mỏng; RestController/RequestMapping/RequiredArgsConstructor/FieldDefaults/Slf4j; Tag/Operation/SecurityRequirement; PreAuthorize BUYER cùng filter chain; ApiResponse qua helper builder |
| PaymentStartService, Booking client | Orchestration ngoài transaction; Service/constructor injection; timeout/strict response validation |
| HttpPaymentStartStore, PaymentAttemptCreator | Store mở transaction; creator dùng MANDATORY qua bean riêng để tránh gọi ngoài boundary; không giả Envelope Booking |
| RecordPaymentResultService, PaymentSagaService, RefundService | Transaction theo use case; helper bắt buộc transaction dùng MANDATORY; chỉ Slf4j khi thực sự log |
| InboxService, OutboxService, PaymentTransactionLocks | Dedupe/envelope/lock dùng chung; không self-invocation để giả transaction mới |
| OutboxRelay, consumers, workers | Gọi transactional services rồi network/ACK bên ngoài; config có ConditionalOnProperty và chặn profile sai |
| Entity/DTO/mapper | Factory entity đúng invariant, giữ Version/LAZY; DTO record/constraint; MapStruct shared config, không query/sinh time/ID trong mapper |

Helper hiện có phải tái sử dụng: `ContractCodec.read/write/fromTree`, `PurchaseSnapshotValidator.validate`, `ContractHashService.requestHash/purchaseHash/envelopeHash`, `PaymentStartMapper.toDto`, `OrderPaymentSnapshotMapper.toEntity`, `InboxMessage.processed`, `ProviderPaymentResult.validateAgainst`, `PaymentException/PaymentErrorCode`, `PaymentProblemFactory`. Logic kiểm payable/tạo context/ghi attempt được trích từ StartPaymentStore, không sao chép sang HTTP service. Mapping transport/provider giữ trong gateway/vnpay.

## Task 1: Gỡ phụ thuộc Payment vào CLI đã xóa và tách phạm vi kiểm thử

**Files:**
- Create M`config/PaymentSimulatorTopology.java`.
- Modify M`config/PaymentSimulatorConfiguration.java`.
- Modify `pom.xml` để thêm profile kiểm Payment độc lập, không đổi default test scope.
- Existing Payment tests: T`config/PaymentSimulatorProfileTest.java`, T`validation/MessageInvariantValidatorTest.java`, T`validation/PurchaseSnapshotValidatorTest.java`.

**Interfaces:**
- Topology giữ chính xác EXCHANGE=payment.simulator.ex, ROUTING_KEY=payment.start-requested.v1, QUEUE=payment.simulator.start.q, DLX=payment.simulator.dlx, DLQ=payment.simulator.start.dlq; production configuration chỉ import class config mới.
- Test dùng `PaymentContractFixtures` hiện có và JSON response cố định; không tạo MockBookingStore/BookingMockServer/BookingCommandFactory hoặc file store thay thế.

- [ ] **1.1:** Lưu baseline status/diff và lỗi main compile đã có; compiler kiểm trực tiếp import package đã xóa. Ghi bốn test Booking tools ngoài scope như mục baseline, không bổ sung implementation Booking để giải quyết chúng.

  Giữ assertion profile hiện có: `assertThat(context).doesNotHaveBean(BookingPaymentGuard.class); assertThat(context).doesNotHaveBean("simulatorListener");` khi không chọn dev profile.
- [ ] **1.2:** Tách constants sang config và thay import. Thêm profile payment-only-tests với bốn testExcludes chính xác đã liệt kê; giữ toàn bộ test Payment, formatter và static-analysis gates. Không tạo source dưới dev/booking hoặc sửa/xóa bốn file test ngoài scope.
- [ ] **1.3:** Chạy `.\mvnw.cmd -B -ntp -Ppayment-only-tests clean test-compile` rồi `.\mvnw.cmd -B -ntp -Ppayment-only-tests '-Dtest=PaymentSimulatorProfileTest,MessageInvariantValidatorTest,PurchaseSnapshotValidatorTest' test`. Expected PASS cho phạm vi Payment; default whole-module baseline vẫn báo riêng, không gọi là đã sửa Booking tests.
- [ ] **1.4:** Review diff chỉ gỡ phụ thuộc main và chọn tập test đúng phạm vi. Ghi rõ command/profile và test ngoài scope trong kết quả, giữ staged deletions của người dùng.

## Task 2: Client gửi request và nhận response trong Payment

**Files:**
- Create M`config/PaymentRuntimeProperties.java`, M`config/BookingClientProperties.java`, M`config/PaymentRuntimeConfiguration.java`.
- Create M`client/booking/BookingPaymentClient.java`, M`client/booking/HttpBookingPaymentClient.java`, M`client/booking/ServiceAccessTokenProvider.java`, M`client/booking/BookingFreezeResponse.java`, M`client/booking/BookingFreezeRequest.java`.
- Modify M`exception/PaymentErrorCode.java`, `src/main/resources/application.yaml`, `src/main/resources/application-local-foundation.yaml`.
- Create T`client/booking/HttpBookingPaymentClientTest.java`, T`client/booking/ServiceAccessTokenProviderTest.java`, T`config/PaymentRuntimeConfigurationTest.java`.

**Interfaces:**
- `BookingPaymentClient.freeze(UUID orderId, String buyerSubject): PurchaseSnapshot`.
- Client và DTO thuộc Payment; endpointPath cấu hình được với default đề xuất `/internal/v1/orders/{orderId}/freeze-payment`. Chỉ nhận/gửi contract này; không viết cách Booking lấy dữ liệu, kiểm đơn hoặc freeze. Test HTTP chỉ trả body/status đã định trước và ghi request để assert.
- `ServiceAccessTokenProvider.accessToken(): String`; client-credentials POST form với client_id/client_secret/scope cấu hình; không dùng bearer BUYER. Cache token với Clock, skew 30s; token sống ngắn hơn skew vẫn dùng cho request hiện tại nhưng không cache quá hạn. Bảo vệ refresh đồng thời.
- Config namespace `flashticket.payment.runtime` có enabled=false, relayEnabled=true, workersEnabled=true, pollInterval=5s,batchSize=20,maxBackoff=300s,pendingAlertAfter=15m. Booking namespace `flashticket.payment.booking`: baseUrl,endpointPath=/internal/v1/orders/{orderId}/freeze-payment,tokenUrl,clientId,clientSecret,scope,connectTimeout=2s,readTimeout=5s,maxBodyBytes=65536. endpointPath chỉ là path template của client; resolve trên baseUrl cấu hình, không lấy URL từ frontend. Tất cả numeric/duration validate dương; credentials chỉ bắt buộc khi runtime.enabled=true.
- BookingFreezeRequest(String buyerSubject), BookingFreezeResponse(PurchaseSnapshot purchase) với Bean Validation phù hợp. Error codes thêm: ORDER_EXPIRED/ORDER_NOT_PAYABLE/IDEMPOTENCY_CONFLICT/PAYMENT_IN_PROGRESS/SNAPSHOT_CONFLICT (409), BOOKING_UNAVAILABLE/PAYMENT_GATEWAY_UNAVAILABLE (503), AMOUNT_OUT_OF_RANGE/INVALID_ORDER_TIME (422). RESOURCE_NOT_FOUND giữ 404.

- [ ] **2.1:** Viết tests `returnsValidatedSnapshotForJwtSubject`, `rejectsWrongOwnerOrderOrSnapshotShape`, `distinguishesBusinessRejectionFromCredentialAndNetworkFailure`, `refreshesExpiredTokenOnceForConcurrentCallers`. Assert đúng method/path/body, không chuyển token buyer; 404/409 mapping theo spec, 401/403/timeout/5xx → BOOKING_UNAVAILABLE; unknown/duplicate JSON field của snapshot bị từ chối.

  Assertion đích: `assertThat(result.buyerSubject()).isEqualTo("buyer-a"); assertThat(result.orderId()).isEqualTo(orderId); assertThat(tokenRequestCount.get()).isEqualTo(1);` cho các caller đồng thời khi token hợp lệ.
- [ ] **2.2:** Chạy `.\mvnw.cmd -B -ntp -Ppayment-only-tests '-Dtest=HttpBookingPaymentClientTest,ServiceAccessTokenProviderTest,PaymentRuntimeConfigurationTest' test`; expected RED do thiếu implementation.
- [ ] **2.3:** Implement bằng JDK HttpClient với redirect NEVER, connect/request timeout và giới hạn body; không giữ transaction. HTTPS cho URL runtime; loopback HTTP chỉ với profile test/dev rõ. Validate PurchaseSnapshot bằng codec/invariant; so path order và JWT buyer. Không retry 401 vô hạn hoặc log token/response thô. Cờ runtime + simulator/prod/sandbox phải fail rõ khi không tương thích; local-foundation không bật HTTP start/workers.
- [ ] **2.4:** Chạy lại tests, expected PASS; kiểm `TransactionSynchronizationManager.isActualTransactionActive()` false tại fake HTTP boundary. Runbook sẽ ghi token scope/audience và Booking response là integration contract cần participant đáp ứng.

## Task 3: Public POST trả URL và tạo attempt dùng chung

**Files:**
- Create M`controller/PaymentStartController.java`, M`dto/request/CreatePaymentRequest.java`.
- Create M`dto/common/ApiResponse.java`, M`controller/support/ApiResponses.java`, M`config/PaymentOpenApiConfiguration.java`.
- Modify M`controller/PaymentQueryController.java`, M`controller/VnpayCallbackController.java`, M`security/PaymentSecurityProblemHandler.java`, `pom.xml`.
- Create M`service/start/PaymentStartService.java`, M`service/start/HttpPaymentStartStore.java`, M`service/start/HttpStartOutcome.java`, M`service/start/PaymentAttemptCreator.java`, M`service/start/AttemptCreation.java`, M`service/start/StartPaymentInput.java`.
- Create M`service/common/PaymentTransactionLocks.java`, M`service/common/PaymentContexts.java`.
- Modify M`service/start/StartPaymentProcessor.java`, M`service/start/StartPaymentStore.java`, M`config/PaymentSecurityConfiguration.java`, M`exception/PaymentExceptionHandler.java` để bọc lỗi nghiệp vụ và giữ response callback, M`config/PaymentRuntimeConfiguration.java`.
- Create T`controller/PaymentStartControllerTest.java`, T`service/start/PaymentStartServiceTest.java`, T`service/start/HttpPaymentStartStoreDatabaseTest.java`.
- Create T`controller/PaymentApiResponseTest.java`, T`config/PaymentOpenApiTest.java`; cập nhật assertions HTTP trong test GET/error/security hiện có sang data, không đổi assertions message MQ.
- Modify T`security/PaymentJwtSecurityTest.java`, T`config/PaymentSimulatorProfileTest.java`.

**Interfaces:**
- `CreatePaymentRequest(@NotNull PaymentProvider provider)`; controller `ApiResponse<PaymentStart> create(UUID orderId, UUID idempotencyKey, CreatePaymentRequest request, Jwt jwt)`, lấy header Idempotency-Key, trả Cache-Control no-store và correlation theo convention hiện có. Strict DTO body, không nhận amount/owner/snapshot.
- `ApiResponse<T>` là DTO immutable với Lombok Builder, fields `T data`, `boolean success`, `String message`. `ApiResponses.success(T data,String message)` và `ApiResponses.failure(Problem problem,String message)` dựng response bằng `ApiResponse.<T>builder()` dùng chung cho controllers/advice/security handler. POST dùng thông báo “Tạo yêu cầu thanh toán thành công”; GET dùng “Lấy thông tin thanh toán thành công”. Service/mapper và inbox vẫn trả/lưu DTO nghiệp vụ, chỉ bọc tại HTTP boundary.
- Mỗi endpoint có Operation summary/description tiếng Việt, Tag, parameters/header/request body và responses đúng schema/content type/status thực tế. Buyer APIs có SecurityRequirement(name="bearerAuth"), PreAuthorize("hasRole('BUYER')"); giữ ownership service và bật EnableMethodSecurity. Bỏ final ở controller được proxy method-security; kiểm Spring context thực để phát hiện lỗi proxy. Slf4j chỉ log orderId/attemptId an toàn, không URL thanh toán/token/payload.
- Bổ sung `org.springdoc:springdoc-openapi-starter-webmvc-api` nhánh 3.x tương thích Boot 4 theo [tài liệu springdoc](https://springdoc.org/); chốt version cụ thể bằng release chính thức và kiểm dependency/build khi triển khai. OpenAPI config khai báo HTTP bearer JWT tên bearerAuth; bật tài liệu trong local/test, không mở docs public trong production. Không cần Swagger UI cho phạm vi yêu cầu này. Tránh trùng tên DTO ApiResponse với annotation Swagger ApiResponse bằng fully-qualified annotation.
- Lỗi 400/401/403/404/409/422/503/500 của API nghiệp vụ dùng success=false/data=Problem/message an toàn; giữ code/correlation/violations, WWW-Authenticate và Cache-Control hiện có. Advice phải giữ riêng callback: IPN `{RspCode,Message}` không bọc, Return text/html không bọc, không gắn bearerAuth/PreAuthorize BUYER; mô tả xác minh chữ ký và tác dụng thực tế của mỗi callback trong Operation. Không biến lỗi thành HTTP 200.
- `PaymentStartService.start(UUID orderId, String buyerSubject, UUID requestKey, PaymentProvider provider): PaymentStart` không Transactional; gọi hash/replay → Booking freeze → store commit → chuyển outcome thành response/PaymentException sau commit.
- `HttpPaymentStartStore.replay(UUID requestKey,String requestHash,String buyerSubject): Optional<HttpStartOutcome>` readOnly; `apply(StartPaymentInput input): HttpStartOutcome` Transactional. `StartPaymentInput(UUID requestKey,String requestHash,PaymentProvider provider,PurchaseSnapshot purchase,String purchaseHash)`.
- `HttpStartOutcome(String buyerSubject,UUID orderId,int httpStatus,PaymentStart payment,PaymentErrorCode error)` có đúng một payment/error; codec lưu JSON nội bộ namespace payment.http-start.v1. Lỗi domain đã lưu được throw ngoài transactional bean để không rollback record rejection.
- `PaymentAttemptCreator.create(StartPaymentInput input, String bookingRejection): AttemptCreation` MANDATORY; `AttemptCreation(UUID attemptId, PaymentErrorCode rejection)` đúng một kết quả. Trích rule/snapshot/attempt/URL từ StartPaymentStore; adapter MQ vẫn lưu StartPaymentResult như cũ. Không gọi helper bằng fake Envelope trong HTTP.
- `PaymentTransactionLocks.message(String consumer,UUID key)`, `.event(UUID eventId)`, `.order(UUID orderId)` dùng pg_advisory_xact_lock và yêu cầu transaction; thứ tự message→event→order→rows. `PaymentContexts.payment(PaymentAttempt attempt,OrderPaymentSnapshot snapshot): PaymentContext` thuần, dùng chung start/record/query/refund.

- [ ] **3.1:** Viết tests `returnsPersistedUrlOnFirstPost`, `replaysAfterResponseLossWithoutCallingBooking`, `doesNotLeakResponseAcrossBuyersSharingKey`, `rejectsChangedOrderUnderSameKey`, `rollsBackAllPaymentRowsOnUrlFailure`, `concurrentRequestsCreateOneUnfinishedAttempt`. Assert success 200, amount="100000", provider VNPAY, một attempt, cùng attemptId/URL khi retry; snapshot TTL không tăng; GET ownership còn đúng.

  Assertion đích: `assertThat(replayed.attemptId()).isEqualTo(first.attemptId()); assertThat(replayed.paymentUrl()).isEqualTo(first.paymentUrl()); assertThat(replayed.amount()).isEqualTo("100000"); verifyNoInteractions(bookingAfterCommit);` và DB count theo order bằng 1.
- [ ] **3.2:** Chạy `.\mvnw.cmd -B -ntp -Ppayment-only-tests '-Dtest=PaymentStartControllerTest,PaymentStartServiceTest,PaymentJwtSecurityTest' test`; expected RED. Task 4 thêm profile DB để chạy HttpPaymentStartStoreDatabaseTest; trước đó không dùng mock transaction làm bằng chứng race.
- [ ] **3.3:** Implement interfaces, tái sử dụng mapper/codec/hashes và tách creator. Cùng key khác subject → RESOURCE_NOT_FOUND; cùng subject khác hash → IDEMPOTENCY_CONFLICT. Replay trước Booking nhưng chỉ sau JWT/header/body validation. Snapshot mismatch không overwrite; amount range giữ guard của VNPay. Một request nhận business rejection sau snapshot lưu outcome cùng transaction; lỗi hạ tầng rollback không lưu rejection.
- [ ] **3.4:** Thêm matcher POST đúng path `.hasRole("BUYER")`, giữ callback chain và GET; endpoint chỉ có bean khi runtime bật. Test JWT ký thật theo fixture hiện có, sai issuer/audience/role và thiếu/blank subject bị chặn trước Booking. Gọi lại tests trên và GET regressions, expected PASS. Giữ query requestId MQ legacy, HTTP chỉ cần GET không requestId.
- [ ] **3.5:** Viết và chạy PaymentApiResponseTest/PaymentOpenApiTest trước implementation: POST/GET thành công có success=true, message và DTO trong data; lỗi validation/auth/ownership có success=false, data.code và HTTP status đúng. Kiểm OpenAPI tạo được khi runtime/callback bật, đủ bốn operation POST/GET/IPN/Return, bearer chỉ ở buyer APIs, header Idempotency-Key bắt buộc ở POST và schema generic đúng. Regression IPN ACK giữ nguyên key/casing, Return giữ text/html và không ghi state.
- [ ] **3.6:** Implement response helper, annotations, dependency/config và security/advice theo interfaces; cập nhật assertions HTTP hiện có. Chạy `./mvnw.cmd -B -ntp -Ppayment-only-tests '-Dtest=PaymentStartControllerTest,PaymentApiResponseTest,PaymentOpenApiTest,PaymentJwtSecurityTest' test` cùng toàn bộ test GET/callback hiện có; xác nhận method security hoạt động qua proxy thật, docs không làm mở thêm route nghiệp vụ. Cập nhật ví dụ frontend đọc data.paymentUrl và data của GET trong README/runbook.

## Task 4: Repository, inbox/outbox và claim bền

**Files:**
- Create M`repository/ChargeRepository.java`, M`repository/PaymentSagaRepository.java`, M`repository/RefundAttemptRepository.java`, M`repository/EventFinanceSnapshotRepository.java`, M`repository/OutboxMessageRepository.java`.
- Modify M`repository/PaymentAttemptRepository.java`, M`repository/RefundRepository.java`, M`repository/InboxMessageRepository.java`.
- Create M`messaging/inbox/InboxService.java`, M`messaging/outbox/OutboxService.java`, M`messaging/outbox/OutboxClaimStore.java`, M`messaging/outbox/OutboxClaim.java`.
- Modify M`entity/OutboxMessage.java`, M`entity/PaymentSaga.java`, M`entity/Charge.java`, M`entity/Refund.java`, M`entity/RefundAttempt.java`, M`entity/PaymentConfirmation.java` bằng named factory dùng ở tasks sau, giữ constructor JPA protected/Version.
- Modify `pom.xml`.
- Create `src/test/resources/application-runtime-test.yaml`, T`repository/PaymentRuntimeRepositoriesDatabaseTest.java`, T`messaging/OutboxClaimDatabaseTest.java`, T`messaging/InboxOutboxDatabaseTest.java`.

**Interfaces:**
- `InboxService.find(String consumer,UUID id,String envelopeHash): Optional<JsonNode>`, `record(String consumer,UUID id,String hash,JsonNode result): void` MANDATORY; result lưu object kể cả no-op, không nhầm absent với processed-null. Caller lấy message lock trước domain locks.
- `OutboxService.append(Envelope<?> envelope): void` MANDATORY, lưu full envelope và metadata đồng nhất; `findEnvelope(UUID messageId): Envelope<?>`; serializer/validation dùng typed codecs ở boundary, không unchecked decode thành sai payload.
- `OutboxClaim(UUID messageId,int claimAttempt,Instant leaseUntil,String messageType,String payloadJson)`; `OutboxClaimStore.claimDue(int limit,Instant now,Duration lease): List<OutboxClaim>` Transactional; `confirmed(OutboxClaim claim,Instant now): boolean`; `failed(OutboxClaim claim,String code,Instant retryAt): boolean`; `scheduleReplay(UUID messageId,Instant due): void` MANDATORY, cùng ID/envelope. CAS theo messageId+attempts+nextAttemptAt, worker cũ không overwrite claim mới.
- Repository queries: Charge findByProviderAndMerchantAccountAndProviderChargeId; Refund findByChargeId; Saga findByOrderId/lock và findDue; RefundAttempt findUnfinishedByRefundId + lịch sử; Attempt list pending/unknown; EventFinance projection cancellation marker; Inbox lookup completion theo consumer+orderId trong responsePayload để so tuple messageId khác.
- Named factories nhận đủ scalar fields tương ứng schema; không factory tính tiền, không public constructor trống. Example `Charge.recorded(UUID id, PaymentAttempt attempt, ProviderPaymentResult result, Instant recordedAt)`, `PaymentSaga.waitingBooking(UUID orderId,UUID chargeId,UUID commandId,Instant now)`; mọi field NOT NULL được gán, version để null.

- [ ] **4.1:** Viết DB tests `inboxAndEffectRollbackTogether`, `rejectsSameMessageDifferentHash`, `staleRelayCannotMarkNewClaimPublished`, `oneCandidateAndOneRefundPerCharge`, `completionLookupRetainsAcceptedTuple`. Có barrier/concurrent transaction độc lập, không sleep đoán; PostgreSQL thật kiểm unique partial index/JSONB/FK/Version.
- [ ] **4.2:** Thêm profile runtime-tests, suffix `*DatabaseTest`/`*RuntimeIntegrationTest` không chạy ở default; profile mở chúng cùng mapping/read repositories, chỉ giữ exclude StartPaymentRabbitIntegrationTest của profile simulator riêng, không tự skip thiếu DB/broker. Mapping/query mở DatabaseTest nhưng exclude RuntimeIntegrationTest/StartPaymentRabbitIntegrationTest; simulator-tests tiếp tục mở test MQ dev cũ, exclude mapping/read/new DatabaseTest/new RuntimeIntegrationTest. Không loại unit test hiện có. Chạy `.\mvnw.cmd -B -ntp -Ppayment-only-tests,runtime-tests '-Dtest=PaymentRuntimeRepositoriesDatabaseTest,OutboxClaimDatabaseTest,InboxOutboxDatabaseTest,HttpPaymentStartStoreDatabaseTest' test`; expected RED trước implementation, không chấp nhận thiếu môi trường làm RED nghiệp vụ.
- [ ] **4.3:** Implement repository/factory/services bằng JPA/JdbcTemplate hiện có. SKIP LOCKED claim và lease 30s; broker confirm timeout 5s, lease phải dài hơn timeout. Outbox aggregateVersion dương từ version sau flush +1, không lấy JPA version 0 làm wire 0. Không đổi schema; nếu phát hiện bắt buộc migration thì trình diff thiết kế cụ thể trước.
- [ ] **4.4:** Chạy lại các DB tests, expected PASS; snapshot trước/sau rollback chứng minh không có effect/inbox/outbox lẻ. Kiểm hai process claim cùng batch không cùng sở hữu claim, late confirm không đặt publishedAt sai. Không giữ DB transaction qua network.

  Assertion đích: `assertThat(store.confirmed(staleClaim, now)).isFalse(); assertThat(reloaded.getPublishedAt()).isNull();` sau khi claim mới đã thay token; rollback giữ `countAfter == countBefore` cho cả ba nhóm effect/inbox/outbox.

## Task 5: Ghi nhận tiền và chọn candidate saga

**Files:**
- Create M`service/payment/RecordPaymentResultService.java`, M`service/refund/RefundService.java`, M`service/refund/RefundReasonResolver.java`.
- Modify M`config/PaymentRuntimeConfiguration.java`, M`config/VnpayConfiguration.java` chỉ nếu điều kiện bean cần rõ hơn; giữ port VerifiedPaymentHandler và VnpayCallbackService.
- Create T`service/payment/RecordPaymentResultServiceTest.java`, T`service/payment/RecordPaymentResultDatabaseTest.java`, T`service/refund/RefundReasonResolverTest.java`; reuse T`gateway/vnpay/VnpayCallbackTest.java`.

**Interfaces:**
- `RecordPaymentResultService.handle(ProviderPaymentResult result): VerifiedPaymentHandler.Outcome`, Transactional, implementation port dùng chung IPN và query; callback ACK chỉ sau method proxy commit.
- `RefundService.ensure(UUID chargeId,RefundReason reason,Instant now): Refund` MANDATORY, unique charge và full amount; tồn tại giữ reason; không tự đổi saga candidate khi refund charge thừa.
- `RefundReasonResolver.resolve(boolean excessCharge,boolean eventCancelled,boolean terminalIssuanceFailed): RefundReason`, thứ tự DUPLICATE_PAYMENT→EVENT_CANCELLED→TICKET_ISSUANCE_FAILED→LATE_PAYMENT.

- [ ] **5.1:** Tests `successRecordsChargeSagaAndAcceptOutboxAtomically`, `exactDuplicateReturnsAlreadyRecorded`, `sameProviderIdentityDifferentPaidAtIsConflict`, `secondChargeOnSucceededAttemptIsRefundedOnce`, `lateSuccessAfterFailedAttemptIsRecorded`, `staleFailedQueryNeverDowngradesSuccess`. Assert outcome RECORDED/ALREADY_RECORDED, đúng row counts và AcceptPayment payload khớp frozen order/version/amount.

  Assertion đích sau hai actual charge cùng attempt: `assertThat(chargesForOrder).hasSize(2); assertThat(sagasForOrder).hasSize(1); assertThat(refundsForOrder).hasSize(1); assertThat(refundsForOrder.getFirst().getReason()).isEqualTo(RefundReason.DUPLICATE_PAYMENT);`.
- [ ] **5.2:** Run unit `.\mvnw.cmd -B -ntp -Ppayment-only-tests '-Dtest=RecordPaymentResultServiceTest,RefundReasonResolverTest,VnpayCallbackTest' test`, DB `.\mvnw.cmd -B -ntp -Ppayment-only-tests,runtime-tests '-Dtest=RecordPaymentResultDatabaseTest' test`; expected RED trước code.
- [ ] **5.3:** Recheck persisted context qua PaymentContexts/validateAgainst; success cần nonempty chargeId và paidAt hợp lệ. Khóa event/order, kiểm identity lẫn content; transaction chọn candidate và append AcceptPayment hoặc obligation của charge thừa. PENDING/UNKNOWN không tạo charge; không bỏ success thực chỉ vì attempt terminal. FAILED chỉ đóng attempt chưa có success; finishedAt không trước startedAt. Marker cancellation có sẵn chặn phát mới và hội tụ compensation. Callback DB failure không ACK success.
- [ ] **5.4:** Rerun tests expected PASS, thêm concurrent callback hai transaction cùng identity chỉ một charge/saga/outbox; mismatch money/merchant/order không tạo vé hoặc workflow Q-07 mới. IPN và query cùng path phải cho cùng final state.

## Task 6: Quyết định Booking, phát vé và completion

**Files:**
- Create M`service/saga/PaymentSagaService.java`, M`service/saga/CompletionRecord.java`.
- Modify M`validation/MessageInvariantValidator.java` thêm typed validators, không đổi validator start legacy.
- Create T`service/saga/PaymentSagaServiceTest.java`, T`service/saga/PaymentSagaDatabaseTest.java`, T`validation/PaymentSagaMessageValidatorTest.java`.

**Interfaces:**
- `PaymentSagaService.onDecision(Envelope<PaymentDecision> message): void`, `onIssuance(Envelope<IssuanceResult> message): void`, `onCompleted(Envelope<OrderCompleted> message): void`; mỗi entry Transactional.
- Validators `validatePaymentDecision`, `validateIssuanceResult`, `validateOrderCompleted` nhận typed Envelope tương ứng; kiểm messageType/schemaVersion/aggregateId/correlationId/data orderId, DTO cross-field, canonical hash trước inbox.
- `CompletionRecord(UUID orderId,UUID chargeId,UUID issuanceId,int ticketCount)` lưu inbox response với discriminator `ORDER_COMPLETED`; được tìm lại để so business replay khác messageId.

- [ ] **6.1:** Viết tests `acceptedCreatesConfirmationAndIssueCommandOnce`, `lateCreatesOneRefundWithoutTicketCommand`, `ticketCompletedDoesNotCompleteSaga`, `bookingCompletedChecksCountAndConfirmation`, `differentIssuanceUnderNewMessageIdIsConflict`, `completionAfterCompensationCannotResurrectSaga`, `earlyValidMessageIsRetryable`. Assert WAITING_BOOKING→WAITING_TICKETS→COMPLETED đúng thứ tự, sum quantity đúng expected ticketCount; FAILED dứt điểm tạo refund và BookingIssuanceResult FAILED cùng commit.

  Assertion đích: `assertThat(sagaAfterTicketCompleted.getState()).isEqualTo(PaymentSagaState.WAITING_TICKETS); assertThat(sagaAfterBookingCompleted.getState()).isEqualTo(PaymentSagaState.COMPLETED);` và late completion sau compensation giữ `PaymentSagaState.COMPENSATING`/`COMPENSATED` như trước message.
- [ ] **6.2:** Run `.\mvnw.cmd -B -ntp -Ppayment-only-tests '-Dtest=PaymentSagaServiceTest,PaymentSagaMessageValidatorTest' test` và DB `-Ppayment-only-tests,runtime-tests '-Dtest=PaymentSagaDatabaseTest' test`; expected RED.
- [ ] **6.3:** Implement state transitions theo bảng spec, reuse Inbox/Outbox/Refund/locks; mỗi command step có UUID persist cùng state. ACCEPTED chỉ khớp candidate; DUPLICATE khác acceptedChargeId không có bằng chứng cục bộ phải giữ state và báo conflict theo spec. Cancellation marker chặn IssueTickets. Ticket COMPLETED validate + inbox no-op; thiếu dependency retry, conflict dừng/parking, terminal compensation không hồi sinh.
- [ ] **6.4:** Rerun expected PASS. Gửi cùng business result với messageId mới và messageId cũ để kiểm cả semantic dedupe lẫn envelope dedupe; không tạo response outbox mới trên replay khớp. Test crash rollback trước commit giữ trạng thái bước trước.

## Task 7: Relay, consumer, retry và saga recovery

**Files:**
- Create M`messaging/outbox/OutboxRelay.java`, M`messaging/ConfirmedPublisher.java`, M`messaging/PaymentMessageConsumers.java`, M`messaging/MessageRetryRouter.java`, M`messaging/PaymentDeliveryException.java`.
- Create M`service/saga/SagaRecoveryService.java`, M`worker/PaymentMessagingWorker.java`, M`config/PaymentMessagingProperties.java`, M`config/PaymentRuntimeMessagingConfiguration.java`.
- Modify M`messaging/PaymentRabbitErrorHandler.java` nếu cần phân loại phù hợp; không log payload.
- Create `docs/runtime/payment-runtime-topology.example.json`, `src/test/resources/runtime/rabbitmq-definitions.json`, T`messaging/PaymentMessagingRuntimeIntegrationTest.java`, T`messaging/OutboxRelayTest.java`, T`service/saga/SagaRecoveryServiceTest.java`.

**Interfaces:**
- `ConfirmedPublisher.publish(String exchange,String routingKey,byte[] body,UUID messageId,Map<String,Object> headers): void`; return chỉ khi confirm ACK và không basic.return, throw typed error nếu timeout/NACK/unroutable. Timeout 5s.
- `OutboxRelay.runOnce(): int` orchestration claim→publish→CAS result, không Transactional; `SagaRecoveryService.scheduleDue(Instant now,int limit): int` transaction chỉ lập lịch replay cùng current commandId/envelope.
- `PaymentMessageConsumers.onDecision(Message,Channel)`, `.onIssuance(Message,Channel)`, `.onCompleted(Message,Channel)`; raw UTF-8 body → ContractCodec strict → validator → saga service → manual ACK sau proxy return/commit.
- `MessageRetryRouter.retryOrPark(Message original,String sourceQueue,PaymentDeliveryException failure): void` republish qua ConfirmedPublisher; header x-payment-retry-attempt tách khỏi envelope; message identity/envelope giữ nguyên. Typed failure phân loại TRANSIENT/DEPENDENCY/CONFLICT/POISON.
- Queue business: payment.decision.q, payment.ticket-issuance-failed.q, payment.order-completed.q. Exchange từ spring.rabbitmq.template.exchange. Retry route riêng từng consumer, 5s/30s/300s, cuối parking; DLX từ retry quay đúng consumer, không phát lại topic tới consumer khác.

- [ ] **7.1:** Tests `acksOnlyAfterCommit`, `republishFailureKeepsOriginalUnacked`, `unroutableConfirmDoesNotMarkPublished`, `crashAfterConfirmRedeliversWithoutDuplicateEffect`, `retryReturnsToOriginalConsumer`, `staleConfirmCannotWinNewLease`, `recoveryReusesPersistedCommand`. RED với unit relay/recovery trước, integration phải broker thật riêng.

  Assertion đích: `assertThat(commitObservedBeforeAck.get()).isTrue(); assertThat(redeliveredMessageId).isEqualTo(originalMessageId); assertThat(otherConsumerDeliveryCount.get()).isZero();` khi retry về queue gốc; transaction rollback phải không có ACK.
- [ ] **7.2:** Implement factories/publisher/consumer/worker với properties validation. Durable messages/queues và topology mẫu có binding/TTL/DLQ; app runtime không RabbitAdmin trên vhost chung. ACL mẫu xác định producer/consumer service, không coi payload source là auth. Lỗi poison park ngay; transient retry hữu hạn; outage diện rộng pause/backoff listener, không spin requeue. Deserialization fail cần giữ raw bytes an toàn cho parking, không stringify/log payload.
- [ ] **7.3:** Profile runtime-tests dùng definitions riêng và test helper provision/cleanup chỉ vhost test đã xác nhận; không chạy unbind test vào vhost chung. `.\mvnw.cmd -B -ntp -Ppayment-only-tests '-Dtest=OutboxRelayTest,SagaRecoveryServiceTest' test`; `.\mvnw.cmd -B -ntp -Ppayment-only-tests,runtime-tests '-Dtest=PaymentMessagingRuntimeIntegrationTest' test`. Expected PASS và log assertions không chứa snapshot/secret.
- [ ] **7.4:** Kiểm restart worker/read DB không dựa RAM; nextAttemptAt/backoff bền, candidate command không đổi. Completion chỉ qua Booking, timeout không tự FAILED/refund. Record lag/pending/errorCode qua logger an toàn và Actuator health contributor nội bộ nếu cần, không mở actuator public.

## Task 8: Query giao dịch chưa rõ và refund worker

**Files:**
- Create M`service/common/PaymentOperationLocks.java`, M`service/payment/PaymentReconciliationService.java`, M`service/refund/RefundExecutionService.java`, M`service/refund/RefundAttemptStore.java`, M`worker/PaymentSettlementWorker.java`.
- Modify M`repository/PaymentAttemptRepository.java`, M`repository/RefundAttemptRepository.java`, M`repository/RefundRepository.java`, M`config/PaymentRuntimeConfiguration.java` theo interfaces.
- Create T`service/payment/PaymentReconciliationServiceTest.java`, T`service/refund/RefundExecutionServiceTest.java`, T`service/refund/RefundExecutionDatabaseTest.java`, T`service/common/PaymentOperationLocksDatabaseTest.java`.

**Interfaces:**
- `PaymentOperationLocks.tryAcquire(String operation,UUID id): Optional<Lease>`; nested Lease implements AutoCloseable sở hữu connection dedicated/session advisory lock, release đúng connection trong finally; tuyệt đối không rò lock về pool. Có cleanup nếu connection mất.
- `PaymentReconciliationService.reconcile(UUID attemptId): void`; query PaymentContext persist ngoài transaction, same attemptId; gọi RecordPaymentResultService để apply và không hạ success bởi kết quả cũ.
- `RefundAttemptStore.claim(UUID refundId,Instant now): Optional<RefundContext>` transaction claim PROCESSING + persist attempt trước network; unfinished trả context cũ cho query. `.apply(UUID refundId,UUID attemptId,ProviderRefundResult result,Instant now): void` transaction CAS, kiểm requestId.
- `RefundExecutionService.execute(UUID refundId): void` giữ operation lease, claim/read context, gọi refund chỉ cho attempt vừa claim mới; attempt unfinished từ trước chỉ queryRefund. requestId=RefundAttempt.id, requestKey=id.toString(), requestedAt=startedAt, operator="payment-service"; full original charge amount/currency/providerChargeId.
- Worker poll 5s/batch20, bounded concurrency; query pending/unknown và refund tới hạn suy từ lịch sử attempt; backoff trần300s, cảnh báo15m. Refund disabled không tạo/gửi attempt mới, vẫn giữ nghĩa vụ.

- [ ] **8.1:** Tests `unknownRefundNeverSendsSecondMoneyRequest`, `restartedWorkerQueriesSameIdentityAndRequestedAt`, `successCompletesOnlyMatchingRefundAndCandidateSaga`, `definitiveFailureRetainsObligationForRetry`, `unavailableQueryRefundLeavesProcessing`, `concurrentWorkersDoNotSendTwice`. Assert một unfinished attempt, giữ request key/time qua restart, không đổi charge, không COMPENSATED khi chưa đủ bằng chứng/outbox.

  Assertion đích sau timeout và restart: `verify(gateway, times(1)).refund(originalContext); verify(gateway).queryRefund(originalContext); assertThat(refund.getState()).isEqualTo(RefundState.PROCESSING); assertThat(unfinishedAttempts).hasSize(1);`.
- [ ] **8.2:** Run unit `'-Dtest=PaymentReconciliationServiceTest,RefundExecutionServiceTest'` và DB `-Ppayment-only-tests,runtime-tests '-Dtest=RefundExecutionDatabaseTest,PaymentOperationLocksDatabaseTest'` ở Maven test; expected RED trước implementation.
- [ ] **8.3:** Implement claim-network-apply, không Transactional trên toàn execute/reconcile. SQL unique chặn unfinished; side effect provider phải dùng identity cũ khi mất connection/crash. Crash sau claim trước network là UNKNOWN an toàn, query trước; vì adapter refund lookup chưa hỗ trợ, giữ PROCESSING/cảnh báo chứ không bịa chưa gửi để gửi lại. FAILED definitive mới đóng attempt và cho retry trên cùng refund. REFUND_LOOKUP_UNAVAILABLE không làm mất nghĩa vụ.
- [ ] **8.4:** Rerun expected PASS, kiểm release operation lock sau exception/restart và CAS khi callback đua query. Fake provider chỉ chứng minh runtime; giữ cờ thật false và ghi giới hạn sandbox trong docs.

## Task 9: Kiểm luồng hoàn chỉnh và hướng dẫn tích hợp

**Files:**
- Create T`runtime/PaymentPurchaseRuntimeIntegrationTest.java`, T`runtime/PaymentParticipantFixtures.java`, `docs/runtime/payment-purchase-runtime.md`, `docs/runtime/payment.env.example`.
- Modify `README.md`, `docs/vnpay-sandbox.md`, `pom.xml`/runtime-test config nếu cần đăng ký test mới (không nới gate).

**Interfaces:**
- PaymentParticipantFixtures là bộ dữ liệu và helper test gửi/nhận ở ranh giới Payment: trả HTTP body/status/token định trước, thu command Payment gửi, inject response envelope/duplicate định trước. Không có DB/order store/giữ chỗ/freeze logic, decision engine, outbox hoặc phát vé của Booking/Ticket.
- Payment runtime integration test gọi POST có JWT ký thật → nhận fixture snapshot qua HTTP → URL → signed IPN → quan sát AcceptPayment gửi ra → inject PaymentDecision → quan sát IssueTickets → inject OrderCompleted → kiểm DB/GET của Payment. Không mô phỏng nghiệp vụ nội bộ giữa Booking và Ticket, không gọi test này là platform E2E.

- [ ] **9.1:** Viết `paymentCompletesWithOneAttemptChargeConfirmationAndIssueCommandIdentity`, `restartAtPaymentCommitBoundariesConverges`, `lostHttpResponseReplaysSameUrl`, `ticketFailureMessageCompensatesExactlyOnce`, `unknownRefundRemainsVisible`, `lateCompletionCannotReviveCompensation`. Failpoint chỉ tại Payment commit-before-publish, confirm-before-publishedAt và trước khi nhận response định trước; kiểm PostgreSQL/broker thật của Payment.

  Assertion đích: `assertThat(saga.getState()).isEqualTo(PaymentSagaState.COMPLETED); assertThat(attemptCount).isEqualTo(1); assertThat(chargeCount).isEqualTo(1); assertThat(confirmationCount).isEqualTo(1); assertThat(issueCommandIds.stream().distinct().count()).isEqualTo(1);` sau duplicate/restart. Có thể publish lặp cùng commandId; không suy số vé thực tế của Ticket từ kiểm này.
- [ ] **9.2:** Chạy `.\mvnw.cmd -B -ntp -Ppayment-only-tests,runtime-tests '-Dtest=PaymentPurchaseRuntimeIntegrationTest' test`; RED trước harness hoàn chỉnh, PASS sau. Nếu thiếu DB/broker report NOT_RUN trong bàn giao và không claim runtime E2E.
- [ ] **9.3:** Viết hướng dẫn chạy trực tiếp Payment, POST/curl và GET poll, biến runtime/Booking credential/JWT/VNPay, topology cần provision, replay parking, pending/UNKNOWN và refund đối soát. Nêu rõ route Gateway đang trỏ Booking cần đổi khi tích hợp ngoài scope; người dùng kiểm trực tiếp Payment trước. Không sửa module khác, không deploy hoặc bật tiền thật. Fixture test không phát hành thành công cụ standalone.
- [ ] **9.4:** Chạy `.\mvnw.cmd -B -ntp -Ppayment-only-tests verify` và `.\mvnw.cmd -B -ntp -Ppayment-only-tests,runtime-tests verify` với Java21/UTC, DB/broker riêng. Expected compile/tests/Spotless/SpotBugs PASS trong phạm vi Payment; default whole-module verify được báo riêng với baseline test Booking ngoài scope. Chỉ format file thay đổi; review ownership, atomicity, concurrency và marker cancellation trước kết luận.
- [ ] **9.5:** Đối chiếu spec/plan bằng diff, chạy review cuối theo workflow execution đã chọn. Báo số test thực, PASS/FAIL/NOT_RUN và giới hạn: Payment với participant giả không là Booking/Ticket/VNPay/platform E2E thật. Chỉ commit hunks thuộc task khi tách được; giữ staged user changes và không push tự động.

## Tự rà soát plan

- Spec §§1–6 → Tasks1–3 (scope, profile, HTTP, freeze, ownership, replay); §§7–8 → Tasks5/6/8 (charge, saga, obligation, refund limits); §§9–10 → Tasks3/4/7/8 (locks, persistence, delivery, recovery); §§11–12 → Tasks2/3/7/9 (config/security/integration/evidence).
- Review Focus có assertions cụ thể trong Tasks3/5/6/7/9; các test DB/broker được tách khỏi unit nhưng bắt buộc trong profile runtime-tests, không dùng mocks để claim atomicity.
- Types/interfaces được định nghĩa ở task sản xuất trước khi task sau dùng. Outbox và RefundService có trước consumer saga; PaymentContexts/locks có trước worker. Test chỉ dùng dữ liệu/response gửi vào Payment và quan sát request/message gửi ra; không có implementation Booking/Ticket.
- Theo xác nhận phạm vi mới của người dùng: chỉ Payment gửi/nhận và xử lý trạng thái của mình. Bốn test công cụ Booking cũ giữ nguyên ngoài phạm vi. Các chi tiết plan còn lại: route POST ở Payment, HTTP dedupe, request/response/auth client, MQ completion và compensation cục bộ.

Sau khi người dùng duyệt plan, thực hiện bằng superpowers:executing-plans trực tiếp trong chat/nhánh đã chọn. Không thêm một vòng hỏi lại nhánh hoặc cách giao tiếp đã chốt.
