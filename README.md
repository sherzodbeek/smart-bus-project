# SmartBus Backend Workspace

This repository now contains the backend foundation for SmartBus alongside the original static frontend in `frontend/`.

## Project Layout

- `frontend/`: Existing static SmartBus frontend built with HTML, CSS, JS, and webpack.
- `backend/gateway/`: Spring Boot API gateway for frontend-facing traffic and service discovery.
- `backend/services/booking-service/`: Booking lifecycle service and future ticket orchestration entry point.
- `backend/services/schedule-service/`: Schedule and route availability service.
- `backend/services/payment-service/`: Payment authorization and settlement service.
- `backend/services/notification-service/`: Notification delivery service.
- `orchestration/`: Workflow definitions and runtime artifacts for later orchestration tasks.
- `contracts/`: OpenAPI or service contract artifacts.
- `infra/`: Local infrastructure, including PostgreSQL runtime definitions.
- `docs/`: Architecture notes, service boundaries, and runtime plans.

## Architecture

The backend is modeled as a small service-oriented system:

- The gateway accepts frontend traffic and routes SmartBus client requests to downstream services.
- The booking service owns ticket purchase workflow and booking records.
- The schedule service owns trip search, route lookup, and seat availability reads.
- The payment service owns payment authorization and transaction history.
- The notification service owns confirmation and alert dispatch.
- PostgreSQL is the system database platform. Each service is assigned its own database for clean service boundaries.

## Technology Choices

- Java 25
- Spring Boot 4.0.3
- Maven multi-module build
- PostgreSQL 17 via Docker Compose for local development
- Kafka 3.9 via Docker Compose for persistent asynchronous messaging

## Prerequisites

- Java 25
- Maven 3.9+
- Docker Desktop or Docker Engine
- Node.js and npm for the existing frontend in `frontend/`

## Run Local Infrastructure

From the repository root:

```sh
docker compose -f infra/docker-compose.yml up -d
```

This starts PostgreSQL, Kafka, and creates the service databases:

- `smartbus_booking`
- `smartbus_schedule`
- `smartbus_payment`
- `smartbus_notification`

Default local credentials:

- PostgreSQL host port: `5433`
- Username: `smartbus`
- Password: `smartbus`

## Deterministic Local Bring-Up

Use this order from a clean clone:

1. Start infrastructure:

```sh
docker compose -f infra/docker-compose.yml up -d
```

2. Verify the backend codebase:

```sh
mvn clean test
```

3. Start backend services in this order:

```sh
mvn -pl backend/services/schedule-service spring-boot:run
```

```sh
mvn -pl backend/services/payment-service spring-boot:run
```

```sh
mvn -pl backend/services/notification-service spring-boot:run
```

```sh
mvn -pl backend/services/booking-service spring-boot:run
```

```sh
mvn -pl backend/gateway spring-boot:run
```

4. Start the existing frontend separately:

```sh
cd frontend
npm install
npm run start -- --port 8088
```

## Build and Test the Backend

From the repository root:

```sh
mvn clean test
```

## Validate Service Contracts

From the repository root:

```sh
sh contracts/validate-openapi.sh
```

This validates that every `contracts/openapi/*.yaml` document is machine-readable and contains the required OpenAPI sections, operations, schemas, and examples.

## Run Backend Services

Each service is an independent Spring Boot application. Start them in separate terminals from the repository root:

```sh
mvn -pl backend/gateway spring-boot:run
```

```sh
mvn -pl backend/services/booking-service spring-boot:run
```

```sh
mvn -pl backend/services/schedule-service spring-boot:run
```

```sh
mvn -pl backend/services/payment-service spring-boot:run
```

```sh
mvn -pl backend/services/notification-service spring-boot:run
```

Default ports:

- Gateway: `8080`
- Booking service: `8081`
- Schedule service: `8082`
- Payment service: `8083`
- Notification service: `8084`

## Run the Existing Frontend

The existing frontend remains in `frontend/` and is intentionally separate from the backend.
At this phase it is still mostly static and not fully rewired to the backend yet, but the codebase and runtime can be started alongside the services.

To avoid a port collision with the gateway, start the webpack dev server on another port:

```sh
cd frontend
npm install
npm run start -- --port 8088
```

## Environment Variables

Each service accepts environment variables for its port and PostgreSQL connection. The exact values are documented in `docs/runtime-plan.md`.

## Repository Artifact Map

- `frontend/`: original SmartBus HTML, CSS, JS, and webpack project
- `backend/`: gateway plus booking, schedule, payment, and notification services
- `orchestration/`: booking workflow definition and runtime configuration
- `contracts/`: OpenAPI and message contracts with validation command
- `infra/`: Docker Compose and database bootstrap assets
- `docs/`: design notes, evidence docs, architecture report, and submission checklist

## Environment Variables

All services have safe defaults and run without any overrides in the local Docker Compose setup.
Override only when deploying to a non-default environment.

### Gateway

| Variable | Default | Description |
|----------|---------|-------------|
| `SERVER_PORT` | `8080` | HTTP listen port |
| `GATEWAY_DB_URL` | `jdbc:postgresql://localhost:5433/smartbus_booking` | PostgreSQL JDBC URL |
| `GATEWAY_DB_USERNAME` | `smartbus` | PostgreSQL username |
| `GATEWAY_DB_PASSWORD` | `smartbus` | PostgreSQL password |
| `SMARTBUS_JWT_SECRET` | `smartbus-smartbus-...-secret-key-2026` | HS256 signing key (change in production) |
| `SMARTBUS_JWT_EXPIRATION` | `PT8H` | Token validity period (ISO-8601 duration) |
| `BOOKING_SERVICE_URL` | `http://localhost:8081` | Booking service base URL |
| `SCHEDULE_SERVICE_URL` | `http://localhost:8082` | Schedule service base URL |
| `PAYMENT_SERVICE_URL` | `http://localhost:8083` | Payment service base URL |
| `NOTIFICATION_SERVICE_URL` | `http://localhost:8084` | Notification service base URL |

### Booking Service

| Variable | Default | Description |
|----------|---------|-------------|
| `SERVER_PORT` | `8081` | HTTP listen port |
| `BOOKING_DB_URL` | `jdbc:postgresql://localhost:5433/smartbus_booking` | PostgreSQL JDBC URL |
| `BOOKING_DB_USERNAME` | `smartbus` | PostgreSQL username |
| `BOOKING_DB_PASSWORD` | `smartbus` | PostgreSQL password |
| `KAFKA_BOOTSTRAP_SERVERS` | `localhost:9092` | Kafka broker address |
| `BOOKING_CONFIRMED_TOPIC` | `smartbus.booking.confirmed.v1` | Topic for confirmed booking events |
| `PAYMENT_DECLINED_TOPIC` | `smartbus.payment.declined.v1` | Topic for declined payment events |
| `PAYMENT_DECLINED_CONSUMER_GROUP` | `booking-service-payment-audit` | Kafka consumer group for declined events |
| `SCHEDULE_SERVICE_URL` | `http://localhost:8082` | Schedule partner URL |
| `PAYMENT_SERVICE_URL` | `http://localhost:8083` | Payment partner URL |
| `NOTIFICATION_SERVICE_URL` | `http://localhost:8084` | Notification partner URL |
| `BOOKING_PARTNER_TIMEOUT` | `PT0.25S` | Per-call timeout to partner services |
| `BOOKING_PARTNER_MAX_ATTEMPTS` | `3` | Max retry attempts per partner call |
| `BOOKING_PARTNER_BACKOFF` | `PT0.05S` | Fixed back-off between retries |

### Schedule Service

| Variable | Default | Description |
|----------|---------|-------------|
| `SERVER_PORT` | `8082` | HTTP listen port |
| `SCHEDULE_DB_URL` | `jdbc:postgresql://localhost:5433/smartbus_schedule` | PostgreSQL JDBC URL |
| `SCHEDULE_DB_USERNAME` | `smartbus` | PostgreSQL username |
| `SCHEDULE_DB_PASSWORD` | `smartbus` | PostgreSQL password |
| `SCHEDULE_DATA_CACHE_TTL` | `PT5M` | Caffeine data cache TTL (ISO-8601 duration) |
| `SCHEDULE_OUTPUT_CACHE_TTL` | `PT30S` | Quote output cache TTL |
| `SCHEDULE_SIMULATED_LATENCY` | `PT0.06S` | Artificial datastore delay (set to PT0S in production) |

### Payment Service

| Variable | Default | Description |
|----------|---------|-------------|
| `SERVER_PORT` | `8083` | HTTP listen port |
| `PAYMENT_DB_URL` | `jdbc:postgresql://localhost:5433/smartbus_payment` | PostgreSQL JDBC URL |
| `PAYMENT_DB_USERNAME` | `smartbus` | PostgreSQL username |
| `PAYMENT_DB_PASSWORD` | `smartbus` | PostgreSQL password |
| `KAFKA_BOOTSTRAP_SERVERS` | `localhost:9092` | Kafka broker address |
| `PAYMENT_DECLINED_TOPIC` | `smartbus.payment.declined.v1` | Topic for declined payment events |

### Notification Service

| Variable | Default | Description |
|----------|---------|-------------|
| `SERVER_PORT` | `8084` | HTTP listen port |
| `NOTIFICATION_DB_URL` | `jdbc:postgresql://localhost:5433/smartbus_notification` | PostgreSQL JDBC URL |
| `NOTIFICATION_DB_USERNAME` | `smartbus` | PostgreSQL username |
| `NOTIFICATION_DB_PASSWORD` | `smartbus` | PostgreSQL password |
| `KAFKA_BOOTSTRAP_SERVERS` | `localhost:9092` | Kafka broker address |
| `BOOKING_CONFIRMED_TOPIC` | `smartbus.booking.confirmed.v1` | Topic to consume confirmed bookings |
| `BOOKING_CONSUMER_GROUP` | `notification-service` | Kafka consumer group |
| `SMARTBUS_MONGODB_ENABLED` | `false` | Set `true` to enable optional MongoDB audit sink |
| `MONGODB_URI` | `mongodb://localhost:27017/smartbus_notifications` | MongoDB connection URI (only when enabled) |

## Phase IV Additions

Phase IV extends SmartBus with AI/ML-powered route recommendations, an OWL 2 semantic
knowledge graph, and a bidirectional AI-Ontology integration layer.

> **Final Report:** `docs/Phase_IV_Final_Report.pdf` (16 pages)
> **Architecture Diagram:** `docs/arch_integration_diagram.svg`
> **Ontology Diagram:** `ontology/ontology-diagram.svg`

### New capabilities in Phase IV

- **Hybrid Recommendation Model** — User-User Collaborative Filtering (cosine similarity,
  k=30) + Content-Based Filtering (alpha=0.3) trained on 5,174 synthetic bookings.
  NDCG@3 = 0.405 (+91.6% over random baseline). Cold-start: popularity fallback.
- **Flask ML + Semantic Server** (`ml/server.py`, port 5050) — serves `/api/ml/*`,
  `/api/semantic/*`, and `/api/intelligent/*` endpoints to the Java gateway.
- **OWL 2 Ontology** (`ontology/smartbus-ontology.ttl`) — 10 classes, 17 object properties,
  24 data properties, 385 triples. Includes property chain axiom, symmetric property,
  and 5 inference rules (R1–R5).
- **SPARQL Semantic Layer** — RDFLib-backed knowledge graph (675 triples after inference).
  Zone proximity (R3), candidate routes (R4), and multi-hop SPARQL passthrough.
- **AI-Ontology Bridge** (`ml/semantic/ai_ontology_bridge.py`) — bidirectional integration:
  - Ontology→AI: R4/R1 features boost ML scores (+0.20 origin match, +0.15 frequent route).
  - AI→Ontology: top-n predictions stored as `sb:Recommendation` RDF triples.
- **Per-Route Explanation** — `/api/intelligent/explain` returns ML score breakdown +
  semantic feature contributions + narrative reasoning for any (user, route) pair.
- **Frontend Recommendations UI** — "Recommended for You" card added to `user/buy-ticket.html`
  with confidence badges, score bars, and one-click route pre-fill.

### Phase IV API endpoints (all require JWT Bearer token)

| Method | Path | Description |
|--------|------|-------------|
| GET | `/api/v1/frontend/recommendations` | ML recommendations for current user |
| GET | `/api/v1/frontend/semantic/health` | Knowledge graph stats + validation |
| GET | `/api/v1/frontend/semantic/routes?from=X` | Routes from named stop (Q1) |
| GET | `/api/v1/frontend/semantic/routes/zone?stop=X` | Zone-based routes (Q2 + R3) |
| GET | `/api/v1/frontend/semantic/candidates` | Ontology candidate routes (R4) |
| GET | `/api/v1/frontend/semantic/insights` | R1 + R2 + R4 inference for user |
| GET | `/api/v1/frontend/semantic/find-related?entity=X&relationship=Y` | Entity traversal |
| POST | `/api/v1/frontend/semantic/query` | Raw SPARQL SELECT passthrough |
| GET | `/api/v1/frontend/intelligent/recommend` | AI + Ontology enriched recommendations |
| GET | `/api/v1/frontend/intelligent/explain?route=X` | Per-route reasoning breakdown |

### Phase IV test coverage

| Suite | Tests | Status |
|-------|-------|--------|
| Gateway Java (DecisionEngine + contract) | 8 | all pass |
| Knowledge graph (Task 05) | 25 | all pass |
| Semantic service (Task 06) | 32 | all pass |
| AI-Ontology bridge (Task 07) | 30 | all pass |
| **Total** | **95** | **all pass** |

### Phase IV setup (additional prerequisites)

- Python 3.14+ (3.11+ minimum)
- Create virtual environment and install dependencies:

```sh
cd ml
python3 -m venv venv
source venv/bin/activate      # Windows: venv\Scripts\activate
pip install -r requirements.txt
```

- Train the ML model (one-time):

```sh
python generate_data.py       # generate synthetic dataset
python preprocessing.py       # build feature matrices
python model_training.py      # train and save model (ml/model/)
```

- Start the Flask ML + Semantic server:

```sh
python server.py              # starts on port 5050
```

- Set the ML server URL when starting the gateway:

```sh
mvn -pl backend/gateway spring-boot:run \
  -Dspring-boot.run.arguments=--ML_SERVER_URL=http://localhost:5050
```

- Run Python tests:

```sh
python -m pytest ml/semantic/ -v
```

Default ML server port: `5050` (override with `ML_SERVER_URL` env var on the gateway).

### Phase IV folder map

| Path | Purpose |
|------|---------|
| `ml/generate_data.py` | Synthetic dataset generator (300 users, 10 routes, 5,174 bookings) |
| `ml/preprocessing.py` | Feature engineering pipeline (interactions, route features, user profiles) |
| `ml/model_training.py` | SmartBusRecommender: CF + CB hybrid, grid search, evaluation |
| `ml/inference.py` | SmartBusInference: production wrapper with email lookup and cold-start |
| `ml/server.py` | Flask server: all ML, semantic, and intelligent endpoints |
| `ml/semantic/knowledge_graph.py` | RDFLib KG: load ontology + data, apply inference, SPARQL |
| `ml/semantic/semantic_service.py` | SemanticService: high-level query operations |
| `ml/semantic/ai_ontology_bridge.py` | AISemanticBridge: bidirectional AI-Ontology integration |
| `ml/semantic/validator.py` | OntologyValidator: domain, range, functional property checks |
| `ml/data/raw/` | Raw synthetic CSV datasets |
| `ml/data/processed/` | Preprocessed feature matrices |
| `ml/model/` | Serialised model artifacts (joblib + JSON metadata) |
| `ontology/smartbus-ontology.ttl` | OWL 2 TBox + ABox named individuals (385 triples) |
| `ontology/sample-data.ttl` | ABox instance data: users, bookings, payments, recs (284 triples) |
| `ontology/ontology-diagram.svg` | OWL class relationship diagram |
| `ontology/README.md` | Ontology documentation: entities, properties, SPARQL examples |
| `backend/gateway/.../semantic/` | Java proxies: SemanticService, SemanticController, |
| | EnrichedDecisionEngine, IntelligentRecommendationController |
| `backend/gateway/.../recommendation/` | DecisionEngine, RecommendationService, RecommendationController |
| `docs/Phase_IV_Final_Report.pdf` | 16-page comprehensive Phase IV report |
| `docs/arch_integration_diagram.svg` | System architecture + AI-Ontology layer diagram |
| `docs/phase4-planning.md` | Phase IV design decisions |
| `docs/phase4-ontology-design.md` | Detailed ontology design document |
| `task/phase_4_tasks/` | Phase IV task breakdown (Tasks 00–10) |

## Submission References

- Phase II architecture report: `docs/report/SmartBus-Integration-Architecture.md` / `.pdf`
- Phase III architecture report: `docs/report/SmartBus-Phase3-Architecture.md` / `.pdf`
- Submission checklist: `docs/submission-checklist.md`

## Phase III Additions

### Components added in Phase III

- **Flyway migrations** — `src/main/resources/db/migration/` in every service provides
  versioned schema lifecycle management (`V1__initial_schema.sql`, `V2__seed_data.sql`).
- **Full CRUD REST** — PUT/PATCH and DELETE endpoints for bookings, routes, locations,
  payment records, and notification deliveries. All list endpoints support `?page=&size=` pagination.
- **Data access layer** — separated controller / service / repository layers with Spring Data JPA
  across all five services. Booking admin list is paginated at the database level.
- **Data transformation** — `Accept: application/xml` content negotiation on schedule catalog
  and quote endpoints (Jackson XML); booking summary aggregation at
  `GET /api/v1/gateway/booking-summary/{bookingReference}`; CSV export at
  `GET /api/v1/bookings/admin/bookings.csv`.
- **Caching** — Caffeine-backed Spring Cache with four named caches (`routeCatalog`,
  `routeDefinition`, `locationCatalog`, `locationById`) plus an output quote cache.
  `@CacheEvict` fires on every admin mutation. Actuator `caches` and `metrics` endpoints expose
  real-time hit/miss statistics.
- **Security** — JWT HS256 auth (JJWT 0.12.7) with BCrypt password hashing, `@Valid` Bean
  Validation on all DTOs, `HtmlSanitizer` XSS protection on all free-text fields.
- **Asynchronous processing** — two Kafka topics:
  - `smartbus.booking.confirmed.v1` — booking-service → notification-service
  - `smartbus.payment.declined.v1` — payment-service → booking-service (audit)
  - Durable `kafka-data` Docker volume; `auto.offset.reset=earliest` replay on restart.
- **Logging and monitoring** — `X-Request-Id` correlation header generated at gateway,
  propagated via `RestClient` interceptor to all microservices, stored in MDC, included in
  every log line via structured Logback pattern. Actuator `metrics` exposed on all five services.
- **MongoDB (optional)** — notification-service can mirror delivery events to MongoDB 7 when
  `SMARTBUS_MONGODB_ENABLED=true`. Disabled by default; no MongoDB connection is opened
  unless the flag is set.

### Phase III folder map

| Path | Purpose |
|------|---------|
| `task/phase3_tasks/` | Phase III task breakdown |
| `docs/api-design.md` | Full endpoint table with status codes and schemas |
| `docs/async-processing.md` | Kafka topics, producer/consumer design, offline recovery |
| `docs/cache-strategy.md` | Cache layers, TTL/invalidation policy, Actuator endpoints |
| `docs/cache-performance.md` | Before/after benchmark results |
| `docs/data-transformation.md` | JSON↔XML, JSON aggregation, CSV export |
| `docs/database-design.md` | ER summary, index choices, MongoDB design |
| `docs/logging-monitoring.md` | MDC correlation, log format, Actuator metrics |
| `docs/security-design.md` | Auth flow, protected endpoints, XSS/SQLi protection |
| `docs/report/SmartBus-Phase3-Architecture.md` | Phase III architecture document (source) |
| `docs/report/SmartBus-Phase3-Architecture.pdf` | Phase III submission artifact |
| `contracts/messages/` | Versioned Kafka message JSON schemas |
| `backend/services/*/src/main/resources/db/migration/` | Flyway migration scripts per service |
