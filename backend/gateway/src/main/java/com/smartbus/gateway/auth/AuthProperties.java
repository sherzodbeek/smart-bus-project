package com.smartbus.gateway.auth;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "smartbus.auth")
public record AuthProperties(
    String jwtSecret,
    Duration jwtExpiration,
    String adminEmail,
    String adminPassword,
    String adminName
) {
}
