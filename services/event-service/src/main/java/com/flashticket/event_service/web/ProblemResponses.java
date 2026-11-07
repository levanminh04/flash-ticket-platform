package com.flashticket.event_service.web;

import com.flashticket.event_service.observability.EventContext;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

@Component
public class ProblemResponses {
  public record Violation(String field, String code) {}

  public record Problem(
      String code, String message, UUID correlationId, List<Violation> violations) {
    public Problem {
      violations = List.copyOf(violations);
    }
  }

  private final JsonMapper json;

  @edu.umd.cs.findbugs.annotations.SuppressFBWarnings(
      value = "EI_EXPOSE_REP2",
      justification =
          "Intentionally shares Spring-managed infrastructure; no dependency accessor is exposed.")
  public ProblemResponses(JsonMapper json) {
    this.json = json;
  }

  public Problem body(HttpServletRequest request, String code, List<Violation> violations) {
    UUID id =
        request.getAttribute("event.correlationId") instanceof UUID value
            ? value
            : EventContext.correlation(UUID.randomUUID());
    return new Problem(code, code.replace('_', ' '), id, violations);
  }

  public void write(
      HttpServletRequest request, HttpServletResponse response, int status, String code)
      throws IOException {
    if (response.isCommitted()) return;
    response.setStatus(status);
    response.setContentType("application/json");
    response.setCharacterEncoding("UTF-8");
    response.getWriter().write(json.writeValueAsString(body(request, code, List.of())));
  }
}
