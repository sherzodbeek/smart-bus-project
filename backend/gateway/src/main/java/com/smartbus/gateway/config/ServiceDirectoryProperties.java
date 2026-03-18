package com.smartbus.gateway.config;

import java.net.URI;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "smartbus.services")
public record ServiceDirectoryProperties(
    URI booking,
    URI schedule,
    URI payment,
    URI notification
) {
}
