package com.flashticket.booking_service.support;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.AppenderBase;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;
import org.slf4j.LoggerFactory;

public final class LogCapture implements AutoCloseable {
  private final Logger logger = (Logger) LoggerFactory.getLogger(Logger.ROOT_LOGGER_NAME);
  private final List<ILoggingEvent> events = new CopyOnWriteArrayList<>();
  private final AppenderBase<ILoggingEvent> appender;

  public LogCapture() {
    this(event -> {});
  }

  public LogCapture(Consumer<ILoggingEvent> observer) {
    appender =
        new AppenderBase<>() {
          @Override
          protected void append(ILoggingEvent event) {
            event.prepareForDeferredProcessing();
            events.add(event);
            observer.accept(event);
          }
        };
    appender.start();
    logger.addAppender(appender);
  }

  public List<ILoggingEvent> events() {
    return List.copyOf(events);
  }

  @Override
  public void close() {
    logger.detachAppender(appender);
    appender.stop();
  }
}
