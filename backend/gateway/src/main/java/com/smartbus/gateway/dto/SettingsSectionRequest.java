package com.smartbus.gateway.dto;

import jakarta.validation.constraints.NotNull;
import java.util.Map;

public record SettingsSectionRequest(
    @NotNull Map<String, Object> values
) {
}
