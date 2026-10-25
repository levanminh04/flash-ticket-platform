package com.flashticket.payment_service.gateway;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/** requestId/requestedAt are persisted by the refund runtime before the first provider call. */
public record RefundContext(
    UUID requestId,
    PaymentContext payment,
    String providerChargeId,
    Instant requestedAt,
    String operator) {
  public RefundContext {
    Objects.requireNonNull(requestId);
    Objects.requireNonNull(payment);
    Objects.requireNonNull(requestedAt);
    if (providerChargeId == null
        || !providerChargeId.matches("[0-9]{1,15}")
        || providerChargeId.matches("0+"))
      throw new GatewayException(GatewayException.Code.INVALID_REQUEST);
    if (operator == null || !operator.matches("[A-Za-z0-9 _.-]{1,50}"))
      throw new GatewayException(GatewayException.Code.INVALID_REQUEST);
  }
}
