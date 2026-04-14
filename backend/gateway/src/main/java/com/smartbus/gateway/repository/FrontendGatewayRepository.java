package com.smartbus.gateway.repository;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartbus.gateway.dto.AdminBusRequest;
import com.smartbus.gateway.dto.AdminBusResponse;
import com.smartbus.gateway.dto.AdminSettingsResponse;
import com.smartbus.gateway.dto.AdminUserResponse;
import com.smartbus.gateway.dto.ContactMessageRequest;
import com.smartbus.gateway.dto.ProfileResponse;
import com.smartbus.gateway.dto.StoredTicketDocument;
import com.smartbus.gateway.dto.TicketDocumentMetadataResponse;
import com.smartbus.gateway.dto.UpdatePreferencesRequest;
import com.smartbus.gateway.dto.UpdateProfileRequest;
import com.smartbus.gateway.entity.GatewayBusEntity;
import com.smartbus.gateway.entity.GatewayContactMessageEntity;
import com.smartbus.gateway.entity.GatewaySettingEntity;
import com.smartbus.gateway.entity.GatewayTicketDocumentEntity;
import com.smartbus.gateway.entity.GatewayTicketDocumentId;
import com.smartbus.gateway.entity.GatewayUserEntity;
import com.smartbus.gateway.exception.GatewayApiException;
import com.smartbus.gateway.util.HtmlSanitizer;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Repository;

@Repository
public class FrontendGatewayRepository {

  private final GatewayUserEntityRepository userRepository;
  private final GatewayBusEntityRepository busRepository;
  private final GatewayContactMessageEntityRepository contactMessageRepository;
  private final GatewaySettingEntityRepository settingRepository;
  private final GatewayTicketDocumentEntityRepository ticketDocumentRepository;
  private final ObjectMapper objectMapper;
  private final PasswordEncoder passwordEncoder;

  public FrontendGatewayRepository(
      GatewayUserEntityRepository userRepository,
      GatewayBusEntityRepository busRepository,
      GatewayContactMessageEntityRepository contactMessageRepository,
      GatewaySettingEntityRepository settingRepository,
      GatewayTicketDocumentEntityRepository ticketDocumentRepository,
      ObjectMapper objectMapper,
      PasswordEncoder passwordEncoder
  ) {
    this.userRepository = userRepository;
    this.busRepository = busRepository;
    this.contactMessageRepository = contactMessageRepository;
    this.settingRepository = settingRepository;
    this.ticketDocumentRepository = ticketDocumentRepository;
    this.objectMapper = objectMapper;
    this.passwordEncoder = passwordEncoder;
  }

  public void saveContactMessage(ContactMessageRequest request) {
    GatewayContactMessageEntity entity = new GatewayContactMessageEntity();
    entity.setFullName(HtmlSanitizer.strip(request.name()));
    entity.setEmail(request.email().trim().toLowerCase());
    entity.setSubject(HtmlSanitizer.strip(request.subject()));
    entity.setMessage(HtmlSanitizer.strip(request.message()));
    contactMessageRepository.save(entity);
  }

  public ProfileResponse loadProfile(String email) {
    return toProfileResponse(userRepository.findByEmailIgnoreCase(email)
        .orElseThrow(() -> new GatewayApiException(HttpStatus.NOT_FOUND, "User profile was not found.")));
  }

  public ProfileResponse updateProfile(String currentEmail, UpdateProfileRequest request) {
    GatewayUserEntity entity = userRepository.findByEmailIgnoreCase(currentEmail)
        .orElseThrow(() -> new GatewayApiException(HttpStatus.NOT_FOUND, "User profile was not found."));
    if (userRepository.existsByEmailIgnoreCaseAndIdNot(request.email().trim(), entity.getId())) {
      throw new GatewayApiException(HttpStatus.CONFLICT, "Another account already uses that email.");
    }
    entity.setFullName(HtmlSanitizer.strip(request.fullName()));
    entity.setEmail(request.email().trim().toLowerCase());
    entity.setPhone(normalize(request.phone()));
    entity.setAddress(HtmlSanitizer.strip(request.address()));
    return toProfileResponse(userRepository.save(entity));
  }

  public void changePassword(String email, String currentPassword, String newPassword) {
    GatewayUserEntity entity = userRepository.findByEmailIgnoreCase(email)
        .orElseThrow(() -> new GatewayApiException(HttpStatus.NOT_FOUND, "User profile was not found."));
    if (!passwordEncoder.matches(currentPassword, entity.getPasswordHash())) {
      throw new GatewayApiException(HttpStatus.BAD_REQUEST, "Current password is incorrect.");
    }
    entity.setPasswordHash(passwordEncoder.encode(newPassword));
    userRepository.save(entity);
  }

  public ProfileResponse updatePreferences(String email, UpdatePreferencesRequest request) {
    GatewayUserEntity entity = userRepository.findByEmailIgnoreCase(email)
        .orElseThrow(() -> new GatewayApiException(HttpStatus.NOT_FOUND, "User profile was not found."));
    entity.setLanguage(blankOrDefault(request.language(), "English"));
    entity.setEmailNotifications(request.emailNotifications());
    entity.setSmsAlerts(request.smsAlerts());
    entity.setPushNotifications(request.pushNotifications());
    return toProfileResponse(userRepository.save(entity));
  }

  public List<AdminUserResponse> listUsers() {
    return userRepository.findAllByOrderByCreatedAtDesc().stream().map(this::toAdminUserResponse).toList();
  }

  public AdminUserResponse createUser(
      String fullName,
      String email,
      String phone,
      String role,
      String password,
      String status
  ) {
    GatewayUserEntity entity = new GatewayUserEntity();
    entity.setFullName(HtmlSanitizer.strip(fullName));
    entity.setEmail(email.trim().toLowerCase());
    entity.setPhone(normalize(phone));
    entity.setPasswordHash(passwordEncoder.encode(password));
    entity.setRole(role.toUpperCase());
    entity.setStatus(blankOrDefault(status, "ACTIVE").toUpperCase());
    return toAdminUserResponse(userRepository.save(entity));
  }

  public AdminUserResponse updateUser(
      long id,
      String fullName,
      String email,
      String phone,
      String role,
      String password,
      String status
  ) {
    GatewayUserEntity entity = userRepository.findById(id)
        .orElseThrow(() -> new GatewayApiException(HttpStatus.NOT_FOUND, "User was not found."));
    if (userRepository.existsByEmailIgnoreCaseAndIdNot(email.trim(), id)) {
      throw new GatewayApiException(HttpStatus.CONFLICT, "Another account already uses that email.");
    }
    entity.setFullName(HtmlSanitizer.strip(fullName));
    entity.setEmail(email.trim().toLowerCase());
    entity.setPhone(normalize(phone));
    entity.setRole(role.toUpperCase());
    entity.setStatus(blankOrDefault(status, "ACTIVE").toUpperCase());
    if (password != null && !password.isBlank()) {
      entity.setPasswordHash(passwordEncoder.encode(password));
    }
    return toAdminUserResponse(userRepository.save(entity));
  }

  public void deleteUser(long id) {
    userRepository.deleteById(id);
  }

  public Optional<AdminUserResponse> findUserByEmail(String email) {
    return userRepository.findByEmailIgnoreCase(email).map(this::toAdminUserResponse);
  }

  public List<AdminBusResponse> listBuses() {
    return busRepository.findAllByOrderByBusIdAsc().stream().map(this::toAdminBusResponse).toList();
  }

  public AdminBusResponse createBus(AdminBusRequest request) {
    GatewayBusEntity entity = new GatewayBusEntity();
    applyBus(entity, request);
    return toAdminBusResponse(busRepository.save(entity));
  }

  public AdminBusResponse updateBus(long id, AdminBusRequest request) {
    GatewayBusEntity entity = busRepository.findById(id)
        .orElseThrow(() -> new GatewayApiException(HttpStatus.NOT_FOUND, "Bus was not found."));
    applyBus(entity, request);
    return toAdminBusResponse(busRepository.save(entity));
  }

  public void deleteBus(long id) {
    busRepository.deleteById(id);
  }

  public long countActiveBuses() {
    return busRepository.countByStatusIgnoreCase("ACTIVE");
  }

  public List<Map<String, Object>> maintenanceAlerts() {
    return busRepository.findTop5ByStatusNotIgnoreCaseOrderByUpdatedAtDesc("ACTIVE").stream()
        .map(bus -> Map.<String, Object>of(
            "severity", "warning",
            "title", "Bus " + bus.getBusId() + " is " + bus.getStatus().toLowerCase(),
            "detail", "Assigned route: " + bus.getAssignedRoute()
        ))
        .toList();
  }

  public AdminSettingsResponse loadSettings() {
    return new AdminSettingsResponse(
        loadSettingsSection("general"),
        loadSettingsSection("ticket"),
        loadSettingsSection("notification"),
        loadSettingsSection("security")
    );
  }

  public AdminSettingsResponse updateSettingsSection(String section, Map<String, Object> values) {
    String normalizedSection = section.toLowerCase();
    if (!List.of("general", "ticket", "notification", "security").contains(normalizedSection)) {
      throw new GatewayApiException(HttpStatus.BAD_REQUEST, "Unknown settings section: " + section);
    }
    GatewaySettingEntity entity = settingRepository.findById(normalizedSection).orElseGet(GatewaySettingEntity::new);
    entity.setSection(normalizedSection);
    entity.setPayload(writeJson(values));
    settingRepository.save(entity);
    return loadSettings();
  }

  public List<TicketDocumentMetadataResponse> listTicketDocuments(String ownerEmail) {
    return ticketDocumentRepository.findByIdOwnerEmailIgnoreCaseOrderByCreatedAtDesc(ownerEmail).stream()
        .map(this::toTicketDocumentMetadata)
        .toList();
  }

  public TicketDocumentMetadataResponse storeTicketDocument(
      String ownerEmail,
      String bookingReference,
      String fileName,
      String contentType,
      byte[] content
  ) {
    GatewayTicketDocumentEntity entity = new GatewayTicketDocumentEntity();
    entity.setId(new GatewayTicketDocumentId(bookingReference, ownerEmail.toLowerCase()));
    entity.setFileName(fileName);
    entity.setContentType(contentType);
    entity.setContent(content);
    entity.setSizeBytes(content.length);
    return toTicketDocumentMetadata(ticketDocumentRepository.save(entity));
  }

  public Optional<TicketDocumentMetadataResponse> findTicketDocumentMetadata(String ownerEmail, String bookingReference) {
    return ticketDocumentRepository.findByIdOwnerEmailIgnoreCaseAndIdBookingReference(ownerEmail, bookingReference)
        .map(this::toTicketDocumentMetadata);
  }

  public Optional<StoredTicketDocument> findStoredTicketDocument(String ownerEmail, String bookingReference) {
    return ticketDocumentRepository.findByIdOwnerEmailIgnoreCaseAndIdBookingReference(ownerEmail, bookingReference)
        .map(entity -> new StoredTicketDocument(
            entity.getId().getBookingReference(),
            entity.getId().getOwnerEmail(),
            entity.getFileName(),
            entity.getContentType(),
            entity.getContent(),
            entity.getSizeBytes(),
            entity.getCreatedAt().toString()
        ));
  }

  private void applyBus(GatewayBusEntity entity, AdminBusRequest request) {
    entity.setBusId(request.busId().trim());
    entity.setPlateNumber(request.plateNumber().trim().toUpperCase());
    entity.setModel(request.model().trim());
    entity.setCapacity(request.capacity());
    entity.setAssignedRoute(request.assignedRoute().trim());
    entity.setStatus(request.status().trim().toUpperCase());
  }

  private ProfileResponse toProfileResponse(GatewayUserEntity entity) {
    return new ProfileResponse(
        entity.getFullName(),
        entity.getEmail(),
        entity.getPhone(),
        entity.getAddress(),
        entity.getLanguage(),
        entity.isEmailNotifications(),
        entity.isSmsAlerts(),
        entity.isPushNotifications(),
        entity.getRole().toLowerCase(),
        null
    );
  }

  private AdminUserResponse toAdminUserResponse(GatewayUserEntity entity) {
    return new AdminUserResponse(
        entity.getId(),
        entity.getFullName(),
        entity.getEmail(),
        entity.getPhone() == null ? "" : entity.getPhone(),
        entity.getRole(),
        entity.getStatus(),
        entity.getCreatedAt().toLocalDate().toString()
    );
  }

  private AdminBusResponse toAdminBusResponse(GatewayBusEntity entity) {
    return new AdminBusResponse(
        entity.getId(),
        entity.getBusId(),
        entity.getPlateNumber(),
        entity.getModel(),
        entity.getCapacity(),
        entity.getAssignedRoute(),
        entity.getStatus()
    );
  }

  private TicketDocumentMetadataResponse toTicketDocumentMetadata(GatewayTicketDocumentEntity entity) {
    return new TicketDocumentMetadataResponse(
        entity.getId().getBookingReference(),
        entity.getFileName(),
        entity.getContentType(),
        entity.getSizeBytes(),
        entity.getCreatedAt().toString()
    );
  }

  private Map<String, Object> loadSettingsSection(String section) {
    return settingRepository.findById(section)
        .map(GatewaySettingEntity::getPayload)
        .map(this::readJson)
        .orElse(Map.of());
  }

  private String blankOrDefault(String value, String defaultValue) {
    return value == null || value.isBlank() ? defaultValue : value.trim();
  }

  private String normalize(String value) {
    return value == null || value.isBlank() ? null : value.trim();
  }

  private Map<String, Object> readJson(String payload) {
    try {
      return objectMapper.readValue(payload, new TypeReference<LinkedHashMap<String, Object>>() { });
    } catch (JsonProcessingException exception) {
      throw new GatewayApiException(HttpStatus.INTERNAL_SERVER_ERROR, "Stored settings payload is invalid.");
    }
  }

  private String writeJson(Map<String, Object> payload) {
    try {
      return objectMapper.writeValueAsString(payload);
    } catch (JsonProcessingException exception) {
      throw new GatewayApiException(HttpStatus.INTERNAL_SERVER_ERROR, "Unable to store settings payload.");
    }
  }
}
