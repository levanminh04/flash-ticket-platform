package com.flashticket.booking_service.dto.projection;

import com.flashticket.booking_service.enums.OrderState;
import java.math.BigDecimal;
import lombok.AllArgsConstructor;
import lombok.Getter;

/** Một dòng kết quả `select new` tổng tiền đơn theo trạng thái. */
@Getter
@AllArgsConstructor
public final class OrderStateTotals {
  private final OrderState state;
  private final Long orderCount;
  private final BigDecimal subtotal;
  private final BigDecimal discount;
  private final BigDecimal total;
}
