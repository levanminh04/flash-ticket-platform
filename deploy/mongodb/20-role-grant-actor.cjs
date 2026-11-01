// Explicit operator migration; NOT a boot-time initializer. No records are rewritten.
// mongosh <operator-uri> --file database/20-role-grant-actor.cjs
// Mount/copy schema.cjs beside this file, or set USER_SCHEMA_PATH to its path.
'use strict';
const spec = require(process.env.USER_SCHEMA_PATH || './schema.cjs');
const target = db.getSiblingDB(spec.targetDatabase);
const application = spec.collections.find(c => c.name === 'organizer_applications');
const name = (process.env.USER_MONGO_COLLECTION_PREFIX || 'tbl_') + application.name;
const info = target.getCollectionInfos({name});
if (info.length !== 1) throw new Error('Expected existing application collection');
const validator = info[0].options?.validator;
const grant = validator?.$jsonSchema?.properties?.roleGrant;
if (grant?.additionalProperties !== false || !grant.properties?.operationId) {
    throw new Error('Existing roleGrant validator does not match the expected closed shape');
}
const actor = application.validator.$jsonSchema.properties.roleGrant.properties.requestedBySubject;
if (grant.properties.requestedBySubject && JSON.stringify(grant.properties.requestedBySubject) !== JSON.stringify(actor)) {
    throw new Error('Existing actor field differs; reconcile manually');
}
grant.properties.requestedBySubject = actor;
if (process.env.USER_APPLY_VALIDATOR !== 'true') {
    print('Dry run: validator would allow optional roleGrant.requestedBySubject on ' + name);
} else {
    const result = target.runCommand({
        collMod: name, validator,
        validationLevel: 'strict', validationAction: 'error'
    });
    if (result.ok !== 1) throw new Error('Validator update failed');
    print('Updated application validator; no documents changed.');
}
