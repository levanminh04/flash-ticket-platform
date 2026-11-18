package com.flashticket.assistant_service.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class AvailabilityReadView {
  private UUID eventId;
  private Instant asOf;
  private List<TicketStock> ticketTypes;

  @Getter
  @Setter
  @NoArgsConstructor
  @JsonIgnoreProperties(ignoreUnknown = true)
  public static class TicketStock {
    private UUID ticketTypeId;
    private String available;
  }
}
