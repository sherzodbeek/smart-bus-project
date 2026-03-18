# SmartBus Service Boundaries

## Overview

SmartBus is being split from a static frontend into backend services with explicit responsibilities. The initial boundary design avoids overlapping ownership and keeps each service focused on one business capability.

## Gateway

- Purpose: Front door for frontend and external clients.
- Owns: Request routing, service registry exposure, simple aggregation, and cross-cutting API concerns.
- Inputs: HTTP requests from the SmartBus frontend or external tools.
- Outputs: Routed or aggregated JSON responses from downstream services.
- Does not own: Booking state, schedules, payments, or notifications.

## Booking Service

- Purpose: Own the ticket purchase lifecycle.
- Owns: Booking requests, booking records, booking status, and workflow state for the purchase process.
- Inputs:
  - trip selection
  - passenger count
  - customer identifier
  - payment intent reference
- Outputs:
  - booking identifier
  - booking status
  - reserved trip summary
- Depends on:
  - schedule service for trip validation and seat availability
  - payment service for payment authorization
  - notification service for confirmation dispatch

## Schedule Service

- Purpose: Own route, trip, and availability data.
- Owns: Routes, timetables, trip lookup, and seat availability reads.
- Inputs:
  - origin stop
  - destination stop
  - trip date
  - passenger count
- Outputs:
  - matching trips
  - seat availability
  - route and schedule metadata
- Does not own: payment state or bookings.

## Payment Service

- Purpose: Own payment processing boundaries.
- Owns: Payment authorization records, transaction identifiers, and future settlement state.
- Inputs:
  - booking reference
  - amount
  - payment method token or reference
- Outputs:
  - payment authorization result
  - transaction identifier
  - payment status
- Does not own: trip inventory or notification delivery.

## Notification Service

- Purpose: Own outbound customer communication.
- Owns: Confirmation message requests, delivery attempts, and channel metadata.
- Inputs:
  - booking or payment event
  - target recipient
  - notification payload
- Outputs:
  - notification identifier
  - delivery status
- Does not own: booking or payment state.

## Service Interaction Summary

The expected request path for ticket purchase is:

1. The frontend calls the gateway.
2. The gateway forwards booking actions to the booking service.
3. The booking service calls the schedule service to validate a trip.
4. The booking service calls the payment service to authorize payment.
5. The booking service calls the notification service to send confirmation.

This keeps the booking service as the business coordinator while preserving clear partner-service boundaries for later orchestration work.
