package com.flashticket.payment_service.dto.message;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import java.util.UUID;

public record BookingIssuanceResult(
    @NotNull UUID orderId,
    @NotNull UUID chargeId,
    @NotNull UUID issuanceId,
    @NotNull BookingIssuanceState state,
    @NotNull @PositiveOrZero Integer ticketCount,
    String failureCode) {
  @AssertTrue
  @JsonIgnore
  public boolean isConsistent() {
    return state == BookingIssuanceState.COMPLETED
        ? ticketCount != null && ticketCount > 0 && failureCode == null
        : state == BookingIssuanceState.FAILED
            && Integer.valueOf(0).equals(ticketCount)
            && failureCode != null
            && !failureCode.isBlank();
  }
}
