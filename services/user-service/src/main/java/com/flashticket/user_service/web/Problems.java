package com.flashticket.user_service.web;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;
import tools.jackson.databind.json.JsonMapper;

@Component
@RestControllerAdvice
public class Problems {
  public record Violation(String field, String code) {}

  public record Problem(
      String code, String message, String correlationId, List<Violation> violations) {
    public Problem {
      violations = List.copyOf(violations);
    }
  }

  private final JsonMapper json;

  @SuppressFBWarnings(
      value = "EI_EXPOSE_REP2",
      justification =
          "Uses Spring's shared Jackson 3 mapper; no mutable mapper is exposed to callers.")
  public Problems(JsonMapper json) {
    this.json = json;
  }

  public Problem problem(
      HttpServletRequest request, String code, String message, List<Violation> violations) {
    Object value = request.getAttribute("user.correlationId");
    return new Problem(
        code, message, value instanceof String id ? id : UUID.randomUUID().toString(), violations);
  }

  public void write(
      HttpServletRequest request,
      HttpServletResponse response,
      int status,
      String code,
      String message)
      throws IOException {
    response.setStatus(status);
    response.setContentType("application/json");
    if (status == 401) response.setHeader("WWW-Authenticate", "Bearer");
    response.getWriter().write(json.writeValueAsString(problem(request, code, message, List.of())));
  }

  @ExceptionHandler(UserException.class)
  ResponseEntity<Problem> business(UserException ex, HttpServletRequest request) {
    return ResponseEntity.status(ex.status())
        .body(problem(request, ex.code(), ex.getMessage(), List.of()));
  }

  @ExceptionHandler(MethodArgumentNotValidException.class)
  ResponseEntity<Problem> validation(
      MethodArgumentNotValidException ex, HttpServletRequest request) {
    List<Violation> violations =
        ex.getBindingResult().getFieldErrors().stream()
            .map(e -> new Violation(e.getField(), e.getCode() == null ? "INVALID" : e.getCode()))
            .toList();
    return ResponseEntity.badRequest()
        .body(problem(request, "INVALID_REQUEST", "Invalid request", violations));
  }

  @ExceptionHandler({
    HttpMessageNotReadableException.class,
    HandlerMethodValidationException.class,
    MethodArgumentTypeMismatchException.class,
    org.springframework.web.bind.MissingServletRequestParameterException.class
  })
  ResponseEntity<Problem> malformed(Exception ex, HttpServletRequest request) {
    return ResponseEntity.badRequest()
        .body(problem(request, "INVALID_REQUEST", "Invalid request", List.of()));
  }

  @ExceptionHandler(NoResourceFoundException.class)
  ResponseEntity<Problem> notFound(HttpServletRequest request) {
    return ResponseEntity.status(404)
        .body(problem(request, "NOT_FOUND", "Resource not found", List.of()));
  }

  @ExceptionHandler({
    org.springframework.web.HttpRequestMethodNotSupportedException.class,
    org.springframework.web.HttpMediaTypeNotSupportedException.class,
    org.springframework.web.HttpMediaTypeNotAcceptableException.class
  })
  ResponseEntity<Problem> protocol(Exception ex, HttpServletRequest request) {
    var error = (org.springframework.web.ErrorResponse) ex;
    return ResponseEntity.status(error.getStatusCode())
        .headers(error.getHeaders())
        .body(problem(request, "INVALID_REQUEST", "Unsupported request", List.of()));
  }

  @ExceptionHandler({
    org.springframework.dao.DataAccessException.class,
    com.mongodb.MongoException.class
  })
  ResponseEntity<Problem> database(Exception ex, HttpServletRequest request) {
    return ResponseEntity.status(503)
        .body(problem(request, "USER_STORAGE_UNAVAILABLE", "User storage unavailable", List.of()));
  }

  @ExceptionHandler(Exception.class)
  ResponseEntity<Problem> unexpected(Exception ex, HttpServletRequest request) {
    org.slf4j.LoggerFactory.getLogger(Problems.class)
        .atError()
        .addKeyValue("errorType", ex.getClass().getSimpleName())
        .log("User request failed");
    return ResponseEntity.internalServerError()
        .body(problem(request, "INTERNAL_ERROR", "Request could not be completed", List.of()));
  }
}
