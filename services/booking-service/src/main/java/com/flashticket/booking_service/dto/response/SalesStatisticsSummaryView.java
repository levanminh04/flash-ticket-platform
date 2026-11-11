package com.flashticket.booking_service.dto.response;

import com.flashticket.booking_service.enums.Currency;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public final class SalesStatisticsSummaryView {
  private final LocalDate from;
  private final LocalDate to;
  private final String organizerSubject;
  private final String timeZone;
  private final Instant asOf;
  private final Currency currency;
  private final long soldOrderCount;
  private final long soldTicketCount;
  private final String soldSubtotal;
  private final String soldDiscount;
  private final String soldRevenue;
  private final List<SalesSummaryView.StateSales> byState;
}
