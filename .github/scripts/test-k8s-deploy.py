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
    elif arguments[0] == 'apply':
        resource = json.loads(Path(arguments[-1]).read_text())
        (api / (resource['kind'] + '.json')).write_text(json.dumps(resource))
    elif arguments[0] == 'rollout':
        fail = root / 'fail-once'
        if fail.exists():
            fail.unlink()
            sys.exit(1)
    elif arguments[:2] == ['get', 'deployment']:
        print((api / 'Deployment.json').read_text())
    elif arguments[:2] == ['get', 'pods']:
        print(json.dumps({'items': [{
            'metadata': {'uid': 'fixture-pod'},
            'spec': {'containers': [{'ports': [{'containerPort': 8083}]}]},
            'status': {'podIP': '10.42.0.9', 'containerStatuses': [{'ready': True, 'restartCount': 0}]}
        }]}))
    elif arguments[:2] == ['get', 'service']:
        print(json.dumps({'spec': {'clusterIP': '127.0.0.1', 'ports': [{'port': int((root / 'port').read_text())}]}}))
    else:
        sys.exit('Unexpected kubectl call')


class Registry(http.server.BaseHTTPRequestHandler):
    def do_GET(self):
        body = json.dumps({'application': {'instance': [{
            'instanceId': 'payment-service:fixture-pod', 'ipAddr': '10.42.0.9',
            'port': {'$': 8083}, 'status': 'UP'
        }]}}).encode()
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
                      'spec': {'template': {'metadata': {}, 'spec': {'containers': [{'image': 'render-required'}]}}}}
        (app / 'deployment.yaml').write_text(json.dumps(deployment))
        config = {'apiVersion': 'v1', 'kind': 'ConfigMap', 'metadata': {'name': 'payment-service-config'}, 'data': {'revision': 'first'}}
        (app / 'configmap.yaml').write_text(json.dumps(config))
        server = http.server.ThreadingHTTPServer(('127.0.0.1', 0), Registry)
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
            print('K8s deploy fixtures passed: bootstrap, maintenance, secret allowlist, image, failed rollout restore, explicit rollback.')
        finally:
            server.shutdown()
            server.server_close()


if __name__ == '__main__':
    if sys.argv[1:2] == ['--kubectl']:
        fake_kubectl()
    else:
        run_fixtures()
