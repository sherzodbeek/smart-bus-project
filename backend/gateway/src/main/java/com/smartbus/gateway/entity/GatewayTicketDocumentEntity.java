package com.smartbus.gateway.entity;

import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Lob;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;

@Entity
@Table(name = "gateway_ticket_documents")
public class GatewayTicketDocumentEntity {

  @EmbeddedId
  private GatewayTicketDocumentId id;

  @Column(name = "file_name", nullable = false, length = 255)
  private String fileName;

  @Column(name = "content_type", nullable = false, length = 128)
  private String contentType;

  @Lob
  @Column(name = "content", nullable = false)
  private byte[] content;

  @Column(name = "size_bytes", nullable = false)
  private long sizeBytes;

  @Column(name = "created_at", nullable = false)
  private OffsetDateTime createdAt;

  @PrePersist
  @PreUpdate
  void touch() {
    createdAt = OffsetDateTime.now();
  }

  public GatewayTicketDocumentId getId() { return id; }
  public void setId(GatewayTicketDocumentId id) { this.id = id; }
  public String getFileName() { return fileName; }
  public void setFileName(String fileName) { this.fileName = fileName; }
  public String getContentType() { return contentType; }
  public void setContentType(String contentType) { this.contentType = contentType; }
  public byte[] getContent() { return content; }
  public void setContent(byte[] content) { this.content = content; }
  public long getSizeBytes() { return sizeBytes; }
  public void setSizeBytes(long sizeBytes) { this.sizeBytes = sizeBytes; }
  public OffsetDateTime getCreatedAt() { return createdAt; }
  public void setCreatedAt(OffsetDateTime createdAt) { this.createdAt = createdAt; }
}
