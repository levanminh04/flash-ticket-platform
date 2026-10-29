# Booking Service handoff

## Booking issuance results — 2026-10-29

- Implemented on dev/son after user approved EXECUTING-PLANS C1–C14,
  M1–M23 except removed M17, U1–U4 and D1–D13. See
  `docs/java-workflow/plans/2026-10-29-booking-issuance-result-plan.md` §11.
- BookingIssuanceResultListener has two AUTO/concurrency1 methods:
  booking.tickets-issued.q accepts ticket.issued.v1 / COMPLETED;
  booking.issuance-failed.q accepts booking.issuance-failed.v1 / FAILED.
  Both call OrderServiceImpl through the transaction proxy. The business sender
  remains Payment; the historical ticket.issued name does not authenticate a sender.
- COMPLETED validates accepted charge, frozen snapshot/version and ticket count,
  changes ISSUING to COMPLETED and writes inbox + OrderCompleted outbox together.
  It does not change committed inventory/promotion counters or recheck hold expiry.
- FAILED releases purchased inventory/buyer limit and USED promotion once,
  even when the event is closed/cancelled. Reservation/allocations become RELEASED.
  Cancelled orders stay cancelled; contradictory terminal results are rejected.
  createOrder still enforces event status/window. This is not a refund handler.
- No issuance columns were added to Order. issuanceId is received/passed back,
  not compared with a previous issuance. Inbox hash and order/resource state guard
  duplicate effects; Payment/Ticket retain ownership of issuance identity/fencing.
- BookingOutboxWorker replaces PaymentDecisionOutboxWorker; publishBookingMessage
  relays only PaymentDecision and OrderCompleted using the same pessimistic row TX,
  persistent delivery, correlated confirm/returns and pending retry metadata.
- Source topology already contains the two queues/DLQs/bindings/policies. No broker
  resources or service permissions were changed by this implementation. Before a
  deployment, coordinate both message types/body with the Payment branch and verify
  actual publisher permissions; no real Payment consumer/Saga E2E is established here.
- New verification results and limits are recorded at the top of TESTING.md.
  No commit/push/deployment is part of this implementation request.
- Follow-up live check requested by user: 10/10 scenarios passed against the actual
  local Booking JAR on8082, DB119 through existing5432 tunnel, and business queues
  on Rabbit119. Both issuance listeners and shared outbox worker were exercised;
  thirteen persistent outbound messages match published outbox rows. Application
  health is UP; four exercised queues each have one consumer and no ready/unacked.
  Synthetic DB fixtures/output/DLQ messages remain for inspection; the eleven old
  sales-configured DLQ messages are preserved. Evidence:
  `target/issuance-live119-20261029/results.json`. Payment/Ticket services themselves
  were not run, so their handling/refund/Saga E2E is still unverified.

## AcceptPayment and decision outbox — 2026-10-29

- Branch `codex/booking-accept-payment`: AcceptPaymentListener consumes
  `booking.accept-payment.q` with AUTO ACK. The service commits before listener return.
  No HTTP accept endpoint, manual ACK, new schema, dependency or Payment code.
- `OrderService.acceptPayment` validates frozen price/version, locks event snapshot
  READ then order WRITE, reads inbox, then locks buyer limit/inventory/promotion.
  Decision uses DB clock after waits. Same accepted charge replays ACCEPTED before
  deadline checks; another charge is DUPLICATE. LATE/CANCELLED release remaining HELD
  resources once. Accepted reservation/allocations become COMMITTED, inventory and
  purchase limit held become purchased, promotion HELD becomes USED, order is ISSUING.
  SEATED uses PURCHASED, retaining reservation ID/generation. Completion belongs to
  the subsequent Ticket → Payment → Booking flow.
- Inbox response and payment-decision outbox commit atomically with resources.
  Wrong input is rejected to DLQ; database/commit failures propagate for redelivery.
- PaymentDecisionOutboxWorker polls due decision rows, at most20 per batch. Each
  `MessagingService.publishPaymentDecision(messageId)` runs a separate transaction,
  pessimistically locks that outbox row, sends a persistent Envelope and waits for
  correlated ACK with no return before saving publishedAt. Only outbox is locked
  across network I/O. Failure leaves pending with retry metadata. Crash between
  broker ACK and DB commit can resend the same messageId: Payment needs deduplication.
- Config Server booking YAML enables worker, poll PT2S, confirm/retry PT5S,
  batch20, Rabbit connection timeout5s. Set booking.outbox.enabled=false to disable
  worker. Existing business exchange/mandatory/confirms/returns remain in force.
- Smoke test now intentionally connects to the real broker using RABBITMQ_* env,
  with listener/worker/topology auto-declarations disabled. The suite must receive
  those variables securely; Maven does not load root .env automatically.
- Verification and remote119 evidence are recorded in TESTING.md. A message in a
  Payment-bound queue or a broker confirm does not prove Payment handler processing.
  No Booking deployment, Payment integration, expiry scheduler or refund worker
  is delivered by this task. The previous local Booking process was stopped to
  release its JAR for Maven packaging; runtime restart is a separate action.

Design/plan: `docs/java-workflow/specs/2026-10-29-booking-accept-payment-design.md`
and `docs/java-workflow/plans/2026-10-29-booking-accept-payment-plan.md`.

## Apply promotion and prepare purchase snapshot — 2026-10-29

- `POST /api/v1/orders/{orderId}/promotion`, BUYER JWT, body `{"code":"SAVE"}`:
  validates and holds one promotion usage, updates order discount/total atomically,
  returns OrderView. Same normalized code replays without holding again before freeze.
- `POST /internal/v1/orders/{orderId}/purchase-snapshot`, INTERNAL_SERVICE JWT,
  body `{"buyerSubject":"..."}` from trusted Payment: validates the order and
  saves price freeze + PurchaseSnapshot in one transaction before responding.
  There is no separate freeze endpoint and no GET that writes a freeze.
- Both operations use the same order write lock after the shared Event snapshot
  lock; apply also locks promotion inventory. Deadline is checked with DB clock
  after waiting. The existing 5s timeout is local to each transaction.
- Once frozen, promotion changes are rejected. Retrying preparation on a payable
  order returns the stored snapshot without changing timestamp/version/amount.
  Expired/cancelled orders cannot open a new attempt using an old snapshot.
- OrderItem retains original ticket prices/names/seats. At first freeze, event
  metadata and PurchaseSnapshot.sourceConfigVersion come from the Event snapshot
  read then; Order.sourceConfigVersion remains its creation version. A version
  increase alone neither cancels nor reprices the order. Event commercial editing
  after submission is outside the agreed business flow and must be enforced by Event.
- Internal access reuses the shared INTERNAL_SERVICE boundary; it does not create
  a Payment-only credential. Payment must authenticate the buyer before forwarding
  buyerSubject. Gateway must keep internal routes private.
- No new schema/class/dependency, start-requested publisher, Payment integration,
  release worker, cancellation/refund handling or runtime deployment was added.
  Existing promotion active-usage unique indexes are exercised in isolated tests.

See [plan](../../docs/java-workflow/plans/2026-10-29-booking-order-promotion-purchase-snapshot-plan.md)
and [verification](TESTING.md) for the executed checks and limitations.

Current build/test uses **JDK 23**, compile release 23 and Enforcer `[23,24)`.
Run `mvnw.cmd -B -ntp verify` from this service directory without skipping Enforcer.
Earlier acceptance results below retain their original Java 21/23 caveats.

## Sales configuration implementation — 2026-10-27

`SalesConfigurationListener` now consumes `event.sales-configured.v1` from
`booking.sales-configured.q` with AUTO ACK and concurrency 1. It calls
`AvailabilityService.synchronizeSalesConfiguration`; the implementation commits
snapshot, quantity/sector/seat/promotion inventories and inbox together. Stale
versions record a receipt without replacing inventory; duplicate/hash conflicts
and permanent invalid messages are rejected, database failures propagate.

Snapshots and inbox now use `@Version Long rowVersion`, independent of source
config versions. Assigned-ID rows are created with null Java versions and saved
through repositories. Booking YAML uses `ddl-auto: update`; ColumnDefault 0 allows
Hibernate to add the new NOT NULL columns for legacy data. Bootstrap SQL also
includes the columns. The change is committed on dev/son; the application has not
been restarted against server 119 to apply these columns there.

See [plan and review of all 15 tables](../../docs/java-workflow/plans/2026-10-27-booking-sales-config-plan.md)
and [current verification](TESTING.md). The five other tables without row_version
were reviewed, not modified. Remaining historical scaffold notes below predate
this consumer and do not supersede the current verification.

## Runtime configuration

The service imports configuration from Config Server using `CONFIG_SERVER_URL` (default `http://localhost:8888`). Spring does not load `.env` files automatically; inject the required values into the Booking process through the IDE, container, or deployment environment.

Required runtime variables:

| Variable | Purpose |
|---|---|
| `BOOKING_DB_URL` | PostgreSQL JDBC URL for `booking_db` and `booking_schema` |
| `BOOKING_DB_PASSWORD` | Password for the application database principal |
| `RABBITMQ_HOST` | Broker hostname |
| `RABBITMQ_PASSWORD` | Broker password |
| `KEYCLOAK_ISSUER_URI` | JWT issuer URI |

Optional variables and defaults are defined in `infrastructure/config-server/src/main/resources/config/booking-service.yaml`. `BOOKING_DB_PASSWORD` is the application credential. `BOOKING_DB_ADMIN_PASSWORD` belongs only to database bootstrap and is not consumed by this service. The connection initializes `SET ROLE booking_owner`; retain the approved database role model when changing credentials.

The Rabbit configuration uses `business.ex`, correlated publisher confirms and
`dynamic: false`. The sales-configuration listener overrides the shared manual
setting with AUTO ACK. Topology remains owned by deployment definitions; no
topology declarations or outgoing outbox relay were added.

## Original scaffold baseline (historical)

- 15 JPA entities, four composite ID types, eight `@Version` fields and five UUID-generated IDs. The selected associations are `Order.items`/`OrderItem.order`, `Reservation.order`, `Reservation.allocations`/`ReservationAllocation.reservation`, `OrderItem.seatAssignments`/`OrderItemSeat.orderItem` with `@MapsId("orderItemId")`, `ReservationAllocation.orderItem`, and `PromotionUsage.reservation`/`PromotionUsage.promotion`. The three collections use `CascadeType.PERSIST`; no `Order.reservation` inverse exists.
- REST request classes use explicit Jackson property creators and final fields. REST response classes use Lombok `@Getter @AllArgsConstructor` and preserve nullable output properties. JSONB snapshots and message payloads remain typed records; `PurchaseSnapshot.Item` is independent of the REST `OrderItemView`.
- `OrderMapper` maps a loaded `Order.items` collection and reads the saved item names/prices. `OutboxMessageMapper` validates the complete envelope at each public conversion boundary; `MappingValueConverter` handles mapping and typed JSON conversion. `ContractRules` owns the finite message-type/payload map; the redundant `ContractType` wrapper was removed.
- No controller, repository, business service, consumer, relay, scheduler, topology bean, Redis cache, or security filter chain is implemented.

## Contract and verification limits

The DTO inventory follows B13 v0.2 where specified; candidate fields remain candidates and are not evidence that the owning services have approved them. `OPEN-SCF-01` remains open: align the exchange and routing-key contract with Payment/Ticket before adding relay or consumers. The current exchange is `business.ex` while B13 names `flash-ticket.v1`; the current Ticket routing key is `ticket.issued.v1` while B13 names `ticket.issuance-result.v1`; the current Booking failure routing key is `booking.issuance-failed.v1` while B13 names `booking.issuance-result.v1`.

| Acceptance criterion | Status |
|---|---|
| AC-01 entities, keys, versions and selected association metadata | Static source review and compile passed; Hibernate metadata and database catalog were not run. |
| AC-02 DTO field/type/nullability inventory | Static review passed against the listed B13 fields; runtime JSON behavior was not run. |
| AC-03 JSON boundary and validation rules | Constraints and Jackson configuration were reviewed statically; JSON/Validator runtime was not run. |
| AC-04 two MapStruct implementations and unmapped-target error | Clean compile passed and both generated mapper implementations were inspected. |
| AC-05 YAML environment configuration | YAML reviewed; no secret default, Redis setting, or topology declaration was added. |
| AC-06 Java 21 compile and Spotless | Compile and Spotless passed using JDK 23 with Enforcer skipped. Java 21 verification remains outstanding because JDK 21 is not installed on this machine. |
| AC-07 test source scope | Existing `src/test` files were preserved; no test source or profile was added or changed, and no tests were run. |
| AC-08 transport deviations | All three exchange/routing-key differences are documented above; no consumer or relay was added. |

The compile command was `rtk proxy .\mvnw.cmd clean '-Denforcer.skip=true' '-DskipTests' compile` and returned `BUILD SUCCESS`; Spotless check returned `BUILD SUCCESS`. The plan's `-DspotlessFiles=src/main/java/**/*.java` expression was rejected by Spotless as an invalid regular expression; production sources were formatted with `-DspotlessFiles=src/main/java/.*\.java` and the scoped check passed. No test source or profile was added or changed, and no tests were run. PostgreSQL catalog mapping, JSONB round trips, Bean Validation runtime behavior, REST request deserialization, association/lazy/cascade behavior, broker delivery and JWT authorization remain unverified at runtime.
