// mongosh only; synthetic fixture data remain in the disposable test database.
'use strict';
if (!/^b12_user_test_[a-z0-9]+$/.test(db.getName())) throw new Error('Expected an isolated B12 test database.');
if (!/^(localhost|127\.0\.0\.1|\[::1\]):[0-9]+$/.test(db.getMongo().host)) throw new Error('Expected loopback endpoint.');
const names = ['users', 'organizer_applications', 'organizer_profiles', 'user_follows'];
if (!process.env.B12_MONGO_SCHEMA_FILE) throw new Error('Set B12_MONGO_SCHEMA_FILE to the absolute schema.cjs path.');
const spec = require(process.env.B12_MONGO_SCHEMA_FILE);
const infos = db.getCollectionInfos();
if (infos.length !== names.length || names.some(name => !infos.some(info => info.name === name))) throw new Error('Test database must contain exactly the four initialized B12 collections.');
function canonical(value) {
  if (Array.isArray(value)) return value.map(canonical);
  if (value && typeof value === 'object') return Object.fromEntries(Object.keys(value).sort().map(key => [key, canonical(value[key])]));
  return value;
}
for (const collection of spec.collections) {
  const info = infos.find(info => info.name === collection.name);
  if (info.type !== 'collection' || info.options.validationLevel !== 'strict' || info.options.validationAction !== 'error') throw new Error('Expected strict B12 validation before writing fixtures.');
  if (JSON.stringify(canonical(info.options.validator)) !== JSON.stringify(canonical(collection.validator))) throw new Error('Validator differs from candidate source; refuse fixture writes.');
  const indexes = db.getCollection(collection.name).getIndexes();
  if (indexes.length !== collection.indexes.length + 1) throw new Error('Unexpected index set; refuse fixture writes.');
  for (const expected of collection.indexes) {
    const actual = indexes.find(index => index.name === expected.name);
    if (!actual || Boolean(actual.unique) !== expected.unique || JSON.stringify(actual.key) !== JSON.stringify(expected.key)) throw new Error('Index differs from candidate source; refuse fixture writes.');
  }
}
if (names.some(name => db.getCollection(name).countDocuments({}) !== 0)) throw new Error('Tests need empty initialized collections.');
const id = n => `00000000-0000-4000-8000-${String(n).padStart(12, '0')}`;
const now = new Date('2026-09-07T00:00:00Z');
const meta = n => ({ _id: id(n), createdAt: now, updatedAt: now, version: NumberInt(0) });
function rejectCode(fn, code) {
  let failure;
  try { fn(); } catch (error) { failure = error; }
  if (!failure || failure.code !== code) throw new Error(`Expected database error ${code}.`);
}
db.users.insertOne({ ...meta(1), identitySubject: 'fixture-buyer' });
rejectCode(() => db.users.insertOne({ ...meta(2), identitySubject: 'fixture-buyer' }), 11000);
rejectCode(() => db.users.insertOne({ ...meta(3), identitySubject: 'fixture-other', password: 'synthetic-invalid-field' }), 121);
rejectCode(() => db.users.insertOne({ ...meta(4), identitySubject: 'fixture-avatar', avatarUrl: 'https://example.invalid/avatar' }), 121);
rejectCode(() => db.users.insertOne({ ...meta(5), identitySubject: 'fixture-role', roles: ['BUYER'] }), 121);
rejectCode(() => db.users.insertOne({ ...meta(6), identitySubject: '   ' }), 121);
rejectCode(() => db.users.insertOne({ ...meta(7), identitySubject: 'fixture-version', version: 0.5 }), 121);
rejectCode(() => db.users.insertOne({ ...meta(8) }), 121);
const application = { ...meta(10), identitySubject: 'fixture-buyer', organizationName: 'Fixture Organization', shortDescription: 'Synthetic test', status: 'PENDING' };
db.organizer_applications.insertOne(application);
rejectCode(() => db.organizer_applications.insertOne({ ...application, _id: id(11) }), 11000);
rejectCode(() => db.organizer_applications.updateOne({ _id: id(10) }, { $set: { status: 'REJECTED', decidedBySubject: 'fixture-admin', decidedAt: now } }), 121);
rejectCode(() => db.organizer_applications.updateOne({ _id: id(10) }, { $set: { status: 'ACTIVE', decidedBySubject: 'fixture-admin', decidedAt: now } }), 121);
rejectCode(() => db.organizer_applications.updateOne({ _id: id(10) }, { $set: { roleGrant: { operationId: id(12), status: 'CONFIRMED', attemptedAt: now } } }), 121);
db.organizer_applications.updateOne({ _id: id(10) }, { $set: { status: 'ACTIVE', decidedBySubject: 'fixture-admin', decidedAt: now, roleGrant: { operationId: id(12), status: 'CONFIRMED', attemptedAt: now, confirmedAt: now } } });
db.organizer_applications.insertOne({ ...application, _id: id(13), identitySubject: 'fixture-rejected', status: 'REJECTED', decidedBySubject: 'fixture-admin', decidedAt: now, rejectionReason: 'Synthetic rejection' });
db.organizer_profiles.insertOne({ _id: id(20), identitySubject: 'fixture-buyer', sourceApplicationId: id(10), sourceVersion: NumberInt(1), organizationName: 'Fixture Organization', shortDescription: 'Synthetic test', status: 'ACTIVE', refreshedAt: now });
rejectCode(() => db.organizer_profiles.insertOne({ _id: id(21), identitySubject: 'fixture-nonactive', sourceApplicationId: id(13), sourceVersion: NumberInt(0), organizationName: 'Fixture Organization', shortDescription: 'Synthetic test', status: 'PENDING', refreshedAt: now }), 121);
db.user_follows.insertOne({ _id: id(30), buyerSubject: 'fixture-buyer', organizerSubject: 'fixture-organizer', createdAt: now });
rejectCode(() => db.user_follows.insertOne({ _id: id(31), buyerSubject: 'fixture-buyer', organizerSubject: 'fixture-organizer', createdAt: now }), 11000);
db.user_follows.deleteOne({ buyerSubject: 'fixture-buyer', organizerSubject: 'fixture-organizer' });
if (db.user_follows.deleteOne({ buyerSubject: 'fixture-buyer', organizerSubject: 'fixture-organizer' }).deletedCount !== 0) throw new Error('Repeated unfollow changed rows.');
print('PASS: Mongo server constraints, unique follow and repeated deletion. No claim about service state transitions or Keycloak truth.');
