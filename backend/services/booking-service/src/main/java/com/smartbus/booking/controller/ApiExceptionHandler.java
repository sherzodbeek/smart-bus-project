package com.smartbus.booking.controller;

import com.smartbus.booking.dto.ApiErrorResponse;
import com.smartbus.booking.exception.BookingNotFoundException;
import com.smartbus.booking.exception.OrchestrationWorkflowException;
import com.smartbus.booking.exception.PartnerServiceException;
import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class ApiExceptionHandler {

  private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);

  @ExceptionHandler(MethodArgumentNotValidException.class)
  @ResponseStatus(HttpStatus.BAD_REQUEST)
  public ApiErrorResponse handleValidationFailure(
      MethodArgumentNotValidException exception,
      HttpServletRequest request
  ) {
    List<String> details = exception.getBindingResult().getFieldErrors().stream()
        .map(error -> error.getField() + ": " + error.getDefaultMessage())
        .toList();
    return new ApiErrorResponse(
        HttpStatus.BAD_REQUEST.value(),
        "VALIDATION_FAILED",
        "Request validation failed.",
        details,
        request.getRequestURI()
    );
  }

  @ExceptionHandler(OrchestrationWorkflowException.class)
  @ResponseStatus(HttpStatus.UNPROCESSABLE_ENTITY)
  public ApiErrorResponse handleWorkflowFailure(
      OrchestrationWorkflowException exception,
      HttpServletRequest request
  ) {
    return new ApiErrorResponse(
        HttpStatus.UNPROCESSABLE_ENTITY.value(),
        "BOOKING_WORKFLOW_REJECTED",
        "Booking workflow rejected the request.",
        List.of(exception.getMessage()),
        request.getRequestURI()
    );
  }

  @ExceptionHandler(BookingNotFoundException.class)
  @ResponseStatus(HttpStatus.NOT_FOUND)
  public ApiErrorResponse handleNotFound(
      BookingNotFoundException exception,
      HttpServletRequest request
  ) {
    return new ApiErrorResponse(
        HttpStatus.NOT_FOUND.value(),
        "BOOKING_NOT_FOUND",
        exception.getMessage(),
        List.of(),
        request.getRequestURI()
    );
  }

  @ExceptionHandler(PartnerServiceException.class)
  public ResponseEntity<ApiErrorResponse> handlePartnerFailure(
      PartnerServiceException exception,
      HttpServletRequest request
  ) {
    return ResponseEntity.status(exception.status()).body(
        new ApiErrorResponse(
            exception.status().value(),
            exception.errorCode(),
            exception.getMessage(),
            List.of(
                "service: " + exception.serviceName(),
                "operation: " + exception.operation(),
                "attempts: " + exception.attempts()
            ),
            request.getRequestURI()
        )
    );
  }

  @ExceptionHandler(Exception.class)
  @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
  public ApiErrorResponse handleUnexpected(Exception exception, HttpServletRequest request) {
    log.error("unexpectedError path={}", request.getRequestURI(), exception);
    return new ApiErrorResponse(
        HttpStatus.INTERNAL_SERVER_ERROR.value(),
        "INTERNAL_ERROR",
        "An unexpected error occurred.",
        List.of(),
        request.getRequestURI()
    );
  }
}
