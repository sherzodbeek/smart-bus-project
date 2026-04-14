package com.smartbus.notification.repository;

import com.smartbus.notification.entity.NotificationDeliveryEntity;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface NotificationDeliveryEntityRepository
    extends JpaRepository<NotificationDeliveryEntity, Long> {

  List<NotificationDeliveryEntity> findAllByOrderByDeliveredAtDesc();

  List<NotificationDeliveryEntity> findByBookingReferenceOrderByDeliveredAtDesc(
      String bookingReference);
}
