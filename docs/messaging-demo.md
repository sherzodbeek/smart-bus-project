# SmartBus Kafka Messaging Demo

## Objective

This task adds asynchronous communication between SmartBus services using Kafka with persistent broker storage.

## Producer and Consumer

- Producer: `booking-service`
- Consumer: `notification-service`
- Topic: `smartbus.booking.confirmed.v1`

The producer publishes a `booking-confirmed.v1` JSON event after a booking orchestration succeeds. The consumer receives the event and records notification delivery.

## Persistence

Kafka persistence is provided by the Docker volume `kafka-data` in `infra/docker-compose.yml`. Messages remain available when the consumer is offline and are consumed after the consumer restarts.

## Message Contract

The versioned schema is stored in `contracts/messages/booking-confirmed.v1.json`.

## Demo Steps for Offline Recovery

1. Start infrastructure:

   `docker compose -f infra/docker-compose.yml up -d`

2. Start these services:

   - `schedule-service`
   - `payment-service`
   - `booking-service`

3. Keep `notification-service` stopped.

4. Submit a booking to:

   `POST /api/v1/bookings/orchestrated-bookings`

5. Check booking-service logs for:

   - `bookingEventPublished`
   - `workflowStep step=invoke-kafka-producer`

6. Start `notification-service`.

7. Check notification-service logs for:

   - `bookingEventConsumed`

8. Verify delivery state:

   `GET /api/v1/notifications/deliveries`

## Expected Evidence

- The booking succeeds even while the consumer is offline.
- The Kafka message remains in the topic because broker data is persisted on disk.
- After `notification-service` starts, it consumes the backlog and creates a delivery record.

## Example Log Evidence

- Producer:
  - `bookingEventPublished topic=smartbus.booking.confirmed.v1 bookingReference=BK-... status=CONFIRMED`
- Consumer:
  - `bookingEventConsumed bookingReference=BK-... customerEmail=... routeCode=... status=CONFIRMED`
