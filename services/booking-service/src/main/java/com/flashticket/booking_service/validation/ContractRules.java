package com.flashticket.booking_service.validation;

import com.flashticket.booking_service.dto.message.AcceptPayment;
import com.flashticket.booking_service.dto.message.BookingIssuanceResult;
import com.flashticket.booking_service.dto.message.EventCancellation;
import com.flashticket.booking_service.dto.message.IssuanceResult;
import com.flashticket.booking_service.dto.message.OrderCompleted;
import com.flashticket.booking_service.dto.message.PaymentDecision;
import com.flashticket.booking_service.dto.message.StartPayment;
import com.flashticket.booking_service.dto.snapshot.PurchaseSnapshot;
import com.flashticket.booking_service.dto.snapshot.SalesConfiguration;
import com.flashticket.booking_service.enums.SalesMode;
import com.flashticket.booking_service.enums.SectorType;
import java.math.BigInteger;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;

public final class ContractRules {
  public static final String MONEY_PATTERN = "^(0|[1-9][0-9]{0,18})$";
  public static final String UINT64_PATTERN = "^[1-9][0-9]{0,18}$";
  private static final BigInteger UINT64_MAX = BigInteger.valueOf(Long.MAX_VALUE);
  private static final Map<String, Class<?>> PAYLOAD_TYPES =
      Map.of(
          "event.sales-configured.v1", SalesConfiguration.class,
          "event.cancelled.v1", EventCancellation.class,
          "booking.accept-payment.v1", AcceptPayment.class,
          "booking.payment-decision.v1", PaymentDecision.class,
          "payment.start-requested.v1", StartPayment.class,
          "ticket.issuance-result.v1", IssuanceResult.class,
          "booking.order-completed.v1", OrderCompleted.class,
          "ticket.issued.v1", BookingIssuanceResult.class,
          "booking.issuance-failed.v1", BookingIssuanceResult.class);

  private ContractRules() {}

  private static <T, K> boolean unique(List<T> values, Function<T, K> key) {
    return values != null
        && values.stream().allMatch(java.util.Objects::nonNull)
        && values.stream().map(key).allMatch(new HashSet<>()::add);
  }

  public static boolean validUInt64(String value) {
    return value != null
        && value.matches(UINT64_PATTERN)
        && new BigInteger(value).compareTo(UINT64_MAX) <= 0;
  }

  public static boolean validMoney(String value) {
    return value != null && value.matches(MONEY_PATTERN);
  }

  public static boolean validWindow(Instant start, Instant end) {
    return start != null && end != null && start.isBefore(end);
  }

  public static boolean validTotals(String subtotal, String discount, String total) {
    if (!validMoney(subtotal) || !validMoney(discount) || !validMoney(total)) return false;
    BigInteger s = new BigInteger(subtotal),
        d = new BigInteger(discount),
        t = new BigInteger(total);
    return d.compareTo(s) <= 0 && s.subtract(d).equals(t) && t.signum() > 0;
  }

  public static boolean validSalesConfiguration(SalesConfiguration value) {
    return value != null
        && value.schemaVersion() == 1
        && value.mode() != null
        && validUInt64(value.sourceConfigVersion())
        && validWindow(value.saleStartsAt(), value.saleEndsAt())
        && validWindow(value.eventStartsAt(), value.eventEndsAt())
        && value.ticketTypes() != null
        && value.sectors() != null
        && value.seats() != null
        && value.promotions() != null
        && unique(value.ticketTypes(), SalesConfiguration.TicketTypeConfig::ticketTypeId)
        && unique(value.sectors(), SalesConfiguration.SectorConfig::sectorId)
        && unique(value.seats(), SalesConfiguration.SeatConfig::seatId)
        && unique(
            value.promotions(),
            com.flashticket.booking_service.dto.snapshot.PromotionConfig::promotionId)
        && (value.mode() != SalesMode.QUANTITY
            || (value.seats().isEmpty() && value.sectors().isEmpty()))
        && value.seats().stream()
            .allMatch(
                seat ->
                    value.sectors().stream()
                            .anyMatch(
                                s ->
                                    Objects.equals(s.sectorId(), seat.sectorId())
                                        && s.kind() == SectorType.SEATED)
                        && value.ticketTypes().stream()
                            .anyMatch(
                                t ->
                                    Objects.equals(t.ticketTypeId(), seat.ticketTypeId())
                                        && Objects.equals(t.sectorId(), seat.sectorId())))
        && value.ticketTypes().stream()
            .allMatch(
                t ->
                    value.mode() == SalesMode.QUANTITY
                        ? t.sectorId() == null
                            && t.configuredQuantity() != null
                            && validMoney(t.configuredQuantity())
                            && new BigInteger(t.configuredQuantity()).compareTo(UINT64_MAX) <= 0
                        : value.sectors().stream()
                            .anyMatch(s -> Objects.equals(s.sectorId(), t.sectorId())))
        && value.promotions().stream().allMatch(p -> p.isValidWindow());
  }

  public static boolean validPurchaseSnapshot(PurchaseSnapshot value) {
    return value != null
        && value.schemaVersion() == 1
        && value.frozenAt() != null
        && value.expiresAt() != null
        && value.frozenAt().isBefore(value.expiresAt())
        && validWindow(value.eventStartsAt(), value.eventEndsAt())
        && validUInt64(value.sourceConfigVersion())
        && validUInt64(value.sourceOrderVersion())
        && value.items() != null
        && validTotals(value.subtotal(), value.discount(), value.total())
        && (value.promotion() != null || "0".equals(value.discount()))
        && (value.mode() != SalesMode.QUANTITY || value.sectorId() == null)
        && unique(value.items(), PurchaseSnapshot.Item::orderItemId)
        && value.items().stream()
            .allMatch(
                item ->
                    item != null
                        && item.lineSubtotal() != null
                        && validMoney(item.lineSubtotal())
                        && item.seats() != null)
        && unique(
            value.items().stream().flatMap(item -> item.seats().stream()).toList(),
            com.flashticket.booking_service.dto.snapshot.SeatSelectionSnapshot::seatId)
        && value.items().stream()
            .map(item -> new BigInteger(item.lineSubtotal()))
            .reduce(BigInteger.ZERO, BigInteger::add)
            .toString()
            .equals(value.subtotal())
        && (value.promotion() == null
            || java.util.Objects.equals(value.promotion().discount(), value.discount()))
        && value.items().stream()
            .allMatch(
                item -> {
                  if (item == null
                      || item.unitPrice() == null
                      || item.lineSubtotal() == null
                      || !validMoney(item.unitPrice())
                      || !validMoney(item.lineSubtotal())
                      || item.seats() == null) return false;
                  BigInteger expected =
                      new BigInteger(item.unitPrice())
                          .multiply(BigInteger.valueOf(item.quantity()));
                  return expected.toString().equals(item.lineSubtotal())
                      && (item.seats().isEmpty() || item.seats().size() == item.quantity());
                });
  }

  public static Class<?> expectedPayloadType(String messageType) {
    Class<?> type = PAYLOAD_TYPES.get(messageType);
    if (type == null) throw new IllegalArgumentException("Unsupported messageType: " + messageType);
    return type;
  }

  public static boolean isPayloadTypeValid(String messageType, Object payload) {
    if (messageType == null || payload == null) return false;
    Class<?> expectedType = PAYLOAD_TYPES.get(messageType);
    return expectedType != null && expectedType.isInstance(payload);
  }

  public static boolean matchesPayload(String messageType, Class<?> payloadType) {
    return expectedPayloadType(messageType).equals(payloadType);
  }
}
