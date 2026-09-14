// mongosh only. Creates a fresh, explicitly selected local test database.
// Never edits validators or indexes in an existing database; no data migration.
'use strict';

if (!process.env.B12_MONGO_SCHEMA_FILE) throw new Error('Set B12_MONGO_SCHEMA_FILE to the absolute schema.cjs path.');
const spec = require(process.env.B12_MONGO_SCHEMA_FILE);
const databaseName = db.getName();
if (!/^b12_user_test_[a-z0-9]+$/.test(databaseName)) throw new Error('Only a b12_user_test_<suffix> database is allowed.');
if (!/^(localhost|127\.0\.0\.1|\[::1\]):[0-9]+$/.test(db.getMongo().host)) throw new Error('Only an explicit loopback endpoint is allowed.');
if (db.getCollectionInfos().length !== 0) throw new Error('Database is not empty. Use another fresh test database; do not overwrite.');
for (const collection of spec.collections) {
  const result = db.createCollection(collection.name, {
    validator: collection.validator, validationLevel: 'strict', validationAction: 'error'
  });
  if (result.ok !== 1) throw new Error(`Could not create ${collection.name}.`);
  for (const { key, name, unique } of collection.indexes) {
    db.getCollection(collection.name).createIndex(key, { name, unique });
  }
}
print(`Created ${spec.collections.length} candidate collections in ${databaseName}; no fixture data or credentials added.`);
