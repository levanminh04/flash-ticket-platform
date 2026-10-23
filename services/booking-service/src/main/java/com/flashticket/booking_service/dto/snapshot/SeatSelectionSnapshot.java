package com.flashticket.booking_service.dto.snapshot;

import java.util.UUID;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record SeatSelectionSnapshot(
    @NotNull UUID seatId,
    @NotBlank String seatLabel,
    @NotBlank String rowName,
    @NotBlank String seatNumber) {}
