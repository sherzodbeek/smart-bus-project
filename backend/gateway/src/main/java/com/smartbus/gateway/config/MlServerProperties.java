package com.smartbus.gateway.config;

import java.net.URI;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "smartbus.ml")
public record MlServerProperties(URI server) {
}
