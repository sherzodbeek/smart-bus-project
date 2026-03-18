package com.smartbus.gateway.controller;

import com.smartbus.gateway.exception.GatewayApiException;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class ApiExceptionHandler {

  @ExceptionHandler(GatewayApiException.class)
  public ResponseEntity<Map<String, Object>> handleGatewayException(GatewayApiException exception) {
    return ResponseEntity.status(exception.status())
        .body(Map.of(
            "timestamp", OffsetDateTime.now().toString(),
            "status", exception.status().value(),
            "message", exception.getMessage()
        ));
  }

  @ExceptionHandler(MethodArgumentNotValidException.class)
  public ResponseEntity<Map<String, Object>> handleValidation(MethodArgumentNotValidException exception) {
    List<String> details = exception.getBindingResult().getFieldErrors().stream()
        .map(error -> error.getField() + ": " + error.getDefaultMessage())
        .toList();
    return ResponseEntity.badRequest()
        .body(Map.of(
            "timestamp", OffsetDateTime.now().toString(),
            "status", 400,
            "message", "Validation failed.",
            "details", details
        ));
  }
}
