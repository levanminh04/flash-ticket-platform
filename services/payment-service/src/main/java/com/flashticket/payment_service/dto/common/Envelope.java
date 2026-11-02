package com.flashticket.payment_service.dto.common;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import java.util.UUID;

public record Envelope<T>(
    @NotNull UUID messageId,
    @NotBlank String messageType,
    @NotNull @Min(1) @Max(1) Integer schemaVersion,
    @NotNull UUID aggregateId,
    @NotNull @WireVersion String aggregateVersion,
    @NotNull Instant occurredAt,
    @NotNull UUID correlationId,
    UUID causationId,
    String traceparent,
    @NotNull @Valid T data) {}
