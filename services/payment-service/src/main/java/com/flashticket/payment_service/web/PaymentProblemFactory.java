package com.flashticket.payment_service.web;

import com.flashticket.payment_service.dto.response.*;
import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public final class PaymentProblemFactory {
  public Problem problem(HttpServletRequest request, String code, List<Violation> violations) {
    String message =
        switch (code) {
          case "PAYMENT_NOT_READY" -> "Payment is not ready.";
          case "RESOURCE_NOT_FOUND" -> "Resource was not found.";
          case "AUTHENTICATION_REQUIRED" -> "A valid bearer token is required.";
          case "ACCESS_DENIED" -> "Access is denied.";
          case "INVALID_REQUEST" -> "Request parameters are invalid.";
          case "PAYMENT_STATE_UNAVAILABLE" -> "Payment state is temporarily unavailable.";
          default -> "The request could not be processed.";
        };
    return new Problem(code, message, PaymentCorrelationFilter.correlationId(request), violations);
  }
}
