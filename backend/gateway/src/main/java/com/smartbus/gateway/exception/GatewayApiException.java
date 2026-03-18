package com.smartbus.gateway.exception;

import org.springframework.http.HttpStatus;

public class GatewayApiException extends RuntimeException {

  private final HttpStatus status;

  public GatewayApiException(HttpStatus status, String message) {
    super(message);
    this.status = status;
  }

  public HttpStatus status() {
    return status;
  }
}
