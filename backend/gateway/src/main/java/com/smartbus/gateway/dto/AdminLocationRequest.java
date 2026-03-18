package com.smartbus.gateway.dto;

import jakarta.validation.constraints.NotBlank;

public record AdminLocationRequest(
    @NotBlank String name
) {
}
