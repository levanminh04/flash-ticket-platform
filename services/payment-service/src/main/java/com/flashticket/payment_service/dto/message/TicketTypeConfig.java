package com.flashticket.payment_service.dto.message;

import com.fasterxml.jackson.annotation.*;
import com.flashticket.payment_service.dto.common.*;
import com.flashticket.payment_service.dto.payment.*;
import com.flashticket.payment_service.dto.snapshot.*;
import com.flashticket.payment_service.entity.enums.*;
import jakarta.validation.constraints.*;
import java.util.*;

public record TicketTypeConfig(
    @NotNull UUID ticketTypeId,
    @NotBlank String name,
    @NotNull @WireMoney(positive = true) String unitPrice,
    @NotNull @Pattern(regexp = "VND") String currency,
    UUID sectorId,
    @WireVersion(allowZero = true) String configuredQuantity,
    String colorCode) {}
