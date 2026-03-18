package com.smartbus.notification.messaging;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import org.springframework.stereotype.Component;

@Component
public class InMemoryNotificationDeliveryStore {

  private final CopyOnWriteArrayList<NotificationDeliveryRecord> records = new CopyOnWriteArrayList<>();

  public void save(NotificationDeliveryRecord record) {
    records.add(record);
  }

  public List<NotificationDeliveryRecord> findAll() {
    return List.copyOf(records);
  }
}
