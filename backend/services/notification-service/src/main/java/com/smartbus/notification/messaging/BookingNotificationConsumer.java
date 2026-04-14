package com.smartbus.notification.messaging;

import com.smartbus.notification.service.NotificationDeliveryService;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
public class BookingNotificationConsumer {

  private static final Logger log = LoggerFactory.getLogger(BookingNotificationConsumer.class);

  private final NotificationDeliveryService deliveryService;

  @Value("${smartbus.messaging.booking-confirmed-topic}")
  private String topic;

  @Value("${smartbus.messaging.booking-consumer-group}")
  private String groupId;

  public BookingNotificationConsumer(NotificationDeliveryService deliveryService) {
    this.deliveryService = deliveryService;
  }

  @PostConstruct
  void logRegistration() {
    log.info("kafkaConsumerRegistered topic={} groupId={}", topic, groupId);
  }

  @KafkaListener(
      topics = "${smartbus.messaging.booking-confirmed-topic}",
      groupId = "${smartbus.messaging.booking-consumer-group}",
      containerFactory = "kafkaListenerContainerFactory"
  )
  public void onBookingConfirmed(BookingConfirmedEvent event) {
    try (MDC.MDCCloseable ignored = MDC.putCloseable("bookingReference", event.bookingReference())) {
      deliveryService.save(
          event.bookingReference(),
          event.customerEmail(),
          event.routeCode(),
          "SENT"
      );
      log.info(
          "bookingEventConsumed bookingReference={} customerEmail={} routeCode={} status={}",
          event.bookingReference(),
          event.customerEmail(),
          event.routeCode(),
          event.status()
      );
    }
  }
}
