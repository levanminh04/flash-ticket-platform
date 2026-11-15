#!/usr/bin/env python3
"""Migrate discovery peers and drain legacy application Pods under deploy.lock on 84.

Snapshots contain only Eureka resources. Do not use this to downgrade the cluster
to standalone after migration; normal image rollback retains the peer topology.
"""
import copy
import json
import os
from pathlib import Path
import re
import subprocess
import sys
import time
import urllib.request

NAMESPACE = 'flash-ticket'
APPS = {'api-gateway', 'booking-service', 'payment-service', 'ticket-service', 'event-service', 'user-service'}
GENERATION = 'flash-ticket/discovery-generation'
GATE = 'flash-ticket.io/legacy-drained'
SERVING = 'flash-ticket/gateway-serving'


def kube(*args):
    return subprocess.check_output(['kubectl', '-n', NAMESPACE, *args], text=True, timeout=650)


def read(path):
    return json.loads(path.read_text())


def write(path, value):
    path.write_text(json.dumps(value))
    path.chmod(0o600)


def apply(root, name, document):
    path = root / name
    write(path, document)
    kube('apply', '-f', str(path))


def clean(document):
    result = copy.deepcopy(document)
    for key in ('uid', 'resourceVersion', 'creationTimestamp', 'generation', 'managedFields'):
        result['metadata'].pop(key, None)
    result['metadata'].get('annotations', {}).pop('kubectl.kubernetes.io/last-applied-configuration', None)
    result.pop('status', None)
    return result


def pods():
    return json.loads(kube('get', 'pods', '-o', 'json'))['items']


def registry(address):
    request = urllib.request.Request(address + '/eureka/apps', headers={'Accept': 'application/json'})
    with urllib.request.urlopen(request, timeout=4) as response:
        applications = json.load(response)['applications'].get('application', [])
    if isinstance(applications, dict):
        applications = [applications]
    result = {}
    for app in applications:
        instances = app.get('instance', [])
        if isinstance(instances, dict):
            instances = [instances]
        for instance in instances:
            result[instance['instanceId']] = instance
    return result


def expected_instances(items, require_all=False):
    expected = {}
    for pod in items:
        name = pod['metadata'].get('labels', {}).get('app.kubernetes.io/name')
        if name not in APPS or pod['metadata'].get('deletionTimestamp'):
            continue
        statuses = pod['status'].get('containerStatuses', [])
        if not statuses or not all(s['ready'] for s in statuses):
            continue
        expected[name + ':' + pod['metadata']['uid']] = (
            pod['status']['podIP'], pod['spec']['containers'][0]['ports'][0]['containerPort'])
    if require_all:
        assert {key.split(':', 1)[0] for key in expected} == APPS, 'APPLICATION_BASELINE_NOT_READY'
    return expected


def contains(actual, expected):
    return all(key in actual and actual[key].get('status') == 'UP'
               and actual[key].get('ipAddr') == ip
               and int(actual[key]['port']['$']) == port
               for key, (ip, port) in expected.items())


def check(root):
    items = pods()
    peers = [p for p in items if p['metadata'].get('labels', {}).get(GENERATION) == 'peers'
             and not p['metadata'].get('deletionTimestamp')]
    expected = expected_instances(items)
    diagnostics = {'expectedCount': len(expected), 'peers': []}
    errors = []
    if len(peers) != 2:
        errors.append('TWO_PEERS_REQUIRED')
    for peer in peers:
        statuses = peer['status'].get('containerStatuses', [])
        row = {'name': peer['metadata']['name'], 'ready': bool(statuses) and all(s['ready'] for s in statuses),
               'restarts': sum(s['restartCount'] for s in statuses)}
        if not row['ready'] or row['restarts']:
            errors.append('PEER_NOT_STABLE')
        address = 'http://' + peer['status']['podIP'] + ':8761'
        try:
            actual = registry(address)
            row['matching'] = [key for key in expected if contains(actual, {key: expected[key]})]
            row['missingOrDifferent'] = [key for key in expected if key not in row['matching']]
            if row['missingOrDifferent']:
                errors.append('PEER_REGISTRY_INCOMPLETE')
        except (OSError, ValueError, KeyError) as error:
            row['queryError'] = type(error).__name__
            errors.append('PEER_REGISTRY_QUERY_FAILED')
        diagnostics['peers'].append(row)
    write(root / 'peer-sync-diagnostics.json', diagnostics)
    assert not errors, ','.join(sorted(set(errors)))


def wait_registry(root):
    # Require two consecutive comparisons; never switch the client Service to an empty registry.
    successes = 0
    last_error = ''
    for _ in range(60):
        try:
            check(root)
            successes += 1
            if successes == 2:
                return
        except (AssertionError, OSError, ValueError, KeyError) as error:
            successes = 0
            last_error = str(error)
        time.sleep(5)
    raise RuntimeError('PEER_SYNC_TIMEOUT ' + last_error)


def migrate(root):
    # If a failed attempt remains, restore it before starting another migration.
    assert not (root / 'legacy-deployment.json').exists(), 'MIGRATION_ALREADY_STARTED'
    legacy = read(root / 'legacy-live.json')
    assert legacy.get('kind') == 'Deployment', 'LEGACY_DEPLOYMENT_REQUIRED'
    assert legacy['spec'].get('replicas', 1) == 1, 'LEGACY_SCALE_UNEXPECTED'
    live_pods = pods()
    expected_instances(live_pods, require_all=True)
    old_pods = [p for p in live_pods if p['metadata'].get('labels', {}).get('app.kubernetes.io/name') == 'discovery-server'
                and p['metadata'].get('labels', {}).get(GENERATION) != 'peers'
                and not p['metadata'].get('deletionTimestamp')]
    assert len(old_pods) == 1, 'ONE_LEGACY_POD_REQUIRED'
    # No cluster mutation before snapshots and capacity checks are complete.
    available = int(next(line.split()[1] for line in Path('/proc/meminfo').read_text().splitlines()
                         if line.startswith('MemAvailable:'))) // 1024
    assert available >= 1280, 'MIGRATION_MEMORY_HEADROOM_TOO_LOW'
    write(root / 'legacy-service.json', json.loads(kube('get', 'service', 'discovery-server', '-o', 'json')))
    write(root / 'legacy-configmap.json', json.loads(kube('get', 'configmap', 'discovery-server-config', '-o', 'json')))
    write(root / 'legacy-deployment.json', legacy)
    kube('label', 'pod', old_pods[0]['metadata']['name'], GENERATION + '=legacy', '--overwrite')
    service = clean(read(root / 'legacy-service.json'))
    service['spec']['selector'][GENERATION] = 'legacy'
    apply(root, 'legacy-client-service.json', service)
    legacy_service = copy.deepcopy(service)
    legacy_service['metadata']['name'] = 'discovery-server-legacy'
    for key in ('clusterIP', 'clusterIPs', 'ipFamilies', 'ipFamilyPolicy'):
        legacy_service['spec'].pop(key, None)
    apply(root, 'legacy-bootstrap-service.json', legacy_service)
    kube('apply', '-f', str(root / 'peer-service.json'))
    kube('apply', '-f', str(root / 'configmap.json'))
    config = json.loads(read(root / 'configmap.json')['data']['SPRING_APPLICATION_JSON'])
    # Bootstrap từ nguồn đã có registry; không chọn ngẫu nhiên một peer mới còn rỗng.
    config['eureka']['client']['service-url']['defaultZone'] = 'http://discovery-server-legacy:8761/eureka/'
    bootstrap = read(root / 'statefulset.json')
    bootstrap['spec']['template']['spec']['containers'][0]['env'].append(
        {'name': 'SPRING_APPLICATION_JSON', 'value': json.dumps(config, separators=(',', ':'))})
    apply(root, 'bootstrap-statefulset.json', bootstrap)
    kube('rollout', 'status', 'statefulset/discovery-server', '--timeout=600s')
    wait_registry(root)
    # Giữ client trên legacy trong cả lượt bỏ URL bootstrap; chỉ cutover cấu hình cuối đã kiểm.
    kube('apply', '-f', str(root / 'statefulset.json'))
    kube('rollout', 'status', 'statefulset/discovery-server', '--timeout=600s')
    wait_registry(root)
    kube('apply', '-f', str(root / 'service.json'))
    write(root / 'client-cutover.json', {'completed': True})
    print('DISCOVERY_PEERS_SYNCED: legacy retained until final release check')


def finish(root):
    if (root / 'migration-finished').exists():
        return
    check(root)
    # Legacy images lack curl/jq. Preserve their raw backup, but establish a valid
    # peer baseline using the current tooling image (no Java behavior changed).
    baseline_image = read(root / 'statefulset.json')['spec']['template']['spec']['containers'][0]['image']
    assert re.fullmatch(r'ghcr.io/levanminh04/flash-ticket-platform/discovery-server@sha256:[a-f0-9]{64}', baseline_image), 'INVALID_BASELINE_IMAGE'
    if (root / 'compatible-previous').exists():
        name = (root / 'compatible-previous').read_text().strip()
        assert re.fullmatch(r'[0-9]+-[a-f0-9]{12}-[0-9]+', name), 'INVALID_BASELINE_REVISION'
        assert (root.parent / name / 'image').read_text().strip() == baseline_image, 'BASELINE_IMAGE_MISMATCH'
    else:
        name = str(int(time.time())) + '-' + baseline_image.rsplit(':', 1)[-1][:12] + '-' + str(os.getpid())
        baseline = root.parent / name
        baseline.mkdir(mode=0o700)
        for resource in ('configmap', 'peer-service', 'service', 'statefulset'):
            document = read(root / (resource + '.json'))
            if resource == 'statefulset':
                document['spec']['template']['spec']['containers'][0]['image'] = baseline_image
                document['spec']['template']['metadata'].setdefault('annotations', {})['flash-ticket/release'] = name
            write(baseline / (resource + '.json'), document)
        (baseline / 'image').write_text(baseline_image + '\n')
        (root / 'compatible-previous').write_text(name + '\n')
    kube('delete', 'deployment', 'discovery-server', '--ignore-not-found', '--wait=true', '--timeout=120s')
    kube('delete', 'service', 'discovery-server-legacy', '--ignore-not-found')
    (root / 'migration-finished').touch(mode=0o600)
    print('DISCOVERY_MIGRATION_FINISHED: two peers, valid peer baseline retained')


def restore(root):
    assert not (root / 'migration-finished').exists(), 'USE_PEER_IMAGE_ROLLBACK_AFTER_MIGRATION'
    legacy = read(root / 'legacy-deployment.json')
    old_pods = [p for p in pods() if p['metadata'].get('labels', {}).get(GENERATION) == 'legacy'
                and not p['metadata'].get('deletionTimestamp')]
    assert len(old_pods) == 1, 'LEGACY_POD_NOT_AVAILABLE_KEEP_PEERS'
    address = 'http://' + old_pods[0]['status']['podIP'] + ':8761'
    expected = expected_instances(pods())
    # Re-register currently live records if needed before restoring the old Service selector.
    actual = registry(address)
    if not contains(actual, expected):
        candidates = [p for p in pods() if p['metadata'].get('labels', {}).get(GENERATION) == 'peers'
                      and p['status'].get('podIP') and not p['metadata'].get('deletionTimestamp')]
        for candidate in candidates:
            try:
                source = registry('http://' + candidate['status']['podIP'] + ':8761')
                if not contains(source, expected):
                    continue
                for key in expected:
                    instance = source[key]
                    request = urllib.request.Request(address + '/eureka/apps/' + instance['app'],
                        data=json.dumps({'instance': instance}).encode(),
                        headers={'Content-Type': 'application/json'}, method='POST')
                    with urllib.request.urlopen(request, timeout=4) as response:
                        assert response.status == 204, 'LEGACY_REGISTRATION_FAILED'
                break
            except (OSError, ValueError, KeyError):
                continue
    for _ in range(15):
        if contains(registry(address), expected):
            break
        time.sleep(5)  # The old standalone server may still have its read-only response cache.
    else:
        raise AssertionError('LEGACY_REGISTRY_INCOMPLETE_KEEP_PEERS')
    # Apply the saved resources only once the old server is proven able to serve again.
    apply(root, 'restore-legacy-configmap.json', clean(read(root / 'legacy-configmap.json')))
    # Keep traffic restricted to the verified old Pod until all peer Pods are gone.
    apply(root, 'restore-restricted-service.json', clean(read(root / 'legacy-client-service.json')))
    kube('delete', 'statefulset', 'discovery-server', '--ignore-not-found', '--wait=true', '--timeout=120s')
    kube('delete', 'service', 'discovery-server-peers', 'discovery-server-legacy', '--ignore-not-found')
    apply(root, 'restore-legacy-service.json', clean(read(root / 'legacy-service.json')))
    # The legacy Deployment was never changed or deleted before finish().
    assert legacy['kind'] == 'Deployment'
    (root / 'migration-restored').touch(mode=0o600)
    print('LEGACY_DISCOVERY_RESTORED')


def recover(root):
    """Unstick StatefulSet rollback after the previous template has been applied."""
    expected = read(root / 'statefulset.json')['spec']['template']['spec']['containers'][0]['image']
    items = [p for p in pods() if p['metadata'].get('labels', {}).get(GENERATION) == 'peers'
             and not p['metadata'].get('deletionTimestamp')]
    for pod in items:
        statuses = pod['status'].get('containerStatuses', [])
        if statuses and all(s['ready'] for s in statuses):
            continue
        if pod['spec']['containers'][0]['image'] == expected:
            continue
        # A Ready peer must remain; never delete healthy old-version peers to accelerate rollout.
        others = [p for p in items if p['metadata']['uid'] != pod['metadata']['uid']]
        assert any(p['status'].get('containerStatuses') and all(s['ready'] for s in p['status']['containerStatuses']) for p in others), 'NO_READY_PEER_FOR_ROLLBACK'
        assert any(owner.get('kind') == 'StatefulSet' and owner.get('name') == 'discovery-server'
                   for owner in pod['metadata'].get('ownerReferences', [])), 'UNEXPECTED_POD_OWNER'
        kube('delete', 'pod', pod['metadata']['name'], '--wait=true', '--timeout=120s')


def prepare(root, capacity=True):
    """Prepare a safe first rollout for application Pods without the new preStop hook."""
    document = read(root / 'deployment.json')
    if document['spec'].get('strategy', {}).get('type') != 'RollingUpdate':
        return
    if capacity:
        available = int(next(line.split()[1] for line in Path('/proc/meminfo').read_text().splitlines()
                             if line.startswith('MemAvailable:'))) // 1024
        assert available >= 1536, 'ROLLOUT_MEMORY_HEADROOM_TOO_LOW'
    if (root / 'hpa.json').exists():
        document['spec'].pop('replicas', None)
    name = document['metadata']['name']
    items = pods()
    legacy = [p for p in items if p['metadata'].get('labels', {}).get('app.kubernetes.io/name') == name
              and not p['metadata'].get('deletionTimestamp')
              and not p['spec']['containers'][0].get('lifecycle', {}).get('preStop')]
    (root / 'bridge-legacy.json').unlink(missing_ok=True)
    (root / 'bridge-drained.json').unlink(missing_ok=True)
    if legacy:
        assert len(legacy) == 1, 'INITIAL_DRAIN_REQUIRES_ONE_LEGACY_POD'
        statuses = legacy[0]['status'].get('containerStatuses', [])
        assert statuses and all(s['ready'] for s in statuses), 'LEGACY_APPLICATION_NOT_READY'
        peers = [p for p in items if p['metadata'].get('labels', {}).get(GENERATION) == 'peers'
                 and not p['metadata'].get('deletionTimestamp')]
        assert len(peers) == 2 and all(p['status'].get('containerStatuses') and all(s['ready'] for s in p['status']['containerStatuses']) for p in peers), 'DISCOVERY_PEERS_REQUIRED_BEFORE_INITIAL_ROLLOUT'
        write(root / 'bridge-legacy.json', legacy[0])
        # Hold Pod Ready until the host drains the old instance. No timer may
        # automatically retire the old Pod if the migration process is interrupted.
        document['spec']['template']['spec']['readinessGates'] = [{'conditionType': GATE}]
        if name == 'api-gateway':
            document['spec']['minReadySeconds'] = 120
            kube('label', 'pod', legacy[0]['metadata']['name'], SERVING + '=true', '--overwrite')
    write(root / 'deployment-effective.json', document)


def bridge(root):
    path = root / 'bridge-legacy.json'
    if not path.exists():
        return
    old = read(path)
    name = old['metadata']['labels']['app.kubernetes.io/name']
    old_id = name + ':' + old['metadata']['uid']
    image = (root / 'image').read_text().strip()
    peers = []
    for _ in range(120):
        items = pods()
        assert any(p['metadata']['uid'] == old['metadata']['uid'] and not p['metadata'].get('deletionTimestamp') for p in items), 'LEGACY_DISAPPEARED_BEFORE_DRAIN'
        candidates = [p for p in items if p['metadata'].get('labels', {}).get('app.kubernetes.io/name') == name
                      and p['metadata']['uid'] != old['metadata']['uid'] and not p['metadata'].get('deletionTimestamp')
                      and p['spec']['containers'][0]['image'] == image and p['status'].get('containerStatuses')
                      and all(s['ready'] for s in p['status']['containerStatuses'])]
        peers = ['http://' + p['status']['podIP'] + ':8761' for p in items
                 if p['metadata'].get('labels', {}).get(GENERATION) == 'peers' and not p['metadata'].get('deletionTimestamp')]
        try:
            if len(peers) == 2:
                registered = [p for p in candidates if all(contains(registry(address), {name + ':' + p['metadata']['uid']:
                    (p['status']['podIP'], p['spec']['containers'][0]['ports'][0]['containerPort'])}) for address in peers)]
                if registered:
                    candidates = registered
                    break
        except (OSError, ValueError, KeyError):
            pass
        time.sleep(3)
    else:
        raise RuntimeError('REPLACEMENT_NOT_READY_FOR_LEGACY_DRAIN')
    # Save intent before the first request, so even a partially failed drain can be undone.
    write(root / 'bridge-drained.json', {'instanceId': old_id, 'application': name.upper(), 'peers': peers})
    for address in peers:
        request = urllib.request.Request(address + '/eureka/apps/' + name.upper() + '/' + old_id + '/status?value=OUT_OF_SERVICE', method='PUT')
        with urllib.request.urlopen(request, timeout=4) as response:
            assert response.status == 200, 'LEGACY_DRAIN_FAILED'
    time.sleep(75)
    # Strategic merge adds this condition without replacing kubelet-owned conditions.
    condition = json.dumps({'status': {'conditions': [{'type': GATE, 'status': 'True', 'reason': 'LegacyDrained',
                                                       'message': 'Legacy instance drained before rollout'}]}})
    for candidate in candidates:
        kube('patch', 'pod', candidate['metadata']['name'], '--subresource=status', '--type=strategic', '-p', condition)
    if name == 'api-gateway':
        addresses = {p['status']['podIP'] for p in candidates}
        for _ in range(20):
            slices = json.loads(kube('get', 'endpointslices', '-l', 'kubernetes.io/service-name=api-gateway', '-o', 'json'))['items']
            ready = {address for item in slices for endpoint in item.get('endpoints', [])
                     if endpoint.get('conditions', {}).get('ready') for address in endpoint.get('addresses', [])}
            if ready & addresses:
                break
            time.sleep(3)
        else:
            raise RuntimeError('NEW_GATEWAY_ENDPOINT_NOT_READY')
        # Legacy Gateway lacks preStop: remove only its Service label while leaving
        # the Deployment selector intact. The old process still serves in-flight HTTP.
        kube('label', 'pod', old['metadata']['name'], SERVING + '=false', '--overwrite')
        time.sleep(15)
    # Let this gated revision actually retire the legacy ReplicaSet before changing
    # templates again. Otherwise the controller could discard a not-yet-Available
    # gated Pod while still counting the drained legacy Pod as Available.
    for _ in range(120):
        items = pods()
        if not any(p['metadata']['uid'] == old['metadata']['uid'] and not p['metadata'].get('deletionTimestamp') for p in items):
            break
        for candidate in items:
            if candidate['metadata'].get('labels', {}).get('app.kubernetes.io/name') != name or candidate['metadata']['uid'] == old['metadata']['uid']:
                continue
            if candidate['metadata'].get('deletionTimestamp') or candidate['spec']['containers'][0]['image'] != image:
                continue
            if any(c['type'] == GATE and c['status'] == 'True' for c in candidate['status'].get('conditions', [])):
                continue
            statuses = candidate['status'].get('containerStatuses', [])
            if not statuses or not all(s['ready'] for s in statuses):
                continue
            expected = {name + ':' + candidate['metadata']['uid']: (candidate['status']['podIP'], candidate['spec']['containers'][0]['ports'][0]['containerPort'])}
            if all(contains(registry(address), expected) for address in peers):
                kube('patch', 'pod', candidate['metadata']['name'], '--subresource=status', '--type=strategic', '-p', condition)
        time.sleep(3)
    else:
        raise RuntimeError('LEGACY_REPLICASET_NOT_RETIRED')
    # Remove the transient gate from the template so future HPA Pods need no host
    # controller. This is one additional rollout, protected by the new preStop hooks.
    document = read(root / 'deployment.json')
    if (root / 'hpa.json').exists():
        document['spec'].pop('replicas', None)
    apply(root, 'deployment-effective.json', document)
    print('LEGACY_APPLICATION_DRAINED ' + name)


def unbridge(root):
    record = read(root / 'bridge-drained.json')
    old = read(root / 'bridge-legacy.json')
    if not any(p['metadata']['uid'] == old['metadata']['uid'] and not p['metadata'].get('deletionTimestamp') for p in pods()):
        return
    if record['application'] == 'API-GATEWAY':
        kube('label', 'pod', old['metadata']['name'], SERVING + '=true', '--overwrite')
    for address in record['peers']:
        request = urllib.request.Request(address + '/eureka/apps/' + record['application'] + '/' + record['instanceId'] + '/status?value=UP', method='DELETE')
        with urllib.request.urlopen(request, timeout=4) as response:
            assert response.status == 200, 'LEGACY_UNDRAIN_FAILED'
    time.sleep(75)


if __name__ == '__main__':
    os.umask(0o077)
    try:
        action, directory = sys.argv[1:]
        assert action in ('migrate', 'check', 'finish', 'restore', 'recover', 'prepare', 'prepare-rollback', 'bridge', 'unbridge'), 'INVALID_ACTION'
        if action == 'prepare-rollback':
            prepare(Path(directory), capacity=False)
        else:
            globals()[action](Path(directory))
    except (AssertionError, OSError, ValueError, KeyError, RuntimeError, subprocess.SubprocessError) as error:
        # Eureka resources have no application secrets; still avoid dumping resources or HTTP bodies.
        print('DISCOVERY_OPERATION_FAILED ' + type(error).__name__ + ' ' + str(error).splitlines()[0][:160], file=sys.stderr)
        sys.exit(1)
