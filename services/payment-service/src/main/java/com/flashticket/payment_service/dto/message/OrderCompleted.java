package com.flashticket.payment_service.dto.message;

import com.fasterxml.jackson.annotation.*;
import com.flashticket.payment_service.dto.snapshot.*;
import com.flashticket.payment_service.enums.*;
import jakarta.validation.constraints.*;
import java.util.UUID;
import lombok.Getter;

@Getter
public final class OrderCompleted {
  @NotNull private final UUID orderId;
  @NotNull private final UUID chargeId;
  @NotNull private final UUID issuanceId;
  @NotNull @Positive private final Integer ticketCount;

  @JsonCreator(mode = JsonCreator.Mode.PROPERTIES)
  public OrderCompleted(
      @JsonProperty(value = "orderId", required = true) UUID orderId,
      @JsonProperty(value = "chargeId", required = true) UUID chargeId,
      @JsonProperty(value = "issuanceId", required = true) UUID issuanceId,
      @JsonProperty(value = "ticketCount", required = true) Integer ticketCount) {
    this.orderId = orderId;
    this.chargeId = chargeId;
    this.issuanceId = issuanceId;
    this.ticketCount = ticketCount;
  }
}
