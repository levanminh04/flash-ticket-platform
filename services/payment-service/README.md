# Payment Service — scaffold và contract boundary P1

Scaffold gồm 13 JPA entity, 2 composite ID, enum, DTO theo B13-v0.2, mapper MapStruct dạng Spring bean và cấu hình. Chưa có endpoint, repository nghiệp vụ, consumer, relay, Saga handler hoặc adapter VNPay. B13-v0.2 vẫn REVIEW_READY; chưa nghiệm thu tích hợp liên service.

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
| SPRING_PROFILES_ACTIVE | Đặt local-foundation khi chỉ kiểm nền DB, chưa dùng broker/Eureka |
| JAVA_TOOL_OPTIONS | Đặt -Duser.timezone=UTC cho JVM chạy ứng dụng |

```powershell
$env:SPRING_PROFILES_ACTIVE = 'local-foundation'
.\mvnw.cmd spring-boot:run
```

Đặt credential trong environment trước chạy. Spring không tự đọc file `.env`. Local-foundation tắt Eureka, simple/direct listener auto-start và Rabbit health; không ngăn mã tương lai chủ động gọi broker. Không bỏ security hoặc mở thêm actuator để kiểm ứng dụng.

YAML bootstrap của Payment chỉ chọn tên app và import Config Server. YAML riêng Payment tại Config Server chứa datasource/JPA/RabbitMQ/port; shared config không bị thay đổi. JPA luôn validate, Open Session in View tắt, SQL init tắt. JSONB dùng Jackson3JsonFormatMapper để thống nhất JsonNode với Boot 4; các domain SQL được ghi rõ trong Column mapping.

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

Snapshot chưa có sector kind hoặc seat→ticketType mapping. Validator kiểm tính nhất quán nội bộ: QUANTITY không sector/seat; SEAT_MAP một sector, toàn bộ items có seats đủ quantity hoặc toàn bộ không có seats. Booking vẫn chịu trách nhiệm xác nhận selection khớp cấu hình nguồn. P1 chưa thêm controller/repository, RabbitMQ/inbox/outbox runtime, provider hoặc Saga.

## Kết quả kiểm

Kiểm tại worktree scaffold ngày 2026-10-23 với Java 21 và PostgreSQL 16.15 tách biệt:

- `verify -Pmapping-tests`: 57 test, gồm 14 test PostgreSQL và kiểm Spring inject mapper, 0 failure/error/skip; Spotless và SpotBugs PASS.
- Config Server `verify`: PASS; endpoint thật `/payment-service/default` trả cấu hình `ddl-auto=validate`.
- JAR Payment chạy profile `local-foundation` với Config Server và PostgreSQL test: khởi tạo EntityManager thành công, `/actuator/health` trả `UP`.

Các kết quả này chỉ xác nhận scaffold và các kiểm đã mô tả, chưa nghiệm thu thanh toán/E2E. Khi thiếu PostgreSQL/JDK đúng, báo NOT_RUN cùng nguyên nhân. Test mock không thay được kiểm mapping trên PostgreSQL thật.

Review độc lập trước P1 đã rà mô hình/quan hệ, contract và profile. Sau review đã bổ sung lỗi timestamp/enum có đường dẫn field, metadata cho 2 EmbeddedId/5 version/12 association, confirmation mismatch/unique, khóa ngoại khác order/currency, JSONB reload và nguyên nhân optimistic lock cụ thể. P1 thay hành vi last-value bằng từ chối duplicate JSON key trong ContractCodec và bổ sung canonical hashing; thỏa thuận liên parser/hash với Booking vẫn cần kiểm tích hợp riêng.

### Kiểm P1 trên nhánh codex/payment-runtime — 23/10/2026

Maven verify bằng JDK 21/UTC: 64 test, 0 failure/error/skip; Spotless và SpotBugs PASS. Có golden byte/hash độc lập, kiểm Spring bean thật và regression cho Unicode surrogate/control escaping từ review độc lập. Không chạy lại PostgreSQL mapping/RabbitMQ/VNPay/E2E trong P1; chưa có thay đổi runtime các phần đó.
