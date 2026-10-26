package com.flashticket.ticket_service.dto.response;

import jakarta.validation.constraints.*;
import java.util.UUID;

public record TicketDeliveryAccepted(
    @NotNull UUID deliveryId, @NotBlank @Pattern(regexp = "PENDING") String state) {}
