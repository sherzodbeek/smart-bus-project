# SmartBus Database Design

## Overview

SmartBus uses a **database-per-service** pattern. Each microservice owns its schema exclusively and no service crosses another's schema boundary. Data is shared only through service APIs or events.

| Service              | Database                  | Port | Technology       |
|----------------------|---------------------------|------|-----------------|
| gateway              | smartbus_booking          | 5433 | PostgreSQL 17   |
| booking-service      | smartbus_booking          | 5433 | PostgreSQL 17   |
| schedule-service     | smartbus_schedule         | 5433 | PostgreSQL 17   |
| payment-service      | smartbus_payment          | 5433 | PostgreSQL 17   |
| notification-service | smartbus_notification     | 5433 | PostgreSQL 17   |
| notification-service | smartbus_notifications    | 27017| MongoDB 7 (opt) |

## Schema Migrations

All services use **Flyway** for schema lifecycle management:

- `flyway-core` + `flyway-database-postgresql` declared in each service POM
- `baseline-on-migrate: true` — safe to run against pre-existing databases
- `baseline-version: 0` — baseline marker before V1 is applied
- Migration scripts live at `src/main/resources/db/migration/`
- Naming: `V{n}__{description}.sql`

## Per-Service Schemas

### Gateway (`V1__initial_schema.sql`, `V2__seed_data.sql`)

```
buses              — vehicle inventory (plate, model, capacity, active)
settings           — runtime configuration key-value pairs
flyway_schema_history — managed by Flyway
```

Seed data: 3 sample buses, 1 settings record.

### Booking Service (`V1__initial_schema.sql`)

```
booking_process_instances
  booking_reference  VARCHAR(36) PK  (UUID)
  customer_name      VARCHAR(200)
  customer_email     VARCHAR(200)
  from_stop          VARCHAR(200)
  to_stop            VARCHAR(200)
  trip_date          DATE
  trip_type          VARCHAR(32)
  passengers         INTEGER
  route_code         VARCHAR(64)
  departure_time     VARCHAR(64)
  arrival_time       VARCHAR(64)
  total_amount       DOUBLE PRECISION
  payment_txn_id     VARCHAR(64)
  notification_id    VARCHAR(64)
  current_state      VARCHAR(32)   NOT NULL
  last_error         TEXT
  created_at         TIMESTAMPTZ   NOT NULL
  updated_at         TIMESTAMPTZ   NOT NULL

Indexes:
  idx_booking_customer_email  (customer_email)
  idx_booking_current_state   (current_state)
```

Lifecycle states: `INITIATED`, `SCHEDULE_CONFIRMED`, `PAYMENT_CONFIRMED`, `NOTIFICATION_SENT`, `COMPLETED`, `FAILED`, `CANCELLED`

### Schedule Service (`V1__initial_schema.sql`, `V2__seed_data.sql`)

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

Seed data: 6 locations, 3 routes (SB-101, SB-202, SB-303).

### Payment Service (`V1__initial_schema.sql`)

```
payment_records
  transaction_id      VARCHAR(64) PK
  booking_reference   VARCHAR(36) NOT NULL
  amount              DOUBLE PRECISION NOT NULL
  currency            VARCHAR(8) NOT NULL DEFAULT 'USD'
  status              VARCHAR(32) NOT NULL
  created_at          TIMESTAMPTZ NOT NULL

Indexes:
  idx_payment_booking_reference   (booking_reference)
  idx_payment_status              (status)
  idx_payment_created_at          (created_at DESC)
```

### Notification Service (`V1__initial_schema.sql`)

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

## Connection Pool Configuration

Pool sizes are set conservatively for a single-host deployment:

| Service              | max-pool-size | min-idle |
|----------------------|---------------|----------|
| booking-service      | 10            | 2        |
| schedule-service     | 10            | 2        |
| payment-service      | 5             | 1        |
| notification-service | 5             | 1        |

Booking and schedule services handle higher concurrency (BPMN orchestration, cache miss queries). Payment and notification have lower peak load and smaller pools reduce idle resource usage.

## Paginated Admin Endpoint

`GET /api/v1/bookings/admin/bookings?page=0&size=20`

Returns a paginated list of all bookings sorted by `updated_at DESC`:

```json
{
  "items": [ ... ],
  "page": 0,
  "size": 20,
  "totalElements": 57,
  "totalPages": 3
}
```

Pagination is implemented at the database level via Spring Data's `Pageable` / `Page<T>`.

## MongoDB Optional Audit Sink

Notification events can be mirrored to MongoDB as a secondary audit store. Activation:

```yaml
smartbus:
  mongodb:
    enabled: true   # default: false

spring:
  data:
    mongodb:
      uri: mongodb://localhost:27017/smartbus_notifications
```

When disabled (default), no MongoDB connection is established. MongoDB repositories are excluded from auto-scanning via `spring.data.mongodb.repositories.type: none`; `MongoConfiguration` and `MongoNotificationSink` are guarded by `@ConditionalOnProperty`.

MongoDB document:

```
notification_events (collection)
  _id               ObjectId
  bookingReference  String  (indexed)
  recipient         String
  routeCode         String
  status            String
  deliveredAt       Instant
```

## Docker Compose

```yaml
postgres:  postgres:17  → port 5433
kafka:     apache/kafka → port 9092
mongo:     mongo:7      → port 27017  (optional audit)
```

Start all infrastructure: `docker compose -f infra/docker-compose.yml up -d`
