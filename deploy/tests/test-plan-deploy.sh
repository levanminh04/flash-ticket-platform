#!/usr/bin/env bash
# plan-deploy.sh validates workflow inputs before the pipeline touches the server.
set -euo pipefail
. "$(dirname "$0")/lib.sh"
cd "$root"
script=.github/scripts/plan-deploy.sh
field() { sed -n "s/^$1=//p" <<< "$2"; }
digest="sha256:$(printf 'c%.0s' {1..64})"

out=$(EVENT_NAME=workflow_dispatch ACTION=deploy SERVICE=all bash "$script")
assert_eq "$(field has_first "$out")" true "deploy all: has_first"
assert_eq "$(field first "$out" | jq -r '.[0].name + ":" + .[0].mode')" "discovery-server:build-and-deploy" "deploy all: first"
assert_eq "$(field rest "$out" | jq 'length')" 7 "deploy all: rest"

out=$(EVENT_NAME=workflow_dispatch ACTION=restart SERVICE=user-service bash "$script")
assert_eq "$(field has_first "$out")" false "restart: no discovery"
assert_eq "$(field rest "$out" | jq -r '.[0].name + ":" + .[0].mode')" "user-service:recreate" "restart"

out=$(EVENT_NAME=workflow_dispatch ACTION=rollback SERVICE=ticket-service IMAGE_DIGEST=$digest bash "$script")
assert_eq "$(field rest "$out" | jq -r '.[0].mode + ":" + .[0].image_digest')" "rollback:$digest" "rollback"

# push goes through detect-changes: a zero base means everything
out=$(EVENT_NAME=push BEFORE=0000000000000000000000000000000000000000 SHA=HEAD bash "$script")
assert_eq "$(field first "$out" | jq 'length')$(field rest "$out" | jq 'length')" 17 "push with zero base"

# bad input must fail before anything is deployed
for bad in "ACTION=rollback SERVICE=all IMAGE_DIGEST=$digest" \
           "ACTION=rollback SERVICE=ticket-service IMAGE_DIGEST=latest" \
           "ACTION=rollback SERVICE=ticket-service" \
           "ACTION=deploy SERVICE=nope" \
           "ACTION=nuke SERVICE=all" \
           "SERVICE=all"; do
  # shellcheck disable=SC2086
  if env EVENT_NAME=workflow_dispatch $bad bash "$script" > /dev/null 2>&1; then fail "should reject: $bad"; fi
done
echo "test-plan-deploy OK"
