package com.smartbus.notification.messaging;

import com.smartbus.notification.dto.ApiErrorResponse;
import com.smartbus.notification.dto.NotificationDeliveryRequest;
import com.smartbus.notification.dto.NotificationDeliveryResponse;
import com.smartbus.notification.service.NotificationDeliveryService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/notifications")
public class NotificationDeliveryController {

  private static final Logger log = LoggerFactory.getLogger(NotificationDeliveryController.class);

  private final NotificationDeliveryService deliveryService;

  public NotificationDeliveryController(NotificationDeliveryService deliveryService) {
    this.deliveryService = deliveryService;
  }

  @PostMapping("/deliveries")
  public ResponseEntity<NotificationDeliveryResponse> createDelivery(
      @Valid @RequestBody NotificationDeliveryRequest body
  ) {
    log.info("notificationDeliveryCreateRequest bookingReference={}", body.bookingReference());
    NotificationDeliveryResponse response = deliveryService.save(
        body.bookingReference(),
        body.recipient(),
        body.routeCode(),
        body.status()
    );
    URI location = URI.create("/api/v1/notifications/deliveries/" + response.id());
    return ResponseEntity.created(location).body(response);
  }

  @GetMapping("/deliveries")
  public List<NotificationDeliveryResponse> listDeliveries(
      @RequestParam(required = false) String bookingReference
  ) {
    if (bookingReference != null && !bookingReference.isBlank()) {
      log.info("notificationDeliveryListRequest bookingReference={}", bookingReference);
      return deliveryService.findByBookingReference(bookingReference);
    }
    log.info("notificationDeliveryListAllRequest");
    return deliveryService.findAll();
  }

  @GetMapping("/deliveries/{id}")
  public ResponseEntity<?> getDelivery(@PathVariable Long id, HttpServletRequest request) {
    log.info("notificationDeliveryRequest id={}", id);
    return deliveryService.findById(id)
        .<ResponseEntity<?>>map(ResponseEntity::ok)
        .orElseGet(() -> ResponseEntity.status(HttpStatus.NOT_FOUND).body(new ApiErrorResponse(
            HttpStatus.NOT_FOUND.value(),
            "DELIVERY_NOT_FOUND",
            "Notification delivery not found.",
            List.of("id: " + id),
            request.getRequestURI()
        )));
  }

  @PutMapping("/deliveries/{id}")
  public ResponseEntity<?> updateDelivery(
      @PathVariable Long id,
      @Valid @RequestBody NotificationDeliveryRequest body,
      HttpServletRequest request
  ) {
    log.info("notificationDeliveryUpdateRequest id={} status={}", id, body.status());
    return deliveryService.updateStatus(id, body.status())
        .<ResponseEntity<?>>map(ResponseEntity::ok)
        .orElseGet(() -> ResponseEntity.status(HttpStatus.NOT_FOUND).body(new ApiErrorResponse(
            HttpStatus.NOT_FOUND.value(),
            "DELIVERY_NOT_FOUND",
            "Notification delivery not found.",
            List.of("id: " + id),
            request.getRequestURI()
        )));
  }

  @DeleteMapping("/deliveries/{id}")
  public ResponseEntity<?> deleteDelivery(
      @PathVariable Long id,
      HttpServletRequest request
  ) {
    log.info("notificationDeliveryDeleteRequest id={}", id);
    boolean deleted = deliveryService.deleteById(id);
    if (!deleted) {
      return ResponseEntity.status(HttpStatus.NOT_FOUND).body(new ApiErrorResponse(
          HttpStatus.NOT_FOUND.value(),
          "DELIVERY_NOT_FOUND",
          "Notification delivery not found.",
          List.of("id: " + id),
          request.getRequestURI()
      ));
    }
    return ResponseEntity.noContent().build();
  }
}
