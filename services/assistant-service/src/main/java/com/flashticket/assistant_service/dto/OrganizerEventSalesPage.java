package com.flashticket.assistant_service.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class OrganizerEventSalesPage {
  private LocalDate from;
  private LocalDate to;
  private String timeZone;
  private String currency;
  private Instant asOf;
  private List<EventSales> items;

  @Getter
  @Setter
  @NoArgsConstructor
  @JsonIgnoreProperties(ignoreUnknown = true)
  public static class EventSales {
    private UUID eventId;
    private String eventTitle;
    private Long soldOrderCount;
    private Long soldTicketCount;
    private String soldRevenue;
  }
}
