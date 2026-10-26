# Keycloak registration outbox

Provider ID: `flash-ticket-registration-outbox`. Keycloak26.8.0 only; compile Java release21.

Build from this directory: `mvn -B -ntp clean verify`. The JAR is a regular provider, not a Spring application. Only the AMQP client5.26.0 JAR is copied beside it; newer5.27+ brings Netty4.2 conflicting with Keycloak's Netty4.1. Do not copy all Maven dependencies into providers.

The listener accepts only successful REGISTER with register_method=form in flash-ticket. It uses Keycloak's native OutboxStore in the request transaction. Capture failure marks rollbackOnly because EventBuilder catches listener exceptions. The native outbox APIs are internal and version pinned; upgrading Keycloak requires integration verification.

Worker uses TimerProvider after PostMigrationEvent and OutboxDrainerTask, batch1 every2s. Native backoff2/5/10/30/60seconds with jitter; pending rows do not expire. DELIVERED retention7days. Throughput is deliberately limited; increase batch only after measuring backlog. Sender confirms mandatory routing and persistent messages; DELIVERED is broker acceptance, not consumer success.

Runtime variables: FT_IDENTITY_OUTBOX_ENABLED=true, FT_IDENTITY_OUTBOX_INTERVAL_MS=2000, FT_IDENTITY_RABBITMQ_HOST=rabbitmq, FT_IDENTITY_RABBITMQ_PORT=5672, FT_IDENTITY_RABBITMQ_VHOST=/, FT_IDENTITY_RABBITMQ_USERNAME/PASSWORD (dedicated identity principal). Secrets only through deployment environment.

Native table: keycloak_db.public.outbox_entry. Scope all operations to entry_kind=flash-ticket-registration and the correct realm. No custom table/JPA entity. Never purge PENDING or modify other kinds. No backfill of existing users. Broker connection is lazy; initial failure is retried too.

Deploy User first, then build this image on119 from the prepared artifacts and update Keycloak image. Existing realm import is not overwritten by --import-realm: append the listener through authenticated Admin API or UI, keeping jboss-logging. Back up DB and old listener list/image first. Rollback disables this listener and restores old image; retain native outbox rows. Registrations during disabled capture need explicit reconciliation or a registration pause.

User requires KEYCLOAK_IDENTITY_REALM_ID. It consumes user.identity.q with manual ACK, validates the closed V1 contract, writes one insert-only user with w1/journaled, and retries only transient DB failures3times (1s/5s). Invalid/exhausted messages go to existing DLQ. ACK failure closes channel for redelivery. Replay after fixing the cause and preserve eventId; no automatic DLQ replay.

Direct119 acceptance requested by user. Unit/boundary tests plus an opt-in Mongo concurrency test are automated. The Mongo test `mvn -Dtest=IdentityUserRegistrarLiveIT test` in User requires IDENTITY_LIVE_TEST=true and MONGO_URI and deletes only its own random identity-it subject. Destructive broker/DB failure tests on shared119 are excluded; evidence must clearly identify NOT RUN cases.
