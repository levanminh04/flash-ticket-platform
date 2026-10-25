package com.flashticket.user_service.observability;

import ch.qos.logback.classic.spi.ILoggingEvent;
import java.util.Set;
import java.util.UUID;
import org.springframework.boot.json.JsonWriter;
import org.springframework.boot.logging.structured.StructuredLoggingJsonMembersCustomizer;

public final class UserLogCustomizer
    implements StructuredLoggingJsonMembersCustomizer<ILoggingEvent> {
  private static final String INSTANCE = UUID.randomUUID().toString();
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
          "correlationId",
          "requestId",
          "traceId",
          "spanId",
          "messageId",
          "causationId",
          "stateBefore",
          "stateAfter",
          "reasonCode",
          "durationMs",
          "retryCount",
          "errorType",
          "errorCode");

  @Override
  public void customize(JsonWriter.Members<ILoggingEvent> members) {
    members.applyingPathFilter(p -> !ALLOWED.contains(rename(p.name())));
    members.applyingNameProcessor((p, name) -> rename(name));
    members.add("schemaVersion", 1);
    members.add("timestamp", event -> event.getInstant().toString());
    members.add("serviceName", "user-service");
    members.add("serviceVersion", setting("USER_SERVICE_VERSION", "local-dev"));
    members.add("instanceId", INSTANCE);
    members.add("environment", setting("USER_ENVIRONMENT", "local"));
    String host = setting("USER_HOST_ID", null);
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
      case "trace_id" -> "traceId";
      case "span_id" -> "spanId";
      default -> name;
    };
  }
}
