package com.flashticket.payment_service.dto.message;

import com.fasterxml.jackson.annotation.*;
import com.flashticket.payment_service.dto.snapshot.*;
import com.flashticket.payment_service.enums.*;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.util.UUID;
import lombok.Getter;

@Getter
public final class IssueTickets {
  @NotNull private final UUID orderId;
  @NotNull private final UUID chargeId;
  @NotNull @Valid private final PurchaseSnapshot purchase;

  @JsonCreator(mode = JsonCreator.Mode.PROPERTIES)
  public IssueTickets(
      @JsonProperty(value = "orderId", required = true) UUID orderId,
      @JsonProperty(value = "chargeId", required = true) UUID chargeId,
      @JsonProperty(value = "purchase", required = true) PurchaseSnapshot purchase) {
    this.orderId = orderId;
    this.chargeId = chargeId;
    this.purchase = purchase;
  }

  @AssertTrue
  @JsonIgnore
  public boolean isOrderConsistent() {
    return purchase != null && orderId != null && orderId.equals(purchase.getOrderId());
  }
}
