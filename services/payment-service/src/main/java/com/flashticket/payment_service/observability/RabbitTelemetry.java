package com.flashticket.payment_service.observability;

import io.opentelemetry.api.trace.Span;
import java.util.*;
import java.util.concurrent.TimeUnit;
import org.slf4j.*;
import org.springframework.amqp.core.Message;

/** Enriches the existing consumer span, never creates an SDK/exporter or changes ACK policy. */
public final class RabbitTelemetry implements AutoCloseable {
  private static final Logger LOG = LoggerFactory.getLogger(RabbitTelemetry.class);
  private final Map<String, String> previous = MDC.getCopyOfContextMap();
  private final long started = System.nanoTime();
  private final String queue;
  private String outcome = "FAILED";
  private String reason = "MESSAGE_PROCESSING_FAILED";

  public RabbitTelemetry(Message message) {
    queue = Objects.toString(message.getMessageProperties().getConsumerQueue(), "unknown");
    if (!queue.equals("unknown")) {
      Span.current().setAttribute("messaging.destination.name", queue);
      Span.current().setAttribute("rabbitmq.queue", queue);
    }
  }

  public void context(UUID message, UUID correlation) {
    if (message != null) MDC.put("messageId", message.toString());
    if (correlation != null) MDC.put("correlationId", correlation.toString());
  }

  public void result(String outcome, String reason) {
    this.outcome = outcome;
    this.reason = reason;
  }

  @Override
  public void close() {
    try {
      LOG.atInfo()
          .addKeyValue("eventName", "payment.message.processed")
          .addKeyValue("operation", "message-process")
          .addKeyValue("outcome", outcome)
          .addKeyValue("reasonCode", reason)
          .addKeyValue("queue", queue)
          .addKeyValue("durationMs", TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started))
          .log("Message processing result");
    } finally {
      if (previous == null) MDC.clear();
      else MDC.setContextMap(previous);
    }
  }
}
