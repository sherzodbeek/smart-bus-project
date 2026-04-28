"""
OntologyValidator — RDF constraint checking for the SmartBus knowledge graph.

Validates that instance data conforms to the ontology's declared domain,
range, and functional property constraints.  Used during data loading and
as a health check endpoint in the Flask server.
"""

from __future__ import annotations

from dataclasses import dataclass, field
from typing import Any

import rdflib
from rdflib import Graph, Namespace, URIRef
from rdflib.namespace import OWL, RDF, RDFS, XSD

SB = Namespace("http://smartbus.example.org/ontology#")


@dataclass
class Violation:
    rule: str
    subject: str
    predicate: str
    detail: str

    def __str__(self) -> str:
        return f"[{self.rule}] {self.subject} — {self.predicate}: {self.detail}"


@dataclass
class ValidationReport:
    violations: list[Violation] = field(default_factory=list)

    @property
    def is_valid(self) -> bool:
        return len(self.violations) == 0

    def summary(self) -> dict[str, Any]:
        return {
            "valid": self.is_valid,
            "violation_count": len(self.violations),
            "violations": [str(v) for v in self.violations],
        }


class OntologyValidator:
    """Validates an RDFLib graph against the SmartBus OWL 2 ontology constraints."""

    # Functional object properties that must have at most one value per subject.
    # Derived from the ontology's owl:FunctionalProperty declarations.
    FUNCTIONAL_OBJECT_PROPERTIES: list[URIRef] = [
        SB.paidWith,
        SB.departsFrom,
        SB.arrivesAt,
        SB.inZone,
        SB.hasPriceTier,
        SB.forUser,
        SB.suggestsRoute,
    ]

    # Functional datatype properties
    FUNCTIONAL_DATA_PROPERTIES: list[URIRef] = [
        SB.email,
        SB.routeCode,
        SB.stopName,
        SB.zoneName,
        SB.busPlate,
        SB.transactionId,
        SB.bookingReference,
        SB.tierLabel,
    ]

    # Expected domain class per property (spot-check coverage)
    DOMAIN_MAP: dict[URIRef, URIRef] = {
        SB.hasBooking:        SB.User,
        SB.bookedBy:          SB.Booking,
        SB.prefersOrigin:     SB.User,
        SB.frequentlyTravels: SB.User,
        SB.onRoute:           SB.Booking,
        SB.paidWith:          SB.Booking,
        SB.departsFrom:       SB.Route,
        SB.arrivesAt:         SB.Route,
        SB.inZone:            SB.Stop,
        SB.operatedBy:        SB.Route,
        SB.hasSchedule:       SB.Route,
        SB.hasPriceTier:      SB.Route,
        SB.forUser:           SB.Recommendation,
        SB.suggestsRoute:     SB.Recommendation,
    }

    # Expected range class per property
    RANGE_MAP: dict[URIRef, URIRef] = {
        SB.hasBooking:        SB.Booking,
        SB.bookedBy:          SB.User,
        SB.prefersOrigin:     SB.Stop,
        SB.frequentlyTravels: SB.Route,
        SB.onRoute:           SB.Route,
        SB.paidWith:          SB.Payment,
        SB.departsFrom:       SB.Stop,
        SB.arrivesAt:         SB.Stop,
        SB.inZone:            SB.RouteZone,
        SB.operatedBy:        SB.Bus,
        SB.hasSchedule:       SB.Schedule,
        SB.hasPriceTier:      SB.PriceTier,
        SB.forUser:           SB.User,
        SB.suggestsRoute:     SB.Route,
    }

    def __init__(self, graph: Graph) -> None:
        self._g = graph

    def validate(self) -> ValidationReport:
        report = ValidationReport()
        self._check_functional_properties(report)
        self._check_domains(report)
        self._check_ranges(report)
        self._check_required_properties(report)
        return report

    # ── Individual checks ─────────────────────────────────────────────────────

    def _check_functional_properties(self, report: ValidationReport) -> None:
        """Detect subjects with more than one value for a functional property."""
        for prop in self.FUNCTIONAL_OBJECT_PROPERTIES + self.FUNCTIONAL_DATA_PROPERTIES:
            result = self._g.query(
                """
                SELECT ?s (COUNT(?o) AS ?cnt) WHERE {
                    ?s ?p ?o .
                }
                GROUP BY ?s
                HAVING (COUNT(?o) > 1)
                """,
                initBindings={"p": prop},
            )
            for row in result:
                report.violations.append(Violation(
                    rule="functional_property",
                    subject=str(row.s),
                    predicate=str(prop),
                    detail=f"has {row.cnt} values; expected ≤1",
                ))

    def _check_domains(self, report: ValidationReport) -> None:
        """Verify that each property use has a subject of the declared domain class."""
        for prop, domain_class in self.DOMAIN_MAP.items():
            result = self._g.query(
                """
                SELECT ?s WHERE {
                    ?s ?p ?o .
                    FILTER NOT EXISTS { ?s a ?domain }
                }
                """,
                initBindings={"p": prop, "domain": domain_class},
            )
            for row in result:
                report.violations.append(Violation(
                    rule="domain_constraint",
                    subject=str(row.s),
                    predicate=str(prop),
                    detail=f"subject is not of type {domain_class.fragment}",
                ))

    def _check_ranges(self, report: ValidationReport) -> None:
        """Verify that each property use has an object of the declared range class."""
        for prop, range_class in self.RANGE_MAP.items():
            result = self._g.query(
                """
                SELECT ?o WHERE {
                    ?s ?p ?o .
                    FILTER(!isLiteral(?o))
                    FILTER NOT EXISTS { ?o a ?range }
                }
                """,
                initBindings={"p": prop, "range": range_class},
            )
            for row in result:
                report.violations.append(Violation(
                    rule="range_constraint",
                    subject=str(row.o),
                    predicate=str(prop),
                    detail=f"object is not of type {range_class.fragment}",
                ))

    def _check_required_properties(self, report: ValidationReport) -> None:
        """Spot-check that every User has an email and every Route has a routeCode."""
        for cls, prop, label in [
            (SB.User,  SB.email,     "email"),
            (SB.Route, SB.routeCode, "routeCode"),
            (SB.Stop,  SB.stopName,  "stopName"),
        ]:
            result = self._g.query(
                """
                SELECT ?s WHERE {
                    ?s a ?cls .
                    FILTER NOT EXISTS { ?s ?prop ?v . }
                }
                """,
                initBindings={"cls": cls, "prop": prop},
            )
            for row in result:
                report.violations.append(Violation(
                    rule="required_property",
                    subject=str(row.s),
                    predicate=str(prop),
                    detail=f"missing required property {label}",
                ))


# ── CLI smoke test ─────────────────────────────────────────────────────────────

if __name__ == "__main__":
    import sys
    from pathlib import Path

    sys.path.insert(0, str(Path(__file__).parent.parent.parent))
    from ml.semantic.knowledge_graph import SmartBusKnowledgeGraph

    print("Validating SmartBus knowledge graph…")
    kg = SmartBusKnowledgeGraph()
    validator = OntologyValidator(kg._g)
    report = validator.validate()

    if report.is_valid:
        print("✓ No violations found")
    else:
        print(f"✗ {len(report.violations)} violation(s):")
        for v in report.violations:
            print(f"  {v}")

    print(f"\nSummary: {report.summary()}")
