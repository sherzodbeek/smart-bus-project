package com.smartbus.gateway.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;

@Entity
@Table(name = "gateway_users")
public class GatewayUserEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "full_name", nullable = false, length = 200)
  private String fullName;

  @Column(name = "email", nullable = false, unique = true, length = 200)
  private String email;

  @Column(name = "password_hash", nullable = false, length = 255)
  private String passwordHash;

  @Column(name = "role", nullable = false, length = 32)
  private String role;

  @Column(name = "phone", length = 64)
  private String phone;

  @Column(name = "address", length = 255)
  private String address;

  @Column(name = "language", nullable = false, length = 32)
  private String language = "English";

  @Column(name = "status", nullable = false, length = 32)
  private String status = "ACTIVE";

  @Column(name = "email_notifications", nullable = false)
  private boolean emailNotifications = true;

  @Column(name = "sms_alerts", nullable = false)
  private boolean smsAlerts = true;

  @Column(name = "push_notifications", nullable = false)
  private boolean pushNotifications;

  @Column(name = "created_at", nullable = false)
  private OffsetDateTime createdAt;

  @Column(name = "updated_at", nullable = false)
  private OffsetDateTime updatedAt;

  @PrePersist
  void onCreate() {
    OffsetDateTime now = OffsetDateTime.now();
    if (createdAt == null) {
      createdAt = now;
    }
    if (updatedAt == null) {
      updatedAt = now;
    }
  }

  @PreUpdate
  void onUpdate() {
    updatedAt = OffsetDateTime.now();
  }

  public Long getId() { return id; }
  public void setId(Long id) { this.id = id; }
  public String getFullName() { return fullName; }
  public void setFullName(String fullName) { this.fullName = fullName; }
  public String getEmail() { return email; }
  public void setEmail(String email) { this.email = email; }
  public String getPasswordHash() { return passwordHash; }
  public void setPasswordHash(String passwordHash) { this.passwordHash = passwordHash; }
  public String getRole() { return role; }
  public void setRole(String role) { this.role = role; }
  public String getPhone() { return phone; }
  public void setPhone(String phone) { this.phone = phone; }
  public String getAddress() { return address; }
  public void setAddress(String address) { this.address = address; }
  public String getLanguage() { return language; }
  public void setLanguage(String language) { this.language = language; }
  public String getStatus() { return status; }
  public void setStatus(String status) { this.status = status; }
  public boolean isEmailNotifications() { return emailNotifications; }
  public void setEmailNotifications(boolean emailNotifications) { this.emailNotifications = emailNotifications; }
  public boolean isSmsAlerts() { return smsAlerts; }
  public void setSmsAlerts(boolean smsAlerts) { this.smsAlerts = smsAlerts; }
  public boolean isPushNotifications() { return pushNotifications; }
  public void setPushNotifications(boolean pushNotifications) { this.pushNotifications = pushNotifications; }
  public OffsetDateTime getCreatedAt() { return createdAt; }
  public void setCreatedAt(OffsetDateTime createdAt) { this.createdAt = createdAt; }
  public OffsetDateTime getUpdatedAt() { return updatedAt; }
  public void setUpdatedAt(OffsetDateTime updatedAt) { this.updatedAt = updatedAt; }
}
