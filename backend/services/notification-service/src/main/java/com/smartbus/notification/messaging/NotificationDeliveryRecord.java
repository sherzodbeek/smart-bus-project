package com.smartbus.notification.messaging;

public record NotificationDeliveryRecord(
    String bookingReference,
    String recipient,
    String routeCode,
    String status
) {
}
