# Spec — Setup scaffold Payment Service

- Ngày: 28/10/2026.
- Pha: BRAINSTORMING, bản nháp v1.
- Trạng thái: **APPROVED** — user trả lời “xác nhận” ngày 28/10/2026 cho spec/plan v1 và danh sách pha EXECUTING-PLANS, gồm delta D1–D4 trong plan. Phê duyệt áp dụng đúng danh sách đã trình, bao gồm phần làm rõ D1–D4 trong plan.
- Yêu cầu nguồn: viết spec scaffold Payment, dùng skill shopapp-java-brainstorming, tham khảo Booking.
- Đây là tài liệu thiết kế; kết quả triển khai và giới hạn kiểm chứng ghi tại plan §9 và Handoff Payment.

## 1. Mục tiêu và phạm vi

Dựng nền tảng Payment Service theo cấu trúc Booking hiện tại, đủ model, hợp đồng và cấu hình để triển khai các use case thanh toán sau này. Kết quả phải compile/verify được và có kiểm chứng JSON, persistence, security, error/logging trong phạm vi scaffold.

Bao gồm 13 entity Payment, hai khóa ghép, enum, repository; DTO REST/snapshot/message và validation; MapStruct; khung service interface/implementation, controller rỗng; Config Client/Eureka, PostgreSQL/JPA, JSON Jackson 3, JWT Keycloak, RabbitMQ converter và global exception/logging.

Chưa làm handler REST nghiệp vụ, RabbitListener, scheduler, outbox relay, retry worker, transaction use case, VNPay/IPN/Return, tạo QR, Saga/refund/payout. Chưa thêm Feign/client credentials, Redis, SMTP, resilience annotations hoặc shared module. Không sửa Booking/Gateway, DB đang chạy hoặc topology broker. Controller chưa handler không trả thành công giả.

## 2. Nguồn và hiện trạng

| Nguồn | Nội dung áp dụng |
|---|---|
| [Playbook scaffold](../../guides/agent-service-scaffold-playbook.md) | Phạm vi, cấu trúc, giới hạn boundary/worker/logging |
| [B12](../../architecture/B12-data-ownership-and-schema.md), [DDL Payment](../../architecture/data/postgres/30-payment.sql) | Owner, toàn bộ bảng/cột/constraint/version |
| [B13](../../architecture/B13-api-and-event-contracts.md), [B14](../../architecture/B14-main-flow-sequences.md), [B7](../../domain/B7-aggregates-and-invariants.md) | JSON, message, ownership, bất biến và ranh giới transaction |
| [B15](../../architecture/B15-verification-plan.md), [B16](../../architecture/B16-observability-baseline.md) | Điều kiện kiểm chứng, phạm vi log |
| [Payment handoff](../../../services/payment-service/HANDOFF.md), [runtime baseline](../../architecture/aws-runtime-baseline.md) | Schema, role runtime, env |
| [Rabbit definitions](../../../deploy/rabbitmq/definitions.json), [Gateway README](../../../infrastructure/api-gateway/README.md) | Queue/exchange, phân tuyến Payment/Booking |
| [Booking source](../../../services/booking-service/src/main/java/com/flashticket/booking_service/), [POM](../../../services/booking-service/pom.xml), [runtime YAML](../../../infrastructure/config-server/src/main/resources/config/booking-service.yaml) | Pattern hiện tại tại checkout |

Đã đọc source Booking qua CodeGraph: SecurityConfig, BookingJsonConfig, RabbitMqConfig, BookingLogFields, SafeStackTracePrinter, KeycloakRealmRoleConverter, ErrorCode, BusinessException, GlobalExceptionHandler, Problem, PurchaseSnapshot, SalesConfiguration, PromotionConfig, Envelope, EventCancellation, MappingValueConverter, OutboxMessageMapper, entity outbox/inbox và khung InternalOrderController/PromotionServiceImpl.

Đã đọc mẫu Shop App tại `C:/Users/sondimid/IdeaProjects/shopapp-backend/shopapp-backend/shopapp/src/main/java/com/project/shopapp/`: ShopappApplication, CategoryDTO, Category, CategoryRepository, OrderController, MapperConfiguration. Áp dụng class Lombok, validation, Spring Data, constructor injection và cấu hình bean đơn giản; giữ kiểu dữ liệu và contract FlashTicket.

Payment hiện chỉ có bootstrap, application/test YAML và POM nền: Boot 4.1.1, Cloud 2025.1.3, Java21, Enforcer `[21,22)`, JPA/PostgreSQL, AMQP, MVC, resource server, validation, Config/Eureka và Lombok. Chưa có model/DTO/mapper/repository/security/error riêng. Test YAML đang loại datasource/JPA/Rabbit auto-config nên context test không chứng minh hạ tầng thật.

Các khác biệt phải giữ:

- Payment giữ **Java21**, không copy Java23 từ Booking.
- Theo convention hiện hành, chỉ dùng **issuer-uri** discovery; không cấu hình jwk-set-uri hoặc audience runtime check. Khác ví dụ cũ trong playbook/B13, không tuyên bố kiểm audience đã được đáp ứng.
- Payment giữ **ddl-auto=none** theo handoff. Convention `update` đang áp dụng riêng Booking, chưa mở rộng sang Payment.
- Inbox Payment không có row_version trong DDL; không copy version mới của Booking.
- Runtime exchange từ env, fallback **business.ex** theo topology; tên logic flash-ticket.v1 trong B13 không khiến scaffold provision exchange mới.
- B13/B14 v0.2 còn REVIEW_READY; dùng làm baseline tích hợp, không tự nâng thành APPROVED.
- Booking có thay đổi dở tại checkout; chỉ tham khảo, không sửa hoặc đưa thay đổi đó vào task Payment.

## 3. Cấu trúc và persistence

Gốc Java: `services/payment-service/src/main/java/com/flashticket/payment_service/`.

Giữ `config/`, `security/`, `exception/`, `entity/`, `enums/`, `dto/{response,snapshot,message}/`, `mapper/`, `validation/`. Feature `payment`, `refund`, `finance` có service/impl và repository; controller chỉ tạo cho payment/finance. Saga/messaging chỉ có repository. Không tạo package rỗng, BaseEntity, BaseResponse hoặc generic CRUD framework.

Mapping `@Table(name="tbl_<table>",schema="payment_schema")`. UUID→UUID; timestamptz→Instant; bigint→Long; tiền→BigDecimal precision19/scale0; feeRate→BigDecimal precision9/scale8. Enum STRING. Snapshot JSONB typed, outbox/inbox JSONB JsonNode của Jackson3. Không association xuyên owner. Scaffold giữ FK dạng ID scalar; FK ghép/unique/partial index hiện có do DB bảo vệ, không thay bằng FK đơn khác nghĩa.

`@Version Long rowVersion` chỉ ở PaymentAttempt, Refund, EventRefundRun, EventPayout, PaymentSaga; entity mới giữ null trước save. Entity ID gán trước nhưng không version chưa có luồng insert trong task này; không giả định save luôn INSERT hoặc thêm version ngoài DDL. Không thêm lock, transaction use case, cascade REMOVE/orphanRemoval hoặc lifecycle timestamp.

## 4. Bảng duyệt type dữ liệu

Tất cả đề xuất mới trong tài liệu là **NEW / APPROVED**. Ký hiệu `?` nghĩa nullable. Tên bảng là canonical trước prefix `tbl_`.

### 4.1 Entity và khóa ghép

C1–C13: `entity/<Class>.java`, `@Entity @Table @Getter @Setter @NoArgsConstructor`; getter/setter cho từng field bên dưới, constructor public không tham số. Không builder/equals/hashCode/toString. Column snake_case, nullability đúng DDL; JSONB dùng `@JdbcTypeCode(SqlTypes.JSON)`.

| ID | Class / bảng | Field đầy đủ, PK và annotation riêng |
|---|---|---|
| C1 | OrderPaymentSnapshot / order_payment_snapshots | PK UUID orderId; UUID eventId; String buyerSubject; BigDecimal expectedAmount; Currency currency; Instant expiresAt; Long sourceOrderVersion; PurchaseSnapshot purchaseSnapshot JSONB; String snapshotHash; Instant receivedAt |
| C2 | PaymentAttempt / payment_attempts | PK UUID id; UUID orderId; String provider,merchantAccount; BigDecimal expectedAmount; Currency currency; Instant startedAt,finishedAt?; PaymentAttemptState state; UUID requestKey; String requestHash,paymentUrl?; @Version Long rowVersion |
| C3 | Charge / charges | PK UUID id; UUID attemptId,orderId; String provider,merchantAccount,providerChargeId; BigDecimal amount; Currency currency; Instant chargedAt,recordedAt |
| C4 | PaymentConfirmation / payment_confirmations | PK UUID orderId; UUID chargeId; BigDecimal amount; Currency currency; Instant confirmedAt |
| C5 | Refund / refunds | PK UUID id; UUID chargeId,orderId; BigDecimal amount; Currency currency; RefundReason reason; RefundState state; Instant createdAt,completedAt?; @Version Long rowVersion |
| C6 | RefundAttempt / refund_attempts | PK UUID id; UUID refundId; String requestKey,providerRefundId?; Instant startedAt,finishedAt?; String outcome? |
| C7 | EventRefundRun / event_refund_runs | PK UUID id; UUID eventId,sourceCancellationId; String selectionCursor?; Boolean selectionComplete; Long selectedOrderCount; Instant startedAt,completedAt?; @Version Long rowVersion |
| C8 | EventRefundItem / event_refund_items | @EmbeddedId EventRefundItemId id; UUID eventId,confirmedChargeId?,refundId?; EventRefundItemState state; String lastErrorCode?; Instant updatedAt |
| C9 | EventPayout / event_payouts | PK UUID eventId; String organizerSubject; Currency currency; BigDecimal validGross,validRefunds,feeAmount,netAmount,feeRate; Long sourceFeeVersion; Instant eventEndsAt; Long paymentPendingCount,refundPendingCount; Instant asOf; String reconciledBy?; Instant reconciledAt?; String reconciliationEvidence?,paidBy?; Instant paidAt?; @Version Long rowVersion |
| C10 | EventFinanceSnapshot / event_finance_snapshots | PK UUID eventId; String organizerSubject?; Instant eventEndsAt?; BigDecimal feeRate?; Long sourceFeeVersion?; UUID cancellationId?; Instant cancelledAt?; Instant receivedAt |
| C11 | PaymentSaga / payment_sagas | PK UUID orderId; UUID chargeId; PaymentSagaState state; UUID commandId; Instant nextAttemptAt; String lastErrorCode?; @Version Long rowVersion; Instant createdAt,updatedAt |
| C12 | OutboxMessage / outbox_messages | PK UUID messageId; UUID aggregateId; Long aggregateVersion; String messageType; Integer schemaVersion; JsonNode payload; Instant occurredAt; UUID correlationId,causationId?; String traceparent?; Instant publishedAt?; Integer attempts; Instant nextAttemptAt; String lastErrorCode? |
| C13 | InboxMessage / inbox_messages | @EmbeddedId InboxMessageId id; String payloadHash; Instant processedAt; JsonNode responsePayload?; không version |
| C14 | entity/EventRefundItemId.java | UUID runId,orderId; @Embeddable @Getter @Setter @NoArgsConstructor @EqualsAndHashCode; Serializable, serialVersionUID=1L |
| C15 | entity/InboxMessageId.java | String consumerName; UUID messageId; annotation/API như C14 |

C14/C15 sinh getter/setter, public no-arg constructor, equals(Object)/hashCode chỉ field khóa. Mẫu là entity/embedded ID Booking, điều chỉnh đúng DDL Payment. Nullable finance configuration và outcome của RefundAttempt phải được giữ.

### 4.2 Enum

Mỗi type tại `enums/<Type>.java`, không field/method/constructor tự viết; chỉ API enum chuẩn do Java sinh.

| ID | Type | Giá trị |
|---|---|---|
| C16 | Currency | VND |
| C17 | PaymentAttemptState | PENDING, UNKNOWN, SUCCEEDED, FAILED |
| C18 | RefundState | PENDING, PROCESSING, SUCCEEDED, FAILED |
| C19 | RefundReason | LATE_PAYMENT, DUPLICATE_PAYMENT, TICKET_ISSUANCE_FAILED, EVENT_CANCELLED |
| C20 | EventRefundItemState | SELECTED, REFUND_PENDING, REFUNDED, NEEDS_ATTENTION |
| C21 | PaymentSagaState | WAITING_BOOKING, WAITING_TICKETS, COMPLETED, COMPENSATING, COMPENSATED |
| C22 | SalesMode | QUANTITY, SEAT_MAP |
| C23 | SectorType | STANDING, SEATED |
| C24 | EventState | DRAFT, PENDING_APPROVAL, APPROVED, PUBLISHED, CANCELLED |
| C25 | PaymentProvider | VNPAY; chỉ wire v1, ledger provider vẫn String |
| C26 | PaymentDecisionOutcome | ACCEPTED, LATE, DUPLICATE, CANCELLED |
| C27 | IssuanceState | COMPLETED, FAILED |
| C28 | StartPaymentResultState | CREATED, REJECTED |
| C29 | StartRequestState | WAITING, CREATED, REJECTED |

### 4.3 DTO và chữ ký constructor theo field

Dùng **class Lombok**, không record. Class DTO cụ thể là public final; nested DTO là public static final. C30–C47 và nested type tương ứng dùng @Getter với field private final; một public constructor explicit nhận đầy đủ field đúng thứ tự bảng, @JsonCreator(PROPERTIES), mỗi tham số @JsonProperty(required=true). Không setter/builder/equals/hashCode/toString. Copy list trong constructor bằng unmodifiable ArrayList khi khác null; giữ null/phần tử null cho Validator báo lỗi, không tạo helper copy. C45 là interface, không áp dụng constructor/class annotation.

C48–C53 là output-only, dùng @Getter @AllArgsConstructor, public constructor theo đúng thứ tự field. Field bắt buộc @NotNull/@NotBlank, nested @Valid và phần tử @NotNull; nullable vẫn phải hiện diện trên input JSON. Mẫu Shop App CategoryDTO/Booking Problem, điều chỉnh immutable và creator theo contract.

| ID | File/type | Toàn bộ field theo thứ tự constructor |
|---|---|---|
| C30 | dto/message/Envelope.java, class Envelope<T> | UUID messageId; String messageType; Integer schemaVersion; UUID aggregateId; String aggregateVersion; Instant occurredAt; UUID correlationId,causationId?; String traceparent?; T data |
| C31 | dto/message/StartPayment.java | UUID requestId,requestKey; String requestHash; PaymentProvider provider; PurchaseSnapshot purchase |
| C32 | dto/message/StartPaymentResult.java | UUID requestId,orderId; StartPaymentResultState state; UUID attemptId?; String failureCode? |
| C33 | dto/message/FinanceConfiguration.java | UUID eventId; String organizerSubject; Instant eventEndsAt; String feeRate,sourceFeeVersion; Currency currency |
| C34 | dto/message/EventCancellation.java | UUID eventId,cancellationId; String sourceConfigVersion; Instant cancelledAt; String actorSubject; SalesConfiguration sales; EventCancellation.AccessConfiguration access |
| C34a | nested EventCancellation.AccessConfiguration | UUID eventId; String organizerSubject; EventState eventStatus; Instant eventStartsAt,eventEndsAt; String sourceConfigVersion |
| C35 | dto/message/AcceptPayment.java | UUID orderId,chargeId,attemptId; String amount; Currency currency; String sourceOrderVersion |
| C36 | dto/message/PaymentDecision.java | UUID orderId,chargeId; Instant decisionAt; PaymentDecisionOutcome outcome; UUID acceptedChargeId? |
| C37 | dto/message/IssueTickets.java | UUID orderId,chargeId; PurchaseSnapshot purchase |
| C38 | dto/message/IssuanceResult.java | UUID orderId,chargeId,issuanceId; IssuanceState state; Integer ticketCount; String failureCode? |
| C39 | dto/message/OrderCompleted.java | UUID orderId,chargeId,issuanceId; Integer ticketCount |
| C40 | dto/message/BookingIssuanceResult.java | UUID orderId,chargeId,issuanceId; IssuanceState state; String failureCode |
| C41 | dto/snapshot/PurchaseSnapshot.java | Integer schemaVersion; UUID orderId,eventId; String buyerSubject,customerEmail,sourceConfigVersion,sourceOrderVersion; Instant frozenAt,expiresAt; Currency currency; String eventTitle; Instant eventStartsAt,eventEndsAt; String venueName?; SalesMode mode; UUID sectorId?; String subtotal,discount,total; PurchaseSnapshot.AppliedPromotionSnapshot promotion?; List<PurchaseSnapshot.Item> items |
| C41a | nested PurchaseSnapshot.AppliedPromotionSnapshot | UUID promotionId; String code,discount |
| C41b | nested PurchaseSnapshot.Item | UUID orderItemId,ticketTypeId; String ticketTypeName; Integer quantity; String unitPrice,lineSubtotal; UUID sectorId?; List<SeatSelectionSnapshot> seats |
| C42 | dto/snapshot/SeatSelectionSnapshot.java | UUID seatId; String seatLabel,rowName,seatNumber |
| C43 | dto/snapshot/SalesConfiguration.java | Integer schemaVersion; UUID eventId; String organizerSubject,sourceConfigVersion; EventState eventStatus; String eventTitle; Instant eventStartsAt,eventEndsAt; String venueName?; SalesMode mode; Instant saleStartsAt,saleEndsAt; Integer purchaseLimit; List<SalesConfiguration.TicketTypeConfig> ticketTypes; List<SalesConfiguration.SectorConfig> sectors; List<SalesConfiguration.SeatConfig> seats; List<PromotionConfig> promotions |
| C43a | nested SalesConfiguration.TicketTypeConfig | UUID ticketTypeId; String name,unitPrice; Currency currency; UUID sectorId?; String configuredQuantity?,colorCode? |
| C43b | nested SalesConfiguration.SectorConfig | UUID sectorId; SectorType kind; String configuredCapacity |
| C43c | nested SalesConfiguration.SeatConfig | UUID seatId,sectorId,ticketTypeId; String seatLabel,rowName,seatNumber; Boolean isHidden; explicit @JsonProperty("isHidden") giữ đúng wire key |
| C44 | dto/snapshot/PromotionConfig.java | UUID promotionId; String code; Instant startsAt,endsAt; String maximumUses; Integer maximumUsesPerUser; PromotionConfig.Discount discount |
| C45 | nested PromotionConfig.Discount | Interface public String getKind(); @JsonTypeInfo EXISTING_PROPERTY kind, @JsonSubTypes allowlist FIXED/PERCENT như Booking; không sealed |
| C46 | nested PromotionConfig.FixedDiscount | final class implements Discount; String kind,amount; kind chỉ FIXED |
| C47 | nested PromotionConfig.PercentDiscount | final class implements Discount; String kind; Integer percent; kind chỉ PERCENT |
| C48 | dto/response/PaymentStart.java | UUID orderId,attemptId; PaymentAttemptState attemptState; String amount; Currency currency; String paymentUrl?; Instant expiresAt |
| C49 | dto/response/PaymentView.java | UUID orderId,acceptedChargeId?; PaymentStart latestAttempt?; PaymentView.StartRequest startRequest?; List<PaymentView.RefundView> refunds |
| C50 | nested PaymentView.StartRequest | UUID requestId; StartRequestState state; String failureCode? |
| C51 | nested PaymentView.RefundView | UUID refundId,chargeId; String amount; RefundState state; RefundReason reason |
| C52 | dto/response/Problem.java | String code,message; UUID correlationId; List<Problem.Violation> violations |
| C53 | nested Problem.Violation | String field,code |

SalesConfiguration/PromotionConfig chỉ xuất hiện vì cancellation mang full payload; không tạo inventory hoặc logic bán vé tại Payment. Interface Discount có hai variant thực sự trong contract, không thêm hierarchy cho DTO khác.

## 5. API/message và validation

| Contract | Hướng/quyền/queue | Phần scaffold |
|---|---|---|
| GET /api/v1/orders/{orderId}/payment; requestId tùy chọn | Payment, BUYER + ownership snapshot | DTO, matcher, controller rỗng; chưa GET handler |
| POST /api/v1/orders/{orderId}/payment-attempts | Booking | Không dựng endpoint/request tạo attempt tại Payment |
| GET /internal/v1/events/{id}/financial-summary | Payment, INTERNAL_SERVICE | Khung controller; B13 chưa đủ response field nên không tự tạo DTO/handler |
| Provider IPN/Return | Task adapter VNPay | Chưa định nghĩa path/ack hoặc mở permitAll callback |
| event.finance-configured.v1 | Nhận FinanceConfiguration; payment.finance-configured.q | DTO/validation |
| event.cancelled.v1 | Nhận EventCancellation; payment.event-cancelled.q | Đủ sales/access, không cắt field |
| payment.start-requested.v1 | Nhận StartPayment; payment.start.q | Request identity + purchase |
| booking.payment-decision.v1 | Nhận PaymentDecision; payment.decision.q | DTO, chưa xử lý acceptance/refund |
| ticket.issuance-result.v1 | Nhận IssuanceResult; payment.ticket-issuance-failed.q | Parse cả variant; flow sau chỉ bù trừ FAILED |
| booking.order-completed.v1 | Nhận OrderCompleted; payment.order-completed.q | Completion signal đúng cho Saga sau này |
| booking.accept-payment.v1 | Gửi AcceptPayment | DTO, chưa publisher |
| ticket.issue.v1 | Gửi IssueTickets | DTO, chưa publisher |
| booking.issuance-result.v1 | Gửi BookingIssuanceResult FAILED | DTO, chưa publisher |
| StartPaymentResult | Kết quả lưu inbox.response_payload | Không tự tạo queue/routing key mới |

Money: String khớp `^(0|[1-9][0-9]{0,18})$`; giá/tổng thu >0; không float/double/làm tròn input. Dùng @Pattern theo regex tương ứng; version thêm @DecimalMax("9223372036854775807"). Version nguồn là chuỗi dương <=Long.MAX_VALUE; schemaVersion=1; counter cấu hình cho phép 0. Instant wire UTC; UUID đúng dạng. CustomerEmail @Email/@Size(max=254). Số lượng positive integer int32 dùng @Positive; percent @Min(1)/@Max(100), discriminator kind @Pattern chỉ FIXED hoặc PERCENT trên class tương ứng. Nullable field vẫn kiểm pattern khi khác null.

Rule liên trường:

- Purchase: items không rỗng; unique item/seat; lineSubtotal=quantity×unitPrice; tổng dòng=subtotal; total=subtotal−discount>0; promotion null⇒discount=0, nếu có discount phải khớp; frozenAt<expiresAt, eventStartsAt<eventEndsAt. QUANTITY không sector/seat; SEAT_MAP cùng sector; nếu có seat thì đủ quantity và không trùng. Snapshot không có SectorType nên không tự khẳng định STANDING/SEATED từ mode; đối chiếu cấu hình tin cậy thuộc Booking freeze.
- Sales: mode/type/sector/seat tương thích, ID/tham chiếu đúng; QUANTITY sectors/seats rỗng; STANDING không seat; thời gian/range đúng; CANCELLED cho phép các mảng rỗng theo B13.
- Cancellation: outer/sales/access cùng event/version, trạng thái CANCELLED; giữ đầy đủ nested validation.
- Finance: feeRate decimal fraction [0,1], <=8 số lẻ, version dương, VND; không default fee thiếu thành 0.
- Envelope: schema1, đúng allowlist messageType/payload; aggregateVersion hợp lệ; aggregateId khớp event/order của loại payload; StartPayment.requestId=messageId.
- StartPaymentResult: CREATED có attemptId, failureCode=null; REJECTED attemptId=null, failureCode không rỗng.
- PaymentDecision: ACCEPTED acceptedChargeId=chargeId; DUPLICATE acceptedChargeId không null và khác chargeId.
- IssueTickets: orderId=purchase.orderId. IssuanceResult FAILED có count0/code; COMPLETED count>0/code=null. BookingIssuanceResult chỉ FAILED/code không rỗng. Đủ vé so snapshot là điều kiện flow, không DTO đơn lẻ tự chứng minh.

DTO field annotation không tự chạy validation sau JSON mapping. Kiểm fixture theo parse→Validator; khi viết boundary thật phải gọi validation thực sự. Không tạo listener giả để có chỗ validate. Canonical hash, ownership, clock/deadline, freshness và transaction chưa triển khai trong scaffold.

## 6. Cấu hình và hành vi nền

### Runtime/JSON/Rabbit/mapper

- Local application chỉ giữ payment-service và Config Server import bắt buộc; Eureka chung dùng config/application.yaml.
- Remote payment-service.yaml: port `${PAYMENT_SERVICE_PORT:8083}`, PAYMENT_DB_URL/USERNAME/PASSWORD; Hikari max5/minIdle1, SET ROLE payment_owner theo account được provision, không dùng ERD reader. open-in-view=false, ddl-auto=none, default_schema=payment_schema, json_format_mapper=jackson3, jdbc.time_zone=UTC.
- Rabbit env host/port/user/password/vhost; dynamic=false, confirms correlated, publisher-returns=true, mandatory=true, exchange `${RABBITMQ_BUSINESS_EXCHANGE:business.ex}`, prefetch20. MANUAL ACK là default scaffold; chưa listener nên chưa phát sinh ACK. Task consumer sau duyệt disposition riêng, không áp dụng máy móc lựa chọn AUTO của Booking.
- Converter dùng Boot JsonMapper chung; giới hạn trusted payload package Payment, không `*`, không tạo Exchange/Queue/Binding bean hoặc container error handler khi chưa listener.
- JSON từ chối unknown field, missing creator property, number/bool→String, String/Float→Integer, ordinal số→enum. Explicit null vẫn được tại field nullable. Không copy OrderSelectionDeserializer. Dùng tools.jackson.*, annotations vẫn com.fasterxml.jackson.annotation.*.
- MapStruct1.6.3, lombok-mapstruct-binding0.2.0, processors main/test theo Booking, componentModel SPRING, unmappedTargetPolicy ERROR, constructor injection. Giữ Java21 và quality gates hiện có; không thêm ModelMapper.
- Chỉ bổ sung env thực dùng vào .env.example; không ghi/commit .env, IP/tunnel/secret. Chưa cần VNPay, Redis hoặc internal client secret.

### Security/error/logging

MVC SecurityFilterChain, Bearer stateless/CSRF disabled. ERROR dispatcher theo Booking; GET health public; actuator khác denyAll; GET payment BUYER; internal INTERNAL_SERVICE; admin ADMIN; route còn lại authenticated. Matcher không tạo handler. Chưa mở callback public.

Role converter đọc realm_access.roles với allowlist BUYER/ORGANIZER/ADMIN/INTERNAL_SERVICE, bỏ role lạ/trùng, thêm ROLE_. Không tin header X-Role/X-User. Issuer discovery giữ kiểm chữ ký/issuer/expiry, không thêm audience check theo convention. Ownership phải được GET handler sau này kiểm từ buyerSubject snapshot; thiếu snapshot trả PAYMENT_NOT_READY.

401/403 chuyển qua HandlerExceptionResolver vào global advice, giữ WWW-Authenticate. Một GlobalExceptionHandler trả Problem trực tiếp; input validation400/output validation500; giữ framework status/headers. Không biến mọi IllegalArgumentException thành400. ErrorCode chỉ phân loại native dependency đang dùng, không import Feign/OAuth2 client khi chưa dependency.

ErrorCode nền: VALIDATION_FAILED, INVALID_REQUEST, UNAUTHENTICATED, ACCESS_DENIED, ROUTE_NOT_FOUND, METHOD_NOT_ALLOWED, NOT_ACCEPTABLE, UNSUPPORTED_MEDIA_TYPE, CONCURRENT_MODIFICATION, RESOURCE_BUSY, DATASTORE_TIMEOUT, DATASTORE_UNAVAILABLE, MESSAGE_BROKER_UNAVAILABLE, INVALID_MESSAGE, INVALID_MESSAGE_ENVELOPE, INVALID_MESSAGE_SCHEMA, INVALID_MESSAGE_TYPE, INVALID_MESSAGE_PAYLOAD, INVALID_MONEY_VALUE, INVALID_VERSION, INTERNAL_ERROR. Thêm PAYMENT_NOT_READY404, IDEMPOTENCY_CONFLICT409, MESSAGE_ID_CONFLICT409; các mã nền lấy status/message an toàn tương ứng Booking. Không copy lỗi ghế/promotion.

Correlation lỗi lấy X-Correlation-Id UUID hợp lệ hoặc tạo mới; header/body/log khớp, tối đa50 violations. JSON logging native allowlist, payment-service/version/instance/environment, schemaVersion1/timestamp UTC, safe stack bỏ exception message. Không log JWT/email/SQL/body/raw IPN/payment URL/secret. Không thêm CorrelationFilter, custom /error, framework observability hoặc publisher RCA. Native /error ngoài advice; chưa correlation mọi request thành công hoặc instrumentation toàn B16.

## 7. Type nền, repository và khung feature

Tất cả NEW/APPROVED; đường dẫn tương đối gốc Java §3. Repository chỉ kế thừa JpaRepository, chưa custom query/lock. Service interface rỗng; implementation @Service @RequiredArgsConstructor, dependency private final. Controller @RestController @RequestMapping @RequiredArgsConstructor, chưa handler.

| ID | File/type | Field/API sinh, trách nhiệm và mẫu |
|---|---|---|
| C60 | payment/repository/OrderPaymentSnapshotRepository.java | JpaRepository<OrderPaymentSnapshot,UUID>, mẫu Spring Data Shop App/Booking |
| C61 | payment/repository/PaymentAttemptRepository.java | JpaRepository<PaymentAttempt,UUID> |
| C62 | payment/repository/ChargeRepository.java | JpaRepository<Charge,UUID> |
| C63 | payment/repository/PaymentConfirmationRepository.java | JpaRepository<PaymentConfirmation,UUID> |
| C64 | refund/repository/RefundRepository.java | JpaRepository<Refund,UUID> |
| C65 | refund/repository/RefundAttemptRepository.java | JpaRepository<RefundAttempt,UUID> |
| C66 | refund/repository/EventRefundRunRepository.java | JpaRepository<EventRefundRun,UUID> |
| C67 | refund/repository/EventRefundItemRepository.java | JpaRepository<EventRefundItem,EventRefundItemId> |
| C68 | finance/repository/EventPayoutRepository.java | JpaRepository<EventPayout,UUID> |
| C69 | finance/repository/EventFinanceSnapshotRepository.java | JpaRepository<EventFinanceSnapshot,UUID> |
| C70 | saga/repository/PaymentSagaRepository.java | JpaRepository<PaymentSaga,UUID> |
| C71 | messaging/repository/OutboxMessageRepository.java | JpaRepository<OutboxMessage,UUID> |
| C72 | messaging/repository/InboxMessageRepository.java | JpaRepository<InboxMessage,InboxMessageId> |
| C73 | payment/service/PaymentService.java | Interface rỗng |
| C74 | payment/service/impl/PaymentServiceImpl.java | Implements C73; constructor sinh `(OrderPaymentSnapshotRepository orderPaymentSnapshotRepository, PaymentAttemptRepository paymentAttemptRepository, ChargeRepository chargeRepository, PaymentConfirmationRepository paymentConfirmationRepository)` |
| C75 | refund/service/RefundService.java | Interface rỗng |
| C76 | refund/service/impl/RefundServiceImpl.java | Implements C75; constructor sinh `(RefundRepository refundRepository, RefundAttemptRepository refundAttemptRepository, EventRefundRunRepository eventRefundRunRepository, EventRefundItemRepository eventRefundItemRepository)` |
| C77 | finance/service/FinanceService.java | Interface rỗng |
| C78 | finance/service/impl/FinanceServiceImpl.java | Implements C77; constructor sinh `(EventFinanceSnapshotRepository eventFinanceSnapshotRepository, EventPayoutRepository eventPayoutRepository)` |
| C79 | payment/controller/PaymentController.java | Mapping /api/v1/orders; constructor sinh `(PaymentService paymentService)`, mẫu InternalOrderController Booking |
| C80 | finance/controller/InternalFinanceController.java | Mapping /internal/v1/events; constructor sinh `(FinanceService financeService)` |
| C81 | config/PaymentJsonConfig.java | @Configuration(proxyBeanMethods=false), mẫu BookingJsonConfig |
| C82 | config/RabbitMqConfig.java | @Configuration(proxyBeanMethods=false), chỉ converter Booking |
| C83 | config/SecurityConfig.java | @Configuration(proxyBeanMethods=false) @EnableWebSecurity, MVC Booking |
| C84 | security/KeycloakRealmRoleConverter.java | final implements Converter<Jwt,Collection<GrantedAuthority>>, static final Set role allowlist |
| C85 | config/PaymentLogFields.java | final implements StructuredLoggingJsonMembersCustomizer<ILoggingEvent>, mẫu BookingLogFields |
| C86 | config/SafeStackTracePrinter.java | final implements StackTracePrinter, mẫu Booking |
| C87 | exception/ErrorCode.java | Enum; private final HttpStatus status, String message; constants §6 |
| C88 | exception/BusinessException.java | final RuntimeException; private final ErrorCode errorCode, mẫu Booking |
| C89 | exception/GlobalExceptionHandler.java | final @Slf4j @RestControllerAdvice; chỉ logger static sinh bởi Lombok |
| C90 | validation/ContractRules.java | final utility, private constructor; rule DTO thuần như Booking |
| C91 | mapper/WireValueConverter.java | @Component, không dependency/field; phần toMoney của Booking |
| C92 | mapper/PaymentMapper.java | @Mapper(componentModel=SPRING,unmappedTargetPolicy=ERROR,uses=WireValueConverter.class,injectionStrategy=CONSTRUCTOR) |

Mapper chỉ có PaymentAttempt+OrderPaymentSnapshot→PaymentStart và Refund→PaymentView.RefundView. attempt.id→attemptId, state→attemptState, expectedAmount→amount; expiresAt từ snapshot cục bộ. Chặn null/mismatch orderId trước mapping. Refund.id→refundId, tiền qua converter. Không mapper đọc DB/ghép latestAttempt/startRequest, không mapper tạo confirmation/Saga/refund hoặc generic outbox khi chưa flow.

## 8. Bảng duyệt constructor/method

Mọi đề xuất NEW/APPROVED; không throws trừ nơi ghi rõ. Các chữ ký dùng import type của Spring/Jakarta/Java tương ứng. Các khoảng ID không sử dụng không đại diện đề xuất mở.

### API sinh và constructor

| ID | Class | Chữ ký/nhóm API cụ thể | NEW/REUSE |
|---|---|---|---|
| M-G1 | C1–C13 | Mỗi class: public <Class>(); public <FieldType> get<Field>(); public void set<Field>(<FieldType> value), cho từng field §4.1 | NEW, Lombok |
| M-G2 | C14–C15 | Như M-G1 cho từng field khóa; public boolean equals(Object other); public int hashCode() | NEW, Lombok |
| M-G3 | C30–C44/C46–C47 và nested DTO | public <Class>(toàn bộ type/tên field đúng thứ tự §4.3); public getter từng field; không setter; C45 không constructor | NEW, creator explicit + getter Lombok |
| M-G4 | C48–C53 | public <Class>(toàn bộ type/tên field đúng thứ tự §4.3); public getter từng field | NEW, @AllArgsConstructor @Getter |
| M-G5 | C74/C76/C78–C80 | public <Class>(dependency đúng type/tên/thứ tự §7) | NEW, @RequiredArgsConstructor |
| M-G6 | C81–C86/C89/C91 | public <Class>() | NEW, constructor mặc định |
| M-G7 | C16–C29/C87 | public static <Enum>[] values(); public static <Enum> valueOf(String name) | NEW, Java enum |
| M-G8 | Generated PaymentMapperImpl | Spring component, constructor dependency WireValueConverter theo MapStruct CONSTRUCTOR; implements M-M2/M-M3 | NEW, API generated được duyệt cùng mapper |
| M-G9 | C60–C72 | Kế thừa JpaRepository<Entity,Id> theo type §7; không khai báo custom method | NEW |
| M-R1 | PaymentServiceApplication | public PaymentServiceApplication(); public static void main(String[] args) | REUSE, giữ annotation/body |
| M-R2 | PaymentServiceApplicationTests hiện có | Giữ context test hiện có, không mở rộng Java ngoài danh sách | REUSE |

### DTO/rule/mapping

M-D1–M-D11 đều public boolean, không tham số, @AssertTrue @JsonIgnore; null-safe. Kiểm field annotation riêng, không NPE trước Validator.

| ID | Class | Chữ ký đầy đủ | Cách viết/lý do |
|---|---|---|---|
| M-D1 | Envelope | public boolean isValidEnvelope() | Gọi ContractRules.validEnvelope |
| M-D2 | StartPaymentResult | public boolean isValidResult() | CREATED/REJECTED consistency |
| M-D3 | FinanceConfiguration | public boolean isValidConfiguration() | Fee/version/currency |
| M-D4 | EventCancellation | public boolean isValidCancellation() | Gọi validCancellation |
| M-D5 | PaymentDecision | public boolean isValidDecision() | Outcome/accepted charge |
| M-D6 | IssueTickets | public boolean isOrderConsistent() | Order khớp purchase |
| M-D7 | IssuanceResult | public boolean isValidResult() | State/count/code |
| M-D8 | BookingIssuanceResult | public boolean isValidFailure() | Chỉ FAILED/code |
| M-D9 | PurchaseSnapshot | public boolean isValidSnapshot() | Gọi validPurchaseSnapshot |
| M-D10 | SalesConfiguration | public boolean isValidSnapshot() | Gọi validSalesConfiguration |
| M-D11 | PromotionConfig | public boolean isValidWindow() | Time/maxUsesPerUser=1 |
| M-D12 | PromotionConfig.Discount | public String getKind() | Interface; Lombok getter C46/C47 thực hiện |
| M-V1 | ContractRules | private ContractRules() | Utility không khởi tạo |
| M-V2 | ContractRules | public static boolean validMoney(String value, boolean positive) | Regex/range, không rounding |
| M-V3 | ContractRules | public static boolean validUInt64(String value) | Version signed bigint |
| M-V4 | ContractRules | public static boolean validFeeRate(String value) | Decimal [0,1], <=8 số lẻ |
| M-V5 | ContractRules | public static boolean validPurchaseSnapshot(PurchaseSnapshot value) | Quy tắc §5, không DB/clock |
| M-V6 | ContractRules | public static boolean validSalesConfiguration(SalesConfiguration value) | Full config nested |
| M-V7 | ContractRules | public static boolean validCancellation(EventCancellation value) | Event/version/status |
| M-V8 | ContractRules | public static boolean validEnvelope(Envelope<?> value) | Metadata/aggregate/request identity |
| M-V9 | ContractRules | public static boolean isPayloadTypeValid(String messageType, Object payload) | Allowlist §5, nhánh trực tiếp |
| M-M1 | WireValueConverter | public String toMoney(BigDecimal value) | @Named("toMoney"), UNNECESSARY/range, lỗi nội bộ |
| M-M2 | PaymentMapper | public PaymentStart toStart(PaymentAttempt attempt, OrderPaymentSnapshot snapshot) | @Mapping theo §7, qualified money conversion |
| M-M3 | PaymentMapper | public PaymentView.RefundView toRefundView(Refund refund) | id→refundId, amount qualifiedByName=toMoney |
| M-M4 | PaymentMapper | public default void validateStartSources(PaymentAttempt attempt, OrderPaymentSnapshot snapshot) | @BeforeMapping; null/mismatch orderId→INTERNAL_ERROR |

### Config và exception

| ID | Class | Chữ ký đầy đủ | Annotation/cách viết |
|---|---|---|---|
| M-C1 | PaymentJsonConfig | package-private JsonMapperBuilderCustomizer paymentJsonMapperCustomizer() | @Bean, Boot mapper |
| M-C2 | RabbitMqConfig | package-private MessageConverter rabbitMessageConverter(JsonMapper jsonMapper) | @Bean, JacksonJsonMessageConverter |
| M-C3 | SecurityConfig | package-private SecurityFilterChain securityFilterChain(HttpSecurity http, JwtAuthenticationConverter keycloakJwtAuthenticationConverter, AuthenticationEntryPoint problemAuthenticationEntryPoint, AccessDeniedHandler problemAccessDeniedHandler) throws Exception | @Bean |
| M-C4 | SecurityConfig | package-private AuthenticationEntryPoint problemAuthenticationEntryPoint(@Qualifier("handlerExceptionResolver") HandlerExceptionResolver resolver) | @Bean, challenge/resolver |
| M-C5 | SecurityConfig | package-private AccessDeniedHandler problemAccessDeniedHandler(@Qualifier("handlerExceptionResolver") HandlerExceptionResolver resolver) | @Bean |
| M-C6 | SecurityConfig | package-private JwtAuthenticationConverter keycloakJwtAuthenticationConverter(KeycloakRealmRoleConverter keycloakRealmRoleConverter) | @Bean |
| M-C7 | SecurityConfig | package-private KeycloakRealmRoleConverter keycloakRealmRoleConverter() | @Bean |
| M-C8 | KeycloakRealmRoleConverter | public Collection<GrantedAuthority> convert(Jwt jwt) | @Override |
| M-C9 | PaymentLogFields | public void customize(JsonWriter.Members<ILoggingEvent> members) | @Override |
| M-C10 | SafeStackTracePrinter | public void printStackTrace(Throwable exception, Appendable output) throws IOException | @Override, giới hạn/cycle theo Booking |
| M-E1 | ErrorCode | private ErrorCode(HttpStatus status, String message) | Enum constructor |
| M-E2 | ErrorCode | public HttpStatus status(); public String message() | Giá trị cố định |
| M-E3 | ErrorCode | public static ErrorCode from(Throwable exception) | Business/native JPA/SQL/AMQP |
| M-E4 | ErrorCode | private static Throwable unwrap(Throwable exception) | Wrapper tối đa16 tầng |
| M-E5 | ErrorCode | public static ErrorCode fromStatus(HttpStatusCode status) | Framework HTTP |
| M-E6 | BusinessException | public BusinessException(ErrorCode errorCode); public BusinessException(ErrorCode errorCode, Throwable cause); public ErrorCode getErrorCode() | Explicit như Booking |
| M-E7 | GlobalExceptionHandler | public ResponseEntity<Problem> handleBusiness(BusinessException exception, HttpServletRequest request) | @ExceptionHandler(BusinessException.class) |
| M-E8 | GlobalExceptionHandler | public ResponseEntity<Problem> handleValidation(Exception exception, HttpServletRequest request) | @ExceptionHandler({BindException.class,HandlerMethodValidationException.class}) |
| M-E9 | GlobalExceptionHandler | public ResponseEntity<Problem> handleException(Exception exception, HttpServletRequest request, HttpServletResponse response) | @ExceptionHandler(Exception.class) |
| M-E10 | GlobalExceptionHandler | private static ResponseEntity<Problem> problem(HttpServletRequest request, HttpStatusCode status, ErrorCode code, Throwable exception, List<Problem.Violation> violations) | Correlation/log trực tiếp |
| M-E11 | GlobalExceptionHandler | private static List<Problem.Violation> violations(Exception exception) | Tối đa50 |
| M-E12 | GlobalExceptionHandler | private static String safeField(String path); private static String violationCode(String code) | Field/code an toàn |

### Test type/method

Test dưới `src/test/java/com/flashticket/payment_service/`, class package-private với constructor mặc định package-private không tham số. Các method dưới đều package-private @Test trả void; signature ghi rõ exception. Không tạo test helper/controller/support type ngoài bảng.

| ID class/method | File | Chữ ký method đầy đủ |
|---|---|---|
| T1/MT1 | dto/PaymentContractTests.java | void readsBookingWireFixturesAndPreservesNulls() throws Exception; void rejectsUnknownMissingAndCoercedFields() throws Exception; void validatesPurchaseAndCancellationConsistency() throws Exception; void validatesEnvelopeAndResultVariants() throws Exception; void preservesExactMoneyVersionAndHiddenProperty() throws Exception |
| T2/MT2 | mapper/PaymentMapperTests.java | void mapsAttemptAndSnapshotToPaymentStart(); void rejectsSourcesFromDifferentOrders(); void mapsRefundWithoutLeakingLedgerFields(); void rejectsFractionalAndOutOfRangeMoney() |
| T3/MT3 | security/PaymentSecurityTests.java | void rejectsMissingAndInvalidJwtWithProblem() throws Exception; void enforcesBuyerAdminAndInternalRoles() throws Exception; void permitsHealthAndBlocksOtherActuatorEndpoints() throws Exception; void ignoresUnknownAndMalformedRealmRoles() |
| T4/MT4 | exception/PaymentErrorLoggingTests.java | void keepsProblemHeaderAndLogCorrelationConsistent() throws Exception; void preservesFrameworkStatusAndHeaders() throws Exception; void distinguishesInputAndOutputValidationFailures() throws Exception; void redactsExceptionMessagesAndSensitiveLogFields() throws Exception |
| T5/MT5 | config/PaymentRuntimeConfigurationTests.java | void loadsPaymentRuntimeProperties() throws Exception; void usesBootJsonMapperForAmqpConverter() throws Exception; void hasNoBusinessListenerOrWorkerBeans() |
| T6/MT6 | persistence/PaymentPersistenceIT.java | void validatesAllMappingsAgainstPaymentSchema() throws Exception; void preservesTypedJsonAndCompositeKeys() throws Exception; void enforcesLedgerForeignKeysAndUnfinishedAttemptConstraint() throws Exception; void acceptsCancellationOnlyFinanceSnapshotAndNullRefundOutcome() throws Exception |

T1 dùng cùng fixture JSON hợp lệ Booking/B13, parse rồi Validator; T3 đi qua filter chain/JwtDecoder fixture kiểm issuer/expiry/chữ ký, không chỉ mock authenticated JWT. T4 có thể gọi advice với framework exception trực tiếp, không tạo endpoint giả. T6 dùng PostgreSQL cô lập đúng DDL/FK/JSONB/partial index; không dùng H2 thay thế hoặc ghi dữ liệu thử vào DB dùng chung. Profile Failsafe opt-in được làm rõ ở pha plan; nếu thiếu môi trường ghi NOT_RUN.

## 9. Ngoại lệ với mẫu Shop App cần duyệt

| ID | Đề xuất | Lý do |
|---|---|---|
| X1 | Package feature và ServiceImpl theo Booking; DTO Lombok immutable/creator, bỏ annotation trùng mẫu | Giữ repo và required/null wire contract |
| X2 | UUID/Instant/BigDecimal, JSONB typed, embedded ID/version đúng DDL | Contract/schema Payment |
| X3 | MapStruct/qualified converter/@BeforeMapping, không ModelMapper | Toolchain Booking, tránh mapping thiếu/trộn order |
| X4 | JWT resource server/role converter/resolver, chỉ issuer discovery | Keycloak và convention hiện hành |
| X5 | Jackson3 strict creator/coercion, hai Discount variant, AMQP converter | Contract liên service thực tế |
| X6 | ErrorCode/BusinessException/Problem/advice, structured log/safe stack | Chuẩn lỗi hiện hành, không raw message/framework RCA |
| X7 | ddl-auto=none, MANUAL ACK chỉ default scaffold chưa listener | Handoff Payment; không mở rộng quyết định riêng Booking |

## 10. Điều kiện nghiệm thu và giới hạn

| Nhóm | Điều kiện/bằng chứng |
|---|---|
| Scope | Chỉ Payment + YAML riêng/env example/tài liệu liên quan; không business endpoint/listener/worker hoặc thay DB/topology |
| Persistence | Đủ13 bảng/repository, hai khóa ghép; đối chiếu mọi cột/nullability/version/JSONB; PostgreSQL IT kiểm FK/unique/partial index, không chỉ Hibernate validate |
| JSON | Parse→validate fixture hợp lệ/sai, required-nullable, enum/schema, Money/version chính xác, isHidden đúng key; full cancellation |
| Mapper | Generated source đúng mapping, ERROR policy và constructor dependency; chặn cross-order, không rounding |
| Security/error | JWT/role/internal/actuator và401/403, status/header/Problem/correlation/log đúng; ghi rõ chưa ownership handler và không audience runtime check |
| Runtime | Config Server/Eureka, schema/role/UTC/JSONB, Rabbit converter/confirms/returns/mandatory/dynamic đúng; không consume message ngầm |
| Quality | Từ module với JDK21: `rtk proxy .\mvnw.cmd -B -ntp verify`; không skip Enforcer/Spotless/SpotBugs |
| IT | Sau khi profile được triển khai: `rtk proxy .\mvnw.cmd -B -ntp -Ppayment-persistence-it verify`; DB cô lập; ghi riêng PASSED/FAILED/NOT_RUN |
| Smoke | Khi hạ tầng sẵn: xác minh Config Server load, Eureka đăng ký, DB query đọc riêng và broker connection riêng; health UP không chứng minh schema/use case |
| Bàn giao | File/commands/result, điều kiện môi trường và phần chưa làm; không tự commit/push/merge |

Luồng sau scaffold: Boot nhận config → dựng bean/repository/mapper → security chặn request sai → advice trả Problem. Controller chưa có handler không thực hiện nghiệp vụ. JSON/mapper được kiểm bằng test nền; chưa có xử lý giao dịch thanh toán.

Config bắt buộc không truy cập được thì startup thất bại; không đổi optional để chữa smoke. DB/broker sai cấu hình phải báo đúng kiểm tra, không thay hạ tầng. Input JSON/validation sai bị từ chối; lỗi mapping dữ liệu nội bộ không tự biến400. Provider timeout không mặc nhiên FAILED; Ticket404/PENDING/COMPLETED trực tiếp không cho quyền refund/đóng Saga. Những nhánh này chỉ là ràng buộc cho task flow sau, chưa được hiện thực ở scaffold.

**Bằng chứng lượt viết spec:** đã đọc source/config/contract; chưa compile/test/smoke Payment. Tiêu chí trên là yêu cầu triển khai tiếp, không phải kết quả đã chạy.

## 11. Gate chốt thiết kế

- Danh sách v1: C1–C53 gồm các nested item có hậu tố, C60–C92; M-G/M-R/M-D/M-V/M-M/M-C/M-E; T1–T6/MT1–MT6; X1–X7.
- Mọi đề xuất mới **APPROVED**; M-R là REUSE. Nguồn: user trả lời “xác nhận” ngày 28/10/2026 sau bảng hỏi duyệt spec/plan v1 và danh sách pha EXECUTING-PLANS.
- Spec/plan v1 và danh sách thực thi đã được xác nhận; không mở quyền thêm khai báo ngoài bảng.
- Nếu phát sinh type/helper/test method hoặc thay annotation/phong cách ngoài danh sách, trình delta để duyệt lại. Không dùng danh sách này như quyền mở tạo helper.
- Sau xác nhận chỉ cập nhật spec theo phạm vi duyệt. Chỉ chuyển sang shopapp-java-writing-plans khi user yêu cầu lập plan; pha đó có gate riêng.
