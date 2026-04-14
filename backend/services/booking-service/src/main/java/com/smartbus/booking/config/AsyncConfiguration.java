package com.smartbus.booking.config;

import java.util.Map;
import java.util.concurrent.Executor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.core.task.TaskDecorator;
import org.slf4j.MDC;
import org.springframework.web.client.RestClient;
import com.smartbus.booking.filter.RequestIdFilter;

@Configuration
public class AsyncConfiguration {

  @Bean
  Executor orchestrationExecutor() {
    ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
    executor.setThreadNamePrefix("booking-orchestration-");
    executor.setCorePoolSize(2);
    executor.setMaxPoolSize(4);
    executor.setTaskDecorator(mdcTaskDecorator());
    executor.initialize();
    return executor;
  }

  @Bean
  @Qualifier("partnerCallTaskExecutor")
  Executor partnerCallTaskExecutor() {
    ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
    executor.setThreadNamePrefix("booking-partner-call-");
    executor.setCorePoolSize(4);
    executor.setMaxPoolSize(8);
    executor.setQueueCapacity(32);
    executor.setTaskDecorator(mdcTaskDecorator());
    executor.initialize();
    return executor;
  }

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

  private TaskDecorator mdcTaskDecorator() {
    return runnable -> {
      Map<String, String> contextMap = MDC.getCopyOfContextMap();
      return () -> {
        Map<String, String> previous = MDC.getCopyOfContextMap();
        try {
          if (contextMap != null) {
            MDC.setContextMap(contextMap);
          } else {
            MDC.clear();
          }
          runnable.run();
        } finally {
          if (previous != null) {
            MDC.setContextMap(previous);
          } else {
            MDC.clear();
          }
        }
      };
    };
  }
}
