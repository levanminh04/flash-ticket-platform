package com.flashticket.booking_service.dto.snapshot;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record PromotionConfigurationSnapshot(
    @Positive int schemaVersion, @NotNull @Valid PromotionConfig configuration) {
  @AssertTrue
  @JsonIgnore
  public boolean isValidSnapshot() {
    return schemaVersion == 1;
  }
}
