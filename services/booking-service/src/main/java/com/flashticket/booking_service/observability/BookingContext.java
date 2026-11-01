package com.flashticket.booking_service.observability;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.slf4j.MDC;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

public final class BookingContext {
  public static final String CORRELATION_ATTRIBUTE = "booking.correlationId";
  public static final String REQUEST_ATTRIBUTE = "booking.requestId";
  public static final String LOGGED_FAILURES_ATTRIBUTE = "booking.loggedFailures";
  private static final Set<String> IDS =
      Set.of(
          "correlationId",
          "requestId",
          "orderId",
          "messageId",
          "causationId",
          "sagaId",
          "paymentAttemptId",
          "chargeId",
          "refundId",
          "issuanceId",
          "ticketId");

  private BookingContext() {}

  public static Scope open(Map<String, String> fields) {
    Map<String, String> previous = MDC.getCopyOfContextMap();
    Map<String, String> next = new HashMap<>();
    if (previous != null) {
      copyTrace(previous, next, "traceId", "trace_id", 32);
      copyTrace(previous, next, "spanId", "span_id", 16);
    }
    fields.forEach(
        (key, value) -> {
          if (IDS.contains(key) && parseUuid(value) != null) next.put(key, value);
        });
    MDC.setContextMap(next);
    return new Scope(previous);
  }

  public static UUID parseUuid(String value) {
    if (value == null || value.length() != 36) return null;
    try {
      UUID parsed = UUID.fromString(value);
      return parsed.toString().equals(value) ? parsed : null;
    } catch (IllegalArgumentException ignored) {
      return null;
    }
  }

  public static UUID correlationId() {
    return parseUuid(MDC.get("correlationId"));
  }

  /** Caller must verify the order and its owner before binding it. */
  public static void bindOrder(UUID verifiedOrderId) {
    String value = java.util.Objects.requireNonNull(verifiedOrderId, "verifiedOrderId").toString();
    MDC.put("orderId", value);
    MDC.put("correlationId", value);
    if (RequestContextHolder.getRequestAttributes()
        instanceof ServletRequestAttributes attributes) {
      attributes.getRequest().setAttribute(CORRELATION_ATTRIBUTE, verifiedOrderId);
      var response = attributes.getResponse();
      if (response != null && !response.isCommitted())
        response.setHeader("X-Correlation-Id", value);
    }
  }

  private static void copyTrace(
      Map<String, String> source,
      Map<String, String> target,
      String key,
      String alias,
      int length) {
    String value = source.getOrDefault(key, source.get(alias));
    if (value != null
        && value.matches("[0-9a-f]{" + length + "}")
        && !value.chars().allMatch(c -> c == '0')) target.put(key, value);
  }

  public static final class Scope implements AutoCloseable {
    private final Map<String, String> previous;

    private Scope(Map<String, String> previous) {
      this.previous = previous;
    }

    @Override
    public void close() {
      if (previous == null) MDC.clear();
      else MDC.setContextMap(previous);
    }
  }
}
