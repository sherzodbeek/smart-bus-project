# SmartBus API Design

## Resource Naming Conventions

- All paths use `kebab-case` plural nouns: `/orchestrated-bookings`, `/admin/routes`, `/deliveries`
- API version is a path segment: `/api/v1/`
- Sub-resources use nested paths: `/{id}/state`, `/{routeCode}/fare`
- Admin operations are scoped under `/admin/` to separate operational from user-facing reads
- Identifiers are always path variables for single-resource operations, query parameters for filtering collections

| Service              | Base Path                       | Main Resource         |
|----------------------|---------------------------------|-----------------------|
| booking-service      | `/api/v1/bookings`              | bookings              |
| schedule-service     | `/api/v1/schedules`             | routes, locations     |
| payment-service      | `/api/v1/payments`              | payment records       |
| notification-service | `/api/v1/notifications`         | deliveries            |

## HTTP Method Usage

| Method   | Semantics                          | Success Code |
|----------|------------------------------------|--------------|
| `GET`    | Read one or many resources         | `200 OK`     |
| `POST`   | Create a new resource              | `201 Created` + `Location` header |
| `PUT`    | Replace a resource in full         | `200 OK`     |
| `PATCH`  | Partial update of a resource       | `200 OK`     |
| `DELETE` | Remove a resource                  | `204 No Content` |

## Status Code Policy

| Code | Meaning                             | When Used |
|------|-------------------------------------|-----------|
| `200` | OK                                 | Successful GET, PUT, PATCH |
| `201` | Created                            | Successful POST — always includes `Location` header |
| `204` | No Content                         | Successful DELETE |
| `400` | Bad Request                        | Bean Validation failure (`@NotBlank`, `@Valid`, etc.) |
| `404` | Not Found                          | Resource not found by ID or reference |
| `409` | Conflict                           | Duplicate key, name collision, or resource in use |
| `422` | Unprocessable Entity               | Business rule rejection (e.g., booking workflow failure) |
| `500` | Internal Server Error              | Unexpected exception — always logs the error |
| `503` | Service Unavailable                | Downstream service unreachable after retries |
| `504` | Gateway Timeout                    | Downstream service exceeded timeout |

## Error Schema

All services return the same `ApiErrorResponse` JSON structure for every non-2xx response:

```json
{
  "status": 404,
  "code": "BOOKING_NOT_FOUND",
  "message": "Booking not found: BK-1234ABCD",
  "details": [],
  "path": "/api/v1/bookings/BK-1234ABCD"
}
```

| Field     | Type            | Description |
|-----------|-----------------|-------------|
| `status`  | integer         | HTTP status code |
| `code`    | string          | Machine-readable error code (SCREAMING_SNAKE_CASE) |
| `message` | string          | Human-readable message |
| `details` | array of string | Field-level errors for validation failures; empty otherwise |
| `path`    | string          | Request URI that produced the error |

## CRUD Summary per Service

### Booking Service (`/api/v1/bookings`)

| Method   | Path                                   | Description                        | Status  |
|----------|----------------------------------------|------------------------------------|---------|
| `POST`   | `/orchestrated-bookings`               | Create booking via BPMN flow       | `201`   |
| `GET`    | `/{bookingReference}`                  | Get full booking details           | `200`   |
| `GET`    | `/{bookingReference}/state`            | Get workflow state                 | `200`   |
| `DELETE` | `/{bookingReference}`                  | Cancel booking                     | `204`   |
| `GET`    | `?customerEmail=`                      | List bookings for customer         | `200`   |
| `GET`    | `/admin/bookings?page=0&size=20`       | Paginated admin list               | `200`   |

### Schedule Service (`/api/v1/schedules`)

| Method   | Path                             | Description                 | Status |
|----------|----------------------------------|-----------------------------|--------|
| `GET`    | `/catalog`                       | Full route catalog          | `200`  |
| `POST`   | `/quote`                         | Trip availability quote     | `200`  |
| `POST`   | `/admin/routes`                  | Create route                | `201`  |
| `GET`    | `/admin/routes/{routeCode}`      | Get single route            | `200`  |
| `PUT`    | `/admin/routes/{routeCode}`      | Replace route               | `200`  |
| `DELETE` | `/admin/routes/{routeCode}`      | Delete route                | `204`  |
| `POST`   | `/admin/routes/{routeCode}/fare` | Update fare + evict cache   | `200`  |
| `POST`   | `/admin/locations`               | Create location             | `201`  |
| `GET`    | `/admin/locations`               | List all locations          | `200`  |
| `GET`    | `/admin/locations/{id}`          | Get single location         | `200`  |
| `PUT`    | `/admin/locations/{id}`          | Rename location             | `200`  |
| `DELETE` | `/admin/locations/{id}`          | Delete location             | `204`  |

### Payment Service (`/api/v1/payments`)

| Method    | Path                              | Description                 | Status |
|-----------|-----------------------------------|-----------------------------|--------|
| `POST`    | `/authorize`                      | Authorize payment           | `201`  |
| `GET`     | `/records`                        | List records (filter opt.)  | `200`  |
| `GET`     | `/records/{transactionId}`        | Get single record           | `200`  |
| `PATCH`   | `/records/{transactionId}`        | Update status (e.g. REFUND) | `200`  |
| `DELETE`  | `/records/{transactionId}`        | Delete record               | `204`  |

### Notification Service (`/api/v1/notifications`)

| Method   | Path                    | Description                   | Status |
|----------|-------------------------|-------------------------------|--------|
| `POST`   | `/prepare`              | Prepare notification          | `200`  |
| `POST`   | `/dispatch`             | Dispatch notification         | `200`  |
| `POST`   | `/deliveries`           | Create delivery record        | `201`  |
| `GET`    | `/deliveries`           | List deliveries (filter opt.) | `200`  |
| `GET`    | `/deliveries/{id}`      | Get single delivery           | `200`  |
| `PUT`    | `/deliveries/{id}`      | Update delivery record        | `200`  |
| `DELETE` | `/deliveries/{id}`      | Delete delivery record        | `204`  |

## Paginated Endpoint

`GET /api/v1/bookings/admin/bookings?page=0&size=20`

```json
{
  "items": [ ... ],
  "page": 0,
  "size": 20,
  "totalElements": 57,
  "totalPages": 3
}
```

Pagination is DB-level via Spring Data `Pageable`. Default page size: `20`. Maximum page size: `100`.

## Example Requests and Responses

### Create Booking

**Request**
```http
POST /api/v1/bookings/orchestrated-bookings
Content-Type: application/json

{
  "customerName": "Jane Rider",
  "customerEmail": "jane@example.com",
  "fromStop": "Downtown Terminal",
  "toStop": "Airport Station",
  "tripDate": "2026-04-20",
  "tripType": "one-way",
  "passengers": 1,
  "paymentMethodToken": "tok_visa_4242"
}
```

**Response `201 Created`**
```http
Location: /api/v1/bookings/BK-1234ABCD

{
  "bookingReference": "BK-1234ABCD",
  "correlationKey": "BK-1234ABCD",
  "status": "COMPLETED",
  "routeCode": "SB-101",
  "paymentTransactionId": "PAY-89AB12CD",
  "notificationId": "NTF-4567EFGH",
  "totalAmount": 12.5,
  "workflowLog": [ ... ]
}
```

### Validation Error

**Response `400 Bad Request`**
```json
{
  "status": 400,
  "code": "VALIDATION_FAILED",
  "message": "Request validation failed.",
  "details": [
    "customerEmail: must be a well-formed email address",
    "passengers: must be between 1 and 6"
  ],
  "path": "/api/v1/bookings/orchestrated-bookings"
}
```

### Not Found

**Response `404 Not Found`**
```json
{
  "status": 404,
  "code": "BOOKING_NOT_FOUND",
  "message": "Booking not found: BK-UNKNOWN",
  "details": [],
  "path": "/api/v1/bookings/BK-UNKNOWN"
}
```

### Update Payment Status

**Request**
```http
PATCH /api/v1/payments/records/PAY-89AB12CD
Content-Type: application/json

{ "status": "REFUNDED" }
```

**Response `200 OK`**
```json
{
  "transactionId": "PAY-89AB12CD",
  "bookingReference": "BK-1234ABCD",
  "customerEmail": "jane@example.com",
  "amount": 12.5,
  "status": "REFUNDED",
  "reason": "Authorization approved",
  "createdAt": "2026-04-13T08:00:00Z"
}
```
