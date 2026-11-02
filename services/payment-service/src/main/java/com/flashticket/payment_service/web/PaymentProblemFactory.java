package com.flashticket.payment_service.web;

import com.flashticket.payment_service.dto.response.*;
import com.flashticket.payment_service.exception.PaymentErrorCode;
import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public final class PaymentProblemFactory {
  public Problem problem(
      HttpServletRequest request, PaymentErrorCode errorCode, List<Violation> violations) {
    return new Problem(
        errorCode.code(),
        errorCode.message(),
        PaymentCorrelationFilter.correlationId(request),
        violations);
  }
}
