package com.flashticket.keycloak;

import com.rabbitmq.client.ConnectionFactory;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import org.keycloak.Config;
import org.keycloak.events.*;
import org.keycloak.events.outbox.*;
import org.keycloak.models.*;
import org.keycloak.models.utils.PostMigrationEvent;
import org.keycloak.timer.TimerProvider;

public final class RegistrationEventListenerFactory implements EventListenerProviderFactory {
  public static final String ID = "flash-ticket-registration-outbox";
  private final AtomicBoolean scheduled = new AtomicBoolean();
  private KeycloakSessionFactory sessions;
  private RabbitMqOutboxDeliveryHandler publisher;
  private boolean enabled;
  private long interval;

  @Override public void init(Config.Scope config) {
    enabled = Boolean.parseBoolean(env("FT_IDENTITY_OUTBOX_ENABLED", "true"));
    interval = Long.parseLong(env("FT_IDENTITY_OUTBOX_INTERVAL_MS", "2000"));
    if (interval < 1000) throw new IllegalArgumentException("Outbox interval must be >= 1000ms");
    if (!enabled) return;
    ConnectionFactory connection = new ConnectionFactory();
    connection.setHost(env("FT_IDENTITY_RABBITMQ_HOST", "rabbitmq"));
    connection.setPort(Integer.parseInt(env("FT_IDENTITY_RABBITMQ_PORT", "5672")));
    connection.setVirtualHost(env("FT_IDENTITY_RABBITMQ_VHOST", "/"));
    connection.setUsername(required("FT_IDENTITY_RABBITMQ_USERNAME"));
    connection.setPassword(required("FT_IDENTITY_RABBITMQ_PASSWORD"));
    connection.setAutomaticRecoveryEnabled(false);
    connection.setConnectionTimeout(2000);
    connection.setHandshakeTimeout(2000);
    connection.setChannelRpcTimeout(2000);
    connection.setRequestedHeartbeat(15);
    publisher = new RabbitMqOutboxDeliveryHandler(connection);
  }

  private static String env(String key, String fallback) {
    return System.getenv().getOrDefault(key, fallback);
  }
  private static String required(String key) {
    String value = System.getenv(key);
    if (value == null || value.isBlank()) throw new IllegalArgumentException("Missing " + key);
    return value;
  }

  @Override public EventListenerProvider create(KeycloakSession session) {
    if (enabled) return new RegistrationEventListener(session);
    return new EventListenerProvider() {
      public void onEvent(Event event) {}
      public void onEvent(org.keycloak.events.admin.AdminEvent event, boolean representation) {}
      public void close() {}
    };
  }

  @Override public void postInit(KeycloakSessionFactory factory) {
    sessions = factory;
    factory.register(event -> {
      if (event instanceof PostMigrationEvent && enabled && scheduled.compareAndSet(false, true)) {
        var backoff = new OutboxBackoff(Integer.MAX_VALUE, List.of(Duration.ofSeconds(2),
            Duration.ofSeconds(5), Duration.ofSeconds(10), Duration.ofSeconds(30), Duration.ofSeconds(60)));
        var settings = new OutboxConfig(RegistrationEventListener.KIND, 1, backoff,
            null, Duration.ofDays(7), null);
        // ponytail: batch1 bounds network time while holding a row lock; increase only after measuring backlog.
        try (var session = factory.create()) {
          session.getProvider(TimerProvider.class).scheduleTask(
              new OutboxDrainerTask(settings, publisher, OutboxStore::new), interval, ID);
        }
      }
    });
  }

  @Override public String getId() { return ID; }
  @Override public void close() {
    if (scheduled.get() && sessions != null) {
      try (var session = sessions.create()) {
        session.getProvider(TimerProvider.class).cancelTask(ID);
      }
    }
    if (publisher != null) publisher.close();
  }
}
