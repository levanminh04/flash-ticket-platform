// Authoritative seed for Flash Ticket MongoDB user_db.
// Links directly to Keycloak realm subjects (admin@gmail.com, buyer@gmail.com, organizer@gmail.com).
// Idempotent: safe to run multiple times without duplicating or overwriting data.
'use strict';

const targetDb = db.getSiblingDB('user_db');
const prefix = process.env.USER_MONGO_COLLECTION_PREFIX || 'tbl_';

const usersCollection = targetDb.getCollection(prefix + 'users');
const appsCollection = targetDb.getCollection(prefix + 'organizer_applications');
const profilesCollection = targetDb.getCollection(prefix + 'organizer_profiles');

const seedUsers = [
  {
    _id: 'c0000001-0000-4000-8000-000000000001',
    identitySubject: '01388e8e-721c-42d5-81b6-3f98f2c521df',
    displayName: 'admin@gmail.com',
    createdAt: new Date('2026-10-01T00:00:00Z'),
    updatedAt: new Date('2026-10-01T00:00:00Z'),
    version: NumberLong(0)
  },
  {
    _id: 'c0000002-0000-4000-8000-000000000002',
    identitySubject: '659d1963-3d1e-4886-9c85-119f11fcd133',
    displayName: 'buyer@gmail.com',
    createdAt: new Date('2026-10-01T00:00:00Z'),
    updatedAt: new Date('2026-10-01T00:00:00Z'),
    version: NumberLong(0)
  },
  {
    _id: 'c0000003-0000-4000-8000-000000000003',
    identitySubject: 'ae3a05cd-95c2-47ec-a041-9c9de198c4a1',
    displayName: 'organizer@gmail.com',
    createdAt: new Date('2026-10-01T00:00:00Z'),
    updatedAt: new Date('2026-10-01T00:00:00Z'),
    version: NumberLong(0)
  }
];

let insertedUsers = 0;
for (const u of seedUsers) {
  const existing = usersCollection.findOne({ identitySubject: u.identitySubject });
  if (!existing) {
    usersCollection.insertOne(u);
    insertedUsers++;
  }
}

const appId = 'a0000001-0000-4000-8000-000000000001';
const organizerSubject = 'ae3a05cd-95c2-47ec-a041-9c9de198c4a1';
const adminSubject = '01388e8e-721c-42d5-81b6-3f98f2c521df';

const existingApp = appsCollection.findOne({ identitySubject: organizerSubject });
if (!existingApp) {
  appsCollection.insertOne({
    _id: appId,
    identitySubject: organizerSubject,
    organizationName: 'Flash Ticket Organizer',
    shortDescription: 'Ban tổ chức sự kiện Flash Ticket',
    status: 'ACTIVE',
    decidedBySubject: adminSubject,
    decidedAt: new Date('2026-10-01T00:00:00Z'),
    roleGrant: {
      operationId: '00000001-0000-4000-8000-000000000001',
      requestedBySubject: adminSubject,
      status: 'CONFIRMED',
      attemptedAt: new Date('2026-10-01T00:00:00Z'),
      confirmedAt: new Date('2026-10-01T00:00:00Z')
    },
    createdAt: new Date('2026-10-01T00:00:00Z'),
    updatedAt: new Date('2026-10-01T00:00:00Z'),
    version: NumberLong(0)
  });
}

const existingProfile = profilesCollection.findOne({ identitySubject: organizerSubject });
if (!existingProfile) {
  profilesCollection.insertOne({
    _id: 'b0000001-0000-4000-8000-000000000001',
    identitySubject: organizerSubject,
    sourceApplicationId: appId,
    sourceVersion: NumberLong(0),
    organizationName: 'Flash Ticket Organizer',
    shortDescription: 'Ban tổ chức sự kiện Flash Ticket',
    status: 'ACTIVE',
    refreshedAt: new Date('2026-10-01T00:00:00Z')
  });
}

print(`Authoritative seed completed for user_db. Users inserted: ${insertedUsers}, Total users: ${usersCollection.countDocuments()}, Apps: ${appsCollection.countDocuments()}, Profiles: ${profilesCollection.countDocuments()}`);
