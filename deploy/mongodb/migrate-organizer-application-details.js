// Run with mongosh as the migration principal before deploying the extended organizer application API.
'use strict';

const target = db.getSiblingDB('user_db');
let prefix = process.env.USER_MONGO_COLLECTION_PREFIX;
let name;
if (prefix !== undefined) {
  if (!['', 'tbl_'].includes(prefix)) throw new Error('Unexpected user collection prefix.');
  name = prefix + 'organizer_applications';
} else {
  const names = target.getCollectionNames();
  name = names.includes('tbl_organizer_applications') ? 'tbl_organizer_applications' : 'organizer_applications';
}
const info = target.getCollectionInfos({ name })[0];
const schema = info?.options?.validator?.$jsonSchema;
if (!schema || schema.additionalProperties !== false || !schema.properties?.organizationName) {
  throw new Error('Expected the existing closed organizer applications validator on ' + name + '.');
}

Object.assign(schema.properties, {
  contactEmail: {
    bsonType: 'string',
    maxLength: 254,
    pattern: '^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$',
  },
  contactPhone: {
    bsonType: 'string',
    pattern: '^\\+84[0-9]{9,10}$',
  },
  websiteUrl: {
    bsonType: 'string',
    maxLength: 500,
    pattern: '^https://\\S+$',
  },
  socialPageUrl: {
    bsonType: 'string',
    maxLength: 500,
    pattern: '^https://\\S+$',
  },
  headquartersAddress: {
    bsonType: 'string',
    maxLength: 500,
    pattern: '\\S',
  },
  taxCode: {
    bsonType: 'string',
    pattern: '^[0-9]{10}([0-9]{2}|-?[0-9]{3})?$',
  },
});
const result = target.runCommand({ collMod: name, validator: info.options.validator });
if (result.ok !== 1) throw new Error('Could not update the organizer applications validator on ' + name + '.');
print('Updated organizer application verification fields on ' + name + '.');
