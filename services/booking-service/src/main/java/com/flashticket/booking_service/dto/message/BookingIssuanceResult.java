package com.flashticket.booking_service.dto.message;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import java.util.UUID;

public record BookingIssuanceResult(
    @NotNull UUID orderId,
    @NotNull UUID chargeId,
    @NotNull UUID issuanceId,
    @NotNull State state,
    @NotNull @PositiveOrZero Integer ticketCount,
    String failureCode) {
  @AssertTrue
  @JsonIgnore
  public boolean isConsistent() {
    return state != null
        && ticketCount != null
        && (state == State.COMPLETED
            ? ticketCount > 0 && failureCode == null
            : ticketCount == 0 && failureCode != null && !failureCode.isBlank());
  }

  public enum State {
    COMPLETED,
    FAILED
  }
}
