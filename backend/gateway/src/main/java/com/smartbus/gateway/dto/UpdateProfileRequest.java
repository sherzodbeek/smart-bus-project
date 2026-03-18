package com.smartbus.gateway.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public record UpdateProfileRequest(
    @NotBlank String fullName,
    @Email @NotBlank String email,
    String phone,
    String address
) {
}
