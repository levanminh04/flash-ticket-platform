package com.flashticket.payment_service.dto.message;

import com.fasterxml.jackson.annotation.*;
import com.flashticket.payment_service.dto.common.*;
import com.flashticket.payment_service.dto.payment.*;
import com.flashticket.payment_service.dto.snapshot.*;
import com.flashticket.payment_service.entity.enums.*;
import jakarta.validation.constraints.*;
import java.time.Instant;
import java.util.*;

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
