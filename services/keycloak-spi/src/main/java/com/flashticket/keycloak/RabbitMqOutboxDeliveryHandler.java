package com.flashticket.keycloak;

import com.rabbitmq.client.*;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicBoolean;
import org.keycloak.events.outbox.*;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.jpa.entities.OutboxEntryEntity;

/** One in-flight mandatory publication per channel; the native drainer owns retry/state. */
public final class RabbitMqOutboxDeliveryHandler implements OutboxDeliveryHandler, AutoCloseable {
  private final ConnectionFactory factory;
  private Connection connection;

  RabbitMqOutboxDeliveryHandler(ConnectionFactory factory) { this.factory = factory; }
  @Override public String entryKind() { return RegistrationEventListener.KIND; }

  @Override public synchronized OutboxDeliveryResult deliver(KeycloakSession session, OutboxEntryEntity row) {
    Channel channel = null;
    try {
      if (connection == null || !connection.isOpen()) connection = factory.newConnection();
      channel = connection.createChannel();
      channel.confirmSelect();
      AtomicBoolean returned = new AtomicBoolean(false);
      channel.addReturnListener((ReturnCallback) reply -> returned.set(true));
      var properties = new AMQP.BasicProperties.Builder().contentType("application/json")
          .contentEncoding("UTF-8").deliveryMode(2).messageId(row.getCorrelationId()).build();
      channel.basicPublish("identity.ex", UserCreatedV1.TYPE, true, properties,
          row.getPayload().getBytes(StandardCharsets.UTF_8));
      if (!channel.waitForConfirms(2000)) return OutboxDeliveryResult.retry("BROKER_NACK");
      if (returned.get()) return OutboxDeliveryResult.retry("BROKER_UNROUTABLE");
      return OutboxDeliveryResult.delivered();
    } catch (InterruptedException interrupted) {
      Thread.currentThread().interrupt();
      return OutboxDeliveryResult.retry("BROKER_INTERRUPTED");
    } catch (Exception failure) {
      return OutboxDeliveryResult.retry("BROKER_UNAVAILABLE");
    } finally {
      if (channel != null) {
        try { channel.abort(); } catch (Exception ignored) { /* Connection recovery handles a dead channel. */ }
      }
    }
  }

  @Override public synchronized void close() {
    if (connection != null) connection.abort();
    connection = null;
  }
}
