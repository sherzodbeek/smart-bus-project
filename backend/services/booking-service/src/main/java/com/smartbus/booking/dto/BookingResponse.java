package com.smartbus.booking.dto;

import java.util.List;

public record BookingResponse(
    String bookingReference,
    String correlationKey,
    String status,
    String routeCode,
    String paymentTransactionId,
    String notificationId,
    double totalAmount,
    List<WorkflowLogEntry> workflowLog
) {
}
