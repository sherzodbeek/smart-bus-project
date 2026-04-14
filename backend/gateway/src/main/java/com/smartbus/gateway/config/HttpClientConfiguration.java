package com.smartbus.gateway.config;

import com.smartbus.gateway.filter.RequestIdFilter;
import org.slf4j.MDC;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

@Configuration
public class HttpClientConfiguration {

  @Bean
  RestClient.Builder restClientBuilder() {
    return RestClient.builder()
        .requestInterceptor((request, body, execution) -> {
          String requestId = MDC.get(RequestIdFilter.MDC_KEY);
          if (requestId != null) {
            request.getHeaders().set(RequestIdFilter.HEADER, requestId);
          }
          return execution.execute(request, body);
        });
  }
}
