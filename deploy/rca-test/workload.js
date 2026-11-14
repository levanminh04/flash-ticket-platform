import http from 'k6/http';
import exec from 'k6/execution';
import { check } from 'k6';
import { Counter } from 'k6/metrics';

if (__ENV.FT_TEST_ENVIRONMENT !== 'true') throw new Error('Isolated test environment required');
const fixtures = JSON.parse(open(__ENV.FT_FIXTURES_FILE));
const token = open(__ENV.FT_BUYER_TOKEN_FILE).trim();
const base = __ENV.FT_GATEWAY_URL;
const rate = Number(__ENV.FT_LOAD_RATE || 2);
const seconds = Number(__ENV.FT_LOAD_SECONDS || 60);
if (!Number.isInteger(rate) || rate < 1 || rate > 100 || seconds < 1 || seconds > 3600) {
  throw new Error('Declare a rate in 1..100 and duration in 1..3600 seconds');
}
if (!fixtures.events.length || !fixtures.orders.length) throw new Error('Prepared event/order fixtures required');
const accepted = new Counter('gateway_accepted');
const throttled = new Counter('gateway_throttled');
export const options = {
  scenarios: {
    common_routes: {
      executor: 'constant-arrival-rate', rate, timeUnit: '1s',
      duration: String(seconds) + 's', preAllocatedVUs: 4, maxVUs: 16,
      exec: 'readMix',
    },
  },
  summaryTrendStats: ['avg', 'p(95)', 'p(99)', 'max'],
};
export function readMix() {
  const iteration = exec.scenario.iterationInTest;
  const event = fixtures.events[iteration % fixtures.events.length];
  const order = fixtures.orders[iteration % fixtures.orders.length];
  const slot = iteration % 100;
  let path, route;
  if (slot < 50) {
    path = '/api/v1/events/' + event.eventId; route = 'GET /api/v1/events/{id}';
  } else if (slot < 75) {
    path = '/api/v1/orders/' + order.orderId; route = 'GET /api/v1/orders/{id}';
  } else if (slot < 90) {
    path = '/api/v1/orders/' + order.orderId + '/payment'; route = 'GET /api/v1/orders/{id}/payment';
  } else {
    path = '/api/v1/orders/' + order.orderId + '/tickets'; route = 'GET /api/v1/orders/{id}/tickets';
  }
  const response = http.get(base + path, {
    headers: { Authorization: 'Bearer ' + token },
    tags: { name: route, route },
    timeout: '35s',
  });
  if (response.status === 429) throttled.add(1, { route });
  else if (response.status >= 200 && response.status < 400) accepted.add(1, { route });
  check(response, { 'successful baseline response': value => value.status === 200 }, { route });
}
// No case name, injection state or answer label is sent to the application.
// Run the same fixture/route mix in all phases. Use fixtures.py separately on a declared,
// identical per-phase schedule for real payment/issuance/refund transactions.
