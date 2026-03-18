package com.smartbus.notification.controller;

import com.smartbus.notification.dto.NotificationDispatchRequest;
import com.smartbus.notification.dto.NotificationDispatchResponse;
import com.smartbus.notification.dto.NotificationPreparationRequest;
import com.smartbus.notification.dto.NotificationPreparationResponse;
import jakarta.validation.Valid;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/notifications")
public class NotificationOrchestrationController {

  private static final Logger log = LoggerFactory.getLogger(NotificationOrchestrationController.class);

  @PostMapping("/prepare")
  public NotificationPreparationResponse prepare(@Valid @RequestBody NotificationPreparationRequest request) {
    log.info(
        "notificationPrepareRequest bookingReference={} customerEmail={} routeCode={}",
        request.bookingReference(),
        request.customerEmail(),
        request.routeCode()
    );
    return new NotificationPreparationResponse(
        "BOOKING-CONFIRMATION",
        "Trip " + request.routeCode() + " is being prepared for " + request.customerName(),
        "EMAIL"
    );
  }

  @PostMapping("/dispatch")
  public NotificationDispatchResponse dispatch(@Valid @RequestBody NotificationDispatchRequest request) {
    log.info(
        "notificationDispatchRequest bookingReference={} recipient={} messageLength={}",
        request.bookingReference(),
        request.recipient(),
        request.message() == null ? 0 : request.message().length()
    );
    return new NotificationDispatchResponse(
        "NTF-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase(),
        "SENT"
    );
  }
}
