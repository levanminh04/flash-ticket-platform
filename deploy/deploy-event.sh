#!/usr/bin/env bash
# Deploy only Event; retain private Compose/env snapshots for automatic rollback.
set -Eeuo pipefail
umask 077

fail() { printf 'DEPLOY_FAILED %s\n' "$1" >&2; exit 1; }
trap 'fail command_failed' ERR
image='' env_file='' revision='' registry_user='' project=flash-ticket-event
compose_file="$(cd -- "$(dirname -- "$0")" && pwd)/docker-compose.event.yml"
predeploy=false local_image=false
while (( $# )); do
  case "$1" in
    --env-file|--compose-file|--revision|--registry-user|--project)
      (( $# >= 2 )) || fail missing_argument
      case "$1" in
        --env-file) env_file="$2";; --compose-file) compose_file="$2";;
        --revision) revision="$2";; --registry-user) registry_user="$2";; --project) project="$2";;
      esac
      shift 2;;
    --predeploy) predeploy=true; shift;;
    --local-image) local_image=true; shift;;
    *) [[ -z "$image" && "$1" != --* ]] || fail unexpected_argument; image="$1"; shift;;
  esac
done
[[ -n "$env_file" && -n "$image" ]] || fail missing_argument
[[ -z "$revision" || "$revision" =~ ^[a-f0-9]{40}$ ]] || fail invalid_expected_revision
repository="${EVENT_IMAGE_REPOSITORY:-ghcr.io/levanminh04/flash-ticket-platform/event-service}"
official=false
if [[ "$image" == "$repository"@sha256:* && "${image#"$repository"@}" =~ ^sha256:[a-f0-9]{64}$ ]]; then
  official=true
elif [[ ( "$predeploy" == true || "$local_image" == true ) && "$image" =~ ^sha256:[a-f0-9]{64}$ ]]; then
  :
else fail unverified_image_reference
fi
if [[ "$predeploy" == true ]]; then
  [[ "$project" =~ ^flash-ticket-event-predeploy-[a-f0-9]{32}$ ]] || fail unsafe_predeploy_project
else
  [[ "$project" == flash-ticket-event ]] || fail unexpected_project
fi
for command in docker curl jq flock ss stat grep sort mktemp base64; do command -v "$command" >/dev/null || fail missing_dependency; done
docker compose version >/dev/null 2>&1 || fail compose_unavailable

private_file() {
  [[ -f "$1" && ! -L "$1" && -r "$1" ]] || fail unsafe_private_file
  local mode; mode=$(stat -c %a -- "$1")
  (( (8#$mode & 077) == 0 )) || fail env_file_must_be_private
}
private_directory() {
  [[ ! -L "$1" ]] || fail unsafe_release_directory
  mkdir -p -- "$1"
  local mode; mode=$(stat -c %a -- "$1")
  # shellcheck disable=SC2015 # Both owner and mode must pass; fail if either does not.
  [[ "$(stat -c %u -- "$1")" == "$(id -u)" ]] && (( (8#$mode & 077) == 0 )) || fail release_directory_must_be_private
}
private_file "$env_file"
[[ -f "$compose_file" && ! -L "$compose_file" ]] || fail unsafe_compose_file
work=$(mktemp -d "${TMPDIR:-/tmp}/ft-event-deploy.XXXXXXXX")
registry_directory=
cleanup() {
  [[ -z "$registry_directory" ]] || rm -rf -- "$registry_directory"
  rm -rf -- "$work"
}
trap cleanup EXIT

# Preserve the former parser's literal dotenv values. Never source/eval credentials.
# JSON quoting handles backslashes/quotes; $$ prevents Compose interpolation.
canonical_env() {
  local line key value encoded
  while IFS= read -r line || [[ -n "$line" ]]; do
    line="${line%$'\r'}"
    if [[ "$line" =~ ^[[:space:]]*([A-Z][A-Z0-9_]*)[[:space:]]*=[[:space:]]*(.*)$ ]]; then
      key="${BASH_REMATCH[1]}"; value="${BASH_REMATCH[2]}"
      if [[ "$value" == \'*\' && "${#value}" -ge 2 ]]; then
        value="${value:1:${#value}-2}"
        value="${value//\\\'/\'}"
      elif [[ "$value" == \"*\" ]]; then
        value=$(printf '%s' "$value" | jq -er 'if type == "string" and
          (test("[\u0000\r\n]") | not) then . else error("invalid dotenv string") end' 2>/dev/null) || fail invalid_dotenv_value
      fi
      [[ "$value" != *$'\r'* && "$value" != *$'\n'* ]] || fail multiline_runtime_value_not_supported
      encoded=$(printf '%s' "$value" | jq -Rs .)
      printf '%s=%s\n' "$key" "${encoded//\$/\$\$}"
    fi
  done < "$1"
}
# Shell overrides of template keys are removed for new deploys AND rollbacks.
compose() {
  local directory="$1" ref="$2"; shift 2
  local key; local -a scrub=()
  while IFS= read -r key; do scrub+=(-u "$key"); done < <(
    { grep -oE '\$\{[A-Z][A-Z0-9_]*' "$directory/compose.yaml" || true; } |
      cut -c 3- | sort -u
  )
  env "${scrub[@]}" EVENT_IMAGE="$ref" docker compose --project-name "$project" \
    --env-file "$directory/runtime.env" -f "$directory/compose.yaml" "$@"
}
render() { compose "$1" "$2" config --format json > "$work/model.json" 2>/dev/null; }
# Compose config escapes literal dollars for re-use as a Compose document.
# Settings consumed directly by curl must recover the actual runtime value.
setting() { jq -er --arg key "$1" '.services["event-service"].environment[$key] // empty | gsub("[$][$]"; "$")' "$work/model.json"; }
lock_root="${EVENT_DEPLOY_LOCK_ROOT:-/run/lock}"
mkdir -p -- "$lock_root"
take_lock() {
  local lock="$lock_root/flash-ticket-event-$1.lock"
  [[ ! -L "$lock" ]] || fail unsafe_deployment_lock
  exec {descriptor}>>"$lock"
  [[ ! -L "$lock" && "$(stat -Lc %u "/proc/$$/fd/$descriptor")" == "$(id -u)" ]] || fail deployment_lock_owner_mismatch
  flock -n "$descriptor" || fail deployment_already_in_progress
}
take_lock "$project"

# Resolve the effective port under the project lock, then also lock that port.
install -m 600 -- "$compose_file" "$work/compose.yaml"
canonical_env "$env_file" > "$work/runtime.env"
printf '\nEVENT_IMAGE=%s\n' "$image" >> "$work/runtime.env"
render "$work" "$image" || fail invalid_compose_configuration
[[ "$(jq -r '.services["event-service"].image' "$work/model.json")" == "$image" ]] || fail compose_image_mismatch
port=$(setting EVENT_SERVICE_PORT) || fail invalid_port
# shellcheck disable=SC2015 # Fail on either invalid syntax or invalid numeric range.
[[ "$port" =~ ^[0-9]{1,10}$ ]] && (( 10#$port >= 1024 && 10#$port <= 65535 )) || fail invalid_port
port=$((10#$port))
printf '\nEVENT_SERVICE_PORT=%s\n' "$port" >> "$work/runtime.env"
address=$(setting SERVER_ADDRESS) || fail independent_rollout_requires_loopback
[[ "$address" == 127.0.0.1 && "$(jq -r '.services["event-service"].network_mode' "$work/model.json")" == host ]] || fail independent_rollout_requires_loopback
state_root="${EVENT_DEPLOY_STATE_ROOT:-$HOME/.flash-ticket-event-deploy}"
take_lock "port-$port"
private_directory "$state_root"
state="$state_root/$project"
private_directory "$state"
read -r uuid < /proc/sys/kernel/random/uuid
release="$state/release-${uuid//-/}"
mkdir -m 700 -- "$release"
install -m 600 -- "$work/compose.yaml" "$release/compose.yaml"
install -m 600 -- "$work/runtime.env" "$release/runtime.env"

current=$(docker ps -aq --filter "label=com.docker.compose.project=$project" --filter "label=com.docker.compose.service=event-service" 2>/dev/null)
[[ "$current" != *$'\n'* ]] || fail ambiguous_existing_container
previous='' previous_image='' previous_id='' previous_port=''
if [[ -n "$current" ]]; then
  docker inspect "$current" > "$work/current.json" 2>/dev/null || fail inspect_failed
  private_file "$state/last-good.json"
  previous_name=$(jq -er '.release' "$state/last-good.json") || fail invalid_previous_release
  [[ "$previous_name" =~ ^release-[a-f0-9]{32}$ ]] || fail invalid_previous_release
  previous="$state/$previous_name"
  private_directory "$previous"
  private_file "$previous/compose.yaml"; private_file "$previous/runtime.env"
  previous_image=$(jq -er '.image' "$state/last-good.json")
  previous_id=$(jq -er '.imageId' "$state/last-good.json")
  previous_port=$(jq -er '.port' "$state/last-good.json")
  [[ "$previous_id" == "$(jq -r '.[0].Image' "$work/current.json")" ]] || fail existing_container_differs_from_release_snapshot
fi
if [[ -n "$(ss -H -ltn "sport = :$port" 2>/dev/null)" ]]; then
  # shellcheck disable=SC2015 # An owned, running container must explain the occupied port.
  [[ -n "$current" ]] && jq -e --arg value "EVENT_SERVICE_PORT=$port" \
    '.[0] | .State.Running and (.Config.Env | index($value) != null)' "$work/current.json" >/dev/null || fail port_owned_by_another_process
fi
if [[ -n "$registry_user" ]]; then
  [[ "$registry_user" =~ ^[A-Za-z0-9_-]+$ ]] || fail invalid_registry_user
  registry_directory=$(mktemp -d "${TMPDIR:-/tmp}/ft-event-registry.XXXXXXXX")
  export DOCKER_CONFIG="$registry_directory"
  docker login ghcr.io --username "$registry_user" --password-stdin >/dev/null 2>&1 || fail registry_login_failed
fi
if [[ "$official" == true ]]; then
  compose "$release" "$image" pull event-service >/dev/null 2>&1 || fail image_pull_failed
fi
docker image inspect "$image" > "$work/image.json" 2>/dev/null || fail image_inspect_failed
jq -e '.[0].Config.Labels["org.flashticket.configuration"] == "service-local-v1"' \
  "$work/image.json" >/dev/null 2>&1 || fail image_requires_service_local_configuration
expected=$(jq -er '.[0].Id' "$work/image.json")
image_revision=$(jq -er '.[0].Config.Labels["org.opencontainers.image.revision"] // empty' "$work/image.json") || fail invalid_image_revision
[[ "$image_revision" =~ ^[a-f0-9]{40}$ ]] || fail invalid_image_revision
[[ -z "$revision" || "$image_revision" == "$revision" ]] || fail image_revision_mismatch
printf '\nEVENT_SERVICE_VERSION=%s\n' "$image_revision" >> "$release/runtime.env"
render "$release" "$image" || fail invalid_compose_configuration
[[ "$(setting EVENT_SERVICE_VERSION)" == "$image_revision" ]] || fail compose_version_mismatch

# OAuth credentials and JWT stay in process memory and pipe stdin, never argv/logs.
# Refresh by the token response expiry while waiting for startup. Rollback renders
# its own saved environment before acquiring a token, just like the new release.
verify_running() {
  local directory="$1" ref="$2" expected_id="$3" check_port="$4" check_revision="$5" healthy=false
  local attempt container token='' token_deadline=0 token_uri client_secret basic response lifetime
  render "$directory" "$ref" || return 1
  token_uri=$(setting EVENT_KEYCLOAK_TOKEN_URI 2>/dev/null || setting KEYCLOAK_TOKEN_URI) || return 1
  client_secret=$(setting EVENT_INTERNAL_SERVICE_CLIENT_SECRET) || return 1
  basic=$(printf 'internal-service:%s' "$client_secret" | base64 -w 0)
  for (( attempt=0; attempt<90; attempt++ )); do
    if (( SECONDS >= token_deadline )); then
      response=$(printf 'Authorization: Basic %s\n' "$basic" |
        curl --silent --fail --max-time 10 --header @- \
          --header 'Content-Type: application/x-www-form-urlencoded' \
          --data 'grant_type=client_credentials' "$token_uri" 2>/dev/null) || return 1
      token=$(printf '%s' "$response" | jq -er '.access_token |
        select(type == "string" and length > 0 and (test("[[:cntrl:][:space:]]") | not))' 2>/dev/null) || return 1
      lifetime=$(printf '%s' "$response" | jq -er '.expires_in // 30 |
        select(type == "number" and . >= 1 and . <= 2147483647 and floor == .)' 2>/dev/null) || return 1
      token_deadline=$((SECONDS + (lifetime + 1) / 2))
    fi
    if printf 'Authorization: Bearer %s\n' "$token" |
      curl --silent --fail --max-time 3 --header @- "http://127.0.0.1:$check_port/actuator/health" > "$work/health.json" 2>/dev/null &&
      jq -e '.status == "UP"' "$work/health.json" >/dev/null 2>&1; then healthy=true; break; fi
    sleep 2
  done
  [[ "$healthy" == true ]] || return 1
  container=$(compose "$directory" "$ref" ps -q event-service 2>/dev/null) || return 1
  [[ -n "$container" && "$container" != *$'\n'* ]] || return 1
  docker inspect "$container" > "$work/running.json" 2>/dev/null || return 1
  jq -e --arg image "$expected_id" --arg project "$project" --arg version "EVENT_SERVICE_VERSION=$check_revision" \
    '.[0] | .Image == $image and .State.Running and (.State.OOMKilled | not)
    and .Config.Labels["com.docker.compose.project"] == $project
    and ([.Config.Env[] | select(startswith("EVENT_SERVICE_VERSION="))] == [$version])' \
    "$work/running.json" >/dev/null 2>&1 || return 1
}
printf 'PREVIOUS event-service %s\n' "${previous_image:-none}"
if compose "$release" "$image" up -d --no-deps --no-build event-service >/dev/null 2>&1 &&
  verify_running "$release" "$image" "$expected" "$port" "$image_revision"; then
  :
else
  if [[ -n "$previous" ]]; then
    render "$previous" "$previous_image" || fail rollback_configuration_failed
    previous_version=$(setting EVENT_SERVICE_VERSION) || fail rollback_configuration_failed
    if compose "$previous" "$previous_image" up -d --no-deps --no-build event-service >/dev/null 2>&1 &&
      verify_running "$previous" "$previous_image" "$previous_id" "$previous_port" "$previous_version"; then
      printf 'ROLLBACK_SUCCESS event-service\n'
    else fail rollback_failed; fi
  else
    compose "$release" "$image" stop event-service >/dev/null 2>&1 || fail failed_first_deploy_stop
  fi
  fail deployment_failed_after_replacement
fi
pointer=$(mktemp "$state/.last-good.XXXXXXXX")
jq -n --arg image "$image" --arg imageId "$expected" --arg revision "$image_revision" \
  --arg release "${release##*/}" --argjson port "$port" \
  '{image:$image,imageId:$imageId,revision:$revision,release:$release,port:$port}' > "$pointer"
mv -f -- "$pointer" "$state/last-good.json"
jq -n --arg project "$project" --arg imageId "$expected" --arg revision "$image_revision" \
  --argjson port "$port" --argjson predeploy "$predeploy" --slurpfile container "$work/running.json" \
  '{event:"DEPLOY_SUCCESS",project:$project,container:$container[0][0].Id,imageId:$imageId,
    revision:$revision,health:"UP",port:$port,restartCount:$container[0][0].RestartCount,predeploy:$predeploy}'
