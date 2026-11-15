#!/usr/bin/env bash
# Runs on 84. Kubeconfig stays on the node; never enable shell tracing here.
set -euo pipefail
umask 077
service=${1:?service}; image=${2:?image digest}; sha=${3:?commit sha}; mode=${4:?mode}
app_root=${APP_ROOT:-/opt/flash-ticket-app}
namespace=flash-ticket
die() { echo "DEPLOY_REJECTED $*" >&2; exit 1; }
case $service in discovery-server|api-gateway|booking-service|payment-service|ticket-service|event-service|user-service) ;; *) die UNKNOWN_SERVICE ;; esac
[[ $image =~ ^ghcr.io/levanminh04/flash-ticket-platform/$service@sha256:[a-f0-9]{64}$ ]] || die INVALID_IMAGE
[[ $sha =~ ^[a-f0-9]{40}$ ]] || die INVALID_SHA
[[ $mode == deploy || $mode == recreate || $mode == rollback ]] || die INVALID_MODE
state="$app_root/state/k8s"
source_root="$app_root/releases/$sha"
mkdir -p "$state"
chmod 700 "$state"
exec 9> "$state/deploy.lock"
flock -w 600 9 || die DEPLOY_LOCK_TIMEOUT
[[ ! -e $app_root/state/maintenance || ${K8S_BOOTSTRAP_SHA:-} == "$sha" ]] || die MAINTENANCE_ACTIVE
[[ -f $state/enabled ]] || die K8S_NOT_ENABLED
# Plain kubectl deliberately uses the operator's protected kubeconfig.
kubectl get namespace "$namespace" >/dev/null || die CLUSTER_NOT_READY
revision_root="$state/$service"
mkdir -p "$revision_root"
previous=''
if [[ -f $revision_root/current ]]; then
  previous=$(cat "$revision_root/current")
  [[ $previous =~ ^[0-9]+-[a-f0-9]{12}-[0-9]+$ ]] || die INVALID_STATE
fi

apply_revision() {
  local revision=$1 resource manifest
  # Secret diagnostics may include values; never forward their raw stderr.
  for resource in "$revision"/secret*.json; do
    [[ -f $resource ]] || continue
    kubectl apply -f "$resource" >/dev/null 2>"$revision/secret-apply.error" || { echo 'SECRET_APPLY_FAILED' >&2; return 1; }
  done
  if [[ -f $revision/hpa.json ]]; then
    kubectl -n "$namespace" get deployment "$service" --ignore-not-found -o json > "$revision/replica-ownership-live.json" || return 1
    python3 - "$revision" <<'PY' || return 1
import json, pathlib, sys
root = pathlib.Path(sys.argv[1])
deployment = json.loads((root / 'deployment.json').read_text())
# HPA owns replicas, including when restoring an older app revision.
deployment['spec'].pop('replicas', None)
(root / 'deployment-effective.json').write_text(json.dumps(deployment))
ownership = root / 'replica-ownership.json'
ownership.unlink(missing_ok=True)
live_text = (root / 'replica-ownership-live.json').read_text().strip()
live = json.loads(live_text) if live_text else {}
last = live.get('metadata', {}).get('annotations', {}).get('kubectl.kubernetes.io/last-applied-configuration')
if last:
    previous = json.loads(last)
    if 'replicas' in previous.get('spec', {}):
        previous['spec'].pop('replicas')
        previous.get('metadata', {}).get('annotations', {}).pop('kubectl.kubernetes.io/last-applied-configuration', None)
        ownership.write_text(json.dumps(previous))
PY
    if [[ -f $revision/replica-ownership.json ]]; then
      kubectl apply set-last-applied -f "$revision/replica-ownership.json" >/dev/null || return 1
    fi
  else
    # Restoring a revision without HPA explicitly restores fixed-replica operation.
    kubectl -n "$namespace" delete hpa "$service" --ignore-not-found >/dev/null || return 1
  fi
  for resource in configmap service ingress deployment hpa; do
    [[ -f $revision/$resource.json ]] || continue
    manifest="$revision/$resource.json"
    if [[ $resource == deployment && -f $revision/hpa.json ]]; then manifest="$revision/deployment-effective.json"; fi
    kubectl apply -f "$manifest" || return 1
  done
}

check_revision() {
  local revision=$1 attempt
  kubectl -n "$namespace" rollout status "deployment/$service" --timeout=300s || return 1
  kubectl -n "$namespace" get service discovery-server -o json > "$revision/discovery.json" || return 1
  for attempt in {1..40}; do
    # Read desired scale again: HPA may change it while Pods register in Eureka.
    kubectl -n "$namespace" get deployment "$service" -o json > "$revision/live-deployment.json" || return 1
    kubectl -n "$namespace" get pods -l "app.kubernetes.io/name=$service" -o json > "$revision/live-pods.json" || return 1
    if python3 - "$revision" "$service" 2>"$revision/rollout-check.error" <<'PY'
import json, pathlib, sys, urllib.request
root, service = pathlib.Path(sys.argv[1]), sys.argv[2]
expected = (root / 'image').read_text().strip()
deployment = json.loads((root / 'live-deployment.json').read_text())
pod_list = json.loads((root / 'live-pods.json').read_text())
assert deployment['spec']['template']['spec']['containers'][0]['image'] == expected, 'IMAGE_MISMATCH'
desired = deployment['spec'].get('replicas', 1)
pods = [p for p in pod_list['items'] if not p['metadata'].get('deletionTimestamp')]
assert desired >= 1 and len(pods) == desired, 'REPLICA_COUNT_MISMATCH'
for pod in pods:
    statuses = pod['status'].get('containerStatuses', [])
    assert pod['spec']['containers'][0]['image'] == expected, 'POD_IMAGE_MISMATCH'
    assert len(statuses) == 1 and statuses[0]['ready'] and statuses[0]['restartCount'] == 0, 'POD_NOT_STABLE'
if service == 'discovery-server':
    sys.exit(0)
discovery = json.loads((root / 'discovery.json').read_text())['spec']
url = 'http://' + discovery['clusterIP'] + ':' + str(discovery['ports'][0]['port']) + '/eureka/apps/' + service.upper()
request = urllib.request.Request(url, headers={'Accept': 'application/json'})
with urllib.request.urlopen(request, timeout=3) as response:
    instances = json.load(response)['application']['instance']
if isinstance(instances, dict):
    instances = [instances]
assert all(any(i.get('instanceId') == service + ':' + pod['metadata']['uid']
               and i.get('ipAddr') == pod['status']['podIP']
               and int(i['port']['$']) == pod['spec']['containers'][0]['ports'][0]['containerPort']
               and i.get('status') == 'UP' for i in instances) for pod in pods), 'EUREKA_REGISTRATION_PENDING'
PY
    then
      return 0
    fi
    sleep 3
  done
  echo "POD_OR_EUREKA_CHECK_TIMEOUT $service attempts=$attempt" >&2
  return 1
}

if [[ $mode == rollback ]]; then
  [[ -f $revision_root/previous ]] || die NO_PREVIOUS_REVISION
  revision=$(cat "$revision_root/previous")
  [[ $revision =~ ^[0-9]+-[a-f0-9]{12}-[0-9]+$ ]] || die INVALID_STATE
  [[ $(cat "$revision_root/$revision/image") == "$image" ]] || die ROLLBACK_CONFIG_NOT_FOUND
else
  revision="$(date +%s)-${sha:0:12}-$$"
  target="$revision_root/$revision"
  mkdir -m 700 "$target"
  printf '%s\n' "$image" > "$target/image"
  for resource in configmap service ingress deployment hpa; do
    file="$source_root/deploy/k8s/$service/$resource.yaml"
    [[ -f $file ]] || { [[ $resource != configmap && $resource != deployment ]] || die MISSING_MANIFEST; continue; }
    kubectl create --dry-run=client --validate=false -f "$file" -o json > "$target/$resource.json"
  done
  python3 - "$target" "$image" "$revision" <<'PY'
import json, pathlib, sys
root = pathlib.Path(sys.argv[1])
path = root / 'deployment.json'
deployment = json.loads(path.read_text())
template = deployment['spec']['template']
template['spec']['containers'][0]['image'] = sys.argv[2]
template['metadata'].setdefault('annotations', {})['flash-ticket/release'] = sys.argv[3]
path.write_text(json.dumps(deployment))
PY
  if [[ $service != discovery-server ]]; then
    python3 - "$source_root/deploy/k8s/examples/$service.secret.env.example" "$app_root/env/$service.env" "$target/secret-env.json" "$service" <<'PY'
import base64, json, pathlib, re, sys, urllib.parse
template, env_file, output, service = sys.argv[1:]
allowed, required, optional = set(), set(), False
for line in pathlib.Path(template).read_text(encoding='utf-8').splitlines():
    if line == '# Optional':
        optional = True
    if re.fullmatch(r'[A-Z][A-Z0-9_]*=', line):
        key = line[:-1]
        allowed.add(key)
        if not optional:
            required.add(key)
values = {}
# Dotenv literals only. No eval, shell expansion or import of endpoint variables.
for line in pathlib.Path(env_file).read_text(encoding='utf-8-sig').splitlines():
    line = line.strip()
    if not line or line.startswith('#'):
        continue
    key, separator, value = line.removeprefix('export ').partition('=')
    key = key.strip()
    if separator and key in allowed:
        value = value.strip()
        if value[:1] in ('"', "'") and value[-1:] == value[:1]:
            value = value[1:-1]
        if value:
            values[key] = value
if service == 'payment-service':
    values.setdefault('PAYMENT_SERVICE_CLIENT_SECRET', values.get('PAYMENT_INTERNAL_SERVICE_CLIENT_SECRET') or values.get('INTERNAL_SERVICE_CLIENT_SECRET', ''))
if service == 'event-service':
    values.setdefault('INTERNAL_SERVICE_CLIENT_SECRET', values.get('EVENT_INTERNAL_SERVICE_CLIENT_SECRET', ''))
if service == 'user-service':
    values.setdefault('USER_MONGO_URI', values.get('MONGO_URI', ''))
    uri = values.get('USER_MONGO_URI', '')
    # Preserve credentials/database/query. Only migrate the verified loopback host.
    uri = re.sub(r'(?<=@)(localhost|127\.0\.0\.1)(?=[:/])', '172.31.41.236', uri)
    uri = re.sub(r'(?<=//)(localhost|127\.0\.0\.1)(?=[:/])', '172.31.41.236', uri)
    if uri and urllib.parse.urlsplit(uri).hostname in ('localhost', '127.0.0.1'):
        sys.exit('MONGO_HOST_NOT_MIGRATED')
    values['USER_MONGO_URI'] = uri
missing = sorted(key for key in required if not values.get(key))
if missing:
    sys.exit('MISSING_SECRET_KEYS ' + ','.join(missing))
document = {'apiVersion': 'v1', 'kind': 'Secret', 'metadata': {'name': service + '-secret', 'namespace': 'flash-ticket'},
            'type': 'Opaque', 'data': {k: base64.b64encode(v.encode()).decode() for k,v in values.items()}}
pathlib.Path(output).write_text(json.dumps(document))
PY
  fi
  if [[ $service == ticket-service ]]; then
    python3 - "$app_root/secrets/ticket-qr/key-ring.json" "$target/configmap.json" <<'PY'
import base64, json, pathlib, sys
try:
    path = pathlib.Path(sys.argv[1])
    assert 0 < path.stat().st_size <= 65536
    keys = json.loads(path.read_text())['keys']
    version = json.loads(pathlib.Path(sys.argv[2]).read_text())['data']['TICKET_QR_ACTIVE_KEY_VERSION']
    assert isinstance(keys, dict) and version in keys
    assert all(isinstance(k, str) and k.strip() and isinstance(v, str)
               and len(base64.b64decode(v, validate=True)) == 32 for k, v in keys.items())
except (OSError, ValueError, KeyError, TypeError, AssertionError):
    sys.exit('INVALID_QR_KEY_RING')
PY
    kubectl -n "$namespace" create secret generic ticket-qr-key-ring --from-file="key-ring.json=$app_root/secrets/ticket-qr/key-ring.json" --dry-run=client -o json > "$target/secret-qr.json" 2> "$target/secret-create.error" || die QR_SECRET_UNAVAILABLE
  fi
fi
target="$revision_root/$revision"
if apply_revision "$target" && check_revision "$target"; then
  printf '%s\n' "$revision" > "$revision_root/current.tmp"
  mv -f "$revision_root/current.tmp" "$revision_root/current"
  if [[ -n $previous && $previous != "$revision" ]]; then printf '%s\n' "$previous" > "$revision_root/previous"; fi
  printf '%s\n' "$image" > "$state/$service.current.tmp"
  mv -f "$state/$service.current.tmp" "$state/$service.current"
  echo "DEPLOY_OK $service $revision"
else
  echo "DEPLOY_FAILED $service $revision" >&2
  if [[ -n $previous ]]; then
    if apply_revision "$revision_root/$previous" && check_revision "$revision_root/$previous"; then
      echo "RESTORED_PREVIOUS $service $previous" >&2
    else
      echo "RESTORE_FAILED $service: operator intervention required" >&2
    fi
  else
    echo 'NO_PREVIOUS_K8S_REVISION: follow the Compose cutover rollback runbook' >&2
  fi
  exit 1
fi
