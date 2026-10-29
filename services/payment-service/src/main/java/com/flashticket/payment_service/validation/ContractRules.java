package com.flashticket.payment_service.validation;

import com.flashticket.payment_service.dto.message.*;
import com.flashticket.payment_service.dto.snapshot.*;
import com.flashticket.payment_service.enums.*;
import com.flashticket.payment_service.enums.Currency;
import java.math.BigDecimal;
import java.util.*;

/** Chỉ kiểm dữ liệu contract; không đọc DB, lấy clock hay quyết định nghiệp vụ. */
public final class ContractRules {
  private ContractRules() {}

  public static boolean validMoney(String value, boolean positive) {
    return value != null
        && value.matches(positive ? "^[1-9][0-9]{0,18}$" : "^(0|[1-9][0-9]{0,18})$");
  }

  public static boolean validUInt64(String value) {
    if (value == null || !value.matches("^[1-9][0-9]{0,18}$")) return false;
    try {
      return Long.parseLong(value) > 0;
    } catch (NumberFormatException ignored) {
      return false;
    }
  }

  public static boolean validFeeRate(String value) {
    return value != null && value.matches("^(0(?:\\.[0-9]{1,8})?|1(?:\\.0{1,8})?)$");
  }

  public static boolean validPurchaseSnapshot(PurchaseSnapshot value) {
    if (value == null
        || !Integer.valueOf(1).equals(value.getSchemaVersion())
        || value.getOrderId() == null
        || value.getEventId() == null
        || !validUInt64(value.getSourceConfigVersion())
        || !validUInt64(value.getSourceOrderVersion())
        || value.getCurrency() != Currency.VND
        || value.getMode() == null
        || value.getFrozenAt() == null
        || value.getExpiresAt() == null
        || !value.getFrozenAt().isBefore(value.getExpiresAt())
        || value.getEventStartsAt() == null
        || value.getEventEndsAt() == null
        || !value.getEventStartsAt().isBefore(value.getEventEndsAt())
        || !validMoney(value.getSubtotal(), false)
        || !validMoney(value.getDiscount(), false)
        || !validMoney(value.getTotal(), true)
        || value.getItems() == null
        || value.getItems().isEmpty()) return false;
    BigDecimal subtotal = new BigDecimal(value.getSubtotal()),
        discount = new BigDecimal(value.getDiscount());
    if (subtotal.subtract(discount).compareTo(new BigDecimal(value.getTotal())) != 0) return false;
    if (value.getPromotion() == null) {
      if (discount.signum() != 0) return false;
    } else if (!validMoney(value.getPromotion().getDiscount(), false)
        || discount.compareTo(new BigDecimal(value.getPromotion().getDiscount())) != 0)
      return false;
    if (value.getMode() == SalesMode.QUANTITY
        ? value.getSectorId() != null
        : value.getSectorId() == null) return false;
    Set<UUID> itemIds = new HashSet<>(), seatIds = new HashSet<>();
    Boolean seated = null;
    BigDecimal sum = BigDecimal.ZERO;
    for (var item : value.getItems()) {
      if (item == null
          || item.getOrderItemId() == null
          || !itemIds.add(item.getOrderItemId())
          || item.getTicketTypeId() == null
          || item.getQuantity() == null
          || item.getQuantity() <= 0
          || !validMoney(item.getUnitPrice(), true)
          || !validMoney(item.getLineSubtotal(), false)
          || item.getSeats() == null
          || !Objects.equals(value.getSectorId(), item.getSectorId())) return false;
      BigDecimal line =
          new BigDecimal(item.getUnitPrice()).multiply(BigDecimal.valueOf(item.getQuantity()));
      if (line.compareTo(new BigDecimal(item.getLineSubtotal())) != 0) return false;
      sum = sum.add(line);
      boolean hasSeats = !item.getSeats().isEmpty();
      if (value.getMode() == SalesMode.QUANTITY && hasSeats) return false;
      if (seated != null && seated != hasSeats) return false;
      seated = hasSeats;
      if (hasSeats && item.getSeats().size() != item.getQuantity()) return false;
      for (var seat : item.getSeats())
        if (seat == null || seat.getSeatId() == null || !seatIds.add(seat.getSeatId()))
          return false;
    }
    return sum.compareTo(subtotal) == 0;
  }

  public static boolean validSalesConfiguration(SalesConfiguration value) {
    if (value == null
        || !Integer.valueOf(1).equals(value.getSchemaVersion())
        || value.getEventId() == null
        || value.getMode() == null
        || value.getEventStatus() == null
        || !validUInt64(value.getSourceConfigVersion())
        || value.getEventStartsAt() == null
        || value.getEventEndsAt() == null
        || !value.getEventStartsAt().isBefore(value.getEventEndsAt())
        || value.getSaleStartsAt() == null
        || value.getSaleEndsAt() == null
        || !value.getSaleStartsAt().isBefore(value.getSaleEndsAt())
        || value.getPurchaseLimit() == null
        || value.getPurchaseLimit() <= 0
        || value.getTicketTypes() == null
        || value.getSectors() == null
        || value.getSeats() == null
        || value.getPromotions() == null) return false;
    if (value.getTicketTypes().isEmpty() && value.getEventStatus() != EventState.CANCELLED)
      return false;
    if (value.getMode() == SalesMode.QUANTITY
        && (!value.getSectors().isEmpty() || !value.getSeats().isEmpty())) return false;
    Map<UUID, SalesConfiguration.SectorConfig> sectors = new HashMap<>();
    for (var sector : value.getSectors()) {
      if (sector == null
          || sector.getSectorId() == null
          || sector.getKind() == null
          || !validMoney(sector.getConfiguredCapacity(), false)) return false;
      try {
        Long.parseLong(sector.getConfiguredCapacity());
      } catch (NumberFormatException ignored) {
        return false;
      }
      if (sectors.putIfAbsent(sector.getSectorId(), sector) != null) return false;
    }
    Map<UUID, SalesConfiguration.TicketTypeConfig> types = new HashMap<>();
    for (var type : value.getTicketTypes()) {
      if (type == null
          || type.getTicketTypeId() == null
          || !validMoney(type.getUnitPrice(), true)
          || type.getCurrency() != Currency.VND) return false;
      if (types.putIfAbsent(type.getTicketTypeId(), type) != null) return false;
      if (value.getMode() == SalesMode.QUANTITY) {
        if (type.getSectorId() != null || !validMoney(type.getConfiguredQuantity(), false))
          return false;
        try {
          Long.parseLong(type.getConfiguredQuantity());
        } catch (NumberFormatException ignored) {
          return false;
        }
      } else if (type.getSectorId() == null
          || !sectors.containsKey(type.getSectorId())
          || type.getConfiguredQuantity() != null) return false;
    }
    Set<UUID> seats = new HashSet<>(), promotions = new HashSet<>();
    for (var seat : value.getSeats()) {
      if (seat == null
          || seat.getSeatId() == null
          || !seats.add(seat.getSeatId())
          || !sectors.containsKey(seat.getSectorId())
          || sectors.get(seat.getSectorId()).getKind() != SectorType.SEATED
          || !types.containsKey(seat.getTicketTypeId())
          || !Objects.equals(types.get(seat.getTicketTypeId()).getSectorId(), seat.getSectorId()))
        return false;
    }
    for (var promotion : value.getPromotions()) {
      if (promotion == null
          || promotion.getPromotionId() == null
          || !promotions.add(promotion.getPromotionId())
          || !promotion.isValidWindow()) return false;
      if (promotion.getDiscount() instanceof PromotionConfig.FixedDiscount fixed) {
        if (!"FIXED".equals(fixed.getKind()) || !validMoney(fixed.getAmount(), true)) return false;
      } else if (promotion.getDiscount() instanceof PromotionConfig.PercentDiscount percent) {
        if (!"PERCENT".equals(percent.getKind())
            || percent.getPercent() == null
            || percent.getPercent() < 1
            || percent.getPercent() > 100) return false;
      } else return false;
    }
    return true;
  }

  public static boolean validCancellation(EventCancellation value) {
    if (value == null
        || value.getEventId() == null
        || value.getCancellationId() == null
        || value.getCancelledAt() == null
        || !validUInt64(value.getSourceConfigVersion())
        || !validSalesConfiguration(value.getSales())
        || value.getAccess() == null) return false;
    var sales = value.getSales();
    var access = value.getAccess();
    return value.getEventId().equals(sales.getEventId())
        && value.getEventId().equals(access.getEventId())
        && value.getSourceConfigVersion().equals(sales.getSourceConfigVersion())
        && value.getSourceConfigVersion().equals(access.getSourceConfigVersion())
        && sales.getEventStatus() == EventState.CANCELLED
        && access.getEventStatus() == EventState.CANCELLED
        && Objects.equals(sales.getOrganizerSubject(), access.getOrganizerSubject())
        && Objects.equals(sales.getEventStartsAt(), access.getEventStartsAt())
        && Objects.equals(sales.getEventEndsAt(), access.getEventEndsAt());
  }

  public static boolean validEnvelope(Envelope<?> value) {
    if (value == null
        || value.getMessageId() == null
        || value.getAggregateId() == null
        || value.getOccurredAt() == null
        || value.getCorrelationId() == null
        || !Integer.valueOf(1).equals(value.getSchemaVersion())
        || !validUInt64(value.getAggregateVersion())
        || !isPayloadTypeValid(value.getMessageType(), value.getData())) return false;
    UUID aggregate =
        switch (value.getData()) {
          case StartPayment p -> p.getPurchase() == null ? null : p.getPurchase().getOrderId();
          case FinanceConfiguration p -> p.getEventId();
          case EventCancellation p -> p.getEventId();
          case AcceptPayment p -> p.getOrderId();
          case PaymentDecision p -> p.getOrderId();
          case IssueTickets p -> p.getOrderId();
          case IssuanceResult p -> p.getOrderId();
          case OrderCompleted p -> p.getOrderId();
          case BookingIssuanceResult p -> p.getOrderId();
          default -> null;
        };
    return value.getAggregateId().equals(aggregate)
        && (!(value.getData() instanceof StartPayment p)
            || value.getMessageId().equals(p.getRequestId()));
  }

  public static boolean isPayloadTypeValid(String messageType, Object payload) {
    if (messageType == null || payload == null) return false;
    return switch (messageType) {
      case "payment.start-requested.v1" -> payload instanceof StartPayment;
      case "event.finance-configured.v1" -> payload instanceof FinanceConfiguration;
      case "event.cancelled.v1" -> payload instanceof EventCancellation;
      case "booking.accept-payment.v1" -> payload instanceof AcceptPayment;
      case "booking.payment-decision.v1" -> payload instanceof PaymentDecision;
      case "ticket.issue.v1" -> payload instanceof IssueTickets;
      case "ticket.issuance-result.v1" -> payload instanceof IssuanceResult;
      case "booking.order-completed.v1" -> payload instanceof OrderCompleted;
      case "booking.issuance-result.v1" -> payload instanceof BookingIssuanceResult;
      default -> false;
    };
  }
}
