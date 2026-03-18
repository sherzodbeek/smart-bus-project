package com.smartbus.gateway.dto;

import java.util.List;
import java.util.Map;

public record AdminTicketsResponse(
    Map<String, Object> stats,
    List<AdminTicketResponse> tickets,
    List<Map<String, Object>> passes
) {
}
