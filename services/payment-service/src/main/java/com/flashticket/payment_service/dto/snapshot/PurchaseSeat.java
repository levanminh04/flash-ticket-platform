package com.flashticket.payment_service.dto.snapshot;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;

public record PurchaseSeat(
    @NotNull UUID seatId,
    @NotBlank String seatLabel,
    @NotBlank String rowName,
    @NotBlank String seatNumber) {}
