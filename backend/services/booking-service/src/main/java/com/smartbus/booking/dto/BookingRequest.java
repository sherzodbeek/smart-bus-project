package com.smartbus.booking.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record BookingRequest(
    @NotBlank String customerName,
    @NotBlank @Email String customerEmail,
    @NotBlank String fromStop,
    @NotBlank String toStop,
    @NotBlank @Pattern(regexp = "^\\d{4}-\\d{2}-\\d{2}$") String tripDate,
    @NotBlank @Pattern(regexp = "^(one-way|round-trip)$") String tripType,
    @Min(1) @Max(6) int passengers,
    @NotBlank String paymentMethodToken
) {
}
