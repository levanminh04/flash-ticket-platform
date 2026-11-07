# Plan — Payment hoàn tiền đầy đủ và kết thúc Saga bù trừ

- Phase: WRITING-PLANS
- Gate: **APPROVED** (WRITING-PLANS) — user duyệt C1–C13, M1–M44, T1–T21 và delta X1–X6 ngày 2026-11-03
- Thiết kế: [2026-11-03-payment-refund-saga-design.md](../specs/2026-11-03-payment-refund-saga-design.md) (BRAINSTORMING APPROVED, D1–D4 đã chốt)
- Hợp đồng: B13 v0.5 §3.1, §5.2; B14 §1, §4, §5; B12 v0.3 `refunds`
- Thư mục làm việc: `services/payment-service`; gốc package `com.flashticket.payment_service` (viết tắt `@/`)

## 1. Mục tiêu và phạm vi

Refund `PENDING` được gửi tới VNPay và kết thúc đúng một lần; saga đi tới `COMPENSATED`; Payment nhận `event.cancelled.v1` và chạy lô hoàn theo từng đơn; admin xem, thử lại, ghi nhận ngoài hệ thống và xem tiến độ lô. Ngoài phạm vi: đối soát/chi trả (`UC-17`–`UC-19`), `event.finance-configured.v1`.

## 2. Delta phát hiện khi đối chiếu source (cần duyệt ở pha này)

| ID | Delta | Lý do |
|---|---|---|
| X1 | C1 `VNPayRefundClient` dùng `@Component @RequiredArgsConstructor @Slf4j` và field `private final`, **không** `@FieldDefaults` như thiết kế ghi | Khớp `@/client/BookingPurchaseClient` của repo đích; `RefundServiceImpl`/`PaymentSagaServiceImpl` cũng viết `private final` |
| X2 | Thêm `T18 VNPayRefundClientTest.void setUp()` (`@BeforeEach`) | Dựng `MockRestServiceServer.bindTo(builder)` như `BookingPurchaseClientTest.setUp`; thiết kế chưa liệt kê |
| X3 | Thêm `T19 RefundFlowIntegrationTest.private UUID seedConfirmedOrder(UUID eventId, PaymentSagaState sagaState)` | Tạo snapshot + attempt + charge + confirmation + saga bằng `JdbcTemplate`, dùng lại ở T2–T11 |
| X4 | Thêm `T20 RefundFlowIntegrationTest.private Envelope<EventCancellation> cancellationEnvelope(UUID eventId, UUID cancellationId, UUID messageId)` | Message `event.cancelled.v1` hợp lệ cho T8–T10 |
| X5 | Thêm `T21 VNPayRefundClientTest.private String signedResponse(Map<String, String> fields, List<String> fieldOrder)` | Ký phản hồi giả đúng thứ tự VNPay cho T13–T15 |
| X6 | `pom.xml`: thêm `<exclude>**/RefundFlowIntegrationTest.java</exclude>` vào surefire mặc định | Test cần PostgreSQL/RabbitMQ thật như `PaymentFlowIntegrationTest`; profile `query-tests`/`mapping-tests` vẫn chạy nó. Không phải class/method |

Không có delta nào khác: C1–C13, M1–M44, T1–T17 giữ nguyên chữ ký đã duyệt ở thiết kế.

Delta phát sinh ở EXECUTING-PLANS — **APPROVED**: user trả lời "Duyệt X7–X8" ngày 2026-11-03:

| ID | Delta | Lý do |
|---|---|---|
| X7 | `PageResponse`: compact constructor `public PageResponse { if (items != null) items = List.copyOf(items); }` | SpotBugs `EI_EXPOSE_REP2` làm `mvnw verify` fail; mẫu đích `dto/snapshot/PurchaseSnapshot` |
| X8 | `PageResponse`: `public List<T> items()` trả `items == null ? null : List.copyOf(items)` | SpotBugs `EI_EXPOSE_REP`; cùng mẫu `PurchaseSnapshot.items()` |

## 3. Bảng class (danh sách cuối)

| ID | Class/file | NEW/EXISTING | Vai trò, annotation, API sinh | Mẫu Cinema Booking hoặc ngoại lệ |
|---|---|---|---|---|
| C1 | `@/client/VNPayRefundClient` | NEW | `@Component @RequiredArgsConstructor @Slf4j`; `private final RestClient vnpayRestClient`, `private final VNPayConfig vnPayConfig`; constructor sinh bởi Lombok (X1) | NGOÀI MẪU (Cinema không gọi API hoàn); mẫu đích `BookingPurchaseClient` |
| C2 | `@/dto/response/RefundGatewayRequest` | NEW | `record (UUID refundId, UUID attemptId, String requestKey, String txnRef, BigDecimal amount, String transactionNo, String transactionDate)`; accessor/equals/hashCode/toString của record | `DTO/Response/*` record |
| C3 | `@/dto/response/RefundGatewayResult` | NEW | `record (RefundGatewayOutcome outcome, String providerRefundId, String responseCode)` | record |
| C4 | `@/entity/enums/RefundGatewayOutcome` | NEW | `enum { SUCCEEDED, PROCESSING, FAILED, UNKNOWN }` | `Shared/enums` |
| C5 | `@/dto/request/ManualSettlementRequest` | NEW | `record (@NotBlank(message = "INVALID_REQUEST") @Size(max = 255, message = "INVALID_REQUEST") String externalReference)` | `CreateCinemaRequest`; message là tên `PaymentErrorCode` |
| C6 | `@/dto/response/RefundAdminView` | NEW | `record` 14 component như thiết kế §4; `amount` có `@WireMoney` | `CinemaResponse` |
| C7 | `@/dto/response/EventRefundProgressView` | NEW | `record` 9 component như thiết kế §4 | record |
| C8 | `@/dto/response/PageResponse<T>` | NEW | `record (List<T> items, int page, int size, long totalElements, int totalPages)` | `Shared/response/PageResponse` (bỏ `@Builder` cho khớp record của đích) |
| C9 | `@/entity/Refund` | EXISTING | `+ @Column(name = "settled_by", nullable = true, columnDefinition = "nonempty_text") private String settledBy;` `+ @Column(name = "external_reference", nullable = true, columnDefinition = "nonempty_text") private String externalReference;` getter/setter sinh bởi `@Getter @Setter` sẵn có | Cột theo B12 v0.3 |
| C10 | `@/entity/RefundAttempt`, `EventRefundRun`, `EventRefundItem`, `EventFinanceSnapshot` | EXISTING | Thêm factory M5–M8; không thêm field | `Refund.pending` |
| C11 | `@/config/VNPayConfig` | EXISTING | `+ @Value("${vnpay.api-url}") apiUrl`, `+ @Value("${vnpay.server-ip:127.0.0.1}") serverIp`, `+ @Value("${vnpay.refund-create-by:flash-ticket}") refundCreateBy`; getter sinh bởi `@Getter` | `Config/VNPayConfig` |
| C12 | `@/exception/PaymentErrorCode` | EXISTING | `+ REFUND_ALREADY_SUCCEEDED(HttpStatus.CONFLICT, "The refund has already succeeded.")`, `+ REFUND_NOT_FAILED(HttpStatus.CONFLICT, "Only a failed refund can be settled manually.")`, `+ REFUND_ATTEMPT_IN_FLIGHT(HttpStatus.CONFLICT, "A refund attempt is still waiting for the gateway.")` | `ErrorCode` |
| C13 | Test `service/RefundFlowIntegrationTest`, `client/VNPayRefundClientTest` | NEW | Integration: package-private, `@SpringBootTest @ActiveProfiles("payment-flow-test")`, `@MockitoBean VNPayRefundClient refundClient; @MockitoBean BookingPurchaseClient bookingClient`; `@Autowired` service/listener/worker/`JdbcTemplate`. Client: `@RestClientTest(VNPayRefundClient.class) @Import(PaymentRuntimeConfig.class)` | `PaymentFlowIntegrationTest`, `BookingPurchaseClientTest` |

## 4. Bảng method (danh sách cuối)

Chữ ký M1–M44 giữ nguyên thiết kế §5; bảng dưới ghi cách triển khai để thực thi.

| ID | Class | Chữ ký | Cách viết |
|---|---|---|---|
| M1 | `VNPayRefundClient` | `public RefundGatewayResult refund(RefundGatewayRequest request)` | Map field VNPay: `vnp_RequestId=requestKey`, `vnp_TransactionType="02"`, `vnp_TxnRef=txnRef`, `vnp_Amount=amount×100`, `vnp_TransactionNo`, `vnp_TransactionDate`, `vnp_CreateBy=refundCreateBy`, `vnp_CreateDate=VNPayUtil.getCurrentTime()`, `vnp_IpAddr=serverIp`, `vnp_OrderInfo="Hoan tien " + refundId`; hash `VNPayUtil.hmacSHA512(hashSecret, String.join("|", ...))` theo thứ tự tài liệu. `vnpayRestClient.post().contentType(JSON).body(map).retrieve().body(Map.class)`. `RestClientException` → `UNKNOWN` |
| M2 | `VNPayRefundClient` | `public RefundGatewayResult queryRefund(RefundGatewayRequest request)` | `vnp_Command="querydr"`, `vnp_RequestId` = UUID mới bỏ dấu gạch; cùng xử lý lỗi M1 |
| M3 | `VNPayRefundClient` | `private boolean verifyResponseHash(Map<String, String> response, List<String> fieldOrder)` | Giá trị null → chuỗi rỗng; so `equalsIgnoreCase` với `vnp_SecureHash` |
| M4 | `VNPayRefundClient` | `private RefundGatewayOutcome toOutcome(String responseCode, String transactionType, String transactionStatus, boolean query)` | `switch` theo bảng thiết kế §2.1 |
| M5 | `RefundAttempt` | `public static RefundAttempt started(UUID refundId, String requestKey, Instant startedAt)` | `id = UUID.randomUUID()` |
| M6 | `EventRefundRun` | `public static EventRefundRun started(UUID eventId, UUID cancellationId, Instant startedAt)` | `selectionComplete=false`, `selectedOrderCount=0L` |
| M7 | `EventRefundItem` | `public static EventRefundItem selected(UUID runId, UUID eventId, UUID orderId, UUID confirmedChargeId, Instant updatedAt)` | `id = new EventRefundItemId(runId, orderId)`, state `SELECTED` |
| M8 | `EventFinanceSnapshot` | `public static EventFinanceSnapshot cancelledMarker(UUID eventId, UUID cancellationId, Instant cancelledAt, Instant receivedAt)` | Field phí để null |
| M9 | `PaymentRuntimeConfig` | `@Bean public RestClient vnpayRestClient(RestClient.Builder builder)` | `@Value` field mới `vnpayApiUrl` (`vnpay.api-url`), `vnpayConnectTimeout` (`payment.vnpay.connect-timeout:PT3S`), `vnpayReadTimeout` (`payment.vnpay.read-timeout:PT10S`); `JdkClientHttpRequestFactory` như `bookingRestClient` |
| M10 | `RabbitMqConfig` | `paymentSagaTopology()` (sửa thân) | `@Value("${payment.queues.event-cancelled:payment.event-cancelled.q}")`; queue bền + binding `event.cancelled.v1` |
| M11 | `PaymentSecurityConfiguration` | `paymentSecurityFilterChain(...)` (sửa thân) | `requestMatchers(GET, "/api/v1/admin/refunds", "/api/v1/admin/events/*/refund-progress")`, `requestMatchers(POST, "/api/v1/admin/refunds/*/retry", "/api/v1/admin/refunds/*/manual-settlement")` → `hasRole("ADMIN")`, đặt trước `anyRequest().denyAll()` |
| M12 | `MessageInvariantValidator` | `public void validateEventCancellation(Envelope<EventCancellation> envelope)` | Theo `validatePaymentDecision`: `messageType`, `aggregateId`/`correlationId` = `eventId`; `sales.eventId`/`access.eventId` = `eventId`; `access.eventStatus == CANCELLED`; sai → `fail(field, code)` |
| M13 | `PaymentMessageListener` | `onEventCancelled(Message, Channel)` | Chép khung `onPaymentDecision`; gọi M12 rồi M17 |
| M14 | `PaymentWorker` | `public void sendRefunds()` | `@Scheduled(fixedDelayString = "${payment.refund.poll-delay:5000}", initialDelayString = "${payment.worker.initial-delay:0}")`; vòng `findRefundsToSend(batchSize)` → `sendRefund`; `catch RuntimeException` → `log.warn` như `recoverSagas` |
| M15 | `PaymentWorker` | `public void pollRefunds()` | Như M14; `findRefundsToPoll(Instant.now().minus(queryAfter), batchSize)`; `@Value("${payment.refund.query-after:PT1M}") Duration queryAfter` |
| M16 | `PaymentWorker` | `public void selectEventRefundOrders()` | Như M14; `findEventRefundRunsToSelect` → `selectEventRefundOrders(runId, batchSize)` |
| M17 | `RefundService`/`Impl` | `void recordEventCancellation(Envelope<EventCancellation> incomingEnvelope)` | `@Transactional`; khóa marker trước (`findForUpdateByEventId`), chưa có thì `save(cancelledMarker)`; `recordInbox("payment.event-cancelled.v1", …)` false → return; set `cancellationId` khi null; `findBySourceCancellationId` chưa có → `save(EventRefundRun.started)` |
| M18 | 〃 | `List<UUID> findEventRefundRunsToSelect(int limit)` | `@Transactional(readOnly = true)`; M42 `findIdsToSelect(PageRequest.of(0, limit))` |
| M19 | 〃 | `void selectEventRefundOrders(UUID runId, int limit)` | `@Transactional`; khóa marker → run (`findForUpdateById`); M44 lấy `orderId`; mỗi đơn: khóa snapshot → saga; `confirmations.findById` → charge; item `selected` → `ensureRefund(charge, EVENT_CANCELLED, now)` → gắn `refundId`, state `REFUNDED` nếu refund `SUCCEEDED`, ngược lại `REFUND_PENDING`; saga `COMPLETED`/`WAITING_TICKETS` → `COMPENSATING`; cập nhật cursor/count; lô `< limit` → `selectionComplete=true` rồi M33 |
| M20 | 〃 | `List<UUID> findRefundsToSend(int limit)` | `@Transactional(readOnly = true)`; M40 `findIdsByState(PENDING, …)` |
| M21 | 〃 | `List<UUID> findRefundsToPoll(Instant startedBefore, int limit)` | `@Transactional(readOnly = true)`; M40 `findIdsToPoll` |
| M22 | 〃 | `void sendRefund(UUID refundId)` | `@Transactional(propagation = Propagation.NEVER)`; `paymentTransactionTemplate.execute(M28)` → null thì return → `refundClient.refund` → `paymentTransactionTemplate.executeWithoutResult(M30)` |
| M23 | 〃 | `void pollRefund(UUID refundId)` | Như M22 với M29 + `queryRefund` |
| M24 | 〃 | `PageResponse<RefundAdminView> getRefunds(RefundState state, int page, int size)` | `@Transactional(readOnly = true)`; `PageRequest.of(page, size, Sort.by("createdAt", "id"))`; map M34 |
| M25 | 〃 | `RefundAdminView retryRefund(UUID refundId)` | `@Transactional`; M31; `SUCCEEDED` → `REFUND_ALREADY_SUCCEEDED`; `FAILED` → `PENDING`, item `NEEDS_ATTENTION` → `REFUND_PENDING`; trả M34 |
| M26 | 〃 | `RefundAdminView settleRefundManually(UUID refundId, ManualSettlementRequest request, String adminSubject)` | `@Transactional`; M31; đã manual cùng reference → trả cũ; `SUCCEEDED` khác → `REFUND_ALREADY_SUCCEEDED`; `PENDING`/`PROCESSING` → `REFUND_NOT_FAILED`; `existsByRefundIdAndFinishedAtIsNull` → `REFUND_ATTEMPT_IN_FLIGHT`; set `SUCCEEDED`, `settledBy`, `externalReference`, `completedAt` → M32; `log.info` refundId/admin |
| M27 | 〃 | `EventRefundProgressView getEventRefundProgress(UUID eventId)` | `@Transactional(readOnly = true)`; M42 `findFirstByEventIdOrderByStartedAtDesc` → không có → `RESOURCE_NOT_FOUND`; đếm bằng M43 |
| M28 | `RefundServiceImpl` | `private RefundGatewayRequest claimRefund(UUID refundId)` | M31; không `PENDING` hoặc có attempt dở → null; `PROCESSING` + `RefundAttempt.started(id, UUID hex 32, now)`; `vnp_TransactionDate` = query `vnp_CreateDate` của `PaymentAttempt.paymentUrl` (`UriComponentsBuilder.fromUriString(...).build().getQueryParams()`) |
| M29 | `RefundServiceImpl` | `private RefundGatewayRequest findUnfinishedAttempt(UUID refundId)` | Đọc refund/charge/attempt; không có attempt dở → null |
| M30 | `RefundServiceImpl` | `private void recordGatewayResult(UUID refundId, UUID attemptId, RefundGatewayResult result)` | M31; attempt đã kết thúc → return; refund đã `SUCCEEDED` (manual) → chỉ ghi `outcome` attempt + `log.warn`; `SUCCEEDED` → attempt finish + refund + M32; `FAILED` → attempt finish, refund `FAILED`, item `NEEDS_ATTENTION` + `lastErrorCode`; `PROCESSING`/`UNKNOWN` → chỉ lưu `providerRefundId` nếu có |
| M31 | `RefundServiceImpl` | `private Refund lockRefund(UUID refundId)` | `findById` (không khóa) → `RESOURCE_NOT_FOUND`; snapshot → `finance.findForUpdateByEventId(eventId)` → `snapshots.findForUpdateByOrderId` → `sagas.findForUpdateByOrderId` → `refunds.findForUpdateById` |
| M32 | `RefundServiceImpl` | `private void completeRefund(Refund refund, Instant completedAt)` | Saga cùng `chargeId` đang `COMPENSATING` → `COMPENSATED`; item → `REFUNDED` → M33 |
| M33 | `RefundServiceImpl` | `private void completeRunIfFinished(UUID runId, Instant now)` | Khóa run; `selectionComplete && !existsByIdRunIdAndStateNot(runId, REFUNDED)` → `completedAt` |
| M34 | `RefundServiceImpl` | `private RefundAdminView toAdminView(Refund refund)` | `findFirstByRefundIdOrderByStartedAtDescIdDesc` → `refundMapper.toAdminView(refund, attempt.orElse(null))` |
| M35 | `RefundMapper` | `RefundAdminView toAdminView(Refund refund, RefundAttempt lastAttempt)` | `@Mapping(target = "refundId", source = "refund.id")`, `amount` qua `qualifiedByName = "moneyToWire"`, `lastAttemptStartedAt` ← `lastAttempt.startedAt`, `lastAttemptFinishedAt` ← `lastAttempt.finishedAt`, `lastAttemptOutcome` ← `lastAttempt.outcome` |
| M36–M39 | `RefundController` | Như thiết kế §5 | Thêm `@RequestMapping("/api/v1/admin") @FieldDefaults(level = PRIVATE, makeFinal = true) @Slf4j @Tag(name = "Refund", description = "Theo dõi và xử lý yêu cầu hoàn")`; mỗi endpoint `@Operation(summary, description)` tiếng Việt, `@SecurityRequirement(name = "bearerAuth")`, `@PreAuthorize("hasRole('ADMIN')")`; trả `ApiResponse.success(..., "...")`. Field hiện có `private final RefundService refundService` đổi thành `RefundService refundService` theo `@FieldDefaults` như `PaymentController` |
| M40 | `RefundRepository` | 4 method thiết kế §5 | `findIdsToPoll`: `select r.id from Refund r where r.state = PROCESSING and exists (select a from RefundAttempt a where a.refundId = r.id and a.finishedAt is null and a.startedAt <= :startedBefore) order by r.createdAt, r.id` |
| M41 | `RefundAttemptRepository` | 3 method thiết kế §5 | Query theo tên |
| M42 | `EventRefundRunRepository` | 4 method thiết kế §5 | `findForUpdateById`: `@Lock(PESSIMISTIC_WRITE) @Query("select r from EventRefundRun r where r.id = :id")` |
| M43 | `EventRefundItemRepository` | 3 method thiết kế §5 | Query theo tên trên `id.runId` |
| M44 | `PaymentConfirmationRepository` | `findOrderIdsByEventIdAfter(...)` | JPQL text block thiết kế §5 |

`RefundServiceImpl` thêm dependency `private final` qua `@RequiredArgsConstructor` (constructor sinh lại): `VNPayRefundClient refundClient`, `PaymentSagaRepository paymentSagaRepository`, `OrderPaymentSnapshotRepository snapshots`, `EventFinanceSnapshotRepository finance`, `PaymentConfirmationRepository confirmations`, `ChargeRepository charges`, `PaymentAttemptRepository attempts`, `MessagingService messaging`, `ContractCodec codec`, `RefundMapper refundMapper`, `TransactionTemplate paymentTransactionTemplate`.

## 5. Bảng test (danh sách cuối)

T1–T17 như thiết kế §6, cộng T18–T21 (X2–X5).

## 6. Task

Mỗi task kết thúc bằng `.\mvnw.cmd -q -DskipTests compile` (JDK 21, `JAVA_TOOL_OPTIONS=-Duser.timezone=UTC`) trừ khi ghi khác. Commit chỉ khi user yêu cầu.

| Task | Nội dung | Item | Xác minh |
|---|---|---|---|
| 1 | Entity, enum, DTO, error code: thêm field C9, factory M5–M8, tạo C2–C8, mã lỗi C12 | C2–C10, C12, M5–M8 | Compile; `EntityMappingTest` qua profile `mapping-tests` với DB đã chạy `30-payment.sql` v0.3 + `services/99-table-prefix.sql` (Hibernate `validate` thấy hai cột mới) |
| 2 | Repository: M40–M44 | M40–M44 | Compile; Spring Data parse query khi context JPA lên ở Task 7 |
| 3 | Cấu hình: C11, M9, M10; thêm `vnpay.api-url` vào `infrastructure/config-server/.../config/payment-service.yaml` và test profile; `payment.queues.event-cancelled` vào `application.yaml` + `application-payment-flow-test.yaml` | C11, M9, M10 | `PaymentConfigurationTest` hiện có vẫn qua |
| 4 | Client VNPay: C1, M1–M4; test C13 phần client, T12–T15, T18, T21 | C1, M1–M4, T12–T15, T18, T21 | `.\mvnw.cmd -Dtest=VNPayRefundClientTest test` qua |
| 5 | Service hoàn: M17–M34, M35 | M17–M35 | Compile |
| 6 | Messaging + worker + security + controller: M11–M16, M36–M39; validator test T17; security test T16 | M11–M16, M36–M39, T16, T17 | `.\mvnw.cmd verify -B -ntp` (suite mặc định, gồm Spotless/SpotBugs) qua |
| 7 | Integration: `RefundFlowIntegrationTest` T1–T11, T19, T20; thêm exclude X6 vào `pom.xml` | T1–T11, T19, T20, X6 | `.\mvnw.cmd -Pquery-tests verify -B -ntp` với PostgreSQL 16.15 + RabbitMQ test riêng (port 25432/25672 như profile) — toàn bộ qua, gồm `PaymentFlowIntegrationTest` cũ |

Task 4 độc lập với Task 5; Task 5 cần Task 1–4; Task 6 cần Task 5; Task 7 cần tất cả.

## 7. Rủi ro và cách kiểm

- `vnp_TransactionDate` lấy từ `paymentUrl`: attempt cũ không có URL → claim trả lỗi kỹ thuật, refund giữ `PENDING`, worker log; T12 kiểm đường thường.
- Thứ tự khóa M31 phải giống `PaymentSagaServiceImpl` (marker → snapshot → saga → refund) để tránh deadlock khi IPN/decision chạy cùng lúc.
- Gọi VNPay sandbox thật không nằm trong test tự động; kiểm thủ công sau khi có `vnpay.api-url` sandbox, ghi kết quả vào báo cáo riêng.

## 8. Nhật ký duyệt

| Pha | Danh sách | Trạng thái | Nguồn |
|---|---|---|---|
| BRAINSTORMING | C1–C13, M1–M44, T1–T17, D1–D4 | APPROVED | "Duyệt hết", 2026-11-03 |
| WRITING-PLANS | C1–C13, M1–M44, T1–T21, X1–X6 (bản 2026-11-03) | APPROVED | User trả lời "Duyệt plan" cho câu hỏi duyệt danh sách và delta X1–X6, 2026-11-03 |
| EXECUTING-PLANS | C1–C13, M1–M44, T1–T21, X1–X6 (bản 2026-11-03) | APPROVED | User trả lời "Duyệt thực thi" ngày 2026-11-03; Codex CLI triển khai, Claude review |

## 9. Tiến độ thực thi

| Task | Trạng thái | File thay đổi | Kiểm thử | Ghi chú |
|---|---|---|---|---|
| 1 | COMPLETED | `entity/Refund`, `RefundAttempt`, `EventRefundRun`, `EventRefundItem`, `EventFinanceSnapshot`, `exception/PaymentErrorCode`; mới: `RefundGatewayOutcome`, `RefundGatewayRequest`, `RefundGatewayResult`, `ManualSettlementRequest`, `RefundAdminView`, `EventRefundProgressView`, `PageResponse` | `spotless:check compile` BUILD SUCCESS | Kiểm mapping DB của 2 cột mới dời sang Task 7. `PageResponse` vướng SpotBugs, xem delta X7–X8 |
| 2 | COMPLETED | 5 repository refund/run/item/attempt/confirmation | `mvnw verify`: 123 test qua | Query chạy thật trên DB ở Task 7 |
| 3 | COMPLETED | `VNPayConfig`, `PaymentRuntimeConfig`, `RabbitMqConfig`, `PaymentConfigurationTest` (chỉ thêm key đối chiếu), 4 YAML (config-server, main, test, flow-test); `PageResponse` thêm X7–X8 | `mvnw verify`: 127 test qua, Spotless + SpotBugs sạch | Queue `payment.event-cancelled.q` đã có trong `deploy/rabbitmq/definitions.json`, không sửa deploy |
| 4 | COMPLETED | mới: `client/VNPayRefundClient`, test `client/VNPayRefundClientTest` | `-Dtest=VNPayRefundClientTest test`: 4/4 qua | Review: `refund`/`queryRefund` lặp đoạn gửi–đọc–kiểm chữ ký; tách helper cần duyệt method mới, để dọn sau |
| 5 | COMPLETED | `service/RefundService`, `service/impl/RefundServiceImpl`, `mapper/RefundMapper` | `mvnw verify`: 127 test qua, Spotless + SpotBugs sạch | Review: thứ tự khóa marker → snapshot → saga → refund → run khớp B14; gọi cổng ngoài TX; hành vi chạy thật kiểm ở Task 7 |
| 6 | COMPLETED | `validation/MessageInvariantValidator`, `messaging/PaymentMessageListener`, `worker/PaymentWorker`, `config/PaymentSecurityConfiguration`, `controller/RefundController`; test `MessageInvariantValidatorTest`, `PaymentJwtSecurityTest` | `mvnw verify`: 129 test qua, Spotless + SpotBugs sạch | M12 không kiểm `correlationId`: event-service đặt correlation theo request (`CancellationTransactions.cancelEvent` → `OutboxStore.append`), kiểm sẽ từ chối message thật. Chỉ đổi thân method, chữ ký giữ nguyên |
| 7 | COMPLETED | test mới `service/RefundFlowIntegrationTest`; `pom.xml` (X6) | `-Pquery-tests verify` trên PostgreSQL 16.15 + RabbitMQ 4.4 tạm: 167 test qua (gồm `EntityMappingTest` 14, `PaymentReadRepositoriesTest` 5, `PaymentFlowIntegrationTest` 9, `RefundFlowIntegrationTest` 10), Spotless + SpotBugs sạch; `mvnw verify` mặc định 129 test qua | Container tạm đã xóa. `90-grants.sql` không nạp được vào DB chỉ có payment; test dùng user `postgres` |

### Kết quả cuối (2026-11-03)

- 7/7 task COMPLETED, 0 BLOCKED. Delta phát sinh khi thực thi: X7–X8 (`PageResponse`, đã duyệt).
- Còn tồn tại:
  - Chưa gọi VNPay sandbox thật; client chỉ được kiểm bằng `MockRestServiceServer`.
  - `VNPayRefundClient.refund`/`queryRefund` lặp đoạn gửi–đọc–kiểm chữ ký; tách helper cần duyệt method mới.
  - Đối soát/chi trả (`UC-17`–`UC-19`) và `refund_pending_count` của `event_payouts` vẫn ngoài phạm vi.
  - View đầu của `settleRefundManually` trả `completedAt` độ chính xác nano, lần đọc lại từ DB là micro giây.
- Chưa commit.

### Đồng bộ queue với RabbitMQ đã deploy (2026-11-03, sau plan)

User chọn "Chỉ Payment". Chỉ đổi thân method/cấu hình, không khai báo mới.

- `RabbitMqConfig`: 4 queue khai báo quorum; `payment.ticket-issuance-failed.q` bind `ticket.issuance-failed.v1` (trước: `ticket.issuance-result.v1`).
- `MessageInvariantValidator.validateIssuanceResult`: nhận messageType `ticket.issuance-result.v1` hoặc `ticket.issuance-failed.v1`.
- `PaymentSagaServiceImpl`: gửi Booking `ticket.issued.v1` (COMPLETED) / `booking.issuance-failed.v1` (FAILED) thay `booking.issuance-result.v1` — key cũ không có binding nào trên broker đã deploy.
- Test: `PaymentFlowIntegrationTest` (2 assertion), `run-payment-scenarios.mjs` (chưa chạy, cần đủ stack).
- Kiểm: `-Pquery-tests verify` 167 test qua; broker test có 4 queue quorum với đúng routing key.
- Còn mở: ticket-service vẫn publish `ticket.issuance-result.v1` (không có binding trên broker đã deploy); `payment.finance-configured.q`, `payment.start.q` chưa có consumer; B13 §5.2 dòng Ticket → Payment còn ghi `ticket.issuance-result.v1`.
