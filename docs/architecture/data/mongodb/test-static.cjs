'use strict';
const assert = require('node:assert/strict');
const spec = require('./schema.cjs');
assert.equal(spec.status, 'CANDIDATE');
assert.deepEqual(spec.collections.map(c => c.name), ['users', 'organizer_applications', 'organizer_profiles', 'user_follows']);
for (const c of spec.collections) {
  const schema = c.validator.$jsonSchema;
  assert.equal(schema.additionalProperties, false);
  assert.ok(schema.required.includes('_id') && schema.properties._id);
  assert.equal(new Set(c.indexes.map(i => i.name)).size, c.indexes.length);
  for (const field of ['password', 'credentials', 'refreshToken', 'roles', 'LEGACY_OPTIONAL']) {
    assert.ok(!Object.hasOwn(schema.properties, field));
  }
  assert.ok(schema.required.every(field => Object.hasOwn(schema.properties, field)));
}
const users = spec.collections.find(c => c.name === 'users');
assert.ok(users.indexes.some(i => i.unique && i.key.identitySubject === 1));
assert.ok(!Object.hasOwn(users.validator.$jsonSchema.properties, 'email'));
assert.ok(!Object.hasOwn(users.validator.$jsonSchema.properties, 'avatarUrl'));
const applications = spec.collections.find(c => c.name === 'organizer_applications');
assert.ok(applications.indexes.some(i => i.unique && i.key.identitySubject === 1));
const branches = applications.validator.$jsonSchema.oneOf;
assert.ok(branches.find(b => b.properties.status.enum.includes('REJECTED')).required.includes('rejectionReason'));
const active = branches.find(b => b.properties.status.enum.includes('ACTIVE'));
assert.ok(active.required.includes('roleGrant'));
assert.deepEqual(active.properties.roleGrant.properties.status.enum, ['CONFIRMED']);
assert.equal(applications.validator.$jsonSchema.properties.roleGrant.additionalProperties, false);
assert.ok(applications.validator.$jsonSchema.properties.roleGrant.oneOf.find(b => b.properties.status.enum.includes('CONFIRMED')).required.includes('confirmedAt'));
assert.ok(spec.collections.find(c => c.name === 'user_follows').indexes.some(i => i.unique && i.key.buyerSubject === 1 && i.key.organizerSubject === 1));
assert.ok(spec.runtimePrivileges.every(p => p.actions.every(a => ['find', 'insert', 'update', 'remove'].includes(a))));
assert.deepEqual(spec.runtimePrivileges.find(p => p.resource.collection === 'user_follows').actions, ['find', 'insert', 'remove']);
assert.ok(spec.runtimePrivileges.filter(p => p.resource.collection !== 'user_follows').every(p => !p.actions.includes('remove')));
console.log('PASS: Mongo static structure, identity keys, rejection rule, role confirmation, unique follow and narrow remove permission. Server validation not exercised.');
