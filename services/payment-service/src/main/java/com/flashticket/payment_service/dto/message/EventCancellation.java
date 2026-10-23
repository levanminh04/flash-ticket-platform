package com.flashticket.payment_service.dto.message;

import com.flashticket.payment_service.dto.common.WireVersion;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import java.util.UUID;

public record EventCancellation(
    @NotNull UUID eventId,
    @NotNull UUID cancellationId,
    @NotNull @WireVersion String sourceConfigVersion,
    @NotNull Instant cancelledAt,
    @NotBlank String actorSubject,
    @NotNull @Valid SalesConfiguration sales,
    @NotNull @Valid AccessConfiguration access) {}
