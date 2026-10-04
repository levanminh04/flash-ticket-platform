#!/usr/bin/env bash
# The official image runs/sources this only when PGDATA is empty.
set -euo pipefail
: "${KC_DB_PASSWORD:?required}"

psql --username "$POSTGRES_USER" --dbname "$POSTGRES_DB" --set ON_ERROR_STOP=1 <<'SQL'
\getenv keycloak_password KC_DB_PASSWORD
CREATE ROLE keycloak_app LOGIN NOSUPERUSER NOCREATEDB NOCREATEROLE
    NOREPLICATION PASSWORD :'keycloak_password';
CREATE DATABASE keycloak_db OWNER keycloak_app;
\connect keycloak_db
REVOKE ALL ON DATABASE keycloak_db FROM PUBLIC;
GRANT CONNECT, TEMPORARY ON DATABASE keycloak_db TO keycloak_app;
REVOKE CREATE ON SCHEMA public FROM PUBLIC;
CREATE SCHEMA keycloak AUTHORIZATION keycloak_app;
ALTER ROLE keycloak_app IN DATABASE keycloak_db SET search_path TO keycloak;
SQL
