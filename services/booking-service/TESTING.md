# Booking Service tests

## Availability Redis cache — 2026-11-04

Implemented the approved spec v0.4 / plan v0.1 on branch
`codex/booking-availability-redis-cache`, worktree
`D:/DATN/flash-ticket-platform-redis-cache`, based on dev/son
`ff72b286df051458a7e4635412e816b1402ce6a2`. The original dev/tuyen checkout was
not used for application changes. EXECUTING-PLANS approval covers C1–C10/M1–M49;
no declaration/signature delta was needed.

GET availability now uses `AvailabilityCacheService`, the existing transactional
DB service and a concrete `RedisService` with five string operations. Default JSON
TTL is 15 minutes; reads do not extend it. Inventory/configuration/cancellation
writers publish an event inside their existing transaction; the AFTER_COMMIT
listener changes the affected event's generation. A reader holding the previous
generation can only populate the previous data key. JSON schema, no-store header,
authorization, inventory locks and idempotency guards are preserved.

Configuration names added to root `.env.example` and Config Server YAML:
`BOOKING_AVAILABILITY_CACHE_TTL=PT15M` and
`BOOKING_AVAILABILITY_CACHE_KEY_PREFIX=flash-ticket:dev:booking:availability:v2:`.
Use an environment-specific prefix when sharing Redis. No connection settings or
secrets were added to the template. Zero/negative TTL bypasses Redis.

Verification used JDK 23.0.1 with Maven Enforcer enabled. The focused cache/MVC
selection passed **16 tests**. Full verification passed **395 unit/MVC tests plus
140 PostgreSQL integration tests: 535 total, zero failures/errors/skips**.
Whole-module Spotless passed, SpotBugs reported **0 findings**, JaCoCo generated
its report and Maven reported **BUILD SUCCESS**. The three new integration methods
exercise real commits/rollbacks and production writers; Redis is mocked there.
Writer coverage includes QUANTITY/STANDING/SEATED reserve, buyer cancellation,
expiry, AcceptPayment, issuance FAILED and event cancellation of HELD/COMMITTED
reservations; configuration replay/old versions do not invalidate again.

Commands, from this service directory:

```powershell
rtk proxy .\mvnw.cmd -B -ntp "-Dtest=AvailabilityCacheServiceTests,AvailabilityControllerTests" test
rtk proxy dotenv -f target/cache-verify/maven.env run -- cmd /d /c mvnw.cmd -B -ntp -Pintegration "-Dit.test=BookingPersistenceIT" verify
```

The full run loaded a saved fixture env-file containing RABBITMQ_HOST,
RABBITMQ_PORT, RABBITMQ_USERNAME and RABBITMQ_PASSWORD for a disposable local
broker. Docker loaded its corresponding rabbit.env with --env-file. These secret
files were removed after verification; recreate them for the next disposable
fixture before using the command. No temporary environment/JVM override supplied
runtime configuration. Historical Enforcer-skip instructions below do not apply
to this verification.

The production JAR also passed **27/27 runtime cases** against disposable real
PostgreSQL, RabbitMQ, Redis and Keycloak. The unchanged existing availability curl
harness supplied its 20 cases; seven additional inline assertions checked Redis
cache behavior without adding a new helper/suite to source control:

| Cases | Runtime evidence |
| --- | --- |
| A01–A20 | Public contract/errors, QUANTITY/STANDING/SEATED, reservation/payment/cancel, event gates, read-only GET and the production expiry worker |
| A21 | PING=PONG; cached JSON matches HTTP; repeated GET retains asOf; data PTTL is positive and at most 900000ms and does not increase on GET; generation PTTL=-1 |
| A22–A23 | Reserve/cancel changes only the affected generation; GET returns 8 then 10 available without waiting for TTL, while the previous JSON still exists |
| A24 | Rejected reservation returns 409 INSUFFICIENT_QUANTITY and leaves generation unchanged |
| A25–A26 | Real AcceptPayment/config/cancellation consumers invalidate warm cache; new source version is visible and CANCELLED returns 409 |
| A27 | A separate short-lived Redis test key expires; production TTL remains 15 minutes |

The cache-hit unit test verifies no DB-service call; runtime assertions use the
response/Redis values and do not measure SQL query counts or performance. The
isolated Redis fixture has no password; this is not a verification of Redis119
authentication. No shared119 service was touched. Owned containers/networks and
runtime secret env-files were removed after the run.

Evidence: `target/cache-verify/full-verify.log`,
`target/cache-verify/runtime-verify.log`, Surefire/Failsafe XML,
`target/spotbugsXml.xml`, and `target/availability-curl/results.json`.
Runtime report: 27 passed, 0 failed; JAR SHA256
`a65dd9746f071ff7742bbe031708ef901c88fbf4a99274c49a05562499579515`.

Redis read/clear/write and JSON failures have focused fallback tests. Invalidation
is an in-process callback, without durable outbox/retry: a crash after DB commit
or Redis failure during invalidation can leave stale cache until its TTL. This
run does not establish deployment, load capacity or Payment/Ticket Saga E2E.

## Booking message compatibility — 2026-11-02

Current production code still uses the configured Jackson message converter and
removes Java type headers before send. Direct JsonMapper serialization was discussed
but is not part of this change. Envelope body, messageId/correlationId, persistent
delivery, publisher confirms and outbox retry behavior are preserved.

Focused real RabbitMQ/PostgreSQL run passed both existing methods:
publishesPaymentDecisionWithConfirmAndPersistentEnvelope and
relaysOrderCompletedThroughRabbitMq: **2 tests, zero failures/errors/skips; exit 0**.
The latter also exercises rollback/redelivery and unroutable-message retry.
Command selection used -P integration with test-compile, failsafe:integration-test
and failsafe:verify; this was not the full integration suite.

Fetched dev/tuan at 32db391c086258268f57213b9d27132097b26d46. The Booking relay's
generated messages passed that Payment revision's actual ContractCodec and
MessageInvariantValidator for ACCEPTED/LATE/DUPLICATE/CANCELLED and OrderCompleted:
**5/5 passed**. Wire checks used mocked broker/repositories, so they establish
payload/header compatibility, not Payment Saga/DB or sandbox E2E completion.
The independent live-broker tests above establish Booking relay delivery.

Two reverse-direction mismatches remain in Payment source: issuance results use
booking.issuance-result.v1 instead of the two Booking queue types in B13; Saga
recovery uses Saga aggregateVersion while the AcceptPayment payload preserves
sourceOrderVersion. Reproduced rejection of both issuance states and recovery
aggregateVersion=3/sourceOrderVersion=1 using Booking's current handlers.
No Payment code or deployed environment was changed during this comparison.
Evidence: target/direct-envelope-baseline.log and
target/payment-compatibility-32db391/{source.json,wire-check.log,results.json}.

## Plain console logging — 2026-11-02

User requested minimal YAML and default text logs, then explicitly chose to remove
the JSON-only logging tests/helpers rather than rename and rewrite them.
Main, Config Server and test YAML now keep only logging.charset.console=UTF-8.
Removed BookingLogFields, SafeStackTracePrinter, RcaLoggingTests (three tests),
the JSON console test in BookingRabbitErrorHandlerTests and LogCapture's JSON
bootstrap method/nested probe. The event-capture helper and existing error handling,
error code and ACK/DLQ behavior tests remain. No new Java declaration or dependency.

Clean verify loaded the root .env through python-dotenv, using Maven 3.9.9 and
Java 23.0.1: **384 unit tests passed, zero failures/errors/skips; exit 0**.
Spotless and SpotBugs checks passed. Boot test contexts emitted default text logs.
This replaces the two JSON logging failures in the earlier header-fix run below.
No integration-profile execution or deployment is claimed.
Latest evidence: target/plain-logging-verify.log, target/surefire-reports and
target/spotbugsXml.xml. Clean verify replaces earlier ignored target artifacts.

## Outbound JSON type headers — 2026-11-02

User confirmed removing Java type headers after conversion and before sending.
Updated only the existing publishBookingMessage body and two existing relay tests;
no Java class/method, dependency, transaction or acknowledgement change.
Both booking.payment-decision.v1 and booking.order-completed.v1 now omit
__TypeId__, __ContentTypeId__ and __KeyTypeId__.

Direct relay checks with the real Jackson converter and a mocked broker observed
both messages fail the header check before the fix and pass afterwards. JSON body,
content type, messageId, correlationId and persistent delivery mode were preserved.
The broker mock intentionally times out confirmation; this check does not establish
broker delivery or Payment consumption. test-compile and spotless:check passed.

Full unit run loaded the project .env using python-dotenv: 388 tests, 386 passed,
one failure (BookingRabbitErrorHandlerTests.nativeFatalLoggerDoesNotDumpMessage)
and one error (RcaLoggingTests.actualJsonLogsHaveRcaContextAndExcludeSensitiveValues).
Full regression is not green. The two relay integration tests could not start
because Docker Engine was unavailable. No deployment or Payment E2E is claimed.
Historical evidence was generated under target/message-type-header-*.log and
target/check-message-type-headers.jsh before the later clean verify above.

## Event availability GET — 2026-11-01

Execution approval: user answered **"triển khai cho tôi"** immediately after the
EXECUTING-PLANS confirmation for C1–C9 / M1–M17 in the
[availability plan](../../docs/java-workflow/plans/2026-11-01-booking-event-availability-plan.md).
Added only three production declarations, one MVC test class (five tests and
its implicit constructor), and eight methods in BookingPersistenceIT.

Implemented public GET `/api/v1/events/{eventId}/availability` using the existing
security rule and Availability DTO. The service reads one PostgreSQL snapshot
with readOnly/REPEATABLE_READ, requires the entity gate PUBLISHED, reads current
resource IDs in batches, and returns QUANTITY counters, a shared STANDING pool,
and SEATED states/counts. Success responses use Cache-Control: no-store.
The existing configuration/cancellation writer method bodies are unchanged.

MVC selection: **5/5 passed**, exit0. Focused integration verify:
**388 unit +8 availability IT passed**, zero failures/errors/skips, exit0;
SpotBugs **0**. Cases cover shared/mixed inventory, zero/hidden/retired resources,
cross-event isolation, order-preserving output, unchanged persistent state,
expired HELD reservations, missing/unpublished/cancelled snapshots and sale
windows. The concurrent test blocks the inventory SELECT in the isolated DB:
the writer commits version2/capacity20 while the GET still returns version1/10;
the next GET returns version2/20. No production test hook or row lock was added.

**Full regression is NOT green.** Latest full verify ran unchanged code/tests
inside Maven3.9.9 / Temurin23.0.2+7 Linux: exit1, **388 unit passed;
137 IT, zero failures +1 error, zero skips**, SpotBugs **0**. All eight new
availability tests passed; both previously failing relay tests passed.
The lone error was releasesExpiredReservationWhenPaymentArrivesLate at line4235,
inside the existing preparePurchaseSnapshot flow: orders_check5 rejected
created_at=2026-11-01T15:05:14.201712Z with
payment_frozen_at=2026-11-01T15:05:13.736130Z (0.465582 seconds earlier).
Both fields originate from OrderRepository.currentDatabaseTime(), whose query
is select clock_timestamp(). The actor/cause of the time reversal is not established.
Linux execution did not eliminate it, so it cannot be attributed only to Windows.

Four earlier full Windows runs also failed existing tests. The last had
388 passing unit tests and137 IT with2 relay failures, zero errors/skips.
An earlier run had one relay failure plus two orders_check5 errors in
protectsNewSeatReservationFromStaleIssuanceFailure and
arbitratesExpiryAgainstAcceptPaymentAfterLockWait; frozen times preceded creation
by0.582/0.611 seconds. Another run timed out a four-second DB-expiry wait.
Relay and deadline tests passed alone, while the paired recheck reproduced the
deadline timeout. Independent PostgreSQL clock probes of600 and4,800 samples
observed no backwards step during their sampling windows; they do not invalidate
the failed-row evidence. No Java change, timeout increase or constraint relaxation
was used to make these checks pass. All13 new tests passed; P5/full acceptance
remains open pending resolution of the existing timing failures.

Linux unit totals include only22 reports from JVM23.0.2+7:388 tests. A stale
Surefire BookingPersistenceIT report with2 tests belongs to an earlier JVM run
and is excluded. Failsafe's137-test report is the integration result above.
The earlier runners supplied process variables for the disposable broker;
future runs must use a test env file loaded via env-file, as required by the
current convention. P5 instructions have been corrected accordingly.

The first focused attempt failed because the disposable Rabbit broker could not
start after Docker Desktop recovery; it was recreated with tmpfs and ping checked.
Restarting the Docker WSL distribution later reproduced the stale runtime socket
startup error. Runtime folders were retained as backups while restoring the engine;
no factory reset, existing data/volume deletion or constraint bypass was used.
Only local disposable fixtures were used; no shared119 DB/queue was changed.
Owned disposable containers were removed after verification; Docker remains running.
Logs and XML evidence are under ignored target/availability-* plus Maven reports;
availability-linux-full-verify.log is the latest full run, and
availability-windows-last-* preserves the preceding Windows result.
No deployment119 or Payment/Ticket service E2E acceptance is claimed here.

### Real HTTP curl acceptance — 2026-11-01

User requested real curl testing after implementation. Command from repository root:

```powershell
rtk proxy python -X utf8 services/booking-service/scripts/test-event-availability-curl.py
```

**20/20 cases passed, exit0**, from22:18:58 to22:21:19 +07. The final run made
**36 business HTTP requests using curl, including22 availability GETs**.
Status counts:200 x19,201 x8,400 x1,401 x1,409 x6,503 x1. Readiness checks,
Keycloak token requests and broker management requests are excluded from36.
Success responses assert no-store and the exact Availability fields; errors
assert the existing application/json Problem contract and matching correlation
header/body. JWTs travel through curl stdin config and are not recorded.

Ran the current production JAR with real PostgreSQL17, RabbitMQ4.3.6, Redis7.4.11
and Keycloak26.8.0, on an owned Docker network with only loopback HTTP ports.
JAR SHA256: `7426c690ae2e91bb27e26992a724fd280bf79df5b49bc6bab7fff982296e6b82`.
The three compiled availability classes were compared byte-for-byte with their
JAR entries before startup. Actual JWT validation, Spring HTTP handlers, service
transactions, repositories and message listeners were used; no Java declaration,
service mock or runtime test hook was added.

| Cases | Real requests and assertions |
|---|---|
| A01–A03 | Malformed UUID400; unknown snapshot503; supplied invalid JWT401 on public route |
| A04–A07 | Public quantity10; HTTP hold2 gives8; HTTP freeze + AcceptPayment consumer moves held2 to purchased2, leaving8; reserve remaining8 gives0 |
| A08 | Two standing types share a pool: capacity8-held3 gives5 for each type and the sector |
| A09–A10 | Mixed standing/seated; HELD/PURCHASED/AVAILABLE/hidden UNAVAILABLE; counts exclude held/purchased/hidden; real buyer cancellation increases seated availability1 to2 |
| A11–A14 | Cancellation consumer sets entity CANCELLED while JSON stays PUBLISHED; GET409; DRAFT/PENDING_APPROVAL/APPROVED also409 |
| A15–A16 | PUBLISHED before opening/after closing remains GET200; actual order POST remains409 |
| A17 | Version2 returns only current IDs in config order; retired DB row retained; other event IDs absent |
| A18 | Three GETs leave fingerprints of every Booking table unchanged, including versions/inbox/outbox/promotion usage |
| A19 | Actual30-second deadline passes with expiry disabled: GET keeps held inventory/voucher and leaves every table unchanged |
| A20 | Restart with expiry enabled; production worker changes order EXPIRED/reservation RELEASED; GET returns10 and voucher held0 |

Snapshots/inventory were created through event.sales-configured.v1 messages.
Orders, freeze, promotion and buyer cancellation used HTTP with real JWTs;
AcceptPayment/event cancellation used real listeners and committed inbox receipts.
SQL supplied canonical schema/roles/indexes and independent assertions, without
manually changing business inventory/order states. Payment/Ticket producer
services were not started.

Configuration is documented in [scripts/.env.example](scripts/.env.example).
The harness generates isolated PostgreSQL/Rabbit credentials and an internal-client
secret, persists pg.env/rabbit.env/keycloak.env/booking.env under ignored
target/availability-curl, and passes those files via Docker --env-file.
Runtime names include BOOKING_DB_*, RABBITMQ_*, REDIS_*, KEYCLOAK_*,
INTERNAL_SERVICE_CLIENT_SECRET, BOOKING_SERVICE_PORT/ENVIRONMENT and the Spring,
Eureka, hold-duration and expiry settings listed in the template. Root .env and
its unrelated .env.example edits are untouched. Production booking-service.yaml
is loaded directly; Config Server/Eureka are disabled, canonical DDL is used
with Hibernate DDL disabled. Other workers retain their production config.
Expiry alone is disabled while staging A01–A19, then enabled for A20; test hold
is30s instead of15m. These settings do not replace production handlers.

Evidence: ignored target/availability-curl/results.json contains20 cases and
all36 business responses/headers/statuses/timings, with no JWTs. App/dependency
logs are alongside it. Five owned containers, anonymous volumes, the network,
env files and realm import were removed; Docker remains running. Two harness
setup/assertion issues (empty204 parsing and an incorrect problem+json expectation)
were corrected before the final complete run, without production Java changes.

Curl acceptance is green; the full Maven regression limitation above remains open.
Successful curl freeze/payment cases do not establish that earlier intermittent
clock failures have been fixed.

### Local Windows clock remediation and focused retest — 2026-11-01

User authorized fixing the local Windows environment and rerunning the failing
releasesExpiredReservationWhenPaymentArrivesLate case. No production Java or test
body was changed. Before retesting, the failing full-run XML/summary was copied
to ignored target/windows-time-fix/before-fix-*; the full-regression limitation
above remains open because a full137-IT verify was not rerun.

Persisted two environment changes:

- Windows W32Time: Stopped/Manual to Running/Automatic (Delayed Start).
  Existing time.windows.com,0x9 peer and triggers preserved. Original registry,
  start mode and triggers saved under target/windows-time-fix. Initial immediate
  resync ran before usable peer data; a follow-up confirmed Leap Indicator0,
  Last Sync Error0 and Active NTP peer. External offset measurements changed
  from about1.29s before repair to a few milliseconds after repair.
- Ubuntu24.04.4: systemd-timesyncd active/enabled to inactive/disabled,
  following Canonical's advice when retaining implicit Hyper-V time sync.
  Normal Ubuntu CLI calls failed/timed out; verified the Ubuntu root filesystem
  and used its existing mount/PID namespace from the WSL system distribution
  to invoke systemctl. No distro reset/restart, Docker update/backend change,
  root .wslconfig change or shared119 operation was performed.

**Focused result:6/6 process runs passed**, zero failures/errors/skips:
three Windows JDK23.0.1 runs plus one Linux Temurin23.0.2 run after the Windows
change; one additional Windows and one Linux run after the Ubuntu change.
Each executes the original test's QUANTITY/STANDING/SEATED paths against real
PostgreSQL17/Rabbit Testcontainers. Ran compiled tests with Maven3.9.9:

```text
mvn -q -P integration -Dit.test=BookingPersistenceIT#releasesExpiredReservationWhenPaymentArrivesLate failsafe:integration-test failsafe:verify
```

Linux Maven used a persisted test.env via Docker --env-file, containing only
TESTCONTAINERS_HOST_OVERRIDE=host.docker.internal; matching .env.example is in
target/windows-time-fix. Native runs used the test's existing disposable
dependency configuration. No shared runtime env/secret was loaded or overridden.

**Clock stepping is NOT fully resolved.** A300s probe after the Windows change
recorded55,982 Windows samples with0 backwards values, but58,170 Linux samples
with4 backwards values (minimum delta about-7.674ms). A detailed120s Linux
probe, with no PostgreSQL running, reproduced4 steps at roughly30s intervals.
An isolated95s kernel ftrace instance recorded successful sys_settimeofday calls
from comm=init at29.983s/30.001s intervals, plus chronyd and systemd-timesyncd
clock_adjtime calls. The exact executable responsible for init was not identified;
do not conflate it with a proven attribution to Docker's initd from other reports.

After disabling Ubuntu timesyncd, a180s/161,933-sample Linux probe still observed
6 backwards values (minimum about-17.738ms). Per-step realtime-minus-monotonic
corrections decreased from about-18.85ms to-1.79ms during this window, but remained
present. Focused tests passing does not prove clock steps are eliminated, identify
the actor behind the original0.465582s inversion, or fix the earlier full suite.
The independent clock reproduction locates an actual problem below PostgreSQL.

Evidence: target/windows-time-fix/summary.json, late-payment-*.xml/log,
after-ubuntu/*, windows-confirm.log, clock-syscalls.log and *clock*.json (ignored).
All owned test/probe containers were removed automatically; the temporary trace
instance was removed and the task-mounted tracefs unmounted. No clock sampler,
background admin helper or kernel tracing was left running. Configuration backups
remain for rollback; normal Docker engine remains running.

References: [Microsoft W32Time](https://learn.microsoft.com/en-us/windows-server/networking/windows-time-service/windows-time-service-tools-and-settings),
[delayed-start option](https://learn.microsoft.com/en-us/troubleshoot/windows-client/active-directory/w32time-not-start-on-workgroup),
[Ubuntu WSL time sync](https://ubuntu.com/wsl/docs/stable/explanation/time-sync/).

## Event cancellation — 2026-10-31

Execution approval: user answered **"đồng ý"** immediately after the execution
list for spec v0.4 / plan v0.2. Added only C1/C8/C12/C16, the seven production
method declarations M1–M3/M8–M11 and 22 test methods from that list. Constructor
injection, field @Value and existing Booking listener/worker patterns were used.
The five HELD helpers and releaseResourcesAfterIssuanceFailure are unchanged.

Implemented gate/inbox transaction then AUTO ACK, a bounded polling worker,
one release transaction/order and the active-reservation query. CANCELLED orders
with COMMITTED reservations remain eligible after a late issuance completion.
Added the standalone concurrent partial-index SQL. During finishing-task on
2026-10-31, applied it on119 as booking_owner outside a transaction, before push;
verified the exact table/column/predicate and indisvalid=true, indisready=true.
The application role booking_admin has no schema DDL privilege; no grants or
business rows were changed. Deployment results are tracked by exact-SHA CI/run
and the task's ClickUp handoff comment, separately from local test evidence.

Full Booking `-P integration -Dit.test=BookingPersistenceIT verify`: **383 unit +
129 integration tests passed**, zero failures/errors/skips, exit0; IT report
completed **21:38:39 +07 on2026-10-31**. Maven enforcer/format/static checks passed,
SpotBugs reported **0 bugs**, JaCoCo report generated. Focused unit tests:
**8 passed**. Initial integration selection: **11/14 passed**, with three test
fixture timeouts. Two lock probes incorrectly depended on other sessions' query
text, hidden from booking_owner; changed them to the existing pg_blocking_pids
pattern. The ACK fixture incorrectly expected a DB failure to reach DLQ by a
delivery limit; it now waits for the in-flight DB write, stops the consumer,
observes requeue, removes the fault and verifies redelivery/commit/ACK. The first
recheck exposed asynchronous queue-count visibility; Awaitility now observes
the settled ready count. The two race tests and final ACK recheck passed.
No production code was changed to accommodate these fixture failures.

RabbitMQ4.3 distinguishes basic.nack requeue from failed-delivery counting; the
test does not claim a delivery-limit bounds DB-error retries. Permanent invalid
messages explicitly reject without requeue to DLQ. Production keeps the existing
broker-redelivery policy, with no application retry/backoff added. Reference:
[RabbitMQ quorum delivery count](https://www.rabbitmq.com/docs/quorum-queues#when-is-delivery-count-incremented).

Config Server: **1 test passed** with the installed JetBrains JDK21.0.5. The first
attempt used Booking's JDK23 and was correctly rejected by the module's enforcer.
Booking's first full-verify attempt also exposed a missing local Rabbit endpoint
for the existing contextLoads connection test. The subsequent verify uses a
disposable localhost RabbitMQ, real PostgreSQL17 and the existing IT Rabbit4.3.6
Testcontainers, without shared server119 fixtures or credentials.

### Partial-index query measurements

Used the **actual SQL emitted by Hibernate for M9**, including its static state
literals. PostgreSQL17, isolated 512MiB container; 100,000 released historical
orders across 1,000 cancelled events, 20,000 HELD orders on a published event,
20 eligible HELD orders on a newly cancelled event. Kept unique order_id and
baseline indexes, including the entity's expiry index. ANALYZE before measuring;
three EXPLAIN (ANALYZE, BUFFERS, FORMAT JSON) samples/case, median below.

| Case | Before index | After index | Returned rows |
|---|---:|---:|---:|
| First page | 213.465ms | 112.265ms | 20 |
| Cursor page | 241.910ms | 63.202ms | 10 |
| No candidates | 292.012ms | 145.652ms | 0 |
| Forced generic prepared plan, first page | 332.309ms | 181.293ms | 20 |

All four post-index plans used tbl_reservations_active_order_idx. Definition,
indisvalid and indisready were checked; the full unique constraint remains.
The index excludes RELEASED work but joins still inspect other active orders;
LIMIT20 is not evidence that only20 rows are read. No additional order/event
index was added or planner scan method disabled.

SQL write-cost samples, three rolled-back batches per phase: inserting1,000
orders/reservations measured502.862ms before and247.090ms after; updating20
reservations to RELEASED measured3.706ms before and5.637ms after. These samples
include cache/order effects, are not isolated index-overhead estimates or service
throughput measurements, and do not establish a production latency/SLO. Release
correctness is separately tested through real production service proxies.

Ignored local evidence: `target/event-cancellation-focused.log`,
`target/event-cancellation-recheck.log`, `target/event-cancellation-ack-recheck.log`,
`target/event-cancellation-full-verify.log`, `target/event-index-hibernate-query.sql`,
`target/event-index-benchmark-seed.sql`, `target/event-index-benchmark-results.json`,
`target/event-index-write-overhead.json`. Benchmark data is synthetic query-only
data, not a Payment/Ticket Saga or refund/void/check-in E2E test. Production
deployment must apply/verify the index SQL outside a transaction before enabling
the worker; health UP alone does not prove the partial index exists.
All task-owned benchmark and suite Rabbit containers were removed; IT containers
were managed by Testcontainers. No deploy, shared queue change or ClickUp write
was performed. The two old persistence XML cases in surefire-reports are stale
artifacts and are excluded from the383 unit total; the current129 IT results are
from failsafe-reports.

### Ten user cases through real HTTP, RabbitMQ and the scheduler

User requested approximately10 diverse user tests after implementation. **10/10
passed**, using the production JAR produced by verify, real curl POST requests,
Keycloak26.8.0 buyer/internal JWTs, RabbitMQ4.3.6 quorum queues imported from the
checked-in topology, PostgreSQL17 and the production @Scheduled worker. The
outbox relay was active, with PaymentDecision/OrderCompleted observed on Rabbit.
Every case checked independent PostgreSQL state/counters or rejection effects.

| Case | Trigger and observed result |
|---|---|
| E1 | Pending QUANTITY: event message commits gate, worker releases held inventory/limit; subsequent create returns409 EVENT_NOT_ON_SALE |
| E2 | Pending STANDING + HELD voucher: shared sector pool, buyer limit and voucher released |
| E3 | Frozen SEATED + voucher: seat holder cleared and usage released; money/frozen snapshot/deadline preserved |
| E4 | Accepted ISSUING + USED voucher: COMMITTED/purchased/limit/used released, accepted charge metadata retained |
| E5 | COMPLETED SEATED: OrderCompleted delivered before cancellation; worker later cancels/releases purchased seat |
| E6 | Same message replay and new messageId/same cancellation have no resource or gate-version effects; changed payload/same messageId goes to DLQ |
| E7 | Charge after gate: real PaymentDecision.CANCELLED delivered, no new accepted charge or double release |
| E8 | Gate ACK then late COMPLETED leaves CANCELLED+COMMITTED; app restart with worker enabled finds DB work and releases without republishing cancellation; no OrderCompleted after gate |
| E9 | Deferred commit fault on one of3 orders: that order rolls back/remains HELD, the other2 finish; removing fault lets scheduler retry, without changing already completed orders |
| E10 | Cancellation before config creates terminal gate without inventory; stale config ACKed, newer PUBLISHED config DLQed, create409 and no event resurrection |

Worker timing/batch matched production defaults PT5S/20. E8 deliberately
disabled only the worker while staging gate ACK and the late issuance result,
then restarted the same JAR with it enabled. No mock service, manual invocation
of a Java worker method, test-only Java endpoint or direct lifecycle SQL setup
was used. E9 injected a PostgreSQL deferred trigger failure to test rollback.
Other setup overrides: expiry disabled to isolate cancellation, hold duration
PT15M, Redis health/Eureka disabled, checked-in schema plus partial index loaded
directly and central Booking YAML supplied locally instead of Config Server.

The first harness bootstrap hit Windows Java stderr encoding while locating the
JDK, before any case ran; fixed decoding in the Python harness and reran. No
production code or Java declarations were changed for these user tests.

Ignored evidence: `target/event_cancel_user_runtime.py` (reuses the existing
`target/cancel_curl_runtime.py` fixture utilities), `target/event-user-run.log`,
`target/event-user-app.log`, `target/event-user-results.json`. JSON records the
production JAR SHA256 and sanitized per-case results, with no JWT/credential
values. The native Java process and3 labelled disposable containers/volumes were
removed; the realm credential file was deleted and all five local test ports
were verified closed. Shared119, production queues and ClickUp were untouched.
Event/Payment messages were fixture producers using the real contracts; actual
Payment refund and Ticket void/check-in services were not run, so these results
do not establish distributed Saga/refund/void E2E completion.

## Buyer cancellation — 2026-10-30

Execution approval: user confirmed "xác nhận" after the C1–C6, M1–M3,
R1–R6, U1–U3 and D1–D8 execution list. Added only the three production
cancelOrder declarations and eleven test methods in existing classes. The
five inventory/limit/promotion helpers, Payment handling and expiry logic are
unchanged. POST /api/v1/orders/{orderId}/cancel uses JWT ownership, the shared
lock order and DB time after resource locks; CANCELLED replay does not release
again. Frozen orders can still be cancelled while pending, HELD and unexpired.

Verification:

- Controller suite: **20 passed**, no failures/errors/skips.
- Initial eight new integration methods: six passed, one assertion failure and
  one fixture error. Corrected an AcceptPayment chargeId/attemptId argument
  mix-up and the expected existing PaymentDecision.CANCELLED after buyer cancel
  (LATE is the expiry branch). No production Payment logic changed.
- Full `-P integration -Dit.test=BookingPersistenceIT verify` completed at
  **14:21:37 +07, 2026-10-30**: **375 unit + 115 integration tests**, zero
  failures/errors/skips, BUILD SUCCESS. Maven format/static/coverage checks
  passed. Log: `target/cancel-full-verify.log`.
- Afterwards strengthened the existing D8 body to include held promotion usage
  in deferred-commit rollback/retry assertions. Focused `test-compile
  failsafe:integration-test failsafe:verify` for that method completed at
  **14:25:51 +07**: **1 passed**, no failures/errors/skips. This was a focused
  test rerun, not another full suite. Production code did not change.
  Log: `target/cancel-rollback-promotion.log`.

The new integration methods cover all inventory modes with/without voucher and
freeze, ownership, terminal states, equality and waiting past deadline,
concurrent cancels, competing Payment/expiry/voucher/snapshot calls, seat R1/R2
replay, lock timeout, resource failure and a deferred PostgreSQL commit failure.
Service calls use real transaction proxies and PostgreSQL locks. Only deadline
equality/replay-clock checks stub the existing repository spy's DB clock.
Promotion state/counters are explicitly compared before/after failed commit.

### Ten curl cases against a real HTTP server

**10/10 passed**, including independent PostgreSQL assertions after every
request. Ran the newly built production Booking JAR, PostgreSQL17,
RabbitMQ4.3.6 and Keycloak26.8.0 in disposable localhost isolation. JWTs came
from the actual Keycloak password/client-credentials grants; security filters,
ownership queries, service transactions and production listeners were active.
Sales configuration and AcceptPayment fixtures traversed RabbitMQ and the
production listeners. No mock controller/service or shared DB119 fixture was
used for these curl results.

| Case | Request | HTTP / result |
|---|---|---|
| H1 | Cancel QUANTITY | 200 CANCELLED; inventory/limit released |
| H2 | Cancel STANDING | 200 CANCELLED; sector pool released |
| H3 | Cancel SEATED | 200 CANCELLED; seat AVAILABLE, holder cleared |
| H4 | Cancel frozen order with held voucher | 200 CANCELLED; voucher released, money/frozen snapshot unchanged |
| H5 | Repeat cancellation | 200 CANCELLED; DB state and updatedAt unchanged |
| H6 | Another buyer's order | 404 ORDER_NOT_FOUND; no mutation |
| H7 | Missing JWT | 401 UNAUTHENTICATED; no mutation |
| H8 | JWT without BUYER role | 403 ACCESS_DENIED; no mutation |
| H9 | Expired pending order | 409 ORDER_EXPIRED; no mutation |
| H10 | Accepted ISSUING order | 409 ORDER_NOT_CANCELLABLE; purchased/COMMITTED preserved |

Runtime test overrides: hold duration10s to reach H9 with the real DB clock;
expiry/outbox workers disabled to isolate HTTP assertions, Redis health disabled,
checked-in PostgreSQL schema initialized as in BookingPersistenceIT. Loaded the
existing central Booking YAML directly, with local endpoints, instead of using
Config Server. An initial harness boot used the wrong import mechanism and
failed before any test; fixing the harness config location required no app code
change. The successful curl run exercised the same handler/service code but is
not a Config Server deployment, scheduler or Payment/Ticket Saga E2E test.

The runtime script and sanitized JSON results are local ignored artifacts:
`target/cancel_curl_runtime.py`, `target/cancel-curl-results.json`;
app log: `target/cancel-curl-app.log`. Tokens were passed to curl on stdin and
not recorded. The disposable realm credential file was removed. Task containers
were removed; the Windows Java launcher left a child process, which was verified
as the task server on18082 and stopped. The harness now selects the JDK's real
java.exe rather than the launcher for future runs. Server119 was not deployed
or seeded by this test.

The existing local Postman collection now has `Booking - Cancel Pending Order`
in its root Booking folder, with userAccessToken, orderId and body {}. The
collection is already ignored locally; it was not force-added to Git.

## Order expiry worker — 2026-10-30

Approved additions: OrderExpiryWorker/OrderExpiryWorkerTests, five production
constructor/method declarations (M1–M5), U1–U8 and D1–D12. Existing Payment methods
were changed only for shared promotion handling; allocation checks remain in
settlePendingPayment. No additional Java helpers/types were introduced.

Coverage includes DB-time/cursor selection, inclusive cutoff, bounded batch,
failure/restart/interruption handling, enabled/missing/disabled config and a real
two-thread Boot scheduler. PostgreSQL tests cover unfrozen orders with/without
held promotion, frozen promotion release after promotion expiry, all inventory
modes, terminal skips, repeated/concurrent expiry, Payment winners in both lock
orders, Payment waiting past expiry, exact equality, promotion/snapshot races,
resource conflicts and a deferred DB-trigger failure at commit followed by retry.
Only equality uses a spy DB clock; other races use actual PostgreSQL locks and
latches. The test trigger/function is dropped in finally and never touches DB119.

The checked-in SQL schema forbids hidden HELD seats (hidden implies UNAVAILABLE).
The seated test therefore proves normal HELD→AVAILABLE, preserves a newer holder
on old expiry replay, and proves rollback when the current holder's seat has
already become hidden/UNAVAILABLE. It does not weaken that schema constraint to
manufacture a hidden HELD fixture. The existing helper's hidden mapping is retained.
The suite initializes schema from SQL; the index test executes DDL using the
entity's @Index name/columns and inspects pg_indexes on PostgreSQL test. This
validates the index DDL, not a deployment or automatic schema update on server119.

An additional isolated worker-path test creates ten valid orders and invokes the
production `OrderExpiryWorker.expireDueOrders()` body against PostgreSQL
Testcontainers. The worker uses the real repository query and calls the proxied
`OrderService`, so each expiry commits in its own transaction. The test checks
order/reservation/allocation states and released inventory/limits. It calls the
scheduled method directly once; scheduling itself is covered separately by
`RuntimeConfigurationTests`.

Verification history:

- Production compile passed. Targeted worker/runtime/AcceptPayment tests:
  **17 passed**, zero skips.
- Initial full verification lacked the root environment and stopped at the
  existing Rabbit context smoke (unresolved RABBITMQ_HOST). Re-run injected .env
  directly into the child process without printing credentials.
- First environment-configured verify: **372 unit tests passed; 104/106
  persistence tests passed**, two race observers timed out. A first observer
  adjustment still timed out; both races passed after using the live pg_locks
  waiting-lock view instead of matching/querying pg_stat_activity. Production
  locking and guards were unchanged. Focused repair: **2/2 passed**.
- Final `-P integration -Dit.test=BookingPersistenceIT verify` completed at
  **10:59:29 +07:00, 2026-10-30**: **372 unit tests + 106 integration tests**,
  zero failures/errors/skips, BUILD SUCCESS. Spotless/SpotBugs/JaCoCo lifecycle
  checks passed; final git diff --check passed. Current-run Maven totals are
  authoritative; do not sum stale XML files from previous focused runs.
- After adding the worker-path method, targeted verify completed at
  **11:30:29 +07:00, 2026-10-30**: **372 unit tests + 1 worker-path integration
  test**, zero failures/errors/skips, BUILD SUCCESS. This was a focused run, not
  a rerun of all 107 integration methods. `git diff --check` also passed.

Logs: target/expiry-verify.log (first environment-configured run),
target/expiry-repair.log, target/expiry-repair2.log (2/2 pass), and
target/expiry-final-verify.log and target/expiry-worker-isolated-target.log.
Unit tests include the pre-existing Rabbit119
connection-only context smoke; business integration uses disposable PostgreSQL17
and RabbitMQ4.3.6 Testcontainers, not shared DB119 or business queues.

Docker Desktop initially could not start because of an inaccessible stale runtime
analytics socket. Its stopped/crashed processes were restarted after moving the
runtime directory to C:/Users/sondimid/AppData/Local/Docker/run.expiry-stale-20261030
as a backup. No Docker data volume was deleted or reset; the engine then responded.
This environment repair is unrelated to application schema or business data.

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

## Buyer order list and organizer sales summary — 2026-11-09

Contract: [Booking API](../../docs/api-documentation-frontend.md).
`GET /api/v1/orders` uses offset paging with a two-step deferred join (ids page + count,
then `left join fetch items` by id). `GET /api/v1/organizer/events/{id}/sales-summary`
checks ownership on `event_sales_snapshots.organizer_subject`, filters `orders.created_at`
by Asia/Ho_Chi_Minh days and is cached in Redis for 30s (TTL only, no event invalidation).

Verification on 2026-11-09 (branch `dev/son`, JDK 23 compiling for Java 21, Enforcer
explicitly skipped because no JDK 21 is installed on this machine):

- `rtk proxy .\mvnw.cmd -B -ntp "-Denforcer.skip=true" test`: **407 passed**, 0 failures.
- `rtk proxy .\mvnw.cmd -B -ntp "-Denforcer.skip=true" -DskipTests verify`: Spotless clean,
  **0 SpotBugs findings**, BUILD SUCCESS.
- New `BookingPersistenceIT` methods on Testcontainers PostgreSQL 17:
  `listsBuyerOrdersNewestFirstWithTotals`, `filtersBuyerOrdersByStateAndOwner`,
  `summarizesEventSalesByStateWithinVietnamDateRange`,
  `defaultsSalesSummaryRangeFromSaleStartToToday`, `hidesSalesSummaryOfOtherOrganizer`:
  **5 passed**. Related existing methods `selectsExpiredPendingOrderIdsWithBoundedCursor`,
  `allEntityTableNamesResolveInTheCheckedInSchema`,
  `allTwelveRepositoriesReadTheirPersistedEntityAfterClearingTheContext`,
  `skipsUnexpiredAndTerminalOrdersDuringExpiry`, `createsQuantityOrderAndHoldsTicketTypeInventory`,
  `seatedOrderGroupsItemsAndHoldsIndividualSeats` passed.
- The full `BookingPersistenceIT` suite was **not** run (machine had ~1.2 GB free commit
  memory). IT runs needed tooling flags `-Dsurefire.useManifestOnlyJar=false` (failsafe's
  manifest JAR in C: temp could not reference D: classpath, so Mockito could not self-attach)
  and `-DargLine="-Xms64m -Xmx512m"` (paging file too small). No application config was
  overridden.
- Observed pre-existing order dependence: `selectsExpiredPendingOrderIdsWithBoundedCursor`
  fails when run after `createsQuantityOrderAndHoldsTicketTypeInventory` /
  `seatedOrderGroupsItemsAndHoldsIndividualSeats`, because those tests do not truncate and
  `findExpiredOrderIds` is not scoped by event. It passes alone; unrelated to this change.
- API Gateway `rtk proxy .\mvnw.cmd -B -ntp "-Denforcer.skip=true" test`: **105 passed**,
  including the new booking route rows for `GET /api/v1/orders` and sales summary.
- No runtime check against deployed Booking/Redis/Keycloak was done.

### Consistent read snapshots — review follow-up, 2026-11-09

`OrderServiceImpl.getOrders` and `getSalesSummary` now use read-only
`REPEATABLE_READ`, following `AvailabilityServiceImpl.getAvailability`.
Extended the existing `filtersBuyerOrdersByStateAndOwner` and
`summarizesEventSalesByStateWithinVietnamDateRange` tests; no new Java methods.
The repository spy only schedules a real `REQUIRES_NEW` cancellation transaction
between the reader's queries, then delegates the original query to Spring Data.
Assertions check the API result, the committed cancellation, and a fresh read.

Before the annotation change, both cases failed on PostgreSQL 17: a PENDING filter
returned CANCELLED, and the summary counted one pending order with money but zero
tickets. After the change, both retained a consistent snapshot while the writer
committed successfully; the following read saw the cancellation.

Verification command from this service directory:

```powershell
rtk proxy .\mvnw.cmd -B -ntp "-Denforcer.skip=true" "-Dsurefire.useManifestOnlyJar=false" "-DargLine=-Xms64m -Xmx512m" "-Dit.test=BookingPersistenceIT#listsBuyerOrdersNewestFirstWithTotals+filtersBuyerOrdersByStateAndOwner+summarizesEventSalesByStateWithinVietnamDateRange+defaultsSalesSummaryRangeFromSaleStartToToday+hidesSalesSummaryOfOtherOrganizer" -Pintegration verify
```

Result: **407 unit/WebMvc tests and 5 selected PostgreSQL IT passed**, zero
failures/errors/skips; Spotless passed and SpotBugs check reported 0 findings.
Logs: `target/read-snapshot-red.log`, `target/read-snapshot-verify.log`;
pre-fix report preserved at `target/read-snapshot-red.xml`.
JDK 23 compiled for Java 21 with Enforcer explicitly skipped. Full IT and checks
against deployed services were not run. Docker Desktop was started for these
disposable Testcontainers fixtures.

## Shared sales statistics — verified 2026-11-10

Contract: [Shared sales statistics](../../docs/api-documentation-frontend.md#thống-kê-bán-vé-dùng-chung--2026-11-10).
API: [contract](../../docs/api-documentation-frontend.md#thống-kê-bán-vé-dùng-chung--2026-11-10).

- Booking: **418 unit/WebMvc tests passed**, including five cache tests and six new controller tests. Covers ADMIN vs owner vs other organizer, malformed binding, default/explicit params and owner lookup before event cache.
- **Five focused BookingPersistenceIT tests passed**, real PostgreSQL17/Rabbit4.3.6 Testcontainers: summarizesSystemAndOrganizerSalesStatistics, groupsSalesChartsByVietnamCalendar, ranksEventSalesStatisticsWithZeroRows, resolvesEventSalesOwnerForAdminAndOrganizer, keepsSalesStatisticsConsistentAcrossConcurrentUpdates.
- Chart queries execute through Hibernate7.4.5, not mocked aggregate responses; tests cover Vietnam midnight, year crossing, daily/weekly/monthly grouping and zero periods. Ranking covers all three metrics in both directions, global/owner scope, zero/cancelled events, count and out-of-range pages.
- Concurrent-update test commits a real cancellation in REQUIRES_NEW between summary queries. Existing request sees matching order/ticket state totals, next request sees cancellation. This is a consistency test, not a load test.
- Summary large-amount/discount assertions added and rerun separately: **1/1 passed**. Each fixture order fits numeric(19,0), the aggregate exceeds19digits without using the per-order money converter. This does not reverify the voucher/payment workflow.
- First Booking verify ran418tests successfully but found one SpotBugs nullability warning in authority comparison. Fixed with literal-first equals; subsequent34controller tests, Spotless check and SpotBugs (**0 findings**) passed.
- Ranking tie/offset assertions added and rerun separately: **1/1 passed**; two zero-sales events retain eventId ASC for all metrics, including page=1,size=1. Evidence: target/sales-rank-it-verified.log.
- Gateway: **112 tests passed**, including seven new route cases. Separate verify passed Spotless and SpotBugs (0 findings).
- JDK23.0.1, release21; Enforcer skipped explicitly because this machine uses23. No datasource/Redis runtime overrides. Testcontainers supplies isolated dependencies under existing test configuration; business DB84 is not used for the IT.
- Server84 index I1 created CONCURRENTLY after checking duplicates, valid/ready verified. Application user lacked DDL permission; executed as postgres in container over verified ft84 SSH without altering grants. Actual57row EXPLAIN preferred seqscan, as expected for a tiny table. No business rows changed.
- Postman local ignored collection has seven GET requests in Booking; global uses adminAccessToken, organizer/event uses userAccessToken with ORGANIZER role, set organizerSubject to the JWT owner. Owner APIs also accept an ADMIN token.

Commands (from Booking, all tooling flags explicit):

```powershell
rtk proxy .\mvnw.cmd -B -ntp "-Denforcer.skip=true" "-Dsurefire.useManifestOnlyJar=false" "-DargLine=-Xms64m -Xmx512m" verify
rtk proxy .\mvnw.cmd -B -ntp "-Denforcer.skip=true" "-DskipTests" test-compile
rtk proxy .\mvnw.cmd -B -ntp "-Dsurefire.useManifestOnlyJar=false" "-DargLine=-Xms64m -Xmx512m" "-Dit.test=BookingPersistenceIT#summarizesSystemAndOrganizerSalesStatistics+groupsSalesChartsByVietnamCalendar+ranksEventSalesStatisticsWithZeroRows+resolvesEventSalesOwnerForAdminAndOrganizer+keepsSalesStatisticsConsistentAcrossConcurrentUpdates" failsafe:integration-test failsafe:verify
```

Do not pass skipTests to the failsafe invocation: skipped tests are not validation.
Logs are under Booking target/sales-*.log and Gateway target/sales-gateway-*.log.
Known limits: no live new-API deployment/HTTP load measurements, no stampede prevention or event invalidation, Redis TTL begins after load, default-date keys may cross midnight for the remaining TTL.

## Buyer order list cache — verified 2026-11-10

[API contract](../../docs/api-documentation-frontend.md#cache-danh-sách-đơn-buyer--2026-11-10).
GET /api/v1/orders caches OrderPage per authenticated buyer + state/page/size.
Payload TTL24h; generation key has no TTL. All changing order/price/freeze paths publish
BuyerOrdersChangedEvent inside their transaction. A synchronous AFTER_COMMIT listener
changes the buyer generation; rollback/replay/no-op does not invalidate.

Validation:

- **424 unit/WebMvc tests passed**,0fail/error/skip. Includes6new BuyerOrdersCacheServiceTests.
- **3 new BookingPersistenceIT tests passed**: invalidatesBuyerOrdersCacheAfterCommitButNotRollback,
  invalidatesBuyerOrdersCacheWhenOrderStateChanges,invalidatesBuyerOrdersCacheWhenOrderPriceOrFreezeChanges.
  Real PostgreSQL17/Rabbit4.3.6 Testcontainers and Spring transactions/listener; RedisService
  is real with a mocked StringRedisTemplate backed by test key maps.
- **4 existing IT regressions passed across runs**: listsBuyerOrdersNewestFirstWithTotals,
  filtersBuyerOrdersByStateAndOwner,salesConfigurationAndCancellationInvalidateAvailabilityCache,
  inventoryWritersInvalidateAvailabilityForAllSalesModes.
- Inventory regression initially failed because its blanket no-Redis assertion after freeze
  also rejected the newly required buyer invalidation. Narrowed the existing assertion to
  forbid writes to the exact availability generation key; buyer invalidation is independently
  verified by the new price/freeze test. Rerun passed1/1, without new Java declarations.
- **Spotless clean; SpotBugs0**. git diff --check has no whitespace errors.
- JDK23.0.1,release21,Enforcer explicitly skipped for lifecycle commands. No runtime DB/Redis
  configuration overrides; test dependencies use existing Testcontainers setup.

Commands run from Booking (tooling flags only):

```powershell
rtk proxy .\mvnw.cmd -B -ntp "-Denforcer.skip=true" "-Dsurefire.useManifestOnlyJar=false" "-DargLine=-Xms64m -Xmx512m" verify
rtk proxy .\mvnw.cmd -B -ntp "-Denforcer.skip=true" spotless:apply test-compile
rtk proxy .\mvnw.cmd -B -ntp "-Dsurefire.useManifestOnlyJar=false" "-DargLine=-Xms64m -Xmx512m" "-Dit.test=BookingPersistenceIT#invalidatesBuyerOrdersCacheAfterCommitButNotRollback+invalidatesBuyerOrdersCacheWhenOrderStateChanges+invalidatesBuyerOrdersCacheWhenOrderPriceOrFreezeChanges" failsafe:integration-test failsafe:verify
```

Evidence logs under target: buyer-orders-cache-unit.log, buyer-orders-cache-it.log,
buyer-orders-cache-verify.log,buyer-orders-cache-regression-it.log,
buyer-orders-cache-inventory-regression-verified.log.

Known limits: no durable invalidation or singleflight; a missed post-commit invalidation can
leave old payloads until TTL expiry, possibly close to24h. Old readers retain their original
generation when writing but may return their old snapshot to that request. No new metadata
fields/batch snapshot query were added to OrderView by this cache change. No live-API deploy,
business DB84 writes,commit or push.
