package com.flashticket.user_service.dto.response;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.util.List;

public record OrganizerApplicationPage(
    @NotNull @Valid List<@NotNull @Valid OrganizerApplicationView> items, String nextCursor) {
  public OrganizerApplicationPage {
    items = List.copyOf(items);
  }
}
