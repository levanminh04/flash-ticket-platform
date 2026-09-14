\set ON_ERROR_STOP on
-- B12-v0.1 CANDIDATE. Run once on a NEW disposable PostgreSQL 16 instance.
-- No passwords, data migration, DROP, or IF NOT EXISTS. Databases need autocommit.
DO $guard$
BEGIN
  IF current_database() <> 'postgres' OR NOT (SELECT rolsuper FROM pg_roles WHERE rolname = current_user) THEN
    RAISE EXCEPTION 'Bootstrap requires the postgres database and a bootstrap superuser';
  END IF;
  IF EXISTS (SELECT FROM pg_database WHERE datname IN ('event_db','booking_db','payment_db','ticket_db','keycloak_db'))
    OR EXISTS (SELECT FROM pg_roles WHERE rolname ~ '^(event|booking|payment|ticket)_(owner|migrator|app)$' OR rolname IN ('keycloak_app','rca_observer')) THEN
    RAISE EXCEPTION 'Target database/role already exists; fresh-instance baseline refuses reuse';
  END IF;
END $guard$;

BEGIN;
DO $roles$
DECLARE service text;
BEGIN
  FOREACH service IN ARRAY ARRAY['event','booking','payment','ticket'] LOOP
    EXECUTE format('CREATE ROLE %I NOLOGIN NOSUPERUSER NOCREATEDB NOCREATEROLE NOREPLICATION NOBYPASSRLS', service || '_owner');
    EXECUTE format('CREATE ROLE %I LOGIN NOINHERIT NOSUPERUSER NOCREATEDB NOCREATEROLE NOREPLICATION NOBYPASSRLS', service || '_migrator');
    EXECUTE format('CREATE ROLE %I LOGIN NOINHERIT NOSUPERUSER NOCREATEDB NOCREATEROLE NOREPLICATION NOBYPASSRLS', service || '_app');
    EXECUTE format('GRANT %I TO %I WITH ADMIN FALSE, INHERIT FALSE, SET TRUE', service || '_owner', service || '_migrator');
    EXECUTE format('ALTER ROLE %I SET search_path = pg_catalog, %I', service || '_app', service || '_schema');
    EXECUTE format('ALTER ROLE %I SET search_path = pg_catalog, %I', service || '_migrator', service || '_schema');
    EXECUTE format('ALTER ROLE %I SET timezone = %L', service || '_app', 'UTC');
  END LOOP;
END $roles$;
CREATE ROLE keycloak_app LOGIN NOINHERIT NOSUPERUSER NOCREATEDB NOCREATEROLE NOREPLICATION NOBYPASSRLS;
CREATE ROLE rca_observer LOGIN NOINHERIT NOSUPERUSER NOCREATEDB NOCREATEROLE NOREPLICATION NOBYPASSRLS;
ALTER ROLE keycloak_app SET search_path = pg_catalog, keycloak;
REVOKE ALL ON DATABASE postgres FROM PUBLIC;
REVOKE ALL ON DATABASE template1 FROM PUBLIC;
COMMIT;

CREATE DATABASE event_db;
CREATE DATABASE booking_db;
CREATE DATABASE payment_db;
CREATE DATABASE ticket_db;
CREATE DATABASE keycloak_db;
REVOKE ALL ON DATABASE event_db, booking_db, payment_db, ticket_db, keycloak_db FROM PUBLIC;
GRANT CONNECT ON DATABASE event_db TO event_app, event_migrator;
GRANT CONNECT ON DATABASE booking_db TO booking_app, booking_migrator;
GRANT CONNECT ON DATABASE payment_db TO payment_app, payment_migrator;
GRANT CONNECT ON DATABASE ticket_db TO ticket_app, ticket_migrator;
GRANT CONNECT ON DATABASE keycloak_db TO keycloak_app;

\connect event_db
REVOKE ALL ON SCHEMA public FROM PUBLIC;
CREATE SCHEMA event_schema AUTHORIZATION event_owner;
\connect booking_db
REVOKE ALL ON SCHEMA public FROM PUBLIC;
CREATE SCHEMA booking_schema AUTHORIZATION booking_owner;
\connect payment_db
REVOKE ALL ON SCHEMA public FROM PUBLIC;
CREATE SCHEMA payment_schema AUTHORIZATION payment_owner;
\connect ticket_db
REVOKE ALL ON SCHEMA public FROM PUBLIC;
CREATE SCHEMA ticket_schema AUTHORIZATION ticket_owner;
\connect keycloak_db
REVOKE ALL ON SCHEMA public FROM PUBLIC;
CREATE SCHEMA keycloak AUTHORIZATION keycloak_app;
ALTER DEFAULT PRIVILEGES FOR ROLE keycloak_app IN SCHEMA keycloak REVOKE ALL ON TABLES FROM PUBLIC;
ALTER DEFAULT PRIVILEGES FOR ROLE keycloak_app REVOKE EXECUTE ON FUNCTIONS FROM PUBLIC;
-- Keycloak manages its own schema. Configure KC_DB_SCHEMA=keycloak separately.
