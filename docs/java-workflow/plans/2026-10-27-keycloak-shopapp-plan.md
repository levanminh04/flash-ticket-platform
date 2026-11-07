# Plan — viết lại SPI Keycloak theo Shop App

- Ngày: 2026-10-27.
- Thiết kế: [2026-10-27-keycloak-shopapp-design.md](../specs/2026-10-27-keycloak-shopapp-design.md).
- BRAINSTORMING: **APPROVED**, user: “oke code đi”.
- WRITING-PLANS: **APPROVED**, user: “xác nhận” cho cùng danh sách ở cả hai pha.
- EXECUTING-PLANS: **APPROVED**, cùng phản hồi “xác nhận” trước khi ghi Java.
- Nền là working tree hiện tại, gồm sửa đổi chưa commit; không reset publisher về HEAD.
- Phạm vi: `services/keycloak-spi`; kiểm consumer User bằng test hiện có. Không triển khai server trong plan này.

## 1. Danh sách duyệt của plan và thực thi

Dùng nguyên C1–C8, M1–M8, R1–R10 và X1–X3 trong §3–6 thiết kế, không thêm mục mới. Hai gate WRITING-PLANS và EXECUTING-PLANS phải được user xác nhận riêng; có thể trả lời duyệt cả hai trong một phản hồi.

| ID | File/class | Đề xuất |
|---|---|---|
| C1 | `RegistrationEventListener` | `@RequiredArgsConstructor`; final session/outboxStore/enabled; bỏ hai constructor tay, giữ callback, tạo DTO bằng builder |
| C2 | `RegistrationEventListenerFactory` | Giữ lifecycle/env/native worker; create trả C1 trực tiếp, bỏ anonymous no-op listener |
| C3 | `RabbitMqOutboxDeliveryHandler` | `@RequiredArgsConstructor`; giữ connection mutable, synchronized, confirm/return và try-with-resources hiện tại |
| C4 | `UserCreatedV1` | record → class với getter, constructor kiểm dữ liệu và builder; JsonCreator/JsonProperty bảo đảm deserialize qua constructor |
| C5 | `UserCreatedV1.UserCreatedV1Builder` | Lombok sinh, không có file riêng |
| C6–C8 | Ba class test SPI hiện có | Chỉ sửa thân method hiện có; không thêm class hoặc method test |

Các field C4 theo thứ tự constructor: `int schemaVersion, UUID eventId, String eventType, Instant occurredAt, String realmId, String identitySubject, String displayName`.

| ID | Chữ ký mới/đổi | Cách sinh/viết |
|---|---|---|
| M1 | `public RegistrationEventListener(KeycloakSession session, OutboxStore outboxStore, boolean enabled)` | Lombok `@RequiredArgsConstructor` |
| M2 | `public RabbitMqOutboxDeliveryHandler(ConnectionFactory factory)` | Lombok; đổi constructor package-private hiện tại sang public |
| M3 | `public UserCreatedV1(int schemaVersion, UUID eventId, String eventType, Instant occurredAt, String realmId, String identitySubject, String displayName)` | Viết constructor thường, giữ guard record cũ; `@JsonCreator`, tham số `@JsonProperty`, constructor có `@Builder` |
| M4 | `public int getSchemaVersion()`, `public UUID getEventId()`, `public String getEventType()`, `public Instant getOccurredAt()`, `public String getRealmId()`, `public String getIdentitySubject()`, `public String getDisplayName()` | Bảy getter Lombok `@Getter` |
| M5 | `public static UserCreatedV1Builder builder()` | Lombok |
| M6 | `UserCreatedV1Builder()` package-private | Lombok |
| M7 | `public UserCreatedV1Builder schemaVersion(int value)`, `eventId(UUID value)`, `eventType(String value)`, `occurredAt(Instant value)`, `realmId(String value)`, `identitySubject(String value)`, `displayName(String value)` | Bảy fluent method; đều public, trả UserCreatedV1Builder |
| M8 | `public UserCreatedV1 build()`, `public String toString()` trên builder | Lombok; không log payload qua toString |

R1–R10 giữ nguyên chữ ký như §5 thiết kế, chỉ sửa thân method cho tương thích constructor/builder và readability. Không thêm setter, no-args DTO constructor, equals/hashCode, mapper, validator class, helper hoặc callback class mới.

Ngoại lệ X1–X3: API Keycloak/Rabbit theo runtime; JsonCreator giữ guard constructor; Lombok1.18.46 chỉ compile/provided + annotation processor. Giữ release21, Keycloak26.8.0 và AMQP5.26.0; không thêm Spring vào SPI.

## 2. Các bước thực hiện

### Bước 1 — DTO và build

- File: `services/keycloak-spi/pom.xml`, `src/main/java/com/flashticket/keycloak/UserCreatedV1.java`, `src/test/java/com/flashticket/keycloak/UserCreatedV1ContractTests.java`.
- Item: C4/C5/C8, M3–M8, X2/X3.
- Thêm Lombok provided và annotation processor1.18.46 rõ ràng; không copy vào thư mục dependency runtime.
- Chuyển record thành class với bảy field private; giữ TYPE. Dùng @Getter; @JsonInclude NON_NULL ở class, @JsonFormat STRING cho occurredAt.
- Constructor M3 giữ toàn bộ guard hiện có, gán từng field; @JsonProperty giữ nguyên tên JSON và @JsonCreator chọn đúng đường deserialize; @Builder đặt trên constructor.
- Thêm kiểm builder/giá trị getter vào thân test hiện có. Không tạo method test mới. Fixture cũ, timestamp UTC, omit displayName và reject missing/typo/version/type vẫn phải đạt.
- Chạy tại module SPI với JDK23 có sẵn, compile release21: `rtk proxy mvn -B -ntp "-Dtest=UserCreatedV1ContractTests" test`. Mong đợi 3 test đạt; lỗi compile hoặc guard là lỗi cần sửa, không hạ assertion để xanh.

### Bước 2 — Listener và factory

- File: `RegistrationEventListener.java`, `RegistrationEventListenerFactory.java`, `RegistrationEventListenerTests.java` trong source/test SPI hiện có.
- Item: C1/C2/C6, M1, R1–R6/R8, X1.
- Listener dùng @RequiredArgsConstructor cho session/outboxStore/enabled; bỏ hai constructor cũ. onEvent trả ngay khi disabled, sau đó chạy filter và transaction guard hiện có.
- Dùng biến có tên rõ và UserCreatedV1.builder() thay chuỗi đối số. Giữ enqueue cùng session, eventId/correlationId và rollbackOnly khi capture lỗi.
- Factory create(session) trả new RegistrationEventListener(session, new OutboxStore(session), enabled). Bỏ anonymous no-op class. Không giữ request session trong factory.
- Giữ postInit callback/native timer/backoff/retention/close; không thêm schedule method hoặc config wrapper. Thay var bằng kiểu/tên rõ nếu giúp đọc, không thay cơ chế đồng bộ.
- Sửa lời gọi constructor trong test hiện có. Bổ sung disabled vào thân ignoresOtherEventsAndNonFormRegistrations(), không thêm @Test method.
- Chạy: `rtk proxy mvn -B -ntp "-Dtest=RegistrationEventListenerTests" test`. Mong đợi 6 test đạt; disabled không enqueue và capture lỗi vẫn setRollbackOnly.

### Bước 3 — Publisher, test tổng và tài liệu

- File: `RabbitMqOutboxDeliveryHandler.java`, `RabbitMqOutboxDeliveryHandlerTests.java`, `services/keycloak-spi/HANDOFF.md`.
- Item: C3/C7, M2, R7/R9.
- Thay constructor bằng @RequiredArgsConstructor. Giữ synchronized và callback AtomicBoolean; không thêm getter/connection manager.
- Lấy bản try-with-resources/waitForConfirmsOrDie hiện tại làm nền. Giữ mandatory, persistent, stable messageId, no-route check, timeout/interrupt/retry và mã lỗi an toàn; không đổi ngược code chưa commit.
- Giữ toàn bộ method test publisher hiện có, gồm channelCloseFailureRemainsRetryable().
- Chạy SPI: `rtk proxy mvn -B -ntp clean verify`. Mong đợi toàn bộ test hiện có đạt (hiện tại 14), JAR thường release21; dependency runtime chỉ có amqp-client.
- Kiểm wire consumer bằng các test User hiện có: tại `services/user-service`, chạy `rtk proxy mvn -B -ntp "-Dtest=IdentityUserCreatedListenerTests,IdentityUserRegistrarTests" test`. Mong đợi 6 test đạt, không cần DB/broker thật cho các unit test này.
- Cập nhật HANDOFF ngắn về class/getter/builder và ranh giới Keycloak/Spring. Giữ comment tiếng Việt theo ý nghĩa, không comment lặp từng dòng.
- Rà diff: không thêm class/method/annotation/dependency ngoài danh sách. Nếu buộc phát sinh, dừng phần phụ thuộc và trình delta để user duyệt.

## 3. Giới hạn và nghiệm thu

Hoàn tất khi SPI build/test đạt, payload tương thích fixture/consumer và diff khớp bảng duyệt. Không suy kết quả này thành bằng chứng fault-test atomicity mới hoặc provider đã được deploy lên119.

Không đổi JSON version/routing, schema DB, ownership, cơ chế outbox/ACK hoặc cấu trúc User Service. Không tự commit/push/merge/redeploy trong phạm vi refactor này.

## 4. Bản ghi xác nhận

- BRAINSTORMING: APPROVED — phản hồi user “oke code đi” sau khi đã trình thiết kế.
- WRITING-PLANS: APPROVED — user “xác nhận” sau câu hỏi duyệt cùng bảng C/M/R/X cho cả hai pha.
- EXECUTING-PLANS: APPROVED — cùng phản hồi trên, cho phép ghi Java theo bảng đã trình.
- Delta: chưa có. Nếu có chỉ xin duyệt mục mới/đổi, không hỏi lại các mục không đổi trong cùng pha.

## 5. Kết quả thực thi — 2026-10-27

- Bước 1: **DONE**. DTO class `@Getter`, constructor `@JsonCreator/@JsonProperty` và `@Builder`; Lombok provided + processor. Test contract 3/3 đạt. JSON sau builder khớp toàn bộ fixture; sai subject/version/type vẫn bị từ chối; builder thiếu subject cũng bị từ chối.
- Bước 2: **DONE**. Listener constructor Lombok nhận session/outboxStore/enabled, tạo message qua builder; factory bỏ anonymous no-op class và truyền cùng session. Test listener 6/6 đạt, gồm disabled không đụng session/outbox và capture lỗi đánh dấu rollbackOnly.
- Bước 3: **DONE**. Publisher constructor Lombok; giữ connection, synchronized, mandatory/confirm/return và try-with-resources từ working tree. HANDOFF đã cập nhật cách đọc code và ranh giới framework.
- Baseline trước refactor: SPI 14 test đạt. Sau refactor: `mvn -B -ntp -f services/keycloak-spi/pom.xml clean verify` — **BUILD SUCCESS**, 14 test, 0 failure/error/skipped (5 publisher, 6 listener, 3 contract).
- Consumer: `mvn -B -ntp -f services/user-service/pom.xml "-Dtest=IdentityUserCreatedListenerTests,IdentityUserRegistrarTests" test` — **BUILD SUCCESS**, 6 test, 0 failure/error/skipped. Không sửa production User Service trong lượt refactor này.
- JDK23 biên dịch release21: kiểm JAR có class major65; đúng bốn class production và một builder; không còn anonymous listener, không có BOOT-INF hay package Lombok. `target/dependency` chỉ có `amqp-client-5.26.0.jar`.
- `javap` đối chiếu constructor/getter/builder với M1–M8: khớp danh sách đã duyệt, không phát sinh class/phương thức ngoài plan. Giữ nguyên các method test hiện có; chỉ bổ sung assertion trong thân method.
- Log test SPI vẫn có thông báo JBoss LogManager và cảnh báo Mockito/Java agent giống baseline; các test và build đều hoàn tất thành công. Không thay logging runtime trong phạm vi này.
- Log local (ignored): `tmp/shopapp-spi-baseline.log`, `tmp/shopapp-spi-contract.log`, `tmp/shopapp-spi-listener.log`, `tmp/shopapp-spi-verify.log`, `tmp/shopapp-user-tests.log`.
- Giới hạn: chưa deploy bản refactor lên119, chưa chạy lại đăng ký thật/MongoDB hoặc fault-test trên server; không commit/push/merge. Không suy unit test rollback thành bằng chứng transaction DB thực tế.
- Delta so với danh sách đã duyệt: **không có**.

## 6. Follow-up — consumer dùng ObjectMapper trực tiếp

- Yêu cầu user: “dùng objectmapper để decode cái message từ rabbitmq chứ k dùng function code tay”. Sau khi giải thích khác biệt định dạng, user chọn: **“Dùng ObjectMapper chuẩn, code gọn”**.
- Phạm vi bổ sung được user yêu cầu: `IdentityUserCreatedListener`, thân các method test hiện có trong `IdentityUserCreatedListenerTests`, và tài liệu. Không tạo class/phương thức/annotation/dependency mới; giữ nguyên chữ ký constructor và các cấu hình bean.
- Đã bỏ `decode(Message)`, whitelist FIELDS và phần duyệt JsonNode/regex/type bằng tay. `onMessage` gọi `ObjectMapper.readValue(message.getBody(), UserCreatedV1.class)` trực tiếp, dùng mapper kế thừa cấu hình Jackson chung của User.
- Giữ giới hạn body64KiB, kiểm routing key/realm, constructor guard DTO, từ chối field lạ/sai kiểu, không tin __TypeId__. Không thay thao tác Mongo, idempotency, ACK, retry hoặc DLQ.
- Theo lựa chọn user, consumer chấp nhận displayName null/không có, UUID Base64 được Jackson hỗ trợ và timestamp ISO8601 có offset; UUID/Instant sau parse tương đương giá trị canonical. Không tự viết deserializer hoặc thêm JSON Schema validator. SPI vẫn phát canonical UUID, UTC Z và bỏ displayName khi không có; schema/fixture producer giữ nguyên.
- Kiểm chứng: baseline6/6 và sau thay đổi6/6 test listener/registrar đạt, **BUILD SUCCESS**. Các assertion bổ sung nằm trong method test hiện có, kiểm ACK sau ghi với cả fixture canonical và các dạng Jackson được user duyệt; input lỗi vẫn reject, lỗi DB/ACK vẫn giữ hành vi cũ.
- Spotless check đúng hai file Java thay đổi và `git diff --check` đạt. Log local: `tmp/identity-objectmapper-baseline.log`, `tmp/identity-objectmapper-tests.log`, `tmp/identity-objectmapper-format-check.log`. Chưa triển khai consumer mới lên119.

## 7. Follow-up — nạp realm ID trong Listener theo Shop App

- User yêu cầu: “cứ load @value ở phía Listener cho tôi, theo chuẩn convention shop app. Rồi update cả 3skill trên”.
- Đã đối chiếu trực tiếp `JwtTokenUtil` và `JwtTokenFilter` của Shop App: cả hai đặt property `@Value` trên field, trong khi dependency vẫn theo constructor injection.
- Listener giữ constructor injection cho `JsonMapper`, registrar và retry; `realmId` nay là field `@Value("${KEYCLOAK_IDENTITY_REALM_ID}")`. Config bean không nhận rồi chuyền tiếp `realmId` nữa. Spring xử lý field trên object được trả từ method `@Bean`.
- Test helper hiện có gán field bằng `ReflectionTestUtils` vì helper tự gọi `new`; không thêm class hay method test.
- Đã cập nhật ba `SKILL.md` để nhận diện pattern này; reference Shop App chung có thêm nguồn mẫu và lưu ý vòng đời bean.
- Kiểm tra `git diff --check`; `mvn -DskipTests test-compile` **BUILD SUCCESS** (51 source production, 17 source test compiled). Spotless check ba file Java **BUILD SUCCESS**. Skill Creator `quick_validate.py` báo cả ba skill hợp lệ. Không chạy test trong lượt này. Chưa deploy lên119.

## 8. Follow-up — inject ObjectMapper qua constructor

- User yêu cầu đổi tham số constructor từ `JsonMapper` sang `ObjectMapper` theo ví dụ đã trình. Config bean cũng nhận `ObjectMapper` và truyền trực tiếp cùng `RetryTemplate` vào Listener.
- Không thêm class hay method; `realmId` vẫn dùng `@Value` trên field.
- Chạy lại `IdentityUserCreatedListenerTests` và `IdentityUserRegistrarTests`: 6/6 đạt, BUILD SUCCESS. `git diff --check` đạt. Chưa deploy lên119.

## 9. Follow-up — Spring AUTO acknowledgement

- Theo yêu cầu user, factory của listener identity dùng `AcknowledgeMode.AUTO`, listener bỏ tham số `Channel` và tự động ACK khi method xử lý thành công.
- Dữ liệu sai hoặc ghi MongoDB thất bại sau retry ném `AmqpRejectAndDontRequeueException` với mã lỗi an toàn; message được route tới DLQ khi broker có cấu hình DLX. Không thay đổi retry, upsert hay unique index. Factory chung ngoài identity vẫn giữ cấu hình riêng.
- Sửa thân test hiện có và assertion factory; bỏ test ACK thủ công không còn áp dụng. Test `IdentityUserCreatedListenerTests`, `IdentityUserRegistrarTests`, `RuntimeConfigurationTests`: 7/7 đạt. Đây là unit/config test; chưa nghiệm thu ACK/DLQ với broker thật hoặc deploy lên119.

## 10. Follow-up — làm rõ ensureRegistered

- Review đối chiếu `CategoryService.createCategory` trong Shop App: code Shop App tạo object rồi gọi repository; `ensureRegistered` dùng MongoDB driver trực tiếp để giữ insert-only upsert và kiểm soát write concern, khác mẫu service/repository của Shop App.
- Viết lại thân method hiện có: tên `newUser`, `registeredAt`, `existingUser` và các bước dựng dữ liệu, upsert, xử lý duplicate rõ ràng hơn. Giữ điều kiện duplicate, BSON/schema và bảo đảm idempotency. Không thêm class hay method.
- `IdentityUserRegistrarTests` và `IdentityUserCreatedListenerTests`: 5/5 đạt; Spotless và `git diff --check` đạt. Các test này không chứng minh lại chạy đồng thời với MongoDB thật; chưa deploy.

## 11. Follow-up — Spring Data MongoTemplate

- Theo yêu cầu user, thay phần ghi MongoDB driver trực tiếp bằng `MongoTemplate`, `Query`, `Criteria`, `Update.setOnInsert` và `upsert` theo tên collection. Kiểm tra unique index bằng `indexOps`; không thêm class hoặc method.
- Template identity riêng được tạo trong bean hiện có, dùng cùng MongoDatabaseFactory và converter, cấu hình write concern `w=1`, journal, timeout 5s. Các chức năng User khác giữ template cũ.
- Dùng overload theo tên collection để không tự tăng `@Version` khi message gửi lại; giữ check duplicate key đúng index và xác minh document đã tồn tại. Spring dịch exception sang `DuplicateKeyException`; vẫn kiểm tra MongoDB cause trước khi bỏ qua.
- Unit/config: 7/7 test đạt, gồm assertion update insert-only và xử lý duplicate trong test hiện có. Test opt-in `IdentityUserRegistrarLiveIT` trên MongoDB119: 1/1 đạt, 8 lượt xử lý đồng thời tạo 1 document, gửi lại không sửa `displayName`, `_id`, `createdAt`, `version`; test chỉ tạo/xóa document subject ngẫu nhiên của chính nó.
- Tunnel MongoDB tạm thời chỉ mở trong lúc chạy test và đã đóng. Spotless đạt; chưa deploy code lên119. Không fault-inject MongoDB/broker trên server dùng chung.

## 12. Follow-up — tìm user rồi save qua repository

- User chọn dùng `UserRepository.findByIdentitySubject(String)` và `save`. Đã bổ sung phương thức find đã trình cho user; `IdentityUserRegistrar` tạo `UserDocument` và lưu bằng repository, giữ unique index cùng kiểm tra hồ sơ sau duplicate.
- Để giữ write concern journal/w1/timeout5s cho riêng identity, bean hiện có tạo repository từ `MongoRepositoryFactory` trên template identity riêng; không đổi write concern cho các repository khác. Converter dùng chung của ứng dụng vẫn loại `_class`.
- MongoDB119 thực tế trả duplicate key với `details={}`: khi có `keyPattern` khác identitySubject, lỗi được ném tiếp; khi không có thông tin index, chỉ coi là thành công nếu tìm lại được hồ sơ hợp lệ cùng subject. Đây là xác minh kết quả nghiệp vụ, không chứng minh trực tiếp index nào gây lỗi.
- Test đầu trên119 phát hiện trường hợp thiếu `keyPattern` và thất bại; sau khi xử lý, test lại 1/1 đạt với 8 lượt đồng thời, redelivery không ghi đè và chỉ tạo/xóa document subject ngẫu nhiên. Unit/config 7/7 đạt, gồm trường hợp metadata thiếu, user không tồn tại, index khác và user đã có.
- Tunnel MongoDB tạm đã đóng. Chưa deploy code lên119. Không fault-inject server dùng chung.

## 13. Follow-up — xử lý duplicate bằng find

- User chọn đơn giản hóa: bắt `DuplicateKeyException`, tìm theo `identitySubject`; tìm thấy user thì kết thúc thành công, không tìm thấy thì ném lỗi ban đầu. Bỏ kiểm tra `keyPattern` và các field của document; do đó nhánh này xác nhận sự tồn tại của user, không xác định index gây lỗi hay kiểm tính hợp lệ toàn bộ document.
- Unit/config 7/7 đạt; `IdentityUserRegistrarLiveIT` trên MongoDB119 1/1 đạt với 8 lượt đồng thời và redelivery. Test chỉ tạo/xóa document của chính nó; tunnel tạm đã đóng. Chưa deploy code lên119.

## 14. Follow-up — Spring @Retryable

- User đồng ý thay `RetryTemplate` bằng `@Retryable` trên `IdentityUserRegistrar.ensureRegistered`. Đã bật `@EnableResilientMethods`, bỏ `final` trên registrar để Spring tạo proxy và bỏ `RetryTemplate` khỏi constructor/listener.
- Giữ 5 loại exception đã có và maxRetries2, delay1s, multiplier5, maxDelay5s. Lỗi sau khi hết retry vẫn được listener chuyển thành `AmqpRejectAndDontRequeueException`; cấu hình AUTO ACK không đổi.
- Sửa thân test listener hiện có để chạy registrar qua Spring proxy trong context có `IdentityMessagingConfig` thực tế: kiểm lỗi tạm thời 3 lần, lỗi ngoài danh sách 1 lần và phục hồi sau retry. Unit/config 7/7 đạt; Spotless đạt. Chưa fault-test lỗi MongoDB/broker thật và chưa deploy code mới lên119.

## 15. Follow-up — bỏ kiểm tra index khi khởi động

- User yêu cầu bỏ `verifyIdentityIndex` vì unique index đã do setup MongoDB tạo. Đã bỏ method, `initMethod` và dependency `MongoTemplate` không còn dùng trong registrar; MongoDB setup vẫn định nghĩa index `uq_users_identity_subject`.
- Unit/config 7/7 đạt; Spotless và `git diff --check` đạt. Consumer không còn tự phát hiện môi trường thiếu unique index khi khởi động; code chưa deploy lên119.
