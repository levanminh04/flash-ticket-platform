// Chạy Payment JAR thật; HTTP Booking/OAuth2/JWKS và phản hồi Ticket là fixture trong process test.
// Không cần npm package. PostgreSQL/RabbitMQ test phải được chuẩn bị riêng, không dùng dữ liệu thật.
import { createServer } from 'node:http';
import { generateKeyPairSync, randomUUID, sign, createHmac } from 'node:crypto';
import { spawn, execFileSync } from 'node:child_process';
import { mkdirSync, openSync, closeSync, readFileSync, writeFileSync } from 'node:fs';
import { dirname, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';
import { setTimeout as pause } from 'node:timers/promises';
import assert from 'node:assert/strict';

const serviceDir = resolve(dirname(fileURLToPath(import.meta.url)), '../../..');
const root = resolve(serviceDir, '../..');
const reportsDir = resolve(root, 'docs/java-workflow/reports');
const logPath = resolve(serviceDir, 'target/payment-http-scenarios.log');
const base = 'http://127.0.0.1:18085';
const issuer = 'http://127.0.0.1:18086';
const rabbitBase = 'http://127.0.0.1:25673/api';
const rabbitAuth = 'Basic ' + Buffer.from('payment_test:payment-flow-test').toString('base64');
const exchange = 'payment.evidence.v1';
const pgContainer = process.env.PAYMENT_SCENARIO_PG_CONTAINER ?? 'codex-payment-evidence-pg';
const secret = 'scenario-vnpay-secret';
const records = [], orders = new Map(), bookingRequests = [];
const startedAt = new Date().toISOString();
const { privateKey, publicKey } = generateKeyPairSync('rsa', { modulusLength: 2048 });
const jwk = { ...publicKey.export({ format: 'jwk' }), kid: 'payment-scenario-key', alg: 'RS256', use: 'sig' };
let backend, logFd;

function jwt(subject = 'buyer-a', role = 'BUYER', expired = false) {
  const now = Math.floor(Date.now() / 1000);
  const head = Buffer.from(JSON.stringify({ alg: 'RS256', kid: jwk.kid })).toString('base64url');
  const body = Buffer.from(JSON.stringify({ iss: issuer, aud: 'payment', sub: subject, iat: now - 3600,
    exp: expired ? now - 300 : now + 3600, realm_access: { roles: [role] } })).toString('base64url');
  return `${head}.${body}.${sign('RSA-SHA256', Buffer.from(`${head}.${body}`), privateKey).toString('base64url')}`;
}

function newOrder(mode = 'payable') {
  const orderId = randomUUID();
  const snapshot = { schemaVersion: 1, orderId, eventId: randomUUID(), buyerSubject: 'buyer-a',
    customerEmail: 'buyer-a@example.test', sourceConfigVersion: '1', sourceOrderVersion: '1',
    frozenAt: new Date(Date.now() - 1000).toISOString(), expiresAt: new Date(Date.now() + 600000).toISOString(),
    currency: 'VND', eventTitle: 'Payment scenario concert', eventStartsAt: '2030-01-02T01:00:00Z',
    eventEndsAt: '2030-01-02T03:00:00Z', venueName: 'Fixture venue', mode: 'QUANTITY', sectorId: null,
    subtotal: '200000', discount: '20000', total: '180000',
    promotion: { promotionId: randomUUID(), code: 'SALE10', discount: '20000' },
    items: [{ orderItemId: randomUUID(), ticketTypeId: randomUUID(), ticketTypeName: 'General', quantity: 2,
      unitPrice: '100000', lineSubtotal: '200000', sectorId: null, seats: [] }] };
  const order = { orderId, mode, snapshot, key: randomUUID() };
  orders.set(orderId, order);
  return order;
}

const fixture = createServer(async (req, res) => {
  const reply = (status, data) => { if (!res.destroyed) { res.writeHead(status, { 'Content-Type': 'application/json' }); res.end(JSON.stringify(data)); } };
  if (req.url === '/jwks') return reply(200, { keys: [jwk] });
  let text = ''; for await (const chunk of req) text += chunk;
  if (req.url === '/oauth/token') {
    const expected = 'Basic ' + Buffer.from('payment-scenario:scenario-only').toString('base64');
    if (req.headers.authorization !== expected) return reply(401, { error: 'invalid_client' });
    return reply(200, { access_token: 'payment-evidence-machine-token', token_type: 'Bearer', expires_in: 300 });
  }
  const match = req.url?.match(/^\/internal\/v1\/orders\/([0-9a-f-]+)\/purchase-snapshot$/);
  if (!match || req.method !== 'POST') return reply(404, { code: 'NOT_FOUND' });
  const body = JSON.parse(text || '{}');
  const order = orders.get(match[1]);
  const credentialOk = req.headers.authorization === 'Bearer payment-evidence-machine-token';
  bookingRequests.push({ orderId: match[1], actor: body.buyerSubject, serviceCredentialValidated: credentialOk });
  if (!credentialOk) return reply(401, { code: 'SERVICE_CREDENTIAL_REQUIRED' });
  if (!order || body.buyerSubject !== 'buyer-a') return reply(404, { code: 'RESOURCE_NOT_FOUND' });
  if (order.mode === 'expired' || order.mode === 'cancelled') return reply(409, { code: order.mode === 'expired' ? 'ORDER_EXPIRED' : 'ORDER_NOT_PAYABLE' });
  if (order.mode === 'unavailable') return reply(503, { code: 'BOOKING_TEMPORARILY_UNAVAILABLE' });
  if (order.mode === 'timeout') await pause(1500);
  return reply(200, order.snapshot);
});

function sql(statement) {
  return execFileSync('docker', ['exec', pgContainer, 'psql', '-U', 'postgres', '-d', 'payment_db', '-t', '-A', '-v', 'ON_ERROR_STOP=1', '-c', statement], { encoding: 'utf8' }).trim();
}

function state(orderId) {
  assert.match(orderId, /^[0-9a-f-]{36}$/);
  return JSON.parse(sql(`select json_build_object(
    'attempts',coalesce((select json_agg(json_build_object('attemptId',id,'state',state,'amount',expected_amount::text,'requestKey',request_key)) from payment_schema.tbl_payment_attempts where order_id='${orderId}'),'[]'::json),
    'saga',(select json_build_object('state',state,'chargeId',charge_id) from payment_schema.tbl_payment_sagas where order_id='${orderId}'),
    'chargeCount',(select count(*) from payment_schema.tbl_charges where order_id='${orderId}'),
    'confirmationCount',(select count(*) from payment_schema.tbl_payment_confirmations where order_id='${orderId}'),
    'refunds',coalesce((select json_agg(json_build_object('state',state,'reason',reason,'amount',amount::text)) from payment_schema.tbl_refunds where order_id='${orderId}'),'[]'::json),
    'outbox',coalesce((select json_agg(json_build_object('messageType',message_type,'published',published_at is not null)) from payment_schema.tbl_outbox_messages where aggregate_id='${orderId}'),'[]'::json))::text`));
}

async function callHttp(method, path, body, { token = jwt(), key } = {}) {
  const headers = { 'Content-Type': 'application/json' };
  if (token) headers.Authorization = `Bearer ${token}`;
  if (key !== undefined) headers['Idempotency-Key'] = key;
  const response = await fetch(base + path, { method, headers, redirect: 'manual', body: body === undefined ? undefined : JSON.stringify(body), signal: AbortSignal.timeout(15000) });
  const text = await response.text(); let data; try { data = JSON.parse(text); } catch { data = text; }
  return { request: { method, path, headers: { ...headers, ...(token ? { Authorization: '[test JWT đã che]' } : {}) }, ...(body === undefined ? {} : { body }) },
    response: { status: response.status, body: data, ...(response.headers.get('location') ? { location: response.headers.get('location') } : {}) } };
}

const create = (order, options = {}, extra = {}) => callHttp('POST', '/api/v1/payments', { orderId: order.orderId, ...extra }, { key: order.key, ...options });
const get = order => callHttp('GET', `/api/v1/orders/${order.orderId}/payment`);
function expectStatus(result, status) { assert.equal(result.response.status, status, JSON.stringify(result.response)); }
async function waitFor(predicate, timeout = 15000) {
  const until = Date.now() + timeout;
  while (Date.now() < until) { const result = await predicate(); if (result) return result; await pause(200); }
  throw new Error('Timeout waiting for observed Payment state');
}

async function rabbit(method, path, body) {
  const response = await fetch(rabbitBase + path, { method, headers: { Authorization: rabbitAuth, 'Content-Type': 'application/json' }, body: body === undefined ? undefined : JSON.stringify(body), signal: AbortSignal.timeout(5000) });
  const text = await response.text(); assert.ok(response.ok, `Rabbit ${response.status}: ${text}`); return text ? JSON.parse(text) : null;
}
async function publish(order, type, data, messageId = randomUUID()) {
  const envelope = { messageId, messageType: type, schemaVersion: 1, aggregateId: order.orderId,
    aggregateVersion: '1', occurredAt: new Date().toISOString(), correlationId: order.orderId, causationId: null, traceparent: null, data };
  const brokerResult = await rabbit('POST', `/exchanges/%2F/${exchange}/publish`, { properties: { content_type: 'application/json', delivery_mode: 2 }, routing_key: type, payload: JSON.stringify(envelope), payload_encoding: 'string' });
  assert.equal(brokerResult.routed, true); return { envelope, brokerResult };
}
async function outgoing(order, type) {
  const payload = await waitFor(() => sql(`select payload::text from payment_schema.tbl_outbox_messages where aggregate_id='${order.orderId}' and message_type='${type}' and published_at is not null order by occurred_at desc limit 1`));
  return JSON.parse(payload);
}
async function ipn(order, { transactionNo = randomUUID(), responseCode = '00', transactionStatus = '00', amount = '18000000', invalidSignature = false } = {}) {
  const data = { vnp_TmnCode: 'test', vnp_TxnRef: order.attemptId, vnp_Amount: amount, vnp_ResponseCode: responseCode,
    vnp_TransactionStatus: transactionStatus, vnp_TransactionNo: transactionNo,
    vnp_PayDate: new Date(Date.now() + 7 * 3600000).toISOString().slice(0,19).replace(/\D/g,'') };
  const canonical = Object.keys(data).sort().map(k => `${k}=${encodeURIComponent(data[k])}`).join('&');
  data.vnp_SecureHash = invalidSignature ? 'invalid-signature' : createHmac('sha512', secret).update(canonical).digest('hex');
  const result = await callHttp('GET', '/api/v1/payments/vnpay/ipn?' + new URLSearchParams(data), undefined, { token: null });
  order.lastIpn = data;
  return result;
}
async function start(order = newOrder()) {
  const result = await create(order); expectStatus(result, 200); order.attemptId = result.response.body.data.attemptId; return { order, result };
}
async function pay(order) {
  const result = await ipn(order); expectStatus(result, 200); assert.equal(result.response.body.RspCode, '00');
  await waitFor(() => state(order.orderId).saga?.state === 'WAITING_BOOKING');
  order.chargeId = state(order.orderId).saga.chargeId; return result;
}
async function accept(order) {
  const result = await publish(order, 'booking.payment-decision.v1', { orderId: order.orderId, chargeId: order.chargeId,
    decisionAt: new Date().toISOString(), outcome: 'ACCEPTED', acceptedChargeId: order.chargeId });
  await waitFor(() => state(order.orderId).saga?.state === 'WAITING_TICKETS'); return result;
}
async function tickets(order, { failed = false, count = 2 } = {}) {
  order.issuanceId ??= randomUUID();
  return publish(order, 'ticket.issuance-result.v1', { orderId: order.orderId, chargeId: order.chargeId, issuanceId: order.issuanceId,
    state: failed ? 'FAILED' : 'COMPLETED', ticketCount: failed ? 0 : count, failureCode: failed ? 'TERMINAL_ISSUANCE_FAILURE' : null });
}
async function complete(order, issuanceId = order.issuanceId) {
  return publish(order, 'booking.order-completed.v1', { orderId: order.orderId, chargeId: order.chargeId, issuanceId, ticketCount: 2 });
}

async function scenario(title, action) {
  const id = `S${String(records.length + 1).padStart(2,'0')}`;
  const record = { id, title, startedAt: new Date().toISOString(), passed: false };
  try { record.evidence = await action(); record.passed = true; }
  catch (error) { record.error = error.stack; }
  records.push(record); console.log(`${id} ${record.passed ? 'PASS' : 'FAIL'} ${title}`);
  return record;
}

function writeReport() {
  mkdirSync(reportsDir, { recursive: true });
  const bundle = { startedAt, finishedAt: new Date().toISOString(), branch: 'codex/gov-156-payment-flow',
    scope: 'Payment JAR + HTTP + PostgreSQL + RabbitMQ thật; Booking/OAuth2/JWKS/Ticket/VNPay là fixture; không chuyển tiền thật.',
    passed: records.filter(r=>r.passed).length, failed: records.filter(r=>!r.passed).length, bookingRequests, records };
  writeFileSync(resolve(reportsDir,'2026-10-30-payment-scenario-results.json'),JSON.stringify(bundle,null,2)+'\n');
  const lines = ['# Kết quả chạy kịch bản Payment thực tế', '', `- Bắt đầu: ${bundle.startedAt}; kết thúc: ${bundle.finishedAt} (UTC).`,
    `- Nhánh: \`${bundle.branch}\`. Kết quả: **${bundle.passed}/${records.length} đạt**, ${bundle.failed} lỗi ngoài dự kiến.`,
    '- Payment chạy JAR thật, gọi HTTP thật, PostgreSQL 16.15 và RabbitMQ 4.4.0 thật trong container riêng. Không sửa Java để chạy báo cáo này.',
    '- Booking, issuer/OAuth2 và kết quả Ticket được mô phỏng trong process test. Webhook ký bằng secret test; URL gateway là sandbox.example.test. Không có giao dịch/hoàn tiền thật.',
    '- JWT và credential không chép vào report. UUID, amount, URL, mã lỗi, trạng thái dưới đây là giá trị thu được ở lần chạy này.',
    '- `saga` ở mục `database` là trạng thái truy vấn trực tiếp PostgreSQL; API GET Payment hiện không trả field Saga. Broker publish routed=true chỉ là ACK vận chuyển.',
    '- File JSON cạnh MD lưu toàn bộ dữ liệu gốc. `refund.state=PENDING` là mới ghi nghĩa vụ hoàn, chưa phải tiền đã được hoàn.', '',
    'Lưu ý khởi chạy đã quan sát: JVM mặc định trên Windows dùng Asia/Saigon, PostgreSQL test trả `FATAL: invalid value for parameter "TimeZone": "Asia/Saigon"`. Bộ kịch bản chạy JAR với `-Duser.timezone=UTC`, giống cấu hình Surefire hiện có. Config Server được thay bằng tham số test cục bộ, không sửa cấu hình production.', '',
    '| Ca | Kịch bản | Kết quả | HTTP / thông tin quan sát |', '|---|---|---|---|'];
  for (const r of records) {
    const evidence = r.evidence ?? {}; const response = evidence.response ?? evidence.http?.response;
    const info = response ? `${response.status}${response.body?.data?.code ? ' — '+response.body.data.code : response.body?.RspCode ? ' — RspCode='+response.body.RspCode : ''}`
      : evidence.database?.saga?.state ?? (r.passed ? 'Xem dữ liệu chi tiết' : 'Lỗi kiểm tra');
    lines.push(`| ${r.id} | ${r.title} | ${r.passed ? 'Đạt' : 'LỖI'} | ${info} |`);
  }
  for (const r of records) lines.push('',`## ${r.id} — ${r.title}`,'',r.passed ? '**Đạt điều kiện kiểm tra.** Dữ liệu thực nhận:' : '**Có lỗi ngoài dự kiến.**', '', '```json',JSON.stringify(r.evidence ?? { error: r.error },null,2),'```');
  writeFileSync(resolve(reportsDir,'2026-10-30-payment-scenario-results.md'),lines.join('\n')+'\n');
  console.log(JSON.stringify({ passed: bundle.passed, failed: bundle.failed, total: records.length, report: resolve(reportsDir,'2026-10-30-payment-scenario-results.md') }));
}

try {
  assert.ok(pgContainer.startsWith('codex-payment-evidence-'), 'Chỉ cho phép container DB chuyên dụng của bộ kịch bản');
  sql('TRUNCATE payment_schema.tbl_order_payment_snapshots, payment_schema.tbl_event_finance_snapshots, payment_schema.tbl_outbox_messages, payment_schema.tbl_inbox_messages CASCADE');
  await new Promise(resolveListening => fixture.listen(18086,'127.0.0.1',resolveListening));
  await waitFor(async()=> { try { return await rabbit('GET','/overview'); } catch { return false; } },60000);
  await rabbit('PUT',`/exchanges/%2F/${exchange}`,{ type:'topic',durable:true,auto_delete:false,internal:false,arguments:{} });
  for (const [queue,keys] of [['payment.evidence.booking-commands',['booking.accept-payment.v1','booking.issuance-result.v1']],['payment.evidence.ticket-commands',['ticket.issue.v1']]]) {
    await rabbit('PUT',`/queues/%2F/${queue}`,{ durable:true,auto_delete:false,arguments:{} });
    await rabbit('DELETE',`/queues/%2F/${queue}/contents`);
    for (const routing_key of keys) await rabbit('POST',`/bindings/%2F/e/${exchange}/q/${queue}`,{ routing_key,arguments:{} });
  }
  const args = ['-Duser.timezone=UTC','-jar','target/payment-service-0.0.1-SNAPSHOT.jar','--server.address=127.0.0.1','--server.port=18085',
    '--spring.config.name=payment-evidence','--spring.application.name=payment-service','--spring.cloud.config.enabled=false','--eureka.client.enabled=false',
    `--payment.booking.base-url=${issuer}`,'--spring.security.oauth2.client.registration.booking.provider=booking',
    '--spring.security.oauth2.client.registration.booking.client-id=payment-scenario',
    '--spring.security.oauth2.client.registration.booking.client-secret=scenario-only',
    '--spring.security.oauth2.client.registration.booking.authorization-grant-type=client_credentials',
    `--spring.security.oauth2.client.provider.booking.token-uri=${issuer}/oauth/token`,
    '--spring.datasource.url=jdbc:postgresql://127.0.0.1:25432/payment_db','--spring.datasource.username=postgres','--spring.datasource.password=payment-flow-test',
    '--spring.jpa.hibernate.ddl-auto=validate','--spring.jpa.open-in-view=false','--spring.jpa.properties.hibernate.default_schema=payment_schema',
    '--spring.jpa.properties.hibernate.type.json_format_mapper=org.hibernate.type.format.jackson.Jackson3JsonFormatMapper',
    '--spring.rabbitmq.host=127.0.0.1','--spring.rabbitmq.port=25672','--spring.rabbitmq.username=payment_test','--spring.rabbitmq.password=payment-flow-test',
    '--spring.rabbitmq.publisher-confirm-type=correlated','--spring.rabbitmq.publisher-returns=true','--spring.rabbitmq.template.mandatory=true',
    `--flashticket.payment.jwt.issuer-uri=${issuer}`,`--flashticket.payment.jwt.jwk-set-uri=${issuer}/jwks`,'--flashticket.payment.jwt.audience=payment',
    '--vnpay.tmn-code=test',`--vnpay.hash-secret=${secret}`,'--vnpay.payment-url=https://sandbox.example.test/pay',
    `--vnpay.return-url=${base}/api/v1/payments/vnpay/return`,`--vnpay.ipn-url=${base}/api/v1/payments/vnpay/ipn`,
    '--vnpay.app-return-url=https://frontend.example.test/payment',`--payment.exchange=${exchange}`,
    '--payment.queues.decision=payment.evidence.decision','--payment.queues.issuance=payment.evidence.issuance','--payment.queues.completed=payment.evidence.completed',
    '--payment.outbox.poll-delay=200','--payment.saga.poll-delay=500','--payment.booking.read-timeout=PT0.5S'];
  logFd=openSync(logPath,'w');
  backend=spawn(process.env.PAYMENT_SCENARIO_JAVA ?? 'java',args,{cwd:serviceDir,windowsHide:true,stdio:['ignore',logFd,logFd],
    env:{...process.env,BOOKING_SERVICE_URL:issuer,PAYMENT_SERVICE_CLIENT_ID:'payment-scenario',PAYMENT_SERVICE_CLIENT_SECRET:'scenario-only',PAYMENT_SERVICE_TOKEN_URI:issuer+'/oauth/token'}});
  await waitFor(async()=> { if(backend.exitCode !== null) throw new Error(`Payment stopped: ${readFileSync(logPath,'utf8').slice(-5000)}`); try { const r=await fetch(base+'/api/v1/orders/'+randomUUID()+'/payment'); return r.status===401; } catch { return false; } },90000);
  console.log('Payment HTTP application ready; running observed scenarios.');

  const happy=newOrder();
  await scenario('Tạo thanh toán từ snapshot có voucher: 200.000 − 20.000 = 180.000 VND',async()=>{
    const http=await create(happy); expectStatus(http,200); happy.attemptId=http.response.body.data.attemptId;
    assert.equal(http.response.body.data.amount,'180000'); assert.equal(http.response.body.data.attemptState,'PENDING');
    assert.ok(bookingRequests.find(r=>r.orderId===happy.orderId)?.serviceCredentialValidated);
    return {http,bookingResponseFixture:happy.snapshot,database:state(happy.orderId)};
  });
  await scenario('Retry cùng key và IP khác: giữ nguyên attempt, URL và deadline',async()=>{
    const before=await get(happy); const http=await create(happy,{}, {clientIp:'203.0.113.99'}); expectStatus(http,200);
    assert.equal(http.response.body.data.attemptId,happy.attemptId); assert.equal(http.response.body.data.paymentUrl,before.response.body.data.latestAttempt.paymentUrl);
    assert.equal(http.response.body.data.expiresAt,happy.snapshot.expiresAt); assert.equal(state(happy.orderId).attempts.length,1);
    return {http,database:state(happy.orderId)};
  });
  await scenario('Key khác khi đang PENDING bị chặn',async()=>{const http=await create(happy,{key:randomUUID()});expectStatus(http,409);return {http,database:state(happy.orderId)};});
  await scenario('FE gửi amount giả: số tiền không bị FE điều khiển',async()=>{
    const order=newOrder();const http=await create(order,{}, {amount:'1'});assert.ok([200,400].includes(http.response.status));
    if(http.response.status===200)assert.equal(http.response.body.data.amount,'180000');return {http,database:state(order.orderId)};
  });
  for(const [title,options,status] of [
    ['Thiếu JWT',{token:null},401],['Role ORGANIZER không được tạo payment',{token:jwt('buyer-a','ORGANIZER')},403],
    ['JWT đã hết hạn',{token:jwt('buyer-a','BUYER',true)},401],['Thiếu Idempotency-Key',{key:undefined},400],
    ['Idempotency-Key không phải UUID',{key:'not-a-uuid'},400],['Không phải chủ đơn',{token:jwt('buyer-b')},404]]) {
    await scenario(title,async()=>{const http=await create(happy,options);expectStatus(http,status);return {http,database:state(happy.orderId)};});
  }
  await scenario('Order không tồn tại',async()=>{const order={orderId:randomUUID(),key:randomUUID()};const http=await create(order);expectStatus(http,404);return {http,database:state(order.orderId)};});
  for(const [mode,title,status] of [['expired','Booking báo đơn hết hạn',409],['cancelled','Booking báo đơn bị hủy',409],['unavailable','Booking trả 503',503],['timeout','Booking chậm quá read timeout',503]]) {
    await scenario(title,async()=>{const order=newOrder(mode);const http=await create(order);expectStatus(http,status);assert.equal(state(order.orderId).attempts.length,0);return {http,bookingMode:mode,database:state(order.orderId)};});
  }
  await scenario('Snapshot sai tổng tiền bị từ chối',async()=>{const order=newOrder();order.snapshot.total='1';const http=await create(order);expectStatus(http,422);assert.equal(state(order.orderId).attempts.length,0);return {http,database:state(order.orderId)};});
  await scenario('Snapshot trả 200 nhưng deadline đã qua: Payment tự chặn',async()=>{
    const order=newOrder();order.snapshot.frozenAt=new Date(Date.now()-60000).toISOString();order.snapshot.expiresAt=new Date(Date.now()-1000).toISOString();
    const http=await create(order);expectStatus(http,409);assert.equal(state(order.orderId).attempts.length,0);return {http,database:state(order.orderId)};
  });
  await scenario('Snapshot thay đổi sau lần lưu đầu bị conflict',async()=>{
    const saved=happy.snapshot.eventTitle;happy.snapshot.eventTitle='Changed after freeze';const http=await create(happy);happy.snapshot.eventTitle=saved;
    expectStatus(http,409);assert.equal(state(happy.orderId).attempts.length,1);return {http,database:state(happy.orderId)};
  });
  await scenario('Hai POST đồng thời cùng key chỉ tạo một attempt',async()=>{
    const order=newOrder();const results=await Promise.all([create(order),create(order)]);for(const r of results)expectStatus(r,200);
    assert.equal(results[0].response.body.data.attemptId,results[1].response.body.data.attemptId);assert.equal(state(order.orderId).attempts.length,1);
    return {httpResponses:results,database:state(order.orderId)};
  });
  await scenario('Webhook sai chữ ký: HTTP 200 nhưng RspCode=97, không ghi tiền',async()=>{
    const http=await ipn(happy,{invalidSignature:true});expectStatus(http,200);assert.equal(http.response.body.RspCode,'97');assert.equal(state(happy.orderId).chargeCount,0);return {http,database:state(happy.orderId)};
  });
  await scenario('Webhook đúng chữ ký nhưng sai amount: RspCode=04',async()=>{
    const http=await ipn(happy,{amount:'100'});assert.equal(http.response.body.RspCode,'04');assert.equal(state(happy.orderId).chargeCount,0);return {http,database:state(happy.orderId)};
  });
  await scenario('Browser return không ghi nhận thu tiền',async()=>{
    const http=await callHttp('GET','/api/v1/payments/vnpay/return?'+new URLSearchParams(happy.lastIpn),undefined,{token:null});
    expectStatus(http,302);assert.equal(state(happy.orderId).chargeCount,0);return {http,database:state(happy.orderId)};
  });
  await scenario('Webhook hợp lệ: SUCCEEDED + WAITING_BOOKING, broker ACK chưa tiến Saga',async()=>{
    const http=await pay(happy);const emitted=await outgoing(happy,'booking.accept-payment.v1');assert.equal(state(happy.orderId).saga.state,'WAITING_BOOKING');
    return {http,publicPaymentQuery:await get(happy),emitted,database:state(happy.orderId)};
  });
  await scenario('Booking ACCEPTED: confirmation + WAITING_TICKETS + IssueTickets',async()=>{
    const received=await accept(happy);const emitted=await outgoing(happy,'ticket.issue.v1');assert.deepEqual(emitted.data.purchase,happy.snapshot);
    assert.equal(state(happy.orderId).confirmationCount,1);return {received,publicPaymentQuery:await get(happy),emitted,database:state(happy.orderId)};
  });
  await scenario('Ticket success: Payment gửi Booking, vẫn WAITING_TICKETS',async()=>{
    const received=await tickets(happy);const emitted=await outgoing(happy,'booking.issuance-result.v1');assert.equal(state(happy.orderId).saga.state,'WAITING_TICKETS');
    assert.equal(emitted.data.ticketCount,2);return {received,publicPaymentQuery:await get(happy),emitted,database:state(happy.orderId)};
  });
  await scenario('Booking OrderCompleted: lúc này Saga mới COMPLETED',async()=>{
    const received=await complete(happy);await waitFor(()=>state(happy.orderId).saga.state==='COMPLETED');return {received,http:await get(happy),database:state(happy.orderId)};
  });
  await scenario('Webhook gửi lặp: RspCode=02, không thêm charge',async()=>{
    const http=await callHttp('GET','/api/v1/payments/vnpay/ipn?'+new URLSearchParams(happy.lastIpn),undefined,{token:null});assert.equal(http.response.body.RspCode,'02');
    assert.equal(state(happy.orderId).chargeCount,1);return {http,database:state(happy.orderId)};
  });
  await scenario('Thu thêm charge khác cùng order: tạo refund DUPLICATE_PAYMENT/PENDING',async()=>{
    const http=await ipn(happy);const database=state(happy.orderId);assert.equal(database.chargeCount,2);assert.equal(database.refunds.length,1);
    assert.equal(database.refunds[0].reason,'DUPLICATE_PAYMENT');assert.equal(database.saga.state,'COMPLETED');return {http,publicPaymentQuery:await get(happy),database};
  });
  await scenario('Kết quả cổng chưa rõ: UNKNOWN, không tạo thêm attempt',async()=>{
    const {order}=await start();const providerResponse=await ipn(order,{responseCode:'07',transactionStatus:'01'});assert.equal(state(order.orderId).attempts[0].state,'UNKNOWN');
    const http=await create(order);expectStatus(http,409);assert.equal(state(order.orderId).chargeCount,0);return {providerResponse,http,publicPaymentQuery:await get(order),database:state(order.orderId)};
  });
  await scenario('FAILED: retry key cũ trả kết quả cũ; key mới mở attempt mới',async()=>{
    const {order}=await start();const providerResponse=await ipn(order,{responseCode:'24',transactionStatus:'02'});const sameKey=await create(order);expectStatus(sameKey,200);
    assert.equal(sameKey.response.body.data.attemptState,'FAILED');assert.equal(sameKey.response.body.data.paymentUrl,null);
    const newKey=await create(order,{key:randomUUID()});expectStatus(newKey,200);assert.notEqual(newKey.response.body.data.attemptId,order.attemptId);
    assert.equal(state(order.orderId).attempts.length,2);return {providerResponse,sameKey,newKey,database:state(order.orderId)};
  });
  await scenario('Tiền đến sau buyer cancel: COMPENSATING + LATE_PAYMENT/PENDING',async()=>{
    const {order}=await start();order.mode='cancelled';const providerResponse=await pay(order);
    const received=await publish(order,'booking.payment-decision.v1',{orderId:order.orderId,chargeId:order.chargeId,decisionAt:new Date().toISOString(),outcome:'CANCELLED',acceptedChargeId:null});
    await waitFor(()=>state(order.orderId).saga.state==='COMPENSATING');const database=state(order.orderId);assert.equal(database.refunds[0].reason,'LATE_PAYMENT');
    assert.ok(!database.outbox.some(m=>m.messageType==='ticket.issue.v1'));return {providerResponse,received,http:await get(order),database};
  });
  await scenario('Ticket terminal FAILED: refund TICKET_ISSUANCE_FAILED/PENDING',async()=>{
    const {order}=await start();await pay(order);await accept(order);const received=await tickets(order,{failed:true});
    await waitFor(()=>state(order.orderId).saga.state==='COMPENSATING');const emitted=await outgoing(order,'booking.issuance-result.v1');
    assert.equal(state(order.orderId).refunds[0].reason,'TICKET_ISSUANCE_FAILED');return {received,emitted,http:await get(order),database:state(order.orderId)};
  });
  await scenario('Event cancellation marker chặn success và completion đến trễ',async()=>{
    const {order}=await start();await pay(order);await accept(order);
    sql(`insert into payment_schema.tbl_event_finance_snapshots(event_id,cancellation_id,cancelled_at,received_at) values ('${order.snapshot.eventId}','${randomUUID()}',clock_timestamp(),clock_timestamp())`);
    const receivedTicket=await tickets(order);await waitFor(()=>state(order.orderId).saga.state==='COMPENSATING');
    const receivedCompletion=await complete(order);await pause(500);const database=state(order.orderId);assert.equal(database.saga.state,'COMPENSATING');
    assert.equal(database.refunds.length,1);assert.equal(database.refunds[0].reason,'EVENT_CANCELLED');return {fixtureAction:'Đã chèn cancellation marker vào DB test, không gọi Event service',receivedTicket,receivedCompletion,database};
  });
  await scenario('Chưa nhận kết quả Ticket: chưa tự refund hoặc hoàn tất',async()=>{
    const {order}=await start();await pay(order);await accept(order);await outgoing(order,'ticket.issue.v1');await pause(1000);
    const database=state(order.orderId);assert.equal(database.saga.state,'WAITING_TICKETS');assert.equal(database.refunds.length,0);return {observationWindow:'1 giây sau khi lệnh IssueTickets đã publish, không gửi Ticket result',http:await get(order),database};
  });
} catch(error) { records.push({id:'HARNESS',title:'Khởi động/chạy bộ kịch bản',passed:false,error:error.stack});console.error(error.message); }
finally {
  if(backend && backend.exitCode===null){backend.kill();await pause(500);}
  if(logFd!==undefined)closeSync(logFd);
  fixture.closeAllConnections();await new Promise(resolveClosed=>fixture.close(resolveClosed));
  writeReport();
  if(records.some(r=>!r.passed))process.exitCode=1;
}
