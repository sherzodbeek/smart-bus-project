package com.smartbus.payment.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public record PaymentAuthorizationRequest(
    @NotBlank String bookingReference,
    @NotBlank @Email String customerEmail,
    @DecimalMin("0.01") double amount,
    @NotBlank String paymentMethodToken
) {
}
