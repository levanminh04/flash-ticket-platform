// Run with mongosh as the migration principal before deploying the profile API.
'use strict';

const target = db.getSiblingDB('user_db');
let prefix = process.env.USER_MONGO_COLLECTION_PREFIX;
let name;
if (prefix !== undefined) {
  if (!['', 'tbl_'].includes(prefix)) throw new Error('Unexpected user collection prefix.');
  name = prefix + 'users';
} else {
  const names = target.getCollectionNames();
  name = names.includes('tbl_users') ? 'tbl_users' : 'users';
}
const info = target.getCollectionInfos({ name })[0];
const schema = info?.options?.validator?.$jsonSchema;
if (!schema || schema.additionalProperties !== false || !schema.properties?.identitySubject) {
  throw new Error('Expected the existing closed user profile validator on ' + name + '.');
}

Object.assign(schema.properties, {
  dateOfBirth: { bsonType: 'string', pattern: '^[0-9]{2}-[0-9]{2}-[0-9]{4}$' },
  address: { bsonType: 'string', maxLength: 500, pattern: '\\S' },
  phoneNumber: { bsonType: 'string', pattern: '^\\+84[0-9]{9}$' },
  avatarUrl: { bsonType: 'string', maxLength: 2048, pattern: '^https://res\\.cloudinary\\.com/' },
});
const result = target.runCommand({ collMod: name, validator: info.options.validator });
if (result.ok !== 1) throw new Error('Could not update the user profile validator on ' + name + '.');
print('Updated optional profile fields on ' + name + '.');
