package com.flashticket.event_service.domain;

import com.flashticket.event_service.api.EventCommands;
import com.flashticket.event_service.web.EventProblem;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.function.Function;

public final class ContractRules {
  private ContractRules() {}

  public static BigDecimal money(String value, String field) {
    if (value == null || !value.matches("[1-9][0-9]{0,18}")) throw EventProblem.invalid(field);
    return new BigDecimal(value);
  }

  public static long unsigned(String value, String field, boolean zero) {
    if (value == null || !value.matches(zero ? "0|[1-9][0-9]*" : "[1-9][0-9]*"))
      throw EventProblem.invalid(field);
    try {
      return Long.parseLong(value);
    } catch (NumberFormatException ex) {
      throw EventProblem.invalid(field);
    }
  }

  public static Instant instant(String value, String field) {
    try {
      if (value == null || !value.endsWith("Z")) throw EventProblem.invalid(field);
      return Instant.parse(value);
    } catch (java.time.DateTimeException ex) {
      throw EventProblem.invalid(field);
    }
  }

  public static BigDecimal fee(String value) {
    if (value == null || !value.matches("(?:0|1)(?:\\.[0-9]{1,8})?"))
      throw EventProblem.invalid("feeRate");
    BigDecimal result = new BigDecimal(value);
    if (result.compareTo(BigDecimal.ONE) > 0) throw EventProblem.invalid("feeRate");
    return result;
  }

  public static String code(String value) {
    return text(value, "code").toUpperCase(Locale.ROOT);
  }

  public static String text(String value, String field) {
    if (value == null || value.isBlank()) throw EventProblem.invalid(field);
    return value.trim();
  }

  public static DraftConfiguration draft(EventCommands.Draft input, EventData existing) {
    if (!java.util.Set.of("QUANTITY", "SEAT_MAP")
        .contains(java.util.Objects.toString(input.mode(), "")))
      throw new EventProblem(422, "SALES_MODE_NOT_IMPLEMENTED", "mode");
    if (existing != null && !existing.mode().equals(input.mode()))
      throw EventProblem.conflict("EXPLICIT_MODE_CHANGE_REQUIRED");
    if ("SEAT_MAP".equals(input.mode())
        && input.ticketTypes() != null
        && !input.ticketTypes().isEmpty())
      throw EventProblem.invalid("ticketTypes.useSectorEndpoint");
    var start = instant(input.startsAt(), "startsAt");
    var end = instant(input.endsAt(), "endsAt");
    var saleStart = instant(input.saleStartsAt(), "saleStartsAt");
    var saleEnd = instant(input.saleEndsAt(), "saleEndsAt");
    if (!start.isBefore(end) || !saleStart.isBefore(saleEnd) || saleEnd.isAfter(end))
      throw EventProblem.invalid("timeWindow");
    if (input.purchaseLimit() == null || input.purchaseLimit() <= 0)
      throw EventProblem.invalid("purchaseLimit");
    if (input.description() == null
        || input.images() == null
        || input.ticketTypes() == null
        || input.promotions() == null) throw EventProblem.invalid("draft");
    var orders = new HashSet<Integer>();
    var imageIds = new HashSet<UUID>();
    var images =
        input.images().stream()
            .map(
                image -> {
                  if (image == null
                      || image.displayOrder() == null
                      || image.displayOrder() < 0
                      || !orders.add(image.displayOrder())) throw EventProblem.invalid("images");
                  UUID id =
                      childId(
                          image.imageId(),
                          existing == null ? List.of() : existing.images(),
                          EventData.Image::id,
                          "images");
                  if (!imageIds.add(id)) throw EventProblem.invalid("images");
                  return new EventData.Image(
                      id, text(image.objectReference(), "objectReference"), image.displayOrder());
                })
            .toList();
    var typeIds = new HashSet<UUID>();
    var types =
        input.ticketTypes().stream()
            .map(
                type -> {
                  if (type == null || !"VND".equals(type.currency()))
                    throw EventProblem.invalid("ticketTypes.currency");
                  if (type.colorCode() != null && !type.colorCode().matches("#[0-9a-fA-F]{6}"))
                    throw EventProblem.invalid("ticketTypes.colorCode");
                  UUID id =
                      childId(
                          type.ticketTypeId(),
                          existing == null ? List.of() : existing.ticketTypes(),
                          EventData.TicketType::id,
                          "ticketTypes");
                  if (!typeIds.add(id)) throw EventProblem.invalid("ticketTypes");
                  return new EventData.TicketType(
                      id,
                      text(type.name(), "ticketTypes.name"),
                      money(type.unitPrice(), "ticketTypes.unitPrice"),
                      unsigned(type.configuredQuantity(), "ticketTypes.configuredQuantity", true),
                      type.colorCode());
                })
            .toList();
    var codes = new HashSet<String>();
    var promotionIds = new HashSet<UUID>();
    var promotions =
        input.promotions().stream()
            .map(
                promotion -> {
                  if (promotion == null || promotion.discount() == null)
                    throw EventProblem.invalid("promotions");
                  UUID id =
                      childId(
                          promotion.promotionId(),
                          existing == null ? List.of() : existing.promotions(),
                          EventData.Promotion::id,
                          "promotions");
                  String normalized = code(promotion.code());
                  if (!promotionIds.add(id) || !codes.add(normalized))
                    throw EventProblem.invalid("promotions.code");
                  var from = instant(promotion.startsAt(), "promotions.startsAt");
                  var to = instant(promotion.endsAt(), "promotions.endsAt");
                  if (!from.isBefore(to)) throw EventProblem.invalid("promotions.timeWindow");
                  var discount = promotion.discount();
                  BigDecimal fixed = null;
                  BigDecimal rate = null;
                  if ("FIXED".equals(discount.kind())) {
                    if (discount.percent() != null) throw EventProblem.invalid("discount");
                    fixed = money(discount.amount(), "discount.amount");
                  } else if ("PERCENT".equals(discount.kind())) {
                    if (discount.amount() != null
                        || discount.percent() == null
                        || discount.percent() < 1
                        || discount.percent() > 100) throw EventProblem.invalid("discount.percent");
                    rate = BigDecimal.valueOf(discount.percent()).movePointLeft(2);
                  } else throw EventProblem.invalid("discount.kind");
                  return new EventData.Promotion(
                      id,
                      normalized,
                      from,
                      to,
                      unsigned(promotion.maximumUses(), "maximumUses", false),
                      discount.kind(),
                      fixed,
                      rate);
                })
            .toList();
    return new DraftConfiguration(
        text(input.title(), "title"),
        input.description(),
        input.categoryId(),
        input.venueId(),
        input.mode(),
        start,
        end,
        saleStart,
        saleEnd,
        input.purchaseLimit(),
        images,
        types,
        promotions);
  }

  private static <T> UUID childId(
      UUID requested, List<T> existing, Function<T, UUID> id, String field) {
    if (requested == null) return UUID.randomUUID();
    if (existing.stream().noneMatch(item -> id.apply(item).equals(requested)))
      throw EventProblem.invalid(field + ".id");
    return requested;
  }
}
