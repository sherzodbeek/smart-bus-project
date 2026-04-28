"""
SmartBus Hybrid Route Recommendation Model — Training Pipeline

Architecture
------------
Two-component hybrid:
  1. Collaborative Filtering (CF): user-user cosine similarity on the
     implicit-rating interaction matrix.
  2. Content-Based Filtering (CB): cosine similarity between a user's
     weighted preference profile and each route's feature vector.

Final score = alpha * CF_score + (1 - alpha) * CB_score

Hyperparameter tuning: grid search over (k_neighbors, alpha) optimising NDCG@3.

Run:
    python ml/model_training.py
"""

from __future__ import annotations

import csv
import json
import math
import warnings
from collections import defaultdict
from datetime import datetime
from pathlib import Path

import joblib
import numpy as np
from sklearn.metrics.pairwise import cosine_similarity
from sklearn.preprocessing import MinMaxScaler

warnings.filterwarnings("ignore")

PROC_DIR  = Path(__file__).parent / "data" / "processed"
MODEL_DIR = Path(__file__).parent / "model"
MODEL_DIR.mkdir(parents=True, exist_ok=True)

RANDOM_SEED = 42
np.random.seed(RANDOM_SEED)

# Numeric feature columns used in the content-based component
CB_FEATURE_COLS = [
    "price_scaled",
    "origin_Downtown_Terminal", "origin_Airport_Station", "origin_University",
    "origin_Bus_Depot", "origin_City_Center", "origin_Harbor",
    "dest_Downtown_Terminal", "dest_Airport_Station", "dest_University",
    "dest_Bus_Depot", "dest_City_Center", "dest_Harbor",
    "hour_early_morning", "hour_morning_rush", "hour_midday",
    "hour_afternoon", "hour_evening",
    "tier_LOW", "tier_MEDIUM", "tier_HIGH",
]


# ── Data Loading ──────────────────────────────────────────────────────────────

def read_csv(path: Path) -> list[dict]:
    with open(path, newline="") as fh:
        return list(csv.DictReader(fh))


def load_data() -> tuple[list[dict], list[dict], list[dict], list[dict]]:
    train         = read_csv(PROC_DIR / "train.csv")
    test          = read_csv(PROC_DIR / "test.csv")
    route_features = read_csv(PROC_DIR / "route_features.csv")
    user_profiles  = read_csv(PROC_DIR / "user_profiles.csv")
    return train, test, route_features, user_profiles


# ── Ranking Metrics ───────────────────────────────────────────────────────────

def precision_at_k(recommended: list[str], relevant: set[str], k: int) -> float:
    hits = sum(1 for r in recommended[:k] if r in relevant)
    return hits / k


def recall_at_k(recommended: list[str], relevant: set[str], k: int) -> float:
    if not relevant:
        return 0.0
    hits = sum(1 for r in recommended[:k] if r in relevant)
    return hits / len(relevant)


def ndcg_at_k(recommended: list[str], relevant: set[str], k: int) -> float:
    dcg  = sum(1.0 / math.log2(i + 2) for i, r in enumerate(recommended[:k]) if r in relevant)
    idcg = sum(1.0 / math.log2(i + 2) for i in range(min(len(relevant), k)))
    return dcg / idcg if idcg > 0 else 0.0


def evaluate_recommendations(
    predictions: dict[str, list[str]],
    ground_truth: dict[str, set[str]],
    k_values: list[int] = [1, 3, 5],
) -> dict[str, float]:
    """Compute mean P@K, R@K, NDCG@K over all users that appear in ground_truth."""
    metrics: dict[str, list[float]] = defaultdict(list)

    for uid, relevant in ground_truth.items():
        recs = predictions.get(uid, [])
        for k in k_values:
            metrics[f"P@{k}"].append(precision_at_k(recs, relevant, k))
            metrics[f"R@{k}"].append(recall_at_k(recs, relevant, k))
            metrics[f"NDCG@{k}"].append(ndcg_at_k(recs, relevant, k))

    return {name: float(np.mean(vals)) for name, vals in metrics.items()}


# ── Model ─────────────────────────────────────────────────────────────────────

class SmartBusRecommender:
    """
    Hybrid recommendation model for SmartBus route suggestions.

    Parameters
    ----------
    k_neighbors : int
        Number of most-similar users to use in the CF component.
    alpha : float
        Weight of the CF score in the hybrid [0, 1].
        (1 - alpha) is applied to the CB score.
    """

    def __init__(self, k_neighbors: int = 20, alpha: float = 0.6) -> None:
        self.k_neighbors = k_neighbors
        self.alpha = alpha

        self._user_ids: list[str] = []
        self._route_codes: list[str] = []
        self._user_item: np.ndarray = np.array([])     # shape (n_users, n_routes)
        self._user_sim: np.ndarray = np.array([])       # shape (n_users, n_users)
        self._route_feat: np.ndarray = np.array([])     # shape (n_routes, n_cb_features)
        self._user_profiles: np.ndarray = np.array([])  # shape (n_users, n_cb_features)
        self._user_index: dict[str, int] = {}
        self._route_index: dict[str, int] = {}
        self._all_route_codes: list[str] = []

    # ── Fit ───────────────────────────────────────────────────────────────────

    def fit(
        self,
        train: list[dict],
        route_features: list[dict],
    ) -> "SmartBusRecommender":
        self._all_route_codes = [r["route_code"] for r in route_features]

        # ── Build user-item matrix ─────────────────────────────────────────
        user_set  = sorted({row["user_id"] for row in train})
        route_set = sorted({r["route_code"] for r in route_features})

        self._user_ids   = user_set
        self._route_codes = route_set
        self._user_index  = {u: i for i, u in enumerate(user_set)}
        self._route_index = {r: i for i, r in enumerate(route_set)}

        n_u, n_r = len(user_set), len(route_set)
        mat = np.zeros((n_u, n_r), dtype=np.float32)
        for row in train:
            ui = self._user_index[row["user_id"]]
            ri = self._route_index[row["route_code"]]
            mat[ui, ri] = float(row["implicit_rating"])
        self._user_item = mat

        # ── User similarity (cosine) ────────────────────────────────────────
        self._user_sim = cosine_similarity(mat)  # (n_u, n_u)

        # ── Route content feature matrix ────────────────────────────────────
        feat_rows = []
        for rc in route_set:
            route = next(r for r in route_features if r["route_code"] == rc)
            feat_rows.append([float(route[col]) for col in CB_FEATURE_COLS])
        self._route_feat = np.array(feat_rows, dtype=np.float32)  # (n_r, n_feat)

        # ── User preference profiles ─────────────────────────────────────────
        # Weighted average of route feature vectors (weight = implicit_rating)
        user_prof = np.zeros((n_u, len(CB_FEATURE_COLS)), dtype=np.float32)
        weight_sum = np.zeros(n_u, dtype=np.float32)
        for row in train:
            ui = self._user_index[row["user_id"]]
            ri = self._route_index[row["route_code"]]
            w  = float(row["implicit_rating"])
            user_prof[ui] += w * self._route_feat[ri]
            weight_sum[ui] += w
        nz = weight_sum > 0
        user_prof[nz] /= weight_sum[nz, np.newaxis]
        self._user_profiles = user_prof  # (n_u, n_feat)

        return self

    # ── Predict for a single user ─────────────────────────────────────────────

    def _cf_scores(self, uid: str) -> np.ndarray:
        """Return CF score vector over all routes for user uid."""
        if uid not in self._user_index:
            return np.zeros(len(self._route_codes), dtype=np.float32)

        ui      = self._user_index[uid]
        sims    = self._user_sim[ui].copy()
        sims[ui] = 0.0  # exclude self

        top_k_idx = np.argpartition(-sims, min(self.k_neighbors, len(sims) - 1))[
            : self.k_neighbors
        ]
        top_k_sims = sims[top_k_idx]
        sim_sum    = top_k_sims.sum()
        if sim_sum == 0:
            return np.zeros(len(self._route_codes), dtype=np.float32)

        # Weighted average of neighbours' ratings
        weighted = (self._user_item[top_k_idx] * top_k_sims[:, np.newaxis]).sum(axis=0)
        return weighted / sim_sum

    def _cb_scores(self, uid: str) -> np.ndarray:
        """Return content-based score vector over all routes for user uid."""
        if uid not in self._user_index:
            return np.zeros(len(self._route_codes), dtype=np.float32)

        ui   = self._user_index[uid]
        prof = self._user_profiles[ui : ui + 1]  # (1, n_feat)
        if np.all(prof == 0):
            return np.zeros(len(self._route_codes), dtype=np.float32)
        sims = cosine_similarity(prof, self._route_feat)[0]  # (n_r,)
        return sims.astype(np.float32)

    def recommend(
        self,
        user_id: str,
        n: int = 3,
        exclude_seen: bool = True,
    ) -> list[dict]:
        """
        Return top-n recommended routes for user_id.

        Returns list of dicts:
            { route_code, hybrid_score, cf_score, cb_score, reason }
        """
        cf = self._cf_scores(user_id)
        cb = self._cb_scores(user_id)

        # Normalise each component to [0,1] to make alpha meaningful
        def _safe_norm(arr: np.ndarray) -> np.ndarray:
            hi = arr.max()
            return arr / hi if hi > 0 else arr

        cf_n = _safe_norm(cf)
        cb_n = _safe_norm(cb)
        hybrid = self.alpha * cf_n + (1.0 - self.alpha) * cb_n

        if exclude_seen and user_id in self._user_index:
            ui = self._user_index[user_id]
            seen_mask = self._user_item[ui] > 0
            hybrid[seen_mask] = -1.0  # push seen routes to the bottom

        top_idx = np.argsort(-hybrid)[:n]
        results = []
        for idx in top_idx:
            rc       = self._route_codes[idx]
            hs       = float(hybrid[idx])
            cf_score = float(cf_n[idx])
            cb_score = float(cb_n[idx])
            reason   = (
                "collaborative_match" if cf_score >= cb_score else "content_match"
            )
            results.append(
                {
                    "route_code":    rc,
                    "hybrid_score":  round(hs, 4),
                    "cf_score":      round(cf_score, 4),
                    "cb_score":      round(cb_score, 4),
                    "reason":        reason,
                }
            )
        return results

    # ── Batch evaluation ──────────────────────────────────────────────────────

    def evaluate(
        self,
        test: list[dict],
        k_values: list[int] = [1, 3, 5],
        exclude_seen: bool = True,
    ) -> dict[str, float]:
        """Compute mean ranking metrics against the held-out test set."""
        ground_truth: dict[str, set[str]] = defaultdict(set)
        for row in test:
            ground_truth[row["user_id"]].add(row["route_code"])

        predictions: dict[str, list[str]] = {}
        for uid in ground_truth:
            recs = self.recommend(uid, n=max(k_values), exclude_seen=exclude_seen)
            predictions[uid] = [r["route_code"] for r in recs]

        return evaluate_recommendations(predictions, dict(ground_truth), k_values)

    def coverage(self, test: list[dict], n: int = 5) -> float:
        """Fraction of all routes recommended to at least one test user."""
        test_users = list({row["user_id"] for row in test})
        recommended: set[str] = set()
        for uid in test_users:
            for rec in self.recommend(uid, n=n):
                recommended.add(rec["route_code"])
        return len(recommended) / len(self._all_route_codes)


# ── Grid search ───────────────────────────────────────────────────────────────

def grid_search(
    train: list[dict],
    test: list[dict],
    route_features: list[dict],
    k_grid: list[int] = [5, 10, 20, 30, 50],
    alpha_grid: list[float] = [0.3, 0.4, 0.5, 0.6, 0.7, 0.8],
    optimise_metric: str = "NDCG@3",
) -> tuple[int, float, dict[str, float]]:
    """Exhaustive grid search; returns (best_k, best_alpha, best_metrics)."""
    best_score  = -1.0
    best_k      = k_grid[0]
    best_alpha  = alpha_grid[0]
    best_metrics: dict[str, float] = {}
    results     = []

    total = len(k_grid) * len(alpha_grid)
    done  = 0

    for k in k_grid:
        for alpha in alpha_grid:
            model = SmartBusRecommender(k_neighbors=k, alpha=alpha)
            model.fit(train, route_features)
            metrics = model.evaluate(test)
            score   = metrics[optimise_metric]
            results.append((k, alpha, score, metrics))
            if score > best_score:
                best_score   = score
                best_k       = k
                best_alpha   = alpha
                best_metrics = metrics
            done += 1
            print(
                f"  [{done:>2}/{total}] k={k:>2}, alpha={alpha:.1f}  "
                f"{optimise_metric}={score:.4f}"
                + (" ← best" if score == best_score else "")
            )

    print(f"\nBest: k={best_k}, alpha={best_alpha}  {optimise_metric}={best_score:.4f}")
    return best_k, best_alpha, best_metrics, results


# ── Main ──────────────────────────────────────────────────────────────────────

def main() -> None:
    print("=" * 60)
    print("SmartBus Hybrid Recommendation Model — Training")
    print("=" * 60)

    # ── Load data ─────────────────────────────────────────────────────────────
    print("\n[1] Loading data …")
    train, test, route_features, user_profiles = load_data()
    print(f"    train={len(train):,}  test={len(test):,}  "
          f"routes={len(route_features)}  users={len(user_profiles)}")

    # ── Baseline (random) ─────────────────────────────────────────────────────
    print("\n[2] Computing random baseline …")
    n_routes = len(route_features)
    all_codes = [r["route_code"] for r in route_features]
    rng       = np.random.default_rng(RANDOM_SEED)
    ground_truth = defaultdict(set)
    for row in test:
        ground_truth[row["user_id"]].add(row["route_code"])
    rand_preds = {
        uid: list(rng.choice(all_codes, size=5, replace=False))
        for uid in ground_truth
    }
    baseline = evaluate_recommendations(rand_preds, dict(ground_truth))
    print("    Random baseline:", {k: f"{v:.4f}" for k, v in baseline.items()})

    # ── Grid search ───────────────────────────────────────────────────────────
    print("\n[3] Hyperparameter grid search (optimising NDCG@3) …")
    best_k, best_alpha, best_metrics, gs_results = grid_search(
        train, test, route_features,
        k_grid=[5, 10, 20, 30, 50],
        alpha_grid=[0.3, 0.4, 0.5, 0.6, 0.7, 0.8],
    )

    # ── Train final model ─────────────────────────────────────────────────────
    print("\n[4] Training final model …")
    final_model = SmartBusRecommender(k_neighbors=best_k, alpha=best_alpha)
    final_model.fit(train, route_features)
    final_metrics = final_model.evaluate(test, k_values=[1, 3, 5])
    cov = final_model.coverage(test, n=5)

    print("\nFinal model metrics (test set):")
    for name, val in final_metrics.items():
        base_val = baseline.get(name, 0.0)
        lift     = (val - base_val) / base_val * 100 if base_val > 0 else float("inf")
        print(f"    {name:>8} = {val:.4f}  (baseline {base_val:.4f}, +{lift:.1f}%)")
    print(f"    Coverage@5 = {cov:.4f}")

    # ── Save artifacts ────────────────────────────────────────────────────────
    print("\n[5] Saving model artifacts …")
    joblib.dump(final_model, MODEL_DIR / "smartbus_recommender.joblib")
    print(f"    saved → {MODEL_DIR}/smartbus_recommender.joblib")

    metadata = {
        "model_version":  "1.0.0",
        "algorithm":      "HybridRecommender (CF + CB)",
        "k_neighbors":    best_k,
        "alpha":          best_alpha,
        "trained_at":     datetime.utcnow().isoformat() + "Z",
        "train_size":     len(train),
        "test_size":      len(test),
        "n_users":        len(final_model._user_ids),
        "n_routes":       len(final_model._route_codes),
        "metrics":        {k: round(v, 6) for k, v in final_metrics.items()},
        "baseline":       {k: round(v, 6) for k, v in baseline.items()},
        "coverage_at_5":  round(cov, 6),
        "cb_features":    CB_FEATURE_COLS,
        "seed":           RANDOM_SEED,
    }
    with open(MODEL_DIR / "model_metadata.json", "w") as fh:
        json.dump(metadata, fh, indent=2)
    print(f"    saved → {MODEL_DIR}/model_metadata.json")

    gs_output = [
        {"k": k, "alpha": a, "ndcg3": s, **m}
        for k, a, s, m in gs_results
    ]
    with open(MODEL_DIR / "grid_search_results.json", "w") as fh:
        json.dump(gs_output, fh, indent=2)
    print(f"    saved → {MODEL_DIR}/grid_search_results.json")

    # ── Sample predictions ────────────────────────────────────────────────────
    print("\n[6] Sample predictions …")
    sample_users = list(ground_truth.keys())[:3]
    for uid in sample_users:
        recs = final_model.recommend(uid, n=3)
        actual = ground_truth.get(uid, set())
        hits   = [r["route_code"] for r in recs if r["route_code"] in actual]
        print(f"\n  user_id={uid}")
        print(f"  actual (test) routes: {sorted(actual)}")
        print(f"  predicted top-3: {[r['route_code'] for r in recs]}")
        print(f"  hits: {hits}")
        for r in recs:
            print(f"    {r['route_code']}  hybrid={r['hybrid_score']:.4f}  "
                  f"cf={r['cf_score']:.4f}  cb={r['cb_score']:.4f}  {r['reason']}")

    print("\n[Done] Training complete.")
    return final_model, metadata, gs_results, baseline


if __name__ == "__main__":
    main()
