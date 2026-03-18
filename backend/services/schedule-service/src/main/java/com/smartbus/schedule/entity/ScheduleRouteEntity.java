package com.smartbus.schedule.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;

@Entity
@Table(name = "schedule_routes")
public class ScheduleRouteEntity {

  @Id
  @Column(name = "route_code", nullable = false, length = 64)
  private String routeCode;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "from_location_id", nullable = false)
  private ScheduleLocationEntity fromLocation;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "to_location_id", nullable = false)
  private ScheduleLocationEntity toLocation;

  @Column(name = "departure_time", nullable = false, length = 64)
  private String departureTime;

  @Column(name = "arrival_time", nullable = false, length = 64)
  private String arrivalTime;

  @Column(name = "unit_price", nullable = false)
  private double unitPrice;

  @Column(name = "seats_available", nullable = false)
  private int seatsAvailable;

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

  public String getRouteCode() {
    return routeCode;
  }

  public void setRouteCode(String routeCode) {
    this.routeCode = routeCode;
  }

  public ScheduleLocationEntity getFromLocation() {
    return fromLocation;
  }

  public void setFromLocation(ScheduleLocationEntity fromLocation) {
    this.fromLocation = fromLocation;
  }

  public ScheduleLocationEntity getToLocation() {
    return toLocation;
  }

  public void setToLocation(ScheduleLocationEntity toLocation) {
    this.toLocation = toLocation;
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

  public double getUnitPrice() {
    return unitPrice;
  }

  public void setUnitPrice(double unitPrice) {
    this.unitPrice = unitPrice;
  }

  public int getSeatsAvailable() {
    return seatsAvailable;
  }

  public void setSeatsAvailable(int seatsAvailable) {
    this.seatsAvailable = seatsAvailable;
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
