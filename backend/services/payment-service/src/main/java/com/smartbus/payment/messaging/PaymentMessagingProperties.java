package com.smartbus.payment.messaging;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "smartbus.messaging")
public record PaymentMessagingProperties(
    String paymentDeclinedTopic
) {
}
