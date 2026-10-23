# Từ ShopApp-BE sang FlashTicket Backend

> Tài liệu chuyển tiếp dành cho người đã hiểu toàn bộ backend `ShopApp-BE` và cần đọc đúng các kiểu dữ liệu, hợp đồng, mapping, validation, persistence và setup mới của FlashTicket.

## 1. Phạm vi và trạng thái bằng chứng

Tài liệu này không dạy lại Spring Boot, Controller–Service–Repository, JPA hay JWT cơ bản. Mỗi phần bắt đầu từ cách đã dùng trong `ShopApp-BE`, sau đó giải thích FlashTicket đổi gì, vì sao phải đổi và chỗ nào trong code/DB thể hiện quyết định đó.

Nguồn đối chiếu:

- `ShopApp-BE`: `D:\tailieu\ky2nam3\web\ShopApp-BE`, commit `389c17853e9dab1eb7e2700ccb39341338b7e6eb`, nhánh `main`. Working tree có thay đổi cục bộ ở `CommentMapper.java` và `application.yml`; tài liệu không sửa repo này.
- FlashTicket: worktree `booking-scaffold`, HEAD `ac7291bf54d718f7701bbd889eee704e22994855` cộng với thay đổi scaffold Booking chưa commit.
- [B12 — data ownership và schema](../architecture/B12-data-ownership-and-schema.md) đang `APPROVED`.
- [B13 — API và event contracts](../architecture/B13-api-and-event-contracts.md) và [B14 — main flow](../architecture/B14-main-flow-sequences.md) bản hiện tại đang `REVIEW_READY`, chưa phải bằng chứng API/E2E đã chạy.
- [Booking handoff](../../services/booking-service/HANDOFF.md) xác nhận scaffold chưa có controller, repository, business service, consumer, outbox relay hoặc scheduler.

Các nhãn được dùng:

- **FACT:** quan sát trực tiếp từ code hoặc DDL hiện tại.
- **DECIDED:** quyết định đã được duyệt trong tài liệu kiến trúc.
- **TARGET:** hành vi đích trong contract/flow, chưa đồng nghĩa đã triển khai.
- **Đánh giá:** nhận xét kỹ thuật về ưu, nhược điểm hoặc rủi ro.
- **Khuyến nghị:** cấu trúc nên dùng khi triển khai production.

Trọng tâm của tài liệu là code Booking, không chỉ định nghĩa khái niệm. [Mục 4.7](#47-code-lab-chọn-đúng-loại-java-class-trong-booking) có ví dụ chọn `final class`, `record`, enum, entity và `@Embeddable`; [Mục 6](#6-case-study-vì-sao-createorderrequest-dùng-orderselection-interface) ghép sealed interface với ba chiến lược inventory; [Mục 7](#7-mapstruct-cùng-thư-viện-nhưng-cách-dùng-đã-đổi) minh họa MapStruct; [Mục 8.6](#86-code-sample-cho-từng-tầng-validation) đặt code validation vào đúng tầng.

## 2. Mô hình tư duy tổng quát

| Chủ đề | ShopApp-BE | FlashTicket |
|---|---|---|
| Ranh giới ứng dụng | Một Spring Boot application | Năm service nghiệp vụ: Event, Booking, Payment, Ticket, User |
| Dữ liệu | Một mô hình JPA liên kết trực tiếp trong MySQL | Mỗi service sở hữu database/schema; liên service dùng ID mềm, snapshot và message |
| Giao tiếp | Gọi method trong cùng process; RabbitMQ chủ yếu cho email/file | HTTP cho truy vấn/guard cần phản hồi; RabbitMQ cho phối hợp bất đồng bộ đã version hóa |
| DTO | Lớp mutable bằng Lombok, DTO thường dùng khá rộng | Tách request, response, message và snapshot; ưu tiên immutable/fixed shape |
| Kiểu ID | `Long` auto increment | `UUID` cho resource/message; composite ID cho idempotency và quan hệ nhiều khóa |
| Thời gian | `LocalDateTime` | `Instant` ở Java, `timestamptz` ở PostgreSQL |
| Tiền | `Double` | `NUMERIC(19,0)`/`BigDecimal` nội bộ; chuỗi số nguyên trên wire |
| Trạng thái | Nhiều chỗ lưu `String` | Enum Java + `CHECK` DB + transition rule ở service |
| Mapping | MapStruct DTO ↔ entity/response, thường bỏ qua field | MapStruct fail-fast cho target, converter rõ ràng; không giao business decision cho mapper |
| Validation | Annotation từng field và `if/throw` trong service | Validation nhiều lớp: JSON → Bean Validation → contract rule → service/domain → DB |
| Authentication | Ứng dụng tự quản user/password và tự phát JWT | Keycloak phát token; Gateway/service là OAuth2 Resource Server |
| Retry/trùng lặp | Chủ yếu xử lý theo request hoặc queue riêng lẻ | Idempotency request, inbox, outbox, version/hash/correlation |
| Cấu hình | `application.yml`, profile dev/prod trong cùng app | Config Server + env vars; service discovery; API Gateway |
| Build gate | Maven compile/test thông thường | Khóa JDK 21, format, coverage và static analysis trong Maven |

Thay đổi lớn nhất là ranh giới tin cậy. Trong ShopApp, một entity có thể tham chiếu entity khác và một transaction có thể sửa nhiều bảng nghiệp vụ. Trong FlashTicket, một service không được JOIN DB của service khác. Dữ liệu bên ngoài đi qua HTTP/message, sau đó được lưu thành snapshot hoặc projection cục bộ trước khi dùng.

## 3. Setup hệ thống mới

### 3.1 Nhiều ứng dụng độc lập

FlashTicket hiện có các ứng dụng Maven riêng:

- `event-service`
- `booking-service`
- `payment-service`
- `ticket-service`
- `user-service`
- `api-gateway`
- `config-server`
- `discovery-server`

Đây không chỉ là chia package. Mỗi process có port, datasource, security context, lifecycle deploy và failure mode riêng. Một service chết không làm JVM của service khác chết, nhưng mọi lời gọi mạng/message phải chấp nhận timeout, retry, trùng lặp và dữ liệu đến sai thứ tự.

**Đánh giá:** các `pom.xml` hiện lặp lại khá nhiều version/plugin và repo chưa có parent/aggregator Maven chung. Điều này dễ làm các service lệch Spring Boot, Spring Cloud hoặc quality gate.

**Khuyến nghị:** tạo parent POM dùng chung khi bước implementation ổn định; mỗi service chỉ khai dependency riêng. Không gộp source code hay data ownership chỉ vì dùng chung parent build.

### 3.2 Config Server

Mỗi service chỉ giữ bootstrap tối thiểu, ví dụ [Booking `application.yaml`](../../services/booking-service/src/main/resources/application.yaml):

```yaml
spring:
  application:
    name: booking-service
  config:
    import: "configserver:${CONFIG_SERVER_URL:http://localhost:8888}"
```

Cấu hình thực tế nằm tại [config/booking-service.yaml](../../infrastructure/config-server/src/main/resources/config/booking-service.yaml). Config Server hiện dùng native classpath, chưa phải Git-backed config repository.

Khác với ShopApp:

- Spring không tự đọc `.env`; IDE/container/deployment phải inject biến môi trường.
- Secret không nên ghi vào YAML commit trong Git.
- Service cần Config Server trước khi khởi động, trừ khi sau này cấu hình import là optional hoặc có cơ chế fallback rõ ràng.
- Thay đổi config trung tâm không có nghĩa mọi bean tự refresh; phải xác định property nào reload được và cách rollout.

### 3.3 Discovery Server và API Gateway

Eureka cung cấp tên logical như `lb://booking-service`; Gateway route request đến instance được discover. Gateway hiện đảm nhiệm:

- route theo path/method;
- kiểm JWT và role ở biên;
- CORS;
- Redis rate limiting;
- circuit breaker/fallback;
- retry có giới hạn cho một số `GET`.

Gateway dùng WebFlux nên security là `SecurityWebFilterChain`; các business service dùng MVC nên dùng servlet security. Không được copy `SecurityFilterChain` kiểu MVC vào Gateway.

**Khuyến nghị:** Gateway là lớp bảo vệ đầu tiên, không phải lớp bảo vệ duy nhất. Booking vẫn phải xác thực token/subject và quyền sở hữu đơn vì request nội bộ hoặc cấu hình route sai không được phép vượt qua invariant nghiệp vụ.

### 3.4 Keycloak thay cho JWT tự phát hành

ShopApp có `UserEntity`, `RoleEntity`, `JwtTokenUtils`, `JwtTokenFilter` và tự xác thực password. FlashTicket chuyển phần identity/token sang Keycloak:

- Keycloak phát access token.
- Gateway và service xác minh issuer, chữ ký, audience.
- `KeycloakRealmRoleConverter` chỉ chấp nhận ba business role `BUYER`, `ORGANIZER`, `ADMIN` rồi đổi thành `ROLE_*` cho Spring Security.
- `buyerSubject`/`organizerSubject` là `sub` ổn định trong JWT, không phải FK sang bảng user của Booking.

Lợi ích là không nhân bản password/token logic trong năm service. Đổi lại, local development cần thêm Keycloak, realm import, client/audience và service account cho API nội bộ.

### 3.5 PostgreSQL, MongoDB, RabbitMQ và Redis

- Event, Booking, Payment và Ticket dùng PostgreSQL.
- User dùng MongoDB vì hồ sơ người dùng thuộc owner riêng.
- RabbitMQ là business message bus.
- Redis hiện phục vụ rate limiting ở Gateway và có thể dùng cho nhu cầu đã được thiết kế, nhưng không thay DB owner.

PostgreSQL có role owner/admin/viewer, database/schema riêng và `ddl-auto: none`. Booking kết nối bằng `booking_admin`, sau đó Hikari chạy `SET ROLE booking_owner`. Application không được tự sửa schema bằng Hibernate.

Canonical B12 DDL dùng tên `orders`, `order_items`, v.v. Local bootstrap chạy [services/99-table-prefix.sql](../../services/99-table-prefix.sql) để đổi thành `tbl_orders`, `tbl_order_items`, khớp annotation entity. Đây là một adapter local inspection, không phải hai schema khác nhau về nghiệp vụ.

**Đánh giá:** việc DDL canonical không có prefix nhưng entity/runtime local có prefix tạo thêm tải nhận thức và rủi ro migration lệch.

**Khuyến nghị:** trước production nên chốt một tên vật lý duy nhất và quản lý bằng Flyway/Liquibase. Nếu vẫn giữ bước rename, CI phải dựng DB từ đầu rồi kiểm Hibernate mapping với schema sau transform.

### 3.6 JDK và quality gate

Booking dùng Java 21, Spring Boot 4.1.1 và MapStruct 1.6.3. Maven Enforcer chỉ chấp nhận `[21,22)`, vì vậy JDK 23 trên máy hiện tại sẽ bị từ chối nếu không skip rule.

Các gate hiện có:

- Maven Enforcer: đúng JDK.
- Spotless/Google Java Format: format nhất quán.
- JaCoCo: báo cáo coverage.
- SpotBugs: static analysis, `maxAllowedViolations=0` ở mức cấu hình hiện tại.

Booking hiện không có test source theo phạm vi scaffold đã chốt; dependency test và plugin vẫn còn để bước implementation sau dùng lại.

## 4. Kiểu dữ liệu: từ “kiểu Java tiện dùng” sang “kiểu có nghĩa trên wire và DB”

### 4.1 `Long` ID và `UUID`

ShopApp dùng `GenerationType.IDENTITY` và `Long`. Cách này đơn giản vì một DB cấp ID.

FlashTicket dùng `UUID` cho order, message, reservation, seat, ticket type và correlation. UUID có thể được tạo ở nhiều process mà không cần gọi một sequence chung, phù hợp với service độc lập và message tạo trước khi DB commit.

Không phải mọi số đều đổi sang UUID:

- `rowVersion`, `sourceConfigVersion`, `aggregateVersion` vẫn là số tăng.
- counter tồn kho vẫn là `long`/`integer`.
- composite business key dùng `@EmbeddedId`, ví dụ `(actorSubject, operation, requestKey)` cho idempotency.

### 4.2 `LocalDateTime` và `Instant`

`LocalDateTime` trong ShopApp không chứa timezone/offset. Hai máy ở timezone khác nhau có thể hiểu cùng chuỗi theo hai thời điểm khác nhau.

FlashTicket dùng:

- Java: `Instant`.
- PostgreSQL: `timestamptz`.
- JSON: timestamp chuẩn UTC theo contract.

PostgreSQL lưu `timestamptz` như một thời điểm tuyệt đối và hiển thị theo timezone session. Việc DB session đang dùng `Asia/Ho_Chi_Minh` không biến `Instant` thành giờ địa phương trong domain. `hibernate.jdbc.time_zone: UTC` vẫn hữu ích để binding nhất quán. UI mới là nơi định dạng UTC+7 cho người dùng.

**Khuyến nghị:** không quay lại `LocalDateTime` cho `occurredAt`, `expiresAt`, `frozenAt`, callback time hoặc audit time. Chỉ dùng `LocalDate`/`LocalTime` khi nghiệp vụ thực sự là ngày/giờ không gắn với một instant.

### 4.3 `Double` và tiền

ShopApp lưu giá/tổng bằng `Double`. Số dấu phẩy động nhị phân không thích hợp cho phép so sánh tiền chính xác.

FlashTicket dùng ba biểu diễn có chủ đích:

| Biên | Kiểu | Lý do |
|---|---|---|
| PostgreSQL | `NUMERIC(19,0)` domain `money_amount` | VND nguyên, phép CHECK chính xác |
| Entity/domain Java | `BigDecimal` scale 0 | Tính và persist chính xác |
| JSON wire | `String`, ví dụ `"500000"` | Không phụ thuộc giới hạn integer/float của JavaScript và consumer |

`MappingValueConverter.toMoney` buộc số không âm, scale bằng 0 và đúng dải 19 chữ số trước khi đưa ra wire.

**Đánh giá:** đây là conversion contract, không phải business calculation. Mapper được phép đổi `BigDecimal(500000)` thành `"500000"`; mapper không được tự tính subtotal, discount hoặc total.

### 4.4 `UInt64` trong contract

Contract gọi version/counter dương là `UInt64`, nhưng implementation hiện dùng `Long`/PostgreSQL `BIGINT` và `ContractRules.validUInt64` giới hạn ở `Long.MAX_VALUE` (`9_223_372_036_854_775_807`), không phải toàn bộ unsigned 64-bit.

**Đánh giá:** tên đang rộng hơn khả năng lưu trữ thật.

**Khuyến nghị:** chọn một trong hai và ghi nhất quán:

1. Đổi tên contract thành `PositiveInt64`/`NonNegativeInt64`; đây là phương án đơn giản, khớp DB/Java hiện tại.
2. Nếu thực sự cần unsigned 64-bit, đổi DB sang `NUMERIC(20,0)` và Java sang `BigInteger`; hiện chưa có nhu cầu nghiệp vụ đủ mạnh cho độ phức tạp này.

### 4.5 Enum thay cho trạng thái `String`

ShopApp có `OrderEntity.status` kiểu `String` rồi parse bằng `OrderStatus.fromString`. FlashTicket dùng `OrderState`, `ReservationState`, `SeatState`, `ResourceKind`, `SalesMode`, `SectorType`, `Currency`.

Enum giúp compiler bắt typo và `switch` có thể exhaustiveness check. DB vẫn có `CHECK`, vì enum Java không ngăn script SQL ghi giá trị sai.

**Đánh giá:** `SalesConfiguration.EventStatus` và entity `EventState` đang biểu diễn gần cùng tập giá trị bằng hai enum riêng. Tách wire enum và persistence enum có thể hợp lý để chống coupling, nhưng cần mapper/test rõ; nếu luôn đồng nhất thì duplication dễ drift.

### 4.6 `record`, final class và sealed interface

Không phải cứ `record` là value object. Cần phân biệt:

- **Entity:** có identity và lifecycle, mutable cho JPA, ví dụ `Order`, `Reservation`.
- **Request/response DTO:** hình dạng ở HTTP boundary.
- **Message DTO:** payload giao tiếp service.
- **Snapshot:** bản sao bất biến của dữ liệu đã dùng cho quyết định.
- **Value object:** được xác định bởi giá trị, thường bất biến và tự bảo vệ invariant.

FlashTicket dùng:

- `record` cho message/snapshot vì tự có constructor, accessor, `equals/hashCode`, phù hợp dữ liệu bất biến.
- final class + final field + `@JsonCreator` cho request/response cần kiểm soát Jackson/Lombok.
- sealed interface cho tập biến thể đóng như `OrderSelection` và `PromotionConfig.Discount`.
- entity vẫn là class có no-arg constructor và setter để JPA hoạt động.

### 4.7 Code lab: chọn đúng loại Java class trong Booking

Các ví dụ từ đây dùng hai nhãn:

- **CURRENT:** code đã tồn tại trong Booking scaffold; đoạn trích có thể lược bớt import/field không liên quan.
- **TARGET:** mẫu áp dụng khi viết controller, service hoặc repository. Scaffold hiện chưa có các lớp runtime này, nên tên dependency trong mẫu thể hiện vai trò cần có chứ không khẳng định chúng đã tồn tại.

| Nhu cầu trong Booking | Loại Java nên dùng | Ví dụ hiện tại |
|---|---|---|
| HTTP request có shape cố định | `final class` với final field | `CreateOrderRequest` |
| Message/snapshot bất biến | `record` | `Envelope<T>`, `StartPayment`, `PurchaseSnapshot` |
| Một request có hữu hạn shape | `sealed interface` | `OrderSelection` |
| Tập giá trị nghiệp vụ đóng | `enum` | `OrderState`, `SalesMode`, `ResourceKind` |
| Row có identity và lifecycle | JPA `@Entity` class | `Order`, `Reservation`, inventory |
| Primary key gồm nhiều cột | `@Embeddable` class | `IdempotencyRequestId` |
| Phép đổi shape xác định | MapStruct interface/abstract mapper | `OrderMapper`, `OutboxMessageMapper` |
| Rule thuần dùng ở nhiều boundary | utility/fn thuần | `ContractRules` |

#### 4.7.1 `final class`: HTTP request

**CURRENT — shape nhận từ client:**

```java
@Getter
public final class CreateOrderRequest {
  @NotNull private final UUID eventId;
  @NotBlank @Email @Size(max = 254)
  private final String customerEmail;
  @NotNull @Valid private final OrderSelection selection;

  @JsonCreator(mode = JsonCreator.Mode.PROPERTIES)
  public CreateOrderRequest(
      @JsonProperty(value = "eventId", required = true) UUID eventId,
      @JsonProperty(value = "customerEmail", required = true) String customerEmail,
      @JsonProperty(value = "selection", required = true) OrderSelection selection) {
    this.eventId = eventId;
    this.customerEmail = customerEmail;
    this.selection = selection;
  }
}
```

`final class` phù hợp ở đây vì request không cần inheritance. Final field ngăn gán lại reference sau khi Jackson tạo object. Đây mới là **shallow immutability**: nếu field trỏ tới một `List` mutable thì phần tử vẫn có thể bị sửa. `@Valid` trên `selection` yêu cầu Bean Validation đi tiếp vào subtype thực tế.

Các subtype có list nên defensive copy trong constructor khi chuyển sang production:

```java
public QuantitySelection(List<QuantityItem> items) {
  this.items = items == null ? null : List.copyOf(items);
}
```

**TARGET — dùng request ở application boundary:**

```java
@RestController
@RequiredArgsConstructor
class OrderController {
  private final OrderCreator orderCreator;

  @PostMapping("/orders")
  ResponseEntity<OrderView> createOrder(
      @AuthenticationPrincipal Jwt jwt,
      @RequestHeader("Idempotency-Key") UUID idempotencyKey,
      @Valid @RequestBody CreateOrderRequest request) {
    OrderView result = orderCreator.create(jwt.getSubject(), idempotencyKey, request);
    return ResponseEntity.status(HttpStatus.CREATED).body(result);
  }
}
```

Không thêm `price`, `subtotal`, `discount`, `state` hoặc `buyerSubject` vào request. Các giá trị đó do Booking lấy từ identity và `EventSalesSnapshot` rồi tự tính.

#### 4.7.2 `record`: message và snapshot bất biến

**CURRENT — generic message envelope:**

```java
public record Envelope<T>(
    @NotNull UUID messageId,
    @NotBlank String messageType,
    @NotNull @Positive Integer schemaVersion,
    @NotNull UUID aggregateId,
    @NotBlank String aggregateVersion,
    @NotNull Instant occurredAt,
    @NotNull UUID correlationId,
    UUID causationId,
    String traceparent,
    @NotNull @Valid T data) {
}
```

`record` phù hợp vì envelope là một value được truyền đi. Sau khi tạo, producer không thể gán lại `messageId`, `schemaVersion` hoặc payload. Generic `T` giữ type cụ thể trong code thay vì buộc mọi consumer nhận `Object`/`Map<String, Object>`.

Record cũng chỉ bất biến nông. `PurchaseSnapshot.items()` hiện trả cùng list được truyền vào constructor. Nếu caller giữ reference và sửa list, snapshot đổi theo. Mẫu production nên copy collection trong compact constructor:

```java
public record SelectionSnapshot(
    int schemaVersion,
    List<SeatSelectionSnapshot> seats) {

  public SelectionSnapshot {
    seats = seats == null ? null : List.copyOf(seats);
  }
}
```

Với `PurchaseSnapshot`, cần copy cả `items`; mỗi nested `Item` cũng copy `seats`. `List.copyOf` đồng thời reject phần tử null, nên phải thống nhất lỗi này với Bean Validation/Jackson error handling.

**TARGET — tạo `StartPayment` rồi lưu cùng transaction qua outbox:**

```java
PurchaseSnapshot purchase = purchaseSnapshotFactory.freeze(order, eventSnapshot, now);
UUID requestId = UUID.randomUUID();

StartPayment payload = new StartPayment(
    requestId,
    requestKey,
    requestHash,
    StartPayment.Provider.VNPAY,
    purchase);

Envelope<StartPayment> envelope = new Envelope<>(
    requestId,
    "payment.start-requested.v1",
    1,
    order.getId(),
    order.getRowVersion().toString(),
    now,
    correlationId,
    causationId,
    traceparent,
    payload);

OutboxMessage row = outboxMessageMapper.toEntity(envelope);
outboxRepository.save(row); // cùng transaction với việc freeze Order
```

Đoạn TARGET trên thể hiện ba điểm:

1. `PurchaseSnapshot` được Booking tạo, không nhận từ client.
2. `Envelope<StartPayment>` giữ compile-time type cho payload.
3. Service lưu `OutboxMessage`; nó không publish RabbitMQ trước khi DB commit.

#### 4.7.3 Snapshot `record` được persist thẳng vào JSONB

**CURRENT — entity giữ một snapshot có type:**

```java
@JdbcTypeCode(SqlTypes.JSON)
@Column(name = "frozen_purchase_snapshot", columnDefinition = "jsonb")
private PurchaseSnapshot frozenPurchaseSnapshot;
```

Hibernate serialize `PurchaseSnapshot` vào PostgreSQL JSONB nhưng code Java vẫn đọc bằng accessor có type:

```java
PurchaseSnapshot frozen = order.getFrozenPurchaseSnapshot();
String amountForPayment = frozen.total();
UUID eventId = frozen.eventId();
List<PurchaseSnapshot.Item> items = frozen.items();
```

**TARGET — freeze đúng một lần:**

```java
if (order.getFrozenPurchaseSnapshot() == null) {
  PurchaseSnapshot frozen = purchaseSnapshotFactory.freeze(order, eventSnapshot, now);
  order.setFrozenPurchaseSnapshot(frozen);
  order.setPaymentFrozenAt(now);
  order.setPaymentSnapshotVersion(order.getRowVersion());
}

PurchaseSnapshot payload = order.getFrozenPurchaseSnapshot();
```

Retry payment phải đọc lại `frozen_purchase_snapshot`; không dựng snapshot mới từ giá Event hiện tại.

#### 4.7.4 `enum`: trạng thái và loại resource

**CURRENT — entity lưu enum bằng tên:**

```java
@Enumerated(EnumType.STRING)
@Column(name = "lifecycle_state", nullable = false)
private OrderState lifecycleState;
```

`EnumType.STRING` lưu `PENDING_PAYMENT` thay vì ordinal `0`. Khi đổi thứ tự enum, dữ liệu cũ vẫn giữ đúng nghĩa.

**TARGET — service kiểm state bằng enum, không so sánh chuỗi:**

```java
private void requirePayable(Order order, Instant now) {
  if (order.getLifecycleState() != OrderState.PENDING_PAYMENT) {
    throw new OrderStateConflict(order.getId(), order.getLifecycleState());
  }
  if (!now.isBefore(order.getExpiresAt())) {
    throw new ReservationExpired(order.getId());
  }
}
```

Không viết `"PENDING_PAYMENT".equals(order.getLifecycleState())`. String chỉ xuất hiện ở wire/DB boundary; domain code dùng enum.

#### 4.7.5 JPA `@Entity`: object có identity và thay đổi trong transaction

`Order` không nên là `record`: Hibernate cần no-arg constructor, entity có identity, collection relation và trạng thái thay đổi theo lifecycle.

**CURRENT — aggregate relation trong cùng owner:**

```java
@Entity
public class Order {
  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  @Version
  private Long rowVersion;

  @OneToMany(mappedBy = "order", fetch = FetchType.LAZY, cascade = CascadeType.PERSIST)
  private List<OrderItem> items = new ArrayList<>();

  public void addItem(OrderItem item) {
    items.add(Objects.requireNonNull(item));
    item.setOrder(this); // giữ hai phía của relation đồng bộ trong memory
  }
}
```

**TARGET — tạo aggregate trong service transaction:**

```java
@Transactional
public Order createAggregate(CreateOrderRequest request, String buyerSubject, Instant now) {
  EventSalesSnapshot sales = salesSnapshotRepository
      .findById(request.getEventId())
      .orElseThrow(EventNotReady::new);

  // Hàm này lookup giá trong SalesConfiguration, lock inventory và trả kết quả đã tính.
  PricedSelection priced = selectionService.priceAndReserve(
      request.getSelection(), sales, buyerSubject, now);

  Order order = new Order();
  order.setEventId(request.getEventId());
  order.setBuyerSubject(buyerSubject);
  order.setCustomerEmail(request.getCustomerEmail());
  order.setSalesMode(priced.salesMode());
  order.setSectorId(priced.sectorId());
  order.setLifecycleState(OrderState.PENDING_PAYMENT);
  order.setSubtotal(priced.subtotal());
  order.setDiscount(BigDecimal.ZERO);
  order.setTotal(priced.subtotal());
  order.setCurrency(priced.currency());
  order.setExpiresAt(priced.expiresAt());
  order.setSourceConfigVersion(sales.getSourceConfigVersion());
  order.setCreatedAt(now);
  order.setUpdatedAt(now);

  for (OrderItem item : priced.items()) {
    order.addItem(item);
  }
  return orderRepository.save(order);
}
```

`PricedSelection` trong mẫu TARGET là internal result của application/domain layer, có thể là một `record`; nó không phải request DTO. Transaction service vẫn chịu trách nhiệm lookup snapshot, reserve inventory, tính tiền và persist. Không biến `OrderMapper` thành factory nghiệp vụ. Flow thật còn phải tạo `Reservation`, `ReservationAllocation`, purchase-limit hold và idempotency row trong cùng transaction.

#### 4.7.6 `@Embeddable`: composite identity

**CURRENT — cùng một UUID request key có thể được dùng cho operation khác, nên ID gồm ba phần:**

```java
@Embeddable
public class IdempotencyRequestId implements Serializable {
  @Column(name = "actor_subject", nullable = false)
  private String actorSubject;

  @Column(name = "operation", nullable = false)
  private String operation;

  @Column(name = "request_key", nullable = false)
  private UUID requestKey;

  // equals/hashCode dùng đủ cả ba field
}

@Entity
public class IdempotencyRequest {
  @EmbeddedId
  private IdempotencyRequestId id;
}
```

**TARGET — dựng đúng key để phát hiện retry:**

```java
IdempotencyRequestId id = new IdempotencyRequestId();
id.setActorSubject(buyerSubject);
id.setOperation("CREATE_ORDER");
id.setRequestKey(idempotencyKey);

Optional<IdempotencyRequest> previous = idempotencyRepository.findById(id);
if (previous.isPresent()) {
  requireSameHash(previous.get().getRequestHash(), requestHash);
  return loadStoredResult(previous.get().getResourceId());
}
```

`equals/hashCode` phải dựa trên toàn bộ composite key; nếu thiếu một field, entity identity trong persistence context và collection có thể sai.

#### 4.7.7 `BigDecimal`, `Instant` và UUID trong cùng use case

**TARGET — tính line subtotal từ giá server-owned:**

```java
BigDecimal unitPrice = new BigDecimal(ticketType.unitPrice())
    .setScale(0, RoundingMode.UNNECESSARY);          // giá từ SalesConfiguration
BigDecimal quantity = BigDecimal.valueOf(requestedQuantity);
BigDecimal lineSubtotal = unitPrice.multiply(quantity);

item.setUnitPrice(unitPrice);
item.setLineSubtotal(lineSubtotal);
order.setExpiresAt(now.plus(reservationTtl));        // now là Instant từ Clock
```

Khi trả response hoặc tạo message, mapper đổi money sang string wire:

```java
String wireTotal = mappingValueConverter.toMoney(order.getTotal());
// BigDecimal("500000") -> "500000"
```

UUID ở đây nhận diện resource; `Instant` nhận diện thời điểm tuyệt đối; `BigDecimal` biểu diễn số tiền chính xác. Không thay ba kiểu này bằng `String` trong domain chỉ vì JSON cuối cùng dùng chuỗi.

## 5. Bốn nhóm DTO và lý do không gom chung

### 5.1 Request DTO

Request diễn tả ý định của caller, ví dụ:

- `CreateOrderRequest`: event, email và selection.
- `ApplyPromotionRequest`: mã promotion.
- `StartPaymentRequest`: provider.

Request không được chứa giá/tổng tiền do client quyết định. Booking lấy ticket type, giá, sector và giới hạn từ snapshot cục bộ đã được xác thực.

### 5.2 Response DTO

Response là view dành cho caller: `OrderView`, `OrderItemView`, `Availability`, `Problem`. Chúng không phải entity và không nên cho phép caller gửi ngược lại để update.

So với `OrderDTO` của ShopApp vừa mang input vừa chứa total/date/items, tách request/response giảm over-posting và tránh client gửi các field server-owned.

### 5.3 Message DTO

Message DTO là contract giữa service, ví dụ `StartPayment`, `PaymentDecision`, `IssuanceResult`. Chúng đi trong `Envelope<T>` có:

- `messageId`: idempotency của message.
- `messageType` và `schemaVersion`: dispatch/compatibility.
- `aggregateId`, `aggregateVersion`: identity và ordering/version.
- `correlationId`, `causationId`, `traceparent`: theo flow và trace.
- `occurredAt`: thời điểm event/command được tạo.
- `data`: payload typed.

ShopApp RabbitMQ DTO chủ yếu đủ cho worker thực hiện tác vụ. FlashTicket envelope còn phải hỗ trợ retry, deduplication, điều tra flow và kiểm message type khớp payload.

### 5.4 Snapshot DTO

Snapshot là dữ liệu được sao chép có chủ đích:

- `SalesConfiguration`: Event → Booking, phục vụ giá và inventory cục bộ.
- `SelectionSnapshot`: ghế được chọn trong order item.
- `PurchaseSnapshot`: order đã freeze trước payment.
- `PromotionConfigurationSnapshot`: rule promotion đã dùng.

Snapshot có thể được nhận qua message, lưu JSONB, đưa vào message khác hoặc trả một phần trong response. Nó không thuộc riêng “request” hay “response”. Client không được tự tạo `PurchaseSnapshot`; Booking phải tạo từ dữ liệu tin cậy.

Snapshot giải quyết hai vấn đề mà quan hệ JPA không giải quyết được:

1. Không JOIN DB owner khác ở runtime.
2. Lịch sử không đổi khi cấu hình nguồn thay đổi.

Đổi lại, snapshot tạo dữ liệu lặp, cần source version và rule chống message cũ ghi đè message mới.

## 6. Case study: vì sao `CreateOrderRequest` dùng `OrderSelection` interface

### 6.1 Ba shape nghiệp vụ khác nhau

Contract nhận một trong ba JSON shape:

```json
{
  "mode": "QUANTITY",
  "items": [{"ticketTypeId": "...", "quantity": 2}]
}
```

```json
{
  "mode": "SEAT_MAP",
  "kind": "STANDING",
  "sectorId": "...",
  "items": [{"ticketTypeId": "...", "quantity": 2}]
}
```

```json
{
  "mode": "SEAT_MAP",
  "kind": "SEATED",
  "sectorId": "...",
  "seatIds": ["...", "..."]
}
```

[OrderSelection.java](../../services/booking-service/src/main/java/com/flashticket/booking_service/dto/request/OrderSelection.java) mô hình hóa chúng bằng sealed hierarchy:

```text
OrderSelection
├── QuantitySelection
└── SeatMapSelection
    ├── StandingSelection
    └── SeatedSelection
```

**CURRENT — hai tầng discriminator bám đúng JSON contract:**

```java
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "mode")
@JsonSubTypes({
  @JsonSubTypes.Type(value = OrderSelection.QuantitySelection.class, name = "QUANTITY"),
  @JsonSubTypes.Type(value = OrderSelection.SeatMapSelection.class, name = "SEAT_MAP")
})
public sealed interface OrderSelection
    permits OrderSelection.QuantitySelection, OrderSelection.SeatMapSelection {

  @Getter
  final class QuantitySelection implements OrderSelection {
    @NotEmpty
    private final List<@NotNull @Valid QuantityItem> items;
  }

  @JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "kind")
  @JsonSubTypes({
    @JsonSubTypes.Type(value = SeatedSelection.class, name = "SEATED"),
    @JsonSubTypes.Type(value = StandingSelection.class, name = "STANDING")
  })
  sealed interface SeatMapSelection extends OrderSelection
      permits SeatedSelection, StandingSelection {}

  @Getter
  final class StandingSelection implements SeatMapSelection {
    @NotNull private final UUID sectorId;
    @NotEmpty private final List<@NotNull @Valid QuantityItem> items;
  }

  @Getter
  final class SeatedSelection implements SeatMapSelection {
    @NotNull private final UUID sectorId;
    @NotEmpty private final List<@NotNull UUID> seatIds;
  }
}
```

Constructor `@JsonCreator` của ba subtype đã được lược khỏi đoạn trích cho ngắn; source thật vẫn cần constructor để Jackson tạo các final field.

### 6.2 Lợi ích so với một DTO có mọi field

Nếu dùng một class phẳng, nó phải chứa `mode`, `kind`, `sectorId`, `items`, `seatIds`. Phần lớn tổ hợp field là vô nghĩa:

- QUANTITY có `seatIds`.
- SEATED có `items` nhưng không có `seatIds`.
- STANDING có `seatIds` thay vì `items`.
- SEAT_MAP không có `sectorId`.

Sealed interface đem lại:

- **Invalid state khó biểu diễn hơn:** subtype chỉ có field nó cần.
- **Deserialize rõ:** Jackson chọn subtype bằng `mode`, sau đó `kind`.
- **Dispatch rõ:** service xử lý theo subtype thay vì một chuỗi `if (mode) if (kind) if (field != null)`.
- **Tập subtype đóng:** code ngoài không tự thêm loại selection không được contract cho phép.
- **Validation cục bộ:** unique `ticketTypeId` hoặc `seatId` nằm gần shape tương ứng.

### 6.3 Nguồn nghiệp vụ và DB

Đây không phải kỹ thuật Java được đặt ra trước nghiệp vụ. Ba subtype phản ánh ba loại resource Booking phải giữ:

| Selection | Nguồn cung bị giữ | Bảng chính | `reservation_allocations.resource_kind` |
|---|---|---|---|
| QUANTITY | Pool riêng của ticket type | `ticket_type_inventory` | `TICKET_TYPE` |
| SEAT_MAP/STANDING | Pool chung của sector | `sector_inventory` | `STANDING_SECTOR` |
| SEAT_MAP/SEATED | Từng ghế cụ thể | `seat_inventory` | `SEAT` |

**TARGET — sealed-type dispatch trong Booking service:**

```java
@Transactional
void reserveSelection(OrderSelection selection, Reservation reservation) {
  switch (selection) {
    case OrderSelection.QuantitySelection quantity -> {
      for (OrderSelection.QuantityItem item : quantity.getItems()) {
        reserveTicketType(
            reservation,
            item.getTicketTypeId(),
            item.getQuantity()); // ticket_type_inventory, ResourceKind.TICKET_TYPE
      }
    }
    case OrderSelection.StandingSelection standing -> {
      int totalQuantity = standing.getItems().stream()
          .mapToInt(OrderSelection.QuantityItem::getQuantity)
          .sum();
      reserveStandingSector(
          reservation,
          standing.getSectorId(),
          standing.getItems(),
          totalQuantity); // sector_inventory, ResourceKind.STANDING_SECTOR
    }
    case OrderSelection.SeatedSelection seated -> {
      for (UUID seatId : seated.getSeatIds()) {
        reserveSeat(
            reservation,
            seated.getSectorId(),
            seatId); // seat_inventory, ResourceKind.SEAT
      }
    }
  }
}
```

Vì `OrderSelection` là sealed hierarchy, compiler biết ba case trên là đầy đủ; không cần `default`. Nếu sau này thêm subtype, switch này buộc phải được xem lại khi compile.

Dispatch đúng subtype mới chỉ chọn chiến lược. Repository vẫn phải khóa/cập nhật nguồn cung nguyên tử. Ví dụ đại diện cho ghế:

```java
public interface SeatInventoryRepository extends JpaRepository<SeatInventory, UUID> {
  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("""
      select seat
      from SeatInventory seat
      where seat.seatId = :seatId
      """)
  Optional<SeatInventory> findForUpdate(UUID seatId);
}

private void reserveSeat(
    Reservation reservation,
    UUID expectedSectorId,
    UUID seatId) {
  UUID reservationId = Objects.requireNonNull(
      reservation.getId(), "reservation must be persisted before allocating seats");
  SeatInventory seat = seatInventoryRepository.findForUpdate(seatId)
      .orElseThrow(SeatNotFound::new);

  if (!seat.getSectorId().equals(expectedSectorId)
      || seat.getIsHidden()
      || seat.getState() != SeatState.AVAILABLE) {
    throw new SeatUnavailable(seatId);
  }

  seat.setState(SeatState.HELD);
  seat.setCurrentReservationId(reservationId);
  seat.setReservationGeneration(seat.getReservationGeneration() + 1);
}
```

Đây là mẫu TARGET; khi triển khai thật phải kiểm thêm cùng `eventId`, source config version, expiry và tạo `ReservationAllocation`. Ý chính là check và đổi trạng thái diễn ra khi row đã bị khóa trong cùng transaction, không check availability ở controller rồi update sau.

Các bảng liên quan:

- `orders`: lưu `sales_mode`, `sector_id`, tổng tiền, deadline và frozen snapshot.
- `order_items`: mỗi ticket type, quantity, unit price, line subtotal và `selection_snapshot`.
- `order_item_seats`: quan hệ order item–seat được chọn.
- `reservations`: một lần giữ nguồn cung của order.
- `reservation_allocations`: ledger nói reservation đang giữ resource nào.
- ba bảng inventory: nơi tranh chấp thực sự được giải quyết.

[B13 § CreateOrder](../architecture/B13-api-and-event-contracts.md) và [B14 main flow](../architecture/B14-main-flow-sequences.md) quy định:

- QUANTITY trừ pool ticket type.
- STANDING trừ một pool sector dù có nhiều ticket type.
- SEATED giữ từng seat, không trừ thêm counter sector.
- toàn bộ SEAT_MAP order thuộc đúng một sector.

### 6.4 Interface bảo vệ gì và không bảo vệ gì

`OrderSelection` có thể bảo vệ shape:

- mảng không rỗng;
- quantity dương;
- ID không trùng;
- trường bắt buộc tồn tại.

Nó không thể tự bảo vệ rule cần external/current state:

- event đang mở bán;
- ticket type thuộc event/sector;
- seat thuộc sector, không hidden và còn AVAILABLE;
- giá lấy từ đúng source config version;
- buyer chưa vượt purchase limit;
- promotion còn lượt;
- hai request đồng thời không bán cùng một ghế.

Các rule này phải nằm trong service/domain transaction và được DB hỗ trợ bằng row lock, conditional update, unique/FK/CHECK và rollback.

### 6.5 Đánh giá implementation hiện tại

**Điểm tốt:** sealed hierarchy bám đúng union type của B13 và loại bỏ nhiều tổ hợp null.

**Điểm cần kiểm:** nested Jackson discriminator (`mode=SEAT_MAP`, tiếp tục `kind`) phải có integration test deserialize cho đủ ba shape, unknown `mode/kind`, missing field và extra field. Static review chưa chứng minh Jackson thực sự resolve interface trung gian đúng ở Spring Boot/Jackson version hiện tại.

**Khuyến nghị:** giữ sealed interface. Không tách thành ba endpoint vì cả ba đều là cùng use case “tạo order”; khác nhau ở selection strategy. Khi service được viết, dùng handler/strategy nội bộ theo subtype nếu method bắt đầu quá lớn.

## 7. MapStruct: cùng thư viện nhưng cách dùng đã đổi

### 7.1 ShopApp-BE đang dùng thế nào

ShopApp đã dùng MapStruct 1.5.5. Các mapper thường:

- `componentModel = "spring"`;
- `uses = {...}` cho nested entity;
- `builder = disableBuilder` để làm việc với Lombok DTO/entity;
- map hai chiều DTO ↔ entity;
- ignore các field server quản lý như status/audit/relations.

MapStruct mặc định thường chỉ warning khi target còn field chưa map. Mapper hai chiều từ request DTO sang entity làm code ngắn, nhưng dễ khiến DTO có thêm field rồi vô tình ghi vào entity nếu review không kỹ.

### 7.2 Booking scaffold đang cấu hình thế nào

[OrderMapper.java](../../services/booking-service/src/main/java/com/flashticket/booking_service/mapper/OrderMapper.java) và [OutboxMessageMapper.java](../../services/booking-service/src/main/java/com/flashticket/booking_service/mapper/OutboxMessageMapper.java) dùng:

```java
@Mapper(
    componentModel = MappingConstants.ComponentModel.SPRING,
    unmappedTargetPolicy = ReportingPolicy.ERROR,
    uses = MappingValueConverter.class)
```

`OrderMapper` hiện map cụ thể như sau:

```java
public interface OrderMapper {
  @Mapping(target = "orderId", source = "id")
  @Mapping(target = "state", source = "lifecycleState")
  @Mapping(target = "items", source = "items")
  OrderView toView(Order order);

  @Mapping(target = "orderItemId", source = "id")
  @Mapping(target = "seats", source = "selectionSnapshot")
  OrderItemView toItemView(OrderItem item);
}
```

`OutboxMessageMapper` dùng `abstract class` vì ngoài generated mapping nó còn có method viết tay để validate và deserialize payload:

```java
public abstract class OutboxMessageMapper {
  @Autowired protected MappingValueConverter converter;
  @Autowired protected Validator validator;

  protected abstract OutboxMessage mapToEntity(Envelope<?> envelope);

  public OutboxMessage toEntity(Envelope<?> envelope) {
    validateEnvelope(envelope);
    return mapToEntity(envelope);
  }

  public <T> Envelope<T> toEnvelope(OutboxMessage row, Class<T> payloadType) {
    T data = converter.fromTree(row.getPayload(), payloadType);
    Envelope<T> result = new Envelope<>(
        row.getMessageId(),
        row.getMessageType(),
        row.getSchemaVersion(),
        row.getAggregateId(),
        row.getAggregateVersion().toString(),
        row.getOccurredAt(),
        row.getCorrelationId(),
        row.getCausationId(),
        row.getTraceparent(),
        data);
    validateEnvelope(result);
    return result;
  }
}
```

Interface phù hợp với mapper thuần như `OrderMapper`. Abstract class cho phép thêm method/state viết tay, nhưng implementation hiện tại vì thế đã ôm cả validation và codec; phần 7.5 đề xuất tách các trách nhiệm này.

**TARGET — gọi mapper sau khi repository đã fetch aggregate cần thiết:**

```java
public interface OrderRepository extends JpaRepository<Order, UUID> {
  @Override
  @EntityGraph(attributePaths = "items")
  Optional<Order> findById(UUID orderId);
}

@Transactional(readOnly = true)
public OrderView getOrder(UUID orderId, String buyerSubject) {
  Order order = orderRepository.findById(orderId)
      .orElseThrow(OrderNotFound::new);
  if (!order.getBuyerSubject().equals(buyerSubject)) {
    throw new OrderNotFound(); // không làm lộ order của buyer khác
  }
  return orderMapper.toView(order);
}
```

MapStruct chỉ chạy phép đổi `Order` → `OrderView`. Repository chịu trách nhiệm fetch `items`; service chịu trách nhiệm ownership. Nếu gọi mapper trên `Order` detached mà `items` chưa được load, mapper không thể tự sửa `LazyInitializationException`.

Khác biệt có chủ đích:

- **Fail compile nếu target mới chưa map:** tránh response/entity âm thầm thiếu field.
- **Converter typed:** money, version, snapshot seat và JSON tree có conversion rõ.
- **Không map CreateOrderRequest thẳng thành Order:** order cần lookup config, tính giá, giữ inventory và transaction; đó là business operation.
- **Entity → View:** phù hợp với read mapping sau khi service đã load đúng aggregate/fetch plan.
- **Envelope ↔ Outbox row:** convert typed payload sang JSONB và ngược lại.

### 7.3 MapStruct nên làm gì

MapStruct phù hợp với phép biến đổi cấu trúc xác định:

- đổi tên `id` → `orderId`;
- enum cùng nghĩa;
- `BigDecimal` → money string đã kiểm range;
- `SelectionSnapshot` → list seat trong response;
- DTO typed → JSON tree sau khi DTO hợp lệ.

MapStruct không nên:

- tính giá/discount/total;
- kiểm ghế còn trống;
- đổi state order;
- tự tạo source version;
- gọi repository/API;
- quyết định event/message nào phải phát.

Mapper phải là deterministic transformation. Business service/aggregate quyết định dữ liệu đầu vào đã hợp lệ và trạng thái nào được phép.

### 7.4 Đánh giá cấu hình hiện tại

Điểm tốt:

- `unmappedTargetPolicy = ERROR` giúp schema evolution an toàn hơn.
- Mapping request → entity không được dùng cho nghiệp vụ tạo order.
- `MappingValueConverter` tập trung conversion có rủi ro mất dữ liệu.

Điểm cần cải thiện:

1. Annotation cấu hình bị lặp giữa mapper; chưa có `@MapperConfig` chung.
2. `OutboxMessageMapper` là abstract class dùng field injection `@Autowired`; dependency khó thấy hơn constructor injection.
3. `expression = "java(...)"` ít refactor-safe hơn method conversion có type/qualifier.
4. Mapper outbox đang vừa validate, vừa dispatch payload type, vừa serialize; trách nhiệm rộng hơn mapper thuần.
5. `OrderMapper.toView` truy cập collection lazy; MapStruct không giải quyết N+1 hoặc `LazyInitializationException`. Repository/service phải fetch đúng dữ liệu trong transaction/read model.

### 7.5 Cấu trúc production khuyến nghị

Tạo một `@MapperConfig`, ví dụ:

```java
@MapperConfig(
    componentModel = MappingConstants.ComponentModel.SPRING,
    injectionStrategy = InjectionStrategy.CONSTRUCTOR,
    unmappedTargetPolicy = ReportingPolicy.ERROR)
interface BookingMapperConfig {}
```

Mapper dùng config chung:

```java
@Mapper(
    config = BookingMapperConfig.class,
    uses = MappingValueConverter.class)
public interface OrderMapper {
  @Mapping(target = "orderId", source = "id")
  @Mapping(target = "state", source = "lifecycleState")
  OrderView toView(Order order);
}
```

Khi thêm field mới vào `OrderView`, `ReportingPolicy.ERROR` từ config chung làm build fail cho đến khi mapper xử lý field đó. `InjectionStrategy.CONSTRUCTOR` chỉ có tác dụng với dependency MapStruct inject vào generated mapper; codec/validator viết tay vẫn phải tự dùng constructor injection.

Sau đó:

- mapper thuần chỉ map field;
- `EnvelopeValidator` kiểm contract;
- `OutboxEnvelopeCodec` serialize/deserialize payload và tra registry message type;
- service/assembler tạo snapshot/business output;
- repository quyết định fetch plan.

Không cần tạo abstraction chung toàn platform ngay. Chỉ chia sẻ contract artifact hoặc mapper config khi có ít nhất hai service thực sự dùng cùng source/versioning policy và đã chốt ownership.

## 8. Validation: tại sao có `ContractRules` static

### 8.1 Năm tầng validation

Một request/message mới đi qua các tầng sau:

| Tầng | Ví dụ | Nơi phù hợp |
|---|---|---|
| JSON shape | field lạ, thiếu constructor property, string bị ép thành integer | Jackson config |
| Field constraint | null, blank, email, positive, regex | Jakarta Bean Validation annotation |
| Cross-field contract | subtotal − discount = total; ID duy nhất; type khớp payload | class-level validator/pure contract rules |
| Stateful business rule | ghế còn trống; order thuộc buyer; deadline; promotion limit | service/domain transaction |
| Storage invariant | unique seat/order, FK cùng event, counter không âm | PostgreSQL constraint/index |

Một tầng không thay thế tầng khác. DB không thể trả error UX tốt cho mọi case; annotation không thể biết seat vừa bị request khác giữ.

### 8.2 Vì sao scaffold dùng static class

[ContractRules.java](../../services/booking-service/src/main/java/com/flashticket/booking_service/validation/ContractRules.java) là pure static utility vì các rule hiện tại:

- không cần repository, clock hay Spring bean;
- cần gọi từ nhiều biên: DTO `@AssertTrue`, envelope, mapper/codec;
- cần kiểm snapshot/message trước khi business service tin payload;
- dễ gọi trực tiếp khi serialize/deserialize, không phụ thuộc controller tự chạy `@Valid`.

Ví dụ `validPurchaseSnapshot` kiểm tổng nhiều item, unique seat, version, thời gian freeze và quan hệ subtotal/discount/total. Đặt toàn bộ vào service sẽ khiến payload không hợp lệ đi sâu vào business flow và làm cùng rule bị lặp ở HTTP consumer, Rabbit consumer và outbox replay.

### 8.3 Vì sao chưa dùng custom annotation

Một custom constraint hoàn chỉnh cần:

- annotation `@ValidPurchaseSnapshot`;
- `ConstraintValidator`;
- message/error code và field path;
- wiring/test cho null/cascade/group;
- đôi khi cần nhiều annotation cho SalesConfiguration, Envelope, selection và message result.

Ở scaffold, `@AssertTrue isValidSnapshot()` gọi pure static rule là cách ngắn để Bean Validation kích hoạt cross-field check. Nó phù hợp để chứng minh contract shape trước khi có handler.

Nhược điểm:

- violation thường mang property `validSnapshot` thay vì field cụ thể;
- error code/message kém giàu thông tin;
- DTO phụ thuộc trực tiếp vào `ContractRules`;
- một boolean dài khó biết điều kiện nào fail;
- rule message registry và rule số học bị gom vào một class lớn.

### 8.4 Có nên chuyển hết validation vào service không?

Không. Service phù hợp với rule cần state:

- query snapshot/inventory;
- authorization theo JWT subject;
- lock/update counter;
- deadline dựa trên clock;
- transition state;
- kiểm cùng event/sector từ DB.

Service không phải nơi tốt cho rule wire thuần như regex money, schemaVersion, messageType ↔ payload class hay tổng số trong snapshot. Các rule này cần chạy trước service và chạy lại khi đọc outbox/inbox.

### 8.5 Cấu trúc production khuyến nghị

Giữ các hàm thuần nhưng không để DTO gọi một static class khổng lồ trực tiếp:

1. Tách `MoneyWireRules`, `SnapshotRules`, `EnvelopeTypeRegistry` theo trách nhiệm.
2. Tạo class-level constraint cho contract lớn: `@ValidPurchaseSnapshot`, `@ValidSalesConfiguration`, `@ValidEnvelope`.
3. `ConstraintValidator` delegate vào pure rules để mapper/codec vẫn gọi lại được khi cần.
4. Trả violation có code/path cụ thể, ví dụ `items[1].lineSubtotal` hoặc `messageType`.
5. Rule stateful giữ ở application/domain service và chạy trong transaction.
6. DB giữ invariant cuối cùng, đặc biệt dưới concurrency.

Cách này không chọn một trong “annotation hoặc static hoặc service”; nó dùng mỗi cơ chế đúng tầng.

### 8.6 Code sample cho từng tầng validation

#### Tầng 1–2: JSON shape và field constraint

**CURRENT:**

```java
public final class CreateOrderRequest {
  @NotNull private final UUID eventId;
  @NotBlank @Email @Size(max = 254)
  private final String customerEmail;
  @NotNull @Valid private final OrderSelection selection;
}
```

`BookingJsonConfig` reject field lạ/coercion sai trước; Bean Validation sau đó kiểm null, email, size và cascade vào `selection`. Không cần viết lại các check này trong service.

#### Tầng 3 hiện tại: cross-field rule thuần

**CURRENT — record gọi pure rule qua `@AssertTrue`:**

```java
public record PurchaseSnapshot(/* fields */) {
  @AssertTrue
  @JsonIgnore
  public boolean isValidSnapshot() {
    return ContractRules.validPurchaseSnapshot(this);
  }
}
```

Pure rule có thể được gọi ngoài MVC, ví dụ khi đọc JSONB/outbox:

```java
PurchaseSnapshot snapshot = converter.fromTree(payload, PurchaseSnapshot.class);
if (!ContractRules.validPurchaseSnapshot(snapshot)) {
  throw new IllegalArgumentException("Invalid purchase snapshot");
}
```

Đây là lý do rule thuần không nên bị nhốt trong controller hoặc service method.

#### Tầng 3 đề xuất: custom class-level annotation delegate pure rule

**TARGET — annotation:**

```java
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = PurchaseSnapshotValidator.class)
public @interface ValidPurchaseSnapshot {
  String message() default "{booking.purchaseSnapshot.invalid}";
  Class<?>[] groups() default {};
  Class<? extends Payload>[] payload() default {};
}
```

**TARGET — validator vẫn tái sử dụng rule thuần:**

```java
public final class PurchaseSnapshotValidator
    implements ConstraintValidator<ValidPurchaseSnapshot, PurchaseSnapshot> {

  @Override
  public boolean isValid(PurchaseSnapshot value, ConstraintValidatorContext context) {
    return value == null || ContractRules.validPurchaseSnapshot(value);
  }
}
```

DTO khi đó chỉ khai báo:

```java
@ValidPurchaseSnapshot
public record PurchaseSnapshot(/* fields */) {}
```

Custom annotation làm tên invariant rõ hơn và bỏ method `isValidSnapshot` khỏi DTO. Tuy nhiên nếu `ContractRules` chỉ trả boolean thì violation vẫn còn chung. Muốn trả đúng path như `items[1].lineSubtotal`, bước tiếp theo là cho pure validator trả danh sách lỗi có `code` và `path`, rồi `ConstraintValidator` chuyển chúng thành Bean Validation violations.

#### Tầng 4: stateful validation trong service transaction

**TARGET:**

```java
private EventSalesSnapshot requireOpenSale(UUID eventId, Instant now) {
  EventSalesSnapshot event = salesSnapshotRepository.findById(eventId)
      .orElseThrow(EventNotReady::new);

  if (event.getEventStatus() != EventState.PUBLISHED
      || now.isBefore(event.getSaleStartsAt())
      || !now.isBefore(event.getSaleEndsAt())) {
    throw new SaleNotOpen(eventId);
  }
  return event;
}

@Transactional
public Order create(/* request context */) {
  Instant now = clock.instant();
  EventSalesSnapshot event = requireOpenSale(request.getEventId(), now);

  // Sau đó lock inventory, kiểm event/sector/source version,
  // tăng held và persist order/reservation/outbox trong cùng transaction.
}
```

`Clock` và repository là dependency của service vì kết quả phụ thuộc thời gian và DB hiện tại. Đưa rule này vào annotation sẽ làm validator gọi DB ngoài transaction nghiệp vụ hoặc tạo query ẩn khi deserialize.

#### Tầng 5: DB invariant dưới concurrency

**CURRENT — B12 DDL:**

```sql
CREATE TABLE ticket_type_inventory (
  configured_capacity bigint NOT NULL CHECK (configured_capacity >= 0),
  held bigint NOT NULL DEFAULT 0 CHECK (held >= 0),
  purchased bigint NOT NULL DEFAULT 0 CHECK (purchased >= 0),
  CHECK (
    purchased <= configured_capacity
    AND held <= configured_capacity - purchased
  )
);
```

Ngay cả khi service check sai hoặc hai transaction cạnh tranh, DB vẫn từ chối trạng thái `held + purchased > capacity`. Service phải map lỗi constraint/optimistic lock thành conflict phù hợp; không bỏ constraint chỉ vì Java đã validate.

## 9. Strict JSON và polymorphism

[BookingJsonConfig.java](../../services/booking-service/src/main/java/com/flashticket/booking_service/config/BookingJsonConfig.java) bật:

- fail khi có unknown property;
- fail khi thiếu creator property;
- không ép integer/float/boolean thành text;
- không ép string/float thành integer;
- không ép integer thành enum.

**CURRENT — cấu hình ở đúng boundary thay vì tự parse trong DTO/service:**

```java
@Configuration(proxyBeanMethods = false)
public class BookingJsonConfig {
  @Bean
  JsonMapperBuilderCustomizer bookingJsonMapperCustomizer() {
    return builder -> builder
        .enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
        .enable(DeserializationFeature.FAIL_ON_MISSING_CREATOR_PROPERTIES)
        .withCoercionConfig(
            LogicalType.Integer,
            config -> config
                .setCoercion(CoercionInputShape.String, CoercionAction.Fail)
                .setCoercion(CoercionInputShape.Float, CoercionAction.Fail))
        .withCoercionConfig(
            LogicalType.Enum,
            config -> config.setCoercion(
                CoercionInputShape.Integer,
                CoercionAction.Fail));
  }
}
```

Ví dụ, `quantity: "2"` bị reject thay vì âm thầm biến thành integer `2`; `state: 0` không được ép thành phần tử enum đầu tiên.

ShopApp thường dựa vào Jackson default nên payload dư field hoặc coercion có thể lọt qua. Với event versioned, “cố hiểu” payload sai nguy hiểm hơn reject sớm vì consumer có thể persist một snapshot không đúng nghĩa.

Strict JSON kết hợp với sealed types tạo contract rõ, nhưng cũng làm backward compatibility thành trách nhiệm thật. Khi thêm field bắt buộc hoặc subtype, phải tăng version/triển khai producer–consumer theo kế hoạch; không chỉ sửa một DTO trong monolith.

## 10. Entity và quan hệ JPA

### 10.1 Quan hệ nội bộ owner vẫn dùng JPA

FlashTicket không bỏ `@OneToMany`/`@ManyToOne`. Booking dùng chúng khi hai bảng cùng ownership và cùng transaction:

- `Order` → `OrderItem`.
- `OrderItem` → `OrderItemSeat`.
- `Reservation` → `ReservationAllocation`.
- `PromotionUsage` → `PromotionInventory`/`Reservation`.

Cascade được dùng hẹp (`PERSIST`) thay vì `ALL` rộng như nhiều entity ShopApp. Xóa order không nên tự động xóa lịch sử payment/message/audit bằng một cascade ngoài ý muốn.

### 10.2 Liên kết ngoài owner chỉ là ID/snapshot

Booking không có `@ManyToOne Event` hay `@ManyToOne User`:

- `eventId` là UUID mềm.
- `buyerSubject` là subject từ identity provider.
- thông tin Event cần cho booking nằm trong `EventSalesSnapshot`.

Lý do: Event/User nằm ở service và datastore khác. JPA association qua service boundary tạo coupling, không có transaction thật và khuyến khích JOIN trái ownership.

### 10.3 Composite ID

`@EmbeddedId` xuất hiện khi identity là một tuple nghiệp vụ:

- idempotency: actor + operation + request key;
- inbox: consumer + message ID;
- purchase limit: event + buyer;
- order item seat: order item + seat.

Các ID class phải có `equals/hashCode` ổn định. Đây là nơi `record` nghe có vẻ hấp dẫn, nhưng JPA compatibility/proxy/no-arg constructor cần được kiểm với provider/version; scaffold chọn class embeddable truyền thống.

### 10.4 Optimistic locking và counter

`@Version` xuất hiện ở order/inventory/reservation/promotion usage. Nó phát hiện lost update khi hai transaction cùng đọc một version rồi cùng ghi.

Tuy nhiên `@Version` không tự giải quyết toàn bộ oversell. Service cần một trong các chiến lược có bằng chứng:

- pessimistic row lock;
- conditional `UPDATE ... WHERE held + purchased + requested <= capacity`;
- optimistic retry có giới hạn;
- unique index cho seat/reservation.

DDL đã có counter CHECK, FK và unique index. Handler/repository chưa được viết nên chưa thể tuyên bố concurrency safe.

## 11. Transaction, idempotency, inbox và outbox

### 11.1 ShopApp

ShopApp có `@Transactional` quanh service và có thể save order, detail, product trong cùng MySQL transaction. Rabbit producer thường được gọi từ service sau/thành phần của flow, nhưng DB commit và broker publish không tạo một transaction nguyên tử chung.

### 11.2 FlashTicket

Mỗi service chỉ có local transaction. Pattern đích:

```text
HTTP/message vào
  → kiểm idempotency/inbox
  → đổi aggregate + inventory
  → insert outbox
  → commit một DB transaction
  → relay publish RabbitMQ
  → publisher confirm
```

- `idempotency_requests`: retry cùng actor/operation/key trả lại kết quả đã lưu; cùng key khác payload phải bị từ chối bằng request hash.
- `inbox_messages`: consumer deduplicate `(consumerName, messageId)` và lưu payload hash/kết quả.
- `outbox_messages`: business change và ý định publish commit cùng DB transaction.
- relay có thể publish lặp nếu crash sau broker confirm trước khi ghi `published_at`; consumer phải idempotent.

Đây là at-least-once processing có kiểm soát, không phải exactly-once toàn hệ thống.

**Trạng thái hiện tại:** entity/DDL/mapper contract đã có; relay, listener, ACK-after-commit và retry scheduler chưa được triển khai.

## 12. Snapshot, version và freeze tiền

`PurchaseSnapshot` là thay đổi quan trọng so với `OrderDTO` cũ. Khi bắt đầu payment, Booking phải đóng băng:

- buyer/order/event identity;
- event display data;
- từng item, giá, quantity và seat;
- subtotal, discount, total, currency;
- source config version và source order version;
- frozen time và expiry.

Lần retry không đọc giá Event hiện tại rồi tính lại. Payment/Ticket nhận đúng snapshot đã freeze. Điều này bảo vệ buyer khỏi giá đổi giữa retry và giúp callback Payment không phụ thuộc Event đang online.

`sourceConfigVersion` trả lời “snapshot Event nào đã được dùng”. `rowVersion` trả lời “row hiện tại đã bị transaction khác đổi chưa”. `schemaVersion` trả lời “shape JSON này theo contract version nào”. Ba version này không thay thế nhau.

## 13. Error model và correlation

ShopApp thường trả message hoặc exception handler dựa trên `MessageKeys`. Booking có `Problem` với:

- machine-readable `code`;
- human-readable `message`;
- `correlationId`;
- danh sách violation gồm field và code.

Correlation ID phải đi qua Gateway, log, HTTP nội bộ và message envelope. Nó dùng để tìm toàn bộ flow; nó không phải idempotency key và không thay `messageId`.

## 14. Những lựa chọn đang tốt và nên giữ

1. Tách request/response/message/snapshot theo boundary.
2. Dùng `Instant`, UUID, enum và money chính xác.
3. Không map request tạo order thẳng sang entity.
4. `unmappedTargetPolicy = ERROR` cho mapper.
5. Strict JSON ở contract boundary.
6. Sealed `OrderSelection` khớp ba inventory strategy.
7. Snapshot + source version thay vì JOIN/call owner mỗi lần.
8. JPA relation chỉ trong owner; external reference bằng ID/snapshot.
9. DDL có CHECK/FK/unique/index, không chỉ trông vào Java validation.
10. Outbox/inbox/idempotency được đưa vào schema từ đầu.
11. Keycloak/resource server thay vì mỗi service tự phát token.
12. `open-in-view: false`, buộc fetch/mapping được thiết kế rõ.

## 15. Những điểm nên refactor hoặc xác minh trước production

### Ưu tiên cao

1. **Triển khai transaction/concurrency thực:** repository/service/locking/conditional update; entity và `@Version` chưa đủ chứng minh chống oversell.
2. **Đồng bộ Rabbit contract:** handoff ghi exchange/routing key hiện tại còn lệch B13; chốt trước khi viết relay/consumer.
3. **Test polymorphic JSON:** đủ ba `OrderSelection`, discount subtype, unknown/missing/extra field.
4. **Tách validation layer:** custom class-level constraint delegate pure rules; stateful rule ở service; DB giữ invariant cuối.
5. **Outbox/inbox runtime:** ACK sau commit, publisher confirm, duplicate và same-ID-different-hash behavior.
6. **Chốt table naming/migration:** bỏ phụ thuộc mơ hồ giữa canonical table và local `tbl_` transform.
7. **Chọn JDK 21 đúng:** không dùng skip Enforcer như cách chạy lâu dài.

### Ưu tiên vừa

8. Tạo `@MapperConfig`, constructor injection và codec riêng cho envelope/outbox.
9. Đổi tên `UInt64` thành kiểu khớp signed `BIGINT`, hoặc thực sự hỗ trợ unsigned.
10. Làm rõ duplication `EventStatus` wire và `EventState` entity bằng explicit mapper/test.
11. Thiết kế fetch plan/projection để `OrderMapper` không kích hoạt N+1.
12. Tạo parent POM/BOM nội bộ để các service không drift.
13. Xác định error code/path cụ thể thay cho boolean `@AssertTrue` chung.

### Không nên làm

14. Không đưa repository vào MapStruct converter.
15. Không cho client gửi `PurchaseSnapshot` hoặc total tin cậy.
16. Không tạo JPA association sang Event/User/Payment/Ticket DB.
17. Không dùng Redis làm nguồn sự thật của order/inventory nếu B12 không giao ownership đó.
18. Không coi Rabbit publisher confirm là consumer đã xử lý.
19. Không coi Gateway authentication là đủ cho ownership check trong service.
20. Không coi scaffold compile/start được là flow payment/ticket đã đúng.

## 16. Cách đọc một flow mới trong FlashTicket

Khi gặp một endpoint/message, đọc theo thứ tự này:

1. **Wire contract:** request/message DTO, strict JSON, field/version.
2. **Structural validation:** annotation và class-level/pure rules.
3. **Identity/security:** JWT subject, role, internal credential, ownership.
4. **Local data:** entity/table nào service này sở hữu; external ID/snapshot nào được đọc.
5. **Transaction:** row nào lock/update; invariant nào phải cùng commit.
6. **Concurrency:** version, conditional update, unique/check constraint.
7. **Side effect:** outbox row nào được tạo; lúc nào HTTP trả response.
8. **Consumer:** inbox/dedup/hash, commit rồi mới ACK.
9. **Retry/failure:** timeout, duplicate, stale version, out-of-order message.
10. **Observability:** correlation/causation/traceparent và error code.

Nếu chỉ đọc Controller → Service → Repository như ShopApp, sẽ bỏ sót một nửa tính đúng của flow phân tán.

## 17. Bản đồ source nên đọc

### ShopApp-BE

- `Model/Entity/AbstractEntity.java`: `Long` ID và `LocalDateTime` audit.
- `Model/Entity/OrderEntity.java`: order mutable, status string, money `Double`, JPA relation trực tiếp.
- `DTO/OrderDTO.java`: input DTO chứa cả dữ liệu do client gửi và dữ liệu giống server state.
- `Mapper/OrderMapper.java`: MapStruct hai chiều DTO/entity/response.
- `Service/Impl/OrderServiceImpl.java`: transaction/service validation và cập nhật order/product.
- `Config/RabbitMQConfig.java`: queue/exchange cho task nội bộ.
- `Filter/JwtTokenFilter.java`: ứng dụng tự xử lý JWT.

### FlashTicket

- [Booking pom.xml](../../services/booking-service/pom.xml): version/dependency/build gate.
- [Booking config](../../infrastructure/config-server/src/main/resources/config/booking-service.yaml): datasource, Rabbit, JWT.
- [BookingJsonConfig](../../services/booking-service/src/main/java/com/flashticket/booking_service/config/BookingJsonConfig.java): strict JSON.
- [OrderSelection](../../services/booking-service/src/main/java/com/flashticket/booking_service/dto/request/OrderSelection.java): sealed polymorphic request.
- [SalesConfiguration](../../services/booking-service/src/main/java/com/flashticket/booking_service/dto/snapshot/SalesConfiguration.java): full Event sales snapshot.
- [PurchaseSnapshot](../../services/booking-service/src/main/java/com/flashticket/booking_service/dto/snapshot/PurchaseSnapshot.java): frozen payment input.
- [Envelope](../../services/booking-service/src/main/java/com/flashticket/booking_service/dto/message/Envelope.java): versioned message metadata.
- [ContractRules](../../services/booking-service/src/main/java/com/flashticket/booking_service/validation/ContractRules.java): pure cross-field rules hiện tại.
- [OrderMapper](../../services/booking-service/src/main/java/com/flashticket/booking_service/mapper/OrderMapper.java): entity → response.
- [OutboxMessageMapper](../../services/booking-service/src/main/java/com/flashticket/booking_service/mapper/OutboxMessageMapper.java): typed envelope ↔ outbox JSONB.
- [Booking entities](../../services/booking-service/src/main/java/com/flashticket/booking_service/entity/): internal relation, snapshot JSONB, embedded ID, optimistic version.
- [Booking DDL](../architecture/data/postgres/20-booking.sql): invariant cuối cùng và inventory model.
- [B12](../architecture/B12-data-ownership-and-schema.md): ownership và lý do schema.
- [B13](../architecture/B13-api-and-event-contracts.md): wire contract/version/hash/idempotency.
- [B14](../architecture/B14-main-flow-sequences.md): transaction order và failure branch.

## 18. Kết luận thực dụng

ShopApp-BE tối ưu cho một JVM và một DB: entity graph, transaction cục bộ và DTO đơn giản đủ giải quyết phần lớn flow. FlashTicket phải làm rõ những thứ monolith có thể ngầm giả định: ai sở hữu dữ liệu, payload nào được tin, giá nào đã đóng băng, retry nào là cùng request, message nào đã xử lý, và điều gì xảy ra khi service khác chậm hoặc gửi trùng.

Các kiểu mới không nên được học như syntax rời rạc:

- `record`/final DTO bảo vệ tính bất biến của contract.
- sealed interface biểu diễn tập biến thể nghiệp vụ đóng.
- snapshot thay thế read/join xuyên owner và giữ lịch sử quyết định.
- UUID/correlation/version phục vụ identity và truy vết phân tán.
- outbox/inbox/idempotency phục vụ failure/retry thực tế.
- enum/CHECK/validation/service mỗi lớp bảo vệ một loại invariant khác nhau.
- MapStruct chỉ chuyển hình dạng dữ liệu; nó không thay domain service.

Mốc “hiểu code mới” không phải là nhớ hết annotation. Đó là khả năng nhìn một field/type và trả lời được bốn câu: nó thuộc boundary nào, owner nào quyết định, invariant nào bảo vệ nó, và retry/concurrency làm nó thay đổi ra sao.
