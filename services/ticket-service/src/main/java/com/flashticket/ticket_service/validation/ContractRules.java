package com.flashticket.ticket_service.validation;

import com.flashticket.ticket_service.dto.message.*;
import com.flashticket.ticket_service.dto.snapshot.*;
import com.flashticket.ticket_service.enums.*;
import java.math.BigInteger;
import java.time.Instant;
import java.util.*;
import java.util.function.Function;

/** Pure checks for Ticket's owner-local B13 wire contracts. */
public final class ContractRules {
  private static final Map<String, Class<?>> TYPES =
      Map.of(
          "event.access-configured.v1",
          AccessConfiguration.class,
          "event.cancelled.v1",
          EventCancellation.class,
          "ticket.issue.v1",
          IssueTickets.class,
          "ticket.issuance-result.v1",
          IssuanceResult.class);

  private ContractRules() {}

  public static boolean validUInt64(String value) {
    return value != null
        && value.matches("^[1-9][0-9]{0,18}$")
        && new BigInteger(value).compareTo(BigInteger.valueOf(Long.MAX_VALUE)) <= 0;
  }

  public static boolean validMoney(String value) {
    return value != null && value.matches("^(0|[1-9][0-9]{0,18})$");
  }

  private static boolean window(Instant start, Instant end) {
    return start != null && end != null && start.isBefore(end);
  }

  private static <T, K> boolean unique(List<T> values, Function<T, K> key) {
    return values != null
        && values.stream().allMatch(Objects::nonNull)
        && values.stream().map(key).allMatch(new HashSet<>()::add);
  }

  public static boolean validAccessConfiguration(AccessConfiguration v) {
    return v != null
        && validUInt64(v.sourceConfigVersion())
        && window(v.eventStartsAt(), v.eventEndsAt());
  }

  public static boolean validSalesConfiguration(SalesConfiguration v) {
    if (v == null
        || v.schemaVersion() != 1
        || !validUInt64(v.sourceConfigVersion())
        || !window(v.saleStartsAt(), v.saleEndsAt())
        || !window(v.eventStartsAt(), v.eventEndsAt())
        || !unique(v.ticketTypes(), SalesConfiguration.TicketTypeConfig::ticketTypeId)
        || !unique(v.sectors(), SalesConfiguration.SectorConfig::sectorId)
        || !unique(v.seats(), SalesConfiguration.SeatConfig::seatId)
        || !unique(v.promotions(), PromotionConfig::promotionId)
        || v.mode() == null) return false;
    if (v.eventStatus() == EventState.PUBLISHED && v.ticketTypes().isEmpty()) return false;
    if (v.mode() == SalesMode.QUANTITY
        && (!v.sectors().isEmpty()
            || !v.seats().isEmpty()
            || v.ticketTypes().stream().anyMatch(t -> t.sectorId() != null))) return false;
    return v.ticketTypes().stream()
            .allMatch(
                t ->
                    v.mode() == SalesMode.QUANTITY
                        || v.sectors().stream()
                            .anyMatch(s -> Objects.equals(s.sectorId(), t.sectorId())))
        && v.seats().stream()
            .allMatch(
                seat ->
                    v.sectors().stream()
                            .anyMatch(
                                s ->
                                    Objects.equals(s.sectorId(), seat.sectorId())
                                        && s.kind() == SalesConfiguration.SectorKind.SEATED)
                        && v.ticketTypes().stream()
                            .anyMatch(
                                t ->
                                    Objects.equals(t.ticketTypeId(), seat.ticketTypeId())
                                        && Objects.equals(t.sectorId(), seat.sectorId())));
  }

  public static boolean validPurchaseSnapshot(PurchaseSnapshot v) {
    if (v == null
        || v.schemaVersion() != 1
        || !validUInt64(v.sourceConfigVersion())
        || !validUInt64(v.sourceOrderVersion())
        || !window(v.frozenAt(), v.expiresAt())
        || !window(v.eventStartsAt(), v.eventEndsAt())
        || v.mode() == null
        || !validMoney(v.subtotal())
        || !validMoney(v.discount())
        || !validMoney(v.total())
        || !unique(v.items(), PurchaseSnapshot.Item::orderItemId)
        || v.items().isEmpty()) return false;
    var subtotal = new BigInteger(v.subtotal());
    var discount = new BigInteger(v.discount());
    if (new BigInteger(v.total()).signum() <= 0
        || !subtotal.subtract(discount).equals(new BigInteger(v.total()))
        || (v.promotion() == null
            ? discount.signum() != 0
            : !Objects.equals(v.discount(), v.promotion().discount()))
        || (v.mode() == SalesMode.QUANTITY ? v.sectorId() != null : v.sectorId() == null))
      return false;
    BigInteger sum = BigInteger.ZERO;
    var seats = new HashSet<UUID>();
    boolean hasSeats = false;
    boolean hasUnseated = false;
    for (var item : v.items()) {
      if (item.quantity() <= 0
          || !validMoney(item.unitPrice())
          || new BigInteger(item.unitPrice()).signum() <= 0
          || !validMoney(item.lineSubtotal())
          || item.seats() == null
          || !new BigInteger(item.unitPrice())
              .multiply(BigInteger.valueOf(item.quantity()))
              .equals(new BigInteger(item.lineSubtotal()))
          || !Objects.equals(v.sectorId(), item.sectorId())) return false;
      sum = sum.add(new BigInteger(item.lineSubtotal()));
      if (item.seats().isEmpty()) hasUnseated = true;
      else hasSeats = true;
      if (v.mode() == SalesMode.QUANTITY && !item.seats().isEmpty()) return false;
      if (!item.seats().isEmpty() && item.seats().size() != item.quantity()) return false;
      for (var seat : item.seats()) if (seat == null || !seats.add(seat.seatId())) return false;
    }
    return sum.equals(subtotal) && !(hasSeats && hasUnseated);
  }

  public static boolean validCancellation(EventCancellation v) {
    return v != null
        && validUInt64(v.sourceConfigVersion())
        && v.sales() != null
        && v.access() != null
        && Objects.equals(v.eventId(), v.sales().eventId())
        && Objects.equals(v.eventId(), v.access().eventId())
        && Objects.equals(v.sourceConfigVersion(), v.sales().sourceConfigVersion())
        && Objects.equals(v.sourceConfigVersion(), v.access().sourceConfigVersion())
        && Objects.equals(v.sales().organizerSubject(), v.access().organizerSubject())
        && Objects.equals(v.sales().eventStartsAt(), v.access().eventStartsAt())
        && Objects.equals(v.sales().eventEndsAt(), v.access().eventEndsAt())
        && v.sales().eventStatus() == EventState.CANCELLED
        && v.access().eventStatus() == EventState.CANCELLED;
  }

  public static boolean validIssueTickets(IssueTickets v) {
    return v != null && v.purchase() != null && Objects.equals(v.orderId(), v.purchase().orderId());
  }

  public static boolean validIssuanceResult(IssuanceResult v) {
    return v != null
        && v.state() != null
        && (v.state() == IssuanceResult.State.COMPLETED
            ? v.ticketCount() > 0 && v.failureCode() == null
            : v.ticketCount() == 0 && v.failureCode() != null && !v.failureCode().isBlank());
  }

  public static boolean isPayloadTypeValid(String type, Object data) {
    return type != null
        && data != null
        && TYPES.containsKey(type)
        && TYPES.get(type).isInstance(data);
  }

  public static boolean matchesPayload(String type, Class<?> payloadType) {
    return type != null && payloadType != null && payloadType.equals(TYPES.get(type));
  }

  public static boolean validEnvelope(Envelope<?> v) {
    if (v == null
        || v.schemaVersion() != 1
        || !validUInt64(v.aggregateVersion())
        || !isPayloadTypeValid(v.messageType(), v.data())) return false;
    UUID aggregate =
        switch (v.data()) {
          case AccessConfiguration p -> p.eventId();
          case EventCancellation p -> p.eventId();
          case IssueTickets p -> p.orderId();
          case IssuanceResult p -> p.orderId();
          default -> null;
        };
    return aggregate != null && aggregate.equals(v.aggregateId());
  }
}
