package com.flashticket.event_service.domain;

import com.flashticket.event_service.api.SeatMap;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record EventData(
    UUID id,
    String organizer,
    UUID categoryId,
    UUID venueId,
    String venueName,
    String title,
    String description,
    String mode,
    String status,
    Instant startsAt,
    Instant endsAt,
    Instant saleStartsAt,
    Instant saleEndsAt,
    int purchaseLimit,
    BigDecimal feeRate,
    String approvedBy,
    Instant approvedAt,
    long configVersion,
    long rowVersion,
    Instant createdAt,
    Instant updatedAt,
    List<Image> images,
    List<TicketType> ticketTypes,
    List<Promotion> promotions,
    SeatMap seatMap) {
  public EventData {
    images = List.copyOf(images);
    ticketTypes = List.copyOf(ticketTypes);
    promotions = List.copyOf(promotions);
  }

  public record Image(UUID id, String objectReference, int displayOrder) {}

  public record TicketType(
      UUID id,
      String name,
      BigDecimal unitPrice,
      Long configuredQuantity,
      String colorCode,
      UUID sectorId) {
    public TicketType(
        UUID id, String name, BigDecimal unitPrice, long configuredQuantity, String colorCode) {
      this(id, name, unitPrice, configuredQuantity, colorCode, null);
    }
  }

  public record Promotion(
      UUID id,
      String code,
      Instant startsAt,
      Instant endsAt,
      long maximumUses,
      String kind,
      BigDecimal fixedDiscount,
      BigDecimal discountRate) {}
}
