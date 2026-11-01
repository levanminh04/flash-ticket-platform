# Booking Service tests

Run commands from this service directory with Java 21. The Maven Enforcer still requires
Java [21,22). On the author's machine the IntelliJ Community 2024.3 JBR supplies Java 21.

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
| SecurityConfigTests | public/protected/denied routes, buyer-only orders, internal-service-only internal access, CSRF/session policy, signatures/expiry/not-before/issuer/audience |
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

Run on Java 21 from this service directory:

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

Run the focused suites or the complete fast suite with Java 21:

```powershell
rtk .\mvnw.cmd "-Dtest=ErrorClassifierTests,ObservabilityTests,HttpErrorHandlingTests,SecurityConfigTests,InternalFeignConfigTests,BookingRabbitErrorHandlerTests,RuntimeConfigurationTests,WorkerErrorConfigTests" test
rtk .\mvnw.cmd test
```

Verification on 2026-10-24: the full fast suite ran **342 tests: 342 passed, 0 failures, 0 errors, 0 skipped**. Scoped Spotless apply/check passed for the 25 Java files in this implementation. Tests exercise the actual Boot JSON formatter and assert sentinels are absent from emitted JSON, HTTP error/security responses, Rabbit fatal logging and the worker error boundary. They also check Booking Rabbit queue/DLX definitions and policies statically.

The final full-suite output is recorded in
`.superpowers/sdd/2026-10-24-booking-error-handling/task8-done.log`;
the Task 6/7 targeted runs are in `task6-done.log` and `task7-done.log` in the
same execution ledger directory.

This run did not contact RabbitMQ, PostgreSQL, Redis, Config Server, Keycloak, an HTTP dependency or a log collector. The Rabbit test does not prove broker-side DLX routing or a real consumer's ACK-after-commit behavior. Booking still has no business consumer/outbox relay/retry worker; their examples in the RCA guide are TARGET. Publisher and worker fixtures check safe failure classification and specified ordering, not durable retry or settlement against a broker. Collector export/masking, tracing agent propagation, performance overhead, whole-module SpotBugs and production fault-injection RCA were not run.
