package com.smartbus.notification.mongo;

import com.smartbus.notification.dto.NotificationDeliveryResponse;
import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Secondary audit sink that mirrors every notification delivery event to MongoDB.
 * Activated only when {@code smartbus.mongodb.enabled=true}.
 */
@Component
@ConditionalOnProperty(name = "smartbus.mongodb.enabled", havingValue = "true")
public class MongoNotificationSink {

  private static final Logger log = LoggerFactory.getLogger(MongoNotificationSink.class);

  private final NotificationEventMongoRepository mongoRepository;

  public MongoNotificationSink(NotificationEventMongoRepository mongoRepository) {
    this.mongoRepository = mongoRepository;
  }

  public void record(NotificationDeliveryResponse delivery) {
    NotificationEventDocument doc = new NotificationEventDocument(
        delivery.bookingReference(),
        delivery.recipient(),
        delivery.routeCode(),
        delivery.status(),
        delivery.deliveredAt() != null ? delivery.deliveredAt().toInstant() : Instant.now()
    );
    mongoRepository.save(doc);
    log.debug("mongoAuditSaved bookingReference={} id={}", delivery.bookingReference(), doc.getId());
  }
}
