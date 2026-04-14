# SmartBus Phase III Integration Architecture

**Course:** CSE 598 — Software Integration and Engineering  
**Project:** SmartBus — Distributed Ticket Booking Platform  
**Phase:** III — Data Management and Integration Layer  
**Date:** 2026-04-13  

---

## Table of Contents

1. [System Overview](#1-system-overview)
2. [Technology Stack Justification](#2-technology-stack-justification)
3. [Database Design](#3-database-design)
4. [API Specifications](#4-api-specifications)
5. [Data Flow Diagrams](#5-data-flow-diagrams)
6. [Caching and Performance Analysis](#6-caching-and-performance-analysis)
7. [Security Implementation](#7-security-implementation)
8. [Asynchronous Processing Design](#8-asynchronous-processing-design)
9. [Challenges and Reflections](#9-challenges-and-reflections)

---

## 1. System Overview

### 1.1 Phase I — Foundation

Phase I delivered a static SmartBus frontend with a monolithic Spring Boot backend: a single process serving HTML, handling user authentication with in-memory session state, and providing basic bus route information via direct service calls. All logic was co-located: no separation of concerns between booking, scheduling, or payment responsibilities.

### 1.2 Phase II — Service-Oriented Architecture

Phase II decomposed the monolith into five independently deployable microservices, each owning one business capability:

| Service | Port | Responsibility |
|---------|------|----------------|
| **gateway** | 8080 | External entry point, JWT auth, routing |
| **booking-service** | 8081 | Booking lifecycle, BPMN orchestration |
| **schedule-service** | 8082 | Routes, timetables, seat availability |
| **payment-service** | 8083 | Payment authorization and records |
| **notification-service** | 8084 | Delivery records, Kafka event consumption |

Service boundaries are enforced: no service crosses another's database schema. Data sharing happens only through HTTP APIs or events.

The booking workflow was implemented on **Flowable BPMN**, making the multi-step orchestration (schedule validation → payment authorization → notification) explicit, durable, and observable. The BPMN process (`smartbusBookingProcess`) uses parallel gateways, service tasks, and Spring delegate expressions so every step is a managed Spring bean.

### 1.3 Phase III — Data Management and Integration Layer

Phase III extends the Phase II service mesh with:

| Concern | Implementation |
|---------|---------------|
| **Data access layer** | Spring Data JPA with full CRUD, pagination, and separated repository/service/controller layers |
| **Database integration** | PostgreSQL 17 (primary, all five services) + MongoDB 7 (optional audit sink in notification-service) |
| **REST API design** | Complete endpoint table, uniform error schema, `@Valid` Bean Validation on all DTOs |
| **Async processing** | Apache Kafka 3.9 with two producer/consumer scenarios; durable volume-backed storage |
| **Caching strategy** | Caffeine-backed Spring Cache (4 caches) + output cache; Actuator metrics exposure |
| **Security and validation** | JWT HS256 auth, BCrypt passwords, HtmlSanitizer XSS protection, parameterized queries |
| **Data transformation** | JSON↔XML content negotiation, JSON→JSON aggregation, JSON→CSV export |
| **Logging and monitoring** | MDC-based X-Request-Id correlation across all five services, structured log pattern, Actuator metrics |

### 1.4 Architecture Diagram

```
                          ┌─────────────────────────────────────┐
                          │            CLIENT (browser / API)   │
                          └───────────────────┬─────────────────┘
                                              │ HTTPS :8080
                          ┌───────────────────▼─────────────────┐
                          │              GATEWAY                 │
                          │  JWT auth · RequestIdFilter          │
                          │  DataTransformationController        │
                          │  FrontendGatewayController           │
                          └───┬───────────┬────────┬────────────┘
                              │           │        │
              REST :8081       │     :8082 │  :8083 │   :8084
         ┌────────────────┐   │  ┌────────┴──┐  ┌──┴──────────┐  ┌──────────────────┐
         │ booking-service│◄──┘  │ schedule- │  │  payment-   │  │ notification-    │
         │                │      │ service   │  │  service    │  │ service          │
         │ Flowable BPMN  │─────►│ Caffeine  │  │             │  │                  │
         │ BookingProcess │      │ Cache     │  │             │  │                  │
         └───────┬────────┘      └─────┬─────┘  └──────┬──────┘  └────────┬─────────┘
                 │                     │                │                  │
         ┌───────▼────────┐    ┌───────▼─────┐  ┌──────▼──────┐  ┌────────▼─────────┐
         │ smartbus_      │    │smartbus_    │  │smartbus_   │  │smartbus_        │
         │ booking (PG)   │    │schedule(PG) │  │payment (PG)│  │notification(PG) │
         └────────────────┘    └─────────────┘  └─────────────┘  └──────────────────┘
                                                                         │
                                                               ┌─────────▼──────────┐
                 ┌──────────── Kafka ──────────────────────────►  MongoDB (opt)      │
                 │  booking.confirmed.v1 ──────────────────────► smartbus_notifications│
                 │  payment.declined.v1  ◄────────────────────  └────────────────────┘
                 └─────────────────────────────────────────────
```

---

## 2. Technology Stack Justification

### 2.1 Java 25 + Spring Boot 4.0.3

**Chosen because:** Spring Boot 4 targets Jakarta EE 11 with Java 21+ LTS. Java 25 (current at project start) provides virtual threads (Project Loom), record patterns, and sealed classes. Spring Boot's auto-configuration reduces boilerplate — a bean validation failure turns into a `400 Bad Request` with zero custom code. The project already used Spring Boot in Phase I/II, so upgrading in-place had no migration cost.

**Alternatives considered:** Node.js (Express), Python (Django). Both were rejected because the team's existing code base, test suite, and BPMN integration were already in Java. Switching languages would have required rewriting 10,000+ lines of working code.

### 2.2 Flowable BPMN Engine

**Chosen because:** The booking workflow has five steps with parallel branches (payment authorization and notification preparation run concurrently), conditional exits, and durable state that must survive service restarts. Expressing this as procedural code would produce fragile nested try/catch blocks. Flowable makes the process structure inspectable (BPMN diagram), testable (per-delegate unit tests), and auditable (Flowable history tables track every token move).

### 2.3 PostgreSQL 17

**Chosen because:** ACID transactions are non-negotiable for payment records and booking state. PostgreSQL's `TIMESTAMPTZ` with time-zone awareness, partial indexes, and row-level locking are necessary for a multi-service deployment where concurrent booking requests may conflict on seat availability. Flyway manages schema migrations with `baseline-on-migrate`, so all five databases start from a known state on first boot.

**Database-per-service pattern:** Each microservice has its own PostgreSQL database (`smartbus_booking`, `smartbus_schedule`, `smartbus_payment`, `smartbus_notification`). This ensures loose coupling — no service can break another's schema through a DDL change.

### 2.4 Apache Kafka 3.9 (KRaft mode)

**Chosen because:** Kafka provides durable, partitioned event logs with configurable consumer groups and offset replay. The booking workflow needs to notify the notification service after a confirmed booking, and the booking service needs to audit declined payments. Both scenarios require at-least-once delivery guarantees with replay on consumer restart — exactly Kafka's model. RabbitMQ was considered but rejected because its queues are not replayable; once a message is ACKed it is gone, which would break the offline-consumer scenario.

### 2.5 Caffeine (in-memory cache)

**Chosen because:** `schedule-service` serves semi-static route and location data. Every booking creates a cache-miss round-trip to PostgreSQL (simulated at 60 ms). Caffeine provides a write-expiry TTL cache (5 minutes for route data, 30 seconds for quote output) with `recordStats()` for Micrometer instrumentation. It integrates with Spring Cache via a single `CaffeineCacheManager` bean with no additional infrastructure. Redis would be appropriate in a multi-node deployment; for a single-host proof of concept Caffeine is sufficient and avoids an extra infrastructure component.

### 2.6 MongoDB 7 (optional audit sink)

**Chosen because:** Notification delivery events are append-only audit records that benefit from a document model — each event is a self-describing JSON document, no schema migration required when adding fields. MongoDB is activated with `smartbus.mongodb.enabled=true`. The conditional approach (`@ConditionalOnProperty`) ensures the service starts without MongoDB in development/CI environments.

### 2.7 JJWT 0.12.7

**Chosen because:** JJWT is the de-facto standard JWT library for Java, actively maintained, and supports HS256 out of the box. The 8-hour token expiry and `X-Request-Id` propagation in the same filter chain give stateless, auditable sessions.

### 2.8 Jackson (JSON + XML)

**Chosen because:** Spring Boot auto-configures Jackson for JSON. Adding `jackson-dataformat-xml` to `schedule-service` enables content negotiation between `application/json` and `application/xml` on the same endpoint with zero controller changes — only `@JacksonXmlRootElement` annotations on DTO records.

---

## 3. Database Design

### 3.1 Database-per-Service Pattern

Each microservice owns its schema exclusively. No service reads another's tables. Data is shared only through HTTP APIs or Kafka events.

| Service | Database | Port | Technology |
|---------|----------|------|------------|
| gateway | `smartbus_booking` | 5433 | PostgreSQL 17 |
| booking-service | `smartbus_booking` | 5433 | PostgreSQL 17 |
| schedule-service | `smartbus_schedule` | 5433 | PostgreSQL 17 |
| payment-service | `smartbus_payment` | 5433 | PostgreSQL 17 |
| notification-service | `smartbus_notification` | 5433 | PostgreSQL 17 |
| notification-service | `smartbus_notifications` | 27017 | MongoDB 7 (opt) |

### 3.2 Schema Migrations

All services use **Flyway** for schema lifecycle management. Migration scripts live at `src/main/resources/db/migration/` with the naming convention `V{n}__{description}.sql`. `baseline-on-migrate: true` ensures safe operation against pre-existing databases.

### 3.3 Entity-Relationship Summary

#### Gateway (`smartbus_booking` — shared schema)

```
users
  id            BIGSERIAL PK
  full_name     VARCHAR(200)
  email         VARCHAR(200) UNIQUE NOT NULL
  password_hash VARCHAR(200) NOT NULL
  role          VARCHAR(32)  NOT NULL  DEFAULT 'USER'
  created_at    TIMESTAMPTZ  NOT NULL

contact_messages
  id            BIGSERIAL PK
  name          VARCHAR(200)
  subject       VARCHAR(200)
  message       TEXT
  created_at    TIMESTAMPTZ  NOT NULL

buses
  id            BIGSERIAL PK
  plate         VARCHAR(20)
  model         VARCHAR(100)
  capacity      INTEGER
  active        BOOLEAN DEFAULT true
```

#### Booking Service (`booking_process_instances`)

```
booking_process_instances
  booking_reference  VARCHAR(36) PK  (UUID format: BK-XXXXXXXX)
  customer_name      VARCHAR(200)
  customer_email     VARCHAR(200)
  from_stop          VARCHAR(200)
  to_stop            VARCHAR(200)
  trip_date          DATE
  trip_type          VARCHAR(32)      -- 'one-way' | 'round-trip'
  passengers         INTEGER
  route_code         VARCHAR(64)
  departure_time     VARCHAR(64)
  arrival_time       VARCHAR(64)
  total_amount       DOUBLE PRECISION
  payment_txn_id     VARCHAR(64)
  notification_id    VARCHAR(64)
  current_state      VARCHAR(32) NOT NULL
  last_error         TEXT
  created_at         TIMESTAMPTZ NOT NULL
  updated_at         TIMESTAMPTZ NOT NULL

Indexes:
  idx_booking_customer_email  (customer_email)    -- list by customer
  idx_booking_current_state   (current_state)     -- admin state filter
```

Lifecycle states: `INITIATED → SCHEDULE_CONFIRMED → PAYMENT_CONFIRMED → NOTIFICATION_SENT → COMPLETED`  
Failure states: `FAILED`, `CANCELLED`

#### Schedule Service

```
schedule_locations
  id          BIGINT PK (identity)
  name        VARCHAR(200) UNIQUE NOT NULL
  created_at  TIMESTAMPTZ NOT NULL

schedule_routes
  route_code        VARCHAR(64) PK
  from_location_id  BIGINT FK → schedule_locations(id)
  to_location_id    BIGINT FK → schedule_locations(id)
  departure_time    VARCHAR(64) NOT NULL
  arrival_time      VARCHAR(64) NOT NULL
  unit_price        DOUBLE PRECISION NOT NULL
  seats_available   INTEGER NOT NULL
  created_at        TIMESTAMPTZ NOT NULL
  updated_at        TIMESTAMPTZ NOT NULL

Indexes:
  idx_schedule_routes_from_location  (from_location_id)
  idx_schedule_routes_to_location    (to_location_id)
```

Seed data: 6 locations, 3 routes (SB-101 Downtown→Airport, SB-202 Airport→University, SB-303 University→Downtown).

#### Payment Service

```
payment_records
  transaction_id      VARCHAR(64) PK
  booking_reference   VARCHAR(36) NOT NULL
  amount              DOUBLE PRECISION NOT NULL
  currency            VARCHAR(8) NOT NULL DEFAULT 'USD'
  status              VARCHAR(32) NOT NULL  -- AUTHORIZED | DECLINED | REFUNDED
  created_at          TIMESTAMPTZ NOT NULL

Indexes:
  idx_payment_booking_reference  (booking_reference)
  idx_payment_status             (status)
  idx_payment_created_at         (created_at DESC)
```

#### Notification Service

```
notification_deliveries
  id                BIGSERIAL PK
  booking_reference VARCHAR(36) NOT NULL
  recipient         VARCHAR(200) NOT NULL
  route_code        VARCHAR(64)
  status            VARCHAR(32) NOT NULL
  delivered_at      TIMESTAMPTZ NOT NULL DEFAULT now()

Indexes:
  idx_notification_booking_ref   (booking_reference)
  idx_notification_delivered_at  (delivered_at DESC)
```

#### MongoDB (optional)

```
Collection: notification_events
  _id               ObjectId (auto)
  bookingReference  String  (indexed)
  recipient         String
  routeCode         String
  status            String
  deliveredAt       ISODate
```

Activated via `smartbus.mongodb.enabled=true`. When disabled, no MongoDB connection is established — the `@ConditionalOnProperty` guard prevents any Spring Data MongoDB beans from loading.

### 3.4 Index Strategy

| Index | Rationale |
|-------|-----------|
| `idx_booking_customer_email` | Supports `GET /api/v1/bookings?customerEmail=` without a full table scan |
| `idx_booking_current_state` | Admin list filters by state (`FAILED`, `COMPLETED`) |
| `idx_payment_booking_reference` | Joins from booking reference to transaction ID |
| `idx_payment_created_at DESC` | Chronological payment audit queries |
| `idx_notification_delivered_at DESC` | Chronological delivery queries |
| `schedule_locations.name UNIQUE` | Enforces no duplicate stop names; implicitly indexed |

### 3.5 Connection Pool Configuration

| Service | max-pool-size | min-idle | Rationale |
|---------|--------------|---------|-----------|
| booking-service | 10 | 2 | Higher concurrency — Flowable BPMN holds connections during multi-step orchestration |
| schedule-service | 10 | 2 | Cache misses under load can generate burst reads |
| payment-service | 5 | 1 | Lower peak; each authorization is a single short-lived transaction |
| notification-service | 5 | 1 | Kafka consumer is single-threaded; low connection demand |

---

## 4. API Specifications

### 4.1 Design Conventions

- All paths use `kebab-case` plural nouns under `/api/v1/`
- Admin operations are scoped under `/admin/` to separate operational from user-facing reads
- Single-resource operations use path variables; collection filters use query parameters
- Every non-2xx response returns the same `ApiErrorResponse` JSON structure

**Uniform error schema:**
```json
{
  "status": 404,
  "code": "BOOKING_NOT_FOUND",
  "message": "Booking not found: BK-1234ABCD",
  "details": [],
  "path": "/api/v1/bookings/BK-1234ABCD"
}
```

### 4.2 HTTP Status Code Policy

| Code | Meaning | When Used |
|------|---------|-----------|
| `200` | OK | Successful GET, PUT, PATCH |
| `201` | Created | Successful POST — always includes `Location` header |
| `204` | No Content | Successful DELETE |
| `400` | Bad Request | Bean Validation failure |
| `401` | Unauthorized | Missing or invalid JWT token |
| `404` | Not Found | Resource not found |
| `409` | Conflict | Duplicate key or name collision |
| `422` | Unprocessable Entity | Business rule rejection |
| `500` | Internal Server Error | Unexpected exception (full stack in server logs only) |
| `503` | Service Unavailable | Downstream unreachable after retries |
| `504` | Gateway Timeout | Downstream exceeded timeout |

### 4.3 Booking Service — `/api/v1/bookings`

| Method | Path | Description | Response |
|--------|------|-------------|----------|
| `POST` | `/orchestrated-bookings` | Create booking via BPMN flow | `201 + Location` |
| `GET` | `/{bookingReference}` | Get full booking details | `200` |
| `GET` | `/{bookingReference}/state` | Get workflow state | `200` |
| `DELETE` | `/{bookingReference}` | Cancel booking | `204` |
| `GET` | `?customerEmail=` | List bookings for customer | `200` |
| `GET` | `/admin/bookings?page=0&size=20` | Paginated admin list | `200` |
| `GET` | `/admin/bookings.csv` | Full export as CSV | `200 text/csv` |

**Create Booking request:**
```json
{
  "customerName": "Jane Rider",
  "customerEmail": "jane@example.com",
  "fromStop": "Downtown Terminal",
  "toStop": "Airport Station",
  "tripDate": "2026-04-20",
  "tripType": "one-way",
  "passengers": 1,
  "paymentMethodToken": "tok_visa_4242"
}
```

**Create Booking response `201`:**
```json
{
  "bookingReference": "BK-1234ABCD",
  "status": "COMPLETED",
  "routeCode": "SB-101",
  "paymentTransactionId": "PAY-89AB12CD",
  "notificationId": "42",
  "totalAmount": 12.5
}
```

**Paginated admin list:**
```json
{
  "items": [ ... ],
  "page": 0,
  "size": 20,
  "totalElements": 57,
  "totalPages": 3
}
```

### 4.4 Schedule Service — `/api/v1/schedules`

| Method | Path | Description | Response | Format |
|--------|------|-------------|----------|--------|
| `GET` | `/catalog` | Full route catalog | `200` | JSON or XML |
| `POST` | `/quote` | Trip availability quote | `200` | JSON or XML |
| `POST` | `/admin/routes` | Create route | `201` | JSON |
| `GET` | `/admin/routes/{routeCode}` | Get single route | `200` | JSON |
| `PUT` | `/admin/routes/{routeCode}` | Replace route | `200` | JSON |
| `DELETE` | `/admin/routes/{routeCode}` | Delete route | `204` | — |
| `POST` | `/admin/routes/{routeCode}/fare` | Update fare + evict cache | `200` | JSON |
| `POST` | `/admin/locations` | Create location | `201` | JSON |
| `GET` | `/admin/locations` | List all locations | `200` | JSON |
| `GET` | `/admin/locations/{id}` | Get single location | `200` | JSON |
| `PUT` | `/admin/locations/{id}` | Rename location | `200` | JSON |
| `DELETE` | `/admin/locations/{id}` | Delete location | `204` | — |

### 4.5 Payment Service — `/api/v1/payments`

| Method | Path | Description | Response |
|--------|------|-------------|----------|
| `POST` | `/authorize` | Authorize payment | `201` |
| `GET` | `/records` | List records (filter optional) | `200` |
| `GET` | `/records/{transactionId}` | Get single record | `200` |
| `PATCH` | `/records/{transactionId}` | Update status (e.g., REFUND) | `200` |
| `DELETE` | `/records/{transactionId}` | Delete record | `204` |

### 4.6 Notification Service — `/api/v1/notifications`

| Method | Path | Description | Response |
|--------|------|-------------|----------|
| `POST` | `/prepare` | Prepare notification | `200` |
| `POST` | `/dispatch` | Dispatch notification | `200` |
| `POST` | `/deliveries` | Create delivery record | `201` |
| `GET` | `/deliveries` | List deliveries (filter optional) | `200` |
| `GET` | `/deliveries/{id}` | Get single delivery | `200` |
| `PUT` | `/deliveries/{id}` | Update delivery record | `200` |
| `DELETE` | `/deliveries/{id}` | Delete delivery record | `204` |

### 4.7 Gateway Aggregation — `/api/v1/gateway`

| Method | Path | Description | Auth |
|--------|------|-------------|------|
| `GET` | `/booking-summary/{bookingReference}` | Aggregated booking+payment view | Bearer |

### 4.8 Gateway Frontend — `/api/v1/frontend`

| Method | Path | Auth |
|--------|------|------|
| `GET` | `/routes` | No |
| `POST` | `/quote` | No |
| `POST` | `/contact` | No |
| `GET/POST` | `/bookings/**` | Yes (USER/ADMIN) |
| `GET/PUT` | `/profile/**` | Yes (USER/ADMIN) |
| `GET/POST` | `/admin/**` | Yes (ADMIN only) |

---

## 5. Data Flow Diagrams

### 5.1 Booking Creation Flow

This is the primary integration scenario. A single `POST /api/v1/frontend/bookings` request touches all five services.

```
Client
  │
  │ POST /api/v1/frontend/bookings
  │ Authorization: Bearer <jwt>
  ▼
Gateway (:8080)
  │ 1. JwtAuthenticationFilter validates token
  │ 2. RequestIdFilter assigns X-Request-Id (UUID)
  │ 3. FrontendGatewayController.createBooking()
  │ 4. Proxies to booking-service with X-Request-Id header
  │
  │ POST /api/v1/bookings/orchestrated-bookings
  ▼
booking-service (:8081)
  │ 5.  RequestIdFilter reads X-Request-Id → MDC
  │ 6.  BookingOrchestrationController receives request
  │ 7.  HtmlSanitizer.strip(customerName)
  │ 8.  Flowable starts smartbusBookingProcess
  │
  │──► 9.  InitializeBookingDelegate → DB: INSERT booking_process_instances (INITIATED)
  │
  │──► 10. ValidateScheduleDelegate
  │         POST /api/v1/schedules/quote  ──────────────────────────────────────►  schedule-service
  │                                                                                 │ Cache check
  │                                                                                 │ If miss: DB query
  │         ◄──────────────────────────────────────── ScheduleQuoteResponse ────────┘
  │
  │──► 11. Parallel Gateway (splits)
  │    │
  │    ├──► 12a. AuthorizePaymentDelegate
  │    │         POST /api/v1/payments/authorize  ──────────────────────────────► payment-service
  │    │                                                                           │ DB: INSERT payment_records
  │    │         ◄──────────────────────────── PaymentAuthorizationResponse ───────┘
  │    │
  │    └──► 12b. PrepareNotificationDelegate
  │              POST /api/v1/notifications/prepare  ──────────────────────────► notification-service
  │              ◄───────────────────────────────── NotificationResponse ─────────┘
  │
  │──► 13. Parallel Gateway (joins)
  │
  │──► 14. CheckPaymentDecisionDelegate → AUTHORIZED → continue; DECLINED → FAILED
  │
  │──► 15. DispatchNotificationDelegate
  │         POST /api/v1/notifications/dispatch ───────────────────────────────► notification-service
  │         ◄───────────────────────────────────────────────────────────────────┘
  │
  │──► 16. FinalizeBookingDelegate
  │         DB: UPDATE booking_process_instances (COMPLETED)
  │         Kafka: publish BookingConfirmedEvent → smartbus.booking.confirmed.v1
  │                                                           │
  │                                                           ▼
  │                                                  notification-service
  │                                                  BookingNotificationConsumer
  │                                                  DB: INSERT notification_deliveries
  │
  ◄── 17. BookingOrchestrationController returns 201 + booking reference
  ◄── 18. Gateway proxies response to client
```

### 5.2 Fare Update with Cache Invalidation

This flow demonstrates how an admin fare change propagates through the caching layer to ensure all subsequent quote requests reflect the new price.

```
Admin Client
  │
  │ POST /api/v1/frontend/admin/routes/SB-101/fare
  │ Authorization: Bearer <admin-jwt>
  │ { "unitPrice": 15.00 }
  ▼
Gateway (:8080)
  │ JWT filter verifies ROLE_ADMIN
  │ Proxies to schedule-service
  │
  │ POST /api/v1/schedules/admin/routes/SB-101/fare
  ▼
schedule-service (:8082)
  │
  │ 1. FareUpdateRequest validated (@DecimalMin("0.01"))
  │
  │ 2. ScheduleCatalogService.refreshFare(routeCode, 15.00)
  │    @CacheEvict({"routeCatalog", "routeDefinition"}, allEntries=true)
  │    → Caffeine evicts all entries in both caches
  │
  │ 3. DB: UPDATE schedule_routes SET unit_price = 15.00 WHERE route_code = 'SB-101'
  │
  │ 4. cachedQuoteResponseService.invalidateAll()
  │    → ConcurrentHashMap cleared (output cache)
  │
  └──► 200 OK { routeCode: "SB-101", unitPrice: 15.00, ... }

Next request: GET /api/v1/schedules/catalog
  │
  ▼
schedule-service
  │ Cache: routeCatalog MISS (was evicted)
  │ DB query: ~65 ms
  │ Cache: routeCatalog POPULATED with new price
  └──► 200 OK { routes: [ { routeCode: "SB-101", unitPrice: 15.00, ... } ] }

Subsequent requests within 5 min TTL:
  │ Cache: routeCatalog HIT
  └──► 200 OK (< 1 ms)
```

---

## 6. Caching and Performance Analysis

### 6.1 Cache Architecture

SmartBus implements two cache layers in `schedule-service`:

**Layer 1 — Data-Level Cache (Caffeine + Spring Cache)**

| Cache Name | Method | Key | TTL |
|------------|--------|-----|-----|
| `routeCatalog` | `ScheduleCatalogService.catalog()` | implicit | 5 min |
| `routeDefinition` | `ScheduleCatalogService.routeDefinition(from, to)` | `from→to` | 5 min |
| `locationCatalog` | `ScheduleCatalogService.locations()` | implicit | 5 min |
| `locationById` | `ScheduleCatalogService.requireLocation(id)` | `#id` | 5 min |

Configuration: `CaffeineCacheManager` with `maximumSize(200)` and `recordStats()` enabled.

**Layer 2 — Output Cache (`CachedQuoteResponseService`)**

| Property | Value |
|----------|-------|
| Technology | `ConcurrentHashMap` with TTL check |
| Cached item | Full `POST /api/v1/schedules/quote` response |
| TTL | 30 seconds (configurable via `SCHEDULE_OUTPUT_CACHE_TTL`) |
| Cache key | `fromStop|toStop|tripDate|tripType|passengers` |

### 6.2 Invalidation Policy

| Operation | Evicted caches |
|-----------|---------------|
| `POST /admin/routes` | `routeCatalog`, `routeDefinition`, output cache |
| `PUT /admin/routes/{code}` | `routeCatalog`, `routeDefinition`, output cache |
| `DELETE /admin/routes/{code}` | `routeCatalog`, `routeDefinition`, output cache |
| `POST /admin/routes/{code}/fare` | `routeCatalog`, `routeDefinition`, output cache |
| `POST /admin/locations` | `locationCatalog`, `locationById` |
| `PUT /admin/locations/{id}` | `locationCatalog`, `locationById` |
| `DELETE /admin/locations/{id}` | `locationCatalog`, `locationById` |

### 6.3 Performance Benchmark Results

Measurements taken from `ScheduleCachingTests` (JUnit 5, Java 25, Apple Silicon). Simulated datastore latency: 60 ms per call (`smartbus.cache.simulated-latency=PT0.06S`).

| Scenario | Cold (ms) | Warm (ms) | Improvement |
|----------|----------:|----------:|-------------|
| Route catalog read | 67 | 0 | >99% |
| Quote output (repeated request) | 65 | 0 | >99% |
| Location catalog read | 65 | 0 | >99% |
| Location catalog after create | 64 (evicted) | — | Invalidation confirmed |

### 6.4 Actuator Metrics Exposure

Caffeine statistics are exposed at runtime via Spring Boot Actuator:

```
GET http://localhost:8082/actuator/metrics/cache.gets?tag=name:routeCatalog&tag=result:hit
GET http://localhost:8082/actuator/metrics/cache.gets?tag=name:routeCatalog&tag=result:miss
GET http://localhost:8082/actuator/caches
```

Sample real metrics after a booking flow:
```
cache.gets{name=routeCatalog, result=hit}  → 6
cache.gets{name=routeCatalog, result=miss} → 1
```

### 6.5 Trade-offs

- **Stale data risk:** Route data cached for 5 minutes means a fare update is invisible to quote callers for up to 5 minutes — unless `@CacheEvict` fires (which it does on every admin mutation).
- **Memory:** 200-entry cap prevents unbounded growth under adversarial request patterns.
- **Single-node:** Caffeine is local to the process. In a multi-replica deployment, Redis would be required for cross-node cache coherence.
- **Short output TTL:** The 30-second quote output TTL bounds stale-fare risk to a narrow window while still eliminating duplicate database round-trips for burst search traffic.

---

## 7. Security Implementation

### 7.1 Authentication Flow

JWT-based stateless authentication. All auth logic is in the `gateway` service; microservices are on internal ports.

```
Client
  │
  ├─► POST /api/v1/auth/register { fullName, email, password }
  │     Gateway: @Valid → HtmlSanitizer.strip(fullName) → BCrypt(password) → DB INSERT
  │     Response: 201 { token, fullName, email, role: "USER" }
  │
  ├─► POST /api/v1/auth/login { email, password }
  │     Gateway: load user → BCrypt.matches() → issue JWT
  │     Response: 200 { token, fullName, email, role }
  │
  └─► GET/POST <protected endpoint>
        Header: Authorization: Bearer <token>
        JwtAuthenticationFilter:
          → Extract Bearer token
          → JJWT 0.12.7 verifies HS256 signature
          → Verifies expiry from `exp` claim
          → Populates SecurityContextHolder (email + ROLE_<role>)
          → OR 401 { message: "Invalid or expired authentication token." }
```

**JWT Token Claims:**

| Claim | Value |
|-------|-------|
| `sub` | User email address |
| `role` | `USER` or `ADMIN` |
| `name` | User full name |
| `iat` | Issued-at timestamp |
| `exp` | Issued-at + 8 hours |

Algorithm: **HS256**. Secret configured via `SMARTBUS_JWT_SECRET` environment variable.

### 7.2 Protected Endpoints

| Path Pattern | Auth | Roles |
|-------------|------|-------|
| `POST /api/v1/auth/login` | No | — |
| `POST /api/v1/auth/register` | No | — |
| `GET /api/v1/system/services` | No | — |
| `GET /api/v1/frontend/routes` | No | — |
| `POST /api/v1/frontend/quote` | No | — |
| `POST /api/v1/frontend/contact` | No | — |
| `GET /actuator/health` | No | — |
| `GET/POST /api/v1/frontend/bookings/**` | **Yes** | USER, ADMIN |
| `GET/PUT /api/v1/frontend/profile/**` | **Yes** | USER, ADMIN |
| `GET/POST /api/v1/frontend/admin/**` | **Yes** | ADMIN only |

Microservices (ports 8081–8084) are not exposed externally. In the Docker Compose network, they are only reachable from the gateway or internal tooling.

### 7.3 Input Validation

All request DTOs are annotated with `jakarta.validation` constraints and controllers use `@Valid`. A `MethodArgumentNotValidException` handler in each service's `@RestControllerAdvice` returns `400 Bad Request` with field-level error details.

**BookingRequest (`booking-service`):**

| Field | Constraint |
|-------|-----------|
| `customerName` | `@NotBlank` |
| `customerEmail` | `@NotBlank @Email` |
| `fromStop` / `toStop` | `@NotBlank` |
| `tripDate` | `@NotBlank @Pattern(^\d{4}-\d{2}-\d{2}$)` |
| `tripType` | `@Pattern(^(one-way\|round-trip)$)` |
| `passengers` | `@Min(1) @Max(6)` |
| `paymentMethodToken` | `@NotBlank` |

### 7.4 XSS Prevention

Free-text fields are sanitized at the storage boundary by stripping all `<...>` HTML tag sequences before persistence. This protects against stored XSS where malicious markup is returned in future responses.

**Implementation:** `HtmlSanitizer.strip(input)` — regex `<[^>]*>` replaced with `""`, input trimmed.

**Sanitization points:**

| Service | Where | Fields |
|---------|-------|--------|
| gateway | `AuthService.register()` | `fullName` |
| gateway | `FrontendGatewayRepository.saveContactMessage()` | `name`, `subject`, `message` |
| gateway | `FrontendGatewayRepository.updateProfile()` | `fullName`, `address` |
| schedule-service | `ScheduleCatalogService.createLocation()` | `name` |
| schedule-service | `ScheduleCatalogService.updateLocation()` | `name` |
| booking-service | `BookingOrchestrationService.orchestrateBooking()` | `customerName` |

Fields already constrained by `@Email`, `@Pattern`, or `@Min`/`@Max` are structurally incapable of containing HTML and are not sanitized.

### 7.5 SQL Injection Prevention

All five services use Spring Data JPA with parameterized queries (`@Query` named parameters, derived queries, or `@Modifying @Query` with `:param` syntax). No service uses string concatenation in any query path. The ORM always produces prepared statements.

### 7.6 Password Security

- Passwords hashed with **BCrypt** (Spring Security `BCryptPasswordEncoder`, strength 10) before storage.
- The `password_hash` column is never returned in any API response.
- Plain-text passwords are never logged — log statements record only the email address.

### 7.7 Sensitive Data Policy

The following fields are **never logged**:

| Field | Appears in | Reason |
|-------|-----------|--------|
| `paymentMethodToken` | `BookingRequest` | Raw payment token, PCI scope |
| `password` / `newPassword` | `RegisterRequest` | Credential |
| `password_hash` | DB entity | Stored credential |

---

## 8. Asynchronous Processing Design

### 8.1 Overview

SmartBus uses Apache Kafka 3.9 (KRaft mode, single broker) for durable event delivery. Kafka data is persisted to a named Docker volume (`kafka-data`), surviving container restarts. Topics are auto-created with 3 partitions. Consumers use `auto.offset.reset = earliest` for backlog replay after downtime.

### 8.2 Scenario 1 — Booking Confirmed

```
booking-service ──publish──► smartbus.booking.confirmed.v1 ──consume──► notification-service
```

| Role | Service | Class |
|------|---------|-------|
| Producer | booking-service | `BookingConfirmedEventProducer` |
| Consumer | notification-service | `BookingNotificationConsumer` |

**Trigger:** The Flowable `FinalizeBookingDelegate` calls `BookingConfirmedEventProducer.publish()` after all workflow steps succeed.

**Event contract** (`contracts/messages/booking-confirmed.v1.json`):
```json
{
  "schemaVersion": "booking-confirmed.v1",
  "bookingReference": "BK-AB12CD34",
  "customerEmail": "alice@example.com",
  "routeCode": "SB-101"
}
```

**Consumer behavior:** `BookingNotificationConsumer` listens on group `notification-service-booking-events`. On receipt it:
1. Sets `bookingReference` in MDC via `MDC.putCloseable()`
2. Calls `NotificationDeliveryService.save()` — persists to PostgreSQL
3. Optionally writes to MongoDB if enabled
4. Emits INFO log: `bookingEventConsumed bookingReference=... customerEmail=... routeCode=... status=CONFIRMED`

### 8.3 Scenario 2 — Payment Declined

```
payment-service ──publish──► smartbus.payment.declined.v1 ──consume──► booking-service
```

| Role | Service | Class |
|------|---------|-------|
| Producer | payment-service | `PaymentDeclinedEventProducer` |
| Consumer | booking-service | `PaymentDeclinedEventConsumer` |

**Trigger:** `PaymentService.authorize()` rejects requests with `amount > 150.00` or blank token. On rejection it publishes a `PaymentDeclinedEvent`.

**Event contract** (`contracts/messages/payment-declined.v1.json`):
```json
{
  "schemaVersion": "payment-declined.v1",
  "transactionId": "PAY-DECLINED",
  "bookingReference": "BK-XYZ",
  "customerEmail": "bob@example.com",
  "amount": 200.0,
  "reason": "Payment rule rejected the request"
}
```

**Consumer behavior:** `PaymentDeclinedEventConsumer` listens on group `booking-service-payment-audit`. On receipt it emits a WARN-level audit log with all event fields.

The consumer uses a dedicated `paymentDeclinedListenerContainerFactory` bean with its own `ConsumerFactory` to avoid interfering with any default Kafka configuration.

### 8.4 Persistence Guarantee

Kafka data is stored in a named Docker volume:

```yaml
kafka:
  volumes:
    - kafka-data:/var/lib/kafka/data
  environment:
    KAFKA_LOG_DIRS: /var/lib/kafka/data
```

Named volumes survive `docker compose down`. Only `docker compose down -v` removes them.

### 8.5 Offline Recovery Demo

**Scenario:** `booking-service` (the consumer for `payment.declined`) is stopped while payment-service publishes an event.

1. Stop booking-service: `docker compose stop booking-service`
2. POST to payment-service with `amount > 150` → event written to Kafka broker
3. Verify event in broker: `kafka-console-consumer.sh --from-beginning --max-messages 1`
4. Restart booking-service: `docker compose start booking-service`
5. Within seconds, booking-service logs show the replayed event

This confirms at-least-once delivery with `auto.offset.reset = earliest` backlog replay.

### 8.6 Kafka Consumer Metrics

Accessible via Actuator on notification-service (port 8084):

```bash
curl http://localhost:8084/actuator/metrics/kafka.consumer.records-lag
curl http://localhost:8084/actuator/metrics/kafka.consumer.fetch-latency-avg
```

| Metric | Description |
|--------|-------------|
| `kafka.consumer.records-lag` | Per-partition lag (zero = consumer is caught up) |
| `kafka.consumer.fetch-latency-avg` | Average broker fetch latency |
| `kafka.consumer.commit-rate` | Offset commits per second |

---

## 9. Challenges and Reflections

### 9.1 Challenge: `RestClientCustomizer` Does Not Exist in Spring Boot 4.0.3

**Problem:** When implementing X-Request-Id propagation for outgoing HTTP calls in `booking-service`, the initial approach was to use `RestClientCustomizer` from `org.springframework.boot.web.client` — the expected auto-configuration hook for customizing the shared `RestClient.Builder`. The import failed with "package does not exist" because the interface was not present at that path in Spring Boot 4.0.3.

**Investigation:** Reading the Spring Boot 4.x auto-configuration source revealed that `RestClient.Builder` is registered as a prototype-scoped bean. Overriding it requires defining a `@Bean RestClient.Builder restClientBuilder()` method, which replaces the auto-configured one.

**Resolution:** Created `RestClientConfiguration.java` in `booking-service` with a `@Bean RestClient.Builder` that attaches a `ClientHttpRequestInterceptor`. The interceptor reads `requestId` from MDC and adds `X-Request-Id` to all outgoing headers. This pattern is identical to the gateway's `HttpClientConfiguration`, confirming that it is the correct Spring Boot 4 approach.

**Lesson:** Spring Boot auto-configuration contracts are not always stable across minor versions. Always verify the actual package structure in the target Spring Boot version rather than relying on documentation for older releases.

---

### 9.2 Challenge: MDC Propagation Across Kafka Consumer Threads

**Problem:** Kafka consumer callbacks run on a dedicated listener thread managed by Spring Kafka, not on the original HTTP request thread where the `RequestIdFilter` populates MDC. As a result, Kafka consumer log lines showed `[--]` for both `requestId` and `bookingReference` — the MDC context was absent.

**Investigation:** MDC in SLF4J is thread-local. When the Kafka listener thread executes `@KafkaListener`, it has an empty MDC regardless of what was set on the producer's thread.

**Resolution:** Both Kafka consumers (`BookingNotificationConsumer` and `PaymentDeclinedEventConsumer`) now use `MDC.putCloseable("bookingReference", event.bookingReference())` inside a try-with-resources block at the start of the handler method. This ensures:
- Every log line in the Kafka callback includes the booking reference
- The MDC key is cleaned up automatically when the try block exits (even on exception)

**Lesson:** MDC propagation is not automatic across thread boundaries — whether thread pools, `CompletableFuture`, or message consumer threads. Every async entry point needs explicit MDC initialization.

---

### 9.3 Challenge: Caching Methods That Throw Exceptions

**Problem:** `ScheduleCatalogService.requireLocation(id)` throws `ResponseStatusException(404)` when a location is not found. When adding `@Cacheable("locationById")`, there was uncertainty about whether Spring Cache would cache the exception itself, potentially returning a "not found" error for a subsequently valid ID.

**Investigation:** The Spring Cache documentation confirms that exceptions propagate normally from `@Cacheable` methods — the exception is not cached, and the cache is not populated. Cache storage only occurs when the method returns normally.

**Resolution:** `@Cacheable(cacheNames = "locationById", key = "#id")` was applied to `requireLocation(id)` without any changes to the exception-throwing behavior. Tests confirmed that a 404 for an unknown ID does not poison the cache for other IDs, and a subsequent successful lookup for a valid ID is cached correctly.

**Lesson:** Spring's `@Cacheable` is exception-transparent. It is safe to annotate methods that throw exceptions — no defensive wrapping or `unless` conditions are needed for the not-found case.

---

### 9.4 Challenge: Sanitizing Immutable Java Records

**Problem:** `BookingRequest` is a Java record — all fields are final and set at construction time. When XSS sanitization was added to `BookingOrchestrationService`, the natural approach of `request.customerName = HtmlSanitizer.strip(...)` is a compile error because records have no setters.

**Investigation:** Java records are purposely immutable. The only way to change a record's value is to construct a new instance. For a record with eight fields, this requires a full constructor call with all eight values.

**Resolution:** At the start of `orchestrateBooking()`, a sanitized copy of the record is constructed:

```java
BookingRequest sanitized = new BookingRequest(
    HtmlSanitizer.strip(request.customerName()),
    request.customerEmail(),
    request.fromStop(), request.toStop(), request.tripDate(),
    request.tripType(), request.passengers(), request.paymentMethodToken()
);
```

All subsequent logic uses `sanitized` instead of `request`. This preserves immutability while ensuring the free-text field is safe before reaching the storage layer.

**Lesson:** When applying cross-cutting concerns (sanitization, normalization) to immutable value objects, the correct pattern is to transform at the boundary and pass the transformed value through the rest of the call chain — not to reach into the object mid-flight.

---

### 9.5 Challenge: Duplicate `spring.datasource` Key in `application.yml`

**Problem:** The `notification-service` and `payment-service` `application.yml` files each had `spring.datasource` defined twice — once at the top (with `url`, `username`, `password`) and once lower in the file (with `hikari.*` pool settings). Spring Boot merges YAML keys, but a duplicate top-level key block overwrites earlier values.

**Investigation:** Running the service and checking the `HikariPool` configuration log showed that `maximum-pool-size` was being picked up, but earlier in the file the second `spring.datasource` block was silently overwriting the first `url` value with undefined/null.

**Resolution:** Merged the two `spring.datasource` blocks into one:

```yaml
spring:
  datasource:
    url: ${NOTIFICATION_DB_URL:...}
    username: ${NOTIFICATION_DB_USERNAME:...}
    password: ${NOTIFICATION_DB_PASSWORD:...}
    hikari:
      maximum-pool-size: 5
      minimum-idle: 1
```

**Lesson:** YAML does not merge repeated top-level keys within the same document — the second occurrence wins. Duplication in `application.yml` is a silent correctness bug, not a parse error. All YAML configuration files should be linted or reviewed for duplicate key paths.

---

## Appendix A — Project Structure

```
project/
├── backend/
│   ├── gateway/                    # Port 8080 — JWT, routing, aggregation
│   ├── services/
│   │   ├── booking-service/        # Port 8081 — BPMN orchestration
│   │   ├── schedule-service/       # Port 8082 — routes, caching
│   │   ├── payment-service/        # Port 8083 — authorization, Kafka producer
│   │   └── notification-service/   # Port 8084 — Kafka consumer, delivery records
├── contracts/
│   └── messages/
│       ├── booking-confirmed.v1.json
│       └── payment-declined.v1.json
├── infra/
│   └── docker-compose.yml          # PostgreSQL 17, Kafka 3.9, MongoDB 7
├── orchestration/
│   └── booking-workflow.yaml
└── docs/
    ├── api-design.md
    ├── async-processing.md
    ├── cache-strategy.md
    ├── cache-performance.md
    ├── data-transformation.md
    ├── database-design.md
    ├── logging-monitoring.md
    ├── orchestration.md
    ├── security-design.md
    └── report/
        ├── SmartBus-Phase3-Architecture.md   ← this document
        └── SmartBus-Phase3-Architecture.pdf
```

## Appendix B — Running the System

```bash
# 1. Start infrastructure
cd infra
docker compose up -d

# 2. Start services (each in a separate terminal or as background processes)
cd backend/gateway      && mvn spring-boot:run
cd backend/services/booking-service      && mvn spring-boot:run
cd backend/services/schedule-service     && mvn spring-boot:run
cd backend/services/payment-service      && mvn spring-boot:run
cd backend/services/notification-service && mvn spring-boot:run

# 3. Run test suite
cd backend/services/booking-service && mvn test
cd backend/services/schedule-service && mvn test

# 4. Health check all services
curl http://localhost:8080/actuator/health
curl http://localhost:8081/actuator/health
curl http://localhost:8082/actuator/health
curl http://localhost:8083/actuator/health
curl http://localhost:8084/actuator/health
```

## Appendix C — Key Metrics Endpoints

| Endpoint | Service | Purpose |
|----------|---------|---------|
| `GET /actuator/health` | all (8080–8084) | Liveness / readiness |
| `GET /actuator/info` | all | App name and phase |
| `GET /actuator/metrics` | all | List registered Micrometer metrics |
| `GET /actuator/caches` | schedule-service (8082) | Caffeine cache names |
| `GET /actuator/metrics/cache.gets` | schedule-service | Hit/miss counts |
| `GET /actuator/metrics/kafka.consumer.records-lag` | notification-service (8084) | Consumer lag |
