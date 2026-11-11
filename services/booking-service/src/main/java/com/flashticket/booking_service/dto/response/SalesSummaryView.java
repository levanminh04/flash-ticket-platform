package com.flashticket.booking_service.dto.response;

import com.flashticket.booking_service.enums.Currency;
import com.flashticket.booking_service.enums.OrderState;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public final class SalesSummaryView {
  private final UUID eventId;
  private final String eventTitle;
  private final LocalDate from;
  private final LocalDate to;
  private final String timeZone;
  private final Instant asOf;
  private final Currency currency;
  // "Đã bán" = đơn đã nhận tiền và đang/đã phát vé: ISSUING + COMPLETED.
  private final long soldOrderCount;
  private final long soldTicketCount;
  private final String soldSubtotal;
  private final String soldDiscount;
  private final String soldRevenue;
  private final List<StateSales> byState;
  private final List<TicketTypeSales> byTicketType;

  @Getter
  @AllArgsConstructor
  public static final class StateSales {
    private final OrderState state;
    private final long orderCount;
    private final long ticketCount;
    private final String subtotal;
    private final String discount;
    private final String total;
  }

  @Getter
  @AllArgsConstructor
  public static final class TicketTypeSales {
    private final UUID ticketTypeId;
    private final String ticketTypeName;
    private final long soldQuantity;
    // Tiền dòng trước giảm giá; discount áp trên tổng đơn nên không chia theo loại vé.
    private final String lineSubtotal;
  }
}
