package com.flashticket.payment_service.dto.message;

import com.flashticket.payment_service.dto.common.WireVersion;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import java.util.UUID;

public record AccessConfiguration(
    @NotNull UUID eventId,
    @NotBlank String organizerSubject,
    @NotNull EventState eventStatus,
    @NotNull Instant eventStartsAt,
    @NotNull Instant eventEndsAt,
    @NotNull @WireVersion String sourceConfigVersion) {}
