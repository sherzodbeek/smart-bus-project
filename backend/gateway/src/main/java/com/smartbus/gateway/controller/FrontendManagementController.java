package com.smartbus.gateway.controller;

import com.smartbus.gateway.dto.AdminBusRequest;
import com.smartbus.gateway.dto.AdminBusResponse;
import com.smartbus.gateway.dto.AdminDashboardResponse;
import com.smartbus.gateway.dto.AdminLocationRequest;
import com.smartbus.gateway.dto.AdminLocationResponse;
import com.smartbus.gateway.dto.AdminReportsResponse;
import com.smartbus.gateway.dto.AdminRouteRequest;
import com.smartbus.gateway.dto.AdminSettingsResponse;
import com.smartbus.gateway.dto.AdminTicketsResponse;
import com.smartbus.gateway.dto.AdminUserRequest;
import com.smartbus.gateway.dto.AdminUserResponse;
import com.smartbus.gateway.dto.ChangePasswordRequest;
import com.smartbus.gateway.dto.ContactMessageRequest;
import com.smartbus.gateway.dto.GeneratedTicketPdf;
import com.smartbus.gateway.dto.ProfileResponse;
import com.smartbus.gateway.dto.SettingsSectionRequest;
import com.smartbus.gateway.dto.StoredTicketDocument;
import com.smartbus.gateway.dto.TicketDocumentMetadataResponse;
import com.smartbus.gateway.dto.UpdatePreferencesRequest;
import com.smartbus.gateway.dto.UpdateProfileRequest;
import com.smartbus.gateway.service.FrontendManagementService;
import jakarta.validation.Valid;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/v1/frontend")
public class FrontendManagementController {

  private static final Logger log = LoggerFactory.getLogger(FrontendManagementController.class);

  private final FrontendManagementService service;

  public FrontendManagementController(FrontendManagementService service) {
    this.service = service;
  }

  @PostMapping("/contact")
  @ResponseStatus(HttpStatus.CREATED)
  public void contact(@Valid @RequestBody ContactMessageRequest request) {
    log.info("frontendContactRequest email={} subject={}", request.email(), request.subject());
    service.submitContactMessage(request);
  }

  @GetMapping("/profile")
  public ProfileResponse profile(Authentication authentication) {
    log.info("frontendProfileRequest user={}", authentication.getName());
    return service.loadProfile(authentication.getName());
  }

  @PutMapping("/profile")
  public ProfileResponse updateProfile(@Valid @RequestBody UpdateProfileRequest request, Authentication authentication) {
    log.info("frontendProfileUpdateRequest user={} email={}", authentication.getName(), request.email());
    return service.updateProfile(authentication.getName(), request);
  }

  @PostMapping("/profile/password")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void changePassword(@Valid @RequestBody ChangePasswordRequest request, Authentication authentication) {
    log.info("frontendPasswordChangeRequest user={}", authentication.getName());
    service.changePassword(authentication.getName(), request);
  }

  @PutMapping("/profile/preferences")
  public ProfileResponse updatePreferences(@RequestBody UpdatePreferencesRequest request, Authentication authentication) {
    log.info("frontendPreferencesUpdateRequest user={}", authentication.getName());
    return service.updatePreferences(authentication.getName(), request);
  }

  @GetMapping("/admin/dashboard")
  public AdminDashboardResponse dashboard() {
    log.info("frontendAdminDashboardRequest");
    return service.loadDashboard();
  }

  @GetMapping("/admin/tickets")
  public AdminTicketsResponse tickets() {
    log.info("frontendAdminTicketsRequest");
    return service.loadTickets();
  }

  @GetMapping("/admin/reports")
  public AdminReportsResponse reports() {
    log.info("frontendAdminReportsRequest");
    return service.loadReports();
  }

  @GetMapping("/admin/locations")
  public List<AdminLocationResponse> locations() {
    log.info("frontendAdminLocationsRequest");
    return service.loadLocations();
  }

  @PostMapping("/admin/locations")
  @ResponseStatus(HttpStatus.CREATED)
  public AdminLocationResponse createLocation(@Valid @RequestBody AdminLocationRequest request) {
    log.info("frontendAdminCreateLocationRequest name={}", request.name());
    return service.createLocation(request);
  }

  @PutMapping("/admin/locations/{id}")
  public AdminLocationResponse updateLocation(@PathVariable long id, @Valid @RequestBody AdminLocationRequest request) {
    log.info("frontendAdminUpdateLocationRequest id={} name={}", id, request.name());
    return service.updateLocation(id, request);
  }

  @DeleteMapping("/admin/locations/{id}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void deleteLocation(@PathVariable long id) {
    log.info("frontendAdminDeleteLocationRequest id={}", id);
    service.deleteLocation(id);
  }

  @PostMapping("/admin/routes")
  @ResponseStatus(HttpStatus.CREATED)
  public Map<String, Object> createRoute(@Valid @RequestBody AdminRouteRequest request) {
    log.info("frontendAdminCreateRouteRequest routeCode={} fromStop={} toStop={}", request.routeCode(), request.fromStop(), request.toStop());
    return service.createRoute(request);
  }

  @PutMapping("/admin/routes/{routeCode}")
  public Map<String, Object> updateRoute(@PathVariable String routeCode, @Valid @RequestBody AdminRouteRequest request) {
    log.info("frontendAdminUpdateRouteRequest routeCode={} nextRouteCode={}", routeCode, request.routeCode());
    return service.updateRoute(routeCode, request);
  }

  @DeleteMapping("/admin/routes/{routeCode}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void deleteRoute(@PathVariable String routeCode) {
    log.info("frontendAdminDeleteRouteRequest routeCode={}", routeCode);
    service.deleteRoute(routeCode);
  }

  @GetMapping("/admin/users")
  public List<AdminUserResponse> users() {
    log.info("frontendAdminUsersRequest");
    return service.listUsers();
  }

  @PostMapping("/admin/users")
  @ResponseStatus(HttpStatus.CREATED)
  public AdminUserResponse createUser(@Valid @RequestBody AdminUserRequest request) {
    log.info("frontendAdminCreateUserRequest email={} role={}", request.email(), request.role());
    return service.createUser(request);
  }

  @PutMapping("/admin/users/{id}")
  public AdminUserResponse updateUser(@PathVariable long id, @Valid @RequestBody AdminUserRequest request) {
    log.info("frontendAdminUpdateUserRequest id={} email={} role={}", id, request.email(), request.role());
    return service.updateUser(id, request);
  }

  @DeleteMapping("/admin/users/{id}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void deleteUser(@PathVariable long id, Authentication authentication) {
    log.info("frontendAdminDeleteUserRequest id={} actor={}", id, authentication.getName());
    service.deleteUser(id, authentication.getName());
  }

  @GetMapping("/admin/buses")
  public List<AdminBusResponse> buses() {
    log.info("frontendAdminBusesRequest");
    return service.listBuses();
  }

  @PostMapping("/admin/buses")
  @ResponseStatus(HttpStatus.CREATED)
  public AdminBusResponse createBus(@Valid @RequestBody AdminBusRequest request) {
    log.info("frontendAdminCreateBusRequest busId={} plateNumber={}", request.busId(), request.plateNumber());
    return service.createBus(request);
  }

  @PutMapping("/admin/buses/{id}")
  public AdminBusResponse updateBus(@PathVariable long id, @Valid @RequestBody AdminBusRequest request) {
    log.info("frontendAdminUpdateBusRequest id={} busId={} plateNumber={}", id, request.busId(), request.plateNumber());
    return service.updateBus(id, request);
  }

  @DeleteMapping("/admin/buses/{id}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void deleteBus(@PathVariable long id) {
    log.info("frontendAdminDeleteBusRequest id={}", id);
    service.deleteBus(id);
  }

  @GetMapping("/admin/settings")
  public AdminSettingsResponse settings() {
    log.info("frontendAdminSettingsRequest");
    return service.loadSettings();
  }

  @PutMapping("/admin/settings/{section}")
  public AdminSettingsResponse updateSettings(@PathVariable String section, @Valid @RequestBody SettingsSectionRequest request) {
    log.info("frontendAdminSettingsUpdateRequest section={} keys={}", section, request.values().keySet());
    return service.updateSettings(section, request.values());
  }

  @GetMapping("/ticket-documents")
  public List<TicketDocumentMetadataResponse> ticketDocuments(Authentication authentication) {
    log.info("frontendTicketDocumentsRequest user={}", authentication.getName());
    return service.listTicketDocuments(authentication.getName());
  }

  @PostMapping(value = "/bookings/{bookingReference}/ticket-document", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
  @ResponseStatus(HttpStatus.CREATED)
  public TicketDocumentMetadataResponse uploadTicketDocument(
      @PathVariable String bookingReference,
      Authentication authentication,
      @org.springframework.web.bind.annotation.RequestPart("file") MultipartFile file
  ) throws java.io.IOException {
    log.info("frontendTicketDocumentUploadRequest user={} bookingReference={} fileName={}", authentication.getName(), bookingReference, file.getOriginalFilename());
    return service.uploadTicketDocument(
        authentication.getName(),
        bookingReference,
        file.getOriginalFilename() == null ? bookingReference + ".pdf" : file.getOriginalFilename(),
        file.getContentType(),
        file.getBytes()
    );
  }

  @GetMapping("/bookings/{bookingReference}/ticket-document")
  public ResponseEntity<byte[]> downloadTicketDocument(@PathVariable String bookingReference, Authentication authentication) {
    log.info("frontendTicketDocumentDownloadRequest user={} bookingReference={}", authentication.getName(), bookingReference);
    StoredTicketDocument document = service.loadTicketDocument(authentication.getName(), bookingReference);
    return ResponseEntity.ok()
        .contentType(MediaType.parseMediaType(document.contentType()))
        .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + document.fileName() + "\"")
        .body(document.content());
  }

  @GetMapping("/bookings/{bookingReference}/ticket-pdf")
  public ResponseEntity<byte[]> downloadTicketPdf(@PathVariable String bookingReference, Authentication authentication) {
    log.info("frontendTicketPdfDownloadRequest user={} bookingReference={}", authentication.getName(), bookingReference);
    GeneratedTicketPdf ticketPdf = service.generateTicketPdf(authentication.getName(), bookingReference);
    return ResponseEntity.ok()
        .contentType(MediaType.APPLICATION_PDF)
        .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + ticketPdf.fileName() + "\"")
        .body(ticketPdf.content());
  }
}
