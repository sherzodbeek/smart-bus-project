package com.smartbus.booking.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "smartbus.fault-handling")
public record BookingFaultHandlingProperties(
    Duration timeout,
    int maxAttempts,
    Duration backoff
) {
}
