"""
AISemanticBridge — bidirectional AI ↔ Ontology integration layer.

Direction 1 — Ontology → AI  (enrich ML predictions):
  Fetches user semantic features from the knowledge graph (preferred origin,
  candidate routes R4, frequently-travelled routes R1) and applies score boosts
  to raw ML recommendations so that ontology-aligned routes rank higher.

Direction 2 — AI → Ontology  (enrich knowledge graph):
  After enrichment, stores each prediction as an sb:Recommendation RDF triple
  in the live knowledge graph, making recommendations queryable via SPARQL.

Combined result:
  The /api/intelligent/recommend endpoint returns both ML and semantic
  contributions per route, with a full reasoning explanation.
"""

from __future__ import annotations

import hashlib
import time
from dataclasses import dataclass, field

from .knowledge_graph import SmartBusKnowledgeGraph, get_knowledge_graph

# ── Boost weights (Ontology → AI direction) ───────────────────────────────────

CANDIDATE_BOOST   = 0.20  # Route departs from user's preferred origin (R4)
FREQUENCY_BOOST   = 0.15  # User has booked this route ≥3 times (R1)
MAX_ENRICHED      = 1.0   # Clamp ceiling


# ── Data structures ───────────────────────────────────────────────────────────

@dataclass
class SemanticFeatures:
    """Ontology-derived features for one user."""
    email: str
    preferred_origin: str | None
    candidate_routes: list[str]   # R4 — routes from preferred origin
    frequently_travels: list[str] # R1 — routes booked ≥3 times
    stored_recs: list[dict]       # existing ontology recommendations

    def to_dict(self) -> dict:
        return {
            "preferred_origin":    self.preferred_origin,
            "candidate_routes":    self.candidate_routes,
            "frequently_travels":  self.frequently_travels,
            "stored_recommendations_count": len(self.stored_recs),
        }


@dataclass
class EnrichedRecommendation:
    """Single recommendation with both ML and semantic components."""
    route_code:       str
    ml_score:         float   # raw hybrid score from ML model
    cf_score:         float
    cb_score:         float
    semantic_boost:   float   # ontology-derived addition
    enriched_score:   float   # ml_score + semantic_boost (clamped)
    original_reason:  str     # ML reason code
    semantic_reasons: list[str] = field(default_factory=list)

    def to_dict(self) -> dict:
        return {
            "route_code":       self.route_code,
            "ml_score":         round(self.ml_score, 4),
            "cf_score":         round(self.cf_score, 4),
            "cb_score":         round(self.cb_score, 4),
            "semantic_boost":   round(self.semantic_boost, 4),
            "enriched_score":   round(self.enriched_score, 4),
            "hybrid_score":     round(self.enriched_score, 4),  # alias for DecisionEngine compat
            "reason":           _merged_reason(self.original_reason, self.semantic_reasons),
            "semantic_reasons": self.semantic_reasons,
        }


# ── Main class ────────────────────────────────────────────────────────────────

class AISemanticBridge:
    """
    Bidirectional bridge between the ML recommendation model and the RDF
    knowledge graph.

    Parameters
    ----------
    kg : SmartBusKnowledgeGraph
        The live knowledge graph (load ontology + sample data).
    inference_engine : SmartBusInference | None
        The trained ML model.  If None, enrichment works on externally supplied
        raw_recs (useful for testing without loading the full model).
    """

    def __init__(
        self,
        kg: SmartBusKnowledgeGraph | None = None,
        inference_engine=None,
    ) -> None:
        self._kg     = kg or get_knowledge_graph()
        self._engine = inference_engine   # set lazily if None

    def _get_engine(self):
        if self._engine is None:
            # Lazy import — avoids circular dependency and keeps unit tests fast
            import sys
            from pathlib import Path
            sys.path.insert(0, str(Path(__file__).parent.parent))
            from inference import SmartBusInference
            self._engine = SmartBusInference()
        return self._engine

    # ── Direction 1: Ontology → AI ────────────────────────────────────────────

    def get_semantic_features(self, email: str) -> SemanticFeatures:
        """Fetch all ontology features for a user in one pass."""
        t0 = time.perf_counter()

        preferred_origin = self._preferred_origin(email)
        candidate_routes = self._kg.candidate_routes_for_user(email)
        frequently_travels = self._kg.frequently_travelled_routes(email)
        stored_recs = self._kg.recommendations_for_email(email)

        return SemanticFeatures(
            email=email,
            preferred_origin=preferred_origin,
            candidate_routes=candidate_routes,
            frequently_travels=frequently_travels,
            stored_recs=stored_recs,
        )

    def enrich_recommendations(
        self,
        raw_recs: list[dict],
        features: SemanticFeatures,
    ) -> list[EnrichedRecommendation]:
        """
        Apply semantic boost to a list of raw ML recommendations.

        The boost is additive:
          +CANDIDATE_BOOST  if the route is in the R4 candidate set
          +FREQUENCY_BOOST  if the user frequentlyTravels this route (R1)

        Returns all routes enriched, unsorted.
        """
        enriched: list[EnrichedRecommendation] = []
        for rec in raw_recs:
            code       = str(rec.get("route_code", ""))
            ml_score   = float(rec.get("hybrid_score", 0.0))
            cf_score   = float(rec.get("cf_score", 0.0))
            cb_score   = float(rec.get("cb_score", 0.0))
            orig_reason = str(rec.get("reason", ""))

            boost   = 0.0
            reasons: list[str] = []

            if code in features.candidate_routes:
                boost += CANDIDATE_BOOST
                reasons.append("origin_preference_match")

            if code in features.frequently_travels:
                boost += FREQUENCY_BOOST
                reasons.append("frequently_travelled")

            enriched.append(EnrichedRecommendation(
                route_code=code,
                ml_score=ml_score,
                cf_score=cf_score,
                cb_score=cb_score,
                semantic_boost=boost,
                enriched_score=min(MAX_ENRICHED, ml_score + boost),
                original_reason=orig_reason,
                semantic_reasons=reasons,
            ))

        return enriched

    # ── Direction 2: AI → Ontology ────────────────────────────────────────────

    def store_predictions_in_kg(
        self,
        email: str,
        enriched: list[EnrichedRecommendation],
        model_version: str = "1.0.0",
    ) -> int:
        """
        Persist each enriched recommendation as an RDF triple set in the
        live knowledge graph.  Returns the number of triples asserted.
        """
        count = 0
        for rec in enriched:
            # Stable unique ID per (email, route, version)
            rec_id = _rec_id(email, rec.route_code, model_version)
            self._kg.assert_recommendation(
                rec_id=rec_id,
                email=email,
                route_code=rec.route_code,
                score=rec.enriched_score,
                confidence=_confidence_label(rec.enriched_score),
                reason=_merged_reason(rec.original_reason, rec.semantic_reasons),
            )
            count += 1
        return count

    # ── Full pipeline ─────────────────────────────────────────────────────────

    def recommend_enriched(self, email: str, n: int = 3) -> dict:
        """
        Full AI-Ontology pipeline:
          1. ML model → raw recommendations (larger pool: n*3 or ≥10)
          2. Ontology → semantic features
          3. Enrich + re-rank by enriched_score
          4. Store top-n back in knowledge graph (AI → Ontology)
          5. Return enriched result with improvement statistics

        Parameters
        ----------
        email : str
        n : int  top-n final recommendations

        Returns
        -------
        dict with keys:
            email, is_cold_start, model_version, semantic_features,
            recommendations, enrichment_stats
        """
        pool_size = max(n * 3, 10)
        engine    = self._get_engine()
        raw_result = engine.recommend_for_email(email, n=pool_size)

        raw_recs       = raw_result.get("recommendations", [])
        model_version  = raw_result.get("model_version", "unknown")
        is_cold_start  = raw_result.get("is_cold_start", False)

        features = self.get_semantic_features(email)
        enriched = self.enrich_recommendations(raw_recs, features)

        # Re-rank by enriched_score and take top-n
        top_n = sorted(enriched, key=lambda r: r.enriched_score, reverse=True)[:n]

        # Store back in KG (Direction 2)
        stored = self.store_predictions_in_kg(email, top_n, model_version)

        return {
            "email":            email,
            "is_cold_start":    is_cold_start,
            "model_version":    model_version,
            "semantic_features": features.to_dict(),
            "recommendations":  [r.to_dict() for r in top_n],
            "enrichment_stats": _improvement_stats(raw_recs, top_n),
            "kg_triples_added": stored,
        }

    # ── Per-route explanation ─────────────────────────────────────────────────

    def explain(self, email: str, route_code: str) -> dict:
        """
        Detailed reasoning breakdown for a specific (email, route_code) pair.

        Shows:
          - Raw ML score and its CF / CB components
          - Semantic features and their individual boost contributions
          - Final enriched score and confidence label
          - Human-readable reasoning narrative
        """
        engine = self._get_engine()
        raw_result = engine.recommend_for_email(email, n=10)
        raw_recs   = raw_result.get("recommendations", [])

        ml_rec = next((r for r in raw_recs if r.get("route_code") == route_code), None)
        ml_score   = float(ml_rec["hybrid_score"]) if ml_rec else 0.0
        cf_score   = float(ml_rec["cf_score"]) if ml_rec else 0.0
        cb_score   = float(ml_rec["cb_score"]) if ml_rec else 0.0

        features = self.get_semantic_features(email)

        origin_boost    = CANDIDATE_BOOST  if route_code in features.candidate_routes   else 0.0
        frequency_boost = FREQUENCY_BOOST  if route_code in features.frequently_travels else 0.0
        total_boost     = origin_boost + frequency_boost
        enriched_score  = min(MAX_ENRICHED, ml_score + total_boost)

        contributions = {
            "ml_collaborative_filtering": round(cf_score, 4),
            "ml_content_based":           round(cb_score, 4),
            "ml_hybrid_score":            round(ml_score, 4),
            "semantic_origin_preference": round(origin_boost, 4),
            "semantic_frequency_boost":   round(frequency_boost, 4),
        }

        return {
            "email":              email,
            "route_code":         route_code,
            "ml_score":           round(ml_score, 4),
            "semantic_boost":     round(total_boost, 4),
            "enriched_score":     round(enriched_score, 4),
            "confidence":         _confidence_label(enriched_score),
            "contributions":      contributions,
            "semantic_context": {
                "preferred_origin":        features.preferred_origin,
                "is_candidate_route":      route_code in features.candidate_routes,
                "is_frequently_travelled": route_code in features.frequently_travels,
            },
            "reasoning": _build_reasoning(route_code, contributions, features),
        }

    # ── Internal helpers ──────────────────────────────────────────────────────

    def _preferred_origin(self, email: str) -> str | None:
        safe = email.replace('"', "").replace("\\", "")
        results = list(self._kg.query(
            f"""
            PREFIX sb: <http://smartbus.example.org/ontology#>
            SELECT ?stopName WHERE {{
                ?user  sb:email         "{safe}" ;
                       sb:prefersOrigin ?stop .
                ?stop  sb:stopName      ?stopName .
            }}
            """
        ))
        return str(results[0][0]) if results else None


# ── Module-level singleton ─────────────────────────────────────────────────────

_bridge: AISemanticBridge | None = None


def get_bridge() -> AISemanticBridge:
    global _bridge
    if _bridge is None:
        _bridge = AISemanticBridge()
    return _bridge


# ── Pure helpers ───────────────────────────────────────────────────────────────

def _confidence_label(score: float) -> str:
    if score >= 0.60:
        return "HIGH"
    if score >= 0.30:
        return "MEDIUM"
    return "LOW"


def _merged_reason(original: str, semantic_reasons: list[str]) -> str:
    """Combine ML reason and semantic reasons into a single reason code."""
    if not semantic_reasons:
        return original
    primary = semantic_reasons[0]
    return f"{primary}" if original == primary else f"{original}+{primary}"


def _rec_id(email: str, route_code: str, model_version: str) -> str:
    raw = f"{email}|{route_code}|{model_version}"
    return hashlib.md5(raw.encode()).hexdigest()[:12]


def _improvement_stats(
    raw_recs: list[dict],
    top_n: list[EnrichedRecommendation],
) -> dict:
    """Compute measurable improvement metrics for the enrichment report."""
    boosted_count = sum(1 for r in top_n if r.semantic_boost > 0)
    avg_boost     = (
        sum(r.semantic_boost for r in top_n) / len(top_n) if top_n else 0.0
    )
    max_boost = max((r.semantic_boost for r in top_n), default=0.0)

    rank_changes: list[dict] = []
    raw_order  = [r.get("route_code") for r in raw_recs]
    enr_order  = [r.route_code for r in top_n]
    for new_rank, code in enumerate(enr_order):
        old_rank = raw_order.index(code) if code in raw_order else -1
        if old_rank != new_rank:
            rank_changes.append({
                "route_code": code,
                "old_rank": old_rank,
                "new_rank": new_rank,
                "delta": old_rank - new_rank,  # positive = moved up
            })

    return {
        "routes_boosted":      boosted_count,
        "average_boost":       round(avg_boost, 4),
        "max_boost":           round(max_boost, 4),
        "rank_changes":        rank_changes,
        "ontology_contributed": boosted_count > 0,
    }


def _build_reasoning(
    route_code: str,
    contributions: dict,
    features: SemanticFeatures,
) -> str:
    parts = []

    if contributions["ml_hybrid_score"] > 0:
        parts.append(
            f"ML model scored this route {contributions['ml_hybrid_score']:.2f} "
            f"(CF={contributions['ml_collaborative_filtering']:.2f}, "
            f"CB={contributions['ml_content_based']:.2f})"
        )

    if contributions["semantic_origin_preference"] > 0:
        stop = features.preferred_origin or "your preferred stop"
        parts.append(
            f"Route departs from {stop} (your preferred origin, Rule R4) "
            f"+{contributions['semantic_origin_preference']:.2f}"
        )

    if contributions["semantic_frequency_boost"] > 0:
        parts.append(
            f"You have booked this route 3+ times (Rule R1) "
            f"+{contributions['semantic_frequency_boost']:.2f}"
        )

    if not parts:
        return "No information available for this route."

    return "; ".join(parts) + "."
