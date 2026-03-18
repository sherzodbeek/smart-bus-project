package com.smartbus.booking.exception;

import org.springframework.http.HttpStatus;

public class PartnerServiceException extends RuntimeException {

  private final String serviceName;
  private final String operation;
  private final String errorCode;
  private final HttpStatus status;
  private final int attempts;

  public PartnerServiceException(
      String serviceName,
      String operation,
      String errorCode,
      HttpStatus status,
      String message,
      int attempts,
      Throwable cause
  ) {
    super(message, cause);
    this.serviceName = serviceName;
    this.operation = operation;
    this.errorCode = errorCode;
    this.status = status;
    this.attempts = attempts;
  }

  public String serviceName() {
    return serviceName;
  }

  public String operation() {
    return operation;
  }

  public String errorCode() {
    return errorCode;
  }

  public HttpStatus status() {
    return status;
  }

  public int attempts() {
    return attempts;
  }
}
