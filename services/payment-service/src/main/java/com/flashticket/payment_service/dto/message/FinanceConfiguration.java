package com.flashticket.payment_service.dto.message;

import com.fasterxml.jackson.annotation.*;
import com.flashticket.payment_service.dto.common.*;
import com.flashticket.payment_service.dto.payment.*;
import com.flashticket.payment_service.dto.snapshot.*;
import com.flashticket.payment_service.entity.enums.*;
import jakarta.validation.constraints.*;
import java.time.Instant;
import java.util.*;

public record FinanceConfiguration(
    @NotNull UUID eventId,
    @NotBlank String organizerSubject,
    @NotNull Instant eventEndsAt,
    @NotNull @Pattern(regexp = "(?:0(?:\\.[0-9]{1,8})?|1(?:\\.0{1,8})?)") String feeRate,
    @NotNull @WireVersion String sourceFeeVersion,
    @NotNull @Pattern(regexp = "VND") String currency) {}
