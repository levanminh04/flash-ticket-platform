"""Static contract for the production HTTPS cutover; no network or secrets."""
from pathlib import Path

import yaml

ROOT = Path(__file__).resolve().parents[2]
APP = 'flashticket.sondimid.cloud'
AUTH = 'auth.flashticket.sondimid.cloud'
ANNOTATION = 'traefik.ingress.kubernetes.io/'


def documents(path):
    return list(yaml.safe_load_all((ROOT / path).read_text(encoding='utf-8')))


def check():
    chart = documents('deploy/https/traefik-config.yaml')[0]
    values = yaml.safe_load(chart['spec']['valuesContent'])
    assert values['deployment']['replicas'] == 1
    assert values['updateStrategy']['type'] == 'Recreate'
    assert values['persistence']['enabled']
    acme = values['certificatesResolvers']['letsencrypt']['acme']
    assert acme['storage'] == '/data/acme.json'
    assert acme['httpChallenge']['entryPoint'] == 'web'
    assert acme['email']
    init = values['deployment']['initContainers'][0]
    assert 'chmod 600 /data/acme.json' in init['command'][-1]
    assert '> /data/acme.json' not in init['command'][-1]
    assert 'ports' not in values, 'Do not redirect unrelated legacy HTTP clients'

    routes = documents('deploy/https/ingress.yaml')
    redirect = next(d for d in routes if d['kind'] == 'Middleware')
    assert redirect['spec']['redirectScheme'] == {'scheme': 'https', 'permanent': True}
    ingresses = {d['metadata']['name']: d for d in routes if d['kind'] == 'Ingress'}
    http = ingresses['domain-http-redirect']
    assert {r['host'] for r in http['spec']['rules']} == {APP, AUTH}
    assert http['metadata']['annotations'][ANNOTATION + 'router.entrypoints'] == 'web'
    assert http['metadata']['annotations'][ANNOTATION + 'router.middlewares'] == 'flash-ticket-https-redirect@kubernetescrd'
    assert int(http['metadata']['annotations'][ANNOTATION + 'router.priority']) == 1000

    api = documents('deploy/k8s/api-gateway/ingress.yaml')[0]
    for ingress, host, priority in [(api, APP, 200), (ingresses['frontend-https'], APP, 100),
                                    (ingresses['keycloak-https'], AUTH, 100)]:
        annotations = ingress['metadata']['annotations']
        assert annotations[ANNOTATION + 'router.entrypoints'] == 'websecure'
        assert annotations[ANNOTATION + 'router.tls'] == 'true'
        assert annotations[ANNOTATION + 'router.tls.certresolver'] == 'letsencrypt'
        assert int(annotations[ANNOTATION + 'router.priority']) == priority
        assert ingress['spec']['tls'] == [{'hosts': [host]}]
        assert {r['host'] for r in ingress['spec']['rules']} == {host}
    paths = api['spec']['rules'][0]['http']['paths']
    assert [(p['path'], p['pathType']) for p in paths] == [('/api', 'Prefix')]
    assert ANNOTATION + 'router.middlewares' not in api['metadata']['annotations']

    legacy = documents('deploy/https/legacy-api.yaml')[0]
    assert legacy['metadata']['annotations'] == {
        ANNOTATION + 'router.entrypoints': 'web', ANNOTATION + 'router.priority': '1'}
    assert 'tls' not in legacy['spec']
    assert 'host' not in legacy['spec']['rules'][0]
    assert legacy['spec']['rules'][0]['http']['paths'] == paths

    external = documents('deploy/https/external-services.yaml')
    services = {d['metadata']['name']: d for d in external if d['kind'] == 'Service'}
    for endpoint in (d for d in external if d['kind'] == 'EndpointSlice'):
        service = services[endpoint['metadata']['labels']['kubernetes.io/service-name']]
        assert 'selector' not in service['spec']
        assert len(endpoint['endpoints']) == 1
        assert endpoint['endpoints'][0]['addresses'] == ['172.31.41.236']
        assert endpoint['endpoints'][0].get('conditions', {}).get('ready', True)
        assert endpoint['ports'][0]['port'] == service['spec']['ports'][0]['port']
        assert endpoint['ports'][0]['name'] == service['spec']['ports'][0]['name']
    assert set(services) == {'keycloak-external'}
    assert len(external) == 2
    assert services['keycloak-external']['spec']['ports'][0]['port'] == 8080

    deployment = documents('deploy/k8s/frontend/deployment.yaml')[0]
    spec = deployment['spec']
    assert spec['replicas'] == 1
    assert spec['minReadySeconds'] == 10 and spec['progressDeadlineSeconds'] == 300
    assert spec['strategy'] == {'type': 'RollingUpdate', 'rollingUpdate': {'maxUnavailable': 0, 'maxSurge': 1}}
    pod = spec['template']['spec']
    assert pod['automountServiceAccountToken'] is False
    assert pod['securityContext'] == {'runAsNonRoot': True, 'runAsUser': 10001, 'runAsGroup': 10001}
    assert pod['terminationGracePeriodSeconds'] == 30
    assert not pod.get('hostNetwork', False)
    container = pod['containers'][0]
    assert container['ports'] == [{'name': 'http', 'containerPort': 5173}]
    assert not container.get('env') and not container.get('envFrom')
    assert container['securityContext'] == {'readOnlyRootFilesystem': True, 'allowPrivilegeEscalation': False,
                                            'capabilities': {'drop': ['ALL']}}
    assert container['resources'] == {'requests': {'cpu': '25m', 'memory': '32Mi'}, 'limits': {'memory': '64Mi'}}
    assert pod['volumes'] == [{'name': 'tmp', 'emptyDir': {'medium': 'Memory', 'sizeLimit': '64Mi'}}]
    assert container['volumeMounts'] == [{'name': 'tmp', 'mountPath': '/tmp'}]
    assert container['lifecycle']['preStop']['exec']['command'] == ['sh', '-c', 'sleep 5']
    for probe, period, failures in [('startupProbe', 2, 30), ('readinessProbe', 2, 2), ('livenessProbe', 10, 3)]:
        assert container[probe] == {'httpGet': {'path': '/index.html', 'port': 'http'}, 'periodSeconds': period,
                                    'timeoutSeconds': 2, 'failureThreshold': failures}
    frontend = documents('deploy/k8s/frontend/service.yaml')[0]
    assert frontend['spec']['type'] == 'ClusterIP'
    assert frontend['spec']['selector'] == spec['selector']['matchLabels']
    assert all(spec['template']['metadata']['labels'][k] == v for k, v in frontend['spec']['selector'].items())
    assert frontend['spec']['ports'] == [{'name': 'http', 'port': 5173, 'targetPort': 'http'}]
    for ingress in [http, ingresses['frontend-https']]:
        rule = next(r for r in ingress['spec']['rules'] if r['host'] == APP)
        assert rule['http']['paths'][0]['backend']['service'] == {'name': 'frontend', 'port': {'number': 5173}}
    for ingress in [http, ingresses['keycloak-https']]:
        rule = next(r for r in ingress['spec']['rules'] if r['host'] == AUTH)
        assert rule['http']['paths'][0]['backend']['service'] == {'name': 'keycloak', 'port': {'number': 8080}}
    assert not (ROOT / 'deploy/k8s/frontend/ingress.yaml').exists()
    assert not (ROOT / 'deploy/k8s/frontend/hpa.yaml').exists()
    assert 'STOPSIGNAL SIGQUIT' in (ROOT / 'frontend/Dockerfile').read_text(encoding='utf-8')
    compose_script = (ROOT / 'deploy/scripts/deploy-service.sh').read_text(encoding='utf-8')
    assert '$svc == frontend && -f $APP_ROOT/state/k8s/frontend.current' in compose_script
    assert compose_script.index('state/k8s/frontend.current') < compose_script.index('docker login')

    for name in ['api-gateway', 'booking-service', 'payment-service', 'ticket-service', 'event-service', 'user-service']:
        config = documents(f'deploy/k8s/{name}/configmap.yaml')[0]['data']
        assert config['KEYCLOAK_ISSUER_URI'] == f'https://{AUTH}/realms/flash-ticket'
        if name == 'api-gateway':
            assert f'https://{APP}' in config['CORS_ALLOWED_ORIGINS'].split(',')
    payment = documents('deploy/k8s/payment-service/configmap.yaml')[0]['data']
    public_env = (ROOT / 'deploy/https/public.env.example').read_text(encoding='utf-8')
    local_env = (ROOT / 'services/payment-service/.env.example').read_text(encoding='utf-8')
    for key, path in [('VNPAY_RETURN_URL', 'return'), ('VNPAY_IPN_URL', 'ipn')]:
        url = f'https://{APP}/api/v1/payments/vnpay/{path}'
        assert payment[key] == url
        assert f'{key}={url}' in public_env
        assert f'{key}=http://localhost:8083/api/v1/payments/vnpay/{path}' in local_env
    assert payment['VNPAY_APP_RETURN_URL'] == 'flash-ticket://payment-return'
    for path in ['deploy/app/payment-service.yml', 'deploy/docker-compose.payment.yml']:
        source = (ROOT / path).read_text(encoding='utf-8')
        assert set(documents(path)[0]['services']) == {'payment-service'}
        assert 'ngrok' not in source.lower()
    local_payment = documents('deploy/docker-compose.payment.yml')[0]['services']['payment-service']
    for key in ['VNPAY_RETURN_URL', 'VNPAY_IPN_URL']:
        assert local_payment['environment'][key].startswith('${' + key + ':?')
    assert 'NGROK_' not in local_env
    assert 'ngrok' not in (ROOT / 'deploy/scripts/deploy-service.sh').read_text(encoding='utf-8').lower()
    for path in ['deploy/app/ngrok.yml', 'deploy/ngrok/payment.yml']:
        assert not (ROOT / path).exists()
    print('HTTPS configuration contract: PASS')


if __name__ == '__main__':
    check()
