package com.smartbus.gateway.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartbus.gateway.auth.GatewayUser;
import com.smartbus.gateway.auth.GatewayUserRepository;
import com.smartbus.gateway.auth.JwtService;
import com.smartbus.gateway.config.ServiceDirectoryProperties;
import com.smartbus.gateway.dto.AdminBusRequest;
import com.smartbus.gateway.dto.AdminBusResponse;
import com.smartbus.gateway.dto.AdminDashboardResponse;
import com.smartbus.gateway.dto.AdminLocationRequest;
import com.smartbus.gateway.dto.AdminLocationResponse;
import com.smartbus.gateway.dto.AdminReportsResponse;
import com.smartbus.gateway.dto.AdminRouteRequest;
import com.smartbus.gateway.dto.AdminSettingsResponse;
import com.smartbus.gateway.dto.AdminTicketResponse;
import com.smartbus.gateway.dto.AdminTicketsResponse;
import com.smartbus.gateway.dto.AdminUserRequest;
import com.smartbus.gateway.dto.AdminUserResponse;
import com.smartbus.gateway.dto.ChangePasswordRequest;
import com.smartbus.gateway.dto.ContactMessageRequest;
import com.smartbus.gateway.dto.GeneratedTicketPdf;
import com.smartbus.gateway.dto.ProfileResponse;
import com.smartbus.gateway.dto.StoredTicketDocument;
import com.smartbus.gateway.dto.TicketDocumentMetadataResponse;
import com.smartbus.gateway.dto.UpdatePreferencesRequest;
import com.smartbus.gateway.dto.UpdateProfileRequest;
import com.smartbus.gateway.exception.GatewayApiException;
import com.smartbus.gateway.repository.FrontendGatewayRepository;
import java.io.IOException;
import java.io.ByteArrayOutputStream;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

@Service
public class FrontendManagementService {

  private static final Logger log = LoggerFactory.getLogger(FrontendManagementService.class);

  private final FrontendGatewayRepository repository;
  private final GatewayUserRepository gatewayUserRepository;
  private final JwtService jwtService;
  private final RestClient restClient;
  private final ServiceDirectoryProperties properties;
  private final ObjectMapper objectMapper;

  public FrontendManagementService(
      FrontendGatewayRepository repository,
      GatewayUserRepository gatewayUserRepository,
      JwtService jwtService,
      RestClient.Builder restClientBuilder,
      ServiceDirectoryProperties properties,
      ObjectMapper objectMapper
  ) {
    this.repository = repository;
    this.gatewayUserRepository = gatewayUserRepository;
    this.jwtService = jwtService;
    this.restClient = restClientBuilder.build();
    this.properties = properties;
    this.objectMapper = objectMapper;
  }

  public void submitContactMessage(ContactMessageRequest request) {
    log.info("contactMessagePersistStart email={} subject={}", request.email(), request.subject());
    repository.saveContactMessage(request);
    log.info("contactMessagePersistSuccess email={}", request.email());
  }

  public ProfileResponse loadProfile(String email) {
    log.info("profileLoadStart email={}", email);
    return repository.loadProfile(email);
  }

  public ProfileResponse updateProfile(String currentEmail, UpdateProfileRequest request) {
    log.info("profileUpdateStart currentEmail={} requestedEmail={}", currentEmail, request.email());
    ProfileResponse profile = repository.updateProfile(currentEmail, request);
    GatewayUser user = gatewayUserRepository.findByEmail(profile.email())
        .orElseThrow(() -> new GatewayApiException(HttpStatus.NOT_FOUND, "Updated user profile was not found."));
    return new ProfileResponse(
        profile.name(),
        profile.email(),
        profile.phone(),
        profile.address(),
        profile.language(),
        profile.emailNotifications(),
        profile.smsAlerts(),
        profile.pushNotifications(),
        profile.role(),
        jwtService.issueToken(user)
    );
  }

  public void changePassword(String email, ChangePasswordRequest request) {
    log.info("passwordChangeStart email={}", email);
    repository.changePassword(email, request.currentPassword(), request.newPassword());
    log.info("passwordChangeSuccess email={}", email);
  }

  public ProfileResponse updatePreferences(String email, UpdatePreferencesRequest request) {
    log.info("preferencesUpdateStart email={}", email);
    return repository.updatePreferences(email, request);
  }

  public List<AdminUserResponse> listUsers() {
    log.info("adminUsersLoadStart");
    return repository.listUsers();
  }

  public AdminUserResponse createUser(AdminUserRequest request) {
    log.info("adminCreateUserStart email={} role={}", request.email(), request.role());
    if (request.password() == null || request.password().isBlank()) {
      throw new GatewayApiException(HttpStatus.BAD_REQUEST, "Password is required for a new user.");
    }
    return repository.createUser(
        request.fullName(),
        request.email(),
        request.phone(),
        normalizeRole(request.role()),
        request.password(),
        normalizeStatus(request.status())
    );
  }

  public AdminUserResponse updateUser(long id, AdminUserRequest request) {
    log.info("adminUpdateUserStart id={} email={} role={}", id, request.email(), request.role());
    return repository.updateUser(
        id,
        request.fullName(),
        request.email(),
        request.phone(),
        normalizeRole(request.role()),
        request.password(),
        normalizeStatus(request.status())
    );
  }

  public void deleteUser(long id, String currentAdminEmail) {
    log.info("adminDeleteUserStart id={} actor={}", id, currentAdminEmail);
    Optional<AdminUserResponse> currentAdmin = repository.findUserByEmail(currentAdminEmail);
    if (currentAdmin.isPresent() && currentAdmin.get().id() == id) {
      throw new GatewayApiException(HttpStatus.BAD_REQUEST, "You cannot delete the currently signed-in admin account.");
    }
    repository.deleteUser(id);
    log.info("adminDeleteUserSuccess id={} actor={}", id, currentAdminEmail);
  }

  public List<AdminBusResponse> listBuses() {
    log.info("adminBusesLoadStart");
    return repository.listBuses();
  }

  public AdminBusResponse createBus(AdminBusRequest request) {
    log.info("adminCreateBusStart busId={} plateNumber={}", request.busId(), request.plateNumber());
    return repository.createBus(normalizeBusRequest(request));
  }

  public AdminBusResponse updateBus(long id, AdminBusRequest request) {
    log.info("adminUpdateBusStart id={} busId={} plateNumber={}", id, request.busId(), request.plateNumber());
    return repository.updateBus(id, normalizeBusRequest(request));
  }

  public void deleteBus(long id) {
    log.info("adminDeleteBusStart id={}", id);
    repository.deleteBus(id);
    log.info("adminDeleteBusSuccess id={}", id);
  }

  public AdminDashboardResponse loadDashboard() {
    log.info("adminDashboardLoadStart");
    List<Map<String, Object>> bookings = fetchBookings();
    List<Map<String, Object>> routes = fetchRoutes();
    LocalDate today = LocalDate.now();
    long todaysTickets = bookings.stream()
        .filter(booking -> today.toString().equals(String.valueOf(booking.get("tripDate"))))
        .count();
    double revenue = bookings.stream()
        .filter(booking -> !"FAILED".equals(String.valueOf(booking.get("currentState"))))
        .mapToDouble(booking -> asDouble(booking.get("totalAmount")))
        .sum();

    Map<String, Object> stats = Map.of(
        "totalRoutes", routes.size(),
        "activeBuses", repository.countActiveBuses(),
        "todaysTickets", todaysTickets,
        "revenue", revenue
    );

    List<Map<String, Object>> recentTickets = bookings.stream()
        .limit(6)
        .map(booking -> Map.<String, Object>of(
            "bookingReference", valueOf(booking.get("bookingReference")),
            "customerName", valueOf(booking.get("customerName")),
            "route", routeLabel(booking),
            "tripDate", valueOf(booking.get("tripDate")),
            "amount", asDouble(booking.get("totalAmount")),
            "status", valueOf(booking.get("currentState"))
        ))
        .toList();

    List<Map<String, Object>> alerts = new ArrayList<>(repository.maintenanceAlerts());
    long failedBookings = bookings.stream()
        .filter(booking -> "FAILED".equals(String.valueOf(booking.get("currentState"))))
        .count();
    if (failedBookings > 0) {
      alerts.add(0, Map.of(
          "severity", "danger",
          "title", "Failed bookings detected",
          "detail", failedBookings + " booking workflows are currently in FAILED state."
      ));
    }
    if (alerts.isEmpty()) {
      alerts.add(Map.of(
          "severity", "success",
          "title", "System healthy",
          "detail", "No current fleet or booking alerts."
      ));
    }

    return new AdminDashboardResponse(stats, recentTickets, alerts);
  }

  public AdminTicketsResponse loadTickets() {
    log.info("adminTicketsLoadStart");
    List<Map<String, Object>> bookings = fetchBookings();
    LocalDate today = LocalDate.now();
    Map<String, Object> stats = Map.of(
        "todaysSales", bookings.stream().filter(booking -> today.toString().equals(valueOf(booking.get("tripDate")))).count(),
        "revenue", bookings.stream().filter(booking -> !"FAILED".equals(valueOf(booking.get("currentState")))).mapToDouble(booking -> asDouble(booking.get("totalAmount"))).sum(),
        "refunds", bookings.stream().filter(booking -> "FAILED".equals(valueOf(booking.get("currentState")))).count(),
        "activePasses", 0
    );
    List<AdminTicketResponse> tickets = bookings.stream()
        .map(booking -> new AdminTicketResponse(
            valueOf(booking.get("bookingReference")),
            valueOf(booking.get("customerName")),
            valueOf(booking.get("customerEmail")),
            valueOf(booking.get("routeCode")),
            valueOf(booking.get("fromStop")),
            valueOf(booking.get("toStop")),
            valueOf(booking.get("tripDate")),
            valueOf(booking.get("departureTime")),
            asDouble(booking.get("totalAmount")),
            valueOf(booking.get("currentState"))
        ))
        .toList();
    return new AdminTicketsResponse(stats, tickets, List.of());
  }

  public AdminReportsResponse loadReports() {
    log.info("adminReportsLoadStart");
    List<Map<String, Object>> bookings = fetchBookings();
    List<Map<String, Object>> routes = fetchRoutes();

    double totalRevenue = bookings.stream()
        .filter(booking -> !"FAILED".equals(valueOf(booking.get("currentState"))))
        .mapToDouble(booking -> asDouble(booking.get("totalAmount")))
        .sum();
    long ticketsSold = bookings.stream()
        .filter(booking -> !"FAILED".equals(valueOf(booking.get("currentState"))))
        .count();
    double avgDailyRiders = bookings.stream()
        .collect(Collectors.groupingBy(booking -> valueOf(booking.get("tripDate"))))
        .values()
        .stream()
        .mapToInt(dayBookings -> dayBookings.stream().mapToInt(booking -> (int) asDouble(booking.get("passengers"))).sum())
        .average()
        .orElse(0);

    Map<String, Object> stats = Map.of(
        "totalRevenue", totalRevenue,
        "ticketsSold", ticketsSold,
        "avgDailyRiders", Math.round(avgDailyRiders),
        "routesCoverage", routes.size()
    );

    List<Map<String, Object>> topRoutes = bookings.stream()
        .filter(booking -> booking.get("routeCode") != null)
        .collect(Collectors.groupingBy(booking -> valueOf(booking.get("routeCode"))))
        .entrySet()
        .stream()
        .map(entry -> {
          String routeCode = entry.getKey();
          long riders = entry.getValue().stream().mapToLong(booking -> (long) asDouble(booking.get("passengers"))).sum();
          double revenue = entry.getValue().stream().mapToDouble(booking -> asDouble(booking.get("totalAmount"))).sum();
          double occupancy = Math.min(100.0, riders * 10.0);
          return Map.<String, Object>of(
              "routeCode", routeCode,
              "routeLabel", routeCode + " - " + valueOf(entry.getValue().getFirst().get("fromStop")) + " to " + valueOf(entry.getValue().getFirst().get("toStop")),
              "dailyAvgRiders", riders,
              "monthlyRevenue", revenue,
              "occupancyRate", Math.round(occupancy)
          );
        })
        .sorted(Comparator.comparingDouble(item -> -asDouble(item.get("monthlyRevenue"))))
        .limit(5)
        .toList();

    return new AdminReportsResponse(stats, topRoutes);
  }

  public AdminSettingsResponse loadSettings() {
    log.info("adminSettingsLoadStart");
    return repository.loadSettings();
  }

  public AdminSettingsResponse updateSettings(String section, Map<String, Object> values) {
    log.info("adminSettingsUpdateStart section={} keys={}", section, values.keySet());
    return repository.updateSettingsSection(section, values);
  }

  public List<TicketDocumentMetadataResponse> listTicketDocuments(String email) {
    log.info("ticketDocumentsListRequest email={}", email);
    return repository.listTicketDocuments(email);
  }

  public TicketDocumentMetadataResponse uploadTicketDocument(
      String email,
      String bookingReference,
      String fileName,
      String contentType,
      byte[] content
  ) {
    log.info("ticketDocumentUploadStart email={} bookingReference={} fileName={} sizeBytes={}", email, bookingReference, fileName, content.length);
    ensureUserOwnsBooking(email, bookingReference);
    if (content.length == 0) {
      throw new GatewayApiException(HttpStatus.BAD_REQUEST, "Uploaded PDF file is empty.");
    }
    String normalizedContentType = contentType == null || contentType.isBlank() ? MediaType.APPLICATION_PDF_VALUE : contentType;
    if (!MediaType.APPLICATION_PDF_VALUE.equalsIgnoreCase(normalizedContentType)) {
      throw new GatewayApiException(HttpStatus.BAD_REQUEST, "Only PDF ticket documents are allowed.");
    }
    return repository.storeTicketDocument(email, bookingReference, fileName, normalizedContentType, content);
  }

  public StoredTicketDocument loadTicketDocument(String email, String bookingReference) {
    log.info("ticketDocumentDownloadRequest email={} bookingReference={}", email, bookingReference);
    ensureUserOwnsBooking(email, bookingReference);
    return repository.findStoredTicketDocument(email, bookingReference)
        .orElseThrow(() -> new GatewayApiException(HttpStatus.NOT_FOUND, "No PDF ticket document was uploaded for this booking."));
  }

  public GeneratedTicketPdf generateTicketPdf(String email, String bookingReference) {
    log.info("ticketPdfGenerateRequest email={} bookingReference={}", email, bookingReference);
    Map<String, Object> booking = loadOwnedBooking(email, bookingReference);
    try (PDDocument document = new PDDocument(); ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
      PDPage page = new PDPage();
      document.addPage(page);

      try (PDPageContentStream contentStream = new PDPageContentStream(document, page)) {
        contentStream.beginText();
        contentStream.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD), 18);
        contentStream.newLineAtOffset(50, 750);
        contentStream.showText("SmartBus E-Ticket");
        contentStream.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 12);
        contentStream.newLineAtOffset(0, -30);
        contentStream.showText("Booking Reference: " + valueOf(booking.get("bookingReference")));
        contentStream.newLineAtOffset(0, -20);
        contentStream.showText("Passenger: " + valueOf(booking.get("customerName")));
        contentStream.newLineAtOffset(0, -20);
        contentStream.showText("Route: " + valueOf(booking.get("fromStop")) + " to " + valueOf(booking.get("toStop")));
        contentStream.newLineAtOffset(0, -20);
        contentStream.showText("Travel Date: " + valueOf(booking.get("tripDate")));
        contentStream.newLineAtOffset(0, -20);
        contentStream.showText("Departure Time: " + valueOf(booking.get("departureTime")));
        contentStream.newLineAtOffset(0, -20);
        contentStream.showText("Trip Type: " + valueOf(booking.get("tripType")));
        contentStream.newLineAtOffset(0, -20);
        contentStream.showText("Passengers: " + valueOf(booking.get("passengers")));
        contentStream.newLineAtOffset(0, -20);
        contentStream.showText("Total Amount: $" + String.format(java.util.Locale.US, "%.2f", asDouble(booking.get("totalAmount"))));
        contentStream.newLineAtOffset(0, -20);
        contentStream.showText("Status: " + valueOf(booking.get("currentState")));
        contentStream.newLineAtOffset(0, -40);
        contentStream.showText("Thank you for choosing SmartBus.");
        contentStream.endText();
      }

      document.save(outputStream);
      return new GeneratedTicketPdf(bookingReference + "-ticket.pdf", outputStream.toByteArray());
    } catch (IOException exception) {
      throw new GatewayApiException(HttpStatus.INTERNAL_SERVER_ERROR, "Unable to generate the SmartBus e-ticket PDF.");
    }
  }

  public List<AdminLocationResponse> loadLocations() {
    log.info("adminLocationsLoadStart");
    String payload = fetch(properties.schedule().resolve("/api/v1/schedules/admin/locations"));
    List<Map<String, Object>> items = readList(payload);
    return items.stream()
        .map(item -> new AdminLocationResponse(
            ((Number) item.get("id")).longValue(),
            valueOf(item.get("name"))
        ))
        .toList();
  }

  public AdminLocationResponse createLocation(AdminLocationRequest request) {
    log.info("adminCreateLocationStart name={}", request.name());
    try {
      return restClient.post()
          .uri(properties.schedule().resolve("/api/v1/schedules/admin/locations"))
          .body(request)
          .retrieve()
          .body(AdminLocationResponse.class);
    } catch (RestClientResponseException exception) {
      throw asGatewayException(exception, "SmartBus schedule service rejected the location request.");
    } catch (RestClientException exception) {
      throw new GatewayApiException(HttpStatus.BAD_GATEWAY, "SmartBus schedule service is temporarily unavailable.");
    }
  }

  public AdminLocationResponse updateLocation(long id, AdminLocationRequest request) {
    log.info("adminUpdateLocationStart id={} name={}", id, request.name());
    try {
      return restClient.put()
          .uri(properties.schedule().resolve("/api/v1/schedules/admin/locations/" + id))
          .body(request)
          .retrieve()
          .body(AdminLocationResponse.class);
    } catch (RestClientResponseException exception) {
      throw asGatewayException(exception, "SmartBus schedule service rejected the location update.");
    } catch (RestClientException exception) {
      throw new GatewayApiException(HttpStatus.BAD_GATEWAY, "SmartBus schedule service is temporarily unavailable.");
    }
  }

  public void deleteLocation(long id) {
    log.info("adminDeleteLocationStart id={}", id);
    try {
      restClient.delete()
          .uri(properties.schedule().resolve("/api/v1/schedules/admin/locations/" + id))
          .retrieve()
          .toBodilessEntity();
    } catch (RestClientResponseException exception) {
      throw asGatewayException(exception, "SmartBus schedule service rejected the location delete request.");
    } catch (RestClientException exception) {
      throw new GatewayApiException(HttpStatus.BAD_GATEWAY, "SmartBus schedule service is temporarily unavailable.");
    }
  }

  public Map<String, Object> createRoute(AdminRouteRequest request) {
    log.info("adminCreateRouteStart routeCode={} fromStop={} toStop={}", request.routeCode(), request.fromStop(), request.toStop());
    return writeScheduleRoute("/api/v1/schedules/admin/routes", request, null);
  }

  public Map<String, Object> updateRoute(String routeCode, AdminRouteRequest request) {
    log.info("adminUpdateRouteStart routeCode={} nextRouteCode={} fromStop={} toStop={}", routeCode, request.routeCode(), request.fromStop(), request.toStop());
    return writeScheduleRoute("/api/v1/schedules/admin/routes/" + routeCode, request, "PUT");
  }

  public void deleteRoute(String routeCode) {
    log.info("adminDeleteRouteStart routeCode={}", routeCode);
    try {
      restClient.delete()
          .uri(properties.schedule().resolve("/api/v1/schedules/admin/routes/" + routeCode))
          .retrieve()
          .toBodilessEntity();
    } catch (RestClientException exception) {
      throw new GatewayApiException(HttpStatus.BAD_GATEWAY, "SmartBus schedule service is temporarily unavailable.");
    }
  }

  private List<Map<String, Object>> fetchBookings() {
    log.info("adminDownstreamFetch service=booking-service operation=list-bookings");
    String payload = fetch(properties.booking().resolve("/api/v1/bookings/admin/bookings"));
    // booking-service returns PagedResponse { items: [...], page, size, totalElements, totalPages }
    Map<String, Object> paged = readMap(payload);
    Object items = paged.get("items");
    if (items instanceof List<?> list) {
      return list.stream()
          .filter(e -> e instanceof Map<?, ?>)
          .map(e -> {
            @SuppressWarnings("unchecked")
            Map<String, Object> m = (Map<String, Object>) e;
            return m;
          })
          .toList();
    }
    return List.of();
  }

  private List<Map<String, Object>> fetchRoutes() {
    log.info("adminDownstreamFetch service=schedule-service operation=route-catalog");
    String payload = fetch(properties.schedule().resolve("/api/v1/schedules/catalog"));
    Map<String, Object> body = readMap(payload);
    Object routes = body.get("routes");
    if (routes instanceof List<?> list) {
      return list.stream()
          .filter(Map.class::isInstance)
          .map(item -> (Map<String, Object>) item)
          .toList();
    }
    return List.of();
  }

  private String fetch(java.net.URI uri) {
    try {
      log.info("adminDownstreamHttpGet uri={}", uri);
      return restClient.get()
          .uri(uri)
          .retrieve()
          .body(String.class);
    } catch (RestClientException exception) {
      throw new GatewayApiException(HttpStatus.BAD_GATEWAY, "SmartBus admin data is temporarily unavailable.");
    }
  }

  private void ensureUserOwnsBooking(String email, String bookingReference) {
    loadOwnedBooking(email, bookingReference);
  }

  private Map<String, Object> loadOwnedBooking(String email, String bookingReference) {
    String payload = fetch(properties.booking().resolve("/api/v1/bookings?customerEmail=" + java.net.URLEncoder.encode(email, java.nio.charset.StandardCharsets.UTF_8)));
    return readList(payload).stream()
        .filter(item -> bookingReference.equals(valueOf(item.get("bookingReference"))))
        .findFirst()
        .orElseThrow(() -> new GatewayApiException(HttpStatus.NOT_FOUND, "Booking was not found for the authenticated user."));
  }

  private Map<String, Object> writeScheduleRoute(String path, AdminRouteRequest request, String method) {
    try {
      String payload = "PUT".equals(method)
          ? restClient.put()
              .uri(properties.schedule().resolve(path))
              .body(request)
              .retrieve()
              .body(String.class)
          : restClient.post()
              .uri(properties.schedule().resolve(path))
              .body(request)
              .retrieve()
              .body(String.class);
      return readMap(payload == null ? "{}" : payload);
    } catch (RestClientResponseException exception) {
      throw asGatewayException(exception, "SmartBus schedule service rejected the route request.");
    } catch (RestClientException exception) {
      throw new GatewayApiException(HttpStatus.BAD_GATEWAY, "SmartBus schedule service is temporarily unavailable.");
    }
  }

  private GatewayApiException asGatewayException(RestClientResponseException exception, String fallbackMessage) {
    HttpStatus status = HttpStatus.resolve(exception.getStatusCode().value());
    if (status == null) {
      status = HttpStatus.BAD_GATEWAY;
    }
    String body = exception.getResponseBodyAsString();
    String message = body == null || body.isBlank() ? fallbackMessage : extractDownstreamMessage(body, fallbackMessage);
    return new GatewayApiException(status, message);
  }

  private String extractDownstreamMessage(String body, String fallbackMessage) {
    try {
      Map<String, Object> parsed = readMap(body);
      Object message = parsed.get("message");
      return message == null || String.valueOf(message).isBlank() ? fallbackMessage : String.valueOf(message);
    } catch (GatewayApiException exception) {
      return fallbackMessage;
    }
  }

  private Map<String, Object> readMap(String payload) {
    try {
      return objectMapper.readValue(payload, new TypeReference<LinkedHashMap<String, Object>>() {
      });
    } catch (IOException exception) {
      throw new GatewayApiException(HttpStatus.BAD_GATEWAY, "Gateway received invalid JSON from a downstream service.");
    }
  }

  private List<Map<String, Object>> readList(String payload) {
    try {
      List<LinkedHashMap<String, Object>> parsed = objectMapper.readValue(
          payload,
          new TypeReference<List<LinkedHashMap<String, Object>>>() {
          }
      );
      return parsed.stream()
          .map(item -> (Map<String, Object>) item)
          .toList();
    } catch (IOException exception) {
      throw new GatewayApiException(HttpStatus.BAD_GATEWAY, "Gateway received invalid JSON from a downstream service.");
    }
  }

  private AdminBusRequest normalizeBusRequest(AdminBusRequest request) {
    return new AdminBusRequest(
        request.busId(),
        request.plateNumber(),
        request.model(),
        request.capacity(),
        request.assignedRoute(),
        normalizeStatus(request.status())
    );
  }

  private String normalizeRole(String role) {
    return role == null || role.isBlank() ? "USER" : role.trim().toUpperCase(Locale.ROOT);
  }

  private String normalizeStatus(String status) {
    return status == null || status.isBlank() ? "ACTIVE" : status.trim().toUpperCase(Locale.ROOT);
  }

  private String routeLabel(Map<String, Object> booking) {
    String routeCode = valueOf(booking.get("routeCode"));
    if (!routeCode.isBlank() && !"null".equalsIgnoreCase(routeCode)) {
      return routeCode + " - " + valueOf(booking.get("fromStop")) + " to " + valueOf(booking.get("toStop"));
    }
    return valueOf(booking.get("fromStop")) + " to " + valueOf(booking.get("toStop"));
  }

  private String valueOf(Object value) {
    return value == null ? "" : String.valueOf(value);
  }

  private double asDouble(Object value) {
    if (value instanceof Number number) {
      return number.doubleValue();
    }
    if (value == null || String.valueOf(value).isBlank()) {
      return 0;
    }
    return Double.parseDouble(String.valueOf(value));
  }
}
