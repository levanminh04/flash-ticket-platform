package com.flashticket.booking_service.dto.message;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import java.util.UUID;

public record IssuanceResult(
    @NotNull UUID orderId,
    @NotNull UUID chargeId,
    @NotNull UUID issuanceId,
    @NotNull State state,
    @NotNull @PositiveOrZero Integer ticketCount,
    String failureCode) {
  @AssertTrue
  @JsonIgnore
  public boolean isValidResult() {
    if (state == null || ticketCount == null) return false;
    return state == State.COMPLETED
        ? failureCode == null && ticketCount > 0
        : ticketCount == 0 && failureCode != null && !failureCode.isBlank();
  }

  public enum State {
    COMPLETED,
    FAILED
  }
}
