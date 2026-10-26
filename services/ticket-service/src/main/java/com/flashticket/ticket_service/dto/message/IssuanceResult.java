package com.flashticket.ticket_service.dto.message;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.flashticket.ticket_service.dto.response.IssuanceProbeView;
import jakarta.validation.constraints.*;
import java.util.UUID;

public record IssuanceResult(
    @NotNull UUID orderId,
    @NotNull UUID chargeId,
    @NotNull UUID issuanceId,
    @NotNull State state,
    @PositiveOrZero int ticketCount,
    @JsonProperty(required = true) String failureCode)
    implements IssuanceProbeView {
  public enum State {
    COMPLETED,
    FAILED
  }

  @AssertTrue
  @JsonIgnore
  public boolean isValidResult() {
    return com.flashticket.ticket_service.validation.ContractRules.validIssuanceResult(this);
  }
}
