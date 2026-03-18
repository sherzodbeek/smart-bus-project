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
@Table(name = "gateway_buses")
public class GatewayBusEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "bus_id", nullable = false, unique = true, length = 64)
  private String busId;

  @Column(name = "plate_number", nullable = false, unique = true, length = 64)
  private String plateNumber;

  @Column(name = "model", nullable = false, length = 200)
  private String model;

  @Column(name = "capacity", nullable = false)
  private int capacity;

  @Column(name = "assigned_route", nullable = false, length = 200)
  private String assignedRoute;

  @Column(name = "status", nullable = false, length = 32)
  private String status;

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
  public String getBusId() { return busId; }
  public void setBusId(String busId) { this.busId = busId; }
  public String getPlateNumber() { return plateNumber; }
  public void setPlateNumber(String plateNumber) { this.plateNumber = plateNumber; }
  public String getModel() { return model; }
  public void setModel(String model) { this.model = model; }
  public int getCapacity() { return capacity; }
  public void setCapacity(int capacity) { this.capacity = capacity; }
  public String getAssignedRoute() { return assignedRoute; }
  public void setAssignedRoute(String assignedRoute) { this.assignedRoute = assignedRoute; }
  public String getStatus() { return status; }
  public void setStatus(String status) { this.status = status; }
  public OffsetDateTime getCreatedAt() { return createdAt; }
  public void setCreatedAt(OffsetDateTime createdAt) { this.createdAt = createdAt; }
  public OffsetDateTime getUpdatedAt() { return updatedAt; }
  public void setUpdatedAt(OffsetDateTime updatedAt) { this.updatedAt = updatedAt; }
}
