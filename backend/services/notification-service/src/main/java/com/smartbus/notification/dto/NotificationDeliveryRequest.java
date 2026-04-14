package com.smartbus.notification.dto;

import jakarta.validation.constraints.NotBlank;

public record NotificationDeliveryRequest(
    @NotBlank String bookingReference,
    @NotBlank String recipient,
    String routeCode,
    @NotBlank String status
) {
}
