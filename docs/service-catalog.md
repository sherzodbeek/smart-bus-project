# SmartBus Service Catalog

## Contract Versioning Policy

- REST contracts are versioned by file name in `contracts/openapi/*.v1.yaml`.
- Event contracts are versioned by schema name, for example `contracts/messages/booking-confirmed.v1.json`.
- Backward-compatible additions may add optional fields or new endpoints inside `v1`.
- Breaking changes require a new major contract file such as `booking-service.v2.yaml`.

## Service Owners

- Gateway: platform integration boundary
- Booking service: booking lifecycle owner
- Schedule service: route and fare owner
- Payment service: payment authorization owner
- Notification service: customer communication owner

## Operations

| Service | Operation | Method | Path | Owner | Notes |
|---|---|---|---|---|---|
| gateway | listServices | `GET` | `/api/v1/system/services` | platform integration boundary | frontend discovery entrypoint |
| booking-service | getBookingFoundation | `GET` | `/api/v1/bookings/foundation` | booking lifecycle owner | foundation metadata |
| booking-service | createOrchestratedBooking | `POST` | `/api/v1/bookings/orchestrated-bookings` | booking lifecycle owner | creates and confirms a booking |
| booking-service | getBookingState | `GET` | `/api/v1/bookings/{bookingReference}/state` | booking lifecycle owner | reads persisted process state |
| schedule-service | getScheduleFoundation | `GET` | `/api/v1/schedules/foundation` | route and fare owner | foundation metadata |
| schedule-service | getRouteCatalog | `GET` | `/api/v1/schedules/catalog` | route and fare owner | read-heavy cached catalog |
| schedule-service | quoteTrip | `POST` | `/api/v1/schedules/quote` | route and fare owner | calculates trip quote |
| schedule-service | updateRouteFare | `POST` | `/api/v1/schedules/admin/routes/{routeCode}/fare` | route and fare owner | invalidates both cache layers |
| payment-service | getPaymentFoundation | `GET` | `/api/v1/payments/foundation` | payment authorization owner | foundation metadata |
| payment-service | authorizePayment | `POST` | `/api/v1/payments/authorize` | payment authorization owner | returns `AUTHORIZED` or `DECLINED` |
| notification-service | getNotificationFoundation | `GET` | `/api/v1/notifications/foundation` | customer communication owner | foundation metadata |
| notification-service | prepareNotification | `POST` | `/api/v1/notifications/prepare` | customer communication owner | creates preview payload |
| notification-service | dispatchNotification | `POST` | `/api/v1/notifications/dispatch` | customer communication owner | returns delivery id |
| notification-service | listNotificationDeliveries | `GET` | `/api/v1/notifications/deliveries` | customer communication owner | shows Kafka-consumed deliveries |

## Message Contracts

| Message | Location | Producer | Consumer |
|---|---|---|---|
| `booking-confirmed.v1` | `contracts/messages/booking-confirmed.v1.json` | booking-service | notification-service |
