package com.smartbus.payment.controller;

import java.util.List;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/payments")
public class PaymentFoundationController {

  @GetMapping("/foundation")
  public Map<String, Object> foundation() {
    return Map.of(
        "service", "payment-service",
        "responsibility", "Owns SmartBus payment authorization and transaction state.",
        "database", "smartbus_payment",
        "plannedOperations", List.of(
            "authorize payments",
            "capture transaction references",
            "expose payment status"
        )
    );
  }
}
