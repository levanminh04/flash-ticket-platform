# Event read adapters — PROPOSED, pending source-owner agreement

This file specifies the Event-side expectations needed by steps 6–7. It is NOT
an approved replacement for B13, NOT evidence that Booking/Payment implement
these endpoints, and does not authorize edits to their schema or services.
`event.read-model.enabled` is false by default. Enable only after the source
owners agree to these exact responses or adapt this boundary to their agreed
contract. Event's local cancellation/state machine and B13 cancellation message
are implemented independently of these reads.

## Provider verification — 2026-10-30

GitHub branch heads were checked directly and matched the local tracking refs:

| Owner/source | Verified implementation | Still required by Event |
|---|---|---|
| Booking `dev/son` at `987e1f050c59464930314c915c423eb2190d06fe` | SalesConfigurationListener, payment/issuance listeners, order expiry | No sales-summary controller and no EventCancellation listener. Son must agree the sales count semantics/response and implement the source endpoint and cancellation projection. |
| Payment `dev/tuan` at `e47b1f65db555ee43783e1cda0922b23edfc8334` | Payment HTTP/query/provider flow; Event finance/cancellation DTOs exist | No financial-summary controller or finance/cancellation consumer. Its purchase-runtime design explicitly excludes these features. Tuan must supply historical collected-order classification and agreed financial totals, then cancellation/refund processing. |

These are verified **missing provider implementations**, not an Event URL mismatch
that can be fixed by selecting another endpoint. Keep the response/path proposals
below disabled. Do not substitute Booking availability for sold-ticket count,
infer collected orders from revenue, or query either owner's database.
The classification must also work before fee approval; missing fee data must not
silently become a fabricated zero-fee financial report.

Live Transaction RabbitMQ had one consumer on `booking.sales-configured.q` and
zero consumers on Booking/Ticket/Payment cancellation queues, Ticket access and
Payment finance queues. Queue/binding existence and broker ACK do not establish
consumer completion. See [verification](INTEGRATION-VERIFICATION.md) for the
observation time, isolated publisher tests and remaining environment limitations.

## Shared requirements

Internal HTTPS/HTTP endpoints accept an `internal-service` client-credentials
Bearer JWT with the configured issuer/audience/INTERNAL_SERVICE authorization.
They must not be exposed by the public Gateway. Event forwards correlation and
valid traceparent. Origin comes from Event configuration or Eureka service ID;
never from browser input. Redirects are not followed and non-2xx is unavailable.

All response fields below are required and non-null; unknown fields are rejected.
UUID is canonical; asOf is UTC ISO-8601 ending Z, not ahead of configured clock
skew. Money is a canonical unsigned integer string <=19 digits, VND. Counts are
canonical unsigned bigint strings including zero. The event and organizer in a
response must exactly match the locally authorized event. Sources must not return
an empty successful response or invented zeros when data is unavailable.

## Payment financial summary

Path already identified by B13: `GET /internal/v1/events/{id}/financial-summary`.
The following **response shape is proposed**, because B13 does not specify it:

```json
{
  "schemaVersion": 1,
  "eventId": "00000000-0000-4000-8000-000000000001",
  "organizerSubject": "issuer-subject",
  "asOf": "2026-10-29T12:00:00Z",
  "hasCollectedOrders": true,
  "currency": "VND",
  "validGross": "100000",
  "validRefunds": "20000",
  "feeAmount": "8000",
  "netAmount": "72000"
}
```

Payment owns the FR-11 classification of whether the event has collected orders;
Event does not infer it from current net revenue (a fully refunded event can
still have collected orders). Positive validGross cannot accompany false.
Amounts follow Payment's valid-gross/refund/fee/net definitions; require
`validRefunds <= validGross` and `netAmount = validGross - validRefunds - feeAmount`.
This is a read snapshot, not a guarantee that no later charge can arrive.

Organizer direct cancellation requires false; requesting admin cancellation
requires true. Failure, malformed data or stale classification gives503 and
leaves Event unchanged. Reads happen outside the business transaction; Event
then takes the parent lock and rechecks ownership, version, status and DB time.
The snapshot timestamp is carried into that transaction and checked again
against decision DB time after the lock wait, even if the blocking writer rolled back.
The B14 eventual cancellation/refund mechanism still handles races after a read.
Admin may cancel directly without this classification query.

## Booking sales summary

**Proposed path and response**: `GET /internal/v1/events/{id}/sales-summary`.

```json
{
  "schemaVersion": 1,
  "eventId": "00000000-0000-4000-8000-000000000001",
  "organizerSubject": "issuer-subject",
  "asOf": "2026-10-29T12:00:00Z",
  "purchasedTicketCount": "12"
}
```

Booking owns this count, not Event. It represents committed purchases, excludes
active holds; the precise cancellation/refund treatment must be agreed with
Booking before activation. Event does not recreate inventory or infer purchases
from published supply. No new business events are invented for dashboard reads.

## Organizer statistics and errors

Event `GET /api/v1/organizer/events/{id}/statistics` requires ORGANIZER + ownership.
It returns eventId, asOf, salesAsOf, financeAsOf, purchasedTicketCount, currency,
validGross, validRefunds, feeAmount and netAmount. `asOf` is the older of the two
source times; the component times remain visible. Old read data can be displayed
with those times; the stricter classification-age limit applies to cancellation.
Both sources must succeed; a failed component does not become zero.

Event503 codes: DEPENDENCY_CONTRACT_NOT_READY (disabled), INTERNAL_AUTH_UNAVAILABLE,
UPSTREAM_UNAVAILABLE (missing instance/non-2xx/timeout), UPSTREAM_INVALID_RESPONSE,
UPSTREAM_SCOPE_MISMATCH, UPSTREAM_DATA_STALE. Gateway/Event role and ownership
errors remain401/403/404. Upstream response bodies/tokens are not returned/logged.
Registry lookup failures are also normalized to UPSTREAM_UNAVAILABLE before any
counterparty HTTP call; registry exception messages/causes are not exposed.

## Event-only configuration

| Environment | Default |
|---|---|
| EVENT_READ_MODEL_ENABLED | false |
| EVENT_PAYMENT_BASE_URL | empty: Eureka payment-service |
| EVENT_BOOKING_BASE_URL | empty: Eureka booking-service |
| EVENT_READ_CONNECT_TIMEOUT | 2s |
| EVENT_READ_RESPONSE_TIMEOUT | 5s |
| EVENT_CANCELLATION_SUMMARY_MAX_AGE | 30s |
| EVENT_READ_CLOCK_SKEW | 5s |

Timeout/age/skew values are configurable operational starting points, not a
business freshness guarantee. Base URLs are internal origins without path,
query, credentials or fragment. OAuth uses the existing Event registration
`internal`; no new privileged Keycloak admin client is introduced.
