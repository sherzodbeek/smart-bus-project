package com.smartbus.notification.messaging;

import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/notifications")
public class NotificationDeliveryController {

  private final InMemoryNotificationDeliveryStore deliveryStore;

  public NotificationDeliveryController(InMemoryNotificationDeliveryStore deliveryStore) {
    this.deliveryStore = deliveryStore;
  }

  @GetMapping("/deliveries")
  public List<NotificationDeliveryRecord> deliveries() {
    return deliveryStore.findAll();
  }
}
