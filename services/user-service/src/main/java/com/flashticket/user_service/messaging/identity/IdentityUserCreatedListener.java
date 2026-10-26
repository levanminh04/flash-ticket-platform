package com.flashticket.user_service.messaging.identity;

import com.rabbitmq.client.Channel;
import java.io.IOException;
import java.math.BigInteger;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.core.retry.RetryTemplate;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

public final class IdentityUserCreatedListener {
  private static final Logger LOG = LoggerFactory.getLogger(IdentityUserCreatedListener.class);
  private static final Set<String> FIELDS =
      Set.of(
          "schemaVersion",
          "eventId",
          "eventType",
          "occurredAt",
          "realmId",
          "identitySubject",
          "displayName");
  private final JsonMapper mapper;
  private final IdentityUserRegistrar registrar;
  private final String realmId;
  private final RetryTemplate retry;

  public IdentityUserCreatedListener(
      JsonMapper mapper, IdentityUserRegistrar registrar, String realmId, RetryTemplate retry) {
    if (realmId == null || realmId.isBlank())
      throw new IllegalArgumentException("Missing identity realm");
    this.mapper = mapper.rebuild().build();
    this.registrar = registrar;
    this.realmId = realmId;
    this.retry = new RetryTemplate(retry.getRetryPolicy());
  }

  @RabbitListener(
      queues = "user.identity.q",
      concurrency = "1",
      containerFactory = "identityRabbitListenerContainerFactory")
  public void onMessage(Message message, Channel channel) throws IOException {
    long tag = message.getMessageProperties().getDeliveryTag();
    UserCreatedV1 event;
    try {
      event = decode(message);
    } catch (RuntimeException invalid) {
      LOG.warn("Identity message rejected: INVALID_IDENTITY_EVENT");
      channel.basicReject(tag, false);
      return;
    }
    try {
      retry.invoke(() -> registrar.ensureRegistered(event));
    } catch (RuntimeException exhausted) {
      LOG.error("Identity write rejected: IDENTITY_WRITE_FAILED eventId={}", event.eventId());
      channel.basicReject(tag, false);
      return;
    }
    // ACK is deliberately outside DB retry. A failed channel must redeliver on a new tag.
    try {
      channel.basicAck(tag, false);
    } catch (IOException | RuntimeException ackFailure) {
      try {
        channel.abort();
      } catch (Exception ignored) {
        /* Broker recovers unsettled delivery. */
      }
      throw ackFailure;
    }
    LOG.info("Identity profile ensured eventId={}", event.eventId());
  }

  private UserCreatedV1 decode(Message message) {
    if (!UserCreatedV1.TYPE.equals(message.getMessageProperties().getReceivedRoutingKey())
        || message.getBody().length > 65536) throw new IllegalArgumentException("Invalid envelope");
    JsonNode tree = mapper.readTree(message.getBody());
    if (!tree.isObject()
        || tree.properties().stream().anyMatch(field -> !FIELDS.contains(field.getKey())))
      throw new IllegalArgumentException("Unknown field");
    for (String field :
        Set.of("eventId", "eventType", "occurredAt", "realmId", "identitySubject")) {
      JsonNode value = tree.get(field);
      if (value == null || !value.isString() || value.asString().isBlank())
        throw new IllegalArgumentException("Missing string field");
    }
    JsonNode version = tree.get("schemaVersion");
    if (version == null
        || !version.isIntegralNumber()
        || !BigInteger.ONE.equals(version.bigIntegerValue())
        || !tree.get("eventId")
            .asString()
            .matches("[0-9a-fA-F]{8}(-[0-9a-fA-F]{4}){3}-[0-9a-fA-F]{12}")
        || !tree.get("occurredAt").asString().endsWith("Z"))
      throw new IllegalArgumentException("Invalid identity schema");
    if (tree.has("displayName")
        && (!tree.get("displayName").isString() || tree.get("displayName").asString().isBlank()))
      throw new IllegalArgumentException("Invalid displayName");
    UserCreatedV1 event = mapper.treeToValue(tree, UserCreatedV1.class);
    if (!realmId.equals(event.realmId())) throw new IllegalArgumentException("Unexpected realm");
    return event;
  }
}
