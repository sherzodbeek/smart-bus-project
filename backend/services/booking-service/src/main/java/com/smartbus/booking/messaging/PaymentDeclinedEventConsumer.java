package com.smartbus.booking.messaging;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * Consumes payment-declined events from {@code smartbus.payment.declined.v1}.
 * Logs an alert for each declined payment so operators can identify failed booking
 * attempts and trigger manual remediation or automated retry flows.
 *
 * <p>Consumer group {@code booking-service-payment-audit} maintains its own offset,
 * so missed events during downtime are replayed from the Kafka log on restart.
 */
@Component
public class PaymentDeclinedEventConsumer {

  private static final Logger log = LoggerFactory.getLogger(PaymentDeclinedEventConsumer.class);

  @KafkaListener(
      topics = "${smartbus.messaging.payment-declined-topic}",
      groupId = "${smartbus.messaging.payment-declined-consumer-group}",
      containerFactory = "paymentDeclinedListenerContainerFactory"
  )
  public void onPaymentDeclined(PaymentDeclinedEvent event) {
    try (MDC.MDCCloseable ignored = MDC.putCloseable("bookingReference", event.bookingReference())) {
      log.warn(
          "paymentDeclinedAlert bookingReference={} transactionId={} customerEmail={} amount={} reason={}",
          event.bookingReference(),
          event.transactionId(),
          event.customerEmail(),
          event.amount(),
          event.reason()
      );
    }
  }
}
