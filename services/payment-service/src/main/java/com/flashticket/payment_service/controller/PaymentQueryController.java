package com.flashticket.payment_service.controller;

import com.flashticket.payment_service.dto.response.PaymentView;
import com.flashticket.payment_service.service.payment.PaymentQueryService;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/orders")
public final class PaymentQueryController {
  private final PaymentQueryService payments;

  @GetMapping("/{orderId}/payment")
  public PaymentView getPayment(
      @PathVariable UUID orderId,
      @RequestParam(required = false) UUID requestId,
      @AuthenticationPrincipal Jwt jwt) {
    return payments.getPayment(orderId, requestId, jwt.getSubject());
  }
}
