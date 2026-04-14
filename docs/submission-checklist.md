# SmartBus Submission Checklist

## Source Package

- [x] `frontend/` contains the original SmartBus HTML, CSS, JS, and webpack sources
- [x] `backend/` contains the Spring Boot gateway and all backend services
- [x] `orchestration/` contains workflow definition and runtime configuration
- [x] `contracts/` contains REST and message contracts
- [x] `infra/` contains Docker Compose and database initialization assets
- [x] `docs/` contains architecture, design, evidence, and report artifacts

## Assignment Artifacts

- [x] orchestration workflow definition: `orchestration/booking-workflow.yaml`
- [x] messaging contract: `contracts/messages/booking-confirmed.v1.json`
- [x] OpenAPI contracts: `contracts/openapi/*.yaml`
- [x] correlation and state design: `docs/correlation-design.md`
- [x] caching design and evidence: `docs/cache-strategy.md`, `docs/cache-performance.md`
- [x] fault handling design and evidence: `docs/fault-strategy.md`, `docs/fault-simulations.md`
- [x] architecture report source: `docs/report/SmartBus-Integration-Architecture.md`
- [x] architecture report PDF: `docs/report/SmartBus-Integration-Architecture.pdf`

## Reproducibility

- [x] local infrastructure command documented in `README.md`
- [x] build and test command documented in `README.md`
- [x] contract validation command documented in `README.md`
- [x] backend startup order documented in `README.md`
- [x] frontend startup command documented in `README.md`
- [x] environment variables documented in `docs/runtime-plan.md`

---

## Phase III — Data Management and Integration Layer

### Source Code Components

- [x] Data access layer — controllers, services, repositories, entities in all five services
- [x] Flyway migration scripts — `src/main/resources/db/migration/` in every service
- [x] Security components — `JwtAuthenticationFilter`, `AuthController`, `HtmlSanitizer`, `@Valid` on all DTOs
- [x] Transformation components — XML annotations on DTOs, `DataTransformationController` (aggregation), CSV export in `BookingOrchestrationController`
- [x] Logging/monitoring — `RequestIdFilter` in all five services, `RestClient` interceptors for header propagation, MDC Kafka wrappers, Actuator `metrics` exposure, Logback pattern
- [x] Async processing — `BookingConfirmedEventProducer`, `BookingNotificationConsumer`, `PaymentDeclinedEventProducer`, `PaymentDeclinedEventConsumer`
- [x] Caching additions — four Caffeine caches (`routeCatalog`, `routeDefinition`, `locationCatalog`, `locationById`), `@CacheEvict` on all mutations, output cache, Actuator `caches` endpoint

### Contracts and Configuration

- [x] Kafka message contracts: `contracts/messages/booking-confirmed.v1.json`
- [x] Kafka message contracts: `contracts/messages/payment-declined.v1.json`
- [x] `infra/docker-compose.yml` includes PostgreSQL 17, Kafka 3.9 (KRaft), and MongoDB 7 (optional)
- [x] All five `application.yml` files updated with Actuator exposure, Kafka config, logging pattern, cache config

### Documentation

- [x] `docs/api-design.md` — full endpoint table, status codes, error schema, examples
- [x] `docs/async-processing.md` — Kafka topics, producer/consumer, persistence evidence, offline recovery demo
- [x] `docs/cache-strategy.md` — cache layers, TTL/invalidation policy, Actuator endpoints
- [x] `docs/cache-performance.md` — before/after benchmark results
- [x] `docs/data-transformation.md` — JSON↔XML, JSON aggregation, CSV export
- [x] `docs/database-design.md` — ER summary, index choices, MongoDB design
- [x] `docs/logging-monitoring.md` — MDC correlation, log format spec, Actuator metrics reference
- [x] `docs/security-design.md` — auth flow, protected endpoints, validation tables, XSS/SQLi protection
- [x] `docs/report/SmartBus-Phase3-Architecture.md` — Phase III architecture document source
- [x] `docs/report/SmartBus-Phase3-Architecture.pdf` — Phase III submission PDF

### Build and Validation

- [x] `mvn clean test` — BUILD SUCCESS, 17 tests, 0 failures
- [x] `sh contracts/validate-openapi.sh` — exit code 0

### README Completeness

- [x] Environment variables for all Phase III additions documented in `README.md`
- [x] Phase III components described in `README.md`
- [x] Phase III folder map in `README.md`
- [x] Startup order unchanged and documented

---

## Reviewer Notes

- The frontend in `frontend/` is still primarily a static UI shell at this phase.
- The backend is runnable and testable independently: `docker compose -f infra/docker-compose.yml up -d` then `mvn spring-boot:run` per service.
- MongoDB is disabled by default; set `SMARTBUS_MONGODB_ENABLED=true` to activate the optional audit sink.
- `SCHEDULE_SIMULATED_LATENCY` defaults to `PT0.06S` (60 ms) to demonstrate cache improvement in tests; set to `PT0S` in production.
