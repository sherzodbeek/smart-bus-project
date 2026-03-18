package com.smartbus.schedule.dto;

public record RouteDefinition(
    String routeCode,
    String fromStop,
    String toStop,
    String departureTime,
    String arrivalTime,
    double unitPrice,
    int seatsAvailable
) {
}
