package com.flashticket.payment_service.security;

import com.flashticket.payment_service.exception.PaymentErrorCode;
import com.flashticket.payment_service.web.PaymentProblemFactory;
import jakarta.servlet.http.*;
import java.io.IOException;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

@Component
@RequiredArgsConstructor
public final class PaymentSecurityProblemHandler
    implements AuthenticationEntryPoint, AccessDeniedHandler {
  private final PaymentProblemFactory problems;
  private final JsonMapper json;

  @Override
  public void commence(
      HttpServletRequest request, HttpServletResponse response, AuthenticationException exception)
      throws IOException {
    response.setHeader("WWW-Authenticate", "Bearer");
    write(request, response, PaymentErrorCode.AUTHENTICATION_REQUIRED);
  }

  @Override
  public void handle(
      HttpServletRequest request, HttpServletResponse response, AccessDeniedException exception)
      throws IOException {
    write(request, response, PaymentErrorCode.ACCESS_DENIED);
  }

  private void write(
      HttpServletRequest request, HttpServletResponse response, PaymentErrorCode errorCode)
      throws IOException {
    response.setStatus(errorCode.status().value());
    response.setContentType("application/json");
    response.setHeader("Cache-Control", "no-store");
    json.writeValue(response.getOutputStream(), problems.problem(request, errorCode, List.of()));
  }
}
