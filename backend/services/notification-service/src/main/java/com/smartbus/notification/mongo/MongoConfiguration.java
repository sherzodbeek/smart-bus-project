package com.smartbus.notification.mongo;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.mongodb.repository.config.EnableMongoRepositories;

/**
 * Activates MongoDB repositories only when {@code smartbus.mongodb.enabled=true}.
 * By default MongoDB repositories are excluded from auto-configuration in
 * {@link com.smartbus.notification.NotificationServiceApplication}.
 */
@Configuration
@ConditionalOnProperty(name = "smartbus.mongodb.enabled", havingValue = "true")
@EnableMongoRepositories(basePackages = "com.smartbus.notification.mongo")
public class MongoConfiguration {
}
