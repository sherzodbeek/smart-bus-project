# SmartBus ML — Model Documentation

---

## Algorithm

**Hybrid Route Recommendation System**  
Two-component model combining user-user collaborative filtering with content-based filtering.

```
score(user, route) = alpha × CF_score + (1-alpha) × CB_score
```

| Component | Method | Features |
|-----------|--------|---------|
| Collaborative (CF) | Cosine similarity, k-nearest neighbours | Implicit booking ratings |
| Content-Based (CB) | Cosine similarity, user preference profile | 21 route features (price, origin, destination, time, tier) |

---

## Best Hyperparameters

| Parameter | Value | Search range |
|-----------|-------|-------------|
| `k_neighbors` | 30 | 5, 10, 20, 30, 50 |
| `alpha` (CF weight) | 0.3 | 0.3, 0.4, 0.5, 0.6, 0.7, 0.8 |

Optimised for **NDCG@3** via exhaustive grid search (30 combinations).

---

## Performance on Test Set

| Metric | Value | vs. Random Baseline |
|--------|-------|-------------------|
| Precision@1 | **0.2846** | +100.0% |
| Recall@1 | **0.1626** | +86.0% |
| Precision@3 | **0.2778** | +97.1% |
| Recall@3 | **0.4898** | +86.8% |
| NDCG@3 | **0.4051** | +91.6% |
| NDCG@5 | **0.4979** | +57.0% |
| Coverage@5 | **1.0000** | all routes covered |

Test set: 382 interactions, 288 users (per-user temporal 75/25 split).

---

## Model Artifacts

```
ml/model/
├── smartbus_recommender.joblib   # serialised SmartBusRecommender
├── model_metadata.json           # hyperparameters and metrics
└── grid_search_results.json      # all grid search runs
```

---

## Quick Start

```sh
source ml/venv/bin/activate

# Train (regenerate artifacts):
python ml/model_training.py

# Inference — by user_id:
python ml/inference.py --user_id 42

# Inference — by email:
python ml/inference.py --email alice.smith1@example.com

# JSON output:
python ml/inference.py --user_id 42 --json

# Model info:
python ml/inference.py --user_id 1 --info
```

---

## Programmatic Usage (from Python)

```python
from ml.inference import SmartBusInference

engine = SmartBusInference()

# Returns top-3 recommendations for user 42
result = engine.recommend_for_user("42", n=3)
print(result["recommendations"])
# [
#   {"route_code": "SB-101", "hybrid_score": 0.7255, "cf_score": 0.14, "cb_score": 0.97, "reason": "content_match"},
#   ...
# ]

# By email
result = engine.recommend_for_email("alice.smith1@example.com", n=3)
```

---

## Response Format

```json
{
  "user_id": "42",
  "is_cold_start": false,
  "model_version": "1.0.0",
  "recommendations": [
    {
      "route_code":   "SB-101",
      "hybrid_score": 0.7255,
      "cf_score":     0.1449,
      "cb_score":     0.9743,
      "reason":       "content_match"
    }
  ]
}
```

| Field | Description |
|-------|-------------|
| `is_cold_start` | True if user has no booking history (popularity fallback used) |
| `hybrid_score` | Combined CF+CB score in [0, 1] |
| `cf_score` | Normalised collaborative filtering score |
| `cb_score` | Normalised content-based score |
| `reason` | `collaborative_match`, `content_match`, or `popularity_fallback` |

---

## Reproducibility

All random operations use `seed=42`. Re-running `model_training.py` on the same raw data
produces bit-identical model artifacts. Verified on Python 3.14, Scikit-learn ≥ 1.4, NumPy ≥ 1.26.
