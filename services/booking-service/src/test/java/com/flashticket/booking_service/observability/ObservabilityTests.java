package com.flashticket.booking_service.observability;

import static org.assertj.core.api.Assertions.*;

import com.flashticket.booking_service.exception.BusinessErrorCode;
import com.flashticket.booking_service.exception.BusinessException;
import com.flashticket.booking_service.exception.ErrorClassifier;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.slf4j.MDC;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.FileSystemResource;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

@ExtendWith(OutputCaptureExtension.class)
class ObservabilityTests {
  @AfterEach
  void clear() {
    MDC.clear();
    RequestContextHolder.resetRequestAttributes();
  }

  @Test
  void scopeRestoresPreviousMdcAfterFailure() {
    MDC.put("unrelated", "parent");
    UUID outer = UUID.randomUUID();
    try (var ignored = BookingContext.open(Map.of("correlationId", outer.toString()))) {
      assertThat(BookingContext.correlationId()).isEqualTo(outer);
      assertThat(MDC.get("unrelated")).isNull();
      assertThatThrownBy(
              () -> {
                try (var inner =
                    BookingContext.open(
                        Map.of("correlationId", "1-1-1-1-1", "Authorization", "secret"))) {
                  assertThat(BookingContext.correlationId()).isNull();
                  assertThat(MDC.get("Authorization")).isNull();
                  throw new IllegalStateException("probe");
                }
              })
          .isInstanceOf(IllegalStateException.class);
      assertThat(BookingContext.correlationId()).isEqualTo(outer);
    }
    assertThat(MDC.get("unrelated")).isEqualTo("parent");
    assertThat(BookingContext.correlationId()).isNull();
  }

  @Test
  void verifiedOrderBindingUpdatesServletContext() {
    var request = new MockHttpServletRequest();
    var response = new MockHttpServletResponse();
    RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request, response));
    UUID order = UUID.randomUUID();
    try (var ignored = BookingContext.open(Map.of())) {
      BookingContext.bindOrder(order);
      assertThat(BookingContext.correlationId()).isEqualTo(order);
      assertThat(MDC.get("orderId")).isEqualTo(order.toString());
      assertThat(request.getAttribute(BookingContext.CORRELATION_ATTRIBUTE)).isEqualTo(order);
      assertThat(response.getHeader("X-Correlation-Id")).isEqualTo(order.toString());
    }
  }

  @Test
  void stackRetainsFramesWithoutSensitiveMessages() {
    var error = new IllegalStateException("SENTINEL-secret", new IOException("SENTINEL-body"));
    error.addSuppressed(new IllegalArgumentException("SENTINEL-suppressed"));
    String stack = new SafeStackTracePrinter().printStackTraceToString(error);
    assertThat(stack).contains("IllegalStateException", "IOException", "ObservabilityTests");
    assertThat(stack).doesNotContain("SENTINEL-secret", "SENTINEL-body", "SENTINEL-suppressed");
    assertThat(stack.length()).isLessThanOrEqualTo(8192);
    assertThat(error.getMessage()).isEqualTo("SENTINEL-secret");
  }

  @Test
  void stackTraversalIsBoundedForCyclesAndDepth() {
    Throwable cycle =
        new RuntimeException("secret") {
          @Override
          public synchronized Throwable getCause() {
            return this;
          }
        };
    assertThat(new SafeStackTracePrinter().printStackTraceToString(cycle).length())
        .isLessThanOrEqualTo(8192);
    Throwable deep = new IOException("secret");
    for (int i = 0; i < 100; i++) deep = new RuntimeException("secret", deep);
    assertThat(new SafeStackTracePrinter().printStackTraceToString(deep).length())
        .isLessThanOrEqualTo(8192);
  }

  @Test
  void bootJsonOutputMatchesB16(CapturedOutput output) throws IOException {
    var application = new SpringApplication(LoggingProbe.class);
    application.setWebApplicationType(WebApplicationType.NONE);
    var source =
        new YamlPropertySourceLoader()
            .load("booking", new FileSystemResource("src/main/resources/application.yaml"))
            .getFirst();
    application.setDefaultProperties(
        Map.of(
            "logging.structured.format.console",
                source.getProperty("logging.structured.format.console"),
            "logging.structured.json.customizer",
                source.getProperty("logging.structured.json.customizer"),
            "logging.structured.json.stacktrace.printer",
                source.getProperty("logging.structured.json.stacktrace.printer")));
    try (var context =
        application.run(
            "--spring.config.location=optional:file:target/logging-probe-isolation.yaml",
            "--spring.cloud.config.enabled=false",
            "--spring.main.banner-mode=off")) {
      UUID correlation = UUID.randomUUID();
      try (var ignored = BookingContext.open(Map.of("correlationId", correlation.toString()))) {
        MDC.put("Authorization", "SENTINEL-authorization");
        FailureLog.record(
            ErrorClassifier.classify(
                new IllegalStateException("SENTINEL-error", new IOException("SENTINEL-cause"))),
            "booking.request.failed",
            "http.request",
            FailureLog.Outcome.FAILED);
        FailureLog.record(
            ErrorClassifier.classify(new BusinessException(BusinessErrorCode.ORDER_EXPIRED)),
            "booking.request.rejected",
            "http.request",
            FailureLog.Outcome.REJECTED);
      }
      FailureLog.record(
          ErrorClassifier.of("MESSAGE_PUBLISH_FAILED", null),
          "booking.worker.failed",
          "outbox.publish",
          FailureLog.Outcome.RETRY);
      List<JsonNode> logs =
          output
              .getOut()
              .lines()
              .filter(line -> line.startsWith("{"))
              .map(line -> JsonMapper.builder().build().readTree(line))
              .filter(node -> node.has("errorCode"))
              .toList();
      assertThat(logs).hasSize(3);
      JsonNode failure = logs.getFirst();
      assertThat(failure.get("schemaVersion").isInt()).isTrue();
      assertThat(failure.get("schemaVersion").asInt()).isEqualTo(1);
      assertThat(failure.get("timestamp").asString()).endsWith("Z");
      assertThat(failure.get("severity").asString()).isEqualTo("ERROR");
      assertThat(failure.get("serviceName").asString()).isEqualTo("booking-service");
      assertThat(failure.get("serviceVersion").asString()).isNotBlank();
      assertThat(failure.get("environment").asString()).isNotBlank();
      assertThat(failure.get("correlationId").asString()).isEqualTo(correlation.toString());
      assertThat(failure.get("operation").asString()).isEqualTo("http.request");
      assertThat(failure.get("stackTrace").asString())
          .contains("IllegalStateException", "IOException");
      assertThat(logs.get(1).get("severity").asString()).isEqualTo("INFO");
      assertThat(logs.get(1).has("stackTrace")).isFalse();
      assertThat(logs.get(2).get("severity").asString()).isEqualTo("WARN");
      assertThat(logs.get(2).has("correlationId")).isFalse();
      assertThat(logs.get(2).get("instanceId").asString())
          .isEqualTo(failure.get("instanceId").asString());
      assertThat(output.getOut()).doesNotContain("SENTINEL-");
    }
  }

  @Configuration(proxyBeanMethods = false)
  static class LoggingProbe {}
}
