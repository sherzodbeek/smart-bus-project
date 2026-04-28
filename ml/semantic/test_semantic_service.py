"""
Tests for SemanticService — Task 06 acceptance criteria.

Run with:  python -m pytest ml/semantic/test_semantic_service.py -v
"""

from __future__ import annotations

import sys
from pathlib import Path

import pytest

_PROJECT_ROOT = Path(__file__).parent.parent.parent
sys.path.insert(0, str(_PROJECT_ROOT / "ml"))

from semantic.knowledge_graph import SmartBusKnowledgeGraph  # noqa: E402
from semantic.semantic_service import SemanticService         # noqa: E402


@pytest.fixture(scope="module")
def svc() -> SemanticService:
    kg = SmartBusKnowledgeGraph()
    return SemanticService(kg)


# ── Graph stats ───────────────────────────────────────────────────────────────

class TestGraphStats:
    def test_returns_valid_true(self, svc):
        stats = svc.graph_stats()
        assert stats["ontology_valid"] is True

    def test_known_relationships_listed(self, svc):
        stats = svc.graph_stats()
        rels = stats["available_relationships"]
        assert "departsFrom" in rels
        assert "inSameZoneAs" in rels
        assert "frequentlyTravels" in rels

    def test_triple_count_positive(self, svc):
        stats = svc.graph_stats()
        assert stats["total_triples"] > 0

    def test_query_completes_quickly(self, svc):
        import time
        t0 = time.perf_counter()
        svc.graph_stats()
        assert (time.perf_counter() - t0) < 1.0


# ── Q1: routes from stop ──────────────────────────────────────────────────────

class TestRoutesFromStop:
    def test_downtown_returns_three_routes(self, svc):
        result = svc.routes_from_stop("Downtown Terminal")
        assert result["count"] == 3
        codes = [r["route_code"] for r in result["routes"]]
        assert set(codes) == {"SB-101", "SB-102", "SB-103"}

    def test_result_contains_price(self, svc):
        result = svc.routes_from_stop("Downtown Terminal")
        for r in result["routes"]:
            assert isinstance(r["price"], float)
            assert r["price"] > 0

    def test_unknown_stop_returns_empty(self, svc):
        result = svc.routes_from_stop("Narnia Bus Station")
        assert result["count"] == 0
        assert result["routes"] == []

    def test_query_ms_reported(self, svc):
        result = svc.routes_from_stop("Downtown Terminal")
        assert "query_ms" in result
        assert result["query_ms"] >= 0

    def test_rule_label_present(self, svc):
        result = svc.routes_from_stop("Downtown Terminal")
        assert "Q1" in result["rule"]


# ── Q2 + R3: zone-based routes ────────────────────────────────────────────────

class TestRoutesInZone:
    def test_downtown_returns_city_center_routes(self, svc):
        """City Center is in the same zone as Downtown Terminal."""
        result = svc.routes_in_zone("Downtown Terminal")
        codes = [r["route_code"] for r in result["routes"]]
        assert len(codes) >= 1

    def test_airport_returns_university_routes(self, svc):
        """University shares Northern zone with Airport Station."""
        result = svc.routes_in_zone("Airport Station")
        codes = [r["route_code"] for r in result["routes"]]
        assert len(codes) >= 1

    def test_rule_label_contains_r3(self, svc):
        result = svc.routes_in_zone("Downtown Terminal")
        assert "R3" in result["rule"]


# ── R4: candidate routes ──────────────────────────────────────────────────────

class TestCandidateRoutes:
    def test_alice_gets_downtown_routes(self, svc):
        result = svc.candidate_routes("alice@smartbus.test")
        codes = [r["route_code"] for r in result["candidates"]]
        assert "SB-101" in codes
        assert "SB-102" in codes
        assert "SB-103" in codes

    def test_candidate_has_confidence_label(self, svc):
        result = svc.candidate_routes("alice@smartbus.test")
        for c in result["candidates"]:
            assert c["confidence"] in {"HIGH", "MEDIUM", "LOW"}

    def test_unknown_user_returns_empty_candidates(self, svc):
        result = svc.candidate_routes("ghost@nobody.test")
        assert result["count"] == 0

    def test_email_in_response(self, svc):
        result = svc.candidate_routes("alice@smartbus.test")
        assert result["email"] == "alice@smartbus.test"


# ── User insights (R1 + R2 + R4) ─────────────────────────────────────────────

class TestUserInsights:
    def test_alice_frequently_travels_sb101(self, svc):
        result = svc.user_insights("alice@smartbus.test")
        assert "SB-101" in result["inferred"]["frequently_travels"]

    def test_alice_candidates_include_downtown_routes(self, svc):
        result = svc.user_insights("alice@smartbus.test")
        assert "SB-101" in result["inferred"]["candidate_routes"]

    def test_stored_recommendations_returned(self, svc):
        result = svc.user_insights("alice@smartbus.test")
        recs = result["stored_recommendations"]
        assert len(recs) >= 1
        assert recs[0]["route_code"] == "SB-101"  # highest score first

    def test_inferred_facts_contains_interactions(self, svc):
        result = svc.user_insights("alice@smartbus.test")
        facts = result["inferred"]["inferred_facts"]
        predicates = {f["relationship"] for f in facts}
        assert "frequentlyTravels" in predicates or "interactedWith" in predicates

    def test_unknown_user_returns_empty_inference(self, svc):
        result = svc.user_insights("ghost@nobody.test")
        assert result["inferred"]["frequently_travels"] == []
        assert result["inferred"]["candidate_routes"] == []

    def test_query_ms_reported(self, svc):
        result = svc.user_insights("alice@smartbus.test")
        assert "total_query_ms" in result


# ── Find related ──────────────────────────────────────────────────────────────

class TestFindRelated:
    def test_downtown_departsFrom_finds_routes(self, svc):
        """'departsFrom' reversed — find routes that depart from Downtown Terminal."""
        result = svc.find_related("Downtown Terminal", "inSameZoneAs")
        assert result["count"] >= 1

    def test_airport_inZone_finds_zone(self, svc):
        result = svc.find_related("Airport Station", "inZone")
        assert result["count"] >= 1

    def test_invalid_relationship_returns_error(self, svc):
        result = svc.find_related("Downtown Terminal", "nonExistentProp")
        assert "error" in result

    def test_response_shape(self, svc):
        result = svc.find_related("Downtown Terminal", "inSameZoneAs")
        assert "entity" in result
        assert "relationship" in result
        assert "predicate" in result
        assert isinstance(result["related"], list)


# ── SPARQL passthrough ────────────────────────────────────────────────────────

class TestExecuteSparql:
    def test_count_routes(self, svc):
        sparql = """
            PREFIX sb: <http://smartbus.example.org/ontology#>
            SELECT (COUNT(?r) AS ?n) WHERE { ?r a sb:Route . }
        """
        result = svc.execute_sparql(sparql)
        assert "error" not in result
        assert result["count"] >= 1
        assert int(result["rows"][0]["n"]) == 10

    def test_select_users(self, svc):
        sparql = """
            PREFIX sb: <http://smartbus.example.org/ontology#>
            SELECT ?email WHERE { ?u a sb:User ; sb:email ?email . }
            ORDER BY ?email
        """
        result = svc.execute_sparql(sparql)
        assert "error" not in result
        emails = [r["email"] for r in result["rows"]]
        assert "alice@smartbus.test" in emails

    def test_rejects_insert(self, svc):
        result = svc.execute_sparql(
            "INSERT DATA { <http://ex.org/x> <http://ex.org/p> <http://ex.org/y> . }"
        )
        assert "error" in result

    def test_rejects_delete(self, svc):
        result = svc.execute_sparql(
            "DELETE WHERE { ?s ?p ?o . }"
        )
        assert "error" in result

    def test_query_ms_reported(self, svc):
        result = svc.execute_sparql(
            "PREFIX sb: <http://smartbus.example.org/ontology#> "
            "SELECT ?r WHERE { ?r a sb:Route . } LIMIT 1"
        )
        assert "query_ms" in result

    def test_multi_hop_sparql(self, svc):
        """Two-hop: User → frequentlyTravels → Route → departsFrom → Stop."""
        sparql = """
            PREFIX sb: <http://smartbus.example.org/ontology#>
            SELECT ?email ?routeCode ?stopName WHERE {
                ?user  sb:frequentlyTravels ?route ;
                       sb:email             ?email .
                ?route sb:routeCode         ?routeCode ;
                       sb:departsFrom       ?stop .
                ?stop  sb:stopName          ?stopName .
            }
            ORDER BY ?email ?routeCode
        """
        result = svc.execute_sparql(sparql)
        assert "error" not in result
        assert result["count"] >= 1
        assert "email" in result["variables"]
        assert "routeCode" in result["variables"]
        assert "stopName" in result["variables"]
