package com.smartbus.gateway.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;

public record AdminRouteRequest(
    @NotBlank String routeCode,
    @NotBlank String fromStop,
    @NotBlank String toStop,
    @NotBlank String departureTime,
    @NotBlank String arrivalTime,
    @Positive double unitPrice,
    @Positive int seatsAvailable
) {
}
