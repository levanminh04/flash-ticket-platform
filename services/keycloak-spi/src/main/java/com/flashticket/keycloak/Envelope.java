package com.flashticket.keycloak;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import lombok.Builder;
import lombok.Getter;

/** Metadata chung của message; dữ liệu nghiệp vụ nằm trong data. */
@Getter
@JsonInclude(JsonInclude.Include.ALWAYS)
public final class Envelope<T> {
  private final UUID messageId;
  private final String messageType;
  private final int schemaVersion;
  private final UUID aggregateId;
  private final String aggregateVersion;
  @JsonFormat(shape = JsonFormat.Shape.STRING)
  private final Instant occurredAt;
  private final UUID correlationId;
  private final UUID causationId;
  private final String traceparent;
  private final T data;

  @Builder
  @JsonCreator
  public Envelope(
      @JsonProperty("messageId") UUID messageId,
      @JsonProperty("messageType") String messageType,
      @JsonProperty("schemaVersion") int schemaVersion,
      @JsonProperty("aggregateId") UUID aggregateId,
      @JsonProperty("aggregateVersion") String aggregateVersion,
      @JsonProperty("occurredAt") Instant occurredAt,
      @JsonProperty("correlationId") UUID correlationId,
      @JsonProperty("causationId") UUID causationId,
      @JsonProperty("traceparent") String traceparent,
      @JsonProperty("data") T data) {
    if (schemaVersion != 1 || messageType == null || messageType.isBlank())
      throw new IllegalArgumentException("Unsupported envelope");
    // UInt64 trong contract là chuỗi số nguyên dương nằm trong bigint PostgreSQL.
    if (aggregateVersion == null || !aggregateVersion.matches("[1-9][0-9]{0,18}")
        || Long.parseLong(aggregateVersion) <= 0)
      throw new IllegalArgumentException("Invalid aggregateVersion");
    this.messageId = Objects.requireNonNull(messageId, "messageId");
    this.messageType = messageType;
    this.schemaVersion = schemaVersion;
    this.aggregateId = Objects.requireNonNull(aggregateId, "aggregateId");
    this.aggregateVersion = aggregateVersion;
    this.occurredAt = Objects.requireNonNull(occurredAt, "occurredAt");
    this.correlationId = Objects.requireNonNull(correlationId, "correlationId");
    this.causationId = causationId;
    this.traceparent = traceparent;
    this.data = Objects.requireNonNull(data, "data");
  }
}
