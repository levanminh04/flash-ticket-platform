package com.flashticket.payment_service.dto.message;

import com.flashticket.payment_service.dto.common.WireMoney;
import com.flashticket.payment_service.dto.common.WireVersion;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import java.util.UUID;

public record TicketTypeConfig(
    @NotNull UUID ticketTypeId,
    @NotBlank String name,
    @NotNull @WireMoney(positive = true) String unitPrice,
    @NotNull @Pattern(regexp = "VND") String currency,
    UUID sectorId,
    @WireVersion(allowZero = true) String configuredQuantity,
    String colorCode) {}
