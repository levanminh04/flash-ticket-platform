package com.flashticket.payment_service.messaging;

import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.listener.ConditionalRejectingErrorHandler;
import org.springframework.amqp.listener.ListenerExecutionFailedException;

/** Keeps native fatal-message rejection without logging payment payloads or headers. */
@Slf4j
public final class PaymentRabbitErrorHandler extends ConditionalRejectingErrorHandler {

  public PaymentRabbitErrorHandler() {
    super(new SafeFatalExceptionStrategy());
    setRejectManual(true);
    setDiscardFatalsWithXDeath(false);
  }

  @Override
  protected void log(Throwable throwable) {
    log.error("Payment message failed exceptionType={}", throwable.getClass().getSimpleName());
  }

  private static final class SafeFatalExceptionStrategy extends DefaultExceptionStrategy {
    @Override
    protected void logFatalException(ListenerExecutionFailedException exception, Throwable cause) {
      // The outer handler logs safe metadata without the failed message or exception text.
    }
  }
}
