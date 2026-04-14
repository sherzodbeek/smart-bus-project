package com.smartbus.payment.messaging;

public record PaymentDeclinedEvent(
    String schemaVersion,
    String transactionId,
    String bookingReference,
    String customerEmail,
    double amount,
    String reason
) {
}
