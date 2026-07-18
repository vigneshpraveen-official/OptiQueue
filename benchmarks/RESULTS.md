# OptiQueue — Measured Benchmark Results
> All numbers below are real measurements from the runs described; nothing is estimated.
> Machine: Linux (Ubuntu), local runs on 2026-07-18. Database: PostgreSQL 14 (Zonky embedded — real Postgres server binary).

---

## 1. Index benchmark (Phase 6 / blueprint Day 6)

**Method:** `IndexBenchmark.java` (test-scoped main). Fresh Postgres; Flyway migrated to **V1 only** (no secondary indexes); seeded **10,000 products, 2,000 users, 100,000 orders, ~200,000 order_items**; each query executed with random parameters, 5 warm-up + 30 measured runs, average wall-clock over JDBC (what the application actually experiences). Then Flyway **V2** (secondary indexes) applied + `ANALYZE`, and the identical measurement repeated.

| Query (application code path) | Before indexes | After V2 indexes | Improvement |
|---|---|---|---|
| `orders WHERE user_id = ?` (order history) | 14.28 ms | 0.30 ms | **97.9%** |
| `orders WHERE status = ?` (staff dashboard) | 13.31 ms | 5.12 ms | **61.5%** |
| `order_items WHERE order_id = ?` (order detail) | 20.95 ms | 0.18 ms | **99.1%** |
| `SUM(quantity) WHERE product_id = ?` (analytics) | 21.78 ms | 0.50 ms | **97.7%** |
| **Average hot-query latency** | **17.58 ms** | **1.53 ms** | **≈91%** |

Indexes added in V2: `idx_orders_user_id`, `idx_orders_status`, `idx_order_items_order_id`, `idx_order_items_product_id`. (`users.username` / `products.sku` were already indexed by their UNIQUE constraints in V1.)

**Reproduce:**
```bash
cd optiqueue-backend
JAVA_HOME=~/.jdks/jdk-17.0.19+10 ./mvnw -q test-compile exec:java \
  -Dexec.mainClass=com.optiqueue.benchmark.IndexBenchmark -Dexec.classpathScope=test
```

## 2. Concurrency load test (Phase 7 / blueprint Day 7)

**Method:** k6 v0.57.0, `k6/order-race.js`, against the full running application (Spring Boot + embedded Postgres, Hikari pool 10, in-memory cache). Each virtual user is a distinct registered customer with its own JWT; all VUs fire `POST /api/orders` (qty 1) simultaneously (`per-vu-iterations: 1`). After the storm, final stock is read back and checked against the count of 201 responses.

| Scenario | VUs | Stock | Orders placed | 409 conflicts | Oversold units | p95 latency |
|---|---|---|---|---|---|---|
| Worst case: all VUs on **one** product row | 300 | 500 | 159 | 141 | **0** | 2934 ms |
| Realistic: VUs across **10** hot products | 300 | 500 | 293 (97.7%) | 7 | **0** | 998 ms |
| Scale: VUs across 10 hot products | **500** | 1000 | 495 (99.0%) | 5 | **0** | 1064 ms |

**Correctness invariant held in every run:** `final_stock == initial_stock − successful_orders` — zero overselling, zero lost updates, all responses accounted for (201 / 409 STOCK_CONFLICT / 409 INSUFFICIENT_STOCK).

**Interpretation (interview-ready):**
- The single-row scenario is optimistic locking's worst case: 300 transactions serialize on one version counter, so many exhaust their 3 retries and correctly receive `409 STOCK_CONFLICT` (the API contract tells clients to retry). Data stays perfectly consistent.
- Spread across 10 products (realistic hot-sale traffic), 3 bounded retries absorb almost all contention: 99% success at 500 concurrent buyers.
- The trade-off vs pessimistic locking (`SELECT … FOR UPDATE`): no held row locks / no deadlock risk / better read throughput, at the cost of retries under extreme single-row contention.

**Reproduce:**
```bash
# backend must be running (see guide.md), then:
~/.local/bin/k6 run -e VUS=500 -e STOCK=1000 -e PRODUCTS=10 k6/order-race.js
```

## 3. Cache benchmark (Phase 5 / blueprint Day 5)

**Method:** k6 `k6/read-load.js` — 20 constant VUs for 30 s, 70% product-list reads (3 distinct pages) + 30% product-detail reads over 60 products, against the full running application. Two runs, identical everything except the cache backend (`CACHE_TYPE=none` vs cache enabled).

| Metric | Cache OFF | Cache ON | Change |
|---|---|---|---|
| Requests served in 30 s | 28,353 | 75,017 | **2.6× throughput** |
| Avg response time | 20.82 ms | 7.73 ms | **−62.9%** |
| p95 response time | 47.09 ms | 17.31 ms | **−63.2%** |

With the cache on, repeated reads within the 5-min TTL are served without touching PostgreSQL at all (evictions occur on any stock/price change, so data stays fresh — see `ProductCachingIntegrationTest`). Local run used the in-memory cache backend; the Redis/Upstash backend has identical caching semantics but adds a network round-trip — re-measure on the deployed stack if you want a deployment-specific figure.

**Reproduce:** start backend with `CACHE_TYPE=none`, run `k6 run -e VUS=20 -e DURATION=30s k6/read-load.js`; restart with `CACHE_TYPE=simple` (or `redis`) and repeat.

## 4. Suggested resume bullet fills (from measured data)

- "…sustaining **500** concurrent order transactions with **zero overselling incidents**" (worst-case single-row: 300 concurrent, still zero oversell)
- "…cutting average query latency by **~91%** (17.6 ms → 1.5 ms) via targeted B-tree indexing, measured on a 100k-order PostgreSQL dataset"
- "…Redis-style caching layer cut repeat-read response times by **~63%** and raised read throughput **2.6×** (938 → 2,482 req/s)"
