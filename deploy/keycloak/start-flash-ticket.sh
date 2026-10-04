#!/usr/bin/env bash
set -euo pipefail

# Keycloak 26.8 evaluates SmallRye expressions in KC_* configuration.
# Escape literal dollars at runtime; dotenv keeps the unescaped DB password.
export KC_DB_PASSWORD="${KC_DB_PASSWORD//\$/\\\$}"

exec /opt/keycloak/bin/kc.sh "$@"
