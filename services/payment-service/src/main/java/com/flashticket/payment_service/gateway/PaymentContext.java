package com.flashticket.payment_service.gateway;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Persist these inputs before creating/sharing a payment URL. Times are never regenerated on retry.
 */
public record PaymentContext(
    UUID attemptId,
    UUID orderId,
    String merchantAccount,
    BigDecimal amountVnd,
    String currency,
    Instant startedAt,
    Instant expiresAt) {
  public PaymentContext {
    Objects.requireNonNull(attemptId);
    Objects.requireNonNull(orderId);
    Objects.requireNonNull(merchantAccount);
    Objects.requireNonNull(amountVnd);
    Objects.requireNonNull(currency);
    Objects.requireNonNull(startedAt);
    Objects.requireNonNull(expiresAt);
  }
}
