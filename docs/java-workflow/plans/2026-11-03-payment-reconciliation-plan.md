# Plan — Payment: cấu hình phí, financial-summary, đối soát và chi trả

- Phase: WRITING-PLANS
- Gate: **APPROVED** (WRITING-PLANS) — user "Duyệt plan" ngày 2026-11-03
- Thiết kế: [2026-11-03-payment-reconciliation-design.md](../specs/2026-11-03-payment-reconciliation-design.md) (BRAINSTORMING APPROVED, D1–D8)
- Thư mục: `services/payment-service` (`@/` = `src/main/java/com/flashticket/payment_service/`), `services/event-service` cho E1–E2, T15

## 1. Mục tiêu

Payment nhận cấu hình phí, trả `financial-summary` cho event-service, cho admin/organizer xem báo cáo đối soát, admin xác nhận đối soát và đánh dấu đã chi trả đúng một lần. Event-service không gọi Payment khi hủy sự kiện chưa công bố.

## 2. Delta phát hiện khi đối chiếu source

| ID | Delta | Lý do |
|---|---|---|
| Y1 | `@/security/KeycloakRealmRoleConverter`: thêm `"INTERNAL_SERVICE"` vào hằng `ROLES` (sửa giá trị hằng, không thêm khai báo) | Converter chỉ giữ `BUYER`/`ORGANIZER`/`ADMIN`; thiếu thì token event-service mất role và `financial-summary` luôn 403 |
| Y2 | `getReconciliation` (M9) khi chưa có cấu hình phí → `404 RESOURCE_NOT_FOUND` (giống D1), không dùng `FINANCE_NOT_CONFIGURED` | Báo cáo cần `organizerSubject` để kiểm quyền organizer |

Không phát sinh class/method ngoài thiết kế.

## 3. Class (danh sách cuối)

Giữ nguyên C1–C8 của thiết kế §4. Ghi chú triển khai:

| ID | Ghi chú |
|---|---|
| C1 `EventFinancialSummary` | `asOf` kiểu `Instant` (Jackson ghi ISO-8601, event-service đọc chuỗi qua `ContractRules.instant`); `schemaVersion = 1` |
| C2 `EventReconciliationView` | `feeRate` là `BigDecimal.toPlainString()`; tiền qua `MoneyMapper.toWire` |
| C6 `PaymentErrorCode` | Message tiếng Anh ngắn như các mã hiện có: `"Finance configuration is missing."`, `"The event is cancelled."`, `"The event has not ended."`, `"A payment attempt is still pending."`, `"A refund is still pending."`, `"Reconciliation is required."`, `"Ledger changed since reconciliation."`, `"The event payout is already marked paid."` |
| C7 `FinanceServiceImpl` | Dependency `private final`: `EventFinanceSnapshotRepository finance`, `EventPayoutRepository payouts`, `PaymentConfirmationRepository confirmations`, `RefundRepository refunds`, `PaymentAttemptRepository attempts`, `ChargeRepository charges`, `MessagingService messaging`, `ContractCodec codec`, `MoneyMapper money`; constructor sinh bởi `@RequiredArgsConstructor` |
| C7 `FinanceController` | Field `FinanceService financeService` theo `@FieldDefaults(level = PRIVATE, makeFinal = true)` như `PaymentController`; `@RequestMapping` không đặt ở class vì có cả `/internal/v1` và `/api/v1` |
| C8 `FinanceFlowIntegrationTest` | Mẫu `RefundFlowIntegrationTest` |

## 4. Method (danh sách cuối, cách viết)

Chữ ký M1–M25 giữ nguyên thiết kế §5. Bổ sung cách viết:

| ID | Cách viết |
|---|---|
| M1 | Kiểu `validatePaymentDecision`; `feeRate` dùng `new BigDecimal(...)`, ngoài [0,1] → fail |
| M2 | Chép khung `onEventCancelled`; `codec.readEnvelope(..., FinanceConfiguration.class)` |
| M3 | `QueueBuilder.durable(financeConfiguredQueue).quorum().build()`, `@Value("${payment.queues.finance-configured:payment.finance-configured.q}")`, binding `event.finance-configured.v1` |
| M4 | Đặt trước `anyRequest().denyAll()`: `GET /internal/v1/events/*/financial-summary` → `hasRole("INTERNAL_SERVICE")`; `GET /api/v1/events/*/reconciliation` → `hasAnyRole("ADMIN","ORGANIZER")`; `POST /api/v1/admin/events/*/reconciliation`, `POST /api/v1/admin/events/*/payout` → `hasRole("ADMIN")` |
| M5 | `EventFinanceSnapshot.configured(...)`: `cancellationId`/`cancelledAt` null |
| M6 | `EventPayout.forEvent(finance)`: tiền và bộ đếm do service gán |
| M7 | Khóa `finance.findForUpdateByEventId` → `recordInbox("payment.finance-configured.v1", ...)` → không có dòng: `save(configured(...))`; marker-only: set 4 field phí; có phí: `incomingVersion <= stored` → return; `payouts.findById(eventId)` có `reconciledAt != null` → `log.warn`, return; còn lại cập nhật 4 field. Không đụng `cancellationId`/`cancelledAt` |
| M8 | `finance.findById(eventId)`, không có hoặc `feeRate == null` → `RESOURCE_NOT_FOUND`; `view = buildReconciliation(stored, payouts.findById(eventId).orElse(null))`; `new EventFinancialSummary(1, eventId, organizerSubject, view.asOf(), charges.existsByEventId(eventId), "VND", gross, refunds, fee, net)` |
| M9 | Như M8 (Y2); `!admin && !organizerSubject.equals(actorSubject)` → `RESOURCE_NOT_FOUND` |
| M10 | `stored = lockConfiguredFinance(eventId)`; `payout = payouts.findForUpdateByEventId(eventId)`; `payout.paidAt != null` → `PAYOUT_ALREADY_PAID`; `ledger = buildReconciliation(stored, payout)`; `requireReconcilable(ledger)`; payout null → `EventPayout.forEvent(stored)`; set gross/refunds/fee/net (decimal), `paymentPendingCount = 0`, `refundPendingCount = 0`, `asOf`, `feeRate`, `sourceFeeVersion`, `eventEndsAt`, `reconciledBy/At/Evidence`; `save`; trả `buildReconciliation(stored, saved)` |
| M11 | `lockConfiguredFinance`; `payout = findForUpdateByEventId` rỗng hoặc `reconciledAt == null` → `RECONCILIATION_REQUIRED`; `paidAt != null` → `PAYOUT_ALREADY_PAID`; `ledger = buildReconciliation`; `requireReconcilable(ledger)`; gross/refunds/fee/net khác payout → `RECONCILIATION_STALE`; set `paidBy`, `paidAt = now`; `log.info` |
| M12 | 5 query M19–M23; `fee = gross.subtract(refunds).multiply(feeRate).setScale(0, RoundingMode.HALF_UP)`; `cancelled = cancellationId != null`; `eligible = !cancelled && !now.isBefore(eventEndsAt) && paymentPending == 0 && refundPending == 0 && (payout == null || payout.paidAt == null)`; view nhận reconciled/paid từ payout nếu có |
| M13 | Thứ tự: `cancelled` → `EVENT_CANCELLED`; `now < eventEndsAt` → `EVENT_NOT_ENDED`; `paymentPendingCount > 0` → `PAYMENT_PENDING`; `refundPendingCount > 0` → `REFUND_PENDING` |
| M14 | `findForUpdateByEventId(eventId)` rỗng hoặc `feeRate == null` → `FINANCE_NOT_CONFIGURED` |
| M15 | `@Operation(summary = "Tóm tắt tài chính sự kiện", description = "Dành cho event-service: doanh thu hợp lệ, đã hoàn, phí, thực nhận và đã có khoản thu hay chưa.")`; trả record thẳng (D6) |
| M16 | `admin = authentication.getAuthorities().stream().anyMatch(a -> "ROLE_ADMIN".equals(a.getAuthority()))`; `ApiResponse.success(..., "Lấy báo cáo đối soát thành công")`; `@SecurityRequirement(name = "bearerAuth")` |
| M17, M18 | `jwt.getSubject()`; `ApiResponse.success(...)`; `log.info("[FinanceController] ... eventId={}", eventId)` |
| M19–M23 | JPQL text block; enum trong JPQL viết tên đầy đủ như `RefundRepository.findIdsToPoll` |
| M24 | Như `EventFinanceSnapshotRepository.findForUpdateByEventId` |
| M25 | `payment.queues.finance-configured` trong `application.yaml` (`payment.finance-configured.q`) và `application-payment-flow-test.yaml` (`payment.gov156.test.finance-configured`); `PaymentConfigurationTest` thêm key vào phép đối chiếu với `deploy/rabbitmq/definitions.json` (sửa assertion, không thêm method) |
| E1, E2 | `"PUBLISHED".equals(event.status())` quyết định có gọi `summaries.financial` hay không |

## 5. Test (danh sách cuối)

T1–T15 như thiết kế §6. T12 dùng helper `token(...)` có sẵn trong `PaymentJwtSecurityTest` với role `INTERNAL_SERVICE` (kiểm Y1). T15 dùng `@MockitoSpyBean HttpEventSummaries summaries` có sẵn trong `EventFlowTests`.

## 6. Task

Build: JDK 21 (`C:\Users\WEOHWOEH\.jdks\ms-21.0.12.1`), `JAVA_TOOL_OPTIONS=-Duser.timezone=UTC`. Commit chỉ khi user yêu cầu.

| Task | Nội dung | Item | Xác minh |
|---|---|---|---|
| 1 | DTO, factory, mã lỗi | C1–C6, M5, M6 | payment `spotless:check compile` |
| 2 | Query repository | M19–M24 | compile |
| 3 | Messaging + role | M1, M2, M3, M25, Y1, T13 | payment `mvnw verify` |
| 4 | Service tài chính | C7 (service), M7–M14, Y2 | compile |
| 5 | Controller + security | C7 (controller), M4, M15–M18, T12 | payment `mvnw verify` |
| 6 | event-service bỏ gọi Payment khi chưa publish | E1, E2, T15 | event-service `mvnw -B -ntp spotless:check verify` (Testcontainers cần Docker) |
| 7 | Integration test | C8, T1–T11, T14 | payment `-Pquery-tests verify` trên PostgreSQL 16.15 + RabbitMQ tạm (port 25432/25672) |

Phụ thuộc: 1 → 2 → 4 → 5 → 7; 3 sau 1; 6 độc lập.

## 7. Rủi ro

- Token event-service phải có audience mà Payment chấp nhận (`flashticket.payment.jwt.audience`) và realm role `INTERNAL_SERVICE`; cấu hình Keycloak nằm ngoài code — kiểm khi chạy chung hệ thống.
- Sổ cái tính bằng 5 query mỗi lần đọc; đủ cho quy mô đồ án, có index `order_snapshots_event_idx`.
- IPN không khóa marker sự kiện, nên một khoản thu có thể đến ngay sau lúc tính; bước kiểm `RECONCILIATION_STALE` khi chi trả và đối chiếu thủ công với báo cáo VNPay là lớp chặn cuối (B14 §8).

## 8. Nhật ký duyệt

| Pha | Danh sách | Trạng thái | Nguồn |
|---|---|---|---|
| BRAINSTORMING | C1–C8, M1–M25, E1–E2, T1–T15, D1–D8 | APPROVED | Xem nhật ký thiết kế |
| WRITING-PLANS | C1–C8, M1–M25, E1–E2, T1–T15, Y1–Y2 (bản 2026-11-03) | APPROVED | User trả lời "Duyệt plan", 2026-11-03 |
| EXECUTING-PLANS | C1–C8, M1–M25, E1–E2, T1–T15, Y1–Y2 (bản 2026-11-03) | APPROVED | User trả lời "Duyệt thực thi", 2026-11-03; Codex CLI triển khai, Claude review |

## 9. Tiến độ thực thi

| Task | Trạng thái | File thay đổi | Kiểm thử | Ghi chú |
|---|---|---|---|---|
| 1 | COMPLETED | mới: `EventFinancialSummary`, `EventReconciliationView`, `ReconciliationRequest`; sửa: `EventFinanceSnapshot` (M5), `EventPayout` (M6), `PaymentErrorCode` | `spotless:check compile` BUILD SUCCESS | |
| 2 | COMPLETED | `PaymentConfirmationRepository`, `RefundRepository`, `PaymentAttemptRepository`, `ChargeRepository`, `EventPayoutRepository` | compile qua | Query chạy thật ở Task 7 |
| 3 | COMPLETED | `MessageInvariantValidator`, `PaymentMessageListener`, `RabbitMqConfig`, `KeycloakRealmRoleConverter` (Y1), `application.yaml`, `application-payment-flow-test.yaml`; test `PaymentConfigurationTest`, `MessageInvariantValidatorTest` | `mvnw verify`: 131 test qua | Queue `payment.finance-configured.q` đã có trong `deploy/rabbitmq/definitions.json` |
| 4 | COMPLETED | `FinanceService`, `FinanceServiceImpl` | compile qua; review: khóa marker → payout, phí HALF_UP một lần, `RECONCILIATION_STALE` so bằng `compareTo` | Hành vi DB kiểm ở Task 7 |
| 5 | COMPLETED | `FinanceController`, `PaymentSecurityConfiguration`; test `PaymentJwtSecurityTest` | `mvnw verify`: 131 test qua, Spotless + SpotBugs sạch | `financial-summary` trả JSON phẳng (D6) |
| 6 | DROPPED | — | — | User yêu cầu bỏ ngày 2026-11-03: "chỉ làm trong phạm vi payment thôi". E1, E2, T15 không làm; event-service giữ nguyên, nên hủy sự kiện chưa công bố vẫn nhận 503 cho tới khi sửa event-service riêng |
| 7 | COMPLETED | test mới `service/FinanceFlowIntegrationTest`; `pom.xml` (T14) | `-Pquery-tests verify` trên PostgreSQL 16.15 + RabbitMQ 4.4 tạm: 177 test qua (gồm Finance 8, Refund 10, PaymentFlow 9, EntityMapping 14, ReadRepositories 5); `mvnw verify` mặc định 131 test qua; Spotless + SpotBugs sạch | Claude sửa 2 comment tiếng Việt bị mất dấu trong test. Container tạm đã xóa |

### Kết quả cuối (2026-11-03)

- 6/7 task COMPLETED, 1 DROPPED (Task 6 event-service, theo yêu cầu user), 0 BLOCKED. Không phát sinh class/method ngoài danh sách đã duyệt.
- Còn tồn tại:
  - Event-service vẫn gọi `financial-summary` khi hủy sự kiện chưa công bố → Payment trả 404 → event-service báo 503 (Task 6 đã bỏ).
  - Token event-service cần audience Payment chấp nhận và realm role `INTERNAL_SERVICE` trên Keycloak; chưa kiểm khi chạy chung hệ thống.
  - B13 v0.6 đã thêm §3.2 (D8); tài liệu ở REVIEW_READY, chờ duyệt.
  - `payment.start.q` vẫn chưa có consumer.
- Chưa commit.
