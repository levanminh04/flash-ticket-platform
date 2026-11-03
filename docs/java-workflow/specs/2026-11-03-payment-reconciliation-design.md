# Payment — tóm tắt tài chính, cấu hình phí, đối soát và chi trả

- Phase: BRAINSTORMING
- Trạng thái: **APPROVED** (BRAINSTORMING) — D1–D8 theo đề xuất; xem nhật ký duyệt §8
- Ngày: 2026-11-03
- Phạm vi: `event.finance-configured.v1` (consumer), `GET /internal/v1/events/{id}/financial-summary` (event-service đang gọi), `UC-17` báo cáo đối soát, `UC-18` xác nhận đối soát, `UC-19` đánh dấu đã chi trả.
- Nguồn: B3 §4.5; B6 §6.10 (`UC-19`), §3.3 (`UC-17`, `UC-18`); B7 §4.5 (`Hồ sơ chi trả sự kiện`, `INV-10`); B8 `FR-11`, `FR-40`–`FR-43`; B12 `event_finance_snapshots`, `event_payouts`; B13 §5.2, §5.3; B14 §8; B15 V19; `BIZ-034`–`BIZ-038`, `BIZ-050`–`BIZ-054`, `BIZ-059`, `BIZ-142`, `BIZ-159`, `GOV-140`.
- Code đã đọc: payment `FinanceService`/`FinanceServiceImpl`/`FinanceController` (rỗng), `EventFinanceSnapshot`, `EventPayout`, `RefundServiceImpl`, `PaymentMessageListener`, `RabbitMqConfig`, `PaymentSecurityConfiguration`, `MoneyMapper`; event-service `HttpEventSummaries.financial`, `EventSummaries.Financial`, `CancellationService`, `StatisticsService`, `EventService.publish`, `EventSnapshots.finance`.
- Mẫu Cinema Booking: `Controller/CinemaController` (controller, `@PreAuthorize`), `Service/Booking/BookingService.createBooking` (`@Transactional`, tiền `BigDecimal`), `Shared/response/ApiResponse`.

## 1. Hiện trạng

| Có | Thiếu |
|---|---|
| Bảng `event_finance_snapshots`, `event_payouts` + entity | Không code nào ghi phí/mốc kết thúc; không code nào tạo `EventPayout` |
| Queue `payment.finance-configured.q` đã deploy (bind `event.finance-configured.v1`) | Không có consumer → message dồn |
| event-service gọi `GET /internal/v1/events/{id}/financial-summary` khi hủy (`CancellationService.direct/request`) và thống kê (`StatisticsService`) | Payment trả 403 (`denyAll`) → event-service báo 503, không phân loại được hủy trực tiếp/yêu cầu hủy |
| `FinanceService`/`FinanceController` | Rỗng |

Event-service publish `event.finance-configured.v1` lúc **công bố** sự kiện (`EventService.publish`), `sourceFeeVersion = configVersion` tại lúc duyệt; data: `eventId, organizerSubject, eventEndsAt, feeRate, sourceFeeVersion, currency`.

## 2. Luồng chính

### 2.1 Nhận cấu hình phí

`PaymentMessageListener.onFinanceConfigured` (queue `payment.finance-configured.q`) → `MessageInvariantValidator.validateFinanceConfiguration` → `FinanceService.recordFinanceConfiguration`, một TX:

1. Khóa `event_finance_snapshots` theo `eventId` (thứ tự khóa B14 §1).
2. `recordInbox("payment.finance-configured.v1", ...)` false → dừng.
3. Chưa có dòng → tạo `EventFinanceSnapshot.configured(...)`. Có dòng chỉ là marker hủy (phí null) → điền phí, giữ `cancellationId`/`cancelledAt`. Có phí rồi → áp theo D5.

### 2.2 Sổ cái đối soát (tính khi đọc, không lưu riêng)

Tất cả tính trên các đơn có `order_payment_snapshots.event_id = eventId`:

| Giá trị | Cách tính | Nguồn |
|---|---|---|
| `validGross` | Tổng `payment_confirmations.amount` | Khoản thu hợp lệ (đã sau khuyến mãi); thu thừa không có confirmation nên không tính (`BIZ-013`) |
| `validRefunds` | Tổng `refunds.amount` `SUCCEEDED` có `charge_id` = charge đã confirmation | Hoàn thu thừa/đến muộn không trừ vì chưa từng vào gross |
| `feeAmount` | `(validGross − validRefunds) × feeRate`, `setScale(0, HALF_UP)` | Một lần trên toàn sự kiện (`GOV-140`, `BIZ-142`) |
| `netAmount` | `validGross − validRefunds − feeAmount` | `BIZ-036` |
| `paymentPendingCount` | Số `payment_attempts` có `finished_at is null` | D7 |
| `refundPendingCount` | Số `refunds` có `state <> SUCCEEDED` (gồm `FAILED`, mọi nguyên nhân) | `BIZ-052`, `BIZ-159` |
| `hasCollectedOrders` | Có ít nhất một `charges` | D2 |

### 2.3 `financial-summary` cho event-service

`GET /internal/v1/events/{eventId}/financial-summary`, role `INTERNAL_SERVICE` (như `/internal/v1/**` của Booking). Trả **JSON phẳng, không bọc `ApiResponse`** vì `HttpEventSummaries` đọc thẳng `EventSummaries.Financial` (D6):

`{schemaVersion:1, eventId, organizerSubject, asOf, hasCollectedOrders, currency:"VND", validGross, validRefunds, feeAmount, netAmount}` — tiền là chuỗi số nguyên qua `MoneyMapper.toWire`. Event-service kiểm `gross − refunds − fee = net` và `!hasCollectedOrders ⇒ gross = 0`; công thức §2.2 thỏa cả hai. Chưa có cấu hình phí → D1.

### 2.4 UC-17 — xem báo cáo đối soát

`GET /api/v1/events/{eventId}/reconciliation`, role `ADMIN` hoặc `ORGANIZER`. Organizer chỉ xem sự kiện có `organizerSubject` trùng subject JWT, sai → `404 RESOURCE_NOT_FOUND`. Trả `EventReconciliationView`: sổ cái §2.2, `feeRate`, `sourceFeeVersion`, `eventEndsAt`, `cancelled`, cờ `eligibleForReconciliation`, `reconciledBy/At/Evidence`, `paidBy/At`, `asOf`.

### 2.5 UC-18 — xác nhận đối soát

`POST /api/v1/admin/events/{eventId}/reconciliation` body `{evidence}`. Một TX, khóa marker trước:

- Chưa có phí → `409 FINANCE_NOT_CONFIGURED`; sự kiện đã hủy → `409 EVENT_CANCELLED` (D3); `now < eventEndsAt` → `409 EVENT_NOT_ENDED`; `paymentPendingCount > 0` → `409 PAYMENT_PENDING`; `refundPendingCount > 0` → `409 REFUND_PENDING`; đã `PAID` → `409 PAYOUT_ALREADY_PAID`.
- Hợp lệ → tạo hoặc ghi đè `EventPayout` (D4): số tiền, phí, hai bộ đếm (= 0), `asOf`, `reconciledBy` = subject admin, `reconciledAt`, `reconciliationEvidence`.

### 2.6 UC-19 — đánh dấu đã chi trả

`POST /api/v1/admin/events/{eventId}/payout` `{}`. Một TX, khóa marker rồi payout:

- Chưa xác nhận đối soát → `409 RECONCILIATION_REQUIRED`; đã `PAID` → `409 PAYOUT_ALREADY_PAID` (`INV-10`, đúng một lần).
- Tính lại sổ cái; khác số đã chốt hoặc bộ đếm > 0 → `409 RECONCILIATION_STALE` (phải xác nhận lại). Cùng các guard của §2.5.
- Hợp lệ → `paidBy`, `paidAt`. CHECK B12 bảo đảm `paid_at ≥ reconciled_at`, `≥ event_ends_at`, hai bộ đếm = 0.

Chuyển tiền thật diễn ra ngoài hệ thống; Payment không gọi ngân hàng.

## 3. Quyết định cần user chọn

| ID | Vấn đề | Đề xuất |
|---|---|---|
| D1 | Chưa có `event_finance_snapshots` có phí (sự kiện chưa công bố) thì Payment không biết `organizerSubject` mà event-service bắt khớp | **Đã chốt (user, 2026-11-03):** Payment trả `404 RESOURCE_NOT_FOUND`, không bịa organizer. Event-service **không gọi** `financial-summary` khi sự kiện chưa `PUBLISHED`: chưa công bố thì chưa thể có đơn/khoản thu, nên hủy trực tiếp luôn; chỉ sự kiện đã `PUBLISHED` mới gọi Payment. Xem §4.1 (E1) |
| D2 | Nghĩa "đã có đơn thu tiền" cho `hasCollectedOrders` | **Có bất kỳ charge nào** của đơn thuộc sự kiện (kể cả muộn/trùng), vì đã có tiền phải hoàn |
| D3 | Sự kiện đã hủy có đi đối soát/chi trả không | **Đã chốt (user, 2026-11-03):** không — UC-18/19 trả `409 EVENT_CANCELLED`. Admin vẫn xem được đã hoàn hết chưa qua báo cáo UC-17 (`validRefunds`, `refundPendingCount`, `cancelled = true`) và API tiến độ lô `GET /api/v1/admin/events/{eventId}/refund-progress` (UC-16, đã có) |
| D4 | Xác nhận đối soát lại trước khi `PAID` | **Cho phép**, ghi đè số chốt và người/thời điểm/bằng chứng; sau `PAID` thì không (`BIZ-054`) |
| D5 | `finance-configured` đến lần nữa | Version nhỏ hơn hoặc bằng → bỏ qua; lớn hơn → cập nhật phí/organizer/end **trừ khi đã có `EventPayout` đã đối soát** (phí đã chốt, `log.warn`); không bao giờ xóa marker hủy |
| D6 | Định dạng `financial-summary` | **JSON phẳng** đúng `EventSummaries.Financial`, ngoại lệ so với `ApiResponse` của Cinema; lỗi trả `Problem` như hiện có |
| D7 | Nghĩa "lần thanh toán đang chờ" | `payment_attempts.finished_at is null` (PENDING/UNKNOWN) |
| D8 | B13 chưa có hợp đồng cho UC-17–19 | **Thêm B13 §3.2** theo §2.4–§2.6 và chuẩn hóa §5.3 `financial-summary` |

## 4. Class/file (gate duyệt)

| ID | Class/file | NEW/EXISTING | Vai trò, annotation, API sinh | Mẫu hoặc ngoại lệ |
|---|---|---|---|---|
| C1 | `@/dto/response/EventFinancialSummary` | NEW | `record (Integer schemaVersion, UUID eventId, String organizerSubject, Instant asOf, Boolean hasCollectedOrders, String currency, @WireMoney String validGross, @WireMoney String validRefunds, @WireMoney String feeAmount, @WireMoney String netAmount)` | Record; khớp `EventSummaries.Financial` |
| C2 | `@/dto/response/EventReconciliationView` | NEW | `record (UUID eventId, String organizerSubject, String currency, @WireMoney String validGross, @WireMoney String validRefunds, @WireMoney String feeAmount, @WireMoney String netAmount, String feeRate, Long sourceFeeVersion, Instant eventEndsAt, boolean cancelled, long paymentPendingCount, long refundPendingCount, boolean eligibleForReconciliation, String reconciledBy, Instant reconciledAt, String reconciliationEvidence, String paidBy, Instant paidAt, Instant asOf)` | `CinemaResponse` |
| C3 | `@/dto/request/ReconciliationRequest` | NEW | `record (@NotBlank(message = "INVALID_REQUEST") @Size(max = 1000, message = "INVALID_REQUEST") String evidence)` | `CreateCinemaRequest` |
| C4 | `@/entity/EventFinanceSnapshot` | EXISTING | Thêm factory M5 | `Refund.pending` |
| C5 | `@/entity/EventPayout` | EXISTING | Thêm factory M6; setter sinh bởi `@Setter` sẵn có | 〃 |
| C6 | `@/exception/PaymentErrorCode` | EXISTING | `+ FINANCE_NOT_CONFIGURED`, `EVENT_CANCELLED`, `EVENT_NOT_ENDED`, `PAYMENT_PENDING`, `REFUND_PENDING`, `RECONCILIATION_REQUIRED`, `RECONCILIATION_STALE`, `PAYOUT_ALREADY_PAID` — tất cả `HttpStatus.CONFLICT` | `ErrorCode` |
| C7 | `@/service/FinanceService`, `@/service/impl/FinanceServiceImpl`, `@/controller/FinanceController` | EXISTING (rỗng) | Thêm method M7–M24; impl `@Service @RequiredArgsConstructor @Slf4j`, field `private final`; controller theo `PaymentController` (`@RequestMapping("/api/v1")`, `@FieldDefaults`, `@Tag`, `@Operation` tiếng Việt) | Giữ cặp interface/impl của đích |
| C8 | Test `service/FinanceFlowIntegrationTest` | NEW | `@SpringBootTest @ActiveProfiles("payment-flow-test")`, `@MockitoBean BookingPurchaseClient`, `@MockitoBean VNPayRefundClient`; exclude khỏi surefire mặc định như `RefundFlowIntegrationTest` | `RefundFlowIntegrationTest` |

Không tạo class worker/listener mới: dùng `PaymentMessageListener`, `RabbitMqConfig`, `PaymentSecurityConfiguration`, `MessageInvariantValidator`.

### 4.1 Thay đổi bên event-service theo D1

| ID | Class | Thay đổi | Cách viết |
|---|---|---|---|
| E1 | `event-service/.../application/CancellationService.direct` | Sửa thân | Sự kiện chưa `PUBLISHED` → gọi `transactions.cancel(id, actor, expected, true)` (overload có sẵn, `asOf = null`), không gọi `summaries.financial`. Đã `PUBLISHED` → giữ nguyên luồng hiện tại |
| E2 | `event-service/.../application/CancellationService.request` | Sửa thân | Sự kiện chưa `PUBLISHED` → `EventProblem.conflict("DIRECT_CANCELLATION_AVAILABLE")` mà không gọi Payment (chưa có đơn thu tiền nên không cần yêu cầu admin duyệt) |

Không thêm class/method ở main. An toàn với publish đồng thời: `CancellationTransactions.cancel` kiểm lại `rowVersion`, publish làm tăng version nên hủy cũ bị `STALE_EVENT_VERSION`. Comment tiếng Việt ngắn ghi lý do bỏ gọi Payment.

## 5. Method (gate duyệt)

| ID | Class | Chữ ký | NEW/REUSE | Cách viết |
|---|---|---|---|---|
| M1 | `MessageInvariantValidator` | `public void validateFinanceConfiguration(Envelope<FinanceConfiguration> envelope)` | NEW | `messageType = "event.finance-configured.v1"`, `aggregateId = eventId`, `feeRate` parse được trong [0,1] → `fail("$", "FINANCE_CONFIGURATION_MISMATCH")` |
| M2 | `PaymentMessageListener` | `@RabbitListener(queues = "${payment.queues.finance-configured:payment.finance-configured.q}", ackMode = "MANUAL") public void onFinanceConfigured(Message message, Channel channel) throws IOException` | NEW | Khung `onEventCancelled`; field mới `private final FinanceService financeService` |
| M3 | `RabbitMqConfig` | `paymentSagaTopology()` | REUSE (sửa thân) | Thêm queue quorum `payment.finance-configured.q` bind `event.finance-configured.v1` |
| M4 | `PaymentSecurityConfiguration` | `paymentSecurityFilterChain(...)` | REUSE (sửa thân) | `GET /internal/v1/events/*/financial-summary` → `INTERNAL_SERVICE`; `GET /api/v1/events/*/reconciliation` → `ADMIN`/`ORGANIZER`; `POST /api/v1/admin/events/*/reconciliation`, `/payout` → `ADMIN` |
| M5 | `EventFinanceSnapshot` | `public static EventFinanceSnapshot configured(UUID eventId, String organizerSubject, Instant eventEndsAt, BigDecimal feeRate, Long sourceFeeVersion, Instant receivedAt)` | NEW | Factory |
| M6 | `EventPayout` | `public static EventPayout forEvent(EventFinanceSnapshot finance)` | NEW | Copy event/organizer/feeRate/version/end, `currency = "VND"` |
| M7 | `FinanceService`/`Impl` | `void recordFinanceConfiguration(Envelope<FinanceConfiguration> incomingEnvelope)` | NEW | `@Transactional`; §2.1, D5 |
| M8 | 〃 | `EventFinancialSummary getFinancialSummary(UUID eventId)` | NEW | `@Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)` như `PaymentService.getPayment`; D1 |
| M9 | 〃 | `EventReconciliationView getReconciliation(UUID eventId, String actorSubject, boolean admin)` | NEW | `@Transactional(readOnly = true, isolation = REPEATABLE_READ)`; ownership §2.4 |
| M10 | 〃 | `EventReconciliationView confirmReconciliation(UUID eventId, ReconciliationRequest request, String adminSubject)` | NEW | `@Transactional`; §2.5 |
| M11 | 〃 | `EventReconciliationView markEventPaid(UUID eventId, String adminSubject)` | NEW | `@Transactional`; §2.6 |
| M12 | `FinanceServiceImpl` | `private EventReconciliationView buildReconciliation(EventFinanceSnapshot finance, EventPayout payout)` | NEW | Chạy 5 query §2.2, tính phí HALF_UP, dựng view |
| M13 | `FinanceServiceImpl` | `private void requireReconcilable(EventReconciliationView ledger)` | NEW | Ném đúng mã lỗi §2.5 theo thứ tự trong bảng |
| M14 | `FinanceServiceImpl` | `private EventFinanceSnapshot lockConfiguredFinance(UUID eventId)` | NEW | `findForUpdateByEventId`; không có hoặc phí null → `FINANCE_NOT_CONFIGURED` |
| M15 | `FinanceController` | `@GetMapping("/internal/v1/events/{eventId}/financial-summary") public EventFinancialSummary getFinancialSummary(@PathVariable UUID eventId)` | NEW | Không `ApiResponse` (D6); `@Operation` tiếng Việt; không `@SecurityRequirement` bearer người dùng |
| M16 | `FinanceController` | `@GetMapping("/api/v1/events/{eventId}/reconciliation") @PreAuthorize("hasAnyRole('ADMIN','ORGANIZER')") public ApiResponse<EventReconciliationView> getReconciliation(@PathVariable UUID eventId, @AuthenticationPrincipal Jwt jwt, Authentication authentication)` | NEW | `admin` = authority `ROLE_ADMIN` |
| M17 | `FinanceController` | `@PostMapping("/api/v1/admin/events/{eventId}/reconciliation") @PreAuthorize("hasRole('ADMIN')") public ApiResponse<EventReconciliationView> confirmReconciliation(@PathVariable UUID eventId, @Valid @RequestBody ReconciliationRequest request, @AuthenticationPrincipal Jwt jwt)` | NEW | `log.info` eventId |
| M18 | `FinanceController` | `@PostMapping("/api/v1/admin/events/{eventId}/payout") @PreAuthorize("hasRole('ADMIN')") public ApiResponse<EventReconciliationView> markEventPaid(@PathVariable UUID eventId, @AuthenticationPrincipal Jwt jwt)` | NEW | `log.info` eventId |
| M19 | `PaymentConfirmationRepository` | `@Query("""select coalesce(sum(c.amount), 0) from PaymentConfirmation c, OrderPaymentSnapshot s where s.orderId = c.orderId and s.eventId = :eventId""") BigDecimal sumAmountByEventId(@Param("eventId") UUID eventId)` | NEW | JPQL text block |
| M20 | `RefundRepository` | `@Query` tổng `r.amount` refund `SUCCEEDED` có `r.chargeId` = `c.chargeId` của confirmation thuộc event → `BigDecimal sumSucceededConfirmedAmountByEventId(@Param("eventId") UUID eventId)` | NEW | 〃 |
| M21 | `RefundRepository` | `@Query` đếm refund `state <> SUCCEEDED` của đơn thuộc event → `long countUnfinishedByEventId(@Param("eventId") UUID eventId)` | NEW | 〃 |
| M22 | `PaymentAttemptRepository` | `@Query` đếm attempt `finishedAt is null` của đơn thuộc event → `long countUnfinishedByEventId(@Param("eventId") UUID eventId)` | NEW | 〃 |
| M23 | `ChargeRepository` | `@Query("select count(c) > 0 from Charge c, OrderPaymentSnapshot s where s.orderId = c.orderId and s.eventId = :eventId") boolean existsByEventId(@Param("eventId") UUID eventId)` | NEW | 〃 |
| M24 | `EventPayoutRepository` | `@Lock(PESSIMISTIC_WRITE) @Query("select p from EventPayout p where p.eventId = :eventId") Optional<EventPayout> findForUpdateByEventId(@Param("eventId") UUID eventId)` | NEW | Như `EventFinanceSnapshotRepository` |
| M25 | YAML | `payment.queues.finance-configured` trong `application.yaml`, test yaml | — | Không phải method |

## 6. Test (gate duyệt)

| ID | Class | Method | Kiểm |
|---|---|---|---|
| T1 | `FinanceFlowIntegrationTest` | `void setUp()` (`@BeforeEach`) | TRUNCATE như `RefundFlowIntegrationTest` + `tbl_event_payouts` |
| T2 | 〃 | `private UUID seedConfirmedOrder(UUID eventId, String amount)` | Snapshot + attempt kết thúc + charge + confirmation |
| T3 | 〃 | `private Envelope<FinanceConfiguration> financeEnvelope(UUID eventId, String organizerSubject, Instant eventEndsAt, String feeRate, String sourceFeeVersion, UUID messageId)` | Message hợp lệ |
| T4 | 〃 | `void recordsFinanceConfigurationOnceAndKeepsCancellationMarker()` | Lặp message không đổi gì; marker hủy có trước được giữ |
| T5 | 〃 | `void ignoresOlderFinanceVersionAndFrozenFee()` | D5 |
| T6 | 〃 | `void summarizesValidRevenueAfterRefundsWithHalfUpFee()` | Thu thừa không vào gross; hoàn confirmed trừ; phí HALF_UP; `hasCollectedOrders` |
| T7 | 〃 | `void returnsNotFoundWithoutFinanceConfiguration()` | D1 |
| T8 | 〃 | `void rejectsReconciliationWhilePendingOrNotEnded()` | V19: chưa kết thúc, attempt dở, refund `FAILED`, thiếu phí, sự kiện hủy |
| T9 | 〃 | `void marksEventPaidOnceAfterReconciliation()` | V19: một lần paid + actor/time/evidence; lần hai `PAYOUT_ALREADY_PAID` |
| T10 | 〃 | `void requiresNewReconciliationWhenLedgerChanges()` | Refund thành công sau đối soát → `RECONCILIATION_STALE`; xác nhận lại rồi paid được |
| T11 | 〃 | `void restrictsReconciliationReportToOwnerOrAdmin()` | Organizer khác → `RESOURCE_NOT_FOUND`; chủ và admin xem được |
| T12 | `PaymentJwtSecurityTest` (EXISTING) | `void restrictsFinanceEndpointsByRole()` | BUYER 403 trên summary; `INTERNAL_SERVICE` qua; organizer không gọi được payout |
| T13 | `MessageInvariantValidatorTest` (EXISTING) | `void rejectsFinanceConfigurationForDifferentAggregate()` | M1 |
| T15 | `event-service` `EventFlowTests` (EXISTING) | `void directCancellationSkipsPaymentBeforePublication()` | Sự kiện DRAFT/APPROVED hủy trực tiếp không gọi `EventSummaries.financial` (verify mock không được gọi); `request` trả `DIRECT_CANCELLATION_AVAILABLE`; sự kiện PUBLISHED vẫn gọi summary |
| T14 | `pom.xml` | Exclude `**/FinanceFlowIntegrationTest.java` khỏi surefire mặc định | Không phải method |

## 7. Điểm khác mẫu Cinema Booking

- `financial-summary` trả JSON phẳng, không `ApiResponse` (D6) — hợp đồng nội bộ do event-service đã định.
- Sổ cái tính khi đọc bằng JPQL `sum`/`count` thay vì bảng read model riêng (B7: read model, không phải aggregate ghi).
- Khóa bi quan marker → payout theo B14 §1; interface + impl service như repo đích.

## 8. Nhật ký duyệt

| Pha | Danh sách | Trạng thái | Nguồn |
|---|---|---|---|
| BRAINSTORMING | C1–C8, M1–M25, E1–E2, T1–T15, D1–D8 | APPROVED | D1, D3 chốt riêng (xem trên); phần còn lại: user trả lời "oke viết plan đi" sau khi được giải thích D1, D3, D4, 2026-11-03. Pha WRITING-PLANS vẫn hỏi duyệt lại toàn bộ danh sách |
