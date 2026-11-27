#!/usr/bin/env bash
# Runs on 84. Kubeconfig stays on the node; never enable shell tracing here.
set -euo pipefail
umask 077
service=${1:?service}; image=${2:?image digest}; sha=${3:?commit sha}; mode=${4:?mode}
app_root=${APP_ROOT:-/opt/flash-ticket-app}
namespace=flash-ticket
die() { echo "DEPLOY_REJECTED $*" >&2; exit 1; }
case $service in discovery-server|api-gateway|booking-service|payment-service|ticket-service|event-service|user-service|frontend|assistant-service|keycloak) ;; *) die UNKNOWN_SERVICE ;; esac
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
[[ $service != keycloak || -f $state/keycloak-enabled ]] || die KEYCLOAK_CUTOVER_REQUIRED
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
  local revision=$1 resource manifest preparation=prepare
  if [[ $service == discovery-server ]]; then
    if [[ -f $revision/statefulset.json ]]; then
      kubectl -n "$namespace" get deployment "$service" --ignore-not-found -o json > "$revision/legacy-live.json" || return 1
      if [[ -s $revision/legacy-live.json ]]; then
        python3 "$source_root/deploy/scripts/migrate-k8s-discovery.py" migrate "$revision" || return 1
        return 0
      fi
    else
      kubectl -n "$namespace" get statefulset "$service" --ignore-not-found -o json > "$revision/peer-live.json" || return 1
      [[ ! -s $revision/peer-live.json ]] || { echo 'DISCOVERY_TOPOLOGY_DOWNGRADE_REQUIRES_RUNBOOK' >&2; return 1; }
    fi
  fi
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
  # Revision cũ có thể chưa có PDB; không để policy mới sót sau khi restore.
  if [[ $service == booking-service && ! -f $revision/pdb.json ]]; then
    kubectl -n "$namespace" delete pdb "$service" --ignore-not-found >/dev/null || return 1
  fi
  if [[ $service != discovery-server && $service != frontend && $service != keycloak ]]; then
    if [[ $mode == rollback || $revision != "$target" ]]; then preparation=prepare-rollback; fi
    python3 "$source_root/deploy/scripts/migrate-k8s-discovery.py" "$preparation" "$revision" || return 1
  fi
  if [[ $service == booking-service && -f $revision/pdb.json ]]; then
    # Bỏ field khỏi manifest không xóa nodeAffinity đã được đặt ngoài last-applied.
    python3 - "$revision/deployment-effective.json" <<'PY' || return 1
import json, pathlib, sys
path = pathlib.Path(sys.argv[1])
deployment = json.loads(path.read_text())
affinity = deployment['spec']['template']['spec'].get('affinity', {})
if affinity.get('podAntiAffinity', {}).get('requiredDuringSchedulingIgnoredDuringExecution') and 'nodeAffinity' not in affinity:
    affinity['nodeAffinity'] = None
    path.write_text(json.dumps(deployment))
PY
  fi
  if [[ $service == ticket-service && -f $revision/hpa.json ]]; then
    # Cùng nguyên tắc b9acaf7, nhưng Ticket recovery-1 không dùng marker PDB Booking.
    python3 - "$revision" <<'PY' || return 1
import json, pathlib, sys
root = pathlib.Path(sys.argv[1])
hpa = json.loads((root / 'hpa.json').read_text())['spec']
if hpa.get('minReplicas') == hpa.get('maxReplicas') == 1:
    path = root / 'deployment-effective.json'
    deployment = json.loads(path.read_text())
    affinity = deployment['spec']['template']['spec'].get('affinity', {})
    if affinity.get('podAntiAffinity', {}).get('requiredDuringSchedulingIgnoredDuringExecution') and 'nodeAffinity' not in affinity:
        affinity['nodeAffinity'] = None
        path.write_text(json.dumps(deployment))
PY
  fi
  for resource in configmap peer-service service ingress deployment statefulset hpa pdb; do
    [[ -f $revision/$resource.json ]] || continue
    manifest="$revision/$resource.json"
    if [[ $resource == deployment && -f $revision/deployment-effective.json ]]; then manifest="$revision/deployment-effective.json"; fi
    kubectl apply -f "$manifest" || return 1
  done
  if [[ $service == discovery-server && -f $revision/statefulset.json ]]; then
    python3 "$source_root/deploy/scripts/migrate-k8s-discovery.py" recover "$revision" || return 1
  elif [[ -f $revision/bridge-legacy.json ]]; then
    python3 "$source_root/deploy/scripts/migrate-k8s-discovery.py" bridge "$revision" || return 1
  fi
}

check_revision() {
  local revision=$1 attempt kind=deployment
  if [[ -f $revision/statefulset.json ]]; then kind=statefulset; fi
  if [[ $service == booking-service && -f $revision/pdb.json ]]; then
    # Dừng sớm nếu pinning live làm Pod HA không thể được xếp lịch như thiết kế.
    kubectl -n "$namespace" get deployment "$service" -o json > "$revision/live-deployment.json" || return 1
    python3 - "$revision/live-deployment.json" <<'PY' || return 1
import json, pathlib, sys
deployment = json.loads(pathlib.Path(sys.argv[1]).read_text())
pod = deployment['spec']['template']['spec']
if (pod.get('affinity', {}).get('nodeAffinity') or pod.get('nodeSelector') or pod.get('nodeName')):
    sys.exit('BOOKING_HA_NODE_PINNING_PRESENT')
PY
  fi
  if [[ $service == event-service && -f $revision/hpa.json ]]; then
    kubectl -n "$namespace" get deployment "$service" -o json > "$revision/event-live-deployment.json" || return 1
    kubectl -n "$namespace" get hpa "$service" -o json > "$revision/event-live-hpa.json" || return 1
    python3 - "$revision" <<'PY' || return 1
import json, pathlib, sys
root = pathlib.Path(sys.argv[1])
expected = json.loads((root / 'hpa.json').read_text())['spec']
# Legacy infra rollback HPA1/2 không được coi là recovery-1 baseline.
if expected.get('minReplicas') == expected.get('maxReplicas') == 1:
    live = json.loads((root / 'event-live-deployment.json').read_text())
    pod = live['spec']['template']['spec']
    hpa = json.loads((root / 'event-live-hpa.json').read_text())['spec']
    if pod.get('nodeName') or pod.get('nodeSelector') or pod.get('affinity', {}).get('nodeAffinity'):
        sys.exit('EVENT_NODE_PINNING_PRESENT')
    if hpa.get('minReplicas') != 1 or hpa.get('maxReplicas') != 1 or live['spec'].get('replicas', 1) != 1:
        sys.exit('EVENT_RECOVERY_REPLICA_COUNT_MISMATCH')
PY
  fi
  if [[ $service == ticket-service && -f $revision/hpa.json ]]; then
    kubectl -n "$namespace" get deployment "$service" -o json > "$revision/ticket-live-deployment.json" || return 1
    kubectl -n "$namespace" get hpa "$service" -o json > "$revision/ticket-live-hpa.json" || return 1
    python3 - "$revision" <<'PY' || return 1
import json, pathlib, sys
root = pathlib.Path(sys.argv[1])
expected = json.loads((root / 'hpa.json').read_text())['spec']
if expected.get('minReplicas') == expected.get('maxReplicas') == 1:
    live = json.loads((root / 'ticket-live-deployment.json').read_text())
    pod = live['spec']['template']['spec']
    hpa = json.loads((root / 'ticket-live-hpa.json').read_text())['spec']
    if pod.get('nodeName') or pod.get('nodeSelector') or pod.get('affinity', {}).get('nodeAffinity'):
        sys.exit('TICKET_NODE_PINNING_PRESENT')
    if hpa.get('minReplicas') != 1 or hpa.get('maxReplicas') != 1 or live['spec'].get('replicas', 1) != 1:
        sys.exit('TICKET_RECOVERY_REPLICA_COUNT_MISMATCH')
PY
  fi
  kubectl -n "$namespace" rollout status "$kind/$service" --timeout=600s || return 1
  if [[ $service == booking-service && -f $revision/pdb.json ]]; then
    kubectl -n "$namespace" get pdb "$service" -o json > "$revision/pdb-live.json" || return 1
    kubectl -n "$namespace" get hpa "$service" -o json > "$revision/hpa-live.json" || return 1
  fi
  if [[ $service != frontend && $service != keycloak ]]; then
    kubectl -n "$namespace" get service discovery-server -o json > "$revision/discovery.json" || return 1
  fi
  for attempt in {1..40}; do
    # Read desired scale again: HPA may change it while Pods register in Eureka.
    kubectl -n "$namespace" get "$kind" "$service" -o json > "$revision/live-deployment.json" || return 1
    kubectl -n "$namespace" get pods -l "app.kubernetes.io/name=$service" -o json > "$revision/live-pods.json" || return 1
    if [[ $service != frontend && $service != keycloak ]]; then
      if [[ $service == assistant-service ]]; then
        kubectl -n "$namespace" get statefulset discovery-server -o json > "$revision/discovery-workload.json" || return 1
      fi
      kubectl -n "$namespace" get pods -l 'app.kubernetes.io/name=discovery-server,flash-ticket/discovery-generation=peers' -o json > "$revision/discovery-pods.json" || return 1
    fi
    if [[ $service == ticket-service && -f $revision/hpa.json ]]; then
      # HPA có thể đổi trong lúc rollout; final gate không dùng snapshot live cũ.
      kubectl -n "$namespace" get hpa "$service" -o json > "$revision/ticket-live-hpa.json" || return 1
    fi
    if python3 - "$revision" "$service" 2>"$revision/rollout-check.error" <<'PY'
import json, pathlib, sys, urllib.request
root, service = pathlib.Path(sys.argv[1]), sys.argv[2]
expected = (root / 'image').read_text().strip()
deployment = json.loads((root / 'live-deployment.json').read_text())
pod_list = json.loads((root / 'live-pods.json').read_text())
assert deployment['spec']['template']['spec']['containers'][0]['image'] == expected, 'IMAGE_MISMATCH'
desired = deployment['spec'].get('replicas', 1)
pods = [p for p in pod_list['items'] if not p['metadata'].get('deletionTimestamp')]
if deployment['kind'] == 'StatefulSet':
    pods = [p for p in pods if p['metadata'].get('labels', {}).get('flash-ticket/discovery-generation') == 'peers']
assert desired >= 1 and len(pods) == desired, 'REPLICA_COUNT_MISMATCH'
if service == 'booking-service' and (root / 'pdb.json').is_file():
    selector = {'matchLabels': {'app.kubernetes.io/name': 'booking-service'}}
    hpa = json.loads((root / 'hpa-live.json').read_text())['spec']
    pdb = json.loads((root / 'pdb-live.json').read_text())['spec']
    assert desired == 2 and hpa['minReplicas'] == 2 and hpa['maxReplicas'] == 2, 'BOOKING_HA_REPLICA_COUNT_MISMATCH'
    nodes = [p['spec'].get('nodeName') for p in pods]
    assert all(nodes) and len(set(nodes)) == 2, 'BOOKING_HA_NODE_PLACEMENT_MISMATCH'
    assert pdb.get('minAvailable') == 1 and pdb.get('selector') == selector and 'maxUnavailable' not in pdb, 'BOOKING_HA_PDB_MISMATCH'
if service == 'ticket-service' and (root / 'hpa.json').is_file():
    profile = json.loads((root / 'hpa.json').read_text())['spec']
    if profile.get('minReplicas') == profile.get('maxReplicas') == 1:
        hpa = json.loads((root / 'ticket-live-hpa.json').read_text())['spec']
        pod_spec = deployment['spec']['template']['spec']
        assert desired == hpa.get('minReplicas') == hpa.get('maxReplicas') == 1, 'TICKET_RECOVERY_REPLICA_COUNT_MISMATCH'
        assert not (pod_spec.get('nodeName') or pod_spec.get('nodeSelector') or pod_spec.get('affinity', {}).get('nodeAffinity')), 'TICKET_NODE_PINNING_PRESENT'
if service in ('frontend', 'keycloak'):
    assert desired == 1, 'FIXED_REPLICA_COUNT_MISMATCH'
if service == 'assistant-service':
    assert desired == 1, 'ASSISTANT_REPLICA_COUNT_MISMATCH'
for pod in pods:
    statuses = pod['status'].get('containerStatuses', [])
    assert pod['spec']['containers'][0]['image'] == expected, 'POD_IMAGE_MISMATCH'
    assert len(statuses) == 1 and statuses[0]['ready'] and statuses[0]['restartCount'] == 0, 'POD_NOT_STABLE'
if service in ('discovery-server', 'frontend', 'keycloak'):
    # Frontend và Keycloak không đăng ký Eureka; Discovery kiểm registry riêng.
    sys.exit(0)
discovery = json.loads((root / 'discovery.json').read_text())['spec']
peers = json.loads((root / 'discovery-pods.json').read_text())['items']
active_peers = [p for p in peers if not p['metadata'].get('deletionTimestamp')]
if service == 'assistant-service':
    replicas = json.loads((root / 'discovery-workload.json').read_text())['spec']['replicas']
    assert replicas >= 1 and {p['metadata']['name'] for p in active_peers} == {f'discovery-server-{n}' for n in range(replicas)}, 'ASSISTANT_EUREKA_PEERS_REQUIRED'
    assert all(any(c['type'] == 'Ready' and c['status'] == 'True' for c in p['status'].get('conditions', []))
               for p in active_peers), 'ASSISTANT_EUREKA_PEER_NOT_READY'
addresses = ['http://' + p['status']['podIP'] + ':8761' for p in active_peers]
if not addresses:
    addresses = ['http://' + discovery['clusterIP'] + ':' + str(discovery['ports'][0]['port'])]
for address in addresses:
    request = urllib.request.Request(address + '/eureka/apps/' + service.upper(), headers={'Accept': 'application/json'})
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
      if [[ $service == discovery-server && $kind == statefulset ]]; then
        python3 "$source_root/deploy/scripts/migrate-k8s-discovery.py" check "$revision" || { sleep 3; continue; }
      fi
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
  for resource in configmap peer-service service ingress deployment statefulset hpa pdb; do
    file="$source_root/deploy/k8s/$service/$resource.yaml"
    [[ -f $file ]] || { [[ $resource != configmap || $service == frontend || $service == keycloak ]] || die MISSING_MANIFEST; continue; }
    kubectl create --dry-run=client --validate=false -f "$file" -o json > "$target/$resource.json"
  done
  python3 - "$target" "$image" "$revision" <<'PY'
import json, pathlib, sys
root = pathlib.Path(sys.argv[1])
path = root / ('statefulset.json' if (root / 'statefulset.json').exists() else 'deployment.json')
deployment = json.loads(path.read_text())
template = deployment['spec']['template']
template['spec']['containers'][0]['image'] = sys.argv[2]
template['metadata'].setdefault('annotations', {})['flash-ticket/release'] = sys.argv[3]
path.write_text(json.dumps(deployment))
PY
  if [[ $service != discovery-server && $service != frontend ]]; then
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
        if service == 'booking-service' and key in (
                'BOOKING_SALES_CHART_REPLICA_ENABLED',
                'BOOKING_SALES_ORGANIZER_REPORT_REPLICA_ENABLED',
                'BOOKING_SALES_EVENT_REPORT_REPLICA_ENABLED') and value not in ('true', 'false'):
            sys.exit('INVALID_BOOKING_READER_FLAG')
        if service == 'event-service' and key == 'EVENT_READ_REPLICA_ENABLED' and value not in ('true', 'false'):
            sys.exit('INVALID_EVENT_READER_FLAG')
        if service == 'ticket-service' and key == 'TICKET_LIST_REPLICA_ENABLED' and value not in ('true', 'false'):
            sys.exit('INVALID_TICKET_READER_FLAG')
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
if [[ $service == booking-service ]]; then
  python3 - "$target" "$previous" "$mode" <<'PY'
import base64, binascii, json, pathlib, re, sys, urllib.parse

target = pathlib.Path(sys.argv[1])
current, mode = sys.argv[2:]
prefix = 'BOOKING_SALES_CHART_REPLICA_'
flag = prefix + 'ENABLED'
flags = (flag, 'BOOKING_SALES_ORGANIZER_REPORT_REPLICA_ENABLED', 'BOOKING_SALES_EVENT_REPORT_REPLICA_ENABLED')


def load(revision, file):
    return json.loads((revision / file).read_text(encoding='utf-8'))


def enabled(secret, key=flag):
    value = secret.get(key, 'ZmFsc2U=')  # base64 of false
    setting = base64.b64decode(value, validate=True)
    if setting not in (b'true', b'false'):
        sys.exit('INVALID_BOOKING_READER_FLAG')
    return setting == b'true'


def writer_revision(revision, pilot):
    expected = ('image', 'deployment.json', 'service.json', 'configmap.json', 'secret-env.json', 'hpa.json', 'pdb.json')
    if not all((revision / file).is_file() and (pilot / file).is_file() for file in expected):
        return False
    deployment = load(revision, 'deployment.json')['spec']['template']
    config = load(revision, 'configmap.json')
    pilot_pod = load(pilot, 'deployment.json')['spec']['template']['spec']
    image = (revision / 'image').read_text().strip()
    return (not any(enabled(load(revision, 'secret-env.json')['data'], key) for key in flags)
            and not any(key in config.get('data', {}) for key in flags)
            and not any(v.get('name') in flags for v in deployment['spec']['containers'][0].get('env', []))
            and deployment['metadata']['annotations'].get('flash-ticket/release') == revision.name
            and deployment['spec']['containers'][0]['image'] == image == pilot_pod['containers'][0]['image']
            and image == (pilot / 'image').read_text().strip()
            and deployment['spec'].get('affinity') == pilot_pod.get('affinity')
            and deployment['spec'].get('nodeName') == pilot_pod.get('nodeName')
            and deployment['spec'].get('nodeSelector') == pilot_pod.get('nodeSelector')
            and load(revision, 'hpa.json')['spec'] == load(pilot, 'hpa.json')['spec']
            and load(revision, 'pdb.json')['spec'] == load(pilot, 'pdb.json')['spec']
            and all(load(revision, resource)['metadata'].get(key) == load(pilot, resource)['metadata'].get(key)
                    for resource in ('hpa.json', 'pdb.json') for key in ('name', 'namespace')))


try:
    secret = load(target, 'secret-env.json')['data']
    use_reader = enabled(secret)
    config = load(target, 'configmap.json')
    pod = load(target, 'deployment.json')['spec']['template']['spec']
    # Ba flag chỉ được lấy từ Secret revision, không bị override bởi nguồn khác.
    settings = {key: enabled(secret, key) for key in flags}
    if any(settings[key] for key in flags[1:]) and not use_reader:
        sys.exit('BOOKING_READER_BASE_REQUIRED')
    if any(key in config.get('data', {}) for key in flags) or any(v.get('name') in flags for v in pod['containers'][0].get('env', [])):
        sys.exit('INVALID_BOOKING_READER_FLAG_SOURCE')
    hpa = load(target, 'hpa.json')['spec'] if (target / 'hpa.json').is_file() else {}
    affinity = pod.get('affinity', {})
    has_pdb = (target / 'pdb.json').is_file()
    if has_pdb or 'podAntiAffinity' in affinity or hpa.get('minReplicas') == 2 or use_reader:
        if not has_pdb or hpa.get('minReplicas') != 2 or hpa.get('maxReplicas') != 2:
            sys.exit('INVALID_BOOKING_POD_HA')
        pdb = load(target, 'pdb.json')
        selector = {'matchLabels': {'app.kubernetes.io/name': 'booking-service'}}
        rules = affinity.get('podAntiAffinity', {}).get('requiredDuringSchedulingIgnoredDuringExecution', [])
        if (pdb.get('apiVersion') != 'policy/v1' or pdb.get('kind') != 'PodDisruptionBudget'
                or pdb['metadata'].get('name') != 'booking-service' or pdb['metadata'].get('namespace') != 'flash-ticket'
                or pdb['spec'].get('minAvailable') != 1 or 'maxUnavailable' in pdb['spec']
                or pdb['spec'].get('selector') != selector or pod.get('nodeName') or pod.get('nodeSelector')
                or 'nodeAffinity' in affinity
                or hpa.get('scaleTargetRef', {}).get('name') != 'booking-service'
                or hpa.get('scaleTargetRef', {}).get('kind') != 'Deployment'
                or not any(r.get('topologyKey') == 'kubernetes.io/hostname' and r.get('labelSelector') == selector
                           and r.get('namespaces', ['flash-ticket']) == ['flash-ticket']
                           and not r.get('matchLabelKeys') and not r.get('namespaceSelector') for r in rules)):
            sys.exit('INVALID_BOOKING_POD_HA')
    if mode == 'rollback':
        if use_reader:
            sys.exit('BOOKING_ROLLBACK_REQUIRES_WRITER_REVISION')
    elif use_reader:
        keys = ('JDBC_URL', 'USERNAME', 'PASSWORD', 'PROBE_INTERVAL', 'PROBE_QUERY_TIMEOUT',
                'MAX_SAMPLE_AGE', 'READER_TRANSACTION_TIMEOUT')
        missing = [prefix + key for key in keys if not secret.get(prefix + key)]
        if missing:
            sys.exit('MISSING_BOOKING_READER_KEYS ' + ','.join(missing))
        jdbc = base64.b64decode(secret[prefix + 'JDBC_URL'], validate=True).decode('utf-8')
        try:
            url = urllib.parse.urlsplit(jdbc.removeprefix('jdbc:'))
            params = urllib.parse.parse_qsl(url.query, strict_parsing=True, keep_blank_values=True)
            required = {'sslmode': 'require', 'targetServerType': 'secondary'}
            if (not jdbc.startswith('jdbc:postgresql://') or re.search(r'[\s\x00-\x1f#]', jdbc)
                    or not re.fullmatch(r'[A-Za-z0-9](?:[A-Za-z0-9.-]*[A-Za-z0-9])?:5432', url.netloc)
                    or url.path != '/booking_db' or len(params) != len(required) or dict(params) != required):
                raise ValueError
        except ValueError:
            sys.exit('INVALID_BOOKING_READER_URL')
        if not current:
            sys.exit('BOOKING_WRITER_REVISION_REQUIRED')
        active = target.parent / current
        baseline = current
        if enabled(load(active, 'secret-env.json')['data']):
            if not (active / 'writer-baseline').is_file():
                sys.exit('BOOKING_WRITER_REVISION_REQUIRED')
            baseline = (active / 'writer-baseline').read_text().strip()
        if not re.fullmatch(r'[0-9]+-[a-f0-9]{12}-[0-9]+', baseline) or not writer_revision(target.parent / baseline, target):
            sys.exit('BOOKING_WRITER_REVISION_REQUIRED')
        (target / 'writer-baseline').write_text(baseline + '\n')
    # Tắt reader/rollback vẫn giữ cùng image và profile HA; không trỏ previous về active cũ.
    if current and not use_reader:
        active = target.parent / current
        if enabled(load(active, 'secret-env.json')['data']):
            if not writer_revision(target, active):
                sys.exit('BOOKING_WRITER_REVISION_REQUIRED')
            if mode == 'rollback':
                baseline = target.name
            else:
                baseline = (active / 'writer-baseline').read_text().strip()
            if not re.fullmatch(r'[0-9]+-[a-f0-9]{12}-[0-9]+', baseline) or not writer_revision(target.parent / baseline, target):
                sys.exit('BOOKING_WRITER_REVISION_REQUIRED')
            (target / 'writer-baseline').write_text(baseline + '\n')
    if (target / 'writer-baseline').is_file():
        baseline = (target / 'writer-baseline').read_text().strip()
        if not re.fullmatch(r'[0-9]+-[a-f0-9]{12}-[0-9]+', baseline) or not writer_revision(target.parent / baseline, target):
            sys.exit('BOOKING_WRITER_REVISION_REQUIRED')
except (OSError, ValueError, KeyError, TypeError, binascii.Error):
    sys.exit('INVALID_BOOKING_READER_CONFIG')
PY
  if [[ -f $target/writer-baseline ]]; then previous=$(cat "$target/writer-baseline"); fi
fi
if [[ $service == event-service ]]; then
  # Chỉ quan sát drift trước apply; không tự xóa pinning/PDB ngoài quản lý.
  kubectl -n "$namespace" get pdb "$service" --ignore-not-found -o json > "$target/event-pdb-live.json"
  kubectl -n "$namespace" get deployment "$service" --ignore-not-found -o json > "$target/event-deployment-live.json"
  python3 - "$target" "$previous" "$mode" <<'PY'
import base64, binascii, json, pathlib, re, sys, urllib.parse

target = pathlib.Path(sys.argv[1])
current, mode = sys.argv[2:]
prefix = 'EVENT_READ_REPLICA_'


def event_load(revision, file):
    return json.loads((revision / file).read_text(encoding='utf-8'))


def event_enabled(secret):
    setting = base64.b64decode(secret.get(prefix + 'ENABLED', 'ZmFsc2U='), validate=True)
    if setting not in (b'true', b'false'):
        sys.exit('INVALID_EVENT_READER_FLAG')
    return setting == b'true'


def event_writer_revision(revision, candidate):
    try:
        expected = ('image', 'deployment.json', 'service.json', 'configmap.json', 'secret-env.json', 'hpa.json')
        if not all((r / file).is_file() for r in (revision, candidate) for file in expected):
            return False
        if any((r / 'pdb.json').exists() for r in (revision, candidate)):
            return False
        deployment = event_load(revision, 'deployment.json')
        pilot = event_load(candidate, 'deployment.json')
        template = deployment['spec']['template']
        pod = template['spec']
        pilot_pod = pilot['spec']['template']['spec']
        image = (revision / 'image').read_text().strip()
        config = event_load(revision, 'configmap.json')
        alternate = [*config.get('data', {}), *(v.get('name', '') for v in pod['containers'][0].get('env', []))]
        # Cùng image/profile/readiness và không nguồn flag thay thế trong baseline.
        return (not event_enabled(event_load(revision, 'secret-env.json')['data'])
                and not any(re.sub('[^a-z0-9]', '', key.lower()).startswith('eventreadreplica') for key in alternate)
                and template['metadata']['annotations'].get('flash-ticket/release') == revision.name
                and pod['containers'][0]['image'] == image == pilot_pod['containers'][0]['image']
                and image == (candidate / 'image').read_text().strip()
                and deployment['metadata'].get('name') == pilot['metadata'].get('name') == 'event-service'
                and deployment['metadata'].get('namespace') == pilot['metadata'].get('namespace') == 'flash-ticket'
                and all(deployment['spec'].get(key) == pilot['spec'].get(key) for key in ('selector', 'strategy', 'minReadySeconds'))
                and all(pod.get(key) == pilot_pod.get(key) for key in ('affinity', 'nodeName', 'nodeSelector', 'terminationGracePeriodSeconds'))
                and all(pod['containers'][0].get(key) == pilot_pod['containers'][0].get(key) for key in ('envFrom', 'env', 'command', 'args', 'startupProbe', 'readinessProbe', 'livenessProbe', 'lifecycle'))
                and event_load(revision, 'hpa.json') == event_load(candidate, 'hpa.json')
                and config == event_load(candidate, 'configmap.json')
                and event_load(revision, 'service.json') == event_load(candidate, 'service.json'))
    except (OSError, ValueError, KeyError, TypeError, binascii.Error):
        return False


try:
    secret = event_load(target, 'secret-env.json')['data']
    use_reader = event_enabled(secret)
    config = event_load(target, 'configmap.json')
    deployment = event_load(target, 'deployment.json')
    pod = deployment['spec']['template']['spec']
    container = pod['containers'][0]
    # Relaxed binding, JSON và JVM args không được ghi đè flag của Secret revision.
    entries = [*config.get('data', {}).items(), *((v.get('name', ''), v.get('value', '')) for v in container.get('env', []))]
    for key, value in entries:
        normalized = re.sub('[^a-z0-9]', '', key.lower())
        if normalized.startswith('eventreadreplica'):
            sys.exit('INVALID_EVENT_READER_FLAG_SOURCE')
        if normalized == 'springapplicationjson':
            pending = [('', json.loads(value))]
            while pending:
                parent, node = pending.pop()
                if isinstance(node, dict):
                    for name, child in node.items():
                        joined = parent + re.sub('[^a-z0-9]', '', name.lower())
                        if joined.startswith('eventreadreplica'):
                            sys.exit('INVALID_EVENT_READER_FLAG_SOURCE')
                        pending.append((joined, child))
        if key in ('JAVA_TOOL_OPTIONS', '_JAVA_OPTIONS', 'JDK_JAVA_OPTIONS', 'JAVA_OPTS', 'EVENT_JAVA_OPTIONS') and 'eventreadreplica' in re.sub('[^a-z0-9]', '', value.lower()):
            sys.exit('INVALID_EVENT_READER_FLAG_SOURCE')
    if any('eventreadreplica' in re.sub('[^a-z0-9]', '', value.lower()) for value in [*container.get('command', []), *container.get('args', [])]):
        sys.exit('INVALID_EVENT_READER_FLAG_SOURCE')
    expected_sources = [{'configMapRef': {'name': 'event-service-config'}}, {'secretRef': {'name': 'event-service-secret'}}]
    if container.get('envFrom') != expected_sources:
        sys.exit('INVALID_EVENT_READER_FLAG_SOURCE')
    hpa_doc = event_load(target, 'hpa.json')
    hpa = hpa_doc['spec']
    affinity = pod.get('affinity', {})
    legacy = mode == 'rollback' and not use_reader and hpa.get('minReplicas') == 1 and hpa.get('maxReplicas') == 2 and not affinity.get('podAntiAffinity')
    if (target / 'pdb.json').exists() or (target / 'event-pdb-live.json').read_text().strip():
        sys.exit('EVENT_UNMANAGED_PDB_PRESENT')
    if not legacy:
        selector = {'matchLabels': {'app.kubernetes.io/name': 'event-service'}}
        rules = affinity.get('podAntiAffinity', {}).get('requiredDuringSchedulingIgnoredDuringExecution', [])
        application = json.loads(config.get('data', {}).get('SPRING_APPLICATION_JSON', '{}'))
        groups = application.get('management', {}).get('endpoint', {}).get('health', {}).get('group', {})
        if (deployment.get('kind') != 'Deployment' or deployment['metadata'].get('name') != 'event-service'
                or deployment['metadata'].get('namespace') != 'flash-ticket' or deployment['spec'].get('selector') != selector
                or deployment['spec']['template']['metadata'].get('labels', {}).get('app.kubernetes.io/name') != 'event-service'
                or hpa.get('minReplicas') != 1 or hpa.get('maxReplicas') != 1
                or hpa_doc['metadata'].get('name') != 'event-service' or hpa_doc['metadata'].get('namespace') != 'flash-ticket'
                or hpa.get('scaleTargetRef') != {'apiVersion': 'apps/v1', 'kind': 'Deployment', 'name': 'event-service'}
                or pod.get('nodeName') or pod.get('nodeSelector') or 'nodeAffinity' in affinity
                or deployment['spec'].get('strategy') != {'type': 'RollingUpdate', 'rollingUpdate': {'maxUnavailable': 0, 'maxSurge': 1}}
                or deployment['spec'].get('minReadySeconds') != 75 or pod.get('terminationGracePeriodSeconds') != 180
                or groups.get('readiness', {}).get('include') != ['readinessState', 'db']
                or groups.get('liveness', {}).get('include') != ['livenessState']
                or not any(r.get('topologyKey') == 'kubernetes.io/hostname' and r.get('labelSelector') == selector and r.get('namespaces') == ['flash-ticket'] and not r.get('namespaceSelector') and not r.get('matchLabelKeys') for r in rules)):
            sys.exit('INVALID_EVENT_RECOVERY_PROFILE')
        live = (target / 'event-deployment-live.json').read_text().strip()
        if live:
            live_pod = json.loads(live)['spec']['template']['spec']
            if live_pod.get('nodeName') or live_pod.get('nodeSelector') or live_pod.get('affinity', {}).get('nodeAffinity'):
                sys.exit('EVENT_NODE_PINNING_PRESENT')
    if mode == 'rollback' and use_reader:
        sys.exit('EVENT_ROLLBACK_REQUIRES_WRITER_REVISION')
    if use_reader:
        keys = ('URL', 'USERNAME', 'PASSWORD', 'PROBE_INTERVAL', 'MAX_SAMPLE_AGE', 'PROBE_QUERY_TIMEOUT', 'TRANSACTION_TIMEOUT')
        if any(not secret.get(prefix + key) for key in keys):
            sys.exit('MISSING_EVENT_READER_KEYS')
        for key in ('PROBE_INTERVAL', 'MAX_SAMPLE_AGE'):
            value = base64.b64decode(secret[prefix + key], validate=True).decode('utf-8')
            if not re.fullmatch(r'(?:[1-9][0-9]*(?:ms|s|m|h)|PT[1-9][0-9]*[SMH])', value):
                sys.exit('INVALID_EVENT_READER_TIMING')
        for key in ('PROBE_QUERY_TIMEOUT', 'TRANSACTION_TIMEOUT'):
            value = base64.b64decode(secret[prefix + key], validate=True).decode('utf-8')
            if not re.fullmatch(r'[1-9][0-9]*', value):
                sys.exit('INVALID_EVENT_READER_TIMING')
        jdbc = base64.b64decode(secret[prefix + 'URL'], validate=True).decode('utf-8')
        try:
            url = urllib.parse.urlsplit(jdbc.removeprefix('jdbc:'))
            params = urllib.parse.parse_qsl(url.query, strict_parsing=True, keep_blank_values=True)
            required = {'sslmode': 'require', 'targetServerType': 'secondary', 'connectTimeout': '5', 'socketTimeout': '5'}
            if (not jdbc.startswith('jdbc:postgresql://') or re.search(r'[\s\x00-\x1f#]', jdbc)
                    or not re.fullmatch(r'[A-Za-z0-9](?:[A-Za-z0-9.-]*[A-Za-z0-9])?:5432', url.netloc)
                    or url.path != '/event_db' or len(params) != len(required) or dict(params) != required):
                raise ValueError
        except ValueError:
            sys.exit('INVALID_EVENT_READER_URL')
        if not current:
            sys.exit('EVENT_WRITER_REVISION_REQUIRED')
        active = target.parent / current
        baseline = current
        if event_enabled(event_load(active, 'secret-env.json')['data']):
            if not (active / 'writer-baseline').is_file():
                sys.exit('EVENT_WRITER_REVISION_REQUIRED')
            baseline = (active / 'writer-baseline').read_text().strip()
        if not re.fullmatch(r'[0-9]+-[a-f0-9]{12}-[0-9]+', baseline) or not event_writer_revision(target.parent / baseline, target):
            sys.exit('EVENT_WRITER_REVISION_REQUIRED')
        (target / 'writer-baseline').write_text(baseline + '\n')
    if current and not use_reader and event_enabled(event_load(target.parent / current, 'secret-env.json')['data']):
        active = target.parent / current
        if not event_writer_revision(target, active):
            sys.exit('EVENT_WRITER_REVISION_REQUIRED')
        baseline = target.name if mode == 'rollback' else (active / 'writer-baseline').read_text().strip()
        if not re.fullmatch(r'[0-9]+-[a-f0-9]{12}-[0-9]+', baseline) or not event_writer_revision(target.parent / baseline, target):
            sys.exit('EVENT_WRITER_REVISION_REQUIRED')
        (target / 'writer-baseline').write_text(baseline + '\n')
    if (target / 'writer-baseline').is_file():
        baseline = (target / 'writer-baseline').read_text().strip()
        if not re.fullmatch(r'[0-9]+-[a-f0-9]{12}-[0-9]+', baseline) or not event_writer_revision(target.parent / baseline, target):
            sys.exit('EVENT_WRITER_REVISION_REQUIRED')
except (OSError, ValueError, KeyError, TypeError, binascii.Error):
    sys.exit('INVALID_EVENT_READER_CONFIG')
PY
  if [[ -f $target/writer-baseline ]]; then previous=$(cat "$target/writer-baseline"); fi
fi
if [[ $service == ticket-service ]]; then
  # Snapshot QR/business phải còn đủ để rollback; không dùng profile/PDB của Booking.
  kubectl -n "$namespace" get pdb "$service" --ignore-not-found -o json > "$target/ticket-pdb-live.json"
  python3 - "$target" "$previous" "$mode" <<'PY'
import base64, binascii, copy, json, pathlib, re, sys, urllib.parse
from decimal import Decimal

target = pathlib.Path(sys.argv[1])
current, mode = sys.argv[2:]
prefix = 'TICKET_LIST_REPLICA_'
reader_keys = {prefix + k for k in ('ENABLED', 'JDBC_URL', 'USERNAME', 'PASSWORD', 'PROBE_INTERVAL', 'PROBE_QUERY_TIMEOUT', 'MAX_SAMPLE_AGE', 'READER_TRANSACTION_TIMEOUT')}


def ticket_load(revision, file):
    return json.loads((revision / file).read_text(encoding='utf-8'))


def ticket_enabled(secret):
    setting = base64.b64decode(secret.get(prefix + 'ENABLED', 'ZmFsc2U='), validate=True)
    if setting not in (b'true', b'false'):
        sys.exit('INVALID_TICKET_READER_FLAG')
    return setting == b'true'


def ticket_semantic(document, kind):
    result = copy.deepcopy(document)
    result.pop('status', None)
    metadata = result.get('metadata', {})
    for key in ('uid', 'resourceVersion', 'creationTimestamp', 'generation', 'managedFields', 'selfLink'):
        metadata.pop(key, None)
    metadata.get('annotations', {}).pop('kubectl.kubernetes.io/last-applied-configuration', None)
    if not metadata.get('annotations'):
        metadata.pop('annotations', None)
    if kind == 'deployment.json':
        result['spec']['template']['metadata'].get('annotations', {}).pop('flash-ticket/release', None)
        if not result['spec']['template']['metadata'].get('annotations'):
            result['spec']['template']['metadata'].pop('annotations', None)
    if kind == 'secret-env.json':
        result['data'] = {k: v for k, v in result['data'].items() if k not in reader_keys}
    return result


def ticket_profile(revision):
    deployment = ticket_load(revision, 'deployment.json')
    pod = deployment['spec']['template']['spec']
    container = pod['containers'][0]
    hpa_doc = ticket_load(revision, 'hpa.json')
    hpa = hpa_doc['spec']
    config = ticket_load(revision, 'configmap.json')['data']
    groups = json.loads(config.get('SPRING_APPLICATION_JSON', '{}')).get('management', {}).get('endpoint', {}).get('health', {}).get('group', {})
    selector = {'matchLabels': {'app.kubernetes.io/name': 'ticket-service'}}
    rule = {'labelSelector': selector, 'namespaces': ['flash-ticket'], 'topologyKey': 'kubernetes.io/hostname'}
    return (deployment.get('kind') == 'Deployment' and deployment['metadata'].get('name') == 'ticket-service'
            and deployment['metadata'].get('namespace') == 'flash-ticket' and deployment['spec'].get('selector') == selector
            and deployment['spec']['template']['metadata'].get('labels', {}).get('app.kubernetes.io/name') == 'ticket-service'
            and deployment['spec'].get('strategy') == {'type': 'RollingUpdate', 'rollingUpdate': {'maxUnavailable': 0, 'maxSurge': 1}}
            and deployment['spec'].get('minReadySeconds') == 75 and pod.get('terminationGracePeriodSeconds') == 180
            and not pod.get('nodeName') and not pod.get('nodeSelector') and 'nodeAffinity' not in pod.get('affinity', {})
            and pod.get('affinity', {}).get('podAntiAffinity', {}).get('requiredDuringSchedulingIgnoredDuringExecution') == [rule]
            and hpa_doc.get('kind') == 'HorizontalPodAutoscaler' and hpa_doc['metadata'].get('name') == 'ticket-service'
            and hpa_doc['metadata'].get('namespace') == 'flash-ticket' and hpa.get('minReplicas') == hpa.get('maxReplicas') == 1
            and hpa.get('scaleTargetRef') == {'apiVersion': 'apps/v1', 'kind': 'Deployment', 'name': 'ticket-service'}
            and groups.get('readiness', {}).get('include') == ['readinessState', 'db']
            and groups.get('liveness', {}).get('include') == ['livenessState']
            and container.get('readinessProbe', {}).get('httpGet', {}).get('path') == '/actuator/health/readiness'
            and container.get('livenessProbe', {}).get('httpGet', {}).get('path') == '/actuator/health/liveness'
            and not (revision / 'pdb.json').exists())


def ticket_writer_revision(revision, candidate):
    try:
        files = ('image', 'deployment.json', 'configmap.json', 'hpa.json', 'secret-env.json', 'secret-qr.json')
        if not all((r / file).is_file() for r in (revision, candidate) for file in files):
            return False
        if ticket_enabled(ticket_load(revision, 'secret-env.json')['data']) or not ticket_profile(revision) or not ticket_profile(candidate):
            return False
        image = (revision / 'image').read_text().strip()
        if image != (candidate / 'image').read_text().strip():
            return False
        for r in (revision, candidate):
            deployment = ticket_load(r, 'deployment.json')
            if deployment['spec']['template']['metadata']['annotations'].get('flash-ticket/release') != r.name or deployment['spec']['template']['spec']['containers'][0]['image'] != image:
                return False
        for file in (*files[1:], 'service.json'):
            if (revision / file).exists() != (candidate / file).exists():
                return False
            if (revision / file).exists() and ticket_semantic(ticket_load(revision, file), file) != ticket_semantic(ticket_load(candidate, file), file):
                return False
        return True
    except (OSError, ValueError, KeyError, TypeError, binascii.Error):
        return False


def ticket_seconds(value):
    match = re.fullmatch(r'(?:PT([0-9]+(?:\.[0-9]+)?)([SMH])|([0-9]+(?:\.[0-9]+)?)(ms|s|m|h))', value)
    if not match:
        return Decimal(0)
    number, unit = (match[1], match[2]) if match[1] is not None else (match[3], match[4])
    return Decimal(number) * {'ms': Decimal('.001'), 's': 1, 'S': 1, 'm': 60, 'M': 60, 'h': 3600, 'H': 3600}[unit]


try:
    secret = ticket_load(target, 'secret-env.json')['data']
    use_reader = ticket_enabled(secret)
    config = ticket_load(target, 'configmap.json')['data']
    deployment = ticket_load(target, 'deployment.json')
    pod = deployment['spec']['template']['spec']
    container = pod['containers'][0]
    # Relaxed Spring binding/JSON/JVM không được mở nguồn reader thứ hai.
    entries = [*config.items(), *((v.get('name', ''), v.get('value', '')) for v in container.get('env', [])), *secret.items()]
    for key, value in entries:
        normalized = re.sub('[^a-z0-9]', '', key.lower())
        if normalized in ('springconfiglocation', 'springconfigadditionallocation', 'springconfigimport', 'springprofilesactive', 'springprofilesinclude', 'springprofilesdefault'):
            sys.exit('INVALID_TICKET_READER_FLAG_SOURCE')
        if use_reader and normalized in ('ticketbusinessenabled', 'ticketfeaturesbusinessenabled') and value != 'true':
            sys.exit('TICKET_READER_REQUIRES_BUSINESS')
        if normalized.startswith('ticketlistreplica'):
            if key not in secret or key not in reader_keys or key in config or any(v.get('name') == key for v in container.get('env', [])):
                sys.exit('INVALID_TICKET_READER_FLAG_SOURCE')
        if normalized == 'springapplicationjson':
            pending = [('', json.loads(value))]
            while pending:
                parent, node = pending.pop()
                if isinstance(node, dict):
                    for name, child in node.items():
                        joined = parent + re.sub('[^a-z0-9]', '', name.lower())
                        if joined.startswith('ticketlistreplica') or joined.startswith('springconfig') or joined.startswith('springprofiles'):
                            sys.exit('INVALID_TICKET_READER_FLAG_SOURCE')
                        if use_reader and joined == 'ticketfeaturesbusinessenabled' and child not in (True, 'true'):
                            sys.exit('TICKET_READER_REQUIRES_BUSINESS')
                        pending.append((joined, child))
        if key in ('JAVA_TOOL_OPTIONS', '_JAVA_OPTIONS', 'JDK_JAVA_OPTIONS', 'JAVA_OPTS', 'TICKET_JAVA_OPTIONS') and any(token in re.sub('[^a-z0-9]', '', value.lower()) for token in ('ticketlistreplica', 'springconfig', 'springprofiles')):
            sys.exit('INVALID_TICKET_READER_FLAG_SOURCE')
    if any(token in re.sub('[^a-z0-9]', '', value.lower()) for value in [*container.get('command', []), *container.get('args', [])] for token in ('ticketlistreplica', 'springconfig', 'springprofiles')):
        sys.exit('INVALID_TICKET_READER_FLAG_SOURCE')
    if container.get('envFrom') != [{'configMapRef': {'name': 'ticket-service-config'}}, {'secretRef': {'name': 'ticket-service-secret'}}]:
        sys.exit('INVALID_TICKET_READER_FLAG_SOURCE')
    hpa = ticket_load(target, 'hpa.json')['spec']
    legacy = (mode == 'rollback' and not use_reader and hpa.get('minReplicas') == 1 and hpa.get('maxReplicas') == 2
              and not pod.get('affinity', {}).get('podAntiAffinity'))
    if (target / 'pdb.json').exists() or (target / 'ticket-pdb-live.json').read_text().strip():
        sys.exit('TICKET_UNMANAGED_PDB_PRESENT')
    if not legacy and not ticket_profile(target):
        sys.exit('INVALID_TICKET_RECOVERY_PROFILE')
    if mode == 'rollback' and use_reader:
        sys.exit('TICKET_ROLLBACK_REQUIRES_WRITER_REVISION')
    if use_reader:
        if any(not secret.get(key) for key in reader_keys):
            sys.exit('MISSING_TICKET_READER_KEYS')
        values = {k.removeprefix(prefix): base64.b64decode(secret[k], validate=True).decode('utf-8') for k in reader_keys}
        if values['USERNAME'] != 'ticket_reader':
            sys.exit('INVALID_TICKET_READER_ROLE')
        if config.get('TICKET_BUSINESS_ENABLED') != 'true':
            sys.exit('TICKET_READER_REQUIRES_BUSINESS')
        for name, ceiling in (('PROBE_INTERVAL', 1), ('MAX_SAMPLE_AGE', 2)):
            if not 0 < ticket_seconds(values[name]) <= ceiling:
                sys.exit('INVALID_TICKET_READER_TIMING')
        for name, ceiling in (('PROBE_QUERY_TIMEOUT', 1), ('READER_TRANSACTION_TIMEOUT', 5)):
            if not re.fullmatch('[1-9][0-9]*', values[name]) or int(values[name]) > ceiling:
                sys.exit('INVALID_TICKET_READER_TIMING')
        jdbc = values['JDBC_URL']
        url = urllib.parse.urlsplit(jdbc.removeprefix('jdbc:'))
        params = urllib.parse.parse_qsl(url.query, strict_parsing=True, keep_blank_values=True)
        writer = urllib.parse.urlsplit(config.get('TICKET_DB_URL', '').removeprefix('jdbc:'))
        if (not jdbc.startswith('jdbc:postgresql://') or re.search(r'[\s\x00-\x1f#]', jdbc)
                or not re.fullmatch(r'[A-Za-z0-9](?:[A-Za-z0-9.-]*[A-Za-z0-9])?:5432', url.netloc)
                or url.path != '/ticket_db' or url.hostname == writer.hostname
                or len(params) != 2 or dict(params) != {'sslmode': 'require', 'targetServerType': 'secondary'}):
            sys.exit('INVALID_TICKET_READER_URL')
        if not current:
            sys.exit('TICKET_WRITER_REVISION_REQUIRED')
        active = target.parent / current
        baseline = current
        if not (active / 'secret-env.json').is_file():
            sys.exit('TICKET_WRITER_REVISION_REQUIRED')
        if ticket_enabled(ticket_load(active, 'secret-env.json')['data']):
            if not (active / 'writer-baseline').is_file():
                sys.exit('TICKET_WRITER_REVISION_REQUIRED')
            baseline = (active / 'writer-baseline').read_text().strip()
        if not re.fullmatch(r'[0-9]+-[a-f0-9]{12}-[0-9]+', baseline) or not ticket_writer_revision(target.parent / baseline, target):
            sys.exit('TICKET_WRITER_REVISION_REQUIRED')
        (target / 'writer-baseline').write_text(baseline + '\n')
    if current and not use_reader and not legacy and ticket_enabled(ticket_load(target.parent / current, 'secret-env.json')['data']):
        active = target.parent / current
        if not ticket_writer_revision(target, active):
            sys.exit('TICKET_WRITER_REVISION_REQUIRED')
        baseline = target.name if mode == 'rollback' else (active / 'writer-baseline').read_text().strip()
        if not re.fullmatch(r'[0-9]+-[a-f0-9]{12}-[0-9]+', baseline) or not ticket_writer_revision(target.parent / baseline, target):
            sys.exit('TICKET_WRITER_REVISION_REQUIRED')
        (target / 'writer-baseline').write_text(baseline + '\n')
    if not legacy and (target / 'writer-baseline').is_file():
        baseline = (target / 'writer-baseline').read_text().strip()
        if not re.fullmatch(r'[0-9]+-[a-f0-9]{12}-[0-9]+', baseline) or not ticket_writer_revision(target.parent / baseline, target):
            sys.exit('TICKET_WRITER_REVISION_REQUIRED')
except (OSError, ValueError, KeyError, TypeError, binascii.Error):
    sys.exit('INVALID_TICKET_READER_CONFIG')
PY
  if [[ -f $target/writer-baseline ]]; then previous=$(cat "$target/writer-baseline"); fi
fi
if apply_revision "$target" && check_revision "$target"; then
  if [[ -f $target/legacy-deployment.json && ! -f $target/migration-finished ]]; then
    python3 "$source_root/deploy/scripts/migrate-k8s-discovery.py" finish "$target" || die DISCOVERY_MIGRATION_FINALIZE_FAILED
    previous=$(cat "$target/compatible-previous")
  fi
  printf '%s\n' "$revision" > "$revision_root/current.tmp"
  mv -f "$revision_root/current.tmp" "$revision_root/current"
  if [[ $service == ticket-service && -n $previous ]]; then
    printf '%s\n' "$previous" > "$revision_root/previous.tmp"
    mv -f "$revision_root/previous.tmp" "$revision_root/previous"
  elif [[ -n $previous && $previous != "$revision" ]]; then
    printf '%s\n' "$previous" > "$revision_root/previous"
  fi
  printf '%s\n' "$image" > "$state/$service.current.tmp"
  mv -f "$state/$service.current.tmp" "$state/$service.current"
  echo "DEPLOY_OK $service $revision"
else
  echo "DEPLOY_FAILED $service $revision" >&2
  if [[ -f $target/bridge-drained.json ]]; then
    python3 "$source_root/deploy/scripts/migrate-k8s-discovery.py" unbridge "$target" || die LEGACY_APPLICATION_UNDRAIN_FAILED
  fi
  if [[ -f $target/legacy-deployment.json && ! -f $target/migration-finished ]]; then
    if python3 "$source_root/deploy/scripts/migrate-k8s-discovery.py" restore "$target"; then
      echo 'RESTORED_LEGACY_DISCOVERY' >&2
    else
      echo 'DISCOVERY_RESTORE_FAILED: both workloads retained for operator recovery' >&2
    fi
    exit 1
  fi
  if [[ -n $previous && $revision_root/$previous != "$target" ]]; then
    if apply_revision "$revision_root/$previous" && check_revision "$revision_root/$previous"; then
      if [[ $service == booking-service || $service == event-service || $service == ticket-service ]]; then
        printf '%s\n' "$previous" > "$revision_root/current.tmp"
        mv -f "$revision_root/current.tmp" "$revision_root/current"
        restored_image=$(cat "$revision_root/$previous/image")
        printf '%s\n' "$restored_image" > "$state/$service.current.tmp"
        mv -f "$state/$service.current.tmp" "$state/$service.current"
        if [[ $service == ticket-service ]]; then
          printf '%s\n' "$previous" > "$revision_root/previous.tmp"
          mv -f "$revision_root/previous.tmp" "$revision_root/previous"
        else
          printf '%s\n' "$previous" > "$revision_root/previous"
        fi
      fi
      echo "RESTORED_PREVIOUS $service $previous" >&2
    else
      echo "RESTORE_FAILED $service: operator intervention required" >&2
    fi
  elif [[ $service == assistant-service ]]; then
    echo 'NO_PREVIOUS_K8S_REVISION: inspect the Assistant bootstrap; no Compose fallback' >&2
  else
    echo 'NO_PREVIOUS_K8S_REVISION: follow the Compose cutover rollback runbook' >&2
  fi
  exit 1
fi
