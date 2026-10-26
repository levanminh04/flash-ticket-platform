# Payment Service — payment status API P2

Có thêm [Booking simulator qua RabbitMQ](docs/booking-simulator.md) để phát triển
luồng tạo attempt/URL khi Booking chưa chạy. Chế độ `payment-simulator` dùng
DB/broker riêng và guard Booking giả; không mở runtime này trong profile mặc
định hoặc production. API GET hiện có vẫn kiểm JWT và chủ đơn.

Có 13 JPA entity, DTO/MapStruct, contract boundary P1 và API đọc trạng thái P2 với repository, JWT Resource Server và ownership. Đã bổ sung cấu hình/adapter VNPay Sandbox và callback được bật có điều kiện; xem [hướng dẫn VNPay](docs/vnpay-sandbox.md). Runtime nghiệp vụ ngoài profile simulator chưa có consumer, outbox relay, StartPayment handler hoặc Saga; callback mặc định tắt và yêu cầu handler lưu bền trước khi bật. B13-v0.2 vẫn REVIEW_READY; chưa nghiệm thu tích hợp liên service hoặc giao dịch VNPay thật.

## Build

Dùng JDK 21, Maven Wrapper và chạy từ thư mục `services/payment-service`:

```powershell
$env:JAVA_HOME = '<duong-dan-JDK-21>'
$env:JAVA_TOOL_OPTIONS = '-Duser.timezone=UTC'
.\mvnw.cmd verify -B -ntp
```

JVM của formatter cần các compiler exports trong `.mvn/jvm.config`. Test JVM tự dùng UTC qua Surefire. JVM ứng dụng cũng nên dùng UTC: PostgreSQL có thể không nhận tên alias múi giờ cũ `Asia/Saigon` do JDBC gửi khi mở kết nối; `hibernate.jdbc.time_zone` chỉ áp dụng sau khi đã kết nối.

Suite mặc định chạy smoke context, DTO, mapper và YAML; không chạy `EntityMappingTest`. Smoke context tắt JPA nên không chứng minh mapping hoặc database đúng. Không giảm enforcer/quality gates nếu môi trường chưa sẵn sàng.

## Kiểm mapping PostgreSQL thật

Chuẩn bị một instance PostgreSQL test tách biệt hoàn toàn với volume deploy dùng chung, có database `payment_db` và schema `payment_schema`. Trong instance test mới, dùng bootstrap role tạo `payment_owner` và `payment_app`, chạy DDL `docs/architecture/data/postgres/30-payment.sql`, sau đó `services/99-table-prefix.sql`, và cấp USAGE schema cùng SELECT/INSERT/UPDATE bảng cho app. Không dùng Hibernate để tạo bảng; không chạy lại initializer trên DB hiện có. Có thể dùng PostgreSQL 16.15 trong container tạm với port riêng, không mount volume production/dev hiện tại.

Đặt các biến sau trong phiên kiểm; lấy credential từ môi trường của DB test, không commit hoặc in password:

- `PAYMENT_TEST_DB_URL`: JDBC URL instance test riêng.
- `PAYMENT_TEST_DB_USERNAME`: application role trong instance test.
- `PAYMENT_TEST_DB_PASSWORD`: password của application role.

```powershell
.\mvnw.cmd -Pmapping-tests verify -B -ntp
```

Profile Maven này chạy cả suite thường và test mapping, không skip khi thiếu DB. Spring profile `mapping-test` override danh sách exclusion của smoke test để bật DataSource/JPA; Config Client, Eureka và RabbitMQ được cô lập. `EntityMappingTest` cũng bật `local-foundation` để kiểm listener tắt và DB vẫn hoạt động. Test dùng UUID riêng, transaction rollback khi phù hợp; test concurrent version commit fixture vào DB test để kiểm hai transaction độc lập. Không dùng database dùng chung vì fixture này giữ lại một ít hàng đã commit.

## Cấu hình chạy nền local

Config Server vẫn bắt buộc, mặc định `http://localhost:8888`; Payment mặc định cổng 8083. Không bật local-foundation mặc định.

| Biến | Ý nghĩa |
| --- | --- |
| CONFIG_SERVER_URL | Địa chỉ Config Server; có mặc định localhost:8888 |
| PAYMENT_SERVICE_PORT | Cổng Payment; mặc định 8083 |
| PAYMENT_DB_URL / PAYMENT_DB_USERNAME / PAYMENT_DB_PASSWORD | Kết nối runtime có quyền app, không dùng payment_erd hoặc mặc định maintenance login |
| RABBITMQ_HOST / RABBITMQ_PORT / RABBITMQ_USERNAME / RABBITMQ_PASSWORD | Cấu hình broker được phân phối từ Config Server |
| RABBITMQ_VHOST / RABBITMQ_BUSINESS_EXCHANGE | Mặc định `/` và `business.ex`, thống nhất với Booking trên `dev/son` |
| SPRING_PROFILES_ACTIVE | Đặt local-foundation khi chỉ kiểm nền DB, chưa dùng broker/Eureka |
| JAVA_TOOL_OPTIONS | Đặt -Duser.timezone=UTC cho JVM chạy ứng dụng |

```powershell
$env:SPRING_PROFILES_ACTIVE = 'local-foundation'
.\mvnw.cmd spring-boot:run
```

Đặt credential trong environment trước chạy. Spring không tự đọc file `.env`. Local-foundation tắt Eureka, simple/direct listener auto-start và Rabbit health; không ngăn mã tương lai chủ động gọi broker. Không bỏ security hoặc mở thêm actuator để kiểm ứng dụng.

YAML bootstrap của Payment chỉ chọn tên app và import Config Server. YAML riêng Payment tại Config Server chứa datasource/JPA/RabbitMQ/port; shared config không bị thay đổi. JPA luôn validate, Open Session in View tắt, SQL init tắt. JSONB dùng Jackson3JsonFormatMapper để thống nhất JsonNode với Boot 4; các domain SQL được ghi rõ trong Column mapping.

RabbitMQ dùng cùng cấu hình Booking trên `dev/son`: cổng mặc định 5672, user `flash_ticket_app`, publisher confirm `correlated`, publisher returns và mandatory publish. Simple listener dùng ACK thủ công, prefetch 20; consumer phải ACK sau khi transaction commit. JSON converter dùng JsonMapper của ứng dụng. Message lỗi chuyển đổi bị reject không requeue, kể cả khi có `x-death`, để broker đưa vào DLQ theo policy; log chỉ ghi loại exception, không ghi payload/header.

`spring.rabbitmq.dynamic=false`: Payment không tự khai báo exchange/queue/binding. Dùng broker đã provision bằng bộ `deploy/docker-compose.rabbitmq.yml` và `deploy/rabbitmq/definitions.json` trên `dev/son`; bộ này đã có các queue `payment.finance-configured.q`, `payment.event-cancelled.q`, `payment.start.q`, `payment.decision.q`, `payment.ticket-issuance-failed.q`, `payment.order-completed.q` cùng DLQ/binding/policy. Thay đổi này chuẩn bị cấu hình messaging, chưa bổ sung consumer/outbox hoặc xác minh kết nối broker thật.

## Dùng entity, DTO và mapper

- Entity tương ứng `payment_schema.tbl_*`; Lombok `@Getter`/`@Setter` ở cấp class, `@Setter(AccessLevel.NONE)` ở các association. Association chỉ điều hướng đọc. Gán scalar FK trước persist, không chỉ thêm vào collection. Không cascade hoặc orphanRemoval. Sau thay FK của entity đã load, reload trước dùng association cũ.
- Để `rowVersion` null khi persist entity mới có `@Version`; Hibernate sẽ khởi tạo và quản lý version. ID/time/state khác do phía gọi cung cấp. Constructor JPA protected; entity có setter scalar để task nghiệp vụ sau dùng factory có chủ ý.
- `ContractCodec` nhận JsonMapper và Jakarta Validator, không thay global mapper của ứng dụng. Dùng codec tại biên nhận command/snapshot để kiểm required field kể cả nullable, unknown field lồng nhau, wire token và Bean Validation. Dùng TypeReference cho Envelope có payload cụ thể.
- DTO là record bất biến, collection được copy. Money/version trên wire là String; Instant xuất UTC Z. DTO HTTP trả về nằm ở `dto/response` (gồm Problem/Violation); contract liên service ở `dto/message`, snapshot ở `dto/snapshot`, codec/validation chung ở `dto/common`. `dto/request` dành cho request HTTP khi có endpoint cần body có cấu trúc; chưa tạo public StartPayment request vì POST khởi tạo thanh toán thuộc Booking.
- PaymentStartMapper, RefundMapper, PaymentViewMapper và OrderPaymentSnapshotMapper dùng MapStruct. `PaymentMapperConfig` chọn Spring component model, constructor injection và báo lỗi build khi có field đích chưa map. MapStruct processor cùng Lombok binding được cấu hình trong Maven; không commit các lớp `*Impl` sinh trong target.
- MoneyMapper/VersionMapper và hai converter JSON là Spring component với các hàm chuyển đổi tường minh. Chúng giữ kiểm tiền/version chính xác và contract codec; `MappingConfiguration` tạo codec từ JsonMapper/Validator của Spring. Có thể inject mapper qua constructor, không cần tự `new` hoặc dùng Mappers.getMapper khi chạy ứng dụng.
- Mapper không query association, chọn accepted charge/latest attempt, tính phí/hash hoặc tạo ID/thời gian. Snapshot hash do caller cung cấp. JSON serialization thông thường không phải canonical serializer.
- `PurchaseSnapshotValidator` kiểm tổng nhiều dòng, discount/promotion, item/seat trùng, mode/sector, quantity và khoảng thời gian; `expectedTicketCount` giới hạn int32. Không kiểm hạn hiện tại để snapshot cũ vẫn đọc được khi retry. Ownership, payable và state transition thuộc application use case ở các gói sau.

## Contract boundary P1

Đường đi cho command start: `ContractCodec.read` với `TypeReference<Envelope<StartPayment>>` → `MessageInvariantValidator.validateStartPayment` → `ContractHashService.envelopeHash` → application use case. Codec chỉ kiểm shape/Bean Validation, không tự áp nghiệp vụ snapshot trong mapper hoặc đổi trách nhiệm các fixture shape cũ. `fromTree` không thể phát hiện key trùng đã bị parser trước đó làm mất; raw JSON phải đi qua codec strict.

Inject validator/serializer/hash service bằng constructor. `purchaseHash` kiểm invariant snapshot trước khi hash; `requestHash` chỉ hash actor/orderId/provider, không trim/đổi case subject. `envelopeHash` yêu cầu envelope typed đã qua shape và invariant validation, hash toàn bộ metadata/data; producer retry giữ nguyên envelope, retry count nằm trong transport header.

Canonical v1 dùng UTF-8 strict (từ chối surrogate lẻ bằng INVALID_UNICODE), control escape hex lowercase, key sắp thứ tự, null tường minh, UUID lowercase, Instant UTC Z và wire money/version dạng chuỗi. Chỉ items/seats của purchase được sắp theo ID; array hợp đồng khác giữ thứ tự. Encoder riêng không nhận cấu hình formatting của global JsonMapper. Golden bytes và SHA-256 ở `src/test/resources/contracts/canonical-v1` được tính độc lập bằng Python stdlib; đây là candidate cần Booking review, chưa chứng minh hash liên service đã khớp.

Snapshot chưa có sector kind hoặc seat→ticketType mapping. Validator kiểm tính nhất quán nội bộ: QUANTITY không sector/seat; SEAT_MAP một sector, toàn bộ items có seats đủ quantity hoặc toàn bộ không có seats. Booking vẫn chịu trách nhiệm xác nhận selection khớp cấu hình nguồn. Ở checkpoint P1 chưa có controller/repository; P2 đã bổ sung query API. RabbitMQ/inbox/outbox runtime, provider và Saga vẫn thuộc các gói sau.

## Kết quả kiểm

Kiểm tại worktree scaffold ngày 2026-10-23 với Java 21 và PostgreSQL 16.15 tách biệt:

- `verify -Pmapping-tests`: 57 test, gồm 14 test PostgreSQL và kiểm Spring inject mapper, 0 failure/error/skip; Spotless và SpotBugs PASS.
- Config Server `verify`: PASS; endpoint thật `/payment-service/default` trả cấu hình `ddl-auto=validate`.
- JAR Payment chạy profile `local-foundation` với Config Server và PostgreSQL test: khởi tạo EntityManager thành công, `/actuator/health` trả `UP`.

Các kết quả này chỉ xác nhận scaffold và các kiểm đã mô tả, chưa nghiệm thu thanh toán/E2E. Khi thiếu PostgreSQL/JDK đúng, báo NOT_RUN cùng nguyên nhân. Test mock không thay được kiểm mapping trên PostgreSQL thật.

Review độc lập trước P1 đã rà mô hình/quan hệ, contract và profile. Sau review đã bổ sung lỗi timestamp/enum có đường dẫn field, metadata cho 2 EmbeddedId/5 version/12 association, confirmation mismatch/unique, khóa ngoại khác order/currency, JSONB reload và nguyên nhân optimistic lock cụ thể. P1 thay hành vi last-value bằng từ chối duplicate JSON key trong ContractCodec và bổ sung canonical hashing; thỏa thuận liên parser/hash với Booking vẫn cần kiểm tích hợp riêng.

### Kiểm P1 trên nhánh codex/payment-runtime — 23/10/2026

Maven verify bằng JDK 21/UTC: 64 test, 0 failure/error/skip; Spotless và SpotBugs PASS. Có golden byte/hash độc lập, kiểm Spring bean thật và regression cho Unicode surrogate/control escaping từ review độc lập. Không chạy lại PostgreSQL mapping/RabbitMQ/VNPay/E2E trong P1; chưa có thay đổi runtime các phần đó.

## API đọc trạng thái P2

`GET /api/v1/orders/{orderId}/payment?requestId={requestId}` yêu cầu bearer JWT có chữ ký, issuer, audience, expiry hợp lệ, subject không rỗng và role BUYER trong realm_access.roles. Payment tự kiểm token, không chỉ tin Gateway. Không có quyền bypass ownership cho ADMIN. Cấu hình runtime từ Config Server cần KEYCLOAK_ISSUER_URI, KEYCLOAK_JWK_SET_URI và KEYCLOAK_AUDIENCE; thiếu cấu hình phải fail startup. Không dùng URI test trong production.

Controller gọi PaymentQueryService, năm repository cục bộ và các mapper. Một response dùng transaction REPEATABLE_READ chỉ đọc; latestAttempt theo startedAt/id giảm dần, refunds theo createdAt/id tăng dần. Không truy cập datastore Booking, gọi provider hoặc ghi state từ GET.

- Chưa snapshot: 404 PAYMENT_NOT_READY; khác owner: 404 RESOURCE_NOT_FOUND.
- Không requestId: startRequest=null. Request chưa có inbox: WAITING, dù latestAttempt đã tồn tại.
- Có result bền: đọc đúng requestId/orderId từ inbox consumer `payment.start-requested.v1`. CREATED kiểm attempt thuộc order; REJECTED giữ failureCode.
- Inbox hỏng/không nhất quán: 503 PAYMENT_STATE_UNAVAILABLE; không bịa CREATED hoặc log payload.
- HTTP lỗi dùng Problem; response có X-Correlation-Id và Cache-Control: no-store. orderId hợp lệ ưu tiên làm correlationId, header sai không được echo.

Gateway POST `/api/v1/orders/{id}/payment-attempts` đã sửa về Booking với breaker/fallback Booking. GET `/api/v1/orders/{id}/payment` vẫn về Payment. P2 không mở POST start/refund hay route nội bộ/management/callback. Kết quả health UP trong lịch sử scaffold không có nghĩa actuator đang public ở P2.

## Throw lỗi Payment

`exception.PaymentErrorCode` tập trung mã lỗi, thông báo an toàn và HTTP status mặc định.
Service throw `PaymentException` thay cho việc tự ghép status và chuỗi mã lỗi:

```java
throw new PaymentException(PaymentErrorCode.PAYMENT_NOT_READY);
```

Khi bọc lỗi gốc, truyền cause để giữ thông tin chẩn đoán:

```java
throw new PaymentException(PaymentErrorCode.PAYMENT_STATE_UNAVAILABLE, cause);
```

`PaymentExceptionHandler` chuyển exception thành `Problem` với `code`, `message`,
`correlationId` và `violations`. Response chỉ dùng thông báo trong enum, không đưa
message hoặc cause nội bộ ra client. Security cũng dùng cùng enum.

| Error code | HTTP | Khi dùng |
| --- | --- | --- |
| `PAYMENT_NOT_READY` | 404 | Chưa có snapshot thanh toán cho order |
| `RESOURCE_NOT_FOUND` | 404 | Tài nguyên không thuộc người dùng/order đang truy cập |
| `AUTHENTICATION_REQUIRED` | 401 | Thiếu hoặc không có xác thực hợp lệ |
| `ACCESS_DENIED` | 403 | Không đủ quyền truy cập |
| `INVALID_REQUEST` | 400 | Tham số yêu cầu không hợp lệ |
| `PAYMENT_STATE_UNAVAILABLE` | 503 | Kết quả lưu bị hỏng hoặc trạng thái thanh toán không nhất quán |
| `INTERNAL_ERROR` | 500 | Lỗi không dự kiến |

`ContractValidationException` tiếp tục giữ field và mã vi phạm chi tiết như
`TOTAL_MISMATCH`; handler giữ HTTP 422 với `Problem.code=INVALID_REQUEST` cho loại
lỗi này. `StartRequestView.failureCode` là kết quả command liên service và được giữ
nguyên, không tự chuyển thành exception HTTP. Khi thêm lỗi nghiệp vụ mới, thêm vào
enum và throw `PaymentException`; không cần thêm switch thông báo trong factory.

Kiểm ngày 24/10/2026 bằng JDK 21/UTC: `clean verify` chạy 88 test mặc định,
0 failure/error/skip; Spotless và SpotBugs PASS. Gồm 9 test HTTP mới cho bộ mã lỗi;
chưa chạy lại profile kiểm PostgreSQL trong thay đổi này.

### Kiểm P2

Từ thư mục Payment, dùng JDK 21/UTC như phần Build:

```powershell
.\mvnw.cmd verify -B -ntp
.\mvnw.cmd -Pquery-tests verify -B -ntp
```

Suite mặc định tại checkpoint P2: 79 test, không cần DB/Keycloak thật. Smoke context chỉ import repository mocks từ test configuration; product code không có fallback mock. JWT HTTP tests dùng RSA token ký thật, JWK server loopback tạm và decoder/filter thật; không dùng mock JWT để kết luận chữ ký/audience đúng.

Profile query-tests: 98 test, gồm 14 mapping test và 5 repository/transaction test trên PostgreSQL 16.15 riêng; cần PAYMENT_TEST_DB_URL/USERNAME/PASSWORD theo hướng dẫn mapping test. Profile mapping-tests cũng mở cùng nhóm DB tests; không chạy hai profile lặp để tăng số kiểm. Missing DB là lỗi, không tự skip. Đã kiểm tie ordering, scalar projection không load graph, PK inbox, response nhất quán khi insert đồng thời, và so toàn bộ dữ liệu 13 bảng trước/sau GET.

Maven verify của Payment, Gateway và Config Server đều PASS cùng Spotless/SpotBugs. Gateway có test predicate method/path cho start POST và payment GET. Test fixture không cần thông tin user thật; không lưu credential/token production vào repository.

Chạy thử Payment JAR qua HTTP ngoài bộ test: NOT_RUN vì automatic approval review từ chối script chạy thử với thông báo “blocked by policy”, không có lý do chi tiết. Config Server test process đã khởi động; các helper do lượt này tạo đã dừng. Kết quả ký JWT/HTTP và PostgreSQL ở trên là bằng chứng kiểm riêng, chưa phải end-to-end qua Gateway/Keycloak/Booking/Ticket thật. RabbitMQ/VNPay/Saga E2E chưa triển khai ở P2.
