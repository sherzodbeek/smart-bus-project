# SmartBus Phase IV — Ontology Design Document

Date: 2026-04-26  
Format: OWL 2 / Turtle  
File: `ontology/smartbus-ontology.ttl`

---

## 1. Domain Analysis

### 1.1 Why an Ontology for SmartBus?

SmartBus already stores domain entities in a relational database (PostgreSQL with
Flyway-managed schemas). The ontology serves a different purpose:

| Concern | Relational DB | Ontology |
|---------|--------------|---------|
| Storage | Rows and columns | RDF triples (subject–predicate–object) |
| Querying | SQL with JOINs | SPARQL with graph patterns |
| Relationships | Foreign keys (implicit) | Named object properties (explicit, typed) |
| Inference | Stored procedures / application code | OWL reasoner + SWRL rules |
| AI integration | N/A | ML predictions stored as semantic facts |

The ontology enables **semantic reasoning**: the system can infer new facts from existing
ones (e.g., derive zone proximity, user route preferences, recommendation confidence) without
changing the relational schema.

### 1.2 Entity Identification

Domain analysis of SmartBus Phase III identified the following real-world concepts:

**Core booking domain:**
- Passengers who register and book tickets
- Routes that connect fixed stops
- Bookings that link passengers to routes
- Payments that authorise bookings

**Transport infrastructure:**
- Stops (terminals) where buses depart/arrive
- Buses (vehicles) that operate routes
- Schedules that define departure/arrival times

**Knowledge representation:**
- Geographic zones grouping nearby stops
- Price tiers categorising fare levels
- AI-generated recommendations (new in Phase IV)

These map directly to the 10 ontology classes.

---

## 2. Ontology Class Hierarchy

```
owl:Thing
├── sb:User              (registered passenger)
├── sb:Route             (named bus route)
├── sb:Stop              (bus terminal / stop)
├── sb:Bus               (physical vehicle)
├── sb:Booking           (ticket reservation)
├── sb:Payment           (payment transaction)
├── sb:Schedule          (time-bound route run)
├── sb:Recommendation    (AI-generated suggestion)
├── sb:PriceTier         (fare category: LOW/MEDIUM/HIGH)
└── sb:RouteZone         (geographic zone)
```

All classes are direct subclasses of `owl:Thing` (flat hierarchy). This reflects the
peer relationship between domain entities — there is no meaningful taxonomic hierarchy
in the SmartBus domain (e.g., a Stop is not a specialisation of a Location class, as
SmartBus does not model geographic coordinates at this stage).

---

## 3. Design Decisions and Alternatives

### 3.1 Format Choice: OWL 2 Turtle over JSON-LD

| Option | Pros | Cons | Decision |
|--------|------|------|----------|
| OWL 2 Turtle | Native OWL axioms; Protégé-compatible; SPARQL-ready | Less web-friendly | **Chosen** |
| JSON-LD | Easy to embed in REST responses | Limited OWL expressiveness | Used for API responses only |
| RDF/XML | W3C standard serialisation | Verbose; hard to read | Not used |

OWL 2 Turtle was chosen because it natively expresses property chain axioms, functional
properties, and symmetric properties, which are needed for the inference rules.

### 3.2 Flat vs. Deep Class Hierarchy

**Alternative considered:** Create an abstract `sb:TransportEntity` superclass with
`sb:Route`, `sb:Stop`, and `sb:Bus` as subclasses.

**Decision:** Flat hierarchy was preferred because:
- Phase IV is focused on inference over relationships, not taxonomic reasoning
- A deep hierarchy would add complexity without improving SPARQL query results
- The SmartBus domain entities are conceptually peer-level

### 3.3 PriceTier as a Class vs. Literal

**Alternative:** Store price tier as a literal string on `sb:Route`:
```turtle
sb:Route_SB101  sb:priceTierLabel "MEDIUM" .
```

**Decision:** Modelled as a class with named individuals (`sb:Tier_LOW`, `sb:Tier_MEDIUM`,
`sb:Tier_HIGH`) because:
- Enables semantic queries: `?route sb:hasPriceTier sb:Tier_LOW`
- Supports OWL class membership (e.g., restrict inference to LOW-tier routes)
- Consistent with the OWL named individuals approach used for Stops and Zones

### 3.4 Recommendation as a First-Class Entity

**Alternative:** Store recommendations only in the PostgreSQL `gateway_recommendations`
table (which Task 03 created).

**Decision:** Modelled as `sb:Recommendation` instances in the ontology because:
- Core Phase IV requirement: AI outputs must connect with semantic data
- Enables SPARQL queries over recommendations alongside route and user data
- Allows `sb:confidenceScore` and `sb:reasonCode` to be stored as typed literals

---

## 4. Semantic Relationship Mapping

### 4.1 Core Booking Relationships

```
sb:User ──hasBooking──► sb:Booking ──onRoute──► sb:Route
                              │
                         paidWith
                              │
                              ▼
                         sb:Payment
```

The property chain `sb:interactedWith ≡ sb:hasBooking ∘ sb:onRoute` allows a reasoner
to directly assert `User sb:interactedWith Route` without traversing the intermediate
Booking node, simplifying recommendation queries.

### 4.2 Route Structure Relationships

```
             sb:Stop (origin)
                  ▲
          departsFrom (functional)
                  │
sb:Bus ◄──operatedBy── sb:Route ──arrivesAt──► sb:Stop (destination)
                  │          (functional)
             hasSchedule
                  │
                  ▼
            sb:Schedule
                  │
           hasPriceTier (functional)
                  │
                  ▼
            sb:PriceTier
```

`departsFrom`, `arrivesAt`, `paidWith`, `inZone`, `hasPriceTier`, and `forUser` are
declared as `owl:FunctionalProperty`, enforcing one-value constraints at the semantic
level.

### 4.3 Geographic Relationships

```
sb:Stop ──inZone──► sb:RouteZone

sb:Stop_DowntownTerminal sb:inZone sb:Zone_Central
sb:Stop_CityCenter       sb:inZone sb:Zone_Central
→ Inferred: sb:Stop_DowntownTerminal sb:inSameZoneAs sb:Stop_CityCenter
```

`sb:inSameZoneAs` is declared `owl:SymmetricProperty`, so the inference is bidirectional.

### 4.4 AI Layer Relationships

```
sb:Recommendation ──forUser──► sb:User
sb:Recommendation ──suggestsRoute──► sb:Route
```

When the ML model produces a recommendation, the Java `RecommendationService` stores it
in PostgreSQL *and* the Python semantic layer creates the corresponding RDF triple pair.
This creates a knowledge graph that can be queried with SPARQL to retrieve, explain, and
audit every recommendation ever made.

---

## 5. Inference Rules Specification

### R1 — Frequent Route Preference (Application Layer)

**Trigger:** Booking count for (user, route) pair reaches threshold.

**Logic:**
```
∀ u ∈ User, r ∈ Route :
  |{ b ∈ Booking : u hasBooking b ∧ b onRoute r ∧ b.status = COMPLETED }| ≥ 3
  ⟹ u frequentlyTravels r
```

**Implementation:** Python preprocessing pipeline counts interactions; the triple is
asserted during ontology population if `booking_count ≥ 3`.

### R2 — Origin Stop Preference (Application Layer)

**Logic:**
```
∀ u ∈ User :
  s = argmax_{stop} |{ b ∈ Booking : u hasBooking b ∧ b.from_stop = stop }|
  ⟹ u prefersOrigin s
```

**Implementation:** Computed in `preprocessing.py` (`frequent_origin` column); asserted
as `sb:prefersOrigin` during knowledge graph population.

### R3 — Zone Proximity (SPARQL CONSTRUCT)

**Logic:**
```
∀ s1, s2 ∈ Stop, z ∈ RouteZone :
  s1 inZone z ∧ s2 inZone z ∧ s1 ≠ s2
  ⟹ s1 inSameZoneAs s2
```

**SPARQL CONSTRUCT:**
```sparql
CONSTRUCT { ?s1 sb:inSameZoneAs ?s2 }
WHERE {
  ?s1 sb:inZone ?zone .
  ?s2 sb:inZone ?zone .
  FILTER (?s1 != ?s2)
}
```

### R4 — Route Candidate (SPARQL SELECT)

**Logic:**
```
∀ u ∈ User, r ∈ Route :
  u prefersOrigin s ∧ r departsFrom s
  ⟹ r is a candidate recommendation for u
```

**Application:** Used in Task 06 (semantic querying) to pre-filter candidate routes
before ML scoring, improving recommendation relevance.

### R5 — High Confidence (Application Layer)

**Logic:**
```
∀ u ∈ User, rec ∈ Recommendation :
  rec forUser u ∧ rec suggestsRoute r
  ∧ u frequentlyTravels r
  ⟹ rec confidenceLevel "HIGH"
```

**Implementation:** The `DecisionEngine.java` class (Task 03) applies this rule when
labelling recommendations.

---

## 6. Ontology Quality Metrics

| Metric | Value |
|--------|-------|
| Total triples | 385 |
| Classes | 10 |
| Object properties | 17 |
| Data properties | 24 |
| Named individuals | 32 (6 stops, 3 zones, 3 tiers, 10 routes, 10 schedules) |
| Functional properties | 8 |
| Symmetric properties | 1 (`inSameZoneAs`) |
| Property chain axioms | 1 (`interactedWith`) |
| Inference rules | 5 |
| SPARQL query examples | 3 |

---

## 7. How the Ontology Supports Intelligent Decision-Making

| Decision | Ontology Contribution |
|----------|-----------------------|
| Route recommendations | R4 filters candidates by origin preference; R5 boosts confidence |
| Zone-based search | R3 enables "routes near my stop" without explicit stop enumeration |
| Explanation generation | `sb:reasonCode`, `sb:confidenceLevel` stored per recommendation |
| ML feature enrichment | `sb:prefersOrigin`, `sb:frequentlyTravels` as ontology features |
| Audit trail | Every recommendation is a named individual linked to User and Route |
| Recommendation recall | SPARQL Q3 retrieves all past recommendations for any user |

---

## 8. Validation

The ontology was validated using RDFLib 7.0:

```
Parsed successfully: 385 triples
  Classes:            10
  Object properties:  17
  Data properties:    24
  Named individuals:  32
```

All classes have `rdfs:label` and `rdfs:comment` annotations.
All object properties have `rdfs:domain` and `rdfs:range` declared.
All data properties have `rdfs:domain` and `rdfs:range` declared.
Functional properties are marked with `a owl:FunctionalProperty`.
The symmetric property `inSameZoneAs` is marked with `a owl:SymmetricProperty`.
The property chain `interactedWith` uses `owl:propertyChainAxiom`.
