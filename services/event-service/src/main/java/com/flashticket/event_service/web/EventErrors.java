package com.flashticket.event_service.web;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import java.util.List;
import org.springframework.dao.DataAccessException;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

@RestControllerAdvice
public class EventErrors {
  private final ProblemResponses responses;

  @edu.umd.cs.findbugs.annotations.SuppressFBWarnings(
      value = "EI_EXPOSE_REP2",
      justification =
          "Intentionally shares Spring-managed infrastructure; no dependency accessor is exposed.")
  public EventErrors(ProblemResponses responses) {
    this.responses = responses;
  }

  @ExceptionHandler(EventProblem.class)
  ResponseEntity<ProblemResponses.Problem> domain(
      EventProblem problem, HttpServletRequest request) {
    return response(
        request,
        problem.status(),
        problem.code(),
        problem.field() == null
            ? List.of()
            : List.of(new ProblemResponses.Violation(problem.field(), problem.code())));
  }

  @ExceptionHandler(MethodArgumentNotValidException.class)
  ResponseEntity<ProblemResponses.Problem> validation(
      MethodArgumentNotValidException problem, HttpServletRequest request) {
    return response(
        request,
        400,
        "INVALID_REQUEST",
        problem.getBindingResult().getFieldErrors().stream()
            .map(e -> new ProblemResponses.Violation(e.getField(), "INVALID_FIELD"))
            .distinct()
            .toList());
  }

  @ExceptionHandler({
    HttpMessageNotReadableException.class,
    MethodArgumentTypeMismatchException.class,
    ConstraintViolationException.class,
    org.springframework.web.bind.MissingServletRequestParameterException.class
  })
  ResponseEntity<ProblemResponses.Problem> malformed(
      Exception problem, HttpServletRequest request) {
    return response(request, 400, "INVALID_REQUEST", List.of());
  }

  @ExceptionHandler(NoResourceFoundException.class)
  ResponseEntity<ProblemResponses.Problem> missing(HttpServletRequest request) {
    return response(request, 404, "RESOURCE_NOT_FOUND", List.of());
  }

  @ExceptionHandler({
    org.springframework.web.HttpRequestMethodNotSupportedException.class,
    org.springframework.web.HttpMediaTypeNotSupportedException.class,
    org.springframework.web.HttpMediaTypeNotAcceptableException.class
  })
  ResponseEntity<ProblemResponses.Problem> protocol(
      Exception exception, HttpServletRequest request) {
    var error = (org.springframework.web.ErrorResponse) exception;
    String code =
        switch (error.getStatusCode().value()) {
          case 405 -> "METHOD_NOT_ALLOWED";
          case 415 -> "UNSUPPORTED_MEDIA_TYPE";
          default -> "NOT_ACCEPTABLE";
        };
    return ResponseEntity.status(error.getStatusCode())
        .headers(error.getHeaders())
        .body(responses.body(request, code, List.of()));
  }

  @ExceptionHandler(org.springframework.web.servlet.NoHandlerFoundException.class)
  ResponseEntity<ProblemResponses.Problem> noHandler(HttpServletRequest request) {
    return response(request, 404, "RESOURCE_NOT_FOUND", List.of());
  }

  @ExceptionHandler(DataAccessException.class)
  ResponseEntity<ProblemResponses.Problem> database(
      DataAccessException error, HttpServletRequest request) {
    Throwable cause = error;
    while (cause != null) {
      if (cause instanceof java.sql.SQLException sql) {
        String state = sql.getSQLState();
        if ("23505".equals(state))
          return response(request, 409, "CONFIGURATION_CONFLICT", List.of());
        if ("23503".equals(state) || "23514".equals(state))
          return response(request, 422, "INVALID_EVENT_CONFIGURATION", List.of());
        if ("40001".equals(state) || "40P01".equals(state))
          return response(request, 409, "CONCURRENT_EVENT_CHANGE", List.of());
      }
      cause = cause.getCause();
    }
    return response(request, 503, "STORAGE_UNAVAILABLE", List.of());
  }

  @ExceptionHandler(Exception.class)
  ResponseEntity<ProblemResponses.Problem> unexpected(Exception error, HttpServletRequest request) {
    org.slf4j.LoggerFactory.getLogger(EventErrors.class)
        .atError()
        .addKeyValue("eventName", "event.request-failed")
        .addKeyValue("operation", "request")
        .addKeyValue("outcome", "FAILED")
        .addKeyValue("errorType", error.getClass().getSimpleName())
        .addKeyValue("errorCode", "INTERNAL_ERROR")
        .log("Event request failed");
    return response(request, 500, "INTERNAL_ERROR", List.of());
  }

  private ResponseEntity<ProblemResponses.Problem> response(
      HttpServletRequest request,
      int status,
      String code,
      List<ProblemResponses.Violation> violations) {
    return ResponseEntity.status(status).body(responses.body(request, code, violations));
  }
}
