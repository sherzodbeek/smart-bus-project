package com.smartbus.gateway.dto;

public record AdminTicketResponse(
    String bookingReference,
    String customerName,
    String customerEmail,
    String routeCode,
    String fromStop,
    String toStop,
    String tripDate,
    String departureTime,
    double totalAmount,
    String currentState
) {
}
