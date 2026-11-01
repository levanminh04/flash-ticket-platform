package com.flashticket.ticket_service.dto.request;

import jakarta.validation.constraints.*;

public record CheckInRequest(@NotBlank String qrToken) {}
