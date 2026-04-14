package com.smartbus.notification.dto;

import java.time.OffsetDateTime;

public record NotificationDeliveryResponse(
    Long id,
    String bookingReference,
    String recipient,
    String routeCode,
    String status,
    OffsetDateTime deliveredAt
) {
}
