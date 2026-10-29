package com.flashticket.payment_service.dto.message;

import com.fasterxml.jackson.annotation.*;
import com.flashticket.payment_service.dto.snapshot.*;
import com.flashticket.payment_service.enums.*;
import jakarta.validation.constraints.*;
import java.util.UUID;
import lombok.Getter;

@Getter
public final class IssuanceResult {
  @NotNull private final UUID orderId;
  @NotNull private final UUID chargeId;
  @NotNull private final UUID issuanceId;
  @NotNull private final IssuanceState state;
  @NotNull @PositiveOrZero private final Integer ticketCount;
  private final String failureCode;

  @JsonCreator(mode = JsonCreator.Mode.PROPERTIES)
  public IssuanceResult(
      @JsonProperty(value = "orderId", required = true) UUID orderId,
      @JsonProperty(value = "chargeId", required = true) UUID chargeId,
      @JsonProperty(value = "issuanceId", required = true) UUID issuanceId,
      @JsonProperty(value = "state", required = true) IssuanceState state,
      @JsonProperty(value = "ticketCount", required = true) Integer ticketCount,
      @JsonProperty(value = "failureCode", required = true) String failureCode) {
    this.orderId = orderId;
    this.chargeId = chargeId;
    this.issuanceId = issuanceId;
    this.state = state;
    this.ticketCount = ticketCount;
    this.failureCode = failureCode;
  }

  @AssertTrue
  @JsonIgnore
  public boolean isValidResult() {
    return state != null
        && ticketCount != null
        && (state == IssuanceState.FAILED
            ? ticketCount == 0 && failureCode != null && !failureCode.isBlank()
            : ticketCount > 0 && failureCode == null);
  }
}
