package com.flashticket.assistant_service.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.time.Instant;
import java.time.LocalDate;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class OrganizerSalesSummary {
  private LocalDate from;
  private LocalDate to;
  private String timeZone;
  private String currency;
  private Instant asOf;
  private Long soldOrderCount;
  private Long soldTicketCount;
  private String soldRevenue;
}
