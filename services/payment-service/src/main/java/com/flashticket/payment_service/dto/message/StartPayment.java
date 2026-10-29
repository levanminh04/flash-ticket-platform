package com.flashticket.payment_service.dto.message;

import com.fasterxml.jackson.annotation.*;
import com.flashticket.payment_service.dto.snapshot.*;
import com.flashticket.payment_service.enums.*;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.util.UUID;
import lombok.Getter;

@Getter
public final class StartPayment {
  @NotNull private final UUID requestId;
  @NotNull private final UUID requestKey;
  @NotBlank private final String requestHash;
  @NotNull private final PaymentProvider provider;
  @NotNull @Valid private final PurchaseSnapshot purchase;

  @JsonCreator(mode = JsonCreator.Mode.PROPERTIES)
  public StartPayment(
      @JsonProperty(value = "requestId", required = true) UUID requestId,
      @JsonProperty(value = "requestKey", required = true) UUID requestKey,
      @JsonProperty(value = "requestHash", required = true) String requestHash,
      @JsonProperty(value = "provider", required = true) PaymentProvider provider,
      @JsonProperty(value = "purchase", required = true) PurchaseSnapshot purchase) {
    this.requestId = requestId;
    this.requestKey = requestKey;
    this.requestHash = requestHash;
    this.provider = provider;
    this.purchase = purchase;
  }
}
