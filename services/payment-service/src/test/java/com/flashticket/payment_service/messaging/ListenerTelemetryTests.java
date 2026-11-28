package com.flashticket.payment_service.messaging;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.flashticket.payment_service.dto.common.ContractCodec;
import com.flashticket.payment_service.dto.common.ContractValidationException;
import com.flashticket.payment_service.dto.common.Envelope;
import com.flashticket.payment_service.dto.message.PaymentDecision;
import com.flashticket.payment_service.observability.BusinessTelemetry;
import com.flashticket.payment_service.observability.RabbitTelemetry;
import com.flashticket.payment_service.service.FinanceService;
import com.flashticket.payment_service.service.PaymentSagaService;
import com.flashticket.payment_service.service.RefundService;
import com.flashticket.payment_service.validation.MessageInvariantValidator;
import com.rabbitmq.client.AlreadyClosedException;
import com.rabbitmq.client.Channel;
import com.rabbitmq.client.ShutdownSignalException;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.support.converter.MessageConversionException;

/** Kết cục trong log khớp quyết định ACK/NACK thật của listener (REVIEW-5 R4, R5). */
class ListenerTelemetryTests {
  private final ContractCodec codec = mock(ContractCodec.class);
  private final PaymentSagaService saga = mock(PaymentSagaService.class);
  private final PaymentMessageListener listener =
      new PaymentMessageListener(
          codec,
          mock(MessageInvariantValidator.class),
          saga,
          mock(RefundService.class),
          mock(FinanceService.class));
  private final Channel channel = mock(Channel.class);

  private static Message message() {
    var properties = new MessageProperties();
    properties.setDeliveryTag(7L);
    properties.setConsumerQueue("payment.decision.q");
    return new Message("{}".getBytes(), properties);
  }

  private static Map<String, Object> fields(ILoggingEvent event) {
    var values = new java.util.HashMap<String, Object>();
    event.getKeyValuePairs().forEach(pair -> values.put(pair.key, pair.value));
    return values;
  }

  private List<Map<String, Object>> run(Class<?> source, Runnable action) {
    var logger = (Logger) LoggerFactory.getLogger(source);
    var appender = new ListAppender<ILoggingEvent>();
    appender.start();
    logger.addAppender(appender);
    try {
      action.run();
      return appender.list.stream().map(ListenerTelemetryTests::fields).toList();
    } finally {
      logger.detachAppender(appender);
      appender.stop();
    }
  }

  @Test
  void transactionFailureAfterRequeueNackIsLoggedAsRetry() throws IOException {
    when(codec.readEnvelope(anyString(), any())).thenThrow(new IllegalStateException("db down"));
    var logs =
        run(
            RabbitTelemetry.class,
            () ->
                assertThatThrownBy(() -> listener.onPaymentDecisionDelivery(message(), channel))
                    .isInstanceOf(IllegalStateException.class));
    verify(channel).basicNack(7L, false, true);
    assertThat(logs)
        .singleElement()
        .satisfies(
            f -> {
              assertThat(f).containsEntry("outcome", "RETRY");
              assertThat(f).containsEntry("reasonCode", "TRANSACTION_UNAVAILABLE");
            });
  }

  @Test
  void invalidContractIsLoggedAsRejected() throws IOException {
    when(codec.readEnvelope(anyString(), any()))
        .thenThrow(new ContractValidationException("data", "INVALID"));
    var logs =
        run(
            RabbitTelemetry.class,
            () ->
                assertThatThrownBy(() -> listener.onPaymentDecisionDelivery(message(), channel))
                    .isInstanceOf(MessageConversionException.class));
    verify(channel, never()).basicNack(anyLong(), anyBoolean(), anyBoolean());
    assertThat(logs)
        .singleElement()
        .satisfies(
            f -> {
              assertThat(f).containsEntry("outcome", "REJECTED");
              assertThat(f).containsEntry("reasonCode", "INVALID_CONTRACT");
            });
  }

  @Test
  void failedBrokerCommandIsLoggedAsUnknown() throws IOException {
    when(codec.readEnvelope(anyString(), any())).thenThrow(new IllegalStateException("db down"));
    doThrow(new IOException("channel closed")).when(channel).basicNack(7L, false, true);
    var logs =
        run(
            RabbitTelemetry.class,
            () ->
                assertThatThrownBy(() -> listener.onPaymentDecisionDelivery(message(), channel))
                    .isInstanceOf(IOException.class));
    assertThat(logs)
        .singleElement()
        .satisfies(
            f -> {
              assertThat(f).containsEntry("outcome", "UNKNOWN");
              assertThat(f).containsEntry("reasonCode", "BROKER_COMMAND_FAILED");
            });
  }

  private static AlreadyClosedException channelClosed() {
    return new AlreadyClosedException(new ShutdownSignalException(false, true, null, null));
  }

  @Test
  void closedChannelOnAckAfterCommitIsLoggedAsUnknown() throws IOException {
    @SuppressWarnings("unchecked")
    Envelope<PaymentDecision> envelope = mock(Envelope.class);
    when(envelope.correlationId()).thenReturn(UUID.randomUUID());
    when(envelope.messageId()).thenReturn(UUID.randomUUID());
    when(codec.readEnvelope(anyString(), eq(PaymentDecision.class))).thenReturn(envelope);
    doThrow(channelClosed()).when(channel).basicAck(7L, false);
    var logs =
        run(
            RabbitTelemetry.class,
            () ->
                assertThatThrownBy(() -> listener.onPaymentDecisionDelivery(message(), channel))
                    .isInstanceOf(AlreadyClosedException.class));
    verify(saga).recordPaymentDecision(envelope);
    verify(channel, never()).basicNack(anyLong(), anyBoolean(), anyBoolean());
    assertThat(logs)
        .singleElement()
        .satisfies(
            f -> {
              assertThat(f).containsEntry("outcome", "UNKNOWN");
              assertThat(f).containsEntry("reasonCode", "BROKER_COMMAND_FAILED");
            });
  }

  @Test
  void springProxyClosedChannelOnAckAfterCommitIsLoggedAsUnknown() throws IOException {
    // CachingConnectionFactory proxy ném IllegalStateException khi kênh đích đã đóng (REVIEW-8 R1).
    @SuppressWarnings("unchecked")
    Envelope<PaymentDecision> envelope = mock(Envelope.class);
    when(envelope.correlationId()).thenReturn(UUID.randomUUID());
    when(envelope.messageId()).thenReturn(UUID.randomUUID());
    when(codec.readEnvelope(anyString(), eq(PaymentDecision.class))).thenReturn(envelope);
    doThrow(new IllegalStateException("Channel closed; cannot ack/nack"))
        .when(channel)
        .basicAck(7L, false);
    var logs =
        run(
            RabbitTelemetry.class,
            () ->
                assertThatThrownBy(() -> listener.onPaymentDecisionDelivery(message(), channel))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessage("Channel closed; cannot ack/nack"));
    verify(saga).recordPaymentDecision(envelope);
    verify(channel, never()).basicNack(anyLong(), anyBoolean(), anyBoolean());
    assertThat(logs)
        .singleElement()
        .satisfies(
            f -> {
              assertThat(f).containsEntry("outcome", "UNKNOWN");
              assertThat(f).containsEntry("reasonCode", "BROKER_COMMAND_FAILED");
            });
  }

  @Test
  void springProxyClosedChannelOnNackIsLoggedAsUnknown() throws IOException {
    when(codec.readEnvelope(anyString(), any())).thenThrow(new IllegalStateException("db down"));
    doThrow(new IllegalStateException("Channel closed; cannot ack/nack"))
        .when(channel)
        .basicNack(7L, false, true);
    var logs =
        run(
            RabbitTelemetry.class,
            () ->
                assertThatThrownBy(() -> listener.onPaymentDecisionDelivery(message(), channel))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessage("Channel closed; cannot ack/nack"));
    assertThat(logs)
        .singleElement()
        .satisfies(
            f -> {
              assertThat(f).containsEntry("outcome", "UNKNOWN");
              assertThat(f).containsEntry("reasonCode", "BROKER_COMMAND_FAILED");
            });
  }

  @Test
  void closedChannelOnNackIsLoggedAsUnknown() throws IOException {
    when(codec.readEnvelope(anyString(), any())).thenThrow(new IllegalStateException("db down"));
    doThrow(channelClosed()).when(channel).basicNack(7L, false, true);
    var logs =
        run(
            RabbitTelemetry.class,
            () ->
                assertThatThrownBy(() -> listener.onPaymentDecisionDelivery(message(), channel))
                    .isInstanceOf(AlreadyClosedException.class));
    assertThat(logs)
        .singleElement()
        .satisfies(
            f -> {
              assertThat(f).containsEntry("outcome", "UNKNOWN");
              assertThat(f).containsEntry("reasonCode", "BROKER_COMMAND_FAILED");
            });
  }

  @Test
  void callbackOutcomeFollowsTheStoredAttemptState() {
    var logs =
        run(
            BusinessTelemetry.class,
            () -> {
              for (String state : List.of("SUCCEEDED", "FAILED", "UNKNOWN"))
                BusinessTelemetry.committed(
                    "callback-confirmed", UUID.randomUUID(), "PENDING", state, "CALLBACK_VERIFIED");
            });
    assertThat(logs)
        .extracting(f -> f.get("outcome"))
        .containsExactly("SUCCESS", "FAILED", "UNKNOWN");
    assertThat(logs)
        .extracting(f -> f.get("stateAfter"))
        .containsExactly("SUCCEEDED", "FAILED", "UNKNOWN");
  }
}
