package com.flashticket.booking_service.observability;

import ch.qos.logback.classic.spi.ILoggingEvent;
import java.util.Set;
import java.util.UUID;
import org.springframework.boot.json.JsonWriter;
import org.springframework.boot.logging.structured.StructuredLoggingJsonMembersCustomizer;

public final class BookingLogCustomizer
    implements StructuredLoggingJsonMembersCustomizer<ILoggingEvent> {
  private static final String INSTANCE_ID = UUID.randomUUID().toString();
  private static final Set<String> ALLOWED =
      Set.of(
          "schemaVersion",
          "timestamp",
          "severity",
          "message",
          "serviceName",
          "serviceVersion",
          "instanceId",
          "environment",
          "hostId",
          "eventName",
          "operation",
          "outcome",
          "errorCode",
          "errorType",
          "stackTrace",
          "correlationId",
          "requestId",
          "orderId",
          "paymentAttemptId",
          "chargeId",
          "refundId",
          "issuanceId",
          "ticketId",
          "messageId",
          "causationId",
          "sagaId",
          "traceId",
          "spanId",
          "stateBefore",
          "stateAfter",
          "reasonCode",
          "durationMs",
          "retryCount",
          "configVersion");

  @Override
  public void customize(JsonWriter.Members<ILoggingEvent> members) {
    members.applyingPathFilter(path -> !ALLOWED.contains(rename(path.name())));
    members.applyingNameProcessor((path, name) -> rename(name));
    members.add("schemaVersion", 1);
    members.add("timestamp", event -> event.getInstant().toString());
    members.add("serviceName", "booking-service");
    members.add("serviceVersion", setting("BOOKING_SERVICE_VERSION", "local-dev"));
    members.add("instanceId", INSTANCE_ID);
    members.add("environment", setting("BOOKING_ENVIRONMENT", "local"));
    String host = setting("BOOKING_HOST_ID", null);
    if (host != null) members.add("hostId", host);
  }

  private static String setting(String key, String fallback) {
    String value = System.getenv(key);
    return value != null && value.matches("[A-Za-z0-9][A-Za-z0-9._-]{0,127}") ? value : fallback;
  }

  private static String rename(String name) {
    if (name == null) return "";
    return switch (name) {
      case "level" -> "severity";
      case "stack_trace" -> "stackTrace";
      case "trace_id" -> "traceId";
      case "span_id" -> "spanId";
      default -> name;
    };
  }
}
