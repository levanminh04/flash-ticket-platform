package com.flashticket.payment_service.dto.response;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import java.util.UUID;

public record PaymentView(
    @NotNull UUID orderId,
    UUID acceptedChargeId,
    @Valid PaymentStart latestAttempt,
    @Valid StartRequestView startRequest,
    @NotNull List<@Valid RefundView> refunds) {
  public PaymentView {
    if (refunds != null) refunds = List.copyOf(refunds);
  }

  public List<RefundView> refunds() {
    return refunds == null ? null : List.copyOf(refunds);
  }
}
