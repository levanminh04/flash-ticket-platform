package com.flashticket.payment_service.dto.message;

import com.fasterxml.jackson.annotation.*;
import com.flashticket.payment_service.dto.snapshot.*;
import com.flashticket.payment_service.enums.*;
import jakarta.validation.constraints.*;
import java.util.UUID;
import lombok.Getter;

@Getter
public final class BookingIssuanceResult {
  @NotNull private final UUID orderId;
  @NotNull private final UUID chargeId;
  @NotNull private final UUID issuanceId;
  @NotNull private final IssuanceState state;
  @NotBlank private final String failureCode;

  @JsonCreator(mode = JsonCreator.Mode.PROPERTIES)
  public BookingIssuanceResult(
      @JsonProperty(value = "orderId", required = true) UUID orderId,
      @JsonProperty(value = "chargeId", required = true) UUID chargeId,
      @JsonProperty(value = "issuanceId", required = true) UUID issuanceId,
      @JsonProperty(value = "state", required = true) IssuanceState state,
      @JsonProperty(value = "failureCode", required = true) String failureCode) {
    this.orderId = orderId;
    this.chargeId = chargeId;
    this.issuanceId = issuanceId;
    this.state = state;
    this.failureCode = failureCode;
  }

  @AssertTrue
  @JsonIgnore
  public boolean isValidFailure() {
    return state == IssuanceState.FAILED && failureCode != null && !failureCode.isBlank();
  }
}
