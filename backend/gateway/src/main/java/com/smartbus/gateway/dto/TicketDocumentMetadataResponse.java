package com.smartbus.gateway.dto;

public record TicketDocumentMetadataResponse(
    String bookingReference,
    String fileName,
    String contentType,
    long sizeBytes,
    String uploadedAt
) {
}
