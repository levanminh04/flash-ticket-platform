# Booking Service handoff

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
