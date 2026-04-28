# SmartBus Phase IV Gap Analysis

Audit date: 2026-04-25

This document evaluates each Phase IV requirement against the current Phase III codebase,
records implementation status, and identifies exactly what must be built.

---

## Phase IV Requirements Status

| # | Requirement | Status | Gap |
|---|-------------|--------|-----|
| 1 | AI/ML Integration | MISSING | No ML model, no training pipeline, no inference endpoint |
| 2 | Intelligent Decision-Making Layer | MISSING | No prediction-driven logic or recommendations |
| 3 | Semantic Data Modeling (Ontology) | MISSING | No RDF/OWL/JSON-LD definitions |
| 4 | Semantic Query and Reasoning | MISSING | No SPARQL queries or inference engine |
| 5 | AI + Ontology Integration | MISSING | No bridge between ML output and semantic layer |
| 6 | Modern Tools (Scikit-learn / RDFLib) | MISSING | Python ML environment not set up |
| 7 | System Demonstration | MISSING | No ML/semantic UI elements in frontend |

---

## Detailed Gap Analysis by Requirement

---

### 1. AI / Machine Learning Integration

**Status: MISSING**

#### What exists
- `booking_process_instances` table in PostgreSQL contains bookings with
  `customer_email`, `from_stop`, `to_stop`, `total_amount`, `created_at`.
- `schedule_routes` table has `route_code`, `unit_price`, `seats_available`, departure/arrival times.
- No ML model, no training code, no Python environment.

#### What is missing
- Python 3.11 environment with Scikit-learn, Pandas, NumPy, Joblib
- Synthetic dataset generator (`ml/generate_data.py`)
- Preprocessing pipeline (`ml/preprocessing.py`)
- Model training script (`ml/model_training.py`)
- Trained model artifact (`ml/model/recommendation_model.joblib`)
- Flask prediction API server (`ml/server.py`)
- `requirements.txt` for reproducible environment setup

#### Plan
→ Task 01: dataset generation and preprocessing
→ Task 02: model training and evaluation
→ Task 03: Flask API and Java integration

---

### 2. Intelligent Decision-Making Layer

**Status: MISSING**

#### What exists
- `user/buy-ticket.html` frontend page for booking (no recommendations shown)
- `admin/reports.html` page exists but is a placeholder with no real data

#### What is missing
- Recommendation endpoint in Java backend (`/api/v1/recommendations/{email}`)
- `recommendations` table to persist ML prediction results
- `RecommendationController` and `RecommendationService` in gateway or booking-service
- Frontend component displaying top-3 recommended routes on buy-ticket page
- Decision logic: demote full routes, boost user's preferred origin

#### Plan
→ Task 03: AI integration with Java backend and frontend display

---

### 3. Semantic Data Modeling (Ontology Layer)

**Status: MISSING**

#### What exists
- Domain entities are well-defined in Java (JPA entities in all 5 services)
- Entity relationships are implicit in foreign key structure
- No RDF/OWL/JSON-LD representation of any kind

#### What is missing
- `ontology/` folder (created in this task)
- `ontology/smartbus-ontology.ttl` — Turtle OWL ontology defining all classes and properties
- `ontology/sample-data.ttl` — RDF triples representing SmartBus instance data
- `ontology/ontology-diagram.png` — visual entity-relationship diagram
- At least 10 classes, 12 object properties, 50+ instance triples

#### Core entities to define (10)
`sb:User`, `sb:Route`, `sb:Stop`, `sb:Bus`, `sb:Booking`,
`sb:Payment`, `sb:Schedule`, `sb:Recommendation`, `sb:PriceTier`, `sb:RouteZone`

#### Core relationships to define (12+)
`hasBooking`, `onRoute`, `paidWith`, `departsFrom`, `arrivesAt`,
`operatedBy`, `hasSchedule`, `hasPriceTier`, `inZone`, `prefersOrigin`,
`forUser`, `suggestsRoute`

#### Plan
→ Task 04: ontology design and diagram
→ Task 05: Turtle file implementation and RDFLib loader

---

### 4. Semantic Query and Reasoning

**Status: MISSING**

#### What exists
- No SPARQL queries, no semantic reasoning
- No Python RDFLib integration
- No Java semantic query layer

#### What is missing
- SPARQL query capability via RDFLib (Python)
- Semantic query endpoint (`/api/semantic/find-routes?origin=X`)
- Inference rules implemented in Python or via SPARQL CONSTRUCT queries
- Query examples:
  - "Find all routes departing from stop X"
  - "Find routes preferred by users in zone Y"
  - "Find users whose origin matches route departure"

#### Plan
→ Task 06: semantic querying and reasoning engine (Python + Flask)

---

### 5. AI + Ontology Integration

**Status: MISSING**

#### What exists
- Nothing bridges ML output with semantic data at this stage

#### What is missing
- Python bridge that converts recommendation output to RDF triples
- Java `AISemanticBridge` service that stores predictions as semantic facts
- Ontology-enriched feature set for ML (origin zone, preferred stops)
- Bidirectional data flow: ML→Ontology and Ontology→ML

#### Plan
→ Task 07: AI-ontology integration layer (core Phase IV requirement)

---

### 6. Modern Tools and Technologies

**Status: MISSING**

#### What exists
- Java 25 / Spring Boot 4.0.3 stack (Phase III)
- No Python ML environment

#### What is missing
- `ml/requirements.txt` with pinned versions:
  - `scikit-learn==1.4.x`
  - `pandas==2.2.x`
  - `numpy==1.26.x`
  - `flask==3.0.x`
  - `rdflib==7.0.x`
  - `joblib==1.3.x`
- Python virtual environment setup instructions in README

#### Plan
→ Task 01 creates `requirements.txt` and environment setup
→ Task 05 adds RDFLib usage

---

### 7. System Demonstration

**Status: MISSING**

#### What exists
- `admin/reports.html` — placeholder reporting page (no real data)
- `user/buy-ticket.html` — ticket purchase page (no recommendations)

#### What is missing
- Recommendation cards on `user/buy-ticket.html`
- Semantic query results display (admin or user facing)
- ML model prediction output visible in UI
- Demo screenshots for documentation and presentation

#### Plan
→ Task 03: frontend ML display
→ Task 06: semantic query results in UI (optional)
→ Task 08/10: documentation screenshots and presentation demo

---

## Phase III Baseline Verification

Verified working before Phase IV work begins:

| Check | Result |
|-------|--------|
| `mvn clean test` | PASS — BUILD SUCCESS |
| `docker compose -f infra/docker-compose.yml config` | VALID |
| All 5 services start | VERIFIED (Phase III) |
| JWT auth endpoints functional | VERIFIED |
| Kafka topics operational | VERIFIED |
| Flyway migrations clean | VERIFIED |

No Phase III code was modified during Task 00. All changes are additive:
- New `docs/` planning files
- New empty `ml/`, `ontology/`, `backend/semantic/` directories

---

## Implementation Priority

1. **Task 01 + 02**: ML foundation must exist before any integration can start.
2. **Task 03**: Frontend display depends on a working prediction API.
3. **Task 04 + 05**: Ontology implementation can run in parallel with Task 03.
4. **Task 06**: Semantic querying depends on Task 05 (ontology must be defined first).
5. **Task 07**: AI-Ontology integration requires both Task 03 and Task 06 complete.
6. **Task 08 + 09 + 10**: Documentation, review, and presentation close out.
