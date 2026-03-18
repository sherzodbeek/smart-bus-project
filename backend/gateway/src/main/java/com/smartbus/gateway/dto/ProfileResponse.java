package com.smartbus.gateway.dto;

public record ProfileResponse(
    String name,
    String email,
    String phone,
    String address,
    String language,
    boolean emailNotifications,
    boolean smsAlerts,
    boolean pushNotifications,
    String role,
    String token
) {
}
