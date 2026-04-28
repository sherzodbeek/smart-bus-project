"""
Tests for SmartBus semantic data layer — Task 05 acceptance criteria.

Run with:  python -m pytest ml/semantic/test_knowledge_graph.py -v
"""

from __future__ import annotations

import sys
from pathlib import Path

import pytest
import rdflib
from rdflib import Namespace

SB = Namespace("http://smartbus.example.org/ontology#")

# Allow imports from project root
_PROJECT_ROOT = Path(__file__).parent.parent.parent
sys.path.insert(0, str(_PROJECT_ROOT))

from ml.semantic.knowledge_graph import SmartBusKnowledgeGraph  # noqa: E402
from ml.semantic.validator import OntologyValidator              # noqa: E402


@pytest.fixture(scope="module")
def kg() -> SmartBusKnowledgeGraph:
    return SmartBusKnowledgeGraph()


@pytest.fixture(scope="module")
def validator(kg: SmartBusKnowledgeGraph) -> OntologyValidator:
    return OntologyValidator(kg._g)


# ── Ontology loading ──────────────────────────────────────────────────────────

class TestOntologyLoading:
    def test_parses_without_error(self, kg):
        assert kg._loaded_ontology

    def test_minimum_triple_count(self, kg):
        assert len(kg._g) >= 400

    def test_ten_classes_declared(self, kg):
        count = sum(
            1 for _ in kg._g.subjects(
                rdflib.RDF.type, rdflib.OWL.Class
            )
        )
        assert count >= 10

    def test_object_properties_declared(self, kg):
        count = sum(
            1 for _ in kg._g.subjects(
                rdflib.RDF.type, rdflib.OWL.ObjectProperty
            )
        )
        assert count >= 15

    def test_data_properties_declared(self, kg):
        count = sum(
            1 for _ in kg._g.subjects(
                rdflib.RDF.type, rdflib.OWL.DatatypeProperty
            )
        )
        assert count >= 20


# ── Sample data coverage ──────────────────────────────────────────────────────

class TestSampleDataCoverage:
    def test_five_users_loaded(self, kg):
        assert kg.stats()["users"] == 5

    def test_ten_routes_loaded(self, kg):
        assert kg.stats()["routes"] == 10

    def test_bookings_loaded(self, kg):
        assert kg.stats()["bookings"] >= 10

    def test_recommendations_loaded(self, kg):
        assert kg.stats()["recommendations"] >= 5

    def test_all_entity_types_present(self, kg):
        for cls_uri in [
            SB.User, SB.Route, SB.Stop, SB.Bus,
            SB.Booking, SB.Payment, SB.Schedule,
            SB.Recommendation, SB.PriceTier, SB.RouteZone,
        ]:
            count = sum(1 for _ in kg._g.subjects(rdflib.RDF.type, cls_uri))
            assert count >= 1, f"No instances of {cls_uri.fragment} found"


# ── Inference rules ───────────────────────────────────────────────────────────

class TestInferenceRules:
    def test_r3_zone_proximity_inferred(self, kg):
        """Rule R3: Downtown Terminal and City Center share Central zone."""
        downtown = SB.Stop_DowntownTerminal
        city_center = SB.Stop_CityCenter
        assert (downtown, SB.inSameZoneAs, city_center) in kg._g

    def test_r3_symmetry(self, kg):
        """inSameZoneAs is symmetric — both directions must hold."""
        downtown = SB.Stop_DowntownTerminal
        city_center = SB.Stop_CityCenter
        assert (city_center, SB.inSameZoneAs, downtown) in kg._g

    def test_r3_northern_zone(self, kg):
        """Airport Station and University share Northern zone."""
        assert (SB.Stop_AirportStation, SB.inSameZoneAs, SB.Stop_University) in kg._g

    def test_chain_interacted_with(self, kg):
        """Property chain: Alice booked SB-101 → Alice interactedWith Route_SB101."""
        alice = SB.User_Alice
        route = SB.Route_SB101
        assert (alice, SB.interactedWith, route) in kg._g

    def test_r1_frequently_travels(self, kg):
        """Alice has ≥3 bookings on SB-101 → frequentlyTravels asserted."""
        assert (SB.User_Alice, SB.frequentlyTravels, SB.Route_SB101) in kg._g

    def test_r2_prefers_origin(self, kg):
        """Alice's preferred origin is Downtown Terminal."""
        assert (SB.User_Alice, SB.prefersOrigin, SB.Stop_DowntownTerminal) in kg._g


# ── SPARQL queries ────────────────────────────────────────────────────────────

class TestSparqlQueries:
    def test_routes_from_downtown(self, kg):
        results = kg.routes_from_stop("Downtown Terminal")
        codes = [r["route_code"] for r in results]
        assert "SB-101" in codes
        assert "SB-102" in codes
        assert "SB-103" in codes

    def test_zone_routes_for_downtown(self, kg):
        """City Center is in same zone as Downtown → its departing routes returned."""
        results = kg.routes_in_same_zone("Downtown Terminal")
        codes = [r["route_code"] for r in results]
        assert len(codes) >= 1

    def test_recommendations_for_alice(self, kg):
        recs = kg.recommendations_for_email("alice@smartbus.test")
        assert len(recs) >= 1
        codes = [r["route_code"] for r in recs]
        assert "SB-101" in codes

    def test_candidate_routes_for_alice(self, kg):
        """Alice prefers Downtown Terminal → R4 returns Downtown-departing routes."""
        candidates = kg.candidate_routes_for_user("alice@smartbus.test")
        assert "SB-101" in candidates

    def test_unknown_user_returns_empty(self, kg):
        recs = kg.recommendations_for_email("nobody@unknown.test")
        assert recs == []


# ── Dynamic recommendation assertion ─────────────────────────────────────────

class TestAssertRecommendation:
    def test_assert_adds_triples(self, kg):
        before = kg.stats()["recommendations"]
        kg.assert_recommendation(
            rec_id="TEST_Eve_SB101",
            email="eve@smartbus.test",
            route_code="SB-101",
            score=0.55,
            confidence="MEDIUM",
            reason="content_match",
        )
        after = kg.stats()["recommendations"]
        assert after == before + 1

    def test_assert_unknown_user_is_noop(self, kg):
        before = kg.stats()["recommendations"]
        kg.assert_recommendation(
            rec_id="TEST_ghost",
            email="ghost@unknown.test",
            route_code="SB-101",
            score=0.5,
            confidence="MEDIUM",
            reason="content_match",
        )
        after = kg.stats()["recommendations"]
        assert after == before  # no change for unknown user


# ── Validation ────────────────────────────────────────────────────────────────

class TestValidation:
    def test_no_violations_on_clean_data(self, validator):
        report = validator.validate()
        assert report.is_valid, f"Violations: {report.violations}"

    def test_functional_property_violation_detected(self, kg):
        """Inject a duplicate email to trigger a functional property violation."""
        import rdflib as _rdflib
        alice = SB.User_Alice
        duplicate_email = _rdflib.Literal("alice2@smartbus.test")
        kg._g.add((alice, SB.email, duplicate_email))
        try:
            v = OntologyValidator(kg._g)
            report = v.validate()
            func_violations = [
                x for x in report.violations
                if x.rule == "functional_property" and "email" in x.predicate
            ]
            assert len(func_violations) >= 1
        finally:
            kg._g.remove((alice, SB.email, duplicate_email))
