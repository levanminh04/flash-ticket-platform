package com.flashticket.ticket_service.dto.snapshot;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.flashticket.ticket_service.enums.EventState;
import jakarta.validation.constraints.*;
import java.time.Instant;
import java.util.UUID;

public record AccessConfiguration(
    @NotNull UUID eventId,
    @NotBlank String organizerSubject,
    @NotNull EventState eventStatus,
    @NotNull Instant eventStartsAt,
    @NotNull Instant eventEndsAt,
    @NotBlank String sourceConfigVersion) {
  @AssertTrue
  @JsonIgnore
  public boolean isValidSnapshot() {
    return com.flashticket.ticket_service.validation.ContractRules.validAccessConfiguration(this);
  }
}
