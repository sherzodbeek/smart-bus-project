"""
SmartBus Knowledge Graph — semantic data layer backed by RDFLib.

Loads the OWL 2 ontology (TBox) and sample instance data (ABox), applies
SPARQL-based inference rules, and exposes a query interface used by both
the Flask server (Task 06 semantic endpoints) and the AI-ontology integration
layer (Task 07).
"""

from __future__ import annotations

import sys
from pathlib import Path
from typing import Any

import rdflib
from rdflib import Graph, Namespace, URIRef
from rdflib.namespace import OWL, RDF, RDFS, XSD

SB = Namespace("http://smartbus.example.org/ontology#")

# Resolve ontology files relative to this module regardless of CWD
_THIS_DIR = Path(__file__).parent
_PROJECT_ROOT = _THIS_DIR.parent.parent
_ONTOLOGY_FILE = _PROJECT_ROOT / "ontology" / "smartbus-ontology.ttl"
_DATA_FILE = _PROJECT_ROOT / "ontology" / "sample-data.ttl"

# ─────────────────────────────────────────────────────────────────────────────
# SPARQL CONSTRUCT rule: R3 — Zone Proximity
# Asserts inSameZoneAs for every pair of stops sharing a zone.
# ─────────────────────────────────────────────────────────────────────────────
_RULE_R3_CONSTRUCT = """
PREFIX sb: <http://smartbus.example.org/ontology#>
CONSTRUCT { ?s1 sb:inSameZoneAs ?s2 }
WHERE {
    ?s1 sb:inZone ?zone .
    ?s2 sb:inZone ?zone .
    FILTER (?s1 != ?s2)
}
"""

# SPARQL CONSTRUCT rule: interactedWith (property chain approximation for RDFLib)
_RULE_CHAIN_CONSTRUCT = """
PREFIX sb: <http://smartbus.example.org/ontology#>
CONSTRUCT { ?user sb:interactedWith ?route }
WHERE {
    ?user sb:hasBooking ?booking .
    ?booking sb:onRoute ?route .
}
"""


class SmartBusKnowledgeGraph:
    """RDFLib-backed knowledge graph for the SmartBus domain."""

    def __init__(self, ontology_path: Path = _ONTOLOGY_FILE,
                 data_path: Path | None = _DATA_FILE) -> None:
        self._g: Graph = Graph()
        self._g.bind("sb", SB)
        self._g.bind("owl", OWL)
        self._g.bind("rdfs", RDFS)
        self._g.bind("xsd", XSD)
        self._loaded_ontology = False
        self._loaded_data = False

        self.load_ontology(ontology_path)
        if data_path and data_path.exists():
            self.load_data(data_path)
            self.apply_inference_rules()

    # ── Loading ──────────────────────────────────────────────────────────────

    def load_ontology(self, path: Path) -> None:
        if not path.exists():
            raise FileNotFoundError(f"Ontology file not found: {path}")
        self._g.parse(str(path), format="turtle")
        self._loaded_ontology = True

    def load_data(self, path: Path) -> None:
        if not path.exists():
            raise FileNotFoundError(f"Data file not found: {path}")
        before = len(self._g)
        self._g.parse(str(path), format="turtle")
        self._loaded_data = True
        self._data_triple_count = len(self._g) - before

    def load_rdf_string(self, ttl_text: str) -> None:
        """Load additional triples from a Turtle string (used by Task 07)."""
        self._g.parse(data=ttl_text, format="turtle")

    # ── Inference rules ──────────────────────────────────────────────────────

    def apply_inference_rules(self) -> dict[str, int]:
        """Apply all SPARQL CONSTRUCT rules and add inferred triples to the graph.

        Returns a dict with counts of triples added per rule.
        """
        counts: dict[str, int] = {}

        r3_result = self._g.query(_RULE_R3_CONSTRUCT)
        before = len(self._g)
        for triple in r3_result:
            self._g.add(triple)
        counts["R3_zone_proximity"] = len(self._g) - before

        chain_result = self._g.query(_RULE_CHAIN_CONSTRUCT)
        before = len(self._g)
        for triple in chain_result:
            self._g.add(triple)
        counts["chain_interactedWith"] = len(self._g) - before

        return counts

    # ── Query helpers ─────────────────────────────────────────────────────────

    def query(self, sparql: str) -> rdflib.query.Result:
        """Execute a raw SPARQL SELECT/CONSTRUCT against the graph."""
        return self._g.query(sparql)

    def routes_from_stop(self, stop_name: str) -> list[dict[str, Any]]:
        """Q1: All routes departing from a named stop."""
        results = self._g.query(
            """
            PREFIX sb: <http://smartbus.example.org/ontology#>
            SELECT ?route ?routeCode ?destination ?price WHERE {
                ?stop  sb:stopName    ?sn .
                ?route sb:departsFrom ?stop ;
                       sb:routeCode   ?routeCode ;
                       sb:unitPrice   ?price ;
                       sb:arrivesAt   ?dest .
                ?dest  sb:stopName    ?destination .
                FILTER(STR(?sn) = ?stopLabel)
            }
            """,
            initBindings={"stopLabel": rdflib.Literal(stop_name)},
        )
        return [
            {
                "route_uri": str(r.route),
                "route_code": str(r.routeCode),
                "destination": str(r.destination),
                "price": float(r.price),
            }
            for r in results
        ]

    def routes_in_same_zone(self, stop_name: str) -> list[dict[str, Any]]:
        """Q2: Routes departing from any stop in the same zone as the named stop."""
        results = self._g.query(
            """
            PREFIX sb: <http://smartbus.example.org/ontology#>
            SELECT DISTINCT ?route ?routeCode ?originName WHERE {
                ?anchor sb:stopName  ?sn ;
                        sb:inZone    ?zone .
                ?peer   sb:inZone    ?zone ;
                        sb:stopName  ?originName .
                ?route  sb:departsFrom ?peer ;
                        sb:routeCode   ?routeCode .
                FILTER(STR(?sn) = ?stopLabel && ?peer != ?anchor)
            }
            """,
            initBindings={"stopLabel": rdflib.Literal(stop_name)},
        )
        return [
            {
                "route_code": str(r.routeCode),
                "origin_stop": str(r.originName),
            }
            for r in results
        ]

    def recommendations_for_email(self, email: str) -> list[dict[str, Any]]:
        """Q3: All stored recommendations for a user identified by email."""
        results = self._g.query(
            """
            PREFIX sb: <http://smartbus.example.org/ontology#>
            SELECT ?routeCode ?score ?confidence ?reason WHERE {
                ?rec  sb:forUser       ?user ;
                      sb:suggestsRoute ?route ;
                      sb:confidenceScore ?score ;
                      sb:confidenceLevel ?confidence ;
                      sb:reasonCode      ?reason .
                ?user sb:email ?em .
                ?route sb:routeCode ?routeCode .
                FILTER(STR(?em) = ?emailVal)
            }
            ORDER BY DESC(?score)
            """,
            initBindings={"emailVal": rdflib.Literal(email)},
        )
        return [
            {
                "route_code": str(r.routeCode),
                "score": float(r.score),
                "confidence": str(r.confidence),
                "reason": str(r.reason),
            }
            for r in results
        ]

    def candidate_routes_for_user(self, email: str) -> list[str]:
        """R4: Routes departing from the user's preferred origin stop."""
        results = self._g.query(
            """
            PREFIX sb: <http://smartbus.example.org/ontology#>
            SELECT ?routeCode WHERE {
                ?user  sb:email         ?em ;
                       sb:prefersOrigin ?stop .
                ?route sb:departsFrom   ?stop ;
                       sb:routeCode     ?routeCode .
                FILTER(STR(?em) = ?emailVal)
            }
            """,
            initBindings={"emailVal": rdflib.Literal(email)},
        )
        return [str(r.routeCode) for r in results]

    def frequently_travelled_routes(self, email: str) -> list[str]:
        """R1 result: Routes the user has booked ≥3 times."""
        results = self._g.query(
            """
            PREFIX sb: <http://smartbus.example.org/ontology#>
            SELECT ?routeCode WHERE {
                ?user sb:email            ?em ;
                      sb:frequentlyTravels ?route .
                ?route sb:routeCode ?routeCode .
                FILTER(STR(?em) = ?emailVal)
            }
            """,
            initBindings={"emailVal": rdflib.Literal(email)},
        )
        return [str(r.routeCode) for r in results]

    def assert_recommendation(self, rec_id: str, email: str,
                               route_code: str, score: float,
                               confidence: str, reason: str) -> None:
        """Add a new Recommendation triple set to the live graph (Task 07)."""
        user_uri = self._user_uri_for_email(email)
        if user_uri is None:
            return

        route_uri = self._route_uri_for_code(route_code)
        if route_uri is None:
            return

        rec_uri = URIRef(f"{SB}Rec_{rec_id}")
        self._g.add((rec_uri, RDF.type, SB.Recommendation))
        self._g.add((rec_uri, SB.forUser, user_uri))
        self._g.add((rec_uri, SB.suggestsRoute, route_uri))
        self._g.add((rec_uri, SB.confidenceScore,
                     rdflib.Literal(score, datatype=XSD.decimal)))
        self._g.add((rec_uri, SB.confidenceLevel,
                     rdflib.Literal(confidence, datatype=XSD.string)))
        self._g.add((rec_uri, SB.reasonCode,
                     rdflib.Literal(reason, datatype=XSD.string)))

    # ── Stats ─────────────────────────────────────────────────────────────────

    def stats(self) -> dict[str, Any]:
        """Summary counts useful for health checks and validation."""

        def count(q: str) -> int:
            r = list(self._g.query(q))
            return int(r[0][0]) if r else 0

        return {
            "total_triples": len(self._g),
            "classes": count(
                "SELECT (COUNT(DISTINCT ?c) AS ?n) WHERE { ?c a <http://www.w3.org/2002/07/owl#Class> . }"
            ),
            "users": count(
                "PREFIX sb: <http://smartbus.example.org/ontology#> "
                "SELECT (COUNT(?u) AS ?n) WHERE { ?u a sb:User . }"
            ),
            "routes": count(
                "PREFIX sb: <http://smartbus.example.org/ontology#> "
                "SELECT (COUNT(?r) AS ?n) WHERE { ?r a sb:Route . }"
            ),
            "bookings": count(
                "PREFIX sb: <http://smartbus.example.org/ontology#> "
                "SELECT (COUNT(?b) AS ?n) WHERE { ?b a sb:Booking . }"
            ),
            "recommendations": count(
                "PREFIX sb: <http://smartbus.example.org/ontology#> "
                "SELECT (COUNT(?r) AS ?n) WHERE { ?r a sb:Recommendation . }"
            ),
        }

    # ── Internal helpers ──────────────────────────────────────────────────────

    def _user_uri_for_email(self, email: str) -> URIRef | None:
        results = list(self._g.query(
            "PREFIX sb: <http://smartbus.example.org/ontology#> "
            "SELECT ?u WHERE { ?u sb:email ?e . FILTER(STR(?e) = ?ev) }",
            initBindings={"ev": rdflib.Literal(email)},
        ))
        return URIRef(str(results[0][0])) if results else None

    def _route_uri_for_code(self, route_code: str) -> URIRef | None:
        results = list(self._g.query(
            "PREFIX sb: <http://smartbus.example.org/ontology#> "
            "SELECT ?r WHERE { ?r sb:routeCode ?c . FILTER(STR(?c) = ?cv) }",
            initBindings={"cv": rdflib.Literal(route_code)},
        ))
        return URIRef(str(results[0][0])) if results else None


# ── Module-level singleton (lazy) ─────────────────────────────────────────────

_instance: SmartBusKnowledgeGraph | None = None


def get_knowledge_graph() -> SmartBusKnowledgeGraph:
    """Return the module-level singleton, creating it on first call."""
    global _instance
    if _instance is None:
        _instance = SmartBusKnowledgeGraph()
    return _instance


# ── CLI smoke test ─────────────────────────────────────────────────────────────

if __name__ == "__main__":
    print("Loading SmartBus knowledge graph…")
    kg = SmartBusKnowledgeGraph()
    s = kg.stats()
    print(f"  Total triples  : {s['total_triples']}")
    print(f"  Classes        : {s['classes']}")
    print(f"  Users          : {s['users']}")
    print(f"  Routes         : {s['routes']}")
    print(f"  Bookings       : {s['bookings']}")
    print(f"  Recommendations: {s['recommendations']}")

    print("\nRoutes from Downtown Terminal:")
    for r in kg.routes_from_stop("Downtown Terminal"):
        print(f"  {r['route_code']}  →  {r['destination']}  (${r['price']})")

    print("\nCandidate routes for alice@smartbus.test:")
    for code in kg.candidate_routes_for_user("alice@smartbus.test"):
        print(f"  {code}")

    print("\nFrequently travelled by alice@smartbus.test:")
    for code in kg.frequently_travelled_routes("alice@smartbus.test"):
        print(f"  {code}")

    print("\nStored recommendations for alice@smartbus.test:")
    for rec in kg.recommendations_for_email("alice@smartbus.test"):
        print(f"  {rec['route_code']}  score={rec['score']}  "
              f"confidence={rec['confidence']}  reason={rec['reason']}")
