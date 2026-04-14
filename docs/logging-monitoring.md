# Logging and Monitoring — SmartBus

## Log Format

All five services share the same Logback console pattern:

```
%d{yyyy-MM-dd'T'HH:mm:ss.SSS} %-5level [%X{requestId:--}] [%X{bookingReference:--}] %logger{35} - %msg%n
```

Configured via `logging.pattern.console` in each service's `application.yml`.

### Field definitions

| Field              | Source    | Description |
|--------------------|-----------|-------------|
| timestamp          | Logback   | ISO-8601 with milliseconds |
| level              | Logback   | `INFO`, `WARN`, `ERROR`, `DEBUG` |
| `requestId`        | MDC       | `X-Request-Id` correlation header; `--` if not set |
| `bookingReference` | MDC       | Booking workflow ID; `--` outside booking context |
| logger             | Logback   | Abbreviated class name |
| message            | logger    | `key=value` structured pairs (no positional fields) |

### Example log lines

```
2026-04-13T09:31:42.001 INFO  [a1b2c3d4-e5f6-7890-abcd-ef1234567890] [BK-AB12CD34] c.s.b.s.BookingWorkflowOperations - workflowStep bookingReference=BK-AB12CD34 step=receive-booking-request element=receive detail=Received booking request
2026-04-13T09:31:42.087 INFO  [a1b2c3d4-e5f6-7890-abcd-ef1234567890] [BK-AB12CD34] c.s.b.s.PartnerCallExecutor - partnerCallStart bookingReference=BK-AB12CD34 service=schedule-service operation=quote-trip attempt=1 timeoutMs=250
2026-04-13T09:31:42.155 INFO  [a1b2c3d4-e5f6-7890-abcd-ef1234567890] [BK-AB12CD34] c.s.b.s.PartnerCallExecutor - partnerCallSuccess bookingReference=BK-AB12CD34 service=schedule-service operation=quote-trip attempt=1 elapsedMs=68
2026-04-13T09:31:42.300 ERROR [a1b2c3d4-e5f6-7890-abcd-ef1234567890] [BK-AB12CD34] c.s.b.s.PartnerCallExecutor - partnerCallFailure bookingReference=BK-AB12CD34 service=payment-service operation=authorize-payment attempt=2 code=DOWNSTREAM_TIMEOUT rootType=TimeoutException message=null
```

---

## Monitored Event Types

### INFO — successful completions

| Event name | Service | Key fields |
|------------|---------|------------|
| `bookingOrchestrationStart` | booking | `bookingReference`, `customerEmail` |
| `bookingOrchestrationComplete` | booking | `bookingReference` |
| `workflowStep` | booking | `bookingReference`, `step`, `element`, `detail` |
| `partnerCallStart` | booking | `bookingReference`, `service`, `operation`, `attempt`, `timeoutMs` |
| `partnerCallSuccess` | booking | `bookingReference`, `service`, `operation`, `attempt`, `elapsedMs` |
| `partnerHttpRequest` | booking | `service`, `operation`, relevant fields (no token) |
| `scheduleCatalogRequest` | schedule | — |
| `scheduleQuoteRequest` | schedule | `fromStop`, `toStop`, `tripDate`, `tripType`, `passengers` |
| `paymentAuthorizeDecision` | payment | `bookingReference`, `transactionId`, `status` |
| `bookingEventConsumed` | notification | `bookingReference`, `customerEmail`, `routeCode`, `status` |
| `jwtAuthenticationSuccess` | gateway | `path`, `user`, `role` |

### WARN — retries, degraded paths

| Event name | Service | Key fields |
|------------|---------|------------|
| `partnerCallRetry` | booking | `bookingReference`, `service`, `operation`, `attempt`, `backoffMs`, `code` |
| `bookingEventPublishFailed` | booking | `bookingReference`, `exceptionType`, `message` |
| `paymentDeclinedAlert` | booking | `bookingReference`, `transactionId`, `customerEmail`, `amount`, `reason` |
| `paymentStatusLookupFailed` | gateway | `bookingReference`, `reason` |
| `jwtAuthenticationFailed` | gateway | `path`, `message` |

### ERROR — failures requiring operator attention

| Event name | Service | Key fields |
|------------|---------|------------|
| `partnerCallFailure` | booking | `bookingReference`, `service`, `operation`, `attempt`, `code`, `rootType` |
| `bookingWorkflowFailed` | booking | `bookingReference`, `exceptionType`, `message` |
| `unexpectedError` | all | `path`, (full exception in log, no stack trace in response) |

---

## Sensitive Data Policy

The following fields are **never logged** in any service:

| Field | Appears in | Not logged because |
|-------|------------|--------------------|
| `paymentMethodToken` | `BookingRequest` | raw token, PCI scope |
| `password` / `newPassword` | `RegisterRequest`, `ChangePasswordRequest` | credential |
| `password_hash` | DB entity | stored credential |

`partnerHttpRequest` in `PartnerServiceClient` logs `bookingReference`, `customerEmail`,
and `amount` — never the `paymentMethodToken` from `PaymentAuthorizationRequest`.

---

## X-Request-Id Correlation

### Generation (gateway)

`RequestIdFilter` (`@Order(1)`) runs before any controller. It:
1. Reads `X-Request-Id` from the inbound request header.
2. If absent, generates `UUID.randomUUID().toString()`.
3. Stores the value in MDC under key `requestId`.
4. Echoes it back in the response header.
5. Removes it from MDC in the `finally` block.

```java
// gateway/filter/RequestIdFilter.java
String requestId = request.getHeader("X-Request-Id");
if (requestId == null || requestId.isBlank()) {
    requestId = UUID.randomUUID().toString();
}
MDC.put("requestId", requestId);
response.setHeader("X-Request-Id", requestId);
```

### Propagation (gateway → microservices)

`HttpClientConfiguration` configures a `ClientHttpRequestInterceptor` on the
shared `RestClient.Builder`:

```java
// gateway/config/HttpClientConfiguration.java
.requestInterceptor((request, body, execution) -> {
    String requestId = MDC.get("requestId");
    if (requestId != null) {
        request.getHeaders().set("X-Request-Id", requestId);
    }
    return execution.execute(request, body);
})
```

All gateway REST calls (to booking-service, payment-service, schedule-service,
notification-service) automatically carry the header.

### Receipt in microservices

Each microservice has a `RequestIdFilter` (`@Order(1)`) that reads the header
and puts the value into MDC:

```java
// booking/filter/RequestIdFilter.java  (same pattern in all 4 microservices)
String requestId = request.getHeader("X-Request-Id");
if (requestId != null && !requestId.isBlank()) {
    MDC.put("requestId", requestId);
    response.setHeader("X-Request-Id", requestId);
}
```

Because the log pattern includes `%X{requestId}`, every log line in the
microservice thread automatically includes the same ID as the gateway line.

### Propagation (booking-service → partners)

`RestClientConfiguration` in booking-service defines a `RestClient.Builder` bean
with the same interceptor, so every `PartnerServiceClient` call to
schedule-service, payment-service, and notification-service carries the header.

### Tracing a booking end-to-end

```bash
# 1. Make a booking — note the X-Request-Id returned in the response header
curl -si -X POST http://localhost:8080/api/v1/frontend/bookings \
  -H "Authorization: Bearer <token>" \
  -H "Content-Type: application/json" \
  -d '{...}' | grep X-Request-Id
# X-Request-Id: a1b2c3d4-e5f6-7890-abcd-ef1234567890

# 2. Grep all service logs for that ID
grep "a1b2c3d4-e5f6-7890-abcd-ef1234567890" \
  gateway.log booking-service.log schedule-service.log payment-service.log notification-service.log
```

Every log line from all five services that participated in the request will match.

---

## Spring Boot Actuator Endpoints

All five services expose:

| Endpoint | Port | Purpose |
|----------|------|---------|
| `GET /actuator/health` | service port | Liveness / readiness — Spring Boot auto-checks DB, disk |
| `GET /actuator/info` | service port | App name and phase from `info.*` properties |
| `GET /actuator/metrics` | service port | Lists all registered Micrometer metrics |
| `GET /actuator/metrics/{name}` | service port | Detailed metric with tags and measurements |
| `GET /actuator/caches` | schedule-service only | Lists Caffeine cache names |

### Expected health response

```json
{"status": "UP"}
```

With datasource contributor active:

```json
{
  "status": "UP",
  "components": {
    "db": {"status": "UP", "details": {"database": "PostgreSQL"}},
    "diskSpace": {"status": "UP"},
    "ping": {"status": "UP"}
  }
}
```

### Expected info response (gateway)

```json
{"app": {"name": "SmartBus Gateway", "phase": "foundation"}}
```

---

## Cache Metrics (schedule-service)

Caffeine is configured with `recordStats()` and the cache manager is backed by
Spring Cache, which Micrometer auto-instruments when Actuator is present.

### Metric names

| Metric | Tags | Meaning |
|--------|------|---------|
| `cache.gets` | `name`, `result=hit` | Requests served from cache |
| `cache.gets` | `name`, `result=miss` | Requests that went to the datastore |
| `cache.puts` | `name` | Entries written to cache |
| `cache.evictions` | `name` | Entries evicted (TTL, capacity) |
| `cache.size` | `name` | Current entry count |

### Sample queries

```bash
# Hit rate for routeCatalog
curl http://localhost:8082/actuator/metrics/cache.gets?tag=name:routeCatalog&tag=result:hit

# Miss count for locationCatalog
curl http://localhost:8082/actuator/metrics/cache.gets?tag=name:locationCatalog&tag=result:miss
```

---

## Kafka Consumer Metrics

When `spring-boot-starter-actuator` and `spring-kafka` are both on the classpath,
Micrometer auto-registers Kafka consumer metrics. They are accessible at:

```bash
curl http://localhost:8084/actuator/metrics/kafka.consumer.records-lag
curl http://localhost:8084/actuator/metrics/kafka.consumer.fetch-latency-avg
```

Relevant metrics:

| Metric | Description |
|--------|-------------|
| `kafka.consumer.records-lag` | Per-partition lag (messages not yet consumed) |
| `kafka.consumer.fetch-latency-avg` | Average broker fetch latency |
| `kafka.consumer.commit-rate` | Offset commits per second |

Consumer groups:
- `notification-service` — consumes `smartbus.booking.confirmed.v1`
- `booking-service-payment-audit` — consumes `smartbus.payment.declined.v1`

Zero lag confirms the consumer is caught up. Non-zero lag after a restart
confirms the `auto.offset.reset = earliest` backlog replay is in progress.
