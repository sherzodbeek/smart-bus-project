package com.smartbus.booking.messaging;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "smartbus.messaging")
public record MessagingProperties(
    String bookingConfirmedTopic,
    String paymentDeclinedTopic,
    String paymentDeclinedConsumerGroup
) {
}
