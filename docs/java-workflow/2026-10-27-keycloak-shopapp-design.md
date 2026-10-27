# Thiết kế nháp — viết lại SPI Keycloak theo Shop App

- Ngày: 2026-10-27.
- Pha: **BRAINSTORMING**.
- Gate: **APPROVED cho BRAINSTORMING** — user phản hồi “oke code đi” sau khi xem đề xuất C1–C8/M1–M8/R1–R10/X1–X3.
- Nguồn yêu cầu: user yêu cầu dùng ba skill ShopApp để viết lại code Keycloak hôm qua; trước đó xác nhận duyệt mọi class/phương thức mới, kể cả theo mẫu.
- Nền: working tree hiện tại trên `codex/keycloak-registration-outbox`, HEAD `bebbbf7` cộng thay đổi chưa commit. Có publisher dùng try-with-resources / waitForConfirmsOrDie và test close failure; lấy bản đang có làm nền, không reset về HEAD.
- Đây là bản đề xuất để duyệt; chưa sửa application/test Java hoặc cấu hình runtime.

## 1. Kết quả và phạm vi đề xuất

Viết lại phần `services/keycloak-spi` để hình dạng code quen với Shop App: class DTO có field/getter/builder rõ tên, constructor injection bằng Lombok ở các class phù hợp, tên biến trực tiếp, method xử lý một nhiệm vụ và comment tiếng Việt ở điểm quan trọng.

Phạm vi đầu tiên là bốn type production của SPI cùng ba class test đang có và POM/HANDOFF liên quan. User Service được đối chiếu như consumer của JSON; chưa đề xuất viết lại consumer trong phạm vi này vì wire contract không đổi. Nếu user muốn cả consumer, cần mở danh sách class/method riêng trước khi chốt thêm scope.

Luồng phải tiếp tục đúng: form REGISTER thành công của flash-ticket → native outbox trong transaction Keycloak → worker native → RabbitMQ confirm/route → consumer hiện có tạo hồ sơ MongoDB idempotent.

Đây là refactor hình dạng Java. Không dùng refactor này làm bằng chứng mới cho các ca rollback/crash chưa được fault-test hôm qua.

## 2. Cách ít thay đổi nhất và mẫu tham chiếu

Nguồn Shop App user đã xác nhận: `C:/Users/sondimid/IdeaProjects/shopapp-backend/shopapp-backend/shopapp`.

| Cách viết đề xuất | Mẫu thật đã đọc |
|---|---|
| Dependency private final, `@RequiredArgsConstructor` | `services/impl/ProductService.java`, `OrderService.java` |
| DTO là class, Lombok getter và builder, tạo object bằng tên field | `DTO/ProductDTO.java`, `responses/ProductResponse.java` |
| Method nghiệp vụ trực tiếp, biến rõ tên, không thêm tầng framework | `ProductService.createProduct`, `CategoryService.createCategory` |

Lựa chọn đề xuất: dùng lại bốn type SPI, không tạo thêm service/repository/interface nghiệp vụ để mô phỏng CRUD. `OutboxStore`/drainer đã là persistence/worker của nền tảng. Thêm wrapper quanh chúng chỉ làm nhiều file và thêm API phải đọc.

Hai phần thực sự rút gọn: thay constructor viết tay của dependency bằng Lombok; bỏ anonymous no-op listener khi disabled, thay bằng cờ `enabled` trong listener bình thường. DTO chuyển từ record sang class với builder để không cần đoán thứ tự bảy đối số khi tạo payload.

Không gắn `@Service` hoặc `@Transactional` Spring vào plugin chạy trong Keycloak. Theo [EventListenerProvider26.8.0](https://www.keycloak.org/docs-api/26.8.0/javadocs/org/keycloak/events/EventListenerProvider.html), callback tham gia transaction Keycloak; việc lập lịch dùng [TimerProvider26.8.0](https://www.keycloak.org/docs-api/26.8.0/javadocs/org/keycloak/timer/TimerProvider.html). Các API nền tảng này cần user duyệt là ngoại lệ ngoài Shop App.

## 3. Danh sách class cần duyệt

Các file production ở `services/keycloak-spi/src/main/java/com/flashticket/keycloak/`; test ở `services/keycloak-spi/src/test/java/com/flashticket/keycloak/`.

| ID | Class/file | Trạng thái | Đề xuất và API sinh | Mẫu/ngoại lệ |
|---|---|---|---|---|
| C1 | `RegistrationEventListener.java` | EXISTING, đổi constructor | `@RequiredArgsConstructor`; dependency final `KeycloakSession session`, `OutboxStore outboxStore`, `boolean enabled`. Check enabled trước xử lý; dùng DTO builder; giữ callback | Constructor như ProductService; EventListenerProvider là X1 |
| C2 | `RegistrationEventListenerFactory.java` | EXISTING, sửa thân method | Giữ lifecycle factory, private env/required, native timer; `create` trả C1 trực tiếp thay anonymous class. Dùng local variable kiểu/tên rõ hơn; không thêm helper | X1 do runtime Keycloak |
| C3 | `RabbitMqOutboxDeliveryHandler.java` | EXISTING, đổi constructor | `@RequiredArgsConstructor` cho final ConnectionFactory; giữ mutable Connection, synchronized, try-with-resources, confirm/return và mã lỗi an toàn | Lombok như ProductService; handler/callback Rabbit là X1 |
| C4 | `UserCreatedV1.java` | EXISTING, record → class | `@Getter`; constructor đầy đủ có `@Builder` và `@JsonCreator`; `@JsonProperty` đặt tên từng tham số, `@JsonInclude` và `@JsonFormat` giữ JSON. Không sinh setter/equals/hashCode/toString của DTO vì payload không được cập nhật sau tạo | Class/getter/builder theo ProductDTO/ProductResponse; lựa chọn rút annotation cần duyệt; JsonCreator là X2 |
| C5 | `UserCreatedV1.UserCreatedV1Builder` | NEW, Lombok sinh, không có file riêng | Builder cho bảy field của C4; API liệt kê bên dưới | `@Builder` trong ProductDTO/ProductResponse |
| C6 | `RegistrationEventListenerTests.java` | EXISTING | Sửa lời gọi constructor trong test hiện có; mở rộng ca ignore để kiểm disabled; không đề xuất method/class test mới | JUnit/Mockito hiện có của đích, ngoài mẫu Java production |
| C7 | `RabbitMqOutboxDeliveryHandlerTests.java` | EXISTING | Dùng lại constructor và các ca confirm/return/timeout/close hiện có; giữ test chưa commit | Test hiện có |
| C8 | `UserCreatedV1ContractTests.java` | EXISTING | Giữ kiểm fixture, UTC, optional displayName, thiếu/sai field/version; kiểm builder trong thân test đang có | Test hiện có |

Field C4: `int schemaVersion`, `UUID eventId`, `String eventType`, `Instant occurredAt`, `String realmId`, `String identitySubject`, `String displayName`. Giữ hằng `TYPE` và giá trị JSON hiện tại.

## 4. Constructor/method mới hoặc đổi hình thức cần duyệt

Không có quyền mở cho helper hoặc method “nếu cần”. Mọi phát sinh ngoài bảng phải hỏi delta.

| ID | Class | Chữ ký/API | Trạng thái | Cách viết |
|---|---|---|---|---|
| M1 | C1 | `public RegistrationEventListener(KeycloakSession session, OutboxStore outboxStore, boolean enabled)` | NEW, Lombok sinh | Thay hai constructor cũ; factory/test truyền dependency rõ ràng |
| M2 | C3 | `public RabbitMqOutboxDeliveryHandler(ConnectionFactory factory)` | CHANGE visibility, Lombok sinh | Thay constructor package-private viết tay; không sinh getter cho connection |
| M3 | C4 | `public UserCreatedV1(int schemaVersion, UUID eventId, String eventType, Instant occurredAt, String realmId, String identitySubject, String displayName)` | CHANGE từ compact record constructor | Giữ guard dữ liệu của constructor cũ, gán field trực tiếp; `@JsonCreator` giữ đường deserialize qua guard; `@Builder` giúp caller không dùng chuỗi đối số |
| M4 | C4 | `public int getSchemaVersion()`; `public UUID getEventId()`; `public String getEventType()`; `public Instant getOccurredAt()`; `public String getRealmId()`; `public String getIdentitySubject()`; `public String getDisplayName()` | NEW, bảy getter Lombok sinh | Thay record accessor; dùng cách gọi getter quen thuộc của Shop App |
| M5 | C4 | `public static UserCreatedV1Builder builder()` | NEW, Lombok sinh | Điểm bắt đầu tạo DTO |
| M6 | C5 | `UserCreatedV1Builder()` package-private | NEW, Lombok sinh | Constructor nội bộ builder, gọi qua M5 |
| M7 | C5 | `public UserCreatedV1Builder schemaVersion(int value)`; `eventId(UUID value)`; `eventType(String value)`; `occurredAt(Instant value)`; `realmId(String value)`; `identitySubject(String value)`; `displayName(String value)` | NEW, bảy fluent method, cùng visibility/return type | Đặt từng field bằng tên |
| M8 | C5 | `public UserCreatedV1 build()`; `public String toString()` | NEW, Lombok sinh | build gọi M3 và guard; toString là API builder sinh mặc định, không dùng để log payload |

Không đề xuất no-args constructor cho C4: nếu Jackson tự tạo bean rồi gán field thì guard constructor có thể bị bỏ qua. Không thêm Bean Validation annotation mà không có nơi thực sự gọi validation trong runtime Keycloak.

## 5. Method hiện có được dùng lại

| ID | Class | Chữ ký hiện có | Thay đổi dự kiến |
|---|---|---|---|
| R1 | C1 | `public void onEvent(Event event)` | Thêm check enabled, tạo message bằng builder, đổi tên biến; giữ filter/rollbackOnly/enqueue cùng session |
| R2 | C1 | `public void onEvent(AdminEvent event, boolean includeRepresentation)`; `public void close()` | Giữ callback no-op có comment vì interface yêu cầu |
| R3 | C2 | `public void init(Config.Scope config)` | Giữ env/timeout/lazy broker config; chỉ làm code rõ hơn |
| R4 | C2 | `private static String env(String key, String fallback)`; `private static String required(String key)` | Giữ hai method hiện có; không thêm config class |
| R5 | C2 | `public EventListenerProvider create(KeycloakSession session)` | Truyền session, native store và enabled vào M1; xóa anonymous listener cùng các callback bên trong nó |
| R6 | C2 | `public void postInit(KeycloakSessionFactory factory)`; `public String getId()`; `public void close()` | Giữ đăng ký timer một lần, native worker, dọn resource; callback lambda hiện có được giữ |
| R7 | C3 | `public String entryKind()`; `public synchronized OutboxDeliveryResult deliver(KeycloakSession session, OutboxEntryEntity row)`; `public synchronized void close()` | Giữ logic publish/confirm/return, interrupt và đóng connection; callback return hiện có giữ nguyên |
| R8 | C6 | `void setup()`; `void capturesRegistrationInCurrentSessionAndNormalizesDisplayName() throws Exception`; `void ignoresOtherEventsAndNonFormRegistrations()`; `void ignoresErrorsAndOtherRealms()`; `void enqueueFailureMarksRollbackOnlyEvenIfKeycloakCatchesListenerException()`; `void missingUserOrInvalidIdCannotCommitWithoutOutbox()`; `void missingNameIsOmittedRatherThanNull() throws Exception` | Sửa thân test tương thích M1, kiểm builder và disabled trong ca hiện có |
| R9 | C7 | `void setup() throws Exception`; `void confirmsPersistentMandatoryPublishWithStableId() throws Exception`; `void returnedMessageIsNotDeliveredEvenWithConfirmAck() throws Exception`; `void negativeConfirmAndTimeoutRemainRetryable() throws Exception`; `void channelCloseFailureRemainsRetryable() throws Exception`; `void initialConnectionFailureIsRetriedOnNextDelivery() throws Exception` | Dùng lại; không revert sang waitForConfirms boolean cũ |
| R10 | C8 | `private String fixture() throws Exception`; `void readsSharedFixtureAndPreservesNamesAndUtcTimestamp() throws Exception`; `void rejectsMissingSubjectTypoAndFutureVersion() throws Exception`; `void omitsAbsentDisplayName() throws Exception` | Dùng lại, bổ sung builder assertions trong test hiện có nếu cần |

Các method test R8–R10 có visibility package-private trừ fixture. Giữ annotation JUnit hiện có. Không đề xuất interface, callback class, test class hoặc test method mới nào ngoài C5/M1–M8.

## 6. Ngoại lệ cần user duyệt

- **X1 — runtime Keycloak/RabbitMQ:** dùng lại EventListenerProvider, Factory, OutboxStore, TimerProvider, OutboxDrainerTask, OutboxDeliveryHandler, PostMigrationEvent và callback bất đồng bộ. Shop App không có mẫu tương đương. Không biến chúng thành Spring bean hoặc dùng `@Transactional` Spring. Giữ AtomicBoolean/synchronized ở nơi có callback hoặc shutdown khác thread.
- **X2 — deserialize DTO:** `@JsonCreator` và `@JsonProperty` trên M3 giữ kiểm dữ liệu của constructor thay vì tự viết parser/validator mới; `@JsonInclude/@JsonFormat` đang có giữ omit null và UTC string. Không tự động thêm một lớp JsonDeserializer.
- **X3 — Lombok SPI:** thêm Lombok1.18.46 compile/provided và annotation processor rõ ràng vào POM SPI, như cách dependency được quản lý tại module Java hiện có. Không đóng gói Lombok vào provider runtime, không thêm Spring Boot/ModelMapper.

Nếu X2 cần thêm một method/deserializer để compile hoặc giữ contract khi thực thi, phải dừng và hỏi delta; không xem việc duyệt ngoại lệ là quyền thêm API tùy ý.

## 7. Kiểm chứng và tiêu chí chấp nhận

Dùng test/build hiện có sau khi đã được duyệt thực thi: SPI verify; test contract và consumer hiện có của User để kiểm JSON producer mới vẫn đọc được; không cần tạo stack Docker riêng cho refactor hình dạng code.

Phải giữ: REGISTER/form/realm filter; cùng session/transaction; capture lỗi đánh dấu rollbackOnly; outbox/timer/native retry; stable eventId; persistent/mandatory publish và confirm; optional displayName; UTC string; missing/invalid DTO không lọt qua constructor; consumer JSON và Mongo idempotency hiện hữu.

Kết quả refactor ngày 2026-10-27 đã ghi tại §5 của plan cùng ngày: SPI clean verify 14 test và consumer User 6 test đều đạt. Đây là kiểm chứng local cho code mới; không thay cho kiểm thử end-to-end trên119.

## 8. Xác nhận và chuyển pha

- BRAINSTORMING: C1–C8, M1–M8, R1–R10, X1–X3 — **APPROVED**; nguồn: user phản hồi “oke code đi” sau câu hỏi duyệt thiết kế.
- WRITING-PLANS: **APPROVED**; user “xác nhận” cùng danh sách C/M/R/X cho cả hai pha sau khi xem plan.
- EXECUTING-PLANS: **APPROVED**; cùng phản hồi “xác nhận” trước khi ghi Java theo plan.

User đã yêu cầu dùng cả ba skill, nên sau khi chốt thiết kế có thể chuẩn bị plan; mỗi pha vẫn phải hỏi xác nhận danh sách riêng theo quy tắc user đã yêu cầu. Không có quyền triển khai lại server119 chỉ từ việc duyệt refactor Java.
