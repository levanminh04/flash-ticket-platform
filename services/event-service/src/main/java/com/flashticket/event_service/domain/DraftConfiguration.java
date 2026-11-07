package com.flashticket.event_service.domain;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record DraftConfiguration(
    String title,
    String description,
    UUID categoryId,
    UUID venueId,
    String mode,
    Instant startsAt,
    Instant endsAt,
    Instant saleStartsAt,
    Instant saleEndsAt,
    int purchaseLimit,
    List<EventData.Image> images,
    List<EventData.TicketType> ticketTypes,
    List<EventData.Promotion> promotions) {
  public DraftConfiguration {
    images = List.copyOf(images);
    ticketTypes = List.copyOf(ticketTypes);
    promotions = List.copyOf(promotions);
  }
}
