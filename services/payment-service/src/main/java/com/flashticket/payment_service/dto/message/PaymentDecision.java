package com.flashticket.payment_service.dto.message;

import com.fasterxml.jackson.annotation.*;
import com.flashticket.payment_service.dto.snapshot.*;
import com.flashticket.payment_service.enums.*;
import jakarta.validation.constraints.*;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;

@Getter
public final class PaymentDecision {
  @NotNull private final UUID orderId;
  @NotNull private final UUID chargeId;
  @NotNull private final Instant decisionAt;
  @NotNull private final PaymentDecisionOutcome outcome;
  private final UUID acceptedChargeId;

  @JsonCreator(mode = JsonCreator.Mode.PROPERTIES)
  public PaymentDecision(
      @JsonProperty(value = "orderId", required = true) UUID orderId,
      @JsonProperty(value = "chargeId", required = true) UUID chargeId,
      @JsonProperty(value = "decisionAt", required = true) Instant decisionAt,
      @JsonProperty(value = "outcome", required = true) PaymentDecisionOutcome outcome,
      @JsonProperty(value = "acceptedChargeId", required = true) UUID acceptedChargeId) {
    this.orderId = orderId;
    this.chargeId = chargeId;
    this.decisionAt = decisionAt;
    this.outcome = outcome;
    this.acceptedChargeId = acceptedChargeId;
  }

  @AssertTrue
  @JsonIgnore
  public boolean isValidDecision() {
    return outcome != null
        && chargeId != null
        && (outcome == PaymentDecisionOutcome.ACCEPTED
            ? chargeId.equals(acceptedChargeId)
            : outcome != PaymentDecisionOutcome.DUPLICATE
                || acceptedChargeId != null && !chargeId.equals(acceptedChargeId));
  }
}
