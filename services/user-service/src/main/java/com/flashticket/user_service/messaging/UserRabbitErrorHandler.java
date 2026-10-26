package com.flashticket.user_service.messaging;

import com.flashticket.user_service.exception.ErrorCode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.listener.ConditionalRejectingErrorHandler;
import org.springframework.amqp.listener.ListenerExecutionFailedException;

/** Xử lý lỗi nhận message theo Spring AMQP và ghi log an toàn, không publish log RCA. */
@Slf4j
public final class UserRabbitErrorHandler extends ConditionalRejectingErrorHandler {

  /** Bật reject lỗi fatal cho MANUAL ACK, kể cả message đã có x-death. */
  public UserRabbitErrorHandler() {
    super(new SafeFatalExceptionStrategy());
    setRejectManual(true);
    setDiscardFatalsWithXDeath(false);
  }

  /** Ghi mã lỗi và nguyên nhân, không in body hoặc header của message. */
  @Override
  protected void log(Throwable throwable) {
    ErrorCode code = ErrorCode.from(throwable);
    log.atError()
        .addKeyValue("eventName", "user.message.failed")
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
