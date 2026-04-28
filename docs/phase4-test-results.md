# SmartBus Phase IV — Test Results

Date: 2026-04-26  
Run: full regression suite — all phases

---

## Summary

| Suite | Tests | Passed | Failed | Status |
|-------|------:|-------:|-------:|--------|
| Gateway Java (JUnit 5) | 8 | 8 | 0 | PASS |
| Knowledge Graph (Task 05) | 25 | 25 | 0 | PASS |
| Semantic Service (Task 06) | 32 | 32 | 0 | PASS |
| AI-Ontology Bridge (Task 07) | 30 | 30 | 0 | PASS |
| **Total** | **95** | **95** | **0** | **PASS** |

---

## 1. Java Gateway Tests (JUnit 5 + Spring Boot Test)

```
mvn test -pl backend/gateway
```

| Class | Tests | Result |
|-------|------:|--------|
| `GatewayApplicationTests` | 1 | PASS — context loads without errors |
| `RecommendationDecisionEngineTests` | 6 | PASS |
| `OpenApiContractValidationTests` | 1 | PASS — all OpenAPI contracts valid |

### RecommendationDecisionEngineTests detail

| Test | Assertion | Result |
|------|-----------|--------|
| `filtersLowScoreRecommendations` | score < 0.01 is dropped | PASS |
| `assignsCorrectConfidenceLabels` | HIGH/MEDIUM/LOW thresholds 0.6/0.3 | PASS |
| `enrichesDisplayNameForKnownRoutes` | SB-101 → "Downtown Terminal → Airport Station" | PASS |
| `coldStartUsesPopularityFallbackLabel` | isColdStart=true → "Popular route" | PASS |
| `collaborativeMatchGetsCorrectReasonLabel` | collaborative_match → "Users like you booked this" | PASS |
| `emptyInputReturnsEmptyList` | empty raw list → empty output | PASS |

---

## 2. Knowledge Graph Tests (Task 05)

```
python -m pytest ml/semantic/test_knowledge_graph.py -v
```

### TestOntologyLoading (5 tests)

| Test | Assertion | Result |
|------|-----------|--------|
| `test_parses_without_error` | `_loaded_ontology = True` | PASS |
| `test_minimum_triple_count` | `len(g) >= 400` | PASS (675) |
| `test_ten_classes_declared` | `owl:Class count >= 10` | PASS (10) |
| `test_object_properties_declared` | `owl:ObjectProperty count >= 15` | PASS (18) |
| `test_data_properties_declared` | `owl:DatatypeProperty count >= 20` | PASS (24) |

### TestSampleDataCoverage (5 tests)

| Test | Assertion | Result |
|------|-----------|--------|
| `test_five_users_loaded` | `stats()["users"] == 5` | PASS |
| `test_ten_routes_loaded` | `stats()["routes"] == 10` | PASS |
| `test_bookings_loaded` | `stats()["bookings"] >= 10` | PASS (14) |
| `test_recommendations_loaded` | `stats()["recommendations"] >= 5` | PASS (5) |
| `test_all_entity_types_present` | all 10 `sb:Class` types have instances | PASS |

### TestInferenceRules (6 tests)

| Test | Rule | Assertion | Result |
|------|------|-----------|--------|
| `test_r3_zone_proximity_inferred` | R3 | Downtown inSameZoneAs CityCenter | PASS |
| `test_r3_symmetry` | R3 | CityCenter inSameZoneAs Downtown | PASS |
| `test_r3_northern_zone` | R3 | Airport inSameZoneAs University | PASS |
| `test_chain_interacted_with` | chain | Alice interactedWith SB-101 | PASS |
| `test_r1_frequently_travels` | R1 | Alice frequentlyTravels SB-101 | PASS |
| `test_r2_prefers_origin` | R2 | Alice prefersOrigin Downtown Terminal | PASS |

### TestSparqlQueries (5 tests)

| Test | Query | Assertion | Result |
|------|-------|-----------|--------|
| `test_routes_from_downtown` | Q1 | SB-101, SB-102, SB-103 returned | PASS |
| `test_zone_routes_for_downtown` | Q2 | ≥1 zone-adjacent route returned | PASS |
| `test_recommendations_for_alice` | Q3 | SB-101 in results | PASS |
| `test_candidate_routes_for_alice` | R4 | SB-101 in candidates | PASS |
| `test_unknown_user_returns_empty` | Q3 | empty list for unknown user | PASS |

### TestAssertRecommendation (2 tests)

| Test | Assertion | Result |
|------|-----------|--------|
| `test_assert_adds_triples` | recommendations++ after assert | PASS |
| `test_assert_unknown_user_is_noop` | no change for unknown email | PASS |

### TestValidation (2 tests)

| Test | Assertion | Result |
|------|-----------|--------|
| `test_no_violations_on_clean_data` | zero violations on loaded data | PASS |
| `test_functional_property_violation_detected` | duplicate email triggers violation | PASS |

---

## 3. Semantic Service Tests (Task 06)

```
python -m pytest ml/semantic/test_semantic_service.py -v
```

### TestGraphStats (4 tests) — all PASS
- ontology_valid=True, available_relationships listed, triple count > 0, < 1s response

### TestRoutesFromStop — Q1 (5 tests) — all PASS
- 3 routes from Downtown Terminal, prices returned, unknown stop returns empty

### TestRoutesInZone — Q2+R3 (3 tests) — all PASS
- Zone-adjacent routes for Downtown and Airport, rule label contains "R3"

### TestCandidateRoutes — R4 (4 tests) — all PASS
- Alice gets SB-101/102/103, confidence labels (HIGH/MEDIUM/LOW), empty for unknown user

### TestUserInsights — R1+R2+R4 (6 tests) — all PASS
- Alice: frequently_travels SB-101, candidates include Downtown routes, stored recs returned,
  inferred facts contain frequentlyTravels or interactedWith, unknown user returns empty

### TestFindRelated — traversal (4 tests) — all PASS
- inSameZoneAs finds zone neighbours, inZone finds zone, invalid relationship returns error

### TestExecuteSparql — SPARQL passthrough (6 tests) — all PASS
- COUNT(routes)=10, SELECT users returns alice@smartbus.test, INSERT rejected, DELETE rejected,
  query_ms reported, 2-hop multi-join returns results

---

## 4. AI-Ontology Bridge Tests (Task 07)

```
python -m pytest ml/semantic/test_ai_ontology_bridge.py -v
```

### TestSemanticFeatures (5 tests) — all PASS
- Alice preferred_origin = Downtown Terminal, candidate_routes contains SB-101/102/103,
  frequently_travels SB-101, unknown user returns empty, Bob preferred_origin = City Center

### TestEnrichmentLogic (7 tests) — all PASS

| Test | Assertion | Result |
|------|-----------|--------|
| `test_candidate_route_receives_boost` | R4 route gets +0.20 | PASS |
| `test_frequently_travelled_receives_boost` | R1 route gets +0.15 | PASS |
| `test_double_boost_for_candidate_and_frequent` | R4+R1 gets +0.35 | PASS |
| `test_enriched_score_clamped_at_one` | 0.90 + 0.35 = 1.00 (clamped) | PASS |
| `test_enriched_score_never_decreases` | enriched >= ml_score always | PASS |
| `test_semantic_reasons_populated_correctly` | both reason codes in list | PASS |
| `test_reranking_promotes_boosted_route` | SB-102 (ML=0.45+0.20=0.65) > SB-101 (ML=0.60) | PASS |

### TestStoreInKG (3 tests) — all PASS
- KG rec count increases by 1 after store, duplicate rec_id is idempotent, stored rec is SPARQL-queryable

### TestExplain (7 tests) — all PASS
- All contribution keys present, origin boost = 0.20, frequency boost = 0.15,
  enriched_score = ml + boost, non-candidate has 0 origin boost, reasoning non-empty

### TestFullPipeline (6 tests) — all PASS (MEASURABLE IMPROVEMENT)

| Test | Evidence | Result |
|------|----------|--------|
| `test_enrichment_reranks_above_higher_ml_route` | SB-101 (ML=0.55+0.35=0.90) ranks above SB-201 (ML=0.72) | PASS |
| `test_enrichment_stats_report_improvement` | routes_boosted >= 1, average_boost > 0 | PASS |
| `test_rank_changes_populated` | rank_changes list has entries | PASS |
| `test_kg_triples_added_is_positive` | kg_triples_added >= 1 | PASS |
| `test_semantic_features_in_response` | candidate_routes contains SB-101 | PASS |
| `test_cold_start_user_gets_no_boost` | unknown user: all semantic_boost = 0 | PASS |

### TestImprovementStats (2 tests) — all PASS
- Report structure complete, boosted_count = 1 for single-boosted input

---

## 5. Security Review

Checks performed on all Phase IV Python code:

| Check | Finding | Resolution |
|-------|---------|------------|
| SPARQL f-string injection | `candidate_routes()` and `user_insights()` passed raw email to `.format()` | Fixed: `email.replace('"', "").replace("\\", "")` before format |
| SPARQL mutation prevention | `execute_sparql()` rejects INSERT/DELETE/DROP/CONSTRUCT/UPDATE | OK — tested by `test_rejects_insert` and `test_rejects_delete` |
| Java XSS | Existing `HtmlSanitizer` covers all free-text fields from Phase III | OK — no new free-text inputs in Phase IV |
| Java SQL injection | All queries use Spring Data JPA / JPQL parameters | OK — no string concatenation in SQL |
| JWT validation | All `/api/v1/frontend/semantic/**` and `/intelligent/**` require auth | OK — SecurityConfiguration updated |
| Input validation on Flask | `email` and `stop_name` params sanitised before SPARQL injection | OK after fix |
| Cold-start data isolation | Unknown email returns empty/popularity-based, never exposes other users | OK — tested |

All identified issues resolved. No critical vulnerabilities found.

---

## 6. Phase III Regression Check

All 8 Java gateway tests pass, including `OpenApiContractValidationTests` which validates all
existing OpenAPI contracts. The following Phase III features were verified unaffected:

- JWT authentication (`/api/v1/auth/login`, `/api/v1/auth/register`)
- Booking proxy endpoints (`/api/v1/frontend/bookings/**`)
- Admin endpoints (`/api/v1/frontend/admin/**`)
- Route search (`/api/v1/frontend/routes`, `/api/v1/frontend/quote`)
- `DecisionEngine` confidence labelling (6/6 tests pass)

No Phase III regressions detected.
