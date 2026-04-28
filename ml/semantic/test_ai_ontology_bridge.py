"""
Tests for AISemanticBridge — Task 07 acceptance criteria.

Verifies the bidirectional AI-Ontology integration:
  - Direction 1: Ontology → AI  (semantic features boost ML scores)
  - Direction 2: AI → Ontology  (predictions stored as RDF triples)
  - Combined: enriched scores measurably improve routing relevance

Run with:  python -m pytest ml/semantic/test_ai_ontology_bridge.py -v
"""

from __future__ import annotations

import sys
from pathlib import Path

import pytest

_PROJECT_ROOT = Path(__file__).parent.parent.parent
sys.path.insert(0, str(_PROJECT_ROOT / "ml"))

from semantic.knowledge_graph import SmartBusKnowledgeGraph  # noqa: E402
from semantic.ai_ontology_bridge import (                     # noqa: E402
    AISemanticBridge,
    SemanticFeatures,
    CANDIDATE_BOOST,
    FREQUENCY_BOOST,
)

# ── Fixtures ──────────────────────────────────────────────────────────────────

@pytest.fixture(scope="module")
def kg() -> SmartBusKnowledgeGraph:
    return SmartBusKnowledgeGraph()


@pytest.fixture(scope="module")
def bridge(kg) -> AISemanticBridge:
    # No inference engine — we'll inject raw_recs manually in most tests
    return AISemanticBridge(kg=kg, inference_engine=None)


def _make_recs(*route_scores: tuple[str, float]) -> list[dict]:
    """Create synthetic raw ML recommendations for testing enrichment logic."""
    return [
        {
            "route_code":   code,
            "hybrid_score": score,
            "cf_score":     score * 0.6,
            "cb_score":     score * 0.4,
            "reason":       "collaborative_match",
        }
        for code, score in route_scores
    ]


# ── SemanticFeatures (Ontology → AI direction) ────────────────────────────────

class TestSemanticFeatures:
    def test_alice_preferred_origin_is_downtown(self, bridge):
        features = bridge.get_semantic_features("alice@smartbus.test")
        assert features.preferred_origin == "Downtown Terminal"

    def test_alice_candidate_routes_are_downtown_departures(self, bridge):
        features = bridge.get_semantic_features("alice@smartbus.test")
        assert "SB-101" in features.candidate_routes
        assert "SB-102" in features.candidate_routes
        assert "SB-103" in features.candidate_routes

    def test_alice_frequently_travels_sb101(self, bridge):
        features = bridge.get_semantic_features("alice@smartbus.test")
        assert "SB-101" in features.frequently_travels

    def test_unknown_user_returns_empty_features(self, bridge):
        features = bridge.get_semantic_features("ghost@unknown.test")
        assert features.preferred_origin is None
        assert features.candidate_routes == []
        assert features.frequently_travels == []

    def test_bob_preferred_origin_is_city_center(self, bridge):
        features = bridge.get_semantic_features("bob@smartbus.test")
        assert features.preferred_origin == "City Center"


# ── Enrichment logic (Ontology → AI direction) ────────────────────────────────

class TestEnrichmentLogic:
    def test_candidate_route_receives_boost(self, bridge, kg):
        """Route in R4 candidate set gets +CANDIDATE_BOOST to its ML score."""
        features = SemanticFeatures(
            email="test@test.test",
            preferred_origin="Downtown Terminal",
            candidate_routes=["SB-101", "SB-102"],
            frequently_travels=[],
            stored_recs=[],
        )
        recs = _make_recs(("SB-101", 0.50), ("SB-201", 0.55))
        enriched = bridge.enrich_recommendations(recs, features)

        by_code = {r.route_code: r for r in enriched}
        assert by_code["SB-101"].semantic_boost == CANDIDATE_BOOST
        assert by_code["SB-201"].semantic_boost == 0.0

    def test_frequently_travelled_receives_boost(self, bridge):
        features = SemanticFeatures(
            email="test@test.test",
            preferred_origin=None,
            candidate_routes=[],
            frequently_travels=["SB-101"],
            stored_recs=[],
        )
        recs = _make_recs(("SB-101", 0.40), ("SB-202", 0.45))
        enriched = bridge.enrich_recommendations(recs, features)

        by_code = {r.route_code: r for r in enriched}
        assert by_code["SB-101"].semantic_boost == FREQUENCY_BOOST
        assert by_code["SB-202"].semantic_boost == 0.0

    def test_double_boost_for_candidate_and_frequent(self, bridge):
        """Route that is both a candidate AND frequently-travelled gets both boosts."""
        features = SemanticFeatures(
            email="test@test.test",
            preferred_origin="Downtown Terminal",
            candidate_routes=["SB-101"],
            frequently_travels=["SB-101"],
            stored_recs=[],
        )
        recs = _make_recs(("SB-101", 0.40))
        enriched = bridge.enrich_recommendations(recs, features)

        assert enriched[0].semantic_boost == CANDIDATE_BOOST + FREQUENCY_BOOST
        expected = min(1.0, 0.40 + CANDIDATE_BOOST + FREQUENCY_BOOST)
        assert abs(enriched[0].enriched_score - expected) < 1e-6

    def test_enriched_score_clamped_at_one(self, bridge):
        features = SemanticFeatures(
            email="test@test.test",
            preferred_origin=None,
            candidate_routes=["SB-101"],
            frequently_travels=["SB-101"],
            stored_recs=[],
        )
        recs = _make_recs(("SB-101", 0.90))   # 0.90 + 0.35 > 1.0
        enriched = bridge.enrich_recommendations(recs, features)
        assert enriched[0].enriched_score == 1.0

    def test_enriched_score_never_decreases(self, bridge):
        """Semantic boost must never lower the ML score."""
        features = SemanticFeatures(
            email="test@test.test",
            preferred_origin=None,
            candidate_routes=[],
            frequently_travels=[],
            stored_recs=[],
        )
        recs = _make_recs(("SB-101", 0.62), ("SB-202", 0.41))
        enriched = bridge.enrich_recommendations(recs, features)
        for r in enriched:
            assert r.enriched_score >= r.ml_score

    def test_semantic_reasons_populated_correctly(self, bridge):
        features = SemanticFeatures(
            email="test@test.test",
            preferred_origin="Downtown Terminal",
            candidate_routes=["SB-101"],
            frequently_travels=["SB-101"],
            stored_recs=[],
        )
        recs = _make_recs(("SB-101", 0.40))
        enriched = bridge.enrich_recommendations(recs, features)

        reasons = enriched[0].semantic_reasons
        assert "origin_preference_match" in reasons
        assert "frequently_travelled" in reasons

    def test_reranking_promotes_boosted_route(self, bridge):
        """A route with lower ML score but high boost should rank above unboosted."""
        features = SemanticFeatures(
            email="test@test.test",
            preferred_origin=None,
            candidate_routes=["SB-102"],
            frequently_travels=[],
            stored_recs=[],
        )
        # SB-101 higher ML, SB-102 lower ML but boosted
        recs = _make_recs(("SB-101", 0.60), ("SB-102", 0.45))
        enriched = bridge.enrich_recommendations(recs, features)
        enriched_sorted = sorted(enriched, key=lambda r: r.enriched_score, reverse=True)

        # SB-102 enriched = 0.45 + 0.20 = 0.65 > SB-101 = 0.60
        assert enriched_sorted[0].route_code == "SB-102"


# ── Direction 2: AI → Ontology (store predictions in KG) ─────────────────────

class TestStoreInKG:
    def test_predictions_increase_kg_rec_count(self, bridge, kg):
        """Storing enriched predictions must add new Recommendation triples."""
        from semantic.ai_ontology_bridge import EnrichedRecommendation
        before = kg.stats()["recommendations"]
        test_recs = [
            EnrichedRecommendation(
                route_code="SB-201",
                ml_score=0.45,
                cf_score=0.30,
                cb_score=0.20,
                semantic_boost=0.20,
                enriched_score=0.65,
                original_reason="content_match",
                semantic_reasons=["origin_preference_match"],
            )
        ]
        stored = bridge.store_predictions_in_kg(
            "david@smartbus.test", test_recs, "1.0.0"
        )
        after = kg.stats()["recommendations"]

        assert stored == 1
        assert after == before + 1

    def test_duplicate_rec_id_does_not_add_extra_triples(self, bridge, kg):
        """Same (email, route, version) must reuse the same rec_id (idempotent-ish)."""
        from semantic.ai_ontology_bridge import EnrichedRecommendation, _rec_id
        before = kg.stats()["recommendations"]
        rec = EnrichedRecommendation(
            route_code="SB-202",
            ml_score=0.40,
            cf_score=0.25,
            cb_score=0.18,
            semantic_boost=0.15,
            enriched_score=0.55,
            original_reason="collaborative_match",
            semantic_reasons=["frequently_travelled"],
        )
        bridge.store_predictions_in_kg("carol@smartbus.test", [rec], "1.0.0")
        # Call again with same inputs — same rec_id, overwrite same triples
        bridge.store_predictions_in_kg("carol@smartbus.test", [rec], "1.0.0")
        after = kg.stats()["recommendations"]

        # Net addition must be exactly 1, not 2
        assert after == before + 1

    def test_stored_rec_queryable_via_sparql(self, bridge, kg):
        """After storing, the recommendation appears in ontology Q3 query."""
        from semantic.ai_ontology_bridge import EnrichedRecommendation
        recs_before = kg.recommendations_for_email("eve@smartbus.test")
        rec = EnrichedRecommendation(
            route_code="SB-101",
            ml_score=0.50,
            cf_score=0.35,
            cb_score=0.20,
            semantic_boost=0.35,
            enriched_score=0.85,
            original_reason="collaborative_match",
            semantic_reasons=["origin_preference_match", "frequently_travelled"],
        )
        bridge.store_predictions_in_kg("eve@smartbus.test", [rec], "1.0.0")
        recs_after = kg.recommendations_for_email("eve@smartbus.test")
        codes_after = [r["route_code"] for r in recs_after]
        assert "SB-101" in codes_after


# ── Explanation (combined breakdown) ─────────────────────────────────────────

class TestExplain:
    def _make_bridge_with_mock_engine(self, kg):
        """Create a bridge with a mock ML engine for deterministic testing."""
        class MockEngine:
            def recommend_for_email(self, email, n=10, **kw):
                return {
                    "recommendations": [
                        {"route_code": "SB-101", "hybrid_score": 0.62,
                         "cf_score": 0.40, "cb_score": 0.22, "reason": "collaborative_match"},
                        {"route_code": "SB-102", "hybrid_score": 0.38,
                         "cf_score": 0.20, "cb_score": 0.18, "reason": "content_match"},
                        {"route_code": "SB-201", "hybrid_score": 0.55,
                         "cf_score": 0.30, "cb_score": 0.25, "reason": "content_match"},
                    ],
                    "is_cold_start": False,
                    "model_version": "1.0.0",
                }
        return AISemanticBridge(kg=kg, inference_engine=MockEngine())

    def test_explain_returns_ml_and_semantic_components(self, kg):
        b = self._make_bridge_with_mock_engine(kg)
        result = b.explain("alice@smartbus.test", "SB-101")

        assert "ml_score" in result
        assert "semantic_boost" in result
        assert "enriched_score" in result
        assert "contributions" in result
        assert "semantic_context" in result
        assert "reasoning" in result

    def test_explain_candidate_route_has_nonzero_origin_boost(self, kg):
        b = self._make_bridge_with_mock_engine(kg)
        result = b.explain("alice@smartbus.test", "SB-101")

        # SB-101 departs from Downtown Terminal — Alice's preferred origin
        assert result["contributions"]["semantic_origin_preference"] == CANDIDATE_BOOST

    def test_explain_frequent_route_has_nonzero_frequency_boost(self, kg):
        b = self._make_bridge_with_mock_engine(kg)
        result = b.explain("alice@smartbus.test", "SB-101")

        assert result["contributions"]["semantic_frequency_boost"] == FREQUENCY_BOOST

    def test_explain_enriched_score_is_sum(self, kg):
        b = self._make_bridge_with_mock_engine(kg)
        result = b.explain("alice@smartbus.test", "SB-101")

        expected = min(1.0, result["ml_score"] + result["semantic_boost"])
        assert abs(result["enriched_score"] - expected) < 1e-6

    def test_explain_non_candidate_route_has_zero_origin_boost(self, kg):
        b = self._make_bridge_with_mock_engine(kg)
        # SB-201 departs from Airport Station, not Alice's preferred Downtown
        result = b.explain("alice@smartbus.test", "SB-201")
        assert result["contributions"]["semantic_origin_preference"] == 0.0

    def test_explain_reasoning_is_nonempty_string(self, kg):
        b = self._make_bridge_with_mock_engine(kg)
        result = b.explain("alice@smartbus.test", "SB-101")
        assert isinstance(result["reasoning"], str)
        assert len(result["reasoning"]) > 0

    def test_explain_semantic_context_flags(self, kg):
        b = self._make_bridge_with_mock_engine(kg)
        result = b.explain("alice@smartbus.test", "SB-101")
        ctx = result["semantic_context"]
        assert ctx["is_candidate_route"] is True
        assert ctx["is_frequently_travelled"] is True
        assert ctx["preferred_origin"] == "Downtown Terminal"


# ── Full pipeline with mock engine ────────────────────────────────────────────

class TestFullPipeline:
    def _bridge_with_mock(self, kg):
        class MockEngine:
            def recommend_for_email(self, email, n=10, **kw):
                # Simulate 10 routes; Alice's candidate routes (SB-101/102/103)
                # have mid-range ML scores
                recs = [
                    {"route_code": "SB-201", "hybrid_score": 0.72,
                     "cf_score": 0.50, "cb_score": 0.30, "reason": "collaborative_match"},
                    {"route_code": "SB-101", "hybrid_score": 0.55,
                     "cf_score": 0.35, "cb_score": 0.25, "reason": "content_match"},
                    {"route_code": "SB-303", "hybrid_score": 0.48,
                     "cf_score": 0.30, "cb_score": 0.20, "reason": "collaborative_match"},
                    {"route_code": "SB-102", "hybrid_score": 0.42,
                     "cf_score": 0.25, "cb_score": 0.18, "reason": "content_match"},
                    {"route_code": "SB-202", "hybrid_score": 0.38,
                     "cf_score": 0.20, "cb_score": 0.18, "reason": "content_match"},
                ]
                return {"recommendations": recs[:n], "is_cold_start": False,
                        "model_version": "1.0.0"}
        return AISemanticBridge(kg=kg, inference_engine=MockEngine())

    def test_enrichment_reranks_above_higher_ml_route(self, kg):
        """
        MEASURABLE IMPROVEMENT: SB-101 has lower ML score than SB-201,
        but after semantic boost (Alice prefers Downtown Terminal) it should
        rank higher than SB-201.
        """
        b = self._bridge_with_mock(kg)
        result = b.recommend_enriched("alice@smartbus.test", n=3)
        codes = [r["route_code"] for r in result["recommendations"]]

        # SB-201 ML=0.72, enriched=0.72 (no boost — from Airport Station)
        # SB-101 ML=0.55, enriched=0.55+0.20+0.15=0.90 (R4+R1)
        assert codes[0] == "SB-101"

    def test_enrichment_stats_report_improvement(self, kg):
        b = self._bridge_with_mock(kg)
        result = b.recommend_enriched("alice@smartbus.test", n=3)
        stats = result["enrichment_stats"]

        assert stats["routes_boosted"] >= 1
        assert stats["average_boost"] > 0
        assert stats["ontology_contributed"] is True

    def test_rank_changes_populated(self, kg):
        b = self._bridge_with_mock(kg)
        result = b.recommend_enriched("alice@smartbus.test", n=3)
        changes = result["enrichment_stats"]["rank_changes"]
        assert isinstance(changes, list)

    def test_kg_triples_added_is_positive(self, kg):
        b = self._bridge_with_mock(kg)
        result = b.recommend_enriched("alice@smartbus.test", n=3)
        assert result["kg_triples_added"] >= 1

    def test_semantic_features_in_response(self, kg):
        b = self._bridge_with_mock(kg)
        result = b.recommend_enriched("alice@smartbus.test", n=3)
        feats = result["semantic_features"]
        assert "candidate_routes" in feats
        assert "frequently_travels" in feats
        assert "SB-101" in feats["candidate_routes"]

    def test_cold_start_user_gets_no_boost(self, kg):
        """Unknown user: no semantic features → all boosts 0 → ML ranking preserved."""
        b = self._bridge_with_mock(kg)
        result = b.recommend_enriched("ghost@nobody.test", n=3)
        for rec in result["recommendations"]:
            assert rec["semantic_boost"] == 0.0


# ── Improvement statistics ────────────────────────────────────────────────────

class TestImprovementStats:
    def test_improvement_stats_structure(self, bridge):
        from semantic.ai_ontology_bridge import _improvement_stats, EnrichedRecommendation
        raw = _make_recs(("SB-101", 0.55), ("SB-201", 0.72))
        enriched = [
            EnrichedRecommendation("SB-101", 0.55, 0.33, 0.22, 0.35, 0.90,
                                   "content_match", ["origin_preference_match", "frequently_travelled"]),
            EnrichedRecommendation("SB-201", 0.72, 0.45, 0.27, 0.0, 0.72,
                                   "collaborative_match", []),
        ]
        stats = _improvement_stats(raw, enriched)

        assert "routes_boosted" in stats
        assert "average_boost" in stats
        assert "max_boost" in stats
        assert "rank_changes" in stats
        assert "ontology_contributed" in stats

    def test_boosted_count_correct(self, bridge):
        from semantic.ai_ontology_bridge import _improvement_stats, EnrichedRecommendation
        raw = _make_recs(("SB-101", 0.55), ("SB-201", 0.72))
        enriched = [
            EnrichedRecommendation("SB-101", 0.55, 0.33, 0.22, 0.35, 0.90,
                                   "content_match", ["origin_preference_match"]),
            EnrichedRecommendation("SB-201", 0.72, 0.45, 0.27, 0.0, 0.72,
                                   "collaborative_match", []),
        ]
        stats = _improvement_stats(raw, enriched)
        assert stats["routes_boosted"] == 1
        assert stats["ontology_contributed"] is True
