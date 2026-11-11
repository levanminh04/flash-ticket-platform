package com.flashticket.booking_service.dto.response;

import com.flashticket.booking_service.enums.Currency;
import com.flashticket.booking_service.enums.EventState;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public final class EventSalesStatisticsPage {
  private final LocalDate from;
  private final LocalDate to;
  private final String organizerSubject;
  private final String timeZone;
  private final Instant asOf;
  private final Currency currency;
  private final List<EventSales> items;
  private final int page;
  private final int size;
  private final long totalElements;
  private final int totalPages;

  @Getter
  @AllArgsConstructor
  public static final class EventSales {
    private final UUID eventId;
    private final String eventTitle;
    private final String organizerSubject;
    private final EventState eventStatus;
    private final long soldOrderCount;
    private final long soldTicketCount;
    private final String soldRevenue;
  }
}
