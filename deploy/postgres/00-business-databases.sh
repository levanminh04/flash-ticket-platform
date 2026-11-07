#!/bin/sh
set -eu

# Fresh init: create four databases in the existing shared PostgreSQL service.
# Restore mode creates only roles/databases, for importing preserved pg_dump files.
mode="${1:-initialize}"
case "$mode" in
  initialize|--restore-target) ;;
  *) echo "Expected initialize or --restore-target" >&2; exit 1 ;;
esac

: "${EVENT_DB_ADMIN_PASSWORD:?Set EVENT_DB_ADMIN_PASSWORD}"
: "${EVENT_DB_VIEWER_PASSWORD:?Set EVENT_DB_VIEWER_PASSWORD}"
: "${BOOKING_DB_ADMIN_PASSWORD:?Set BOOKING_DB_ADMIN_PASSWORD}"
: "${BOOKING_DB_VIEWER_PASSWORD:?Set BOOKING_DB_VIEWER_PASSWORD}"
: "${PAYMENT_DB_ADMIN_PASSWORD:?Set PAYMENT_DB_ADMIN_PASSWORD}"
: "${PAYMENT_DB_VIEWER_PASSWORD:?Set PAYMENT_DB_VIEWER_PASSWORD}"
: "${TICKET_DB_ADMIN_PASSWORD:?Set TICKET_DB_ADMIN_PASSWORD}"
: "${TICKET_DB_VIEWER_PASSWORD:?Set TICKET_DB_VIEWER_PASSWORD}"

# Refuse existing targets before making any changes.
psql -X -v ON_ERROR_STOP=1 -U "$POSTGRES_USER" -d "$POSTGRES_DB" <<'SQL'
DO $preflight$
BEGIN
  IF EXISTS (SELECT FROM pg_database WHERE datname IN ('event_db','booking_db','payment_db','ticket_db'))
     OR EXISTS (SELECT FROM pg_roles WHERE rolname ~ '^(event|booking|payment|ticket)_(owner|admin|erd)$') THEN
    RAISE EXCEPTION 'Business databases or roles already exist; do not rerun initialization';
  END IF;
END $preflight$;
SQL

for service in event booking payment ticket; do
  case "$service" in
    event) ddl=10-event.sql; admin_password="$EVENT_DB_ADMIN_PASSWORD"; viewer_password="$EVENT_DB_VIEWER_PASSWORD" ;;
    booking) ddl=20-booking.sql; admin_password="$BOOKING_DB_ADMIN_PASSWORD"; viewer_password="$BOOKING_DB_VIEWER_PASSWORD" ;;
    payment) ddl=30-payment.sql; admin_password="$PAYMENT_DB_ADMIN_PASSWORD"; viewer_password="$PAYMENT_DB_VIEWER_PASSWORD" ;;
    ticket) ddl=40-ticket.sql; admin_password="$TICKET_DB_ADMIN_PASSWORD"; viewer_password="$TICKET_DB_VIEWER_PASSWORD" ;;
  esac
  psql -X -v ON_ERROR_STOP=1 -U "$POSTGRES_USER" -d "$POSTGRES_DB" \
    -v database="${service}_db" -v owner="${service}_owner" \
    -v admin="${service}_admin" -v viewer="${service}_erd" -v schema="${service}_schema" \
    -v admin_password="$admin_password" -v viewer_password="$viewer_password" <<'SQL'
CREATE ROLE :"owner" NOLOGIN NOSUPERUSER NOCREATEDB NOCREATEROLE;
CREATE ROLE :"admin" LOGIN NOINHERIT NOSUPERUSER NOCREATEDB NOCREATEROLE PASSWORD :'admin_password';
CREATE ROLE :"viewer" LOGIN NOSUPERUSER NOCREATEDB NOCREATEROLE PASSWORD :'viewer_password';
GRANT :"owner" TO :"admin";
CREATE DATABASE :"database" OWNER :"owner";
REVOKE ALL ON DATABASE :"database" FROM PUBLIC;
GRANT CONNECT ON DATABASE :"database" TO :"admin", :"viewer";
ALTER ROLE :"admin" SET search_path = :"schema", pg_catalog;
ALTER ROLE :"viewer" SET search_path = :"schema", pg_catalog;
ALTER ROLE :"viewer" SET timezone = 'Asia/Ho_Chi_Minh';
ALTER ROLE :"viewer" SET default_transaction_read_only = on;
SQL

  if [ "$mode" = initialize ]; then
    psql -X -v ON_ERROR_STOP=1 -U "$POSTGRES_USER" -d "${service}_db" \
      -v schema="${service}_schema" -v owner="${service}_owner" <<'SQL'
REVOKE ALL ON SCHEMA public FROM PUBLIC;
CREATE SCHEMA :"schema" AUTHORIZATION :"owner";
SQL
    psql -X -v ON_ERROR_STOP=1 -U "$POSTGRES_USER" -d "${service}_db" \
      -f "/opt/flash-ticket/postgres/$ddl" \
      -f "/opt/flash-ticket/readers/$service.sql" \
      -f /opt/flash-ticket/99-table-prefix.sql
  fi
done
