package com.smartbus.gateway.auth;

public record AuthResponse(
    String token,
    String name,
    String email,
    String role
) {
}
