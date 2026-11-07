#!/usr/bin/env bash
# Decides which services CD touches.
# Env: EVENT_NAME; push: BEFORE, SHA; workflow_dispatch: ACTION (deploy|restart|rollback), SERVICE (name|all), IMAGE_DIGEST.
# Prints first/rest/has_first/has_rest for $GITHUB_OUTPUT. `first` is discovery-server, which must be up before the rest.
set -euo pipefail

here=$(cd "$(dirname "$0")" && pwd)
registry="$here/../services.json"
die() { echo "plan-deploy: $*" >&2; exit 1; }

build='[]'; recreate='[]'; rollback='[]'
if [[ ${EVENT_NAME:-} == push ]]; then
  out=$(bash "$here/detect-changes.sh" "${BEFORE:?BEFORE}" "${SHA:?SHA}")
  build=$(sed -n 's/^build=//p' <<< "$out")
  recreate=$(sed -n 's/^recreate=//p' <<< "$out")
else
  action=${ACTION:-}; service=${SERVICE:-}
  [[ $action == deploy || $action == restart || $action == rollback ]] || die "unknown action '$action'"
  if [[ $service == all ]]; then
    [[ $action != rollback ]] || die "rollback needs exactly one service"
    selected=$(jq -c '.services' "$registry")
  else
    selected=$(jq -c --arg s "$service" '[.services[] | select(.name == $s)]' "$registry")
    [[ $selected != '[]' ]] || die "unknown service '$service'"
  fi
  if [[ $action == rollback && ! ${IMAGE_DIGEST:-} =~ ^sha256:[a-f0-9]{64}$ ]]; then
    die "rollback needs image_digest sha256:<64 hex>"
  fi
  case $action in
    deploy) build=$selected ;;
    restart) recreate=$selected ;;
    rollback) rollback=$selected ;;
  esac
fi

plan=$(jq -nc --argjson b "$build" --argjson r "$recreate" --argjson k "$rollback" \
  --arg d "${IMAGE_DIGEST:-}" --slurpfile reg "$registry" '
  ($b | map(. + {mode: "build-and-deploy"}))
  + ($r | map(. + {mode: "recreate"}))
  + ($k | map(. + {mode: "rollback", image_digest: $d})) as $all
  | [$reg[0].services[] | .name as $n | $all[] | select(.name == $n)]')
first=$(jq -c '[.[] | select(.name == "discovery-server")]' <<< "$plan")
rest=$(jq -c '[.[] | select(.name != "discovery-server")]' <<< "$plan")
echo "first=$first"
echo "rest=$rest"
echo "has_first=$(jq 'length > 0' <<< "$first")"
echo "has_rest=$(jq 'length > 0' <<< "$rest")"
