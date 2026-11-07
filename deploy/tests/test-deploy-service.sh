#!/usr/bin/env bash
# Runs deploy-service.sh against fake docker/curl/flock; no Docker daemon needed. Linux only (stat -c, GNU date).
set -euo pipefail
. "$(dirname "$0")/lib.sh"

tmp=$(mktemp -d); trap 'rm -rf "$tmp"' EXIT
export APP_ROOT="$tmp/app" FAKE_LOG="$tmp/calls.log" MEMINFO_FILE="$tmp/meminfo"
export POLL_SECONDS=0 HEALTH_TIMEOUT=2 REGISTRY_USER=tester
mkdir -p "$APP_ROOT"/{compose,env,state} "$tmp/bin"
export PATH="$tmp/bin:$PATH"
printf 'MemAvailable:   4000000 kB\n' > "$MEMINFO_FILE"

cat > "$tmp/bin/docker" <<'EOF'
#!/usr/bin/env bash
echo "docker $*" >> "$FAKE_LOG"
case "$1" in
  login) cat > /dev/null ;;
  image) [[ $2 == inspect ]] && echo "sha256:fakeimageid" ;;
  inspect)
    if [[ "$*" == *StartedAt* ]]; then echo "2026-01-01T00:00:00.5Z"
    else
      # FAKE_INSPECT_FIRST only applies until the first recreate has been followed by a second one (i.e. the rollback).
      n=$(grep -c ' up -d ' "$FAKE_LOG" || true)
      if (( n <= 1 )); then echo "${FAKE_INSPECT_FIRST:-false 0 true sha256:fakeimageid}"; else echo "false 0 true sha256:fakeimageid"; fi
    fi ;;
  compose)
    if [[ "$*" == *" config "* ]]; then echo "{\"services\":{\"$FAKE_SVC\":{\"mem_limit\":\"${FAKE_MEM_LIMIT:-805306368}\"}}}"
    elif [[ "$*" == *" ps "* ]]; then echo fakecid; fi ;;
esac
exit 0
EOF
cat > "$tmp/bin/curl" <<'EOF'
#!/usr/bin/env bash
# FAKE_HEALTH: ok | never | fail_first (unhealthy until the rollback recreate happens)
n=$(grep -c ' up -d ' "$FAKE_LOG" || true)
case ${FAKE_HEALTH:-ok} in
  never) exit 22 ;;
  fail_first) (( n > 1 )) || exit 22 ;;
esac
printf '%s' "$FAKE_BODY"
EOF
printf '#!/usr/bin/env bash\nexit 0\n' > "$tmp/bin/flock"
chmod +x "$tmp/bin/"*

digest_a=$(printf 'a%.0s' {1..64}); digest_b=$(printf 'b%.0s' {1..64}); commit=$(printf 'c%.0s' {1..40})
img() { echo "ghcr.io/levanminh04/flash-ticket-platform/$1@sha256:$2"; }
eureka_up='{"application":{"instance":[{"status":"UP","port":{"$":8085,"@enabled":"true"},"leaseInfo":{"registrationTimestamp":4102444800000}}]}}'
eureka_stale='{"application":{"instance":[{"status":"UP","port":{"$":8085,"@enabled":"true"},"leaseInfo":{"registrationTimestamp":1000}}]}}'

run() { # run <svc> <image>; sets $out and $st
  : > "$FAKE_LOG"
  out=$(printf 'tok' | bash "$root/deploy/scripts/deploy-service.sh" "$1" "$2" "$commit" 2>&1) && st=0 || st=$?
}
use_svc() { # use_svc <svc>: fresh state and valid env file
  export FAKE_SVC=$1; rm -f "$APP_ROOT"/state/*
  printf 'X=1\n' > "$APP_ROOT/env/$1.env"; chmod 600 "$APP_ROOT/env/$1.env"
  export FAKE_BODY=$eureka_up FAKE_HEALTH=ok FAKE_MEM_LIMIT=805306368; unset FAKE_INSPECT_FIRST
}
ups() { grep -c ' up -d ' "$FAKE_LOG" || true; }

use_svc user-service
# --- validation happens before any container is touched
run user-service "ghcr.io/evil/other@sha256:$digest_a"
assert_eq "$st" 64 "foreign image exit"; assert_contains "$out" DEPLOY_REJECTED "foreign image"
run user-service "$(img event-service "$digest_a")"
assert_contains "$out" DEPLOY_REJECTED "image of another service"
chmod 644 "$APP_ROOT/env/user-service.env"; run user-service "$(img user-service "$digest_a")"
assert_contains "$out" "mode 600" "env file mode"; chmod 600 "$APP_ROOT/env/user-service.env"
out=$(printf '' | bash "$root/deploy/scripts/deploy-service.sh" user-service "$(img user-service "$digest_a")" "$commit" 2>&1) || true
assert_contains "$out" "token" "missing stdin token"
assert_eq "$(ups)" 0 "no recreate on rejection"

# --- not enough RAM: refuse, leave the running container alone
printf 'MemAvailable:   100000 kB\n' > "$MEMINFO_FILE"
run user-service "$(img user-service "$digest_a")"
assert_contains "$out" INSUFFICIENT_MEMORY "low memory"; assert_eq "$st" 65 "low memory exit"; assert_eq "$(ups)" 0 "no recreate on low memory"
printf 'MemAvailable:   4000000 kB\n' > "$MEMINFO_FILE"

# --- first deploy succeeds and records state
run user-service "$(img user-service "$digest_a")"
assert_contains "$out" DEPLOY_SUCCESS "first deploy"; assert_eq "$st" 0 "first deploy exit"
assert_eq "$(cat "$APP_ROOT/state/user-service.current")" "$(img user-service "$digest_a")" "current after first deploy"
[[ ! -e "$APP_ROOT/state/user-service.previous" ]] || fail "previous must not exist after the first deploy"

# --- second deploy shifts current to previous
run user-service "$(img user-service "$digest_b")"
assert_contains "$out" DEPLOY_SUCCESS "second deploy"
assert_eq "$(cat "$APP_ROOT/state/user-service.previous")" "$(img user-service "$digest_a")" "previous"
assert_eq "$(cat "$APP_ROOT/state/user-service.current")" "$(img user-service "$digest_b")" "current"

# --- unhealthy new image rolls back to current and keeps state
FAKE_HEALTH=fail_first run user-service "$(img user-service "$digest_a")"
assert_contains "$out" "ROLLED_BACK HEALTH_TIMEOUT" "rollback on unhealthy"; assert_eq "$st" 1 "rollback exit"
assert_eq "$(ups)" 2 "deploy + rollback recreate"
assert_eq "$(cat "$APP_ROOT/state/user-service.current")" "$(img user-service "$digest_b")" "current kept after rollback"

# --- stale Eureka registration from the previous container must not count as healthy
FAKE_BODY=$eureka_stale run user-service "$(img user-service "$digest_a")"
assert_contains "$out" "HEALTH_TIMEOUT" "stale registration"

# --- OOM is reported as OOM_KILLED, not as a bad build, and still rolls back
FAKE_INSPECT_FIRST="true 0 true sha256:fakeimageid" run user-service "$(img user-service "$digest_a")"
assert_contains "$out" "ROLLED_BACK OOM_KILLED" "oom"

# --- first deploy that fails has nothing to roll back to
use_svc user-service
FAKE_HEALTH=never run user-service "$(img user-service "$digest_a")"
assert_contains "$out" "DEPLOY_FAILED HEALTH_TIMEOUT" "first deploy failure"; assert_eq "$(ups)" 1 "no rollback recreate"

# --- discovery-server health is its own actuator endpoint
use_svc discovery-server
FAKE_BODY='{"status":"UP"}' run discovery-server "$(img discovery-server "$digest_a")"
assert_contains "$out" DEPLOY_SUCCESS "discovery deploy"
echo "test-deploy-service OK"
