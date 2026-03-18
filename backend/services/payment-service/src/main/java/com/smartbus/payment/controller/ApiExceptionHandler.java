package com.smartbus.payment.controller;

import com.smartbus.payment.dto.ApiErrorResponse;
import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import org.springframework.http.HttpStatus;
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
}
