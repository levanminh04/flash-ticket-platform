package com.flashticket.ticket_service.messaging;

import com.flashticket.ticket_service.exception.ErrorCode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.listener.ConditionalRejectingErrorHandler;
import org.springframework.amqp.listener.ListenerExecutionFailedException;

/** Xử lý lỗi nhận message theo Spring AMQP và ghi log an toàn, không publish log RCA. */
@Slf4j
public final class TicketRabbitErrorHandler extends ConditionalRejectingErrorHandler {

  /** Bật reject lỗi fatal cho MANUAL ACK, kể cả message đã có x-death. */
  public TicketRabbitErrorHandler() {
    super(new SafeFatalExceptionStrategy());
    setRejectManual(true);
    setDiscardFatalsWithXDeath(false);
  }

  /** Ghi mã lỗi và nguyên nhân, không in body hoặc header của message. */
  @Override
  protected void log(Throwable throwable) {
    ErrorCode code = ErrorCode.from(throwable);
    log.atError()
        .addKeyValue("eventName", "ticket.message.failed")
        .addKeyValue("operation", "messaging.consume")
        .addKeyValue("outcome", "FAILED")
        .addKeyValue("errorCode", code.name())
        .addKeyValue("errorType", throwable.getClass().getName())
        .setCause(throwable)
        .log("RabbitMQ message processing failed: {}", code.message());
  }

  /** Giữ cách nhận diện lỗi fatal của Spring nhưng tắt log có thể chứa payload. */
  private static final class SafeFatalExceptionStrategy extends DefaultExceptionStrategy {
    /** Tránh log trùng; lỗi đã được ghi an toàn trong log(Throwable). */
    @Override
    protected void logFatalException(ListenerExecutionFailedException exception, Throwable cause) {}
  }
}
