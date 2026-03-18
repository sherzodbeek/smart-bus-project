package com.smartbus.schedule.controller;

import com.smartbus.schedule.dto.ApiErrorResponse;
import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ResponseStatusException;

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

  @ExceptionHandler(ResponseStatusException.class)
  public ResponseEntity<ApiErrorResponse> handleResponseStatus(
      ResponseStatusException exception,
      HttpServletRequest request
  ) {
    HttpStatus status = HttpStatus.valueOf(exception.getStatusCode().value());
    return ResponseEntity.status(status).body(new ApiErrorResponse(
        status.value(),
        status.name(),
        exception.getReason() == null ? "Schedule request failed." : exception.getReason(),
        List.of(),
        request.getRequestURI()
    ));
  }
}
