"""
Synthetic dataset generator for SmartBus Phase IV ML training.

Generates realistic booking patterns matching the Phase III PostgreSQL schema:
  - users.csv        — 300 registered passengers with home stops
  - routes.csv       — 10 routes across 6 stops
  - bookings.csv     — 4 500+ booking records with temporal and behavioural patterns

Run:
    python ml/generate_data.py
"""

import csv
import random
from datetime import datetime, timedelta
from pathlib import Path

SEED = 42
random.seed(SEED)

RAW_DIR = Path(__file__).parent / "data" / "raw"
RAW_DIR.mkdir(parents=True, exist_ok=True)

# ── Domain constants ──────────────────────────────────────────────────────────

STOPS = [
    "Downtown Terminal",
    "Airport Station",
    "University",
    "Bus Depot",
    "City Center",
    "Harbor",
]

ROUTES = [
    # (code, from, to, departure_time, arrival_time, unit_price, seats_total)
    ("SB-101", "Downtown Terminal", "Airport Station",  "08:00", "08:45", 15.00, 45),
    ("SB-102", "Downtown Terminal", "University",       "07:30", "08:00", 8.50,  50),
    ("SB-103", "Downtown Terminal", "Harbor",           "09:00", "09:50", 12.00, 40),
    ("SB-201", "Airport Station",   "City Center",      "10:00", "10:40", 13.50, 45),
    ("SB-202", "University",        "City Center",      "08:00", "08:25", 9.00,  55),
    ("SB-203", "City Center",       "Bus Depot",        "11:00", "11:30", 7.00,  50),
    ("SB-301", "Harbor",            "Downtown Terminal","07:00", "07:50", 12.00, 40),
    ("SB-302", "Bus Depot",         "Harbor",           "06:30", "07:20", 11.00, 45),
    ("SB-303", "City Center",       "Airport Station",  "14:00", "14:35", 16.50, 40),
    ("SB-304", "University",        "Airport Station",  "13:00", "13:55", 18.00, 35),
]

BOOKING_STATES = ["COMPLETED", "COMPLETED", "COMPLETED", "CANCELLED"]  # 75% complete

START_DATE = datetime(2025, 1, 1)
END_DATE   = datetime(2026, 4, 1)
DATE_RANGE_DAYS = (END_DATE - START_DATE).days

FIRST_NAMES = [
    "Alice", "Bob", "Carol", "David", "Emma", "Frank", "Grace", "Henry",
    "Irene", "James", "Karen", "Leo", "Mia", "Noah", "Olivia", "Paul",
    "Quinn", "Rachel", "Sam", "Tara", "Uma", "Victor", "Wendy", "Xander",
    "Yara", "Zoe", "Aaron", "Beth", "Chris", "Diana",
]
LAST_NAMES = [
    "Smith", "Jones", "Brown", "Wilson", "Taylor", "Davis", "Clark",
    "Lewis", "Hall", "Young", "Allen", "Wright", "Scott", "King", "Green",
    "Adams", "Baker", "Hill", "Nelson", "Carter",
]

# ── Generators ────────────────────────────────────────────────────────────────

def generate_users(n: int = 300) -> list[dict]:
    users = []
    seen_emails: set[str] = set()
    for i in range(1, n + 1):
        first = random.choice(FIRST_NAMES)
        last  = random.choice(LAST_NAMES)
        base  = f"{first.lower()}.{last.lower()}"
        email = f"{base}{i}@example.com"
        while email in seen_emails:
            email = f"{base}{i}_{random.randint(10,99)}@example.com"
        seen_emails.add(email)

        reg_days_ago = random.randint(30, 500)
        reg_date = (END_DATE - timedelta(days=reg_days_ago)).strftime("%Y-%m-%d")

        # Travel profile drives booking behaviour
        profile = random.choices(
            ["commuter", "frequent", "occasional", "rare"],
            weights=[0.25, 0.30, 0.30, 0.15],
        )[0]

        users.append(
            {
                "user_id": i,
                "email": email,
                "full_name": f"{first} {last}",
                "home_stop": random.choice(STOPS),
                "travel_profile": profile,
                "registration_date": reg_date,
            }
        )
    return users


def _booking_count_for_profile(profile: str) -> int:
    """Return total number of bookings for a user profile over the date range."""
    return {
        "commuter":   random.randint(25, 60),
        "frequent":   random.randint(10, 24),
        "occasional": random.randint(3, 9),
        "rare":        random.randint(1, 2),
    }[profile]


def _preferred_routes(home_stop: str, profile: str) -> list[str]:
    """Pick 1-3 routes that match the user's home stop as preferred."""
    home_departing = [r[0] for r in ROUTES if r[1] == home_stop]
    all_codes      = [r[0] for r in ROUTES]

    if profile in ("commuter", "frequent") and home_departing:
        # 70% chance of picking from home-departing routes
        primary = random.sample(home_departing, min(2, len(home_departing)))
        filler  = random.sample(all_codes, min(2, len(all_codes)))
        return list(dict.fromkeys(primary + filler))  # deduplicated, order preserved
    return random.sample(all_codes, min(3, len(all_codes)))


def generate_bookings(users: list[dict]) -> list[dict]:
    route_map = {r[0]: r for r in ROUTES}
    bookings: list[dict] = []
    bk_id = 1

    for user in users:
        count     = _booking_count_for_profile(user["travel_profile"])
        preferred = _preferred_routes(user["home_stop"], user["travel_profile"])
        all_codes = [r[0] for r in ROUTES]

        for _ in range(count):
            # Commuters/frequent travellers pick from preferred routes more often
            if user["travel_profile"] in ("commuter", "frequent"):
                route_code = random.choices(
                    preferred + all_codes,
                    weights=[3] * len(preferred) + [1] * len(all_codes),
                )[0]
            else:
                route_code = random.choice(all_codes)

            route      = route_map[route_code]
            passengers = random.choices([1, 2, 3, 4], weights=[0.60, 0.25, 0.10, 0.05])[0]
            amount     = round(route[5] * passengers, 2)
            state      = random.choice(BOOKING_STATES)

            offset_days = random.randint(0, DATE_RANGE_DAYS)
            bk_date     = (START_DATE + timedelta(days=offset_days)).strftime("%Y-%m-%d")

            ref = f"BK-{bk_id:06d}"
            bookings.append(
                {
                    "booking_id":        bk_id,
                    "booking_reference": ref,
                    "user_id":           user["user_id"],
                    "customer_email":    user["email"],
                    "route_code":        route_code,
                    "from_stop":         route[1],
                    "to_stop":           route[2],
                    "trip_date":         bk_date,
                    "trip_type":         "one-way",
                    "passengers":        passengers,
                    "total_amount":      amount,
                    "current_state":     state,
                    "created_at":        bk_date,
                }
            )
            bk_id += 1

    random.shuffle(bookings)
    for i, bk in enumerate(bookings, start=1):
        bk["booking_id"] = i
        bk["booking_reference"] = f"BK-{i:06d}"
    return bookings


def write_csv(path: Path, rows: list[dict], fieldnames: list[str]) -> None:
    with open(path, "w", newline="") as fh:
        writer = csv.DictWriter(fh, fieldnames=fieldnames)
        writer.writeheader()
        writer.writerows(rows)
    print(f"  wrote {len(rows):,} rows → {path}")


# ── Main ──────────────────────────────────────────────────────────────────────

def main() -> None:
    print("Generating SmartBus synthetic dataset …")

    users = generate_users(300)
    write_csv(
        RAW_DIR / "users.csv",
        users,
        ["user_id", "email", "full_name", "home_stop", "travel_profile", "registration_date"],
    )

    route_rows = [
        {
            "route_code":      r[0],
            "from_stop":       r[1],
            "to_stop":         r[2],
            "departure_time":  r[3],
            "arrival_time":    r[4],
            "unit_price":      r[5],
            "seats_total":     r[6],
            "seats_available": r[6],
        }
        for r in ROUTES
    ]
    write_csv(
        RAW_DIR / "routes.csv",
        route_rows,
        ["route_code", "from_stop", "to_stop", "departure_time", "arrival_time",
         "unit_price", "seats_total", "seats_available"],
    )

    bookings = generate_bookings(users)
    write_csv(
        RAW_DIR / "bookings.csv",
        bookings,
        ["booking_id", "booking_reference", "user_id", "customer_email",
         "route_code", "from_stop", "to_stop", "trip_date", "trip_type",
         "passengers", "total_amount", "current_state", "created_at"],
    )

    completed = sum(1 for b in bookings if b["current_state"] == "COMPLETED")
    print(f"\nSummary:")
    print(f"  Users:     {len(users)}")
    print(f"  Routes:    {len(ROUTES)}")
    print(f"  Bookings:  {len(bookings):,}  ({completed:,} COMPLETED, "
          f"{len(bookings)-completed:,} CANCELLED)")


if __name__ == "__main__":
    main()
