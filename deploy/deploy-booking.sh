#!/usr/bin/env bash
# Chạy từ root trên119; registry token đi qua stdin, không truyền trong command line.
set -Eeuo pipefail
umask 077
cd /home/ubuntu/flash-ticket-platform

if [[ $# != 3 ]]; then
  echo 'Usage: deploy-booking.sh BOOKING_IMAGE@sha256:... CONFIG_SERVER_IMAGE@sha256:... REGISTRY_USERNAME' >&2
  exit 2
fi
export BOOKING_IMAGE="$1" CONFIG_SERVER_IMAGE="$2"
registry_username="$3"
[[ "$BOOKING_IMAGE" =~ ^ghcr\.io/levanminh04/flash-ticket-platform/booking-service@sha256:[a-f0-9]{64}$ ]]
[[ "$CONFIG_SERVER_IMAGE" =~ ^ghcr\.io/levanminh04/flash-ticket-platform/config-server@sha256:[a-f0-9]{64}$ ]]
[[ "$registry_username" =~ ^[A-Za-z0-9_-]+$ ]]
test -r .env
for command in docker curl jq flock ss; do command -v "$command" >/dev/null; done
docker compose version >/dev/null

# Cả lần deploy thủ công cũng phải chờ lượt trước; không cắt dở container recreate.
exec 9>.booking-deploy.lock
flock -w 300 9
booking_compose=(docker compose --env-file .env -f deploy/docker-compose.booking.yml)
config_compose=(docker compose --env-file .env -f deploy/docker-compose.config-server.yml)
"${booking_compose[@]}" config --quiet
"${config_compose[@]}" config --quiet

for specification in 'flash-ticket-config-server config-server 8888' 'flash-ticket-booking booking-service 8082'; do
  read -r project service port <<< "$specification"
  container=$(docker ps -aq --filter "label=com.docker.compose.project=$project" --filter "label=com.docker.compose.service=$service")
  if [[ -n "$(ss -H -lnt "sport = :$port")" && -z "$container" ]]; then
    echo "Port $port occupied outside expected Compose project $project" >&2
    exit 1
  fi
  if [[ -n "$container" ]]; then
    echo "PREVIOUS $service $(docker inspect --format '{{.Config.Image}}' "$container")"
  else
    echo "PREVIOUS $service none"
  fi
done

registry_config=$(mktemp -d)
export DOCKER_CONFIG="$registry_config"
trap 'rm -rf -- "$registry_config"' EXIT
docker login ghcr.io --username "$registry_username" --password-stdin

# Pull đủ cả hai image rồi mới thay container đang chạy.
"${config_compose[@]}" pull config-server
"${booking_compose[@]}" pull booking-service

wait_for_health() {
  local url="$1" label="$2" deadline=$((SECONDS + 180))
  while (( SECONDS < deadline )); do
    if curl --silent --fail --max-time 5 "$url" | jq -e '.status == "UP"' >/dev/null; then
      echo "HEALTH $label UP"
      return 0
    fi
    sleep 3
  done
  echo "Health timeout: $label; inspect filtered application logs on119" >&2
  return 1
}

"${config_compose[@]}" up -d --no-deps --no-build config-server
wait_for_health http://127.0.0.1:8888/actuator/health config-server
# Không in config response ra log vì có thể chứa property nhạy cảm.
curl --silent --fail --max-time 10 http://127.0.0.1:8888/booking-service/default |
  jq -e '.name == "booking-service" and (.propertySources | length > 0)' >/dev/null
"${booking_compose[@]}" up -d --no-deps --no-build booking-service
wait_for_health http://127.0.0.1:8082/actuator/health booking-service

# Config.Image giữ digest đã yêu cầu; Image ID xác nhận container dùng đúng image local.
for specification in 'flash-ticket-config-server config-server' 'flash-ticket-booking booking-service'; do
  read -r project service <<< "$specification"
  container=$(docker ps -q --filter "label=com.docker.compose.project=$project" --filter "label=com.docker.compose.service=$service")
  test -n "$container"
  image="$BOOKING_IMAGE"
  [[ "$service" != config-server ]] || image="$CONFIG_SERVER_IMAGE"
  test "$(docker inspect --format '{{.Image}}' "$container")" = "$(docker image inspect --format '{{.Id}}' "$image")"
  docker inspect --format 'DEPLOYED {{.Name}} {{.Config.Image}} state={{.State.Status}} restarts={{.RestartCount}} oom={{.State.OOMKilled}}' "$container"
  test "$(docker inspect --format '{{.State.OOMKilled}} {{.RestartCount}}' "$container")" = 'false 0'
done
free -m
echo 'DEPLOY_SUCCESS config-server + booking-service'
