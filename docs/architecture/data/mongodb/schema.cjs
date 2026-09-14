// B12-v0.1 CANDIDATE. Source: ../../B12-data-ownership-and-schema.md §4.5.
// Data-only module; requiring this file never connects to MongoDB.
'use strict';

const text = { bsonType: 'string', pattern: '\\S' };
const uuid = { bsonType: 'string', pattern: '^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$' };
const date = { bsonType: 'date' };
const version = { bsonType: ['int', 'long'], minimum: 0 };
const closed = (required, properties, extra = {}) => ({
  bsonType: 'object', additionalProperties: false, required, properties, ...extra
});
const index = (key, name, unique = false) => ({ key, name, unique });
const metadata = { _id: uuid, createdAt: date, updatedAt: date, version };
const roleGrant = closed(['operationId', 'status', 'attemptedAt'], {
  operationId: uuid,
  status: { enum: ['REQUESTED', 'CONFIRMED', 'FAILED', 'UNKNOWN'] },
  attemptedAt: date,
  confirmedAt: date
}, {
  oneOf: [
    { properties: { status: { enum: ['CONFIRMED'] } }, required: ['confirmedAt'] },
    { properties: { status: { enum: ['REQUESTED', 'FAILED', 'UNKNOWN'] } }, not: { required: ['confirmedAt'] } }
  ]
});

const collections = [
  {
    name: 'users',
    validator: { $jsonSchema: closed(['_id', 'identitySubject', 'createdAt', 'updatedAt', 'version'], {
      ...metadata, identitySubject: text, displayName: text
    }) },
    indexes: [index({ identitySubject: 1 }, 'uq_users_identity_subject', true)]
  },
  {
    name: 'organizer_applications',
    validator: { $jsonSchema: closed(
      ['_id', 'identitySubject', 'organizationName', 'shortDescription', 'status', 'createdAt', 'updatedAt', 'version'],
      {
        ...metadata, identitySubject: text, organizationName: text, shortDescription: text,
        status: { enum: ['PENDING', 'ACTIVE', 'REJECTED'] },
        rejectionReason: text, decidedBySubject: text, decidedAt: date, roleGrant
      },
      {
        oneOf: [
          {
            properties: { status: { enum: ['PENDING'] } },
            not: { anyOf: [{ required: ['rejectionReason'] }, { required: ['decidedBySubject'] }, { required: ['decidedAt'] }] }
          },
          {
            properties: { status: { enum: ['ACTIVE'] }, roleGrant: { properties: { status: { enum: ['CONFIRMED'] } } } },
            required: ['decidedBySubject', 'decidedAt', 'roleGrant'],
            not: { required: ['rejectionReason'] }
          },
          {
            properties: { status: { enum: ['REJECTED'] } },
            required: ['decidedBySubject', 'decidedAt', 'rejectionReason'],
            not: { required: ['roleGrant'] }
          }
        ]
      }
    ) },
    indexes: [
      index({ identitySubject: 1 }, 'uq_application_identity_subject', true),
      index({ status: 1, createdAt: 1 }, 'ix_application_review_queue')
    ]
  },
  {
    name: 'organizer_profiles',
    validator: { $jsonSchema: closed(
      ['_id', 'identitySubject', 'sourceApplicationId', 'sourceVersion', 'organizationName', 'shortDescription', 'status', 'refreshedAt'],
      {
        _id: uuid, identitySubject: text, sourceApplicationId: uuid, sourceVersion: version,
        organizationName: text, shortDescription: text, status: { enum: ['ACTIVE'] },
        logoUrl: { bsonType: 'string', pattern: '^https://\\S+$' },
        bannerUrl: { bsonType: 'string', pattern: '^https://\\S+$' },
        refreshedAt: date
      }
    ) },
    indexes: [
      index({ identitySubject: 1 }, 'uq_profile_identity_subject', true),
      index({ sourceApplicationId: 1 }, 'uq_profile_source_application', true)
    ]
  },
  {
    name: 'user_follows',
    validator: { $jsonSchema: closed(['_id', 'buyerSubject', 'organizerSubject', 'createdAt'], {
      _id: uuid, buyerSubject: text, organizerSubject: text, createdAt: date
    }) },
    // GOV-101/BIZ-157: a single current relation, retry-safe PUT/DELETE.
    indexes: [index({ buyerSubject: 1, organizerSubject: 1 }, 'uq_follow_buyer_organizer', true),
      index({ organizerSubject: 1 }, 'ix_follow_organizer')]
  }
];

module.exports = {
  version: 'B12-v0.2', status: 'CANDIDATE', targetDatabase: 'user_db', collections,
  runtimePrivileges: collections.map(({ name }) => ({
    resource: { db: 'user_db', collection: name },
    actions: name === 'user_follows' ? ['find', 'insert', 'remove'] : ['find', 'insert', 'update']
  }))
};
