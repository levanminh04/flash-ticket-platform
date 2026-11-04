package com.flashticket.ticket_service.dto.message;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.time.Instant;
import java.util.UUID;

public record Envelope<T>(
    @NotNull UUID messageId,
    @NotBlank String messageType,
    @Min(1) @Max(1) int schemaVersion,
    @NotNull UUID aggregateId,
    @NotBlank String aggregateVersion,
    @NotNull Instant occurredAt,
    @NotNull UUID correlationId,
    UUID causationId,
    String traceparent,
    @NotNull @Valid T data) {
  @AssertTrue
  @JsonIgnore
  public boolean isValidEnvelope() {
    return com.flashticket.ticket_service.validation.ContractRules.validEnvelope(this);
  }
}
