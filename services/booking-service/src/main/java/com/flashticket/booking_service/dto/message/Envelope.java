package com.flashticket.booking_service.dto.message;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import java.time.Instant;
import java.util.UUID;

public record Envelope<T>(
    @NotNull UUID messageId,
    @NotBlank String messageType,
    @NotNull @jakarta.validation.constraints.Positive Integer schemaVersion,
    @NotNull UUID aggregateId,
    @NotBlank
        @Pattern(regexp = "^[1-9][0-9]{0,18}$")
        @DecimalMax("9223372036854775807")
        String aggregateVersion,
    @NotNull Instant occurredAt,
    @NotNull UUID correlationId,
    UUID causationId,
    String traceparent,
    @NotNull @Valid T data) {
  @AssertTrue
  @JsonIgnore
  public boolean isValidEnvelope() {
    return schemaVersion != null
        && schemaVersion == 1
        && com.flashticket.booking_service.validation.ContractRules.isPayloadTypeValid(
            messageType, data)
        && com.flashticket.booking_service.validation.ContractRules.validUInt64(aggregateVersion);
  }

  @AssertTrue
  @JsonIgnore
  public boolean isRequestIdConsistent() {
    return !(data instanceof StartPayment p)
        || (messageId != null && messageId.equals(p.requestId()));
  }
}
