"""
SemanticService — high-level query/reasoning interface for Flask endpoints.

Wraps SmartBusKnowledgeGraph and exposes the domain operations required by
the semantic REST endpoints in server.py.  Designed as a singleton that is
loaded once at Flask startup alongside the ML model.
"""

from __future__ import annotations

import time
from typing import Any

import rdflib

from .knowledge_graph import SmartBusKnowledgeGraph, SB, get_knowledge_graph
from .validator import OntologyValidator

SB_NS = "http://smartbus.example.org/ontology#"

# SPARQL template: multi-hop relationship traversal
_FIND_RELATED_SPARQL = """
PREFIX sb: <http://smartbus.example.org/ontology#>
PREFIX rdfs: <http://www.w3.org/2000/01/rdf-schema#>
SELECT DISTINCT ?relatedLabel ?relatedType WHERE {{
    ?entity  rdfs:label|sb:stopName|sb:routeCode|sb:fullName|sb:zoneName  ?lbl .
    FILTER(STR(?lbl) = "{entity_name}")
    ?entity  {predicate}  ?related .
    OPTIONAL {{ ?related  rdfs:label|sb:stopName|sb:routeCode|sb:fullName|sb:zoneName  ?relatedLabel . }}
    OPTIONAL {{ ?related  a  ?relatedType . }}
}}
LIMIT 50
"""

# Inference report query: all inferred facts for a user
_USER_INFERENCE_SPARQL = """
PREFIX sb: <http://smartbus.example.org/ontology#>
SELECT ?predLabel ?routeCode WHERE {{
    ?user  sb:email  "{email}" .
    {{
        ?user  sb:frequentlyTravels  ?route .
        ?route sb:routeCode         ?routeCode .
        BIND("frequentlyTravels" AS ?predLabel)
    }} UNION {{
        ?user  sb:interactedWith  ?route .
        ?route sb:routeCode      ?routeCode .
        BIND("interactedWith" AS ?predLabel)
    }}
}}
ORDER BY ?predLabel ?routeCode
"""

# Confidence scoring for SPARQL-inferred candidates (R4)
_CANDIDATE_CONFIDENCE_SPARQL = """
PREFIX sb: <http://smartbus.example.org/ontology#>
SELECT ?routeCode ?originStop ?price WHERE {{
    ?user  sb:email         "{email}" ;
           sb:prefersOrigin ?origin .
    ?origin sb:stopName     ?originStop .
    ?route  sb:departsFrom  ?origin ;
            sb:routeCode    ?routeCode ;
            sb:unitPrice    ?price .
}}
"""

# Relationship map: user-visible relationship name → SPARQL predicate fragment
RELATIONSHIP_REGISTRY: dict[str, str] = {
    "departsFrom":        "sb:departsFrom",
    "arrivesAt":          "sb:arrivesAt",
    "inZone":             "sb:inZone",
    "inSameZoneAs":       "sb:inSameZoneAs",
    "hasPriceTier":       "sb:hasPriceTier",
    "operatedBy":         "sb:operatedBy",
    "hasSchedule":        "sb:hasSchedule",
    "frequentlyTravels":  "sb:frequentlyTravels",
    "prefersOrigin":      "sb:prefersOrigin",
    "interactedWith":     "sb:interactedWith",
    "hasBooking":         "sb:hasBooking",
    "suggestsRoute":      "sb:suggestsRoute",
    "forUser":            "sb:forUser",
}


class SemanticService:
    """All semantic query and inference operations exposed by the Flask server."""

    def __init__(self, kg: SmartBusKnowledgeGraph | None = None) -> None:
        self._kg = kg or get_knowledge_graph()

    # ── Graph health ──────────────────────────────────────────────────────────

    def graph_stats(self) -> dict[str, Any]:
        s = self._kg.stats()
        report = OntologyValidator(self._kg._g).validate()
        s["ontology_valid"] = report.is_valid
        s["violation_count"] = len(report.violations)
        s["available_relationships"] = sorted(RELATIONSHIP_REGISTRY.keys())
        return s

    # ── Route queries (Q1, Q2) ────────────────────────────────────────────────

    def routes_from_stop(self, stop_name: str) -> list[dict[str, Any]]:
        t0 = time.perf_counter()
        rows = self._kg.routes_from_stop(stop_name)
        return {
            "stop": stop_name,
            "routes": rows,
            "count": len(rows),
            "query_ms": _ms(t0),
            "rule": "Q1 (routes_from_stop)",
        }

    def routes_in_zone(self, stop_name: str) -> dict[str, Any]:
        t0 = time.perf_counter()
        rows = self._kg.routes_in_same_zone(stop_name)
        return {
            "anchor_stop": stop_name,
            "routes": rows,
            "count": len(rows),
            "query_ms": _ms(t0),
            "rule": "Q2 (zone_proximity R3)",
        }

    # ── User inference (R1, R2, R4) ───────────────────────────────────────────

    def candidate_routes(self, email: str) -> dict[str, Any]:
        """R4: routes departing from the user's preferred origin stop."""
        t0 = time.perf_counter()
        safe_email = email.replace('"', "").replace("\\", "")
        results = list(self._kg.query(
            _CANDIDATE_CONFIDENCE_SPARQL.format(email=safe_email)
        ))
        candidates = [
            {
                "route_code":  str(r.routeCode),
                "origin_stop": str(r.originStop),
                "price":       float(r.price),
                "confidence":  _candidate_confidence(float(r.price)),
                "rule":        "R4 (prefersOrigin → departsFrom)",
            }
            for r in results
        ]
        return {
            "email":      email,
            "candidates": candidates,
            "count":      len(candidates),
            "query_ms":   _ms(t0),
        }

    def user_insights(self, email: str) -> dict[str, Any]:
        """Combined inference summary for a user: R1 + R2 + R4 + stored recommendations."""
        t0 = time.perf_counter()

        frequent  = self._kg.frequently_travelled_routes(email)
        candidates = self._kg.candidate_routes_for_user(email)
        stored_recs = self._kg.recommendations_for_email(email)

        safe_email = email.replace('"', "").replace("\\", "")
        # Inferred interactions (property chain)
        interacted_results = list(self._kg.query(
            _USER_INFERENCE_SPARQL.format(email=safe_email)
        ))
        inferred_facts = [
            {"relationship": str(r.predLabel), "route_code": str(r.routeCode)}
            for r in interacted_results
        ]

        return {
            "email": email,
            "inferred": {
                "frequently_travels":  frequent,
                "candidate_routes":    candidates,
                "inferred_facts_count": len(inferred_facts),
                "inferred_facts":      inferred_facts,
            },
            "stored_recommendations": stored_recs,
            "total_query_ms": _ms(t0),
        }

    # ── Generic entity–relationship traversal ─────────────────────────────────

    def find_related(self, entity_name: str, relationship: str) -> dict[str, Any]:
        """Multi-hop entity lookup by relationship name (RELATIONSHIP_REGISTRY)."""
        t0 = time.perf_counter()

        if relationship not in RELATIONSHIP_REGISTRY:
            return {
                "error": f"Unknown relationship '{relationship}'. "
                         f"Valid values: {sorted(RELATIONSHIP_REGISTRY.keys())}",
            }

        predicate = RELATIONSHIP_REGISTRY[relationship]
        sparql = _FIND_RELATED_SPARQL.format(
            entity_name=entity_name.replace('"', ""),
            predicate=predicate,
        )

        results = list(self._kg.query(sparql))
        related = []
        seen: set[str] = set()
        for r in results:
            label = str(r.relatedLabel) if r.relatedLabel else None
            rtype = str(r.relatedType).replace(SB_NS, "sb:") if r.relatedType else None
            key = f"{label}|{rtype}"
            if key not in seen:
                seen.add(key)
                related.append({"label": label, "type": rtype})

        return {
            "entity":       entity_name,
            "relationship": relationship,
            "predicate":    predicate,
            "related":      related,
            "count":        len(related),
            "query_ms":     _ms(t0),
        }

    # ── Raw SPARQL (SELECT only, for demo/admin) ──────────────────────────────

    def execute_sparql(self, sparql: str) -> dict[str, Any]:
        """Execute an arbitrary SPARQL SELECT and return JSON-serialisable rows."""
        sparql_upper = sparql.strip().upper()
        if not sparql_upper.startswith("SELECT") and not sparql_upper.startswith("PREFIX"):
            return {"error": "Only SELECT queries are permitted."}

        # reject CONSTRUCT / UPDATE / DELETE / DROP
        for forbidden in ("INSERT", "DELETE", "DROP", "CREATE", "CONSTRUCT", "UPDATE"):
            if forbidden in sparql_upper:
                return {"error": f"Query type not permitted: {forbidden}"}

        t0 = time.perf_counter()
        try:
            results = self._kg.query(sparql)
        except Exception as exc:
            return {"error": f"SPARQL parse error: {exc}"}

        vars_ = [str(v) for v in results.vars] if hasattr(results, "vars") and results.vars else []
        rows = []
        for row in results:
            record: dict[str, str | None] = {}
            for var in vars_:
                val = getattr(row, var, None)
                record[var] = str(val) if val is not None else None
            rows.append(record)

        return {
            "variables": vars_,
            "rows":      rows,
            "count":     len(rows),
            "query_ms":  _ms(t0),
        }


# ── Helpers ───────────────────────────────────────────────────────────────────

def _ms(t0: float) -> float:
    return round((time.perf_counter() - t0) * 1000, 2)


def _candidate_confidence(price: float) -> str:
    """Simple heuristic: route tier → candidate confidence label."""
    if price >= 15.0:
        return "HIGH"
    if price >= 10.0:
        return "MEDIUM"
    return "LOW"


# ── Module-level singleton ────────────────────────────────────────────────────

_service: SemanticService | None = None


def get_semantic_service() -> SemanticService:
    global _service
    if _service is None:
        _service = SemanticService()
    return _service
