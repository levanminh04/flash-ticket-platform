#!/usr/bin/env bash
# detect-changes.sh turns a git diff into "build" and "recreate-only" service lists.
set -euo pipefail
. "$(dirname "$0")/lib.sh"

tmp=$(mktemp -d); trap 'rm -rf "$tmp"' EXIT
cd "$tmp"
git init -q -b main
git config user.email t@example.com; git config user.name t
mkdir -p .github/scripts .github/workflows deploy/app deploy/scripts
cp "$root/.github/services.json" .github/services.json
cp "$root/.github/scripts/detect-changes.sh" .github/scripts/
touch deploy/app/_common.yml deploy/scripts/deploy-service.sh .github/workflows/ci.yml
for name in $(jq -r '.services[].name' .github/services.json); do touch "deploy/app/$name.yml"; done
for path in $(jq -r '.services[].path' .github/services.json); do
  mkdir -p "$path/src"; echo 1 > "$path/src/A.java"; echo 1 > "$path/README.md"; echo 1 > "$path/.env.example"
done
git add -A; git commit -qm base
base=$(git rev-parse HEAD)

all="discovery-server,api-gateway,user-service,event-service,ticket-service,booking-service,payment-service,keycloak"
names() { sed -n "s/^$1=//p" <<< "$out" | jq -r '[.[].name] | join(",")'; }
step() { # step <file>...: commit a change to the files, detect against the previous commit
  for f in "$@"; do echo "$RANDOM" >> "$f"; done
  git add -A; git commit -qm change
  out=$(bash .github/scripts/detect-changes.sh "$base" HEAD)
  base=$(git rev-parse HEAD)
}

step services/user-service/src/A.java
assert_eq "$(names build)" user-service "code change: build"; assert_eq "$(names recreate)" "" "code change: recreate"

step services/user-service/README.md services/user-service/.env.example
assert_eq "$(names build)" "" "docs only: build"; assert_eq "$(names recreate)" "" "docs only: recreate"

step deploy/app/payment-service.yml
assert_eq "$(names build)" "" "compose change: build"; assert_eq "$(names recreate)" payment-service "compose change: recreate"

step deploy/app/_common.yml
assert_eq "$(names recreate)" "$all" "common change recreates everything"

step .github/workflows/ci.yml
assert_eq "$(names build)$(names recreate)" "" ".github only deploys nothing"

step infrastructure/discovery-server/src/A.java deploy/scripts/deploy-service.sh
assert_eq "$(names build)" discovery-server "script + code: build"
assert_eq "$(names recreate)" "api-gateway,user-service,event-service,ticket-service,booking-service,payment-service,keycloak" "rebuilt service is not also recreated"

# no usable base (first push of a branch, force-push): everything is treated as changed
out=$(bash .github/scripts/detect-changes.sh 0000000000000000000000000000000000000000 HEAD)
assert_eq "$(names build)" "$all" "zero base"
out=$(bash .github/scripts/detect-changes.sh deadbeefdeadbeefdeadbeefdeadbeefdeadbeef HEAD)
assert_eq "$(names build)" "$all" "unknown base"
echo "test-detect-changes OK"
