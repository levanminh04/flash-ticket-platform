"""Exercise the real deploy script with a fake Kubernetes API and disposable files.

This verifies release/config rollback and secret filtering; it does not prove
cluster rollout, network connectivity or flock concurrency.
"""
import base64
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
    if (root / 'frontend-only').exists() and any('discovery-server' in arg for arg in arguments):
        sys.exit('Frontend must not query discovery')
    if arguments[:2] == ['get', 'namespace']:
        return
    if arguments[0] == 'create':
        print(Path(arguments[arguments.index('-f') + 1]).read_text())
    elif arguments[:2] in (['delete', 'hpa'], ['delete', 'pdb']):
        kind = 'HorizontalPodAutoscaler' if arguments[1] == 'hpa' else 'PodDisruptionBudget'
        (api / (kind + '.json')).unlink(missing_ok=True)
    elif arguments[:2] in (['get', 'pdb'], ['get', 'hpa']):
        kind = 'PodDisruptionBudget' if arguments[1] == 'pdb' else 'HorizontalPodAutoscaler'
        path = api / (kind + '.json')
        if path.exists():
            print(path.read_text())
        elif '--ignore-not-found' not in arguments:
            sys.exit('Resource not found')
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
        if (root / 'frontend-only').exists():
            assert resource['kind'] in ('Deployment', 'Service'), 'FE must not apply backend config or Secret'
        if (root.name == 'booking-fixture' and resource['kind'] == 'HorizontalPodAutoscaler'
                and 'minReplicas' in resource.get('spec', {})):
            desired_count = resource['spec']['minReplicas']
            (root / 'desired').write_text(str(desired_count))
            workload_path = api / 'Deployment.json'
            if workload_path.exists():
                workload = json.loads(workload_path.read_text())
                workload['spec']['replicas'] = desired_count
                workload_path.write_text(json.dumps(workload))
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
    elif arguments[:3] == ['get', 'statefulset', 'discovery-server'] and root.name == 'assistant-fixture':
        print(json.dumps({'spec': {'replicas': 1}}))
    elif arguments[:2] == ['get', 'pods']:
        if 'app.kubernetes.io/name=discovery-server,flash-ticket/discovery-generation=peers' in arguments:
            peers = []
            missing = root / 'peer-missing-once'
            if root.name == 'assistant-fixture':
                if missing.exists():
                    missing.unlink()
                else:
                    (root / 'peer-observed').touch()
                    peers = [{'metadata': {'name': 'discovery-server-0'},
                              'status': {'podIP': '127.0.0.1', 'conditions': [{'type': 'Ready', 'status': 'True'}]}}]
            print(json.dumps({'items': peers}))
            return
        deployment = json.loads((api / 'Deployment.json').read_text())
        count = int((root / 'desired').read_text()) if (root / 'desired').exists() else 1
        image = deployment['spec']['template']['spec']['containers'][0]['image']
        print(json.dumps({'items': [{
            'metadata': {'uid': 'fixture-pod' if i == 0 else f'fixture-pod-{i+1}'},
            'spec': {'nodeName': 'fixture-node-0' if (root / 'same-node').exists() else f'fixture-node-{i}',
                     'containers': [{'image': image, 'ports': deployment['spec']['template']['spec']['containers'][0].get('ports', [{'containerPort': 8083}])}]},
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
        service, port = {'assistant-fixture': ('assistant-service', 8089),
                         'booking-fixture': ('booking-service', 8082)}.get(root.name, ('payment-service', 8083))
        assert self.path == '/eureka/apps/' + service.upper()
        body = json.dumps({'application': {'instance': [{
            'instanceId': service + ':' + ('fixture-pod' if i == 0 else f'fixture-pod-{i+1}'), 'ipAddr': f'10.42.0.{9+i}',
            'port': {'$': port}, 'status': 'UP'
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
        scripts = source.parent / 'scripts'
        scripts.mkdir(parents=True)
        shutil.copyfile(SCRIPT.parent / 'migrate-k8s-discovery.py', scripts / 'migrate-k8s-discovery.py')
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
            (app / 'configmap.yaml').rename(app / 'configmap.saved')
            result = deploy('a', success=False)
            assert 'MISSING_MANIFEST' in result.stderr
            (app / 'configmap.saved').rename(app / 'configmap.yaml')
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
            placement = {'nodeAffinity': {'requiredDuringSchedulingIgnoredDuringExecution': {
                'nodeSelectorTerms': [{'matchExpressions': [
                    {'key': 'kubernetes.io/hostname', 'operator': 'In', 'values': ['ft84', 'server-2']},
                ]}],
            }}}
            pod_spec = deployment['spec']['template']['spec']
            pod_spec['affinity'] = placement
            (app / 'deployment.yaml').write_text(json.dumps(deployment))
            deploy('c')
            restricted = (state / 'current').read_text().strip()
            snapshot = json.loads((state / restricted / 'deployment.json').read_text())
            assert snapshot['spec']['template']['spec']['affinity'] == placement
            live = json.loads((root / 'api/Deployment.json').read_text())
            assert live['spec']['replicas'] == 2
            assert live['spec']['template']['spec']['affinity'] == placement
            assert 'replicas' not in json.loads(live['metadata']['annotations']['kubectl.kubernetes.io/last-applied-configuration'])['spec']
            assert server.registry_requests - before_registry >= 2, 'Must wait for BOTH Eureka instances'
            assert json.loads((root / 'api/HorizontalPodAutoscaler.json').read_text())['spec']['maxReplicas'] == 2
            del pod_spec['affinity']
            (app / 'deployment.yaml').write_text(json.dumps(deployment))
            hpa['spec']['maxReplicas'] = 3
            (app / 'hpa.yaml').write_text(json.dumps(hpa))
            (root / 'fail-once').touch()
            result = deploy('d', success=False)
            assert 'RESTORED_PREVIOUS' in result.stderr
            restored = json.loads((root / 'api/Deployment.json').read_text())
            assert restored['spec']['template']['spec']['affinity'] == placement
            assert (state / 'current').read_text().strip() == restricted
            assert json.loads((root / 'api/HorizontalPodAutoscaler.json').read_text())['spec']['maxReplicas'] == 2
            assert int((root / 'desired').read_text()) == 2
            # Trở về snapshot trước HPA chưa có ràng buộc lịch.
            deploy('a', 'rollback')
            assert not (root / 'api/HorizontalPodAutoscaler.json').exists()
            restored = json.loads((root / 'api/Deployment.json').read_text())
            assert restored['spec']['replicas'] == 1
            assert 'affinity' not in restored['spec']['template']['spec']
            print('K8s deploy fixtures passed: gates, secrets, image/config rollback, multi-Pod/Eureka, HPA ownership and placement rollback.')
        finally:
            server.shutdown()
            server.server_close()

        # FE độc lập: không env/config/Secret, không server Eureka hoặc migration helper.
        frontend_root = root / 'frontend-fixture'
        for path in ('state/k8s', 'api'):
            (frontend_root / path).mkdir(parents=True)
        env['APP_ROOT'] = frontend_root.as_posix()
        (frontend_root / 'frontend-only').touch()
        (frontend_root / 'state/k8s/enabled').touch()
        frontend_source = frontend_root / 'releases' / SHA / 'deploy'
        frontend_app = frontend_source / 'k8s/frontend'
        frontend_app.mkdir(parents=True)
        (frontend_source / 'scripts').mkdir()
        (frontend_source / 'scripts/migrate-k8s-discovery.py').write_text('raise AssertionError("FE called discovery migration")\n')
        frontend_deployment = json.loads(json.dumps(deployment))
        frontend_deployment['metadata']['name'] = 'frontend'
        frontend_deployment['spec']['template']['spec']['containers'][0]['ports'] = [{'containerPort': 5173}]
        (frontend_app / 'deployment.yaml').write_text(json.dumps(frontend_deployment))
        (frontend_app / 'service.yaml').write_text(json.dumps({
            'apiVersion': 'v1', 'kind': 'Service', 'metadata': {'name': 'frontend'},
            'spec': {'selector': {'app.kubernetes.io/name': 'frontend'}, 'ports': [{'port': 5173, 'targetPort': 5173}]}}))
        frontend_repository = REPOSITORY.replace('payment-service', 'frontend')
        frontend_state = frontend_root / 'state/k8s/frontend'
        current_image = frontend_root / 'state/k8s/frontend.current'
        (frontend_root / 'fail-once').touch()
        result = subprocess.run([bash, SCRIPT.as_posix(), 'frontend', frontend_repository + 'a' * 64, SHA, 'deploy'],
                                env=env, text=True, capture_output=True)
        assert result.returncode != 0 and 'NO_PREVIOUS_K8S_REVISION' in result.stderr, result.stdout + result.stderr
        assert not current_image.exists() and not (frontend_state / 'current').exists()
        for digest, mode in [('a', 'deploy'), ('a', 'recreate'), ('b', 'deploy')]:
            before = (frontend_state / 'current').read_text() if (frontend_state / 'current').exists() else None
            result = subprocess.run([bash, SCRIPT.as_posix(), 'frontend', frontend_repository + digest * 64, SHA, mode],
                                    env=env, text=True, capture_output=True)
            assert result.returncode == 0, result.stdout + result.stderr
            assert current_image.read_text().strip() == frontend_repository + digest * 64
            assert (frontend_state / 'current').read_text() != before
        before = (frontend_state / 'current').read_text()
        (frontend_root / 'fail-once').touch()
        result = subprocess.run([bash, SCRIPT.as_posix(), 'frontend', frontend_repository + 'c' * 64, SHA, 'deploy'],
                                env=env, text=True, capture_output=True)
        assert result.returncode != 0 and 'RESTORED_PREVIOUS' in result.stderr, result.stdout + result.stderr
        assert (frontend_state / 'current').read_text() == before
        assert current_image.read_text().strip() == frontend_repository + 'b' * 64
        assert json.loads((frontend_root / 'api/Deployment.json').read_text())['spec']['template']['spec']['containers'][0]['image'] == frontend_repository + 'b' * 64
        result = subprocess.run([bash, SCRIPT.as_posix(), 'frontend', frontend_repository + 'a' * 64, SHA, 'rollback'],
                                env=env, text=True, capture_output=True)
        assert result.returncode == 0, result.stdout + result.stderr
        assert current_image.read_text().strip() == frontend_repository + 'a' * 64
        before = (frontend_state / 'current').read_text()
        for service, image, commit, mode, reason in [
            ('frontend', REPOSITORY + 'a' * 64, SHA, 'deploy', 'INVALID_IMAGE'),
            ('frontend', frontend_repository + 'a' * 63, SHA, 'deploy', 'INVALID_IMAGE'),
            ('frontend', frontend_repository + 'a' * 64, 'bad', 'deploy', 'INVALID_SHA'),
            ('frontend', frontend_repository + 'a' * 64, SHA, 'bad', 'INVALID_MODE'),
            ('unknown', frontend_repository + 'a' * 64, SHA, 'deploy', 'UNKNOWN_SERVICE'),
            ('frontend', frontend_repository + 'c' * 64, SHA, 'rollback', 'ROLLBACK_CONFIG_NOT_FOUND'),
        ]:
            result = subprocess.run([bash, SCRIPT.as_posix(), service, image, commit, mode], env=env, text=True, capture_output=True)
            assert result.returncode != 0 and reason in result.stderr, result.stdout + result.stderr
            assert (frontend_state / 'current').read_text() == before
        assert not list(frontend_state.rglob('secret*.json'))
        assert not list(frontend_state.rglob('discovery*.json'))
        assert not (frontend_root / 'api/ConfigMap.json').exists()
        assert not (frontend_root / 'api/Secret.json').exists()

        # Guard chạy trước docker login; marker enabled chung vẫn cho phép Compose trước bootstrap FE.
        docker = binary / 'docker'
        docker.write_text('#!/usr/bin/env bash\nprintf called > "$APP_ROOT/docker-called"\nexit 79\n')
        docker.chmod(0o755)
        env['REGISTRY_USER'] = 'fixture'
        compose = SCRIPT.parent / 'deploy-service.sh'
        for migrated in (True, False):
            if not migrated:
                current_image.unlink()
            result = subprocess.run([bash, compose.as_posix(), 'frontend', frontend_repository + 'a' * 64, SHA],
                                    input='fixture-token\n', env=env, text=True, capture_output=True)
            assert result.returncode == (64 if migrated else 79), result.stdout + result.stderr
            assert (frontend_root / 'docker-called').exists() == (not migrated)
            if migrated:
                assert 'has migrated to K8s' in result.stdout
        print('Frontend fixtures passed: no backend dependencies, deploy/recreate/rollback, failure state, validation and Compose migration guard.')

        # Synthetic Assistant manifests exercise bootstrap and a single Ready Eureka peer.
        assistant_root = root / 'assistant-fixture'
        for path in ('state/k8s', 'api', 'env'):
            (assistant_root / path).mkdir(parents=True)
        env['APP_ROOT'] = assistant_root.as_posix()
        (assistant_root / 'state/k8s/enabled').touch()
        assistant_source = assistant_root / 'releases' / SHA / 'deploy'
        assistant_app = assistant_source / 'k8s/assistant-service'
        assistant_app.mkdir(parents=True)
        shutil.copytree(scripts, assistant_source / 'scripts')
        assistant_examples = assistant_source / 'k8s/examples'
        assistant_examples.mkdir()
        template = 'assistant-service.secret.env.example'
        shutil.copyfile(SCRIPT.parents[1] / 'k8s/examples' / template, assistant_examples / template)
        for name, document in [('deployment', deployment), ('configmap', config)]:
            fixture = json.loads(json.dumps(document).replace('payment-service', 'assistant-service'))
            if name == 'deployment':
                fixture['spec']['template']['spec']['containers'][0]['ports'] = [{'containerPort': 8089}]
            else:
                fixture['data'].update(REDIS_HOST='redis-standalone', REDIS_PORT='6379')
            (assistant_app / (name + '.yaml')).write_text(json.dumps(fixture))
        assistant_env = assistant_root / 'env/assistant-service.env'
        assistant_env.write_text('REDIS_PASSWORD=fixture-redis\nUNRELATED_SECRET=fixture-ignored\n')
        assistant_repository = REPOSITORY.replace('payment-service', 'assistant-service')
        command = [bash, SCRIPT.as_posix(), 'assistant-service', assistant_repository + 'a' * 64, SHA, 'deploy']
        result = subprocess.run(command, env=env, text=True, capture_output=True)
        assert result.returncode != 0 and 'MISSING_SECRET_KEYS ASSISTANT_MODEL_API_KEY' in result.stderr
        assert not (assistant_root / 'api/Secret.json').exists()
        with assistant_env.open('a') as stream:
            stream.write('ASSISTANT_MODEL_API_KEY=fixture-model\n')
        (assistant_root / 'fail-once').touch()
        result = subprocess.run(command, env=env, text=True, capture_output=True)
        assert result.returncode != 0 and 'NO_PREVIOUS_K8S_REVISION' in result.stderr
        assert 'no Compose fallback' in result.stderr and 'RESTORED_PREVIOUS' not in result.stderr
        assert not (assistant_root / 'state/k8s/assistant-service/current').exists()
        assert not (assistant_root / 'state/k8s/assistant-service.current').exists()
        assert not any(value in result.stdout + result.stderr for value in ('fixture-redis', 'fixture-ignored', 'fixture-model'))
        secret = json.loads((assistant_root / 'api/Secret.json').read_text())['data']
        assert set(secret) == {'REDIS_PASSWORD', 'ASSISTANT_MODEL_API_KEY'}
        # Initially no peer pod. The Service still responds, but must not be used as a fallback.
        (assistant_root / 'peer-missing-once').touch()
        with http.server.ThreadingHTTPServer(('127.0.0.1', 8761), Registry) as server:
            server.fixture_root = assistant_root
            server.registry_requests = 0
            (assistant_root / 'port').write_text(str(server.server_port))
            threading.Thread(target=server.serve_forever, daemon=True).start()
            try:
                result = subprocess.run(command, env=env, text=True, capture_output=True, timeout=90)
                assert result.returncode == 0, result.stdout + result.stderr
                assert (assistant_root / 'peer-observed').exists(), 'Must wait for the required Ready peer'
                assert server.registry_requests == 1, 'Must not query the Service when the required peer is missing'
                assert (assistant_root / 'state/k8s/assistant-service.current').read_text().strip() == assistant_repository + 'a' * 64
                live = json.loads((assistant_root / 'api/Deployment.json').read_text())
                assert live['spec']['replicas'] == 1
                assert live['spec']['template']['spec']['containers'][0]['image'] == assistant_repository + 'a' * 64

                manifest = assistant_app / 'configmap.yaml'
                state = assistant_root / 'state/k8s/assistant-service'
                baseline = (state / 'current').read_text()
                standalone_config = json.loads(manifest.read_text())
                assert standalone_config['data']['REDIS_HOST'] == 'redis-standalone'
                assert standalone_config['data']['REDIS_PORT'] == '6379'
                fixture_env = assistant_env.read_text()
                assert fixture_env.count('REDIS_PASSWORD=fixture-redis\n') == 1
                revisions = []
                for revision in ('first', 'second'):
                    standalone_config['data']['ASSISTANT_REDIS_KEY_PREFIX'] = f'fixture-{revision}'
                    manifest.write_text(json.dumps(standalone_config))
                    assistant_env.write_text(fixture_env.replace(
                        'REDIS_PASSWORD=fixture-redis\n', f'REDIS_PASSWORD=fixture-redis-{revision}\n'))
                    result = subprocess.run(command, env=env, text=True, capture_output=True, timeout=90)
                    assert result.returncode == 0, result.stdout + result.stderr
                    assert f'fixture-redis-{revision}' not in result.stdout + result.stderr
                    assert json.loads((assistant_root / 'api/ConfigMap.json').read_text()) == standalone_config
                    deployed = json.loads((assistant_root / 'api/Secret.json').read_text())['data']
                    assert set(deployed) == set(secret)
                    assert base64.b64decode(deployed['REDIS_PASSWORD']).decode() == f'fixture-redis-{revision}'
                    assert {key: deployed[key] for key in secret if key != 'REDIS_PASSWORD'} == {
                        key: value for key, value in secret.items() if key != 'REDIS_PASSWORD'}
                    live = json.loads((assistant_root / 'api/Deployment.json').read_text())
                    assert live['spec']['template']['spec']['containers'][0]['image'] == assistant_repository + 'a' * 64
                    revisions.append(((state / 'current').read_text(), deployed,
                                      json.loads(manifest.read_text())))
                first, second = revisions
                assert first[0] != baseline and second[0] != first[0]
                assert second[1]['REDIS_PASSWORD'] != first[1]['REDIS_PASSWORD']
                assert second[2] != first[2]
                assert (state / 'previous').read_text() == first[0]
                # Rollout lỗi phải phục hồi revision hiện tại, không lấy cấu hình bootstrap cũ.
                standalone_config['data']['ASSISTANT_REDIS_KEY_PREFIX'] = 'fixture-failed'
                manifest.write_text(json.dumps(standalone_config))
                assistant_env.write_text(fixture_env.replace(
                    'REDIS_PASSWORD=fixture-redis\n', 'REDIS_PASSWORD=fixture-redis-failed\n'))
                (assistant_root / 'fail-once').touch()
                result = subprocess.run(command, env=env, text=True, capture_output=True, timeout=90)
                assert result.returncode != 0 and 'RESTORED_PREVIOUS' in result.stderr
                assert 'fixture-redis-failed' not in result.stdout + result.stderr
                assert (state / 'current').read_text() == second[0]
                assert json.loads((assistant_root / 'api/ConfigMap.json').read_text()) == second[2]
                assert json.loads((assistant_root / 'api/Secret.json').read_text())['data'] == second[1]
                result = subprocess.run([*command[:-1], 'rollback'], env=env, text=True, capture_output=True, timeout=90)
                assert result.returncode == 0, result.stdout + result.stderr
                assert (state / 'current').read_text() == first[0]
                assert json.loads((assistant_root / 'api/ConfigMap.json').read_text()) == first[2]
                assert json.loads((assistant_root / 'api/Secret.json').read_text())['data'] == first[1]
            finally:
                server.shutdown()
        print('Assistant fixtures passed: bootstrap, allowlist, Eureka and same-image standalone revision rollback.')
        booking_fixture(root / 'booking-fixture', bash, env, scripts, deployment, config)


def booking_fixture(root, bash, env, scripts, deployment, config):
    # A missing deploy guard must fail before changing the fake API or its revision pointers.
    env = dict(env, APP_ROOT=root.as_posix())
    for name in ('state/k8s', 'api', 'env'):
        (root / name).mkdir(parents=True)
    (root / 'state/k8s/enabled').touch()
    deploy = root / 'releases' / SHA / 'deploy'
    app = deploy / 'k8s/booking-service'
    app.mkdir(parents=True)
    shutil.copytree(scripts, deploy / 'scripts')
    examples = deploy / 'k8s/examples'
    examples.mkdir()
    template_name = 'booking-service.secret.env.example'
    shutil.copyfile(SCRIPT.parents[1] / 'k8s/examples' / template_name, examples / template_name)
    manifest = json.loads(json.dumps(deployment).replace('payment-service', 'booking-service'))
    spec = manifest['spec']['template']['spec']
    spec['containers'][0].update(name='booking-service', ports=[{'containerPort': 8082}], envFrom=[
        {'configMapRef': {'name': 'booking-service-config'}}, {'secretRef': {'name': 'booking-service-secret'}},
    ])
    selector = {'matchLabels': {'app.kubernetes.io/name': 'booking-service'}}
    spec['affinity'] = {'podAntiAffinity': {'requiredDuringSchedulingIgnoredDuringExecution': [{
        'labelSelector': selector, 'namespaces': ['flash-ticket'], 'topologyKey': 'kubernetes.io/hostname',
    }]}}
    (root / 'desired').write_text('2')
    hpa = {'apiVersion': 'autoscaling/v2', 'kind': 'HorizontalPodAutoscaler',
           'metadata': {'name': 'booking-service', 'namespace': 'flash-ticket'},
           'spec': {'scaleTargetRef': {'apiVersion': 'apps/v1', 'kind': 'Deployment', 'name': 'booking-service'},
                    'minReplicas': 2, 'maxReplicas': 2}}
    pdb = {'apiVersion': 'policy/v1', 'kind': 'PodDisruptionBudget',
           'metadata': {'name': 'booking-service', 'namespace': 'flash-ticket'},
           'spec': {'minAvailable': 1, 'selector': selector}}
    config = json.loads(json.dumps(config).replace('payment-service', 'booking-service'))
    for name, document in [('deployment', manifest), ('configmap', config),
                           ('service', {'kind': 'Service', 'metadata': {'name': 'booking-service'}}),
                           ('hpa', hpa), ('pdb', pdb)]:
        (app / (name + '.yaml')).write_text(json.dumps(document))
    env_file = root / 'env/booking-service.env'
    state = root / 'state/k8s/booking-service'
    writer_env = ('BOOKING_DB_PASSWORD=fixture-booking-writer\nREDIS_PASSWORD=fixture-booking-redis\n'
                  'RABBITMQ_PASSWORD=fixture-booking-broker\nINTERNAL_SERVICE_CLIENT_SECRET=fixture-booking-client\n')
    prefix = 'BOOKING_SALES_CHART_REPLICA_'
    jdbc = 'jdbc:postgresql://reader.fixture:5432/booking_db?sslmode=require&targetServerType=secondary'
    pilot = {'ENABLED': 'true', 'JDBC_URL': jdbc, 'USERNAME': 'fixture-booking-reader',
             'PASSWORD': 'fixture-reader-value', 'PROBE_INTERVAL': 'PT2S', 'PROBE_QUERY_TIMEOUT': '1',
             'MAX_SAMPLE_AGE': 'PT3S', 'READER_TRANSACTION_TIMEOUT': '2'}
    command = [bash, SCRIPT.as_posix(), 'booking-service', REPOSITORY.replace('payment-service', 'booking-service') + 'b' * 64, SHA]

    def run(values, mode='deploy'):
        env_file.write_text(writer_env + ''.join(f'{k if k.startswith("BOOKING_") else prefix + k}={v}\n' for k, v in values.items()), encoding='utf-8')
        result = subprocess.run([*command, mode], env=env, capture_output=True, text=True, timeout=90)
        assert not any(v in result.stdout + result.stderr for v in (jdbc, 'fixture-reader-value', 'fixture-booking-writer'))
        return result

    def live(kind):
        return json.loads((root / 'api' / (kind + '.json')).read_text())

    def reject(values, marker):
        before = {p.name: p.read_bytes() for p in (root / 'api').glob('*.json')}
        current = (state / 'current').read_bytes() if (state / 'current').exists() else None
        result = run(values)
        assert result.returncode and marker in result.stderr, result.stdout + result.stderr
        assert before == {p.name: p.read_bytes() for p in (root / 'api').glob('*.json')}
        assert current == ((state / 'current').read_bytes() if (state / 'current').exists() else None)

    with http.server.ThreadingHTTPServer(('127.0.0.1', 0), Registry) as server:
        server.fixture_root = root
        server.registry_requests = 0
        (root / 'port').write_text(str(server.server_port))
        threading.Thread(target=server.serve_forever, daemon=True).start()
        try:
            reject(pilot, 'BOOKING_WRITER_REVISION_REQUIRED')
            result = run({})
            assert result.returncode == 0, result.stdout + result.stderr
            assert 'binaryData' not in live('ConfigMap')
            assert (root / 'api/PodDisruptionBudget.json').is_file(), 'PDB must be applied with the revision'
            assert live('PodDisruptionBudget')['spec'] == pdb['spec']
            assert live('HorizontalPodAutoscaler')['spec']['minReplicas'] == 2
            assert live('HorizontalPodAutoscaler')['spec']['maxReplicas'] == 2
            assert not any(k.startswith(prefix) for k in live('Secret')['data'])
            result = run({'ENABLED': 'false'})
            assert result.returncode == 0, result.stdout + result.stderr
            writer = (state / 'current').read_text().strip()
            old_config, old_secret = live('ConfigMap'), live('Secret')
            assert base64.b64decode(old_secret['data'][prefix + 'ENABLED']) == b'false'
            snapshot = state / writer
            check_source = SCRIPT.read_text(encoding='utf-8').split(
                '    if python3 - "$revision" "$service" 2>"$revision/rollout-check.error" <<\'PY\'\n', 1)[1].split('\nPY\n', 1)[0]
            # Chạy chính validator của script, không dựng lại logic scheduler trong test.
            pod_snapshot = snapshot / 'live-pods.json'
            original_pods = pod_snapshot.read_bytes()
            for missing_node in (False, True):
                wrong = json.loads(original_pods)
                for pod in wrong['items']:
                    if missing_node:
                        pod['spec'].pop('nodeName', None)
                    else:
                        pod['spec']['nodeName'] = 'same-fixture-node'
                pod_snapshot.write_text(json.dumps(wrong))
                try:
                    checked = subprocess.run([sys.executable, '-I', '-c', check_source, str(snapshot), 'booking-service'],
                                             capture_output=True, text=True, timeout=15)
                    assert checked.returncode and 'BOOKING_HA_NODE_PLACEMENT_MISMATCH' in checked.stderr
                finally:
                    pod_snapshot.write_bytes(original_pods)
            service_snapshot = snapshot / 'service.json'
            original_service = service_snapshot.read_bytes()
            service_snapshot.unlink()
            try:
                reject(pilot, 'BOOKING_WRITER_REVISION_REQUIRED')
            finally:
                service_snapshot.write_bytes(original_service)
            for file, change in (
                ('secret-env.json', lambda d: d['data'].update({prefix + 'ENABLED': 'dHJ1ZQ=='})),
                ('deployment.json', lambda d: d['spec']['template']['spec'].pop('affinity')),
            ):
                path = snapshot / file
                original = path.read_bytes()
                modified = json.loads(original)
                change(modified)
                path.write_text(json.dumps(modified))
                try:
                    reject(pilot, 'BOOKING_WRITER_REVISION_REQUIRED')
                finally:
                    path.write_bytes(original)
            digest = snapshot / 'image'
            original_digest = digest.read_bytes()
            deploy_snapshot = snapshot / 'deployment.json'
            original_deploy = deploy_snapshot.read_bytes()
            wrong_image = command[3].rsplit(':', 1)[0] + ':' + 'c' * 64
            wrong_deploy = json.loads(original_deploy)
            wrong_deploy['spec']['template']['spec']['containers'][0]['image'] = wrong_image
            digest.write_text(wrong_image + '\n')
            deploy_snapshot.write_text(json.dumps(wrong_deploy))
            try:
                reject(pilot, 'BOOKING_WRITER_REVISION_REQUIRED')
            finally:
                digest.write_bytes(original_digest)
                deploy_snapshot.write_bytes(original_deploy)
            pdb_snapshot = snapshot / 'pdb.json'
            original_pdb = pdb_snapshot.read_bytes()
            pdb_snapshot.unlink()
            try:
                reject(pilot, 'BOOKING_WRITER_REVISION_REQUIRED')
            finally:
                pdb_snapshot.write_bytes(original_pdb)
            for group in ('BOOKING_SALES_ORGANIZER_REPORT_REPLICA_ENABLED', 'BOOKING_SALES_EVENT_REPORT_REPLICA_ENABLED'):
                reject({group: 'true'}, 'BOOKING_READER_BASE_REQUIRED')
                reject(dict(pilot, **{group: 'yes'}), 'INVALID_BOOKING_READER_FLAG')
            for file, change in (
                ('hpa.json', lambda d: d['spec'].update(minReplicas=1)),
                ('pdb.json', lambda d: d['spec'].update(selector={'matchLabels': {'app.kubernetes.io/name': 'other'}})),
            ):
                path = snapshot / file
                original = path.read_bytes()
                modified = json.loads(original)
                change(modified)
                path.write_text(json.dumps(modified))
                try:
                    reject(pilot, 'BOOKING_WRITER_REVISION_REQUIRED')
                finally:
                    path.write_bytes(original)
            reject(dict(pilot, ENABLED='yes'), 'INVALID_BOOKING_READER_FLAG')
            reject(dict(pilot, PASSWORD=''), 'MISSING_BOOKING_READER_KEYS')
            for value in (jdbc.replace('sslmode=require', 'sslmode=verify-full'), jdbc + '&sslmode=disable',
                          jdbc.replace('reader.fixture:5432', 'reader.fixture:5432,writer.fixture:5432'),
                          jdbc + '&user=fixture-embedded', jdbc + '&sslrootcert=/fixture-ca.crt'):
                reject(dict(pilot, JDBC_URL=value), 'INVALID_BOOKING_READER_URL')
            (root / 'fail-once').touch()
            result = run(pilot)
            assert result.returncode and 'RESTORED_PREVIOUS' in result.stderr
            assert (state / 'current').read_text().strip() == writer
            assert live('ConfigMap') == old_config and live('Secret') == old_secret
            result = run(pilot)
            assert result.returncode == 0, result.stdout + result.stderr
            deployed = live('Secret')['data']
            assert {k: base64.b64decode(deployed[prefix + k]).decode() for k in pilot} == pilot
            assert live('ConfigMap') == old_config
            assert (state / 'previous').read_text().strip() == writer
            active = state / (state / 'current').read_text().strip()
            assert (active / 'writer-baseline').is_file(), 'Active revision must retain a writer baseline'
            assert (active / 'writer-baseline').read_text().strip() == writer
            organizer = dict(pilot, BOOKING_SALES_ORGANIZER_REPORT_REPLICA_ENABLED='true')
            baseline_file = active / 'writer-baseline'
            original_baseline = baseline_file.read_bytes()
            for broken in (None, '../not-a-revision'):
                if broken is None:
                    baseline_file.unlink()
                else:
                    baseline_file.write_text(broken)
                try:
                    reject(organizer, 'BOOKING_WRITER_REVISION_REQUIRED')
                finally:
                    baseline_file.write_bytes(original_baseline)
            config_source = app / 'configmap.yaml'
            original_config = config_source.read_bytes()
            overridden = json.loads(original_config)
            overridden['data']['BOOKING_SALES_EVENT_REPORT_REPLICA_ENABLED'] = 'false'
            config_source.write_text(json.dumps(overridden))
            try:
                reject(organizer, 'INVALID_BOOKING_READER_FLAG_SOURCE')
            finally:
                config_source.write_bytes(original_config)
            assert run(organizer).returncode == 0
            assert (state / 'previous').read_text().strip() == writer
            both = dict(organizer, BOOKING_SALES_EVENT_REPORT_REPLICA_ENABLED='true')
            (root / 'fail-once').touch()
            result = run(both)
            assert result.returncode and 'RESTORED_PREVIOUS' in result.stderr
            assert (state / 'current').read_text().strip() == writer
            assert live('Secret') == old_secret
            assert live('PodDisruptionBudget')['spec'] == pdb['spec']
            assert run(pilot).returncode == 0
            assert run(organizer).returncode == 0
            assert run(both).returncode == 0
            assert (state / 'previous').read_text().strip() == writer
            env_file.unlink()  # Rollback chỉ dùng snapshot, không đọc env của operator.
            result = subprocess.run([*command, 'rollback'], env=env, capture_output=True, text=True, timeout=90)
            assert result.returncode == 0, result.stdout + result.stderr
            assert (state / 'current').read_text().strip() == writer
            assert live('ConfigMap') == old_config and live('Secret') == old_secret
            pod = live('Deployment')['spec']['template']
            assert pod['metadata']['annotations']['flash-ticket/release'] == writer
            assert pod['spec']['affinity'] == spec['affinity']
            assert live('Deployment')['spec']['replicas'] == 2
            assert (state / 'previous').read_text().strip() == writer
            # Infra rollback cũ không có PDB: khôi phục đúng absence, không coi là reader baseline.
            legacy = state / ('1700000000-' + SHA[:12] + '-777')
            shutil.copytree(snapshot, legacy)
            (legacy / 'pdb.json').unlink()
            (legacy / 'writer-baseline').unlink(missing_ok=True)
            old_deployment = json.loads((legacy / 'deployment.json').read_text())
            old_deployment['spec']['template']['metadata']['annotations']['flash-ticket/release'] = legacy.name
            old_deployment['spec']['template']['spec'].pop('affinity')
            (legacy / 'deployment.json').write_text(json.dumps(old_deployment))
            old_hpa = json.loads((legacy / 'hpa.json').read_text())
            old_hpa['spec'].update(minReplicas=1, maxReplicas=2)
            (legacy / 'hpa.json').write_text(json.dumps(old_hpa))
            (state / 'previous').write_text(legacy.name + '\n')
            result = subprocess.run([*command, 'rollback'], env=env, capture_output=True, text=True, timeout=90)
            assert result.returncode == 0, result.stdout + result.stderr
            assert not (root / 'api/PodDisruptionBudget.json').exists()
            assert (state / 'current').read_text().strip() == legacy.name
            assert live('Deployment')['spec']['replicas'] == 1
        finally:
            server.shutdown()
    print('Booking fixtures passed: 3 reader flags, dynamic 2-Pod placement, PDB presence/absence and writer-baseline rollback.')


if __name__ == '__main__':
    if sys.argv[1:2] == ['--kubectl']:
        fake_kubectl()
    else:
        run_fixtures()
