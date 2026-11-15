"""Run the exact Pod startup/drain commands in the pinned Alpine runtime image.

The HTTP endpoints are local fixtures. This does not test Traefik, real Eureka
replication or request continuity on node 84.
"""
import http.server
import json
import os
from pathlib import Path
import subprocess
import sys
import tempfile
import threading


def container_fixtures():
    apps = json.loads(Path('/fixture/apps.json').read_text())
    state = {'status': 'UP', 'health': 200, 'id': '', 'ip': '10.42.0.9', 'malformed': False, 'puts': [], 'putStatus': 200}
    sample = {'app': 'BOOKING-SERVICE', 'instanceId': 'booking-service:sample', 'status': 'UP', 'ipAddr': '10.42.0.8', 'port': {'$': 8082}}
    state.update({'localApps': [sample], 'peerApps': [sample], 'peerStatus': 200})

    class Handler(http.server.BaseHTTPRequestHandler):
        def do_GET(self):
            self.send_response(state['health'] if self.path.startswith('/actuator/') else 200)
            self.end_headers()
            if self.path.startswith('/actuator/'):
                self.wfile.write(b'{"status":"UP"}')
            elif self.path == '/eureka/apps':
                self.wfile.write(json.dumps({'applications': {'application': [{'name': 'BOOKING-SERVICE', 'instance': state['localApps']}]}}).encode())
            elif state['malformed']:
                self.wfile.write(b'not-json')
            else:
                self.wfile.write(json.dumps({'instance': {'status': state['status'], 'instanceId': state['id'], 'ipAddr': state['ip']}}).encode())

        def do_PUT(self):
            state['puts'].append(self.path)
            if state['putStatus'] == 200:
                state['status'] = 'OUT_OF_SERVICE'
            self.send_response(state['putStatus'])
            self.end_headers()

        def log_message(self, *args):
            pass

    class PeerHandler(Handler):
        def do_GET(self):
            self.send_response(state['peerStatus'])
            self.end_headers()
            self.wfile.write(json.dumps({'applications': {'application': [{'name': 'BOOKING-SERVICE', 'instance': state['peerApps']}]}}).encode())

    ports = {a['port'] for a in apps} | {8761}
    servers = [http.server.ThreadingHTTPServer(('127.0.0.1', port), Handler) for port in ports]
    servers.append(http.server.ThreadingHTTPServer(('127.0.0.2', 8761), PeerHandler))
    for server in servers:
        threading.Thread(target=server.serve_forever, daemon=True).start()
    try:
        env = os.environ.copy()
        env.update({'POD_UID': 'fixture-uid', 'POD_IP': '10.42.0.9', 'EUREKA_SERVER_URL': 'http://127.0.0.1:8761/eureka/'})
        binary = Path('/tmp/hook-bin')
        binary.mkdir()
        sleep = binary / 'sleep'
        sleep.write_text('#!/bin/sh\nprintf "%s" "$1" > /tmp/drain-seconds\n')
        sleep.chmod(0o755)
        env['PATH'] = str(binary) + os.pathsep + env['PATH']
        for app in apps:
            state.update({'status': 'UP', 'health': 200, 'id': app['name'] + ':fixture-uid', 'ip': env['POD_IP'], 'malformed': False})

            def startup(passed):
                result = subprocess.run(app['startup'], env=env, capture_output=True, timeout=10)
                assert (result.returncode == 0) == passed, app['name'] + ': startup result'

            startup(True)
            state['status'] = 'STARTING'
            startup(False)
            state['status'] = 'UP'
            state['id'] = 'another-instance'
            startup(False)
            state['id'] = app['name'] + ':fixture-uid'
            state['ip'] = '10.42.0.99'
            startup(False)
            state['ip'] = env['POD_IP']
            state['malformed'] = True
            startup(False)
            state['malformed'] = False
            state['health'] = 503
            startup(False)
            state['health'] = 200
            result = subprocess.run(app['drain'], env=env, capture_output=True, timeout=10)
            assert result.returncode == 0, app['name'] + ': drain failed'
            assert state['puts'][-1] == '/eureka/apps/' + app['name'].upper() + '/' + app['name'] + ':fixture-uid/status?value=OUT_OF_SERVICE'
            assert Path('/tmp/drain-seconds').read_text() == '75'
            startup(False)  # Draining instances must never pass the initial traffic gate.
            if app['name'] == 'api-gateway':
                state['putStatus'] = 503
                result = subprocess.run(app['drain'], env=env, capture_output=True, timeout=15)
                assert result.returncode != 0, 'Failed drain must be visible as a failed preStop hook'
                assert Path('/tmp/drain-seconds').read_text() == '75', 'Failed drain must still allow propagation time'
                state['putStatus'] = 200
            print('HOOK_PASSED ' + app['name'], flush=True)
        env['POD_NAME'] = 'discovery-server-0'
        command = json.loads(Path('/fixture/discovery.json').read_text())

        def peer_startup(passed):
            result = subprocess.run(command, env=env, capture_output=True, timeout=10)
            assert (result.returncode == 0) == passed, 'Eureka startup registry comparison'

        peer_startup(True)
        state['localApps'] = []
        peer_startup(False)  # Healthy peer has a record the new Pod has not copied.
        state['localApps'] = [sample]
        peer_startup(True)
        state['localApps'] = []
        state['peerStatus'] = 503
        peer_startup(True)  # Cold bootstrap with no serving peer must remain possible.
        print('HOOK_PASSED discovery-server: sync gate and cold bootstrap', flush=True)
    finally:
        for server in servers:
            server.shutdown()
            server.server_close()


def run_docker():
    import yaml
    root = Path(__file__).resolve().parents[2]
    apps = []
    base = None
    for name in ['api-gateway', 'booking-service', 'payment-service', 'ticket-service', 'event-service', 'user-service']:
        doc = yaml.safe_load((root / 'deploy/k8s' / name / 'deployment.yaml').read_text())
        container = doc['spec']['template']['spec']['containers'][0]
        directory = 'infrastructure' if name == 'api-gateway' else 'services'
        image = next(line[5:] for line in (root / directory / name / 'Dockerfile').read_text().splitlines() if line.startswith('FROM '))
        base = base or image
        assert image == base, 'Hook fixtures must use each app runtime base'
        apps.append({'name': name, 'port': container['ports'][0]['containerPort'], 'startup': container['startupProbe']['exec']['command'],
                     'drain': container['lifecycle']['preStop']['exec']['command']})
    with tempfile.TemporaryDirectory(prefix='ft-rollout-hooks-') as directory:
        context = Path(directory)
        (context / 'apps.json').write_text(json.dumps(apps))
        discovery = yaml.safe_load((root / 'deploy/k8s/discovery-server/statefulset.yaml').read_text())
        discovery_base = next(line[5:] for line in (root / 'infrastructure/discovery-server/Dockerfile').read_text().splitlines() if line.startswith('FROM '))
        assert discovery_base == base, 'Eureka hook fixture must use its actual runtime base'
        (context / 'discovery.json').write_text(json.dumps(discovery['spec']['template']['spec']['containers'][0]['startupProbe']['exec']['command']))
        (context / 'fixture.py').write_text(Path(__file__).read_text())
        (context / 'Dockerfile').write_text('FROM ' + base + '\nRUN apk add --no-cache curl jq python3 && addgroup -S -g 10001 app && adduser -S -D -u 10001 -G app app\nCOPY . /fixture/\nUSER 10001:10001\nCMD ["python3", "/fixture/fixture.py", "--container"]\n')
        tag = 'flash-ticket-rollout-hooks:fixture'
        subprocess.run(['docker', 'build', '--quiet', '-t', tag, str(context)], check=True, timeout=300)
        subprocess.run(['docker', 'run', '--rm', '--add-host', 'discovery-server-1.discovery-server-peers.flash-ticket.svc.cluster.local:127.0.0.2', tag], check=True, timeout=120)


if __name__ == '__main__':
    if '--container' in sys.argv:
        container_fixtures()
    else:
        run_docker()
