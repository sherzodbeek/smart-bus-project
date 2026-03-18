# SmartBus Fault Handling Strategy

## Scope

Task 06 adds resilient failure handling to the booking orchestration in `booking-service`.

## Fault Policy

- Timeout threshold: `250 ms` per downstream call by default
- Retry policy: `3` bounded attempts
- Backoff policy: linear backoff starting at `50 ms`
- Correlation key: every retry and failure log includes `bookingReference`

## Implemented Behavior

### Downstream Timeouts

- Downstream calls are executed through `PartnerCallExecutor`.
- Each call is wrapped in a timed `CompletableFuture`.
- When the timeout threshold is exceeded, the current attempt is failed and retried until the attempt budget is exhausted.
- Exhausted timeouts become `DOWNSTREAM_TIMEOUT` and return HTTP `504`.

### Downstream Unavailability

- Temporary client-side transport failures such as `ResourceAccessException` are retried with bounded backoff.
- Exhausted downstream connectivity failures become `DOWNSTREAM_UNAVAILABLE` and return HTTP `503`.

### Business Rejections

- Business failures like unavailable trips or declined payments remain `422`.
- These are not retried because they are domain decisions, not transient transport faults.

## User-Facing Responses

The booking API now returns a consistent `ApiErrorResponse` with:

- HTTP status
- stable error code
- clear user-facing message
- service and operation context
- request path

Example error codes:

- `BOOKING_WORKFLOW_REJECTED`
- `DOWNSTREAM_TIMEOUT`
- `DOWNSTREAM_UNAVAILABLE`
- `DOWNSTREAM_FAILURE`

## Process State Recovery

- Every failed booking is persisted as `FAILED` in `booking_process_instances`.
- The last error message is stored with the same `bookingReference`.
- Operators can inspect the terminal state using `GET /api/v1/bookings/{bookingReference}/state`.

## Logging Strategy

- Console logs include `bookingReference` through MDC.
- Partner call logs emit:
  - `partnerCallStart`
  - `partnerCallSuccess`
  - `partnerCallRetry`
  - `partnerCallFailure`
- Workflow failures emit `bookingWorkflowFailed`.

## Trade-Offs

- Retries improve resilience for brief outages, but may increase latency before final failure.
- Payment retries are acceptable in this prototype, but production payment flows would usually require idempotency keys or compensation.
- Timeouts prevent hanging requests from consuming orchestration threads indefinitely.
