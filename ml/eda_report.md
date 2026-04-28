# SmartBus Dataset — Exploratory Data Analysis

Generated: 2026-04-25  
Dataset version: synthetic v1.0 (seed=42)

---

## 1. Dataset Overview

| File | Rows | Description |
|------|------|-------------|
| `data/raw/users.csv` | 300 | Registered passengers with home stops and travel profiles |
| `data/raw/routes.csv` | 10 | Bus routes with prices, stops, and schedules |
| `data/raw/bookings.csv` | 5,174 | Booking records spanning 2025-01-01 to 2026-04-01 |
| `data/processed/interactions.csv` | 1,507 | (user, route) pairs with booking counts and implicit ratings |
| `data/processed/route_features.csv` | 10 | One-hot encoded route feature vectors |
| `data/processed/user_profiles.csv` | 300 | Aggregated user behaviour profiles |
| `data/processed/train.csv` | 1,125 | Training split (≈74.7%) |
| `data/processed/test.csv` | 382 | Test split (≈25.3%) |

---

## 2. Booking Distribution Analysis

### 2.1 Booking Status

| Status | Count | Percentage |
|--------|-------|------------|
| COMPLETED | 3,858 | 74.6% |
| CANCELLED | 1,316 | 25.4% |
| **Total** | **5,174** | 100% |

Only COMPLETED bookings contribute to the ML training signal. CANCELLED bookings are
retained in `raw/bookings.csv` for completeness but filtered out during preprocessing.

### 2.2 Fare Distribution

| Metric | Value |
|--------|-------|
| Minimum | $7.00 (1 passenger × SB-203) |
| Maximum | $72.00 (4 passengers × SB-304) |
| Mean | $19.69 |

The wide range ($7–$72) reflects both route price variation and passenger count (1–4).
No normalisation is applied to raw booking amounts — per-route unit prices are
normalised separately in `route_features.csv`.

### 2.3 Bookings per Route (COMPLETED only)

```
SB-101  Downtown Terminal → Airport Station  │████████████████         │  287  ( 7.4%)
SB-102  Downtown Terminal → University       │██████████████████████   │  339  ( 8.8%)
SB-103  Downtown Terminal → Harbor           │███████████████████████  │  357  ( 9.3%)
SB-201  Airport Station   → City Center      │██████████████████████████████│  455 (11.8%)
SB-202  University        → City Center      │█████████████████████████████ │  449 (11.6%)
SB-203  City Center       → Bus Depot        │████████████████████████ │  383  ( 9.9%)
SB-301  Harbor            → Downtown Terminal│████████████████████████ │  381  ( 9.9%)
SB-302  Bus Depot         → Harbor           │█████████████████████████│  391 (10.1%)
SB-303  City Center       → Airport Station  │█████████████████████████│  395 (10.2%)
SB-304  University        → Airport Station  │███████████████████████████ │  421 (10.9%)
```

**Observation:** Distribution is moderately uniform (CV ≈ 0.15) with SB-101
(Downtown→Airport, lower frequency commuter route) slightly underrepresented.
SB-201 and SB-202 (hub routes through City Center) are most popular.

**No data imbalance issue** for the recommendation model, which treats all routes equally.

---

## 3. User Behaviour Analysis

### 3.1 Travel Profiles

| Profile | Users | Expected Booking Range |
|---------|-------|----------------------|
| occasional | 86 (28.7%) | 3–9 |
| frequent | 83 (27.7%) | 10–24 |
| commuter | 72 (24.0%) | 25–60 |
| rare | 59 (19.7%) | 1–2 |

### 3.2 Booking Frequency Distribution

| Booking Count | Users | Behaviour |
|---------------|-------|-----------|
| 0 (cold start) | 12 (4.0%) | Registered but never booked |
| 1–2 | 63 (21.0%) | Rare travellers |
| 3–5 | 45 (15.0%) | Occasional users |
| 6–10 | 52 (17.3%) | Regular users |
| 11–20 | 57 (19.0%) | Frequent users |
| 21+ | 71 (23.7%) | Power users / commuters |

| Metric | Value |
|--------|-------|
| Min bookings/user | 1 |
| Max bookings/user | 47 |
| Mean bookings/user | 13.4 |

**Cold-start users (12):** Users with no COMPLETED bookings have no interaction history.
The model falls back to content-based features (their home stop and travel profile)
for these users.

**Power user skew:** 71 users with 21+ bookings drive a disproportionate share of the
training signal. This is expected and realistic (commuters book daily).

### 3.3 Home Stop Distribution

Users are uniformly distributed across 6 stops by design. This ensures no single stop
dominates the `origin_match` feature in the recommendation model.

---

## 4. Route Feature Analysis

### 4.1 Route Catalogue

| Route | From → To | Unit Price | Price Tier | Departs |
|-------|-----------|-----------|------------|---------|
| SB-101 | Downtown Terminal → Airport Station | $15.00 | MEDIUM | 08:00 |
| SB-102 | Downtown Terminal → University | $8.50 | LOW | 07:30 |
| SB-103 | Downtown Terminal → Harbor | $12.00 | MEDIUM | 09:00 |
| SB-201 | Airport Station → City Center | $13.50 | MEDIUM | 10:00 |
| SB-202 | University → City Center | $9.00 | LOW | 08:00 |
| SB-203 | City Center → Bus Depot | $7.00 | LOW | 11:00 |
| SB-301 | Harbor → Downtown Terminal | $12.00 | MEDIUM | 07:00 |
| SB-302 | Bus Depot → Harbor | $11.00 | MEDIUM | 06:30 |
| SB-303 | City Center → Airport Station | $16.50 | HIGH | 14:00 |
| SB-304 | University → Airport Station | $18.00 | HIGH | 13:00 |

### 4.2 Price Tier Breakdown

| Tier | Routes | Price Range |
|------|--------|-------------|
| LOW | 3 (SB-102, SB-202, SB-203) | $7.00–$9.00 |
| MEDIUM | 5 (SB-101, SB-103, SB-201, SB-301, SB-302) | $11.00–$15.00 |
| HIGH | 2 (SB-303, SB-304) | $16.50–$18.00 |

Price range: $7.00–$18.00, mean: $12.25.

### 4.3 Departure Hour Bucket Distribution

| Bucket | Routes | Hours |
|--------|--------|-------|
| early_morning | 1 (SB-302) | 06:00–07:59 |
| morning_rush | 4 (SB-301, SB-101, SB-102, SB-202) | 07:00–09:59 |
| midday | 2 (SB-103, SB-203) | 09:00–12:59 |
| afternoon | 2 (SB-303, SB-304) | 13:00–15:59 |

**Observation:** Morning-rush routes (4 of 10) dominate, which aligns with commuter
travel patterns in the booking data.

---

## 5. Interaction Matrix Analysis

| Metric | Value |
|--------|-------|
| Total (user, route) pairs | 1,507 |
| Matrix density | 50.2% (1,507 / 3,000 possible) |
| Min booking count per pair | 1 |
| Max booking count per pair | 15 |
| Mean booking count per pair | 2.56 |
| Implicit rating range | 0.0000–1.0000 (MinMax scaled) |

**50.2% matrix density** is high relative to typical recommendation datasets (often <5%).
This is expected for a synthetic dataset where users are assigned preferred routes.
The density ensures collaborative filtering has sufficient signal without cold-start issues
for the majority of users.

---

## 6. Missing Value Analysis

| Dataset | Column | Missing | Handling |
|---------|--------|---------|----------|
| bookings | all | 0 | N/A |
| users | all | 0 | N/A |
| routes | all | 0 | N/A |
| interactions | all | 0 | N/A |

**No missing values detected.** The synthetic generator produces complete records.
The preprocessing `validate()` function asserts this on every run.

---

## 7. Outlier Detection

### Booking Amounts
- Maximum: $72.00 (4 passengers × SB-304 at $18.00)
- This is structurally valid: `passengers × unit_price`. No outlier removal needed.

### Booking Counts per User
- Maximum: 47 bookings (commuter profile)
- This is expected for the 15-month date range. Not an outlier.

### Interaction Booking Count
- Maximum: 15 (same user booked same route 15 times)
- Valid for commuter profile. MinMax scaling naturally handles this.

---

## 8. Feature Correlation Analysis

**High-signal features for recommendation:**

| Feature | Signal Type | Justification |
|---------|-------------|--------------|
| `implicit_rating` | Target | Normalised booking frequency — primary training signal |
| `origin_*` (one-hot) | Content | User's home stop matches route origin → strong positive signal |
| `tier_*` (one-hot) | Content | User preferred_tier from profile matches route tier |
| `hour_*` (one-hot) | Content | User preferred_hour matches route departure bucket |
| `booking_count` | Collaborative | Raw interaction strength (pre-scaling) |

**Correlation observations:**
- Routes departing from a user's `home_stop` have 3× higher booking probability
  (enforced by the generator's `_preferred_routes` logic).
- LOW-tier routes (SB-102, SB-202, SB-203) attract frequent/occasional users;
  HIGH-tier routes (SB-303, SB-304) are more common for rare/occasional users
  making one-off airport trips.
- Morning-rush routes have the highest mean booking counts per user.

---

## 9. Train / Test Split

**Method:** Per-user temporal hold-out — last ⌈20%⌉ interactions per user held for test.

| Split | Interactions | Percentage |
|-------|-------------|------------|
| Train | 1,125 | 74.7% |
| Test | 382 | 25.3% |

Cold-start users (zero interactions after filtering) are included in `user_profiles.csv`
for content-based predictions but excluded from train/test evaluation.

**Stratification:** The per-user split ensures every user with ≥2 interactions
contributes to both train and test, preventing data leakage from future bookings.

---

## 10. Preprocessing Pipeline Summary

| Step | Input | Output | Notes |
|------|-------|--------|-------|
| 1. Load | `raw/*.csv` | In-memory DataFrames | Validates schema integrity |
| 2. Validate | All raw | Assertions | Zero orphans, zero duplicates |
| 3. Filter | All bookings | COMPLETED only | Removes 25.4% CANCELLED |
| 4. Aggregate | Bookings | Interaction pairs | Groups by (user_id, route_code) |
| 5. Scale | booking_count | implicit_rating | MinMax over all pairs (0→1) |
| 6. Route OHE | routes.csv | route_features.csv | 22-column feature vector per route |
| 7. User profile | users + bookings | user_profiles.csv | Aggregated preferences |
| 8. Split | interactions | train / test | Per-user 80/20 hold-out |

All steps are deterministic given the same raw data. Re-run with:

```sh
source ml/venv/bin/activate
python ml/preprocessing.py
```
