"""Exercise migration/cutover/rollback with a fake API; no real cluster mutations."""
import copy
import importlib.util
import json
from pathlib import Path
import tempfile
import unittest
from unittest.mock import Mock, patch

SOURCE = Path(__file__).resolve().parents[2] / 'deploy/scripts/migrate-k8s-discovery.py'
SPEC = importlib.util.spec_from_file_location('discovery_migration', SOURCE)
migration = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(migration)
OLD_IMAGE = 'ghcr.io/levanminh04/flash-ticket-platform/discovery-server@sha256:' + 'b' * 64
NEW_IMAGE = 'ghcr.io/levanminh04/flash-ticket-platform/discovery-server@sha256:' + 'c' * 64


class MigrationTests(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory(prefix='ft-peer-fixture-')
        self.addCleanup(self.temp.cleanup)
        self.root = Path(self.temp.name) / ('1700000000-' + 'a' * 12 + '-99')
        self.root.mkdir()
        self.calls = []
        self.workload = None
        self.partial = False
        self.old_incomplete = False
        self.memory = 3072
        self.deleted_legacy = False
        self.restart = False
        self.retired_application = None
        self.pod_items = []
        for n, app in enumerate(sorted(migration.APPS)):
            self.pod_items.append(self.pod(app, app + '-uid', '10.42.0.' + str(n + 10)))
        self.legacy = {'apiVersion': 'apps/v1', 'kind': 'Deployment', 'metadata': {'name': 'discovery-server'},
                       'spec': {'replicas': 1, 'template': {'metadata': {}, 'spec': {'containers': [{'image': OLD_IMAGE}]}}}}
        self.service = {'apiVersion': 'v1', 'kind': 'Service', 'metadata': {'name': 'discovery-server'},
                        'spec': {'clusterIP': '10.43.0.5', 'selector': {'app.kubernetes.io/name': 'discovery-server'}, 'ports': [{'port': 8761}]}}
        self.config = {'kind': 'ConfigMap', 'metadata': {'name': 'discovery-server-config'}, 'data': {'legacy': 'original'}}
        desired_config = {'kind': 'ConfigMap', 'metadata': {'name': 'discovery-server-config'},
                          'data': {'SPRING_APPLICATION_JSON': json.dumps({'eureka': {'client': {'service-url': {'defaultZone': 'http://peer-0/eureka/,http://peer-1/eureka/'}}}})}}
        desired_service = copy.deepcopy(self.service)
        desired_service['spec']['selector'][migration.GENERATION] = 'peers'
        statefulset = {'kind': 'StatefulSet', 'metadata': {'name': 'discovery-server'},
                       'spec': {'replicas': 2, 'template': {'metadata': {}, 'spec': {'containers': [{'image': NEW_IMAGE, 'env': []}]}}}}
        for name, document in {'legacy-live': self.legacy, 'service': desired_service, 'configmap': desired_config,
                               'statefulset': statefulset, 'peer-service': {'kind': 'Service', 'metadata': {'name': 'discovery-server-peers'}}}.items():
            migration.write(self.root / (name + '.json'), document)
        self.addCleanup(patch.stopall)
        patch.object(migration, 'kube', side_effect=self.kube).start()
        patch.object(migration, 'registry', side_effect=self.registry).start()
        patch.object(migration.time, 'sleep').start()
        patch.object(migration, 'Path', side_effect=lambda value: Mock(read_text=lambda: 'MemAvailable: ' + str(self.memory * 1024) + ' kB\n')
                     if value == '/proc/meminfo' else Path(value)).start()

    def pod(self, app, uid, ip, generation=None):
        labels = {'app.kubernetes.io/name': app}
        if generation:
            labels[migration.GENERATION] = generation
        return {'metadata': {'name': uid, 'uid': uid, 'labels': labels},
                'spec': {'containers': [{'ports': [{'containerPort': 8082}]}]},
                'status': {'podIP': ip, 'containerStatuses': [{'ready': True, 'restartCount': 0}]}}

    def kube(self, *args):
        self.calls.append(args)
        if args[:2] == ('get', 'pods'):
            rows = copy.deepcopy(self.pod_items)
            if not self.deleted_legacy:
                rows.append(self.pod('discovery-server', 'legacy-pod', '10.42.0.1', 'legacy'))
            if self.workload:
                for i in range(2):
                    pod = self.pod('discovery-server', 'peer-' + str(i), '10.42.0.' + str(i + 2), 'peers')
                    if self.restart:
                        pod['status']['containerStatuses'][0]['restartCount'] = 1
                    rows.append(pod)
            return json.dumps({'items': rows})
        if args[:2] == ('get', 'service'):
            return json.dumps(self.service)
        if args[:2] == ('get', 'configmap'):
            return json.dumps(self.config)
        if args[0] == 'apply':
            document = migration.read(Path(args[-1]))
            if document['kind'] == 'StatefulSet':
                self.workload = document
            elif document['metadata']['name'] == 'discovery-server' and document['kind'] == 'Service':
                self.service = document
            elif document['kind'] == 'ConfigMap':
                self.config = document
            return ''
        if args[:2] == ('patch', 'pod'):
            replacement = next(p for p in self.pod_items if p['metadata']['name'] == args[2])
            app = replacement['metadata']['labels']['app.kubernetes.io/name']
            old = next(p for p in self.pod_items if p['metadata']['uid'] == app + '-uid')
            old['metadata']['deletionTimestamp'] = 'fixture-retired'
            replacement['status']['conditions'] = [{'type': migration.GATE, 'status': 'True'}]
            self.retired_application = app
        if args[:2] == ('get', 'endpointslices'):
            return json.dumps({'items': [{'endpoints': [{'conditions': {'ready': True}, 'addresses': ['10.42.0.90']}]}]})
        if args[:2] == ('delete', 'statefulset'):
            self.workload = None
        if args[:2] == ('delete', 'deployment'):
            self.deleted_legacy = True
        return ''

    def registry(self, address):
        records = {}
        for p in self.pod_items:
            app = p['metadata']['labels']['app.kubernetes.io/name']
            key = app + ':' + p['metadata']['uid']
            records[key] = {'app': app.upper(), 'instanceId': key, 'status': 'UP', 'ipAddr': p['status']['podIP'], 'port': {'$': 8082}}
        if (self.partial and address.endswith('.3:8761')) or (self.old_incomplete and address.endswith('.1:8761')):
            records.pop(next(iter(records)))
        return records

    def test_success_retains_legacy_until_final_check_and_creates_a_valid_peer_baseline(self):
        original_check = migration.check

        def check_before_cutover(root):
            self.assertEqual(self.service['spec']['selector'][migration.GENERATION], 'legacy')
            original_check(root)

        with patch.object(migration, 'check', side_effect=check_before_cutover):
            migration.migrate(self.root)
        self.assertFalse(self.deleted_legacy)
        self.assertEqual(self.service['spec']['selector'][migration.GENERATION], 'peers')
        migration.finish(self.root)
        self.assertTrue(self.deleted_legacy)
        baseline = self.root.parent / (self.root / 'compatible-previous').read_text().strip()
        self.assertEqual((baseline / 'image').read_text().strip(), NEW_IMAGE)
        self.assertEqual(migration.read(baseline / 'statefulset.json')['spec']['template']['spec']['containers'][0]['image'], NEW_IMAGE)
        self.assertEqual(migration.read(self.root / 'legacy-deployment.json')['spec']['template']['spec']['containers'][0]['image'], OLD_IMAGE)
        self.assertFalse((baseline / 'deployment.json').exists())
        migration.finish(self.root)  # finalization is idempotent after success

    def test_final_peer_rollout_failure_keeps_clients_on_legacy(self):
        original_kube = self.kube

        def fail_final_rollout(*args):
            result = original_kube(*args)
            if args[:2] == ('rollout', 'status') and not self.workload['spec']['template']['spec']['containers'][0]['env']:
                self.partial = True
            return result

        with patch.object(migration, 'kube', side_effect=fail_final_rollout):
            with self.assertRaisesRegex(RuntimeError, 'PEER_SYNC_TIMEOUT PEER_REGISTRY_INCOMPLETE'):
                migration.migrate(self.root)
        self.assertEqual(self.service['spec']['selector'][migration.GENERATION], 'legacy')
        self.assertFalse((self.root / 'client-cutover.json').exists())
        diagnostics = migration.read(self.root / 'peer-sync-diagnostics.json')
        self.assertTrue(any(peer['missingOrDifferent'] for peer in diagnostics['peers']))
        self.assertFalse(self.deleted_legacy)

    def test_partial_registry_never_cuts_over_and_can_restore(self):
        self.partial = True
        with self.assertRaisesRegex(RuntimeError, 'PEER_SYNC_TIMEOUT'):
            migration.migrate(self.root)
        self.assertEqual(self.service['spec']['selector'][migration.GENERATION], 'legacy')
        self.assertFalse(self.deleted_legacy)
        migration.restore(self.root)
        self.assertIsNone(self.workload)
        self.assertNotIn(migration.GENERATION, self.service['spec']['selector'])
        self.assertEqual(self.config['data'], {'legacy': 'original'})

    def test_failed_restore_keeps_peer_workload_and_service(self):
        migration.migrate(self.root)
        self.old_incomplete = True
        self.partial = True
        # Make every candidate incomplete, so restore cannot select a valid registry source.
        with patch.object(migration, 'registry', return_value={}):
            with self.assertRaisesRegex(AssertionError, 'LEGACY_REGISTRY_INCOMPLETE_KEEP_PEERS'):
                migration.restore(self.root)
        self.assertIsNotNone(self.workload)
        self.assertEqual(self.service['spec']['selector'][migration.GENERATION], 'peers')

    def test_memory_guard_precedes_cluster_mutation(self):
        self.memory = 700
        with self.assertRaisesRegex(AssertionError, 'MIGRATION_MEMORY_HEADROOM_TOO_LOW'):
            migration.migrate(self.root)
        self.assertFalse(any(call[0] in ('apply', 'label', 'delete') for call in self.calls))

    def test_restarted_peer_fails_before_cutover(self):
        self.restart = True
        with self.assertRaisesRegex(RuntimeError, 'PEER_SYNC_TIMEOUT'):
            migration.migrate(self.root)
        self.assertEqual(self.service['spec']['selector'][migration.GENERATION], 'legacy')

    def test_final_registry_failure_does_not_delete_old_server(self):
        migration.migrate(self.root)
        self.partial = True
        with self.assertRaisesRegex(AssertionError, 'PEER_REGISTRY_INCOMPLETE'):
            migration.finish(self.root)
        self.assertFalse(self.deleted_legacy)

    def rollback_pods(self):
        result = [self.pod('discovery-server', 'peer-' + str(i), '10.42.0.' + str(i + 2), 'peers') for i in range(2)]
        for pod in result:
            pod['metadata']['ownerReferences'] = [{'kind': 'StatefulSet', 'name': 'discovery-server'}]
            pod['spec']['containers'][0]['image'] = OLD_IMAGE
        result[1]['status']['containerStatuses'][0]['ready'] = False
        return result

    def test_rollback_recreates_only_the_unready_wrong_image(self):
        with patch.object(migration, 'pods', return_value=self.rollback_pods()):
            migration.recover(self.root)
        deletes = [call for call in self.calls if call[:2] == ('delete', 'pod')]
        self.assertEqual(len(deletes), 1)
        self.assertEqual(deletes[0][2], 'peer-1')

    def test_rollback_does_not_delete_without_a_ready_peer(self):
        items = self.rollback_pods()
        items[0]['status']['containerStatuses'][0]['ready'] = False
        with patch.object(migration, 'pods', return_value=items):
            with self.assertRaisesRegex(AssertionError, 'NO_READY_PEER_FOR_ROLLBACK'):
                migration.recover(self.root)
        self.assertFalse(any(call[:2] == ('delete', 'pod') for call in self.calls))

    def test_rollback_leaves_unready_pod_with_the_correct_target_image(self):
        items = self.rollback_pods()
        items[1]['spec']['containers'][0]['image'] = NEW_IMAGE
        with patch.object(migration, 'pods', return_value=items):
            migration.recover(self.root)
        self.assertFalse(any(call[:2] == ('delete', 'pod') for call in self.calls))

    def application_rollout(self, name='booking-service'):
        document = {'kind': 'Deployment', 'metadata': {'name': name}, 'spec': {
            'replicas': 1, 'minReadySeconds': 75, 'strategy': {'type': 'RollingUpdate'},
            'template': {'metadata': {}, 'spec': {'containers': [{'image': 'new-booking-image'}]}}}}
        migration.write(self.root / 'deployment.json', document)
        migration.write(self.root / 'hpa.json', {'kind': 'HorizontalPodAutoscaler'})
        (self.root / 'image').write_text('new-booking-image')
        self.workload = migration.read(self.root / 'statefulset.json')
        migration.prepare(self.root)

    def test_first_application_rollout_holds_old_pod_and_does_not_reset_hpa(self):
        self.application_rollout()
        document = migration.read(self.root / 'deployment-effective.json')
        self.assertEqual(document['spec']['template']['spec']['readinessGates'], [{'conditionType': migration.GATE}])
        self.assertNotIn('replicas', document['spec'])
        old = migration.read(self.root / 'bridge-legacy.json')
        self.assertEqual(old['metadata']['uid'], 'booking-service-uid')

    def test_first_rollout_waits_for_registered_replacement_before_draining(self):
        self.application_rollout()
        replacement = self.pod('booking-service', 'replacement-uid', '10.42.0.90')
        replacement['spec']['containers'][0]['image'] = 'new-booking-image'
        self.pod_items.append(replacement)
        response = Mock(status=200)
        response.__enter__ = Mock(return_value=response)
        response.__exit__ = Mock(return_value=False)
        with patch.object(migration.urllib.request, 'urlopen', return_value=response) as http:
            migration.bridge(self.root)
        self.assertEqual(http.call_count, 2)
        self.assertTrue(all(call.args[0].method == 'PUT' and 'OUT_OF_SERVICE' in call.args[0].full_url for call in http.call_args_list))
        self.assertEqual(migration.read(self.root / 'deployment-effective.json')['spec']['minReadySeconds'], 75)
        self.assertNotIn('readinessGates', migration.read(self.root / 'deployment-effective.json')['spec']['template']['spec'])
        self.assertTrue(any(call[:2] == ('patch', 'pod') for call in self.calls))
        self.assertTrue((self.root / 'bridge-drained.json').exists())

    def test_first_rollout_does_not_drain_without_a_replacement(self):
        self.application_rollout()
        with patch.object(migration.urllib.request, 'urlopen') as http:
            with self.assertRaisesRegex(RuntimeError, 'REPLACEMENT_NOT_READY_FOR_LEGACY_DRAIN'):
                migration.bridge(self.root)
        http.assert_not_called()
        self.assertEqual(migration.read(self.root / 'deployment-effective.json')['spec']['template']['spec']['readinessGates'], [{'conditionType': migration.GATE}])

    def test_first_rollout_rejects_ambiguous_two_legacy_pods(self):
        self.application_rollout()
        self.pod_items.append(self.pod('booking-service', 'second-old-uid', '10.42.0.91'))
        with self.assertRaisesRegex(AssertionError, 'INITIAL_DRAIN_REQUIRES_ONE_LEGACY_POD'):
            migration.prepare(self.root)

    def test_gateway_removes_only_service_label_after_new_endpoint_is_ready(self):
        self.application_rollout('api-gateway')
        replacement = self.pod('api-gateway', 'replacement-uid', '10.42.0.90')
        replacement['spec']['containers'][0]['image'] = 'new-booking-image'
        self.pod_items.append(replacement)
        response = Mock(status=200)
        response.__enter__ = Mock(return_value=response)
        response.__exit__ = Mock(return_value=False)
        with patch.object(migration.urllib.request, 'urlopen', return_value=response):
            migration.bridge(self.root)
        endpoint_check = next(i for i, call in enumerate(self.calls) if call[:2] == ('get', 'endpointslices'))
        remove_label = next(i for i, call in enumerate(self.calls) if migration.SERVING + '=false' in call)
        self.assertLess(endpoint_check, remove_label)
        self.assertTrue(all('app.kubernetes.io/name-' not in call for call in self.calls))


if __name__ == '__main__':
    unittest.main()
