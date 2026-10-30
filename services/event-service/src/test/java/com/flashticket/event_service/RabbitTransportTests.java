package com.flashticket.event_service;

import static org.assertj.core.api.Assertions.*;

import com.flashticket.event_service.domain.EventData;
import com.flashticket.event_service.messaging.*;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.amqp.core.*;
import org.springframework.amqp.rabbit.connection.CachingConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitAdmin;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.testcontainers.containers.GenericContainer;
import tools.jackson.databind.json.JsonMapper;

/** Real AMQP transport only; never connects to the shared business vhost. */
class RabbitTransportTests {
  static GenericContainer<?> broker;
  static CachingConnectionFactory connection;
  static RabbitTemplate rabbit;
  static RabbitAdmin admin;
  static RabbitMessagePublisher publisher;
  static final JsonMapper JSON = JsonMapper.builder().build();
  static final String TRACE = "00-1234567890abcdef1234567890abcdef-1234567890abcdef-01";
  String queue;
  Binding binding;

  @BeforeAll
  static void connect() {
    String host = System.getenv("EVENT_TEST_AMQP_HOST");
    String user;
    String password;
    String vhost;
    int port;
    if (host == null) {
      user = "isolated-event";
      password = UUID.randomUUID().toString();
      vhost = "/";
      broker =
          new GenericContainer<>("rabbitmq:4.3.6-alpine")
              .withEnv("RABBITMQ_DEFAULT_USER", user)
              .withEnv("RABBITMQ_DEFAULT_PASS", password)
              .withExposedPorts(5672);
      broker.start();
      host = broker.getHost();
      port = broker.getMappedPort(5672);
    } else {
      user = required("EVENT_TEST_AMQP_USER");
      password = required("EVENT_TEST_AMQP_PASSWORD");
      vhost = required("EVENT_TEST_AMQP_VHOST");
      port = Integer.parseInt(required("EVENT_TEST_AMQP_PORT"));
      if (!vhost.matches("event-verify-[a-f0-9]{32}") || !user.equals(vhost))
        throw new IllegalArgumentException(
            "External transport tests require a dedicated task vhost/user");
    }
    connection = new CachingConnectionFactory(host, port);
    connection.setUsername(user);
    connection.setPassword(password);
    connection.setVirtualHost(vhost);
    connection.setPublisherConfirmType(CachingConnectionFactory.ConfirmType.CORRELATED);
    connection.setPublisherReturns(true);
    rabbit = new RabbitTemplate(connection);
    rabbit.setExchange("business.ex");
    rabbit.setMandatory(true);
    admin = new RabbitAdmin(connection);
    admin.declareExchange(new TopicExchange("business.ex", true, false));
    publisher = new RabbitMessagePublisher(rabbit, JSON, Duration.ofSeconds(5));
  }

  @AfterAll
  static void close() {
    if (connection != null) connection.destroy();
    if (broker != null) broker.stop();
  }

  @BeforeEach
  void destination() {
    queue = "event-transport-" + UUID.randomUUID();
    admin.declareQueue(QueueBuilder.durable(queue).quorum().build());
    binding =
        BindingBuilder.bind(new Queue(queue)).to(new TopicExchange("business.ex")).with("event.#");
    admin.declareBinding(binding);
  }

  @AfterEach
  void removeDestination() {
    if (admin != null && queue != null) admin.deleteQueue(queue);
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "event.sales-configured.v1", "event.access-configured.v1",
        "event.finance-configured.v1", "event.cancelled.v1"
      })
  void confirmedMessagesKeepEnvelopeAndTransportMetadata(String type) {
    var saved = message(type);
    publisher.publish(saved);
    var received = rabbit.receive(queue, 5000);
    assertThat(received).isNotNull();
    var properties = received.getMessageProperties();
    assertThat(properties.getReceivedDeliveryMode()).isEqualTo(MessageDeliveryMode.PERSISTENT);
    assertThat(properties.getContentType()).isEqualTo("application/json");
    assertThat(properties.getMessageId()).isEqualTo(saved.id().toString());
    assertThat(properties.getType()).isEqualTo(type);
    assertThat(properties.getReceivedExchange()).isEqualTo("business.ex");
    assertThat(properties.getReceivedRoutingKey()).isEqualTo(type);
    assertThat(properties.getCorrelationId()).isEqualTo(saved.correlationId().toString());
    assertThat((String) properties.getHeader("traceparent")).isEqualTo(TRACE);
    var body = JSON.readTree(received.getBody());
    assertThat(body.path("messageId").stringValue()).isEqualTo(saved.id().toString());
    assertThat(body.path("messageType").stringValue()).isEqualTo(type);
    assertThat(body.path("aggregateId").stringValue()).isEqualTo(saved.aggregateId().toString());
    assertThat(body.path("occurredAt").stringValue()).isEqualTo(saved.occurredAt().toString());
    assertThat(body.path("schemaVersion").intValue()).isEqualTo(1);
    assertThat(body.path("aggregateVersion").stringValue()).isEqualTo("9007199254740993");
    assertThat(body.path("causationId").stringValue()).isEqualTo(saved.causationId().toString());
    assertThat(body.path("correlationId").stringValue())
        .isEqualTo(saved.correlationId().toString());
    assertThat(body.path("traceparent").stringValue()).isEqualTo(TRACE);
    assertThat(body.path("data").path("eventId").stringValue())
        .isEqualTo(saved.aggregateId().toString());
    assertThat(body.path("data")).isEqualTo(JSON.readTree(saved.payload()));
    assertThat(rabbit.receive(queue)).isNull();
  }

  @Test
  void mandatoryReturnDoesNotSucceedAndSameMessageCanBeRetriedAfterBindingRecovery() {
    admin.removeBinding(binding);
    var saved = message(EventSnapshots.CANCELLED);
    assertThatThrownBy(() -> publisher.publish(saved))
        .isInstanceOfSatisfying(
            MessagePublisher.Failure.class,
            error -> assertThat(error.code()).isEqualTo("UNROUTABLE"));
    assertThat(rabbit.receive(queue)).isNull();
    admin.declareBinding(binding);
    publisher.publish(saved);
    var received = rabbit.receive(queue, 5000);
    assertThat(received).isNotNull();
    assertThat(received.getMessageProperties().getMessageId()).isEqualTo(saved.id().toString());
    assertThat(rabbit.receive(queue)).isNull();
  }

  @Test
  void reconnectAndReplayPreserveBytesAndIdentity() {
    var saved = message(EventSnapshots.SALES);
    publisher.publish(saved);
    var first = rabbit.receive(queue, 5000);
    assertThat(first).isNotNull();
    connection.resetConnection();
    publisher.publish(saved);
    var replay = rabbit.receive(queue, 5000);
    assertThat(replay).isNotNull();
    assertThat(replay.getBody()).isEqualTo(first.getBody());
    assertThat(replay.getMessageProperties().getMessageId()).isEqualTo(saved.id().toString());
  }

  private static String required(String key) {
    String value = System.getenv(key);
    if (value == null || value.isBlank()) throw new IllegalArgumentException("Missing " + key);
    return value;
  }

  private static OutboxStore.Message message(String type) {
    UUID eventId = UUID.randomUUID();
    Instant now = Instant.parse("2026-10-30T00:00:00Z");
    var event =
        new EventData(
            eventId,
            "transport-probe-organizer",
            null,
            null,
            "Probe hall",
            "Isolated transport",
            "",
            "QUANTITY",
            type.equals(EventSnapshots.CANCELLED) ? "CANCELLED" : "PUBLISHED",
            now.plusSeconds(86400),
            now.plusSeconds(90000),
            now.minusSeconds(3600),
            now.plusSeconds(80000),
            4,
            new BigDecimal("0.1"),
            "probe-admin",
            now,
            9007199254740993L,
            0,
            now,
            now,
            List.of(),
            List.of(
                new EventData.TicketType(
                    UUID.randomUUID(), "Standard", new BigDecimal("100000"), 10, null)),
            List.of(),
            null);
    Object data =
        switch (type) {
          case EventSnapshots.SALES -> EventSnapshots.sales(event);
          case EventSnapshots.ACCESS -> EventSnapshots.access(event);
          case EventSnapshots.FINANCE -> EventSnapshots.finance(event);
          case EventSnapshots.CANCELLED ->
              new EventSnapshots.Cancellation(
                  eventId,
                  UUID.randomUUID(),
                  "9007199254740993",
                  now,
                  "probe-admin",
                  EventSnapshots.sales(event),
                  EventSnapshots.access(event));
          default -> throw new IllegalArgumentException("Unexpected test message type");
        };
    return new OutboxStore.Message(
        UUID.randomUUID(),
        eventId,
        9007199254740993L,
        type,
        1,
        JSON.writeValueAsString(data),
        now,
        UUID.randomUUID(),
        UUID.randomUUID(),
        TRACE,
        1);
  }
}
