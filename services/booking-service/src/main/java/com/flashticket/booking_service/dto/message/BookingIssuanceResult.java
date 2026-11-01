package com.flashticket.booking_service.dto.message;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;

public record BookingIssuanceResult(
    @NotNull UUID orderId,
    @NotNull UUID chargeId,
    @NotNull UUID issuanceId,
    @NotNull State state,
    @NotBlank String failureCode) {
  @AssertTrue
  @JsonIgnore
  public boolean isValidFailure() {
    return state == State.FAILED && failureCode != null && !failureCode.isBlank();
  }

  public enum State {
    FAILED
  }
}
