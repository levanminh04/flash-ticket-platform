package com.flashticket.booking_service.web;

import jakarta.servlet.*;
import jakarta.servlet.http.*;
import java.io.IOException;
import java.util.*;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public final class CorrelationFilter extends OncePerRequestFilter {
  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain chain)
      throws ServletException, IOException {
    var previous = MDC.getCopyOfContextMap();
    String correlation = UUID.randomUUID().toString();
    var headers = Collections.list(request.getHeaders("X-Correlation-Id"));
    if (headers.size() == 1) {
      try {
        String candidate = headers.getFirst();
        if (candidate.length() == 36 && UUID.fromString(candidate).toString().equals(candidate))
          correlation = candidate;
      } catch (IllegalArgumentException ignored) {
        /* use a fresh bounded identifier */
      }
    }
    String requestId = UUID.randomUUID().toString();
    MDC.put("correlationId", correlation);
    MDC.put("requestId", requestId);
    try {
      chain.doFilter(request, response);
    } finally {
      if (previous == null) MDC.clear();
      else MDC.setContextMap(previous);
    }
  }
}
