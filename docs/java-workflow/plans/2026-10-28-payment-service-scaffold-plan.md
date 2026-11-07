# Plan — Setup scaffold Payment Service

- Ngày: 28/10/2026; phiên bản danh sách: **v1**.
- Pha: **EXECUTING-PLANS / APPROVED** — user trả lời “xác nhận” ngày 28/10/2026 sau câu hỏi duyệt spec/plan v1 và danh sách cho pha thực thi.
- Design: [Payment scaffold spec](../specs/2026-10-28-payment-service-scaffold-design.md), bản nháp v1.
- Nguồn yêu cầu lập plan: user gọi `$shopapp-java-writing-plans` và yêu cầu “viết plan theo spec trên”. Yêu cầu này cho phép soạn plan; không tự đổi gate class/method trong spec hoặc plan thành APPROVED.
- Checkout khảo sát: `D:/DATN/flash-ticket-platform`, branch `dev/son`, HEAD `b257e38`. Không yêu cầu tạo worktree, commit hoặc deploy trong pha này.

## 1. Mục tiêu, phạm vi và điều kiện thực thi

Triển khai scaffold Payment theo spec: 13 entity/repository, hai embedded ID, enum, DTO/message/snapshot, validation, MapStruct, khung service/controller và cấu hình runtime/security/error/logging. Sau đó kiểm bằng fixture, unit/HTTP tests, PostgreSQL IT và smoke có điều kiện.

Không triển khai REST business handler, RabbitListener, scheduler, relay, Saga, VNPay/IPN/Return/refund/payout, Feign/Redis/SMTP hoặc shared module. Giữ Java21, Boot4.1.1, Cloud2025.1.3, package Payment hiện có; JWT chỉ issuer-uri; JPA ddl-auto=none; Rabbit topology thuộc deploy.

Danh sách v1, X1–X7 và D1–D4 đã được duyệt cho thực thi. P1–P6 DONE; P7 đã triển khai/compile nhưng IT NOT_RUN; P8 verify và handoff DONE, smoke NOT_RUN. Kết quả chi tiết ở §9. Xác nhận chỉ áp dụng danh sách đã hỏi, không mở quyền thêm khai báo ngoài bảng.

## 2. Đối chiếu source và mẫu áp dụng

Đã kiểm lại Payment application/test, POM, test YAML và runtime YAML: module vẫn chỉ có bootstrap. Test YAML tắt Config/Eureka và loại datasource/JPA/Rabbit auto-config; khi thêm service có constructor repository, context test cần mock dependency thực sự. POM đã có các starter test MVC/security/validation/AMQP/JPA; dùng lại, không thêm starter trùng.

Đã đọc lại Booking RuntimeConfigurationTests, SecurityConfigTests, RcaLoggingTests, LogCapture, BookingPersistenceIT, OrderMapper, MappingValueConverter và RabbitMqConfig qua CodeGraph. Áp dụng cách dùng framework, không import source/test Java Booking sang Payment. BookingPersistenceIT hiện dùng Testcontainers; plan này giữ PostgreSQL cô lập được cung cấp qua env theo spec, không thêm Docker/Testcontainers bắt buộc.

Các mã mẫu dùng trong bảng type/method:

| Mã | Nguồn cụ thể | Áp dụng/khác biệt |
|---|---|---|
| S1 | Shop App `models/Category.java`; Booking entity/InboxMessageId | JPA/Lombok; NGOÀI MẪU: UUID, JSONB, embedded ID/version theo DDL Payment |
| S2 | Shop App `DTO/CategoryDTO.java`; Booking `Problem`, `PurchaseSnapshot`, `SalesConfiguration`, `Envelope` | Class Lombok/validation; NGOÀI MẪU: immutable creator constructor, typed contract; không record |
| S3 | Shop App `repositories/CategoryRepository.java` | JpaRepository theo Entity/Id cụ thể; chưa query custom |
| S4 | Shop App `controllers/OrderController.java`, `services/IOrderService.java`, `services/impl/OrderService.java`; Booking `InternalOrderController`, `PromotionServiceImpl` | Constructor injection, interface/impl/controller rỗng; giữ tên/package Booking, không prefix I |
| S5 | Shop App `configurations/MapperConfiguration.java`; Booking config JSON/Rabbit/Security và role converter | Bean đơn giản; NGOÀI MẪU: Jackson3, AMQP, resource server và resolver |
| S6 | Shop App `customexceptions/DataNotFoundException.java`; Booking ErrorCode/BusinessException/GlobalExceptionHandler/Problem | Exception nhỏ; NGOÀI MẪU: status/code/advice/Problem an toàn |
| S7 | Booking OrderMapper/MappingValueConverter | NGOÀI MẪU: MapStruct, @Named converter, @BeforeMapping; không thêm ModelMapper |
| S8 | Booking BookingLogFields/SafeStackTracePrinter | NGOÀI MẪU: structured JSON logging native và safe stack |
| S9 | Booking tests và LogCapture nêu trên | NGOÀI MẪU: Spring test, MockitoBean, JWKS fixture, log capture, PostgreSQL IT |
| S10 | B12/B13/B14 và DDL Payment, ContractRules Booking | NGOÀI MẪU: enum/trạng thái/rule v1 của Payment; không business transition trong DTO |

Đường dẫn Shop App: `C:/Users/sondimid/IdeaProjects/shopapp-backend/shopapp-backend/shopapp/src/main/java/com/project/shopapp/`. Đường dẫn Booking: `services/booking-service/src/{main,test}/java/com/flashticket/booking_service/`. Nguồn contract và link chi tiết nằm tại spec §2. Không thay source mẫu hoặc convention trong lượt này.

Đối chiếu bổ sung để giải quyết phần test/config: [Spring MockitoBean](https://docs.spring.io/spring-framework/reference/testing/annotations/integration-spring/annotation-mockitobean.html) cho mock trên test class; [ví dụ và quyết định trong Spring Boot migration guide](https://github.com/spring-projects/spring-boot/wiki/Spring-Boot-4.0-Migration-Guide) để tránh đặt mock vào configuration dùng chung; [Spring AMQP converter API](https://docs.spring.io/spring-amqp/docs/current/api/org/springframework/amqp/support/converter/JacksonJsonMessageConverter.html) cho JsonMapper + trustedPackages; [Maven Failsafe](https://maven.apache.org/surefire/maven-failsafe-plugin/) cho profile chạy integration-test rồi verify. Giữ version POM dự án, không nâng dependency theo trang current.

## 3. Bảng duyệt class, field và API sinh

Tất cả đề xuất mới trong tài liệu là **NEW / APPROVED**. Ký hiệu `?` nghĩa nullable. Tên bảng là canonical trước prefix `tbl_`.

### 3.1 Entity và khóa ghép

C1–C13: `entity/<Class>.java`, `@Entity @Table @Getter @Setter @NoArgsConstructor`; getter/setter cho từng field bên dưới, constructor public không tham số. Không builder/equals/hashCode/toString. Column snake_case, nullability đúng DDL; JSONB dùng `@JdbcTypeCode(SqlTypes.JSON)`.

| ID | Class / bảng | Field đầy đủ, PK và annotation riêng | Trạng thái | Mẫu / ngoại lệ |
|---|---|---|---|---|
| C1 | `OrderPaymentSnapshot / order_payment_snapshots` | `PK UUID orderId; UUID eventId; String buyerSubject; BigDecimal expectedAmount; Currency currency; Instant expiresAt; Long sourceOrderVersion; PurchaseSnapshot purchaseSnapshot JSONB; String snapshotHash; Instant receivedAt` | NEW / APPROVED | S1 |
| C2 | `PaymentAttempt / payment_attempts` | `PK UUID id; UUID orderId; String provider,merchantAccount; BigDecimal expectedAmount; Currency currency; Instant startedAt,finishedAt?; PaymentAttemptState state; UUID requestKey; String requestHash,paymentUrl?; @Version Long rowVersion` | NEW / APPROVED | S1 |
| C3 | `Charge / charges` | `PK UUID id; UUID attemptId,orderId; String provider,merchantAccount,providerChargeId; BigDecimal amount; Currency currency; Instant chargedAt,recordedAt` | NEW / APPROVED | S1 |
| C4 | `PaymentConfirmation / payment_confirmations` | `PK UUID orderId; UUID chargeId; BigDecimal amount; Currency currency; Instant confirmedAt` | NEW / APPROVED | S1 |
| C5 | `Refund / refunds` | `PK UUID id; UUID chargeId,orderId; BigDecimal amount; Currency currency; RefundReason reason; RefundState state; Instant createdAt,completedAt?; @Version Long rowVersion` | NEW / APPROVED | S1 |
| C6 | `RefundAttempt / refund_attempts` | `PK UUID id; UUID refundId; String requestKey,providerRefundId?; Instant startedAt,finishedAt?; String outcome?` | NEW / APPROVED | S1 |
| C7 | `EventRefundRun / event_refund_runs` | `PK UUID id; UUID eventId,sourceCancellationId; String selectionCursor?; Boolean selectionComplete; Long selectedOrderCount; Instant startedAt,completedAt?; @Version Long rowVersion` | NEW / APPROVED | S1 |
| C8 | `EventRefundItem / event_refund_items` | `@EmbeddedId EventRefundItemId id; UUID eventId,confirmedChargeId?,refundId?; EventRefundItemState state; String lastErrorCode?; Instant updatedAt` | NEW / APPROVED | S1 |
| C9 | `EventPayout / event_payouts` | `PK UUID eventId; String organizerSubject; Currency currency; BigDecimal validGross,validRefunds,feeAmount,netAmount,feeRate; Long sourceFeeVersion; Instant eventEndsAt; Long paymentPendingCount,refundPendingCount; Instant asOf; String reconciledBy?; Instant reconciledAt?; String reconciliationEvidence?,paidBy?; Instant paidAt?; @Version Long rowVersion` | NEW / APPROVED | S1 |
| C10 | `EventFinanceSnapshot / event_finance_snapshots` | `PK UUID eventId; String organizerSubject?; Instant eventEndsAt?; BigDecimal feeRate?; Long sourceFeeVersion?; UUID cancellationId?; Instant cancelledAt?; Instant receivedAt` | NEW / APPROVED | S1 |
| C11 | `PaymentSaga / payment_sagas` | `PK UUID orderId; UUID chargeId; PaymentSagaState state; UUID commandId; Instant nextAttemptAt; String lastErrorCode?; @Version Long rowVersion; Instant createdAt,updatedAt` | NEW / APPROVED | S1 |
| C12 | `OutboxMessage / outbox_messages` | `PK UUID messageId; UUID aggregateId; Long aggregateVersion; String messageType; Integer schemaVersion; JsonNode payload; Instant occurredAt; UUID correlationId,causationId?; String traceparent?; Instant publishedAt?; Integer attempts; Instant nextAttemptAt; String lastErrorCode?` | NEW / APPROVED | S1 |
| C13 | `InboxMessage / inbox_messages` | `@EmbeddedId InboxMessageId id; String payloadHash; Instant processedAt; JsonNode responsePayload?; không version` | NEW / APPROVED | S1 |
| C14 | `entity/EventRefundItemId.java` | `UUID runId,orderId; @Embeddable @Getter @Setter @NoArgsConstructor @EqualsAndHashCode; Serializable, serialVersionUID=1L` | NEW / APPROVED | S1 |
| C15 | `entity/InboxMessageId.java` | `String consumerName; UUID messageId; annotation/API như C14` | NEW / APPROVED | S1 |

C14/C15 sinh getter/setter, public no-arg constructor, equals(Object)/hashCode chỉ field khóa. Mẫu là entity/embedded ID Booking, điều chỉnh đúng DDL Payment. Nullable finance configuration và outcome của RefundAttempt phải được giữ.

### 3.2 Enum

Mỗi type tại `enums/<Type>.java`, không field/method/constructor tự viết; chỉ API enum chuẩn do Java sinh.

| ID | Type | Giá trị | Trạng thái | Mẫu / ngoại lệ |
|---|---|---|---|---|
| C16 | `Currency` | `VND` | NEW / APPROVED | S10 |
| C17 | `PaymentAttemptState` | `PENDING, UNKNOWN, SUCCEEDED, FAILED` | NEW / APPROVED | S10 |
| C18 | `RefundState` | `PENDING, PROCESSING, SUCCEEDED, FAILED` | NEW / APPROVED | S10 |
| C19 | `RefundReason` | `LATE_PAYMENT, DUPLICATE_PAYMENT, TICKET_ISSUANCE_FAILED, EVENT_CANCELLED` | NEW / APPROVED | S10 |
| C20 | `EventRefundItemState` | `SELECTED, REFUND_PENDING, REFUNDED, NEEDS_ATTENTION` | NEW / APPROVED | S10 |
| C21 | `PaymentSagaState` | `WAITING_BOOKING, WAITING_TICKETS, COMPLETED, COMPENSATING, COMPENSATED` | NEW / APPROVED | S10 |
| C22 | `SalesMode` | `QUANTITY, SEAT_MAP` | NEW / APPROVED | S10 |
| C23 | `SectorType` | `STANDING, SEATED` | NEW / APPROVED | S10 |
| C24 | `EventState` | `DRAFT, PENDING_APPROVAL, APPROVED, PUBLISHED, CANCELLED` | NEW / APPROVED | S10 |
| C25 | `PaymentProvider` | `VNPAY; chỉ wire v1, ledger provider vẫn String` | NEW / APPROVED | S10 |
| C26 | `PaymentDecisionOutcome` | `ACCEPTED, LATE, DUPLICATE, CANCELLED` | NEW / APPROVED | S10 |
| C27 | `IssuanceState` | `COMPLETED, FAILED` | NEW / APPROVED | S10 |
| C28 | `StartPaymentResultState` | `CREATED, REJECTED` | NEW / APPROVED | S10 |
| C29 | `StartRequestState` | `WAITING, CREATED, REJECTED` | NEW / APPROVED | S10 |

### 3.3 DTO và chữ ký constructor theo field

Dùng **class Lombok**, không record. Class DTO cụ thể là public final; nested DTO là public static final. C30–C47 và nested type tương ứng dùng @Getter với field private final; một public constructor explicit nhận đầy đủ field đúng thứ tự bảng, @JsonCreator(PROPERTIES), mỗi tham số @JsonProperty(required=true). Không setter/builder/equals/hashCode/toString. Copy list trong constructor bằng unmodifiable ArrayList khi khác null; giữ null/phần tử null cho Validator báo lỗi, không tạo helper copy. C45 là interface, không áp dụng constructor/class annotation.

C48–C53 là output-only, dùng @Getter @AllArgsConstructor, public constructor theo đúng thứ tự field. Field bắt buộc @NotNull/@NotBlank, nested @Valid và phần tử @NotNull; nullable vẫn phải hiện diện trên input JSON. Mẫu Shop App CategoryDTO/Booking Problem, điều chỉnh immutable và creator theo contract.

| ID | File/type | Toàn bộ field theo thứ tự constructor | Trạng thái | Mẫu / ngoại lệ |
|---|---|---|---|---|
| C30 | `dto/message/Envelope.java, class Envelope<T>` | `UUID messageId; String messageType; Integer schemaVersion; UUID aggregateId; String aggregateVersion; Instant occurredAt; UUID correlationId,causationId?; String traceparent?; T data` | NEW / APPROVED | S2 / S10 |
| C31 | `dto/message/StartPayment.java` | `UUID requestId,requestKey; String requestHash; PaymentProvider provider; PurchaseSnapshot purchase` | NEW / APPROVED | S2 / S10 |
| C32 | `dto/message/StartPaymentResult.java` | `UUID requestId,orderId; StartPaymentResultState state; UUID attemptId?; String failureCode?` | NEW / APPROVED | S2 / S10 |
| C33 | `dto/message/FinanceConfiguration.java` | `UUID eventId; String organizerSubject; Instant eventEndsAt; String feeRate,sourceFeeVersion; Currency currency` | NEW / APPROVED | S2 / S10 |
| C34 | `dto/message/EventCancellation.java` | `UUID eventId,cancellationId; String sourceConfigVersion; Instant cancelledAt; String actorSubject; SalesConfiguration sales; EventCancellation.AccessConfiguration access` | NEW / APPROVED | S2 / S10 |
| C34a | `nested EventCancellation.AccessConfiguration` | `UUID eventId; String organizerSubject; EventState eventStatus; Instant eventStartsAt,eventEndsAt; String sourceConfigVersion` | NEW / APPROVED | S2 / S10 |
| C35 | `dto/message/AcceptPayment.java` | `UUID orderId,chargeId,attemptId; String amount; Currency currency; String sourceOrderVersion` | NEW / APPROVED | S2 / S10 |
| C36 | `dto/message/PaymentDecision.java` | `UUID orderId,chargeId; Instant decisionAt; PaymentDecisionOutcome outcome; UUID acceptedChargeId?` | NEW / APPROVED | S2 / S10 |
| C37 | `dto/message/IssueTickets.java` | `UUID orderId,chargeId; PurchaseSnapshot purchase` | NEW / APPROVED | S2 / S10 |
| C38 | `dto/message/IssuanceResult.java` | `UUID orderId,chargeId,issuanceId; IssuanceState state; Integer ticketCount; String failureCode?` | NEW / APPROVED | S2 / S10 |
| C39 | `dto/message/OrderCompleted.java` | `UUID orderId,chargeId,issuanceId; Integer ticketCount` | NEW / APPROVED | S2 / S10 |
| C40 | `dto/message/BookingIssuanceResult.java` | `UUID orderId,chargeId,issuanceId; IssuanceState state; String failureCode` | NEW / APPROVED | S2 / S10 |
| C41 | `dto/snapshot/PurchaseSnapshot.java` | `Integer schemaVersion; UUID orderId,eventId; String buyerSubject,customerEmail,sourceConfigVersion,sourceOrderVersion; Instant frozenAt,expiresAt; Currency currency; String eventTitle; Instant eventStartsAt,eventEndsAt; String venueName?; SalesMode mode; UUID sectorId?; String subtotal,discount,total; PurchaseSnapshot.AppliedPromotionSnapshot promotion?; List<PurchaseSnapshot.Item> items` | NEW / APPROVED | S2 / S10 |
| C41a | `nested PurchaseSnapshot.AppliedPromotionSnapshot` | `UUID promotionId; String code,discount` | NEW / APPROVED | S2 / S10 |
| C41b | `nested PurchaseSnapshot.Item` | `UUID orderItemId,ticketTypeId; String ticketTypeName; Integer quantity; String unitPrice,lineSubtotal; UUID sectorId?; List<SeatSelectionSnapshot> seats` | NEW / APPROVED | S2 / S10 |
| C42 | `dto/snapshot/SeatSelectionSnapshot.java` | `UUID seatId; String seatLabel,rowName,seatNumber` | NEW / APPROVED | S2 / S10 |
| C43 | `dto/snapshot/SalesConfiguration.java` | `Integer schemaVersion; UUID eventId; String organizerSubject,sourceConfigVersion; EventState eventStatus; String eventTitle; Instant eventStartsAt,eventEndsAt; String venueName?; SalesMode mode; Instant saleStartsAt,saleEndsAt; Integer purchaseLimit; List<SalesConfiguration.TicketTypeConfig> ticketTypes; List<SalesConfiguration.SectorConfig> sectors; List<SalesConfiguration.SeatConfig> seats; List<PromotionConfig> promotions` | NEW / APPROVED | S2 / S10 |
| C43a | `nested SalesConfiguration.TicketTypeConfig` | `UUID ticketTypeId; String name,unitPrice; Currency currency; UUID sectorId?; String configuredQuantity?,colorCode?` | NEW / APPROVED | S2 / S10 |
| C43b | `nested SalesConfiguration.SectorConfig` | `UUID sectorId; SectorType kind; String configuredCapacity` | NEW / APPROVED | S2 / S10 |
| C43c | `nested SalesConfiguration.SeatConfig` | `UUID seatId,sectorId,ticketTypeId; String seatLabel,rowName,seatNumber; Boolean isHidden; explicit @JsonProperty("isHidden") giữ đúng wire key` | NEW / APPROVED | S2 / S10 |
| C44 | `dto/snapshot/PromotionConfig.java` | `UUID promotionId; String code; Instant startsAt,endsAt; String maximumUses; Integer maximumUsesPerUser; PromotionConfig.Discount discount` | NEW / APPROVED | S2 / S10 |
| C45 | `nested PromotionConfig.Discount` | `Interface public String getKind(); @JsonTypeInfo EXISTING_PROPERTY kind, @JsonSubTypes allowlist FIXED/PERCENT như Booking; không sealed` | NEW / APPROVED | S2 / S10 |
| C46 | `nested PromotionConfig.FixedDiscount` | `final class implements Discount; String kind,amount; kind chỉ FIXED` | NEW / APPROVED | S2 / S10 |
| C47 | `nested PromotionConfig.PercentDiscount` | `final class implements Discount; String kind; Integer percent; kind chỉ PERCENT` | NEW / APPROVED | S2 / S10 |
| C48 | `dto/response/PaymentStart.java` | `UUID orderId,attemptId; PaymentAttemptState attemptState; String amount; Currency currency; String paymentUrl?; Instant expiresAt` | NEW / APPROVED | S2 / S10 |
| C49 | `dto/response/PaymentView.java` | `UUID orderId,acceptedChargeId?; PaymentStart latestAttempt?; PaymentView.StartRequest startRequest?; List<PaymentView.RefundView> refunds` | NEW / APPROVED | S2 / S10 |
| C50 | `nested PaymentView.StartRequest` | `UUID requestId; StartRequestState state; String failureCode?` | NEW / APPROVED | S2 / S10 |
| C51 | `nested PaymentView.RefundView` | `UUID refundId,chargeId; String amount; RefundState state; RefundReason reason` | NEW / APPROVED | S2 / S10 |
| C52 | `dto/response/Problem.java` | `String code,message; UUID correlationId; List<Problem.Violation> violations` | NEW / APPROVED | S2 / S10 |
| C53 | `nested Problem.Violation` | `String field,code` | NEW / APPROVED | S2 / S10 |

SalesConfiguration/PromotionConfig chỉ xuất hiện vì cancellation mang full payload; không tạo inventory hoặc logic bán vé tại Payment. Interface Discount có hai variant thực sự trong contract, không thêm hierarchy cho DTO khác.

### 3.4 Type nền, repository và khung feature

Tất cả NEW/APPROVED; đường dẫn tương đối services/payment-service/src/main/java/com/flashticket/payment_service/. Repository chỉ kế thừa JpaRepository, chưa custom query/lock. Service interface rỗng; implementation @Service @RequiredArgsConstructor, dependency private final. Controller @RestController @RequestMapping @RequiredArgsConstructor, chưa handler.

| ID | File/type | Field/API sinh, trách nhiệm và mẫu | Trạng thái | Mẫu / ngoại lệ |
|---|---|---|---|---|
| C60 | `payment/repository/OrderPaymentSnapshotRepository.java` | `JpaRepository<OrderPaymentSnapshot,UUID>, mẫu Spring Data Shop App/Booking` | NEW / APPROVED | S3 |
| C61 | `payment/repository/PaymentAttemptRepository.java` | `JpaRepository<PaymentAttempt,UUID>` | NEW / APPROVED | S3 |
| C62 | `payment/repository/ChargeRepository.java` | `JpaRepository<Charge,UUID>` | NEW / APPROVED | S3 |
| C63 | `payment/repository/PaymentConfirmationRepository.java` | `JpaRepository<PaymentConfirmation,UUID>` | NEW / APPROVED | S3 |
| C64 | `refund/repository/RefundRepository.java` | `JpaRepository<Refund,UUID>` | NEW / APPROVED | S3 |
| C65 | `refund/repository/RefundAttemptRepository.java` | `JpaRepository<RefundAttempt,UUID>` | NEW / APPROVED | S3 |
| C66 | `refund/repository/EventRefundRunRepository.java` | `JpaRepository<EventRefundRun,UUID>` | NEW / APPROVED | S3 |
| C67 | `refund/repository/EventRefundItemRepository.java` | `JpaRepository<EventRefundItem,EventRefundItemId>` | NEW / APPROVED | S3 |
| C68 | `finance/repository/EventPayoutRepository.java` | `JpaRepository<EventPayout,UUID>` | NEW / APPROVED | S3 |
| C69 | `finance/repository/EventFinanceSnapshotRepository.java` | `JpaRepository<EventFinanceSnapshot,UUID>` | NEW / APPROVED | S3 |
| C70 | `saga/repository/PaymentSagaRepository.java` | `JpaRepository<PaymentSaga,UUID>` | NEW / APPROVED | S3 |
| C71 | `messaging/repository/OutboxMessageRepository.java` | `JpaRepository<OutboxMessage,UUID>` | NEW / APPROVED | S3 |
| C72 | `messaging/repository/InboxMessageRepository.java` | `JpaRepository<InboxMessage,InboxMessageId>` | NEW / APPROVED | S3 |
| C73 | `payment/service/PaymentService.java` | `Interface rỗng` | NEW / APPROVED | S4 |
| C74 | `payment/service/impl/PaymentServiceImpl.java` | `Implements C73; constructor sinh (OrderPaymentSnapshotRepository orderPaymentSnapshotRepository, PaymentAttemptRepository paymentAttemptRepository, ChargeRepository chargeRepository, PaymentConfirmationRepository paymentConfirmationRepository)` | NEW / APPROVED | S4 |
| C75 | `refund/service/RefundService.java` | `Interface rỗng` | NEW / APPROVED | S4 |
| C76 | `refund/service/impl/RefundServiceImpl.java` | `Implements C75; constructor sinh (RefundRepository refundRepository, RefundAttemptRepository refundAttemptRepository, EventRefundRunRepository eventRefundRunRepository, EventRefundItemRepository eventRefundItemRepository)` | NEW / APPROVED | S4 |
| C77 | `finance/service/FinanceService.java` | `Interface rỗng` | NEW / APPROVED | S4 |
| C78 | `finance/service/impl/FinanceServiceImpl.java` | `Implements C77; constructor sinh (EventFinanceSnapshotRepository eventFinanceSnapshotRepository, EventPayoutRepository eventPayoutRepository)` | NEW / APPROVED | S4 |
| C79 | `payment/controller/PaymentController.java` | `Mapping /api/v1/orders; constructor sinh (PaymentService paymentService), mẫu InternalOrderController Booking` | NEW / APPROVED | S4 |
| C80 | `finance/controller/InternalFinanceController.java` | `Mapping /internal/v1/events; constructor sinh (FinanceService financeService)` | NEW / APPROVED | S4 |
| C81 | `config/PaymentJsonConfig.java` | `@Configuration(proxyBeanMethods=false), mẫu BookingJsonConfig` | NEW / APPROVED | S5 |
| C82 | `config/RabbitMqConfig.java` | `@Configuration(proxyBeanMethods=false), chỉ converter Booking` | NEW / APPROVED | S5 |
| C83 | `config/SecurityConfig.java` | `@Configuration(proxyBeanMethods=false) @EnableWebSecurity, MVC Booking` | NEW / APPROVED | S5 |
| C84 | `security/KeycloakRealmRoleConverter.java` | `final implements Converter<Jwt,Collection<GrantedAuthority>>, static final Set role allowlist` | NEW / APPROVED | S5 |
| C85 | `config/PaymentLogFields.java` | `final implements StructuredLoggingJsonMembersCustomizer<ILoggingEvent>, mẫu BookingLogFields` | NEW / APPROVED | S8 |
| C86 | `config/SafeStackTracePrinter.java` | `final implements StackTracePrinter, mẫu Booking` | NEW / APPROVED | S8 |
| C87 | `exception/ErrorCode.java` | `Enum; private final HttpStatus status, String message; constants §6` | NEW / APPROVED | S6 |
| C88 | `exception/BusinessException.java` | `final RuntimeException; private final ErrorCode errorCode, mẫu Booking` | NEW / APPROVED | S6 |
| C89 | `exception/GlobalExceptionHandler.java` | `final @Slf4j @RestControllerAdvice; chỉ logger static sinh bởi Lombok` | NEW / APPROVED | S6 |
| C90 | `validation/ContractRules.java` | `final utility, private constructor; rule DTO thuần như Booking` | NEW / APPROVED | S10 |
| C91 | `mapper/WireValueConverter.java` | `@Component, không dependency/field; phần toMoney của Booking` | NEW / APPROVED | S7 |
| C92 | `mapper/PaymentMapper.java` | `@Mapper(componentModel=SPRING,unmappedTargetPolicy=ERROR,uses=WireValueConverter.class,injectionStrategy=CONSTRUCTOR)` | NEW / APPROVED | S7 |

Mapper chỉ có PaymentAttempt+OrderPaymentSnapshot→PaymentStart và Refund→PaymentView.RefundView. attempt.id→attemptId, state→attemptState, expectedAmount→amount; expiresAt từ snapshot cục bộ. Chặn null/mismatch orderId trước mapping. Refund.id→refundId, tiền qua converter. Không mapper đọc DB/ghép latestAttempt/startRequest, không mapper tạo confirmation/Saga/refund hoặc generic outbox khi chưa flow.

## 4. Bảng duyệt constructor/method kế thừa spec

Bảng này đã áp dụng chữ ký thay thế MT4c/MT4d và M-R2; xem thêm delta đầy đủ tại §5.1. Mọi đề xuất NEW/APPROVED; không throws trừ nơi ghi rõ. Các chữ ký dùng import type của Spring/Jakarta/Java tương ứng. Các khoảng ID không sử dụng không đại diện đề xuất mở.

### API sinh và constructor

| ID | Class | Chữ ký/nhóm API cụ thể | NEW/REUSE | Mẫu / ngoại lệ |
|---|---|---|---|---|
| M-G1 | C1–C13 | `Mỗi class: public <Class>(); public <FieldType> get<Field>(); public void set<Field>(<FieldType> value), cho từng field §3.1` | NEW, Lombok | Theo class tương ứng tại §3 |
| M-G2 | C14–C15 | `Như M-G1 cho từng field khóa; public boolean equals(Object other); public int hashCode()` | NEW, Lombok | Theo class tương ứng tại §3 |
| M-G3 | C30–C44/C46–C47 và nested DTO | `public <Class>(toàn bộ type/tên field đúng thứ tự §3.3); public getter từng field; không setter; C45 không constructor` | NEW, creator explicit + getter Lombok | Theo class tương ứng tại §3 |
| M-G4 | C48–C53 | `public <Class>(toàn bộ type/tên field đúng thứ tự §3.3); public getter từng field` | NEW, @AllArgsConstructor @Getter | Theo class tương ứng tại §3 |
| M-G5 | C74/C76/C78–C80 | `public <Class>(dependency đúng type/tên/thứ tự §3.4)` | NEW, @RequiredArgsConstructor | Theo class tương ứng tại §3 |
| M-G6 | C81–C86/C89/C91 | `public <Class>()` | NEW, constructor mặc định | Theo class tương ứng tại §3 |
| M-G7 | C16–C29/C87 | `public static <Enum>[] values(); public static <Enum> valueOf(String name)` | NEW, Java enum | Theo class tương ứng tại §3 |
| M-G8 | Generated PaymentMapperImpl | `Spring component, constructor dependency WireValueConverter theo MapStruct CONSTRUCTOR; implements M-M2/M-M3` | NEW, API generated được duyệt cùng mapper | Theo class tương ứng tại §3 |
| M-G9 | C60–C72 | `Kế thừa JpaRepository<Entity,Id> theo type §3.4; không khai báo custom method` | NEW | Theo class tương ứng tại §3 |
| M-R1 | PaymentServiceApplication | `public PaymentServiceApplication(); public static void main(String[] args)` | REUSE, giữ annotation/body | ShopappApplication / REUSE |
| M-R2 | PaymentServiceApplicationTests hiện có | `package-private void contextLoads(); constructor mặc định package-private giữ nguyên; annotation delta D1` | REUSE có thay annotation | S9 / D1 |

### DTO/rule/mapping

M-D1–M-D11 đều public boolean, không tham số, @AssertTrue @JsonIgnore; null-safe. Kiểm field annotation riêng, không NPE trước Validator.

| ID | Class | Chữ ký đầy đủ | Cách viết/lý do | Mẫu / ngoại lệ |
|---|---|---|---|---|
| M-D1 | Envelope | `public boolean isValidEnvelope()` | Gọi ContractRules.validEnvelope | S2 / S10 |
| M-D2 | StartPaymentResult | `public boolean isValidResult()` | CREATED/REJECTED consistency | S2 / S10 |
| M-D3 | FinanceConfiguration | `public boolean isValidConfiguration()` | Fee/version/currency | S2 / S10 |
| M-D4 | EventCancellation | `public boolean isValidCancellation()` | Gọi validCancellation | S2 / S10 |
| M-D5 | PaymentDecision | `public boolean isValidDecision()` | Outcome/accepted charge | S2 / S10 |
| M-D6 | IssueTickets | `public boolean isOrderConsistent()` | Order khớp purchase | S2 / S10 |
| M-D7 | IssuanceResult | `public boolean isValidResult()` | State/count/code | S2 / S10 |
| M-D8 | BookingIssuanceResult | `public boolean isValidFailure()` | Chỉ FAILED/code | S2 / S10 |
| M-D9 | PurchaseSnapshot | `public boolean isValidSnapshot()` | Gọi validPurchaseSnapshot | S2 / S10 |
| M-D10 | SalesConfiguration | `public boolean isValidSnapshot()` | Gọi validSalesConfiguration | S2 / S10 |
| M-D11 | PromotionConfig | `public boolean isValidWindow()` | Time/maxUsesPerUser=1 | S2 / S10 |
| M-D12 | PromotionConfig.Discount | `public String getKind()` | Interface; Lombok getter C46/C47 thực hiện | S2 / S10 |
| M-V1 | ContractRules | `private ContractRules()` | Utility không khởi tạo | S2 / S10 |
| M-V2 | ContractRules | `public static boolean validMoney(String value, boolean positive)` | Regex/range, không rounding | S2 / S10 |
| M-V3 | ContractRules | `public static boolean validUInt64(String value)` | Version signed bigint | S2 / S10 |
| M-V4 | ContractRules | `public static boolean validFeeRate(String value)` | Decimal [0,1], <=8 số lẻ | S2 / S10 |
| M-V5 | ContractRules | `public static boolean validPurchaseSnapshot(PurchaseSnapshot value)` | Quy tắc spec §5, không DB/clock | S2 / S10 |
| M-V6 | ContractRules | `public static boolean validSalesConfiguration(SalesConfiguration value)` | Full config nested | S2 / S10 |
| M-V7 | ContractRules | `public static boolean validCancellation(EventCancellation value)` | Event/version/status | S2 / S10 |
| M-V8 | ContractRules | `public static boolean validEnvelope(Envelope<?> value)` | Metadata/aggregate/request identity | S2 / S10 |
| M-V9 | ContractRules | `public static boolean isPayloadTypeValid(String messageType, Object payload)` | Allowlist spec §5, nhánh trực tiếp | S2 / S10 |
| M-M1 | WireValueConverter | `public String toMoney(BigDecimal value)` | @Named("toMoney"), UNNECESSARY/range, lỗi nội bộ | S7 |
| M-M2 | PaymentMapper | `public PaymentStart toStart(PaymentAttempt attempt, OrderPaymentSnapshot snapshot)` | @Mapping theo §3.4, qualified money conversion | S7 |
| M-M3 | PaymentMapper | `public PaymentView.RefundView toRefundView(Refund refund)` | id→refundId, amount qualifiedByName=toMoney | S7 |
| M-M4 | PaymentMapper | `public default void validateStartSources(PaymentAttempt attempt, OrderPaymentSnapshot snapshot)` | @BeforeMapping; null/mismatch orderId→INTERNAL_ERROR | S7 |

### Config và exception

| ID | Class | Chữ ký đầy đủ | Annotation/cách viết | Mẫu / ngoại lệ |
|---|---|---|---|---|
| M-C1 | PaymentJsonConfig | `package-private JsonMapperBuilderCustomizer paymentJsonMapperCustomizer()` | @Bean, Boot mapper | S5 |
| M-C2 | RabbitMqConfig | `package-private MessageConverter rabbitMessageConverter(JsonMapper jsonMapper)` | @Bean, JacksonJsonMessageConverter | S5 |
| M-C3 | SecurityConfig | `package-private SecurityFilterChain securityFilterChain(HttpSecurity http, JwtAuthenticationConverter keycloakJwtAuthenticationConverter, AuthenticationEntryPoint problemAuthenticationEntryPoint, AccessDeniedHandler problemAccessDeniedHandler) throws Exception` | @Bean | S5 |
| M-C4 | SecurityConfig | `package-private AuthenticationEntryPoint problemAuthenticationEntryPoint(@Qualifier("handlerExceptionResolver") HandlerExceptionResolver resolver)` | @Bean, challenge/resolver | S5 |
| M-C5 | SecurityConfig | `package-private AccessDeniedHandler problemAccessDeniedHandler(@Qualifier("handlerExceptionResolver") HandlerExceptionResolver resolver)` | @Bean | S5 |
| M-C6 | SecurityConfig | `package-private JwtAuthenticationConverter keycloakJwtAuthenticationConverter(KeycloakRealmRoleConverter keycloakRealmRoleConverter)` | @Bean | S5 |
| M-C7 | SecurityConfig | `package-private KeycloakRealmRoleConverter keycloakRealmRoleConverter()` | @Bean | S5 |
| M-C8 | KeycloakRealmRoleConverter | `public Collection<GrantedAuthority> convert(Jwt jwt)` | @Override | S5 |
| M-C9 | PaymentLogFields | `public void customize(JsonWriter.Members<ILoggingEvent> members)` | @Override | S8 |
| M-C10 | SafeStackTracePrinter | `public void printStackTrace(Throwable exception, Appendable output) throws IOException` | @Override, giới hạn/cycle theo Booking | S8 |
| M-E1 | ErrorCode | `private ErrorCode(HttpStatus status, String message)` | Enum constructor | S6 |
| M-E2 | ErrorCode | `public HttpStatus status(); public String message()` | Giá trị cố định | S6 |
| M-E3 | ErrorCode | `public static ErrorCode from(Throwable exception)` | Business/native JPA/SQL/AMQP | S6 |
| M-E4 | ErrorCode | `private static Throwable unwrap(Throwable exception)` | Wrapper tối đa16 tầng | S6 |
| M-E5 | ErrorCode | `public static ErrorCode fromStatus(HttpStatusCode status)` | Framework HTTP | S6 |
| M-E6 | BusinessException | `public BusinessException(ErrorCode errorCode); public BusinessException(ErrorCode errorCode, Throwable cause); public ErrorCode getErrorCode()` | Explicit như Booking | S6 |
| M-E7 | GlobalExceptionHandler | `public ResponseEntity<Problem> handleBusiness(BusinessException exception, HttpServletRequest request)` | @ExceptionHandler(BusinessException.class) | S6 |
| M-E8 | GlobalExceptionHandler | `public ResponseEntity<Problem> handleValidation(Exception exception, HttpServletRequest request)` | @ExceptionHandler({BindException.class,HandlerMethodValidationException.class}) | S6 |
| M-E9 | GlobalExceptionHandler | `public ResponseEntity<Problem> handleException(Exception exception, HttpServletRequest request, HttpServletResponse response)` | @ExceptionHandler(Exception.class) | S6 |
| M-E10 | GlobalExceptionHandler | `private static ResponseEntity<Problem> problem(HttpServletRequest request, HttpStatusCode status, ErrorCode code, Throwable exception, List<Problem.Violation> violations)` | Correlation/log trực tiếp | S6 |
| M-E11 | GlobalExceptionHandler | `private static List<Problem.Violation> violations(Exception exception)` | Tối đa50 | S6 |
| M-E12 | GlobalExceptionHandler | `private static String safeField(String path); private static String violationCode(String code)` | Field/code an toàn | S6 |

### Test type/method

Test dưới `src/test/java/com/flashticket/payment_service/`, class package-private với constructor mặc định package-private không tham số. Các method dưới đều package-private @Test trả void; signature ghi rõ exception. Các helper/lifecycle và LoggingProbe duy nhất được bổ sung tại §5.1; không tạo helper/type khác.

| ID class/method | File | Chữ ký method đầy đủ | Mẫu / ngoại lệ |
|---|---|---|---|
| T1/MT1 | dto/PaymentContractTests.java | `void readsBookingWireFixturesAndPreservesNulls() throws Exception; void rejectsUnknownMissingAndCoercedFields() throws Exception; void validatesPurchaseAndCancellationConsistency() throws Exception; void validatesEnvelopeAndResultVariants() throws Exception; void preservesExactMoneyVersionAndHiddenProperty() throws Exception` | S9 |
| T2/MT2 | mapper/PaymentMapperTests.java | `void mapsAttemptAndSnapshotToPaymentStart(); void rejectsSourcesFromDifferentOrders(); void mapsRefundWithoutLeakingLedgerFields(); void rejectsFractionalAndOutOfRangeMoney()` | S9 |
| T3/MT3 | security/PaymentSecurityTests.java | `void rejectsMissingAndInvalidJwtWithProblem() throws Exception; void enforcesBuyerAdminAndInternalRoles() throws Exception; void permitsHealthAndBlocksOtherActuatorEndpoints() throws Exception; void ignoresUnknownAndMalformedRealmRoles()` | S9 |
| T4/MT4 | exception/PaymentErrorLoggingTests.java | `void keepsProblemHeaderAndLogCorrelationConsistent(CapturedOutput output) throws Exception; void preservesFrameworkStatusAndHeaders() throws Exception; void distinguishesInputAndOutputValidationFailures() throws Exception; void redactsExceptionMessagesAndSensitiveLogFields(CapturedOutput output) throws Exception` | S9 |
| T5/MT5 | config/PaymentRuntimeConfigurationTests.java | `void loadsPaymentRuntimeProperties() throws Exception; void usesBootJsonMapperForAmqpConverter() throws Exception; void hasNoBusinessListenerOrWorkerBeans()` | S9 |
| T6/MT6 | persistence/PaymentPersistenceIT.java | `void validatesAllMappingsAgainstPaymentSchema() throws Exception; void preservesTypedJsonAndCompositeKeys() throws Exception; void enforcesLedgerForeignKeysAndUnfinishedAttemptConstraint() throws Exception; void acceptsCancellationOnlyFinanceSnapshotAndNullRefundOutcome() throws Exception` | S9 |

T1 dùng cùng fixture JSON hợp lệ Booking/B13, parse rồi Validator; T3 đi qua filter chain/JwtDecoder fixture kiểm issuer/expiry/chữ ký, không chỉ mock authenticated JWT. T4 có thể gọi advice với framework exception trực tiếp, không tạo endpoint giả. T6 dùng PostgreSQL cô lập đúng DDL/FK/JSONB/partial index; không dùng H2 thay thế hoặc ghi dữ liệu thử vào DB dùng chung. Profile Failsafe opt-in được cụ thể tại P1/P7; nếu thiếu môi trường ghi NOT_RUN.

## 5. Phần làm rõ và delta so với spec

D1–D4 đã được user duyệt bằng câu “xác nhận” ngày 28/10/2026 cho pha thực thi.

| ID | Item bị tác động | Quyết định đề xuất và lý do |
|---|---|---|
| D1 | E1: PaymentServiceApplicationTests hiện có, M-R2 | Giữ @SpringBootTest/contextLoads; thêm @MockitoBean(types={C60–C69, JwtDecoder}) trên test class. Mười repository là dependency thật của ba service, không cần mock ba repository chưa được inject. Test không discovery Keycloak thật và không có application helper mới |
| D2 | T3/MT3a–c | Thêm lifecycle/property/token helper liệt kê dưới để kiểm JWT thật với local discovery/JWKS, tránh copy ProbeController của Booking hoặc chỉ mock Authentication |
| D3 | T4/T4a/MT4a–d; hai chữ ký test MT4 | Dùng OutputCaptureExtension với CapturedOutput parameter; helper boot log cấu hình Payment và nested LoggingProbe rỗng. Chỉ test scaffold logging, không tạo LogCapture framework, appender anonymous hoặc business endpoint |
| D4 | T6/MT6a, POM/profile/resource | PostgreSQL ngoài process được provision cô lập; env PAYMENT_TEST_DB_*, Failsafe profile payment-persistence-it. DDL/version/prefix giữ nguyên; không copy Testcontainers, không app tự tạo DB/schema |

### 5.1 Type/method bổ sung và chữ ký thay thế

Các dòng dưới đều NEW/APPROVED trừ E1/M-R2 là EXISTING/REUSE có thay annotation. Không có constructor/override/helper khác được ngầm cho phép.

| ID | Class/file | Field/annotation/API sinh | Mẫu |
|---|---|---|---|
| E1 | `src/test/java/com/flashticket/payment_service/PaymentServiceApplicationTests.java` | EXISTING, @SpringBootTest và @MockitoBean(types={OrderPaymentSnapshotRepository, PaymentAttemptRepository, ChargeRepository, PaymentConfirmationRepository, RefundRepository, RefundAttemptRepository, EventRefundRunRepository, EventRefundItemRepository, EventPayoutRepository, EventFinanceSnapshotRepository, JwtDecoder}); không field/method mới | S9; mock trực tiếp trên test class |
| T3 — bổ sung cấu hình | `security/PaymentSecurityTests.java` dưới test root | @SpringBootTest @AutoConfigureMockMvc; @MockitoBean(types={mười repository C60–C69}); @Autowired MockMvc; static RSAKey signingKey, HttpServer issuerServer, String issuer. Dùng JwtDecoder thật qua issuer discovery, không mock JwtDecoder ở T3 | S9 |
| T4 — bổ sung cấu hình | `exception/PaymentErrorLoggingTests.java` dưới test root | @ExtendWith(OutputCaptureExtension.class), không @SpringBootTest; không field/generated API mới | S9 |
| T4a | nested `PaymentErrorLoggingTests.LoggingProbe` | NEW, static class package-private @TestConfiguration(proxyBeanMethods=false), constructor mặc định package-private `LoggingProbe()`; không field/bean/method | S9, từ LoggingProbe Booking nhưng dùng TestConfiguration tránh lọt component scan |
| T6 — bổ sung cấu hình | `persistence/PaymentPersistenceIT.java` dưới test root | @DataJpaTest(properties={"spring.autoconfigure.exclude=","spring.jpa.hibernate.ddl-auto=validate"}) @AutoConfigureTestDatabase(replace=NONE) @ActiveProfiles("payment-persistence-it"); @Import(PaymentJsonConfig,JacksonAutoConfiguration); @Sql("/fixtures/payment/persistence.sql"); dùng repository/JdbcTemplate/TestEntityManager có sẵn trong slice, không wrapper | S9 |

| ID | Class | Chữ ký đầy đủ | Annotation/cách triển khai |
|---|---|---|---|
| M-R2 — cụ thể hóa | E1 | package-private `void contextLoads()` | REUSE @Test, giữ body; constructor mặc định package-private giữ nguyên |
| MT3a | T3 | package-private static `void jwtProperties(DynamicPropertyRegistry properties) throws Exception` | @DynamicPropertySource; sinh RSAKey test, tạo HttpServer localhost cổng ngẫu nhiên; phục vụ discovery và JWKS bằng lambda; đăng ký issuer-uri. Không thêm jwk-set-uri vào runtime YAML |
| MT3b | T3 | package-private static `void stopIssuer()` | @AfterAll; dừng server, không rò socket sang test khác |
| MT3c | T3 | private static `String signedToken(String role, String scenario) throws JOSEException` | Nimbus có sẵn qua security starter; scenario valid/expired/future/issuer/signature, không helper crypto riêng |
| MT4a | T4 | private static `ConfigurableApplicationContext bootJsonLogging() throws IOException` | Đọc riêng logging.* từ runtime YAML Payment, chạy SpringApplication(LoggingProbe.class) với WebApplicationType.NONE, Config/Eureka tắt, không component scan/data/broker; caller dùng try-with-resources |
| MT4b | T4 | package-private `void clearMdc()` | @AfterEach; MDC.clear |
| MT4c — thay chữ ký | T4 | package-private `void keepsProblemHeaderAndLogCorrelationConsistent(CapturedOutput output) throws Exception` | @Test; thay bản không tham số trong spec; kiểm log JSON thật/header/body |
| MT4d — thay chữ ký | T4 | package-private `void redactsExceptionMessagesAndSensitiveLogFields(CapturedOutput output) throws Exception` | @Test; thay bản không tham số trong spec; sentinel secret ở cause/MDC phải bị loại |
| MT6a | T6 | package-private static `void requireIsolatedDatabase()` | @BeforeAll; require PAYMENT_TEST_DB_URL/USERNAME/PASSWORD, không fallback PAYMENT_DB_*; reject URL trùng runtime nếu runtime URL được cung cấp. Không in/log secret |

MT4 còn lại giữ chữ ký spec. T1/T2/T5 không thêm helper/config class. Dùng Jackson JavaType/constructParametricType cho Envelope<T>, không tạo anonymous TypeReference để phát sinh type ngoài bảng. Test getter đơn giản/constructor injection không được thêm làm KPI.

### 5.2 Constructor DTO explicit để duyệt

M-G3 trong spec gom constructor theo field; plan ghi chữ ký cụ thể bên dưới. Tất cả public, không static/throws, @JsonCreator(mode=PROPERTIES); từng tham số có @JsonProperty(value=tênField,required=true). Getter Lombok theo đúng field; không phát sinh API khác.

| Item | Class | Constructor đầy đủ | Mẫu |
|---|---|---|---|
| M-G3/C30 | dto/message/Envelope.java, class Envelope<T> | `public Envelope(UUID messageId, String messageType, Integer schemaVersion, UUID aggregateId, String aggregateVersion, Instant occurredAt, UUID correlationId, UUID causationId, String traceparent, T data)` | S2 / S10 |
| M-G3/C31 | dto/message/StartPayment.java | `public StartPayment(UUID requestId, UUID requestKey, String requestHash, PaymentProvider provider, PurchaseSnapshot purchase)` | S2 / S10 |
| M-G3/C32 | dto/message/StartPaymentResult.java | `public StartPaymentResult(UUID requestId, UUID orderId, StartPaymentResultState state, UUID attemptId, String failureCode)` | S2 / S10 |
| M-G3/C33 | dto/message/FinanceConfiguration.java | `public FinanceConfiguration(UUID eventId, String organizerSubject, Instant eventEndsAt, String feeRate, String sourceFeeVersion, Currency currency)` | S2 / S10 |
| M-G3/C34 | dto/message/EventCancellation.java | `public EventCancellation(UUID eventId, UUID cancellationId, String sourceConfigVersion, Instant cancelledAt, String actorSubject, SalesConfiguration sales, EventCancellation.AccessConfiguration access)` | S2 / S10 |
| M-G3/C34a | nested EventCancellation.AccessConfiguration | `public AccessConfiguration(UUID eventId, String organizerSubject, EventState eventStatus, Instant eventStartsAt, Instant eventEndsAt, String sourceConfigVersion)` | S2 / S10 |
| M-G3/C35 | dto/message/AcceptPayment.java | `public AcceptPayment(UUID orderId, UUID chargeId, UUID attemptId, String amount, Currency currency, String sourceOrderVersion)` | S2 / S10 |
| M-G3/C36 | dto/message/PaymentDecision.java | `public PaymentDecision(UUID orderId, UUID chargeId, Instant decisionAt, PaymentDecisionOutcome outcome, UUID acceptedChargeId)` | S2 / S10 |
| M-G3/C37 | dto/message/IssueTickets.java | `public IssueTickets(UUID orderId, UUID chargeId, PurchaseSnapshot purchase)` | S2 / S10 |
| M-G3/C38 | dto/message/IssuanceResult.java | `public IssuanceResult(UUID orderId, UUID chargeId, UUID issuanceId, IssuanceState state, Integer ticketCount, String failureCode)` | S2 / S10 |
| M-G3/C39 | dto/message/OrderCompleted.java | `public OrderCompleted(UUID orderId, UUID chargeId, UUID issuanceId, Integer ticketCount)` | S2 / S10 |
| M-G3/C40 | dto/message/BookingIssuanceResult.java | `public BookingIssuanceResult(UUID orderId, UUID chargeId, UUID issuanceId, IssuanceState state, String failureCode)` | S2 / S10 |
| M-G3/C41 | dto/snapshot/PurchaseSnapshot.java | `public PurchaseSnapshot(Integer schemaVersion, UUID orderId, UUID eventId, String buyerSubject, String customerEmail, String sourceConfigVersion, String sourceOrderVersion, Instant frozenAt, Instant expiresAt, Currency currency, String eventTitle, Instant eventStartsAt, Instant eventEndsAt, String venueName, SalesMode mode, UUID sectorId, String subtotal, String discount, String total, PurchaseSnapshot.AppliedPromotionSnapshot promotion, List<PurchaseSnapshot.Item> items)` | S2 / S10 |
| M-G3/C41a | nested PurchaseSnapshot.AppliedPromotionSnapshot | `public AppliedPromotionSnapshot(UUID promotionId, String code, String discount)` | S2 / S10 |
| M-G3/C41b | nested PurchaseSnapshot.Item | `public Item(UUID orderItemId, UUID ticketTypeId, String ticketTypeName, Integer quantity, String unitPrice, String lineSubtotal, UUID sectorId, List<SeatSelectionSnapshot> seats)` | S2 / S10 |
| M-G3/C42 | dto/snapshot/SeatSelectionSnapshot.java | `public SeatSelectionSnapshot(UUID seatId, String seatLabel, String rowName, String seatNumber)` | S2 / S10 |
| M-G3/C43 | dto/snapshot/SalesConfiguration.java | `public SalesConfiguration(Integer schemaVersion, UUID eventId, String organizerSubject, String sourceConfigVersion, EventState eventStatus, String eventTitle, Instant eventStartsAt, Instant eventEndsAt, String venueName, SalesMode mode, Instant saleStartsAt, Instant saleEndsAt, Integer purchaseLimit, List<SalesConfiguration.TicketTypeConfig> ticketTypes, List<SalesConfiguration.SectorConfig> sectors, List<SalesConfiguration.SeatConfig> seats, List<PromotionConfig> promotions)` | S2 / S10 |
| M-G3/C43a | nested SalesConfiguration.TicketTypeConfig | `public TicketTypeConfig(UUID ticketTypeId, String name, String unitPrice, Currency currency, UUID sectorId, String configuredQuantity, String colorCode)` | S2 / S10 |
| M-G3/C43b | nested SalesConfiguration.SectorConfig | `public SectorConfig(UUID sectorId, SectorType kind, String configuredCapacity)` | S2 / S10 |
| M-G3/C43c | nested SalesConfiguration.SeatConfig | `public SeatConfig(UUID seatId, UUID sectorId, UUID ticketTypeId, String seatLabel, String rowName, String seatNumber, Boolean isHidden)` | S2 / S10 |
| M-G3/C44 | dto/snapshot/PromotionConfig.java | `public PromotionConfig(UUID promotionId, String code, Instant startsAt, Instant endsAt, String maximumUses, Integer maximumUsesPerUser, PromotionConfig.Discount discount)` | S2 / S10 |
| M-G3/C46 | nested PromotionConfig.FixedDiscount | `public FixedDiscount(String kind, String amount)` | S2 / S10 |
| M-G3/C47 | nested PromotionConfig.PercentDiscount | `public PercentDiscount(String kind, Integer percent)` | S2 / S10 |

## 6. File ngoài Java và dependency

`M = services/payment-service/`, `R = repository root`. Đây là danh sách file được phép sửa khi thực thi sau gate.

| File | Thay đổi dự kiến |
|---|---|
| M/pom.xml | Thêm mapstruct1.6.3, lombok-mapstruct-binding0.2.0 và mapstruct-processor ở annotationProcessorPaths main/test; compiler release21. Giữ Enforcer/Spotless/JaCoCo/SpotBugs. Profile payment-persistence-it thêm Failsafe integration-test+verify, includes **/PaymentPersistenceIT.java, failIfNoTests=true; version lấy parent quản lý hoặc đối chiếu effective POM trước khi cố định |
| M/src/main/resources/application.yaml | Giữ tên/import Config Server bắt buộc; không đưa runtime secret/config vào local file |
| R/infrastructure/config-server/src/main/resources/config/payment-service.yaml | Port8083, Hikari/schema/UTC/JSONB/ddl-auto=none, Rabbit env/confirms/returns/mandatory/dynamic=false/default MANUAL, issuer-uri và structured logging như spec |
| M/src/test/resources/application.yaml | Giữ Config/Eureka/data/broker isolation; config test riêng không áp production. Các mock là annotation trong test, không fake bean production |
| M/src/test/resources/application-payment-persistence-it.yaml | Datasource từ PAYMENT_TEST_DB_URL/USERNAME/PASSWORD, default_schema payment_schema, JSON mapper jackson3, JDBC UTC, open-in-view=false, ddl-auto=validate; không kế thừa SET ROLE production nếu test account không dùng mô hình đó |
| M/src/test/resources/fixtures/payment/purchase-quantity.json | Full PurchaseSnapshot v1 QUANTITY hợp lệ, giá trị giả, explicit nullable field |
| M/src/test/resources/fixtures/payment/purchase-seated.json | Full snapshot có seat, đủ quantity/unique IDs |
| M/src/test/resources/fixtures/payment/purchase-standing.json | Full snapshot SEAT_MAP không seat, sector nhất quán |
| M/src/test/resources/fixtures/payment/start-payment-envelope.json | Full Envelope<StartPayment>, messageId=requestId |
| M/src/test/resources/fixtures/payment/event-cancelled-envelope.json | Full cancellation gồm sales/access, version/ID/CANCELLED nhất quán |
| M/src/test/resources/fixtures/payment/message-cases.json | Các envelope/message còn lại trong spec §5, đủ CREATED/REJECTED/FAILED/COMPLETED; test chọn type theo allowlist, không class name từ JSON |
| M/src/test/resources/fixtures/payment/persistence.sql | Dữ liệu test giả cho 13 bảng, FK đúng và snapshot đầy đủ; không DDL/migration; @Sql tham gia transaction rollback của mỗi IT |
| M/HANDOFF.md | Ghi cách chạy/giới hạn, bỏ hiểu nhầm Redis/Feign/provider đã có; env và chứng cứ thực tế, không commit secret |
| R/.env.example | Chỉ bổ sung biến runtime thực sự còn thiếu, nếu đã có thì giữ; PAYMENT_TEST_DB_* được mô tả trong handoff thay vì bắt buộc env production |

Không cần dependency mới cho Nimbus, Mockito, OutputCapture, H2 hoặc container; kiểm effective dependency trước khi thêm bất kỳ test artifact còn thiếu. Không thêm spotbugs-annotations hoặc suppression sẵn chỉ vì Booking có; chỉ xử lý finding thật và duyệt delta annotation nếu cần.

## 7. Các task triển khai

### P1 — Chốt baseline và toolchain

- **Phụ thuộc:** gate WRITING-PLANS và EXECUTING-PLANS được xác nhận đúng danh sách; kiểm lại status để giữ thay đổi sẵn có.
- **File/item:** M/pom.xml; M-R1/M-R2, E1/D1; bộ processor phục vụ C92/M-G8.
- Giữ Java21/release21/Enforcer `[21,22)`; cấu hình MapStruct/binding và Failsafe opt-in như §6. Ghi effective dependency hiện có, không copy toàn POM Booking.
- Chuẩn bị sửa annotation context test khi các repository type đã tồn tại ở P3; không thêm khai báo Java tạm để build từng bước.
- **Xác minh:** `rtk proxy .\mvnw.cmd -version`, `rtk proxy .\mvnw.cmd -B -ntp validate`, `rtk proxy .\mvnw.cmd -B -ntp help:effective-pom -Doutput=target/effective-pom.xml`. Mong đợi JDK21/gate qua; processors cho main/test và quality gates đúng. Chưa kết luận full compile khi các type phụ thuộc chưa hoàn tất.

### P2 — Dựng hợp đồng, JSON và rule validation

- **Phụ thuộc:** P1.
- **File/item:** C16–C53 và nested type, C81/C90; M-G3/M-G4/M-G7, M-D1–12, M-V1–9, M-C1; C87/C88 + M-E1–6 dựng cùng batch để JSON/mapper có error type; T1/MT1; sáu JSON fixture §6.
- Viết class/creator/getter đúng bảng; list copy trong constructor; field validation và @AssertTrue @JsonIgnore null-safe. Discount chỉ hai subtype/name allowlist; giữ key isHidden. Mã lỗi/status/message nền theo Booking, bỏ import dependency không dùng.
- Dùng Boot JsonMapper customizer, không new mapper default trong đường test cần kiểm config. JSON tests parse rồi Validator.validate để chứng minh rule chạy; Money/version không coercion/rounding, missing nullable field khác explicit null.
- **Xác minh:** `rtk proxy .\mvnw.cmd -B -ntp -Dtest=PaymentContractTests test`. Mong đợi fixtures hợp lệ qua, từng biến thể malformed/unknown/missing/wrong type/range/invariant thất bại đúng boundary. Enum CLOSED theo v1, payload type header không quyết định Java type.

### P3 — Mapping 13 bảng và repository

- **Phụ thuộc:** P2 vì typed JSONB/enum.
- **File/item:** C1–C15/C60–C72; M-G1/M-G2/M-G9; runtime persistence YAML §6.
- Map đầy đủ field/column/nullable/PK, JSONB typed PurchaseSnapshot và JsonNode outbox/inbox, money/fee precision, version chỉ năm bảng. Không JPA Order xuyên Booking, không association đơn thay FK ghép, không tự sửa DDL.
- Dựng JpaRepository<Entity,Id> rỗng, chưa lock/custom query; entity ID gán trước giữ ý nghĩa version null, chưa insert use case.
- **Xác minh:** `rtk proxy .\mvnw.cmd -B -ntp -DskipTests compile`; đối chiếu bảng field với DDL đủ13 bảng/hai embedded ID. PostgreSQL validation/constraint sẽ kiểm P7, compile chưa chứng minh schema.

### P4 — Mapper và khung feature

- **Phụ thuộc:** P2–P3.
- **File/item:** C73–C80/C91–C92; M-G5/M-G6/M-G8, M-M1–4; T2/MT2; E1/D1.
- Service interface/implementation và controller đúng bảng, dependency final/constructor Lombok; chưa handler hoặc business method. Không dựng service Saga/messaging rỗng vì chưa được giao.
- PaymentMapper map hai nguồn cùng order; expiresAt lấy snapshot. Refund map id/amount/state/reason; @Named toMoney và @BeforeMapping kiểm nguồn. Generated implementation phải constructor-inject WireValueConverter, không default conversion ngầm.
- Cập nhật test context E1 mock đúng mười repository và JwtDecoder; không tắt service scan production để test boot qua.
- **Xác minh:** `rtk proxy .\mvnw.cmd -B -ntp -Dtest=PaymentMapperTests,PaymentServiceApplicationTests test`; đọc target/generated-sources/annotations/com/flashticket/payment_service/mapper/PaymentMapperImpl.java. Mong đợi mapping đúng, mismatch order/fractional/out-of-range bị reject, context boot không network.

### P5 — Runtime, Rabbit và security boundary

- **Phụ thuộc:** P4, error type ở P2; thực hiện với advice P6 trong cùng đợt trước chạy HTTP tests.
- **File/item:** C82–C84, M-C2–8, T3/MT3+MT3a–c, T5/MT5; config/local/test YAML §6; không đổi PaymentServiceApplication annotation.
- Cấu hình Rabbit converter dùng JsonMapper chung và trusted package Payment, không broker declarations/listener/error handler. Runtime giữ default MANUAL/prefetch20 nhưng chưa có ACK.
- Security MVC theo spec: health GET public, actuator còn lại denyAll, GET payment BUYER, internal INTERNAL_SERVICE, admin ADMIN, fallback authenticated. JWT issuer discovery, role allowlist, 401/403 resolver/challenge. Không ownership giả trong matcher, không mở callback VNPay.
- T3 dùng local issuer/discovery/JWKS và JWT ký thật; role đúng trên route chưa handler được phép tới MVC trả404, không đòi200 từ endpoint giả. Health dùng actuator thật. T5 kiểm binding/bean trong ApplicationContextRunner không kết nối DB/broker.
- **Xác minh sau P6:** `rtk proxy .\mvnw.cmd -B -ntp -Dtest=PaymentSecurityTests,PaymentRuntimeConfigurationTests test`. Mong đợi401/403/headers/role chính xác, token sai issuer/expiry/chữ ký bị chặn, converter/settings khớp và không startup worker.

### P6 — Global exception và JSON logging

- **Phụ thuộc:** P2/P5 security callbacks; P5–P6 có thể viết theo cùng batch, không claim một phần đã pass khi bean chưa đủ.
- **File/item:** C52/C53, C85–C89; M-C9–10/M-E7–12; T4/T4a, MT4 và MT4a–d; logging YAML Payment.
- Advice trả Problem trực tiếp và log an toàn; phân biệt input400/output500, HTTP framework headers/status, disconnect/committed response theo Booking. Giới hạn violation50/cause traversal16/stack8192 như mẫu đã đọc.
- X-Correlation-Id UUID hợp lệ hoặc mới; header/body/log phải khớp. Metadata owner và allowlist log native; SafeStackTracePrinter không exception message; không CorrelationFilter/RCA publisher.
- Log tests khởi logging-only SpringApplication từ logging.* thực của Payment, dùng CapturedOutput. Đóng context/clearMdc, không appender/helper anonymous hoặc probe business controller.
- **Xác minh:** `rtk proxy .\mvnw.cmd -B -ntp -Dtest=PaymentErrorLoggingTests,PaymentSecurityTests test`. Mong đợi correlation/status/headers đúng, JSON log owner/schema/time chuẩn, sentinel secret/SQL/PII/body/payment URL không xuất hiện.

### P7 — Persistence IT trên PostgreSQL cô lập

- **Phụ thuộc:** P3/P6; POM profile ở P1; DB test đã provision riêng.
- **File/item:** T6/MT6/MT6a, M/src/test/resources/application-payment-persistence-it.yaml, fixtures/payment/persistence.sql; dùng lại entity/repository, không method production mới.
- Người vận hành chuẩn bị instance PostgreSQL cô lập với database payment_db/schema payment_schema, 13 bảng tbl_* và constraint từ DDL chuẩn + prefix script. DDL chứa lệnh psql/SET ROLE, prefix script kiểm tên business DB: không chạy mù với database tên khác hoặc chuyển thành migration Java. Plan không sửa/chạy DDL ở DB chia sẻ.
- Env PAYMENT_TEST_DB_URL/USERNAME/PASSWORD bắt buộc, không fallback runtime. Slice JPA bật datasource/JPA trở lại qua property exclude rỗng, ddl-auto=validate, không Rabbit/Eureka/Keycloak. @Sql seed có full snapshot, transaction rollback từng test.
- Kiểm đọc/roundtrip JSONB, embedded key, năm version field; kiểm FK ghép amount/currency/order/merchant và partial unique unfinished attempt. Dùng transaction/savepoint độc lập cho mỗi lỗi SQL dự kiến, không tiếp tục query transaction PostgreSQL đã aborted; đây là test DB constraint, không triển khai transaction use case.
- **Xác minh:** `rtk proxy .\mvnw.cmd -B -ntp -Ppayment-persistence-it verify`. Mong đợi cả unit tests và đúng bốn IT qua, failsafe-reports có result; thiếu DB ghi NOT_RUN, bật profile mà env thiếu phải fail rõ thay vì skip silently.

### P8 — Verify tổng, smoke có điều kiện và bàn giao

- **Phụ thuộc:** P1–P6; P7 ghi trạng thái riêng theo hạ tầng, không gọi unit verify là đã chạy IT.
- **File/item:** toàn bộ item đã duyệt, M/HANDOFF.md và .env.example nếu thiếu biến thực dùng; không thêm Java method/type.
- Chạy `rtk proxy .\mvnw.cmd -B -ntp verify` với JDK21, giữ Enforcer/Spotless/SpotBugs/JaCoCo. Nếu format lỗi, chỉ chạy Spotless trong module Payment rồi kiểm lại diff. Không blanket suppression/skip quality gate.
- Khi Config Server/Eureka/DB/broker đã có: chạy service với env runtime được nạp vào process; xác nhận Config Server load, Eureka PAYMENT-SERVICE, health, query đọc DB và broker connection riêng. Không gửi message nghiệp vụ/ghi DB live, không đổi queue/role/schema để làm smoke xanh. Dừng process do task khởi tạo sau kiểm tra.
- Bàn giao từng PASSED/FAILED/NOT_RUN, command/log đã lọc, file thay đổi, điều kiện để chạy lại. Ghi rõ chưa handler nghiệp vụ, ownership GET, consumer ACK, outbox relay, VNPay/Saga/refund/payout hoặc tracing toàn B16. Không commit/push/merge/deploy khi chưa yêu cầu.

## 8. Ngoại lệ và gate xác nhận

X1–X7 của spec được đề xuất lại cho pha WRITING-PLANS: package/immutable creator, JPA JSONB và kiểu dữ liệu, MapStruct, JWT Keycloak, Jackson/AMQP, advice/logging, ddl-auto=none/default ACK scaffold. D1–D4 là phần làm rõ/bổ sung ở plan, bao gồm E1/T4a và helper/lifecycle/signature test trong §5.1.

- **Danh sách cần duyệt:** C1–C53 kể cả nested item, C60–C92, E1, T1–T6/T4a; mọi M-G/M-R/M-D/M-V/M-M/M-C/M-E/MT; X1–X7 và D1–D4.
- **Trạng thái:** spec/plan v1 và gate EXECUTING-PLANS **APPROVED**. Nguồn: user trả lời “xác nhận” sau câu hỏi đầy đủ về spec/plan v1, class/method/API sinh và X1–X7/D1–D4.
- **Xác nhận thực thi:** đúng C1–C53/C60–C92, E1/T1–T6/T4a và mọi method đã liệt kê; không phát sinh Java type/method/helper ngoài danh sách.
- Chỉ cập nhật APPROVED theo phản hồi cụ thể. Nếu phát sinh helper/type/annotation/style khác khi chỉnh plan, trình delta; không thêm dưới nhãn “helper cần thiết”. Phần thực thi và bằng chứng đã ghi tại §9.

**Bằng chứng khi lập plan (trước thực thi):** đã đọc source/config/skill/convention và đối chiếu tài liệu framework; chưa chạy Maven/test/smoke. Các lệnh P1–P8 là hướng dẫn thực thi sau gate, không phải kết quả đã chạy.


## 9. Kết quả thực thi — 28/10/2026

Nguồn phê duyệt: câu user “xác nhận” trong lượt ngay sau bảng hỏi gate EXECUTING-PLANS. Không tự commit/push/merge, tạo worktree hoặc deploy.

| Bước | Trạng thái | Bằng chứng/giới hạn |
|---|---|---|
| P1 | DONE / PASSED | Microsoft OpenJDK21.0.13.0.2; Enforcer [21,22) qua; effective POM và processors main/test đúng. Không đổi JDK toàn hệ thống |
| P2 | DONE / PASSED | Class DTO/creator và rule contract; 5 contract test qua, có fixture/nullable/strict coercion/variant |
| P3 | IMPLEMENTED / COMPILE PASSED | 13 entity/repository, hai ID ghép, version đúng năm bảng. Chưa chứng minh mapping/constraint bằng PostgreSQL live |
| P4 | DONE / PASSED | MapStruct constructor injection, hai mapping + guard; 4 mapper test và context test qua |
| P5 | DONE / PASSED (isolated) | 4 security test JWT ký thật/local discovery-JWKS và 3 runtime config test qua. Chưa nối Keycloak/broker production |
| P6 | DONE / PASSED | 4 error/log test qua; correlation header/body/log khớp, khôi phục MDC; log loại sentinel secret |
| P7 | IMPLEMENTED / IT NOT_RUN | Bốn IT + SQL seed + profile đã compile. PAYMENT_TEST_DB_URL/USERNAME/PASSWORD chưa được cấp; không tự tạo môi trường hoặc dùng DB runtime thay thế |
| P8 | VERIFY/HANDOFF DONE; SMOKE NOT_RUN | Maven verify thành công, handoff cập nhật. Config Server/Eureka localhost reachable; config Payment đang được phục vụ chưa có PaymentLogFields mới |

Lệnh chính tại module với JAVA_HOME chỉ cho process trỏ JDK21:

    rtk proxy .\mvnw.cmd -B -ntp validate help:effective-pom -Doutput=target/effective-pom.xml compile
    rtk proxy .\mvnw.cmd -B -ntp spotless:apply verify

Kết quả verify cuối: BUILD SUCCESS, 21 tests / 0 failures / 0 errors / 0 skipped; Spotless 88 Java files sạch, JaCoCo report sinh được, SpotBugs 0 finding. Report trong services/payment-service/target/surefire-reports và target/site/jacoco; log target/verify.log. Chỉ sửa format POM sau verify và đã kiểm canonical XML không thay đổi nội dung.

Các lỗi phát hiện trong quá trình kiểm đã sửa trong chính API/phong cách được duyệt: ép kiểu ObjectNode/generic assert, loại Pattern gắn nhầm vào SectorType/Discount. Không thêm declaration hoặc suppression để làm test xanh.

Các phần chưa kiểm không được coi PASSED: PostgreSQL mapping/FK/partial unique ở P7; boot/DB/broker/Eureka thật với YAML mới ở P8. Handoff ghi điều kiện và lệnh chạy lại. Không thêm business handler, consumer, worker, relay, VNPay/Saga/refund/payout hoặc ownership GET.

[Handoff Payment](../../../services/payment-service/HANDOFF.md).
