# Ticket Service Scaffold Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Setup scaffold Ticket Service theo Booking hiện tại, để các task nghiệp vụ sau có model, contract, persistence và các boundary kỹ thuật dùng được.

**Architecture:** Một module Spring MVC, chia theo feature và dùng JPA/PostgreSQL. DTO/message là bản owner-local tương thích B13; snapshot thay cho truy cập DB owner khác. Controller/service rỗng, cấu hình và error handling theo Booking; chưa có handler, listener, worker hoặc flow phát vé.

**Tech Stack:** Java 21; Spring Boot 4.1.1; Spring Cloud 2025.1.3; PostgreSQL/JPA; Jackson 3; MapStruct 1.6.3; lombok-mapstruct-binding 0.2.0; Keycloak JWT; RabbitMQ; mail starter hiện có; JUnit/AssertJ/Spring Test hiện có.

**Spec:** `docs/superpowers/specs/2026-10-26-ticket-service-scaffold-design.md` — APPROVED 2026-10-26, đã áp ponytail-review.

## Global Constraints

- Package gốc `com.flashticket.ticket_service`; application name `ticket-service`; port `${TICKET_SERVICE_PORT:8084}`.
- Database `ticket_db`, schema `ticket_schema`, bảng prefix `tbl_`; giữ mọi cột theo `docs/architecture/data/postgres/40-ticket.sql` và prefix theo `services/99-table-prefix.sql`.
- Chỉ Issuance và Ticket có `@Version`; không nhầm sourceConfigVersion/aggregateVersion với rowVersion.
- TicketDisplaySnapshot chỉ có `schemaVersion=1`, `ticketTypeName`, nullable `seatLabel`; thông tin event lấy PurchaseSnapshot của issuance.
- Controller/service rỗng như Booking; sáu JpaRepository cơ bản, chưa có TicketRepository, query nghiệp vụ hoặc lock.
- MapStruct Spring component, `unmappedTargetPolicy=ERROR`; Jackson 3 `tools.jackson.*`, annotations vẫn `com.fasterxml.jackson.annotation.*`.
- Giữ Java 21 Enforcer, Spotless, JaCoCo, SpotBugs; không thay phiên bản stack hoặc skip Enforcer để tuyên bố gate đạt.
- Chỉ sửa Ticket, YAML riêng Ticket, HANDOFF/TESTING; `.env.example` chỉ thêm tên biến thật sự thiếu. Không sửa Booking, User, Gateway, B12–B16, DB/realm/broker đang chạy.
- Không Redis/Feign/OAuth2 client/Keycloak Admin/QR dependency/shared framework; giữ mail starter, chưa gửi SMTP.
- Rabbit `dynamic=false`; không declare Queue/Exchange/Binding/RabbitAdmin, không có listener/publisher/relay/job.
- Giữ nguyên OPEN-TKT-01/02/03 trong spec/HANDOFF; không tự đổi paymentConfirmationId thành chargeId hoặc thêm routing alias.
- Tại lúc thực thi kiểm branch/worktree/status và JDK bằng lệnh đọc; giữ thay đổi có sẵn. Chỉ tạo worktree nếu cần, theo using-git-worktrees tại lúc thực thi.
- Commit commands cuối task là checkpoint **chỉ chạy nếu người dùng đã cho phép commit ở lần thực thi**; không stage toàn repo hoặc push tự động. Plan/spec local đang bị ignore: không force-add/sửa ignore.

## Review Focus

1. Nullable/optional JSON hợp lệ không bị strict creator config từ chối; tiền/version dạng number, quantity dạng chuỗi/phần lẻ và field lạ phải bị reject — Task 1, `TicketJsonTests`.
2. EventCancellation CANCELLED tới trước cấu hình, có sales rỗng, phải deserialize/validate; mismatch event/version hoặc thiếu sales phải bị reject — Task 1, `TicketContractTests`.
3. Outbox khôi phục sau retry phải giữ nguyên messageId/time/type/correlation/causation/traceparent; sai payload class hoặc schema không được phục hồi thành công — Task 3, `MapperTests`.
4. Machine token, role claim sai kiểu, token hết hạn/sai issuer/audience/signature và sai HTTP method phải bị chặn đúng 401/403 — Task 4, `SecurityConfigTests`/`KeycloakRealmRoleConverterTests`.
5. Exception có secret, correlation header không phải UUID và Rabbit message lỗi có body nhạy cảm không được lộ ra Problem/JSON log — Task 4/5, `HttpErrorHandlingTests`, `TicketLoggingTests`, `TicketRabbitErrorHandlerTests`.

---

## File structure và cách đọc đường dẫn

Mọi đường dẫn tương đối tính từ repo `D:/DATN/flash-ticket-platform`.
Để tránh lặp, trong bảng/task dùng ba tiền tố cố định sau; nối tiền tố với hậu tố là đường dẫn file chính xác, không phải glob:

- `M/` = `services/ticket-service/src/main/java/com/flashticket/ticket_service/`.
- `T/` = `services/ticket-service/src/test/java/com/flashticket/ticket_service/`.
- `C` = `infrastructure/config-server/src/main/resources/config/ticket-service.yaml`.

Các tên trong `{a,b}.java` là danh sách đóng hai file `a.java`, `b.java`; không phải yêu cầu tạo mọi lớp có thể có.

| Task | Create / Modify | Trách nhiệm |
|---|---|---|
| 1 | M/enums/{Currency,SalesMode,EventState,IssuanceState,TicketState}.java | Enum đúng nguồn; enum result/check-in/probe đặt lồng DTO tương ứng |
| 1 | M/dto/snapshot/{PurchaseSnapshot,SeatSelectionSnapshot,AccessConfiguration,SalesConfiguration,PromotionConfig,TicketDisplaySnapshot}.java | Typed snapshot; subtype nhỏ đặt nested record |
| 1 | M/dto/message/{Envelope,IssueTickets,IssuanceResult,EventCancellation}.java | Wire payload/envelope |
| 1 | M/dto/request/CheckInRequest.java; M/dto/response/{TicketView,OrderTicketsView,TicketDeliveryAccepted,QrTokenView,CheckInResult,IssuanceProbeView,PendingIssuanceView,Problem}.java | REST contract; hai shape probe |
| 1 | M/config/TicketJsonConfig.java; M/validation/ContractRules.java | JSON coercion/field policy và rule pure Java |
| 1 | T/support/TicketFixtures.java; T/dto/{TicketJsonTests,TicketContractTests}.java | Fixture có kiểu, JSON/validation tests |
| 2 | M/entity/{EventAccessSnapshot,Issuance,Ticket,TicketDelivery,CheckinAttempt,OutboxMessage,InboxMessage,InboxMessageId}.java | Đầy đủ cột và association trong owner |
| 2 | M/access/repository/EventAccessSnapshotRepository.java; M/issuance/repository/IssuanceRepository.java | Snapshot và aggregate root |
| 2 | M/delivery/repository/TicketDeliveryRepository.java; M/checkin/repository/CheckinAttemptRepository.java; M/messaging/repository/{InboxMessageRepository,OutboxMessageRepository}.java | Job/audit/inbox/outbox persistence entry points |
| 2 | Modify C; T/entity/EntityBehaviorTests.java | JPA/Hikari config và helper aggregate |
| 3 | M/mapper/{TicketMapper,IssuanceMapper,OutboxMessageMapper,MappingValueConverter}.java; T/mapper/MapperTests.java | Mapping view và storage boundary |
| 3 | Modify services/ticket-service/pom.xml; T/support/TicketFixtures.java | MapStruct/processor và entity fixture |
| 4 | M/exception/{ErrorCode,BusinessException,GlobalExceptionHandler}.java | Problem/status/safe error logging |
| 4 | M/security/KeycloakRealmRoleConverter.java; M/config/{SecurityConfig,TicketLogFields,SafeStackTracePrinter}.java | JWT/policy và native JSON log helpers |
| 4 | T/security/{SecurityConfigTests,KeycloakRealmRoleConverterTests}.java; T/exception/HttpErrorHandlingTests.java; T/config/TicketLoggingTests.java; T/support/LogCapture.java | JWT, errors, actual formatter, capture test-only |
| 4 | Modify C; services/ticket-service/pom.xml; services/ticket-service/src/test/resources/application.yaml | JWT/log config, test JWT isolation; annotations provided nếu cần |
| 5 | M/config/RabbitMqConfig.java; M/messaging/TicketRabbitErrorHandler.java | Shared JsonMapper conversion, safe native reject behavior |
| 5 | Feature controller/service/impl theo danh sách Task 5 | Các bean rỗng đã được yêu cầu |
| 5 | T/config/RuntimeConfigurationTests.java; T/messaging/TicketRabbitErrorHandlerTests.java | Config binding, converter/error disposition |
| 5 | Modify C; services/ticket-service/src/test/resources/application.yaml; T/TicketServiceApplicationTests.java | Runtime config hoàn chỉnh, bean graph test doubles |
| 5 | Modify services/ticket-service/HANDOFF.md; Create services/ticket-service/TESTING.md | Env, lệnh chạy, giới hạn và OPEN-TKT |

Không sửa bootstrap production `services/ticket-service/src/main/resources/application.yaml`: Config Server import bắt buộc đã đúng. Không tạo test integration profile mới trong plan này; ghi rõ chưa có PostgreSQL round-trip proof.

## Quy ước kiểm tra và commit

Mọi Maven command dưới đây chạy từ `services/ticket-service` với Java 21. FAIL đầu tiên có thể là test-compile vì class/interface chưa tồn tại; sau khi code có, lỗi test phải thuộc assertion được mô tả. Không chạy build/test trong lượt **viết plan**.

Test snippet là assertion cốt lõi của method được đặt tên; dùng JUnit 5/AssertJ và fixture được định nghĩa trong Interfaces. `validator` là Validator từ LocalValidatorFactoryBean hoặc ValidatorFactory được đóng sau suite; `json` là JsonMapper từ TicketFixtures.jsonMapper(). Không mock JsonMapper/Validator ở các test contract.

Commit checkpoint luôn gồm: từ repo root, `rtk git diff --check` → `rtk git add -- <từng file thuộc task>` → `rtk git diff --cached --stat` → commit message được cho. Danh sách file là cột Files của task, không dùng `git add .`; nếu có staged changes khác thì chỉ stage/commit patch thuộc task hoặc giữ checkpoint chưa commit và báo lý do.

### Task 1: Wire contracts, snapshot, strict JSON và validation

**Files:** Tạo toàn bộ file Task 1 trong bảng File structure. Reference read-only: B13 §2–7; Booking dto/snapshot, dto/message, BookingJsonConfig, ContractRules và BookingJsonTests.

**Interfaces:**

- Produces: record constructors/field names theo đúng B13 §3–7 và spec §7; dùng UUID, Instant, String cho Money/version, int cho quantity/ordinal, immutable List cho nested arrays. Không đưa entity vào DTO.
- `TicketDisplaySnapshot(int schemaVersion, String ticketTypeName, String seatLabel)`; `TicketView(UUID ticketId, UUID eventId, UUID orderItemId, int ordinal, String ticketTypeName, String seatLabel, TicketState state)`.
- `OrderTicketsView(IssuanceState issuanceState, List<TicketView> tickets)`; `CheckInRequest(String qrToken)`; `TicketDeliveryAccepted(UUID deliveryId, String state)` với state=PENDING; `QrTokenView(String qrToken)`.
- `CheckInResult(UUID attemptId, UUID eventId, UUID ticketId, Result result, boolean succeeded, Instant processedAt)`; Result đúng 9 giá trị B13, không REPLAYED.
- `IssuanceProbeView` là interface chỉ khai báo `UUID orderId()`/`UUID issuanceId()`. `PendingIssuanceView(String state, UUID orderId, UUID issuanceId)` validate state=PENDING; `IssuanceResult(UUID orderId, UUID chargeId, UUID issuanceId, State state, int ticketCount, String failureCode)` implements cùng interface, State chỉ COMPLETED/FAILED. Jackson state discriminator dùng existing property với allowlist PENDING/COMPLETED/FAILED và visible=true, giữ đúng hai shape, không thêm wrapper/type field. Không dùng sealed xuyên package response/message trong unnamed Java module.
- `IssueTickets(UUID orderId, UUID chargeId, PurchaseSnapshot purchase)`; `EventCancellation(UUID eventId, UUID cancellationId, String sourceConfigVersion, Instant cancelledAt, String actorSubject, SalesConfiguration sales, AccessConfiguration access)`; `Envelope<T>(UUID messageId, String messageType, int schemaVersion, UUID aggregateId, String aggregateVersion, Instant occurredAt, UUID correlationId, UUID causationId, String traceparent, T data)`.
- `Problem(String code, String message, UUID correlationId, List<Violation> violations)` và nested `Violation(String field, String code)`.
- `ContractRules`: `boolean validUInt64(String)`, `boolean validMoney(String)`, `boolean validPurchaseSnapshot(PurchaseSnapshot)`, `boolean validSalesConfiguration(SalesConfiguration)`, `boolean validAccessConfiguration(AccessConfiguration)`, `boolean validCancellation(EventCancellation)`, `boolean validIssueTickets(IssueTickets)`, `boolean validIssuanceResult(IssuanceResult)`, `boolean isPayloadTypeValid(String,Object)`, `boolean matchesPayload(String,Class<?>)`, `boolean validEnvelope(Envelope<?>)`. Nối từ @AssertTrue/@JsonIgnore, không có DB/clock.
- `TicketJsonConfig.ticketJsonMapperCustomizer(): JsonMapperBuilderCustomizer`.
- Test fixture methods: `JsonMapper jsonMapper()`, `PurchaseSnapshot purchase()`, `SalesConfiguration sales()`, `AccessConfiguration access()`, `EventCancellation cancellation()`, `IssueTickets issue()`, `IssuanceResult completed()`, `IssuanceResult failed()`, `Envelope<IssuanceResult> resultEnvelope()`.
- Fixture constants: UUID EVENT/ORDER/ITEM/TYPE/SEAT/SECTOR/CHARGE/ISSUANCE/MESSAGE/CAUSE/CORRELATION dùng lần lượt UUID `00000000-0000-0000-0000-000000000001` đến `...000011`; NOW=`2026-10-26T12:00:00Z`. Purchase QUANTITY: 1 item quantity=2, unitPrice="100", subtotal/lineSubtotal/total="200", discount="0", promotion/sector/venue=null, seats=[], versions="1", frozen=NOW, expires=NOW+600s, eventStart=NOW+7200s, eventEnd=NOW+10800s. Buyer subject `buyer-subject`, email `buyer@example.test`; event title Concert, ticketTypeName Standard.
- sales()/access(): PUBLISHED cùng EVENT/version="1"; cancellation(): cả sales/access CANCELLED cùng EVENT/version="2", sales ticketTypes/sectors/seats/promotions rỗng. completed(): count=2, failure=null; failed(): count=0, failure="ISSUANCE_REJECTED". resultEnvelope(): type=ticket.issuance-result.v1, aggregateId=ORDER, version="1", occurredAt=NOW, causationId=CAUSE, traceparent=`00-4bf92f3577b34da6a3ce929d0e0e4736-00f067aa0ba902b7-01`.

- [ ] **Step 1: Viết tests JSON/validation với assertion dưới đây.**

```java
// TicketJsonTests.preservesWireTypesAndRequiredNullableFields
var purchase = TicketFixtures.purchase();
var tree = json.valueToTree(purchase);
assertThat(tree.get("subtotal").isString()).isTrue();
assertThat(tree.get("subtotal").asString()).isEqualTo("200");
assertThat(tree.get("venueName").isNull()).isTrue();
assertThat(tree.has("validSnapshot")).isFalse();
assertThat(json.treeToValue(tree, PurchaseSnapshot.class)).isEqualTo(purchase);
// TicketContractTests.acceptsEarlyCancellationAndRejectsMissingPayload
assertThat(validator.validate(TicketFixtures.cancellation())).isEmpty();
assertThat(validator.validate(new IssueTickets(TicketFixtures.ORDER,
    TicketFixtures.CHARGE, null))).isNotEmpty();
```

Thêm parameterized methods trong hai test file: `rejectsUnknownFieldsAndPrimitiveCoercion` với CheckInRequest `{"qrToken":123}`/`{"qrToken":"x","extra":true}` → JacksonException; `rejectsFractionalOrTextQuantity` với quantity=1.5/"2" → JacksonException; `versionsKeepBigintPrecision` với version="9223372036854775807" round-trip, "0"/"9223372036854775808" validation fail. Mutation fixture bằng JsonNode (không tạo reflection with-helper): `rejectsInvalidTotalsAndSelections` kiểm total="201", tiền "1.01"/"1e3", duplicate item/seat, SEATED số seat lệch quantity và STANDING có seats → violations. `cancellationIdsVersionsAndStatusMustAgree` đổi EVENT/version/status của access hoặc bỏ sales → fail; CANCELLED rỗng vẫn pass. `probeHasOnlyItsContractFields` kiểm PENDING đúng 3 fields, terminal đúng 6 fields, không thêm discriminator phụ. `optionalFieldCanBeAbsent` bỏ cause/traceparent hợp lệ của Envelope và kiểm null; required-nullable vẫn phải có theo shape. `immutableListsKeepCallerChangesOut` mutate list nguồn không đổi DTO; validate phần tử null không phát sinh NullPointerException trước boundary.

- [ ] **Step 2: Chạy `rtk proxy .\mvnw.cmd -B -ntp '-Dtest=TicketJsonTests,TicketContractTests' test`.** Expected FAIL do DTO/config chưa có.
- [ ] **Step 3: Tạo DTO/enums/snapshot và ContractRules đúng Interfaces/spec.** Giữ full SalesConfiguration trong cancellation; nested promotion discriminator FIXED/PERCENT theo Booking; PUBLISHED sales đầy đủ và CANCELLED rỗng có rule riêng. Envelope allowlist đúng 4 type Ticket; aggregateId nhất quán theo message, không ép Event correlationId=eventId. Không tự dựng field/alias ngoài contract.
- [ ] **Step 4: Tạo TicketJsonConfig và TicketFixtures.** Dùng Boot JacksonAutoConfiguration cùng customizer để lấy đúng mapper như BookingFixtures; reject unknown fields/text/integer/enum coercion, đặt required/nullable theo DTO thay vì bật blanket rule làm optional không đọc được. Không đăng ký deserializer OrderSelection.
- [ ] **Step 5: Chạy lại command Step 2.** Expected BUILD SUCCESS; cả JSON và Bean Validation assertions PASS.
- [ ] **Step 6: Commit checkpoint:** `feat(ticket): thêm DTO và validation nền`.

### Task 2: Entity, aggregate helper, repository và JPA config

**Files:** Tạo file Task 2 trong File structure; modify C phần datasource/JPA; thêm entity fixture vào T/support/TicketFixtures.java. Read-only DDL/prefix, Booking Order/OrderItem và InboxMessageId.

**Interfaces:**

- Consumes: Task 1 PurchaseSnapshot/TicketDisplaySnapshot/enums; class entity có no-arg constructor + getters/setters theo Booking. Không có constructor sinh dữ kiện nghiệp vụ.
- Produces: 7 entity đúng mọi cột DDL; `InboxMessageId(String consumerName, UUID messageId)` là @Embeddable, Serializable, equals/hashCode theo cả hai field.
- `Issuance.addTicket(Ticket ticket): void`; `Issuance.getTickets(): List<Ticket>`; `Ticket.getIssuance(): Issuance`. Dùng owning @ManyToOne LAZY trên issuance_id, eventId scalar giống cách Booking map child/order. Helper addTicket nối hai phía và đồng bộ eventId của root; composite FK (issuance_id,event_id) vẫn do schema PostgreSQL enforce, không tạo/thay constraint bằng Hibernate.
- Cascade PERSIST như Booking; không REMOVE/orphanRemoval, không thêm repository cho child. UUID root/ticket dùng @GeneratedValue(UUID) như Booking; helper không gọi randomUUID/clock. eventId của root phải được gán trước addTicket; thời điểm cấp ID persistence không phải thời điểm publish.
- `EventAccessSnapshotRepository extends JpaRepository<EventAccessSnapshot,UUID>`; Issuance/TicketDelivery/CheckinAttempt/OutboxMessage tương tự UUID; `InboxMessageRepository extends JpaRepository<InboxMessage,InboxMessageId>`. Chưa thêm method ngoài inherited CRUD.
- Fixture additions: `Ticket ticket()` có id=SEAT, event=EVENT, item=ITEM, ordinal=1, type=TYPE, holder=buyer-subject, VALID, display=(1,Standard,null), ciphertext byte[]{1,2,3}, keyVersion=TEST_ONLY, hash=TEST_HASH, rowVersion=0; `Issuance completedIssuance()` có id=ISSUANCE/order=ORDER/event=EVENT, purchase(), expectedCount=2, COMPLETED, timestamps NOW, paymentConfirmationId=TEST_CONFIRMATION, hai child ordinal 1/2 với ID SEAT/SECTOR; key/hash khác nhau, không phải QR thật.

- [ ] **Step 1: Viết EntityBehaviorTests trước helper/entity.**

```java
// addTicketLinksBothSidesWithoutAssigningBusinessValues
var issuance = new Issuance();
issuance.setEventId(TicketFixtures.EVENT);
var ticket = new Ticket();
issuance.addTicket(ticket);
assertThat(issuance.getTickets()).containsExactly(ticket);
assertThat(ticket.getIssuance()).isSameAs(issuance);
assertThat(ticket.getEventId()).isEqualTo(TicketFixtures.EVENT);
assertThat(ticket.getState()).isNull();
assertThat(ticket.getQrTokenCiphertext()).isNull();
// inboxKeyIncludesConsumerName
var key = new InboxMessageId("ticket-issue", TicketFixtures.MESSAGE);
assertThat(key).isEqualTo(new InboxMessageId("ticket-issue", TicketFixtures.MESSAGE));
assertThat(key).isNotEqualTo(new InboxMessageId("ticket-cancel", TicketFixtures.MESSAGE));
```

- [ ] **Step 2: Chạy `rtk proxy .\mvnw.cmd -B -ntp '-Dtest=EntityBehaviorTests' test`.** Expected FAIL do entity/helper chưa có.
- [ ] **Step 3: Map đầy đủ DDL trong 7 entity và khóa ghép; thêm helper/repository theo Interfaces.** @Table có schema/prefix, enum STRING, Instant, bytea không @Lob OID, JSONB @JdbcTypeCode; paymentConfirmationId và audit correlationId là String; source/aggregate version là Long, rowVersion chỉ 2 entity. Scalar FK của delivery/audit/snapshot không cần association không được dùng. Fixture dùng trạng thái/timestamp hợp lệ của DDL; không coi fixture SQL tối giản là wire payload.
- [ ] **Step 4: Cập nhật C phần persistence.** Env TICKET_DB_URL/USERNAME/PASSWORD, username default ticket_admin; SET ROLE ticket_owner; pool max=5/min=1; open-in-view=false, ddl-auto=none, default_schema=ticket_schema, jdbc.time_zone=UTC, type.json_format_mapper=jackson3. Giữ nguyên phần SMTP/Rabbit hiện có cho task sau.
- [ ] **Step 5: Chạy lại Step 2, đối chiếu mọi cột với DDL/prefix.** Expected PASS/BUILD SUCCESS; ghi nhận manual parity không chứng minh PostgreSQL load/JSONB/constraint. Không thêm annotation-count test để thay proof persistence.
- [ ] **Step 6: Commit checkpoint:** `feat(ticket): thêm entity và repository nền`.

### Task 3: View mapper và outbox round-trip

**Files:** Tạo M/mapper/{TicketMapper,IssuanceMapper,OutboxMessageMapper,MappingValueConverter}.java, T/mapper/MapperTests.java; modify POM annotation processing.

**Interfaces:**

- Consumes: Task 1 DTO/Validator/JsonMapper và Task 2 entity/fixture.
- `TicketMapper.toView(Ticket ticket): TicketView` lấy display.ticketTypeName/seatLabel; không lấy thông tin từ DB hoặc xuất QR/holder/audit.
- `IssuanceMapper.toView(Issuance issuance): OrderTicketsView`: PENDING/FAILED trả tickets rỗng; COMPLETED map tập child đã tải, số lượng phải khớp expectedTicketCount, thiếu/null dữ liệu bắt buộc báo IllegalArgumentException. Không thay state hoặc gọi repository.
- `MappingValueConverter(JsonMapper jsonMapper)` với `String toUInt64(Long)`, `Long parseUInt64(String)`, `JsonNode toTree(Object)`, `<T> T fromTree(JsonNode,Class<T>)`; null/schema/range sai bị reject, không fallback.
- `OutboxMessageMapper.toEntity(Envelope<?> envelope): OutboxMessage`; `<T> Envelope<T> toEnvelope(OutboxMessage row, Class<T> payloadType)`; Validator kiểm payload/envelope hai chiều. Field retry khi tạo row: publishedAt=null, attempts=0, nextAttemptAt=occurredAt, lastErrorCode=null. Chiều phục hồi giữ metadata gốc, không reset row hoặc sinh ID/time mới.

- [ ] **Step 1: Viết MapperTests bằng Spring context nhỏ có generated mappers, converter, LocalValidatorFactoryBean và TicketFixtures.jsonMapper().**

```java
// restoresOutboxEnvelopeWithoutChangingIdentity
var original = TicketFixtures.resultEnvelope();
var row = outbox.toEntity(original);
assertThat(outbox.toEnvelope(row, IssuanceResult.class)).isEqualTo(original);
assertThat(row.getAttempts()).isZero();
assertThat(row.getNextAttemptAt()).isEqualTo(original.occurredAt());
// exposesOnlyTicketView
var view = tickets.toView(TicketFixtures.ticket());
assertThat(view.ticketTypeName()).isEqualTo("Standard");
assertThat(view.seatLabel()).isNull();
assertThat(json.writeValueAsString(view))
    .doesNotContain("TEST_HASH", "TEST_ONLY", "holderSubject", "qrTokenCiphertext");
```

`nonCompletedIssuanceExposesNoTickets`: fixture COMPLETED đổi state=PENDING/FAILED → tickets rỗng. `completedIssuanceRequiresExpectedCount`: bỏ một child → IllegalArgumentException. `rejectsMismatchedOutboxTypeAndSchema`: row schema=2, payloadType=IssueTickets cho result, version=0, missing correlation → reject. `versionConversionKeepsSignedBigintBoundary`: Long.MAX_VALUE → "9223372036854775807"; null/0/-1 và overflow string → reject. Row retry metadata thay đổi không làm envelope thay đổi.

- [ ] **Step 2: Chạy `rtk proxy .\mvnw.cmd -B -ntp '-Dtest=MapperTests' test`.** Expected FAIL do mapper chưa có/generated implementation thiếu.
- [ ] **Step 3: Thêm MapStruct dependency/processor 1.6.3 và Lombok binding 0.2.0 trong POM.** Giữ Lombok processor hiện tại; testCompile chỉ cần binding/MapStruct nếu có test mapper thật. Tạo mappers theo Interfaces, Spring model và unmapped-target ERROR; không tạo toIssuanceResult từ entity vì OPEN-TKT-01.
- [ ] **Step 4: Chạy `rtk proxy .\mvnw.cmd -B -ntp clean '-Dtest=TicketJsonTests,TicketContractTests,EntityBehaviorTests,MapperTests' test`.** Expected BUILD SUCCESS, MapStruct sinh implementation; chạy clean ở đây để tránh pass do generated source cũ.
- [ ] **Step 5: Commit checkpoint:** `feat(ticket): thêm mapper view và outbox`.

### Task 4: Security, Problem và structured logging

**Files:** Các file Task 4 trong File structure. Reference read-only: Booking SecurityConfig, KeycloakRealmRoleConverter, GlobalExceptionHandler, ErrorCode, Ticket-independent log helper patterns và các tests tương ứng; không sao Feign/OAuth2 client imports.

**Interfaces:**

- Consumes: Problem/Violation từ Task 1, TicketJsonConfig và JPA dependencies hiện có.
- `KeycloakRealmRoleConverter implements Converter<Jwt,Collection<GrantedAuthority>>`, method `convert(Jwt): Collection<GrantedAuthority>`; whitelist BUYER/ORGANIZER/ADMIN/INTERNAL_SERVICE, distinct, sai kiểu không cấp quyền.
- `SecurityConfig.securityFilterChain(HttpSecurity,JwtAuthenticationConverter,AuthenticationEntryPoint,AccessDeniedHandler): SecurityFilterChain`; bean converter và handler nối HandlerExceptionResolver như Booking.
- `ErrorCode.status(): HttpStatus`, `message(): String`, `static from(Throwable): ErrorCode`, `static fromStatus(HttpStatusCode): ErrorCode`; `BusinessException(ErrorCode)`/`getErrorCode()`; GlobalExceptionHandler giữ tên/signature handleBusiness, handleValidation, handleException của Booking với Problem của Ticket.
- ErrorCode tối thiểu theo boundary: VALIDATION_FAILED/INVALID_REQUEST 400; UNAUTHENTICATED 401; ACCESS_DENIED 403; ROUTE_NOT_FOUND 404; METHOD_NOT_ALLOWED 405; NOT_ACCEPTABLE 406; UNSUPPORTED_MEDIA_TYPE 415; CONCURRENT_MODIFICATION/DATA_CONFLICT 409; RESOURCE_BUSY/DATASTORE_TIMEOUT/DATASTORE_UNAVAILABLE/MESSAGE_BROKER_UNAVAILABLE 503; INVALID_MESSAGE/INTERNAL_ERROR 500. Không sao ORDER/SEAT/PROMOTION/Feign/OAuth2 outbound codes.
- `TicketLogFields implements StructuredLoggingJsonMembersCustomizer<ILoggingEvent>` với `customize(JsonWriter.Members<ILoggingEvent>): void`; `SafeStackTracePrinter implements StackTracePrinter` với `printStackTrace(Throwable,Appendable): void`. Follow giới hạn safe printer Booking hiện tại, không tạo observability framework.
- `T/support/LogCapture implements AutoCloseable`: constructors `LogCapture()`/`LogCapture(Consumer<ILoggingEvent>)`, `List<ILoggingEvent> events()`, `static ConfigurableApplicationContext bootJsonLogging() throws IOException`, `void close()`. Adapt Booking helper nhưng bootJsonLogging phải đọc logging properties từ C qua đường dẫn `../../infrastructure/config-server/src/main/resources/config/ticket-service.yaml` tính từ module, không đọc bootstrap YAML không có log config. Không đưa helper capture vào production.

- [ ] **Step 1: Viết các tests Task 4 bằng test fixture controllers, không tạo handler production.** SecurityConfigTests có signed RSA JWT/local JWKS fixture private trong class như Booking; dùng resource server decoder thật, không chỉ mockJwt để tuyên bố đã kiểm chữ ký.

```java
// KeycloakRealmRoleConverterTests.rejectsMalformedRoleClaim
var jwt = Jwt.withTokenValue("fixture").header("alg", "RS256").subject("caller")
    .claim("realm_access", Map.of("roles", "BUYER")).build();
assertThat(new KeycloakRealmRoleConverter().convert(jwt)).isEmpty();
// HttpErrorHandlingTests.technicalErrorNeverEchoesItsMessage
var response = new GlobalExceptionHandler().handleException(
    new IllegalArgumentException("SENTINEL-secret"),
    new MockHttpServletRequest("GET", "/probe"), new MockHttpServletResponse());
assertThat(response.getStatusCode().value()).isEqualTo(500);
assertThat(response.getBody().code()).isEqualTo("INTERNAL_ERROR");
assertThat(response.getBody().message()).doesNotContain("SENTINEL-secret");
```

Pin các methods có test fixture cụ thể: `buyerCanReadOwnRouteButMachineCannot` GET order tickets fixture BUYER=200, INTERNAL_SERVICE=403; `organizerIsRequiredForCheckIn` POST check-ins ORGANIZER=200, BUYER=403; `internalProbeRequiresMachineRole` GET internal issuance INTERNAL_SERVICE=200, BUYER=403; `adminDoesNotGainBuyerQrAccess` ADMIN-only GET QR=403. Đây chỉ kiểm role matcher, không ownership. `wrongMethodAndUnknownPathAreDenied` BUYER DELETE order tickets/GET unknown=403; health anonymous=200, other actuator bị chặn. `invalidTokensAreUnauthorized` bad signature/expired/issuer/audience/missing token=401 với Bearer; role sai=403 insufficient_scope. `malformedCorrelationGetsReplaced` header `not-a-uuid` sinh UUID mới hợp lệ, body/header/log cùng ID; UUID hợp lệ được giữ. `frameworkErrorsKeepStatusAndHeaders` fixture 405 giữ Allow, validation input=400, invalid return value=500. `actualJsonFormatterDropsSecrets` dùng native formatter từ C với SENTINEL ở exception/cause/suppressed/MDC: JSON có serviceName=ticket-service, schemaVersion số, timestamp UTC; không có SENTINEL/email/QR/Authorization; safe printer có stack frames, bound và chống cycle.

- [ ] **Step 2: Chạy `rtk proxy .\mvnw.cmd -B -ntp '-Dtest=SecurityConfigTests,KeycloakRealmRoleConverterTests,HttpErrorHandlingTests,TicketLoggingTests' test`.** Expected FAIL do security/advice/helper chưa có hoặc route policy chưa đúng.
- [ ] **Step 3: Implement exception/security interfaces.** Route/method đúng spec §9; stateless/CSRF disabled, ERROR dispatch permit để giữ status, health public, anyRequest deny. 401/403 qua advice với WWW-Authenticate; phân biệt native datastore conflict/timeout/unavailable, HTTP client input và invariant lỗi nội bộ. Không mở API kinh doanh trong main source.
- [ ] **Step 4: Thêm JWT/log properties trong C và safe log helpers.** JWT env theo spec; native structured logstash UTF-8, field allowlist/rename và metadata theo Booking nhưng serviceName=ticket-service và customizer/printer package Ticket. Nếu cần annotations suppression có lý do như Booking thì thêm spotbugs-annotations 4.10.4 provided; không thêm runtime logging SDK. Test YAML chỉ dùng URI/JWK fixture nội bộ hoặc mock JwtDecoder cho contextLoads; production vẫn cần env thật.
- [ ] **Step 5: Chạy lại Step 2.** Expected tất cả PASS; cả sentinel và signature/issuer/audience/expiry phải được thực thi, không skip/disable vì chưa có hạ tầng.
- [ ] **Step 6: Commit checkpoint:** `feat(ticket): cấu hình bảo mật và xử lý lỗi`.

### Task 5: RabbitMQ, feature bean graph và bàn giao runtime

**Files:** Tạo M/config/RabbitMqConfig.java, M/messaging/TicketRabbitErrorHandler.java và hai test Task 5; sửa C, context test/YAML, HANDOFF; tạo TESTING. Các file feature dưới đây là danh sách đóng:

| Feature | Controller (M/<feature>/controller/) | Service (M/<feature>/service/) | Implementation (M/<feature>/service/impl/) |
|---|---|---|---|
| issuance | OrderTicketsController.java; InternalIssuanceController.java | IssuanceService.java | IssuanceServiceImpl.java |
| ticket | TicketController.java | TicketService.java | TicketServiceImpl.java |
| delivery | TicketDeliveryController.java | TicketDeliveryService.java | TicketDeliveryServiceImpl.java |
| checkin | CheckInController.java | CheckInService.java | CheckInServiceImpl.java |
| messaging | Không có controller | MessagingService.java | MessagingServiceImpl.java |

**Interfaces:**

- Consumes: Task 1 JsonMapper, Task 2 sáu repository, Task 3 mappers, Task 4 ErrorCode/log config/security.
- `RabbitMqConfig.rabbitMessageConverter(JsonMapper): MessageConverter`; `ticketRabbitContainerCustomizer(): ContainerCustomizer<SimpleMessageListenerContainer>`; TicketRabbitErrorHandler theo native ConditionalRejectingErrorHandler của Booking, không log raw Message.
- Service interfaces không có method. Impl @Service/@RequiredArgsConstructor: Issuance inject IssuanceRepository/EventAccessSnapshotRepository/IssuanceMapper; Ticket inject IssuanceRepository/TicketMapper; Delivery inject TicketDeliveryRepository/IssuanceRepository; CheckIn inject CheckinAttemptRepository/IssuanceRepository/EventAccessSnapshotRepository; Messaging inject InboxMessageRepository/OutboxMessageRepository/OutboxMessageMapper. Không inject client/worker.
- Controller roots: OrderTicketsController/TicketDeliveryController `/api/v1/orders`; InternalIssuanceController `/internal/v1/orders`; TicketController `/api/v1/tickets`; CheckInController `/api/v1/events`. Chỉ inject service tương ứng, chưa có method handler.

- [ ] **Step 1: Viết RuntimeConfigurationTests và TicketRabbitErrorHandlerTests với boundary assertions.** Đọc/bind central YAML C qua Spring YAML loader/environment, áp fixture env; không phụ thuộc Config Server thật và không chỉ grep chuỗi. Test converter thực với JsonMapper Task 1; Rabbit error tests thích nghi từ Booking.

```java
// converterUsesTheApplicationJsonPolicy
var converter = new RabbitMqConfig().rabbitMessageConverter(TicketFixtures.jsonMapper());
var message = converter.toMessage(TicketFixtures.completed(), new MessageProperties());
assertThat(TicketFixtures.jsonMapper().readTree(message.getBody()).get("ticketCount").asInt())
    .isEqualTo(2);
// fatalMessageDoesNotExposePayload
var failure = new ListenerExecutionFailedException("SENTINEL-exception",
    new org.springframework.amqp.support.converter.MessageConversionException("SENTINEL-cause"),
    new Message("SENTINEL-QR-email".getBytes(StandardCharsets.UTF_8), new MessageProperties()));
assertThatThrownBy(() -> new TicketRabbitErrorHandler().handleError(failure))
    .isInstanceOf(AmqpRejectAndDontRequeueException.class);
```

`rejectsMalformedJsonWithSharedConverter`: MessageProperties có contentType=application/json và inferredArgumentType=IssueTickets.class; invalid numeric/text coercion nhận qua converter → MessageConversionException, không chỉ kiểm outbound byte encoding. `fatalWithXDeathStillRejectsSafely` có x-death vẫn reject, captured logs không SENTINEL; `nonfatalFailureKeepsNativeDisposition` không đổi lỗi tạm thành success/drop. RuntimeConfigurationTests assert RabbitProperties dynamic=false, acknowledgeMode=MANUAL, prefetch=20, publisher confirms=CORRELATED, returns/mandatory=true, exchange=business.ex; datasource/JPA/JWT env bind đúng; username/pool/schema/timezone đã chốt, không mặc định password. Test context Rabbit nhỏ có converter/customizer dùng mock ConnectionFactory; không Queue/Exchange/Binding/RabbitAdmin hoặc listener container nghiệp vụ.

- [ ] **Step 2: Chạy `rtk proxy .\mvnw.cmd -B -ntp '-Dtest=RuntimeConfigurationTests,TicketRabbitErrorHandlerTests' test`.** Expected FAIL do config/error handler thiếu hoặc central Rabbit flags chưa đúng.
- [ ] **Step 3: Implement Rabbit config/error handler và hoàn thiện C.** Env RABBITMQ_HOST/PORT=5672/USERNAME=flash_ticket_app/PASSWORD/VHOST=/; dynamic=false, confirms correlated, returns/mandatory=true, exchange `${RABBITMQ_BUSINESS_EXCHANGE:business.ex}`, MANUAL/prefetch=20. Giữ MAIL_HOST/PORT/USERNAME/PASSWORD và không thêm Redis. Không phát message/declare topology để smoke.
- [ ] **Step 4: Tạo feature beans theo Interfaces; cập nhật TicketServiceApplicationTests/contextLoads.** Test-only exclude datasource/JPA/Rabbit/mail/Config/Eureka hiện có được giữ; dùng @MockitoBean(types={sáu repository}) và mock JwtDecoder tại context test để context boot không gọi mạng. Không mock mappers/feature service/advice/security cần kiểm bean graph. Chưa có handler/listener nên context PASS không được ghi là API/ACK đã chạy.
- [ ] **Step 5: Chạy lại Step 2 và `rtk proxy .\mvnw.cmd -B -ntp '-Dtest=TicketServiceApplicationTests' test`.** Expected BUILD SUCCESS; feature bean graph/converter/native error assertions PASS.
- [ ] **Step 6: Cập nhật HANDOFF/TESTING bằng kết quả thật.** Ghi package/7 entity/6 repo, env và cách nạp `.env` vào process, tunnel có sẵn/không commit credential, SET ROLE, config import, lệnh test và giới hạn DB proof. Giữ ba OPEN-TKT nguyên nghĩa: payment_confirmation_id chưa có bridge chargeId; business.ex khác B13 exchange; success/failure routing keys khác messageType. Danh sách incoming queues Ticket và nguồn definitions; không nói listener hoạt động. Nếu `.env.example` đã có mọi tên biến thì không sửa file đó.
- [ ] **Step 7: Chạy gate cuối `rtk proxy .\mvnw.cmd -B -ntp verify`.** Expected BUILD SUCCESS, fast suite/Spotless/JaCoCo/SpotBugs/Enforcer hiện có qua. Nếu formatting fail chỉ format file vừa sửa rồi chạy lại phần fail/gate cần thiết. Thiếu Java 21 là blocker môi trường cần báo, không skip Enforcer. Không tự thêm gate coverage mới.
- [ ] **Step 8: Smoke khi hạ tầng có sẵn và ghi rõ kết quả.** Không bắt buộc sửa hạ tầng để chạy. Runtime Ticket dùng env thật/Config Server/Eureka và ddl-auto=none; kiểm Config Server trả Ticket config (không in secret), health public, Eureka đăng ký TICKET-SERVICE, PostgreSQL read-only `SELECT current_database(),current_schema(),current_user;` cho ticket_db/ticket_schema/ticket_owner, Rabbit connection không auto-declare/publish/consume. Thử route production chưa có handler không chứng minh nghiệp vụ. Thiếu prerequisite thì HANDOFF ghi chưa chạy và lý do; không tạo DB fixture trên server triển khai.
- [ ] **Step 9: Commit checkpoint:** `feat(ticket): hoàn thiện scaffold và hướng dẫn runtime`.

## Coverage và kết thúc thực thi

| Spec | Task / bằng chứng |
|---|---|
| §1–5 phạm vi/cấu trúc | Constraints; Task 5 context/feature graph |
| §6 entity/repository | Task 2 helper test, mapping parity; Task 5 smoke nếu có môi trường |
| §7 contract/validation | Task 1 DTO/JSON/validation và 5 Review Focus được gắn task |
| §8 mapper/JSON | Task 1/3, clean generated MapStruct + round-trip |
| §9 security | Task 4 signed JWT/route fixtures/401–403 |
| §10 runtime | Task 2/4/5 central YAML, test isolation, optional smoke |
| §11 errors/logging | Task 4/5 Problem, formatter sentinel, native Rabbit error disposition |
| §12 điểm mở | Task 5 HANDOFF; không cố giải trong scaffold |
| §13 AC-01–09 | Task 1–5 targeted checks, cuối cùng verify; báo riêng phần DB/infrastructure chưa kiểm |
| §14 bàn giao | Task 5 HANDOFF/TESTING và diff/status cuối |

Sau gate cuối: kiểm `rtk git diff --check`, `rtk git status --short --branch` và phạm vi file thay đổi; báo task/check đã chạy, chưa chạy và mọi blocker còn lại. Không gọi scaffold là flow phát vé/check-in hoàn chỉnh.

Plan này chỉ là tài liệu, chưa thực thi task hoặc chạy test. Đề xuất thực thi Native vì các task cùng phụ thuộc DTO/entity/mapper và cùng sửa YAML Ticket; làm tuần tự tránh xung đột. Review plan trước khi triển khai; không tạo task/thread/subagent thay cho công cụ subagent khi chưa được người dùng yêu cầu.
