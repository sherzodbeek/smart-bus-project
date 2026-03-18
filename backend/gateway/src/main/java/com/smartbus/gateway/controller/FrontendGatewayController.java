package com.smartbus.gateway.controller;

import com.smartbus.gateway.config.ServiceDirectoryProperties;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.net.URLEncoder;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.util.StreamUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestClient;

@RestController
@RequestMapping("/api/v1/frontend")
public class FrontendGatewayController {

  private static final Logger log = LoggerFactory.getLogger(FrontendGatewayController.class);

  private static final Set<String> HOP_BY_HOP_HEADERS = Set.of(
      HttpHeaders.TRANSFER_ENCODING.toLowerCase(Locale.ROOT),
      HttpHeaders.CONNECTION.toLowerCase(Locale.ROOT),
      "Keep-Alive",
      HttpHeaders.PROXY_AUTHENTICATE.toLowerCase(Locale.ROOT),
      HttpHeaders.PROXY_AUTHORIZATION.toLowerCase(Locale.ROOT),
      HttpHeaders.TE.toLowerCase(Locale.ROOT),
      HttpHeaders.TRAILER.toLowerCase(Locale.ROOT),
      HttpHeaders.UPGRADE.toLowerCase(Locale.ROOT),
      HttpHeaders.CONTENT_LENGTH.toLowerCase(Locale.ROOT)
  ).stream().map(value -> value.toLowerCase(Locale.ROOT)).collect(java.util.stream.Collectors.toUnmodifiableSet());

  private final RestClient restClient;
  private final ServiceDirectoryProperties properties;

  public FrontendGatewayController(RestClient.Builder builder, ServiceDirectoryProperties properties) {
    this.restClient = builder.build();
    this.properties = properties;
  }

  @GetMapping(value = "/routes", produces = MediaType.APPLICATION_JSON_VALUE)
  public ResponseEntity<String> routes() {
    log.info("frontendRoutesRequest target={}", properties.schedule().resolve("/api/v1/schedules/catalog"));
    return forwardGet(properties.schedule().resolve("/api/v1/schedules/catalog"));
  }

  @PostMapping(value = "/quote", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
  public ResponseEntity<String> quote(@RequestBody Map<String, Object> request) {
    log.info(
        "frontendQuoteRequest fromStop={} toStop={} tripDate={} tripType={} passengers={}",
        request.get("fromStop"),
        request.get("toStop"),
        request.get("tripDate"),
        request.get("tripType"),
        request.get("passengers")
    );
    return forwardPost(properties.schedule().resolve("/api/v1/schedules/quote"), request);
  }

  @PostMapping(value = "/bookings", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
  public ResponseEntity<String> createBooking(@RequestBody Map<String, Object> request, Authentication authentication) {
    log.info(
        "frontendCreateBookingRequest user={} fromStop={} toStop={} tripDate={} tripType={} passengers={}",
        authentication.getName(),
        request.get("fromStop"),
        request.get("toStop"),
        request.get("tripDate"),
        request.get("tripType"),
        request.get("passengers")
    );
    return forwardPost(
        properties.booking().resolve("/api/v1/bookings/orchestrated-bookings"),
        withAuthenticatedCustomerEmail(request, authentication)
    );
  }

  @GetMapping(value = "/bookings", produces = MediaType.APPLICATION_JSON_VALUE)
  public ResponseEntity<String> bookings(Authentication authentication) {
    log.info("frontendBookingsRequest user={}", authentication.getName());
    return forwardGet(properties.booking().resolve("/api/v1/bookings?customerEmail=" + encode(authentication.getName())));
  }

  @GetMapping(value = "/bookings/{bookingReference}/state", produces = MediaType.APPLICATION_JSON_VALUE)
  public ResponseEntity<String> bookingState(@PathVariable String bookingReference, Authentication authentication) {
    log.info("frontendBookingStateRequest user={} bookingReference={}", authentication.getName(), bookingReference);
    return forwardGet(properties.booking().resolve("/api/v1/bookings/" + bookingReference + "/state"));
  }

  @PostMapping(
      value = "/admin/routes/{routeCode}/fare",
      consumes = MediaType.APPLICATION_JSON_VALUE,
      produces = MediaType.APPLICATION_JSON_VALUE
  )
  public ResponseEntity<String> updateFare(@PathVariable String routeCode, @RequestBody Map<String, Object> request) {
    log.info("frontendAdminFareUpdate routeCode={} unitPrice={}", routeCode, request.get("unitPrice"));
    return forwardPost(properties.schedule().resolve("/api/v1/schedules/admin/routes/" + routeCode + "/fare"), request);
  }

  private ResponseEntity<String> forwardGet(java.net.URI uri) {
    log.info("frontendGatewayForwardGet uri={}", uri);
    return restClient.get()
        .uri(uri)
        .exchange((request, response) -> toResponseEntity(response.getHeaders(), response.getStatusCode(), response.getBody()));
  }

  private ResponseEntity<String> forwardPost(java.net.URI uri, Map<String, Object> request) {
    log.info("frontendGatewayForwardPost uri={} keys={}", uri, request.keySet());
    return restClient.post()
        .uri(uri)
        .body(request)
        .exchange((httpRequest, response) -> toResponseEntity(response.getHeaders(), response.getStatusCode(), response.getBody()));
  }

  private ResponseEntity<String> toResponseEntity(
      HttpHeaders headers,
      org.springframework.http.HttpStatusCode statusCode,
      InputStream body
  ) throws IOException {
    HttpHeaders forwardedHeaders = new HttpHeaders();
    headers.forEach((name, values) -> {
      if (!HOP_BY_HOP_HEADERS.contains(name.toLowerCase(Locale.ROOT))) {
        forwardedHeaders.put(name, values);
      }
    });
    String payload = body == null ? "" : StreamUtils.copyToString(body, StandardCharsets.UTF_8);
    ResponseEntity.BodyBuilder builder = ResponseEntity.status(statusCode);
    MediaType contentType = headers.getContentType();
    if (contentType != null) {
      builder.contentType(contentType);
    }
    return builder.headers(forwardedHeaders).body(payload);
  }

  private String encode(String value) {
    return URLEncoder.encode(value, StandardCharsets.UTF_8);
  }

  private Map<String, Object> withAuthenticatedCustomerEmail(Map<String, Object> request, Authentication authentication) {
    request.put("customerEmail", authentication.getName());
    return request;
  }
}
