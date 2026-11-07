#!/usr/bin/env bash
# Static checks on source-controlled configuration.
set -euo pipefail
. "$(dirname "$0")/lib.sh"

# --- keycloak has its own env template with the variables the server compose used to inline
for v in KC_DB_URL KC_DB_USERNAME KC_DB_PASSWORD KC_HOSTNAME KC_HTTP_ENABLED KC_BOOTSTRAP_ADMIN_PASSWORD \
         FT_IDENTITY_RABBITMQ_HOST FT_IDENTITY_RABBITMQ_USERNAME FT_IDENTITY_RABBITMQ_PASSWORD; do
  grep -q "^$v=" "$root/services/keycloak-spi/.env.example" || fail "keycloak .env.example missing $v"
done
# --- Java 21 everywhere
for p in services/booking-service services/ticket-service; do
  grep -q '<java.version>21</java.version>' "$root/$p/pom.xml" || fail "$p: java.version must be 21"
  grep -q '<version>\[21,22)</version>' "$root/$p/pom.xml" || fail "$p: enforcer range must be [21,22)"
done

echo "test-config OK"
