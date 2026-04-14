package com.smartbus.gateway.controller;

import com.smartbus.gateway.config.ServiceDirectoryProperties;
import com.smartbus.gateway.dto.BookingTripSummary;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestClient;

/**
 * Demonstrates JSON-to-JSON transformation: aggregates booking and payment data from two
 * downstream services into a single, externally-facing flattened document with renamed fields.
 */
@RestController
@RequestMapping("/api/v1/gateway")
public class DataTransformationController {

  private static final Logger log = LoggerFactory.getLogger(DataTransformationController.class);

  private final RestClient restClient;
  private final ServiceDirectoryProperties properties;

  public DataTransformationController(
      RestClient.Builder builder,
      ServiceDirectoryProperties properties
  ) {
    this.restClient = builder.build();
    this.properties = properties;
  }

  /**
   * Aggregates booking details (from booking-service) with payment status (from payment-service)
   * and returns a single flattened document with consumer-friendly field names.
   *
   * <p>Transformation applied:
   * <ul>
   *   <li>customerName  → passengerName</li>
   *   <li>customerEmail → email</li>
   *   <li>fromStop      → origin</li>
   *   <li>toStop        → destination</li>
   *   <li>tripDate      → travelDate</li>
   *   <li>passengers    → seats</li>
   *   <li>currentState  → bookingStatus</li>
   *   <li>paymentStatus enriched from payment-service (not present in booking response)</li>
   * </ul>
   */
  @GetMapping("/booking-summary/{bookingReference}")
  public BookingTripSummary bookingSummary(
      @PathVariable String bookingReference,
      Authentication authentication
  ) {
    log.info("bookingSummaryRequest bookingReference={} user={}", bookingReference, authentication.getName());

    Map<String, Object> booking = restClient.get()
        .uri(properties.booking().resolve("/api/v1/bookings/" + bookingReference))
        .retrieve()
        .body(new ParameterizedTypeReference<>() {});

    String paymentStatus = resolvePaymentStatus(bookingReference,
        nullableString(booking, "paymentTransactionId"));

    return new BookingTripSummary(
        nullableString(booking, "bookingReference"),
        nullableString(booking, "customerName"),
        nullableString(booking, "customerEmail"),
        nullableString(booking, "fromStop"),
        nullableString(booking, "toStop"),
        nullableString(booking, "tripDate"),
        nullableString(booking, "tripType"),
        intValue(booking, "passengers"),
        nullableString(booking, "routeCode"),
        nullableString(booking, "departureTime"),
        nullableString(booking, "arrivalTime"),
        doubleValue(booking, "totalAmount"),
        paymentStatus,
        nullableString(booking, "paymentTransactionId"),
        nullableString(booking, "currentState")
    );
  }

  private String resolvePaymentStatus(String bookingReference, String transactionId) {
    if (transactionId == null || transactionId.isBlank()) {
      return "PENDING";
    }
    try {
      List<Map<String, Object>> records = restClient.get()
          .uri(properties.payment()
              .resolve("/api/v1/payments/records?bookingReference=" + bookingReference))
          .retrieve()
          .body(new ParameterizedTypeReference<>() {});
      if (records != null && !records.isEmpty()) {
        return nullableString(records.get(0), "status");
      }
    } catch (Exception exception) {
      log.warn("paymentStatusLookupFailed bookingReference={} reason={}", bookingReference, exception.getMessage());
    }
    return "UNKNOWN";
  }

  private static String nullableString(Map<String, Object> map, String key) {
    Object value = map == null ? null : map.get(key);
    return value == null ? null : String.valueOf(value);
  }

  private static int intValue(Map<String, Object> map, String key) {
    Object value = map == null ? null : map.get(key);
    if (value instanceof Number number) {
      return number.intValue();
    }
    return 0;
  }

  private static double doubleValue(Map<String, Object> map, String key) {
    Object value = map == null ? null : map.get(key);
    if (value instanceof Number number) {
      return number.doubleValue();
    }
    return 0.0;
  }
}
