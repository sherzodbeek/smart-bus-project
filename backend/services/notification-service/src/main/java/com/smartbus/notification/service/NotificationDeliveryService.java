package com.smartbus.notification.service;

import com.smartbus.notification.dto.NotificationDeliveryResponse;
import com.smartbus.notification.entity.NotificationDeliveryEntity;
import com.smartbus.notification.mongo.MongoNotificationSink;
import com.smartbus.notification.repository.NotificationDeliveryEntityRepository;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Service;

@Service
public class NotificationDeliveryService {

  private final NotificationDeliveryEntityRepository deliveryRepository;
  private final Optional<MongoNotificationSink> mongoSink;

  public NotificationDeliveryService(
      NotificationDeliveryEntityRepository deliveryRepository,
      Optional<MongoNotificationSink> mongoSink
  ) {
    this.deliveryRepository = deliveryRepository;
    this.mongoSink = mongoSink;
  }

  public NotificationDeliveryResponse save(
      String bookingReference,
      String recipient,
      String routeCode,
      String status
  ) {
    NotificationDeliveryEntity entity = new NotificationDeliveryEntity();
    entity.setBookingReference(bookingReference);
    entity.setRecipient(recipient);
    entity.setRouteCode(routeCode);
    entity.setStatus(status);
    NotificationDeliveryEntity saved = deliveryRepository.save(entity);
    NotificationDeliveryResponse response = toResponse(saved);
    mongoSink.ifPresent(sink -> sink.record(response));
    return response;
  }

  public List<NotificationDeliveryResponse> findAll() {
    return deliveryRepository.findAllByOrderByDeliveredAtDesc().stream()
        .map(this::toResponse)
        .toList();
  }

  public List<NotificationDeliveryResponse> findByBookingReference(String bookingReference) {
    return deliveryRepository.findByBookingReferenceOrderByDeliveredAtDesc(bookingReference).stream()
        .map(this::toResponse)
        .toList();
  }

  public Optional<NotificationDeliveryResponse> findById(Long id) {
    return deliveryRepository.findById(id).map(this::toResponse);
  }

  public Optional<NotificationDeliveryResponse> updateStatus(Long id, String status) {
    return deliveryRepository.findById(id).map(entity -> {
      entity.setStatus(status);
      return toResponse(deliveryRepository.save(entity));
    });
  }

  public boolean deleteById(Long id) {
    if (!deliveryRepository.existsById(id)) {
      return false;
    }
    deliveryRepository.deleteById(id);
    return true;
  }

  private NotificationDeliveryResponse toResponse(NotificationDeliveryEntity entity) {
    return new NotificationDeliveryResponse(
        entity.getId(),
        entity.getBookingReference(),
        entity.getRecipient(),
        entity.getRouteCode(),
        entity.getStatus(),
        entity.getDeliveredAt()
    );
  }
}
