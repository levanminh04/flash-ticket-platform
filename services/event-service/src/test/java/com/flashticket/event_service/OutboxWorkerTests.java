package com.flashticket.event_service;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.flashticket.event_service.messaging.*;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.Test;

class OutboxWorkerTests {
  @Test
  void successIsRecordedOnlyAfterTransportConfirmation() {
    var store = mock(OutboxStore.class);
    var transport = mock(MessagePublisher.class);
    var message = OutboxPublisherTests.message();
    var metrics = new SimpleMeterRegistry();
    when(store.claim(any())).thenReturn(List.of(message), List.of());
    when(store.confirmed(message)).thenReturn(true);
    new OutboxWorker(
            store,
            transport,
            metrics,
            2,
            Duration.ofSeconds(30),
            Duration.ofSeconds(1),
            Duration.ofMinutes(5))
        .relay();
    var order = inOrder(store, transport);
    order.verify(store).claim(any());
    order.verify(transport).publish(message);
    order.verify(store).confirmed(message);
    assertThat(metrics.get("event.outbox.publish").tag("outcome", "confirmed").counter().count())
        .isEqualTo(1);
  }

  @Test
  void failuresBackOffWithoutMarkingPublished() {
    var store = mock(OutboxStore.class);
    var transport = mock(MessagePublisher.class);
    var message = OutboxPublisherTests.message();
    when(store.claim(any())).thenReturn(List.of(message));
    doThrow(new MessagePublisher.Failure("UNROUTABLE")).when(transport).publish(message);
    new OutboxWorker(
            store,
            transport,
            new SimpleMeterRegistry(),
            1,
            Duration.ofSeconds(30),
            Duration.ofSeconds(1),
            Duration.ofMinutes(5))
        .relay();
    verify(store, never()).confirmed(any());
    verify(store).retry(message, "UNROUTABLE", Duration.ofSeconds(1));
  }

  @Test
  void expiredWorkerDoesNotReportUnownedSuccess() {
    var store = mock(OutboxStore.class);
    var transport = mock(MessagePublisher.class);
    var message = OutboxPublisherTests.message();
    var metrics = new SimpleMeterRegistry();
    when(store.claim(any())).thenReturn(List.of(message));
    when(store.confirmed(message)).thenReturn(false);
    new OutboxWorker(
            store,
            transport,
            metrics,
            1,
            Duration.ofSeconds(30),
            Duration.ofSeconds(1),
            Duration.ofMinutes(5))
        .relay();
    assertThat(metrics.find("event.outbox.publish").tag("outcome", "confirmed").counter()).isNull();
  }
}
