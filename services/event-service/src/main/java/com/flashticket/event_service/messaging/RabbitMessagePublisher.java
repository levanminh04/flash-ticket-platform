package com.flashticket.event_service.messaging;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageDeliveryMode;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

@Component
public class RabbitMessagePublisher implements MessagePublisher {
  private final RabbitTemplate rabbit;
  private final JsonMapper json;
  private final Duration timeout;

  @edu.umd.cs.findbugs.annotations.SuppressFBWarnings(
      value = "EI_EXPOSE_REP2",
      justification =
          "Intentionally shares Spring-managed infrastructure; no dependency accessor is exposed.")
  public RabbitMessagePublisher(
      RabbitTemplate rabbit,
      JsonMapper json,
      @Value("${event.outbox.confirm-timeout:5s}") Duration timeout) {
    this.rabbit = rabbit;
    this.json = json;
    this.timeout = timeout;
  }

  public record Envelope(
      java.util.UUID messageId,
      String messageType,
      int schemaVersion,
      java.util.UUID aggregateId,
      String aggregateVersion,
      java.time.Instant occurredAt,
      java.util.UUID correlationId,
      java.util.UUID causationId,
      String traceparent,
      tools.jackson.databind.JsonNode data) {}

  @Override
  public void publish(OutboxStore.Message message) {
    if (timeout.isZero() || timeout.isNegative())
      throw new IllegalArgumentException("Confirm timeout must be positive");
    if (message.schemaVersion() != 1
        || !Set.of(EventSnapshots.SALES, EventSnapshots.ACCESS, EventSnapshots.FINANCE)
            .contains(message.type())) throw new Failure("INVALID_MESSAGE");
    try {
      var payload = json.readTree(message.payload());
      if (!payload.isObject()) throw new Failure("INVALID_MESSAGE");
      var envelope =
          new Envelope(
              message.id(),
              message.type(),
              1,
              message.aggregateId(),
              Long.toString(message.version()),
              message.occurredAt(),
              message.correlationId(),
              message.causationId(),
              message.traceparent(),
              payload);
      var properties = new MessageProperties();
      properties.setContentType(MessageProperties.CONTENT_TYPE_JSON);
      properties.setContentEncoding(StandardCharsets.UTF_8.name());
      properties.setDeliveryMode(MessageDeliveryMode.PERSISTENT);
      properties.setMessageId(message.id().toString());
      properties.setType(message.type());
      properties.setCorrelationId(message.correlationId().toString());
      if (message.traceparent() != null) properties.setHeader("traceparent", message.traceparent());
      var correlation = new CorrelationData(message.id().toString());
      rabbit.send(
          rabbit.getExchange(),
          message.type(),
          new Message(json.writeValueAsBytes(envelope), properties),
          correlation);
      var confirm = correlation.getFuture().get(timeout.toMillis(), TimeUnit.MILLISECONDS);
      if (!confirm.ack()) throw new Failure("BROKER_NACK");
      if (correlation.getReturned() != null) throw new Failure("UNROUTABLE");
    } catch (Failure error) {
      throw error;
    } catch (TimeoutException error) {
      throw new Failure("CONFIRM_TIMEOUT");
    } catch (InterruptedException error) {
      Thread.currentThread().interrupt();
      throw new Failure("PUBLISH_INTERRUPTED");
    } catch (tools.jackson.core.JacksonException error) {
      throw new Failure("INVALID_MESSAGE");
    } catch (java.util.concurrent.ExecutionException
        | org.springframework.amqp.AmqpException error) {
      throw new Failure("BROKER_UNAVAILABLE");
    }
  }
}
