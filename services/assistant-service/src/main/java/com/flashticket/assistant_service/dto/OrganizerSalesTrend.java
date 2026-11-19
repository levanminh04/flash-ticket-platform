package com.flashticket.assistant_service.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class OrganizerSalesTrend {
  private LocalDate from;
  private LocalDate to;
  private String timeZone;
  private String currency;
  private String groupBy;
  private Instant asOf;
  private List<PeriodSales> items;

  @Getter
  @Setter
  @NoArgsConstructor
  @JsonIgnoreProperties(ignoreUnknown = true)
  public static class PeriodSales {
    private LocalDate periodStart;
    private LocalDate periodEnd;
    private Long soldOrderCount;
    private Long soldTicketCount;
    private String soldRevenue;
  }
}
