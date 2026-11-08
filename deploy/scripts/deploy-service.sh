#!/usr/bin/env bash
# Usage: deploy-service.sh <service> <ghcr-image@sha256:digest> <commit>     (GHCR token on stdin)
# Env:   REGISTRY_USER (required), APP_ROOT (default /opt/flash-ticket-app)
# First output line of interest: DEPLOY_SUCCESS | ROLLED_BACK <why> | DEPLOY_FAILED <why> | DEPLOY_REJECTED <why>
set -euo pipefail

APP_ROOT=${APP_ROOT:-/opt/flash-ticket-app}
MEMINFO_FILE=${MEMINFO_FILE:-/proc/meminfo}
HEALTH_TIMEOUT=${HEALTH_TIMEOUT:-180}
POLL_SECONDS=${POLL_SECONDS:-5}
REPO=ghcr.io/levanminh04/flash-ticket-platform

declare -A PORT=(
  [discovery-server]=8761 [api-gateway]=8081 [user-service]=8085 [event-service]=8086
  [ticket-service]=8084 [booking-service]=8082 [payment-service]=8083 [keycloak]=8080
)

reject() { echo "DEPLOY_REJECTED $*"; exit 64; }

svc=${1:-}; image=${2:-}; commit=${3:-}
[[ -n $svc && -v PORT[$svc] ]] || reject "unknown service '$svc'"
[[ $image =~ ^$REPO/$svc@sha256:[a-f0-9]{64}$ ]] || reject "image must be $REPO/$svc@sha256:<digest>"
[[ $commit =~ ^[a-f0-9]{40}$ ]] || reject "commit must be 40 hex characters"
[[ ${REGISTRY_USER:-} =~ ^[A-Za-z0-9-]+$ ]] || reject "REGISTRY_USER is missing or invalid"
env_file=$APP_ROOT/env/$svc.env
[[ -f $env_file && $(stat -c %a "$env_file") == 600 ]] || reject "$env_file must exist with mode 600"
IFS= read -r token || true
[[ -n $token ]] || reject "registry token must be passed on stdin"

mkdir -p "$APP_ROOT/state"
exec 9>"$APP_ROOT/state/$svc.lock"
flock -w 300 9 || reject "another deploy of $svc is still running"

state=$APP_ROOT/state/$svc
current=$(cat "$state.current" 2>/dev/null || true)
reason=""
cid=""

docker_config=$(mktemp -d); export DOCKER_CONFIG=$docker_config
trap 'rm -rf "$docker_config"' EXIT
printf '%s' "$token" | docker login ghcr.io -u "$REGISTRY_USER" --password-stdin > /dev/null

dc() { local img=$1; shift; IMAGE=$img docker compose --env-file "$env_file" -p "flash-ticket-$svc" -f "$APP_ROOT/compose/$svc.yml" "$@"; }
targets=("$svc")
if [[ $svc == payment-service ]]; then targets+=(ngrok); fi

need=$(dc "$image" config --format json | jq -er --arg s "$svc" '.services[$s].mem_limit | tonumber') \
  || { echo "DEPLOY_FAILED INVALID_COMPOSE"; exit 1; }
avail=$(awk '/^MemAvailable:/ {print $2 * 1024}' "$MEMINFO_FILE")
# The old container is replaced by the new one, so its current RAM is released; count it as available. An unreadable cgroup counts as 0 (strict check).
old_cid=$(dc "$image" ps -q "$svc" 2>/dev/null | head -n1 || true)
freed=0
if [[ -n $old_cid ]]; then
  freed=$(cat "${CGROUP_ROOT:-/sys/fs/cgroup}/system.slice/docker-$old_cid.scope/memory.current" 2>/dev/null || echo 0)
  [[ $freed =~ ^[0-9]+$ ]] || freed=0
fi
if (( avail + freed < need )); then
  echo "DEPLOY_REJECTED INSUFFICIENT_MEMORY need=${need}B available=${avail}B freed_by_old=${freed}B"; exit 65
fi

dc "$image" pull --quiet "${targets[@]}" || { echo "DEPLOY_FAILED PULL_FAILED"; exit 1; }

recreate() { dc "$1" up -d --no-deps --no-build --force-recreate "${targets[@]}"; }

healthy() { # $1 = container id
  case $svc in
    discovery-server)
      [[ $(curl -fsS -m 5 http://127.0.0.1:8761/actuator/health | jq -r .status) == UP ]] ;;
    keycloak)
      curl -fsS -m 5 -o /dev/null http://127.0.0.1:8080/realms/flash-ticket/.well-known/openid-configuration ;;
    *)
      # Eureka keeps the old container's registration "UP" for up to 90 s, so require one registered after this container started.
      local started_ms
      started_ms=$(( $(date -d "$(docker inspect -f '{{.State.StartedAt}}' "$1")" +%s) * 1000 ))
      curl -fsS -m 5 -H 'Accept: application/json' "http://127.0.0.1:8761/eureka/apps/${svc^^}" |
        jq -e --argjson p "${PORT[$svc]}" --argjson t "$started_ms" \
          '[.application.instance | if type == "array" then .[] else . end
            | select(.status == "UP" and (.port["$"] | tonumber) == $p and .leaseInfo.registrationTimestamp >= $t)]
           | length > 0' > /dev/null ;;
  esac
}

verify() { # $1 = image that must be running; sets $reason and $cid
  local deadline=$((SECONDS + HEALTH_TIMEOUT)) oom restarts running imgid
  while (( SECONDS < deadline )); do
    cid=$(dc "$1" ps -q "$svc" || true)
    if [[ -n $cid ]]; then
      read -r oom restarts running imgid < <(docker inspect -f '{{.State.OOMKilled}} {{.RestartCount}} {{.State.Running}} {{.Image}}' "$cid")
      if [[ $oom == true ]]; then reason=OOM_KILLED; return 1; fi
      if (( restarts > 0 )); then reason=CRASH_LOOP; return 1; fi
      if [[ $running == true && $imgid == "$(docker image inspect -f '{{.Id}}' "$1")" ]] && healthy "$cid"; then
        return 0
      fi
    fi
    sleep "$POLL_SECONDS"
  done
  reason=HEALTH_TIMEOUT
  return 1
}

prune() { # keep only the digests recorded in current/previous
  local keep
  keep=" $(cat "$state.current" 2>/dev/null || true) $(cat "$state.previous" 2>/dev/null || true) "
  docker image ls --digests --format '{{.Repository}}@{{.Digest}}' "$REPO/$svc" |
    while read -r ref; do [[ $keep == *" $ref "* ]] || docker image rm "$ref" > /dev/null || true; done || true
}

recreate "$image"
if verify "$image"; then
  if [[ -n $current && $current != "$image" ]]; then printf '%s' "$current" > "$state.previous"; fi
  printf '%s' "$image" > "$state.current"
  prune
  echo "DEPLOY_SUCCESS $svc $image $commit"
  exit 0
fi

echo "---- last log lines (secrets masked) ----"
# shortcut: regex masking catches common key=value / key: value secrets only; widen it if logs ever carry other formats.
docker logs --tail 50 "$cid" 2>&1 | sed -E 's/((password|secret|token|key)[A-Za-z_]*[=:] *)[^ ,"]+/\1***/Ig' || true
if [[ -z $current ]]; then
  echo "DEPLOY_FAILED $reason (first deploy: no previous digest to roll back to)"; exit 1
fi
first_reason=$reason
recreate "$current"
if verify "$current"; then
  echo "ROLLED_BACK $first_reason -> $current"
else
  echo "DEPLOY_FAILED $first_reason (rollback to $current also failed: $reason)"
fi
exit 1
