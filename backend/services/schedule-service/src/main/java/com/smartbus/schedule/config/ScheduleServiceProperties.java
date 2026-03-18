package com.smartbus.schedule.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "smartbus.cache")
public record ScheduleServiceProperties(
    Duration dataTtl,
    Duration outputTtl,
    Duration simulatedLatency
) {
}
