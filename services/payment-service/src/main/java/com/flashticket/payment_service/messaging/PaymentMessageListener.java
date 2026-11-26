package com.flashticket.payment_service.messaging;

import com.flashticket.payment_service.dto.common.ContractCodec;
import com.flashticket.payment_service.dto.common.ContractValidationException;
import com.flashticket.payment_service.dto.message.*;
import com.flashticket.payment_service.observability.RabbitTelemetry;
import com.flashticket.payment_service.service.FinanceService;
import com.flashticket.payment_service.service.PaymentSagaService;
import com.flashticket.payment_service.service.RefundService;
import com.flashticket.payment_service.validation.MessageInvariantValidator;
import com.rabbitmq.client.Channel;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import lombok.RequiredArgsConstructor;
import org.slf4j.MDC;
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
  private final RefundService refundService;
  private final FinanceService financeService;

  @RabbitListener(queues = "${payment.queues.decision:payment.decision.q}", ackMode = "MANUAL")
  public void onPaymentDecisionDelivery(Message message, Channel channel) throws IOException {
    try (var telemetry = new RabbitTelemetry(message)) {
      onPaymentDecision(message, channel);
      telemetry.result("SUCCESS", "MESSAGE_ACKNOWLEDGED");
    }
  }

  public void onPaymentDecision(Message message, Channel channel) throws IOException {
    try {
      var envelope =
          codec.readEnvelope(
              new String(message.getBody(), StandardCharsets.UTF_8), PaymentDecision.class);
      validator.validatePaymentDecision(envelope);
      MDC.put("correlationId", envelope.correlationId().toString());
      MDC.put("messageId", envelope.messageId().toString());
      sagaService.recordPaymentDecision(envelope);
    } catch (ContractValidationException ex) {
      throw new MessageConversionException("Invalid payment decision", ex);
    } catch (RuntimeException ex) {
      // MANUAL ACK: trả message về queue khi TX thất bại, không giữ Unacked vô hạn.
      channel.basicNack(message.getMessageProperties().getDeliveryTag(), false, true);
      throw ex;
    }
    // Service proxy chỉ trả về sau khi commit thành công.
    channel.basicAck(message.getMessageProperties().getDeliveryTag(), false);
  }

  @RabbitListener(
      queues = "${payment.queues.issuance:payment.ticket-issuance-failed.q}",
      ackMode = "MANUAL")
  public void onIssuanceResultDelivery(Message message, Channel channel) throws IOException {
    try (var telemetry = new RabbitTelemetry(message)) {
      onIssuanceResult(message, channel);
      telemetry.result("SUCCESS", "MESSAGE_ACKNOWLEDGED");
    }
  }

  public void onIssuanceResult(Message message, Channel channel) throws IOException {
    try {
      var envelope =
          codec.readEnvelope(
              new String(message.getBody(), StandardCharsets.UTF_8), IssuanceResult.class);
      validator.validateIssuanceResult(envelope);
      MDC.put("correlationId", envelope.correlationId().toString());
      MDC.put("messageId", envelope.messageId().toString());
      sagaService.recordIssuanceResult(envelope);
    } catch (ContractValidationException ex) {
      throw new MessageConversionException("Invalid issuance result", ex);
    } catch (RuntimeException ex) {
      // MANUAL ACK: trả message về queue khi TX thất bại, không giữ Unacked vô hạn.
      channel.basicNack(message.getMessageProperties().getDeliveryTag(), false, true);
      throw ex;
    }
    channel.basicAck(message.getMessageProperties().getDeliveryTag(), false);
  }

  @RabbitListener(
      queues = "${payment.queues.completed:payment.order-completed.q}",
      ackMode = "MANUAL")
  public void onOrderCompletedDelivery(Message message, Channel channel) throws IOException {
    try (var telemetry = new RabbitTelemetry(message)) {
      onOrderCompleted(message, channel);
      telemetry.result("SUCCESS", "MESSAGE_ACKNOWLEDGED");
    }
  }

  public void onOrderCompleted(Message message, Channel channel) throws IOException {
    try {
      var envelope =
          codec.readEnvelope(
              new String(message.getBody(), StandardCharsets.UTF_8), OrderCompleted.class);
      validator.validateOrderCompleted(envelope);
      MDC.put("correlationId", envelope.correlationId().toString());
      MDC.put("messageId", envelope.messageId().toString());
      sagaService.recordOrderCompleted(envelope);
    } catch (ContractValidationException ex) {
      throw new MessageConversionException("Invalid order completion", ex);
    } catch (RuntimeException ex) {
      // MANUAL ACK: trả message về queue khi TX thất bại, không giữ Unacked vô hạn.
      channel.basicNack(message.getMessageProperties().getDeliveryTag(), false, true);
      throw ex;
    }
    channel.basicAck(message.getMessageProperties().getDeliveryTag(), false);
  }

  @RabbitListener(
      queues = "${payment.queues.event-cancelled:payment.event-cancelled.q}",
      ackMode = "MANUAL")
  public void onEventCancelledDelivery(Message message, Channel channel) throws IOException {
    try (var telemetry = new RabbitTelemetry(message)) {
      onEventCancelled(message, channel);
      telemetry.result("SUCCESS", "MESSAGE_ACKNOWLEDGED");
    }
  }

  public void onEventCancelled(Message message, Channel channel) throws IOException {
    try {
      var envelope =
          codec.readEnvelope(
              new String(message.getBody(), StandardCharsets.UTF_8), EventCancellation.class);
      validator.validateEventCancellation(envelope);
      MDC.put("correlationId", envelope.correlationId().toString());
      MDC.put("messageId", envelope.messageId().toString());
      refundService.recordEventCancellation(envelope);
    } catch (ContractValidationException ex) {
      throw new MessageConversionException("Invalid event cancellation", ex);
    } catch (RuntimeException ex) {
      // MANUAL ACK: trả message về queue khi TX thất bại, không giữ Unacked vô hạn.
      channel.basicNack(message.getMessageProperties().getDeliveryTag(), false, true);
      throw ex;
    }
    // Service proxy chỉ trả về sau khi commit thành công.
    channel.basicAck(message.getMessageProperties().getDeliveryTag(), false);
  }

  @RabbitListener(
      queues = "${payment.queues.finance-configured:payment.finance-configured.q}",
      ackMode = "MANUAL")
  public void onFinanceConfiguredDelivery(Message message, Channel channel) throws IOException {
    try (var telemetry = new RabbitTelemetry(message)) {
      onFinanceConfigured(message, channel);
      telemetry.result("SUCCESS", "MESSAGE_ACKNOWLEDGED");
    }
  }

  public void onFinanceConfigured(Message message, Channel channel) throws IOException {
    try {
      var envelope =
          codec.readEnvelope(
              new String(message.getBody(), StandardCharsets.UTF_8), FinanceConfiguration.class);
      validator.validateFinanceConfiguration(envelope);
      MDC.put("correlationId", envelope.correlationId().toString());
      MDC.put("messageId", envelope.messageId().toString());
      financeService.recordFinanceConfiguration(envelope);
    } catch (ContractValidationException ex) {
      throw new MessageConversionException("Invalid finance configuration", ex);
    } catch (RuntimeException ex) {
      // MANUAL ACK: trả message về queue khi TX thất bại, không giữ Unacked vô hạn.
      channel.basicNack(message.getMessageProperties().getDeliveryTag(), false, true);
      throw ex;
    }
    // Service proxy chỉ trả về sau khi commit thành công.
    channel.basicAck(message.getMessageProperties().getDeliveryTag(), false);
  }
}
