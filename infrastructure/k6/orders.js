import http from 'k6/http';
import { check, fail } from 'k6';
import { Counter, Rate, Trend } from 'k6/metrics';

function positiveInteger(name, fallback) {
  const value = Number(__ENV[name] || fallback);
  if (!Number.isInteger(value) || value <= 0) {
    throw new Error(`${name} must be a positive integer`);
  }
  return value;
}

const rate = positiveInteger('RATE', 500);
const duration = positiveInteger('DURATION_SECONDS', 30);
const preAllocatedVUs = positiveInteger('PRE_ALLOCATED_VUS', rate);
const maxVUs = positiveInteger('MAX_VUS', rate * 2);
if (maxVUs < preAllocatedVUs) {
  throw new Error('MAX_VUS must be at least PRE_ALLOCATED_VUS');
}
const baseUrl = (__ENV.BASE_URL || 'http://api-gateway:8080').replace(/\/$/, '');
const runId = __ENV.RUN_ID || `orders-${rate}`;
if (!/^[a-zA-Z0-9_-]+$/.test(runId)) {
  throw new Error('RUN_ID may only contain letters, digits, underscores and hyphens');
}
const orderAttempts = new Counter('order_attempts');
const successfulOrders = new Counter('successful_orders');
const orderErrors = new Rate('order_errors');
const orderLatency = new Trend('order_latency', true);
const statuses = new Counter('order_statuses');

export const options = {
  scenarios: {
    orders: {
      executor: 'constant-arrival-rate',
      rate,
      timeUnit: '1s',
      duration: `${duration}s`,
      preAllocatedVUs,
      maxVUs,
      gracefulStop: '30s',
    },
  },
  thresholds: {
    order_errors: [`rate<${__ENV.MAX_ERROR_RATE || '0.01'}`],
    order_latency: [`p(95)<${__ENV.P95_LIMIT_MS || '2000'}`],
    dropped_iterations: ['count==0'],
    successful_orders: ['count>0'],
  },
  // Group every order under one metric label instead of generating labels per ID.
  systemTags: ['status', 'method', 'name', 'scenario', 'expected_response'],
  summaryTrendStats: ['avg', 'min', 'med', 'max', 'p(90)', 'p(95)', 'p(99)'],
  discardResponseBodies: true,
};

export function setup() {
  for (const name of ['LOAD_TEST_EMAIL', 'LOAD_TEST_PASSWORD', 'PRODUCT_ID', 'SKU']) {
    if (!__ENV[name]) fail(`Missing environment variable: ${name}`);
  }
  const login = http.post(`${baseUrl}/api/auth/login`, JSON.stringify({
    email: __ENV.LOAD_TEST_EMAIL,
    password: __ENV.LOAD_TEST_PASSWORD,
  }), {
    headers: { 'Content-Type': 'application/json' },
    tags: { name: 'login' },
    responseType: 'text',
    timeout: '30s',
  });
  if (login.status !== 200) fail(`Login failed with HTTP ${login.status}`);
  const token = login.json('accessToken');
  if (!token) fail('Login response is missing accessToken');

  const product = http.get(`${baseUrl}/api/products/${__ENV.PRODUCT_ID}`, {
    tags: { name: 'product-preflight' }, responseType: 'text', timeout: '30s',
  });
  if (product.status !== 200) fail(`Product preflight failed with HTTP ${product.status}`);
  if (!product.json('variants').some((variant) => variant.sku === __ENV.SKU)) {
    fail('SKU does not belong to the selected product');
  }
  return {
    token,
    body: JSON.stringify({
      items: [{ productId: __ENV.PRODUCT_ID, sku: __ENV.SKU, quantity: 1 }],
      shippingAddress: 'Day 13 load test',
      note: runId,
    }),
  };
}

export default function createOrder(data) {
  const response = http.post(`${baseUrl}/api/orders`, data.body, {
    headers: { 'Content-Type': 'application/json', Authorization: `Bearer ${data.token}` },
    tags: { name: 'POST /api/orders' },
    timeout: '30s',
  });
  orderAttempts.add(1);
  const isCreated = response.status === 201;
  successfulOrders.add(isCreated ? 1 : 0);
  orderErrors.add(!isCreated);
  orderLatency.add(response.timings.duration);
  statuses.add(1, { status: String(response.status) });
  check(response, { 'order created (201)': () => isCreated });
}

export function handleSummary(data) {
  const values = (name) => data.metrics[name] ? data.metrics[name].values : {};
  const attempts = values('order_attempts').count || 0;
  const report = {
    runId,
    targetRps: rate,
    durationSeconds: duration,
    attemptedOrders: attempts,
    // Only POST /api/orders requests count towards RPS; login/preflight are excluded.
    achievedRps: attempts / duration,
    p95LatencyMs: values('order_latency')['p(95)'] ?? null,
    errorRate: values('order_errors').rate ?? null,
    successfulOrders: values('successful_orders').count || 0,
    droppedIterations: values('dropped_iterations').count || 0,
    thresholdsPassed: Object.values(data.metrics).every((metric) =>
      Object.values(metric.thresholds || {}).every((threshold) => threshold.ok)),
    metrics: data.metrics,
  };
  return {
    [`/results/${runId}-k6.json`]: JSON.stringify(report, null, 2),
    stdout: `${JSON.stringify({ ...report, metrics: undefined }, null, 2)}\n`,
  };
}
