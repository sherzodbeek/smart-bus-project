package com.smartbus.notification.messaging;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
public class BookingNotificationConsumer {

  private static final Logger log = LoggerFactory.getLogger(BookingNotificationConsumer.class);

  private final InMemoryNotificationDeliveryStore deliveryStore;

  public BookingNotificationConsumer(InMemoryNotificationDeliveryStore deliveryStore) {
    this.deliveryStore = deliveryStore;
  }

  @KafkaListener(
      topics = "${smartbus.messaging.booking-confirmed-topic}",
      groupId = "${smartbus.messaging.booking-consumer-group}"
  )
  public void onBookingConfirmed(BookingConfirmedEvent event) {
    deliveryStore.save(new NotificationDeliveryRecord(
        event.bookingReference(),
        event.customerEmail(),
        event.routeCode(),
        "SENT"
    ));
    log.info(
        "bookingEventConsumed bookingReference={} customerEmail={} routeCode={} status={}",
        event.bookingReference(),
        event.customerEmail(),
        event.routeCode(),
        event.status()
    );
  }
}
