# Local Development Runtime Plan

## Ports

- Gateway: `8080`
- Booking service: `8081`
- Schedule service: `8082`
- Payment service: `8083`
- Notification service: `8084`
- Frontend webpack dev server: `8088`
- PostgreSQL: `5433`
- Kafka: `9092`

## PostgreSQL Databases

- `smartbus_booking`
- `smartbus_schedule`
- `smartbus_payment`
- `smartbus_notification`

## Shared PostgreSQL Credentials

- Username: `smartbus`
- Password: `smartbus`

## Service Environment Variables

### Gateway

- `SERVER_PORT` default: `8080`
- `BOOKING_SERVICE_URL` default: `http://localhost:8081`
- `SCHEDULE_SERVICE_URL` default: `http://localhost:8082`
- `PAYMENT_SERVICE_URL` default: `http://localhost:8083`
- `NOTIFICATION_SERVICE_URL` default: `http://localhost:8084`

### Booking Service

- `SERVER_PORT` default: `8081`
- `BOOKING_DB_URL` default: `jdbc:postgresql://localhost:5433/smartbus_booking`
- `BOOKING_DB_USERNAME` default: `smartbus`
- `BOOKING_DB_PASSWORD` default: `smartbus`
- `BOOKING_PARTNER_TIMEOUT` default: `PT0.25S`
- `BOOKING_PARTNER_MAX_ATTEMPTS` default: `3`
- `BOOKING_PARTNER_BACKOFF` default: `PT0.05S`
- `KAFKA_BOOTSTRAP_SERVERS` default: `localhost:9092`
- `BOOKING_CONFIRMED_TOPIC` default: `smartbus.booking.confirmed.v1`
- `SCHEDULE_SERVICE_URL` default: `http://localhost:8082`
- `PAYMENT_SERVICE_URL` default: `http://localhost:8083`
- `NOTIFICATION_SERVICE_URL` default: `http://localhost:8084`

### Schedule Service

- `SERVER_PORT` default: `8082`
- `SCHEDULE_DB_URL` default: `jdbc:postgresql://localhost:5433/smartbus_schedule`
- `SCHEDULE_DB_USERNAME` default: `smartbus`
- `SCHEDULE_DB_PASSWORD` default: `smartbus`

### Payment Service

- `SERVER_PORT` default: `8083`
- `PAYMENT_DB_URL` default: `jdbc:postgresql://localhost:5433/smartbus_payment`
- `PAYMENT_DB_USERNAME` default: `smartbus`
- `PAYMENT_DB_PASSWORD` default: `smartbus`

### Notification Service

- `SERVER_PORT` default: `8084`
- `NOTIFICATION_DB_URL` default: `jdbc:postgresql://localhost:5433/smartbus_notification`
- `NOTIFICATION_DB_USERNAME` default: `smartbus`
- `NOTIFICATION_DB_PASSWORD` default: `smartbus`
- `KAFKA_BOOTSTRAP_SERVERS` default: `localhost:9092`
- `BOOKING_CONFIRMED_TOPIC` default: `smartbus.booking.confirmed.v1`
- `BOOKING_CONSUMER_GROUP` default: `notification-service`

## Startup Order

1. Start PostgreSQL and Kafka with Docker Compose.
2. Start schedule, payment, and notification services.
3. Start booking service.
4. Start gateway.
5. Start the frontend from `frontend/` on port `8088`.

## Dependencies

- PostgreSQL is required for backend service startup.
- Kafka is required for asynchronous booking event delivery.
- The frontend can still run independently because it remains a static app.
- Gateway startup does not require the downstream services to be available immediately, but its discovery endpoint will reflect configured URLs rather than runtime health.
