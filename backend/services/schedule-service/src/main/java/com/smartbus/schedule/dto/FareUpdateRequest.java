package com.smartbus.schedule.dto;

import jakarta.validation.constraints.DecimalMin;

public record FareUpdateRequest(
    @DecimalMin("0.01") double unitPrice
) {
}
