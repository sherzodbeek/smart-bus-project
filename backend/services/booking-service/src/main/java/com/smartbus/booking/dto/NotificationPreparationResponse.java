package com.smartbus.booking.dto;

public record NotificationPreparationResponse(
    String templateId,
    String previewMessage,
    String channel
) {
}
