package com.smartbus.schedule.config;

import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConfigurationPropertiesScan
@EnableCaching
public class CacheConfiguration {

  @Bean
  CacheManager cacheManager(ScheduleServiceProperties properties) {
    CaffeineCacheManager cacheManager = new CaffeineCacheManager("routeCatalog", "routeDefinition");
    cacheManager.setCaffeine(Caffeine.newBuilder()
        .maximumSize(200)
        .recordStats()
        .expireAfterWrite(properties.dataTtl()));
    return cacheManager;
  }
}
