# SmartBus Cache Performance

## Measurement Setup

All measurements are taken from `ScheduleCachingTests` in `schedule-service`.
The test application is configured with `smartbus.cache.simulated-latency=PT0.06S` (60 ms)
to simulate a realistic datastore read latency. Each test calls the service method twice —
once cold (cache empty) and once warm (result already cached) — and prints elapsed time
in milliseconds.

**Tool:** JUnit 5 via `System.nanoTime()`  
**Environment:** local JVM, Apple Silicon, Java 25  
**Simulated latency:** 60 ms per datastore call

---

## Measured Results

Numbers recorded from test run on 2026-04-13:

| Scenario                         | Cold (ms) | Warm (ms) | Improvement |
|----------------------------------|----------:|----------:|-------------|
| Route catalog read               | 67        | 0         | >99 %       |
| Quote output (repeated request)  | 65        | 0         | >99 %       |
| Location catalog read            | 65        | 0         | >99 %       |
| Location catalog after create    | 64 *      | —         | cache evicted correctly |

\* After `createLocation`, the `locationCatalog` cache is evicted. The next call goes
through the datastore (64 ms), confirming the invalidation path works.

---

## Caffeine Hit/Miss via Actuator

At runtime, Caffeine statistics are exposed through Spring Boot Actuator at
`GET /actuator/metrics/cache.gets?tag=name:<cache-name>`.

Example after several requests to `GET /api/v1/schedules/catalog` (first call is a miss,
subsequent calls within the 5-minute TTL window are hits):

```
cache.gets{name=routeCatalog, result=hit}  → 6
cache.gets{name=routeCatalog, result=miss} → 1
```

Example for `locationCatalog` after two `GET /api/v1/schedules/admin/locations` calls
followed by one `POST /admin/locations` (which evicts) and one more GET:

```
cache.gets{name=locationCatalog, result=hit}  → 1
cache.gets{name=locationCatalog, result=miss} → 2
```

---

## Before / After Summary

| Layer           | Endpoint                          | Before cache | After cache |
|-----------------|-----------------------------------|-------------:|------------:|
| Data — routes   | `GET /api/v1/schedules/catalog`   | ~67 ms       | <1 ms       |
| Output          | `POST /api/v1/schedules/quote`    | ~65 ms       | <1 ms       |
| Data — locations| `GET /api/v1/schedules/admin/locations` | ~65 ms | <1 ms       |

---

## Invalidation Correctness

The `fareUpdateInvalidatesCatalogAndQuoteCaches` test and
`locationCacheInvalidatedOnLocationCreate` test both assert that after a mutation:

- The next read re-hits the datastore (latency ≥ 60 ms).
- The freshly loaded data reflects the mutation (updated fare / new location name).

This proves that cache invalidation fires synchronously within the same thread before the
response is returned to the caller.

---

## Trade-offs

- Faster reads come at the cost of extra heap usage and the risk of serving slightly stale
  data within the TTL window.
- Short output TTL (30 s) and explicit `@CacheEvict` on every mutation reduce stale-response
  risk to the minimum.
- The data cache is safer for semi-static route/location data; the output cache is best for
  short-lived repeated search patterns.
- Caffeine's `maximumSize(200)` prevents unbounded growth under adversarial request patterns.
