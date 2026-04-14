package com.smartbus.payment.messaging;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Component
public class PaymentDeclinedEventProducer {

  private static final Logger log = LoggerFactory.getLogger(PaymentDeclinedEventProducer.class);

  private final KafkaTemplate<String, PaymentDeclinedEvent> kafkaTemplate;
  private final PaymentMessagingProperties messagingProperties;

  public PaymentDeclinedEventProducer(
      KafkaTemplate<String, PaymentDeclinedEvent> kafkaTemplate,
      PaymentMessagingProperties messagingProperties
  ) {
    this.kafkaTemplate = kafkaTemplate;
    this.messagingProperties = messagingProperties;
  }

  public void publish(PaymentDeclinedEvent event) {
    kafkaTemplate.send(messagingProperties.paymentDeclinedTopic(), event.bookingReference(), event);
    log.info(
        "paymentDeclinedEventPublished topic={} bookingReference={} transactionId={} amount={}",
        messagingProperties.paymentDeclinedTopic(),
        event.bookingReference(),
        event.transactionId(),
        event.amount()
    );
  }
}
