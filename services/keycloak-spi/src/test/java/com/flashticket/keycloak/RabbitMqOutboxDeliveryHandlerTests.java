package com.flashticket.keycloak;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import com.rabbitmq.client.*;
import java.io.IOException;
import java.util.concurrent.TimeoutException;
import org.junit.jupiter.api.*;
import org.keycloak.events.outbox.OutboxDeliveryOutcome;
import org.keycloak.models.jpa.entities.OutboxEntryEntity;
import org.mockito.ArgumentCaptor;

class RabbitMqOutboxDeliveryHandlerTests {
  ConnectionFactory factory = mock(ConnectionFactory.class);
  Connection connection = mock(Connection.class);
  Channel channel = mock(Channel.class);
  OutboxEntryEntity row = mock(OutboxEntryEntity.class);

  @BeforeEach void setup() throws Exception {
    when(factory.newConnection()).thenReturn(connection);
    when(connection.isOpen()).thenReturn(true);
    when(connection.createChannel()).thenReturn(channel);
    when(row.getPayload()).thenReturn("{\"eventId\":\"test\"}");
    when(row.getCorrelationId()).thenReturn("stable-event-id");
  }

  @Test void confirmsPersistentMandatoryPublishWithStableId() throws Exception {
    var handler = new RabbitMqOutboxDeliveryHandler(factory);
    assertEquals(OutboxDeliveryOutcome.DELIVERED, handler.deliver(null, row).outcome());
    var properties = ArgumentCaptor.forClass(AMQP.BasicProperties.class);
    verify(channel).basicPublish(eq("identity.ex"), eq("identity.user-created.v1"), eq(true),
        properties.capture(), any(byte[].class));
    assertEquals(2, properties.getValue().getDeliveryMode());
    assertEquals("stable-event-id", properties.getValue().getMessageId());
    assertEquals("application/json", properties.getValue().getContentType());
    verify(channel).waitForConfirmsOrDie(2000);
    verify(channel).close();
    handler.close();
  }

  @Test void returnedMessageIsNotDeliveredEvenWithConfirmAck() throws Exception {
    var callback = new ReturnCallback[1];
    doAnswer(call -> { callback[0] = call.getArgument(0); return null; })
        .when(channel).addReturnListener(any(ReturnCallback.class));
    doAnswer(call -> {
      callback[0].handle(new Return(312, "NO_ROUTE", "identity.ex", "identity.user-created.v1",
          new AMQP.BasicProperties(), new byte[0])); return null;
    }).when(channel).basicPublish(anyString(), anyString(), anyBoolean(), any(), any(byte[].class));
    assertEquals(OutboxDeliveryOutcome.RETRY,
        new RabbitMqOutboxDeliveryHandler(factory).deliver(null, row).outcome());
  }

  @Test void negativeConfirmAndTimeoutRemainRetryable() throws Exception {
    doThrow(new IOException("NACK")).when(channel).waitForConfirmsOrDie(2000);
    var handler = new RabbitMqOutboxDeliveryHandler(factory);
    assertEquals(OutboxDeliveryOutcome.RETRY, handler.deliver(null, row).outcome());
    doThrow(new TimeoutException()).when(channel).waitForConfirmsOrDie(2000);
    assertEquals(OutboxDeliveryOutcome.RETRY, handler.deliver(null, row).outcome());
    verify(channel, times(2)).close();
  }

  @Test void channelCloseFailureRemainsRetryable() throws Exception {
    doThrow(new IOException("close failed")).when(channel).close();
    assertEquals(OutboxDeliveryOutcome.RETRY,
        new RabbitMqOutboxDeliveryHandler(factory).deliver(null, row).outcome());
  }

  @Test void initialConnectionFailureIsRetriedOnNextDelivery() throws Exception {
    when(factory.newConnection()).thenThrow(new IOException("private credentials")).thenReturn(connection);
    var handler = new RabbitMqOutboxDeliveryHandler(factory);
    var failed = handler.deliver(null, row);
    assertEquals(OutboxDeliveryOutcome.RETRY, failed.outcome());
    assertFalse(failed.errorMessage().contains("private"));
    assertEquals(OutboxDeliveryOutcome.DELIVERED, handler.deliver(null, row).outcome());
  }
}
