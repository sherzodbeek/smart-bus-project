package com.smartbus.notification.mongo;

import java.time.Instant;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

@Document(collection = "notification_events")
public class NotificationEventDocument {

  @Id
  private String id;

  @Indexed
  private String bookingReference;

  private String recipient;
  private String routeCode;
  private String status;
  private Instant deliveredAt;

  public NotificationEventDocument() {
  }

  public NotificationEventDocument(
      String bookingReference,
      String recipient,
      String routeCode,
      String status,
      Instant deliveredAt
  ) {
    this.bookingReference = bookingReference;
    this.recipient = recipient;
    this.routeCode = routeCode;
    this.status = status;
    this.deliveredAt = deliveredAt;
  }

  public String getId() {
    return id;
  }

  public void setId(String id) {
    this.id = id;
  }

  public String getBookingReference() {
    return bookingReference;
  }

  public void setBookingReference(String bookingReference) {
    this.bookingReference = bookingReference;
  }

  public String getRecipient() {
    return recipient;
  }

  public void setRecipient(String recipient) {
    this.recipient = recipient;
  }

  public String getRouteCode() {
    return routeCode;
  }

  public void setRouteCode(String routeCode) {
    this.routeCode = routeCode;
  }

  public String getStatus() {
    return status;
  }

  public void setStatus(String status) {
    this.status = status;
  }

  public Instant getDeliveredAt() {
    return deliveredAt;
  }

  public void setDeliveredAt(Instant deliveredAt) {
    this.deliveredAt = deliveredAt;
  }
}
