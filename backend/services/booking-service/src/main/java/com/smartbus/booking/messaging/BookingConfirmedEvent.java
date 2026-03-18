package com.smartbus.booking.messaging;

public record BookingConfirmedEvent(
    String schemaVersion,
    String bookingReference,
    String customerName,
    String customerEmail,
    String routeCode,
    String tripDate,
    String tripType,
    int passengers,
    double totalAmount,
    String paymentTransactionId,
    String status
) {
}
