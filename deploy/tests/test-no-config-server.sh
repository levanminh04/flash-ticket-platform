#!/usr/bin/env bash
# --- config-server is gone
set -euo pipefail
. "$(dirname "$0")/lib.sh"

[[ ! -e "$root/infrastructure/config-server" ]] || fail "infrastructure/config-server must be deleted"
[[ ! -e "$root/deploy/docker-compose.config-server.yml" ]] || fail "config-server compose must be deleted"
if grep -n -i 'config-server\|CONFIG_SERVER' "$root/deploy/docker-compose.integration.local.yml" "$root/.env.example"; then
  fail "config-server references remain in local compose or root .env.example"
fi
echo "test-no-config-server OK"
