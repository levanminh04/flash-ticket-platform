package com.flashticket.booking_service.dto.projection;

import com.flashticket.booking_service.enums.OrderState;
import java.math.BigDecimal;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Getter;

/** Một dòng kết quả `select new` số vé và tiền dòng theo trạng thái đơn + loại vé. */
@Getter
@AllArgsConstructor
public final class TicketTypeStateTotals {
  private final OrderState state;
  private final UUID ticketTypeId;
  private final String ticketTypeName;
  private final Long quantity;
  private final BigDecimal lineSubtotal;
}
