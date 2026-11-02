package com.flashticket.payment_service.dto.response;

import jakarta.validation.constraints.NotBlank;

public record Violation(@NotBlank String field, @NotBlank String code) {}
