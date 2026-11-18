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
public class EventView {
  private UUID eventId;
  private String title;
  private String description;
  private String venueName;
  private String mode;
  private Instant startsAt;
  private Instant endsAt;
  private List<TicketType> ticketTypes;

  @Getter
  @Setter
  @NoArgsConstructor
  @JsonIgnoreProperties(ignoreUnknown = true)
  public static class TicketType {
    private UUID ticketTypeId;
    private String name;
    private String unitPrice;
    private String currency;
  }
}
