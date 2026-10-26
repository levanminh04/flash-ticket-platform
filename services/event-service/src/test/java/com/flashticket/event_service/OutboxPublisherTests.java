package com.flashticket.event_service;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.flashticket.event_service.messaging.*;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.*;
import org.springframework.amqp.core.*;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import tools.jackson.databind.json.JsonMapper;

class OutboxPublisherTests {
  RabbitTemplate rabbit;
  JsonMapper json;
  RabbitMessagePublisher publisher;

  static OutboxStore.Message message() {
    return new OutboxStore.Message(
        UUID.randomUUID(),
        UUID.randomUUID(),
        7,
        EventSnapshots.SALES,
        1,
        "{\"schemaVersion\":1}",
        Instant.parse("2027-01-01T00:00:00Z"),
        UUID.randomUUID(),
        null,
        null,
        1);
  }

  @BeforeEach
  void setup() {
    rabbit = mock(RabbitTemplate.class);
    json = new JsonMapper();
    when(rabbit.getExchange()).thenReturn("business.ex");
    publisher = new RabbitMessagePublisher(rabbit, json, Duration.ofMillis(20));
  }

  @Test
  void ackUsesPersistentStableEnvelopeAndBusinessRouting() {
    var saved = message();
    var bodies = new ArrayList<String>();
    doAnswer(
            invocation -> {
              Message body = invocation.getArgument(2);
              CorrelationData correlation = invocation.getArgument(3);
              bodies.add(new String(body.getBody(), java.nio.charset.StandardCharsets.UTF_8));
              assertThat(body.getMessageProperties().getDeliveryMode())
                  .isEqualTo(MessageDeliveryMode.PERSISTENT);
              correlation.getFuture().complete(new CorrelationData.Confirm(true, null));
              return null;
            })
        .when(rabbit)
        .send(
            eq("business.ex"),
            eq(EventSnapshots.SALES),
            any(Message.class),
            any(CorrelationData.class));
    publisher.publish(saved);
    publisher.publish(saved);
    assertThat(bodies).hasSize(2);
    assertThat(bodies.getFirst()).isEqualTo(bodies.getLast());
    var envelope = json.readTree(bodies.getFirst());
    assertThat(envelope.path("messageId").stringValue()).isEqualTo(saved.id().toString());
    assertThat(envelope.path("aggregateVersion").stringValue()).isEqualTo("7");
    assertThat(envelope.path("data").isObject()).isTrue();
  }

  @Test
  void ackWithReturnIsNotSuccessfulDelivery() {
    doAnswer(
            invocation -> {
              Message body = invocation.getArgument(2);
              CorrelationData correlation = invocation.getArgument(3);
              correlation.setReturned(
                  new ReturnedMessage(body, 312, "NO_ROUTE", "business.ex", EventSnapshots.SALES));
              correlation.getFuture().complete(new CorrelationData.Confirm(true, null));
              return null;
            })
        .when(rabbit)
        .send(anyString(), anyString(), any(Message.class), any(CorrelationData.class));
    assertThatThrownBy(() -> publisher.publish(message()))
        .isInstanceOf(MessagePublisher.Failure.class)
        .hasMessage("UNROUTABLE");
  }

  @Test
  void nackDoesNotCountAsPublished() {
    doAnswer(
            invocation -> {
              CorrelationData correlation = invocation.getArgument(3);
              correlation.getFuture().complete(new CorrelationData.Confirm(false, "rejected"));
              return null;
            })
        .when(rabbit)
        .send(anyString(), anyString(), any(Message.class), any(CorrelationData.class));
    assertThatThrownBy(() -> publisher.publish(message()))
        .isInstanceOf(MessagePublisher.Failure.class)
        .hasMessage("BROKER_NACK");
  }

  @Test
  void missingConfirmIsRetried() {
    assertThatThrownBy(() -> publisher.publish(message()))
        .isInstanceOf(MessagePublisher.Failure.class)
        .hasMessage("CONFIRM_TIMEOUT");
  }

  @Test
  void brokenConnectionIsSanitized() {
    doThrow(new org.springframework.amqp.AmqpException("secret must not persist"))
        .when(rabbit)
        .send(anyString(), anyString(), any(Message.class), any(CorrelationData.class));
    assertThatThrownBy(() -> publisher.publish(message()))
        .isInstanceOf(MessagePublisher.Failure.class)
        .hasMessage("BROKER_UNAVAILABLE");
  }
}
