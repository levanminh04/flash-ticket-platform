\set ON_ERROR_STOP on
\connect payment_db
BEGIN;
GRANT CONNECT ON DATABASE payment_db TO payment_erd;
GRANT USAGE ON SCHEMA payment_schema TO payment_erd;
GRANT SELECT ON ALL TABLES IN SCHEMA payment_schema TO payment_erd;
ALTER DEFAULT PRIVILEGES FOR ROLE payment_owner IN SCHEMA payment_schema
  GRANT SELECT ON TABLES TO payment_erd;
COMMIT;
