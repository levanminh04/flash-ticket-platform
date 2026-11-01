package com.flashticket.booking_service.dto.message;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import java.util.UUID;

public record PaymentDecision(
    @NotNull UUID orderId,
    @NotNull UUID chargeId,
    @NotNull Instant decisionAt,
    @NotNull Outcome outcome,
    UUID acceptedChargeId) {
  @AssertTrue
  @JsonIgnore
  public boolean isValidDecision() {
    return outcome != null
        && switch (outcome) {
          case ACCEPTED -> chargeId != null && chargeId.equals(acceptedChargeId);
          case DUPLICATE -> acceptedChargeId != null && !acceptedChargeId.equals(chargeId);
          case LATE, CANCELLED -> acceptedChargeId == null;
        };
  }

  public enum Outcome {
    ACCEPTED,
    LATE,
    DUPLICATE,
    CANCELLED
  }
}
