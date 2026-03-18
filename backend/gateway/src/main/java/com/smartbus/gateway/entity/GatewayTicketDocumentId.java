package com.smartbus.gateway.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import java.io.Serializable;
import java.util.Objects;

@Embeddable
public class GatewayTicketDocumentId implements Serializable {

  @Column(name = "booking_reference", nullable = false, length = 64)
  private String bookingReference;

  @Column(name = "owner_email", nullable = false, length = 200)
  private String ownerEmail;

  public GatewayTicketDocumentId() {
  }

  public GatewayTicketDocumentId(String bookingReference, String ownerEmail) {
    this.bookingReference = bookingReference;
    this.ownerEmail = ownerEmail;
  }

  public String getBookingReference() { return bookingReference; }
  public void setBookingReference(String bookingReference) { this.bookingReference = bookingReference; }
  public String getOwnerEmail() { return ownerEmail; }
  public void setOwnerEmail(String ownerEmail) { this.ownerEmail = ownerEmail; }

  @Override
  public boolean equals(Object object) {
    if (this == object) {
      return true;
    }
    if (!(object instanceof GatewayTicketDocumentId that)) {
      return false;
    }
    return Objects.equals(bookingReference, that.bookingReference) && Objects.equals(ownerEmail, that.ownerEmail);
  }

  @Override
  public int hashCode() {
    return Objects.hash(bookingReference, ownerEmail);
  }
}
