# OptiQueue — Your Guide
> How to run, test, and understand the project at every phase. Updated as we go.
> (Deep technical/agent context lives in `PROJECT_CONTEXT.md`.)

---

## What exists right now

**Days 1–8 of the blueprint are complete.** Backend (auth/JWT/RBAC, products, concurrency-safe orders, caching layer), React frontend, and all three benchmarks with **real measured numbers** (see `benchmarks/RESULTS.md`):

- Indexing cut average hot-query latency **~91%** (17.6 ms → 1.5 ms) on a 100k-order dataset.
- **500 concurrent buyers, zero overselling** (k6-verified against the DB).
- Caching cut repeat-read response time **~63%** and raised throughput **2.6×**.

Remaining: connecting real Neon + Upstash, and deployment (Render + Vercel) — those need your accounts.

## Run the whole app locally — NO cloud accounts needed

Terminal 1 (backend on :8080, embedded PostgreSQL, data resets on restart):
```bash
cd "/media/vp/PRIMARY/VS CODE/Projects/OptiQueue/optiqueue-backend"
JAVA_HOME=~/.jdks/jdk-17.0.19+10 ./mvnw test-compile exec:java \
  -Dexec.mainClass=com.optiqueue.LocalDevApplication -Dexec.classpathScope=test
```
Terminal 2 (frontend on :5173):
```bash
cd "/media/vp/PRIMARY/VS CODE/Projects/OptiQueue/optiqueue-frontend"
npm run dev
```
Open http://localhost:5173 — log in as `admin/admin12345` (create products, manage orders), `staff/staff12345` (restock, ship), or register your own customer account and buy things.

## Re-run the benchmarks yourself

```bash
# Index benchmark (self-contained, ~1 min)
cd optiqueue-backend
JAVA_HOME=~/.jdks/jdk-17.0.19+10 ./mvnw -q test-compile exec:java \
  -Dexec.mainClass=com.optiqueue.benchmark.IndexBenchmark -Dexec.classpathScope=test

# Concurrency race (backend must be running)
~/.local/bin/k6 run -e VUS=300 -e STOCK=500 -e PRODUCTS=10 k6/order-race.js

# Cache comparison (run once with backend started CACHE_TYPE=none, once with CACHE_TYPE=simple)
~/.local/bin/k6 run -e VUS=20 -e DURATION=30s k6/read-load.js
```

## One small sudo fix (optional, whenever convenient)

Vite currently uses file polling because your system's file-watcher limit is exhausted. The clean fix (needs admin — run it yourself):
```bash
echo fs.inotify.max_user_watches=524288 | sudo tee -a /etc/sysctl.conf && sudo sysctl -p
```

## One-time setup on your machine (already done for you)

- A real **JDK 17** was installed to `~/.jdks/jdk-17.0.19+10` (your system only had a Java 25 JRE — no compiler). No sudo was needed.
- Maven is not installed system-wide; the project uses the **Maven Wrapper** (`./mvnw`) which self-downloads.

Optional quality-of-life: add this to your `~/.bashrc` so `JAVA_HOME` is always set:
```bash
export JAVA_HOME=$HOME/.jdks/jdk-17.0.19+10
export PATH=$JAVA_HOME/bin:$PATH
```

## How to run the tests (works TODAY, no accounts needed)

```bash
cd "/media/vp/PRIMARY/VS CODE/Projects/OptiQueue/optiqueue-backend"
JAVA_HOME=~/.jdks/jdk-17.0.19+10 ./mvnw test
```
This spins up a **real PostgreSQL inside the test JVM** (no Docker, no cloud), runs Flyway migrations, and executes 18 tests — including the concurrency test where 20 threads race to buy 10 units and exactly 10 orders succeed.

## How to run the app for real (needs your Neon account)

1. Create a free account at https://neon.tech → new project (Postgres 16+, any region near you).
2. In Neon's dashboard, copy the connection details (host, database, user, password).
3. Run:
```bash
cd "/media/vp/PRIMARY/VS CODE/Projects/OptiQueue/optiqueue-backend"
export JAVA_HOME=~/.jdks/jdk-17.0.19+10
export DB_URL='jdbc:postgresql://<HOST>/<DATABASE>?sslmode=require'
export DB_USERNAME='<USER>'
export DB_PASSWORD='<PASSWORD>'
./mvnw spring-boot:run
```
4. The app starts on http://localhost:8080 — Flyway creates all tables on first boot, and two accounts are auto-created: `admin/admin12345` and `staff/staff12345` (we'll change these before deploying).

## Try it with curl (once running)

```bash
# Register a customer
curl -s -X POST localhost:8080/api/auth/register -H 'Content-Type: application/json' \
  -d '{"username":"vp","password":"password123"}'

# Log in → copy the "token" value from the response
curl -s -X POST localhost:8080/api/auth/login -H 'Content-Type: application/json' \
  -d '{"username":"admin","password":"admin12345"}'

# Create a product (as admin — replace $TOKEN)
curl -s -X POST localhost:8080/api/products -H "Authorization: Bearer $TOKEN" \
  -H 'Content-Type: application/json' \
  -d '{"sku":"PHONE-1","name":"Phone","price":999.99,"stockQuantity":50}'

# Place an order (as customer — use the customer token)
curl -s -X POST localhost:8080/api/orders -H "Authorization: Bearer $TOKEN" \
  -H 'Content-Type: application/json' \
  -d '{"items":[{"productId":1,"quantity":2}]}'
```

## The API at a glance

| Method | Endpoint | Who | What |
|---|---|---|---|
| POST | /api/auth/register | public | create CUSTOMER account |
| POST | /api/auth/login | public | get JWT |
| GET | /api/products (+/{id}) | logged in | browse (paginated: `?page=0&size=20`) |
| POST | /api/products | ADMIN | create product |
| PUT | /api/products/{id} | ADMIN, STAFF | edit name/price |
| PUT | /api/products/{id}/restock | ADMIN, STAFF | add stock |
| POST | /api/orders | CUSTOMER | place order (the concurrency-safe path) |
| GET | /api/orders/mine | CUSTOMER | my orders |
| GET | /api/orders (+/{id}) | ADMIN, STAFF (owner for /{id}) | view orders |
| PATCH | /api/orders/{id}/status | ADMIN, STAFF | CONFIRMED→SHIPPED etc.; cancel restores stock |

## Interview talking points you already own

- **Why optimistic locking (not pessimistic)?** Inventory is high-read/low-conflict; optimistic avoids holding row locks, scales reads, and conflicts are cheap to retry. Pessimistic (`SELECT … FOR UPDATE`) is the alternative we consciously rejected — know both.
- **The `@Version` mechanic:** every UPDATE runs `… WHERE id=? AND version=?`. If another transaction won the race, 0 rows update → exception → retry with **fresh transaction and fresh data** (that's why retry and transaction live on *separate beans* — stacking them on one method silently retries inside the dead transaction).
- **Why indexes come later (Phase 6):** so we can measure real before/after latency — resume numbers must be measured, not invented.

## What I need from you next

1. **Neon.tech** account → send me the connection string (Phase 1 live-boot + Phase 6 benchmarks).
2. **Upstash** account → send me the Redis URL (`rediss://…`) (Phase 5).
3. GitHub repo + Render + Vercel — only needed at Phase 9; I'll walk you through each click.
