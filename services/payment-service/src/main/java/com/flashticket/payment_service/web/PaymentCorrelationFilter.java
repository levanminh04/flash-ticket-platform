package com.flashticket.payment_service.web;

import jakarta.servlet.*;
import jakarta.servlet.http.*;
import java.io.IOException;
import java.util.UUID;
import java.util.regex.Pattern;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public final class PaymentCorrelationFilter extends OncePerRequestFilter {
  private static final String ATTRIBUTE = PaymentCorrelationFilter.class.getName() + ".id";
  private static final Pattern UUID_PATTERN =
      Pattern.compile(
          "[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}");
  private static final Pattern ORDER_PATH = Pattern.compile("^/api/v1/orders/([^/]+)/payment$");

  public static UUID parseUuid(String value) {
    if (value == null || !UUID_PATTERN.matcher(value).matches())
      throw new IllegalArgumentException("Invalid UUID");
    return UUID.fromString(value);
  }

  public static UUID correlationId(HttpServletRequest request) {
    Object stored = request.getAttribute(ATTRIBUTE);
    if (stored instanceof UUID id) return id;
    String path = request.getRequestURI().substring(request.getContextPath().length());
    var match = ORDER_PATH.matcher(path);
    UUID id = null;
    if (match.matches()) {
      try {
        id = parseUuid(match.group(1));
      } catch (IllegalArgumentException ignored) {
      }
    }
    if (id == null) {
      try {
        id = parseUuid(request.getHeader("X-Correlation-Id"));
      } catch (IllegalArgumentException ignored) {
        id = UUID.randomUUID();
      }
    }
    request.setAttribute(ATTRIBUTE, id);
    return id;
  }

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain chain)
      throws ServletException, IOException {
    var id = correlationId(request);
    response.setHeader("X-Correlation-Id", id.toString());
    response.setHeader("Cache-Control", "no-store");
    String previous = MDC.get("correlationId");
    MDC.put("correlationId", id.toString());
    try {
      chain.doFilter(request, response);
    } finally {
      if (previous == null) MDC.remove("correlationId");
      else MDC.put("correlationId", previous);
    }
  }
}
