package com.smartbus.schedule.controller;

import java.util.List;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/schedules")
public class ScheduleFoundationController {

  @GetMapping("/foundation")
  public Map<String, Object> foundation() {
    return Map.of(
        "service", "schedule-service",
        "responsibility", "Owns SmartBus routes, schedules, and availability lookups.",
        "database", "smartbus_schedule",
        "plannedOperations", List.of(
            "search trips by origin and destination",
            "lookup route schedules",
            "check seat availability"
        )
    );
  }
}
