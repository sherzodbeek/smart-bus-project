package com.smartbus.payment.dto;

import jakarta.validation.constraints.NotBlank;

public record PaymentStatusUpdateRequest(
    @NotBlank String status
) {
}
