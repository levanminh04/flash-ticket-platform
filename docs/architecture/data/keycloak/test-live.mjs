// Read-only inspection of an explicitly prepared local test realm. Never imports or modifies a realm.
import assert from 'node:assert/strict';
const endpoint = process.env.B12_KC_TEST_BASE_URL;
const token = process.env.B12_KC_TEST_ADMIN_TOKEN;
assert.equal(process.env.B12_KC_DISPOSABLE_TEST, 'yes', 'Confirm this is a disposable test environment.');
assert.ok(endpoint && token, 'Set the local test base URL and a short-lived admin read token via environment.');
const base = new URL(endpoint);
assert.ok(['localhost', '127.0.0.1', '[::1]'].includes(base.hostname), 'Only loopback is allowed.');
assert.ok(['http:', 'https:'].includes(base.protocol) && base.port, 'Use an explicit local port.');
assert.ok(!base.username && !base.password && !base.search && !base.hash && base.pathname === '/', 'Base URL must not contain credential, path or query.');
async function get(relative) {
  const response = await fetch(new URL(relative, base), {
    headers: { authorization: `Bearer ${token}` }, redirect: 'error', signal: AbortSignal.timeout(10000)
  });
  assert.equal(response.status, 200, `Read failed: ${relative}; status ${response.status}. Response omitted.`);
  return response.json();
}
try {
  const root = 'admin/realms/flash-ticket';
  const realm = await get(root);
  assert.equal(realm.verifyEmail, false);
  assert.equal(realm.registrationAllowed, false);
  assert.equal(realm.resetPasswordAllowed, false);
  assert.equal(realm.sslRequired, 'all');
  assert.equal(realm.bruteForceProtected, true);
  const defaults = await get(`${root}/roles/default-roles-flash-ticket/composites/realm`);
  assert.ok(defaults.every(r => !['BUYER', 'ORGANIZER', 'ADMIN'].includes(r.name)), 'Default role grants a business role.');
  const clients = await get(`${root}/clients`);
  for (const name of ['flash-ticket-web', 'flash-ticket-android', 'flash-ticket-api', 'flash-ticket-user-management']) {
    const client = clients.find(c => c.clientId === name);
    assert.ok(client, `Missing candidate client ${name}.`);
    assert.equal(client.fullScopeAllowed, false);
    assert.equal(client.serviceAccountsEnabled, false);
    assert.equal(client.directAccessGrantsEnabled, false);
    assert.equal(client.implicitFlowEnabled, false);
    if (['flash-ticket-web', 'flash-ticket-android'].includes(name)) {
      assert.equal(client.publicClient, true);
      assert.equal(client.standardFlowEnabled, true);
      assert.equal(client.attributes['pkce.code.challenge.method'], 'S256');
      assert.ok(client.redirectUris.length > 0 && client.redirectUris.every(u => u.startsWith('https://') && !u.includes('*')));
      assert.ok((client.webOrigins ?? []).every(u => u.startsWith('https://') && !u.includes('*')));
      const linkedScopes = await get(`${root}/clients/${encodeURIComponent(client.id)}/default-client-scopes`);
      assert.ok(linkedScopes.some(s => s.name === 'flash-ticket-human'));
    }
    if (name === 'flash-ticket-user-management') assert.equal(client.enabled, false);
    if (name === 'flash-ticket-api') assert.equal(client.bearerOnly, true);
  }
  const scopes = await get(`${root}/client-scopes`);
  const human = scopes.find(s => s.name === 'flash-ticket-human');
  assert.ok(human);
  const mappers = await get(`${root}/client-scopes/${encodeURIComponent(human.id)}/protocol-mappers/models`);
  assert.ok(mappers.some(m => m.protocolMapper === 'oidc-audience-mapper' && m.config['included.custom.audience'] === 'flash-ticket-api' && m.config['access.token.claim'] === 'true'));
  const roles = await get(`${root}/client-scopes/${encodeURIComponent(human.id)}/scope-mappings/realm`);
  assert.deepEqual(roles.map(r => r.name).sort(), ['ADMIN', 'BUYER', 'ORGANIZER']);
  console.log('PASS: imported candidate realm configuration read back from local Keycloak. No JWT exchange, PKCE challenge, role-grant flow or application authorization proven.');
} catch (error) {
  // Do not emit HTTP bodies, tokens, realm users or stack traces into logs.
  console.error(`FAIL: ${error instanceof assert.AssertionError ? error.message : 'Local Keycloak read failed; inspect the environment without logging credentials.'}`);
  process.exitCode = 1;
}
