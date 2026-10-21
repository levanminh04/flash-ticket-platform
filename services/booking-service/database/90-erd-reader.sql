\set ON_ERROR_STOP on
\connect booking_db
BEGIN;
GRANT CONNECT ON DATABASE booking_db TO booking_erd;
GRANT USAGE ON SCHEMA booking_schema TO booking_erd;
GRANT SELECT ON ALL TABLES IN SCHEMA booking_schema TO booking_erd;
ALTER DEFAULT PRIVILEGES FOR ROLE booking_owner IN SCHEMA booking_schema
  GRANT SELECT ON TABLES TO booking_erd;
COMMIT;
