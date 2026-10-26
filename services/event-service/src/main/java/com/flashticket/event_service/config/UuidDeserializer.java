package com.flashticket.event_service.config;

import com.flashticket.event_service.observability.EventContext;
import java.util.UUID;
import tools.jackson.core.JsonParser;
import tools.jackson.core.JsonToken;
import tools.jackson.databind.DeserializationContext;
import tools.jackson.databind.ValueDeserializer;

public final class UuidDeserializer extends ValueDeserializer<UUID> {
  @Override
  public UUID deserialize(JsonParser parser, DeserializationContext context) {
    if (parser.currentToken() != JsonToken.VALUE_STRING)
      return context.reportInputMismatch(UUID.class, "Expected RFC UUID string");
    UUID value = EventContext.uuid(parser.getString());
    if (value == null) return context.reportInputMismatch(UUID.class, "Expected RFC UUID string");
    return value;
  }
}
