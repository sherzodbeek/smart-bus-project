"""
SmartBus Route Recommendation — Inference Script

Loads the saved model and serves recommendations for a given user.

Usage
-----
# Recommend for a specific user (by user_id):
    python ml/inference.py --user_id 42

# Recommend for a user by email (requires user_profiles.csv):
    python ml/inference.py --email alice.smith1@example.com

# Return top-N results (default 3):
    python ml/inference.py --user_id 42 --n 5

# Output as JSON:
    python ml/inference.py --user_id 42 --json

Programmatic usage (from Java via shell or from Flask):
    from ml.inference import SmartBusInference
    engine = SmartBusInference()
    result = engine.recommend_for_user("42", n=3)
"""

from __future__ import annotations

import argparse
import csv
import json
import sys
from pathlib import Path

import joblib

# SmartBusRecommender must be importable so joblib can unpickle the saved model.
sys.path.insert(0, str(Path(__file__).parent))
from model_training import SmartBusRecommender  # noqa: F401 (needed by joblib)

MODEL_DIR = Path(__file__).parent / "model"
PROC_DIR  = Path(__file__).parent / "data" / "processed"


class SmartBusInference:
    """Thin wrapper around the saved SmartBusRecommender for production inference."""

    def __init__(self, model_path: Path | None = None) -> None:
        path = model_path or MODEL_DIR / "smartbus_recommender.joblib"
        if not path.exists():
            raise FileNotFoundError(
                f"Model artifact not found at {path}. "
                "Run ml/model_training.py first."
            )
        self._model = joblib.load(path)
        self._metadata = self._load_metadata()
        self._email_to_id = self._build_email_index()

    def _load_metadata(self) -> dict:
        meta_path = MODEL_DIR / "model_metadata.json"
        if meta_path.exists():
            with open(meta_path) as fh:
                return json.load(fh)
        return {}

    def _build_email_index(self) -> dict[str, str]:
        """Map email → user_id from user_profiles.csv."""
        path = PROC_DIR / "user_profiles.csv"
        if not path.exists():
            return {}
        index: dict[str, str] = {}
        with open(path, newline="") as fh:
            for row in csv.DictReader(fh):
                index[row["email"]] = row["user_id"]
        return index

    def recommend_for_user(
        self,
        user_id: str,
        n: int = 3,
        exclude_seen: bool = True,
    ) -> dict:
        """
        Generate route recommendations for a user.

        Parameters
        ----------
        user_id : str
            The user's integer ID as a string.
        n : int
            Number of recommendations to return.
        exclude_seen : bool
            Whether to exclude routes the user has already booked.

        Returns
        -------
        dict with keys:
            user_id, is_cold_start, recommendations, model_version
        """
        is_cold_start = user_id not in self._model._user_index
        if is_cold_start:
            recs = self._cold_start_recommendations(n)
        else:
            recs = self._model.recommend(user_id, n=n, exclude_seen=exclude_seen)

        return {
            "user_id":       user_id,
            "is_cold_start": is_cold_start,
            "recommendations": recs,
            "model_version": self._metadata.get("model_version", "unknown"),
        }

    def recommend_for_email(
        self,
        email: str,
        n: int = 3,
        exclude_seen: bool = True,
    ) -> dict:
        """Lookup user_id from email and return recommendations."""
        user_id = self._email_to_id.get(email)
        if user_id is None:
            # Unknown email — cold-start (content-based only)
            return {
                "user_id":       None,
                "email":         email,
                "is_cold_start": True,
                "recommendations": self._cold_start_recommendations(n),
                "model_version": self._metadata.get("model_version", "unknown"),
            }
        result = self.recommend_for_user(user_id, n=n, exclude_seen=exclude_seen)
        result["email"] = email
        return result

    def _cold_start_recommendations(self, n: int) -> list[dict]:
        """
        For a completely unknown user, return the globally most popular routes
        (most interactions in the training matrix).
        """
        import numpy as np
        popularity = self._model._user_item.sum(axis=0)
        top_idx    = np.argsort(-popularity)[:n]
        return [
            {
                "route_code":   self._model._route_codes[i],
                "hybrid_score": round(float(popularity[i] / popularity.max()), 4),
                "cf_score":     0.0,
                "cb_score":     0.0,
                "reason":       "popularity_fallback",
            }
            for i in top_idx
        ]

    def model_info(self) -> dict:
        return {
            "model_version": self._metadata.get("model_version"),
            "algorithm":     self._metadata.get("algorithm"),
            "k_neighbors":   self._model.k_neighbors,
            "alpha":         self._model.alpha,
            "n_users":       len(self._model._user_ids),
            "n_routes":      len(self._model._route_codes),
            "trained_at":    self._metadata.get("trained_at"),
            "metrics":       self._metadata.get("metrics", {}),
        }


# ── CLI entry point ───────────────────────────────────────────────────────────

def main() -> None:
    parser = argparse.ArgumentParser(
        description="SmartBus route recommendation inference"
    )
    group = parser.add_mutually_exclusive_group(required=True)
    group.add_argument("--user_id", type=str, help="User ID (integer as string)")
    group.add_argument("--email",   type=str, help="User email address")
    parser.add_argument("--n",    type=int, default=3, help="Number of recommendations")
    parser.add_argument("--json", action="store_true", help="Output as JSON")
    parser.add_argument("--info", action="store_true", help="Print model info and exit")
    args = parser.parse_args()

    try:
        engine = SmartBusInference()
    except FileNotFoundError as e:
        print(f"ERROR: {e}", file=sys.stderr)
        sys.exit(1)

    if args.info:
        print(json.dumps(engine.model_info(), indent=2))
        return

    if args.user_id:
        result = engine.recommend_for_user(args.user_id, n=args.n)
    else:
        result = engine.recommend_for_email(args.email, n=args.n)

    if args.json:
        print(json.dumps(result, indent=2))
    else:
        uid   = result.get("email") or result.get("user_id")
        cold  = result.get("is_cold_start", False)
        print(f"\nRecommendations for user: {uid}"
              + ("  [cold-start — no booking history]" if cold else ""))
        print(f"{'Route':<10} {'Score':>7}  {'CF':>7}  {'CB':>7}  Reason")
        print("-" * 55)
        for rec in result["recommendations"]:
            print(
                f"{rec['route_code']:<10} "
                f"{rec['hybrid_score']:>7.4f}  "
                f"{rec['cf_score']:>7.4f}  "
                f"{rec['cb_score']:>7.4f}  "
                f"{rec['reason']}"
            )


if __name__ == "__main__":
    main()
