package com.flashticket.booking_service.dto.request;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import lombok.Getter;

@Getter
public final class FreezePaymentRequest {
  @NotBlank private final String buyerSubject;

  @JsonCreator(mode = JsonCreator.Mode.PROPERTIES)
  public FreezePaymentRequest(
      @JsonProperty(value = "buyerSubject", required = true) String buyerSubject) {
    this.buyerSubject = buyerSubject;
  }
}
