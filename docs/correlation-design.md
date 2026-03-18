# SmartBus Correlation and State Design

## Correlation Key Design

SmartBus uses `bookingReference` as the business correlation ID. It is generated once by the booking service at the start of orchestration and reused across:

- the booking HTTP response
- workflow state persistence
- partner service requests
- Kafka booking confirmation events
- state lookup endpoint

This keeps every process instance isolated by a single business identifier.

## State Management Approach

The booking service persists workflow state in PostgreSQL table `booking_process_instances`.

Tracked lifecycle states:

- `RECEIVED`
- `SCHEDULE_VALIDATED`
- `ROUND_TRIP_VALIDATED`
- `PAYMENT_PENDING`
- `PAYMENT_AUTHORIZED`
- `NOTIFICATION_PENDING`
- `CONFIRMED`
- `FAILED`

State transitions are written after each important orchestration step. If a failure occurs, the same process instance is updated to `FAILED` with the last error message.

## Concurrent Request Strategy

Parallel booking requests remain isolated because:

- each request gets a unique `bookingReference`
- state rows are keyed by `booking_reference`
- Kafka messages include the same `bookingReference`
- concurrent in-memory workflow logs also include the same correlation ID

No shared mutable booking state is reused across requests. Only per-request variables and correlation-keyed persistence records are updated.

## Resume After Restart

The state model is stored in PostgreSQL rather than memory. After service restart, process state can be inspected using:

`GET /api/v1/bookings/{bookingReference}/state`

This allows the system to resume inspection of any previously created process instance because the correlation key and latest state are durable.

## Concurrency Test Scenario

The automated concurrency test submits multiple booking requests in parallel and verifies:

- all booking references are unique
- each persisted process instance ends in `CONFIRMED`
- each response keeps its own correlation key
- event publishing count matches completed bookings

Related test:

- `BookingCorrelationConcurrencyTests`
