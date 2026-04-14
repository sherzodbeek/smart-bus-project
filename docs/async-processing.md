# Async Processing — SmartBus

## Overview

SmartBus uses Apache Kafka for asynchronous, durable event delivery between microservices.
All topics use `KAFKA_AUTO_CREATE_TOPICS_ENABLE: "true"` with 3 partitions and a single
in-process KRaft broker. Kafka data is persisted to a named Docker volume (`kafka-data`),
so messages survive container restarts.

---

## Scenario 1 — Booking Confirmed

### Flow

```
booking-service  ──publish──►  smartbus.booking.confirmed.v1  ──consume──►  notification-service
```

| Role     | Service              | Class                            |
|----------|----------------------|----------------------------------|
| Producer | booking-service      | `BookingConfirmedEventProducer`  |
| Consumer | notification-service | `BookingNotificationConsumer`    |

### Topic

`smartbus.booking.confirmed.v1`

### Contract

`contracts/messages/booking-confirmed.v1.json`

### Trigger

`booking-service` publishes a `BookingConfirmedEvent` after the Flowable BPMN orchestration
process successfully obtains a schedule quote, authorizes payment, and sends a notification.
The event carries `schemaVersion = "booking-confirmed.v1"`.

### Consumer behaviour

`BookingNotificationConsumer` listens on consumer group `notification-service-booking-events`.
On receipt it calls `NotificationDeliveryService.save()` to persist a delivery record and
emits an info log:

```
bookingEventConsumed bookingReference=BK-... customerEmail=... routeCode=... status=CONFIRMED
```

### Log evidence

```
INFO  c.s.b.messaging.BookingConfirmedEventProducer - bookingConfirmedPublished bookingReference=BK-ABC123 topic=smartbus.booking.confirmed.v1
INFO  c.s.n.messaging.BookingNotificationConsumer  - bookingEventConsumed bookingReference=BK-ABC123 customerEmail=alice@example.com routeCode=RT-001 status=CONFIRMED
```

---

## Scenario 2 — Payment Declined

### Flow

```
payment-service  ──publish──►  smartbus.payment.declined.v1  ──consume──►  booking-service
```

| Role     | Service         | Class                          |
|----------|-----------------|--------------------------------|
| Producer | payment-service | `PaymentDeclinedEventProducer` |
| Consumer | booking-service | `PaymentDeclinedEventConsumer` |

### Topic

`smartbus.payment.declined.v1`

### Contract

`contracts/messages/payment-declined.v1.json`

### Trigger

`PaymentService.authorize()` rejects a request when `amount > 150.00` or
`paymentMethodToken` is blank. On rejection it publishes a `PaymentDeclinedEvent` with
`schemaVersion = "payment-declined.v1"`.

### Consumer behaviour

`PaymentDeclinedEventConsumer` listens on consumer group
`booking-service-payment-audit` with `auto-offset-reset = earliest`.  
On receipt it emits a warn-level audit log:

```
WARN  c.s.b.messaging.PaymentDeclinedEventConsumer - paymentDeclinedAlert bookingReference=BK-... transactionId=PAY-DECLINED customerEmail=... amount=... reason=...
```

The dedicated `paymentDeclinedListenerContainerFactory` bean uses a standalone
`ConsumerFactory` so it does not interfere with any default Kafka listener configuration.

### Log evidence

```
INFO  c.s.p.service.PaymentService                   - paymentAuthorizeDecision bookingReference=BK-XYZ transactionId=PAY-DECLINED status=DECLINED
WARN  c.s.b.messaging.PaymentDeclinedEventConsumer   - paymentDeclinedAlert bookingReference=BK-XYZ transactionId=PAY-DECLINED customerEmail=bob@example.com amount=200.0 reason=Payment rule rejected the request
```

---

## Persistence Guarantee

Kafka data is stored in the `kafka-data` named Docker volume:

```yaml
# infra/docker-compose.yml
kafka:
  volumes:
    - kafka-data:/var/lib/kafka/data
  environment:
    KAFKA_LOG_DIRS: /var/lib/kafka/data
```

Named volumes survive `docker compose down` and are only removed with
`docker compose down -v`.  Consumers use `auto.offset.reset = earliest`, so any
messages produced while a consumer was offline are replayed from the start of the
partition log on restart.

---

## Offline Recovery Demo — Payment Declined Scenario

These steps prove that a declined-payment event is not lost when `booking-service` is
stopped at the time of publication.

### Prerequisites

```bash
cd infra
docker compose up -d kafka postgres-booking postgres-payment postgres-schedule
```

### 1 — Stop the consumer

```bash
# booking-service is the consumer for payment-declined events
docker compose stop booking-service   # or kill the local JVM process
```

### 2 — Trigger a declined payment

```bash
curl -s -X POST http://localhost:8083/authorize \
  -H "Content-Type: application/json" \
  -d '{
    "bookingReference": "BK-OFFLINE-TEST",
    "customerEmail": "test@example.com",
    "amount": 999.99,
    "paymentMethodToken": "tok_test"
  }'
# Response: {"approved":false,"transactionId":"PAY-DECLINED","status":"DECLINED",...}
```

Verify the event was written to Kafka even though the consumer is down:

```bash
docker exec smartbus-kafka \
  /opt/kafka/bin/kafka-console-consumer.sh \
  --bootstrap-server localhost:9092 \
  --topic smartbus.payment.declined.v1 \
  --from-beginning \
  --max-messages 1
```

Expected output:

```json
{"schemaVersion":"payment-declined.v1","transactionId":"PAY-DECLINED","bookingReference":"BK-OFFLINE-TEST","customerEmail":"test@example.com","amount":999.99,"reason":"Payment rule rejected the request"}
```

### 3 — Restart the consumer

```bash
docker compose start booking-service   # or restart the local JVM
```

### 4 — Observe backlog replay

Within seconds booking-service logs will show the replayed event:

```
WARN  c.s.b.messaging.PaymentDeclinedEventConsumer - paymentDeclinedAlert bookingReference=BK-OFFLINE-TEST transactionId=PAY-DECLINED customerEmail=test@example.com amount=999.99 reason=Payment rule rejected the request
```

This confirms the message was durably stored in Kafka and replayed from `auto.offset.reset = earliest`
after the consumer rejoined its group.

---

## Configuration Reference

### payment-service `application.yml`

```yaml
spring:
  kafka:
    bootstrap-servers: ${KAFKA_BOOTSTRAP_SERVERS:localhost:9092}
    producer:
      key-serializer: org.apache.kafka.common.serialization.StringSerializer
      value-serializer: org.springframework.kafka.support.serializer.JsonSerializer
      properties:
        spring.json.add.type.headers: false

smartbus:
  messaging:
    payment-declined-topic: ${PAYMENT_DECLINED_TOPIC:smartbus.payment.declined.v1}
```

### booking-service `application.yml`

```yaml
spring:
  kafka:
    bootstrap-servers: ${KAFKA_BOOTSTRAP_SERVERS:localhost:9092}

smartbus:
  messaging:
    booking-confirmed-topic: ${BOOKING_CONFIRMED_TOPIC:smartbus.booking.confirmed.v1}
    payment-declined-topic: ${PAYMENT_DECLINED_TOPIC:smartbus.payment.declined.v1}
    payment-declined-consumer-group: ${PAYMENT_DECLINED_CONSUMER_GROUP:booking-service-payment-audit}
```
