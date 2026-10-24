package com.flashticket.booking_service.web.error;

import com.flashticket.booking_service.exception.ErrorClassifier;
import com.flashticket.booking_service.observability.BookingContext;
import jakarta.servlet.DispatcherType;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.slf4j.LoggerFactory;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.core.Ordered;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.DisconnectedClientHelper;

@Component
@RequiredArgsConstructor
public final class CorrelationFilter extends OncePerRequestFilter {
  private final ProblemResponses responses;

  @Bean
  static FilterRegistrationBean<CorrelationFilter> correlationRegistration(
      CorrelationFilter filter) {
    var registration = new FilterRegistrationBean<>(filter);
    registration.setOrder(Ordered.HIGHEST_PRECEDENCE);
    registration.setDispatcherTypes(DispatcherType.REQUEST, DispatcherType.ERROR);
    return registration;
  }

  @Override
  protected boolean shouldNotFilterErrorDispatch() {
    return false;
  }

  @Override
  protected void doFilterNestedErrorDispatch(
      HttpServletRequest request, HttpServletResponse response, FilterChain chain)
      throws ServletException, IOException {
    doFilterInternal(request, response, chain);
  }

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain chain)
      throws ServletException, IOException {
    UUID requestId =
        request.getAttribute(BookingContext.REQUEST_ATTRIBUTE) instanceof UUID id
            ? id
            : UUID.randomUUID();
    UUID correlation =
        request.getAttribute(BookingContext.CORRELATION_ATTRIBUTE) instanceof UUID id
            ? id
            : incomingCorrelation(request);
    if (correlation == null) correlation = UUID.randomUUID();
    request.setAttribute(BookingContext.REQUEST_ATTRIBUTE, requestId);
    request.setAttribute(BookingContext.CORRELATION_ATTRIBUTE, correlation);
    if (!response.isCommitted()) {
      response.setHeader("X-Request-Id", requestId.toString());
      response.setHeader("X-Correlation-Id", correlation.toString());
    }
    try (var scope =
        BookingContext.open(
            Map.of("requestId", requestId.toString(), "correlationId", correlation.toString()))) {
      try {
        chain.doFilter(request, response);
      } catch (Exception error) {
        if (DisconnectedClientHelper.isClientDisconnectedException(error)) {
          LoggerFactory.getLogger(CorrelationFilter.class).debug("HTTP client disconnected.");
        } else {
          var entity =
              responses.response(
                  request,
                  HttpStatus.INTERNAL_SERVER_ERROR,
                  ErrorClassifier.classify(error),
                  List.of());
          responses.write(request, response, entity);
        }
      }
    }
  }

  private static UUID incomingCorrelation(HttpServletRequest request) {
    var headers = Collections.list(request.getHeaders("X-Correlation-Id"));
    return headers.size() == 1 ? BookingContext.parseUuid(headers.getFirst()) : null;
  }
}
