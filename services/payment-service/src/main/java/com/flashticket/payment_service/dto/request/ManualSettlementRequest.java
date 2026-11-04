package com.flashticket.payment_service.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ManualSettlementRequest(
    @NotBlank(message = "INVALID_REQUEST") @Size(max = 255, message = "INVALID_REQUEST")
        String externalReference) {}
