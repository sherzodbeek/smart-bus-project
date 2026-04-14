package com.smartbus.gateway.dto;

/**
 * Aggregated, externally-facing booking document produced by the data-transformation
 * endpoint.  Field names are deliberately different from the internal
 * {@code BookingSummaryResponse} to demonstrate JSON-to-JSON restructuring:
 * <ul>
 *   <li>{@code customerName}  → {@code passengerName}</li>
 *   <li>{@code customerEmail} → {@code email}</li>
 *   <li>{@code fromStop}      → {@code origin}</li>
 *   <li>{@code toStop}        → {@code destination}</li>
 *   <li>{@code tripDate}      → {@code travelDate}</li>
 *   <li>{@code passengers}    → {@code seats}</li>
 *   <li>{@code currentState}  → {@code bookingStatus}</li>
 *   <li>{@code paymentStatus} enriched from payment-service lookup</li>
 * </ul>
 */
public record BookingTripSummary(
    String reference,
    String passengerName,
    String email,
    String origin,
    String destination,
    String travelDate,
    String tripType,
    int seats,
    String routeCode,
    String departureTime,
    String arrivalTime,
    double totalAmount,
    String paymentStatus,
    String transactionId,
    String bookingStatus
) {
}
