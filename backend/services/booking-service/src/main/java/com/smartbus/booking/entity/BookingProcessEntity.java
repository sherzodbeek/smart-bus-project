package com.smartbus.booking.entity;

import com.smartbus.booking.model.BookingLifecycleState;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;

@Entity
@Table(name = "booking_process_instances")
public class BookingProcessEntity {

  @Id
  @Column(name = "booking_reference", nullable = false, length = 32)
  private String bookingReference;

  @Column(name = "customer_name", nullable = false)
  private String customerName;

  @Column(name = "customer_email", nullable = false)
  private String customerEmail;

  @Column(name = "from_stop", nullable = false)
  private String fromStop;

  @Column(name = "to_stop", nullable = false)
  private String toStop;

  @Column(name = "trip_date", nullable = false, length = 32)
  private String tripDate;

  @Column(name = "trip_type", nullable = false, length = 32)
  private String tripType;

  @Column(name = "passengers", nullable = false)
  private int passengers;

  @Column(name = "route_code", length = 64)
  private String routeCode;

  @Column(name = "departure_time", length = 64)
  private String departureTime;

  @Column(name = "arrival_time", length = 64)
  private String arrivalTime;

  @Column(name = "total_amount")
  private Double totalAmount;

  @Column(name = "payment_transaction_id", length = 64)
  private String paymentTransactionId;

  @Column(name = "notification_id", length = 64)
  private String notificationId;

  @Enumerated(EnumType.STRING)
  @Column(name = "current_state", nullable = false, length = 64)
  private BookingLifecycleState currentState;

  @Column(name = "last_error", length = 512)
  private String lastError;

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

  public String getBookingReference() {
    return bookingReference;
  }

  public void setBookingReference(String bookingReference) {
    this.bookingReference = bookingReference;
  }

  public String getCustomerName() {
    return customerName;
  }

  public void setCustomerName(String customerName) {
    this.customerName = customerName;
  }

  public String getCustomerEmail() {
    return customerEmail;
  }

  public void setCustomerEmail(String customerEmail) {
    this.customerEmail = customerEmail;
  }

  public String getFromStop() {
    return fromStop;
  }

  public void setFromStop(String fromStop) {
    this.fromStop = fromStop;
  }

  public String getToStop() {
    return toStop;
  }

  public void setToStop(String toStop) {
    this.toStop = toStop;
  }

  public String getTripDate() {
    return tripDate;
  }

  public void setTripDate(String tripDate) {
    this.tripDate = tripDate;
  }

  public String getTripType() {
    return tripType;
  }

  public void setTripType(String tripType) {
    this.tripType = tripType;
  }

  public int getPassengers() {
    return passengers;
  }

  public void setPassengers(int passengers) {
    this.passengers = passengers;
  }

  public String getRouteCode() {
    return routeCode;
  }

  public void setRouteCode(String routeCode) {
    this.routeCode = routeCode;
  }

  public String getDepartureTime() {
    return departureTime;
  }

  public void setDepartureTime(String departureTime) {
    this.departureTime = departureTime;
  }

  public String getArrivalTime() {
    return arrivalTime;
  }

  public void setArrivalTime(String arrivalTime) {
    this.arrivalTime = arrivalTime;
  }

  public Double getTotalAmount() {
    return totalAmount;
  }

  public void setTotalAmount(Double totalAmount) {
    this.totalAmount = totalAmount;
  }

  public String getPaymentTransactionId() {
    return paymentTransactionId;
  }

  public void setPaymentTransactionId(String paymentTransactionId) {
    this.paymentTransactionId = paymentTransactionId;
  }

  public String getNotificationId() {
    return notificationId;
  }

  public void setNotificationId(String notificationId) {
    this.notificationId = notificationId;
  }

  public BookingLifecycleState getCurrentState() {
    return currentState;
  }

  public void setCurrentState(BookingLifecycleState currentState) {
    this.currentState = currentState;
  }

  public String getLastError() {
    return lastError;
  }

  public void setLastError(String lastError) {
    this.lastError = lastError;
  }

  public OffsetDateTime getCreatedAt() {
    return createdAt;
  }

  public void setCreatedAt(OffsetDateTime createdAt) {
    this.createdAt = createdAt;
  }

  public OffsetDateTime getUpdatedAt() {
    return updatedAt;
  }

  public void setUpdatedAt(OffsetDateTime updatedAt) {
    this.updatedAt = updatedAt;
  }
}
