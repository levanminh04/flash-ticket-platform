package com.flashticket.booking_service.dto.request;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import lombok.Getter;

@Getter
public final class ApplyPromotionRequest {
  @NotBlank private final String code;

  @JsonCreator(mode = JsonCreator.Mode.PROPERTIES)
  public ApplyPromotionRequest(@JsonProperty(value = "code", required = true) String code) {
    this.code = code;
  }
}
