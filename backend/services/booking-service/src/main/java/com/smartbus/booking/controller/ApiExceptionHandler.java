package com.smartbus.booking.controller;

import com.smartbus.booking.dto.ApiErrorResponse;
import com.smartbus.booking.exception.OrchestrationWorkflowException;
import com.smartbus.booking.exception.PartnerServiceException;
import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class ApiExceptionHandler {

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
}
