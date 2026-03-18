package com.smartbus.gateway.dto;

public record AdminUserResponse(
    long id,
    String fullName,
    String email,
    String phone,
    String role,
    String status,
    String registeredAt
) {
}
