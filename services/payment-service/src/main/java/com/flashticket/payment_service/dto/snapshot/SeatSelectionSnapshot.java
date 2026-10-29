package com.flashticket.payment_service.dto.snapshot;

import com.fasterxml.jackson.annotation.*;
import com.flashticket.payment_service.enums.*;
import jakarta.validation.constraints.*;
import java.util.UUID;
import lombok.Getter;

@Getter
public final class SeatSelectionSnapshot {
  @NotNull private final UUID seatId;
  @NotBlank private final String seatLabel;
  @NotBlank private final String rowName;
  @NotBlank private final String seatNumber;

  @JsonCreator(mode = JsonCreator.Mode.PROPERTIES)
  public SeatSelectionSnapshot(
      @JsonProperty(value = "seatId", required = true) UUID seatId,
      @JsonProperty(value = "seatLabel", required = true) String seatLabel,
      @JsonProperty(value = "rowName", required = true) String rowName,
      @JsonProperty(value = "seatNumber", required = true) String seatNumber) {
    this.seatId = seatId;
    this.seatLabel = seatLabel;
    this.rowName = rowName;
    this.seatNumber = seatNumber;
  }
}
