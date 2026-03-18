package com.smartbus.payment.dto;

public record PaymentAuthorizationResponse(
    boolean approved,
    String transactionId,
    String status,
    String reason
) {
}
