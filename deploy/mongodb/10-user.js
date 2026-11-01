// Runs only during first initialization of this service's empty Docker volume.
'use strict';

const spec = require('/opt/flash-ticket/schema.cjs');
const target = db.getSiblingDB('user_db');
if (spec.targetDatabase !== 'user_db' || spec.collections.length !== 4) {
  throw new Error('Expected the canonical B12 user_db schema.');
}
if (!process.env.USER_DB_VIEWER_PASSWORD) throw new Error('Viewer password is required.');
if (target.getCollectionInfos().length !== 0) throw new Error('Refuse to initialize a nonempty user_db.');

for (const collection of spec.collections) {
  const result = target.createCollection(collection.name, {
    validator: collection.validator, validationLevel: 'strict', validationAction: 'error'
  });
  if (result.ok !== 1) throw new Error('Could not create ' + collection.name);
  for (const { key, name, unique } of collection.indexes) {
    target.getCollection(collection.name).createIndex(key, { name, unique });
  }
}
target.createUser({
  user: 'user_erd',
  pwd: process.env.USER_DB_VIEWER_PASSWORD,
  roles: [{ role: 'read', db: 'user_db' }]
});
print('Initialized user_db: four canonical collections and a read-only inspection account.');
