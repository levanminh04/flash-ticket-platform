package com.flashticket.event_service.messaging;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "event.outbox.enabled", havingValue = "true", matchIfMissing = true)
public final class OutboxWorker {
  private final OutboxStore store;
  private final MessagePublisher publisher;
  private final MeterRegistry metrics;
  private final int batch;
  private final Duration lease;
  private final Duration initialRetry;
  private final Duration maximumRetry;

  @edu.umd.cs.findbugs.annotations.SuppressFBWarnings(
      value = "EI_EXPOSE_REP2",
      justification =
          "Intentionally shares Spring-managed infrastructure; no dependency accessor is exposed.")
  public OutboxWorker(
      OutboxStore store,
      MessagePublisher publisher,
      MeterRegistry metrics,
      @Value("${event.outbox.batch-size:20}") int batch,
      @Value("${event.outbox.lease:30s}") Duration lease,
      @Value("${event.outbox.initial-retry:1s}") Duration initialRetry,
      @Value("${event.outbox.maximum-retry:5m}") Duration maximumRetry) {
    if (batch <= 0
        || lease.isNegative()
        || lease.isZero()
        || initialRetry.isNegative()
        || initialRetry.isZero()
        || maximumRetry.compareTo(initialRetry) < 0)
      throw new IllegalArgumentException("Invalid Event outbox settings");
    this.store = store;
    this.publisher = publisher;
    this.metrics = metrics;
    this.batch = batch;
    this.lease = lease;
    this.initialRetry = initialRetry;
    this.maximumRetry = maximumRetry;
    Gauge.builder("event.outbox.backlog", store, OutboxStore::backlog).register(metrics);
    Gauge.builder("event.outbox.oldest.age", store, OutboxStore::oldestAge)
        .baseUnit("seconds")
        .register(metrics);
  }

  @Scheduled(fixedDelayString = "${event.outbox.poll-delay:1s}")
  public void relay() {
    for (int i = 0; i < batch && !Thread.currentThread().isInterrupted(); i++) {
      var claimed = store.claim(lease);
      if (claimed.isEmpty()) return;
      var message = claimed.getFirst();
      try {
        publisher.publish(message);
        if (store.confirmed(message)) {
          metrics.counter("event.outbox.publish", "outcome", "confirmed").increment();
          log(message, "SUCCESS", "BROKER_CONFIRMED");
        }
      } catch (MessagePublisher.Failure error) {
        retry(message, error.code());
      } catch (RuntimeException error) {
        retry(message, "PUBLISH_UNAVAILABLE");
      }
    }
  }

  private void retry(OutboxStore.Message message, String code) {
    long multiplier = 1L << Math.min(Math.max(message.attempt() - 1, 0), 20);
    long maximum = maximumRetry.toMillis();
    long initial = initialRetry.toMillis();
    long delay = initial > maximum / multiplier ? maximum : Math.min(maximum, initial * multiplier);
    store.retry(message, code, Duration.ofMillis(delay));
    metrics.counter("event.outbox.publish", "outcome", "retry").increment();
    log(message, "RETRY", code);
  }

  private static void log(OutboxStore.Message message, String outcome, String code) {
    org.slf4j.LoggerFactory.getLogger(OutboxWorker.class)
        .atInfo()
        .addKeyValue("eventName", "event.outbox.publish")
        .addKeyValue("operation", "outbox-publish")
        .addKeyValue("outcome", outcome)
        .addKeyValue("messageId", message.id().toString())
        .addKeyValue("correlationId", message.correlationId().toString())
        .addKeyValue("configVersion", Long.toString(message.version()))
        .addKeyValue("retryCount", message.attempt())
        .addKeyValue("reasonCode", code)
        .log("Event outbox delivery decision");
  }
}
