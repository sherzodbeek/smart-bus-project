"""
SmartBus ML Server — Flask REST API bridge between Python model and Java gateway.

This is the backend_integration layer: it loads the trained SmartBusRecommender and
exposes it as a lightweight HTTP API that the Java gateway calls via RestClient.

Endpoints
---------
GET  /api/ml/health                        — liveness probe
GET  /api/ml/recommend?email=X&n=3        — route recommendations by email
GET  /api/ml/recommend/user?user_id=X&n=3 — route recommendations by user_id
GET  /api/ml/info                          — model metadata

Run:
    source ml/venv/bin/activate
    python ml/server.py           # default port 5050

    ML_SERVER_PORT=5050 python ml/server.py
"""

from __future__ import annotations

import logging
import os
import time
from pathlib import Path

from flask import Flask, jsonify, request

# Ensure inference.py (and model_training.py it imports) are on the path
import sys
import __main__
sys.path.insert(0, str(Path(__file__).parent))

# SmartBusRecommender must exist at __main__ scope so joblib can unpickle the saved model
# (the model was serialised while model_training.py ran as __main__).
from model_training import SmartBusRecommender  # noqa: E402
__main__.SmartBusRecommender = SmartBusRecommender

from inference import SmartBusInference  # noqa: E402
from semantic.semantic_service import get_semantic_service  # noqa: E402
from semantic.ai_ontology_bridge import get_bridge          # noqa: E402

app = Flask(__name__)

logging.basicConfig(
    level=logging.INFO,
    format="%(asctime)s %(levelname)s %(name)s — %(message)s",
)
log = logging.getLogger("ml-server")

# ── Load model once at startup ─────────────────────────────────────────────────
_engine: SmartBusInference | None = None


def get_engine() -> SmartBusInference:
    global _engine
    if _engine is None:
        log.info("Loading SmartBus recommendation model …")
        t0 = time.perf_counter()
        _engine = SmartBusInference()
        log.info("Model loaded in %.3fs", time.perf_counter() - t0)
    return _engine


# ── Routes ─────────────────────────────────────────────────────────────────────

@app.get("/api/ml/health")
def health():
    try:
        engine = get_engine()
        info   = engine.model_info()
        return jsonify({
            "status":        "UP",
            "model_version": info.get("model_version"),
            "n_users":       info.get("n_users"),
            "n_routes":      info.get("n_routes"),
        })
    except Exception as exc:
        log.exception("Health check failed")
        return jsonify({"status": "DOWN", "error": str(exc)}), 503


@app.get("/api/ml/info")
def model_info():
    try:
        return jsonify(get_engine().model_info())
    except Exception as exc:
        log.exception("Info failed")
        return jsonify({"error": str(exc)}), 500


@app.get("/api/ml/recommend")
def recommend_by_email():
    email = request.args.get("email", "").strip()
    if not email:
        return jsonify({"error": "email query parameter is required"}), 400
    n = _parse_n(request.args.get("n", "3"))

    t0 = time.perf_counter()
    try:
        result = get_engine().recommend_for_email(email, n=n)
    except Exception as exc:
        log.exception("Recommendation failed for email=%s", email)
        return jsonify({"error": str(exc)}), 500

    elapsed_ms = round((time.perf_counter() - t0) * 1000, 1)
    log.info(
        "recommend email=%s n=%d cold_start=%s recs=%d latency_ms=%.1f",
        email,
        n,
        result.get("is_cold_start"),
        len(result.get("recommendations", [])),
        elapsed_ms,
    )
    result["inference_ms"] = elapsed_ms
    return jsonify(result)


@app.get("/api/ml/recommend/user")
def recommend_by_user_id():
    user_id = request.args.get("user_id", "").strip()
    if not user_id:
        return jsonify({"error": "user_id query parameter is required"}), 400
    n = _parse_n(request.args.get("n", "3"))

    t0 = time.perf_counter()
    try:
        result = get_engine().recommend_for_user(user_id, n=n)
    except Exception as exc:
        log.exception("Recommendation failed for user_id=%s", user_id)
        return jsonify({"error": str(exc)}), 500

    elapsed_ms = round((time.perf_counter() - t0) * 1000, 1)
    result["inference_ms"] = elapsed_ms
    return jsonify(result)


# ═══════════════════════════════════════════════════════════════════════════════
# Semantic endpoints  (Knowledge-graph / SPARQL layer)
# ═══════════════════════════════════════════════════════════════════════════════

@app.get("/api/semantic/health")
def semantic_health():
    """Return KG statistics and ontology validation summary."""
    try:
        stats = get_semantic_service().graph_stats()
        stats["status"] = "UP"
        return jsonify(stats)
    except Exception as exc:
        log.exception("Semantic health check failed")
        return jsonify({"status": "DOWN", "error": str(exc)}), 503


@app.get("/api/semantic/routes")
def semantic_routes_from_stop():
    """Q1 — routes departing from a named stop.

    Query param: from=<stop_name>  (e.g. "Downtown Terminal")
    """
    stop_name = request.args.get("from", "").strip()
    if not stop_name:
        return jsonify({"error": "'from' query parameter is required"}), 400
    try:
        return jsonify(get_semantic_service().routes_from_stop(stop_name))
    except Exception as exc:
        log.exception("semantic_routes_from_stop failed stop=%s", stop_name)
        return jsonify({"error": str(exc)}), 500


@app.get("/api/semantic/routes/zone")
def semantic_routes_zone():
    """Q2 + R3 — routes departing from stops in the same zone as the anchor stop.

    Query param: stop=<stop_name>
    """
    stop_name = request.args.get("stop", "").strip()
    if not stop_name:
        return jsonify({"error": "'stop' query parameter is required"}), 400
    try:
        return jsonify(get_semantic_service().routes_in_zone(stop_name))
    except Exception as exc:
        log.exception("semantic_routes_zone failed stop=%s", stop_name)
        return jsonify({"error": str(exc)}), 500


@app.get("/api/semantic/candidates")
def semantic_candidates():
    """R4 — candidate routes derived from the user's preferred origin stop.

    Query param: email=<email>
    """
    email = request.args.get("email", "").strip()
    if not email:
        return jsonify({"error": "'email' query parameter is required"}), 400
    try:
        return jsonify(get_semantic_service().candidate_routes(email))
    except Exception as exc:
        log.exception("semantic_candidates failed email=%s", email)
        return jsonify({"error": str(exc)}), 500


@app.get("/api/semantic/insights")
def semantic_insights():
    """R1 + R2 + R4 — full inference summary for a user.

    Returns frequently-travelled routes, candidate routes, inferred facts,
    and stored ontology recommendations.

    Query param: email=<email>
    """
    email = request.args.get("email", "").strip()
    if not email:
        return jsonify({"error": "'email' query parameter is required"}), 400
    try:
        return jsonify(get_semantic_service().user_insights(email))
    except Exception as exc:
        log.exception("semantic_insights failed email=%s", email)
        return jsonify({"error": str(exc)}), 500


@app.get("/api/semantic/find-related")
def semantic_find_related():
    """Generic entity-relationship traversal.

    Query params:
      entity=<label>           e.g. "Downtown Terminal"
      relationship=<name>      e.g. "departsFrom"  (see /api/semantic/health for full list)
    """
    entity_name  = request.args.get("entity", "").strip()
    relationship = request.args.get("relationship", "").strip()
    if not entity_name or not relationship:
        return jsonify({"error": "'entity' and 'relationship' query parameters are required"}), 400
    try:
        result = get_semantic_service().find_related(entity_name, relationship)
        if "error" in result:
            return jsonify(result), 400
        return jsonify(result)
    except Exception as exc:
        log.exception("semantic_find_related failed entity=%s relationship=%s", entity_name, relationship)
        return jsonify({"error": str(exc)}), 500


@app.post("/api/semantic/query")
def semantic_query():
    """Execute a SPARQL SELECT query against the knowledge graph.

    Body (JSON): {"sparql": "SELECT ..."}

    Only SELECT queries are permitted; INSERT/DELETE/DROP are rejected.
    """
    body = request.get_json(silent=True) or {}
    sparql = body.get("sparql", "").strip()
    if not sparql:
        return jsonify({"error": "JSON body must contain a 'sparql' key"}), 400
    try:
        result = get_semantic_service().execute_sparql(sparql)
        if "error" in result:
            return jsonify(result), 400
        return jsonify(result)
    except Exception as exc:
        log.exception("semantic_query failed")
        return jsonify({"error": str(exc)}), 500


# ═══════════════════════════════════════════════════════════════════════════════
# Intelligent (AI + Ontology) endpoints
# ═══════════════════════════════════════════════════════════════════════════════

@app.get("/api/intelligent/recommend")
def intelligent_recommend():
    """Full AI-Ontology pipeline: ML model + ontology enrichment + KG update.

    Direction 1 (Ontology→AI): semantic features boost ML scores.
    Direction 2 (AI→Ontology): top-n results stored as RDF triples.

    Query params: email=X  n=3 (optional, default 3)
    """
    email = request.args.get("email", "").strip()
    if not email:
        return jsonify({"error": "email query parameter is required"}), 400
    n = _parse_n(request.args.get("n", "3"))

    t0 = time.perf_counter()
    try:
        result = get_bridge().recommend_enriched(email, n=n)
    except Exception as exc:
        log.exception("intelligent_recommend failed email=%s", email)
        return jsonify({"error": str(exc)}), 500

    elapsed_ms = round((time.perf_counter() - t0) * 1000, 1)
    result["inference_ms"] = elapsed_ms
    log.info(
        "intelligentRecommend email=%s n=%d boosted=%d latency_ms=%.1f",
        email, n,
        result.get("enrichment_stats", {}).get("routes_boosted", 0),
        elapsed_ms,
    )
    return jsonify(result)


@app.get("/api/intelligent/explain")
def intelligent_explain():
    """Explain why a specific route was recommended to a user.

    Returns ML score breakdown + semantic feature contributions + narrative.

    Query params: email=X  route=SB-101
    """
    email      = request.args.get("email", "").strip()
    route_code = request.args.get("route", "").strip()
    if not email or not route_code:
        return jsonify({"error": "email and route query parameters are required"}), 400

    t0 = time.perf_counter()
    try:
        result = get_bridge().explain(email, route_code)
    except Exception as exc:
        log.exception("intelligent_explain failed email=%s route=%s", email, route_code)
        return jsonify({"error": str(exc)}), 500

    result["explain_ms"] = round((time.perf_counter() - t0) * 1000, 1)
    return jsonify(result)


# ── Helpers ────────────────────────────────────────────────────────────────────

def _parse_n(raw: str) -> int:
    try:
        n = int(raw)
        return max(1, min(n, 10))
    except (ValueError, TypeError):
        return 3


# ── Entry point ────────────────────────────────────────────────────────────────

if __name__ == "__main__":
    port = int(os.environ.get("ML_SERVER_PORT", 5050))
    log.info("Starting SmartBus ML server on port %d", port)
    get_engine()                 # eager-load ML model
    get_semantic_service()       # eager-load knowledge graph
    get_bridge()                 # eager-load AI-Ontology bridge
    app.run(host="0.0.0.0", port=port, debug=False)
