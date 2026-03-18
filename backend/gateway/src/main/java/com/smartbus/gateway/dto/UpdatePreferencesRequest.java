package com.smartbus.gateway.dto;

public record UpdatePreferencesRequest(
    String language,
    boolean emailNotifications,
    boolean smsAlerts,
    boolean pushNotifications
) {
}
