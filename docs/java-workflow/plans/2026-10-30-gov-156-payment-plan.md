# Payment GOV-156/GOV-157 — plan triển khai theo Shop App

- Ngày: 30/10/2026. Pha: **WRITING-PLANS — APPROVED**; gate **EXECUTING-PLANS — APPROVED; phần Payment đã triển khai và kiểm chứng**.
- Thiết kế nguồn: [spec v2](../../superpowers/specs/2026-10-30-gov-156-payment-flow-design.md).
- Danh sách đề xuất: **SA-PAY-v2.1: C01–C20, M01–M54, E01–E06**. C01–C19/M01–M53/E01–E05 lấy từ spec; delta ở mục 4.
- Nguồn xác nhận: người dùng trả lời **“oke triển khai đi”** sau câu hỏi xác nhận rõ C01–C20, M01–M54, E01–E06 cho cả thiết kế, kế hoạch và thực thi. Dùng cùng xác nhận cho đúng danh sách SA-PAY-v2.1; chỉ trình lại nếu có delta.
- Nhánh hiện tại: `codex/gov-156-payment-flow`. Giữ toàn bộ Java/test work-in-progress đã có; không stage/commit/push hoặc sửa service khác từ việc viết plan.

## 1. Kết quả và ranh giới

Chỉ thực hiện năm subtask Payment: API tạo URL/QR, webhook, AcceptPayment, IssueTickets, thông báo Booking và kết thúc Saga. Mọi thay đổi Java/test/config chỉ trong `services/payment-service`. Tài liệu thiết kế/plan là ngoại lệ về nơi lưu; không đổi Config Server, Gateway, Booking, Ticket, Keycloak hoặc schema toàn hệ thống.

Luồng cố định: FE POST Payment → một POST lấy snapshot ngoài TX Payment → snapshot+attempt commit → trả URL. Webhook commit charge+attempt SUCCEEDED+WAITING_BOOKING+outbox rồi ACK. Chỉ Booking ACCEPTED mới lưu confirmation+WAITING_TICKETS+IssueTickets. Ticket success tới Payment, Payment lưu checkpoint+gửi Booking; chỉ OrderCompleted từ Booking khớp dữ liệu mới đóng Saga. Broker confirm/ACK không tiến Saga.

Payment dùng toàn bộ PurchaseSnapshot hiện có, kiểm order/owner/deadline/hash, lấy amount từ total; không nhận giá FE, không áp voucher, không tự gia hạn hoặc tính lại snapshot khi retry. Không start-requested, freeze API riêng hoặc GET ghi dữ liệu.

## 2. Nền đã kiểm tra trước khi sửa

- Maven 3.9.16; dự án yêu cầu Java 21. PATH mặc định đang là Java 17; đã tìm được Java 21 tại `C:/Users/WEOHWOEH/.jdks/ms-21.0.12.1` và chỉ đặt JAVA_HOME trong process Maven cần chạy, không đổi cấu hình máy.
- Đã chạy `mvn -q -DskipTests compile` bằng Java 21: exit 0.
- Đã chạy `mvn -q test` bằng Java 21: exit 0; báo cáo Surefire **120 tests, 0 failure, 0 error, 0 skipped**. Đây là baseline code có sẵn, không phải tính năng mới.
- POM mặc định loại EntityMappingTest/PaymentReadRepositoriesTest; 120 test không phải bằng chứng PostgreSQL/broker thật.
- Docker CLI có, daemon Docker Desktop chưa chạy. Chưa có bằng chứng môi trường DB/broker test sẵn sàng; không sửa dữ liệu/service hiện có để tạo điều kiện test.
- Source xác nhận bằng CodeGraph: PaymentSagaServiceImpl còn skeleton; RabbitMqConfig mới có converter/error-handler; PaymentAttemptRepository đã có lookup key và unfinished check. Tái sử dụng chúng, không sinh đủ tầng cho hình thức.

## 3. Danh sách class và phương thức thực thi đề xuất

Danh sách dưới đây được chép nguyên từ spec để duyệt theo đúng pha. Đường dẫn tính theo package gốc Payment như spec. Không có quyền tạo helper/type/callback/constructor ngoài danh sách. Delta ở mục 4 là một phần bắt buộc của danh sách cuối cùng, thay thế annotation được nêu tương ứng ở bảng gốc.

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

## 4. Delta khi đối chiếu baseline — PENDING

| ID | Class/file | NEW hoặc EXISTING | Vai trò, field/annotation/API sinh | Mẫu hoặc ngoại lệ |
| --- | --- | --- | --- | --- |
| C20 | src/test/java/com/flashticket/payment_service/config/PaymentReadRepositoryTestConfiguration.java | EXISTING | Thêm đúng bean M54 để smoke test có transaction manager giả khi DataSource/JPA bị loại. Không constructor/helper/class mới | E06: tái sử dụng test configuration đã có; Mockito mock như các bean repository hiện tại |

| ID | Class | Constructor/method đầy đủ | NEW hoặc REUSE | Cách viết và lý do cần |
| --- | --- | --- | --- | --- |
| M54 | PaymentReadRepositoryTestConfiguration | PlatformTransactionManager paymentTransactionManager() | NEW | Package-private @Bean, return Mockito.mock(PlatformTransactionManager.class). Chỉ test; không dùng cho integration test hoặc production |

- **C09/M19/M20 delta annotation:** PaymentWorker thêm `@ConditionalOnProperty(prefix="payment.worker", name="enabled", havingValue="true", matchIfMissing=true)`. Hai @Scheduled thêm `initialDelayString="${payment.worker.initial-delay:0}"`. Smoke/client test đặt worker.enabled=false; integration test bật worker nhưng initial-delay lớn để gọi method trực tiếp mà không chạy race với scheduler. Không thêm method scheduler/helper mới.
- **C17 delta test injection:** ngoài mock OAuth2AuthorizedClientManager đã kê, thêm `@MockitoBean PlatformTransactionManager` để @Import PaymentRuntimeConfig không yêu cầu DB ở client slice. Không thêm test config class hoặc bean method thứ hai.
- **E02/E05/E06 delta dependency/config:** POM Payment cần OAuth2 client và RestClient/test hỗ trợ theo BOM Spring Boot hiện có; xác nhận module/starter chính xác bằng dependency resolution, không ép version riêng. Test resource khai báo registration booking giả, token-uri giả và timeout; không đưa credential thật vào source. Cấu hình confirm=CORRELATED, returns=true, mandatory=true chỉ tại Payment.
- **E06 — bảo vệ test baseline:** smoke test đang chủ động exclude DataSource/JPA/Rabbit auto-config. Dùng C20/M54 + worker.enabled=false và OAuth2 registration giả trong test resource, không bỏ smoke test hoặc bật broker thật cho toàn bộ test suite.

Không phát sinh method/application type khác ngoài C01–C20/M01–M54; nếu cần thay chữ ký/framework callback để compile hoặc test, phải trình delta trước khi tạo, không lách bằng anonymous class hoặc lambda chứa khai báo mới.

## 5. Các bước triển khai sau xác nhận

### P1. Hợp đồng, client và cấu hình Payment

- Item: C05–C07/C10/C11/C13/C14/C17/C20; M06/M21–M24/M29/M37/M38/M40–M43/M54; E02/E04/E05/E06.
- File chính: `client/BookingPurchaseClient.java`, `dto/request/PreparePurchaseSnapshotRequest.java`, `config/PaymentRuntimeConfig.java`, `config/RabbitMqConfig.java`, `dto/common/ContractCodec.java`, `validation/MessageInvariantValidator.java`, `dto/message/BookingIssuanceResult.java`, enum state/error và hai resource config Payment/test.
- Giữ DTO record/MapStruct cũ. DTO request mới là class Lombok; client gửi body actor đã xác thực, URL/path từ @Value, token client_credentials do Spring quản lý. Chỉ một POST, strict read/validate response, ánh xạ lỗi 404/409/5xx/timeout. Không log raw credential hoặc payload thanh toán.
- BookingIssuanceResult hỗ trợ COMPLETED+ticketCount và FAILED; failureCode conditional theo state. Ba typed validator kiểm envelope tương ứng, không tạo message dispatcher framework.
- Chỉ khai báo ba queue nhận phản hồi của Payment; không tạo queue hoặc sửa binding của Booking/Ticket. Mở rộng test config C20 và tắt worker trong smoke test để test suite hiện tại vẫn độc lập hạ tầng.
- Xác minh: client slice M40–M43 + contract/mapper test hiện có; POST đúng path/order/actor, token giả đúng service identity, invalid response/lỗi mạng bị từ chối, không chạm DB.

### P2. POST tạo thanh toán từ snapshot

- Item: C01/C02/C19; M01–M03/M05; E01/E04.
- File chính: `controller/PaymentController.java`, `service/PaymentService.java`, `service/CinemaVNPayServiceTest.java` ở test.
- Controller nhận UUID Idempotency-Key và subject JWT. PaymentService có propagation NEVER; gọi client trước, rồi template REQUIRES_NEW gọi savePaymentAttempt. Snapshot lần đầu và attempt/URL commit cùng nhau; retry same key trả cùng attempt/URL, không ký lại theo IP mới.
- Key scope `(orderId,requestKey)`; cùng key khác hash conflict; key khác gặp unfinished attempt trả PAYMENT_IN_PROGRESS; FAILED chỉ mở attempt mới với key mới và snapshot còn payable; UNKNOWN không mở mới. Snapshot conflict/ownership/deadline fail trước ghi. Unique race thoát transaction lỗi rồi đọc/retry trong transaction mới hữu hạn.
- Không thay GET lịch sử thành receipt của POST mới và không đổi return/IPN route. C19 chỉ cập nhật constructor/signature trong test đang có; không ghi đè việc tách controller/service của người dùng.
- Xác minh: compile + baseline suite; M45/M46 khi môi trường integration sẵn sàng. Kiểm method client chạy khi không có transaction Payment, response chỉ về sau commit, hai request không sinh hai unfinished attempt.

### P3. Webhook và AcceptPayment

- Item: C02/C03/C04/C12/C15/C16; M04/M07/M13–M18/M25/M27/M28/M30–M36; E01/E03.
- File chính: PaymentService, PaymentSagaService/Impl, MessagingService/Impl, entity factory và repository đã kê.
- Verify signature/merchant/attempt/amount/currency/provider identity trước ghi. Callback thành công ghi charge+SUCCEEDED+WAITING_BOOKING+AcceptPayment outbox cùng TX; commit lỗi phải thoát ra ngoài, không ACK thành công.
- Candidate một order không đổi bởi charge thừa; charge identity cũ dedupe, charge thừa liên kết đúng ghi nghĩa vụ refund unique(charge). Callback muộn vẫn lưu tiền thật để xử lý nhánh hoàn, không bị bỏ chỉ vì order hết hạn/hủy.
- recordInbox so hash trước dedupe; enqueue chỉ lưu. Claim outbox bằng khóa ngắn/lease/attempt token; markPublished và reschedule phải so token để worker cũ không ghi đè worker mới. Không publish khi giữ transaction nghiệp vụ.
- Xác minh: M47 + baseline VNPay tests; fault ở bất kỳ ghi nào rollback charge/attempt/Saga/outbox; duplicate callback không tăng tác động tài chính.

### P4. Booking decision, phát vé và completion

- Item: C03/C08/C11/C12/C13; M08–M10/M14/M15/M26/M29/M37–M39; E03/E04.
- File chính: PaymentSagaService/Impl, PaymentMessageListener, MessageInvariantValidator, BookingIssuanceResult/State.
- ACCEPTED → confirmation + WAITING_TICKETS + IssueTickets cùng transaction. LATE/CANCELLED/DUPLICATE → nghĩa vụ hoàn theo nguyên nhân, không phát vé.
- IssuanceResult COMPLETED được Payment kiểm đầy đủ, lưu checkpoint và Booking success outbox cùng commit; Saga vẫn WAITING_TICKETS. Chỉ OrderCompleted khớp confirmation/issuance/count mới COMPLETED. FAILED phải terminal theo hợp đồng; timeout/404/PENDING không tự refund.
- Listener codec→validator→service; service commit rồi mới basicAck. Sai schema/hash/correlation không ACK như một xử lý thành công. Dùng error handler hiện có và ghi mã lỗi đã lọc.
- Xác minh: M48–M50/M52/M53; Ticket success/broker confirm chưa đủ để kết thúc Saga; replay không tăng hiệu ứng, compensation/cancellation marker không bị hồi sinh.

### P5. Relay và phục hồi

- Item: C03/C04/C09/C15; M11/M12/M16–M20/M30–M34; E01/E03/E06.
- File chính: PaymentWorker, hai service/Impl có sẵn và query đã kê.
- Worker claim→commit→publish persistent/mandatory→đợi confirm/return ngoài TX→markPublished đúng claim. Nack/timeout/no-route lưu lịch retry, không xóa công việc khi đủ số lần thử.
- Recovery xét Saga đến hạn: WAITING_BOOKING phát lại logical AcceptPayment; WAITING_TICKETS chưa có kết quả thì IssueTickets, đã có success checkpoint thì thông báo Booking. Envelope recovery mới giữ business identity và causation; broker redelivery giữ ID/payload gốc.
- Giữ payload/checkpoint khi Saga còn chờ. Không dùng ACK/confirm hoặc probe riêng để đóng Saga; chỉ response nghiệp vụ hợp lệ mới tiến state. Không thêm enum Saga hoặc orchestrator khác.
- Xác minh: M51/M53, race token claim, crash sau commit trước publish/ACK, mất Ticket result/Booking completion; khởi động lại không mất bước thông báo Booking.

### P6. Kiểm chứng và báo cáo đúng phạm vi

- Item: C17–C20; M40–M54; tất cả E đã duyệt.
- Thêm `src/test/resources/application-payment-flow-test.yaml`: bật DB/broker thực cho integration test, tắt Config Server/Eureka, registration OAuth2 giả, worker delay dài. Không dùng Hibernate update/create để thay schema đã chốt, không dùng DB production.
- Test mặc định vẫn chạy độc lập DB/broker. Chạy riêng PaymentFlowIntegrationTest khi có PostgreSQL/RabbitMQ test và schema Payment phù hợp; không trộn báo cáo stub với E2E ba service.
- Cần fixture SQL Payment thì chỉ đặt trong `services/payment-service/src/test/resources`, từ schema/fixture đã có; chưa cho phép DDL/migration chung. Không tạo test Java/helper ngoài manifest.
- Ghi kết quả thực tế vào plan: compile/test suite, client slice, transaction/concurrency/broker tests, phần chưa chạy và lý do. Không đánh dấu runtime refund/cancellation toàn bộ đã hoàn thiện từ việc ghi nghĩa vụ/đọc marker trong năm subtask.

## 6. Lệnh xác minh dự kiến

Chạy từ `services/payment-service` bằng Java 21, giữ cấu hình máy nguyên trạng:

```powershell
$env:JAVA_HOME = 'C:\Users\WEOHWOEH\.jdks\ms-21.0.12.1'
mvn -q -DskipTests compile
mvn -q '-Dtest=BookingPurchaseClientTest' test
mvn -q test
mvn -q '-Dtest=PaymentFlowIntegrationTest' test
mvn -q spotless:check
```

Integration test lấy endpoint/credential test qua môi trường; không in secret ra output. `PaymentFlowIntegrationTest` không được vô tình chạy cùng bộ test mặc định không có DB/broker: cấu hình Surefire mặc định loại đúng class này, lệnh `-Dtest=PaymentFlowIntegrationTest` chạy tường minh. Không bỏ test bằng cách giả trả pass nếu hạ tầng chưa sẵn sàng. spotless:check là kiểm tra; nếu cần format chỉ các file Payment thuộc task, không apply toàn repo và ghi đè WIP.

## 7. Xác nhận và kết quả thực tế — 30/10/2026

- Thiết kế, WRITING-PLANS và EXECUTING-PLANS: **APPROVED** cho SA-PAY-v2.1, nguồn “oke triển khai đi” ngay sau câu hỏi duyệt đúng danh sách cho ba pha. Không có class/helper/overload mới ngoài manifest. Các API sinh theo Lombok và override được giữ đúng nhóm đã duyệt.
- **P1–P6 đã thực hiện trong Payment.** Có thay đổi thân method/test đang có để thích nghi header/constructor: PaymentExceptionHandler.unexpected trả 400 khi thiếu Idempotency-Key; PaymentJwtSecurityTest thêm mock client và kiểm header; PaymentQueryServiceTest cập nhật constructor. Không thêm method/type cho các chỉnh sửa tương thích này.
- Client contract test: **3/3 đạt**, gồm service identity, strict snapshot response và lỗi HTTP/timeout; dùng HTTP/authorized-client giả trong test.
- Bộ test mặc định Payment: **123/123 đạt**, 0 failure/error/skipped, tăng từ baseline 120. `mvn -q test` vẫn không cần PostgreSQL/RabbitMQ; integration class được loại đúng mặc định.
- PostgreSQL 16.15 + RabbitMQ 4.4.0-management trong hai container test riêng: **PaymentFlowIntegrationTest 9/9 đạt**, 0 failure/error/skipped. Bằng chứng có unique race thực, rollback toàn bộ webhook khi outbox bị DB từ chối, callback lặp/thu thừa, acceptance trước IssueTickets, checkpoint Ticket success, replay thông báo Booking, Broker confirm/no-route, token claim cũ, expiry, cancellation marker, terminal FAILED và thứ tự commit trước ACK.
- Sửa lỗi phát hiện bởi test thật: timestamp PostgreSQL làm tròn microsecond; response tạo/retry lấy expiresAt nguyên bản từ PurchaseSnapshot để giữ deadline chính xác. URL retry không ký lại; callback FAILED đã terminal không bị non-success tới trễ mở lại UNKNOWN.
- Kiểm chất lượng: `mvn -q spotless:check` **đạt toàn module**; `mvn -q -DskipTests verify` **đạt**, gồm package, Spotless và SpotBugs. Default suite và integration suite đã chạy riêng trước verify, không dùng skipTests làm bằng chứng test.
- Chỉ format các file Java thuộc task; các phần WIP Finance/controller/repository khác giữ nguyên. Không sửa Booking/Ticket/Config Server/Gateway hoặc DDL chung. Không stage/commit/push.

Log/report cục bộ (ignored): `services/payment-service/target/gov156-default-test.log`, `gov156-client-test.log`, `gov156-integration-test.log`, `gov156-verify.log`, `target/surefire-reports`. Các ca test kiểm service Payment với DB/broker thật và đối tác mô phỏng, **không** chứng minh E2E Booking/Ticket hoặc giao dịch VNPay thật.

## 8. Cấu hình để kết nối môi trường thật

- `BOOKING_SERVICE_URL`: base URL Booking; property `payment.booking.snapshot-path` mặc định `/internal/v1/orders/{orderId}/purchase-snapshot`, thay bằng hợp đồng peer khi tích hợp.
- `PAYMENT_SERVICE_CLIENT_ID` (mặc định payment-service), `PAYMENT_SERVICE_CLIENT_SECRET`, `PAYMENT_SERVICE_TOKEN_URI`: registration client_credentials Payment gọi Booking. Token endpoint, credential và quyền service phải được cấp ở môi trường; không dùng token buyer thay credential service. Chưa gọi Keycloak thật trong kiểm thử.
- Payment nhận ba messageType: `booking.payment-decision.v1`, `ticket.issuance-result.v1`, `booking.order-completed.v1`; gửi `booking.accept-payment.v1`, `ticket.issue.v1`, `booking.issuance-result.v1`. Queue đích do service đích sở hữu; Payment chỉ khai báo queue nhận của mình.
- URL VNPay được ký bằng adapter hiện có; không thêm endpoint QR độc lập hoặc tích hợp provider mới. Return page không cập nhật tiền.
- Ghi nghĩa vụ refund và đọc cancellation marker đã có trong nhánh Payment; không mở rộng task thành xây toàn bộ refund execution/finance engine còn scaffold.

Harness đã dùng hai container riêng `codex-payment-gov156-pg` và `codex-payment-gov156-rabbit`, bind localhost 25432/25672, database payment_db. Schema chỉ nạp canonical `30-payment.sql` + `99-table-prefix.sql` vào database test này; không sửa source DDL. Hai container được xóa sau kiểm chứng, không giữ test data như môi trường chạy ứng dụng. Muốn chạy lại integration phải chuẩn bị DB/broker test tương đương; các biến `PAYMENT_TEST_DB_*`/`PAYMENT_TEST_RABBIT_*` cho phép đổi endpoint test, không dùng database chứa dữ liệu nghiệp vụ thật.

## 9. Chạy và ghi lại response thực tế theo yêu cầu người dùng

Ngày 30/10/2026, đã chạy JAR Payment qua HTTP cùng PostgreSQL/RabbitMQ thật trong container test riêng: **34/34 kịch bản đạt**; chạy lại **9 integration + 3 client test đều đạt**. Booking/OAuth2/JWKS/Ticket và webhook provider là fixture; không có giao dịch tiền thật. Không thay đổi Java để lấy bằng chứng.

[Báo cáo request/response, message và trạng thái DB](../reports/2026-10-30-payment-scenario-results.md) và [JSON gốc](../reports/2026-10-30-payment-scenario-results.json) ghi kết quả từng ca cùng SHA-256 JAR đã chạy. Script tái lập nằm trong `services/payment-service/src/test/scenarios/run-payment-scenarios.mjs`. Lưu ý thực nhận: HTTP 200 của IPN vẫn cần đọc RspCode; GET Payment không trả Saga state; lỗi Booking 409 hết hạn/hủy hiện ánh xạ chung PAYMENT_STATE_INVALID; refund vẫn PENDING. JVM thử nghiệm cần `-Duser.timezone=UTC` vì PostgreSQL test từ chối alias Asia/Saigon của JVM Windows. Các process/container thử nghiệm được dừng/xóa sau khi lưu báo cáo.
