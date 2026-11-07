#!/usr/bin/env bash
# Each registered service has a valid Compose file; app containers are hardened and bounded.
set -euo pipefail
. "$(dirname "$0")/lib.sh"
command -v docker >/dev/null || fail "docker compose is required"

tmp=$(mktemp -d); trap 'rm -rf "$tmp"' EXIT
mkdir -p "$tmp/compose" "$tmp/env" "$tmp/secrets/ticket-qr"
cp "$root"/deploy/app/*.yml "$tmp/compose/"

registered=$(jq -r '.services[].name' "$root/.github/services.json" | sort)
composed=$(cd "$root/deploy/app" && ls ./*.yml | sed 's|^\./||; s|\.yml$||' | grep -v -e '^_common$' -e '^ngrok$' | sort)
assert_eq "$composed" "$registered" "deploy/app/*.yml must match .github/services.json"

digest=$(printf 'a%.0s' {1..64})
for name in $registered; do
  grep -q "\[$name\]=" "$root/deploy/scripts/deploy-service.sh" || fail "$name missing from deploy-service.sh PORT table"
  printf 'NGROK_DOMAIN=test.example.dev\nNGROK_AUTHTOKEN=x\n' > "$tmp/env/$name.env"
  out=$(IMAGE="ghcr.io/levanminh04/flash-ticket-platform/$name@sha256:$digest" \
        docker compose --env-file "$tmp/env/$name.env" -p "flash-ticket-$name" \
        -f "$tmp/compose/$name.yml" config --format json) || fail "$name: docker compose config failed"
  mem=$(jq -r --arg s "$name" '.services[$s].mem_limit // empty' <<< "$out")
  [[ -n $mem ]] || fail "$name: mem_limit is required"
  if [[ $name != keycloak ]]; then
    assert_eq "$(jq -r --arg s "$name" '.services[$s].network_mode' <<< "$out")" host "$name network_mode"
    assert_eq "$(jq -r --arg s "$name" '.services[$s].read_only' <<< "$out")" true "$name read_only"
    assert_eq "$(jq -r --arg s "$name" '.services[$s].cap_drop[0]' <<< "$out")" ALL "$name cap_drop"
  fi
done

# payment owns ngrok: it must reach payment on loopback and keep its inspector off public interfaces
out=$(IMAGE="ghcr.io/levanminh04/flash-ticket-platform/payment-service@sha256:$digest" \
      docker compose --env-file "$tmp/env/payment-service.env" -f "$tmp/compose/payment-service.yml" config --format json)
jq -e '.services.ngrok.command | index("http://127.0.0.1:8083")' <<< "$out" >/dev/null || fail "ngrok must target http://127.0.0.1:8083"
jq -e '.services.ngrok.command | index("--url=https://test.example.dev")' <<< "$out" >/dev/null || fail "ngrok --url must come from NGROK_DOMAIN"
grep -q 'web_addr: 127.0.0.1:4040' "$root/deploy/app/ngrok.yml" || fail "ngrok inspector must bind 127.0.0.1"

# ticket mounts the QR key ring read-only
out=$(IMAGE="ghcr.io/levanminh04/flash-ticket-platform/ticket-service@sha256:$digest" \
      docker compose --env-file "$tmp/env/ticket-service.env" -f "$tmp/compose/ticket-service.yml" config --format json)
jq -e '.services["ticket-service"].volumes[] | select(.target == "/run/ticket-qr" and .read_only == true)' <<< "$out" >/dev/null \
  || fail "ticket-service must mount /run/ticket-qr read-only"
echo "test-compose OK"
