#!/usr/bin/env bash
# Deploy only Ticket; retain private Compose/env snapshots for automatic rollback.
set -Eeuo pipefail
umask 077

fail() { printf 'DEPLOY_FAILED %s\n' "$1" >&2; exit 1; }
trap 'fail command_failed' ERR
image='' env_file='' revision='' registry_user='' project=flash-ticket-ticket
compose_file="$(cd -- "$(dirname -- "$0")" && pwd)/docker-compose.ticket.yml"
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
repository="${TICKET_IMAGE_REPOSITORY:-ghcr.io/levanminh04/flash-ticket-platform/ticket-service}"
official=false
if [[ "$image" == "$repository"@sha256:* && "${image#"$repository"@}" =~ ^sha256:[a-f0-9]{64}$ ]]; then
  official=true
elif [[ ( "$predeploy" == true || "$local_image" == true ) && "$image" =~ ^sha256:[a-f0-9]{64}$ ]]; then
  :
else fail unverified_image_reference
fi
if [[ "$predeploy" == true ]]; then
  [[ "$project" =~ ^flash-ticket-ticket-predeploy-[a-f0-9]{32}$ ]] || fail unsafe_predeploy_project
else
  [[ "$project" == flash-ticket-ticket ]] || fail unexpected_project
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
work=$(mktemp -d "${TMPDIR:-/tmp}/ft-ticket-deploy.XXXXXXXX")
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
  env "${scrub[@]}" TICKET_IMAGE="$ref" docker compose --project-name "$project" \
    --env-file "$directory/runtime.env" -f "$directory/compose.yaml" "$@"
}
render() { compose "$1" "$2" config --format json > "$work/model.json" 2>/dev/null; }
# Compose config escapes literal dollars for re-use as a Compose document.
# Settings consumed directly by curl must recover the actual runtime value.
setting() { jq -er --arg key "$1" '.services["ticket-service"].environment[$key] // empty | gsub("[$][$]"; "$")' "$work/model.json"; }
lock_root="${TICKET_DEPLOY_LOCK_ROOT:-/run/lock}"
mkdir -p -- "$lock_root"
take_lock() {
  local lock="$lock_root/flash-ticket-ticket-$1.lock"
  [[ ! -L "$lock" ]] || fail unsafe_deployment_lock
  exec {descriptor}>>"$lock"
  [[ ! -L "$lock" && "$(stat -Lc %u "/proc/$$/fd/$descriptor")" == "$(id -u)" ]] || fail deployment_lock_owner_mismatch
  flock -n "$descriptor" || fail deployment_already_in_progress
}
take_lock "$project"

# Resolve the effective port under the project lock, then also lock that port.
install -m 600 -- "$compose_file" "$work/compose.yaml"
canonical_env "$env_file" > "$work/runtime.env"
printf '\nTICKET_IMAGE=%s\n' "$image" >> "$work/runtime.env"
render "$work" "$image" || fail invalid_compose_configuration
[[ "$(jq -r '.services["ticket-service"].image' "$work/model.json")" == "$image" ]] || fail compose_image_mismatch
port=$(setting TICKET_SERVICE_PORT) || fail invalid_port
# shellcheck disable=SC2015 # Fail on either invalid syntax or invalid numeric range.
[[ "$port" =~ ^[0-9]{1,10}$ ]] && (( 10#$port >= 1024 && 10#$port <= 65535 )) || fail invalid_port
port=$((10#$port))
printf '\nTICKET_SERVICE_PORT=%s\n' "$port" >> "$work/runtime.env"
address=$(setting SERVER_ADDRESS) || fail independent_rollout_requires_loopback
[[ "$address" == 127.0.0.1 && "$(jq -r '.services["ticket-service"].network_mode' "$work/model.json")" == host ]] || fail independent_rollout_requires_loopback
config_url=$(setting CONFIG_SERVER_URL) || fail missing_config_server
# Bootstrap may keep business disabled and the private QR directory empty.
# Never generate/rotate keys during deployment; an existing keyring must survive rollback.
business=$(setting TICKET_BUSINESS_ENABLED) || fail invalid_feature_flags
listeners=$(setting TICKET_LISTENERS_ENABLED) || fail invalid_feature_flags
workers=$(setting TICKET_WORKERS_ENABLED) || fail invalid_feature_flags
for flag in "$business" "$listeners" "$workers"; do
  [[ "$flag" == true || "$flag" == false ]] || fail invalid_feature_flags
done
[[ "$business" == true || ( "$listeners" == false && "$workers" == false ) ]] || fail business_required_for_background_work
qr_directory=$(jq -er '.services["ticket-service"].volumes[] |
  select(.type == "bind" and .target == "/run/ticket-qr" and .read_only == true and
    .bind.create_host_path == false) | .source' "$work/model.json") || fail missing_read_only_qr_mount
[[ "$qr_directory" == /* && "$qr_directory" != *,* && -d "$qr_directory" && ! -L "$qr_directory" ]] || fail unsafe_qr_directory
if [[ "$business" == true ]]; then
  key_location=$(setting TICKET_QR_KEY_RING_LOCATION) || fail missing_keyring_location
  [[ "$key_location" == file:/run/ticket-qr/keys.json || "$key_location" == file:///run/ticket-qr/keys.json ]] || fail keyring_outside_read_only_mount
  key_version=$(setting TICKET_QR_ACTIVE_KEY_VERSION) || fail missing_active_key_version
  key_file="$qr_directory/keys.json"
  [[ -f "$key_file" && ! -L "$key_file" && -r "$key_file" ]] || fail missing_private_keyring
  for path in "$qr_directory" "$key_file"; do
    mode=$(stat -c %a -- "$path")
    (( (8#$mode & 027) == 0 )) || fail keyring_must_be_private
  done
  key_size=$(stat -c %s -- "$key_file")
  (( key_size > 0 && key_size <= 65536 )) || fail invalid_keyring_size
  # Ordinary jq collapses duplicate object properties, unlike the strict Java loader.
  # A leaf closes its path; an end marker closes its parent. Reopening any closed
  # prefix means a duplicate key (at any depth) or a trailing JSON document.
  jq --stream -s -e 'reduce .[] as $item ({closed:{}, duplicate:false};
    if ($item | length) == 2 then
      $item[0] as $path |
      [range(0; ($path | length) + 1) as $n | ($path[:$n] | tojson)] as $prefixes |
      . as $state |
      .duplicate = (.duplicate or any($prefixes[]; . as $prefix | $state.closed[$prefix] == true)) |
      .closed[($path | tojson)] = true
    else .closed[($item[0][:-1] | tojson)] = true end) | .duplicate | not' \
    "$key_file" >/dev/null 2>&1 || fail duplicate_or_trailing_keyring_json
  jq -e --arg version "$key_version" '.keys | type == "object" and length > 0 and has($version) and
    all(to_entries[]; (.key | test("[^[:space:]]")) and
      (.value | type == "string" and test("^[A-Za-z0-9+/]{43}=$")))' \
    "$key_file" >/dev/null 2>&1 || fail invalid_aes256_keyring
fi
if [[ "$workers" == true ]]; then
  sender=$(setting TICKET_MAIL_FROM) || fail missing_mail_sender
  buyer_url=$(setting TICKET_BUYER_BASE_URL) || fail missing_buyer_url
  [[ "$sender" =~ ^[^[:space:]@]+@[^[:space:]@]+\.[^[:space:]@]+$ ]] || fail invalid_mail_sender
  [[ "$buyer_url" =~ ^https?://[^/?#@[:space:]]+(/[^?#[:space:]]*)?$ ]] || fail invalid_buyer_url
fi
state_root="${TICKET_DEPLOY_STATE_ROOT:-$HOME/.flash-ticket-ticket-deploy}"
take_lock "port-$port"
private_directory "$state_root"
state="$state_root/$project"
private_directory "$state"
read -r uuid < /proc/sys/kernel/random/uuid
release="$state/release-${uuid//-/}"
mkdir -m 700 -- "$release"
install -m 600 -- "$work/compose.yaml" "$release/compose.yaml"
install -m 600 -- "$work/runtime.env" "$release/runtime.env"

current=$(docker ps -aq --filter "label=com.docker.compose.project=$project" --filter "label=com.docker.compose.service=ticket-service" 2>/dev/null)
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
  [[ -n "$current" ]] && jq -e --arg value "TICKET_SERVICE_PORT=$port" \
    '.[0] | .State.Running and (.Config.Env | index($value) != null)' "$work/current.json" >/dev/null || fail port_owned_by_another_process
fi
curl --silent --fail --max-time 10 "${config_url%/}/ticket-service/default" > "$work/config.json" 2>/dev/null || fail config_server_unavailable
jq -e '[.propertySources | reverse[] | .source] | add |
  .["spring.datasource.hikari.connection-init-sql"] == "SET ROLE ticket_owner" and
  .["spring.jpa.hibernate.ddl-auto"] == "none" and
  .["spring.jpa.properties.hibernate.default_schema"] == "ticket_schema" and
  .["spring.sql.init.mode"] == "never" and
  .["spring.rabbitmq.dynamic"] == false and
  .["spring.rabbitmq.listener.simple.acknowledge-mode"] == "manual" and
  .["spring.rabbitmq.publisher-confirm-type"] == "correlated" and
  .["spring.rabbitmq.publisher-returns"] == true and
  .["spring.rabbitmq.template.mandatory"] == true and
  (.["spring.rabbitmq.template.exchange"] == "business.ex" or
   .["spring.rabbitmq.template.exchange"] == "${TICKET_RABBITMQ_BUSINESS_EXCHANGE:business.ex}" or
   .["spring.rabbitmq.template.exchange"] == "${TICKET_RABBITMQ_BUSINESS_EXCHANGE:${RABBITMQ_BUSINESS_EXCHANGE:business.ex}}") and
  has("ticket.features.business-enabled") and has("ticket.features.listeners-enabled") and
  has("ticket.features.workers-enabled") and has("ticket.qr.key-ring-location") and has("ticket.qr.active-key-version") and
  (.["spring.security.oauth2.resourceserver.jwt.issuer-uri"] | type == "string" and length > 0) and
  (.["spring.security.oauth2.resourceserver.jwt.audiences[0]"] | type == "string" and length > 0) and
  (.["spring.security.oauth2.resourceserver.jwt.jwk-set-uri"] | type == "string" and length > 0) and
  (has("logging.structured.json.add.serviceName") | not)' \
  "$work/config.json" >/dev/null 2>&1 || fail config_server_event_contract_incomplete

if [[ -n "$registry_user" ]]; then
  [[ "$registry_user" =~ ^[A-Za-z0-9_-]+$ ]] || fail invalid_registry_user
  registry_directory=$(mktemp -d "${TMPDIR:-/tmp}/ft-ticket-registry.XXXXXXXX")
  export DOCKER_CONFIG="$registry_directory"
  docker login ghcr.io --username "$registry_user" --password-stdin >/dev/null 2>&1 || fail registry_login_failed
fi
if [[ "$official" == true ]]; then
  compose "$release" "$image" pull ticket-service >/dev/null 2>&1 || fail image_pull_failed
fi
docker image inspect "$image" > "$work/image.json" 2>/dev/null || fail image_inspect_failed
expected=$(jq -er '.[0].Id' "$work/image.json")
image_revision=$(jq -er '.[0].Config.Labels["org.opencontainers.image.revision"] // empty' "$work/image.json") || fail invalid_image_revision
[[ "$image_revision" =~ ^[a-f0-9]{40}$ ]] || fail invalid_image_revision
[[ -z "$revision" || "$image_revision" == "$revision" ]] || fail image_revision_mismatch
printf '\nTICKET_SERVICE_VERSION=%s\n' "$image_revision" >> "$release/runtime.env"
render "$release" "$image" || fail invalid_compose_configuration
[[ "$(setting TICKET_SERVICE_VERSION)" == "$image_revision" ]] || fail compose_version_mismatch
if [[ "$business" == true ]]; then
  docker run --rm --network none --read-only --user 10001:10001 \
    --mount "type=bind,source=$qr_directory,target=/run/ticket-qr,readonly" \
    --entrypoint sh "$image" -c 'test -r /run/ticket-qr/keys.json' \
    >/dev/null 2>&1 || fail keyring_unreadable_by_runtime_uid
fi

# OAuth credentials and JWT stay in process memory and pipe stdin, never argv/logs.
# Refresh by the token response expiry while waiting for startup. Rollback renders
# its own saved environment before acquiring a token, just like the new release.
verify_running() {
  local directory="$1" ref="$2" expected_id="$3" check_port="$4" check_revision="$5" healthy=false
  local attempt container token='' token_deadline=0 token_uri client_secret basic response lifetime
  render "$directory" "$ref" || return 1
  token_uri=$(setting TICKET_KEYCLOAK_TOKEN_URI) || return 1
  client_secret=$(setting TICKET_INTERNAL_SERVICE_CLIENT_SECRET) || return 1
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
  container=$(compose "$directory" "$ref" ps -q ticket-service 2>/dev/null) || return 1
  [[ -n "$container" && "$container" != *$'\n'* ]] || return 1
  docker inspect "$container" > "$work/running.json" 2>/dev/null || return 1
  jq -e --arg image "$expected_id" --arg project "$project" --arg version "TICKET_SERVICE_VERSION=$check_revision" \
    '.[0] | .Image == $image and .State.Running and (.State.OOMKilled | not)
    and .Config.Labels["com.docker.compose.project"] == $project
    and ([.Config.Env[] | select(startswith("TICKET_SERVICE_VERSION="))] == [$version])' \
    "$work/running.json" >/dev/null 2>&1 || return 1
}
printf 'PREVIOUS ticket-service %s\n' "${previous_image:-none}"
if compose "$release" "$image" up -d --no-deps --no-build ticket-service >/dev/null 2>&1 &&
  verify_running "$release" "$image" "$expected" "$port" "$image_revision"; then
  :
else
  if [[ -n "$previous" ]]; then
    render "$previous" "$previous_image" || fail rollback_configuration_failed
    previous_version=$(setting TICKET_SERVICE_VERSION) || fail rollback_configuration_failed
    if compose "$previous" "$previous_image" up -d --no-deps --no-build ticket-service >/dev/null 2>&1 &&
      verify_running "$previous" "$previous_image" "$previous_id" "$previous_port" "$previous_version"; then
      printf 'ROLLBACK_SUCCESS ticket-service\n'
    else fail rollback_failed; fi
  else
    compose "$release" "$image" stop ticket-service >/dev/null 2>&1 || fail failed_first_deploy_stop
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
