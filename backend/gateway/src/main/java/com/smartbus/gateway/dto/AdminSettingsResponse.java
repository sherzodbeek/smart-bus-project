package com.smartbus.gateway.dto;

import java.util.Map;

public record AdminSettingsResponse(
    Map<String, Object> general,
    Map<String, Object> ticket,
    Map<String, Object> notification,
    Map<String, Object> security
) {
}
