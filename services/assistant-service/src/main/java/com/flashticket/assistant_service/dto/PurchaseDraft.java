package com.flashticket.assistant_service.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class PurchaseDraft {
  private UUID draftId;
  private UUID eventId;
  private String eventTitle;
  private String estimatedTotal;
  private String currency;
  private List<QuantityLine> items;
  private List<EventView.TicketType> ticketTypes;
  private Instant availabilityAsOf;
  private Instant expiresAt;
}
