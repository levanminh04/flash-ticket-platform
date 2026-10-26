package com.flashticket.ticket_service.dto.response;

import com.flashticket.ticket_service.enums.IssuanceState;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public record OrderTicketsView(
    @NotNull IssuanceState issuanceState,
    @NotNull @Valid List<@NotNull @Valid TicketView> tickets) {
  public OrderTicketsView {
    tickets = tickets == null ? null : Collections.unmodifiableList(new ArrayList<>(tickets));
  }
}
