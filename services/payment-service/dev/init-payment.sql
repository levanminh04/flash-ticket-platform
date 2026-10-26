\set ON_ERROR_STOP on
CREATE ROLE payment_owner NOLOGIN;
CREATE ROLE payment_app LOGIN PASSWORD 'simulator-local-only';
CREATE DATABASE payment_db;
\connect payment_db
CREATE SCHEMA payment_schema AUTHORIZATION payment_owner;
ALTER ROLE payment_app SET search_path = pg_catalog;
ALTER DEFAULT PRIVILEGES FOR ROLE payment_owner IN SCHEMA payment_schema
  GRANT SELECT, INSERT, UPDATE, DELETE ON TABLES TO payment_app;
GRANT USAGE ON SCHEMA payment_schema TO payment_app;
\ir /simulator/payment.sql
\ir /simulator/prefix.sql
