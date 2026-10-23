package com.flashticket.payment_service.dto.message;

import com.flashticket.payment_service.dto.common.WireVersion;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;

public record SectorConfig(
    @NotNull UUID sectorId,
    @NotNull SectorKind kind,
    @NotNull @WireVersion(allowZero = true) String configuredCapacity) {}
