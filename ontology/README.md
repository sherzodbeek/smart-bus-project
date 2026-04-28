# SmartBus Transport Ontology

**Namespace:** `http://smartbus.example.org/ontology#` (prefix `sb:`)  
**Format:** OWL 2 / Turtle (.ttl)  
**Version:** 1.0.0  
**Triples:** 385  

---

## Overview

The SmartBus ontology formally represents the domain knowledge of a smart bus ticket booking
system. It enables:

- **Semantic queries** — find routes by zone, stop, or price tier using SPARQL
- **Inference** — derive user preferences and route suitability from booking history
- **AI integration** — store ML recommendation results as RDF triples for explainability

---

## Classes (10)

| Class | Description | Key Data Properties |
|-------|-------------|---------------------|
| `sb:User` | Registered passenger | email, fullName, travelProfile |
| `sb:Route` | Named bus route | routeCode, unitPrice |
| `sb:Stop` | Bus terminal / stop | stopName |
| `sb:Bus` | Physical vehicle | busPlate, seatCapacity |
| `sb:Booking` | Ticket reservation | bookingReference, tripDate, passengers, totalAmount, bookingStatus |
| `sb:Payment` | Payment transaction | transactionId, paymentStatus, paymentAmount |
| `sb:Schedule` | Time-bound route run | departureTime, arrivalTime, seatsAvailable |
| `sb:Recommendation` | AI-generated suggestion | confidenceScore, confidenceLevel, reasonCode |
| `sb:PriceTier` | Fare category | tierLabel (LOW/MEDIUM/HIGH) |
| `sb:RouteZone` | Geographic zone | zoneName |

---

## Object Properties (17)

| Property | Domain | Range | Cardinality | Notes |
|----------|--------|-------|------------|-------|
| `sb:hasBooking` | User | Booking | one-to-many | inverse: `bookedBy` |
| `sb:bookedBy` | Booking | User | many-to-one | inverse of `hasBooking` |
| `sb:prefersOrigin` | User | Stop | many-to-one | inferred from booking history |
| `sb:frequentlyTravels` | User | Route | many-to-many | inferred: ≥3 bookings on route |
| `sb:interactedWith` | User | Route | many-to-many | derived via property chain: `hasBooking ∘ onRoute` |
| `sb:onRoute` | Booking | Route | many-to-one | links booking to its route |
| `sb:paidWith` | Booking | Payment | one-to-one | functional property |
| `sb:departsFrom` | Route | Stop | many-to-one | functional property; origin stop |
| `sb:arrivesAt` | Route | Stop | many-to-one | functional property; destination stop |
| `sb:servedBy` | Stop | Route | one-to-many | inverse of departsFrom/arrivesAt |
| `sb:inZone` | Stop | RouteZone | many-to-one | functional property |
| `sb:inSameZoneAs` | Stop | Stop | symmetric | inferred: shared zone |
| `sb:operatedBy` | Route | Bus | many-to-one | vehicle assignment |
| `sb:hasSchedule` | Route | Schedule | one-to-many | time schedule for route |
| `sb:hasPriceTier` | Route | PriceTier | many-to-one | functional; LOW/MEDIUM/HIGH |
| `sb:forUser` | Recommendation | User | many-to-one | functional; recommendation target |
| `sb:suggestsRoute` | Recommendation | Route | many-to-one | functional; recommended route |

---

## Inference Rules

### R1 — Frequent Route Preference

```
IF   User sb:hasBooking Booking1
     Booking1 sb:onRoute Route
     Booking1 sb:bookingStatus "COMPLETED"
     ... (count ≥ 3 bookings for same User–Route pair)
THEN User sb:frequentlyTravels Route
```

### R2 — Origin Stop Preference

```
IF   User most commonly departs from Stop X
     (plurality of User's completed bookings have from_stop = X)
THEN User sb:prefersOrigin Stop_X
```

### R3 — Zone Proximity

```
IF   Stop_A sb:inZone Zone_Z
     Stop_B sb:inZone Zone_Z
     Stop_A ≠ Stop_B
THEN Stop_A sb:inSameZoneAs Stop_B
```

### R4 — Route Candidate for User

```
IF   User sb:prefersOrigin Stop_X
     Route sb:departsFrom Stop_X
THEN Route is a candidate recommendation for User
```

### R5 — High-Confidence Recommendation

```
IF   User sb:frequentlyTravels Route
     Route sb:hasPriceTier Tier_T
     User's preferred tier = Tier_T
THEN Recommendation sb:confidenceLevel "HIGH"
```

### Property Chain (OWL 2 axiom)

```
sb:interactedWith ≡ sb:hasBooking ∘ sb:onRoute
```

A User `sb:interactedWith` a Route if there is any Booking linking them.

---

## Named Individuals

### Stops (6)

| Individual | stopName | Zone |
|------------|----------|------|
| `sb:Stop_DowntownTerminal` | Downtown Terminal | Central Zone |
| `sb:Stop_AirportStation` | Airport Station | Northern Zone |
| `sb:Stop_University` | University | Northern Zone |
| `sb:Stop_BusDepot` | Bus Depot | Southern Zone |
| `sb:Stop_CityCenter` | City Center | Central Zone |
| `sb:Stop_Harbor` | Harbor | Southern Zone |

### Route Zones (3)

| Individual | zoneName |
|------------|---------|
| `sb:Zone_Central` | Central Zone |
| `sb:Zone_North` | Northern Zone |
| `sb:Zone_South` | Southern Zone |

### Price Tiers (3)

| Individual | tierLabel | Price Range |
|------------|-----------|-------------|
| `sb:Tier_LOW` | LOW | < $10 |
| `sb:Tier_MEDIUM` | MEDIUM | $10–$15 |
| `sb:Tier_HIGH` | HIGH | > $15 |

### Routes (10)

All 10 routes are represented with `departsFrom`, `arrivesAt`, `hasPriceTier`, and `hasSchedule` triples.

---

## Example Triples (subject–predicate–object)

```turtle
# A user with a preference for Downtown Terminal
sb:User_Alice  sb:prefersOrigin  sb:Stop_DowntownTerminal .

# A completed booking linking user to route
sb:User_Alice      sb:hasBooking    sb:Booking_BK001 .
sb:Booking_BK001   sb:onRoute       sb:Route_SB101 .
sb:Booking_BK001   sb:bookingStatus "COMPLETED" .
sb:Booking_BK001   sb:paidWith      sb:Payment_TX001 .

# Route structure
sb:Route_SB101  sb:departsFrom   sb:Stop_DowntownTerminal .
sb:Route_SB101  sb:arrivesAt     sb:Stop_AirportStation .
sb:Route_SB101  sb:hasPriceTier  sb:Tier_MEDIUM .
sb:Route_SB101  sb:hasSchedule   sb:Sched_SB101_0800 .
sb:Sched_SB101_0800  sb:departureTime  "08:00:00"^^xsd:time .

# AI recommendation result stored as triples
sb:Rec_Alice_SB101  a                sb:Recommendation .
sb:Rec_Alice_SB101  sb:forUser       sb:User_Alice .
sb:Rec_Alice_SB101  sb:suggestsRoute sb:Route_SB101 .
sb:Rec_Alice_SB101  sb:confidenceScore  "0.73"^^xsd:decimal .
sb:Rec_Alice_SB101  sb:confidenceLevel  "HIGH" .
sb:Rec_Alice_SB101  sb:reasonCode       "content_match" .

# Zone proximity (inferred by Rule R3)
sb:Stop_DowntownTerminal  sb:inSameZoneAs  sb:Stop_CityCenter .
```

---

## SPARQL Query Examples

### Q1 — Find all routes departing from a given stop

```sparql
PREFIX sb: <http://smartbus.example.org/ontology#>
SELECT ?route ?routeCode ?destination WHERE {
  ?route sb:departsFrom sb:Stop_DowntownTerminal ;
         sb:routeCode   ?routeCode ;
         sb:arrivesAt   ?dest .
  ?dest  sb:stopName    ?destination .
}
```

### Q2 — Find routes in the same zone as a user's preferred stop

```sparql
PREFIX sb: <http://smartbus.example.org/ontology#>
SELECT DISTINCT ?route ?routeCode WHERE {
  ?user  sb:email         "alice@example.com" ;
         sb:prefersOrigin ?prefStop .
  ?prefStop sb:inZone     ?zone .
  ?candidate sb:inZone    ?zone .
  ?route sb:departsFrom   ?candidate ;
         sb:routeCode     ?routeCode .
  FILTER (?candidate != ?prefStop)
}
```

### Q3 — List AI recommendations for a user

```sparql
PREFIX sb: <http://smartbus.example.org/ontology#>
SELECT ?routeCode ?score ?confidence ?reason WHERE {
  ?rec  sb:forUser      ?user ;
        sb:suggestsRoute ?route ;
        sb:confidenceScore ?score ;
        sb:confidenceLevel ?confidence ;
        sb:reasonCode      ?reason .
  ?user sb:email "alice@example.com" .
  ?route sb:routeCode ?routeCode .
}
ORDER BY DESC(?score)
```

---

## How to Parse and Query

```python
import rdflib

g = rdflib.Graph()
g.parse("ontology/smartbus-ontology.ttl", format="turtle")

# Count all routes
result = g.query("""
    PREFIX sb: <http://smartbus.example.org/ontology#>
    SELECT (COUNT(?r) AS ?n) WHERE { ?r a sb:Route . }
""")
for row in result:
    print("Routes:", row.n)  # → 10
```
