package com.smartbus.gateway.dto;

public record AdminBusResponse(
    long id,
    String busId,
    String plateNumber,
    String model,
    int capacity,
    String assignedRoute,
    String status
) {
}
