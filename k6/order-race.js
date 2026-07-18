import http from "k6/http";
import { check } from "k6";
import { Counter } from "k6/metrics";

/**
 * OptiQueue concurrency test (Phase 7).
 *
 * N virtual users all try to buy 1 unit of the SAME product at the same
 * moment. The pass criterion is exactness: after the storm,
 *   final_stock == initial_stock - (number of 201 responses)
 * i.e. zero overselling and zero lost updates, under any mix of
 * 201 / 409 STOCK_CONFLICT / 409 INSUFFICIENT_STOCK responses.
 *
 * Usage:
 *   k6 run -e VUS=300 -e STOCK=500 k6/order-race.js
 *   BASE_URL defaults to http://localhost:8080
 */

const BASE = __ENV.BASE_URL || "http://localhost:8080";
const VUS = parseInt(__ENV.VUS || "300", 10);
const STOCK = parseInt(__ENV.STOCK || "500", 10);
// 1 = extreme single-row contention (worst case for optimistic locking);
// >1 = realistic spread of the same total stock across several hot products.
const PRODUCTS = parseInt(__ENV.PRODUCTS || "1", 10);

export const options = {
  setupTimeout: "10m",   // registering VUS users does a BCrypt hash per user
  scenarios: {
    order_storm: {
      executor: "per-vu-iterations",
      vus: VUS,
      iterations: 1,
      maxDuration: "5m",
    },
  },
};

export const ordersPlaced = new Counter("orders_placed");
export const stockConflicts = new Counter("stock_conflicts");
export const insufficientStock = new Counter("insufficient_stock");

const JSON_HEADERS = { "Content-Type": "application/json" };

export function setup() {
  const runId = Date.now();

  // Admin creates the contested product
  const adminLogin = http.post(
    `${BASE}/api/auth/login`,
    JSON.stringify({ username: __ENV.ADMIN_USER || "admin", password: __ENV.ADMIN_PASS || "admin12345" }),
    { headers: JSON_HEADERS }
  );
  if (adminLogin.status !== 200) throw new Error(`admin login failed: ${adminLogin.status}`);
  const adminToken = adminLogin.json("token");

  const perProductStock = Math.floor(STOCK / PRODUCTS);
  const productIds = [];
  for (let p = 0; p < PRODUCTS; p++) {
    const created = http.post(
      `${BASE}/api/products`,
      JSON.stringify({
        sku: `RACE-${runId}-${p}`,
        name: `k6 Contested Item ${runId}/${p}`,
        price: 99.99,
        stockQuantity: perProductStock,
      }),
      { headers: { ...JSON_HEADERS, Authorization: `Bearer ${adminToken}` } }
    );
    if (created.status !== 201) throw new Error(`product create failed: ${created.status} ${created.body}`);
    productIds.push(created.json("id"));
  }

  // Register one customer per VU (registration returns a JWT directly)
  const tokens = [];
  for (let i = 0; i < VUS; i++) {
    const res = http.post(
      `${BASE}/api/auth/register`,
      JSON.stringify({ username: `k6buyer${i}_${runId}`, password: "password123" }),
      { headers: JSON_HEADERS }
    );
    if (res.status !== 201) throw new Error(`register failed for buyer ${i}: ${res.status}`);
    tokens.push(res.json("token"));
  }

  console.log(`setup done: ${PRODUCTS} product(s) [${productIds}] with stock ${perProductStock} each, ${VUS} buyers ready`);
  return { productIds, tokens, adminToken, initialStock: perProductStock * PRODUCTS };
}

export default function (data) {
  const token = data.tokens[__VU - 1];
  const productId = data.productIds[(__VU - 1) % data.productIds.length];
  const res = http.post(
    `${BASE}/api/orders`,
    JSON.stringify({ items: [{ productId, quantity: 1 }] }),
    { headers: { ...JSON_HEADERS, Authorization: `Bearer ${token}` } }
  );

  if (res.status === 201) {
    ordersPlaced.add(1);
  } else if (res.status === 409) {
    const code = res.json("error");
    if (code === "STOCK_CONFLICT") stockConflicts.add(1);
    else if (code === "INSUFFICIENT_STOCK") insufficientStock.add(1);
  }

  check(res, {
    "response is 201 or 409": (r) => r.status === 201 || r.status === 409,
  });
}

export function teardown(data) {
  let finalStock = 0;
  for (const id of data.productIds) {
    const res = http.get(`${BASE}/api/products/${id}`, {
      headers: { Authorization: `Bearer ${data.adminToken}` },
    });
    finalStock += res.json("stockQuantity");
  }
  console.log(`FINAL_STOCK=${finalStock} INITIAL_STOCK=${data.initialStock} PRODUCTS=[${data.productIds}]`);
}

export function handleSummary(data) {
  const placed = data.metrics.orders_placed ? data.metrics.orders_placed.values.count : 0;
  const conflicts = data.metrics.stock_conflicts ? data.metrics.stock_conflicts.values.count : 0;
  const insufficient = data.metrics.insufficient_stock ? data.metrics.insufficient_stock.values.count : 0;
  const p95 = data.metrics.http_req_duration.values["p(95)"];
  const summary = {
    vus: VUS,
    initialStock: STOCK,
    ordersPlaced: placed,
    stockConflicts: conflicts,
    insufficientStock: insufficient,
    p95LatencyMs: Math.round(p95 * 100) / 100,
  };
  return {
    stdout: `\n=== RACE SUMMARY ===\n${JSON.stringify(summary, null, 2)}\n`,
    "k6/last-run-summary.json": JSON.stringify(summary, null, 2),
  };
}
