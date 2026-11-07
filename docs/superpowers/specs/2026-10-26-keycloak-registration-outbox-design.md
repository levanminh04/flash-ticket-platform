# Spec — Đồng bộ đăng ký Keycloak qua RabbitMQ đến User Service

- Ngày: 2026-10-26
- Trạng thái: **DRAFT — chờ duyệt tài liệu trước triển khai**.
- Người dùng đã chọn: transactional outbox trong transaction đăng ký Keycloak; worker trong SPI; consumer idempotent theo `identitySubject`.
- Phạm vi: chỉ tự đăng ký thành công (`REGISTER`) của realm `flash-ticket`.
- Đây là thiết kế triển khai tiếp theo; chưa triển khai plugin hoặc nghiệm thu luồng trên server.

## 1. Kết quả cần đạt và hiệu chỉnh yêu cầu

1. `keycloak_db` trên server 119 giữ bền vững sự kiện đăng ký trong cùng transaction tạo tài khoản.
2. SPI được nạp vào Keycloak; worker trong tiến trình Keycloak tự gửi sự kiện sang RabbitMQ server 119 và retry khi broker lỗi.
3. User Service nhận sự kiện, tạo đúng một hồ sơ trong `user_db.tbl_users` trên MongoDB, ACK sau khi ghi được xác nhận.

**Không cần tạo bảng `outbox_messages` mới.** Keycloak 26.8.0 đã có entity `OutboxEntryEntity`, bảng vật lý `public.outbox_entry`, DAO `OutboxStore` và `OutboxDrainerTask`. Lệnh chỉ đọc ngày 2026-10-26 trên `ft119` xác nhận `keycloak_db` hiện có `public.outbox_entry`. Tái sử dụng native outbox đáp ứng ý định lưu bền vững, giảm entity, migration, query locking và code retry tự viết.

Nếu runtime thực tế thiếu class/API hoặc schema không khớp: dừng triển khai, báo mismatch; không tự nâng Keycloak hay tạo bảng thay thế để bỏ qua kiểm chứng.

## 2. Nguồn, hiện trạng và giới hạn bằng chứng

| Nguồn | Điều đã xác minh |
|---|---|
| `deploy/docker-compose.keycloak.yml` | Image Keycloak 26.8.0; chưa đóng gói provider; realm đang có chỉ listener `jboss-logging` |
| `services/keycloak-spi/pom.xml` | Khung Spring Boot/AMQP, chưa phải provider chạy trong Keycloak |
| `deploy/rabbitmq/definitions.json` và Compose RabbitMQ | Có exchange/binding/queue/DLQ, publisher principal identity riêng |
| `UserDocument`, `MongoConfig`, schema MongoDB | Collection `tbl_users`, validator kín; unique `identitySubject`; `_id` UUID string; version số nguyên >= 0; không `_class` |
| `infrastructure/config-server/src/main/resources/config/user-service.yaml` | MANUAL ACK, prefetch 20, auto-declare topology tắt |
| B12 §4.5 và B13 §9 | User giữ hồ sơ nghiệp vụ; Keycloak giữ danh tính/role; không thêm role/email/password vào model |
| Server 119, truy vấn chỉ đọc | `SELECT table_schema,table_name FROM information_schema.tables WHERE lower(table_name) LIKE '%outbox%'` trả `public|outbox_entry` |

Repo cũ `levanminh04/flash-ticket-system`, commit `4516f18130e0b5896c30841cd56401c681fa7dac`, chỉ tham khảo mô hình listener/factory/consumer. Không bê topology `keycloak.events`, model user cũ, publish trước commit hoặc nhận định MANUAL ACK bảo đảm exactly-once.

Source chính thức, pin đúng tag 26.8.0:

- [EventListenerProvider: callback trong transaction](https://www.keycloak.org/docs-api/26.8.0/javadocs/org/keycloak/events/EventListenerProvider.html).
- [EventBuilder: bắt exception từ listener](https://github.com/keycloak/keycloak/blob/26.8.0/server-spi-private/src/main/java/org/keycloak/events/EventBuilder.java).
- [OutboxStore: enqueue dùng EntityManager của session; dedup nguyên tử](https://github.com/keycloak/keycloak/blob/26.8.0/model/jpa/src/main/java/org/keycloak/events/outbox/OutboxStore.java).
- [OutboxEntryEntity: schema và indexes](https://github.com/keycloak/keycloak/blob/26.8.0/model/jpa/src/main/java/org/keycloak/models/jpa/entities/OutboxEntryEntity.java).
- [OutboxDrainerTask: locking, retry, retention](https://github.com/keycloak/keycloak/blob/26.8.0/model/jpa/src/main/java/org/keycloak/events/outbox/OutboxDrainerTask.java).
- [OutboxConfig](https://github.com/keycloak/keycloak/blob/26.8.0/model/jpa/src/main/java/org/keycloak/events/outbox/OutboxConfig.java), [OutboxBackoff](https://github.com/keycloak/keycloak/blob/26.8.0/model/jpa/src/main/java/org/keycloak/events/outbox/OutboxBackoff.java).
- [TimerProvider](https://www.keycloak.org/docs-api/26.8.0/javadocs/org/keycloak/timer/TimerProvider.html), [đóng gói provider](https://www.keycloak.org/docs/26.8.0/server_development/#_providers).
- [Spring AMQP error/retry](https://docs.spring.io/spring-amqp/reference/4.0/amqp/resilience-recovering-from-errors-and-broker-failures.html), [MongoDB single-document atomicity](https://www.mongodb.com/docs/manual/core/write-operations-atomicity/).

Outbox classes thuộc implementation `keycloak-model-jpa`, không xem là public SPI có cam kết tương thích. Pin runtime và dependency 26.8.0; nâng Keycloak phải chạy lại kiểm chứng này. Docs hướng dẫn custom JPA cũng ghi API không được cam kết tương thích; dùng custom entity không loại bỏ hạn chế đó. Bằng chứng hiện tại là đọc source và kiểm bảng, chưa chứng minh plugin đã chạy.

## 3. Approaches và lựa chọn

| Phương án | Đánh đổi | Quyết định |
|---|---|---|
| Outbox cùng transaction, worker trong SPI | Phục hồi khi broker lỗi; cần kiểm transaction/lifecycle | Người dùng chọn |
| Publish sau commit, không outbox | Gọn nhưng có khoảng mất sự kiện trước publish | Không đáp ứng yêu cầu đã chọn |
| Custom bảng/JPA entity, worker tự viết | Chủ động schema; thêm migration, locking, retry và phụ thuộc API extension | Không cần trên baseline 26.8.0 |

Chi tiết do người viết đề xuất: dùng native `outbox_entry` + `OutboxStore` + `OutboxDrainerTask` thay bảng/worker tự viết. Người dùng cần duyệt hiệu chỉnh này cùng tài liệu.

Không thêm service worker riêng, CDC/Debezium, Kafka, generic messaging framework, shared DTO module, Mongo inbox collection hoặc Mongo multi-document transaction. Không mở rộng sang admin CREATE, social/broker registration, UPDATE/DELETE, cấp role hay backfill tài khoản có sẵn.

## 4. Luồng và transaction

### 4.1 Ghi outbox cùng đăng ký

`Registration form → Keycloak tạo user → listener REGISTER → native enqueuePending → Keycloak commit user + outbox`.

- Listener chỉ nhận `EventType.REGISTER`, không `REGISTER_ERROR`; kiểm `event.getError() == null`, realm đúng, userId/time/eventId hợp lệ và `event.getDetails().get(Details.REGISTER_METHOD)` bằng `form`. Source `RegistrationUserCreation` tag26.8.0 ghi giá trị này; thiếu details hoặc method khác thì bỏ qua. Không mặc định mọi REGISTER là form cục bộ.
- Lấy profile qua `session.users()` và realm qua `session.realms()`; không đọc raw form hoặc password, không tạo `RegisterRequest`.
- Dùng `new OutboxStore(session).enqueuePending(...)`; session này là session callback, transaction phải đang active. Không mở transaction mới, không dùng Spring `@Transactional`, connection độc lập hoặc after-completion để ghi outbox.
- Nếu tạo/serialize/enqueue thất bại: đánh dấu `session.getTransactionManager().setRollbackOnly()` rồi propagate lỗi an toàn. `EventBuilder.sendNow()` có catch listener exception, vì vậy **throw một mình không đủ**. Chỉ callback đúng phạm vi mới áp fail-closed; event ngoài phạm vi được bỏ qua.
- DB lỗi hoặc outbox lỗi làm đăng ký thất bại và rollback. RabbitMQ lỗi không ảnh hưởng đăng ký vì request không gọi broker.
- Kiểm chứng bắt buộc trên Keycloak thật: success commit cả hai; lỗi outbox rollback user; rollback sau enqueue không để outbox tồn tại.

### 4.2 Định danh và lưu trữ native

| Tham số native | Giá trị |
|---|---|
| `entryKind` | `flash-ticket-registration` |
| `realmId` | ID nội bộ realm của event |
| `ownerId` | Cùng `realmId`, để dedup cách ly theo realm |
| `containerId` | `null` |
| `correlationId` | `eventId` của event Keycloak, giữ nguyên khi publish lại |
| `entryType` | `identity.user-created.v1` |
| `payload` | JSON theo §5, serialize một lần trước enqueue |
| `metadata` | `null` |

Native dedup `(entryKind, ownerId, correlationId)`. Row ID native khác wire `eventId`; không nhầm hai giá trị. Dùng state/attempt/next-attempt/retention do native quản lý, không thêm bảng hoặc status mới. Mọi query/replay/purge phải scope theo đúng `entryKind` và realm; không tác động outbox Keycloak khác.

### 4.3 Worker và publisher

- Factory đăng ký timer tên `flash-ticket-registration-outbox` một lần sau khi Keycloak sẵn sàng, theo lifecycle server. Dùng `TimerProvider.scheduleTask(OutboxDrainerTask, interval, taskName)`; runner cấp session/transaction cho từng tick. Không giữ request session/EntityManager trong factory hoặc publisher.
- Một tick xử lý tối đa **1 row**, interval **2 giây**. Native store dùng pessimistic locking/skip locked. Giữ baseline một instance Keycloak; không thêm distributed lease/Redis lock. Đây là giới hạn throughput có chủ ý (~0.5 message/s lúc broker khỏe), chỉ tăng batch sau đo đạc; không nhận là HA.
- `OutboxConfig`: batchSize 1; backoff native với `maxAttempts = Integer.MAX_VALUE`, curve 2s/5s/10s/30s/60s (giữ jitter native); `pendingMaxAge=null`, `deadLetterRetention=null`, `deliveredRetention=7 ngày`. Retry lỗi broker không hết hạn theo thời gian; row lỗi không bị tự purge. Số attempt vẫn hữu hạn theo kiểu int, không hứa retry vô hạn toán học.
- `OutboxDeliveryHandler` là adapter nhỏ gọi RabbitMQ Java client. Tái sử dụng một connection; channel riêng cho lượt gửi, tối đa một message đang chờ confirm. Dùng client 5.30.0, pin dependency; runtime Keycloak cung cấp logging/API.
- Kết nối lazy từ worker; lỗi kết nối lần đầu vẫn được thử lại ở tick sau. Không chỉ dựa automatic recovery vì nó không xử lý kết nối ban đầu thất bại. Đặt connection/handshake timeout 2s; confirm timeout 2s; đóng channel hết hạn. Cấu hình timeout DB/runner để một lượt không treo timer; kiểm giới hạn thực tế khi fault test.
- Exchange `identity.ex`, routing key `identity.user-created.v1`, vhost `/`, `contentType=application/json`, encoding UTF-8, deliveryMode 2, messageId wire eventId, mandatory=true.
- Chỉ trả `OutboxDeliveryResult.delivered()` khi confirm ACK **và không có basic.return**. NACK/timeout/unroutable/connection lỗi → `retry(code)` với code an toàn; không ghi payload, credential hoặc raw exception message vào `last_error`.
- Native drainer đánh dấu `DELIVERED` và commit transaction sau publish. Crash sau confirm trước DB commit có thể gửi trùng; xử lý bằng consumer idempotent. `DELIVERED` chỉ chứng minh broker nhận/routed, không chứng minh MongoDB đã ghi.
- Factory shutdown hủy đúng timer và đóng resource broker; lỗi một tick không làm mất timer các tick sau. Không schedule trong mỗi `create(session)`.

## 5. Message contract V1

Exchange/routing key cố định; dùng record DTO ở mỗi module và một JSON Schema + fixture dùng chung trong repo làm contract. Không tạo shared Java module chỉ để chia sẻ một record. Dùng serializer Keycloak cung cấp (`JsonSerialization`) phía SPI; phía User dùng Jackson 3 policy hiện có.

```json
{
  "schemaVersion": 1,
  "eventId": "9fe8679b-759a-4529-bc49-e522c59da03b",
  "eventType": "identity.user-created.v1",
  "occurredAt": "2026-10-26T12:00:00Z",
  "realmId": "realm-id-from-keycloak",
  "identitySubject": "user-id-from-keycloak",
  "displayName": "Nguyễn Văn An"
}
```

- Bắt buộc: `schemaVersion` số nguyên bằng 1; `eventId` UUID string; `eventType` chính xác; `occurredAt` RFC3339 UTC; realmId/identitySubject string không trắng. Subject giữ nguyên, không ép UUID hoặc lower-case.
- `displayName` tùy chọn, không nhận null/blank nếu có. Ghép phần lastName rồi firstName đã trim, bỏ phần trống; cả hai trống thì bỏ field. Không fallback email.
- V1 là contract kín (`additionalProperties:false`); sai tên field, sai kiểu, thiếu field, unknown field, sai version/type/realm → lỗi vĩnh viễn, DLQ. Không bật polymorphic deserialization theo class/header do sender cung cấp.
- Consumer so realmId với biến `KEYCLOAK_IDENTITY_REALM_ID` lấy từ deployment đã xác minh; routing key cũng phải khớp. SPI so realm name `flash-ticket`; không tin metadata request.
- Không có email, roles, attributes, IP, token/password. Payload là snapshot đăng ký; không áp như profile update ở redelivery.
- JSON Schema/fixture phục vụ review/test contract, không thêm runtime JSON-schema engine; validate DTO tại boundary bằng các thư viện hiện có.

## 6. MongoDB, idempotency và ACK

- MongoDB hiện cấu hình standalone; chỉ ghi một document, không thêm transaction manager/inbox collection. Unique index `uq_users_identity_subject` trên `identitySubject` phải tồn tại trước bật consumer; nếu thiếu thì fail readiness/start listener, không tự sửa schema bằng runtime principal.
- `ensureRegistered(UserCreatedV1)` dùng `MongoTemplate.upsert` theo identitySubject và `$setOnInsert` toàn bộ trường tạo mới: `_id` UUID string mới, subject, createdAt/updatedAt từ occurredAt, version 0 và displayName nếu có. Không ghi `_class` hoặc trường ngoài validator.
- Document đã tồn tại giữ nguyên ID, displayName, dates, version. Không dùng `exists()+save()` hoặc update toàn bộ document.
- Race duplicate key chỉ coi thành công sau khi đọc lại đúng subject và xác nhận document hợp lệ; lỗi unique khác không bị nuốt.
- Yêu cầu write acknowledgment và journaling của write concern trước ACK, ví dụ `w=1,j=true` cho standalone; lỗi/timeout có thể là ghi thành công nhưng response mất, nên retry cùng phép upsert.
- MANUAL ACK `basicAck(tag,false)` sau ghi được xác nhận hoặc duplicate hợp lệ. ACK exception không bị đưa vào nhánh retry ghi/reject; kết thúc delivery bằng exception/channel recovery, broker redeliver khi mất channel. Không ACK/NACK lần hai trên tag không còn hợp lệ.
- Lỗi validation/deserialization/realm → reject `requeue=false`, broker chuyển qua `dlx.ex`/`user.identity.dlq` có sẵn. Không ACK message lỗi để bỏ qua.
- Lỗi Mongo tạm thời: 3 lần tổng cộng, nghỉ 1s rồi 5s. Dùng Spring Framework retry có sẵn, bao quanh **thao tác Mongo**, ngoài phần ACK; không thêm Spring Retry cũ hoặc tự viết framework. Hết lượt → reject `requeue=false` vào DLQ. Khi consumer crash, bộ đếm local có thể bắt đầu lại; không tuyên bố giới hạn tổng toàn bộ redelivery.
- Consumer dùng một concurrent consumer, prefetch 1 cho queue identity trong baseline; không thay prefetch listener khác hoặc broker topology. Không có retry queue mới.

## 7. Đóng gói, cấu hình và triển khai

- `services/keycloak-spi` thành JAR provider thường, Java release 21 khớp image; bỏ Spring Boot main/context test/repackage và AMQP starter. Keycloak artifacts 26.8.0 dùng provided; `keycloak-model-jpa` dùng provided để gọi native outbox. Không đóng gói lại Hibernate/Jakarta/Jackson/Keycloak bên trong plugin.
- Tạo image Keycloak dựa chính xác 26.8.0, COPY provider JAR và dependency RabbitMQ client cần thiết vào `/opt/keycloak/providers`, chạy `kc.sh build` theo guide. Không đổi version Keycloak để task chạy được.
- Config qua `.env` gốc/Compose: host `rabbitmq` khi cùng network server119, port5672, vhost `/`, username/password từ `RABBITMQ_IDENTITY_*`; không dùng `localhost` của container hoặc SSH tunnel trong production. User Service dùng principal ứng dụng hiện có.
- Provider ID `flash-ticket-registration-outbox`; realm `flash-ticket` bật listener này và giữ `jboss-logging`. Import realm chỉ phục vụ môi trường mới; realm đã tồn tại phải cập nhật danh sách listener qua Admin API/UI, không giả định `--import-realm` sẽ ghi đè.
- Config poll interval/enable flag là knob vận hành cần thiết; giữ exchange/routing/entryKind/version cố định. User thêm `KEYCLOAK_IDENTITY_REALM_ID` và write concern được xác minh, không đổi DB ownership hoặc validator.
- Triển khai cập nhật User Service trước, sau đó image Keycloak/listener. Backup trước thay image, lưu image cũ và config listener cũ. Không reset realm, database, volume hoặc copy secrets vào Git.
- Rollback: tắt listener/timer, quay image cũ; giữ nguyên outbox/PENDING để phục hồi. Có cửa sổ không capture đăng ký khi plugin tắt: phải công bố hoặc tạm dừng self-registration, không hứa tự backfill. Không drop native table.
- DLQ consumer là lỗi sau broker nhận, khác native DEAD_LETTER trước broker. Runbook replay sau sửa nguyên nhân, giữ nguyên eventId; không auto-replay hàng loạt.
- Log chỉ eventId/native rowId/attempt/errorCode/outcome và độ trễ; backlog và oldest pending kiểm bằng query scope hoặc công cụ sẵn có. Không thêm dashboard framework hoặc ACK ngược về Keycloak.

## 8. Điều kiện nghiệm thu

| Ca kiểm | Bằng chứng bắt buộc |
|---|---|
| Đăng ký form thành công | Keycloak user + outbox entryKind đúng + đúng một `tbl_users`, cùng subject |
| Đăng ký lỗi, admin tạo user, login/update, realm khác, social flow | Không phát message đăng ký thuộc phạm vi này |
| Ghi outbox lỗi | Request thất bại, user/outbox mới đều không tồn tại |
| Transaction đăng ký rollback sau enqueue | Không có row outbox hoặc user đã commit |
| RabbitMQ không truy cập được rồi phục hồi | Đăng ký vẫn thành công; row PENDING được giữ; tự publish và tạo user sau phục hồi |
| Broker unroutable hoặc NACK/timeout | Không DELIVERED giả; retry không mất row |
| Restart Keycloak khi PENDING | Worker khôi phục từ DB; không phụ thuộc memory queue |
| Crash sau broker confirm trước commit | Redelivery có thể xảy ra; Mongo vẫn một user |
| Mongo tạm lỗi/hết retry | Không ACK sớm; đúng 3 attempts ở delivery, sau đó DLQ; replay thành công |
| Mongo ghi thành công, ACK mất | Redelivery không tạo đôi/ghi đè user; ACK lại trên delivery mới |
| Sai field/realm/type/version, duplicate đồng thời | Poison vào DLQ; duplicate hợp lệ chỉ một document |
| Retention/replay | Chỉ tác động entryKind riêng; pending không bị tự xóa; eventId giữ nguyên |

Ca fault injection chạy trong môi trường/realm test tách biệt cùng version trước; không tắt broker shared server119 hoặc sửa quyền DB đang phục vụ để tạo lỗi. Nghiệm thu119 bằng tài khoản test riêng qua self-registration, query scope và evidence đã che dữ liệu nhạy cảm. Nếu thiếu Docker local thì báo chặn ca tích hợp; không đánh dấu hoàn tất bằng unit test thay thế.

## 9. Các lựa chọn của người viết cần duyệt cùng spec

- Tái sử dụng native `outbox_entry`/drainer thay bảng và worker tự viết; giới hạn tương thích theo phiên bản.
- Worker 2s/batch1; broker retry native có backoff, không tự xóa pending; DELIVERED giữ 7 ngày.
- Retry Mongo 3 attempts, 1s/5s; sau đó DLQ/replay vận hành; không transaction Mongo nhiều document.
- Contract kín V1 và displayName ghép lastName/firstName; unique subject giữ hồ sơ hiện hữu khi redelivery.
- Một deployment Keycloak và một identity consumer cho baseline; mở rộng chỉ sau đo tải.

Đã tự đọc lại theo builder-check vì phiên làm việc không có tool subagent: nguồn ghi/schema, API/version, transaction rollback, retry/ACK, startup và recovery đều có quyết định và tiêu chí kiểm. Những mặc định trên là đề xuất của người viết, không ghi là người dùng đã duyệt tài liệu này.
