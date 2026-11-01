#!/usr/bin/env bash
set -euo pipefail
if [[ "${1:-}" == --registry-user ]]; then
  registry_user="$2"
  shift 2
  [[ "$registry_user" =~ ^[A-Za-z0-9_-]+$ ]]
  registry_config=$(mktemp -d /tmp/ft-user-registry.XXXXXXXX)
  trap 'rm -rf -- "$registry_config"' EXIT
  export DOCKER_CONFIG="$registry_config"
  docker login ghcr.io --username "$registry_user" --password-stdin >/dev/null
  python3 "$(dirname -- "$0")/deploy-user.py" "$@"
else
  exec python3 "$(dirname -- "$0")/deploy-user.py" "$@"
fi
