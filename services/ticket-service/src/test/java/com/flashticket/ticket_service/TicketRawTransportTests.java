package com.flashticket.ticket_service;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.mock;

import com.rabbitmq.client.Channel;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.config.SimpleRabbitListenerContainerFactory;
import org.springframework.amqp.rabbit.connection.CachingConnectionFactory;
import org.springframework.amqp.rabbit.listener.MethodRabbitListenerEndpoint;
import org.springframework.amqp.rabbit.listener.api.ChannelAwareMessageListener;
import org.springframework.messaging.handler.annotation.support.DefaultMessageHandlerMethodFactory;

class TicketRawTransportTests {
  @Test
  void rawListenerMustNotResolveProducerJavaTypeHeaders() throws Exception {
    MessageProperties props = new MessageProperties();
    props.setContentType("application/json");
    props.setHeader("__TypeId__", "producer.private.package.Envelope");
    Message input =
        new Message(
            "{\"messageType\":\"ticket.issue.v1\"}".getBytes(StandardCharsets.UTF_8), props);
    assertRawDelivery(input);
  }

  @Test
  void unexpectedMimeTypeIsDeliveredAsRawBytesForStrictDecoderInsteadOfDeserialization()
      throws Exception {
    MessageProperties props = new MessageProperties();
    props.setContentType("application/x-java-serialized-object");
    props.setHeader("__TypeId__", "java.io.File");
    Message input = new Message(new byte[] {1, 2, 3}, props);
    assertRawDelivery(input);
  }

  private void assertRawDelivery(Message input) throws Exception {
    Receiver receiver = new Receiver();
    var endpoint = new MethodRabbitListenerEndpoint();
    endpoint.setId("ticket-raw-test");
    endpoint.setQueueNames("isolated.fixture");
    endpoint.setBean(receiver);
    endpoint.setMethod(Receiver.class.getMethod("consume", Message.class, Channel.class));
    var handlers = new DefaultMessageHandlerMethodFactory();
    handlers.afterPropertiesSet();
    endpoint.setMessageHandlerMethodFactory(handlers);
    var connection = new CachingConnectionFactory("127.0.0.1", 1);
    try {
      var factory = new SimpleRabbitListenerContainerFactory();
      factory.setConnectionFactory(connection);
      factory.setMessageConverter(
          new com.flashticket.ticket_service.messaging.TicketRawMessageConverter());
      var container = factory.createListenerContainer(endpoint);
      ((ChannelAwareMessageListener) container.getMessageListener())
          .onMessage(input, mock(Channel.class));
      assertThat(receiver.received).isSameAs(input);
    } finally {
      connection.destroy();
    }
  }

  public static class Receiver {
    Message received;

    public void consume(Message message, Channel channel) {
      received = message;
    }
  }
}
