# booking database on shared PostgreSQL

`booking_db` runs in the single PostgreSQL instance defined by
[deploy/docker-compose.postgresql.yml](../../../deploy/docker-compose.postgresql.yml),
alongside the other business databases and the existing Keycloak database.
There is no PostgreSQL container or Compose file for this service alone.

## Connection

- Host: `127.0.0.1`
- Port: `5432`
- Database: `booking_db`
- Schema: `booking_schema`
- Read-only username: `booking_erd`
- Password: `BOOKING_DB_VIEWER_PASSWORD` in `deploy/.env.dev` at the repository root
- JDBC URL: `jdbc:postgresql://127.0.0.1:5432/booking_db`

Credentials are unchanged after consolidation. This account is restricted to its
own business database. `booking_admin` is a maintenance login with the password
`BOOKING_DB_ADMIN_PASSWORD`; it is not a cluster superuser and must explicitly
`SET ROLE booking_owner` for owner operations.

See [all connections and startup commands](../../LOCAL-DATABASES.md).
Fresh initialization uses the canonical B12 DDL plus the local `tbl_` naming
migration, preserving column types, nullability, keys and indexes.
Initialization runs only on an empty volume. Existing data live in the shared
`deploy_postgres-data` volume; changing SQL does not migrate that volume.

The saved local project is `database/booking-db-erd.vpp`, diagram
**Booking Database ERD - Docker**. Change its database connection port from 5433
to **5432**, then run Reverse Database to refresh the `tbl_` names.
