package com.flashticket.booking_service.dto.projection;

import java.math.BigDecimal;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public final class EventOrderSalesTotals {
  private final UUID eventId;
  private final Long soldOrderCount;
  private final BigDecimal soldRevenue;
}
