package com.smartbus.payment.model;

import java.time.OffsetDateTime;

public record PaymentRecord(
    String transactionId,
    String bookingReference,
    String customerEmail,
    double amount,
    String status,
    String reason,
    OffsetDateTime createdAt
) {
}
