#!/usr/bin/env bash
# shellcheck disable=SC2034
# Usage: detect-changes.sh <base-sha> <head-sha>
# Prints build=<json> and recreate=<json> (arrays of .github/services.json entries, in deploy order) for $GITHUB_OUTPUT.
#   build    = the service's source changed: verify + image + deploy
#   recreate = only its compose / shared deploy files changed: redeploy the digest already running
set -euo pipefail

base=${1:?base sha}
head=${2:?head sha}
root=$(git rev-parse --show-toplevel 2> /dev/null || (cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd))
registry="$root/.github/services.json"

if [[ $base =~ ^0+$ ]] || ! git -C "$root" cat-file -e "$base^{commit}" 2> /dev/null; then
  # first push of a branch or a force-push: no usable base, treat every service as changed
  files=$(jq -r '.services[].path + "/x"' "$registry")
else
  files=$(git -C "$root" diff --name-only "$base" "$head")
fi

declare -A build recreate
while IFS= read -r f; do
  if [[ -z $f || $f == *.md || $f == .env.example || $f == */.env.example ]]; then continue; fi
  while IFS=$'\t' read -r name path runtime; do
    if [[ $f == "$path"/* ]]; then build[$name]=1; fi
    # Shared log contract/checks affect the seven Spring applications.
    if [[ $f == deploy/observability/* && $name != keycloak && $name != frontend ]]; then build[$name]=1; fi
    if [[ $runtime == k8s ]]; then
      if [[ $f == "deploy/k8s/$name/"*.yaml || $f == deploy/k8s/namespace.yaml || $f == deploy/scripts/deploy-k8s-service.sh ]]; then recreate[$name]=1; fi
    else
      if [[ $f == "deploy/app/$name.yml" || $f == deploy/app/_common.yml || $f == deploy/scripts/deploy-service.sh ]]; then recreate[$name]=1; fi
    fi
  done < <(jq -r '.services[] | [.name, .path, .runtime] | @tsv' "$registry")
done <<< "$files"

for name in "${!build[@]}"; do unset "recreate[$name]"; done # a rebuilt service is redeployed anyway

pick() { # pick <assoc array name>: matching registry entries as a JSON array
  local -n ref=$1
  jq -c --argjson names "$(jq -nc '$ARGS.positional' --args "${!ref[@]}")" \
    '[.services[] | select(.name as $n | $names | index($n))]' "$registry"
}
echo "build=$(pick build)"
echo "recreate=$(pick recreate)"
