package com.worksphere.shared.web;

import jakarta.servlet.http.HttpServletRequest;
import java.time.Clock;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {

  private final Clock clock;

  public GlobalExceptionHandler(Clock clock) {
    this.clock = clock;
  }

  @ExceptionHandler(MethodArgumentNotValidException.class)
  ResponseEntity<ApiErrorResponse> handleValidation(
      MethodArgumentNotValidException exception, HttpServletRequest request) {
    List<ApiErrorResponse.FieldViolation> violations =
        exception.getBindingResult().getFieldErrors().stream()
            .map(
                error ->
                    new ApiErrorResponse.FieldViolation(
                        error.getField(), error.getDefaultMessage()))
            .toList();

    return response(
        HttpStatus.BAD_REQUEST, "Request validation failed", request.getRequestURI(), violations);
  }

  @ExceptionHandler(IllegalArgumentException.class)
  ResponseEntity<ApiErrorResponse> handleIllegalArgument(
      IllegalArgumentException exception, HttpServletRequest request) {
    return response(
        HttpStatus.BAD_REQUEST, exception.getMessage(), request.getRequestURI(), List.of());
  }

  private ResponseEntity<ApiErrorResponse> response(
      HttpStatus status,
      String message,
      String path,
      List<ApiErrorResponse.FieldViolation> violations) {
    ApiErrorResponse body =
        new ApiErrorResponse(
            clock.instant(), status.value(), status.getReasonPhrase(), message, path, violations);
    return ResponseEntity.status(status).body(body);
  }
}
