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
    assert 'ports' not in values, 'Do not redirect the entire HTTP entrypoint/Ngrok'

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
    assert {s['spec']['ports'][0]['port'] for s in services.values()} == {5173, 8080}

    for name in ['api-gateway', 'booking-service', 'payment-service', 'ticket-service', 'event-service', 'user-service']:
        config = documents(f'deploy/k8s/{name}/configmap.yaml')[0]['data']
        assert config['KEYCLOAK_ISSUER_URI'] == f'https://{AUTH}/realms/flash-ticket'
        if name == 'api-gateway':
            assert f'https://{APP}' in config['CORS_ALLOWED_ORIGINS'].split(',')
    print('HTTPS configuration contract: PASS')


if __name__ == '__main__':
    check()
