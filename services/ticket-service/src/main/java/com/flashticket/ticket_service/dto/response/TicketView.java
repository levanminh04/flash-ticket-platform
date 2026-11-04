package com.flashticket.ticket_service.dto.response;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.flashticket.ticket_service.enums.TicketState;
import jakarta.validation.constraints.*;
import java.util.UUID;

public record TicketView(
    @NotNull UUID ticketId,
    @NotNull UUID eventId,
    @NotNull UUID orderItemId,
    @Positive int ordinal,
    @NotBlank String ticketTypeName,
    @JsonProperty(required = true) String seatLabel,
    @NotNull TicketState state) {}
