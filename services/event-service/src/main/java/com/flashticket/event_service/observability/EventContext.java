package com.flashticket.event_service.observability;

import java.util.UUID;
import org.slf4j.MDC;

public final class EventContext {
  private EventContext() {}

  public static UUID uuid(String value) {
    if (value == null
        || !value.matches(
            "[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}"))
      return null;
    return UUID.fromString(value);
  }

  public static UUID correlation(UUID fallback) {
    UUID id = uuid(MDC.get("correlationId"));
    return id == null ? fallback : id;
  }

  public static String traceparent() {
    String value = MDC.get("traceparent");
    return validTraceparent(value) ? value : null;
  }

  public static boolean validTraceparent(String value) {
    return value != null
        && value.matches("00-[0-9a-f]{32}-[0-9a-f]{16}-[0-9a-f]{2}")
        && !value.substring(3, 35).equals("0".repeat(32))
        && !value.substring(36, 52).equals("0".repeat(16));
  }
}
