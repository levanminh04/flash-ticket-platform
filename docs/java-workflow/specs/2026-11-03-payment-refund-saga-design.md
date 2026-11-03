# Payment — hoàn tiền đầy đủ và kết thúc Saga bù trừ

- Phase: BRAINSTORMING
- Trạng thái: **APPROVED** (BRAINSTORMING) — user duyệt C1–C13, M1–M44, T1–T17 và chọn D1–D4 theo đề xuất ngày 2026-11-03
- Ngày: 2026-11-03
- Phạm vi use case: `UC-15` (hoàn qua cổng), `UC-16` (theo dõi và xử lý yêu cầu hoàn, `BIZ-159`), phần Payment của `UC-08` (lô hoàn khi hủy sự kiện). `UC-12`/`UC-14` đã gọi `ensureRefund` từ trước, chỉ hưởng phần gửi cổng.
- Nguồn: B6 §6.9, §4.7; B4 `C01`–`C06`; B8 `FR-34`–`FR-39a`; B12 `refunds`, `refund_attempts`, `event_refund_runs`, `event_refund_items`; B13 §3.1, §5.2; B14 §1, §4, §5; `BIZ-101`, `BIZ-159`.
- Mẫu đã đọc: Cinema Booking `Service/Payment/PaymentService.refund` (chỉ đổi trạng thái, không gọi cổng), `Service/Booking/BookingService.expireStaleBookings` (`@Scheduled`), `Controller/PaymentController`, `Config/VNPayConfig`. Code đích đã đọc: `PaymentService`, `PaymentSagaServiceImpl`, `RefundServiceImpl`, `PaymentWorker`, `PaymentMessageListener`, `RabbitMqConfig`, `PaymentRuntimeConfig`, `VNPayConfig`, `VNPayUtil`, các entity/repository refund.
- Tài liệu chính thức: VNPay "Truy vấn và hoàn tiền" (`/merchant_webapi/api/transaction`, lệnh `refund` và `querydr`).

## 1. Hiện trạng và khoảng trống

| Có rồi | Còn thiếu |
|---|---|
| `RefundServiceImpl.ensureRefund` tạo refund `PENDING`, unique theo charge | Không ai gửi lệnh hoàn tới VNPay; refund nằm `PENDING` mãi |
| Saga chuyển `COMPENSATING` khi hoàn do trùng/muộn/hủy/phát vé lỗi | Không có bước `COMPENSATING → COMPENSATED` |
| Entity/repository `RefundAttempt`, `EventRefundRun`, `EventRefundItem` | Không được dùng |
| `EventFinanceSnapshot.cancellationId` được đọc ở Saga | Payment **không nhận** `event.cancelled.v1`, nên marker không bao giờ có |
| `RefundController` | Rỗng; chưa có API admin của B13 §3.1 |

## 2. Luồng chính

### 2.1 Gửi hoàn qua VNPay (UC-15)

`PaymentWorker.sendRefunds` quét refund `PENDING` → `RefundService.sendRefund(refundId)` (`Propagation.NEVER`, theo mẫu `PaymentService.createPayment` + `paymentTransactionTemplate`):

1. **TX1 claim:** khóa theo thứ tự B14 (event marker → order snapshot → saga → refund). Chỉ khi refund `PENDING` và không có attempt dở: refund → `PROCESSING`, lưu `RefundAttempt` với `requestKey` mới (32 ký tự hex, dùng làm `vnp_RequestId`). Trả `RefundGatewayRequest`.
2. **Ngoài TX:** `VNPayRefundClient.refund(request)` gọi `refund` loại `02` (hoàn toàn bộ), ký HMAC-SHA512 theo đúng thứ tự trường của VNPay và kiểm chữ ký phản hồi.
3. **TX2 ghi kết quả:** khóa lại cùng thứ tự rồi áp `RefundGatewayResult`:
   - `SUCCEEDED` → refund `SUCCEEDED`, `completedAt`; attempt kết thúc; chạy **hậu quả thành công** (§2.4).
   - `FAILED` → refund `FAILED`, attempt kết thúc; item lô (nếu có) → `NEEDS_ATTENTION`. Không tự thử lại; chờ admin (`BIZ-159`).
   - `PROCESSING`/`UNKNOWN` → giữ attempt dở, refund vẫn `PROCESSING`.

`PaymentWorker.pollRefunds` quét refund `PROCESSING` có attempt dở quá `payment.refund.query-after` → `RefundService.pollRefund` gọi `querydr` rồi áp kết quả như bước 3. Không bao giờ gửi `refund` lần hai khi attempt cũ chưa rõ kết quả (B14 §4).

**Ánh xạ mã VNPay** (C4 `RefundGatewayOutcome`):

| Phản hồi | Kết quả |
|---|---|
| `refund` `00` + `vnp_TransactionStatus=00` | `SUCCEEDED` |
| `refund` `00` + `05`/`06`; `refund` `94` | `PROCESSING` |
| `refund` `00` + `09`; `refund` `02`, `03`, `91`, `95`, `97` | `FAILED` |
| `refund` `99`, timeout/lỗi mạng, chữ ký phản hồi sai | `UNKNOWN` |
| `querydr` `00` + loại `02` + `00` | `SUCCEEDED` |
| `querydr` `00` + loại `02` + `05`/`06` | `PROCESSING` |
| `querydr` `00` + loại `02` + `09`; loại `01` (cổng chưa ghi nhận lệnh hoàn); `querydr` `91` | `FAILED` |
| `querydr` khác, timeout, chữ ký sai | `UNKNOWN` |

`vnp_TransactionDate` phải là `vnp_CreateDate` của lần thanh toán gốc. Hệ thống không lưu riêng giá trị này; client đọc nó từ query string của `PaymentAttempt.paymentUrl` đã lưu, nên không cần đổi schema.

### 2.2 Hủy sự kiện (phần Payment của UC-08)

1. `PaymentMessageListener.onEventCancelled` nhận `event.cancelled.v1` (queue mới `payment.event-cancelled.q`) → `RefundService.recordEventCancellation`: một TX ghi inbox, tạo hoặc khóa `EventFinanceSnapshot` của event, đặt `cancellationId`/`cancelledAt` nếu chưa có, tạo `EventRefundRun` duy nhất theo `source_cancellation_id`. Message lặp hoặc cùng cancellation → không tác dụng phụ.
2. `PaymentWorker.selectEventRefundOrders` quét run chưa `selection_complete` → `RefundService.selectEventRefundOrders(runId, batchSize)` theo lô, mỗi lô một TX:
   - lấy `order_id` có confirmation thuộc event, `order_id > selection_cursor`, theo thứ tự `order_id`;
   - mỗi đơn: tạo item `SELECTED`, `ensureRefund(confirmedCharge, EVENT_CANCELLED)`, gắn `refundId`; item → `REFUND_PENDING`, hoặc `REFUNDED` nếu refund đã thành công từ trước;
   - saga `COMPLETED`/`WAITING_TICKETS` của đơn → `COMPENSATING`;
   - cập nhật cursor và `selected_order_count`; lô ít hơn `batchSize` → `selection_complete = true`.
   Thu thừa không được chọn, vì FK item chỉ nhận charge đã confirmation (`BIZ-102`). Charge hoặc acceptance đến sau marker đã đi nhánh hoàn có sẵn trong `PaymentSagaServiceImpl`.
3. Run `completed_at` khi `selection_complete` và mọi item `REFUNDED` (kiểm ở §2.4 và lúc chọn xong).

### 2.3 Admin xử lý yêu cầu hoàn (UC-16, BIZ-159)

| Endpoint | Hành vi |
|---|---|
| `GET /api/v1/admin/refunds?state=FAILED&page=0&size=20` | Danh sách refund theo trạng thái, mọi `reason` |
| `POST /api/v1/admin/refunds/{refundId}/retry` | `FAILED` → `PENDING`; item → `REFUND_PENDING`; worker gửi lại trên cùng refund. `PENDING`/`PROCESSING` trả trạng thái hiện có; `SUCCEEDED` → `409 REFUND_ALREADY_SUCCEEDED` |
| `POST /api/v1/admin/refunds/{refundId}/manual-settlement` | Chỉ từ `FAILED` và không có attempt dở → `SUCCEEDED` + `settledBy` (subject JWT), `externalReference`, `completedAt`; chạy hậu quả thành công. Attempt dở → `409 REFUND_ATTEMPT_IN_FLIGHT`; `PENDING`/`PROCESSING` → `409 REFUND_NOT_FAILED`; đã thành công qua cổng hoặc reference khác → `409 REFUND_ALREADY_SUCCEEDED`; cùng reference → trả kết quả cũ |
| `GET /api/v1/admin/events/{eventId}/refund-progress` | Tiến độ lô của sự kiện bị hủy: số đơn đã chọn, chờ, đã hoàn, cần xử lý, đã chọn xong hay chưa, `asOf` (`FR-39`) |

Cả bốn endpoint yêu cầu role `ADMIN`.

### 2.4 Hậu quả thành công — dùng chung cho cổng và ghi nhận ngoài hệ thống

Trong cùng TX đã khóa theo thứ tự B14:

- saga của đơn có `chargeId` trùng refund và đang `COMPENSATING` → `COMPENSATED`. Refund của thu thừa không đụng saga;
- item lô gắn refund → `REFUNDED`; nếu run đã chọn xong và hết item chưa `REFUNDED` → `completed_at`;
- không đổi order, vé hay tồn kho (`BIZ-150`). Không phát message mới: Booking và Ticket đã nhận lệnh hủy/thất bại từ trước (B13 §5.2).

### 2.5 Ngoài phạm vi

- Đối soát và chi trả (`UC-17`–`UC-19`), `EventPayout.refund_pending_count`: chưa có code nào ghi bảng này; thiết kế này không tạo.
- `event.finance-configured.v1`: marker hủy được tạo trước, không cần phí (B14 §5 bước 2).
- Kết quả thành công từ cổng đến sau ghi nhận ngoài hệ thống: chỉ quan sát được khi có attempt dở để `querydr`. Manual settlement đã cấm khi có attempt dở, nên trường hợp này chỉ ghi `outcome` của attempt và log; không có luồng đối soát tự động.

## 3. Quyết định đã chốt

| ID | Vấn đề | Đã chọn |
|---|---|---|
| D1 | B13 §3.1 bắt `Idempotency-Key` cho retry và manual-settlement, nhưng payment-service chưa có bảng idempotency cho lệnh admin | **Bỏ header.** Hai lệnh đã idempotent theo trạng thái và reference; sửa B13 cho khớp |
| D2 | B13 §3.1 dùng cursor và `RefundAdminView` lồng (`settlement`, `lastAttempt`) | **Đổi sang `page/size` + `PageResponse` theo mẫu Cinema và view phẳng** để MapStruct map thẳng; sửa B13 |
| D3 | B13 §3.1 chưa có API tiến độ lô `FR-39` | **Thêm** `GET /admin/events/{eventId}/refund-progress` vào B13 |
| D4 | Mẫu Cinema dùng service class cụ thể, không interface; payment-service đang dùng `RefundService` + `RefundServiceImpl` | **Giữ cặp interface/impl hiện có** của repo đích, không tách mới |

## 4. Class/file (gate duyệt)

| ID | Class/file | NEW/EXISTING | Vai trò, field/annotation/API sinh | Mẫu Cinema Booking hoặc ngoại lệ |
|---|---|---|---|---|
| C1 | `client/VNPayRefundClient` | NEW | `@Component @RequiredArgsConstructor @Slf4j @FieldDefaults(PRIVATE, makeFinal)`; field `RestClient vnpayRestClient`, `VNPayConfig vnPayConfig`. Gọi `refund`/`querydr`, ký và kiểm chữ ký | **Ngoại lệ**: Cinema không gọi API hoàn. Theo `client/BookingPurchaseClient` của đích + tài liệu VNPay |
| C2 | `dto/response/RefundGatewayRequest` | NEW | `record (UUID refundId, UUID attemptId, String requestKey, String txnRef, BigDecimal amount, String transactionNo, String transactionDate)` — dữ liệu mang từ TX1 ra lời gọi cổng | Record như DTO Cinema |
| C3 | `dto/response/RefundGatewayResult` | NEW | `record (RefundGatewayOutcome outcome, String providerRefundId, String responseCode)` | Record |
| C4 | `entity/enums/RefundGatewayOutcome` | NEW | `enum { SUCCEEDED, PROCESSING, FAILED, UNKNOWN }` | `Shared/enums` |
| C5 | `dto/request/ManualSettlementRequest` | NEW | `record (@NotBlank(message = "INVALID_REQUEST") @Size(max = 255) String externalReference)` | `CreateCinemaRequest` |
| C6 | `dto/response/RefundAdminView` | NEW | `record (UUID refundId, UUID orderId, UUID chargeId, @WireMoney String amount, String currency, RefundReason reason, RefundState state, Instant createdAt, Instant completedAt, String settledBy, String externalReference, Instant lastAttemptStartedAt, Instant lastAttemptFinishedAt, String lastAttemptOutcome)` | `CinemaResponse`; phẳng theo D2 |
| C7 | `dto/response/EventRefundProgressView` | NEW | `record (UUID eventId, UUID cancellationId, boolean selectionComplete, long selectedOrderCount, long pendingCount, long refundedCount, long needsAttentionCount, Instant completedAt, Instant asOf)` | Record |
| C8 | `dto/response/PageResponse<T>` | NEW | `record (List<T> items, int page, int size, long totalElements, int totalPages)` | `Shared/response/PageResponse` (Cinema dùng `@Builder`; đích dùng record thuần như các DTO khác) |
| C9 | `entity/Refund` | EXISTING | Thêm field `settledBy` (`settled_by`, `nonempty_text`, nullable), `externalReference` (`external_reference`, `nonempty_text`, nullable); getter/setter sinh bởi `@Getter @Setter` sẵn có | Theo B12 v0.3 |
| C10 | `entity/RefundAttempt`, `EventRefundRun`, `EventRefundItem`, `EventFinanceSnapshot` | EXISTING | Thêm factory static (M5–M8); không thêm field | Theo `Refund.pending` của đích |
| C11 | `config/VNPayConfig` | EXISTING | Thêm `@Value` field: `apiUrl` (`vnpay.api-url`), `serverIp` (`vnpay.server-ip:127.0.0.1`), `refundCreateBy` (`vnpay.refund-create-by:flash-ticket`); getter sinh bởi `@Getter` | `Config/VNPayConfig` |
| C12 | `exception/PaymentErrorCode` | EXISTING | Thêm `REFUND_ALREADY_SUCCEEDED(409)`, `REFUND_NOT_FAILED(409)`, `REFUND_ATTEMPT_IN_FLIGHT(409)` | `ErrorCode` |
| C13 | Test: `service/RefundFlowIntegrationTest`, `client/VNPayRefundClientTest` | NEW | `@SpringBootTest @ActiveProfiles("payment-flow-test")` với `@MockitoBean VNPayRefundClient, BookingPurchaseClient`; client test dùng `MockRestServiceServer` | Theo `PaymentFlowIntegrationTest`, `BookingPurchaseClientTest` của đích |

Không tạo class worker/listener/controller mới: dùng `PaymentWorker`, `PaymentMessageListener`, `RefundController`, `RabbitMqConfig`, `PaymentRuntimeConfig`, `PaymentSecurityConfiguration`, `MessageInvariantValidator`, `RefundMapper` hiện có.

## 5. Constructor/method (gate duyệt)

| ID | Class | Chữ ký | NEW/REUSE | Cách viết và lý do |
|---|---|---|---|---|
| M1 | `VNPayRefundClient` | `public RefundGatewayResult refund(RefundGatewayRequest request)` | NEW | POST JSON `vnp_Command=refund`, `vnp_TransactionType=02`; ký theo thứ tự VNPay; timeout/`RestClientException` → `UNKNOWN` |
| M2 | `VNPayRefundClient` | `public RefundGatewayResult queryRefund(RefundGatewayRequest request)` | NEW | `querydr` với `vnp_RequestId` mới mỗi lần; ánh xạ theo bảng §2.1 |
| M3 | `VNPayRefundClient` | `private boolean verifyResponseHash(Map<String, String> response, List<String> fieldOrder)` | NEW | `VNPayUtil.hmacSHA512` trên chuỗi nối bằng dấu gạch đứng |
| M4 | `VNPayRefundClient` | `private RefundGatewayOutcome toOutcome(String responseCode, String transactionType, String transactionStatus, boolean query)` | NEW | Bảng ánh xạ §2.1 |
| M5 | `RefundAttempt` | `public static RefundAttempt started(UUID refundId, String requestKey, Instant startedAt)` | NEW | Factory như `Refund.pending` |
| M6 | `EventRefundRun` | `public static EventRefundRun started(UUID eventId, UUID cancellationId, Instant startedAt)` | NEW | `selectionComplete=false`, `selectedOrderCount=0` |
| M7 | `EventRefundItem` | `public static EventRefundItem selected(UUID runId, UUID eventId, UUID orderId, UUID confirmedChargeId, Instant updatedAt)` | NEW | State `SELECTED` |
| M8 | `EventFinanceSnapshot` | `public static EventFinanceSnapshot cancelledMarker(UUID eventId, UUID cancellationId, Instant cancelledAt, Instant receivedAt)` | NEW | Marker trước khi có phí (B14 §5) |
| M9 | `PaymentRuntimeConfig` | `@Bean public RestClient vnpayRestClient(RestClient.Builder builder)` | NEW | Base URL `vnpay.api-url`, timeout `@Value` `payment.vnpay.connect-timeout`/`read-timeout`; không gắn OAuth2 interceptor |
| M10 | `RabbitMqConfig` | `paymentSagaTopology()` | REUSE (sửa thân) | Thêm queue `payment.event-cancelled.q` (`@Value payment.queues.event-cancelled`) bind `event.cancelled.v1` |
| M11 | `PaymentSecurityConfiguration` | `paymentSecurityFilterChain(...)` | REUSE (sửa thân) | `GET/POST /api/v1/admin/refunds/**`, `GET /api/v1/admin/events/*/refund-progress` → `hasRole("ADMIN")` |
| M12 | `MessageInvariantValidator` | `public void validateEventCancellation(Envelope<EventCancellation> envelope)` | NEW | `aggregateId == eventId`, `correlationId == eventId`, sales/access cùng `eventId` và `CANCELLED` |
| M13 | `PaymentMessageListener` | `@RabbitListener(queues = "${payment.queues.event-cancelled:payment.event-cancelled.q}", ackMode = "MANUAL") public void onEventCancelled(Message message, Channel channel) throws IOException` | NEW | Cùng khung ack/nack với `onPaymentDecision` |
| M14 | `PaymentWorker` | `@Scheduled(fixedDelayString = "${payment.refund.poll-delay:5000}", initialDelayString = "${payment.worker.initial-delay:0}") public void sendRefunds()` | NEW | Mẫu `recoverSagas`; lỗi một refund không chặn refund khác |
| M15 | `PaymentWorker` | `@Scheduled(...) public void pollRefunds()` | NEW | Như M14 |
| M16 | `PaymentWorker` | `@Scheduled(...) public void selectEventRefundOrders()` | NEW | Như M14 |
| M17 | `RefundService` / `Impl` | `void recordEventCancellation(Envelope<EventCancellation> incomingEnvelope)` | NEW | `@Transactional`; inbox + marker + run |
| M18 | `RefundService` / `Impl` | `List<UUID> findEventRefundRunsToSelect(int limit)` | NEW | `@Transactional(readOnly = true)` |
| M19 | `RefundService` / `Impl` | `void selectEventRefundOrders(UUID runId, int limit)` | NEW | `@Transactional`; một lô |
| M20 | `RefundService` / `Impl` | `List<UUID> findRefundsToSend(int limit)` | NEW | `@Transactional(readOnly = true)`; `PENDING` theo `createdAt` |
| M21 | `RefundService` / `Impl` | `List<UUID> findRefundsToPoll(Instant startedBefore, int limit)` | NEW | `@Transactional(readOnly = true)` |
| M22 | `RefundService` / `Impl` | `void sendRefund(UUID refundId)` | NEW | `@Transactional(propagation = NEVER)`; TX1 → client → TX2 qua `paymentTransactionTemplate` |
| M23 | `RefundService` / `Impl` | `void pollRefund(UUID refundId)` | NEW | `@Transactional(propagation = NEVER)`; TX đọc → `queryRefund` → TX ghi |
| M24 | `RefundService` / `Impl` | `PageResponse<RefundAdminView> getRefunds(RefundState state, int page, int size)` | NEW | `@Transactional(readOnly = true)`; `PageRequest` + `Sort` theo `createdAt` như `CinemaService.getAllCinemas` |
| M25 | `RefundService` / `Impl` | `RefundAdminView retryRefund(UUID refundId)` | NEW | `@Transactional` |
| M26 | `RefundService` / `Impl` | `RefundAdminView settleRefundManually(UUID refundId, ManualSettlementRequest request, String adminSubject)` | NEW | `@Transactional` |
| M27 | `RefundService` / `Impl` | `EventRefundProgressView getEventRefundProgress(UUID eventId)` | NEW | `@Transactional(readOnly = true)` |
| M28 | `RefundServiceImpl` | `private RefundGatewayRequest claimRefund(UUID refundId)` | NEW | Trả `null` khi không còn claim được |
| M29 | `RefundServiceImpl` | `private RefundGatewayRequest findUnfinishedAttempt(UUID refundId)` | NEW | Dữ liệu cho `querydr` |
| M30 | `RefundServiceImpl` | `private void recordGatewayResult(UUID refundId, UUID attemptId, RefundGatewayResult result)` | NEW | TX2; bỏ qua attempt đã kết thúc |
| M31 | `RefundServiceImpl` | `private Refund lockRefund(UUID refundId)` | NEW | Khóa marker → snapshot → saga → refund theo B14 §1 |
| M32 | `RefundServiceImpl` | `private void completeRefund(Refund refund, Instant completedAt)` | NEW | Hậu quả thành công §2.4 |
| M33 | `RefundServiceImpl` | `private void completeRunIfFinished(UUID runId, Instant now)` | NEW | `completed_at` khi đủ điều kiện |
| M34 | `RefundServiceImpl` | `private RefundAdminView toAdminView(Refund refund)` | NEW | Lấy attempt mới nhất rồi gọi M35 |
| M35 | `RefundMapper` | `@Mapping(target = "refundId", source = "refund.id") ... RefundAdminView toAdminView(Refund refund, RefundAttempt lastAttempt)` | NEW | MapStruct hai nguồn; tiền qua `MoneyMapper.moneyToWire` |
| M36 | `RefundController` | `@GetMapping("/refunds") @PreAuthorize("hasRole('ADMIN')") public ApiResponse<PageResponse<RefundAdminView>> getRefunds(@RequestParam(defaultValue = "FAILED") RefundState state, @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size)` | NEW | `@RequestMapping("/api/v1/admin")`, `@Tag`, `@Operation(summary, description)`, `@SecurityRequirement`; `ApiResponse.success` của đích |
| M37 | `RefundController` | `@PostMapping("/refunds/{refundId}/retry") ... public ApiResponse<RefundAdminView> retryRefund(@PathVariable UUID refundId)` | NEW | Như M36 |
| M38 | `RefundController` | `@PostMapping("/refunds/{refundId}/manual-settlement") ... public ApiResponse<RefundAdminView> settleRefundManually(@PathVariable UUID refundId, @Valid @RequestBody ManualSettlementRequest request, @AuthenticationPrincipal Jwt jwt)` | NEW | Như M36 |
| M39 | `RefundController` | `@GetMapping("/events/{eventId}/refund-progress") ... public ApiResponse<EventRefundProgressView> getEventRefundProgress(@PathVariable UUID eventId)` | NEW | Như M36 |
| M40 | `RefundRepository` | `@Lock(PESSIMISTIC_WRITE) @Query("select r from Refund r where r.id = :id") Optional<Refund> findForUpdateById(@Param("id") UUID id)`; `@Query("select r.id from Refund r where r.state = :state order by r.createdAt, r.id") List<UUID> findIdsByState(@Param("state") RefundState state, Pageable pageable)`; `Page<Refund> findAllByState(RefundState state, Pageable pageable)`; `@Query` JPQL `List<UUID> findIdsToPoll(@Param("startedBefore") Instant startedBefore, Pageable pageable)` (refund `PROCESSING` có attempt `finishedAt is null` bắt đầu trước mốc) | NEW | Query theo tên method và JPQL như `CinemaRepository`/`PaymentSagaRepository` |
| M41 | `RefundAttemptRepository` | `Optional<RefundAttempt> findFirstByRefundIdAndFinishedAtIsNull(UUID refundId)`; `Optional<RefundAttempt> findFirstByRefundIdOrderByStartedAtDescIdDesc(UUID refundId)`; `boolean existsByRefundIdAndFinishedAtIsNull(UUID refundId)` | NEW | Query theo tên |
| M42 | `EventRefundRunRepository` | `Optional<EventRefundRun> findBySourceCancellationId(UUID cancellationId)`; `Optional<EventRefundRun> findFirstByEventIdOrderByStartedAtDesc(UUID eventId)`; `@Query("select r.id from EventRefundRun r where r.selectionComplete = false order by r.startedAt") List<UUID> findIdsToSelect(Pageable pageable)`; `@Lock(PESSIMISTIC_WRITE) @Query(...) Optional<EventRefundRun> findForUpdateById(@Param("id") UUID id)` | NEW | Như trên |
| M43 | `EventRefundItemRepository` | `Optional<EventRefundItem> findFirstByRefundIdOrderByUpdatedAtDesc(UUID refundId)`; `long countByIdRunIdAndState(UUID runId, EventRefundItemState state)`; `boolean existsByIdRunIdAndStateNot(UUID runId, EventRefundItemState state)` | NEW | Query theo tên trên `@EmbeddedId` |
| M44 | `PaymentConfirmationRepository` | `@Query("""select c.orderId from PaymentConfirmation c, OrderPaymentSnapshot s where s.orderId = c.orderId and s.eventId = :eventId and (:cursor is null or c.orderId > :cursor) order by c.orderId""") List<UUID> findOrderIdsByEventIdAfter(@Param("eventId") UUID eventId, @Param("cursor") UUID cursor, Pageable pageable)` | NEW | JPQL text block như `CinemaRepository` |

`ensureRefund` (M có sẵn) được tái sử dụng nguyên chữ ký.

## 6. Test (gate duyệt)

| ID | Class | Method | Kiểm điều gì |
|---|---|---|---|
| T1 | `RefundFlowIntegrationTest` | `void setUp()` (`@BeforeEach`) | Xóa dữ liệu, dựng snapshot/charge/saga như `PaymentFlowIntegrationTest` |
| T2 | 〃 | `void sendsFullRefundOnceAndCompensatesSaga()` | `PENDING` → `SUCCEEDED`; một attempt; saga `COMPENSATED` |
| T3 | 〃 | `void keepsRefundProcessingUntilQueryConfirms()` | `UNKNOWN` → poll `SUCCEEDED`; không gọi `refund` lần hai |
| T4 | 〃 | `void marksRefundFailedAndAllowsAdminRetry()` | `FAILED`, item `NEEDS_ATTENTION`; retry → `PENDING` → gửi lại cùng refund |
| T5 | 〃 | `void settlesFailedRefundManuallyOnce()` | Manual → `SUCCEEDED`, lưu actor/reference; lặp cùng reference trả cũ; reference khác → 409 |
| T6 | 〃 | `void rejectsManualSettlementWhileAttemptInFlight()` | `REFUND_ATTEMPT_IN_FLIGHT`, `REFUND_NOT_FAILED` |
| T7 | 〃 | `void keepsDuplicateRefundAwayFromSaga()` | Refund thu thừa thành công không đổi saga |
| T8 | 〃 | `void selectsConfirmedOrdersForEventCancellationRun()` | Một run/cancellation; chỉ đơn có confirmation; saga `COMPLETED` → `COMPENSATING`; cursor qua nhiều lô |
| T9 | 〃 | `void completesRunAfterAllItemsRefunded()` | `completed_at` chỉ khi hết item chưa `REFUNDED` |
| T10 | 〃 | `void ignoresDuplicateEventCancellationMessage()` | Message lặp không tạo run/marker mới |
| T11 | 〃 | `void reportsEventRefundProgress()` | Đếm chờ/đã hoàn/cần xử lý |
| T12 | `VNPayRefundClientTest` | `void signsRefundRequestInDocumentedOrder()` | Thứ tự trường hash và `vnp_TransactionDate` lấy từ payment URL |
| T13 | 〃 | `void mapsRefundAndQueryResponseCodes()` | Bảng §2.1 |
| T14 | 〃 | `void treatsTimeoutAsUnknown()` | Timeout → `UNKNOWN` |
| T15 | 〃 | `void rejectsResponseWithInvalidSignature()` | Chữ ký sai → `UNKNOWN` |
| T16 | `PaymentJwtSecurityTest` (EXISTING) | `void requiresAdminRoleForRefundAdministration()` | BUYER 403, ADMIN qua |
| T17 | `MessageInvariantValidatorTest` (EXISTING) | `void rejectsEventCancellationForDifferentAggregate()` | M12 |

## 7. Điểm khác mẫu Cinema Booking

- Gọi API hoàn VNPay qua `RestClient` và chia ba bước TX1 → gọi ngoài → TX2: Cinema không có; đây là yêu cầu B14 §4 (không gọi cổng trong transaction, không gửi lại khi chưa rõ kết quả).
- Khóa bi quan theo thứ tự B14 và inbox/outbox: giữ cơ chế đã có của payment-service.
- Interface + impl cho `RefundService` (D4), envelope `ApiResponse.success(...)`, `PaymentErrorCode`/`PaymentException` thay `AppException`/`ErrorCode`: giữ convention đã có của đích.
- Không thêm `ApiPaths`: payment-service đang hard-code `/api/v1` trong `PaymentController`; giữ như hiện có.

## 8. Nhật ký duyệt

| Pha | Danh sách | Trạng thái | Nguồn |
|---|---|---|---|
| BRAINSTORMING | C1–C13, M1–M44, T1–T17, D1–D4 (bản 2026-11-03) | APPROVED | User trả lời "Duyệt hết" cho câu hỏi duyệt toàn bộ danh sách và D1–D4 theo đề xuất, 2026-11-03 |

Pha WRITING-PLANS và EXECUTING-PLANS vẫn phải hỏi duyệt lại theo gate riêng.
