package com.flashticket.ticket_service.web;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.List;
import java.util.Map;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.ErrorResponse;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

@RestControllerAdvice(assignableTypes = TicketApiController.class)
@ConditionalOnProperty(prefix = "ticket.features", name = "business-enabled", havingValue = "true")
public class TicketApiExceptionHandler {
  @ExceptionHandler(TicketProblem.class)
  ResponseEntity<Map<String, Object>> problem(
      TicketProblem error, HttpServletRequest request, HttpServletResponse response) {
    return response(error.status(), error.code(), request, response);
  }

  @ExceptionHandler({
    HttpMessageNotReadableException.class,
    MissingRequestHeaderException.class,
    MethodArgumentTypeMismatchException.class
  })
  ResponseEntity<Map<String, Object>> invalid(
      Exception error, HttpServletRequest request, HttpServletResponse response) {
    return response(400, "INVALID_REQUEST", request, response);
  }

  @ExceptionHandler(AccessDeniedException.class)
  ResponseEntity<Map<String, Object>> denied(
      AccessDeniedException error, HttpServletRequest request, HttpServletResponse response) {
    return response(403, "ACCESS_DENIED", request, response);
  }

  @ExceptionHandler(AuthenticationException.class)
  ResponseEntity<Map<String, Object>> unauthenticated(
      AuthenticationException error, HttpServletRequest request, HttpServletResponse response) {
    return response(401, "UNAUTHENTICATED", request, response);
  }

  @ExceptionHandler(RuntimeException.class)
  ResponseEntity<Map<String, Object>> unavailable(
      RuntimeException error, HttpServletRequest request, HttpServletResponse response) {
    if (error instanceof ErrorResponse httpError) {
      return response(httpError.getStatusCode().value(), "REQUEST_REJECTED", request, response);
    }
    return response(503, "SERVICE_UNAVAILABLE", request, response);
  }

  private ResponseEntity<Map<String, Object>> response(
      int status, String code, HttpServletRequest request, HttpServletResponse response) {
    var correlation = TicketRequestContext.correlation(request, response);
    return ResponseEntity.status(status)
        .body(
            Map.of(
                "code",
                code,
                "message",
                code,
                "correlationId",
                correlation,
                "violations",
                List.of()));
  }
}
