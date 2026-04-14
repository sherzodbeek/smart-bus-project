# SmartBus Security Design

## Authentication Flow

JWT-based, stateless. All auth logic lives in the `gateway` service (port 8080).
Microservices are on internal ports and are not directly exposed to clients.

```
Client
  │
  │  POST /api/v1/auth/register   { fullName, email, password }
  ▼
Gateway (AuthController → AuthService)
  • Validates request with @Valid (NotBlank, Email, Size)
  • Strips HTML from fullName via HtmlSanitizer
  • Hashes password with BCrypt (strength 10)
  • Persists user with role = USER
  • Issues signed JWT (HS256, 8-hour expiry)
  └─► 201 { token, fullName, email, role }

  │  POST /api/v1/auth/login   { email, password }
  ▼
Gateway (AuthController → AuthService)
  • Validates request with @Valid
  • Loads user by email
  • Verifies BCrypt hash
  └─► 200 { token, fullName, email, role }

  │  GET/POST (protected endpoint)
  │  Authorization: Bearer <token>
  ▼
Gateway (JwtAuthenticationFilter)
  • Extracts Bearer token
  • Parses and verifies signature via JwtService (JJWT 0.12.7)
  • Verifies expiry (embedded in token claims)
  • Populates SecurityContextHolder with email + ROLE_<role>
  └─► Passes to controller  OR  401 { message: "Invalid or expired authentication token." }
```

### Token Claims

| Claim       | Value                              |
|-------------|-------------------------------------|
| `sub`       | user email address                  |
| `role`      | `USER` or `ADMIN`                   |
| `name`      | user full name                      |
| `iat`       | issued-at timestamp                 |
| `exp`       | expiry (issued-at + PT8H by default)|

Algorithm: HS256. Secret configured via `SMARTBUS_JWT_SECRET` env var.

---

## Protected vs Public Endpoints

### Gateway (`/api/v1/*`)

| Method + Path                                | Auth required | Roles     |
|----------------------------------------------|:-------------:|-----------|
| `POST /api/v1/auth/login`                    | No            | —         |
| `POST /api/v1/auth/register`                 | No            | —         |
| `GET  /api/v1/system/services`               | No            | —         |
| `GET  /api/v1/frontend/routes`               | No            | —         |
| `POST /api/v1/frontend/quote`                | No            | —         |
| `POST /api/v1/frontend/contact`              | No            | —         |
| `GET  /actuator/health`                      | No            | —         |
| `GET  /actuator/info`                        | No            | —         |
| `GET/POST /api/v1/frontend/bookings/**`      | Yes           | USER/ADMIN|
| `GET/PUT  /api/v1/frontend/profile/**`       | Yes           | USER/ADMIN|
| `GET/POST /api/v1/frontend/admin/**`         | Yes           | ADMIN     |

### Microservices (internal only — no JWT filter)

The four microservices (booking-service, schedule-service, payment-service,
notification-service) run on internal ports (8081–8084) and are only reachable
from `gateway` or internal tooling. They are not exposed to the internet in the
Docker Compose setup. Bean Validation is enforced on all request bodies.

---

## Input Validation

All request DTOs are validated with `@Valid` on controller method parameters and
`jakarta.validation` annotations on record fields.

### Booking service — `BookingRequest`

| Field                | Constraint                                    |
|----------------------|-----------------------------------------------|
| `customerName`       | `@NotBlank`                                   |
| `customerEmail`      | `@NotBlank @Email`                            |
| `fromStop`           | `@NotBlank`                                   |
| `toStop`             | `@NotBlank`                                   |
| `tripDate`           | `@NotBlank @Pattern(^\d{4}-\d{2}-\d{2}$)`    |
| `tripType`           | `@NotBlank @Pattern(^(one-way\|round-trip)$)` |
| `passengers`         | `@Min(1) @Max(6)`                             |
| `paymentMethodToken` | `@NotBlank`                                   |

### Gateway auth — `RegisterRequest`

| Field      | Constraint                        |
|------------|-----------------------------------|
| `fullName` | `@NotBlank`                       |
| `email`    | `@NotBlank @Email`                |
| `password` | `@NotBlank @Size(min=8, max=100)` |

### Schedule service — `FareUpdateRequest`

| Field       | Constraint            |
|-------------|-----------------------|
| `unitPrice` | `@DecimalMin("0.01")` |

### Payment service — `PaymentAuthorizationRequest`

| Field                | Constraint            |
|----------------------|-----------------------|
| `bookingReference`   | `@NotBlank`           |
| `customerEmail`      | `@NotBlank @Email`    |
| `amount`             | `@DecimalMin("0.01")` |
| `paymentMethodToken` | `@NotBlank`           |

### Validation error response format

All five services return `400 Bad Request` with a field-level error body when
`MethodArgumentNotValidException` is thrown:

```json
{
  "status": 400,
  "errorCode": "VALIDATION_FAILED",
  "message": "Request validation failed.",
  "details": ["customerName: must not be blank", "passengers: must be greater than or equal to 1"],
  "path": "/api/v1/bookings/orchestrated-bookings"
}
```

---

## XSS Prevention

### Approach

Free-text fields (user-supplied strings containing arbitrary characters that are
persisted and later returned in responses) are sanitized by stripping all
`<...>` HTML tag sequences before storage.

Implementation: `HtmlSanitizer.strip(input)` in three packages:
- `com.smartbus.gateway.util.HtmlSanitizer`
- `com.smartbus.schedule.util.HtmlSanitizer`
- `com.smartbus.booking.util.HtmlSanitizer`

Pattern used: `<[^>]*>` replaced with `""`. Input is also trimmed.

### Sanitization points

| Service         | Storage boundary                                | Sanitized fields                     |
|-----------------|--------------------------------------------------|--------------------------------------|
| gateway         | `AuthService.register()`                        | `fullName`                           |
| gateway         | `FrontendGatewayRepository.saveContactMessage()`| `name`, `subject`, `message`         |
| gateway         | `FrontendGatewayRepository.updateProfile()`     | `fullName`, `address`                |
| gateway         | `FrontendGatewayRepository.createUser()`        | `fullName`                           |
| gateway         | `FrontendGatewayRepository.updateUser()`        | `fullName`                           |
| schedule-service| `ScheduleCatalogService.createLocation()`       | `name`                               |
| schedule-service| `ScheduleCatalogService.updateLocation()`       | `name`                               |
| booking-service | `BookingOrchestrationService.orchestrateBooking()`| `customerName`                     |

### Fields not sanitized (structured/constrained)

Email addresses, dates, route codes, payment tokens, and enum-like fields are
already validated by `@Email`, `@Pattern`, `@Min`/`@Max` — they structurally
cannot contain HTML and are not sanitized.

---

## SQL Injection Prevention

All database access uses Spring Data JPA (`JpaRepository` / `@Query` with named
parameters) or Flyway DDL migrations. No string concatenation is used in any
query path across all five services:

| Service          | DB access pattern                                   |
|------------------|-----------------------------------------------------|
| gateway          | Spring Data JPA derived queries + `@Modifying @Query` with `:param` |
| booking-service  | Spring Data JPA derived queries + Flowable ORM      |
| schedule-service | Spring Data JPA `@Query` named parameters           |
| payment-service  | Spring Data JPA derived queries                     |
| notification-service | Spring Data JPA derived queries                 |

SQL injection is structurally impossible because the ORM always uses prepared
statements / parameterized queries under the hood.

---

## Password Security

- Passwords are hashed with **BCrypt** (Spring Security `BCryptPasswordEncoder`,
  default strength 10) before storage.
- The `password_hash` column is never returned in any API response.
- Plain-text passwords are never logged (`authLoginStart` / `authRegisterStart`
  log only the email address).
- Password change (`PUT /api/v1/frontend/profile/password`) verifies the
  current password hash before accepting a new one.

---

## Stack Trace Suppression

Every service has an `@RestControllerAdvice` with an `@ExceptionHandler(Exception.class)`
catch-all that returns `500 INTERNAL_SERVER_ERROR` with a fixed message
(`"An unexpected error occurred."`) and no stack trace. The full exception is
logged at ERROR level server-side for diagnostics.
