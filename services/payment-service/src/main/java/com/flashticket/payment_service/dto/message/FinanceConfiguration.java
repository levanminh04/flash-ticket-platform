package com.flashticket.payment_service.dto.message;

import com.flashticket.payment_service.dto.common.WireVersion;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import java.time.Instant;
import java.util.UUID;

public record FinanceConfiguration(
    @NotNull UUID eventId,
    @NotBlank String organizerSubject,
    @NotNull Instant eventEndsAt,
    @NotNull @Pattern(regexp = "(?:0(?:\\.[0-9]{1,8})?|1(?:\\.0{1,8})?)") String feeRate,
    @NotNull @WireVersion String sourceFeeVersion,
    @NotNull @Pattern(regexp = "VND") String currency) {}
