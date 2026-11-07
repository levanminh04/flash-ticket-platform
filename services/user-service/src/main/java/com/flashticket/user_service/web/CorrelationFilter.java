package com.flashticket.user_service.web;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.UUID;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class CorrelationFilter extends OncePerRequestFilter {
  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain chain)
      throws ServletException, IOException {
    String incoming = request.getHeader("X-Correlation-Id");
    String correlation =
        incoming != null
                && incoming.matches("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}")
            ? UUID.fromString(incoming).toString()
            : UUID.randomUUID().toString();
    String requestId = UUID.randomUUID().toString();
    request.setAttribute("user.correlationId", correlation);
    response.setHeader("X-Correlation-Id", correlation);
    response.setHeader("X-Request-Id", requestId);
    var previous = MDC.getCopyOfContextMap();
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
