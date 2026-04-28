"""
SmartBus ML preprocessing pipeline.

Transforms raw CSVs in ml/data/raw/ into feature matrices for the
hybrid route recommendation model and writes them to ml/data/processed/.

Outputs
-------
interactions.csv  — one row per (user_id, route_code) pair that has ≥ 1 COMPLETED booking;
                    columns: user_id, route_code, booking_count, implicit_rating
route_features.csv— one row per route; engineered features for content-based filtering
user_profiles.csv — one row per user; engineered features (preferred origin, price tier, etc.)
train.csv         — 80% of interactions for model training
test.csv          — 20% of interactions held out for evaluation

Run:
    python ml/preprocessing.py
"""

from __future__ import annotations

import csv
from collections import defaultdict
from pathlib import Path

RAW_DIR  = Path(__file__).parent / "data" / "raw"
PROC_DIR = Path(__file__).parent / "data" / "processed"
PROC_DIR.mkdir(parents=True, exist_ok=True)

RANDOM_SEED = 42


# ── Helpers ───────────────────────────────────────────────────────────────────

def read_csv(path: Path) -> list[dict]:
    with open(path, newline="") as fh:
        return list(csv.DictReader(fh))


def write_csv(path: Path, rows: list[dict], fieldnames: list[str]) -> None:
    with open(path, "w", newline="") as fh:
        writer = csv.DictWriter(fh, fieldnames=fieldnames)
        writer.writeheader()
        writer.writerows(rows)
    print(f"  wrote {len(rows):,} rows → {path}")


def price_tier(price: float) -> str:
    if price < 10.0:
        return "LOW"
    if price <= 15.0:
        return "MEDIUM"
    return "HIGH"


def departure_hour_bucket(time_str: str) -> str:
    """Map HH:MM to a named bucket for one-hot encoding."""
    hour = int(time_str.split(":")[0])
    if hour < 8:
        return "early_morning"
    if hour < 10:
        return "morning_rush"
    if hour < 13:
        return "midday"
    if hour < 16:
        return "afternoon"
    return "evening"


def minmax_scale(values: list[float]) -> list[float]:
    lo, hi = min(values), max(values)
    if hi == lo:
        return [0.0] * len(values)
    return [(v - lo) / (hi - lo) for v in values]


# ── Step 1: load raw data ─────────────────────────────────────────────────────

def load_raw() -> tuple[list[dict], list[dict], list[dict]]:
    users    = read_csv(RAW_DIR / "users.csv")
    routes   = read_csv(RAW_DIR / "routes.csv")
    bookings = read_csv(RAW_DIR / "bookings.csv")
    print(f"Loaded: {len(users)} users, {len(routes)} routes, {len(bookings):,} bookings")
    return users, routes, bookings


# ── Step 2: validate data quality ────────────────────────────────────────────

def validate(users: list[dict], routes: list[dict], bookings: list[dict]) -> None:
    user_ids   = {u["user_id"] for u in users}
    route_codes = {r["route_code"] for r in routes}

    missing_user  = sum(1 for b in bookings if b["user_id"] not in user_ids)
    missing_route = sum(1 for b in bookings if b["route_code"] not in route_codes)
    duplicates    = len(bookings) - len({b["booking_reference"] for b in bookings})
    missing_email = sum(1 for b in bookings if not b.get("customer_email"))

    print(f"Validation:")
    print(f"  Orphan bookings (missing user):  {missing_user}")
    print(f"  Orphan bookings (missing route): {missing_route}")
    print(f"  Duplicate booking references:    {duplicates}")
    print(f"  Bookings with missing email:     {missing_email}")

    assert missing_user  == 0, "Orphan bookings found (user_id not in users)"
    assert missing_route == 0, "Orphan bookings found (route_code not in routes)"
    assert duplicates    == 0, "Duplicate booking_reference found"
    print("  All checks passed.")


# ── Step 3: build interaction matrix ─────────────────────────────────────────

def build_interactions(bookings: list[dict]) -> list[dict]:
    """
    Aggregate COMPLETED bookings into (user_id, route_code) → booking_count.
    Implicit rating = normalized booking_count (MinMax over all pairs).
    """
    counts: dict[tuple[str, str], int] = defaultdict(int)
    for b in bookings:
        if b["current_state"] == "COMPLETED":
            counts[(b["user_id"], b["route_code"])] += 1

    raw_counts = list(counts.values())
    scaled     = minmax_scale([float(c) for c in raw_counts])

    interactions = []
    for (uid, rc), cnt, rating in zip(counts.keys(), raw_counts, scaled):
        interactions.append(
            {
                "user_id":         uid,
                "route_code":      rc,
                "booking_count":   cnt,
                "implicit_rating": round(rating, 6),
            }
        )
    return interactions


# ── Step 4: route feature engineering ────────────────────────────────────────

ALL_STOPS = [
    "Downtown Terminal", "Airport Station", "University",
    "Bus Depot", "City Center", "Harbor",
]

ALL_HOUR_BUCKETS = [
    "early_morning", "morning_rush", "midday", "afternoon", "evening",
]

ALL_PRICE_TIERS = ["LOW", "MEDIUM", "HIGH"]


def build_route_features(routes: list[dict]) -> list[dict]:
    rows = []
    prices = [float(r["unit_price"]) for r in routes]
    price_scaled = minmax_scale(prices)

    for r, ps in zip(routes, price_scaled):
        tier   = price_tier(float(r["unit_price"]))
        bucket = departure_hour_bucket(r["departure_time"])

        # One-hot: origin stop
        origin_ohe = {f"origin_{s.replace(' ', '_')}": int(r["from_stop"] == s)
                      for s in ALL_STOPS}
        # One-hot: destination stop
        dest_ohe   = {f"dest_{s.replace(' ', '_')}": int(r["to_stop"] == s)
                      for s in ALL_STOPS}
        # One-hot: departure hour bucket
        hour_ohe   = {f"hour_{b}": int(bucket == b) for b in ALL_HOUR_BUCKETS}
        # One-hot: price tier
        tier_ohe   = {f"tier_{t}": int(tier == t) for t in ALL_PRICE_TIERS}

        row = {
            "route_code":         r["route_code"],
            "from_stop":          r["from_stop"],
            "to_stop":            r["to_stop"],
            "unit_price":         float(r["unit_price"]),
            "price_scaled":       round(ps, 6),
            "price_tier":         tier,
            "departure_time":     r["departure_time"],
            "departure_bucket":   bucket,
            "seats_total":        int(r["seats_total"]),
        }
        row.update(origin_ohe)
        row.update(dest_ohe)
        row.update(hour_ohe)
        row.update(tier_ohe)
        rows.append(row)
    return rows


def route_feature_columns(route_features: list[dict]) -> list[str]:
    base = ["route_code", "from_stop", "to_stop", "unit_price", "price_scaled",
            "price_tier", "departure_time", "departure_bucket", "seats_total"]
    extra = [k for k in route_features[0] if k not in base]
    return base + extra


# ── Step 5: user profile engineering ─────────────────────────────────────────

def build_user_profiles(
    users: list[dict],
    bookings: list[dict],
    routes: list[dict],
) -> list[dict]:
    route_map     = {r["route_code"]: r for r in routes}
    user_bookings: dict[str, list[dict]] = defaultdict(list)
    for b in bookings:
        if b["current_state"] == "COMPLETED":
            user_bookings[b["user_id"]].append(b)

    user_profiles = []
    for u in users:
        bks = user_bookings.get(u["user_id"], [])
        total_bks = len(bks)

        # Most frequent origin (from actual bookings; fall back to home_stop)
        origin_counts: dict[str, int] = defaultdict(int)
        for b in bks:
            origin_counts[b["from_stop"]] += 1
        frequent_origin = (
            max(origin_counts, key=origin_counts.__getitem__)
            if origin_counts else u["home_stop"]
        )

        # Average spend
        amounts = [float(b["total_amount"]) for b in bks]
        avg_spend = round(sum(amounts) / len(amounts), 2) if amounts else 0.0

        # Preferred price tier (plurality of booked routes)
        tier_counts: dict[str, int] = defaultdict(int)
        for b in bks:
            rt = route_map.get(b["route_code"])
            if rt:
                tier_counts[price_tier(float(rt["unit_price"]))] += 1
        preferred_tier = (
            max(tier_counts, key=tier_counts.__getitem__)
            if tier_counts else "MEDIUM"
        )

        # Preferred hour bucket
        hour_counts: dict[str, int] = defaultdict(int)
        for b in bks:
            rt = route_map.get(b["route_code"])
            if rt:
                hour_counts[departure_hour_bucket(rt["departure_time"])] += 1
        preferred_hour = (
            max(hour_counts, key=hour_counts.__getitem__)
            if hour_counts else "morning_rush"
        )

        user_profiles.append(
            {
                "user_id":          u["user_id"],
                "email":            u["email"],
                "home_stop":        u["home_stop"],
                "travel_profile":   u["travel_profile"],
                "total_bookings":   total_bks,
                "frequent_origin":  frequent_origin,
                "avg_spend":        avg_spend,
                "preferred_tier":   preferred_tier,
                "preferred_hour":   preferred_hour,
            }
        )
    return user_profiles


# ── Step 6: train / test split ────────────────────────────────────────────────

def train_test_split(
    interactions: list[dict],
    test_ratio: float = 0.20,
) -> tuple[list[dict], list[dict]]:
    """
    Per-user temporal split: hold out the last ⌈test_ratio⌉ interactions
    per user as the test set; use the rest for training.

    Users with only 1 interaction stay in train only (cold-start — the model
    cannot be evaluated on them but can still generate predictions).
    """
    # Group interactions by user
    by_user: dict[str, list[dict]] = defaultdict(list)
    for row in interactions:
        by_user[row["user_id"]].append(row)

    train, test = [], []
    import math
    for uid, rows in by_user.items():
        n_test = max(1, math.ceil(len(rows) * test_ratio))
        if len(rows) <= 1:
            train.extend(rows)
        else:
            train.extend(rows[:-n_test])
            test.extend(rows[-n_test:])

    print(f"Split: {len(train):,} train, {len(test):,} test "
          f"({100*len(test)/(len(train)+len(test)):.1f}% test)")
    return train, test


# ── Main ──────────────────────────────────────────────────────────────────────

def run() -> None:
    print("=" * 60)
    print("SmartBus ML Preprocessing Pipeline")
    print("=" * 60)

    users, routes, bookings = load_raw()
    validate(users, routes, bookings)

    print("\nBuilding interaction matrix …")
    interactions = build_interactions(bookings)
    write_csv(
        PROC_DIR / "interactions.csv",
        interactions,
        ["user_id", "route_code", "booking_count", "implicit_rating"],
    )

    print("\nEngineering route features …")
    route_features = build_route_features(routes)
    write_csv(
        PROC_DIR / "route_features.csv",
        route_features,
        route_feature_columns(route_features),
    )

    print("\nBuilding user profiles …")
    user_profiles = build_user_profiles(users, bookings, routes)
    write_csv(
        PROC_DIR / "user_profiles.csv",
        user_profiles,
        ["user_id", "email", "home_stop", "travel_profile", "total_bookings",
         "frequent_origin", "avg_spend", "preferred_tier", "preferred_hour"],
    )

    print("\nSplitting train / test …")
    train, test = train_test_split(interactions)
    write_csv(
        PROC_DIR / "train.csv", train,
        ["user_id", "route_code", "booking_count", "implicit_rating"],
    )
    write_csv(
        PROC_DIR / "test.csv", test,
        ["user_id", "route_code", "booking_count", "implicit_rating"],
    )

    print("\nPreprocessing complete.")
    print(f"  Unique users with interactions: "
          f"{len({r['user_id'] for r in interactions})}")
    print(f"  Unique routes in interactions:  "
          f"{len({r['route_code'] for r in interactions})}")
    print(f"  Interaction matrix density:     "
          f"{len(interactions) / (len(users) * len(routes)) * 100:.1f}%")


if __name__ == "__main__":
    run()
