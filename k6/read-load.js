import http from "k6/http";
import { check } from "k6";

/**
 * Phase 5 cache benchmark: identical read-heavy load, run twice —
 * once with the backend started with CACHE_TYPE=none and once with the cache
 * enabled (simple/redis). Compare http_req_duration between runs.
 *
 * Usage:  k6 run -e VUS=20 -e DURATION=20s k6/read-load.js
 */

const BASE = __ENV.BASE_URL || "http://localhost:8080";
const VUS = parseInt(__ENV.VUS || "20", 10);

export const options = {
  scenarios: {
    readers: {
      executor: "constant-vus",
      vus: VUS,
      duration: __ENV.DURATION || "20s",
    },
  },
};

export function setup() {
  const res = http.post(
    `${BASE}/api/auth/login`,
    JSON.stringify({ username: __ENV.ADMIN_USER || "admin", password: __ENV.ADMIN_PASS || "admin12345" }),
    { headers: { "Content-Type": "application/json" } }
  );
  if (res.status !== 200) throw new Error(`login failed: ${res.status}`);

  // Grab some real product ids to request
  const token = res.json("token");
  const page = http.get(`${BASE}/api/products?size=50`, {
    headers: { Authorization: `Bearer ${token}` },
  });
  const ids = page.json("content").map((p) => p.id);
  if (ids.length === 0) throw new Error("no products exist — create some first");
  return { token, ids };
}

export default function (data) {
  const headers = { Authorization: `Bearer ${data.token}` };
  // 70% list reads (few distinct pages → cache-friendly), 30% detail reads
  if (Math.random() < 0.7) {
    const page = Math.floor(Math.random() * 3);
    const res = http.get(`${BASE}/api/products?page=${page}&size=20`, { headers });
    check(res, { "list 200": (r) => r.status === 200 });
  } else {
    const id = data.ids[Math.floor(Math.random() * data.ids.length)];
    const res = http.get(`${BASE}/api/products/${id}`, { headers });
    check(res, { "detail 200": (r) => r.status === 200 });
  }
}

export function handleSummary(data) {
  const d = data.metrics.http_req_duration.values;
  const out = {
    requests: data.metrics.http_reqs.values.count,
    rps: Math.round(data.metrics.http_reqs.values.rate * 10) / 10,
    avgMs: Math.round(d.avg * 100) / 100,
    p95Ms: Math.round(d["p(95)"] * 100) / 100,
  };
  return { stdout: `\n=== READ LOAD SUMMARY ===\n${JSON.stringify(out, null, 2)}\n` };
}
