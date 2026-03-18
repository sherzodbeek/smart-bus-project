package com.smartbus.gateway.dto;

import java.util.List;
import java.util.Map;

public record AdminDashboardResponse(
    Map<String, Object> stats,
    List<Map<String, Object>> recentTickets,
    List<Map<String, Object>> alerts
) {
}
