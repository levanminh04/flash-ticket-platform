package com.flashticket.payment_service.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ReconciliationRequest(
    @NotBlank(message = "INVALID_REQUEST") @Size(max = 1000, message = "INVALID_REQUEST")
        String evidence) {}
