# ERD review fixes — 2026-10-19

Project: `booking-db-erd.vpp` in this directory. Open this project in Visual Paradigm; its project title is `do-an-tot-nghiep`.

The fixes address the findings in `tmp/db-review-20261019/review.md`. PostgreSQL and MongoDB catalogs already matched the authoritative DDL/validators, so no live schema or records were modified.

| Diagram | Tables / collections | Columns / fields | PK / document keys | Physical FK |
|---|---:|---:|---:|---:|
| Event | 12 | 109 | 12 | 11 |
| Booking | 15 | 149 | 15 | 25 |
| payment-duyet | 13 | 127 | 13 | 13 |
| Ticket | 7 | 82 | 7 | 5 |
| User | 4 | 32 top-level + 4 embedded | 4 `_id` keys | 0 |

Changes:

- Added complete column-to-column FK mappings for Event/Ticket, including composite keys; corrected identifying flags and cardinalities from source keys/nullability. Retained the real `checkin_attempts.replay_of -> checkin_attempts.id` self-FK.
- Corrected Booking's six money columns to native numeric(19,0), preserving the domain's meaning in metadata. Corrected 17 JSONB labels using PostgreSQL user types. The VP renderer requires both Show Column User Type and Show Extra Column Properties; CHECK/UNIQUE text rows remain absent.
- Enabled the nullable N indicator and used the equivalent PostgreSQL alias `timestamptz` to avoid truncated type labels. Expanded Event/Ticket entities and rerouted their connectors around the tables.
- Added the editable **User Service — MongoDB document model** diagram, registered in the project navigator. `roleGrant` remains an embedded object. Its note distinguishes OPTIONAL/absent from BSON null, documents status-dependent required fields and the application-managed projection reference; no SQL FK is invented for MongoDB or Keycloak.
- Synchronized **Booking Service ERD**, **Booking Domain ERD** and **Booking Schema ERD** to the canonical Booking models, including `tbl_` names, fields and 25 FK mappings. Unrelated UML/activity/sequence diagrams were retained.

Validation:

- Native VP open/save round trip and PNG exports; field/type/PK/nullable and exact composite FK mappings checked against the source schema; SQLite integrity check.
- Fresh live catalog comparison: all four SQL services and User Mongo validators/indexes match; no business-schema issues. Keycloak remains vendor-owned and was not renamed or modified.
- `node docs/architecture/data/test-static.cjs`, Mongo `test-static.cjs` and `test-preflight.cjs`: PASS. These are schema/static checks, not business E2E tests.

Evidence and backup:

- Original project backup: `tmp/db-fix-20261019/before-fix.vpp` (SHA-256 `0a0cc1c4acd55451bd06738699635430347b65ad19986a2bce0e03c8b909f713`).
- Verification: `rtk proxy python -X utf8 tmp/db-fix-20261019/verify.py services/booking-service/database/booking-db-erd.vpp`.
- Fresh read-only catalogs: `tmp/db-fix-20261019/live-after/`.
- Final PNG files: `erd-exports/` in this directory. Images exported by the installed VP evaluation edition retain its watermark.
