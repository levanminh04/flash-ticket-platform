package com.flashticket.payment_service.worker;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.flashticket.payment_service.entity.OutboxMessage;
import com.flashticket.payment_service.service.MessagingService;
import com.flashticket.payment_service.service.PaymentSagaService;
import com.flashticket.payment_service.service.RefundService;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.AmqpConnectException;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.test.util.ReflectionTestUtils;
import tools.jackson.databind.JsonNode;

/** Kết cục log outbox theo việc dời lịch có thực sự tác động hay không (REVIEW-6 R5). */
class PaymentWorkerTelemetryTests {
  private static final UUID MESSAGE = UUID.randomUUID();
  private static final UUID CORRELATION = UUID.randomUUID();
  private final MessagingService messaging = mock(MessagingService.class);
  private final RabbitTemplate rabbit = mock(RabbitTemplate.class);
  private final PaymentWorker worker =
      new PaymentWorker(
          messaging, mock(PaymentSagaService.class), mock(RefundService.class), rabbit);

  private List<Map<String, Object>> publishOnce(boolean rescheduled) {
    ReflectionTestUtils.setField(worker, "exchange", "business.ex");
    ReflectionTestUtils.setField(worker, "batchSize", 1);
    ReflectionTestUtils.setField(worker, "lease", Duration.ofMinutes(2));
    ReflectionTestUtils.setField(worker, "confirmTimeout", Duration.ofSeconds(1));
    var stored = mock(OutboxMessage.class);
    when(stored.getMessageId()).thenReturn(MESSAGE);
    when(stored.getCorrelationId()).thenReturn(CORRELATION);
    when(stored.getMessageType()).thenReturn("payment.succeeded");
    when(stored.getPayload()).thenReturn(mock(JsonNode.class));
    when(stored.getAttempts()).thenReturn(1);
    when(messaging.claimOutbox(any(), any(), anyInt())).thenReturn(List.of(stored));
    doThrow(new AmqpConnectException(new RuntimeException("broker down")))
        .when(rabbit)
        .send(anyString(), anyString(), any(Message.class), any(CorrelationData.class));
    when(messaging.rescheduleOutbox(any(), anyInt(), any(), anyString())).thenReturn(rescheduled);
    var logger = (Logger) LoggerFactory.getLogger(PaymentWorker.class);
    var appender = new ListAppender<ILoggingEvent>();
    appender.start();
    logger.addAppender(appender);
    try {
      worker.publishOutbox();
      return appender.list.stream()
          .map(
              event -> {
                Map<String, Object> values = new HashMap<>();
                event.getKeyValuePairs().forEach(pair -> values.put(pair.key, pair.value));
                return values;
              })
          .toList();
    } finally {
      logger.detachAppender(appender);
      appender.stop();
    }
  }

  @Test
  void rescheduledDeliveryIsLoggedAsRetry() {
    assertThat(publishOnce(true))
        .singleElement()
        .satisfies(
            f -> {
              assertThat(f).containsEntry("outcome", "RETRY");
              assertThat(f).containsEntry("reasonCode", "BROKER_DELIVERY_UNCONFIRMED");
              // REVIEW-8 R4: hai mã giữ đúng vai trò.
              assertThat(f).containsEntry("messageId", MESSAGE.toString());
              assertThat(f).containsEntry("correlationId", CORRELATION.toString());
            });
  }

  @Test
  void changedLeaseIsNotLoggedAsRetry() {
    assertThat(publishOnce(false))
        .singleElement()
        .satisfies(
            f -> {
              assertThat(f).containsEntry("outcome", "UNKNOWN");
              assertThat(f).containsEntry("reasonCode", "LEASE_CHANGED");
              assertThat(f).containsEntry("correlationId", CORRELATION.toString());
            });
  }
}
