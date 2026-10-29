package com.flashticket.event_service.api;

import com.flashticket.event_service.domain.EventData;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public final class EventViews {
  private EventViews() {}

  public record Page<T>(List<T> items, String nextCursor) {
    public Page {
      items = List.copyOf(items);
    }
  }

  public record Lookup(UUID id, String name, String address) {}

  public record Summary(
      UUID eventId,
      String title,
      String status,
      String mode,
      Instant startsAt,
      Instant endsAt,
      String rowVersion,
      String sourceConfigVersion) {}

  public record Owner(
      UUID eventId,
      String organizerSubject,
      String title,
      String description,
      UUID categoryId,
      UUID venueId,
      String mode,
      String status,
      Instant startsAt,
      Instant endsAt,
      Instant saleStartsAt,
      Instant saleEndsAt,
      int purchaseLimit,
      String rowVersion,
      String sourceConfigVersion,
      String feeRate,
      Instant approvedAt,
      List<Image> images,
      List<TicketType> ticketTypes,
      List<Promotion> promotions,
      SeatMap seatMap) {
    public Owner {
      images = List.copyOf(images);
      ticketTypes = List.copyOf(ticketTypes);
      promotions = List.copyOf(promotions);
    }
  }

  public record Image(UUID imageId, String objectReference, int displayOrder) {}

  public record TicketType(
      UUID ticketTypeId,
      String name,
      String unitPrice,
      String currency,
      UUID sectorId,
      String configuredQuantity,
      String colorCode) {}

  @com.fasterxml.jackson.annotation.JsonInclude(
      com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL)
  public record Discount(String kind, String amount, Integer percent) {}

  public record Promotion(
      UUID promotionId,
      String code,
      Instant startsAt,
      Instant endsAt,
      String maximumUses,
      Discount discount) {}

  public record Public(
      UUID eventId,
      String title,
      String description,
      Instant startsAt,
      Instant endsAt,
      String venueName,
      String mode,
      List<TicketType> ticketTypes,
      SeatMap seatMap) {
    public Public {
      ticketTypes = List.copyOf(ticketTypes);
    }
  }

  public static TicketType ticketType(EventData.TicketType value) {
    return new TicketType(
        value.id(),
        value.name(),
        value.unitPrice().toPlainString(),
        "VND",
        value.sectorId(),
        value.configuredQuantity() == null ? null : Long.toString(value.configuredQuantity()),
        value.colorCode());
  }

  public static Owner owner(EventData event) {
    return new Owner(
        event.id(),
        event.organizer(),
        event.title(),
        event.description(),
        event.categoryId(),
        event.venueId(),
        event.mode(),
        event.status(),
        event.startsAt(),
        event.endsAt(),
        event.saleStartsAt(),
        event.saleEndsAt(),
        event.purchaseLimit(),
        Long.toString(event.rowVersion()),
        Long.toString(event.configVersion()),
        event.feeRate() == null ? null : event.feeRate().toPlainString(),
        event.approvedAt(),
        event.images().stream()
            .map(i -> new Image(i.id(), i.objectReference(), i.displayOrder()))
            .toList(),
        event.ticketTypes().stream().map(EventViews::ticketType).toList(),
        event.promotions().stream()
            .map(
                p ->
                    new Promotion(
                        p.id(),
                        p.code(),
                        p.startsAt(),
                        p.endsAt(),
                        Long.toString(p.maximumUses()),
                        new Discount(
                            p.kind(),
                            p.fixedDiscount() == null ? null : p.fixedDiscount().toPlainString(),
                            p.discountRate() == null
                                ? null
                                : p.discountRate().movePointRight(2).intValueExact())))
            .toList(),
        event.seatMap());
  }

  public static Public publicView(EventData event) {
    return new Public(
        event.id(),
        event.title(),
        event.description(),
        event.startsAt(),
        event.endsAt(),
        event.venueName(),
        event.mode(),
        event.ticketTypes().stream().map(EventViews::ticketType).toList(),
        event.seatMap() == null ? null : event.seatMap().publicView());
  }

  public static Summary summary(EventData event) {
    return new Summary(
        event.id(),
        event.title(),
        event.status(),
        event.mode(),
        event.startsAt(),
        event.endsAt(),
        Long.toString(event.rowVersion()),
        Long.toString(event.configVersion()));
  }
}
