package com.smartbus.booking.dto;

public record BookingProcessStateResponse(
    String bookingReference,
    String correlationKey,
    String currentState,
    String lastError
) {
}
