package com.flashticket.payment_service.dto.message;

import com.fasterxml.jackson.annotation.*;
import com.flashticket.payment_service.dto.snapshot.*;
import com.flashticket.payment_service.enums.*;
import com.flashticket.payment_service.validation.ContractRules;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;

@Getter
public final class Envelope<T> {
  @NotNull private final UUID messageId;
  @NotBlank private final String messageType;

  @NotNull
  @Min(1)
  @Max(1)
  private final Integer schemaVersion;

  @NotNull private final UUID aggregateId;

  @NotBlank
  @Pattern(regexp = "^[1-9][0-9]{0,18}$")
  @DecimalMax("9223372036854775807")
  private final String aggregateVersion;

  @NotNull private final Instant occurredAt;
  @NotNull private final UUID correlationId;
  private final UUID causationId;
  private final String traceparent;
  @NotNull @Valid private final T data;

  @JsonCreator(mode = JsonCreator.Mode.PROPERTIES)
  public Envelope(
      @JsonProperty(value = "messageId", required = true) UUID messageId,
      @JsonProperty(value = "messageType", required = true) String messageType,
      @JsonProperty(value = "schemaVersion", required = true) Integer schemaVersion,
      @JsonProperty(value = "aggregateId", required = true) UUID aggregateId,
      @JsonProperty(value = "aggregateVersion", required = true) String aggregateVersion,
      @JsonProperty(value = "occurredAt", required = true) Instant occurredAt,
      @JsonProperty(value = "correlationId", required = true) UUID correlationId,
      @JsonProperty(value = "causationId", required = true) UUID causationId,
      @JsonProperty(value = "traceparent", required = true) String traceparent,
      @JsonProperty(value = "data", required = true) T data) {
    this.messageId = messageId;
    this.messageType = messageType;
    this.schemaVersion = schemaVersion;
    this.aggregateId = aggregateId;
    this.aggregateVersion = aggregateVersion;
    this.occurredAt = occurredAt;
    this.correlationId = correlationId;
    this.causationId = causationId;
    this.traceparent = traceparent;
    this.data = data;
  }

  @AssertTrue
  @JsonIgnore
  public boolean isValidEnvelope() {
    return ContractRules.validEnvelope(this);
  }
}
