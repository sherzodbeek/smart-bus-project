package com.smartbus.payment.dto;

import java.time.OffsetDateTime;

public record PaymentRecordResponse(
    String transactionId,
    String bookingReference,
    String customerEmail,
    double amount,
    String status,
    String reason,
    OffsetDateTime createdAt
) {
}
