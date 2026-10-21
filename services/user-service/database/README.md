# Local User MongoDB for inspection

MongoDB 8.0.28 uses the canonical B12 `docs/architecture/data/mongodb/schema.cjs`.
Initialization creates four empty collections with strict validators and indexes:
`tbl_users`, `tbl_organizer_applications`, `tbl_organizer_profiles`, `tbl_user_follows`.
The final initializer `99-collection-prefix.js` adds the requested local `tbl_`
prefix after the canonical collections are created. It can also be run once
against an existing local `user_db` as the local admin; it refuses name conflicts
and never overwrites a destination collection. Database name and credentials
stay the same.
No synthetic data or Keycloak data are inserted.
This local database does not configure the User application runtime.

From the repository root, copy `deploy/.env.example` to `deploy/.env.dev`,
set `USER_DB_ADMIN_PASSWORD` and `USER_DB_VIEWER_PASSWORD`, then run:

```powershell
docker compose --env-file deploy/.env.dev -f services/user-service/compose.db.yaml up -d --wait
```

## Connection (MongoDB Compass or another MongoDB client)

- Host: `127.0.0.1`
- Port: `27017` (or `USER_DB_PORT`)
- Database: `user_db`
- Username: `user_erd` (read only)
- Password: `USER_DB_VIEWER_PASSWORD` in the ignored `deploy/.env.dev`
- Authentication database / authSource: `user_db`
- URI template: `mongodb://user_erd:<password>@127.0.0.1:27017/user_db?authSource=user_db`

The local admin is `user_admin`, password `USER_DB_ADMIN_PASSWORD`, authSource
`admin`. Prefer the read-only account for inspection.
This is a document database; it is not a PostgreSQL database for SQL ERD reverse engineering.
Visual Paradigm has not been configured.

The official image starts MongoDB with authentication. Data persist in the named
Docker volume. The service-specific initializer consumes the canonical schema
module directly; the existing test-only bootstrap remains unchanged.
Initialization runs only on an empty volume. Do not delete a populated volume
or rerun initialization to apply schema changes.

To stop without removing data:

```powershell
docker compose --env-file deploy/.env.dev -f services/user-service/compose.db.yaml stop
```
