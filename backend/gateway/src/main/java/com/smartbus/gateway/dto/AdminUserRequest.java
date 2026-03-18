package com.smartbus.gateway.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public record AdminUserRequest(
    @NotBlank String fullName,
    @Email @NotBlank String email,
    String phone,
    @NotBlank String role,
    String password,
    String status
) {
}
