package com.flashticket.api_gateway.observability;

import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.classic.spi.ThrowableProxy;
import java.util.*;
import org.springframework.boot.json.JsonWriter;
import org.springframework.boot.logging.structured.StructuredLoggingJsonMembersCustomizer;

/** Single allowlist and field vocabulary for application and library log records. */
public final class LogFields implements StructuredLoggingJsonMembersCustomizer<ILoggingEvent> {
  private static final Set<String> ALLOWED =
      Set.of(
          "eventName",
          "operation",
          "outcome",
          "correlationId",
          "requestId",
          "traceId",
          "spanId",
          "orderId",
          "paymentAttemptId",
          "chargeId",
          "refundId",
          "issuanceId",
          "ticketId",
          "messageId",
          "causationId",
          "sagaId",
          "stateBefore",
          "stateAfter",
          "reasonCode",
          "durationMs",
          "retryCount",
          "configVersion",
          "errorType",
          "errorCode",
          "eventId",
          "actorSubject",
          "httpMethod",
          "httpStatus",
          "routeId",
          "dependencyService",
          "queue");
  private static final String INSTANCE = setting("INSTANCE_ID", UUID.randomUUID().toString());

  @Override
  public void customize(JsonWriter.Members<ILoggingEvent> members) {
    // Emit a fresh, prefixed map. Native fields/MDC never bypass the allowlist, and each output
    // key is written once (including trace_id versus traceId and native @timestamp).
    members.applyingPathFilter(
        path -> {
          String name = path.name();
          return name == null || !name.startsWith("b16_");
        });
    members.applyingNameProcessor(
        (path, name) -> name.startsWith("b16_") ? name.substring(4) : name);
    members.addMapEntries(this::fields);
  }

  private Map<String, Object> fields(ILoggingEvent event) {
    Map<String, Object> values = new LinkedHashMap<>();
    event
        .getMDCPropertyMap()
        .forEach(
            (key, value) -> {
              if (ALLOWED.contains(key)) values.put(key, value);
            });
    if (event.getKeyValuePairs() != null)
      event
          .getKeyValuePairs()
          .forEach(
              pair -> {
                if (ALLOWED.contains(pair.key) && pair.value != null)
                  values.put(pair.key, scalar(pair.value));
              });
    // The agent's active span takes precedence over a propagated parent stored in custom MDC.
    var mdc = event.getMDCPropertyMap();
    for (String kind : List.of("trace", "span")) {
      String value = mdc.getOrDefault(kind + "_id", Objects.toString(values.get(kind + "Id"), ""));
      if (value.matches(kind.equals("trace") ? "[0-9a-f]{32}" : "[0-9a-f]{16}")
          && !value.chars().allMatch(c -> c == '0')) values.put(kind + "Id", value);
      else values.remove(kind + "Id");
    }
    values.put("schemaVersion", 1);
    values.put("timestamp", event.getInstant().toString());
    String severity = event.getLevel().toString();
    values.put("severity", severity.equals("TRACE") ? "DEBUG" : severity);
    values.put("serviceName", "api-gateway");
    values.put(
        "serviceVersion",
        setting("SERVICE_VERSION", setting("API_GATEWAY_SERVICE_VERSION", "local-dev")));
    values.put("instanceId", INSTANCE);
    values.put("environment", setting("ENVIRONMENT", setting("API_GATEWAY_ENVIRONMENT", "local")));
    values.put("hostId", setting("HOST_ID", setting("API_GATEWAY_HOST_ID", "local-test")));
    values.put("loggerName", event.getLoggerName());
    values.put(
        "message",
        event.getLoggerName().startsWith("com.flashticket")
            ? event.getFormattedMessage()
            : "Library diagnostic");
    values.putIfAbsent("eventName", "runtime.log");
    values.putIfAbsent("operation", "runtime");
    values.putIfAbsent("outcome", "UNKNOWN");
    if (event.getThrowableProxy() instanceof ThrowableProxy proxy) {
      values.put(
          "stackTrace", new SafeStackTracePrinter().printStackTraceToString(proxy.getThrowable()));
      values.putIfAbsent("errorType", proxy.getClassName());
    }
    Map<String, Object> prefixed = new LinkedHashMap<>();
    values.forEach(
        (key, value) -> {
          if (value != null) prefixed.put("b16_" + key, value);
        });
    return prefixed;
  }

  private static Object scalar(Object value) {
    return value instanceof Number || value instanceof Boolean || value instanceof String
        ? value
        : value.toString();
  }

  private static String setting(String key, String fallback) {
    String value = System.getenv(key);
    return value != null && value.matches("[A-Za-z0-9][A-Za-z0-9._-]{0,127}") ? value : fallback;
  }
}
