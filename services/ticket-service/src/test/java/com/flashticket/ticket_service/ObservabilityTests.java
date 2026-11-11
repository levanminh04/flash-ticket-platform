package com.flashticket.ticket_service;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.springframework.transaction.support.TransactionSynchronization.STATUS_ROLLED_BACK;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.flashticket.ticket_service.observability.BusinessTelemetry;
import com.flashticket.ticket_service.observability.RabbitTelemetry;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.context.Context;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mockito;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.system.*;
import org.springframework.core.env.EnumerablePropertySource;
import org.springframework.core.io.FileSystemResource;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import tools.jackson.databind.json.JsonMapper;

@ExtendWith(OutputCaptureExtension.class)
class ObservabilityTests {
  @TestConfiguration(proxyBeanMethods = false)
  static class Probe {}

  @Test
  void productionFormatterProducesTypedSafeCorrelatedRecords(CapturedOutput output)
      throws Exception {
    var source =
        (EnumerablePropertySource<?>)
            new YamlPropertySourceLoader()
                .load("production", new FileSystemResource("src/main/resources/application.yaml"))
                .getFirst();
    Map<String, Object> props = new HashMap<>();
    for (String name : source.getPropertyNames())
      if (name.startsWith("logging.")) props.put(name, source.getProperty(name));
    var application = new SpringApplication(Probe.class);
    application.setWebApplicationType(WebApplicationType.NONE);
    application.setDefaultProperties(props);
    var previous = MDC.getCopyOfContextMap();
    try (var context =
        application.run(
            "--spring.config.location=optional:file:target/b16-probe-isolated.yaml",
            "--spring.main.banner-mode=off",
            "--spring.cloud.config.enabled=false")) {
      MDC.put("Authorization", "SENTINEL-authorization");
      MDC.put("traceId", "11111111111111111111111111111111");
      MDC.put("spanId", "1111111111111111");
      MDC.put("trace_id", "22222222222222222222222222222222");
      MDC.put("span_id", "2222222222222222");
      MDC.put("correlationId", "00000000-0000-4000-8000-000000000001");
      var log = LoggerFactory.getLogger("com.flashticket.b16.probe");
      log.atInfo()
          .addKeyValue("eventName", "probe.success")
          .addKeyValue("operation", "probe")
          .addKeyValue("outcome", "SUCCESS")
          .addKeyValue("durationMs", 12L)
          .addKeyValue("password", "SENTINEL-password")
          .log("Probe finished");
      log.atError()
          .addKeyValue("eventName", "probe.failed")
          .addKeyValue("operation", "probe")
          .addKeyValue("outcome", "FAILED")
          .setCause(new IllegalStateException("SENTINEL-exception"))
          .log("Probe failed");
    } finally {
      if (previous == null) MDC.clear();
      else MDC.setContextMap(previous);
    }
    var mapper = JsonMapper.builder().build();
    var lines =
        output
            .getOut()
            .lines()
            .filter(s -> s.startsWith("{"))
            .filter(s -> s.contains("probe.success") || s.contains("probe.failed"))
            .toList();
    assertThat(lines).hasSize(2);
    for (String line : lines) {
      assertThat(line).doesNotContain("SENTINEL", "b16_", "@timestamp");
      var node = mapper.readTree(line);
      assertThat(node.path("schemaVersion").asInt()).isEqualTo(1);
      assertThat(node.path("timestamp").asString()).endsWith("Z");
      for (String key :
          List.of(
              "serviceName", "serviceVersion", "instanceId", "environment", "hostId", "loggerName"))
        assertThat(node.path(key).asString()).isNotBlank();
      assertThat(node.path("traceId").asString()).isEqualTo("22222222222222222222222222222222");
      assertThat(node.path("spanId").asString()).isEqualTo("2222222222222222");
      assertThat(node.path("correlationId").asString())
          .isEqualTo("00000000-0000-4000-8000-000000000001");
    }
    assertThat(mapper.readTree(lines.getFirst()).path("durationMs").isNumber()).isTrue();
    assertThat(mapper.readTree(lines.getLast()).path("stackTrace").asString())
        .contains("IllegalStateException");
    Files.createDirectories(Path.of("target"));
    Files.write(Path.of("target/b16-log-samples.jsonl"), lines, StandardCharsets.UTF_8);
  }

  @Test
  void rollbackEmitsNoSuccessfulDecisionAndQueueUsesActualDeliveryMetadata() {
    var logger = (Logger) LoggerFactory.getLogger(BusinessTelemetry.class);
    var appender = new ListAppender<ILoggingEvent>();
    appender.start();
    logger.addAppender(appender);
    try {
      var order = UUID.randomUUID();
      TransactionSynchronizationManager.initSynchronization();
      TransactionSynchronizationManager.setActualTransactionActive(true);
      BusinessTelemetry.committed("probe", order, "A", "B", "COMMITTED");
      var callbacks = TransactionSynchronizationManager.getSynchronizations();
      assertThat(appender.list).isEmpty();
      TransactionSynchronizationManager.clearSynchronization();
      TransactionSynchronizationManager.setActualTransactionActive(false);
      callbacks.forEach(c -> c.afterCompletion(STATUS_ROLLED_BACK));
      assertThat(appender.list).isEmpty();
      var span = Mockito.mock(Span.class, CALLS_REAL_METHODS);
      var properties = new MessageProperties();
      properties.setConsumerQueue("actual.queue");
      var message = new Message(new byte[0], properties);
      try (var scope = Context.current().with(span).makeCurrent();
          var telemetry = new RabbitTelemetry(message)) {
        telemetry.context(UUID.randomUUID(), order);
        telemetry.result("SUCCESS", "TRANSACTION_COMMITTED");
      }
      Mockito.verify(span).setAttribute("messaging.destination.name", "actual.queue");
      Mockito.verify(span).setAttribute("rabbitmq.queue", "actual.queue");
    } finally {
      TransactionSynchronizationManager.clear();
      logger.detachAppender(appender);
      appender.stop();
    }
  }
}
