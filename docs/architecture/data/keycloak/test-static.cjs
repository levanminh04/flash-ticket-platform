'use strict';
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const realm = JSON.parse(fs.readFileSync(path.join(__dirname, 'flash-ticket-realm.candidate.json'), 'utf8'));
assert.equal(realm.realm, 'flash-ticket');
assert.equal(realm.verifyEmail, false, 'FR-50 must not silently acquire mandatory email verification.');
assert.equal(realm.registrationAllowed, false, 'Registration remains blocked until human-only BUYER provisioning exists.');
assert.equal(realm.resetPasswordAllowed, false, 'Do not claim password reset works without SMTP provisioning.');
assert.equal(realm.sslRequired, 'all');
assert.equal(realm.bruteForceProtected, true);
assert.deepEqual(realm.defaultRoles, []);
assert.deepEqual(realm.roles.realm.map(r => r.name).sort(), ['ADMIN', 'BUYER', 'ORGANIZER']);
assert.ok(realm.roles.realm.every(r => !r.composite && !r.clientRole));
const forbidden = new Set(['users', 'credentials', 'secret', 'privateKey', 'certificate', 'components', 'loginTheme', 'eventsListeners', 'smtpServer', 'identityProviders', 'userFederationProviders']);
function noSensitiveContent(value) {
  if (!value || typeof value !== 'object') return;
  for (const [key, child] of Object.entries(value)) {
    assert.ok(!forbidden.has(key), `Forbidden fixture key: ${key}`);
    noSensitiveContent(child);
  }
}
noSensitiveContent(realm);
assert.deepEqual(realm.clients.map(c => c.clientId), ['flash-ticket-web', 'flash-ticket-android', 'flash-ticket-api', 'flash-ticket-user-management']);
for (const c of realm.clients) {
  assert.equal(c.protocol, 'openid-connect');
  assert.equal(c.fullScopeAllowed, false);
  assert.equal(c.directAccessGrantsEnabled, false);
  assert.equal(c.serviceAccountsEnabled, false);
  assert.equal(c.implicitFlowEnabled, false);
}
for (const clientId of ['flash-ticket-web', 'flash-ticket-android']) {
  const c = realm.clients.find(c => c.clientId === clientId);
  assert.equal(c.publicClient, true);
  assert.equal(c.standardFlowEnabled, true);
  assert.equal(c.attributes['pkce.code.challenge.method'], 'S256');
  assert.deepEqual(c.defaultClientScopes, ['flash-ticket-human']);
  assert.ok(c.redirectUris.length === 1 && c.redirectUris.every(u => /^https:\/\/[a-z]+\.example\.invalid\/oidc\/callback$/.test(u)));
  assert.ok(c.webOrigins.every(u => /^https:\/\/[a-z]+\.example\.invalid$/.test(u)));
}
assert.equal(realm.clients.find(c => c.clientId === 'flash-ticket-api').bearerOnly, true);
const management = realm.clients.find(c => c.clientId === 'flash-ticket-user-management');
assert.equal(management.enabled, false);
assert.equal(management.standardFlowEnabled, false);
assert.deepEqual(management.defaultClientScopes, []);
assert.equal(realm.clientScopes.length, 1);
const scope = realm.clientScopes[0];
const audience = scope.protocolMappers.find(m => m.protocolMapper === 'oidc-audience-mapper');
assert.equal(audience.config['included.custom.audience'], 'flash-ticket-api');
assert.equal(audience.config['access.token.claim'], 'true');
assert.equal(audience.config['id.token.claim'], 'false');
const roles = scope.protocolMappers.find(m => m.protocolMapper === 'oidc-usermodel-realm-role-mapper');
assert.equal(roles.config['claim.name'], 'realm_access.roles');
assert.equal(roles.config['multivalued'], 'true');
assert.deepEqual(realm.scopeMappings, [{ clientScope: 'flash-ticket-human', roles: ['BUYER', 'ORGANIZER', 'ADMIN'] }]);
console.log('PASS: fresh candidate realm whitelist, PKCE, audience, no secrets/users, zero business default roles and disabled management/registration. Import and token flow not exercised.');
