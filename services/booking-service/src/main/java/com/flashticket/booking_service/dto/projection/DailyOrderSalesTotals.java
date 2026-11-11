package com.flashticket.booking_service.dto.projection;

import java.math.BigDecimal;
import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public final class DailyOrderSalesTotals {
  private final String day;
  private final Long orderCount;
  private final BigDecimal revenue;
}
