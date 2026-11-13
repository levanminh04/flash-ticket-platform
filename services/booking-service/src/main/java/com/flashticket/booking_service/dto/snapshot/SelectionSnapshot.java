package com.flashticket.booking_service.dto.snapshot;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

// JSONB cũ có thể chứa field dư như quantity; số lượng lấy từ cột của OrderItem.
@JsonIgnoreProperties(ignoreUnknown = true)
public record SelectionSnapshot(
    @Positive int schemaVersion, @NotNull List<@NotNull @Valid SeatSelectionSnapshot> seats) {
  public SelectionSnapshot {
    seats = seats == null ? null : Collections.unmodifiableList(new ArrayList<>(seats));
  }

  @AssertTrue
  @JsonIgnore
  public boolean isSupportedVersion() {
    return schemaVersion == 1;
  }
}
