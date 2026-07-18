# OptiQueue — Master Blueprint
### Concurrent Order Processing & Inventory Management System

---

## 1. Problem Statement

E-commerce/warehouse systems lose money and trust when multiple customers order the last units
of a product simultaneously — without proper concurrency control, two orders can both "succeed"
against stock that only exists once (overselling). OptiQueue solves this with a layered,
role-based backend that guarantees inventory correctness under concurrent load, while staying
fast through caching and query optimization.

**Resume claims this project must back up:**
- Layered (Controller → Service → Repository) backend with RBAC
- Concurrency control preventing overselling under simultaneous requests
- Measured DB query latency improvement via indexing
- Redis caching layer reducing repeat DB load

Every number in your resume bullets must come from a real benchmark you run in Phase 6-7 below —
not an estimate.

---

## 2. Tech Stack

| Layer | Choice | Why |
|---|---|---|
| Language | Java 17 | LTS, matches your resume skill claim |
| Framework | Spring Boot 3.x | Industry standard, matches Infosys stack expectations |
| Database | PostgreSQL | Free tier via **Neon.tech** (doesn't expire, unlike Render's 90-day free Postgres) |
| Cache | Redis | Free tier via **Upstash** (serverless Redis, works well with Spring's `RedisTemplate`) |
| ORM | Spring Data JPA (Hibernate) | Standard, supports `@Version` for optimistic locking |
| Auth | Spring Security + JWT | Matches your resume's JWT/RBAC claim |
| Frontend | React (Vite) + Axios + React Router | Lightweight, fast to build |
| Deployment (backend) | Render (free Web Service) | Free tier, auto-deploy from GitHub |
| Deployment (frontend) | Vercel | Free, trivial React deploy |
| Load testing | k6 or Apache Bench (ab) | To generate your real concurrency numbers |
| API testing | Postman | For manual verification + demo |

**Action needed from you:** create free accounts on Neon, Upstash, Render, Vercel before Day 1.
If you hit a snag creating any of these, tell me and I'll walk you through it.

---

## 3. System Architecture

```
┌─────────────┐      HTTPS/JWT       ┌──────────────────────────────────────┐
│   React     │ ───────────────────► │           Spring Boot API             │
│  Frontend   │ ◄─────────────────── │                                        │
│ (Vercel)    │        JSON           │  Controller Layer                     │
└─────────────┘                       │       │                               │
                                      │       ▼                               │
                                      │  Service Layer  ──────► Redis Cache   │
                                      │       │                (Upstash)      │
                                      │       ▼                               │
                                      │  Repository Layer (Spring Data JPA)   │
                                      └───────────┬────────────────────────────┘
                                                  ▼
                                        PostgreSQL (Neon)
                                        - Optimistic locking (@Version)
                                        - Indexes on hot columns
```

**Request flow for a critical path (placing an order):**
1. Client sends `POST /api/orders` with JWT.
2. Controller validates JWT + role (`CUSTOMER`).
3. Service layer starts a transaction, reads product row with its `version` field.
4. Service deducts stock, saves — Hibernate checks `version` matches; if another
   transaction updated it first, this one fails with `OptimisticLockException` and retries
   (max 3 attempts) or returns `409 Conflict` ("stock changed, please retry").
5. On success, Redis cache entry for that product is evicted (so next read is fresh).

---

## 4. Database Schema

```sql
-- users
CREATE TABLE users (
    id BIGSERIAL PRIMARY KEY,
    username VARCHAR(50) UNIQUE NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    role VARCHAR(20) NOT NULL CHECK (role IN ('ADMIN','STAFF','CUSTOMER')),
    created_at TIMESTAMP DEFAULT now()
);

-- products
CREATE TABLE products (
    id BIGSERIAL PRIMARY KEY,
    sku VARCHAR(50) UNIQUE NOT NULL,
    name VARCHAR(150) NOT NULL,
    price NUMERIC(10,2) NOT NULL,
    stock_quantity INT NOT NULL CHECK (stock_quantity >= 0),
    version BIGINT NOT NULL DEFAULT 0,   -- optimistic locking column
    created_at TIMESTAMP DEFAULT now()
);
CREATE INDEX idx_products_sku ON products(sku);

-- orders
CREATE TABLE orders (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT REFERENCES users(id),
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING'
        CHECK (status IN ('PENDING','CONFIRMED','CANCELLED','SHIPPED')),
    total_amount NUMERIC(10,2) NOT NULL,
    created_at TIMESTAMP DEFAULT now()
);
CREATE INDEX idx_orders_user_id ON orders(user_id);
CREATE INDEX idx_orders_status ON orders(status);

-- order_items
CREATE TABLE order_items (
    id BIGSERIAL PRIMARY KEY,
    order_id BIGINT REFERENCES orders(id),
    product_id BIGINT REFERENCES products(id),
    quantity INT NOT NULL,
    unit_price NUMERIC(10,2) NOT NULL
);
CREATE INDEX idx_order_items_order_id ON order_items(order_id);
```

**Why these indexes:** `sku`, `user_id`, `status`, and `order_id` are the columns you'll
filter/join on most — these are exactly what you'll benchmark in Phase 6 (before/after
`EXPLAIN ANALYZE` timings).

---

## 5. API Specification

| Method | Endpoint | Role | Purpose |
|---|---|---|---|
| POST | `/api/auth/register` | Public | Create account |
| POST | `/api/auth/login` | Public | Returns JWT |
| GET | `/api/products` | Any authenticated | List products (paginated, cached) |
| GET | `/api/products/{id}` | Any authenticated | Product detail (cached) |
| POST | `/api/products` | ADMIN | Create product |
| PUT | `/api/products/{id}` | ADMIN, STAFF | Update product |
| PUT | `/api/products/{id}/restock` | ADMIN, STAFF | Add stock |
| POST | `/api/orders` | CUSTOMER | Place order (concurrency-critical path) |
| GET | `/api/orders/{id}` | Owner, ADMIN, STAFF | Order detail |
| GET | `/api/orders` | ADMIN, STAFF | List all orders |
| PATCH | `/api/orders/{id}/status` | ADMIN, STAFF | Update order status |

**Sample request/response — `POST /api/orders`:**
```json
// Request
{
  "items": [
    { "productId": 12, "quantity": 2 },
    { "productId": 7,  "quantity": 1 }
  ]
}

// Response (201)
{
  "orderId": 105,
  "status": "CONFIRMED",
  "totalAmount": 1499.00
}

// Response (409 — lost the concurrency race after retries)
{
  "error": "STOCK_CONFLICT",
  "message": "Product 12 stock changed during processing. Please retry."
}
```

---

## 6. Concurrency Control — Core Logic

Use **optimistic locking** (simpler to implement and explain in an interview than pessimistic
row locks, and it's the industry-realistic answer for high-read/low-conflict inventory systems):

```java
@Entity
public class Product {
    @Id @GeneratedValue
    private Long id;
    private String sku;
    private int stockQuantity;

    @Version
    private Long version;   // Hibernate auto-manages this
}
```

```java
@Retryable(value = ObjectOptimisticLockingFailureException.class, maxAttempts = 3)
@Transactional
public Order placeOrder(OrderRequest request) {
    for (OrderItemRequest item : request.getItems()) {
        Product product = productRepository.findById(item.getProductId())
            .orElseThrow(...);
        if (product.getStockQuantity() < item.getQuantity()) {
            throw new InsufficientStockException(...);
        }
        product.setStockQuantity(product.getStockQuantity() - item.getQuantity());
        productRepository.save(product); // version check happens here
    }
    // create order + order_items, save
    redisTemplate.delete("product:" + item.getProductId()); // cache eviction
}
```

**Getting your real "300+ concurrent transactions" number (Phase 7):**
Write a k6 script that fires N concurrent `POST /api/orders` requests against a product with
a fixed stock count (e.g., 500 units). Confirm via DB query that final `stock_quantity` is
exactly `500 - (successful orders × quantity)` — zero overselling. Record the highest concurrent
load you tested cleanly (e.g., "tested stable at 300 concurrent requests, 0 overselling
incidents") — that's your honest, defensible resume number.

---

## 7. Caching Strategy

- Cache `GET /api/products` (list) and `GET /api/products/{id}` in Redis, TTL 5 minutes.
- Evict the specific product's cache key on any stock update (restock or order placement).
- **How to measure your cache-load-reduction %:** run the same read load twice — once hitting
  DB directly (cache disabled) and once with cache enabled — compare DB query counts (Postgres
  `pg_stat_statements` or simple query logging) or response times. The % reduction in DB hits
  or average response time is your real number for the "close to 40%" resume claim.

---

## 8. RBAC Design

JWT payload includes `role` claim. Spring Security config:
```java
@PreAuthorize("hasRole('ADMIN')")
@PostMapping("/api/products")
public ResponseEntity<Product> createProduct(...) { ... }

@PreAuthorize("hasAnyRole('ADMIN','STAFF')")
@PutMapping("/api/products/{id}/restock")
public ResponseEntity<Product> restock(...) { ... }
```

---

## 9. Folder Structure

```
optiqueue-backend/
├── src/main/java/com/optiqueue/
│   ├── config/         (SecurityConfig, RedisConfig, RetryConfig)
│   ├── controller/      (AuthController, ProductController, OrderController)
│   ├── service/          (AuthService, ProductService, OrderService)
│   ├── repository/       (UserRepository, ProductRepository, OrderRepository)
│   ├── entity/            (User, Product, Order, OrderItem)
│   ├── dto/                (request/response DTOs)
│   ├── security/            (JwtUtil, JwtAuthFilter)
│   └── exception/            (GlobalExceptionHandler, custom exceptions)
├── src/test/java/...        (unit + integration tests)
└── src/main/resources/
    ├── application.yml
    └── db/migration/          (Flyway SQL scripts — recommended over auto-DDL)

optiqueue-frontend/
├── src/
│   ├── pages/     (Login, ProductList, Cart, AdminDashboard)
│   ├── components/
│   ├── context/    (AuthContext)
│   └── api/         (axios instance)
```

---

## 10. Execution Timeline (9 working days)

| Day | Focus | Deliverable |
|---|---|---|
| 1 | Project skeleton, DB schema, entities, Flyway migrations | Boots up locally, connects to Neon |
| 2 | Auth: register/login, JWT issuing, Spring Security config | Can log in and get a token |
| 3 | Product CRUD APIs + validation + RBAC | Postman collection passes |
| 4 | Order placement + optimistic locking + retry logic | Manually verified no overselling |
| 5 | Redis integration (Upstash) + cache eviction logic | Cache hit/miss visibly working |
| 6 | Seed 10k+ products, add indexes, benchmark with `EXPLAIN ANALYZE` | Real before/after latency numbers |
| 7 | k6 load test script for concurrency, run and record results | Real concurrency number for resume |
| 8 | React frontend: login, product list, cart/order, admin dashboard | End-to-end demo works locally |
| 9 | Deploy (Render + Neon + Upstash + Vercel), write README, final test pass | Live demo link works |

---

## 11. Testing Plan

- **Unit tests:** JUnit + Mockito for `OrderService` (mock repository, verify locking/retry logic).
- **Integration test:** Spring Boot Test with an embedded/test Postgres — simulate two
  concurrent `placeOrder` calls in threads, assert final stock is correct.
- **Manual:** Postman collection covering every endpoint + role (test as ADMIN, STAFF, CUSTOMER).

---

## 12. Deployment Steps

1. Push backend to GitHub.
2. Neon: create Postgres project, copy connection string into Render env vars.
3. Upstash: create Redis database, copy REST URL/token into Render env vars.
4. Render: create Web Service from GitHub repo, set env vars (`DB_URL`, `REDIS_URL`, `JWT_SECRET`),
   build command `mvn clean package`, start command `java -jar target/*.jar`.
5. Vercel: import frontend repo, set `VITE_API_BASE_URL` env var to your Render backend URL.
6. Test the live deployed flow end-to-end before treating it as demo-ready.

---

## 13. Resume Metrics Mapping

| Resume claim | Where the number comes from |
|---|---|
| "sustaining [X]+ concurrent transactions without conflicts" | Day 7 k6 load test — use the actual highest clean value |
| "cutting average query latency by [X]%" | Day 6 `EXPLAIN ANALYZE` before/after indexing |
| "reducing repeated-read DB load by [X]%" | Day 5-6 cache hit-rate/response-time comparison |

**Do not fill these into your resume until you've actually run the tests — send me the real
numbers once you have them and I'll update the LaTeX.**

---

## 14. Risks & Fallbacks

- **Upstash Redis free tier has request limits** — if you hit them during load testing, fall
  back to a local Redis via Docker for the benchmark itself, and only use Upstash for the
  live deployed demo (this is normal practice, no need to disclose in resume).
- **Neon free tier can pause on inactivity** — first request after idle may be slow; mention
  this if a recruiter tests the live link and sees a cold-start delay.
- **If optimistic locking retries feel too simple for interview depth** — you can additionally
  implement and explain pessimistic locking (`SELECT ... FOR UPDATE`) as an alternative you
  evaluated, which shows deeper trade-off understanding.

---

## 15. Open Items — Confirm With Me

- [ ] Do you want Flyway for migrations (recommended, more professional) or Hibernate
      auto-DDL (faster to start but less production-realistic)?
- [ ] Once you run the Day 6/7 benchmarks, send me the actual numbers so I update your resume
      LaTeX to replace the `[X]%` placeholders with real, defensible figures.
- [ ] If you get stuck on Neon/Upstash/Render setup at any point, tell me exactly where and
      I'll give step-by-step help rather than assuming it went smoothly.
