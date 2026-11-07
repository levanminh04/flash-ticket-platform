# Keycloak Registration Outbox Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Đồng bộ tự đăng ký thành công từ Keycloak119 qua outbox/RabbitMQ119 đến một hồ sơ MongoDB trong User Service.

**Architecture:** Dùng native `OutboxStore` cùng transaction REGISTER, native `OutboxDrainerTask` trong tiến trình Keycloak và adapter RabbitMQ có confirm/mandatory return. User Service validate contract kín và upsert một document theo unique identitySubject trước MANUAL ACK.

**Tech Stack:** Keycloak 26.8.0, Java release21 cho provider, RabbitMQ Java client5.30.0; User Service Java23/Spring Boot4.1.1/Jackson3/Spring AMQP/MongoDB8.0 hiện có.

**Spec:** `docs/superpowers/specs/2026-10-26-keycloak-registration-outbox-design.md`.

**Status:** DRAFT — kế hoạch được lập theo yêu cầu; chờ duyệt spec và plan trước triển khai. Chưa thực hiện task bên dưới.

## Global Constraints

- Chỉ local form REGISTER thành công, realm `flash-ticket`; bỏ admin CREATE, REGISTER_ERROR, social/broker, login/update/delete.
- Native `public.outbox_entry` đã có trên keycloak_db119; không thêm bảng/entity/migration/framework outbox riêng.
- Pin Keycloak26.8.0 cả compile/runtime, dependency Keycloak/JPA provided; API outbox nội bộ phải có kiểm chứng khi nâng phiên bản.
- Topology: `/`, `identity.ex` direct → `identity.user-created.v1` → `user.identity.q` → `user.identity.dlq`; không auto-declare hoặc sửa ACL/topology.
- Provider/entryKind/timer lần lượt `flash-ticket-registration-outbox` / `flash-ticket-registration` / `flash-ticket-registration-outbox`.
- Poll2s, batch1; native backoff2s/5s/10s/30s/60s với jitter, maxAttempts Integer.MAX_VALUE; pending/dead-letter không tự purge, delivered giữ7ngày.
- JSON V1 đúng §5 spec; eventId ổn định, không PII ngoài displayName, không password/token/role/email.
- Unique index identitySubject có sẵn; atomic setOnInsert, version0, UUID string `_id`, không `_class`, không ghi đè user hiện có.
- MANUAL ACK sau Mongo acknowledgment (`w=1,j=true` standalone); Mongo retry3attempts/1s/5s, lỗi vĩnh viễn/hết retry reject false vào DLQ.
- Chỉ viết code tối thiểu có nhu cầu; không service worker riêng, Spring trong plugin, retry queue, inbox collection hoặc shared DTO module.
- Fault tests ở môi trường tách biệt; không stop broker119 dùng chung/reset volume hay đổi quyền DB đang chạy để thử lỗi.

## Review Focus

1. Keycloak bắt listener exception: setRollbackOnly rõ ràng và test đăng ký thật phải rollback khi outbox lỗi (Task2).
2. Broker confirm thành công nhưng unroutable: basic.return vẫn làm publish thất bại; không DELIVERED giả (Task3).
3. Kết nối RabbitMQ ban đầu thất bại hoặc restart giữa confirm và DB commit: tự thử lại và không mất row (Task3/5).
4. Mongo đã ghi nhưng ACK mất: không đưa ACK vào retry ghi hoặc ACK/NACK lại tag cũ; redelivery giữ nguyên hồ sơ (Task4).
5. Realm có sẵn không được cập nhật bởi import; consumer có thể ghi null displayName hoặc auto-version sai validator (Task4/6).

## File map

| Phạm vi | File dự kiến và trách nhiệm |
|---|---|
| Wire contract | `docs/contracts/identity/user-created-v1.schema.json`, `user-created-v1.example.json` |
| SPI build | `services/keycloak-spi/pom.xml`, `Dockerfile`, `HANDOFF.md` |
| SPI code, base `services/keycloak-spi/src/main/java/com/flashticket/keycloak/` | `UserCreatedV1.java` (wire DTO); `RegistrationEventListener.java`; `RegistrationEventListenerFactory.java` (config/lifecycle/timer); `RabbitMqOutboxDeliveryHandler.java` (native handler + connection/publish) |
| SPI service registration | `services/keycloak-spi/src/main/resources/META-INF/services/org.keycloak.events.EventListenerProviderFactory` |
| SPI tests | `.../src/test/java/com/flashticket/keycloak/RegistrationEventListenerTests.java`, `RabbitMqOutboxDeliveryHandlerTests.java`, `RegistrationOutboxIT.java` |
| User | `services/user-service/src/main/java/com/flashticket/user_service/messaging/identity/UserCreatedV1.java`, `IdentityUserCreatedListener.java`, `IdentityUserRegistrar.java`; chỉnh `config/RabbitMqConfig.java` nếu cần boundary converter/retry |
| User tests | `.../src/test/java/com/flashticket/user_service/messaging/identity/IdentityUserCreatedListenerTests.java`, `IdentityUserRegistrarIT.java` |
| Deployment/config | `deploy/docker-compose.keycloak.yml`, `deploy/keycloak/import/flash-ticket-realm.json`, `infrastructure/config-server/src/main/resources/config/user-service.yaml`, `.env.example`, `deploy/README.md` |

Không tạo interface dịch vụ một implementation. Factory và native OutboxDeliveryHandler là interface extension bắt buộc của Keycloak, được triển khai trực tiếp. Xóa Spring placeholder application/test/application.properties chỉ trong module SPI khi thay bằng provider.

### Task 1: Khóa contract và build provider đúng runtime

**Interfaces:** DTO `UserCreatedV1(int schemaVersion, UUID eventId, String eventType, Instant occurredAt, String realmId, String identitySubject, String displayName)`; JSON optional displayName phải omit khi null. Contract/schema là đầu vào chung cho hai module.

- [ ] Tạo schema và fixture đúng §5; field required/optional, additionalProperties:false, enum/version/type, UUID và timestamp được chốt trong schema. Không runtime schema-engine dependency.
- [ ] Viết contract checks đọc cùng fixture từ hai module: roundtrip tên field và UTC, missing subject, typo identitySubjet, version2/type sai; kiểm displayName null bị omit.
- [ ] Chạy checks mới để thấy thất bại trước thêm DTO/build; ghi đúng nguyên nhân, không coi Maven download/JDK lỗi là test đỏ hợp lệ.
- [ ] Chuyển POM SPI sang normal JAR release21; provided `keycloak-server-spi`, `keycloak-server-spi-private`, `keycloak-services`, `keycloak-model-jpa`26.8.0 theo imports thực sự. Pin compiler/test plugin cần thiết, chỉ thêm test dependency có nhu cầu. RabbitMQ client5.30.0; dùng Keycloak JsonSerialization thay tự bundle Jackson.
- [ ] Tạo record DTO ở hai module, validation message để trong listener/registrar theo nhiệm vụ; bỏ các placeholder Spring của module SPI và thêm service descriptor factory khi Task2 triển khai.
- [ ] Verify từ thư mục SPI bằng `rtk proxy .\mvnw.cmd -B -ntp test package` với JDK21; kiểm JAR không có BOOT-INF hoặc lớp Keycloak/JPA/Jackson bundle. User checks chạy bằng wrapper module với JDK23.
- [ ] Commit nhóm contract/build: `chore(keycloak): chuẩn bị provider và contract đăng ký v1`.

### Task 2: REGISTER enqueue cùng transaction, chứng minh rollback

**Files:** listener/factory/service descriptor, `RegistrationEventListenerTests.java`, phần registration của `RegistrationOutboxIT.java`.

**Interfaces:** `RegistrationEventListener(KeycloakSession session)` implements EventListenerProvider; `onEvent(Event event)` và admin overload no-op; factory `create(session)` trả listener. Enqueue dùng chính signature native OutboxStore, không wrapper repository.

- [ ] Viết unit checks filter REGISTER/error/realm/social và mapping fullname; test serialize/enqueue failure phải gọi setRollbackOnly, không gọi RabbitMQ. Chạy thấy fail rồi thêm implementation tối thiểu.
- [ ] Filter local form bằng `Details.REGISTER_METHOD == "form"`, theo `RegistrationUserCreation` tag26.8.0 đã đối chiếu; fixture missing details/method khác/broker phải bị bỏ qua. Không đoán phạm vi chỉ từ tên REGISTER.
- [ ] Implement native enqueue với entryKind/ownerId/correlationId/entryType đúng bảng §4.2; realm/user đọc qua session. Kiểm transaction active. Catch failure trong capture path → setRollbackOnly rồi throw; ngoài phạm vi return.
- [ ] Tạo integration fixture Keycloak26.8.0 + PostgreSQL riêng; enable listener trên realm test, submit **form đăng ký thật**. Test `registerCommitsUserAndOutbox`: user tồn tại và một native row PENDING đúng subject/eventId.
- [ ] Test `outboxFailureRollsBackRegistration`: làm enqueue thất bại bằng DB fault/quyền hoặc probe chỉ ở fixture tách biệt; assert request thất bại, không user đã commit, không row đã commit. Test `rollbackAfterEnqueueLeavesNeitherRecord` dùng probe chỉ trong test artifact để rollback sau enqueue; không đưa test flag vào provider production.
- [ ] Test register invalid/admin CREATE/realm khác/social không tạo native row cho entryKind riêng; cùng event enqueue lại không tạo thêm row.
- [ ] Run `rtk proxy .\mvnw.cmd -B -ntp verify` trong SPI với integration profile tối thiểu do fixture định nghĩa; ghi command/profile cụ thể vào HANDOFF. Đây là gate bắt buộc trước deploy; thiếu Docker local thì báo chưa kiểm, không thay bằng mock.
- [ ] Commit: `feat(keycloak): lưu sự kiện đăng ký trong transaction gốc`.

### Task 3: Native drainer và adapter RabbitMQ

**Files:** factory, RabbitMqOutboxDeliveryHandler, publisher tests, SPI integration test.

**Interfaces:** handler implements `OutboxDeliveryHandler.entryKind()` và `deliver(KeycloakSession, OutboxEntryEntity): OutboxDeliveryResult`, AutoCloseable cho tài nguyên broker. Factory khởi tạo native OutboxConfig/OutboxDrainerTask và schedule một lần; không tạo custom scheduler/DAO.

- [ ] Viết publisher checks: confirm ACK/no-return → delivered; return dù ACK → retry; NACK/timeout/initial-connect-fail → retry; lượt sau connect lại thành công. Khóa một message/channel để correlate return/confirm rõ ràng; test fail trước implementation.
- [ ] Implement adapter theo §4.3: connection lazy tái sử dụng, channel mỗi lượt, mandatory/persistent/UTF8/eventId, confirm wait2s, connect/handshake2s; mọi retry giữ nguyên payload/eventId. Trả mã lỗi an toàn thay raw exception.
- [ ] Factory đăng ký native drainer qua TimerProvider khi server startup hoàn tất; config đúng Global Constraints; close hủy timer/connection. Timer không schedule lặp trên mỗi callback hoặc chạy trong build/import mode.
- [ ] Integration với RabbitMQ riêng: không có binding → row vẫn PENDING; khôi phục binding → DELIVERED; tạm ngắt broker → đăng ký vẫn commit, worker tiếp tục khi broker về. Verify max thời gian tick, không giữ lock vô hạn.
- [ ] Thử restart Keycloak có PENDING và crash sau publish trước transaction commit bằng fixture; assert tiếp tục publish, eventId giữ nguyên. Kiểm khóa native tránh hai tick gửi đồng thời cùng row; không thêm lock framework.
- [ ] Kiểm retention chỉ purge DELIVERED quá7ngày đúng entryKind; PENDING không bị purge/promote theo tuổi. Kiểm exception một tick không làm timer dừng.
- [ ] Chạy SPI `verify` và commit: `feat(keycloak): gửi outbox qua worker native và publisher confirm`.

### Task 4: Consumer idempotent và ACK đúng thời điểm

**Interfaces:** `IdentityUserRegistrar.ensureRegistered(UserCreatedV1 event): void`; `IdentityUserCreatedListener.onMessage(Message message, Channel channel): void` nhận raw message để xử lý parse/validation/ACK tại một boundary rõ ràng. Inject JsonMapper/Validator/MongoTemplate hiện có. Spring Framework retry chỉ bọc ensureRegistered.

- [ ] Viết consumer checks cho JSON typo/unknown/missing/null/version/type/realm/routing-key sai → reject false không retry Mongo; hợp lệ → gọi registrar trước ACK. Chạy fail trước implementation.
- [ ] RegistrarIT trên MongoDB standalone: atomic upsert tạo `_id` UUID string, subject, createdAt/updatedAt occurredAt, version0; displayName null không tạo field null. Assert validator chấp nhận và không `_class`.
- [ ] Test duplicate sequential/concurrent và user đã sửa displayName: chỉ một doc, hồ sơ cũ giữ nguyên ID/name/version/dates. DuplicateKeyException chỉ hợp lệ khi đọc lại đúng subject; lỗi index khác vẫn fail.
- [ ] Implement setOnInsert bằng MongoTemplate, không thay model/validator; preflight kiểm unique index hiện có trước listener start. Thiếu index fail startup thay tự tạo bằng app principal. Xác nhận runtime Mongo principal có quyền đọc index, không nâng quyền bằng suy đoán.
- [ ] Implement raw-message listener: deserialize theo contract kín dùng mapper policy sẵn có, validate DTO và realm từ KEYCLOAK_IDENTITY_REALM_ID. Không deserialize class dựa header __TypeId__.
- [ ] Dùng Spring Framework retry hiện có cho Mongo transient exception, 3attempts delay1s rồi5s; các lỗi permanent dừng ngay. Sau exhaustion reject false; MANUAL ACK chỉ sau Mongo ack. ACK IOException/channel failure propagate riêng; không chạy lại DB retry hoặc NACK tag đã lỗi.
- [ ] Test transient lần1/2 rồi success → ACK đúng1lần; hết3lần → reject false; commit rồi ACK mất → broker redelivery upsert no-op rồi ACK mới. Test malformed conversion thật cũng về DLQ.
- [ ] Listener identity prefetch1/concurrency1 và dynamic:false; không thay config listener khác. Configure write concern w1/jtrue và timeout kết nối Mongo hữu hạn để retry có thể tiến triển; xác nhận không phá URI options hiện có.
- [ ] Run `rtk proxy .\mvnw.cmd -B -ntp verify` trong User với JDK23 và integration env riêng; commit `feat(user): nhận đăng ký và tạo hồ sơ idempotent`.

### Task 5: Đóng gói và kiểm luồng đầy đủ ở môi trường tách biệt

**Files:** SPI Dockerfile/HANDOFF, Keycloak Compose, realm import, config User/.env.example, deploy README.

- [ ] Dockerfile build provider normal JAR và chỉ copy RabbitMQ client JAR cần thiết; base quay.io/keycloak/keycloak:26.8.0; kc.sh build. Không bundle lại logging/Keycloak/Hibernate hoặc sửa image version.
- [ ] Compose truyền identity broker credentials từ env gốc và tên host network rabbitmq; giữ DB/hostname/theme/resources config hiện có. Env example chỉ thêm tên/default công khai, không secret.
- [ ] Bổ sung listener ID vào import realm mới, giữ jboss-logging; runbook nêu cách cập nhật listener realm tồn tại bằng Admin API/UI sau xác minh ID. Thêm KEYCLOAK_IDENTITY_REALM_ID cho User và kiểm khớp đúng deployment.
- [ ] Chạy matrix §8 spec trên stack tách biệt: commit/rollback, broker outage/unroutable/restart, Mongo outage/DLQ, ACK loss/redelivery, invalid contract, duplicate concurrent. Gate không đạt thì chưa triển khai119.
- [ ] Ghi artifact SHA256, phiên bản thực tế và kết quả từng case; chưa có kết quả phải giữ NOT RUN. Commit `build(keycloak): đóng gói provider và cấu hình đồng bộ đăng ký`.

### Task 6: Triển khai119 và bằng chứng nghiệm thu

**Interfaces:** package/artifact đã qua Task5; deployment giữ topology/server119 hiện có. Mục tiêu không yêu cầu User Service phải cùng JVM/máy với Keycloak, chỉ kết nối đúng Mongo/Rabbit.

- [ ] Chỉ thực hiện sau người dùng duyệt tài liệu và yêu cầu triển khai. Preflight read-only xác nhận runtime26.8.0, native outbox schema, đúng realm ID, Mongo unique index, Rabbit binding/ACL; không in credentials.
- [ ] Backup/capture image+realm listener config cũ. Deploy User consumer trước; deploy provider image sau, bật đúng listener trên realm đã tồn tại. Nếu restart ảnh hưởng môi trường dùng chung thì chốt thời điểm triển khai với người dùng.
- [ ] Self-register một tài khoản test qua UI/flow thật, ghi subject/eventId/rowId đã che thông tin. Assert native DELIVERED, broker route đúng, Mongo đúng1doc và queue không còn delivery chưa ACK cho event đó. Không dùng admin-create thay REGISTER.
- [ ] Không fault-inject broker shared119. Dùng kết quả isolated matrix cho lỗi; trên119 chỉ kiểm health/startup/happy-path/restart đã được phép và redelivery an toàn nếu có phương thức kiểm tách biệt.
- [ ] Lưu `docs/evidence/2026-10-26-keycloak-registration-outbox.md` hoặc ngày nghiệm thu thực tế: build SHA/artifact hash/runtime, lệnh/check đã chạy, trạng thái từng case, giới hạn còn lại; không chứa token/password/payload PII.
- [ ] Runbook rollback giữ PENDING, không drop table; replay consumer DLQ và native row lỗi phải scope entryKind/realm, giữ eventId; thao tác chỉ thực hiện sau sửa nguyên nhân. Công bố cửa sổ không capture khi tắt plugin.
- [ ] Commit docs/evidence đã xác minh. Chỉ báo hoàn tất khi đủ3đầu ra, transaction gate và ACK/redelivery checks có bằng chứng.

## Self-review và handoff

Đã đối chiếu từng yêu cầu spec vào Task1–6; transaction/rollback Task2, delivery Task3, idempotency/ACK Task4, packaging Task5, server evidence Task6. Native outbox thay bảng custom là đề xuất rõ ràng trong spec, chưa gọi là người dùng đã duyệt. Commands có prefix rtk; phiên bản JDK/module tách đúng runtime. Không chạy test hoặc thay server trong giai đoạn lập tài liệu này.

Khuyến nghị thực thi trực tiếp trong phiên (native) vì các phần phụ thuộc cùng contract và runtime pin; không cần tạo một agent/context cho mỗi file. Sau khi tài liệu được duyệt, dùng executing-plans và kiểm từng task; không coi sự tồn tại của plan là quyền triển khai lên server.
