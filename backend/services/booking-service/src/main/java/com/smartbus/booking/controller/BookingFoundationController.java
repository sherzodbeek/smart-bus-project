package com.smartbus.booking.controller;

import java.util.List;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/bookings")
public class BookingFoundationController {

  @GetMapping("/foundation")
  public Map<String, Object> foundation() {
    return Map.of(
        "service", "booking-service",
        "responsibility", "Owns the SmartBus ticket purchase lifecycle.",
        "database", "smartbus_booking",
        "dependsOn", List.of("schedule-service", "payment-service", "notification-service"),
        "plannedOperations", List.of(
            "create booking request",
            "track booking status",
            "coordinate booking workflow"
        )
    );
  }
}
