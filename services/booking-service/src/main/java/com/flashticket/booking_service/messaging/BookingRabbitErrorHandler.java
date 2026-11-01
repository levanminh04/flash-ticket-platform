package com.flashticket.booking_service.messaging;

import com.flashticket.booking_service.exception.ErrorClassifier;
import com.flashticket.booking_service.observability.FailureLog;
import org.springframework.amqp.listener.ConditionalRejectingErrorHandler;
import org.springframework.amqp.listener.ListenerExecutionFailedException;

/** Applies Spring AMQP's native fatal-message disposition and records safe RCA metadata. */
public final class BookingRabbitErrorHandler extends ConditionalRejectingErrorHandler {

  public BookingRabbitErrorHandler() {
    super(new SafeFatalExceptionStrategy());
    setRejectManual(true);
    setDiscardFatalsWithXDeath(false);
  }

  @Override
  protected void log(Throwable throwable) {
    FailureLog.record(
        ErrorClassifier.classify(throwable),
        "booking.message.failed",
        "messaging.consume",
        FailureLog.Outcome.FAILED);
  }

  private static final class SafeFatalExceptionStrategy extends DefaultExceptionStrategy {
    @Override
    protected void logFatalException(ListenerExecutionFailedException exception, Throwable cause) {
      // FailureLog records the failure without logging message headers or payload.
    }
  }
}
