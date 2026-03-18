package com.smartbus.gateway.dto;

import java.util.List;
import java.util.Map;

public record AdminReportsResponse(
    Map<String, Object> stats,
    List<Map<String, Object>> topRoutes
) {
}
