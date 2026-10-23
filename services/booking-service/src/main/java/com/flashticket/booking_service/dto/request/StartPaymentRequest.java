package com.flashticket.booking_service.dto.request;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;

@Getter
public final class StartPaymentRequest {
  @NotNull private final Provider provider;

  @JsonCreator(mode = JsonCreator.Mode.PROPERTIES)
  public StartPaymentRequest(
      @JsonProperty(value = "provider", required = true) Provider provider) {
    this.provider = provider;
  }

  public enum Provider {
    VNPAY
  }
}
