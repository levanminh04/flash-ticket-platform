package com.flashticket.payment_service.dto.message;

import com.fasterxml.jackson.annotation.*;
import com.flashticket.payment_service.dto.common.*;
import com.flashticket.payment_service.dto.payment.*;
import com.flashticket.payment_service.dto.snapshot.*;
import com.flashticket.payment_service.entity.enums.*;
import jakarta.validation.constraints.*;
import java.time.Instant;
import java.util.*;

public record AccessConfiguration(
    @NotNull UUID eventId,
    @NotBlank String organizerSubject,
    @NotNull EventState eventStatus,
    @NotNull Instant eventStartsAt,
    @NotNull Instant eventEndsAt,
    @NotNull @WireVersion String sourceConfigVersion) {}
