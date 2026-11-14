"""Exercise the real deploy script with a fake Kubernetes API and disposable files.

This verifies release/config rollback and secret filtering; it does not prove
cluster rollout, network connectivity or flock concurrency.
"""
import http.server
import json
import os
from pathlib import Path
import shutil
import subprocess
import sys
import tempfile
import threading

SCRIPT = Path(__file__).resolve().parents[2] / 'deploy/scripts/deploy-k8s-service.sh'
SHA = 'a' * 40
REPOSITORY = 'ghcr.io/levanminh04/flash-ticket-platform/payment-service@sha256:'


def fake_kubectl():
    arguments = sys.argv[2:]
    root = Path(os.environ['APP_ROOT'])
    api = root / 'api'
    if arguments[:2] == ['-n', 'flash-ticket']:
        arguments = arguments[2:]
    if arguments[:2] == ['get', 'namespace']:
        return
    if arguments[0] == 'create':
        print(Path(arguments[arguments.index('-f') + 1]).read_text())
    elif arguments[:2] == ['delete', 'hpa']:
        (api / 'HorizontalPodAutoscaler.json').unlink(missing_ok=True)
    elif arguments[:2] == ['apply', 'set-last-applied']:
        resource = json.loads(Path(arguments[-1]).read_text())
        live = json.loads((api / 'Deployment.json').read_text())
        live['metadata'].setdefault('annotations', {})['kubectl.kubernetes.io/last-applied-configuration'] = json.dumps(resource)
        (api / 'Deployment.json').write_text(json.dumps(live))
    elif arguments[0] == 'apply':
        resource = json.loads(Path(arguments[-1]).read_text())
        if resource['kind'] == 'Deployment':
            last_applied = json.dumps(resource)
            desired = root / 'desired'
            if 'replicas' in resource['spec']:
                desired.write_text(str(resource['spec']['replicas']))
            resource['spec']['replicas'] = int(desired.read_text()) if desired.exists() else 1
            resource['metadata'].setdefault('annotations', {})['kubectl.kubernetes.io/last-applied-configuration'] = last_applied
        (api / (resource['kind'] + '.json')).write_text(json.dumps(resource))
    elif arguments[0] == 'rollout':
        fail = root / 'fail-once'
        if fail.exists():
            fail.unlink()
            sys.exit(1)
    elif arguments[:2] == ['get', 'deployment']:
        path = api / 'Deployment.json'
        if path.exists():
            live = json.loads(path.read_text())
            if (root / 'desired').exists():
                live['spec']['replicas'] = int((root / 'desired').read_text())
            print(json.dumps(live))
        elif '--ignore-not-found' not in arguments:
            sys.exit('Deployment not found')
    elif arguments[:2] == ['get', 'pods']:
        deployment = json.loads((api / 'Deployment.json').read_text())
        count = int((root / 'desired').read_text()) if (root / 'desired').exists() else 1
        image = deployment['spec']['template']['spec']['containers'][0]['image']
        print(json.dumps({'items': [{
            'metadata': {'uid': 'fixture-pod' if i == 0 else f'fixture-pod-{i+1}'},
            'spec': {'containers': [{'image': image, 'ports': [{'containerPort': 8083}]}]},
            'status': {'podIP': f'10.42.0.{9+i}', 'containerStatuses': [{'ready': True, 'restartCount': 0}]}
        } for i in range(count)]}))
    elif arguments[:2] == ['get', 'service']:
        print(json.dumps({'spec': {'clusterIP': '127.0.0.1', 'ports': [{'port': int((root / 'port').read_text())}]}}))
    else:
        sys.exit('Unexpected kubectl call')


class Registry(http.server.BaseHTTPRequestHandler):
    def do_GET(self):
        root = self.server.fixture_root
        self.server.registry_requests += 1
        count = int((root / 'desired').read_text()) if (root / 'desired').exists() else 1
        missing = root / 'registry-missing-once'
        if missing.exists():
            missing.unlink()
            count = 1
        body = json.dumps({'application': {'instance': [{
            'instanceId': 'payment-service:' + ('fixture-pod' if i == 0 else f'fixture-pod-{i+1}'), 'ipAddr': f'10.42.0.{9+i}',
            'port': {'$': 8083}, 'status': 'UP'
        } for i in range(count)]}}).encode()
        self.send_response(200)
        self.end_headers()
        self.wfile.write(body)

    def log_message(self, *args):
        pass


def run_fixtures():
    bash = 'C:/Program Files/Git/bin/bash.exe' if os.name == 'nt' else shutil.which('bash')
    assert bash, 'bash required'
    with tempfile.TemporaryDirectory(prefix='ft-k8s-fixture-') as directory:
        root = Path(directory)
        binary = root / 'bin'
        binary.mkdir()
        # Shims allow the same fixture on Windows Git Bash and Linux CI.
        for name, command in {
            'python3': f'exec "{Path(sys.executable).as_posix()}" "$@"',
            'kubectl': f'exec "{Path(sys.executable).as_posix()}" "{Path(__file__).resolve().as_posix()}" --kubectl "$@"',
            'flock': 'exit 0',
        }.items():
            path = binary / name
            path.write_text('#!/usr/bin/env bash\n' + command + '\n')
            path.chmod(0o755)
        env = os.environ.copy()
        env['APP_ROOT'] = root.as_posix()
        env['PATH'] = str(binary) + os.pathsep + env['PATH']
        env.pop('K8S_BOOTSTRAP_SHA', None)
        for path in ('state/k8s', 'api', 'env'):
            (root / path).mkdir(parents=True)
        source = root / 'releases' / SHA / 'deploy/k8s'
        app = source / 'payment-service'
        app.mkdir(parents=True)
        examples = source / 'examples'
        examples.mkdir()
        (examples / 'payment-service.secret.env.example').write_text('PAYMENT_DB_PASSWORD=\nRABBITMQ_PASSWORD=\nPAYMENT_SERVICE_CLIENT_SECRET=\n# Optional\n')
        (root / 'env/payment-service.env').write_text('PAYMENT_DB_PASSWORD=fixture-db\nRABBITMQ_PASSWORD=fixture-broker\nPAYMENT_SERVICE_CLIENT_SECRET=fixture-client\nRABBITMQ_HOST=localhost\nUNRELATED_SECRET=must-not-import\n')
        deployment = {'apiVersion': 'apps/v1', 'kind': 'Deployment', 'metadata': {'name': 'payment-service'},
                      'spec': {'replicas': 1, 'template': {'metadata': {}, 'spec': {'containers': [{'image': 'render-required'}]}}}}
        (app / 'deployment.yaml').write_text(json.dumps(deployment))
        config = {'apiVersion': 'v1', 'kind': 'ConfigMap', 'metadata': {'name': 'payment-service-config'}, 'data': {'revision': 'first'}}
        (app / 'configmap.yaml').write_text(json.dumps(config))
        server = http.server.ThreadingHTTPServer(('127.0.0.1', 0), Registry)
        server.fixture_root = root
        server.registry_requests = 0
        (root / 'port').write_text(str(server.server_port))
        thread = threading.Thread(target=server.serve_forever, daemon=True)
        thread.start()
        try:
            def deploy(digest, mode='deploy', success=True):
                result = subprocess.run([bash, SCRIPT.as_posix(), 'payment-service', REPOSITORY + digest * 64, SHA, mode],
                                        env=env, text=True, capture_output=True)
                assert (result.returncode == 0) == success, result.stdout + result.stderr
                assert 'fixture-db' not in result.stdout + result.stderr
                return result

            deploy('a', success=False)  # bootstrap must be explicit
            (root / 'state/k8s/enabled').touch()
            (root / 'state/maintenance').touch()
            deploy('a', success=False)
            (root / 'state/maintenance').unlink()
            deploy('a')
            state = root / 'state/k8s/payment-service'
            first = (state / 'current').read_text()
            secret = json.loads((root / 'api/Secret.json').read_text())['data']
            assert set(secret) == {'PAYMENT_DB_PASSWORD', 'RABBITMQ_PASSWORD', 'PAYMENT_SERVICE_CLIENT_SECRET'}
            assert json.loads((root / 'api/Deployment.json').read_text())['spec']['template']['spec']['containers'][0]['image'] == REPOSITORY + 'a' * 64
            config['data']['revision'] = 'second'
            (app / 'configmap.yaml').write_text(json.dumps(config))
            (root / 'fail-once').touch()
            result = deploy('b', success=False)
            assert 'RESTORED_PREVIOUS' in result.stderr
            assert json.loads((root / 'api/ConfigMap.json').read_text())['data']['revision'] == 'first'
            assert (state / 'current').read_text() == first
            deploy('b')
            assert (state / 'previous').read_text() == first
            deploy('a', 'rollback')
            assert json.loads((root / 'api/ConfigMap.json').read_text())['data']['revision'] == 'first'
            deploy('c', 'rollback', success=False)  # no matching config snapshot
            # The dry-run/defaulted Deployment still has replicas=1. HPA owns the live 2.
            hpa = {'apiVersion': 'autoscaling/v2', 'kind': 'HorizontalPodAutoscaler',
                   'metadata': {'name': 'payment-service', 'namespace': 'flash-ticket'},
                   'spec': {'minReplicas': 1, 'maxReplicas': 2}}
            (app / 'hpa.yaml').write_text(json.dumps(hpa))
            (root / 'desired').write_text('2')
            (root / 'registry-missing-once').touch()
            before_registry = server.registry_requests
            deploy('c')
            live = json.loads((root / 'api/Deployment.json').read_text())
            assert live['spec']['replicas'] == 2
            assert 'replicas' not in json.loads(live['metadata']['annotations']['kubectl.kubernetes.io/last-applied-configuration'])['spec']
            assert server.registry_requests - before_registry >= 2, 'Must wait for BOTH Eureka instances'
            assert json.loads((root / 'api/HorizontalPodAutoscaler.json').read_text())['spec']['maxReplicas'] == 2
            hpa['spec']['maxReplicas'] = 3
            (app / 'hpa.yaml').write_text(json.dumps(hpa))
            (root / 'fail-once').touch()
            result = deploy('d', success=False)
            assert 'RESTORED_PREVIOUS' in result.stderr
            assert json.loads((root / 'api/HorizontalPodAutoscaler.json').read_text())['spec']['maxReplicas'] == 2
            assert int((root / 'desired').read_text()) == 2
            deploy('a', 'rollback')  # pre-HPA revision restores fixed scale and removes the HPA
            assert not (root / 'api/HorizontalPodAutoscaler.json').exists()
            assert json.loads((root / 'api/Deployment.json').read_text())['spec']['replicas'] == 1
            print('K8s deploy fixtures passed: gates, secrets, image/config rollback, multi-Pod/Eureka, HPA ownership and rollback.')
        finally:
            server.shutdown()
            server.server_close()


if __name__ == '__main__':
    if sys.argv[1:2] == ['--kubectl']:
        fake_kubectl()
    else:
        run_fixtures()
