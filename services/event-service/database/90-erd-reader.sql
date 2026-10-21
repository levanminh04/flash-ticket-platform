\set ON_ERROR_STOP on
\connect event_db
BEGIN;
GRANT CONNECT ON DATABASE event_db TO event_erd;
GRANT USAGE ON SCHEMA event_schema TO event_erd;
GRANT SELECT ON ALL TABLES IN SCHEMA event_schema TO event_erd;
ALTER DEFAULT PRIVILEGES FOR ROLE event_owner IN SCHEMA event_schema
  GRANT SELECT ON TABLES TO event_erd;
COMMIT;
