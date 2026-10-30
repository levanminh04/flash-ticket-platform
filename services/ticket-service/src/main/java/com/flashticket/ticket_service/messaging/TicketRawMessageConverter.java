package com.flashticket.ticket_service.messaging;

import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.support.converter.MessageConversionException;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.stereotype.Component;

/**
 * Business listeners decode the exact JSON envelope themselves; broker type headers are untrusted.
 */
@Component("ticketRawMessageConverter")
@ConditionalOnExpression(
    "${ticket.features.business-enabled:false} and ${ticket.features.listeners-enabled:false}")
public class TicketRawMessageConverter implements MessageConverter {
  @Override
  public Object fromMessage(Message message) {
    return message.getBody();
  }

  @Override
  public Message toMessage(Object value, MessageProperties properties) {
    if (value instanceof byte[] bytes) return new Message(bytes, properties);
    throw new MessageConversionException("Raw ticket messages require bytes");
  }
}
