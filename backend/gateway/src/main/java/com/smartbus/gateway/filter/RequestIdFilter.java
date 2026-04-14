package com.smartbus.gateway.filter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Ensures every request has an {@code X-Request-Id} correlation header.
 * If the inbound request already carries the header (from a test client or upstream proxy)
 * it is reused; otherwise a new UUID is generated. The value is stored in MDC under
 * {@code requestId} so every log line for that request carries it automatically.
 */
@Component
@Order(1)
public class RequestIdFilter extends OncePerRequestFilter {

  public static final String HEADER = "X-Request-Id";
  public static final String MDC_KEY = "requestId";

  private static final Logger log = LoggerFactory.getLogger(RequestIdFilter.class);

  @Override
  protected void doFilterInternal(
      HttpServletRequest request,
      HttpServletResponse response,
      FilterChain filterChain
  ) throws ServletException, IOException {
    String requestId = request.getHeader(HEADER);
    if (requestId == null || requestId.isBlank()) {
      requestId = UUID.randomUUID().toString();
    }
    MDC.put(MDC_KEY, requestId);
    response.setHeader(HEADER, requestId);
    log.debug("requestReceived method={} path={} requestId={}", request.getMethod(), request.getRequestURI(), requestId);
    try {
      filterChain.doFilter(request, response);
    } finally {
      MDC.remove(MDC_KEY);
    }
  }
}
