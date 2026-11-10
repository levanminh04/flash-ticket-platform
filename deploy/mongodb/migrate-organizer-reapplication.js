// Run with mongosh as a MongoDB migration principal on user_db.
// Set USER_MONGO_COLLECTION_PREFIX to tbl_ for the deployed collection.
'use strict';

const target = db.getSiblingDB('user_db');
const prefix = process.env.USER_MONGO_COLLECTION_PREFIX || '';
if (!['', 'tbl_'].includes(prefix)) throw new Error('Unexpected user collection prefix.');
const name = prefix + 'organizer_applications';
if (!target.getCollectionNames().includes(name)) throw new Error('Organizer applications collection not found.');

const applications = target.getCollection(name);
const oldName = 'uq_application_identity_subject';
const openName = 'uq_application_open_subject';
const cursorName = 'ix_application_subject_cursor';
const key = { identitySubject: 1 };
const filter = { status: { $in: ['PENDING', 'ACTIVE'] } };
const indexes = applications.getIndexes();
const old = indexes.find(index => index.name === oldName);

if (!old && !indexes.some(index => index.name === openName)) {
  throw new Error('Expected an existing organizer uniqueness constraint.');
}
if (old && (old.unique !== true || old.partialFilterExpression || JSON.stringify(old.key) !== JSON.stringify(key))) {
  throw new Error('Existing index does not match the expected legacy definition.');
}

applications.createIndex(key, { name: openName, unique: true, partialFilterExpression: filter });
applications.createIndex({ identitySubject: 1, _id: 1 }, { name: cursorName });
const open = applications.getIndexes().find(index => index.name === openName);
const indexedStatuses = open?.partialFilterExpression?.status?.$in;
if (open?.unique !== true || JSON.stringify(open.key) !== JSON.stringify(key) ||
    !Array.isArray(indexedStatuses) || [...indexedStatuses].sort().join(',') !== 'ACTIVE,PENDING') {
  throw new Error('New uniqueness constraint could not be verified.');
}

if (old) applications.dropIndex(oldName);
print('Organizer reapplication indexes verified on ' + name + '.');
