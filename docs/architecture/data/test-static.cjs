// Structural text checks only. This script does not connect to a datastore.
'use strict';
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const root = path.resolve(__dirname, '../../..');
const read = relative => fs.readFileSync(path.join(root, relative), 'utf8');
const source = read('docs/architecture/B12-data-ownership-and-schema.md');
assert.match(source, /Trạng thái: `(?:DRAFT|REVIEW_READY|APPROVED)`/);
const owners = ['event', 'booking', 'payment', 'ticket'];
let tableCount = 0;
const withoutComments = sql => sql.replace(/--[^\n]*/g, '');
owners.forEach((owner, i) => {
  const section = source.split(`### 4.${i + 1} ${owner}-service`)[1]?.split('### ')[0];
  assert.ok(section, `Missing owner section: ${owner}`);
  const declaration = section.match(/Bảng tối thiểu: ([^\n]+)/)?.[1];
  assert.ok(declaration, `Missing table declaration: ${owner}`);
  const expected = [...declaration.matchAll(/`([a-z_]+)`/g)].map(m => m[1]).sort();
  const sql = withoutComments(read(`docs/architecture/data/postgres/${(i + 1) * 10}-${owner}.sql`));
  const actual = [...sql.matchAll(/CREATE TABLE\s+([a-z_]+)\s*\(/g)].map(m => m[1]).sort();
  assert.deepEqual(actual, expected, `${owner}: source/DDL table mismatch`);
  tableCount += actual.length;
  assert.ok(sql.includes(`\\connect ${owner}_db`), `${owner}: wrong target database`);
  assert.ok(sql.includes(`SET LOCAL ROLE ${owner}_owner`), `${owner}: wrong owner`);
  assert.match(sql, /BEGIN;/);
  assert.match(sql, /COMMIT;/);
  assert.doesNotMatch(sql, /\b(DROP|TRUNCATE)\b|IF NOT EXISTS/i);
  for (const ref of sql.matchAll(/REFERENCES\s+([a-z_.]+)\s*\(/g)) {
    assert.ok(actual.includes(ref[1]), `${owner}: unresolved or external FK ${ref[1]}`);
  }
  const erd = read(`docs/diagrams/src/B12-0${i + 1}-${owner}-erd.puml`);
  for (const table of expected) assert.ok(erd.includes(`\\n${table}"`), `${owner}: table absent from ERD: ${table}`);
  assert.doesNotMatch(erd, /^\s*!include(?:url)?\b/m);
});
const mongo = require('./mongodb/schema.cjs');
const mongoExpected = source.match(/Collection: ([^\n]+)/)[1];
const declaredCollections = [...mongoExpected.matchAll(/`([a-z_]+)`/g)].map(m => m[1]).filter(n => n !== 'user_db').sort();
assert.deepEqual(mongo.collections.map(c => c.name).sort(), declaredCollections);
const mongoErd = read('docs/diagrams/src/B12-05-user-document-map.puml');
for (const name of declaredCollections) assert.ok(mongoErd.includes(`\\n${name}"`), `Collection absent from ERD: ${name}`);
const validation = read('docs/architecture/B12-validation.md');
for (let i = 1; i <= 11; i++) assert.ok(validation.includes(`INV-${String(i).padStart(2, '0')}`), `Missing INV-${i}`);
for (let i = 1; i <= 8; i++) assert.ok(source.includes(`B12-OPEN-${String(i).padStart(2, '0')}`), `Missing OPEN-${i}`);
for (const relative of [
  'docs/architecture/data/README.md',
  ...['postgres', 'mongodb', 'keycloak'].map(n => `docs/architecture/data/${n}/README.md`),
  'docs/architecture/B12-legacy-configuration-comparison.md'
]) assert.ok(fs.existsSync(path.join(root, relative)), `Missing artifact: ${relative}`);
console.log(JSON.stringify({ result: 'PASS', layer: 'static text only', sqlOwners: owners.length,
  tables: tableCount, collections: declaredCollections.length, diagrams: 5, invariantsTraced: 11 }));

// Reference arithmetic vectors, not tests of unimplemented Java services.
const whole = value => {
  assert.match(value, /^(0|[1-9][0-9]{0,18})$/);
  return BigInt(value);
};
const roundPercent = (amount, percent) => (amount * BigInt(percent) + 50n) / 100n;
assert.throws(() => whole('1.5'));
assert.throws(() => whole('1e3'));
assert.equal(roundPercent(100005n, 10), 10001n);
assert.equal(100005n - roundPercent(100005n, 10), 90004n);
assert.equal(roundPercent(200100n, 1), 2001n);
assert.notEqual(roundPercent(200100n, 1), roundPercent(100050n, 1) * 2n);
assert.equal((whole('90004') * 100n) / 100n, 90004n);
assert.equal(JSON.parse(JSON.stringify({amount: whole('9999999999999999999').toString()})).amount, '9999999999999999999');
for (const owner of owners.slice(0, 3)) {
  const sql = read(`docs/architecture/data/postgres/${(owners.indexOf(owner)+1)*10}-${owner}.sql`);
  assert.match(sql, /money_amount AS numeric\(19,0\)/);
  assert.match(sql, /currency_code AS text CHECK \(VALUE = 'VND'\)/);
}
const contract = read('docs/architecture/B13-api-and-event-contracts.md');
for (const name of ['PurchaseSnapshot','SalesConfiguration','EventCancellation','AcceptPayment','PaymentDecision','IssueTickets','IssuanceResult','Geometry','CheckIn']) {
  assert.ok(contract.includes(`type ${name} =`), `Missing wire DTO ${name}`);
}
console.log('PASS: 8 reference integer-money vectors, VND domains and 9 named wire DTO declarations. Not Java/API/E2E validation.');

// Compare every physical column with its dictionary section, not only table names.
let dictionaryColumns = 0;
const splitDefinitions = body => {
  const parts = []; let start = 0, depth = 0, quoted = false;
  for (let i = 0; i < body.length; i++) {
    const c = body[i];
    if (c === "'") {
      if (quoted && body[i + 1] === "'") { i++; continue; }
      quoted = !quoted;
    }
    if (quoted) continue;
    if (c === '(') depth++;
    if (c === ')') depth--;
    if (c === ',' && depth === 0) { parts.push(body.slice(start, i).trim()); start = i + 1; }
  }
  parts.push(body.slice(start).trim());
  return parts;
};
for (const [i, owner] of owners.entries()) {
  const sql = withoutComments(read(`docs/architecture/data/postgres/${(i + 1) * 10}-${owner}.sql`));
  for (const table of sql.matchAll(/CREATE TABLE (\w+)\s*\(([\s\S]*?)\n\);/g)) {
    const columns = splitDefinitions(table[2]).map(part => part.match(/^([a-z_]+)\s+[a-z]/)?.[1]).filter(Boolean);
    const section = source.split(`### ${owner}_schema.${table[1]}\n`)[1]?.split('\n### ')[0];
    assert.ok(section, `Missing dictionary ${owner}.${table[1]}`);
    const documented = [...section.matchAll(/^\| `([a-z_]+)` \|/gm)].map(m => m[1]);
    assert.deepEqual(documented, columns, `Dictionary column drift ${owner}.${table[1]}`);
    dictionaryColumns += columns.length;
  }
}
assert.equal(dictionaryColumns, 467);
console.log(`PASS: dictionary covers all ${dictionaryColumns} SQL columns in order. Text parity, not runtime behavior.`);
