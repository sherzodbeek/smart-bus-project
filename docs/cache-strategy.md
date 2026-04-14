# SmartBus Cache Strategy

## Overview

SmartBus uses two active cache layers inside `schedule-service`:

1. **Data-level cache** — Caffeine-backed Spring Cache for route and location reads
2. **Output cache** — explicit in-memory response cache for repeated quote API calls

---

## Cache Layers

### Layer 1 — Data-Level Cache (Caffeine)

| Property        | Value                                                  |
|-----------------|--------------------------------------------------------|
| Technology      | Spring Cache + Caffeine                                |
| Cache names     | `routeCatalog`, `routeDefinition`, `locationCatalog`, `locationById` |
| TTL policy      | Absolute write-expiry (`PT5M` default, env `SCHEDULE_DATA_CACHE_TTL`) |
| Max entries     | 200 (across all named caches via shared `CaffeineCacheManager`) |
| Stats           | `recordStats()` enabled — hit/miss counts via Actuator `/actuator/metrics` |

**Cached items:**

| Cache name       | Method                                          | Cache key             |
|------------------|-------------------------------------------------|-----------------------|
| `routeCatalog`   | `ScheduleCatalogService.catalog()`              | implicit (single key) |
| `routeDefinition`| `ScheduleCatalogService.routeDefinition(from,to)`| `fromStop->toStop`   |
| `locationCatalog`| `ScheduleCatalogService.locations()`            | implicit (single key) |
| `locationById`   | `ScheduleCatalogService.requireLocation(id)`    | `#id`                 |

### Layer 2 — Output Cache (CachedQuoteResponseService)

| Property     | Value                                                              |
|--------------|--------------------------------------------------------------------|
| Technology   | `ConcurrentHashMap` with TTL check in `CachedQuoteResponseService` |
| Cached item  | `POST /api/v1/schedules/quote` full response payload               |
| TTL policy   | Absolute expiry (`PT30S` default, env `SCHEDULE_OUTPUT_CACHE_TTL`) |
| Cache key    | `fromStop\|toStop\|tripDate\|tripType\|passengers`                |

---

## Invalidation Policy

### Route mutations

All of `createRoute`, `updateRoute`, `deleteRoute`, and `refreshFare` carry
`@CacheEvict(cacheNames = {"routeCatalog", "routeDefinition"}, allEntries = true)`.

The controller additionally calls `cachedQuoteResponseService.invalidateAll()` for every
route or fare mutation so the output cache is cleared together with the data cache.

### Location mutations

`createLocation`, `updateLocation`, and `deleteLocation` carry
`@CacheEvict(cacheNames = {"locationCatalog", "locationById"}, allEntries = true)`.

### Summary table

| Operation                   | `routeCatalog` | `routeDefinition` | `locationCatalog` | `locationById` | Output cache |
|-----------------------------|:--------------:|:-----------------:|:-----------------:|:--------------:|:------------:|
| `POST /admin/routes`        | evict          | evict             |                   |                | clear        |
| `PUT /admin/routes/{code}`  | evict          | evict             |                   |                | clear        |
| `DELETE /admin/routes/{code}` | evict        | evict             |                   |                | clear        |
| `POST /admin/routes/{code}/fare` | evict    | evict             |                   |                | clear        |
| `POST /admin/locations`     |                |                   | evict             | evict          |              |
| `PUT /admin/locations/{id}` |                |                   | evict             | evict          |              |
| `DELETE /admin/locations/{id}` |             |                   | evict             | evict          |              |

---

## Actuator Endpoints

The following management endpoints expose cache state and metrics at runtime:

| Endpoint                             | Purpose                                    |
|--------------------------------------|--------------------------------------------|
| `GET /actuator/caches`               | Lists all cache names and their manager    |
| `GET /actuator/caches/{name}`        | Shows a specific cache (evict via DELETE)  |
| `GET /actuator/metrics/cache.gets`   | Caffeine hit/miss counts per cache         |
| `GET /actuator/metrics/cache.size`   | Current entry count per cache              |

Example response for `GET /actuator/metrics/cache.gets?tag=name:routeCatalog`:

```json
{
  "name": "cache.gets",
  "measurements": [{"statistic": "COUNT", "value": 7}],
  "availableTags": [{"tag": "result", "values": ["hit", "miss"]}]
}
```

---

## Memory Trade-offs

- All four data caches share a single `CaffeineCacheManager` capped at 200 entries.
  Route and location objects are small DTOs; memory pressure is negligible.
- The output cache stores full `ScheduleQuoteResponse` objects. The short 30-second TTL
  bounds memory and prevents stale fare data from persisting across administrative updates.
- Caching is local to the `schedule-service` instance. In a multi-instance deployment
  a shared Redis cache would be the next step for cross-node consistency.
