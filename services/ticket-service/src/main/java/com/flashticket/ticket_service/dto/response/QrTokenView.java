package com.flashticket.ticket_service.dto.response;

import jakarta.validation.constraints.*;

public record QrTokenView(@NotBlank String qrToken) {}
