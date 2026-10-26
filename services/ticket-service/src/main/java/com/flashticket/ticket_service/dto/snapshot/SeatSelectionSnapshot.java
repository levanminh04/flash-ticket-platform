package com.flashticket.ticket_service.dto.snapshot;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;

public record SeatSelectionSnapshot(
    @NotNull UUID seatId,
    @NotBlank String seatLabel,
    @NotBlank String rowName,
    @NotBlank String seatNumber) {}
