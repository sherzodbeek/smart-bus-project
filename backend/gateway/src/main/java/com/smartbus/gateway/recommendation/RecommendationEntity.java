package com.smartbus.gateway.recommendation;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;

@Entity
@Table(name = "gateway_recommendations")
public class RecommendationEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "customer_email", nullable = false, length = 200)
  private String customerEmail;

  @Column(name = "route_code", nullable = false, length = 64)
  private String routeCode;

  @Column(name = "hybrid_score", nullable = false)
  private double hybridScore;

  @Column(name = "cf_score", nullable = false)
  private double cfScore;

  @Column(name = "cb_score", nullable = false)
  private double cbScore;

  @Column(name = "reason", nullable = false, length = 64)
  private String reason;

  @Column(name = "confidence", nullable = false, length = 32)
  private String confidence;

  @Column(name = "model_version", nullable = false, length = 32)
  private String modelVersion;

  @Column(name = "is_cold_start", nullable = false)
  private boolean coldStart;

  @Column(name = "created_at", nullable = false)
  private OffsetDateTime createdAt;

  @PrePersist
  void onCreate() {
    if (createdAt == null) {
      createdAt = OffsetDateTime.now();
    }
  }

  public Long getId() { return id; }
  public String getCustomerEmail() { return customerEmail; }
  public void setCustomerEmail(String customerEmail) { this.customerEmail = customerEmail; }
  public String getRouteCode() { return routeCode; }
  public void setRouteCode(String routeCode) { this.routeCode = routeCode; }
  public double getHybridScore() { return hybridScore; }
  public void setHybridScore(double hybridScore) { this.hybridScore = hybridScore; }
  public double getCfScore() { return cfScore; }
  public void setCfScore(double cfScore) { this.cfScore = cfScore; }
  public double getCbScore() { return cbScore; }
  public void setCbScore(double cbScore) { this.cbScore = cbScore; }
  public String getReason() { return reason; }
  public void setReason(String reason) { this.reason = reason; }
  public String getConfidence() { return confidence; }
  public void setConfidence(String confidence) { this.confidence = confidence; }
  public String getModelVersion() { return modelVersion; }
  public void setModelVersion(String modelVersion) { this.modelVersion = modelVersion; }
  public boolean isColdStart() { return coldStart; }
  public void setColdStart(boolean coldStart) { this.coldStart = coldStart; }
  public OffsetDateTime getCreatedAt() { return createdAt; }
  public void setCreatedAt(OffsetDateTime createdAt) { this.createdAt = createdAt; }
}
