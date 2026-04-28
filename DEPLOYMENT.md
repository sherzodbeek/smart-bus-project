# SmartBus — Deployment Guide

Complete step-by-step instructions for running the full SmartBus system locally,
including all Phase IV AI/ML and semantic components.

---

## Table of Contents

1. [Prerequisites](#1-prerequisites)
2. [Quick Start](#2-quick-start)
3. [Phase IV ML + Semantic Setup](#3-phase-iv-ml--semantic-setup)
4. [Java Services Setup](#4-java-services-setup)
5. [Frontend Setup](#5-frontend-setup)
6. [Environment Variables Reference](#6-environment-variables-reference)
7. [Running Tests](#7-running-tests)
8. [Validation Checklist](#8-validation-checklist)
9. [Troubleshooting](#9-troubleshooting)
10. [Docker Compose (Infrastructure Only)](#10-docker-compose-infrastructure-only)

---

## 1. Prerequisites

| Tool | Version | Install |
|------|---------|---------|
| Java | 25 (JDK) | https://adoptium.net/ |
| Maven | 3.9+ | https://maven.apache.org/ |
| Python | 3.11+ (3.14 recommended) | https://python.org/ |
| Docker Desktop | 4.x+ | https://docker.com/ |
| Node.js + npm | 18+ | https://nodejs.org/ |
| curl | any | (pre-installed on most systems) |

Verify:

```sh
java --version      # should print 25.x
mvn --version       # should print 3.9.x
python3 --version   # should print 3.11+
docker --version
node --version
```

---

## 2. Quick Start

For experienced users who want everything running in one pass:

```sh
# 1. Clone and enter project
git clone <repo-url> && cd project

# 2. Start infrastructure
docker compose -f infra/docker-compose.yml up -d

# 3. Set up Python ML environment
cd ml && python3 -m venv venv && source venv/bin/activate
pip install -r requirements.txt
python generate_data.py && python preprocessing.py && python model_training.py
python server.py &          # starts Flask on :5050
cd ..

# 4. Start Java services (6 terminals or use & for background)
mvn -pl backend/services/schedule-service    spring-boot:run &
mvn -pl backend/services/payment-service     spring-boot:run &
mvn -pl backend/services/notification-service spring-boot:run &
mvn -pl backend/services/booking-service     spring-boot:run &
mvn -pl backend/gateway spring-boot:run \
  -Dspring-boot.run.arguments=--ML_SERVER_URL=http://localhost:5050 &

# 5. Open frontend
open frontend/user/index.html   # or open in browser directly
```

---

## 3. Phase IV ML + Semantic Setup

This is the new Phase IV component. All steps are **one-time** unless the model needs retraining.

### Step 3a — Create Python virtual environment

```sh
cd ml
python3 -m venv venv
```

Activate it:
- **macOS / Linux:** `source venv/bin/activate`
- **Windows:** `venv\Scripts\activate`

### Step 3b — Install Python dependencies

```sh
pip install -r requirements.txt
```

Dependencies installed:
- `scikit-learn >= 1.4.0` — ML model
- `pandas >= 2.2.0` — data processing
- `numpy >= 1.26.0` — numerical operations
- `flask >= 3.0.0` — REST server
- `rdflib >= 7.0.0` — OWL/SPARQL knowledge graph
- `joblib >= 1.3.0` — model serialisation
- `matplotlib >= 3.8.0` — training plots
- `pytest >= 8.0.0` — test runner

### Step 3c — Generate the synthetic dataset

```sh
python generate_data.py
```

Creates:
- `ml/data/raw/users.csv` — 300 synthetic users with travel profiles
- `ml/data/raw/routes.csv` — 10 routes with stops and pricing
- `ml/data/raw/bookings.csv` — 5,174 bookings with realistic behavioural patterns

### Step 3d — Preprocess features

```sh
python preprocessing.py
```

Creates:
- `ml/data/processed/interactions.csv` — 1,507 user-route interaction pairs
- `ml/data/processed/route_features.csv` — 21-dimensional route feature vectors
- `ml/data/processed/user_profiles.csv` — user profile with frequent origin
- `ml/data/processed/train.csv` / `test.csv` — per-user temporal split

### Step 3e — Train the ML model

```sh
python model_training.py
```

Creates (takes 10–60s depending on machine):
- `ml/model/smartbus_recommender.joblib` — trained hybrid CF+CB model
- `ml/model/model_metadata.json` — hyperparameters and evaluation metrics
- `ml/model/grid_search_results.json` — full 30-combination grid search log

Expected output includes:
```
Best: k=30  alpha=0.3  NDCG@3=0.4051
```

### Step 3f — Start the Flask ML + Semantic server

```sh
python server.py
```

The server starts on port 5050 (override with `ML_SERVER_PORT` env var).
Startup loads:
1. The trained ML model (~400ms)
2. The OWL 2 knowledge graph from `ontology/` (~140ms)
3. The AI-Ontology bridge

Health check:
```sh
curl http://localhost:5050/api/ml/health
```

Expected response:
```json
{"status": "UP", "model_version": "1.0.0", "n_users": 288, "n_routes": 10}
```

Semantic health check:
```sh
curl http://localhost:5050/api/semantic/health
```

---

## 4. Java Services Setup

### Step 4a — Start infrastructure

```sh
docker compose -f infra/docker-compose.yml up -d
```

Starts:
- PostgreSQL 17 on port 5433
- Apache Kafka 3.9 on port 9092
- MongoDB 7 on port 27017 (optional, for notification audit)

Wait for PostgreSQL to be healthy:
```sh
docker compose -f infra/docker-compose.yml ps
```

### Step 4b — Build and verify backend

```sh
mvn clean test
```

All 8 gateway tests should pass. The gateway test suite validates:
- Spring Boot application context loads
- `DecisionEngine` logic (6 unit tests)
- All OpenAPI contracts are valid

### Step 4c — Start backend services

Start in separate terminals (or background with `&`).

**Order matters:** schedule and payment must be up before booking.

```sh
# Terminal 1 — Schedule service
mvn -pl backend/services/schedule-service spring-boot:run

# Terminal 2 — Payment service
mvn -pl backend/services/payment-service spring-boot:run

# Terminal 3 — Notification service
mvn -pl backend/services/notification-service spring-boot:run

# Terminal 4 — Booking service
mvn -pl backend/services/booking-service spring-boot:run

# Terminal 5 — Gateway (with ML server URL)
mvn -pl backend/gateway spring-boot:run \
  -Dspring-boot.run.arguments=--ML_SERVER_URL=http://localhost:5050
```

Verify gateway is up:
```sh
curl http://localhost:8080/actuator/health
```

### Step 4d — Verify Phase IV integration

Register a test user and confirm intelligent recommendations work:

```sh
# Register
curl -s -X POST http://localhost:8080/api/v1/auth/register \
  -H 'Content-Type: application/json' \
  -d '{"email":"test@smartbus.test","password":"Test1234!","fullName":"Test User"}' | jq .

# Login
TOKEN=$(curl -s -X POST http://localhost:8080/api/v1/auth/login \
  -H 'Content-Type: application/json' \
  -d '{"email":"test@smartbus.test","password":"Test1234!"}' | jq -r .token)

# ML recommendations (cold-start — popularity fallback)
curl -s -H "Authorization: Bearer $TOKEN" \
  http://localhost:8080/api/v1/frontend/recommendations | jq .

# Semantic candidate routes (empty for new user)
curl -s -H "Authorization: Bearer $TOKEN" \
  http://localhost:8080/api/v1/frontend/semantic/candidates | jq .

# Zone-based routes (ontology query — always available)
curl -s -H "Authorization: Bearer $TOKEN" \
  "http://localhost:8080/api/v1/frontend/semantic/routes?from=Downtown+Terminal" | jq .

# AI+Ontology intelligent recommendation
curl -s -H "Authorization: Bearer $TOKEN" \
  http://localhost:8080/api/v1/frontend/intelligent/recommend | jq .
```

---

## 5. Frontend Setup

The frontend is a static SPA served directly from files.

```sh
cd frontend
npm install
npm run start -- --port 8088
```

Open: http://localhost:8088/user/index.html

The frontend talks to the gateway at `http://localhost:8080` (hardcoded base URL).

> **Note:** The "Recommended for You" card in `user/buy-ticket.html` appears automatically
> after login. It calls `/api/v1/frontend/recommendations` and shows the top-3 ML routes.
> The card is hidden if the ML server is unavailable (graceful degradation).

---

## 6. Environment Variables Reference

### Gateway (`:8080`)

| Variable | Default | Description |
|----------|---------|-------------|
| `SERVER_PORT` | `8080` | HTTP listen port |
| `GATEWAY_DB_URL` | `jdbc:postgresql://localhost:5433/smartbus_booking` | PostgreSQL URL |
| `GATEWAY_DB_USERNAME` | `smartbus` | DB username |
| `GATEWAY_DB_PASSWORD` | `smartbus` | DB password |
| `SMARTBUS_JWT_SECRET` | `smartbus-smartbus-...-secret-key-2026` | HS256 key — **change in production** |
| `SMARTBUS_JWT_EXPIRATION` | `PT8H` | Token TTL |
| `ML_SERVER_URL` | `http://localhost:5050` | Flask ML+Semantic server URL |

### Flask ML Server

| Variable | Default | Description |
|----------|---------|-------------|
| `ML_SERVER_PORT` | `5050` | Flask listen port |

### Other services

See `README.md` → Environment Variables section for all five microservice configs.

---

## 7. Running Tests

### Python tests (Phase IV)

```sh
cd /path/to/project
source ml/venv/bin/activate

# All semantic tests (87 tests)
python -m pytest ml/semantic/ -v

# Individual suites
python -m pytest ml/semantic/test_knowledge_graph.py -v    # 25 tests
python -m pytest ml/semantic/test_semantic_service.py -v   # 32 tests
python -m pytest ml/semantic/test_ai_ontology_bridge.py -v # 30 tests
```

### Java tests

```sh
# Gateway only (fast, 8 tests)
mvn test -pl backend/gateway

# All backend services
mvn clean test
```

### Expected output

```
ml/semantic/: 87 passed in ~2s
backend/gateway: Tests run: 8, Failures: 0  BUILD SUCCESS
```

---

## 8. Validation Checklist

Run through this checklist after a clean setup to confirm all Phase IV features work:

- [ ] `curl http://localhost:5050/api/ml/health` returns `{"status":"UP"}`
- [ ] `curl http://localhost:5050/api/semantic/health` returns `{"ontology_valid":true}`
- [ ] `curl "http://localhost:5050/api/semantic/routes?from=Downtown+Terminal"` returns 3 routes
- [ ] Gateway starts without errors (`http://localhost:8080/actuator/health` = UP)
- [ ] Login returns a JWT token
- [ ] `GET /api/v1/frontend/recommendations` returns recommendations (or empty list if cold-start)
- [ ] `GET /api/v1/frontend/semantic/routes?from=Downtown+Terminal` returns routes
- [ ] `GET /api/v1/frontend/intelligent/recommend` returns `enrichment_stats.ontology_contributed`
- [ ] `GET /api/v1/frontend/intelligent/explain?route=SB-101` returns `contributions` breakdown
- [ ] `POST /api/v1/frontend/semantic/query` with valid SPARQL returns rows
- [ ] `POST /api/v1/frontend/semantic/query` with INSERT SPARQL returns `{"error":"..."}`
- [ ] All Python tests pass: `python -m pytest ml/semantic/ -q` → 87 passed
- [ ] All Java tests pass: `mvn test -pl backend/gateway` → BUILD SUCCESS

---

## 9. Troubleshooting

### Flask server fails to start: "Model artifact not found"

**Cause:** The ML model has not been trained yet.  
**Fix:** Run the training pipeline (Section 3c–3e) before starting `server.py`.

### Gateway logs: "recommendationsMlCallFailed"

**Cause:** Flask server is not running or wrong URL.  
**Fix:**
1. Check `http://localhost:5050/api/ml/health`
2. Verify `ML_SERVER_URL=http://localhost:5050` is passed to the gateway

The gateway degrades gracefully — recommendations endpoint returns `[]` instead of failing.

### joblib pickle error: "has no attribute 'SmartBusRecommender'"

**Cause:** The module context differs when loading the model outside `model_training.py`.  
**Fix:** The server.py already contains the fix:
```python
import __main__
from model_training import SmartBusRecommender
__main__.SmartBusRecommender = SmartBusRecommender
```
If running inference manually, do the same before `joblib.load()`.

### RDFLib: "Not enough horizontal space" or similar

**Cause:** This is an fpdf2 issue in the report generator, not in the application.  
The application itself has no fpdf dependency.

### SPARQL query returns unexpected results

**Cause:** The KG is in-memory; inference rules are applied on load.  
Check which triples are present:
```sh
curl "http://localhost:5050/api/semantic/health"
# Look at total_triples; should be ~675
```

If low (e.g., 385), the sample-data.ttl did not load. Check Flask server startup logs.

### PostgreSQL: "relation does not exist" at gateway startup

**Cause:** Flyway migration V3 (gateway_recommendations table) may not have run.  
**Fix:** Restart the gateway — Flyway runs automatically on startup.

### Tests fail with "ModuleNotFoundError: No module named 'semantic'"

**Cause:** Running pytest from the wrong directory.  
**Fix:** Run from project root (not from `ml/`):
```sh
cd /path/to/project
source ml/venv/bin/activate
python -m pytest ml/semantic/ -v
```

---

## 10. Docker Compose (Infrastructure Only)

The `infra/docker-compose.yml` provides PostgreSQL, Kafka, and MongoDB.
The ML Flask server is not containerised by default but a commented template is provided.

```sh
# Start infrastructure
docker compose -f infra/docker-compose.yml up -d

# Stop all
docker compose -f infra/docker-compose.yml down

# Reset all data (destructive)
docker compose -f infra/docker-compose.yml down -v

# Check service health
docker compose -f infra/docker-compose.yml ps
```

### To containerise the ML server (optional)

1. Create `ml/Dockerfile`:

```dockerfile
FROM python:3.14-slim
WORKDIR /app
COPY requirements.txt .
RUN pip install --no-cache-dir -r requirements.txt
COPY . .
EXPOSE 5050
CMD ["python", "server.py"]
```

2. Pre-train the model locally and copy `ml/model/` into the container, or mount it as a volume.

3. Un-comment the `ml-server` block in `infra/docker-compose.yml`.

4. Set `ML_SERVER_URL=http://ml-server:5050` on the gateway when running in Docker.

---

*Document version: 1.0  —  SmartBus Phase IV  —  2026-04-26*
