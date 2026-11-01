# Local database connections

**One PostgreSQL instance, multiple databases.** The `postgres` service in
`deploy/docker-compose.postgresql.yml` runs all four business databases in the
same `deploy-postgres-1` container and `deploy_postgres-data` volume. It also
continues serving the existing Keycloak database. MongoDB remains a separate service.

All connections use host `127.0.0.1`.

| Service | Engine | Port | Database | Schema / authSource | Read-only username | Local password variable |
| --- | --- | --- | --- | --- | --- | --- |
| Booking | PostgreSQL 16.15 | 5432 | booking_db | booking_schema | booking_erd | `BOOKING_DB_VIEWER_PASSWORD` in root `.env` |
| Event | PostgreSQL 16.15 | 5432 | event_db | event_schema | event_erd | `EVENT_DB_VIEWER_PASSWORD` in root `.env` |
| Payment | PostgreSQL 16.15 | 5432 | payment_db | payment_schema | payment_erd | `PAYMENT_DB_VIEWER_PASSWORD` in root `.env` |
| Ticket | PostgreSQL 16.15 | 5432 | ticket_db | ticket_schema | ticket_erd | `TICKET_DB_VIEWER_PASSWORD` in root `.env` |
| User | MongoDB 8.0.28 | 27017 | user_db | user_db (authSource) | user_erd | `USER_DB_VIEWER_PASSWORD` in root `.env` |

Passwords remain unchanged after consolidation. Use `<SERVICE>_DB_VIEWER_PASSWORD`
from the root `.env`. PostgreSQL accounts can connect
only to their own business database; reader accounts have SELECT access.
PostgreSQL publishes one shared port, `5432`, in the deployment Compose file.

## Start or stop

From the repository root, start the shared PostgreSQL service:

```powershell
docker compose --env-file .env -f deploy/docker-compose.postgresql.yml -f deploy/docker-compose.keycloak.yml up -d --wait postgres
```

For a fresh checkout, copy `.env.example` to `.env` and fill in the application
and database passwords. This one file supplies both database Compose setup and
application runtime. The initializer creates
separate owner, maintenance and reader roles for each database, applies canonical
B12 DDL, grants read access and adds the `tbl_` table prefix.

MongoDB starts separately:

```powershell
docker compose --env-file .env -f services/user-service/compose.db.yaml up -d --wait user-db
```

Stop PostgreSQL with the shared Compose service; this also stops DB access for
all four business services and Keycloak:

```powershell
docker compose --env-file .env -f deploy/docker-compose.postgresql.yml -f deploy/docker-compose.keycloak.yml stop postgres
```

There are no per-service PostgreSQL Compose files. Data persist in the shared
volume. Initializers run only on an empty volume; later changes need migrations.
For an already populated server, the initializer refuses existing business
databases/roles rather than replacing them.

## Naming and verification

All local business tables and User collections use `tbl_<original_name>`.
The canonical B12 files retain their original names; local initialization applies
`services/99-table-prefix.sql` and User's `99-collection-prefix.js`.
Existing Visual Paradigm connections must use port **5432**; run Reverse Database
again to show the renamed tables.

| Service | Tables / collections | Columns | Nullable columns | Physical foreign keys |
| --- | ---: | ---: | ---: | ---: |
| Booking | 15 tables | 149 | 17 | 25 |
| Event | 12 tables | 109 | 23 | 11 |
| Payment | 13 tables | 127 | 28 | 13 |
| Ticket | 7 tables | 82 | 17 | 5 |
| User | 4 collections | — | — | — |

Consolidation preserved table definitions, row counts, foreign keys and reader
access. Event's delete guard was verified with rolled-back fixtures. User's
MongoDB collections retain their strict validators and indexes.

Custom-format database backups are kept under `deploy/postgres/backups/` (ignored
by Git). The old per-service PostgreSQL containers/networks were removed; their
named volumes remain as recovery copies. Never start an old volume as a second
active copy of the shared database.
