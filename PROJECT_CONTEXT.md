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
| 5. Redis caching (Upstash) | Day 5 | ⬜ TODO | — |
| 6. Seed 10k products + index benchmark | Day 6 | ⬜ TODO | — |
| 7. k6 concurrency load test | Day 7 | ⬜ TODO | — |
| 8. React frontend | Day 8 | ⬜ TODO | — |
| 9. Deploy (Render/Vercel) + README | Day 9 | ⬜ TODO | — |

**Test suite:** 18/18 passing (`./mvnw test` with `JAVA_HOME=~/.jdks/jdk-17.0.19+10`).

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

1. **Phase 5**: RedisConfig + `@Cacheable` on product list/detail (TTL 5 min) + `@CacheEvict` on update/restock/order-placement/cancellation. Needs `REDIS_URL` from user (Upstash) — for local verification can also run without.
2. **Phase 6**: seed script (10k+ products, orders), V2__add_indexes.sql, `EXPLAIN ANALYZE` before/after on Neon → record real latency numbers in this file + guide.md.
3. **Phase 7**: k6 script (`k6/order-race.js`), needs k6 binary (user may need to install or download portable). Record max clean concurrency.
4. **Phase 8**: `optiqueue-frontend` Vite React app (pages: Login, ProductList, Cart, Orders, AdminDashboard; AuthContext; axios instance with JWT interceptor; `VITE_API_BASE_URL`).
5. **Phase 9**: user creates GitHub repo + Render/Vercel setups (guided); README.md; live E2E test.

## 9. Benchmark results (fill with REAL numbers only)

- Index latency improvement: _not yet measured_
- Cache DB-load reduction: _not yet measured_
- Max clean concurrent orders: _not yet measured (local integration test: 20 threads, 0 oversell)_
