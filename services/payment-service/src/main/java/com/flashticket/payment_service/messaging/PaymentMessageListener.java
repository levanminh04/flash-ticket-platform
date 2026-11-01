package com.flashticket.payment_service.messaging;

import com.flashticket.payment_service.dto.common.ContractCodec;
import com.flashticket.payment_service.dto.common.ContractValidationException;
import com.flashticket.payment_service.dto.message.*;
import com.flashticket.payment_service.service.PaymentSagaService;
import com.flashticket.payment_service.validation.MessageInvariantValidator;
import com.rabbitmq.client.Channel;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import lombok.RequiredArgsConstructor;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.support.converter.MessageConversionException;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class PaymentMessageListener {
  private final ContractCodec codec;
  private final MessageInvariantValidator validator;
  private final PaymentSagaService sagaService;

  @RabbitListener(
      queues = "${payment.queues.decision:payment.booking-decision.v1}",
      ackMode = "MANUAL")
  public void onPaymentDecision(Message message, Channel channel) throws IOException {
    try {
      var envelope =
          codec.readEnvelope(
              new String(message.getBody(), StandardCharsets.UTF_8), PaymentDecision.class);
      validator.validatePaymentDecision(envelope);
      sagaService.recordPaymentDecision(envelope);
    } catch (ContractValidationException ex) {
      throw new MessageConversionException("Invalid payment decision", ex);
    }
    // Service proxy chỉ trả về sau khi commit thành công.
    channel.basicAck(message.getMessageProperties().getDeliveryTag(), false);
  }

  @RabbitListener(
      queues = "${payment.queues.issuance:payment.ticket-result.v1}",
      ackMode = "MANUAL")
  public void onIssuanceResult(Message message, Channel channel) throws IOException {
    try {
      var envelope =
          codec.readEnvelope(
              new String(message.getBody(), StandardCharsets.UTF_8), IssuanceResult.class);
      validator.validateIssuanceResult(envelope);
      sagaService.recordIssuanceResult(envelope);
    } catch (ContractValidationException ex) {
      throw new MessageConversionException("Invalid issuance result", ex);
    }
    channel.basicAck(message.getMessageProperties().getDeliveryTag(), false);
  }

  @RabbitListener(
      queues = "${payment.queues.completed:payment.booking-completed.v1}",
      ackMode = "MANUAL")
  public void onOrderCompleted(Message message, Channel channel) throws IOException {
    try {
      var envelope =
          codec.readEnvelope(
              new String(message.getBody(), StandardCharsets.UTF_8), OrderCompleted.class);
      validator.validateOrderCompleted(envelope);
      sagaService.recordOrderCompleted(envelope);
    } catch (ContractValidationException ex) {
      throw new MessageConversionException("Invalid order completion", ex);
    }
    channel.basicAck(message.getMessageProperties().getDeliveryTag(), false);
  }
}
