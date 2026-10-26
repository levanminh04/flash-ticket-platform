package com.flashticket.event_service.messaging;

import com.flashticket.event_service.api.EventViews;
import com.flashticket.event_service.domain.EventData;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public final class EventSnapshots {
  public static final String SALES = "event.sales-configured.v1";
  public static final String ACCESS = "event.access-configured.v1";
  public static final String FINANCE = "event.finance-configured.v1";

  private EventSnapshots() {}

  public record Sales(
      int schemaVersion,
      UUID eventId,
      String organizerSubject,
      String sourceConfigVersion,
      String eventStatus,
      String eventTitle,
      Instant eventStartsAt,
      Instant eventEndsAt,
      String venueName,
      String mode,
      Instant saleStartsAt,
      Instant saleEndsAt,
      int purchaseLimit,
      List<EventViews.TicketType> ticketTypes,
      List<Void> sectors,
      List<Void> seats,
      List<Promotion> promotions) {
    public Sales {
      ticketTypes = List.copyOf(ticketTypes);
      sectors = List.copyOf(sectors);
      seats = List.copyOf(seats);
      promotions = List.copyOf(promotions);
    }
  }

  public record Promotion(
      UUID promotionId,
      String code,
      Instant startsAt,
      Instant endsAt,
      String maximumUses,
      int maximumUsesPerUser,
      Discount discount) {}

  @com.fasterxml.jackson.annotation.JsonInclude(
      com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL)
  public record Discount(String kind, String amount, Integer percent) {}

  public record Access(
      UUID eventId,
      String organizerSubject,
      String eventStatus,
      Instant eventStartsAt,
      Instant eventEndsAt,
      String sourceConfigVersion) {}

  public record Finance(
      UUID eventId,
      String organizerSubject,
      Instant eventEndsAt,
      String feeRate,
      String sourceFeeVersion,
      String currency) {}

  public static Sales sales(EventData event) {
    return new Sales(
        1,
        event.id(),
        event.organizer(),
        Long.toString(event.configVersion()),
        event.status(),
        event.title(),
        event.startsAt(),
        event.endsAt(),
        event.venueName(),
        event.mode(),
        event.saleStartsAt(),
        event.saleEndsAt(),
        event.purchaseLimit(),
        event.ticketTypes().stream().map(EventViews::ticketType).toList(),
        List.of(),
        List.of(),
        event.promotions().stream()
            .map(
                p ->
                    new Promotion(
                        p.id(),
                        p.code(),
                        p.startsAt(),
                        p.endsAt(),
                        Long.toString(p.maximumUses()),
                        1,
                        new Discount(
                            p.kind(),
                            p.fixedDiscount() == null ? null : p.fixedDiscount().toPlainString(),
                            p.discountRate() == null
                                ? null
                                : p.discountRate().movePointRight(2).intValueExact())))
            .toList());
  }

  public static Access access(EventData e) {
    return new Access(
        e.id(),
        e.organizer(),
        e.status(),
        e.startsAt(),
        e.endsAt(),
        Long.toString(e.configVersion()));
  }

  public static Finance finance(EventData e) {
    return new Finance(
        e.id(),
        e.organizer(),
        e.endsAt(),
        e.feeRate().toPlainString(),
        Long.toString(e.configVersion()),
        "VND");
  }
}
