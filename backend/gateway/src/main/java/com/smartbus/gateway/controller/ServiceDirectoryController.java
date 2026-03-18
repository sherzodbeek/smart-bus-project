package com.smartbus.gateway.controller;

import com.smartbus.gateway.config.ServiceDirectoryProperties;
import java.util.List;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/system")
public class ServiceDirectoryController {

  private final ServiceDirectoryProperties properties;

  public ServiceDirectoryController(ServiceDirectoryProperties properties) {
    this.properties = properties;
  }

  @GetMapping("/services")
  public Map<String, Object> services() {
    return Map.of(
        "gateway", "smartbus-gateway",
        "frontendHint", "Static SmartBus frontend lives in frontend/ and should call the gateway first.",
        "services", List.of(
            Map.of("name", "booking-service", "url", properties.booking().toString(), "responsibility", "booking lifecycle"),
            Map.of("name", "schedule-service", "url", properties.schedule().toString(), "responsibility", "trip and route lookup"),
            Map.of("name", "payment-service", "url", properties.payment().toString(), "responsibility", "payment authorization"),
            Map.of("name", "notification-service", "url", properties.notification().toString(), "responsibility", "confirmation delivery")
        )
    );
  }
}
