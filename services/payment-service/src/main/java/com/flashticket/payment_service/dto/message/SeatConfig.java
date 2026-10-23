package com.flashticket.payment_service.dto.message;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;

public record SeatConfig(
    @NotNull UUID seatId,
    @NotNull UUID sectorId,
    @NotNull UUID ticketTypeId,
    @NotBlank String seatLabel,
    @NotBlank String rowName,
    @NotBlank String seatNumber,
    @NotNull Boolean isHidden) {}
