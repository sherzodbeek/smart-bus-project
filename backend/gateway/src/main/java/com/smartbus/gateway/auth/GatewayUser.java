package com.smartbus.gateway.auth;

public record GatewayUser(
    Long id,
    String fullName,
    String email,
    String passwordHash,
    String role
) {
}
