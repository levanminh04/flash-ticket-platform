package com.flashticket.payment_service.gateway;

import java.time.Instant;
import java.util.UUID;

public record ProviderRefundResult(
    UUID requestId,
    State state,
    String providerRefundId,
    Instant completedAt,
    String responseCode) {
  public enum State {
    SUCCEEDED,
    PROCESSING,
    FAILED,
    UNKNOWN
  }
}
