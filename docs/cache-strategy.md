# SmartBus Cache Strategy

## Overview

SmartBus Task 04 uses two active cache levels inside `schedule-service`:

1. Data-level cache for frequent route and schedule reads
2. Output cache for repeated quote API responses

## Active Cache Types

### Data-Level Cache

- Technology: Spring Cache with Caffeine
- Cached items:
  - route catalog
  - route definitions by `fromStop -> toStop`
- TTL policy: absolute expiration after `5 minutes`
- Metrics: Caffeine statistics are enabled so cache hits and misses can be exposed through Spring Boot metrics
- Purpose: avoid repeated simulated datastore reads for route and fare information

### Output Cache

- Technology: explicit in-memory response cache in `CachedQuoteResponseService`
- Cached items:
  - `POST /api/v1/schedules/quote` response payloads
- TTL policy: absolute expiration after `30 seconds`
- Cache key:
  - `fromStop|toStop|tripDate|tripType|passengers`
- Purpose: return the full quote response immediately for repeated searches

## Invalidation Policy

Critical fare changes invalidate both cache levels through:

`POST /api/v1/schedules/admin/routes/{routeCode}/fare`

On fare update:

- data cache entries are evicted
- quote output cache is cleared

This prevents stale prices from being served after administrative updates.

## Memory Trade-Offs

- Data cache holds only small route objects and a catalog snapshot, so it is low risk and low memory.
- Output cache stores complete quote responses, which is slightly more expensive but still bounded by natural request diversity and short TTL.
- Caffeine is capped at `200` entries for the data cache to prevent uncontrolled growth.
- Output cache uses a short TTL to trade memory for fast repeated quote responses.

## Operational Notes

- Caching is local to the `schedule-service` instance.
- This is appropriate for the current single-node local architecture.
- In a multi-instance deployment, Redis or another shared cache would be the next step if consistency across nodes became necessary.
