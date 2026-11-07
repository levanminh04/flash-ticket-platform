package com.flashticket.ticket_service.dto.message;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.flashticket.ticket_service.dto.snapshot.AccessConfiguration;
import com.flashticket.ticket_service.dto.snapshot.SalesConfiguration;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.time.Instant;
import java.util.UUID;

public record EventCancellation(
    @NotNull UUID eventId,
    @NotNull UUID cancellationId,
    @NotBlank String sourceConfigVersion,
    @NotNull Instant cancelledAt,
    @NotBlank String actorSubject,
    @NotNull @Valid SalesConfiguration sales,
    @NotNull @Valid AccessConfiguration access) {
  @AssertTrue
  @JsonIgnore
  public boolean isValidCancellation() {
    return com.flashticket.ticket_service.validation.ContractRules.validCancellation(this);
  }
}
