# SmartBus Data Access Layer Design

## Layering Architecture

Every backend service follows a strict three-tier structure:

```
HTTP Request
    │
    ▼
Controller  ──── request/response mapping, HTTP status codes, input validation
    │
    ▼
Service     ──── business logic, orchestration, domain decisions
    │
    ▼
Repository  ──── all database interactions (domain interface)
    │
    ▼
EntityRepository (Spring Data JPA)  ──── JPA operations on entity classes
    │
    ▼
PostgreSQL
```

Controllers never call `JpaRepository` or JDBC templates directly. They call a service;
the service calls a domain repository interface; the domain repository implementation
delegates to the Spring Data JPA entity repository.

---

## Service-by-Service CRUD Summary

### Gateway (port 8080)

**Resources**: `GatewayUserEntity`, `GatewayBusEntity`, `GatewayTicketDocumentEntity`,
`GatewayContactMessageEntity`, `GatewaySettingEntity`

| Operation | Method | Path |
|---|---|---|
| Auth: register | POST | `/api/v1/auth/register` |
| Auth: login | POST | `/api/v1/auth/login` |
| Users: list | GET | `/api/v1/frontend/admin/users` |
| Users: create | POST | `/api/v1/frontend/admin/users` |
| Users: update | PUT | `/api/v1/frontend/admin/users/{id}` |
| Users: delete | DELETE | `/api/v1/frontend/admin/users/{id}` |
| Buses: CRUD | GET/POST/PUT/DELETE | `/api/v1/frontend/admin/buses/**` |
| Tickets: list | GET | `/api/v1/frontend/admin/tickets` |
| Profile: read | GET | `/api/v1/frontend/profile` |
| Profile: update | PUT | `/api/v1/frontend/profile` |

Layer: `FrontendGatewayController` → `FrontendManagementService` → `FrontendGatewayRepository`

---

### Booking Service (port 8081)

**Resource**: `BookingProcessEntity` → `booking_process_instances`

| Operation | Method | Path |
|---|---|---|
| Create booking | POST | `/api/v1/bookings/orchestrated-bookings` |
| Read state | GET | `/api/v1/bookings/{bookingReference}/state` |
| List by customer | GET | `/api/v1/bookings?customerEmail={email}` |
| List all (admin) | GET | `/api/v1/bookings/admin/bookings` |
| Cancel (delete) | DELETE | `/api/v1/bookings/{bookingReference}` |

Layer: `BookingOrchestrationController` → `BookingQueryService` / `BookingOrchestrationService`
→ `BookingProcessRepository` (domain interface) → `JpaBookingProcessRepository`
→ `BookingProcessEntityRepository` (Spring Data JPA)

**Lifecycle states**: `RECEIVED → SCHEDULE_VALIDATED → ROUND_TRIP_VALIDATED →
PAYMENT_PENDING → PAYMENT_AUTHORIZED → NOTIFICATION_PENDING → CONFIRMED / FAILED / CANCELLED`

---

### Schedule Service (port 8082)

**Resources**: `ScheduleRouteEntity` → `schedule_routes`, `ScheduleLocationEntity` → `schedule_locations`

| Operation | Method | Path |
|---|---|---|
| Catalog (read) | GET | `/api/v1/schedules/catalog` |
| Quote (read) | POST | `/api/v1/schedules/quote` |
| Locations: list | GET | `/api/v1/schedules/admin/locations` |
| Locations: create | POST | `/api/v1/schedules/admin/locations` |
| Locations: update | PUT | `/api/v1/schedules/admin/locations/{id}` |
| Locations: delete | DELETE | `/api/v1/schedules/admin/locations/{id}` |
| Routes: create | POST | `/api/v1/schedules/admin/routes` |
| Routes: update | PUT | `/api/v1/schedules/admin/routes/{routeCode}` |
| Routes: delete | DELETE | `/api/v1/schedules/admin/routes/{routeCode}` |
| Fare: update | POST | `/api/v1/schedules/admin/routes/{routeCode}/fare` |

Layer: `ScheduleOrchestrationController` → `ScheduleCatalogService` / `CachedQuoteResponseService`
→ `ScheduleRepository` (domain interface) → `JpaScheduleRepository`
→ `ScheduleRouteEntityRepository`, `ScheduleLocationEntityRepository` (Spring Data JPA)

---

### Payment Service (port 8083)

**Resource**: `PaymentRecordEntity` → `payment_records` (added in Phase III)

| Operation | Method | Path |
|---|---|---|
| Authorize + create | POST | `/api/v1/payments/authorize` |
| Read by transaction ID | GET | `/api/v1/payments/records/{transactionId}` |
| List (optionally by booking) | GET | `/api/v1/payments/records[?bookingReference=...]` |
| Delete (void) | DELETE | `/api/v1/payments/records/{transactionId}` |

Layer: `PaymentAuthorizationController` → `PaymentService` → `PaymentRecordRepository`
(domain interface) → `JpaPaymentRecordRepository` → `PaymentRecordEntityRepository`
(Spring Data JPA)

---

### Notification Service (port 8084)

**Resource**: `NotificationDeliveryEntity` → `notification_deliveries` (added in Phase III,
replaced `InMemoryNotificationDeliveryStore`)

| Operation | Method | Path |
|---|---|---|
| Prepare (orchestration step) | POST | `/api/v1/notifications/prepare` |
| Dispatch (orchestration step) | POST | `/api/v1/notifications/dispatch` |
| List deliveries | GET | `/api/v1/notifications/deliveries[?bookingReference=...]` |
| Read delivery by ID | GET | `/api/v1/notifications/deliveries/{id}` |
| Delete delivery | DELETE | `/api/v1/notifications/deliveries/{id}` |

Layer: `NotificationDeliveryController` → `NotificationDeliveryService`
→ `NotificationDeliveryEntityRepository` (Spring Data JPA)

Kafka consumer `BookingNotificationConsumer` also calls `NotificationDeliveryService.save()`
when a `booking-confirmed.v1` event is consumed.

---

## Connection Pool Settings (HikariCP)

HikariCP is the default Spring Boot connection pool. The following settings are configured
explicitly in each service's `application.yml`:

| Parameter | booking / schedule | payment / notification / gateway |
|---|---|---|
| `maximum-pool-size` | 10 | 5 |
| `minimum-idle` | 2 | 1 |
| `connection-timeout` | 20 000 ms | 20 000 ms |
| `idle-timeout` | 300 000 ms (5 min) | 300 000 ms (5 min) |

Booking and schedule services use slightly larger pools because they handle higher request
concurrency (orchestration fan-out and cached catalog reads). Payment and notification
services see lower per-request concurrency so smaller pools are appropriate.

Spring Boot manages the DataSource lifecycle and connection release. All JPA operations use
`@Transactional` where state mutations need rollback semantics; read-only controllers rely
on Spring's default auto-commit behaviour.

---

## Entity Design Principles

- Every entity has a `created_at` column populated by `@PrePersist`.
- Entities that support mutable state also have `updated_at` populated by `@PreUpdate`.
- Primary keys:
  - Business-meaningful string IDs where available (`booking_reference`, `transaction_id`).
  - Auto-generated `bigserial` for append-only records (`notification_deliveries`).
- Indexes are declared on `@Table(indexes = {...})` and mirrored in `schema.sql` for
  the most frequent query predicates (`booking_reference`, `customer_email`, `route_code`).
