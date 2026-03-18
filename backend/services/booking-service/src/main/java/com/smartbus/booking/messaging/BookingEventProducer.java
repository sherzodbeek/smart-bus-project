package com.smartbus.booking.messaging;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Component
public class BookingEventProducer {

  private static final Logger log = LoggerFactory.getLogger(BookingEventProducer.class);

  private final KafkaTemplate<String, BookingConfirmedEvent> kafkaTemplate;
  private final MessagingProperties messagingProperties;

  public BookingEventProducer(
      KafkaTemplate<String, BookingConfirmedEvent> kafkaTemplate,
      MessagingProperties messagingProperties
  ) {
    this.kafkaTemplate = kafkaTemplate;
    this.messagingProperties = messagingProperties;
  }

  public void publish(BookingConfirmedEvent event) {
    kafkaTemplate.send(messagingProperties.bookingConfirmedTopic(), event.bookingReference(), event);
    log.info(
        "bookingEventPublished topic={} bookingReference={} status={}",
        messagingProperties.bookingConfirmedTopic(),
        event.bookingReference(),
        event.status()
    );
  }
}
