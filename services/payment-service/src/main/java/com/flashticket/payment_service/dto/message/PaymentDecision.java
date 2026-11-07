package com.flashticket.payment_service.dto.message;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import java.util.UUID;

public record PaymentDecision(
    @NotNull UUID orderId,
    @NotNull UUID chargeId,
    @NotNull Instant decisionAt,
    @NotNull PaymentDecisionOutcome outcome,
    UUID acceptedChargeId) {
  @AssertTrue
  @JsonIgnore
  public boolean isAcceptanceConsistent() {
    return outcome == PaymentDecisionOutcome.ACCEPTED
        ? chargeId != null && chargeId.equals(acceptedChargeId)
        : outcome != PaymentDecisionOutcome.DUPLICATE
            || acceptedChargeId != null && !acceptedChargeId.equals(chargeId);
  }
}
