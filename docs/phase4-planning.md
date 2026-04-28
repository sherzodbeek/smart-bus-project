# SmartBus Phase IV — Implementation Planning

Audit date: 2026-04-25

This document defines the complete AI/ML and semantic ontology integration plan for Phase IV.
It covers the chosen ML approach, dataset plan, ontology design, technology selections,
and the task-by-task implementation roadmap.

---

## 1. Phase IV Objectives

Phase IV transforms SmartBus from a data-integration platform into an **intelligent,
knowledge-aware booking system** by adding:

1. A trained ML model that recommends the best routes for each user based on historical
   booking behavior and route characteristics.
2. A semantic knowledge graph (ontology) that formally represents SmartBus domain concepts
   and their relationships using RDF triples.
3. An integration layer that feeds ontology knowledge into the ML feature set and stores
   ML prediction results back as semantic triples — closing the loop between learning and
   reasoning.

---

## 2. AI/ML Approach

### 2.1 Selected Model: Route Recommendation System

**Type:** Hybrid Recommendation System (collaborative filtering + content-based filtering)

**Use case:** When a user opens the "Buy Ticket" page, the system recommends the three
most relevant routes for that user based on:
- Their personal booking history (collaborative signal)
- Route attributes that match their typical travel profile (content signal)
- Semantic enrichment from ontology (e.g., preferred departure time, frequent origin stop)

**Why this model fits SmartBus:**

| Criterion | Justification |
|-----------|--------------|
| Natural fit | Users repeatedly book the same commute routes. Collaborative filtering captures this naturally. |
| Available data | `booking_process_instances` contains customer email, origin, destination, fare, and date for every completed booking. |
| Ontology synergy | The route recommendation directly benefits from semantic relationships (stop → location → zone, route → schedule → peak hours). |
| Academic coverage | Satisfies the "recommendation system" ML category from Phase IV requirements. |
| Deliverable clarity | Prediction output (ranked route list) is displayable in the existing `user/buy-ticket.html` page with minimal UI work. |

**Alternative considered — Demand Forecasting (Regression):**
Demand forecasting (predict seat occupancy by route/date) was evaluated but requires time-series
data that does not naturally exist in the current Phase III schema. Recommendation requires only
the user-booking history which is already persisted.

### 2.2 Algorithm

- **Step 1 — Collaborative filtering (user-user):** Build a user × route interaction matrix
  from booking history. Use cosine similarity to find users with similar travel patterns.
  Recommend routes popular with similar users.
- **Step 2 — Content-based features:** Represent each route as a feature vector
  (origin, destination, departure hour, price tier, average duration, availability).
  Score candidate routes by similarity to the user's past preferences.
- **Step 3 — Hybrid score:** Weighted combination of collaborative and content-based scores.
  Default weights: 60% collaborative, 40% content-based.
- **Step 4 — Ontology reranking:** Apply semantic rules to demote routes with unavailable
  seats, and boost routes whose origin matches the user's "home stop" in the knowledge graph.

**Framework:** Python 3.11 + Scikit-learn (cosine similarity, TF-IDF for content features).

### 2.3 Prediction Output

```json
{
  "customerEmail": "alice@example.com",
  "recommendations": [
    { "routeCode": "SB-101", "score": 0.91, "reason": "collaborative_match" },
    { "routeCode": "SB-202", "score": 0.74, "reason": "content_match" },
    { "routeCode": "SB-303", "score": 0.61, "reason": "ontology_boost" }
  ],
  "modelVersion": "1.0.0",
  "generatedAt": "2026-04-25T10:00:00Z"
}
```

---

## 3. Dataset Plan

### 3.1 Source

The dataset will be **generated/simulated** from the SmartBus domain schema because the
system is new and has minimal real production traffic. A Python generator script will produce
a realistic synthetic dataset matching exactly the schema already in PostgreSQL.

### 3.2 Dataset Composition

| Table / Entity | Rows | Key Fields Used |
|----------------|------|-----------------|
| `users` (synthetic) | 500 | id, email, registration_date, preferred_origin |
| `booking_process_instances` (synthetic) | 5,000 | customer_email, from_stop, to_stop, route_code, created_at, total_amount, current_state |
| `schedule_routes` (real seed) | 3+ | route_code, from_location, to_location, departure_time, unit_price, seats_available |
| `schedule_locations` (real seed) | 6+ | id, name |

**Target ML features (per user-route interaction):**

- `user_id` (hashed email)
- `route_code`
- `booking_count` (number of times user booked this route — implicit rating)
- `route_origin_match` (1 if route origin matches user's most frequent origin)
- `route_price_tier` (low/medium/high relative to user's average spend)
- `route_departure_hour` (hour bucket: 06, 09, 12, 15, 18, 21)
- `seats_available` (from schedule)
- `is_repeat_route` (binary: booked at least once before)

### 3.3 Preprocessing Steps

1. Load booking records → compute user-route interaction counts
2. Normalize booking counts to implicit ratings (0.0 – 1.0 scale using MinMaxScaler)
3. One-hot encode route origin/destination
4. Engineer `departure_hour_bucket` from departure time
5. Train/test split: 80/20 stratified by user
6. Export to `ml/data/processed/interactions.csv` and `ml/data/processed/route_features.csv`

**Size:** ~5,000 booking records → ~2,500 unique user-route pairs after aggregation.

---

## 4. Ontology Design

### 4.1 Purpose

The ontology provides SmartBus with a **formal knowledge representation** that:
- Enables semantic querying ("find all routes connecting stops in the northern zone")
- Provides context-aware features to the ML model
- Receives prediction results as inferred triples ("user X is predicted to prefer route Y")

### 4.2 Core Entities (Classes)

| Entity | Description |
|--------|-------------|
| `sb:User` | A registered SmartBus passenger |
| `sb:Route` | A named bus route between two stops |
| `sb:Stop` | A named bus stop / terminal location |
| `sb:Bus` | A vehicle (plate number, capacity, model) |
| `sb:Booking` | A completed ticket reservation |
| `sb:Payment` | A payment transaction |
| `sb:Schedule` | A time-bound run of a route (departure, arrival) |
| `sb:Recommendation` | An ML-generated route recommendation for a user |
| `sb:PriceTier` | Categorical fare level (LOW, MEDIUM, HIGH) |
| `sb:RouteZone` | A geographic zone grouping nearby stops |

### 4.3 Key Relationships (Object Properties)

| Triple Pattern | Meaning |
|----------------|---------|
| `sb:User sb:hasBooking sb:Booking` | User created a booking |
| `sb:Booking sb:onRoute sb:Route` | Booking is for a specific route |
| `sb:Booking sb:paidWith sb:Payment` | Booking was paid by a transaction |
| `sb:Route sb:departsFrom sb:Stop` | Route origin stop |
| `sb:Route sb:arrivesAt sb:Stop` | Route destination stop |
| `sb:Route sb:operatedBy sb:Bus` | Bus vehicle assigned to route |
| `sb:Route sb:hasSchedule sb:Schedule` | Time schedule for a route run |
| `sb:Route sb:hasPriceTier sb:PriceTier` | Fare tier classification |
| `sb:Stop sb:inZone sb:RouteZone` | Stop belongs to a geographic zone |
| `sb:User sb:prefersOrigin sb:Stop` | User's most frequent boarding stop |
| `sb:Recommendation sb:forUser sb:User` | Recommendation targets a user |
| `sb:Recommendation sb:suggestsRoute sb:Route` | Recommended route |

### 4.4 Sample Triples

```turtle
@prefix sb: <http://smartbus.example.org/ontology#> .
@prefix xsd: <http://www.w3.org/2001/XMLSchema#> .

# Route definition
sb:Route_SB101 a sb:Route ;
    sb:routeCode "SB-101" ;
    sb:departsFrom sb:Stop_Downtown ;
    sb:arrivesAt sb:Stop_Airport ;
    sb:hasPriceTier sb:PriceTier_Medium ;
    sb:hasSchedule sb:Schedule_SB101_0800 .

# Schedule
sb:Schedule_SB101_0800 a sb:Schedule ;
    sb:departureTime "08:00:00"^^xsd:time ;
    sb:arrivalTime "08:45:00"^^xsd:time ;
    sb:seatsAvailable "35"^^xsd:integer .

# User
sb:User_alice a sb:User ;
    sb:email "alice@example.com" ;
    sb:prefersOrigin sb:Stop_Downtown .

# Booking
sb:Booking_BK001 a sb:Booking ;
    sb:bookingReference "BK-001" ;
    sb:onRoute sb:Route_SB101 ;
    sb:paidWith sb:Payment_TX001 .

sb:User_alice sb:hasBooking sb:Booking_BK001 .

# ML Recommendation (inferred from model)
sb:Rec_alice_SB101 a sb:Recommendation ;
    sb:forUser sb:User_alice ;
    sb:suggestsRoute sb:Route_SB101 ;
    sb:confidenceScore "0.91"^^xsd:decimal ;
    sb:reasonCode "collaborative_match" .
```

### 4.5 Inference Rules

| Rule | Logic |
|------|-------|
| Route preference | IF user booked route ≥ 3 times THEN assert `sb:prefersRoute` |
| Origin familiarity | IF user.prefersOrigin == route.departsFrom THEN boost score |
| Route availability | IF schedule.seatsAvailable < 5 THEN demote recommendation |
| Zone proximity | IF user.prefersOrigin.inZone == route.departsFrom.inZone THEN suggest route |

---

## 5. Technology Stack

### 5.1 AI/ML Layer

| Component | Technology | Justification |
|-----------|-----------|--------------|
| ML Framework | Python 3.11 + Scikit-learn 1.4 | Standard, well-documented, no GPU needed for recommendation |
| Data processing | Pandas 2.2 + NumPy | Natural for CSV-based feature engineering |
| Model serving | Python Flask 3.0 | Lightweight HTTP API; callable from Java via RestClient |
| Model persistence | joblib | Native Scikit-learn serialization format |
| Notebook/analysis | Jupyter Lab | EDA and model development |

### 5.2 Semantic/Ontology Layer

| Component | Technology | Justification |
|-----------|-----------|--------------|
| Ontology format | Turtle (.ttl) / JSON-LD | Turtle for readability; JSON-LD for API responses |
| RDF library | RDFLib 7.0 (Python) | Pure-Python, no external server needed, SPARQL support |
| Ontology design | Hand-coded + Protégé (validation) | Protégé validates OWL constraints |
| Storage backend | In-memory RDFLib graph (Phase IV) | Simplest approach; can be upgraded to Apache Jena later |

### 5.3 Integration Layer

| Component | Technology | Justification |
|-----------|-----------|--------------|
| ML endpoint | Flask REST API (`/api/ml/recommend`) | Simple HTTP bridge; Java calls via existing RestClient pattern |
| Semantic endpoint | Flask REST API (`/api/semantic/query`) | Co-hosted with ML server for simplicity |
| Java integration | Spring RestClient (already in project) | Reuses existing HTTP client infrastructure |
| Prediction storage | New `recommendations` table in PostgreSQL | Consistent with existing database-per-service pattern |

### 5.4 No New Infrastructure Required

Phase IV does **not** require adding new Docker Compose services for the ML/semantic layer.
The Python Flask server runs as a local process (or an additional Docker container if
needed). The existing PostgreSQL and Kafka infrastructure are sufficient.

---

## 6. Implementation Roadmap

### Task Sequence and Dependencies

```
00_foundation (this task) ──► 01_dataset_preparation
                                      │
                                      ▼
                            02_ml_model_implementation
                                      │
                          ┌───────────┴───────────┐
                          ▼                       ▼
                 03_ai_integration       04_ontology_design
                                                  │
                                                  ▼
                                       05_ontology_implementation
                                                  │
                                                  ▼
                                       06_semantic_querying
                                                  │
                          ┌───────────────────────┘
                          ▼
               07_ai_ontology_integration ◄── (requires 03 + 06)
                          │
              ┌───────────┴───────────┐
              ▼                       ▼
     08_documentation        09_technical_review
              └───────────┬───────────┘
                          ▼
                   10_presentation
```

### Timeline Estimate

| Task | Effort | Key Output |
|------|--------|------------|
| 00 foundation | 1 day | Planning docs, folder structure |
| 01 dataset | 1 day | Synthetic data generator, preprocessed CSV |
| 02 ML model | 2 days | Trained recommendation model, evaluation report |
| 03 AI integration | 2 days | Flask API, Java REST calls, frontend display |
| 04 ontology design | 1 day | Ontology diagram, entity/relationship spec |
| 05 ontology impl | 1 day | Turtle file, RDFLib loader, sample triples |
| 06 semantic querying | 1-2 days | SPARQL endpoint, reasoning rules |
| 07 AI+ontology | 1-2 days | Prediction→triple storage, ontology→ML features |
| 08 documentation | 2 days | 8–12 page PDF |
| 09 review | 1 day | Deployment guide, test sign-off |
| 10 presentation | 1-2 days | 12–18 slides + demo |

---

## 7. Phase III System Baseline

Verified working as of Phase III completion (2026-04-13):

- `mvn clean test` — **BUILD SUCCESS** (all 5 services)
- `docker compose -f infra/docker-compose.yml up -d` — **STARTS CLEANLY**
- All REST endpoints documented in `contracts/openapi/`
- Flyway migrations in all services
- JWT auth, caching, Kafka, XML transforms, logging all operational

No Phase III functionality will be modified in Phase IV. All new code is additive:
- New `ml/` folder (Python, no Maven module)
- New `ontology/` folder (Turtle/JSON-LD files)
- New Java classes under `backend/semantic/` (no changes to existing controllers)
- New endpoints under `/api/v1/recommendations/` and `/api/v1/semantic/`

---

## 8. Risk Register

| Risk | Mitigation |
|------|-----------|
| Python environment not set up | Document venv setup in README; pin all deps in `requirements.txt` |
| Small synthetic dataset reduces model quality | Use cross-validation; report metrics with dataset size context |
| Java ↔ Python HTTP latency | Cache predictions in PostgreSQL; serve cached results to frontend |
| RDF querying complexity | Use RDFLib's built-in SPARQL 1.1 support; avoid complex inference for Phase IV |
| Ontology design scope creep | Limit to 10 entities and 12 relationships for Phase IV |
