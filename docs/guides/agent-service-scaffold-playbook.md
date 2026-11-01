# Hướng dẫn agent setup scaffold các service FlashTicket

Ngày đối chiếu: **24/10/2026**, cập nhật phần error handling **25/10/2026**. Áp dụng cho `payment-service`, `user-service`,
`event-service`, `ticket-service`. Mẫu tham khảo là Booking hiện tại và Gateway
ở commit `68b6e32`; khi thực hiện phải đọc lại source tại branch đang làm.

Đây là tài liệu bàn giao kỹ thuật, **không thay thế hoặc tự sửa hợp đồng B12–B16**.
Các ví dụ mang tên service khác dưới đây là hướng dẫn cần triển khai, không phải
tuyên bố những service đó đã có code. Scaffold hoàn tất nghĩa là cấu trúc,
mapping, dependency và các boundary dùng được; chưa có nghĩa flow nghiệp vụ đã chạy.

## 1. Prompt giao việc cho agent

Thay `<SERVICE>` bằng đúng một trong bốn tên service rồi gửi nguyên khối sau:

```text
Setup scaffold cho <SERVICE> theo docs/guides/agent-service-scaffold-playbook.md.
Đọc AGENTS.md và các hướng dẫn được dẫn, B12/B13/B14/B16, DDL hoặc Mongo validator,
cấu hình deploy và source Booking/Gateway hiện tại trước khi sửa.

Thực hiện trực tiếp, không dùng subagent. Chỉ sửa module <SERVICE>, config YAML
riêng của service và các khai báo môi trường/tài liệu liên quan cần thiết.
Kiểm tra branch, worktree và thay đổi có sẵn; giữ nguyên công việc người khác.

Bao gồm entity/document, enum/value type, DTO REST/message/snapshot đúng contract,
validation, MapStruct, repository, service interface + implementation rỗng,
controller rỗng và constructor injection. Setup Config Server/Eureka, persistence,
JWT/role, JSON, RabbitMQ, error handling và logging/correlation theo hướng dẫn.
Redis, Feign và client credentials chỉ thêm khi có nhu cầu thực tế của owner.

Chưa triển khai handler API nghiệp vụ, RabbitListener, cron, outbox relay, workflow
thanh toán/phát vé/phê duyệt; không tạo endpoint trả thành công giả. Không sửa DB
đang chạy hoặc topology broker để làm cho scaffold boot được. Không thêm dependency
vào booking-service hoặc tạo shared framework/module mới.

Trước khi code, lập bảng ngắn mapping dữ liệu/API/message và phần baseline còn thiếu.
Nếu source dự án đã trả lời thì tự tiến hành; chỉ hỏi khi có xung đột contract thật.
Chạy compile/test/verify phù hợp, kiểm JSON/security và smoke boot khi hạ tầng sẵn sàng.
Báo rõ kiểm tra đã chạy/chưa chạy, giới hạn và file thay đổi. Không gọi scaffold
là nghiệp vụ hoàn chỉnh. Chỉ commit/push/merge khi người giao việc yêu cầu.
```

## 2. Đọc nguồn theo thứ tự này

| Cần xác định | Nguồn sở hữu |
|---|---|
| Phạm vi service, một Saga do Payment điều phối | [B11-C](../architecture/B11-C-target-architecture.md) |
| Bất biến aggregate, trạng thái và trách nhiệm | [B7](../domain/B7-aggregates-and-invariants.md) |
| Dữ liệu, FK, snapshot, kiểu/constraint | [B12](../architecture/B12-data-ownership-and-schema.md) |
| DTO, endpoint, messageType, tiền, ID, lỗi | [B13](../architecture/B13-api-and-event-contracts.md) |
| Thứ tự, transaction, timeout, idempotency | [B14](../architecture/B14-main-flow-sequences.md) |
| Tiêu chí kiểm chứng | [B15](../architecture/B15-verification-plan.md) |
| Log, trace, correlation và RCA | [B16](../architecture/B16-observability-baseline.md) |
| Runtime local/AWS hiện hành | [Runtime baseline](../architecture/aws-runtime-baseline.md) |
| SQL thật | [PostgreSQL DDL](../architecture/data/postgres/) |
| Mongo validator/index | [schema.cjs](../architecture/data/mongodb/schema.cjs), [MongoDB deploy/init](../../deploy/mongodb/) |
| Tên bảng/collection thực tế | [LOCAL-DATABASES.md](../../services/LOCAL-DATABASES.md), [script prefix SQL](../../services/99-table-prefix.sql) |
| Broker được provision | [definitions.json](../../deploy/rabbitmq/definitions.json) |

Nếu repo có `.codegraph/`, dùng CodeGraph trước khi tìm/đọc code; YAML, SQL và tài
liệu vẫn cần đọc trực tiếp. Không tự tạo index nếu repo chưa có.

Lập inventory tại ref hiện tại: loại dữ liệu → bảng/collection → class → DTO →
mapper → repository → service/controller sở hữu; API/message → hướng gửi/nhận →
quyền → nguồn contract. Ghi rõ cái đã có và cái cần bổ sung. Không suy nghiệp vụ từ
tên class cũ hoặc sao toàn bộ Booking rồi đổi package.

## 3. Khác nhau bắt buộc giữa các service

### 3.1 Persistence và cấu hình nhận diện

| Service | Datastore | Database/schema | Package hiện có | Port trong `.env.example` |
|---|---|---|---|---:|
| Event | PostgreSQL/JPA | `event_db` / `event_schema` | `com.flashticket.event_service` | 8086 |
| Payment | PostgreSQL/JPA | `payment_db` / `payment_schema` | `com.flashticket.payment_service` | 8083 |
| Ticket | PostgreSQL/JPA | `ticket_db` / `ticket_schema` | `com.flashticket.ticket_service` | 8084 |
| User | MongoDB/Spring Data MongoDB | `user_db` | `com.flashticket.user_service` | 8085 |

Đây là cấu hình tham khảo của checkout ngày đối chiếu, không phải port nghiệp vụ.
`spring.application.name` phải đúng tên `*-service` để Config Server/Eureka/Gateway
khớp nhau. Gateway local của phiên làm việc này dùng `8088`; default YAML vẫn là
`8081`. Event YAML hiện còn fallback `8081`, khác `.env.example`; agent Event cần
đồng bộ fallback với port được chọn trong task của mình, tránh trùng Gateway.

SQL canonical dùng tên như `payment_attempts`; initializer đang áp prefix `tbl_`.
Mongo cũng có bước đổi collection sang `tbl_*`. Kiểm server/initializer của đúng
môi trường, không đổi annotation để né lỗi tên bảng rồi tự chạy DDL phá schema.

### 3.2 Danh mục dữ liệu phải đối chiếu đủ

Tên dưới đây là tên logic trong B12; áp prefix theo deployment thật.

| Owner | Bảng/collection thuộc owner |
|---|---|
| Event | `categories`, `venues`, `events`, `event_images`, `event_layouts`, `event_sectors`, `event_seats`, `ticket_types`, `promotions`, `event_cancellation_requests`, `outbox_messages`, `inbox_messages` |
| Payment | `order_payment_snapshots`, `payment_attempts`, `charges`, `payment_confirmations`, `refunds`, `refund_attempts`, `event_refund_runs`, `event_refund_items`, `event_payouts`, `event_finance_snapshots`, `payment_sagas`, `outbox_messages`, `inbox_messages` |
| Ticket | `event_access_snapshots`, `issuances`, `tickets`, `ticket_deliveries`, `checkin_attempts`, `outbox_messages`, `inbox_messages` |
| User | `users`, `organizer_applications`, `organizer_profiles`, `user_follows` |

User **không tự thêm Mongo outbox/inbox hoặc Mongo transaction manager** theo mẫu SQL.
Workflow `roleGrant` nằm trong application, projection phục hồi theo B13; Mongo
standalone không hỗ trợ transaction nhiều document như một replica set.

### 3.3 Ranh giới không được làm sai

- **Event** sở hữu cấu hình bán, giá, fee đã duyệt và bố cục. Không sở hữu counter
  held/purchased hoặc số lượt promotion đã dùng của Booking. `SEAT_MAP` khác với
  loại sector `SEATED`/`STANDING`; không gộp thành một enum.
- **Payment** giữ `PurchaseSnapshot` ở bản sao cục bộ và sổ tiền, không tạo entity
  JPA `Order` liên kết sang Booking. Chỉ Payment điều phối Saga mua vé. Callback
  chưa xác minh chữ ký không phải bằng chứng thu tiền; timeout không tự thành FAILED.
- **Ticket** giữ snapshot phát hành; không đọc DB Booking/Event. Gửi email lỗi
  không đồng nghĩa phát vé lỗi. Không có trạng thái vé `REFUNDED`; không thêm
  check-in offline hoặc phát hành từng phần vào scaffold.
- **User** dùng nguyên Keycloak `sub` làm subject; không dùng email làm khóa.
  Không lưu password/JWT/refresh token hoặc roles làm nguồn sự thật thứ hai.
  Không đánh application ACTIVE trước khi Keycloak xác nhận quyền ORGANIZER.

## 4. Cấu trúc package và dependency

Theo cách tổ chức Booking hiện có, feature giữ controller/service/repository;
entity, DTO và các cấu hình dùng trong owner ở cấp service:

```text
com/flashticket/<service>_service/
  <Service>ServiceApplication.java
  config/                 # JSON, MVC security, Rabbit; worker config khi có job thật
  entity/                 # SQL owners; User có thể dùng document/
  enums/
  dto/request/            # Input REST; không phải entity
  dto/response/           # Output REST và Problem
  dto/snapshot/           # Bản sao typed có contract
  dto/message/            # Envelope và message gửi/nhận thực tế
  mapper/
  validation/
  security/
  exception/
  client/config/          # Chỉ khi cần HTTP client
  client/internal/        # Chỉ client nội bộ có hợp đồng
  <feature>/controller/
  <feature>/repository/
  <feature>/service/
  <feature>/service/impl/
```

Chỉ tạo package/class có nhiệm vụ trong scaffold đã giao, không tạo thư mục rỗng
hàng loạt. Không bắt mọi service có cùng danh sách feature của Booking.

Baseline lúc đối chiếu: **Java 21, Spring Boot 4.1.1, Spring Cloud 2025.1.3,
MapStruct 1.6.3, lombok-mapstruct-binding 0.2.0**. Đọc POM hiện tại trước khi thêm;
không tự nâng/hạ phiên bản cả repo trong task scaffold. Từ 26/10/2026, theo yêu cầu
người dùng, **Booking/Ticket/User dùng JDK 23**, compile release 23 và Enforcer
`[23,24)`; các module khác giữ Java 21. Giữ Enforcer theo POM của từng module,
Spotless, JaCoCo và SpotBugs đã có.

- Service nghiệp vụ dùng Spring MVC: `spring-boot-starter-webmvc`, validation,
  resource server, Config Client, Eureka và Lombok/MapStruct khi có mapping.
- SQL owners dùng Data JPA/PostgreSQL; User dùng Data MongoDB, không kéo JPA theo mẫu.
- Rabbit/Redis/Feign/OAuth2 client thêm hoặc giữ theo nhu cầu thực của owner;
  không copy mail starter sang service không gửi mail.
- Booking đang dùng Jackson 3: `tools.jackson.databind.*`, `JsonMapper`,
  `JacksonJsonMessageConverter`. Jackson annotations vẫn ở
  `com.fasterxml.jackson.annotation.*`; không đổi package annotations theo máy móc.
- Không thêm dependency sang module Booking, không tạo shared SDK/base-service,
  không thêm MapStruct factory hoặc generic CRUD framework.

## 5. Entity/document và quan hệ

### SQL owners

Mapping khớp DDL: tên vật lý, PK/PK ghép, nullability, precision/scale, enum,
`jsonb`, timestamp và `row_version`. Không dùng `ddl-auto=update/create` để ép DB
theo Java. Các constraint/index/trigger có sẵn không được coi là đã thay bằng JPA.

Trích đoạn Payment, **chưa phải entity đầy đủ để copy nguyên khối**:

```java
@Entity
@Table(name = "tbl_payment_attempts")
@Getter
@Setter
public class PaymentAttempt {
  @Id private UUID id;

  @Column(name = "order_id", nullable = false)
  private UUID orderId;

  @Column(name = "expected_amount", precision = 19, scale = 0, nullable = false)
  private BigDecimal expectedAmount;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false)
  private PaymentAttemptState state;

  @Version
  @Column(name = "row_version", nullable = false)
  private Long rowVersion;
  // Bổ sung toàn bộ cột còn lại từ 30-payment.sql, không chỉ các field trên.
}
```

`orderId` ở đây là ID của Booking và liên kết tới snapshot trong DB Payment theo
DDL, không phải `@ManyToOne` tới entity Order của Booking. Nếu muốn map association
tới `OrderPaymentSnapshot` cục bộ thì xử lý đầy đủ FK ghép, không map hai field cùng
ghi một column. Không mất ràng buộc amount/currency của FK ghép khi viết repository.

Quan hệ trong cùng owner chỉ map khi có ích cho aggregate: ưu tiên `LAZY`, quản
lý owning side và helper đồng bộ hai phía như `Order.addItem()` hiện tại. Không
`@Data` trên JPA entity để tránh equals/toString đệ quy và lazy load ngoài ý muốn.
Không cascade REMOVE/orphanRemoval mọi quan hệ; Event có guard xóa cấu hình nháp,
Payment có dữ liệu tiền phải giữ lịch sử. Chọn cascade theo vòng đời đã chốt.

Mapping `@Version` chỉ đặt ở cột version có trong DDL. Nó không tự cài locking cho
use case, không thay idempotency, không sinh source version hoặc schema version.
Không thêm `@Lock`/transaction giữ tồn kho vào scaffold khi chưa triển khai flow.

### User/MongoDB

Ví dụ mapping collection Users, đối chiếu đủ validator thực tế trước khi dùng:

```java
@Document(collection = "tbl_users")
@Getter
@Setter
public class UserDocument {
  @Id private String id; // UUID canonical dạng chuỗi, không phải ObjectId tự sinh
  private String identitySubject;
  private String displayName;
  private Instant createdAt;
  private Instant updatedAt;
  @org.springframework.data.annotation.Version private Long version;
}
```

Service sẽ cấp `UUID.randomUUID().toString()` khi tạo document trong use case thật.
Không dùng UUID BSON binary hoặc Mongo ObjectId nếu validator `_id` yêu cầu chuỗi.
`organizer_profiles` và `user_follows` có metadata khác `users`; không ép cả bốn
collection kế thừa một BaseDocument có version/updatedAt ngoài validator kín.
Không thay Mongo index/validator bằng `@Indexed` rồi cho app tự tạo tùy ý.

## 6. DTO, snapshot, kiểu dữ liệu và validation

DTO lấy từ B13; entity lấy từ B12/DDL. Hai loại không có nhiệm vụ giống nhau:

| Loại | Ví dụ đúng owner | Giới hạn |
|---|---|---|
| Request | User đăng ký organizer; Ticket check-in | Chỉ field client được gửi; actor từ JWT |
| Response | `PaymentView`, `TicketView`, `EventPublic` | Không trả entity, QR ciphertext, secret hoặc audit nội bộ |
| Snapshot | Payment `PurchaseSnapshot`/finance; Ticket purchase/access | Bản sao typed phục vụ quyết định, không JOIN owner gốc |
| Message | `StartPayment`, `IssueTickets`, `EventCancellation` | Payload cùng Envelope/metadata của B13 |

Các service sở hữu DTO riêng nhưng JSON của contract dùng chung phải tương thích.
Không import `com.flashticket.booking_service.*` vào Payment/Ticket để tái sử dụng.
Khi nhân bản một wire DTO, kiểm cùng fixture JSON, nullable/required và enum;
không tự thêm alias hoặc field convenience làm thay shape.

- Java entity: UUID, `BigDecimal`, `Instant`, enum STRING. Money VND không dùng
  `double/float`; không nhận giá trị phần lẻ rồi làm tròn để hợp lệ.
- Wire Money và version theo chuỗi chuẩn của B13. Version nguồn > 0; `rowVersion`
  có thể bắt đầu 0; availability có ngoại lệ counter bằng 0. Không dùng một regex
  positive duy nhất cho tất cả số hoặc quy tắc nguyên đồng cho tọa độ Event.
- `Instant` serialize dạng UTC cho API/message. Session PostgreSQL có thể hiển thị
  `Asia/Ho_Chi_Minh` theo môi trường; vẫn giữ ý nghĩa TIMESTAMPTZ/Instant và
  `hibernate.jdbc.time_zone: UTC`. Không tự ALTER timezone DB trong task scaffold.
- `rowVersion`: xung đột ghi row. `sourceConfigVersion`/`sourceFeeVersion`: phiên
  bản cấu hình nguồn. `schemaVersion`: shape contract. Không tăng chúng cùng nhau.
- Dùng class/record bất biến cho snapshot nếu phù hợp Jackson/validation. List
  cần defensive copy; `List.copyOf` reject null ngay, còn unmodifiable copy có thể
  giữ null để Bean Validation báo lỗi tại boundary như DTO Booking hiện tại.
- Sealed interface + deserializer chỉ khi contract có các variant thật, như
  `OrderSelection` hoặc shape Event; không tạo hierarchy cho mọi request.

Ví dụ input User đúng B13, không tự thêm field quyền/giấy phép/ngân hàng:

```java
public record OrganizerApplicationRequest(
    @NotBlank String organizationName,
    @NotBlank String shortDescription) {}
```

Ba nơi validation có trách nhiệm khác nhau:

1. Bean Validation kiểm hình dạng field, null, độ dài/range đã có nguồn tại REST
   và message boundary. Dùng `@Valid` cho object lồng; validate cả message nhận được.
2. Helper `ContractRules` thuần Java kiểm nhiều field/cấu trúc và dùng chung giữa
   các DTO của owner. Static helper không tự được gọi: phải nối từ constructor,
   `@AssertTrue` hoặc deserializer/boundary như mẫu Booking; tránh serialize method
   validation thành field JSON ngoài hợp đồng. Custom annotation chỉ thêm khi thật
   sự cần tái dùng rule/nhắm lỗi field; không bắt buộc mọi rule phải có annotation.
3. Service kiểm ownership, trạng thái hiện tại, uniqueness nghiệp vụ, clock,
   snapshot freshness và quyết định transaction. DTO/mapper không truy vấn DB,
   lock tài nguyên hoặc quyết định quyền truy cập.

Không biến mọi IllegalArgumentException thành lỗi 400: lỗi invariant/mapping
nội bộ cần phân biệt với input client. Xem cách Booking phân biệt validation
input với return value trong GlobalExceptionHandler.

## 7. MapStruct và JSON

Mẫu đang có: [OrderMapper](../../services/booking-service/src/main/java/com/flashticket/booking_service/mapper/OrderMapper.java),
[MappingValueConverter](../../services/booking-service/src/main/java/com/flashticket/booking_service/mapper/MappingValueConverter.java),
[BookingJsonConfig](../../services/booking-service/src/main/java/com/flashticket/booking_service/config/BookingJsonConfig.java).

Giữ `componentModel=SPRING`, `unmappedTargetPolicy=ERROR`. Converter tiền/version
phải kiểm range/scale rồi trả chuỗi; không dùng implicit conversion làm mất rule.
Không `ignore=true` hàng loạt chỉ để compile qua; lý do bỏ target field phải rõ.

Ví dụ mapper Payment khi các entity/DTO đã được khai báo đủ từ B12/B13:

```java
@Mapper(componentModel = MappingConstants.ComponentModel.SPRING,
    unmappedTargetPolicy = ReportingPolicy.ERROR,
    uses = WireValueConverter.class)
public interface PaymentMapper {
  @Mapping(target = "orderId", source = "attempt.orderId")
  @Mapping(target = "attemptId", source = "attempt.id")
  @Mapping(target = "attemptState", source = "attempt.state")
  @Mapping(target = "amount", source = "attempt.expectedAmount")
  @Mapping(target = "currency", source = "attempt.currency")
  @Mapping(target = "paymentUrl", source = "attempt.paymentUrl")
  @Mapping(target = "expiresAt", source = "snapshot.expiresAt")
  PaymentStart toStart(PaymentAttempt attempt, OrderPaymentSnapshot snapshot);
}
```

`expiresAt` đến từ snapshot Payment cục bộ, không tự gọi Booking trong mapper.
Mapper không tính phí, freeze snapshot, ký QR, chọn charge hay chuyển trạng thái.

Trong compiler plugin, đối chiếu Booking POM để cấu hình processors:

```xml
<annotationProcessorPaths>
  <path>
    <groupId>org.projectlombok</groupId><artifactId>lombok</artifactId>
  </path>
  <path>
    <groupId>org.projectlombok</groupId><artifactId>lombok-mapstruct-binding</artifactId>
    <version>0.2.0</version>
  </path>
  <path>
    <groupId>org.mapstruct</groupId><artifactId>mapstruct-processor</artifactId>
    <version>1.6.3</version>
  </path>
</annotationProcessorPaths>
```

Kèm dependency `org.mapstruct:mapstruct:1.6.3`; tránh append plugin trùng execution
có sẵn. Bật annotation processing trong IDE và kiểm `target/generated-sources/annotations`.
Không sửa file mapper generated hoặc dùng `Mappers.getMapper()` để né Spring DI.

JSON config nên reject unknown fields và coercion sai kiểu theo contract như
Booking. `FAIL_ON_MISSING_CREATOR_PROPERTIES` cần kiểm cả nullable creator fields:
nullable không mặc nhiên nghĩa được bỏ key. Có fixture cho field optional/required
để không áp strict config rồi vô tình reject payload hợp lệ.

## 8. Repository, service interface/impl và controller

Repository đúng datasource: SQL `JpaRepository<Entity, IdType>`; User
`MongoRepository<Document, String>`. PK ghép dùng IdClass/EmbeddedId đúng DDL.
Không tạo repository của entity owner khác. Association LAZY không được mapper
vô tình fetch ngoài transaction; fetch plan là việc use case đọc sẽ triển khai.

Đây là mẫu scaffold theo yêu cầu tổ chức giống Booking/ShopApp, không phải generic
CRUD abstraction. Ví dụ Ticket sau khi IssuanceRepository/IssuanceMapper đã có:

```java
public interface IssuanceService {}

@Service
@RequiredArgsConstructor
public class IssuanceServiceImpl implements IssuanceService {
  private final IssuanceRepository issuanceRepository;
  private final IssuanceMapper issuanceMapper;
}

@RestController
@RequestMapping("/api/v1/orders/{orderId}/tickets")
@RequiredArgsConstructor
public class OrderTicketsController {
  private final IssuanceService issuanceService;
}
```

Mỗi public class nằm trong file riêng. Controller/service viết tay dùng constructor
injection với final fields và `@RequiredArgsConstructor`, hoặc constructor tường minh;
không field injection. Mapper generated tuân cấu hình MapStruct của owner, không sửa tay.
Chỉ inject collaborator đã cần ở feature này; không thêm tất cả repository vào
một service tổng, không phụ thuộc vòng service A↔B, không dùng `@Lazy` để che vòng.

Controller giai đoạn này chưa có `@GetMapping`/`@PostMapping` handler. Không thêm
method trả null, `200 OK` rỗng hoặc success giả. Không thêm `@Transactional` để tạo
cảm giác flow đã có; đặt transaction trên use case khi thực sự triển khai.

## 9. Config Server, persistence và môi trường

File local của từng service chỉ giữ nhận diện/import và cấu hình bootstrap cần
thiết; runtime YAML đặt tại `infrastructure/config-server/src/main/resources/config/`:

```yaml
spring:
  application:
    name: payment-service
  config:
    import: "configserver:${CONFIG_SERVER_URL:http://localhost:8888}"
```

Common Eureka config đã nằm ở `config/application.yaml`, không copy 4 bản khác nhau.
Không biến Config Server thành optional ở production chỉ để app boot qua lỗi.

Ví dụ đoạn persistence Payment cần đối chiếu role đã provision:

```yaml
server:
  port: ${PAYMENT_SERVICE_PORT:8083}
spring:
  datasource:
    url: ${PAYMENT_DB_URL}
    username: ${PAYMENT_DB_USERNAME}
    password: ${PAYMENT_DB_PASSWORD}
    hikari:
      maximum-pool-size: 5
      minimum-idle: 1
  jpa:
    open-in-view: false
    hibernate:
      ddl-auto: none
    properties:
      hibernate:
        default_schema: payment_schema
        type:
          json_format_mapper: jackson3
        jdbc:
          time_zone: UTC
```

Booking hiện dùng `connection-init-sql: SET ROLE booking_owner`. Không copy nguyên
sang Payment/Event/Ticket. Chỉ thêm `SET ROLE <owner>` khi username hiện tại có
quyền chuyển đúng role đã provision và mô hình vận hành cho phép; không tự cấp
quyền superuser hoặc dùng account reader để chạy service.

User dùng cấu hình Boot hiện tại, không copy `spring.jpa`/Hikari:

```yaml
spring:
  mongodb:
    uri: ${MONGO_URI}
```

URI phải đúng authSource/encoding credential đã provision, không mặc định hóa
theo trí nhớ cấu hình Boot cũ. Chỉ một `.env` ở root theo quy ước repo; mỗi máy
tự điền secret/host và nạp vào IDE/runtime. Không commit `.env`, không chép IP,
SSH alias hoặc mật khẩu của máy mẫu vào cấu hình dùng chung. Secret cần mới phải
được liệt kê trong `.env.example` và chỉ bắt buộc khi feature tương ứng được bật.

## 10. JWT, quyền và HTTP client

### Resource server MVC

Sao pattern của [SecurityConfig Booking](../../services/booking-service/src/main/java/com/flashticket/booking_service/config/SecurityConfig.java),
không sao Gateway WebFlux `SecurityWebFilterChain` vào service MVC.

```yaml
spring:
  security:
    oauth2:
      resourceserver:
        jwt:
          issuer-uri: ${KEYCLOAK_ISSUER_URI}
          jwk-set-uri: ${KEYCLOAK_JWK_SET_URI}
          audiences:
            - ${KEYCLOAK_AUDIENCE:flash-ticket-api}
```

Issuer khớp chính xác `iss`, JWKS reachable từ JVM; JWT hợp lệ còn cần đúng audience,
signature, expiry. Trích `realm_access.roles` thành ROLE_BUYER/ORGANIZER/ADMIN và
ROLE_INTERNAL_SERVICE theo allowlist, không tin header X-User/X-Role từ client.
`INTERNAL_SERVICE` là role ứng dụng, khác realm-management permission của Keycloak.

| Owner | Quy tắc quyền cần map từ B13 khi dựng security |
|---|---|
| Event | GET public event được public; thao tác organizer cần ORGANIZER + ownership; phê duyệt thuộc ADMIN |
| Payment | Đọc payment cần BUYER + ownership từ snapshot; API quản trị tài chính theo B13; callback provider có cơ chế xác minh riêng |
| Ticket | Đọc/tải/gửi vé cần BUYER + ownership; POST check-in cần ORGANIZER + quyền với event |
| User | `/me` cần JWT; organizer application/follow theo actor; approve/reject ADMIN; profile công khai chỉ dữ liệu ACTIVE |
| Mọi service có internal API | `/internal/v1/**` cần INTERNAL_SERVICE và kiểm caller/use case cần thiết |

Rule rộng đứng sau rule cụ thể; không để `.authenticated()` vô tình cho organizer
đọc QR buyer. Role chưa chứng minh ownership. Gateway chặn internal public nhưng
service vẫn phải kiểm JWT nội bộ khi được gọi trực tiếp. Health endpoint có thể
public theo mẫu Booking; các actuator khác không mở công khai. CSRF/session policy
theo API Bearer stateless của Booking, không dùng form login mặc định.

### Client credentials và Feign

Chỉ tạo HTTP client khi B13 có lời gọi thật. Hướng giao tiếp quan trọng:

- Booking → Payment khởi tạo thanh toán bằng message, không thêm Feign POST tạo attempt.
- Payment → Booking có guard nội bộ freeze-payment trước attempt mới; Ticket có
  issuance probe; Event có thể đọc financial-summary Payment theo B13/B14.
- User → Keycloak Admin API cần client quản trị phù hợp. Không dùng token BUYER
  hoặc mặc định role INTERNAL_SERVICE cho quyền quản trị realm.
- Payment → VNPay là external adapter, không gắn JWT internal; callback và chữ ký
  chỉ triển khai trong task tích hợp provider. Ticket SMTP cũng không phải Feign.

Mẫu cấu hình tái sử dụng:
[InternalFeignConfig](../../services/booking-service/src/main/java/com/flashticket/booking_service/client/config/InternalFeignConfig.java).
Class này cố ý không có `@Configuration` toàn cục; chỉ opt-in cho client internal.
Đổi machine principal `booking-service` thành service đang triển khai, giữ OAuth2
authorized-client manager cho client_credentials và timeout cả khi lấy token.

```java
// Chỉ dùng khi triển khai HTTP contract, không tạo method rỗng hoặc endpoint giả.
@FeignClient(name = "booking-service", contextId = "paymentBookingInternal",
    configuration = InternalFeignConfig.class)
public interface BookingInternalClient {
  // Khai báo method đúng B13 khi thực hiện task guard nội bộ.
}
```

Snippet minh họa cách opt-in; **không cần tạo interface rỗng này** trong scaffold
nếu chưa có method được giao. Giới hạn `@EnableFeignClients` vào package client
nội bộ cần thiết. Đặt `spring.cloud.openfeign.oauth2.enabled=false` nếu dùng cấu
hình opt-in như Booking để tránh auto interceptor gắn token cho client ngoài.
Không copy JWT người dùng từ SecurityContext để gọi nội bộ; không tự cache/refresh
token bằng static field. Chỉ forward correlation UUID khi ứng dụng đã đặt trong MDC; scaffold Booking hiện không có HTTP correlation filter.

Chỉ `@FeignClient(name=...)` không tự chứng minh load balancing có đủ dependency;
kiểm effective dependencies, ServiceInstance/Eureka và test HTTP. Không tắt TLS,
audience hoặc role để chữa lỗi kết nối.

## 11. RabbitMQ, inbox/outbox và Redis

Theo [RabbitMqConfig Booking](../../services/booking-service/src/main/java/com/flashticket/booking_service/config/RabbitMqConfig.java),
cấu hình JSON converter bằng `JsonMapper` đã được Boot cấu hình. Topology thuộc
deploy, không khai báo lại Exchange/Queue/Binding bằng Java khi dùng baseline này:

```yaml
spring:
  rabbitmq:
    host: ${RABBITMQ_HOST}
    port: ${RABBITMQ_PORT:5672}
    username: ${RABBITMQ_USERNAME:flash_ticket_app}
    password: ${RABBITMQ_PASSWORD}
    virtual-host: ${RABBITMQ_VHOST:/}
    dynamic: false
    publisher-confirm-type: correlated
    publisher-returns: true
    template:
      exchange: ${RABBITMQ_BUSINESS_EXCHANGE:business.ex}
      mandatory: true
    listener:
      simple:
        acknowledge-mode: manual
        prefetch: 20
```

Đây là đoạn config Rabbit, phải merge vào `spring` hiện có, không thêm hai key
`spring:` ngang cấp. User identity flow phải kiểm exchange/binding/account riêng;
không mặc định `business.ex` hoặc tài khoản business là đúng cho mọi message User.

| Owner | Contract cần lấy DTO/schema từ B13 |
|---|---|
| Event gửi | SalesConfiguration, AccessConfiguration, FinanceConfiguration, EventCancellation |
| Payment nhận | StartPayment, FinanceConfiguration, EventCancellation, PaymentDecision, OrderCompleted, nhánh FAILED của IssuanceResult |
| Payment gửi | AcceptPayment, IssueTickets, BookingIssuanceResult FAILED và kết quả start theo contract hiện hành |
| Ticket nhận | AccessConfiguration, EventCancellation, IssueTickets |
| Ticket gửi | IssuanceResult: COMPLETED về Booking; FAILED về Payment |
| User | Identity/role-grant theo contract và topology thực tế; không tự thêm business Saga |

Tên queue/routing key/messageType phải lấy đúng definitions/B13, không suy từ tên
DTO. Validate Envelope, schemaVersion và typed payload; không coi header type ID
do người gửi cung cấp là quyền deserialize class Java tùy ý. Chỉ ACK sau commit
của hiệu ứng/inbox/outbox khi consumer được triển khai. Scaffold không tạo listener
ACK mọi message, scheduler hoặc publisher đang chạy mà chưa có handler.

Publisher confirm chỉ chứng minh broker nhận, không chứng minh consumer xử lý.
Chỉ mark published sau confirm ACK và không returned; timeout là UNKNOWN, không
được bỏ kiểm idempotency khi publish lại. Business rejection khác malformed
message và khác lỗi hạ tầng; không requeue vô hạn mọi RuntimeException.

Redis dùng khi owner có cache/ephemeral use case được giao; không thêm counter
Redis làm nguồn sự thật thứ hai của tồn kho, tiền, vé hoặc follow. Nếu dùng Redis,
cấu hình host/port/password bằng env và connect/read timeout có giới hạn như Booking.
Không copy rate limiter reactive của Gateway vào các service MVC.

## 12. Global exception, logging và RCA

Phạm vi scaffold được chốt lại 25/10/2026: làm như ShopApp, chỉ một
`exception/GlobalExceptionHandler` dùng `@RestControllerAdvice` và các
`@ExceptionHandler`. Service ném business/native exception; handler tạo response và
ghi log trực tiếp. Không thêm `web.error`, `ProblemResponses`, `CorrelationFilter`,
`RequestCorrelation`, custom error controller hoặc framework `observability`.
Chưa triển khai publisher gửi log/error event qua RabbitMQ để xử lý RCA.

| Thành phần Booking làm mẫu | Việc phải đổi/giữ đúng |
|---|---|
| ErrorCode/BusinessException | Một enum chứa code/message/status của owner; service ném lỗi đúng nghĩa |
| ErrorCode.from(Throwable) | Nhận diện native exception thực tế của owner, không cần classifier/Failure DTO riêng |
| GlobalExceptionHandler | Trả Problem trực tiếp, validation input 400/output 500, giữ headers framework |
| Security callbacks | Đặt Bearer challenge và chuyển 401/403 vào global advice qua HandlerExceptionResolver của Spring |
| YAML logging và config nhỏ | JSON native, metadata owner, UTC timestamp và stack trace đã lọc |

Không copy raw exception message hoặc fallback 404 cho mọi lỗi từ ShopApp. Problem
là DTO JSON cho client; không phải helper hoặc message gửi broker. Giữ code/message/
correlationId/violations theo B13. Message log nêu lý do an toàn, kèm status/method/code/
correlation để điều tra sau này; không log JWT, email, SQL, body hay secret.

Booking hiện chỉ tạo/nhận correlation khi trả lỗi. Không tự cấp request ID trên mọi
HTTP request, không tự bind order hoặc tạo context framework. Native `/error` của
Boot dùng response mặc định, ngoài phạm vi global advice. Các agent phải ghi rõ
giới hạn này, không tự thêm toàn bộ B16 hoặc pipeline RCA khi task chỉ là scaffold.

Không catch-log-rethrow ở mọi tầng. User dùng Mongo cần kiểm tra exception native
tương ứng thay vì copy nguyên phân loại SQL. Chỉ thêm xử lý scheduler, retry hoặc
publisher khi flow thật được giao và có kiểm tra transaction/disposition.

Đọc [hướng dẫn global exception Booking](booking-error-handling-and-rca.md) và source
`exception/GlobalExceptionHandler.java` để làm theo cấu trúc hiện tại.

## 13. Tương thích Gateway

Đối chiếu [Gateway README](../../infrastructure/api-gateway/README.md) và route
Java thực tế, không chỉ security matcher. Gateway hiện là bộ route bootstrap,
chưa tự bao phủ mọi API Event/User trong B13.

- Public prefix giữ nguyên `/api/v1`, nội bộ `/internal/v1` gọi trực tiếp service.
- POST `/api/v1/orders/{id}/payment-attempts` thuộc Booking; GET payment thuộc Payment.
- Availability thuộc Booking dù URL bắt đầu bằng `/events`.
- Order tickets/QR/ticket-deliveries/check-ins thuộc Ticket.
- Khi thêm handler Event/User khác, bổ sung đúng method/path, đích và rule Gateway
  trong task được giao; không suy `.anyExchange().authenticated()` là đã có route.
- Gateway đã chuyển tiếp JWT, Idempotency-Key, query/body và correlation; service
  vẫn xác thực độc lập. CORS hiện thuộc Gateway cho frontend được cấu hình.
- Không thêm automatic retry cho thao tác có tác dụng. Timeout Gateway không
  chứng minh transaction downstream rollback; client đọc trạng thái hoặc retry
  cùng key theo contract.

## 14. Trình tự làm và kiểm tra bàn giao

1. Kiểm branch/worktree/status, đọc nguồn và ghi inventory/diff phạm vi ngắn.
2. Đối chiếu datastore + tên vật lý + metadata/version; dựng entity/document/enums.
3. Dựng DTO REST/snapshot/message và validation theo B13, fixture JSON trước mapper.
4. Cấu hình MapStruct, repositories và service/impl/controller rỗng có constructor.
5. Setup YAML/JSON/security/error/logging; Rabbit/Feign/Redis đúng nhu cầu owner.
6. Kiểm mapper generated, JSON round-trip, security boundary và các config cần thiết.
7. Chạy Maven verify với JDK đúng POM (Booking/Ticket/User: 23; module khác: 21).
   Smoke boot khi có hạ tầng; ghi điều kiện môi trường.
8. Tự review diff, link tài liệu, bàn giao kết quả/giới hạn; không tự commit/merge.

Từ module cần kiểm:

```powershell
# PowerShell; trong môi trường có quy tắc RTK thì thêm `rtk proxy` trước lệnh.
.\mvnw.cmd -B -ntp verify
```

Không tự chạy Spotless cả repo rồi đưa file ngoài scope vào commit. Chạy JDK khớp
Enforcer của từng module; Booking/Ticket/User không cần bỏ gate khi dùng JDK 23.
Với module còn target 21, nếu chẩn đoán bằng JDK 23 và `"-Denforcer.skip=true"`,
ghi rõ ngoại lệ; **không gọi đó là bằng chứng gate Java 21 đã qua**.
Không sửa POM bỏ gate hoặc suppress SpotBugs hàng loạt để đổi test đỏ thành xanh.

Các kiểm tra phải có ý nghĩa với thay đổi, dùng framework sẵn có; không viết test
chỉ kiểm getter/constructor injection, không copy số lượng test Booking làm KPI:

| Kiểm tra | Chứng minh được |
|---|---|
| Compile + MapStruct ERROR policy | Dependency/processors/mapping target khớp |
| Fixture JSON/validation | Money/version/schema, enum, missing/unknown fields và message tương thích |
| Security HTTP tests | Public route, 401/403 cùng Problem, role/audience, internal bị kiểm đúng |
| Mapping/persistence test phù hợp | JSONB/Mongo type, FK ghép, metadata/version và association cần thiết |
| Error/log boundary tests | Không lộ secret, correlation lỗi header/body/log khớp, HTTP status đúng; không yêu cầu correlation filter |
| Config/Rabbit tests | Converter, timeout, MANUAL ACK, dynamic=false; chưa chứng minh broker live |
| Smoke chạy thật | Config Server load, Eureka đăng ký và dependency reachable trong lần chạy đó |

Không test bằng việc gọi endpoint rỗng trả success giả. Controller chưa có handler
có thể trả 404 khi đã qua security; thiếu JWT vẫn 401. Health UP không chứng minh
schema đúng hoặc use case hoàn chỉnh; Hikari có thể kết nối lười, cần kiểm persistence
riêng khi tuyên bố DB hoạt động. Không ghi dữ liệu thử vào DB dùng chung/production;
test persistence dùng môi trường cô lập hoặc rollback rõ ràng.

### Checklist kết thúc

- [ ] Chỉ owner được giao thay đổi; không có dependency Java/JOIN/FK xuyên owner.
- [ ] Đủ bảng/collection/DTO cần scaffold; không tạo business API/worker giả.
- [ ] Enum/column/nullability/type/PK ghép/version khớp DDL hoặc Mongo validator.
- [ ] MapStruct compile; không âm thầm bỏ mapping hoặc làm tròn tiền.
- [ ] Actor từ JWT, audience/role/internal đúng; ownership để use case kiểm thực sự.
- [ ] HTTP/security/message/worker boundary áp dụng đúng phần owner đang có.
- [ ] JSON/correlation/log có bằng chứng kiểm tra, không chỉ tên class đã tồn tại.
- [ ] Env không hard-code secret; không commit `.env`; port/issuer/host phân biệt rõ.
- [ ] Verify, smoke và test tích hợp ghi riêng PASSED/FAILED/NOT_RUN kèm giới hạn.
- [ ] Bàn giao file đã đổi, câu lệnh chạy, đầu vào cần owner provision và phần chưa làm.

## 15. Source mẫu nên mở trực tiếp

- [Booking POM](../../services/booking-service/pom.xml), [Booking application YAML](../../services/booking-service/src/main/resources/application.yaml).
- [Booking config](../../services/booking-service/src/main/java/com/flashticket/booking_service/config/), [runtime YAML](../../infrastructure/config-server/src/main/resources/config/booking-service.yaml).
- [Entity](../../services/booking-service/src/main/java/com/flashticket/booking_service/entity/), [DTO](../../services/booking-service/src/main/java/com/flashticket/booking_service/dto/), [mapper](../../services/booking-service/src/main/java/com/flashticket/booking_service/mapper/).
- [Order feature](../../services/booking-service/src/main/java/com/flashticket/booking_service/order/), [internal Feign config](../../services/booking-service/src/main/java/com/flashticket/booking_service/client/config/InternalFeignConfig.java).
- [Global exception](../../services/booking-service/src/main/java/com/flashticket/booking_service/exception/GlobalExceptionHandler.java), [logging config](../../services/booking-service/src/main/resources/application.yaml).
- [Booking test evidence/limits](../../services/booking-service/TESTING.md), [hướng dẫn kiểu dữ liệu và ví dụ Booking](shopapp-be-to-flash-ticket-backend.md).
- [Gateway route](../../infrastructure/api-gateway/src/main/java/com/flashticket/api_gateway/routing/GatewayRouteConfiguration.java), [Gateway security](../../infrastructure/api-gateway/src/main/java/com/flashticket/api_gateway/security/GatewaySecurityConfiguration.java), [Gateway HTTP tests](../../infrastructure/api-gateway/src/test/java/com/flashticket/api_gateway/GatewayHttpTests.java).

Tái sử dụng pattern và contract đã kiểm chứng; mỗi owner vẫn phải chứng minh
implementation của mình. Không coi việc Booking/Gateway pass test là test của
Payment, User, Event hoặc Ticket đã pass.
