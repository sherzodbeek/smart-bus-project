package com.smartbus.notification.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;

@Entity
@Table(
    name = "notification_deliveries",
    indexes = {
        @Index(name = "idx_notification_deliveries_booking_reference", columnList = "booking_reference")
    }
)
public class NotificationDeliveryEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  @Column(name = "id")
  private Long id;

  @Column(name = "booking_reference", nullable = false, length = 32)
  private String bookingReference;

  @Column(name = "recipient", nullable = false)
  private String recipient;

  @Column(name = "route_code", nullable = false, length = 64)
  private String routeCode;

  @Column(name = "status", nullable = false, length = 32)
  private String status;

  @Column(name = "delivered_at", nullable = false)
  private OffsetDateTime deliveredAt;

  @PrePersist
  void onCreate() {
    if (deliveredAt == null) {
      deliveredAt = OffsetDateTime.now();
    }
  }

  public Long getId() { return id; }

  public String getBookingReference() { return bookingReference; }
  public void setBookingReference(String bookingReference) { this.bookingReference = bookingReference; }

  public String getRecipient() { return recipient; }
  public void setRecipient(String recipient) { this.recipient = recipient; }

  public String getRouteCode() { return routeCode; }
  public void setRouteCode(String routeCode) { this.routeCode = routeCode; }

  public String getStatus() { return status; }
  public void setStatus(String status) { this.status = status; }

  public OffsetDateTime getDeliveredAt() { return deliveredAt; }
  public void setDeliveredAt(OffsetDateTime deliveredAt) { this.deliveredAt = deliveredAt; }
}
