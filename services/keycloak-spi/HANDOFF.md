# Keycloak registration outbox

Provider ID: `flash-ticket-registration-outbox`. Keycloak26.8.0 only; compile Java release21.

Build from this directory: `mvn -B -ntp clean verify`. The JAR is a regular provider, not a Spring application. Only the AMQP client5.26.0 JAR is copied beside it; newer5.27+ brings Netty4.2 conflicting with Keycloak's Netty4.1. Do not copy all Maven dependencies into providers.

Code theo mẫu Shop App: listener/publisher dùng `@RequiredArgsConstructor` với dependency `private final`; DTO dùng `@Getter`, `@Builder` và constructor kiểm tra dữ liệu. Lombok1.18.46 chỉ dùng lúc compile, không đưa vào providers. API transaction, outbox và timer do Keycloak quản lý, không dùng `@Service` hay `@Transactional` của Spring trong SPI.

Từ 28/10/2026, SPI gửi `Envelope<UserCreatedV1>`: đủ 10 field theo B13; `data` chỉ chứa realmId/identitySubject/displayName. messageId và correlationId là ID sự kiện Keycloak; aggregateId là UUID user; aggregateVersion là chuỗi "1" cho lần đăng ký. causationId và traceparent xuất hiện với null. Payload được lưu nguyên vào native outbox, retry giữ nguyên metadata. Ví dụ: `docs/contracts/identity/user-created-envelope-v1.example.json`. Fixture phẳng cũ được giữ để người phụ trách user-service chuyển consumer ở branch riêng; không dùng fixture đó làm format SPI mới. Cần consumer hiểu envelope trước khi xử lý message mới. Row outbox cũ vẫn giữ payload cũ, không tự sửa lịch sử.

Gate thực thi 28/10/2026: user xác nhận thêm Envelope<T> với 10 field, @Getter/@Builder/constructor validation; đổi constructor UserCreatedV1 còn 3 field; sửa onEvent và các test hiện có. Nguồn: phản hồi "xác nhận" sau danh sách thực thi trong chat. Phạm vi chỉ SPI; user-service ở branch khác.

Kiểm chứng envelope: Maven package chạy 14 test, 0 failure/error. Image triển khai: `flash-ticket/keycloak:26.8.0-identity-envelope-20261028`. JAR SHA-256: `38ab2974f70f182af64f5c523ece0da23a9fad4fc86499de6aca2728464e3fde`. Backup DB và compose trước deploy lưu riêng trên server tại `/home/ubuntu/spi-envelope-20261028/`; không commit backup DB vào Git.

The listener accepts only successful REGISTER with register_method=form in flash-ticket. It uses Keycloak's native OutboxStore in the request transaction. Capture failure marks rollbackOnly because EventBuilder catches listener exceptions. The native outbox APIs are internal and version pinned; upgrading Keycloak requires integration verification.

Worker uses TimerProvider after PostMigrationEvent and OutboxDrainerTask, batch1 every2s. Native backoff2/5/10/30/60seconds with jitter; pending rows do not expire. DELIVERED retention7days. Throughput is deliberately limited; increase batch only after measuring backlog. Sender confirms mandatory routing and persistent messages; DELIVERED is broker acceptance, not consumer success.

Runtime variables: FT_IDENTITY_OUTBOX_ENABLED=true, FT_IDENTITY_OUTBOX_INTERVAL_MS=2000, FT_IDENTITY_RABBITMQ_HOST=rabbitmq, FT_IDENTITY_RABBITMQ_PORT=5672, FT_IDENTITY_RABBITMQ_VHOST=/, FT_IDENTITY_RABBITMQ_USERNAME/PASSWORD (dedicated identity principal). Secrets only through deployment environment.

Native table: keycloak_db.public.outbox_entry. Scope all operations to entry_kind=flash-ticket-registration and the correct realm. No custom table/JPA entity. Never purge PENDING or modify other kinds. No backfill of existing users. Broker connection is lazy; initial failure is retried too.

Publisher uses a reusable connection and a per-delivery channel closed by try-with-resources. `waitForConfirmsOrDie(2000)` throws on NACK or confirm timeout; both return RETRY with BROKER_UNAVAILABLE. A mandatory return still reports BROKER_UNROUTABLE. Channel close failure also returns RETRY, so delivery may repeat after broker acceptance; consumer idempotency remains required.

Deploy User first, then build this image on119 from the prepared artifacts and update Keycloak image. Existing realm import is not overwritten by --import-realm: append the listener through authenticated Admin API or UI, keeping jboss-logging. Back up DB and old listener list/image first. Rollback disables this listener and restores old image; retain native outbox rows. Registrations during disabled capture need explicit reconciliation or a registration pause.

User requires KEYCLOAK_IDENTITY_REALM_ID. It consumes user.identity.q with Spring AUTO acknowledgement: the container ACKs after successful processing. It validates the V1 payload, writes one insert-only user with w1/journaled, and retries only transient DB failures3times (1s/5s). Invalid/exhausted messages are rejected without requeue and routed to the configured DLQ. Unacknowledged deliveries can be redelivered, so the insert remains idempotent. Replay after fixing the cause and preserve eventId; no automatic DLQ replay.

Direct119 acceptance requested by user. Unit/boundary tests plus an opt-in Mongo concurrency test are automated. The Mongo test `mvn -Dtest=IdentityUserRegistrarLiveIT test` in User requires IDENTITY_LIVE_TEST=true and MONGO_URI and deletes only its own random identity-it subject. Destructive broker/DB failure tests on shared119 are excluded; evidence must clearly identify NOT RUN cases.
