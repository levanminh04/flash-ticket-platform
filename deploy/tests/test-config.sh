#!/usr/bin/env bash
# Static checks on source-controlled configuration.
set -euo pipefail
. "$(dirname "$0")/lib.sh"

# --- keycloak has its own env template with the variables the server compose used to inline
for v in KC_DB_URL KC_DB_USERNAME KC_DB_PASSWORD KC_HOSTNAME KC_HTTP_ENABLED KC_BOOTSTRAP_ADMIN_PASSWORD \
         FT_IDENTITY_RABBITMQ_HOST FT_IDENTITY_RABBITMQ_USERNAME FT_IDENTITY_RABBITMQ_PASSWORD; do
  grep -q "^$v=" "$root/services/keycloak-spi/.env.example" || fail "keycloak .env.example missing $v"
done
echo "test-config OK"
