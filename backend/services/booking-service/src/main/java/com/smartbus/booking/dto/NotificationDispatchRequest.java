package com.smartbus.booking.dto;

import jakarta.validation.constraints.NotBlank;

public record NotificationDispatchRequest(
    @NotBlank String bookingReference,
    @NotBlank String recipient,
    @NotBlank String message
) {
}
