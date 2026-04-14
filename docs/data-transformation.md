# Data Transformation and Interoperability — SmartBus

## Overview

Three transformation scenarios are implemented across the SmartBus platform:

| # | Scenario | Where | Technology |
|---|----------|-------|------------|
| 1 | JSON → XML content negotiation | schedule-service | Jackson `jackson-dataformat-xml` |
| 2 | JSON → JSON restructuring / aggregation | gateway | `RestClient` + field mapping |
| 3 | JSON → CSV export | booking-service | Manual CSV serialization |

---

## Scenario 1 — JSON to XML (Content Negotiation)

### What changed

`schedule-service` now negotiates between `application/json` (default) and
`application/xml` on two endpoints:

- `GET /api/v1/schedules/catalog`
- `POST /api/v1/schedules/quote`

**Dependency added to `schedule-service/pom.xml`:**

```xml
<dependency>
  <groupId>com.fasterxml.jackson.dataformat</groupId>
  <artifactId>jackson-dataformat-xml</artifactId>
</dependency>
```

**XML annotations on DTOs:**

| Class | Root element | Collection wrapper |
|-------|--------------|--------------------|
| `RouteCatalogResponse` | `<routeCatalog>` | `<routes>` wrapping `<route>` items |
| `RouteDefinition` | `<route>` | — |
| `ScheduleQuoteResponse` | `<scheduleQuote>` | — |

### Example — JSON (default, `Accept: application/json`)

```
GET /api/v1/schedules/catalog
Accept: application/json
```

```json
{
  "routes": [
    {
      "routeCode": "SB-101",
      "fromStop": "Downtown Terminal",
      "toStop": "Airport Station",
      "departureTime": "08:00 AM",
      "arrivalTime": "09:15 AM",
      "unitPrice": 12.50,
      "seatsAvailable": 24
    }
  ]
}
```

### Example — XML (`Accept: application/xml`)

```
GET /api/v1/schedules/catalog
Accept: application/xml
```

```xml
<routeCatalog>
  <routes>
    <route>
      <routeCode>SB-101</routeCode>
      <fromStop>Downtown Terminal</fromStop>
      <toStop>Airport Station</toStop>
      <departureTime>08:00 AM</departureTime>
      <arrivalTime>09:15 AM</arrivalTime>
      <unitPrice>12.5</unitPrice>
      <seatsAvailable>24</seatsAvailable>
    </route>
  </routes>
</routeCatalog>
```

### Example — Quote XML (`Accept: application/xml`)

```
POST /api/v1/schedules/quote
Content-Type: application/json
Accept: application/xml

{"fromStop":"Downtown Terminal","toStop":"Airport Station","tripDate":"2026-06-01","tripType":"one-way","passengers":2}
```

```xml
<scheduleQuote>
  <tripAvailable>true</tripAvailable>
  <returnTripAvailable>true</returnTripAvailable>
  <routeCode>SB-101</routeCode>
  <departureTime>08:00 AM</departureTime>
  <arrivalTime>09:15 AM</arrivalTime>
  <unitPrice>12.5</unitPrice>
  <availableSeats>22</availableSeats>
</scheduleQuote>
```

### Why this supports interoperability

Legacy systems and enterprise integrations often require XML (EDI, SOAP wrappers, SAP connectors).
By advertising both media types via `produces = {APPLICATION_JSON_VALUE, APPLICATION_XML_VALUE}`,
any HTTP client can request its preferred format without requiring a separate endpoint or a proxy
transformation layer.

---

## Scenario 2 — JSON to JSON Restructuring (Aggregation)

### Endpoint

```
GET /api/v1/gateway/booking-summary/{bookingReference}
Authorization: Bearer <token>
```

### What it does

The gateway calls two downstream services, merges the results, and returns a
single flat document with consumer-friendly field names. No internal service
names leak through:

| Internal field (`BookingSummaryResponse`) | External field (`BookingTripSummary`) |
|-------------------------------------------|---------------------------------------|
| `bookingReference` | `reference` |
| `customerName` | `passengerName` |
| `customerEmail` | `email` |
| `fromStop` | `origin` |
| `toStop` | `destination` |
| `tripDate` | `travelDate` |
| `passengers` | `seats` |
| `currentState` | `bookingStatus` |
| *(not in booking response)* | `paymentStatus` — fetched from payment-service |

### Example — Internal booking-service response (`GET /api/v1/bookings/BK-AB12CD34`)

```json
{
  "bookingReference": "BK-AB12CD34",
  "customerName": "Alice Smith",
  "customerEmail": "alice@example.com",
  "fromStop": "Downtown Terminal",
  "toStop": "Airport Station",
  "tripDate": "2026-06-01",
  "tripType": "one-way",
  "passengers": 2,
  "routeCode": "SB-101",
  "departureTime": "08:00 AM",
  "arrivalTime": "09:15 AM",
  "totalAmount": 25.0,
  "paymentTransactionId": "PAY-7F3A2C1B",
  "notificationId": "42",
  "currentState": "CONFIRMED",
  "lastError": null
}
```

### Example — Aggregated response (`GET /api/v1/gateway/booking-summary/BK-AB12CD34`)

```json
{
  "reference": "BK-AB12CD34",
  "passengerName": "Alice Smith",
  "email": "alice@example.com",
  "origin": "Downtown Terminal",
  "destination": "Airport Station",
  "travelDate": "2026-06-01",
  "tripType": "one-way",
  "seats": 2,
  "routeCode": "SB-101",
  "departureTime": "08:00 AM",
  "arrivalTime": "09:15 AM",
  "totalAmount": 25.0,
  "paymentStatus": "AUTHORIZED",
  "transactionId": "PAY-7F3A2C1B",
  "bookingStatus": "CONFIRMED"
}
```

### Technology

- `RestClient` (Spring 6) in `DataTransformationController`
- `ParameterizedTypeReference<Map<String, Object>>` for type-safe JSON deserialization
- Manual field mapping in `DataTransformationController.bookingSummary()`
- Payment status is fetched from payment-service via `GET /api/v1/payments/records?bookingReference=...`
  with a fallback to `"UNKNOWN"` if the call fails

### Why this supports interoperability

A mobile or frontend client should not be forced to make two service calls and join the results
client-side. The aggregation endpoint presents a stable external contract that hides the internal
split between booking-service and payment-service, and uses domain-language field names
(`passengerName`, `origin`, `destination`) that map directly to UI labels.

---

## Scenario 3 — JSON to CSV Export (Admin)

### Endpoint

```
GET /api/v1/bookings/admin/bookings.csv
Accept: text/csv
```

### What it does

Transforms the internal `BookingProcessInstance` collection into a flat comma-separated document
suitable for spreadsheet import or data pipeline ingestion.

**Response headers:**

```
Content-Type: text/csv
Content-Disposition: attachment; filename="bookings.csv"
```

### Example output

```csv
bookingReference,customerName,customerEmail,fromStop,toStop,tripDate,tripType,passengers,routeCode,departureTime,arrivalTime,totalAmount,paymentTransactionId,status
BK-AB12CD34,Alice Smith,alice@example.com,Downtown Terminal,Airport Station,2026-06-01,one-way,2,SB-101,08:00 AM,09:15 AM,25.0,PAY-7F3A2C1B,CONFIRMED
BK-XY99ZZ01,Bob Jones,bob@example.com,Airport Station,Downtown Terminal,2026-06-02,round-trip,1,SB-102,10:00 AM,11:00 AM,18.5,PAY-DECLINED,CANCELLED
```

### Technology

- Standard Java `StringBuilder` CSV serialization with RFC 4180 quoting
  (fields containing commas, double-quotes, or newlines are wrapped in `"..."`)
- `BookingQueryService.findAll()` fetches all records from PostgreSQL via JPA
- `MediaType.parseMediaType("text/csv")` on the response

### Why this supports interoperability

CSV is the lingua franca of data exchange: accepted by Excel, Google Sheets,
pandas, Power BI, and virtually every ETL pipeline without any adapter. The
booking admin can download a complete snapshot for offline analysis without
querying the database directly.
