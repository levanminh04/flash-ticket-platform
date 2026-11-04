# Từ ShopApp-BE sang FlashTicket Backend

Tài liệu này là bản đồ chuyển tiếp ngắn cho người đã quen `ShopApp-BE`. Chi tiết nghiệp vụ và hợp đồng nằm ở các tài liệu nguồn được liên kết, không lặp lại tại đây.

## 1. Khác biệt cốt lõi

| ShopApp-BE | FlashTicket |
|---|---|
| Một Spring Boot application và một database | Năm service nghiệp vụ, mỗi service sở hữu datastore riêng |
| JPA association xuyên toàn bộ domain | Chỉ association trong cùng owner; liên service dùng ID và snapshot |
| Ứng dụng tự quản password và phát JWT | Keycloak phát token; Gateway/service là OAuth2 Resource Server |
| RabbitMQ chủ yếu cho tác vụ phụ | RabbitMQ mang business event có version, inbox/outbox và idempotency |
| `Long`, `LocalDateTime`, `Double`, trạng thái `String` | UUID, `Instant`, tiền chính xác, enum và DB constraint |
| DTO dùng lại giữa nhiều boundary | Request, response, message và snapshot tách riêng |
| Một transaction có thể sửa toàn bộ mô hình | Mỗi service chỉ có local transaction; phối hợp qua message |

Điểm quan trọng nhất: đừng chuyển nguyên cấu trúc monolith sang từng microservice. Trước khi thêm một field hoặc dependency, xác định service nào sở hữu dữ liệu và biên giao tiếp nào được phép dùng.

## 2. Cách chạy Booking Service

Booking là Maven application độc lập tại `services/booking-service`.

- Java 21; Maven Enforcer từ chối JDK ngoài `[21,22)`.
- Bootstrap config: `services/booking-service/src/main/resources/application.yaml`.
- Runtime config: `infrastructure/config-server/src/main/resources/config/booking-service.yaml`.
- Service Discovery: Eureka.
- External traffic: API Gateway.
- Database: PostgreSQL `booking_db`, schema `booking_schema`.
- Identity: Keycloak.
- Async messaging: RabbitMQ topology trong `deploy/rabbitmq/definitions.json`.
- Redis: dependency phụ trợ; không phải nguồn sự thật của order/inventory.

Spring không tự đọc `.env`. IDE, shell hoặc container phải inject biến môi trường. Không commit secret vào YAML.

## 3. Chuyển các khái niệm quen thuộc

### 3.1 Security

Không mang `JwtTokenFilter`, `UserDetailsService`, password encoder hoặc logic phát token từ ShopApp sang Booking.

Booking cần:

1. `SecurityFilterChain` cho servlet application.
2. OAuth2 Resource Server JWT.
3. Kiểm issuer, signature, expiry và audience từ Config Server.
4. Converter lấy `BUYER`, `ORGANIZER`, `ADMIN` từ `realm_access.roles` thành `ROLE_*`.
5. Ownership/state check trong application service khi nghiệp vụ được triển khai.

Gateway là lớp bảo vệ đầu tiên, không thay thế authorization trong service.

### 3.2 Persistence

- Entity mutable cho JPA; DTO/message/snapshot ưu tiên immutable.
- JPA relation chỉ dùng giữa bảng thuộc Booking.
- Event, Payment, Ticket và User chỉ xuất hiện dưới dạng ID hoặc snapshot.
- `ddl-auto: none`; schema được quản lý ngoài Hibernate.
- Repository chỉ tạo cho aggregate hoặc bảng cần truy cập/lock độc lập, không tạo máy móc cho mọi entity con.

### 3.3 RabbitMQ

Ứng dụng dùng topology đã provision. Không tạo `Queue`, `Exchange`, `Binding` bean trong Booking.

Flow đích:

```text
request/message
  -> kiểm idempotency hoặc inbox
  -> đổi local state
  -> ghi outbox cùng transaction
  -> commit
  -> relay publish
  -> consumer khác deduplicate
```

Publisher confirm chỉ xác nhận broker nhận message, không chứng minh consumer đã xử lý. Với manual acknowledgement, consumer ACK sau local commit.

### 3.4 Redis

Ưu tiên Boot auto-configuration và `StringRedisTemplate`/`RedisOperations<String, String>`. Key và JSON value ở dạng string; không dùng Java native serialization hoặc permissive polymorphic typing.

Chỉ thêm cache/lock khi có use case, key namespace, TTL và failure policy rõ. PostgreSQL vẫn là nguồn sự thật.

## 4. Kiểu dữ liệu

| Ý nghĩa | Java | PostgreSQL / wire |
|---|---|---|
| Resource/message ID | `UUID` | `uuid` / chuỗi UUID |
| Thời điểm tuyệt đối | `Instant` | `timestamptz` / UTC timestamp |
| VND nguyên | `BigDecimal` scale 0 | `numeric(19,0)` / chuỗi số nguyên |
| Trạng thái đóng | enum | text có `CHECK` |
| Composite identity | `@Embeddable` | primary/unique key nhiều cột |
| Message/snapshot bất biến | `record` | JSON có schema version |
| Request có hữu hạn biến thể | sealed interface | discriminator rõ |

Không dùng mapper để ra quyết định nghiệp vụ. Mapper chỉ đổi shape và kiểu biểu diễn; service quyết định giá, state transition và transaction.

## 5. Validation theo tầng

1. Jackson kiểm JSON shape và coercion.
2. Bean Validation kiểm field/cross-field thuần.
3. Application service kiểm ownership, state, time window và dữ liệu hiện tại.
4. Database giữ invariant cuối dưới concurrency.

Không gọi repository từ DTO, mapper hoặc `ConstraintValidator`. Rule cần DB phải chạy trong transaction nghiệp vụ.

## 6. Cách đọc một flow Booking

Với mỗi endpoint hoặc message, lần lượt trả lời:

1. Wire contract và version nào?
2. Token subject/role nào được tin?
3. Bảng nào Booking sở hữu?
4. Row nào cần query hoặc lock?
5. Invariant nào phải giữ trong cùng transaction?
6. Retry/trùng message được nhận diện bằng khóa nào?
7. Outbox/message nào được tạo?
8. Khi crash giữa commit và publish thì phục hồi thế nào?
9. Correlation ID đi qua log/message ra sao?

Nếu chỉ đọc Controller → Service → Repository, bạn sẽ bỏ sót tính đúng của flow phân tán.

## 7. Nguồn cần đọc

- [B12 — data ownership và schema](../architecture/B12-data-ownership-and-schema.md)
- [B13 — API và event contracts](../architecture/B13-api-and-event-contracts.md)
- [B14 — main flow sequences](../architecture/B14-main-flow-sequences.md)
- [Booking handoff](../../services/booking-service/HANDOFF.md)
- [Booking runtime config](../../infrastructure/config-server/src/main/resources/config/booking-service.yaml)
- [RabbitMQ topology](../../deploy/rabbitmq/definitions.json)
- [Booking entities](../../services/booking-service/src/main/java/com/flashticket/booking_service/entity/)
- [Booking DTOs](../../services/booking-service/src/main/java/com/flashticket/booking_service/dto/)
- [Booking DDL](../architecture/data/postgres/20-booking.sql)

## 8. Quy tắc thực dụng

- Không JOIN hoặc import persistence model của service khác.
- Không tin total, price, owner hoặc state do client gửi nếu server có owner nguồn.
- Không dùng Redis thay database owner.
- Không tự declare Rabbit topology.
- Không tạo interface/adapter rỗng để “dành chỗ”.
- Không tạo repository cho entity con nếu aggregate owner đã quản lý lifecycle.
- Thêm controller, service và message adapter cùng use case đầu tiên của chúng.
- Giữ cấu hình và boundary tối thiểu; chỉ trừu tượng hóa khi có biến thể thật.
