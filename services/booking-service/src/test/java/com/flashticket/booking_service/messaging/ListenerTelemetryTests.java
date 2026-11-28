package com.flashticket.booking_service.messaging;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.flashticket.booking_service.availability.service.AvailabilityService;
import com.flashticket.booking_service.exception.BusinessException;
import com.flashticket.booking_service.exception.ErrorCode;
import com.flashticket.booking_service.observability.RabbitTelemetry;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;

/** Kết cục trong log khớp cách container AUTO ACK xử lý bản tin (REVIEW-5 R5). */
class ListenerTelemetryTests {
  private final AvailabilityService availability = mock(AvailabilityService.class);
  private final SalesConfigurationListener listener = new SalesConfigurationListener(availability);

  private static Message raw() {
    var properties = new MessageProperties();
    properties.setConsumerQueue("booking.sales-configured.q");
    return new Message(new byte[0], properties);
  }

  private static List<Map<String, Object>> run(Runnable action) {
    var logger = (Logger) LoggerFactory.getLogger(RabbitTelemetry.class);
    var appender = new ListAppender<ILoggingEvent>();
    appender.start();
    logger.addAppender(appender);
    try {
      action.run();
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
  void permanentMessageErrorIsLoggedAsRejectedWithItsCode() {
    doThrow(new BusinessException(ErrorCode.INVALID_MESSAGE))
        .when(availability)
        .synchronizeSalesConfiguration(any());
    var logs =
        run(
            () ->
                assertThatThrownBy(() -> listener.handleSalesConfiguredEventDelivery(null, raw()))
                    .isInstanceOf(AmqpRejectAndDontRequeueException.class));
    assertThat(logs)
        .singleElement()
        .satisfies(
            f -> {
              assertThat(f).containsEntry("outcome", "REJECTED");
              assertThat(f).containsEntry("reasonCode", "INVALID_MESSAGE");
            });
  }

  @Test
  void transientFailureIsLoggedAsRetry() {
    doThrow(new IllegalStateException("db down"))
        .when(availability)
        .synchronizeSalesConfiguration(any());
    var logs =
        run(
            () ->
                assertThatThrownBy(() -> listener.handleSalesConfiguredEventDelivery(null, raw()))
                    .isInstanceOf(IllegalStateException.class));
    assertThat(logs)
        .singleElement()
        .satisfies(
            f -> {
              assertThat(f).containsEntry("outcome", "RETRY");
              assertThat(f).containsEntry("reasonCode", "TRANSACTION_UNAVAILABLE");
            });
  }

  @Test
  void committedMessageIsLoggedAsSuccess() {
    var logs = run(() -> listener.handleSalesConfiguredEventDelivery(null, raw()));
    assertThat(logs)
        .singleElement()
        .satisfies(f -> assertThat(f).containsEntry("outcome", "SUCCESS"));
  }
}
