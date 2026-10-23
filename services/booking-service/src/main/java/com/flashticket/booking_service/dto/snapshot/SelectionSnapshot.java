package com.flashticket.booking_service.dto.snapshot;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.util.List;

public record SelectionSnapshot(
    @Positive int schemaVersion, @NotNull List<@NotNull @Valid SeatSelectionSnapshot> seats) {
  @AssertTrue
  @JsonIgnore
  public boolean isSupportedVersion() {
    return schemaVersion == 1;
  }
}
