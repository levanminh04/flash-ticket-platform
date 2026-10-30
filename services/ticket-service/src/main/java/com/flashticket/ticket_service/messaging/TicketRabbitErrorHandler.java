package com.flashticket.ticket_service.messaging;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.listener.ConditionalRejectingErrorHandler;
import org.springframework.amqp.listener.ListenerExecutionFailedException;

/** Retains fatal-message DLQ rejection without logging payloads, QR tokens or buyer information. */
public final class TicketRabbitErrorHandler extends ConditionalRejectingErrorHandler {
  private static final Logger LOG = LoggerFactory.getLogger(TicketRabbitErrorHandler.class);

  public TicketRabbitErrorHandler() {
    super(new SafeFatalExceptionStrategy());
    setRejectManual(true);
    setDiscardFatalsWithXDeath(false);
  }

  @Override
  protected void log(Throwable throwable) {
    LOG.warn(
        "Ticket message processing failed exceptionType={}", throwable.getClass().getSimpleName());
  }

  private static final class SafeFatalExceptionStrategy extends DefaultExceptionStrategy {
    @Override
    protected void logFatalException(ListenerExecutionFailedException exception, Throwable cause) {
      // The outer handler writes safe metadata; the default strategy logs the failed message.
    }
  }
}
