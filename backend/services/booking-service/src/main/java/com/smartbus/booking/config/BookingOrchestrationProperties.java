package com.smartbus.booking.config;

import java.net.URI;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "smartbus.services")
public record BookingOrchestrationProperties(
    URI schedule,
    URI payment,
    URI notification
) {
}
