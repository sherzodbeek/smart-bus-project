# SmartBus Phase IV — Performance Benchmarks

Date: 2026-04-26  
Environment: macOS 25.4 (Darwin) · Python 3.14.3 · RDFLib 7.0 · 20 warm iterations each

---

## 1. Knowledge Graph Startup

| Operation | Latency | Notes |
|-----------|--------:|-------|
| KG cold load (both .ttl files + inference) | 135.6 ms | One-time at Flask startup |
| ontology/smartbus-ontology.ttl only | ~80 ms | 385 triples |
| ontology/sample-data.ttl load | ~40 ms | 284 triples |
| apply_inference_rules() (R3 + chain) | ~15 ms | Adds zone proximity + interactedWith triples |

The KG is loaded once at server startup (`get_knowledge_graph()` singleton) and kept
in memory for all requests. Subsequent query calls incur no reload overhead.

---

## 2. SPARQL Query Performance

Measured over 20 iterations on the 675-triple in-memory RDFLib graph.

| Query / Operation | Avg (ms) | p95 (ms) | Min (ms) | Verdict |
|-------------------|--------:|--------:|--------:|---------|
| `routes_from_stop` (Q1 — 3-triple match) | 3.92 | 7.36 | 3.44 | Excellent |
| `routes_in_zone` (Q2 + R3 — UNION) | 5.68 | 8.51 | 4.81 | Excellent |
| `candidate_routes` (R4 — 2-hop) | 2.03 | 3.08 | 1.74 | Excellent |
| `user_insights` (R1+R2+R4 — 4 queries) | 16.18 | 23.33 | 14.05 | Good |
| `find_related` (generic traversal) | 5.88 | 8.34 | 5.15 | Excellent |
| `execute_sparql` (2-hop multi-join) | 1.29 | 1.45 | 1.22 | Excellent |
| `graph_stats + validation` | 154.26 | 161.35 | 146.21 | Acceptable* |

*`graph_stats` runs a full OntologyValidator pass (domain/range/functional checks over all triples).
For a production health endpoint this is acceptable as an infrequent call; it is not on the
hot request path.

**All SPARQL queries complete well under the 1-second SLA requirement.**

---

## 3. AI-Ontology Bridge Performance

| Operation | Avg (ms) | p95 (ms) | Min (ms) |
|-----------|--------:|--------:|--------:|
| `get_semantic_features` (3 KG queries) | 12.80 | 17.03 | 10.97 |
| `enrich_recommendations` (pure Python) | < 0.5 | < 0.5 | < 0.1 |
| `store_predictions_in_kg` (n=3 triples) | < 1.0 | < 1.0 | < 0.5 |
| `full enrichment pipeline` (mock ML) | 26.32 | 47.65 | 20.31 |
| `explain` (1 ML call + 3 KG queries) | 14.02 | 21.25 | 11.21 |

The full enrichment pipeline (26ms avg) includes mock ML inference. With a real ML model
loaded in memory, actual inference adds 2–5ms, giving an expected end-to-end latency of
~30ms for `/api/intelligent/recommend`.

---

## 4. ML Inference Performance

| Operation | Latency | Notes |
|-----------|--------:|-------|
| Model cold load (joblib) | ~400 ms | One-time at Flask startup |
| `recommend_for_email` (known user, n=3) | 2–5 ms | User-item matrix lookup |
| `recommend_for_email` (cold start, n=3) | < 2 ms | Popularity sum — no KNN |
| `recommend_for_email` (known user, n=10) | 3–8 ms | Larger pool for enrichment |

---

## 5. Gateway Round-Trip Estimates

For a logged-in user calling `/api/v1/frontend/intelligent/recommend`:

| Step | Latency |
|------|--------:|
| JWT validation (gateway) | < 1 ms |
| HTTP call: gateway → Flask | 1–3 ms (localhost) |
| Flask: ML inference | 2–5 ms |
| Flask: get_semantic_features | 13 ms |
| Flask: enrichment + KG store | 2 ms |
| HTTP response: Flask → gateway | 1–3 ms |
| JSON deserialisation + EnrichedDecisionEngine | < 1 ms |
| **Total gateway round-trip** | **~20–30 ms** |

For the simpler `/api/v1/frontend/recommendations` (ML only):

| Step | Latency |
|------|--------:|
| ML inference | 2–5 ms |
| DecisionEngine.process() | < 1 ms |
| PostgreSQL persist | 2–5 ms |
| **Total** | **~5–15 ms** |

---

## 6. Database Performance

### gateway_recommendations table

| Operation | Notes |
|-----------|-------|
| INSERT (n=3 rows) | Transactional, < 5ms |
| SELECT by email (indexed) | < 2ms for typical user history |

Indexes: `idx_gateway_recommendations_email` (customer_email), `idx_gateway_recommendations_created_at` (created_at DESC)

---

## 7. Knowledge Graph Scalability Notes

The current in-memory RDFLib graph (675 triples) is appropriate for the development dataset.
Scalability considerations for production:

| Dimension | Current | Estimated limit |
|-----------|---------|----------------|
| Triple count | 675 | ~50,000 before query degradation |
| SPARQL query latency | 1–16 ms | Linear with triple count for simple patterns |
| Users | 5 (ontology) / 300 (ML training) | Unbounded — KG scales per-assertion |
| Concurrent requests | Single-process Flask | Use gunicorn workers for concurrency |

For a production deployment with >10,000 users, migrate the RDFLib in-memory graph to a
dedicated triple store (Apache Jena TDB2, Blazegraph, or Oxigraph) and update
`SmartBusKnowledgeGraph` to use SPARQL over HTTP.

---

## 8. SLA Compliance Summary

| Requirement | Threshold | Actual | Status |
|-------------|----------:|-------:|--------|
| SPARQL query latency (typical) | < 1,000 ms | < 25 ms | PASS |
| ML inference (warm) | < 100 ms | < 8 ms | PASS |
| Intelligent recommendation pipeline | < 1,000 ms | < 50 ms | PASS |
| KG load at startup | N/A (one-time) | 136 ms | OK |
| All tests pass | 95/95 | 95/95 | PASS |
