package com.smartbus.gateway.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;

@Entity
@Table(name = "gateway_settings")
public class GatewaySettingEntity {

  @Id
  @Column(name = "section", nullable = false, length = 64)
  private String section;

  @Column(name = "payload", nullable = false, columnDefinition = "text")
  private String payload;

  @Column(name = "updated_at", nullable = false)
  private OffsetDateTime updatedAt;

  @PrePersist
  @PreUpdate
  void touch() {
    updatedAt = OffsetDateTime.now();
  }

  public String getSection() { return section; }
  public void setSection(String section) { this.section = section; }
  public String getPayload() { return payload; }
  public void setPayload(String payload) { this.payload = payload; }
  public OffsetDateTime getUpdatedAt() { return updatedAt; }
  public void setUpdatedAt(OffsetDateTime updatedAt) { this.updatedAt = updatedAt; }
}
