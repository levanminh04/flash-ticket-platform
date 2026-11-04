// Local inspection database only. Safe to rerun; never overwrite a collection.
'use strict';

const prefixSpec = require('/opt/flash-ticket/schema.cjs');
const prefixDb = db.getSiblingDB('user_db');
const prefixNames = new Set(prefixDb.getCollectionNames());
if (prefixSpec.targetDatabase !== 'user_db' || prefixSpec.collections.length !== 4) {
  throw new Error('Expected the canonical four-collection User schema.');
}
if (prefixNames.size !== prefixSpec.collections.length) throw new Error('Unexpected collection set.');
for (const { name } of prefixSpec.collections) {
  if (prefixNames.has(name) === prefixNames.has('tbl_' + name)) {
    throw new Error('Missing source or conflicting destination for ' + name);
  }
}
for (const { name } of prefixSpec.collections) {
  if (prefixNames.has(name)) prefixDb.getCollection(name).renameCollection('tbl_' + name, false);
}
const prefixedNames = new Set(prefixDb.getCollectionNames());
if (prefixSpec.collections.some(({ name }) => !prefixedNames.has('tbl_' + name))) {
  throw new Error('Collection prefix verification failed.');
}
print('Verified four tbl_ collections in user_db.');
