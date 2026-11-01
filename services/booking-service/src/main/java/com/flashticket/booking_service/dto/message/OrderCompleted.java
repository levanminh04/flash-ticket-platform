package com.flashticket.booking_service.dto.message;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import java.util.UUID;

public record OrderCompleted(
    @NotNull UUID orderId,
    @NotNull UUID chargeId,
    @NotNull UUID issuanceId,
    @NotNull @PositiveOrZero Integer ticketCount) {}
