package com.smartbus.schedule.dto;

import jakarta.validation.constraints.NotBlank;

public record LocationRequest(
    @NotBlank String name
) {
}
