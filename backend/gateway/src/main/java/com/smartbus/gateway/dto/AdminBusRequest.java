package com.smartbus.gateway.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;

public record AdminBusRequest(
    @NotBlank String busId,
    @NotBlank String plateNumber,
    @NotBlank String model,
    @Min(1) int capacity,
    @NotBlank String assignedRoute,
    @NotBlank String status
) {
}
