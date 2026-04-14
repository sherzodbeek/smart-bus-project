# SmartBus Phase III Gap Analysis

Audit date: 2026-04-12

This document evaluates each Phase III requirement against the current Phase II codebase and
records the implementation status, what exists, what is missing, and the planned approach for
each gap.

---

## 1. Data Access Layer

**Status: PARTIAL**

### What exists
- Gateway, booking-service, and schedule-service all follow a clean three-tier architecture:
  controller → service → repository (JPA).
- `BookingProcessEntity` is a full JPA entity with `@Entity`, `@Table`, audit columns
  (`created_at`, `updated_at`), and lifecycle callbacks (`@PrePersist`, `@PreUpdate`).
- `BookingProcessEntityRepository` (Spring Data JPA) is injected into the service layer,
  never directly into controllers.
- Schedule-service has `ScheduleRouteEntity`, `ScheduleLocationEntity`, JPA repositories,
  and a service class that owns all business logic.
- Bean Validation is present on request DTOs (`BookingRequest`, `PaymentAuthorizationRequest`,
  `ScheduleQuoteRequest`, etc.) using `@NotBlank`, `@Email`, `@Min`, `@Max`, `@Pattern`.

### What is missing
- **Payment-service** has no persistence layer. `PaymentAuthorizationController` and
  `PaymentAuthorizationController` return simulated in-memory results. No entity, no
  repository, no schema.sql.
- **Notification-service** uses `InMemoryNotificationDeliveryStore` (a plain
  `CopyOnWriteArrayList`) rather than a JPA repository. Delivery records are lost on restart.
- No **paginated list** endpoints exist on any service. Phase III requires at least one
  paginated collection read (`page` + `size` parameters).
- No **PUT/PATCH** or **DELETE** endpoints exist on any service for their primary resources.

### Plan for Task 01
- Add a `payment_records` table and JPA entity to payment-service. Persist every authorization
  result with its transaction ID, status, and amount. Add `GET /payments/{transactionId}` and
  `GET /payments?page=0&size=20` endpoints.
- Migrate `InMemoryNotificationDeliveryStore` to a JPA-backed store in notification-service.
  Add `GET /notifications/deliveries?page=0&size=20`.
- Add `DELETE /api/v1/bookings/{bookingReference}` (soft-delete via state change) to
  booking-service so the resource lifecycle is complete.

---

## 2. Database Integration

**Status: PARTIAL**

### What exists
- PostgreSQL 17 runs via Docker Compose with a persistent named volume (`postgres-data`).
- Each service targets its own database (`smartbus_booking`, `smartbus_schedule`,
  `smartbus_payment`, `smartbus_notification`).
- `booking-service` and `schedule-service` have `schema.sql` bootstrap files under
  `src/main/resources/`. The gateway also has a `schema.sql`.
- `BookingProcessEntity` has `created_at`/`updated_at` audit columns.
- `spring.sql.init.mode: always` is set so schemas apply on startup.

### What is missing
- **No Flyway or Liquibase migrations.** Schema files are plain `schema.sql` (re-run each
  start with `CREATE TABLE IF NOT EXISTS`). This is not versioned migration.
- **Payment-service and notification-service have no schema.sql.** All their state is
  in-memory.
- **No explicit column indexes** beyond primary keys. Columns used in frequent queries
  (`booking_reference`, `route_code`, `customer_email`) have no explicit `CREATE INDEX`.
- **No NoSQL store.** Phase III recommends it for higher evaluation.

### Plan for Task 02
- Add Flyway to all four backend services. Rename existing `schema.sql` content to
  `V1__initial_schema.sql` migration files. Add `V2__add_indexes.sql` for explicit indexes.
- Create `schema.sql` (then Flyway migration) for payment-service and notification-service.
- Optionally add MongoDB via Docker Compose for notification delivery records (document
  model is natural fit for delivery receipts).

---

## 3. REST API

**Status: PARTIAL**

### What exists
- `GET` and `POST` endpoints exist for all main operations.
- `ApiErrorResponse` is a consistent error structure used across all services.
- HTTP status codes are semantically used: `200`, `201`, `404`, `422`, `503`, `504`.
- All responses are JSON.

### What is missing
- **No PUT/PATCH endpoints** on any resource. Phase III requires full CRUD via REST.
- **No DELETE endpoints.** Bookings have no cancellation endpoint. Routes have no remove.
- **No paginated collection reads.** No `page`/`size` query parameters anywhere.
- **No `Location` header** returned on `201 Created` responses.
- Payment records and notification deliveries are not addressable by ID (no schema).

### Plan for Task 03
- Add `PUT /api/v1/bookings/{bookingReference}/cancel` (soft-delete, state → CANCELLED).
- Add `PUT /api/v1/schedules/admin/routes/{routeCode}` for fare/route update.
- Add `DELETE /api/v1/schedules/admin/routes/{routeCode}` for route removal.
- Add `page` and `size` query parameters to all list endpoints.
- Add `Location` header to booking creation `201` response.

---

## 4. Asynchronous Data Processing

**Status: DONE (minor gap)**

### What exists
- Kafka 3.9 running in Docker with a persistent `kafka-data` volume.
- Producer in booking-service: publishes `booking-confirmed.v1` JSON events to
  `smartbus.booking.confirmed.v1` after orchestration completes.
- Consumer in notification-service: consumes the same topic and persists delivery records.
- Offline recovery is documented in `docs/messaging-demo.md`.
- Versioned message contract in `contracts/messages/booking-confirmed.v1.json`.

### What is missing
- **Only one Kafka scenario.** Phase III recommends demonstrating a second async processing
  pattern beyond booking confirmation.
- **No background job processing.** Phase III explicitly lists background job systems as
  an option for async processing demonstration.

### Plan for Task 04
- Add a second Kafka topic: `smartbus.fare.updated.v1`. When an admin updates a route fare,
  publish a fare-change event. Schedule-service consumers this event and invalidates its cache
  asynchronously, demonstrating event-driven cache invalidation.
- Document this second scenario in `docs/async-processing.md`.

---

## 5. Caching Strategy

**Status: DONE (documentation gap)**

### What exists
- Data-level cache in schedule-service: Caffeine with 5-minute absolute TTL, 200-entry cap,
  caches route catalog and route definitions.
- Output cache in `CachedQuoteResponseService`: 30-second absolute TTL for quote responses,
  keyed by `fromStop|toStop|tripDate|tripType|passengers`.
- Cache invalidation on `POST /api/v1/schedules/admin/routes/{routeCode}/fare` clears both
  cache layers.
- `docs/cache-strategy.md` documents the design.

### What is missing
- **Actuator metrics endpoint is not exposed.** Current config exposes only `health` and
  `info`. Cache hit/miss metrics from Caffeine are not reachable via Actuator.
- **`docs/cache-performance.md` exists** but may not contain real measured benchmark numbers.
  Phase III requires actual before/after measurements.

### Plan for Task 05
- Add `metrics` to the `management.endpoints.web.exposure.include` list in schedule-service.
- Enable Caffeine statistics and wire them to Micrometer so cache metrics appear under
  `/actuator/metrics/cache.gets`, `/actuator/metrics/cache.puts`, etc.
- Run actual benchmarks (curl loop or wrk) and record the numbers in `docs/cache-performance.md`.

---

## 6. Data Security and Validation

**Status: PARTIAL (mostly done)**

### What exists
- **JWT authentication** fully implemented in the gateway:
  - `JwtService` issues and parses HMAC-SHA signed tokens with expiration.
  - `JwtAuthenticationFilter` validates the Bearer token on protected routes.
  - `SecurityConfiguration` defines public vs. protected endpoint rules.
  - `AuthController` provides `POST /api/v1/auth/login` and `POST /api/v1/auth/register`.
  - `AuthService` stores passwords as BCrypt hashes.
- **Bean Validation** annotations on all main request DTOs.
- All database interactions use JPA (parameterized queries only — no SQL string concatenation).
- Sensitive fields (passwords) are not returned in response bodies.

### What is missing
- **Field-level validation error response**: the `ApiExceptionHandler` may return a generic
  error on `MethodArgumentNotValidException` rather than listing which fields failed and why.
  Phase III requires field-level detail in `400 Bad Request` responses.
- **XSS sanitization**: free-text fields (`customerName`, contact message body) are stored and
  returned without stripping HTML.
- **MDC correlation in all services**: only `booking-service` uses MDC. Other services log
  without a correlation key, making cross-service tracing hard.

### Plan for Task 06
- Update every `ApiExceptionHandler` to handle `MethodArgumentNotValidException` with a
  field-by-field error list in the response body.
- Add a simple HTML-stripping utility applied to free-text fields before persistence.
- Add MDC context (correlation ID from `X-Request-Id` header) to all services.

---

## 7. Data Transformation and Interoperability

**Status: MISSING**

### What exists
- Nothing. All endpoints return JSON only. No XML support, no content negotiation, no
  aggregation endpoint.

### What is missing
- **XML serialization**: no `jackson-dataformat-xml` dependency, no `@JacksonXmlRootElement`,
  no `produces = {MediaType.APPLICATION_JSON_VALUE, MediaType.APPLICATION_XML_VALUE}`.
- **Response aggregation**: no endpoint that combines booking, schedule, and payment data
  into a single flattened document.
- **Content negotiation**: no `Accept: application/xml` handling anywhere.

### Plan for Task 07
- Add `jackson-dataformat-xml` to schedule-service (or gateway). Configure Spring MVC to
  handle `Accept: application/xml` for route catalog and quote endpoints.
- Add `GET /api/v1/gateway/booking-summary/{bookingReference}` to the gateway that calls
  booking-service, fetches schedule details, and returns a flattened summary document.
- Document both transformation scenarios with before/after payload examples.

---

## 8. Logging and Monitoring

**Status: PARTIAL**

### What exists
- Structured event-style log entries in booking-service: `workflowStep`, `partnerCallStart`,
  `partnerCallSuccess`, `partnerCallRetry`, `partnerCallFailure`, `bookingWorkflowFailed`.
- MDC used in booking-service to tag logs with `bookingReference`.
- Spring Boot Actuator exposes `health` and `info` on all services.

### What is missing
- **`X-Request-Id` header propagation**: not implemented. No header is generated at the
  gateway or passed to downstream services, making end-to-end request tracing impossible
  from logs alone.
- **`metrics` endpoint not exposed**: `management.endpoints.web.exposure.include` is set to
  `health,info` on all services. Cache metrics, JVM metrics, and HTTP metrics are unreachable.
- **Cache metrics not wired to Micrometer**: Caffeine statistics are enabled in config but
  are not bridged to the Actuator metrics registry.
- **MDC absent in schedule-service, payment-service, notification-service**: log lines from
  these services have no correlation ID.
- **No Kafka consumer lag logging** on startup or on consume.

### Plan for Task 08
- Add a gateway servlet filter that generates `X-Request-Id` if absent and logs it on every
  request. Propagate the header on all outbound `RestClient` calls to downstream services.
- Add an MDC filter to each service that reads `X-Request-Id` from the incoming request and
  puts it in the MDC context for the duration of the request.
- Expose `metrics` in all `application.yml` files.
- Register Caffeine cache statistics with Micrometer using `CacheMetricsRegistrar`.

---

## Summary Table

| # | Requirement | Status | Key Gap |
|---|---|---|---|
| 1 | Data Access Layer | PARTIAL | No payment/notification persistence; no paginated lists; no PUT/DELETE |
| 2 | Database Integration | PARTIAL | No Flyway migrations; no indexes; no payment/notification schema |
| 3 | REST API | PARTIAL | No PUT/PATCH/DELETE; no pagination; no Location header |
| 4 | Async Processing | DONE | One Kafka scenario; no background job |
| 5 | Caching | DONE | Metrics not exposed; benchmark numbers needed |
| 6 | Security & Validation | PARTIAL | No field-level error response; no XSS sanitization; MDC missing in most services |
| 7 | Data Transformation | MISSING | No XML support; no aggregation endpoint |
| 8 | Logging & Monitoring | PARTIAL | No X-Request-Id; metrics not exposed; MDC in booking only |

## Build Verification

`mvn clean test` — **BUILD SUCCESS** (verified 2026-04-13).

```
[INFO] Reactor Summary for smartbus-platform 0.1.0-SNAPSHOT:
[INFO] smartbus-platform .............. SUCCESS [  0.044 s]
[INFO] smartbus-gateway ............... SUCCESS [  1.579 s]
[INFO] smartbus-booking-service ....... SUCCESS [  1.221 s]
[INFO] smartbus-schedule-service ...... SUCCESS [  3.445 s]
[INFO] smartbus-payment-service ....... SUCCESS [  0.451 s]
[INFO] smartbus-notification-service .. SUCCESS [  0.487 s]
[INFO] BUILD SUCCESS — Total time: 7.359 s
```

`docker compose -f infra/docker-compose.yml config` validates cleanly. No source files or
pom.xml were changed in Task 00 — this task is purely additive (new docs and folders).

---

## Implementation Priority

1. **Task 01 + 02**: persistence for payment and notification is foundational — Tasks 03–08
   depend on data being durable.
2. **Task 07**: data transformation is entirely missing and needs new dependencies.
3. **Task 06**: field-level validation and XSS sanitization are straightforward additions.
4. **Task 08**: X-Request-Id + metrics endpoint changes are low-risk and high-visibility.
5. **Task 03**: REST CRUD additions build on the new persistence from Tasks 01–02.
6. **Task 04 + 05**: Kafka second scenario and cache metrics are relatively contained.
