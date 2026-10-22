# Payment Service scaffold

Scaffold gồm 13 JPA entity, 2 composite ID, enum, DTO theo B13-v0.2, mapper thủ công và cấu hình. Chưa có endpoint, repository nghiệp vụ, consumer, relay, Saga handler hoặc adapter VNPay. B13-v0.2 vẫn REVIEW_READY; chưa nghiệm thu tích hợp liên service.

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

- Entity tương ứng `payment_schema.tbl_*`; association chỉ điều hướng đọc. Gán scalar FK trước persist, không chỉ thêm vào collection. Không cascade hoặc orphanRemoval. Sau thay FK của entity đã load, reload trước dùng association cũ.
- Để `rowVersion` null khi persist entity mới có `@Version`; Hibernate sẽ khởi tạo và quản lý version. ID/time/state khác do phía gọi cung cấp. Constructor JPA protected; entity có setter scalar để task nghiệp vụ sau dùng factory có chủ ý.
- `ContractCodec` nhận JsonMapper và Jakarta Validator, không thay global mapper của ứng dụng. Dùng codec tại biên nhận command/snapshot để kiểm required field kể cả nullable, unknown field lồng nhau, wire token và Bean Validation. Dùng TypeReference cho Envelope có payload cụ thể.
- DTO là record bất biến, collection được copy. Money/version trên wire là String; Instant xuất UTC Z. Mapper là lớp Java thuần, khởi tạo bằng constructor; wiring Spring thực hiện khi có application handler thật.
- Mapper không query association, chọn accepted charge/latest attempt, tính phí/hash hoặc tạo ID/thời gian. Snapshot hash do caller cung cấp. JSON serialization thông thường không phải canonical serializer.
- Chưa có validator bất biến nhiều dòng/tổng tiền/ghế trùng/ownership/state transition. DTO hợp lệ hình dạng không đồng nghĩa đủ điều kiện thực hiện thanh toán.

## Kết quả kiểm

Kết quả thực tế và giới hạn môi trường được ghi trong phần bàn giao của task; không coi ví dụ lệnh ở đây là bằng chứng PASS. Khi thiếu PostgreSQL/JDK đúng, báo NOT_RUN cùng nguyên nhân. Test mock không thay được kiểm mapping trên PostgreSQL thật.
