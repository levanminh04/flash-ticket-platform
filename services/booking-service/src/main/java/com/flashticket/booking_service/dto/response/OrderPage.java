package com.flashticket.booking_service.dto.response;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public final class OrderPage {
  @NotNull @Valid private final List<@NotNull @Valid OrderView> items;
  private final String nextCursor;
}
