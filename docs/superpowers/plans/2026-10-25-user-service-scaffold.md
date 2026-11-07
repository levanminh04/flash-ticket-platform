# User Service Scaffold Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Triển khai scaffold đầy đủ cho User Service theo Booking hiện tại, với MongoDB mapping, DTO, feature shell, security, error/logging, RabbitMQ và kiểm chứng có giới hạn rõ ràng.

**Architecture:** Một module Spring MVC, cấu trúc feature như Booking; document/DTO/mapper/config dùng chung trong owner. MongoDB có bốn collection theo B12; controller/service chưa có nghiệp vụ. Keycloak Admin API chỉ được mô tả bằng tài liệu/code mẫu, không nối vào runtime.

**Tech Stack:** Java 21; Spring Boot 4.1.1; Spring Cloud 2025.1.3; Spring Data MongoDB; Jackson 3; Spring Security JWT; Spring AMQP; MapStruct 1.6.3; lombok-mapstruct-binding 0.2.0; JUnit/AssertJ; Testcontainers cho integration opt-in; Spotless/JaCoCo/SpotBugs.

**Spec:** [2026-10-25-user-service-scaffold-design.md](../specs/2026-10-25-user-service-scaffold-design.md). Đọc cả spec lẫn plan trước khi thực hiện; người dùng đã yêu cầu chuyển sang lập plan, chưa yêu cầu chạy plan.

## Global Constraints

- Giữ **Java 21, Spring Boot 4.1.1, Spring Cloud 2025.1.3, MapStruct 1.6.3, lombok-mapstruct-binding 0.2.0**; không sửa Enforcer để dùng JDK khác.
- Giữ package `com.flashticket.user_service`; không import Booking hoặc tạo shared framework/module.
- Chỉ sửa User, central YAML riêng User, `.env.example` khi cần và tài liệu liên quan. Không sửa Gateway, Booking, B12–B16 hoặc hạ tầng đang chạy.
- MongoDB `user_db`: `tbl_users`, `tbl_organizer_applications`, `tbl_organizer_profiles`, `tbl_user_follows`; `_id` là String UUID, Subject là String không trắng, ngày là BSON date, version int/long không âm.
- Không ghi `_class`, BSON null cho field optional hoặc metadata ngoài validator; chỉ users/applications có optimistic `@Version`; không auto-create index/schema, Mongo transaction manager, outbox/inbox hoặc follower counter.
- Controller có root mapping và DI, chưa có handler; service interface/implementation rỗng; không thêm nghiệp vụ, consumer/publisher, cron, Redis, SMTP, Feign, OAuth2 Client hoặc Admin SDK.
- JWT Resource Server vẫn cần chữ ký/issuer/audience/expiry/role; Admin API dùng `user-management` chỉ xuất hiện trong guide cho giai đoạn sau.
- RabbitMQ: `dynamic=false`, MANUAL ACK, prefetch 20, JSON converter và safe error handler; không declare topology hoặc tự suy đoán payload identity.
- Theo Booking `6656f0d`: một `GlobalExceptionHandler`, cấu hình JSON log native và hai hook log nhỏ; không đưa correlation filter hoặc framework observability đã bỏ trở lại.
- Mọi shell command dùng `rtk`; khi có `.codegraph/`, dùng CodeGraph trước khi tìm/đọc code cần hiểu. Không đọc hoặc in secret từ `.env`.
- Tại lúc viết plan, `.gitignore` và việc chuyển MongoDB sang `deploy/mongodb/` có thay đổi sẵn. Không stage chúng vào commit scaffold; nếu dùng worktree, bảo đảm baseline đã có ba file MongoDB ở đường dẫn mới trước khi bắt đầu.
- MongoDB server 119 đã được kiểm tra running/healthy ngày 25/10/2026. Không dùng nó để insert/delete fixture; integration dùng container riêng. Không restart, migrate hoặc đồng bộ server trong plan này.

## Review Focus

1. **Optional BSON và type metadata:** application PENDING/REJECTED và embedded RoleGrant không phát sinh null/`_class`; kiểm ở Task 1 và Task 6.
2. **Subject khác UUID và version bằng 0:** giữ nguyên subject như `idp|buyer-17`, đọc sourceVersion=0, không gán optimistic version cho projection/follow; kiểm ở Task 1, 2, 6.
3. **Matcher public quá rộng:** anonymous chỉ GET profile, không lọt vào follow/admin/route lạ; machine token không thay human role; kiểm ở Task 4.
4. **Tracing header/MDC có dữ liệu lạ:** lỗi vẫn trả đúng status, một correlation hợp lệ khớp body/header/log, không rò sentinel hay field trùng; kiểm ở Task 3.
5. **Fast suite vô tình cần hạ tầng:** context có repository/security nhưng test không gọi Mongo, Rabbit hoặc Keycloak thật; integration không bị chạy/skipped âm thầm trong fast suite; kiểm ở Task 4–6.

---

## Bản đồ file và interface chung

Các đường dẫn Java dưới đây tương đối với `services/user-service/src/main/java/com/flashticket/user_service/` (**M**); đường dẫn test tương đối với `services/user-service/src/test/java/com/flashticket/user_service/` (**T**). Mỗi tên class là một file `.java` riêng. Lệnh Maven chạy từ `services/user-service`.

| Task | File tạo/sửa và trách nhiệm |
|---|---|
| 1 | M `document/{UserDocument,OrganizerApplicationDocument,OrganizerProfileDocument,UserFollowDocument,RoleGrant}.java`; M `enums/{OrganizerApplicationStatus,OrganizerProfileStatus,RoleGrantStatus}.java`; M `config/MongoConfig.java`; M `user/repository/UserRepository.java`, `organizer/repository/{OrganizerApplicationRepository,OrganizerProfileRepository}.java`, `follow/repository/UserFollowRepository.java`; T `persistence/MongoMappingTests.java`, `support/UserFixtures.java`; sửa test YAML/context isolation nếu cần |
| 2 | M `dto/request/{OrganizerApplicationRequest,RejectOrganizerApplicationRequest}.java`, `dto/response/{MeView,OrganizerApplicationView,OrganizerPublicView,OrganizerApplicationPage}.java`; M `mapper/{UserMapper,OrganizerApplicationMapper,OrganizerProfileMapper}.java`, `config/UserJsonConfig.java`; T `dto/UserDtoTests.java`, `mapper/MapperTests.java`; sửa POM cho MapStruct |
| 3 | M `dto/response/Problem.java`, `exception/{ErrorCode,BusinessException,GlobalExceptionHandler}.java`, `config/{UserLogFields,SafeStackTracePrinter}.java`; T `exception/{ErrorCodeTests,HttpErrorHandlingTests}.java`, `config/RcaLoggingTests.java`, `support/LogCapture.java`; sửa logging ở bootstrap/test/central YAML |
| 4 | M `security/KeycloakRealmRoleConverter.java`, `config/SecurityConfig.java`; các controller/service ở bảng feature bên dưới; T `security/{SecurityConfigTests,KeycloakRealmRoleConverterTests}.java`; sửa T `UserServiceApplicationTests.java` để kiểm bean graph feature; JWT/JWKS helper private trong SecurityConfigTests |
| 5 | M `config/RabbitMqConfig.java`, `messaging/UserRabbitErrorHandler.java`; T `config/RuntimeConfigurationTests.java`, `messaging/UserRabbitErrorHandlerTests.java`; sửa POM, bootstrap/test YAML và central YAML User |
| 6 | T `persistence/UserPersistenceIT.java`; sửa POM cho test dependencies/Failsafe và tạo `services/user-service/src/test/resources/application-integration.yaml` |
| 7 | Tài liệu `services/user-service/{TESTING,HANDOFF}.md`, `docs/guides/user-service-keycloak-admin-integration.md`; kiểm `.env.example` và các thay đổi runtime/test của Task 1–6 |

Nguồn field document là spec §6 và `docs/architecture/data/mongodb/schema.cjs`; không rút gọn thành vài field minh họa. File MongoDB đầu vào integration là `deploy/mongodb/{compose.yaml,10-user.js,99-collection-prefix.js}` cùng schema canonical trong docs; đọc, không sửa trong task scaffold.

### Task 1: Mapping BSON và repository cho bốn collection

**Files:** Nhóm Task 1 trong bản đồ; test YAML tại `services/user-service/src/test/resources/application.yaml`.

**Interfaces:**
- Produces: document mutable có getter/setter/no-arg constructor, đúng toàn bộ field ở spec §6; `RoleGrant(String operationId, RoleGrantStatus status, Instant attemptedAt, Instant confirmedAt)` là record embedded, confirmedAt nullable.
- Produces: `OrganizerApplicationStatus {PENDING,ACTIVE,REJECTED}`, `OrganizerProfileStatus {ACTIVE}`, `RoleGrantStatus {REQUESTED,CONFIRMED,FAILED,UNKNOWN}`; bốn repository `extends MongoRepository<đúng Document, String>`, không query riêng.
- Produces: `MongoConfig.mongoTypeMetadataCustomizer(): static BeanPostProcessor`, chỉ đặt `DefaultMongoTypeMapper(null)` trên `MappingMongoConverter`, giữ conversions của Boot; auto index creation=false.
- Produces test support: `UserFixtures.ID`, `SUBJECT="idp|buyer-17"`, `NOW=Instant.parse("2026-10-25T00:00:00.123Z")`; factory `user()`, `pendingApplication()`, `activeApplication()`, `rejectedApplication()`, `profile()`, `follow()` trả document hợp lệ, mỗi factory trả object mới.

- [ ] **Step 1: Viết `MongoMappingTests` dùng actual MappingMongoConverter trong context test không kết nối Mongo.** Có `writesOnlySchemaFields`, `omitsOptionalNullsAndNestedClassMetadata`, `preservesSubjectDatesAndZeroSourceVersion`, `hasFourRepositoriesWithCorrectCollections`. Assertion tiêu biểu:

```java
Document bson = new Document();
converter.write(UserFixtures.pendingApplication(), bson);
assertThat(bson.get("_id")).isInstanceOf(String.class).isEqualTo(UserFixtures.ID);
assertThat(bson).doesNotContainKeys("_class", "roleGrant", "decidedAt", "rejectionReason");
assertThat(bson.get("identitySubject")).isEqualTo("idp|buyer-17");
assertThat(bson.get("createdAt")).isEqualTo(Date.from(UserFixtures.NOW));
```

Kiểm đầy đủ key set cho từng document; kiểm RoleGrant CONFIRMED có confirmedAt và không `_class`; sourceVersion=0 round-trip; mapping metadata chỉ users/applications có version property. Không yêu cầu converter tự làm thay validator DB.
- [ ] **Step 2: Chạy RED.** `rtk proxy .\mvnw.cmd -B -ntp "-Dtest=MongoMappingTests" test`; expected compilation/test failure vì chưa có model/config, không phải lỗi credential hoặc JDK.
- [ ] **Step 3: Tạo document/enum/repository/MongoConfig đúng interface trên.** Ghi ID String tường minh, omit optional null; không thêm BaseDocument, field `_class`, custom schema hoặc query. Factory dùng cho BSON fixture có version=0 để đại diện bản đã lưu; new entity trước lần repository save đầu tiên phải để wrapper `Long version=null` cho Spring Data nhận biết insert. Test converter dùng stub `MongoDatabaseFactory`/client, không driver connection thật; contextLoads hiện có kiểm bean graph feature ở Task 4, repository thật được kiểm ở Task 6.
- [ ] **Step 4: Chạy GREEN và fast suite hiện có.** Chạy lại lệnh Step 2 rồi `rtk proxy .\mvnw.cmd -B -ntp test`; expected 0 failures/errors và không cần external DB. Không dùng mock save để tuyên bố persistence đã pass.
- [ ] **Step 5: Commit riêng nhóm Task 1 sau `rtk git diff --check`.** Dùng skill commit-message-vi, chỉ stage các file của task; đề xuất `feat(user): thêm MongoDB document và repository đúng schema User`.

### Task 2: DTO, strict JSON và MapStruct

**Files:** Nhóm Task 2; sửa `services/user-service/pom.xml`.

**Interfaces:**
- Consumes: document/enum/factory Task 1.
- Produces request records: `OrganizerApplicationRequest(String organizationName, String shortDescription)`, `RejectOrganizerApplicationRequest(String reason)`; các field `@NotBlank`.
- Produces response records: `MeView(String identitySubject, String displayName)`; `OrganizerApplicationView(String applicationId, String identitySubject, String organizationName, String shortDescription, OrganizerApplicationStatus status, String rejectionReason, Instant createdAt, Instant updatedAt)`; `OrganizerPublicView(String organizerSubject, String organizationName, String shortDescription, @PositiveOrZero long followerCount)`; `OrganizerApplicationPage(List<OrganizerApplicationView> items, String nextCursor)` có defensive copy items, nextCursor nullable, theo kiểu page cụ thể như OrderPage của Booking.
- Produces: `UserMapper.toView(UserDocument): MeView`; `OrganizerApplicationMapper.toView(OrganizerApplicationDocument): OrganizerApplicationView`; `OrganizerProfileMapper.toPublicView(OrganizerProfileDocument profile, long followerCount): OrganizerPublicView`.
- Produces: `UserJsonConfig.userJsonMapperCustomizer(): JsonMapperBuilderCustomizer` cho Jackson 3; unknown properties và coercion sai kiểu bị reject. Không bật global rule làm mất optional/nullable hợp lệ.

- [ ] **Step 1: Viết RED `UserDtoTests` và `MapperTests`.** Các test `rejectsUnknownAndWrongTypedRequestFields`, `validatesRequiredTextWithoutInventedLengthLimits`, `roundTripsNullableResponsesAndUtcTime`, `copiesApplicationPageItems`, `mapsPublicProfileWithoutInternalFields`, `doesNotInventFollowerCount`. Dùng Validator/JsonMapper production; assertions tiêu biểu:

```java
assertThat(validator.validate(new OrganizerApplicationRequest(" ", "Mô tả"))).isNotEmpty();
var view = profileMapper.toPublicView(UserFixtures.profile(), 7L);
assertThat(view.organizerSubject()).isEqualTo("idp|buyer-17");
assertThat(view.followerCount()).isEqualTo(7L);
assertThat(validator.validate(new OrganizerPublicView("s", "n", "d", -1L))).isNotEmpty();
```

JSON profile có đúng bốn key B13; response nullable vẫn giữ key cần thiết; requests có actor/status/role/field lạ bị reject. MeView/application view không lộ roleGrant, audit actor, Mongo ID của profile hoặc secret.
- [ ] **Step 2: Chạy RED.** `rtk proxy .\mvnw.cmd -B -ntp "-Dtest=UserDtoTests,MapperTests" test`; expected thiếu DTO/mapper/config.
- [ ] **Step 3: Tạo DTO/config/mapper và bổ sung annotation processors.** MapStruct 1.6.3, binding 0.2.0, `componentModel=SPRING`, `unmappedTargetPolicy=ERROR`; map application.id→applicationId, profile.identitySubject→organizerSubject và tham số count→followerCount. Không thêm helper tiền/version hoặc IO trong mapper. `displayName`/`rejectionReason`/`nextCursor` nullable đúng spec; kiểm count âm bằng Bean Validation, không tự đổi thành 0.
- [ ] **Step 4: Chạy GREEN.** Chạy lại Step 2 rồi `rtk proxy .\mvnw.cmd -B -ntp test`; expected tất cả pass, generated mapper không có unmapped target.
- [ ] **Step 5: Commit riêng nhóm Task 2.** Đề xuất `feat(user): thêm DTO, validation và mapper hồ sơ organizer`.

### Task 3: Global error handling và JSON log an toàn

**Files:** Nhóm Task 3; bootstrap `services/user-service/src/main/resources/application.yaml`, test YAML và `infrastructure/config-server/src/main/resources/config/user-service.yaml` cho logging.

**Interfaces:**
- Produces: `Problem(String code, String message, UUID correlationId, List<Violation> violations)`, nested `Violation(String field, String code)`; wire theo spec, defensive copy list.
- Produces: `ErrorCode.status(): HttpStatus`, `message(): String`, `from(Throwable): ErrorCode`, `fromStatus(HttpStatusCode): ErrorCode`; `BusinessException(ErrorCode)`, `(ErrorCode, Throwable)`, `getErrorCode(): ErrorCode`.
- Produces advice public methods như Booking: `handleBusiness(BusinessException,HttpServletRequest)`, `handleValidation(Exception,HttpServletRequest)`, `handleException(Exception,HttpServletRequest,HttpServletResponse)`, đều trả `ResponseEntity<Problem>`; giữ native framework headers/status.
- Produces: `UserLogFields.customize(JsonWriter.Members<ILoggingEvent>): void`; `SafeStackTracePrinter.printStackTrace(Throwable, Appendable): void throws IOException`; test `LogCapture` có `events(): List<ILoggingEvent>`, `bootJsonLogging(): ConfigurableApplicationContext`, `close(): void` như mẫu Booking.

- [ ] **Step 1: Viết RED `ErrorCodeTests`, `HttpErrorHandlingTests`, `RcaLoggingTests`.** Dùng fixture HTTP chỉ trong test, MVC slice tắt security filters để kiểm advice độc lập; Task 4 sẽ kiểm chain thật. Pin bảng mapping sau và test framework 400/404/405/406/415, return validation 500, status/header phù hợp:

| Input | Code / HTTP |
|---|---|
| Bind/handler input validation | VALIDATION_FAILED / 400 |
| Malformed JSON/type mismatch | INVALID_REQUEST / 400 |
| Authentication / access denied | UNAUTHENTICATED / 401, ACCESS_DENIED / 403 |
| DuplicateKeyException | DATA_CONFLICT / 409 |
| OptimisticLockingFailureException | CONCURRENT_MODIFICATION / 409 |
| MongoExecutionTimeoutException hoặc QueryTimeoutException | DATASTORE_TIMEOUT / 503 |
| MongoSocketException/MongoTimeoutException hoặc DataAccessResourceFailureException | DATASTORE_UNAVAILABLE / 503 |
| AmqpConnectException | MESSAGE_BROKER_UNAVAILABLE / 503 |
| AMQP/messaging conversion exception | INVALID_MESSAGE / 500 |
| IllegalArgumentException nội bộ / exception khác | INTERNAL_ERROR / 500 |

`ErrorCode.from` nhận biết BusinessException và bounded unwrap các wrapper Completion/Execution/InvocationTarget/ListenerExecution như Booking, không duyệt cause vô hạn. Các framework code còn lại lấy tên/mapping hiện có của Booking, bỏ SQL/Feign/business Booking.

```java
assertThat(ErrorCode.from(new DuplicateKeyException("SECRET-MONGO-URI")))
    .isEqualTo(ErrorCode.DATA_CONFLICT);
assertThat(ErrorCode.from(new IllegalArgumentException("SECRET")))
    .isEqualTo(ErrorCode.INTERNAL_ERROR);
```

HTTP tests: correlation UUID hợp lệ được giữ; invalid/multiple/oversized tracing header không che lỗi gốc; có/không có MDC trước đó vẫn một correlation body/header/actual JSON log, không sửa MDC của caller sau xử lý; không lộ nested/map key do client kiểm soát hoặc rejectedValue. Log probe dùng formatter Boot thật, sentinel trong URI/JWT/exception/MDC, kiểm không rò và không duplicate JSON field; native `/error` không bắt buộc Problem.
- [ ] **Step 2: Chạy RED.** `rtk proxy .\mvnw.cmd -B -ntp "-Dtest=ErrorCodeTests,HttpErrorHandlingTests,RcaLoggingTests" test`; expected chưa có boundary/classes.
- [ ] **Step 3: Implement boundary/log và YAML.** Adapt Booking source tại `6656f0d`, giữ safe Vietnamese messages cho code chung, DATA_CONFLICT có message cố định. Event `user.request.rejected`/`user.request.failed`, INFO cho 4xx, ERROR cho lỗi kỹ thuật; fixed method/status/code, correlation, không raw URI/body. Stack printer giữ giới hạn 16 throwable, 32 frame/throwable, 8192 ký tự của Booking. YAML UTF-8/logstash, rename severity/stackTrace, allowlist field User cần dùng; metadata user-service, USER_SERVICE_VERSION:local-dev, USER_INSTANCE_ID:random.uuid, USER_ENVIRONMENT:local; timestamp UTC, schemaVersion số 1. Bootstrap và central YAML phải cùng policy log; không thêm filter/custom error controller/framework.
- [ ] **Step 4: Chạy GREEN.** Chạy lại Step 2 rồi fast suite; expected đúng statuses, actual JSON không có sentinel/trùng field, correlation khớp.
- [ ] **Step 5: Commit riêng nhóm Task 3.** Đề xuất `feat(user): thêm global exception handler và JSON log an toàn`.

### Task 4: Feature shell và JWT security

**Files:** Nhóm Task 4. Bảng này chốt toàn bộ feature file mới, tương đối với M:

| Controller file và root mapping | Service interface → impl | Final collaborators của impl |
|---|---|---|
| `user/controller/MeController.java`: `/api/v1/me` | `user/service/UserService.java` → `user/service/impl/UserServiceImpl.java` | UserRepository, UserMapper |
| `organizer/controller/OrganizerApplicationController.java`: `/api/v1/organizer-applications` | `organizer/service/OrganizerApplicationService.java` → `organizer/service/impl/OrganizerApplicationServiceImpl.java` | OrganizerApplicationRepository, OrganizerApplicationMapper |
| `organizer/controller/AdminOrganizerApplicationController.java`: `/api/v1/admin/organizer-applications` | Cùng OrganizerApplicationService | Không thêm service quản trị thứ hai |
| `organizer/controller/OrganizerController.java`: `/api/v1/organizers` | `organizer/service/OrganizerProfileService.java` → `organizer/service/impl/OrganizerProfileServiceImpl.java` | OrganizerProfileRepository, OrganizerProfileMapper |
| `follow/controller/OrganizerFollowController.java`: `/api/v1/organizers/{subject}/follow` | `follow/service/UserFollowService.java` → `follow/service/impl/UserFollowServiceImpl.java` | UserFollowRepository |

**Interfaces:** Consumes Task 1–3. Các service interface không có method; controller chỉ inject đúng service trên. Produces `SecurityConfig.securityFilterChain(HttpSecurity, JwtAuthenticationConverter, AuthenticationEntryPoint, AccessDeniedHandler): SecurityFilterChain`; converter `convert(Jwt): Collection<GrantedAuthority>` và callbacks chuyển lỗi vào HandlerExceptionResolver như Booking. Trong SecurityConfigTests dùng private static `token(String role): String`, `signed(String variant, String role): String` throws Exception, RSA key fixture, loopback JWK_SERVER và ISSUER như Booking. Variant gồm valid/signature/issuer/audience/expired/future/roles-missing/roles-malformed; đóng server ở @AfterAll, không gọi realm thật.

- [ ] **Step 1: Viết RED `SecurityConfigTests`, `KeycloakRealmRoleConverterTests` và mở rộng `UserServiceApplicationTests/contextLoads` hiện có.** HTTP fixture controller chỉ trong test có handler trả 204 tại các route cần probe. Dùng Boot JwtDecoder với local JWKS và validator thật, không mock JWT decoder trong security behavior tests. Pin ma trận:

| Route | Token / expected |
|---|---|
| GET `/api/v1/me` | BUYER/ORGANIZER/ADMIN→204; INTERNAL_SERVICE→403; anonymous→401 |
| POST `/api/v1/organizer-applications` | BUYER→204; chỉ ADMIN hoặc chỉ ORGANIZER→403 |
| POST `/api/v1/admin/organizer-applications/{id}/approve`, `/reject` | ADMIN→204; BUYER→403 |
| GET `/api/v1/organizers/{subject}` | anonymous→204 |
| PUT/DELETE `/api/v1/organizers/{subject}/follow` | BUYER→204; anonymous→401; INTERNAL_SERVICE→403 |
| POST profile, GET follow, `/internal/v1/probe`, `/actuator/info`, route lạ | Authenticated human/machine→403 |

Sai signature/issuer/audience, expired/future nbf→401; malformed realm roles không có authority. Callback 401/403 có Problem và Bearer challenge, không redirect/form login. `UserServiceApplicationTests/contextLoads` khởi tạo bean graph feature với bốn repository @MockitoBean như Booking và JwtDecoder stub chỉ cho startup; không operation hoặc dependency thật. Repository thật được kiểm ở Task 6, runtime connection ở Task 7.
- [ ] **Step 2: Chạy RED.** `rtk proxy .\mvnw.cmd -B -ntp "-Dtest=SecurityConfigTests,KeycloakRealmRoleConverterTests,UserServiceApplicationTests" test`; expected thiếu config/feature shell hoặc policy sai.
- [ ] **Step 3: Tạo shell và security theo bảng.** CSRF disabled, STATELESS, exact public GET single-segment profile trước matcher rộng, BUYER/admin rules riêng, human roles cho me; health public, error dispatch giữ lỗi gốc, default deny. Allowlist realm role BUYER/ORGANIZER/ADMIN/INTERNAL_SERVICE, không authority từ header. Security behavior test dùng local issuer/JWKS properties và private helpers nêu trên; không dùng decoder stub của contextLoads để chứng minh JWT validation. Nếu exclude repository autoconfig thì tên Boot 4 là `org.springframework.boot.data.mongodb.autoconfigure.DataMongoRepositoriesAutoConfiguration`; property tắt repositories là `spring.data.mongodb.repositories.type=none`, không dùng property `enabled` suy đoán. Không thêm production handler, ownership giả hoặc API internal.
- [ ] **Step 4: Chạy GREEN.** Chạy lại Step 2 và full fast suite; expected all pass, production API chưa có business mapping; chỉ fixture trả 204. Ghi rõ Gateway chưa mở anonymous profile, không sửa Gateway.
- [ ] **Step 5: Commit riêng nhóm Task 4.** Đề xuất `feat(user): dựng khung feature và phân quyền JWT cho API User`.

### Task 5: Runtime YAML và Rabbit boundary

**Files:** Nhóm Task 5; POM, bootstrap/test YAML, central YAML User, `.env.example` chỉ nếu thiếu biến thực sự cần.

**Interfaces:** Consumes JsonMapper Task 2, ErrorCode Task 3, SecurityConfig Task 4. Produces `RabbitMqConfig.rabbitMessageConverter(JsonMapper): MessageConverter`, `userRabbitContainerCustomizer(): ContainerCustomizer<SimpleMessageListenerContainer>`; `UserRabbitErrorHandler extends ConditionalRejectingErrorHandler` có safe logging/fatal strategy tương đương Booking; Boot giữ RabbitTemplate/container factories.

- [ ] **Step 1: Viết RED `RuntimeConfigurationTests`, `UserRabbitErrorHandlerTests`.** Dùng YAML đã merge/context runner với mock connection factory; converter JSON dùng đúng JsonMapper, không bean topology. Assert dynamic=false, MANUAL ACK, prefetch=20, bounded connection timeout; malformed payload không bị ACK/requeue vô hạn và log không lộ body/header. Test startup không tạo listener/client Admin API hoặc yêu cầu credential user-management.
- [ ] **Step 2: Chạy RED.** `rtk proxy .\mvnw.cmd -B -ntp "-Dtest=RuntimeConfigurationTests,UserRabbitErrorHandlerTests" test`; expected chưa có Rabbit boundary/runtime properties.
- [ ] **Step 3: Implement config và dọn dependency trong task này.** Giữ existing Mongo URI/env; central port USER_SERVICE_PORT:8085, issuer/JWKS/audience theo KEYCLOAK_* và audience mặc định flash-ticket-api. Rabbit host/port/username/password/vhost qua env, port mặc định 5672, connection-timeout=2s, dynamic=false, manual/prefetch20. Chưa cấu hình exchange publish business.ex hoặc publisher nghiệp vụ. Gỡ mail starter/mail-test và mail exclusion; không thêm Redis/Feign/OAuth2Client/AdminSDK. Common Eureka/management đọc từ central application.yaml; production Config Server vẫn bắt buộc. Test-only config cô lập external runtime; không đổi production để fast suite giả thành công.
- [ ] **Step 4: Chạy GREEN.** Chạy lại Step 2 và fast suite; expected all pass. Render YAML bằng parser hiện có trong test; không dùng `docker compose config` thay cho kiểm Spring YAML. Xác nhận không duplicate root spring và không secret literal.
- [ ] **Step 5: Commit riêng nhóm Task 5.** Đề xuất `feat(user): cấu hình runtime và xử lý lỗi RabbitMQ`.

### Task 6: Persistence integration trên MongoDB riêng

**Files:** Nhóm Task 6; đọc ba file deploy MongoDB và schema, không sửa chúng.

**Interfaces:** Consumes production repositories/converter Task 1, fixtures Task 1 và runtime Task 5. Produces `UserPersistenceIT` dùng `@DataMongoTest`, `@Import(MongoConfig.class)`, `@ActiveProfiles("integration")` và bốn repository thật. `application-integration.yaml` bật lại Mongo/repositories, override danh sách exclude test Mongo bằng `spring.autoconfigure.exclude: []` và `spring.data.mongodb.repositories.type=auto`, tắt Config Server/Eureka; slice không tải web/security/Rabbit nên không cần JWKS ngoài. POM test-scope `org.testcontainers:testcontainers`, `org.testcontainers:testcontainers-junit-jupiter` dùng version BOM; Failsafe profile `integration` chạy `*IT`, Surefire mặc định không chạy IT.

- [ ] **Step 1: Viết RED integration tests.** `roundTripsFourDocumentsWithoutExtraFields`, `rejectsDuplicateIdentityAndFollowPair`, `enforcesApplicationBranchesAndOptionalFields`, `staleVersionCannotOverwriteUserOrApplication`, `projectionSourceVersionIsNotOptimisticVersion`. Assert lưu/đọc đủ bốn collection với BSON inspection; Mongo reject null optional/field thừa, ACTIVE thiếu confirmed roleGrant, REJECTED thiếu reason hoặc có roleGrant; giữ sourceVersion=0; unique users/application subject và follow pair; stale write→OptimisticLockingFailureException.
- [ ] **Step 2: Chạy RED nếu Docker sẵn sàng.** `rtk proxy .\mvnw.cmd -B -ntp -Pintegration "-Dit.test=UserPersistenceIT" verify`; expected chưa có integration profile/wiring hoặc persistence assertion fail. Nếu Docker thiếu, ghi BLOCKED môi trường thay vì gọi đó là RED hành vi và không bật tự skip để làm xanh gate.
- [ ] **Step 3: Hoàn thiện fixture/profile và sửa mapping nếu test chứng minh cần.** Dùng GenericContainer Mongo **cùng image digest ở `deploy/mongodb/compose.yaml`**, random host port, database user_db chỉ bên trong container test. Copy schema tới `/opt/flash-ticket/schema.cjs`, hai init script vào `/docker-entrypoint-initdb.d/`; root/viewer password chỉ là test constant. Wait for reachable mapped port; connection factory lấy host/mappedPort và test credential, authSource=admin. Trước save lần đầu, clear version của user/application fixture về null; assert bản insert có version=0, stale update bị từ chối; profile sourceVersion=0 vẫn giữ nguyên. Không dùng Mongo replica set/transaction hoặc URI từ `.env`/server119. Integration bật real auto-config trở lại, không kế thừa mock factory của fast suite. Không dùng disabledWithoutDocker để giấu lần integration không thực thi.
- [ ] **Step 4: Chạy GREEN.** Chạy lại Step 2; expected Surefire fast suite và Failsafe IT đều 0 failures/errors/skips. Nếu Docker chưa có, vẫn hoàn thiện profile/tests và ghi rõ chưa kiểm real persistence; không sửa validator/index để cứu mapping.
- [ ] **Step 5: Commit riêng nhóm Task 6 sau các kiểm tra chạy được.** Đề xuất `test(user): kiểm mapping, index và optimistic locking trên MongoDB cô lập`; commit body phải ghi đúng giới hạn nếu IT chưa chạy.

### Task 7: Handoff, guide Admin API và kiểm chứng cuối

**Files:** `services/user-service/TESTING.md` (Create), `services/user-service/HANDOFF.md` (Modify), `docs/guides/user-service-keycloak-admin-integration.md` (Create); chỉ sửa thiếu sót Task 1–6 nếu verification chỉ ra.

**Interfaces:** Consumes kết quả Task 1–6; produces runbook tái chạy được, bảng test đã chạy/chưa chạy và guide chứa code mẫu nhưng không runtime bean/dependency/secret mới. Không thêm interface production cho Admin API.

- [ ] **Step 1: Viết guide tích hợp Admin API cho giai đoạn sau.** Kiểm nguồn chính thức theo Keycloak đang dùng lúc viết. Guide có dependency và YAML mẫu OAuth2 client_credentials cho user-management; application principal ổn định, manager/HTTP client timeout; code mẫu lookup role representation, query role và add ORGANIZER đúng subject/user-ID mapping đã xác minh, giữ BUYER. Nêu CAS REQUESTED→query/confirm→ACTIVE→projection/rebuild và nhánh timeout/concurrent approve-reject theo B13. Các snippet gắn nhãn chưa tích hợp; chỉ nhận là đã compile/test nếu có kiểm chứng riêng. Không gọi realm thật hoặc cấp realm-admin trong task này.
- [ ] **Step 2: Viết TESTING/HANDOFF.** Ghi Java21, lệnh fast/integration, Docker và fixture isolation; tên env hiện hành không chứa giá trị secret. Link `../../deploy/mongodb/compose.yaml`, script init/prefix và guide mới. Ghi server119 đã có Mongo healthy qua kiểm tra chỉ đọc 25/10; chưa đồng bộ việc chuyển path lên server. Ghi gateway anonymous profile, identity publisher/payload, Admin API và B15 V17/V18 còn dành cho task sau.
- [ ] **Step 3: Chọn một final gate bằng Java 21.** Có Docker: `rtk proxy .\mvnw.cmd -B -ntp -Pintegration verify` kiểm cả fast suite và IT; thiếu Docker: `rtk proxy .\mvnw.cmd -B -ntp verify` và báo IT chưa chạy. Expected BUILD SUCCESS, các suite thực thi không fail/error/skip bất ngờ, Spotless pass, SpotBugs 0 findings, JaCoCo report được tạo; profile integration phải có Failsafe report chứng minh UserPersistenceIT chạy thật. Nếu Task 6 đã chạy cùng gate với code/config/dependency không đổi thì dùng kết quả đó, không chạy lặp. Nếu formatter fail, apply scoped file của task rồi chạy lại gate. Ghi test count thực tế, không dùng số của Booking; thiếu JDK21 hoặc Docker không được gọi full verification passed.
- [ ] **Step 4: Kiểm runtime smoke khi hạ tầng/credential sẵn sàng.** Chạy User với Config Server/Eureka và env được nạp vào process; kiểm tên/port/config, đăng ký Eureka, health, read-only Mongo ping/read metadata và Rabbit connection không declare/publish/consume. Dùng SSH tunnel hiện có nếu cần tới Mongo119; không ghi fixture hoặc sửa server/realm/broker. Hạ tầng thiếu thì đánh chưa chạy; không thêm endpoint success để demo.
- [ ] **Step 5: Rà scope và commit tài liệu/kết quả.** `rtk git diff --check`; xem staged diff để loại `.gitignore`/relocation MongoDB có sẵn và secret. TESTING phân biệt converter, real persistence, signed JWT fixture và smoke thật; không gọi scaffold là flow hoàn chỉnh. Commit riêng file do task tạo/sửa bằng commit-message-vi; đề xuất `docs(user): hướng dẫn chạy scaffold và tích hợp Keycloak Admin API sau`.

## Tự rà soát plan và bàn giao

- Spec §1–5 → global constraints, bản đồ file, Task 4; §6 → Task 1/6; §7 → Task 2; §8 → Task 4; §9 → Task 5; §10 → Task 3/5; §11 → Task 7; §12 → test/gate Task 1–7; §13–14 → constraints và handoff. Không còn yêu cầu spec thiếu task.
- Interface xuyên task thống nhất: document/factory ở Task 1, DTO/mapper/JsonMapper ở Task 2, ErrorCode/Problem/log ở Task 3, feature/security ở Task 4, Rabbit/runtime ở Task 5; IT dùng đúng bốn repository. Không dùng helper hoặc query nghiệp vụ chưa được định nghĩa.
- Cả năm Review Focus có test sở hữu; test snippet chỉ minh họa assertion cần chốt, người triển khai viết thân hàm idiomatic. Không bổ sung tests cho docs-only hoặc method rỗng chỉ để kiểm tên class.
- Plan chưa được thực thi, chưa có kết quả build/test User mới. Đề xuất **Native** trong phiên này vì bảy task nối nhau qua model/DTO/error interfaces; không có công cụ subagent trong phiên tại thời điểm lập plan. Chờ người dùng review plan và chọn cách thực hiện; nếu chọn Native, dùng `superpowers:executing-plans`, nếu có môi trường subagent và người dùng chọn cách đó thì dùng `superpowers:subagent-driven-development`.
