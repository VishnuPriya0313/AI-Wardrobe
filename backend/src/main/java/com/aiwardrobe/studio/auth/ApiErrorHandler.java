package com.aiwardrobe.studio.auth;

import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ResponseStatusException;

@RestControllerAdvice
public class ApiErrorHandler {
  @ExceptionHandler(ResponseStatusException.class)
  ResponseEntity<Map<String, String>> status(ResponseStatusException error) {
    return ResponseEntity.status(error.getStatusCode()).body(Map.of("error", error.getReason()));
  }

  @ExceptionHandler(MethodArgumentNotValidException.class)
  ResponseEntity<Map<String, String>> validation(MethodArgumentNotValidException error) {
    String message = error.getBindingResult().getFieldErrors().stream()
        .findFirst().map(field -> field.getDefaultMessage()).orElse("Please check the form and try again.");
    return ResponseEntity.badRequest().body(Map.of("error", message));
  }
}
