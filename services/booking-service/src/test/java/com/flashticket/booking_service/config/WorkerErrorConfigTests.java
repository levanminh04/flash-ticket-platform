package com.flashticket.booking_service.config;

import static org.assertj.core.api.Assertions.assertThat;

import com.flashticket.booking_service.exception.ErrorClassifier;
import com.flashticket.booking_service.observability.BookingContext;
import com.flashticket.booking_service.observability.FailureLog;
import com.flashticket.booking_service.support.LogCapture;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.SimpleTransactionStatus;
import org.springframework.transaction.support.TransactionTemplate;

class WorkerErrorConfigTests {
  @Test
  void schedulerReportsUncaughtFailureOnce() throws Exception {
    var logged = new CountDownLatch(1);
    try (var capture =
        new LogCapture(
            event -> {
              if ("booking.worker.failed"
                  .equals(
                      event.getKeyValuePairs().stream()
                          .filter(pair -> pair.key.equals("eventName"))
                          .map(pair -> pair.value)
                          .findFirst()
                          .orElse(null))) logged.countDown();
            })) {
      ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
      scheduler.setPoolSize(1);
      new WorkerErrorConfig().bookingTaskSchedulerCustomizer().customize(scheduler);
      scheduler.initialize();
      try {
        scheduler.schedule(
            () -> {
              throw new IllegalStateException("private-sentinel");
            },
            Instant.now());
        assertThat(logged.await(2, TimeUnit.SECONDS)).isTrue();
        var failures =
            capture.events().stream()
                .filter(
                    event ->
                        event.getKeyValuePairs().stream()
                            .anyMatch(
                                pair ->
                                    pair.key.equals("eventName")
                                        && pair.value.equals("booking.worker.failed")))
                .toList();
        assertThat(failures).hasSize(1);
        assertThat(failures.getFirst().getFormattedMessage())
            .isEqualTo("Booking operation failed.");
        assertThat(failures.getFirst().getKeyValuePairs())
            .extracting(pair -> pair.key)
            .doesNotContain("orderId", "correlationId", "message");
      } finally {
        scheduler.destroy();
      }
    }
  }

  @Test
  void itemFailureDoesNotLeakContext() {
    UUID first = UUID.randomUUID();
    UUID second = UUID.randomUUID();
    MDC.put("requestId", UUID.randomUUID().toString());
    String parent = MDC.get("requestId");
    try {
      try (var ignored = BookingContext.open(Map.of("correlationId", first.toString()))) {
        try (var item = BookingContext.open(Map.of("correlationId", second.toString()))) {
          assertThat(BookingContext.correlationId()).isEqualTo(second);
          throw new IllegalStateException("item failure");
        } catch (IllegalStateException expected) {
          assertThat(BookingContext.correlationId()).isEqualTo(first);
        }
      }
      assertThat(MDC.get("requestId")).isEqualTo(parent);
    } finally {
      MDC.clear();
    }
  }

  @Test
  void retryIsReportedOnlyAfterPersistence() {
    AtomicBoolean rolledBack = new AtomicBoolean();
    AtomicBoolean retryStored = new AtomicBoolean();
    PlatformTransactionManager manager =
        new PlatformTransactionManager() {
          @Override
          public TransactionStatus getTransaction(TransactionDefinition definition) {
            return new SimpleTransactionStatus();
          }

          @Override
          public void commit(TransactionStatus status) {}

          @Override
          public void rollback(TransactionStatus status) {
            rolledBack.set(true);
          }
        };
    TransactionTemplate transaction = new TransactionTemplate(manager);
    try (var capture =
        new LogCapture(
            event -> {
              if (event.getKeyValuePairs().stream()
                  .anyMatch(pair -> pair.key.equals("outcome") && pair.value.equals("RETRY"))) {
                assertThat(rolledBack).isTrue();
                assertThat(retryStored).isTrue();
              }
            })) {
      IllegalStateException failed = new IllegalStateException("transaction rolled back");
      try {
        transaction.executeWithoutResult(
            status -> {
              throw failed;
            });
      } catch (IllegalStateException expected) {
        assertThat(expected).isSameAs(failed);
      }
      assertThat(rolledBack).isTrue();
      retryStored.set(true); // stands for a successful write of the next attempt
      FailureLog.record(
          ErrorClassifier.classify(failed),
          "booking.worker.failed",
          "worker.execute",
          FailureLog.Outcome.RETRY);
      assertThat(capture.events())
          .singleElement()
          .satisfies(
              event ->
                  assertThat(event.getKeyValuePairs())
                      .anyMatch(pair -> pair.key.equals("outcome") && pair.value.equals("RETRY")));
    }

    var processingFailure = new IllegalStateException("processing failed");
    var persistenceFailure =
        new DataAccessResourceFailureException("retry write failed", processingFailure);
    try (var capture = new LogCapture()) {
      FailureLog.record(
          ErrorClassifier.classify(persistenceFailure),
          "booking.worker.failed",
          "worker.execute",
          FailureLog.Outcome.FAILED);
      assertThat(capture.events())
          .singleElement()
          .satisfies(
              event -> {
                assertThat(event.getThrowableProxy().getClassName())
                    .isEqualTo(DataAccessResourceFailureException.class.getName());
                assertThat(event.getThrowableProxy().getCause().getClassName())
                    .isEqualTo(IllegalStateException.class.getName());
              });
    }
  }
}
