package com.flashticket.ticket_service.dto.snapshot;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.*;

public record TicketDisplaySnapshot(
    @Min(1) @Max(1) int schemaVersion,
    @NotBlank String ticketTypeName,
    @JsonProperty(required = true) String seatLabel) {}
