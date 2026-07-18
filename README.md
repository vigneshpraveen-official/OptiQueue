# OptiQueue

**Concurrent Order Processing & Inventory Management System** — a layered Spring Boot backend + React frontend that guarantees inventory correctness under concurrent load, with measured performance numbers to back it up.

> The problem: when many customers order the last units of a product simultaneously, naive systems oversell — two orders both "succeed" against stock that exists once. OptiQueue makes that impossible.

## Measured results (see [`benchmarks/RESULTS.md`](benchmarks/RESULTS.md) for methodology)

| Claim | Measurement |
|---|---|
| Concurrency safety | **500 concurrent buyers, 0 units oversold** (k6, DB-verified invariant); worst-case 300 buyers racing on a single row — still 0 oversold |
| Query optimization | Secondary indexing cut avg hot-query latency **17.6 ms → 1.5 ms (~91%)** on 100k orders / 200k order items |
| Caching | Repeat-read avg response **−63%** (20.8 → 7.7 ms), throughput **2.6×** (938 → 2,482 req/s) |

## Architecture

```
React (Vite) ──HTTPS/JWT──► Spring Boot 3 API ──► Redis (cache, 5-min TTL + eviction)
                             Controller → Service → Repository (Spring Data JPA)
                                          │
                                          ▼
                             PostgreSQL — Flyway migrations,
                             optimistic locking (@Version), B-tree indexes
```

**The concurrency-critical path** (`POST /api/orders`):
1. Transaction reads product rows including their `@Version` counter.
2. Stock is decremented; at commit Hibernate issues `UPDATE … WHERE id=? AND version=?`.
3. If a rival transaction committed first, 0 rows match → `OptimisticLockingFailureException` → the whole transaction is retried (max 3 attempts, jittered backoff) with **fresh reads in a fresh transaction** (retry and transaction live on separate beans — stacking both on one method would retry inside the dead transaction).
4. Retries exhausted → `409 {"error":"STOCK_CONFLICT"}` and the client is told to retry. Data is never wrong.

## Tech stack

Java 17 · Spring Boot 3.5 · Spring Security + JWT (jjwt) · Spring Data JPA/Hibernate · PostgreSQL (Neon) · Flyway · Redis (Upstash) · Spring Retry · React 19 + Vite · Axios · React Router · k6 · JUnit 5 + Mockito + embedded Postgres

## API

| Method | Endpoint | Role |
|---|---|---|
| POST | `/api/auth/register` · `/api/auth/login` | public |
| GET | `/api/products`, `/api/products/{id}` | authenticated (cached) |
| POST | `/api/products` | ADMIN |
| PUT | `/api/products/{id}`, `…/restock` | ADMIN, STAFF |
| POST | `/api/orders` | CUSTOMER (concurrency-critical) |
| GET | `/api/orders` · PATCH `…/{id}/status` | ADMIN, STAFF |
| GET | `/api/orders/mine`, `/api/orders/{id}` | owner (or ADMIN/STAFF) |

## Run locally

Backend (needs JDK 17; uses an embedded PostgreSQL — no DB install required):
```bash
cd optiqueue-backend
./mvnw test-compile exec:java -Dexec.mainClass=com.optiqueue.LocalDevApplication -Dexec.classpathScope=test
```
Frontend:
```bash
cd optiqueue-frontend && npm install && npm run dev
```
Open http://localhost:5173 — bootstrap accounts `admin/admin12345`, `staff/staff12345`, or register as a customer.

Tests (20 tests incl. a 20-thread overselling race against real Postgres):
```bash
cd optiqueue-backend && ./mvnw test
```

## Configuration (env vars)

`DB_URL` `DB_USERNAME` `DB_PASSWORD` — PostgreSQL · `REDIS_URL` `CACHE_TYPE=redis` — cache · `JWT_SECRET` (≥256-bit) `JWT_EXPIRATION_MS` — auth · `CORS_ALLOWED_ORIGINS` — frontend origin(s) · `PORT` — server port. Frontend: `VITE_API_BASE_URL`.

## Design decisions worth asking me about

- **Optimistic vs pessimistic locking** — chosen for high-read/low-conflict inventory: no held row locks, no deadlocks, cheap conflict recovery; the trade-off shows up only under extreme single-row contention (measured and documented).
- **Indexes shipped as a second migration** — so the latency improvement could be measured honestly, not assumed.
- **`PageResponse` instead of Spring's `Page`** in cached endpoints — `PageImpl` isn't safe for cache serialization round-trips.
- **Cancellation restores stock** inside the same optimistic-lock protection.
