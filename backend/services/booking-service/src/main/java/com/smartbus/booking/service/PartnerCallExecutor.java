package com.smartbus.booking.service;

import com.smartbus.booking.config.BookingFaultHandlingProperties;
import com.smartbus.booking.exception.PartnerServiceException;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Supplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClientException;

@Component
public class PartnerCallExecutor {

  private static final Logger log = LoggerFactory.getLogger(PartnerCallExecutor.class);

  private final BookingFaultHandlingProperties properties;
  private final Executor partnerCallExecutor;

  public PartnerCallExecutor(
      BookingFaultHandlingProperties properties,
      @Qualifier("partnerCallTaskExecutor")
      Executor partnerCallExecutor
  ) {
    this.properties = properties;
    this.partnerCallExecutor = partnerCallExecutor;
  }

  public <T> T execute(
      String bookingReference,
      String serviceName,
      String operation,
      Supplier<T> supplier
  ) {
    RuntimeException lastFailure = null;

    for (int attempt = 1; attempt <= properties.maxAttempts(); attempt++) {
      long startedAt = System.nanoTime();
      try {
        log.info(
            "partnerCallStart bookingReference={} service={} operation={} attempt={} timeoutMs={}",
            bookingReference,
            serviceName,
            operation,
            attempt,
            properties.timeout().toMillis()
        );

        T result = CompletableFuture.supplyAsync(supplier, partnerCallExecutor)
            .orTimeout(properties.timeout().toMillis(), TimeUnit.MILLISECONDS)
            .join();

        log.info(
            "partnerCallSuccess bookingReference={} service={} operation={} attempt={} elapsedMs={}",
            bookingReference,
            serviceName,
            operation,
            attempt,
            Duration.ofNanos(System.nanoTime() - startedAt).toMillis()
        );
        return result;
      } catch (RuntimeException exception) {
        Throwable rootCause = unwrap(exception);
        lastFailure = translateFailure(bookingReference, serviceName, operation, attempt, exception);
        boolean retryable = attempt < properties.maxAttempts();
        if (!retryable) {
          throw lastFailure;
        }

        long backoffMillis = properties.backoff().multipliedBy(attempt).toMillis();
        log.warn(
            "partnerCallRetry bookingReference={} service={} operation={} attempt={} backoffMs={} code={} message={}",
            bookingReference,
            serviceName,
            operation,
            attempt,
            backoffMillis,
            classifyCode(rootCause),
            lastFailure.getMessage()
        );
        sleep(backoffMillis);
      }
    }

    throw lastFailure;
  }

  private RuntimeException translateFailure(
      String bookingReference,
      String serviceName,
      String operation,
      int attempt,
      RuntimeException exception
  ) {
    Throwable rootCause = unwrap(exception);
    String errorCode = classifyCode(rootCause);
    HttpStatus status = "DOWNSTREAM_TIMEOUT".equals(errorCode) ? HttpStatus.GATEWAY_TIMEOUT : HttpStatus.SERVICE_UNAVAILABLE;
    String userMessage = switch (serviceName) {
      case "schedule-service" -> "SmartBus is temporarily unable to confirm trip availability. Please try again.";
      case "payment-service" -> "SmartBus is temporarily unable to authorize payment. Please try again.";
      case "notification-service" -> "SmartBus is temporarily unable to complete the booking confirmation. Please try again.";
      default -> "SmartBus is temporarily unable to complete the booking request. Please try again.";
    };

    log.error(
        "partnerCallFailure bookingReference={} service={} operation={} attempt={} code={} rootType={} message={}",
        bookingReference,
        serviceName,
        operation,
        attempt,
        errorCode,
        rootCause.getClass().getSimpleName(),
        rootCause.getMessage()
    );

    return new PartnerServiceException(
        serviceName,
        operation,
        errorCode,
        status,
        userMessage,
        attempt,
        rootCause
    );
  }

  private String classifyCode(Throwable throwable) {
    if (throwable instanceof TimeoutException) {
      return "DOWNSTREAM_TIMEOUT";
    }
    if (throwable instanceof ResourceAccessException || throwable instanceof RestClientException) {
      return "DOWNSTREAM_UNAVAILABLE";
    }
    return "DOWNSTREAM_FAILURE";
  }

  private Throwable unwrap(Throwable throwable) {
    Throwable current = throwable;
    while (current.getCause() != null
        && (current instanceof java.util.concurrent.CompletionException
        || current instanceof java.util.concurrent.ExecutionException)) {
      current = current.getCause();
    }
    return current;
  }

  private void sleep(long backoffMillis) {
    try {
      Thread.sleep(backoffMillis);
    } catch (InterruptedException exception) {
      Thread.currentThread().interrupt();
      throw new IllegalStateException(exception);
    }
  }
}
