#!/usr/bin/env bash
# Isolated Git fixtures exercise the real detector and planner, not a copy.
set -euo pipefail
root=$(cd "$(dirname "$0")/../.." && pwd)
fixture=$(mktemp -d)
trap 'rm -rf -- "$fixture"' EXIT
mkdir -p "$fixture/.github/scripts"
cp "$root/.github/services.json" "$fixture/.github/"
cp "$root/.github/scripts/"{detect-changes,plan-deploy}.sh "$fixture/.github/scripts/"
cd "$fixture"
git -C "$fixture" init -q
git -C "$fixture" config core.autocrlf false
git -C "$fixture" add .
git -C "$fixture" -c user.name=fixture -c user.email=fixture@example.invalid commit -qm baseline --allow-empty
base=$(git -C "$fixture" rev-parse HEAD)
check() {
  local path=$1 build=$2 recreate=$3 output actual_build actual_recreate
  mkdir -p "$fixture/$(dirname "$path")"
  printf 'fixture\n' > "$fixture/$path"
  git -C "$fixture" add .
  git -C "$fixture" -c user.name=fixture -c user.email=fixture@example.invalid commit -qm fixture
  output=$(bash "$fixture/.github/scripts/detect-changes.sh" "$base" HEAD)
  actual_build=$(sed -n 's/^build=//p' <<< "$output" | jq -c '[.[].name] | sort')
  actual_recreate=$(sed -n 's/^recreate=//p' <<< "$output" | jq -c '[.[].name] | sort')
  [[ $actual_build == "$build" && $actual_recreate == "$recreate" ]] || { echo "Failed: $path build=$actual_build recreate=$actual_recreate" >&2; exit 1; }
  git -C "$fixture" reset --hard -q "$base"
}
check services/payment-service/src/main/Example.java '["payment-service"]' '[]'
check deploy/k8s/payment-service/configmap.yaml '[]' '["payment-service"]'
check deploy/k8s/event-service/service.yaml '[]' '["event-service"]'
check deploy/k8s/booking-service/service.yaml '[]' '["booking-service"]'
check services/assistant-service/src/main/Example.java '["assistant-service"]' '[]'
check deploy/k8s/assistant-service/deployment.yaml '[]' '["assistant-service"]'
check deploy/k8s/assistant-service/configmap.yaml '[]' '["assistant-service"]'
check deploy/scripts/deploy-k8s-service.sh '[]' '["api-gateway","assistant-service","booking-service","discovery-server","event-service","frontend","keycloak","payment-service","ticket-service","user-service"]'
check deploy/scripts/migrate-k8s-discovery.py '[]' '["api-gateway","assistant-service","booking-service","discovery-server","event-service","frontend","keycloak","payment-service","ticket-service","user-service"]'
check deploy/k8s/discovery-server/statefulset.yaml '[]' '["discovery-server"]'
check deploy/k8s/discovery-server/peer-service.yaml '[]' '["discovery-server"]'
check deploy/app/keycloak.yml '[]' '[]'
check deploy/k8s/keycloak/deployment.yaml '[]' '["keycloak"]'
check frontend/src/App.tsx '["frontend"]' '[]'
check deploy/k8s/frontend/deployment.yaml '[]' '["frontend"]'
check deploy/k8s/frontend/service.yaml '[]' '["frontend"]'
check deploy/k3s/agent-config.yaml.example '[]' '[]'
check deploy/k8s/examples/payment-service.secret.env.example '[]' '[]'
check docs/README.md '[]' '[]'
# Same service in source and manifest must appear exactly once in build.
mkdir -p "$fixture/services/payment-service/src" "$fixture/deploy/k8s/payment-service"
touch "$fixture/services/payment-service/src/Example.java" "$fixture/deploy/k8s/payment-service/configmap.yaml"
git -C "$fixture" add .
git -C "$fixture" -c user.name=fixture -c user.email=fixture@example.invalid commit -qm both
output=$(EVENT_NAME=push BEFORE="$base" SHA=HEAD bash "$fixture/.github/scripts/plan-deploy.sh")
sed -n 's/^rest=//p' <<< "$output" | jq -e 'length == 1 and .[0].name == "payment-service" and .[0].runtime == "k8s" and .[0].mode == "build-and-deploy"' >/dev/null
for service in keycloak frontend; do
  runtime=k8s
  output=$(EVENT_NAME=workflow_dispatch ACTION=restart SERVICE="$service" bash "$fixture/.github/scripts/plan-deploy.sh")
  sed -n 's/^rest=//p' <<< "$output" | jq -e --arg runtime "$runtime" 'length == 1 and .[0].runtime == $runtime and .[0].mode == "recreate"' >/dev/null
done
output=$(EVENT_NAME=workflow_dispatch ACTION=deploy SERVICE=assistant-service bash "$fixture/.github/scripts/plan-deploy.sh")
sed -n 's/^first=//p' <<< "$output" | jq -e 'length == 0' >/dev/null
sed -n 's/^rest=//p' <<< "$output" | jq -e 'length == 1 and (.[0] | .name == "assistant-service" and .kind == "maven" and .mvn == "mvn" and .runtime == "k8s" and .mode == "build-and-deploy" and .postgres == false and .rabbitmq == false)' >/dev/null
output=$(EVENT_NAME=workflow_dispatch ACTION=deploy SERVICE=k8s bash "$fixture/.github/scripts/plan-deploy.sh")
sed -n 's/^first=//p' <<< "$output" | jq -e 'length == 1 and .[0].name == "discovery-server" and .[0].runtime == "k8s"' >/dev/null
sed -n 's/^rest=//p' <<< "$output" | jq -e 'length == 9 and all(.[]; .runtime == "k8s" and .mode == "build-and-deploy")' >/dev/null
echo 'Deployment routing fixtures passed (source, manifest, shared, Compose, examples, docs, precedence).'
