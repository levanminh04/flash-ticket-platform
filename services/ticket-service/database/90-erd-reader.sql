\set ON_ERROR_STOP on
\connect ticket_db
BEGIN;
GRANT CONNECT ON DATABASE ticket_db TO ticket_erd;
GRANT USAGE ON SCHEMA ticket_schema TO ticket_erd;
GRANT SELECT ON ALL TABLES IN SCHEMA ticket_schema TO ticket_erd;
ALTER DEFAULT PRIVILEGES FOR ROLE ticket_owner IN SCHEMA ticket_schema
  GRANT SELECT ON TABLES TO ticket_erd;
COMMIT;
