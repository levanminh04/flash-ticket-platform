package com.flashticket.payment_service.dto.common;

import com.fasterxml.jackson.annotation.*;
import com.flashticket.payment_service.dto.message.*;
import com.flashticket.payment_service.dto.payment.*;
import com.flashticket.payment_service.dto.snapshot.*;
import com.flashticket.payment_service.entity.enums.*;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.time.Instant;
import java.util.*;

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
