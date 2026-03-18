package com.smartbus.schedule.dto;

public record ScheduleQuoteResponse(
    boolean tripAvailable,
    boolean returnTripAvailable,
    String routeCode,
    String departureTime,
    String arrivalTime,
    double unitPrice,
    int availableSeats
) {
}
