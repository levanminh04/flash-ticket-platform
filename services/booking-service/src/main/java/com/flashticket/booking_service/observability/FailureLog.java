package com.flashticket.booking_service.observability;

import com.flashticket.booking_service.exception.ErrorClassifier;
import com.flashticket.booking_service.exception.ErrorClassifier.Failure;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.event.Level;

public final class FailureLog {
  private static final Logger LOG = LoggerFactory.getLogger(FailureLog.class);
  private static final Set<String> OPERATIONS =
      Set.of(
          "http.request",
          "security.authenticate",
          "security.authorize",
          "messaging.consume",
          "worker.execute",
          "order.create",
          "order.expire",
          "order.freeze-payment",
          "outbox.publish");
  private static final Set<String> EVENTS =
      Set.of(
          "booking.request.rejected",
          "booking.request.failed",
          "booking.message.failed",
          "booking.worker.failed");

  public enum Outcome {
    REJECTED,
    FAILED,
    RETRY,
    UNKNOWN
  }

  private FailureLog() {}

  public static void record(Failure failure, String eventName, String operation, Outcome outcome) {
    Failure safe = ErrorClassifier.of(failure.code(), failure.exception());
    Level level =
        switch (outcome) {
          case REJECTED -> Level.INFO;
          case FAILED -> Level.ERROR;
          case RETRY, UNKNOWN -> Level.WARN;
        };
    var entry =
        LOG.atLevel(level)
            .addKeyValue(
                "eventName", EVENTS.contains(eventName) ? eventName : "booking.worker.failed")
            .addKeyValue("operation", OPERATIONS.contains(operation) ? operation : "worker.execute")
            .addKeyValue("outcome", outcome.name())
            .addKeyValue("errorCode", safe.code())
            .addKeyValue("errorType", safe.errorType());
    var withCause =
        outcome != Outcome.REJECTED && safe.exception() != null
            ? entry.setCause(safe.exception())
            : entry;
    withCause.log(
        outcome == Outcome.REJECTED ? "Booking operation rejected." : "Booking operation failed.");
  }
}
