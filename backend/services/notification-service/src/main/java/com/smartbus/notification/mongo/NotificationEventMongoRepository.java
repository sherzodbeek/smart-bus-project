package com.smartbus.notification.mongo;

import java.util.List;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface NotificationEventMongoRepository extends MongoRepository<NotificationEventDocument, String> {

  List<NotificationEventDocument> findByBookingReference(String bookingReference);
}
