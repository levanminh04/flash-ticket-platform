# Booking Service Scaffold Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (<code>- [ ]</code>) syntax for tracking.

**Goal:** Tạo scaffold Booking Service có thể build gồm entity JPA, REST/snapshot/message DTO, validation biên, hai mapper MapStruct và cấu hình YAML bám đúng DDL Booking cùng B13 v0.2.

**Architecture:** Dùng JPA entity trực tiếp với khóa ngoại scalar, Java record cho contract, Jakarta constraints và record-local assertions cho các bất biến trong một payload. Jackson 3 do Spring quản lý giữ wire format nghiêm ngặt; MapStruct chỉ ánh xạ Order sang view và Envelope sang outbox.

**Tech Stack:** Java 21, Spring Boot 4.1.1, Spring Cloud 2025.1.3, Hibernate ORM 7.4.5, Jackson 3, Jakarta Validation, PostgreSQL, Lombok, MapStruct 1.6.3.

**Spec:** [Booking Service scaffold design v0.4](../specs/2026-10-22-booking-service-scaffold-design.md)

## Global Constraints

- Giữ Java 21, Spring Boot 4.1.1, Spring Cloud BOM 2025.1.3, Maven Wrapper và package gốc <code>com.flashticket.booking_service</code>.
- Chỉ thêm <code>org.mapstruct:mapstruct:1.6.3</code>, <code>org.mapstruct:mapstruct-processor:1.6.3</code> và <code>org.projectlombok:lombok-mapstruct-binding:0.2.0</code>; không nâng stack hoặc thêm framework mapping khác.
- Map đủ 15 bảng sau khi chạy prefix thành <code>booking_schema.tbl_*</code>, đủ 149 cột, bốn <code>@EmbeddedId</code> và đúng tám <code>@Version</code>; không sửa DDL.
- Entity dùng FK scalar, không tạo association/cascade/base entity/auditing callback; dùng Lombok getter/setter/no-args constructor nhưng không dùng <code>@Data</code>.
- Snapshot có shape cố định dùng DTO cụ thể; chỉ payload đa hình của inbox/outbox dùng Jackson <code>JsonNode</code>.
- DTO giữ tên field B13; tiền, version và counter trên wire là chuỗi; ID là <code>UUID</code>; thời gian là <code>Instant</code>; <code>schemaVersion</code> là integer bằng 1.
- Jackson 3 do Spring quản lý phải reject unknown field, missing creator property, number→String, String→number và float→integer; required-nullable chấp nhận null nhưng không chấp nhận missing.
- Scaffold không có controller, repository nghiệp vụ, service/use case, transaction tồn kho, scheduler, relay, consumer, HTTP client, Saga, SecurityFilterChain hay topology bean.
- Config Booking không chứa password mặc định, IP hạ tầng, Redis hoặc topology declaration; RabbitMQ dùng <code>dynamic: false</code>.
- Không tạo hoặc sửa file dưới <code>src/test/java</code> và <code>src/test/resources</code>. Giữ nguyên smoke test hiện có của repo.
- Không tạo persistence-test profile hoặc tuyên bố đã kiểm JSONB/catalog runtime. Ghi giới hạn này trong handoff.
- Mọi shell command trong repo phải bắt đầu bằng <code>rtk</code>. Chạy Maven từ <code>services/booking-service</code> bằng <code>rtk proxy .\mvnw.cmd ...</code>.
- Spec và plan trong <code>docs/superpowers/</code> đang bị Git ignore; giữ local, không force-add và không sửa <code>.gitignore</code>.

## Review Focus

1. Đối chiếu <code>BookingJsonConfig</code> để bảo đảm unknown/missing/coercion bị chặn mà không tạo ObjectMapper thứ hai.
2. Đối chiếu annotation của tiền/version ở biên 19 chữ số và <code>Long.MAX_VALUE</code>; JSON number, số mũ, phần lẻ, dấu cộng và leading zero không được hợp lệ.
3. Đọc các hàm thuần trong <code>ContractRules</code> cho tổng tiền, promotion/discount, cửa sổ thời gian và số ghế; không suy ra SEATED khi PurchaseSnapshot thiếu sector kind.
4. Kiểm mapping hữu hạn messageType/payload và bất biến requestId/messageId, ACCEPTED/DUPLICATE, COMPLETED/FAILED.
5. So từng entity với DDL cho đủ 149 cột, bốn khóa ghép, tám version field và JSONB type; runtime PostgreSQL vẫn là giới hạn chưa kiểm vì scope không có test file.

---

## File Structure

Các đường dẫn dưới đây nằm dưới <code>services/booking-service</code> nếu không ghi khác.

| Nhóm | File tạo/sửa | Trách nhiệm |
|---|---|---|
| JSON | <code>src/main/java/com/flashticket/booking_service/config/BookingJsonConfig.java</code> | Coercion và required-field behavior trên Jackson 3 chung của Spring |
| Validation | <code>validation/ContractRules.java</code> | Hằng regex/bigint và các hàm thuần cho bất biến liên field |
| Enum | <code>enums/Currency.java</code>, <code>SalesMode.java</code>, <code>EventState.java</code>, <code>OrderState.java</code>, <code>ReservationState.java</code>, <code>ResourceKind.java</code>, <code>SectorType.java</code>, <code>SeatState.java</code>, <code>PromotionUsageState.java</code> | Token dùng chung bởi entity/DTO |
| Snapshot DTO | <code>dto/snapshot/SeatSelectionSnapshot.java</code>, <code>SelectionSnapshot.java</code>, <code>SalesConfiguration.java</code>, <code>PromotionConfig.java</code>, <code>PromotionConfigurationSnapshot.java</code>, <code>PurchaseSnapshot.java</code> | Root/shared type; kiểu chỉ dùng một lần lồng trong record sở hữu |
| REST DTO | <code>dto/request/CreateOrderRequest.java</code>, <code>OrderSelection.java</code>, <code>ApplyPromotionRequest.java</code>, <code>StartPaymentRequest.java</code>, <code>FreezePaymentRequest.java</code>; <code>dto/response/OrderView.java</code>, <code>OrderItemView.java</code>, <code>OrderPage.java</code>, <code>Availability.java</code>, <code>PaymentRequestAccepted.java</code>, <code>Problem.java</code> | Contract REST; toàn bộ selection variants và QuantityItem lồng trong OrderSelection |
| Message DTO | <code>dto/message/Envelope.java</code>, <code>EventCancellation.java</code>, <code>AcceptPayment.java</code>, <code>PaymentDecision.java</code>, <code>StartPayment.java</code>, <code>IssuanceResult.java</code>, <code>OrderCompleted.java</code>, <code>BookingIssuanceResult.java</code> | Envelope và payload Booking gửi/nhận |
| Core entity | <code>entity/EventSalesSnapshot.java</code>, <code>Order.java</code>, <code>OrderItem.java</code>, <code>Reservation.java</code>, <code>OrderItemSeat.java</code>, <code>OrderItemSeatId.java</code> | Aggregate order và sales snapshot |
| Inventory entity | <code>entity/TicketTypeInventory.java</code>, <code>SectorInventory.java</code>, <code>SeatInventory.java</code>, <code>ReservationAllocation.java</code>, <code>PurchaseLimit.java</code>, <code>PurchaseLimitId.java</code>, <code>PromotionInventory.java</code>, <code>PromotionUsage.java</code> | Tồn kho, allocation và promotion persistence |
| Operational entity | <code>entity/IdempotencyRequest.java</code>, <code>IdempotencyRequestId.java</code>, <code>OutboxMessage.java</code>, <code>InboxMessage.java</code>, <code>InboxMessageId.java</code> | Idempotency và storage message |
| Mapper | <code>mapper/OrderMapper.java</code>, <code>OutboxMessageMapper.java</code>, <code>MappingValueConverter.java</code>; sửa <code>pom.xml</code> | Hai MapStruct bean và conversion helper |
| Config/handoff | Sửa <code>../../infrastructure/config-server/src/main/resources/config/booking-service.yaml</code> và <code>HANDOFF.md</code> | Runtime configuration, biến môi trường, deviation và giới hạn verification |

### Task 1: JSON boundary, enums and shared contract rules

**Files:**
- Create: <code>services/booking-service/src/main/java/com/flashticket/booking_service/config/BookingJsonConfig.java</code>
- Create: <code>services/booking-service/src/main/java/com/flashticket/booking_service/validation/ContractRules.java</code>
- Create: the nine enum files listed in File Structure

**Interfaces:**
- Consumes: Spring-managed <code>tools.jackson.databind.json.JsonMapper.Builder</code> through <code>JsonMapperBuilderCustomizer</code>.
- Produces: <code>JsonMapperBuilderCustomizer bookingJsonCustomizer()</code>; decimal regex constants, signed-bigint max and pure helper methods in <code>ContractRules</code>.

- [ ] **Step 1: Implement the shared enums and constants**

Create exact token sets from DDL/B13. Add compile-time constants for zero-allowed/positive decimal strings and signed-bigint max <code>9223372036854775807</code>; do not create custom constraint annotations.

- [ ] **Step 2: Implement the Spring JSON customizer**

Enable <code>FAIL_ON_UNKNOWN_PROPERTIES</code> and <code>FAIL_ON_MISSING_CREATOR_PROPERTIES</code>. Configure coercion to fail for number/boolean→String, String→integer and float→integer. Keep Spring's mapper/modules; do not declare a second <code>ObjectMapper</code>.

- [ ] **Step 3: Compile**

~~~powershell
rtk proxy .\mvnw.cmd -DskipTests compile
~~~

Expected: BUILD SUCCESS.

- [ ] **Step 4: Commit**

~~~powershell
rtk git add services/booking-service/src/main/java/com/flashticket/booking_service/config services/booking-service/src/main/java/com/flashticket/booking_service/validation services/booking-service/src/main/java/com/flashticket/booking_service/enums
rtk git commit -m "feat(booking): thêm nền tảng contract"
~~~

### Task 2: Snapshot DTOs

**Files:**
- Create: the six snapshot DTO files and shared <code>dto/response/OrderItemView.java</code> listed in File Structure
- Modify: <code>services/booking-service/src/main/java/com/flashticket/booking_service/validation/ContractRules.java</code>

**Interfaces:**
- Consumes: enums and constants from Task 1.
- Produces: typed JSONB snapshot roots, nested config/discount/applied-promotion records and <code>OrderItemView</code>.

- [ ] **Step 1: Implement snapshot records**

Use exact fields from spec §5.3. Nest TicketTypeConfig/SectorConfig/SeatConfig in SalesConfiguration, FIXED/PERCENT discount in PromotionConfig and AppliedPromotionSnapshot in PurchaseSnapshot. Apply <code>@Valid</code>, <code>@Pattern</code>, <code>@DecimalMax</code>, collection and int32 constraints directly.

- [ ] **Step 2: Implement snapshot invariants**

Add <code>@AssertTrue</code>/<code>@JsonIgnore</code> methods on SalesConfiguration and PurchaseSnapshot. Delegate to overloaded pure functions in ContractRules for schema version, windows, unique IDs, references, exact money equations, promotion/null consistency, seat count when present and <code>frozenAt < expiresAt</code>.

- [ ] **Step 3: Compile and inspect**

~~~powershell
rtk proxy .\mvnw.cmd -DskipTests compile
rtk rg -n "record (TicketTypeConfig|SectorConfig|SeatConfig|AppliedPromotionSnapshot)|sealed interface PromotionDiscount" src/main/java/com/flashticket/booking_service/dto
~~~

Expected: BUILD SUCCESS; single-use records appear only inside their owner files.

- [ ] **Step 4: Commit**

~~~powershell
rtk git add services/booking-service/src/main/java/com/flashticket/booking_service/dto/snapshot services/booking-service/src/main/java/com/flashticket/booking_service/dto/response/OrderItemView.java services/booking-service/src/main/java/com/flashticket/booking_service/validation/ContractRules.java
rtk git commit -m "feat(booking): thêm hợp đồng snapshot"
~~~

### Task 3: REST DTOs and native selection hierarchy

**Files:**
- Create: all REST files listed in File Structure except <code>OrderItemView.java</code>
- Modify: <code>services/booking-service/src/main/java/com/flashticket/booking_service/validation/ContractRules.java</code>

**Interfaces:**
- Consumes: Tasks 1–2 enums, snapshots, shared view and rules.
- Produces: <code>sealed interface OrderSelection</code> containing <code>OrderSelection.QuantityItem</code>, <code>OrderSelection.QuantitySelection</code>, <code>OrderSelection.SeatedSelection</code>, <code>OrderSelection.StandingSelection</code> and <code>OrderSelection.SeatMapSelection</code>; remaining request/response records from spec §5.2.

- [ ] **Step 1: Implement REST records**

Use exact fields from spec §5.2. Nest single-use children in Availability and Problem. Apply standard constraints to email, non-empty collections, quantities, provider and required values.

- [ ] **Step 2: Implement native Jackson selection**

Discriminate OrderSelection by <code>mode</code>: QUANTITY → <code>OrderSelection.QuantitySelection</code>, SEAT_MAP → <code>OrderSelection.SeatMapSelection</code>. Discriminate SeatMapSelection by <code>kind</code>: SEATED → <code>OrderSelection.SeatedSelection</code>, STANDING → <code>OrderSelection.StandingSelection</code>. Keep <code>OrderSelection.QuantityItem</code> in the same file because only the hierarchy uses it. Add record-local assertions for unique IDs; rely on global unknown-field rejection for foreign branch fields.

- [ ] **Step 3: Compile**

~~~powershell
rtk proxy .\mvnw.cmd -DskipTests compile
~~~

Expected: BUILD SUCCESS without a custom deserializer.

- [ ] **Step 4: Commit**

~~~powershell
rtk git add services/booking-service/src/main/java/com/flashticket/booking_service/dto/request services/booking-service/src/main/java/com/flashticket/booking_service/dto/response services/booking-service/src/main/java/com/flashticket/booking_service/validation/ContractRules.java
rtk git commit -m "feat(booking): thêm hợp đồng REST"
~~~

### Task 4: Message DTOs

**Files:**
- Create: all message DTO files listed in File Structure
- Modify: <code>services/booking-service/src/main/java/com/flashticket/booking_service/validation/ContractRules.java</code>

**Interfaces:**
- Consumes: SalesConfiguration, PurchaseSnapshot, Currency and ContractRules.
- Produces: <code>Envelope&lt;T&gt;</code>, seven payload records, nested <code>EventCancellation.AccessConfiguration</code> and <code>ContractRules.expectedPayloadType(String)</code>.

- [ ] **Step 1: Implement message records**

Use exact fields/message names from spec §5.4. Keep provider, payment outcome, issuance state and FAILED-only state nested in their owning records; do not create a generic Status enum.

- [ ] **Step 2: Implement message invariants**

Add record-local <code>@AssertTrue</code>/<code>@JsonIgnore</code> methods for payment decision, issuance result and envelope. ContractRules owns the finite messageType→payload mapping and requestId/messageId comparison; do not use class-name reflection.

- [ ] **Step 3: Compile**

~~~powershell
rtk proxy .\mvnw.cmd -DskipTests compile
~~~

Expected: BUILD SUCCESS.

- [ ] **Step 4: Commit**

~~~powershell
rtk git add services/booking-service/src/main/java/com/flashticket/booking_service/dto/message services/booking-service/src/main/java/com/flashticket/booking_service/validation/ContractRules.java
rtk git commit -m "feat(booking): thêm hợp đồng message"
~~~

### Task 5: Complete entity catalogue

**Files:**
- Create: all 15 entity and four embedded-ID files listed in File Structure

**Interfaces:**
- Consumes: persistence enums, typed snapshots and Jackson 3 JsonNode.
- Produces: the complete Booking persistence model for order, inventory, promotion, idempotency, outbox and inbox tables.

- [ ] **Step 1: Implement all persistence types**

Map every DDL column with explicit schema/table/column names and nullability. Use UUID, Instant, Integer, Long, BigDecimal precision 19 scale 0, string enums and typed JSONB; keep foreign keys scalar. Generate UUID only for Order, OrderItem, Reservation, ReservationAllocation and PromotionUsage. Map outbox/inbox polymorphic payloads as JsonNode and preserve caller-supplied message metadata.

- [ ] **Step 2: Implement key and version rules**

Create serializable OrderItemSeatId, PurchaseLimitId, IdempotencyRequestId and InboxMessageId with equality on exact PK components. Add <code>@Version</code> only to Order, Reservation, TicketTypeInventory, SectorInventory, SeatInventory, PurchaseLimit, PromotionInventory and PromotionUsage.

- [ ] **Step 3: Review the catalogue against DDL**

Compare every field with <code>docs/architecture/data/postgres/20-booking.sql</code> and the prefix script. Count 15 entities, four embedded IDs, 149 columns and eight version fields. Confirm no JPA association, base entity or Lombok Data annotation. Do not treat compile success as PostgreSQL runtime evidence.

- [ ] **Step 4: Compile**

~~~powershell
rtk proxy .\mvnw.cmd -DskipTests compile
~~~

Expected: BUILD SUCCESS.

- [ ] **Step 5: Commit**

~~~powershell
rtk git add services/booking-service/src/main/java/com/flashticket/booking_service/entity
rtk git commit -m "feat(booking): ánh xạ mô hình persistence"
~~~

### Task 6: MapStruct mappers

**Files:**
- Modify: <code>services/booking-service/pom.xml</code>
- Create: <code>mapper/OrderMapper.java</code>, <code>OutboxMessageMapper.java</code>, <code>MappingValueConverter.java</code>

**Interfaces:**
- Consumes: Tasks 1–5 entity/DTO types, Spring ObjectMapper and Validator.
- Produces: <code>OrderView toView(Order, List&lt;OrderItem&gt;)</code>, <code>OrderItemView toItemView(OrderItem)</code>, <code>OutboxMessage toEntity(Envelope&lt;?&gt;)</code>, <code>&lt;T&gt; Envelope&lt;T&gt; toEnvelope(OutboxMessage, Class&lt;T&gt;)</code>.

- [ ] **Step 1: Add the MapStruct build configuration**

Add MapStruct API 1.6.3. In compiler execution <code>default-compile</code>, retain Lombok, then add lombok-mapstruct-binding 0.2.0 and mapstruct-processor 1.6.3. Do not add Spring Extensions or a second compiler execution.

- [ ] **Step 2: Implement MappingValueConverter**

Provide only the conversions used by the two mappers: BigDecimal → Money string, Long → UInt64 string, SelectionSnapshot → seat list and typed DTO ↔ JsonNode. Reject fractional/out-of-range persistence values; do not add reverse numeric converters until a write mapper needs them.

- [ ] **Step 3: Implement the mapper interfaces**

Use Spring component model, <code>unmappedTargetPolicy = ERROR</code> and MappingValueConverter. Map order IDs/state/items explicitly. For outbox set publishedAt=null, attempts=0, nextAttemptAt=occurredAt and lastErrorCode=null; restore envelopes using the finite type map.

- [ ] **Step 4: Compile and inspect generated sources**

~~~powershell
rtk proxy .\mvnw.cmd clean -DskipTests compile
rtk rg -n "class (OrderMapperImpl|OutboxMessageMapperImpl)" target/generated-sources/annotations
~~~

Expected: BUILD SUCCESS and both generated implementations exist.

- [ ] **Step 5: Commit**

~~~powershell
rtk git add services/booking-service/pom.xml services/booking-service/src/main/java/com/flashticket/booking_service/mapper
rtk git commit -m "feat(booking): thêm mapper MapStruct"
~~~

### Task 7: Runtime YAML and handoff

**Files:**
- Modify: <code>infrastructure/config-server/src/main/resources/config/booking-service.yaml</code>
- Modify: <code>services/booking-service/HANDOFF.md</code>

**Interfaces:**
- Consumes: completed scaffold and existing Config Server bootstrap.
- Produces: Booking datasource/JPA/Rabbit/JWT configuration and operator handoff.

- [ ] **Step 1: Implement Booking runtime YAML**

Set port 8082 default, required BOOKING_DB_URL/password, booking_admin, SET ROLE booking_owner, pool 5/1, schema, UTC, Rabbit manual ACK/prefetch 20/dynamic false/confirm/returns/mandatory/business.ex and JWT issuer/JWK/audience. Do not add password defaults, Redis, topology beans or infrastructure IPs.

- [ ] **Step 2: Update the handoff**

Document runtime variables, process-loading requirement, application versus bootstrap password, generated type inventory and AC status. Record OPEN-SCF-01 and the three transport deviations. State explicitly that no new test source/profile exists and PostgreSQL catalog/JSONB round-trip remains unverified.

- [ ] **Step 3: Run final build and scope checks**

~~~powershell
rtk proxy .\mvnw.cmd clean verify
rtk git diff --check
rtk git status --short
~~~

Expected: existing repository verification passes; diff has no whitespace errors; no new or modified path appears under <code>services/booking-service/src/test</code>. Confirm no controller, repository, consumer, relay, Redis, topology bean or unrelated owner config appears in the diff.

- [ ] **Step 4: Commit**

~~~powershell
rtk git add infrastructure/config-server/src/main/resources/config/booking-service.yaml services/booking-service/HANDOFF.md
rtk git commit -m "chore(booking): hoàn thiện cấu hình scaffold"
~~~

