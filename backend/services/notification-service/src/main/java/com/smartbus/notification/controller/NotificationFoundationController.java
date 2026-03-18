package com.smartbus.notification.controller;

import java.util.List;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/notifications")
public class NotificationFoundationController {

  @GetMapping("/foundation")
  public Map<String, Object> foundation() {
    return Map.of(
        "service", "notification-service",
        "responsibility", "Owns SmartBus confirmation and alert delivery.",
        "database", "smartbus_notification",
        "plannedOperations", List.of(
            "queue booking confirmations",
            "track delivery attempts",
            "support email and SMS notifications"
        )
    );
  }
}
