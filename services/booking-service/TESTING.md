# Booking Service tests

## Booking issuance results — 2026-10-29

New approved methods: three in BookingIssuanceResultListenerTests, one in
DtoValidationTests and thirteen in BookingPersistenceIT. Existing fixtures/callers
were updated for the DTO and relay rename; no new Java test helpers/types outside
the approved class/method list were introduced.

Coverage includes COMPLETED/FAILED, charge/count/version/type guards, acceptance
dependency, success after hold expiry, cancelled/closed events, all three inventory
modes, promotion release/replay, stale R1 vs R2, concurrent duplicate failures,
concurrent opposing outcomes, same messageId on different orders, atomic rollback,
two AUTO listener endpoints with their own DLQs, and dual-type outbox publishing.
OrderCompleted tests include persistent envelope metadata, broker return when its
binding is removed, retry, and publish-before-DB-rollback resend with unchanged ID.

Initial targeted unit/DTO/listener + real119 connection smoke: **101 passed**.
Initial thirteen-method IT run: **12 passed, one fixture error** before handler
execution. The fixture used maximumUsesPerUser=2, while existing PromotionConfig
requires exactly1; corrected to1 and the affected test passed on rerun. Production
guards were not relaxed to fix the fixture. Logs: `target/issuance-unit.log`,
`target/issuance-it.log`, `target/issuance-repair.log`.

Final full `-P integration verify` completed at **21:28:58 +07:00, 2026-10-29**:
**364 unit tests + 94 integration tests**, zero failures/errors/skips, BUILD SUCCESS.
This includes the expanded D10 races and D13 broker-return/retry cases. Maven's
current-run totals are authoritative; summing retained surefire XML can include
old reports. Log: `target/issuance-verify.log`. Existing lifecycle format/static
checks passed as part of verify; final `git diff --check` passed.
PostgreSQL/broker IT uses disposable Testcontainers. Tests truncate only the test
database and consume/purge dedicated test queues, not shared DB119 or Payment's
production queues. Context smoke opens a real Rabbit119 connection with listeners,
worker and dynamic declaration disabled; it does not prove a live issuance flow.
Actual Payment/Ticket producers/consumers and payment/refund E2E are not covered.

### Live Booking + RabbitMQ119 + PostgreSQL119

User-requested runtime check on 2026-10-29: **10/10 scenarios passed**. The actual
Booking JAR runs locally on8082 (PID11008, JVM timezone UTC), using root environment,
Config Server8888, PostgreSQL119 through the existing localhost5432 SSH tunnel,
and Rabbit119 with the application's own credential. Real BUYER/INTERNAL_SERVICE
tokens authorize order creation/promotion and PurchaseSnapshot HTTP calls; synthetic
SalesConfigured, AcceptPayment and issuance-result envelopes use business.ex and
the existing business queues. No broker permissions/topology or production code changed.

| Case | Observed result |
| --- | --- |
| 1 | QUANTITY + promotion: COMPLETED, committed resource counters unchanged; OrderCompleted outbox published |
| 2 | Same-ID replay ACKs without repeated effects; new-ID/different issuanceId replay is accepted; same ID/different hash is rejected to DLQ |
| 3 | FAILED QUANTITY returns purchased inventory, buyer limit and USED promotion; no success outbox |
| 4 | Same/new-ID FAILED replay never releases resources twice |
| 5 | FAILED STANDING returns the sector pool |
| 6 | FAILED SEATED returns seats; a new order holds them; old failure replay preserves the new reservation |
| 7 | FAILED after event cancellation releases resources, keeps order CANCELLED; new order gets409 EVENT_NOT_ON_SALE |
| 8 | FAILED after sale close releases resources; new order gets409 EVENT_NOT_ON_SALE |
| 9 | Wrong charge/count and wrong queue/state go to the correct DLQ, without inbox receipt or order/resource mutation |
| 10 | Concurrent COMPLETED/FAILED on separate queues commits one outcome; observed COMPLETED wins, opposing FAILED is rejected to DLQ |

Final audit: health UP; each of sales-configured/accept-payment/tickets-issued/
issuance-failed has one consumer, zero ready/unacked. Thirteen matching outbox rows
are published, and the corresponding persistent messages are present on Payment's
queues: nine decisions and four OrderCompleted. Four success-DLQ and one failure-DLQ
test messages have x-death reason rejected. The pre-existing eleven sales-configured
DLQ messages are unchanged. No queue was purged. All observed output/DLQ messages
belong to the synthetic orders; test records/messages remain available for inspection.

An initial observation run was stopped to reuse the script's DB connection instead
of reconnecting for every query. The next run hit one Rabbit Management HTTP
RemoteDisconnected before completing case1; dependent case2 was consequently not
executed. Cases3–10 passed. Cases1–2 were rerun successfully with a fresh HTTP
connection per management request; production code/configuration was unchanged.
Ten synthetic orders include the retained first successful setup run. One order
intentionally remains ISSUING after invalid results, and the R2 order remains
PENDING_PAYMENT after the seat-ownership scenario.

Evidence and rerunnable local harness (ignored build artifacts):
`target/issuance-live119-20261029/results.json`, `booking.log`, and
`target/issuance-live119.py` (`test`, `test 1,2`, `audit`). The evidence includes
input envelopes/IDs, per-case assertions, previous attempts and the final broker/DB
audit; no credentials/tokens. Actual Payment/Ticket handling, ticket issuance and
refund/Saga end-to-end remain outside this runtime check.

After injecting the root environment into the process securely, run:

```powershell
rtk mvn -f services/booking-service/pom.xml -B -ntp -Dtest=BookingIssuanceResultListenerTests,DtoValidationTests,AcceptPaymentListenerTests,BookingServiceApplicationTests test
rtk mvn -f services/booking-service/pom.xml -B -ntp -P integration verify
```

## AcceptPayment and outbox relay — 2026-10-29

New coverage: five methods in AcceptPaymentListenerTests, seventeen methods in
BookingPersistenceIT. Existing contextLoads now opens/closes a real Rabbit119
connection using environment credentials, with worker/listener/dynamic declarations
disabled. Delta C14 was explicitly approved by the user; no mock RabbitTemplate
is used by that smoke test.

Initial checkpoints: production compile passed; five listener/worker unit tests
and live-broker smoke passed (6/6). The first targeted IT run found fixture errors
(QUANTITY accidentally included seats), the wrong received-delivery-mode accessor,
and an unreliable pg_stat_activity lock observation. Those were corrected within
the approved test bodies; the four affected methods passed on rerun. PostgreSQL
and fault-injection broker tests use disposable Testcontainers, not shared DB119.

Remote relay checkpoint: 5/5 methods passed with RabbitMQ `3.106.109.119`, isolated
exchange `booking.accept-payment.test.eb7586b75989` and its dedicated queue. These
resources were absent before the run and deleted afterward (HTTP204). An admin
test credential was required to declare/read isolated resources; the application
credential has no configure permission. No permissions or production bindings
were changed and no Payment queue was purged/consumed. DB fixtures remained local.
Nack/timeout/connect-error subcases in D14 simulate the transport response;
the unroutable return, persistent delivery, actual confirms, rollback/resend and
concurrent publisher cases exercise the real broker. This does not prove Payment
consumer idempotency or payment-provider E2E success.

Local evidence: `target/accept-live-smoke.log`, `target/accept-it.log`,
`target/accept-it-repair.log`, `target/accept-remote119.log`.
Final full `-P integration verify` passed at 17:23:47 on 2026-10-29:
**360 unit tests +81 integration tests**, no failures/errors/skips, BUILD SUCCESS.
Log `target/accept-verify-final.log`; the full81-test XML report is preserved as
`target/accept-full-81-tests.xml`. D3 verifies accepted-charge replay after the
actual DB expiry time; D4 includes concurrent same-messageId commands for distinct
orders; D17 exercises all four outcomes through command and response broker queues.
Nack/connect-timeout faults are clearly separated from real broker cases above.

Final repeat of the five relay methods against119 also passed after the D17
four-outcome expansion. Dedicated exchange `booking.accept-payment.test.1d36822b890a`,
queue with suffix `.q`, and the unroutable-test exchange were all deleted after
the run (HTTP204). Latest result is in `target/accept-remote119.log`.

Run Maven with root environment variables injected securely into its process;
do not put secrets on CLI flags or commit tokens. Example commands once env is set:

```powershell
rtk mvn -B -ntp -Dtest=BookingServiceApplicationTests,AcceptPaymentListenerTests test
rtk mvn -B -ntp -P integration verify
```

## Order promotion and purchase snapshot — 2026-10-29

New coverage: seven HTTP methods in OrderControllerTests and sixteen PostgreSQL
methods in BookingPersistenceIT. They cover FIXED/PERCENT/HALF_UP, replay, six
promotion errors, last-use/buyer quota races, apply-vs-freeze ordering, immutable
snapshot replay, three selection modes, config-version changes, rollback, deadline
after lock wait and transaction-local 5s lock timeout.

Observed: `mvn -B -ntp test` passed 355 tests; the targeted PostgreSQL run passed
19 tests (16 new plus 3 existing matches), no failures/errors/skips. Tests used
a disposable local Testcontainers PostgreSQL 17 database, not server 119.
The first targeted run exposed missing cleanup in new tests; finally cleanup
now follows the existing disposable-database pattern. No cleanup is run against
the team's shared database.

Final full-module `-P integration verify` passed at 13:52:40 on 2026-10-29:
**355 unit tests + 64 integration tests**, no failures/errors/skips, BUILD SUCCESS.
Local log: `target/promotion-verify-final.log`. `git diff --check` also passed.
Existing global-count tests now start with clean disposable data; the test that
alters columns evicts pooled connections holding obsolete PostgreSQL prepared plans.
The snapshot-lock DB-clock test now sets the closing time after observing the
blocked request, before releasing the lock, avoiding a narrow timed wait.
Production transaction lock timeout remains 5s; assertions still require the
specific business result, never an arbitrary technical failure.

```powershell
rtk mvn -B -ntp test
rtk mvn -B -ntp -P integration test-compile failsafe:integration-test failsafe:verify "-Dit.test=BookingPersistenceIT#*Promotion*+*Snapshot*+checksDeadlineAfterWaitingForOrderLock"
rtk mvn -B -ntp -P integration verify
```

These are Booking component/database checks, not a live Payment/Keycloak/broker
checkout or payment-gateway acceptance. The service has not been deployed by this task.

Run commands from this service directory with JDK 23. Compilation targets Java 23;
Maven Enforcer requires Java [23,24). Keep Enforcer enabled for verification.
Older results below retain the Java version and Enforcer exceptions used at the time.

## Live curl acceptance — 2026-10-29

**10/10 cases passed** against the real Booking process at `http://localhost:8082`,
PostgreSQL 16.15 on server 119 through SSH tunnel `127.0.0.1:5432`, RabbitMQ 119,
and actual Keycloak BUYER / INTERNAL_SERVICE tokens. Config Server ran on 8888;
Discovery on 8761 registered Booking UP. Booking and Config health both returned
HTTP 200 / UP after the run. This was not a mocked HTTP or MockMvc run.

| Case | Observed HTTP/result and DB checks |
| --- | --- |
| 1. Apply PERCENT | 200; subtotal400000, discount40000, total360000; held1/usage1 |
| 2. Same voucher, normalized code | 200; total/rowVersion/held/usage unchanged |
| 3. Apply FIXED | 200; subtotal200000, discount25000, total175000; usage1 |
| 4. Unknown code | 422 PROMOTION_NOT_FOUND; no order/usage change |
| 5. Different code on an order with voucher | 422 ORDER_ALREADY_HAS_PROMOTION; original discount preserved |
| 6. Prepare and replay PurchaseSnapshot | Both 200; snapshot stored; replay identical, frozenAt/version/money unchanged |
| 7. Apply after freeze | 409 PRICE_FROZEN; frozen data unchanged |
| 8. Auth boundaries | 401 without token; 403 for BUYER on internal API and INTERNAL_SERVICE on BUYER API |
| 9. Expired order | Both APIs 409 ORDER_EXPIRED; no usage/freeze/money change |
| 10. Concurrent final-use requests | Two orders: exactly one 200 and one 422 PROMOTION_EXHAUSTED; held1/usage1; losing order unchanged |

Local request/response evidence: `target/curl-live-20261029/results.json`;
runtime logs/PIDs and seed envelope are in the same ignored directory.
Runner: `target/curl-live-cases.py`; curl credentials/tokens are passed through
stdin, not recorded in the evidence. Seed event:
`113bc45b-7f96-4921-b47e-b30141ca2645`, title prefixed TEST CURL, created via
RabbitMQ `event.sales-configured.v1`. Only newly created test rows were used;
existing events/orders were not edited. Synthetic fixtures remain for inspection.

Case9 first failed during fixture setup: updating only order.expires_at violated
the reservation composite FK. No application request had failed in that case.
The fixture now updates order/reservation deadlines in one SQL CTE statement;
both actual HTTP checks then passed. No FK or production code was changed.

Runtime loaded the root `.env`; missing `KEYCLOAK_TOKEN_URI` and
`INTERNAL_SERVICE_CLIENT_SECRET` were supplied only to the Booking process using
Keycloak's actual endpoint/client secret, without editing `.env`. Config Server
used its existing JAR with native search location pointing to current repository
YAML files. JVM23 used UTC. These cases cover QUANTITY; they do not establish live
SEATED/STANDING checkout, Payment-provider E2E behavior, or load-test capacity.

## Sales configuration consumer — 2026-10-27

Final command, with JDK 23 and Enforcer enabled:

```powershell
rtk proxy .\mvnw.cmd -B -ntp -Pintegration spotless:apply verify "-Dit.test=BookingPersistenceIT"
```

**338 unit tests + 27 integration tests passed**, no failures/errors/skips;
Spotless passed and SpotBugs reported zero findings. Local output:
`target/sales-config-final-verify.log`.

The service transaction is invoked through the real Spring proxy against an
isolated PostgreSQL 17 Testcontainer. Tests cover all three inventory modes,
promotion counters, rollback on a real unique-constraint failure at flush/commit,
duplicate/hash conflicts, stale and conflicting source versions, concurrent first
creation, reserved seats, event ownership and the CANCELLED guard.

`InboxMessage` and `EventSalesSnapshot` have nullable-in-Java boxed `@Version`
fields so new assigned-ID entities use INSERT through repository `save()`.
A duplicate inbox INSERT is tested to roll back without overwriting its hash.
The upgrade test removes both version columns in the disposable database,
inserts legacy rows, runs Hibernate `hbm2ddl.auto=update` for these two entities,
and verifies NOT NULL columns/default 0 and version 0 on the legacy rows before
checking subsequent JPA INSERT/UPDATE and optimistic conflict behavior.

The same integration suite starts an isolated RabbitMQ 4.3.6 container with
durable quorum test queues. A real Jackson converter and Spring AMQP method
adapter call the transactional service; a successful delivery commits its
snapshot/inbox and is absent from the queue after the container stops. Invalid
schema is rejected into the test DLQ without extra DB effects. Three focused
listener tests also check successful dispatch, permanent rejection and propagation
of database failures. Logs retain the business error code through the AMQP reject
wrapper.

No production database or queue on server 119 was changed or consumed. This does
not verify Event's publisher/outbox, a multi-node broker, or production deployment.
The default broker delivery-limit/redelivery policy remains the configured native
policy; this task adds no retry worker or application backoff mechanism.

## JDK 23 verification — 2026-10-26

`clean verify` passed with Maven 3.9.16 and JDK 23.0.1, Enforcer enabled:
**336 tests, 0 failures/errors/skips**, Spotless clean and **0 SpotBugs findings**.
Clean compilation produced Java 23 bytecode (class major version 67).
Local output: `.superpowers/jdk23/booking-service.log` at repo root.
This run used test fixtures/loopback servers, not live infrastructure or the integration profile.

## Fast suite

```powershell
rtk .\mvnw.cmd test
```

This runs unit tests, real MapStruct mappings, Bean Validation, Jackson contract tests,
the application context smoke test, configuration binding from the actual Config Server
YAML, and the HTTP security filter chain. HTTP security uses locally signed RSA JWTs and
an ephemeral loopback JWK server; it never contacts Keycloak. Redis/RabbitMQ configuration
tests create clients without opening broker/cache connections.

| Test class | Behavior protected |
|---|---|
| ContractRulesTests | money/version boundaries, time windows, exact totals, sales/purchase reference integrity and payload registry |
| DtoValidationTests | valid/invalid requests, nested DTO constraints, envelope metadata and all eight message types, payment/issuance decisions |
| BookingJsonTests | QUANTITY/SEAT_MAP and FIXED/PERCENT discriminators, strict coercion, unknown/missing fields, money/version precision |
| MapperTests | money/version conversion, immutable seat lists, typed JSON, Order views and all eight Outbox round trips |
| EntityBehaviorTests | bidirectional collection helpers, null rejection and four composite key contracts |
| KeycloakRealmRoleConverterTests | business/internal role whitelist, deduplication, missing/malformed/unknown claims |
| SecurityConfigTests | public/protected/denied routes, buyer-only orders, internal-service-only internal access, CSRF/session policy, signatures/expiry/not-before/issuer |
| RuntimeConfigurationTests | actual Booking YAML binding, AMQP conversion and template wiring, no topology, Redis connection settings, OAuth2/Feign settings and internal-client scan |
| InternalFeignConfigTests | actual Feign HTTP calls, machine token acquisition/reuse/expiry, buyer isolation, opt-in scope, failure/timeout behavior and strict JSON |
| BookingServiceApplicationTests | isolated full application startup |

Do not disable known-failing cases to obtain a green result.

## PostgreSQL integration suite

Docker with a working Linux engine is required. Testcontainers owns a disposable
`postgres:17-alpine` instance and database. No connection to the project's shared database
is made. The first run may download the image.

```powershell
rtk .\mvnw.cmd -P integration test-compile failsafe:integration-test failsafe:verify
```

This runs the integration suite independently. Use
`rtk .\mvnw.cmd -P integration verify` for the normal lifecycle
(which also runs the repository's whole-module Spotless and SpotBugs gates).

BookingPersistenceIT invokes psql inside the disposable container to apply
`docs/architecture/data/postgres/20-booking.sql` followed by
`services/99-table-prefix.sql`, matching the deployment initializer's schema and
table-naming steps. The database is named booking_db so the scripts' guards and
psql directives run unchanged. Hibernate `ddl-auto=none` cannot invent replacement tables.

Sixteen integration cases cover entity/DDL compatibility; all twelve repositories;
JSONB, enum, timestamp and money persistence; composite IDs; cascade through order/item/seat
and reservation/allocation; stale row versions; inventory check/FK constraints;
inbox uniqueness; successful idempotency statuses; outbox JSON shape; and dedicated
Jackson 3 Inbox/Outbox JSONB round trips.
Tests roll back their own data. Missing Docker is an error, not a silent skip.

## Results and discovered defects (2026-10-24)

Before the DTO fixes, the fast run on Java 21 executed 231 cases: 226 passed,
one failed and four errored, with zero skipped. The five red cases exposed:

1. `BookingJsonTests.seatMapDiscriminatorsSelectTheCorrectType` (SEATED and STANDING):
   the outer mode selects the abstract SeatMapSelection, but Jackson does not dispatch
   again using its nested kind annotation.
2. `BookingJsonTests.promotionDiscriminatorRoundTrips` (FIXED and PERCENT):
   Jackson consumes kind as the type discriminator, leaving the required record
   constructor property missing.
3. `DtoValidationTests.nestedPromotionDiscountMustBeValidated`:
   PromotionConfig.discount does not cascade Bean Validation, so percent=101 is accepted.

The approved fixes add explicit OrderSelection mode/kind dispatch through the existing
strict Jackson mapper, retain PromotionConfig.Discount.kind using EXISTING_PROPERTY
and visible=true, and cascade validation into PromotionConfig.discount with @Valid.
Computed read-only mode/kind properties preserve the flat B13 serialization shape.

After reproducing the five failures, the updated fast suite executed 249 cases:
all passed, zero skipped. Added regression coverage checks full selection round trips,
SEAT_MAP inside CreateOrderRequest, invalid discriminator combinations, missing/unknown
fields, strict nested integer coercion, and a single retained promotion kind property.

After Docker Desktop was started, the integration suite ran on Docker 28.0.4 and a real
PostgreSQL 17 container: 16 cases, all passed, zero skipped.
The deployed schema sequence initialized successfully. All 15 entity table names resolve.
Inventory checks/FKs, inbox uniqueness, idempotency status constraints and outbox JSON
shape passed. The earlier missing-table failure was a test fixture defect: it omitted
the official prefix migration, and was not evidence of incorrect production entity names.

The subsequent Jackson 3 fix sets
`spring.jpa.properties.hibernate.type.json_format_mapper: jackson3` in Booking's
Config Server YAML. The integration context reads that actual YAML instead of a
test-only mapper override. Dedicated Inbox/Outbox tests first reproduced the
Jackson 2 versus Jackson 3 JsonNode failure, then passed after the configuration change.
The full repository round trip, cascade and stale-version tests also now pass.

Before the DTO fixes, the combined run contained 247 cases: 242 passed and five
non-passing, zero skipped. Testcontainers removed its PostgreSQL and helper containers
after that run.

Post-fix verification reran both suites on Java 21: 249 fast tests and 16 PostgreSQL
integration tests passed, for 265 total, zero failures/errors/skips. Docker Desktop
was started for this run. The integration fixture still applies the deployed DDL
and table-prefix migration inside a disposable PostgreSQL container, never production.
An independent read-only review of the scoped DTO fix found no actionable issues.

## Booking internal authorization

Booking maps the exact realm role INTERNAL_SERVICE to ROLE_INTERNAL_SERVICE and
requires it for `/internal/v1/**`. BUYER, ORGANIZER, ADMIN, unknown roles and role
aliases are rejected there. Anonymous requests and invalid JWTs receive 401.
Machine tokens cannot access BUYER-only orders or non-health actuator endpoints.
Both GET and POST internal authorization are exercised through a test-only probe
controller, real signed JWTs, and the production security filter chain.

The new converter and machine-access tests first failed (no mapped authority and
403 instead of 200), then the focused security run passed all 37 cases.
The final rerun passed 256 fast tests and all 16 PostgreSQL integration cases:
272 total, zero failures/errors/skips. Independent security review found no
actionable issues. Gateway files remain unchanged.
This change configures inbound authorization only: no Gateway changes, outbound
client-credentials implementation, or new business endpoint handlers are included.

## Booking outbound HTTP: OpenFeign

OpenFeign is prepared for future internal HTTP contracts. There is currently no
production outbound business client in Booking: payment initiation still uses
RabbitMQ as described in B13/B14. Test-only probe interfaces are never scanned
into the production application.

Provide these variables to the Booking runtime:

- `KEYCLOAK_TOKEN_URI`: the reachable Keycloak token endpoint.
- `INTERNAL_SERVICE_CLIENT_SECRET`: the secret of the existing `internal-service` client.

The Spring registration ID is `internal`; the grant is `client_credentials`, with
`client_secret_basic`. Keycloak must issue the existing machine role
`INTERNAL_SERVICE` and audience `flash-ticket-api`. No user-management/admin
credential is used by this client.

When an actual HTTP contract requires a client, place its interface under
`com.flashticket.booking_service.client.internal`, annotate it with
`@FeignClient(configuration = InternalFeignConfig.class)` and configure its target
URL in Config Server. Declare methods from that approved contract, then constructor
inject the interface into its caller. No manual Bearer header or token lookup is needed.

`InternalFeignConfig` intentionally has no `@Configuration`/`@Component`: Feign
loads it only for clients opting in. Keep
`spring.cloud.openfeign.oauth2.enabled=false`; enabling the global OAuth2
interceptor would add a second interceptor and affect unrelated clients.
External clients must not choose `InternalFeignConfig`.

The authorized-client store is Boot's in-memory store. Tokens use the stable key
`(internal, booking-service)`, independent of the logged-in buyer; background calls
work without a request SecurityContext. The first call obtains a token, later calls
reuse it until renewal is needed. Concurrent cold calls may obtain multiple valid
tokens. Restarting the process clears the cache.

Feign requests and token requests use connect/read timeouts of 2000/5000ms from
the central YAML. Feign logging is NONE. Token acquisition failure stops the
resource call. HTTP errors propagate to the caller; 401 does not automatically
evict/reacquire a token or replay the request. Keep the default NEVER_RETRY policy,
especially for POST requests. Do not perform HTTP while holding database locks.

Run on JDK 23 from this service directory:

```powershell
rtk .\mvnw.cmd "-Dtest=InternalFeignConfigTests,RuntimeConfigurationTests" test
rtk .\mvnw.cmd test
```

Verification for this change (2026-10-24): the new YAML test first failed because
the registration was absent; the actual Feign call first failed because there was
no Authorization header. After implementation, all 20 Feign cases passed.
The full fast suite passed 278 tests with zero failures/errors/skips, including
the existing signed-JWT receiver and strict-JSON tests. Scoped Spotless checks
cover the four Java files changed by this task. PostgreSQL integration was not
rerun for this HTTP-only change.

Token/resource servers in the Feign tests are disposable loopback HTTP servers;
their fake tokens verify outbound header/lifecycle behavior, not JWT signatures.
The tests do not contact real Keycloak, Payment or any deployment endpoint.

## Reports and formatting

```powershell
rtk .\mvnw.cmd jacoco:report
rtk .\mvnw.cmd spotless:check "-DspotlessFiles=.*src.test.java.*"
```

Surefire reports: `target/surefire-reports/`; Failsafe: `target/failsafe-reports/`;
coverage: `target/site/jacoco/index.html`. Coverage records executed production paths,
including paths reached by failing tests; it is not evidence that the suite is green.

The whole-module Spotless/SpotBugs baseline debt reported by the earlier scaffold task
is outside this change. Formatting checks passed for all test sources and the changed
BookingJsonConfig, OrderSelection, OrderSelectionDeserializer and PromotionConfig sources.

## Error handling and RCA boundaries

Run the focused suites or the complete fast suite with JDK 23:

```powershell
rtk .\mvnw.cmd "-Dtest=ErrorCodeTests,RcaLoggingTests,HttpErrorHandlingTests,SecurityConfigTests,InternalFeignConfigTests,BookingRabbitErrorHandlerTests,RuntimeConfigurationTests" test
rtk .\mvnw.cmd test
```

Verification on 2026-10-24: the full fast suite ran **342 tests: 342 passed, 0 failures, 0 errors, 0 skipped**. Scoped Spotless apply/check passed for the 25 Java files in this implementation. Tests exercise the actual Boot JSON formatter and assert sentinels are absent from emitted JSON, HTTP error/security responses, Rabbit fatal logging. They also check Booking Rabbit queue/DLX definitions and policies statically.

The final full-suite output is recorded in
`.superpowers/sdd/2026-10-24-booking-error-handling/task8-done.log`;
the Task 6/7 targeted runs are in `task6-done.log` and `task7-done.log` in the
same execution ledger directory.

This run did not contact RabbitMQ, PostgreSQL, Redis, Config Server, Keycloak, an HTTP dependency or a log collector. The Rabbit test does not prove broker-side DLX routing or a real consumer's ACK-after-commit behavior. Booking still has no business consumer/outbox relay/retry worker; their examples in the RCA guide are TARGET. Historical publisher/worker fixtures were synthetic; they were removed during simplification because those production flows do not exist yet. Collector export/masking, tracing agent propagation, performance overhead, whole-module SpotBugs and production fault-injection RCA were not run.

## Error handling simplification — 2026-10-25

`ErrorCode` replaces `BusinessErrorCode` and centralizes safe messages and default
HTTP statuses. Classification accepts enum values; logging uses that result directly.
HTTP error logging uses one request flag, including wrapped error redispatches.
Nested validation reports only DTO root fields; client-controlled map keys are not echoed.
Malformed parameters now use the common `INVALID_REQUEST` message as well as its code.
The unused scheduler configuration and its synthetic worker tests were removed;
add a scheduler error handler when an actual scheduled job is implemented.

The refactor keeps the Problem JSON shape, business codes/statuses, 401/403 Bearer
responses, correlation/MDC handling and secret/stack-trace filtering. Two added HTTP
regression tests first failed against the old implementation (duplicate logging and
nested-field output) and passed after the change.

Verification command: `rtk proxy .\mvnw.cmd -B -ntp "-Denforcer.skip=true" verify`.
The machine had JDK 23; compilation still targeted Java 21. Java 21 Enforcer was
explicitly skipped, so this run does not prove the Java 21 runtime gate passed.
Full results: **341 tests passed, 0 failures, 0 errors, 0 skipped**; Spotless passed;
SpotBugs reported **0 findings**. Output: `target/errors-verify.log` (local build artifact).
No live database/broker/identity service, collector or business worker was exercised.

### ShopApp-style advice and direct RCA logging — earlier iteration

`GlobalExceptionHandler` is now a plain `@RestControllerAdvice` with direct
business, validation and fallback handlers. It no longer extends Spring's
`ResponseEntityExceptionHandler`. The `observability` package, `FailureLog`,
`ErrorClassifier` and its `Failure` record were removed. Native error mapping
is `ErrorCode.from(Throwable)`; HTTP and Rabbit boundaries use SLF4J directly.
`RequestCorrelation` contains only HTTP ID helpers; the filter restores MDC in
`finally`, and Feign forwards the current canonical UUID.

JSON filtering/renaming/metadata use native Spring Boot YAML settings. Two small
config hooks retain numeric schemaVersion/UTC timestamps and safe stack frames.
Console encoding is explicitly UTF-8 so Vietnamese reasons survive Windows output.
The new tests cover meaningful rejection reasons, HTTP method/status/correlation,
MDC restoration/trace normalization and the actual JSON output, including duplicate
field and secret-leak checks. Synthetic publisher/worker fixtures were removed;
those production flows have not been implemented.

Current verification (2026-10-25):

```powershell
rtk proxy .\mvnw.cmd -B -ntp "-Denforcer.skip=true" verify
```

**340 tests passed, 0 failures, 0 errors, 0 skipped.** Whole-module Spotless passed;
SpotBugs reported **0 findings**, and Maven reported **BUILD SUCCESS**.
Output: `target/simple-errors-verify.log` (local build artifact).
This machine used JDK 23 with Java 21 compilation target and the Java 21 Enforcer
check explicitly skipped. No live database, broker, Keycloak or collector was used;
the Feign tests use disposable loopback HTTP servers. This verifies the scaffold
boundaries, not production business flows or RCA end-to-end.

### Advice-only HTTP errors — current implementation

The approved scope is now one `exception/GlobalExceptionHandler`. The complete
`web.error` package was removed: no `ProblemResponses`, `CorrelationFilter`,
`RequestCorrelation` or custom `BookingErrorController`. The advice creates Problem
responses and logs errors directly. Security callbacks delegate to Spring MVC's
`HandlerExceptionResolver`; signed JWT/role tests still verify 401/403 and Bearer
challenges. No RCA message publisher was added; Rabbit consumer disposition is unchanged.

Success responses no longer receive request/correlation headers from a Booking
filter. Error responses still have a safe correlation UUID matching their body/header
and log. Invalid optional tracing headers cannot prevent the original response.
Tests for removed context/filter/error-controller behavior were dropped; native
Boot `/error` responses are no longer required to have the application Problem shape.
The remaining tests verify business/validation/framework statuses, header safety,
security, local JSON logs and the absence of the removed success-response headers.

Verification on 2026-10-25: `rtk proxy .\mvnw.cmd -B -ntp "-Denforcer.skip=true" verify`
completed with **336 tests passed, 0 failures/errors/skips**, whole-module Spotless
passed, **0 SpotBugs findings**, and **BUILD SUCCESS**.
Output: `target/advice-only-verify.log` (local build artifact).
JDK 23 compiled for Java 21; the Java 21 Enforcer gate was explicitly skipped.
Tests used local fixtures/loopback servers; no deployed DB, broker, Keycloak or
RCA collector was exercised. The RCA publishing/integration pipeline remains deferred.
