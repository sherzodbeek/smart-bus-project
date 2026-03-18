package com.smartbus.notification.messaging;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "smartbus.messaging")
public record NotificationMessagingProperties(
    String bookingConfirmedTopic,
    String bookingConsumerGroup
) {
}
