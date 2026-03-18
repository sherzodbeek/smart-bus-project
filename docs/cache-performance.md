# SmartBus Cache Performance

## Measurement Setup

Measurements were taken from `ScheduleCachingTests` in `schedule-service` with simulated datastore latency set to `60 ms`.

## Measured Results

Measured local test output on March 15, 2026:

- `catalogColdMs=60`
- `catalogWarmMs=0`
- `quoteColdMs=62`
- `quoteWarmMs=0`
- Fare-update invalidation test forced the next catalog and quote reads back through the datastore path and verified the updated `SB-101` fare of `18.75`.

## Interpretation

- Data-level caching removes the repeated datastore-read penalty for route catalog access.
- Output caching removes the recomputation path entirely for identical quote requests during the output TTL window.
- The improvement is especially visible on read-heavy endpoints where users repeatedly search the same trips.

## Before and After Summary

- Route catalog:
  - before cache: about `60 ms`
  - after cache: under `1 ms`
- Quote response:
  - before cache: about `62 ms`
  - after cache: under `1 ms`

## Trade-Offs

- Faster reads come at the cost of extra heap usage.
- Short output TTL and explicit invalidation reduce stale-response risk.
- Data cache is safer for semi-static route data; output cache is best for short-lived repeated search patterns.
