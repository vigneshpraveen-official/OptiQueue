# OptiQueue — Project Context & Progress Log
> **Audience:** AI agents / future contributors picking up this project at any phase.
> **Source of truth for requirements:** `optiqueue_master.md` (the master blueprint).
> **User-facing how-to:** `guide.md`.
> Keep this file updated after every meaningful change.

---

## 1. Current Status (last updated: 2026-07-18)

| Phase | Blueprint Day | Status | Proof |
|---|---|---|---|
| 1. Skeleton, schema, entities, Flyway | Day 1 | ✅ DONE | `OptiqueueBackendApplicationTests` boots full context vs embedded Postgres |
| 2. Auth (register/login, JWT, security) | Day 2 | ✅ DONE | `AuthFlowIntegrationTest` (6 tests) |
| 3. Product CRUD + RBAC | Day 3 | ✅ DONE | `ProductRbacIntegrationTest` (5 tests) |
| 4. Orders + optimistic locking + retry | Day 4 | ✅ DONE | `ConcurrentOrderIntegrationTest` (3) + `OrderServiceRetryTest` (3) |
| 5. Redis caching (Upstash) | Day 5 | 🟡 CODE DONE | `ProductCachingIntegrationTest`; cache benchmarked (−63% avg latency, 2.6× throughput). Only the Upstash connection itself pends user credentials |
| 6. Seed 10k products + index benchmark | Day 6 | ✅ DONE | `IndexBenchmark` — ~91% avg latency cut, see benchmarks/RESULTS.md |
| 7. k6 concurrency load test | Day 7 | ✅ DONE | `k6/order-race.js` — 500 VUs, 0 oversell, see benchmarks/RESULTS.md |
| 8. React frontend | Day 8 | ✅ DONE | builds clean; dev server verified serving; browser walk-through by user pending |
| 9. Deploy (Render/Vercel) + README | Day 9 | ⬜ TODO | blocked on user accounts |

**Test suite:** 20/20 passing (`./mvnw test` with `JAVA_HOME=~/.jdks/jdk-17.0.19+10`).
**All measured benchmark numbers live in `benchmarks/RESULTS.md` — treat that file as the source of truth for resume figures.**

## 2. Blocked on user (human actions pending)

- **Neon.tech** Postgres: user must create free account + project, provide JDBC URL/user/password → goes into `DB_URL`, `DB_USERNAME`, `DB_PASSWORD` env vars. Until then, local runs use embedded-Postgres tests only; the app cannot boot against a real dev DB.
- **Upstash** Redis: user must create free DB, provide `rediss://…` URL → `REDIS_URL` env var (Phase 5).
- **Render + Vercel + GitHub remote**: needed at Phase 9. User handles all dashboard actions manually.
- **HARD CONSTRAINT:** Never use MCP tools/connectors (Vercel, Figma, Supabase, etc.) — shared account config belongs to the user's friend. All cloud actions are done by the user manually in dashboards, guided by us.

## 3. Environment facts (this machine)

- Ubuntu Linux; project path: `/media/vp/PRIMARY/VS CODE/Projects/OptiQueue` (note the space in `VS CODE` — always quote paths).
- System Java is a **JRE-only** OpenJDK 25 (no javac). A real **JDK 17 (Temurin 17.0.19+10)** is installed at `~/.jdks/jdk-17.0.19+10`. Build with:
  `JAVA_HOME=~/.jdks/jdk-17.0.19+10 ./mvnw <goal>`
- No system Maven → use `./mvnw` (Maven Wrapper 3.9.16, downloads on first run).
- No Docker, no local Postgres/Redis. Integration tests use **Zonky embedded-postgres** (real PG binary in-JVM, no Docker).
- Node 22 + npm 9 available for the frontend.
- Admin/sudo commands: **left to the user** — never run them.
- Git repo root = project root; branch `master`; no remote configured yet.

## 4. Key architecture decisions (and why)

1. **Spring Boot 3.5.3, Java 17** — blueprint/resume say "Spring Boot 3.x, Java 17". start.spring.io now only serves Boot 4.x, so the pom parent was manually pinned to 3.5.3 (latest 3.x on Maven Central).
2. **Flyway over auto-DDL** (blueprint open-item resolved as recommended). `ddl-auto: validate` so Hibernate verifies entity↔schema match at boot.
3. **V1 migration has NO secondary indexes deliberately** — they arrive in V2 during Phase 6 so before/after `EXPLAIN ANALYZE` latency numbers are real. Note: `users.username` and `products.sku` UNIQUE constraints already create indexes in V1.
4. **Retry/transaction split across two beans**: `OrderService.placeOrder` carries `@Retryable` (3 attempts, jittered backoff) and delegates to `OrderPlacementService.placeOrderOnce` which carries `@Transactional`. Same-method stacking is a known trap: a retry must get a fresh transaction + fresh persistence context.
5. **Optimistic locking** via `@Version Long version` on `Product`. Exhausted retries → `ObjectOptimisticLockingFailureException` → `GlobalExceptionHandler` maps to **409 `STOCK_CONFLICT`** (per blueprint API contract).
6. **Public registration is CUSTOMER-only** (403 `ROLE_NOT_ALLOWED` otherwise). ADMIN/STAFF are provisioned by `DemoUserBootstrap` at startup from env (`optiqueue.bootstrap.*`), defaults: `admin/admin12345`, `staff/staff12345` — MUST be overridden via env in production.
7. **JWT**: jjwt 0.12.x, HS256 key from `optiqueue.jwt.secret` (`JWT_SECRET` env), role claim `role`, filter `JwtAuthFilter` → `ROLE_<role>` authority. Stateless; CSRF disabled; unauthenticated → 401 via `HttpStatusEntryPoint`.
8. **Order cancellation restores stock** (design choice beyond blueprint; interview-defensible). Status transitions constrained: PENDING→{CONFIRMED,CANCELLED}, CONFIRMED→{SHIPPED,CANCELLED}, SHIPPED/CANCELLED terminal. New orders are created directly CONFIRMED.
9. **Cache config placeholder**: `spring.cache.type=${CACHE_TYPE:none}` — Phase 5 flips to `redis`; `management.health.redis.enabled=false` until then. `@Cacheable/@CacheEvict` annotations NOT yet added (Phase 5 work).
10. **Extra endpoint beyond blueprint:** `GET /api/orders/mine` (CUSTOMER's own orders) — needed by the frontend.
11. **UserDetailsServiceAutoConfiguration excluded** in application.yml (JWT-only auth, silences generated-password warning).

## 5. Code map (backend)

```
optiqueue-backend/src/main/java/com/optiqueue/
├── config/    SecurityConfig (filter chain, CORS via CORS_ALLOWED_ORIGINS env, BCrypt),
│              RetryConfig (@EnableRetry), DemoUserBootstrap (admin/staff seeding)
├── controller/ AuthController, ProductController, OrderController
├── service/    AuthService, ProductService, OrderService (retry wrapper + queries + status),
│               OrderPlacementService (transactional order core — THE concurrency-critical code)
├── repository/ UserRepository, ProductRepository, OrderRepository (findDetailById fetch-join),
│               OrderItemRepository
├── entity/     User, Product (@Version), Order, OrderItem, Role, OrderStatus
├── dto/        AuthDtos, ProductDtos, OrderDtos (java records, static from() mappers)
├── security/   JwtUtil (issue/parse), JwtAuthFilter (OncePerRequestFilter)
└── exception/  ApiException (status+code base), InsufficientStockException (409),
                NotFoundException (404), GlobalExceptionHandler (@RestControllerAdvice)

src/main/resources/
├── application.yml           all config env-var-driven with local defaults
└── db/migration/V1__init_schema.sql   (V2 = indexes, arrives in Phase 6)

src/test/java/com/optiqueue/
├── testsupport/TestDatabaseConfig.java   embedded PG DataSource override
├── OptiqueueBackendApplicationTests      boot smoke
├── auth/AuthFlowIntegrationTest          register/login/401s
├── product/ProductRbacIntegrationTest    role matrix, validation, dup SKU
└── order/ConcurrentOrderIntegrationTest  20-thread race: zero overselling
        OrderServiceRetryTest             @Retryable wiring (mocked core)
```

## 6. API surface (implemented)

Matches blueprint §5 exactly, plus `GET /api/orders/mine` (CUSTOMER). Error contract: `{"error": CODE, "message": …}`; validation: `{"error":"VALIDATION_FAILED","fields":{…}}`.

## 7. Env vars consumed

`DB_URL, DB_USERNAME, DB_PASSWORD, DB_POOL_SIZE, REDIS_URL, CACHE_TYPE, PORT, JWT_SECRET, JWT_EXPIRATION_MS, CORS_ALLOWED_ORIGINS (comma-separated), SQL_LOG_LEVEL, optiqueue.bootstrap.* (ADMIN/STAFF seeds)`

## 8. Next steps (in order)

1. **User provides Neon credentials** → boot app against Neon (`DB_URL` etc.), verify Flyway migrates + bootstrap users appear.
2. **User provides Upstash `REDIS_URL`** → run with `CACHE_TYPE=redis`, verify hit/miss + eviction against real Redis (watch for serialization of `PageResponse` records through GenericJackson2JsonRedisSerializer — validated only against simple cache so far; if deserialization misbehaves, fall back to caching JSON strings).
3. **Phase 9 deploy** (user does all dashboard clicks; NO MCP tools):
   - GitHub repo + push (user creates repo, we add remote and push)
   - Render Web Service: build `./mvnw clean package -DskipTests`, start `java -jar target/optiqueue-backend-0.0.1-SNAPSHOT.jar`, env: DB_URL/DB_USERNAME/DB_PASSWORD/REDIS_URL/CACHE_TYPE=redis/JWT_SECRET (generate strong!)/CORS_ALLOWED_ORIGINS=<vercel url>/bootstrap admin+staff passwords
   - Vercel: import `optiqueue-frontend`, env `VITE_API_BASE_URL=<render url>`; SPA rewrite for react-router (vercel.json with rewrites to /index.html — NOT YET CREATED)
   - README.md for GitHub (NOT YET WRITTEN)
4. Optional: re-run read-load benchmark against deployed stack for a Redis-specific cache figure.

## 9. Benchmark results (all REAL, measured 2026-07-18 — full detail in benchmarks/RESULTS.md)

- **Indexing**: avg hot-query latency 17.58 ms → 1.53 ms (**~91% cut**) on 100k orders/200k items/10k products (PostgreSQL 14).
- **Concurrency**: 500 concurrent buyers → 495 orders (99%), **0 oversold units**; worst-case 300 VUs on ONE row → 159 orders, 141 clean 409s, **0 oversold**. Invariant `final_stock == initial − successes` held in every run.
- **Cache**: identical 30s read load — avg 20.82 → 7.73 ms (**−63%**), throughput 938 → 2,482 rps (**2.6×**), p95 −63%.

## 10. Local dev/testing infrastructure (no cloud needed)

- `LocalDevApplication` (src/test) runs the real app on embedded Postgres: `./mvnw test-compile exec:java -Dexec.mainClass=com.optiqueue.LocalDevApplication -Dexec.classpathScope=test` (+ optional `CACHE_TYPE=simple|none` env). Data is per-process, wiped on restart. Bootstrap users admin/admin12345, staff/staff12345.
- `IndexBenchmark` (src/test) — Phase 6 harness, self-contained.
- k6 binary at `~/.local/bin/k6` (v0.57.0, portable install). Scripts: `k6/order-race.js` (concurrency), `k6/read-load.js` (cache).
- Frontend dev: `npm run dev` in optiqueue-frontend (Vite polls files — host inotify limit exhausted; permanent fix needs sudo sysctl fs.inotify.max_user_watches=524288).
