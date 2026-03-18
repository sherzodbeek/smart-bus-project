package com.smartbus.gateway.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

@Configuration
public class HttpClientConfiguration {

  @Bean
  RestClient.Builder restClientBuilder() {
    return RestClient.builder();
  }
}
