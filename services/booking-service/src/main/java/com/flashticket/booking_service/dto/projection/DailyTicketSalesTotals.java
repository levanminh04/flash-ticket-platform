package com.flashticket.booking_service.dto.projection;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public final class DailyTicketSalesTotals {
  private final String day;
  private final Long ticketCount;
}
