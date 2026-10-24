package com.flashticket.booking_service.dto.request;

import tools.jackson.core.JsonParser;
import tools.jackson.databind.DeserializationContext;
import tools.jackson.databind.ValueDeserializer;
import tools.jackson.databind.node.ObjectNode;

/** Reads the flat B13 selection contract using its explicit mode/kind pair. */
public final class OrderSelectionDeserializer extends ValueDeserializer<OrderSelection> {
  @Override
  public OrderSelection deserialize(JsonParser parser, DeserializationContext context) {
    var tree = context.readTree(parser);
    if (!(tree instanceof ObjectNode object) || !object.path("mode").isString()) {
      return context.reportInputMismatch(OrderSelection.class, "selection requires a string mode");
    }
    Class<? extends OrderSelection> type;
    switch (object.path("mode").asString()) {
      case "QUANTITY" -> {
        if (object.has("kind")) {
          return context.reportInputMismatch(OrderSelection.class, "QUANTITY does not accept kind");
        }
        type = OrderSelection.QuantitySelection.class;
      }
      case "SEAT_MAP" -> {
        if (!object.path("kind").isString()) {
          return context.reportInputMismatch(
              OrderSelection.class, "SEAT_MAP requires a string kind");
        }
        type =
            switch (object.path("kind").asString()) {
              case "SEATED" -> OrderSelection.SeatedSelection.class;
              case "STANDING" -> OrderSelection.StandingSelection.class;
              default -> context.reportInputMismatch(OrderSelection.class, "Unknown SEAT_MAP kind");
            };
        object.remove("kind");
      }
      default -> {
        return context.reportInputMismatch(OrderSelection.class, "Unknown selection mode");
      }
    }
    object.remove("mode");
    // Delegate all remaining fields to the same strict mapper (UUIDs, coercion, unknown fields).
    return context.readTreeAsValue(object, type);
  }
}
