package com.flashticket.event_service.web;

import com.flashticket.event_service.observability.EventContext;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.*;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.servlet.HandlerMapping;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class CorrelationFilter extends OncePerRequestFilter {
  private final MeterRegistry registry;

  @edu.umd.cs.findbugs.annotations.SuppressFBWarnings(
      value = "EI_EXPOSE_REP2",
      justification =
          "Intentionally shares Spring-managed infrastructure; no dependency accessor is exposed.")
  public CorrelationFilter(MeterRegistry registry) {
    this.registry = registry;
  }

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain chain)
      throws ServletException, IOException {
    var previous = MDC.getCopyOfContextMap();
    var sample = Timer.start(registry);
    UUID correlation = EventContext.uuid(single(request, "X-Correlation-Id"));
    if (correlation == null) correlation = UUID.randomUUID();
    UUID requestId = UUID.randomUUID();
    request.setAttribute("event.correlationId", correlation);
    response.setHeader("X-Correlation-Id", correlation.toString());
    response.setHeader("X-Request-Id", requestId.toString());
    MDC.put("correlationId", correlation.toString());
    MDC.put("requestId", requestId.toString());
    String trace = single(request, "traceparent");
    if (EventContext.validTraceparent(trace)) {
      MDC.put("traceparent", trace);
      MDC.put("traceId", trace.substring(3, 35));
      MDC.put("spanId", trace.substring(36, 52));
    }
    try {
      chain.doFilter(request, response);
    } finally {
      Object pattern = request.getAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE);
      String method =
          Set.of("GET", "POST", "PUT", "PATCH", "DELETE", "HEAD", "OPTIONS", "TRACE")
                  .contains(request.getMethod())
              ? request.getMethod()
              : "OTHER";
      sample.stop(
          Timer.builder("event.http.requests")
              .tag("route", pattern == null ? "unmapped" : pattern.toString())
              .tag("method", method)
              .tag("status", Integer.toString(response.getStatus()))
              .register(registry));
      if (previous == null) MDC.clear();
      else MDC.setContextMap(previous);
    }
  }

  private static String single(HttpServletRequest request, String header) {
    var values = Collections.list(request.getHeaders(header));
    return values.size() == 1 ? values.getFirst() : null;
  }
}
