package com.flashticket.ticket_service.web;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.UUID;
import java.util.regex.Pattern;

final class TicketRequestContext {
  private static final Pattern UUID_TEXT =
      Pattern.compile(
          "[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}");
  private static final String ATTRIBUTE = TicketRequestContext.class.getName() + ".correlationId";

  private TicketRequestContext() {}

  static UUID uuid(String value) {
    if (value == null || !UUID_TEXT.matcher(value).matches()) {
      throw new TicketProblem(400, "INVALID_REQUEST");
    }
    return UUID.fromString(value);
  }

  static UUID correlation(HttpServletRequest request, HttpServletResponse response) {
    Object existing = request.getAttribute(ATTRIBUTE);
    UUID correlation;
    if (existing instanceof UUID id) {
      correlation = id;
    } else {
      String incoming = request.getHeader("X-Correlation-Id");
      correlation =
          incoming != null && UUID_TEXT.matcher(incoming).matches()
              ? UUID.fromString(incoming)
              : UUID.randomUUID();
      request.setAttribute(ATTRIBUTE, correlation);
    }
    response.setHeader("X-Correlation-Id", correlation.toString());
    return correlation;
  }
}
