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

## Submission References

- Architecture report source: `docs/report/SmartBus-Integration-Architecture.md`
- Architecture report PDF: `docs/report/SmartBus-Integration-Architecture.pdf`
- Submission checklist: `docs/submission-checklist.md`

## Next Steps

This foundation establishes the repository structure required by phase 2. The next implementation tasks can add:

- orchestration flows in `orchestration/`
- asynchronous messaging
- correlation state
- caching
- service contracts in `contracts/`
- fault-handling policies
