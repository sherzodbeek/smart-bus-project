package com.smartbus.booking.dto;

public record BookingSummaryResponse(
    String bookingReference,
    String customerName,
    String customerEmail,
    String fromStop,
    String toStop,
    String tripDate,
    String tripType,
    int passengers,
    String routeCode,
    String departureTime,
    String arrivalTime,
    double totalAmount,
    String paymentTransactionId,
    String notificationId,
    String currentState,
    String lastError
) {
}
