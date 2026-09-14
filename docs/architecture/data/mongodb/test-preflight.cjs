// Exercises refusal paths with in-memory mocks. No MongoDB connection is made.
'use strict';
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');
const spec = require('./schema.cjs');
const initialInfos = () => spec.collections.map(c => ({
  name: c.name, type: 'collection',
  options: { validator: structuredClone(c.validator), validationLevel: 'strict', validationAction: 'error' }
}));
function refused(file, changes, expected) {
  const config = { name: 'b12_user_test_mock', host: '127.0.0.1:27017', infos: initialInfos(), count: 0, schemaFile: 'candidate-schema', ...changes };
  let writes = 0;
  const mutation = () => { writes++; throw new Error('Unexpected mutation before completed preflight.'); };
  const db = {
    getName: () => config.name,
    getMongo: () => ({ host: config.host }),
    getCollectionInfos: () => config.infos,
    getCollection: name => ({
      countDocuments: () => config.count,
      getIndexes: () => [{ name: '_id_', key: { _id: 1 } }, ...spec.collections.find(c => c.name === name).indexes.map(i => ({ ...i, unique: config.badIndexes ? true : i.unique }))],
      insertOne: mutation, insertMany: mutation, updateOne: mutation, createIndex: mutation
    }),
    createCollection: mutation
  };
  const context = {
    db, process: { env: { B12_MONGO_SCHEMA_FILE: config.schemaFile } },
    require: p => { assert.equal(p, 'candidate-schema'); return spec; },
    print: () => {}, NumberInt: n => n
  };
  const script = fs.readFileSync(path.join(__dirname, file), 'utf8');
  assert.throws(() => vm.runInNewContext(script, context), expected);
  assert.equal(writes, 0, `${file} mutated before rejecting unsafe context.`);
}
refused('bootstrap.js', { schemaFile: undefined }, /Set B12_MONGO_SCHEMA_FILE/);
refused('bootstrap.js', { name: 'user_db' }, /Only a b12_user_test/);
refused('bootstrap.js', { host: 'db.example.invalid:27017' }, /Only an explicit loopback/);
refused('bootstrap.js', {}, /Database is not empty/);
refused('test-live.js', { name: 'user_db' }, /Expected an isolated/);
refused('test-live.js', { host: 'db.example.invalid:27017' }, /Expected loopback/);
refused('test-live.js', { infos: [] }, /exactly the four/);
const drift = initialInfos();
drift[0].options.validator.$jsonSchema.additionalProperties = true;
refused('test-live.js', { infos: drift }, /Validator differs/);
refused('test-live.js', { badIndexes: true }, /Index differs/);
refused('test-live.js', { count: 1 }, /Tests need empty/);
console.log('PASS: 10 mocked Mongo refusal cases; zero mutations before preflight rejection. No server behavior proven.');
