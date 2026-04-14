package com.smartbus.payment.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;

@Entity
@Table(
    name = "payment_records",
    indexes = {
        @Index(name = "idx_payment_records_booking_reference", columnList = "booking_reference"),
        @Index(name = "idx_payment_records_customer_email", columnList = "customer_email")
    }
)
public class PaymentRecordEntity {

  @Id
  @Column(name = "transaction_id", nullable = false, length = 64)
  private String transactionId;

  @Column(name = "booking_reference", nullable = false, length = 32)
  private String bookingReference;

  @Column(name = "customer_email", nullable = false)
  private String customerEmail;

  @Column(name = "amount", nullable = false)
  private double amount;

  @Column(name = "status", nullable = false, length = 32)
  private String status;

  @Column(name = "reason", length = 256)
  private String reason;

  @Column(name = "created_at", nullable = false)
  private OffsetDateTime createdAt;

  @PrePersist
  void onCreate() {
    if (createdAt == null) {
      createdAt = OffsetDateTime.now();
    }
  }

  public String getTransactionId() { return transactionId; }
  public void setTransactionId(String transactionId) { this.transactionId = transactionId; }

  public String getBookingReference() { return bookingReference; }
  public void setBookingReference(String bookingReference) { this.bookingReference = bookingReference; }

  public String getCustomerEmail() { return customerEmail; }
  public void setCustomerEmail(String customerEmail) { this.customerEmail = customerEmail; }

  public double getAmount() { return amount; }
  public void setAmount(double amount) { this.amount = amount; }

  public String getStatus() { return status; }
  public void setStatus(String status) { this.status = status; }

  public String getReason() { return reason; }
  public void setReason(String reason) { this.reason = reason; }

  public OffsetDateTime getCreatedAt() { return createdAt; }
  public void setCreatedAt(OffsetDateTime createdAt) { this.createdAt = createdAt; }
}
