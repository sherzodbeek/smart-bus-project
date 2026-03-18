package com.smartbus.booking.model;

import java.time.OffsetDateTime;

public record BookingProcessInstance(
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
    BookingLifecycleState currentState,
    String lastError,
    OffsetDateTime createdAt,
    OffsetDateTime updatedAt
) {
}
