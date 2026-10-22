package com.flashticket.payment_service.dto.message;

import com.fasterxml.jackson.annotation.*;
import com.flashticket.payment_service.dto.common.*;
import com.flashticket.payment_service.dto.payment.*;
import com.flashticket.payment_service.dto.snapshot.*;
import com.flashticket.payment_service.entity.enums.*;
import jakarta.validation.constraints.*;
import java.util.*;

public record SeatConfig(
    @NotNull UUID seatId,
    @NotNull UUID sectorId,
    @NotNull UUID ticketTypeId,
    @NotBlank String seatLabel,
    @NotBlank String rowName,
    @NotBlank String seatNumber,
    @NotNull Boolean isHidden) {}
