package com.flashticket.booking_service.dto.response;

import com.flashticket.booking_service.enums.Currency;
import com.flashticket.booking_service.enums.SalesGrouping;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public final class SalesStatisticsChartView {
  private final LocalDate from;
  private final LocalDate to;
  private final String organizerSubject;
  private final String timeZone;
  private final Instant asOf;
  private final Currency currency;
  private final SalesGrouping groupBy;
  private final List<PeriodSales> items;

  @Getter
  @AllArgsConstructor
  public static final class PeriodSales {
    private final LocalDate periodStart;
    private final LocalDate periodEnd;
    private final long soldOrderCount;
    private final long soldTicketCount;
    private final String soldRevenue;
  }
}
