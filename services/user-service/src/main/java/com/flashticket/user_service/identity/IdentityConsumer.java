package com.flashticket.user_service.identity;

import com.flashticket.user_service.observability.UserTelemetry;
import com.flashticket.user_service.persistence.UserStore;
import com.rabbitmq.client.Channel;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import jakarta.validation.Valid;
import jakarta.validation.Validator;
import jakarta.validation.constraints.*;
import java.io.IOException;
import java.time.Instant;
import java.util.UUID;
import org.slf4j.MDC;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

/** Identity wire contract is documented in this module's README; no broker topology is declared. */
@Component
@ConditionalOnProperty(name = "user.identity.enabled", havingValue = "true", matchIfMissing = true)
public final class IdentityConsumer {
  public record Data(
      @NotBlank String realmId,
      @NotBlank @Size(max = 512) String identitySubject,
      @Size(max = 200) @Pattern(regexp = "(?s).*\\S.*") String displayName) {}

  public record Created(
      @NotNull UUID messageId,
      @NotNull @Pattern(regexp = "identity\\.user-created\\.v1") String messageType,
      @Min(1) @Max(1) int schemaVersion,
      @NotNull UUID aggregateId,
      @NotNull @Pattern(regexp = "[1-9][0-9]*") String aggregateVersion,
      @NotNull Instant occurredAt,
      @NotNull UUID correlationId,
      UUID causationId,
      String traceparent,
      @NotNull @Valid Data data) {}

  private final JsonMapper json;
  private final Validator validator;
  private final UserStore store;
  private final UserTelemetry telemetry;
  private final int maxBytes;

  @SuppressFBWarnings(
      value = "EI_EXPOSE_REP2",
      justification =
          "Uses the shared, immutable-after-construction Jackson 3 mapper configured by Spring.")
  public IdentityConsumer(
      JsonMapper json,
      Validator validator,
      UserStore store,
      UserTelemetry telemetry,
      @Value("${user.identity.max-message-bytes:65536}") int maxBytes) {
    if (maxBytes < 1) throw new IllegalArgumentException("Message size limit must be positive");
    this.json = json;
    this.validator = validator;
    this.store = store;
    this.telemetry = telemetry;
    this.maxBytes = maxBytes;
  }

  @RabbitListener(queues = "${user.identity.queue:user.identity.q}", ackMode = "MANUAL")
  public void receive(Message message, Channel channel) throws IOException {
    long tag = message.getMessageProperties().getDeliveryTag();
    Created event;
    try {
      if (message.getBody().length > maxBytes)
        throw new IllegalArgumentException("Message too large");
      event = json.readValue(message.getBody(), Created.class);
      if (event == null
          || !validator.validate(event).isEmpty()
          || !"identity.user-created.v1"
              .equals(message.getMessageProperties().getReceivedRoutingKey()))
        throw new IllegalArgumentException("Invalid identity message");
      if (event.traceparent() != null
          && (!event.traceparent().matches("00-[0-9a-f]{32}-[0-9a-f]{16}-[0-9a-f]{2}")
              || event.traceparent().substring(3, 35).equals("0".repeat(32))
              || event.traceparent().substring(36, 52).equals("0".repeat(16))))
        throw new IllegalArgumentException("Invalid traceparent");
    } catch (RuntimeException ex) {
      telemetry.decision(
          "identity-consume", "REJECTED", "UNKNOWN", "UNKNOWN", "INVALID_IDENTITY_MESSAGE");
      channel.basicReject(tag, false);
      return;
    }
    var previous = MDC.getCopyOfContextMap();
    try {
      MDC.put("correlationId", event.correlationId().toString());
      MDC.put("messageId", event.messageId().toString());
      if (event.causationId() != null) MDC.put("causationId", event.causationId().toString());
      try {
        store.ensureUser(event.data().identitySubject(), event.data().displayName());
      } catch (RuntimeException ex) {
        telemetry.decision(
            "identity-consume", "RETRY", "UNKNOWN", "UNKNOWN", "IDENTITY_STORAGE_UNAVAILABLE");
        // Existing quorum delivery-limit/DLX policy bounds requeues; ACK only after durable
        // storage.
        channel.basicNack(tag, false, true);
        return;
      }
      channel.basicAck(tag, false);
      telemetry.decision("identity-consume", "SUCCESS", "UNKNOWN", "PRESENT", "IDENTITY_STORED");
    } finally {
      if (previous == null) MDC.clear();
      else MDC.setContextMap(previous);
    }
  }
}
