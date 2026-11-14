#!/usr/bin/env bash
# Revoke new connections AND terminate old sessions only for booking_db on TEST PostgreSQL.
set -euo pipefail
[[ ${FT_TEST_ENVIRONMENT:-} == true ]] || { echo "Test environment required" >&2; exit 64; }
seconds=${1:?seconds}
[[ $seconds =~ ^[0-9]+$ ]] && (( seconds > 0 && seconds <= 300 ))
dc=(docker compose --env-file "${FT_TEST_ENV_FILE:-deploy/rca-test/.env.example}" -p ft-bi -f deploy/rca-test/compose.yml)
cid=$("${dc[@]}" ps -q postgres)
[[ -n $cid && $(docker inspect -f '{{index .Config.Labels "ft.bi.test"}}' "$cid") == true ]]
psql() { "${dc[@]}" exec -T postgres psql -X -v ON_ERROR_STOP=1 -U postgres -d postgres "$@"; }
restore() { psql -c 'ALTER DATABASE booking_db WITH ALLOW_CONNECTIONS true;' || {
  echo "RESTORE_FAILED: operator must restore booking_db connections" >&2; return 1; }; }
trap restore EXIT INT TERM
psql -c 'ALTER DATABASE booking_db WITH ALLOW_CONNECTIONS false;'
psql -c "SELECT pg_terminate_backend(pid) FROM pg_stat_activity WHERE datname='booking_db';"
psql -d event_db -c 'SELECT 1;'
sleep "$seconds"
