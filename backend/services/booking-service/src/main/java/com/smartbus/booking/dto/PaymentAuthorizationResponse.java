package com.smartbus.booking.dto;

public record PaymentAuthorizationResponse(
    boolean approved,
    String transactionId,
    String status,
    String reason
) {
}
