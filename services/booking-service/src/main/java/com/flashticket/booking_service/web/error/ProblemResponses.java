package com.flashticket.booking_service.web.error;

import com.flashticket.booking_service.dto.response.Problem;
import com.flashticket.booking_service.exception.ErrorClassifier.Failure;
import com.flashticket.booking_service.observability.BookingContext;
import com.flashticket.booking_service.observability.FailureLog;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

@Component
@RequiredArgsConstructor
public final class ProblemResponses {
  private final JsonMapper jsonMapper;

  public ResponseEntity<Problem> response(
      HttpServletRequest request,
      HttpStatusCode status,
      Failure failure,
      List<Problem.Violation> violations) {
    UUID correlation =
        request.getAttribute(BookingContext.CORRELATION_ATTRIBUTE) instanceof UUID id
            ? id
            : BookingContext.correlationId();
    if (correlation == null) correlation = UUID.randomUUID();
    request.setAttribute(BookingContext.CORRELATION_ATTRIBUTE, correlation);
    boolean rejected = status.is4xxClientError() || failure.code().equals("CONFIG_NOT_READY");
    if (loggedFailures(request).add(failure.exception())) {
      String operation =
          switch (failure.code()) {
            case "UNAUTHENTICATED" -> "security.authenticate";
            case "ACCESS_DENIED" -> "security.authorize";
            default -> "http.request";
          };
      FailureLog.record(
          failure,
          rejected ? "booking.request.rejected" : "booking.request.failed",
          operation,
          rejected ? FailureLog.Outcome.REJECTED : FailureLog.Outcome.FAILED);
    }
    var builder =
        ResponseEntity.status(status)
            .contentType(MediaType.APPLICATION_JSON)
            .header("X-Correlation-Id", correlation.toString());
    if (request.getAttribute(BookingContext.REQUEST_ATTRIBUTE) instanceof UUID requestId)
      builder.header("X-Request-Id", requestId.toString());
    return builder.body(
        new Problem(failure.code(), failure.message(), correlation, List.copyOf(violations)));
  }

  public void write(
      HttpServletRequest request, HttpServletResponse response, ResponseEntity<Problem> entity)
      throws IOException {
    if (response.isCommitted()) return;
    response.setStatus(entity.getStatusCode().value());
    response.setCharacterEncoding(StandardCharsets.UTF_8.name());
    entity
        .getHeaders()
        .forEach(
            (key, values) -> {
              if (!values.isEmpty()) {
                response.setHeader(key, values.getFirst());
                values.stream().skip(1).forEach(value -> response.addHeader(key, value));
              }
            });
    jsonMapper.writeValue(response.getOutputStream(), entity.getBody());
  }

  @SuppressWarnings("unchecked")
  private static Set<Throwable> loggedFailures(HttpServletRequest request) {
    Object existing = request.getAttribute(BookingContext.LOGGED_FAILURES_ATTRIBUTE);
    if (existing instanceof Set<?>) return (Set<Throwable>) existing;
    Set<Throwable> failures = Collections.newSetFromMap(new IdentityHashMap<>());
    request.setAttribute(BookingContext.LOGGED_FAILURES_ATTRIBUTE, failures);
    return failures;
  }
}
