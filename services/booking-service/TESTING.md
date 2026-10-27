# Booking Service tests

Run commands from this service directory with JDK 23. Compilation targets Java 23;
Maven Enforcer requires Java [23,24). Keep Enforcer enabled for verification.
Older results below retain the Java version and Enforcer exceptions used at the time.

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
