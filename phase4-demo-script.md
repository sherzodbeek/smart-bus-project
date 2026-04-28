# SmartBus Phase IV Demo Script
# AI/ML Recommendations + Semantic Knowledge Graph

**Target runtime:** 12–15 minutes  
**Format:** Live demo with terminal + browser, backed by slides for any failure

---

## Demo User Reference

| User | Purpose | ML history | Ontology data |
|------|---------|-----------|---------------|
| `chris.taylor86@example.com` | Show personalised ML scores (Segment 2) | 43 bookings, non-cold-start | none |
| `alice@smartbus.test` | Show ontology data + semantic boost (Segments 3–4) | cold-start | full: prefersOrigin, frequentlyTravels |
| `demo@smartbus.test` | Gateway authenticated path (Segments 5–7) | cold-start (new user) | none |

---

## Before You Start — Setup Checklist

Run each command in a separate terminal tab. Wait for each "Started" or "Running" message before moving on.

```sh
# Tab 1 — Infrastructure (PostgreSQL + Kafka + MongoDB)
docker compose -f infra/docker-compose.yml up -d

# Wait ~10 seconds, verify:
docker ps   # all three containers should say "healthy"
```

```sh
# Tab 2 — Backend booking service
mvn -pl backend/services/booking-service spring-boot:run
```

```sh
# Tab 3 — Gateway (Java Spring Boot, port 8080)
mvn -pl backend/gateway spring-boot:run
```

```sh
# Tab 4 — ML / Knowledge Graph server (Python Flask, port 5050)
cd ml && source venv/bin/activate && python server.py
```

Verify everything before starting the demo:

```sh
# Gateway health (should return "status": "UP")
curl -s http://localhost:8080/actuator/health | python3 -m json.tool

# ML server health (should return "status": "UP", "total_triples": 675)
curl -s http://localhost:5050/api/semantic/health | python3 -m json.tool
```

Get a JWT token for authenticated gateway calls:

```sh
# Register demo user (only needed once)
curl -s -X POST http://localhost:8080/api/v1/auth/register \
  -H 'Content-Type: application/json' \
  -d '{"fullName":"Demo User","email":"demo@smartbus.test","password":"Demo1234!"}' \
  | python3 -m json.tool

# Log in and save token to shell variable
TOKEN=$(curl -s -X POST http://localhost:8080/api/v1/auth/login \
  -H 'Content-Type: application/json' \
  -d '{"email":"demo@smartbus.test","password":"Demo1234!"}' \
  | python3 -c "import sys,json; print(json.load(sys.stdin)['token'])")

echo "Token: ${TOKEN:0:60}..."
```

---

## Demo Flow

---

### Segment 1 — The Problem We Are Solving (2 min)

**What to say:**

> "Phase III gave us a working booking system — search, book, pay, ticket. But every user sees the same list of routes in the same order every time. There is no personalisation. Phase IV adds two new layers on top.
>
> First, a machine-learning model that studies each passenger's booking history and predicts the three routes they are most likely to want next.
>
> Second, a semantic knowledge graph — a structured map of facts about users, routes, stops, and zones — that the model can consult to sharpen those predictions and explain them.
>
> These two systems are independent enough to show separately, but designed to amplify each other when combined."

Show the architecture diagram: `docs/arch_integration_diagram.svg`

> "Everything the browser touches goes through the Java gateway on port 8080. The gateway forwards recommendation requests to the Python ML server on port 5050. The ML server holds both the trained model and the knowledge graph in memory."

---

### Segment 2 — The ML Model: How It Thinks (3 min)

**Plain-language definitions — say these before running commands:**

| Term | Plain English |
|------|--------------|
| **Recommendation system** | Software that predicts "you will probably like this" from past behaviour, not hardcoded rules |
| **Collaborative Filtering (CF)** | "Passengers with similar booking history to yours also booked this route" |
| **Content-Based Filtering (CB)** | "This route matches your usual origin stop and price range" |
| **Hybrid model** | We run both CF and CB and blend the scores — 30% CF, 70% CB |
| **NDCG@3** | Measures how good our top-3 list is, 0→1. Our score: 0.405 — 91% better than a random guess |
| **Cold start** | New user with no history — model falls back to globally popular routes |

**What to say:**

> "The model uses two signals. Collaborative filtering finds the 30 passengers whose booking history is most similar to yours, then recommends what they booked that you haven't. Content-based filtering ignores other passengers entirely and compares each route's features — origin stop, destination, price tier, time of day — against your own travel profile.
>
> We blend them 30% collaborative, 70% content-based. That ratio was found automatically by testing 30 combinations. Content-based is stronger because passengers consistently depart from the same home stop."

Run the raw ML endpoint to show a personalised user — **not** through the gateway, just the model itself:

```sh
curl -s "http://localhost:5050/api/ml/recommend?email=chris.taylor86@example.com&n=3" \
  | python3 -m json.tool
```

Expected response:
```json
{
  "email": "chris.taylor86@example.com",
  "is_cold_start": false,
  "model_version": "1.0.0",
  "recommendations": [
    {"route_code": "SB-103", "hybrid_score": 0.5738, "cf_score": 0.2707, "cb_score": 0.7037, "reason": "content_match"},
    {"route_code": "SB-301", "hybrid_score": 0.2892, "cf_score": 0.2498, "cb_score": 0.3061, "reason": "content_match"},
    {"route_code": "SB-302", "hybrid_score": 0.2606, "cf_score": 0.2019, "cb_score": 0.2857, "reason": "content_match"}
  ],
  "inference_ms": 1.3
}
```

**What to say:**

> "This passenger has 43 bookings. The model is not cold-starting — it found real neighbours and real content matches. Look at SB-103: the content-based score is 0.70 while collaborative is 0.27. His departure stop strongly predicts this route. The model knows *why* it's recommending, not just *what*."

---

### Segment 3 — The Knowledge Graph: Structured Meaning (3 min)

**Plain-language definitions:**

| Term | Plain English |
|------|--------------|
| **Knowledge graph** | A network of facts stored as subject → relationship → object. Example: "Alice → prefersOrigin → Downtown Terminal" |
| **Ontology** | The rulebook defining what kinds of things exist and what relationships are valid |
| **RDF triple** | One single fact with three parts: (who) → (relationship) → (what). Everything in the graph is made of these |
| **SPARQL** | SQL for knowledge graphs — a query language for questions like "find all routes departing from Alice's preferred stop" |
| **Inference rule** | A rule the system applies automatically to derive new facts without any data entry |

**What to say:**

> "The relational database stores facts as rows and columns. The knowledge graph stores facts as a web of relationships. Those relationships carry meaning that SQL can't easily express. For example, 'Downtown Terminal and City Center are in the same geographic zone' is one triple. From that, an inference rule automatically derives that any route from City Center is relevant to someone near Downtown Terminal — no extra code needed."

**Demo 3a — Routes from a stop (SPARQL Q1):**

```sh
curl -s "http://localhost:5050/api/semantic/routes?from=Downtown%20Terminal" \
  | python3 -m json.tool
```

Expected:
```json
{
  "stop": "Downtown Terminal",
  "count": 3,
  "rule": "Q1 (routes_from_stop)",
  "routes": [
    {"route_code": "SB-101", "destination": "Airport Station", "price": 15.0},
    {"route_code": "SB-102", "destination": "University", "price": 8.5},
    {"route_code": "SB-103", "destination": "Harbor", "price": 12.0}
  ]
}
```

**Demo 3b — Inference rule R3: zone proximity:**

```sh
curl -s "http://localhost:5050/api/semantic/routes/zone?stop=Downtown%20Terminal" \
  | python3 -m json.tool
```

Expected:
```json
{
  "anchor_stop": "Downtown Terminal",
  "count": 2,
  "rule": "Q2 (zone_proximity R3)",
  "routes": [
    {"route_code": "SB-203", "origin_stop": "City Center"},
    {"route_code": "SB-303", "origin_stop": "City Center"}
  ]
}
```

**What to say:**

> "These are routes the system found by inference rule R3. We never said 'Downtown Terminal and City Center are near each other'. We said they are both in Zone_Central, and the reasoner derived the proximity automatically. This gives us two extra candidate routes we would have missed with a simple stop filter."

**Demo 3c — User's semantic profile:**

```sh
curl -s "http://localhost:5050/api/semantic/insights?email=alice@smartbus.test" \
  | python3 -m json.tool
```

Expected:
```json
{
  "email": "alice@smartbus.test",
  "inferred": {
    "candidate_routes": ["SB-101", "SB-102", "SB-103"],
    "frequently_travels": ["SB-101", "SB-102"],
    "inferred_facts": [
      {"relationship": "frequentlyTravels", "route_code": "SB-101"},
      {"relationship": "frequentlyTravels", "route_code": "SB-102"},
      {"relationship": "interactedWith",    "route_code": "SB-101"},
      {"relationship": "interactedWith",    "route_code": "SB-102"}
    ],
    "inferred_facts_count": 4
  },
  "stored_recommendations": [
    {"route_code": "SB-101", "confidence": "HIGH",   "score": 0.81},
    {"route_code": "SB-103", "confidence": "MEDIUM", "score": 0.47}
  ]
}
```

**What to say:**

> "The graph knows Alice's preferred stop because of inference rule R2 — whichever stop she departed from most is stored as `prefersOrigin`. SB-101 and SB-102 are marked `frequentlyTravels` because she booked each at least 3 times — rule R1. Candidate routes are the three that depart from her preferred stop — rule R4. These four facts, derived automatically from her booking history, are what we hand to the ML model next."

---

### Segment 4 — The Bridge: AI + Ontology Together (3 min)

**Plain-language definitions:**

| Term | Plain English |
|------|--------------|
| **Semantic boost** | Extra score added to an ML prediction because the knowledge graph independently confirms it |
| **Bidirectional bridge** | The ML model reads from the graph to improve predictions, AND writes its predictions back into the graph |
| **Enrichment** | Adjusting raw ML scores upward for routes the ontology also endorses |
| **Explanation** | A breakdown showing exactly how much each system contributed to a final score |

**What to say:**

> "The bridge runs in two directions.
>
> Direction 1: the graph teaches the model. Before finalising recommendations, we query the knowledge graph for the user's preferred origin and frequently-travelled routes. Any route the graph endorses gets a boost: +0.20 if it departs from the user's preferred stop, +0.15 if they have booked it three or more times.
>
> Direction 2: the model teaches the graph. After recommendations are finalised, we write them back into the knowledge graph as semantic facts — queryable with SPARQL — forming a permanent audit trail of every recommendation ever made."

**Demo 4a — Full intelligent pipeline (Flask direct, alice has ontology data):**

```sh
curl -s "http://localhost:5050/api/intelligent/recommend?email=alice@smartbus.test&n=3" \
  | python3 -m json.tool
```

Expected (key fields):
```json
{
  "email": "alice@smartbus.test",
  "is_cold_start": true,
  "semantic_features": {
    "preferred_origin": "Downtown Terminal",
    "candidate_routes": ["SB-101", "SB-102", "SB-103"],
    "frequently_travels": ["SB-101", "SB-102"]
  },
  "recommendations": [
    {"route_code": "SB-201", "ml_score": 1.0,   "semantic_boost": 0.0,  "enriched_score": 1.0,   "semantic_reasons": []},
    {"route_code": "SB-202", "ml_score": 0.9619, "semantic_boost": 0.0,  "enriched_score": 0.9619,"semantic_reasons": []},
    {"route_code": "SB-102", "ml_score": 0.609,  "semantic_boost": 0.35, "enriched_score": 0.959, "semantic_reasons": ["origin_preference_match","frequently_travelled"]}
  ],
  "enrichment_stats": {
    "routes_boosted": 1,
    "average_boost": 0.1167,
    "ontology_contributed": true,
    "rank_changes": [{"route_code": "SB-102", "old_rank": 8, "new_rank": 2, "delta": 6}]
  }
}
```

**What to say:**

> "Alice is cold-start in the ML model — no training data for her. The ML model ranked SB-102 at position 8, giving it a popularity score of 0.609. But the knowledge graph knew she departs from Downtown Terminal and has booked SB-102 more than three times. Those two facts added +0.20 and +0.15, pushing the score to 0.959 and moving SB-102 from rank 8 to rank 2. The ontology did the personalisation that the ML model couldn't do on its own."

**Demo 4b — Per-route explanation (the 'why' behind every recommendation):**

```sh
curl -s "http://localhost:5050/api/intelligent/explain?email=alice@smartbus.test&route=SB-102" \
  | python3 -m json.tool
```

Expected:
```json
{
  "route_code": "SB-102",
  "ml_score": 0.609,
  "semantic_boost": 0.35,
  "enriched_score": 0.959,
  "confidence": "HIGH",
  "contributions": {
    "ml_collaborative_filtering": 0.0,
    "ml_content_based": 0.0,
    "ml_hybrid_score": 0.609,
    "semantic_origin_preference": 0.2,
    "semantic_frequency_boost": 0.15
  },
  "reasoning": "ML model scored this route 0.61 (CF=0.00, CB=0.00); Route departs from Downtown Terminal (your preferred origin, Rule R4) +0.20; You have booked this route 3+ times (Rule R1) +0.15."
}
```

**What to say:**

> "This is the explanation endpoint. Every recommendation can be broken down into four contributions: collaborative filtering, content-based filtering, origin preference from the ontology, and travel frequency from the ontology. Any user or auditor can look at this and understand exactly why the system chose this route — not just a black box score."

---

### Segment 5 — Through the Gateway: End-to-End (2 min)

**What to say:**

> "Everything so far was direct calls to the Python server. In the real system, the browser only talks to the Java gateway on port 8080. The gateway handles authentication, validates the JWT token, and proxies to the ML server on the user's behalf."

**Demo 5a — Standard recommendations (authenticated, cold-start user):**

```sh
curl -s http://localhost:8080/api/v1/frontend/recommendations \
  -H "Authorization: Bearer $TOKEN" | python3 -m json.tool
```

Expected (key fields):
```json
{
  "customerEmail": "demo@smartbus.test",
  "isColdStart": true,
  "recommendations": [
    {"routeCode": "SB-201", "displayName": "Airport Station → City Center", "hybridScore": 1.0,    "confidence": "HIGH", "reasonLabel": "Popular route"},
    {"routeCode": "SB-202", "displayName": "University → City Center",       "hybridScore": 0.9619, "confidence": "HIGH", "reasonLabel": "Popular route"},
    {"routeCode": "SB-304", "displayName": "University → Airport Station",   "hybridScore": 0.8512, "confidence": "HIGH", "reasonLabel": "Popular route"}
  ]
}
```

**What to say:**

> "This is our demo user — brand new, no booking history. `isColdStart: true`. The model falls back to the three most popular routes across all users. As soon as this user makes their first booking, that flag disappears and the model starts personalising."

**Demo 5b — Semantic routes via gateway (the originally broken endpoint, now fixed):**

```sh
curl -s "http://localhost:8080/api/v1/frontend/semantic/routes?from=Downtown%20Terminal" \
  -H "Authorization: Bearer $TOKEN" | python3 -m json.tool
```

Should return the same 3-route result as the direct Flask call.

---

### Segment 6 — What the User Actually Sees (1 min)

Open `frontend/user/buy-ticket.html` in the browser and log in as `demo@smartbus.test`.

**What to show:**

1. The **"Recommended for You"** card — three route cards with HIGH confidence badges.
2. Point to the confidence badge: `>= 0.60` → GREEN / HIGH, `>= 0.30` → YELLOW / MEDIUM, `< 0.30` → GREY / LOW.
3. Each card shows a `reasonLabel` from the decision engine: "Popular route", "Users like you booked this", or "Matches your travel preferences".

**What to say:**

> "This is what the passenger sees. The complexity of ML models and knowledge graphs is completely hidden. They see route cards ranked by how likely they are to book them, with a short human-readable reason. The confidence badge tells them how sure the system is."

---

### Segment 7 — Knowledge Graph Stats (30 sec)

```sh
curl -s http://localhost:5050/api/semantic/health | python3 -m json.tool
```

Expected:
```json
{
  "status": "UP",
  "total_triples": 675,
  "classes": 10,
  "users": 5,
  "routes": 10,
  "bookings": 14,
  "recommendations": 5,
  "ontology_valid": true,
  "violation_count": 0
}
```

**What to say:**

> "The knowledge graph holds 675 RDF triples after inference rules are applied. Load time at startup is 136 milliseconds — once. A SPARQL query answers in 1 to 25 milliseconds. The full intelligent recommendation pipeline — ML scoring, ontology lookup, boost, re-ranking, and writing predictions back into the graph — runs in about 68 milliseconds. All well under the 1-second SLA we set."

---

## Backup Plan

If Flask server fails or a SPARQL query crashes:
- Show `docs/Phase_IV_Final_Report.pdf` — Sections 4 (Ontology) and 5 (AI Integration) have diagrams and pre-rendered query results.
- Show `docs/phase4-performance.md` — all latency numbers recorded.
- Show `docs/phase4-test-results.md` — 95 tests passing.

If gateway returns **403**:
- Token may have expired (8-hour TTL). Re-run the login command to get a fresh `$TOKEN`.
- Verify Flask is on port 5050: `curl http://localhost:5050/api/semantic/health`

If containers are not running (fresh session):
```sh
docker compose -f infra/docker-compose.yml up -d
# Wait ~15 seconds, then retry
```

If `gateway_recommendations` table is missing (fresh Docker volume):
```sh
docker exec smartbus-postgres psql -U smartbus -d smartbus_booking -c "
  CREATE TABLE IF NOT EXISTS gateway_recommendations (
    id BIGSERIAL PRIMARY KEY,
    customer_email VARCHAR(200) NOT NULL,
    route_code VARCHAR(64) NOT NULL,
    hybrid_score DOUBLE PRECISION NOT NULL,
    cf_score DOUBLE PRECISION NOT NULL,
    cb_score DOUBLE PRECISION NOT NULL,
    reason VARCHAR(64) NOT NULL,
    confidence VARCHAR(32) NOT NULL,
    model_version VARCHAR(32) NOT NULL,
    is_cold_start BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
  );
"
```

---

## Quick Reference — All Demo Commands

| Segment | What it shows | Command |
|---------|--------------|---------|
| Setup | Gateway health | `curl http://localhost:8080/actuator/health` |
| Setup | ML server + KG stats | `curl http://localhost:5050/api/semantic/health` |
| 2 | Personalised ML (non-cold-start) | `curl "http://localhost:5050/api/ml/recommend?email=chris.taylor86@example.com&n=3"` |
| 3a | Routes from a stop | `curl "http://localhost:5050/api/semantic/routes?from=Downtown%20Terminal"` |
| 3b | Zone-proximity inference (R3) | `curl "http://localhost:5050/api/semantic/routes/zone?stop=Downtown%20Terminal"` |
| 3c | User's KG profile | `curl "http://localhost:5050/api/semantic/insights?email=alice@smartbus.test"` |
| 4a | Intelligent pipeline — ontology boost in action | `curl "http://localhost:5050/api/intelligent/recommend?email=alice@smartbus.test&n=3"` |
| 4b | Per-route explanation | `curl "http://localhost:5050/api/intelligent/explain?email=alice@smartbus.test&route=SB-102"` |
| 5a | Gateway: standard recommendations | `curl http://localhost:8080/api/v1/frontend/recommendations -H "Authorization: Bearer $TOKEN"` |
| 5b | Gateway: semantic query proxy | `curl "http://localhost:8080/api/v1/frontend/semantic/routes?from=Downtown%20Terminal" -H "Authorization: Bearer $TOKEN"` |

---

## Key Numbers to Memorise

| Metric | Value | What it means |
|--------|-------|--------------|
| NDCG@3 | **0.405** | 91.6% better than random at recommending routes users actually book |
| Precision@3 | **0.278** | 28% of recommended routes are ones the user genuinely books |
| KG triples | **675** | Facts stored after inference rules are applied |
| KG startup | **136 ms** | One-time cost at server launch |
| SPARQL query | **1–25 ms** | Per-request semantic lookup |
| Full pipeline | **~68 ms** | ML score + ontology boost + re-rank + write-back |
| Rank change | **8 → 2** | SB-102 for alice: ML gave it rank 8, ontology boost moved it to rank 2 |
| Ontology boost | **+0.35** | +0.20 (origin preference R4) + 0.15 (frequency R1) |
