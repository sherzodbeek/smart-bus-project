package com.smartbus.gateway.dto;

public record StoredTicketDocument(
    String bookingReference,
    String ownerEmail,
    String fileName,
    String contentType,
    byte[] content,
    long sizeBytes,
    String uploadedAt
) {
}
