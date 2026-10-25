package com.flashticket.user_service.support;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.AppenderBase;
import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;
import org.slf4j.LoggerFactory;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.EnumerablePropertySource;
import org.springframework.core.io.FileSystemResource;

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

  public static ConfigurableApplicationContext bootJsonLogging() throws IOException {
    var application = new SpringApplication(LoggingProbe.class);
    application.setWebApplicationType(WebApplicationType.NONE);
    var source =
        (EnumerablePropertySource<?>)
            new YamlPropertySourceLoader()
                .load("booking", new FileSystemResource("src/main/resources/application.yaml"))
                .getFirst();
    Map<String, Object> properties = new HashMap<>();
    for (String key : source.getPropertyNames()) {
      if (key.startsWith("logging.")) properties.put(key, source.getProperty(key));
    }
    application.setDefaultProperties(properties);
    return application.run(
        "--spring.config.location=optional:file:target/logging-probe-isolation.yaml",
        "--spring.cloud.config.enabled=false",
        "--spring.main.banner-mode=off");
  }

  @Configuration(proxyBeanMethods = false)
  static class LoggingProbe {}

  @Override
  public void close() {
    logger.detachAppender(appender);
    appender.stop();
  }
}
