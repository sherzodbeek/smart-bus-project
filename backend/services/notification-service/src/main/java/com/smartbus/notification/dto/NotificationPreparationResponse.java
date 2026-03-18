package com.smartbus.notification.dto;

public record NotificationPreparationResponse(
    String templateId,
    String previewMessage,
    String channel
) {
}
