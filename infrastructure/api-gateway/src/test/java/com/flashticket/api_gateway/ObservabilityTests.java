package com.flashticket.api_gateway;

import static org.assertj.core.api.Assertions.*;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.flashticket.api_gateway.web.CorrelationIdFilter;
import com.flashticket.api_gateway.web.GatewayErrorWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.system.*;
import org.springframework.core.env.EnumerablePropertySource;
import org.springframework.core.io.FileSystemResource;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import reactor.core.publisher.Mono;
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
  void gatewayWritesOneResultForSuccessErrorAndCancellation() throws Exception {
    var constructor = CorrelationIdFilter.class.getDeclaredConstructor(GatewayErrorWriter.class);
    constructor.setAccessible(true);
    var filter = constructor.newInstance(new GatewayErrorWriter());
    var logger = (Logger) LoggerFactory.getLogger(CorrelationIdFilter.class);
    var appender = new ListAppender<ILoggingEvent>();
    appender.start();
    logger.addAppender(appender);
    try {
      var success = MockServerWebExchange.from(MockServerHttpRequest.get("/api/v1/events"));
      filter
          .filter(
              success,
              exchange -> {
                exchange.getResponse().setStatusCode(HttpStatus.OK);
                return exchange.getResponse().setComplete();
              })
          .block();
      var failure = MockServerWebExchange.from(MockServerHttpRequest.get("/api/v1/events"));
      assertThatThrownBy(
              () ->
                  filter
                      .filter(
                          failure, exchange -> Mono.error(new IllegalStateException("SENTINEL")))
                      .block())
          .isInstanceOf(IllegalStateException.class);
      var cancel = MockServerWebExchange.from(MockServerHttpRequest.get("/api/v1/events"));
      var subscription = filter.filter(cancel, exchange -> Mono.never()).subscribe();
      subscription.dispose();
      assertThat(appender.list).hasSize(3);
      assertThat(appender.list.get(0).getKeyValuePairs())
          .anySatisfy(
              pair -> {
                assertThat(pair.key).isEqualTo("outcome");
                assertThat(pair.value).isEqualTo("SUCCESS");
              });
      assertThat(appender.list.get(1).getKeyValuePairs())
          .anySatisfy(
              pair -> {
                assertThat(pair.key).isEqualTo("outcome");
                assertThat(pair.value).isEqualTo("FAILED");
              });
      assertThat(appender.list.get(2).getKeyValuePairs())
          .anySatisfy(
              pair -> {
                assertThat(pair.key).isEqualTo("outcome");
                assertThat(pair.value).isEqualTo("UNKNOWN");
              });
    } finally {
      logger.detachAppender(appender);
      appender.stop();
    }
  }
}
