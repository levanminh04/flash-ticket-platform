package com.flashticket.ticket_service.dto.response;

import jakarta.validation.constraints.*;
import java.util.UUID;

public record PendingIssuanceView(
    @NotBlank @Pattern(regexp = "PENDING") String state,
    @NotNull UUID orderId,
    @NotNull UUID issuanceId)
    implements IssuanceProbeView {}
