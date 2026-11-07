package com.flashticket.payment_service.dto.message;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;

public record IssuanceResult(
    @NotNull UUID orderId,
    @NotNull UUID chargeId,
    @NotNull UUID issuanceId,
    @NotNull IssuanceState state,
    @NotNull @Min(0) Integer ticketCount,
    String failureCode) {
  @AssertTrue
  @JsonIgnore
  public boolean isResultConsistent() {
    return state == IssuanceState.COMPLETED
        ? ticketCount != null && ticketCount > 0 && failureCode == null
        : state == IssuanceState.FAILED
            && Integer.valueOf(0).equals(ticketCount)
            && failureCode != null
            && !failureCode.isBlank();
  }
}
