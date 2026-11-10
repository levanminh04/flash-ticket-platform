package com.flashticket.booking_service.dto.projection;

import com.flashticket.booking_service.enums.OrderState;
import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public final class StateTicketTotals {
  private final OrderState state;
  private final Long ticketCount;
}
