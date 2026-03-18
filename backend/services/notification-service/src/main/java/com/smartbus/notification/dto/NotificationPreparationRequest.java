package com.smartbus.notification.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public record NotificationPreparationRequest(
    @NotBlank String bookingReference,
    @NotBlank String customerName,
    @NotBlank @Email String customerEmail,
    @NotBlank String routeCode
) {
}
