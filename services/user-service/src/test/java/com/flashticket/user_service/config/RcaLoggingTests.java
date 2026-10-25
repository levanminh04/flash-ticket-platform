package com.flashticket.user_service.config;

import static org.assertj.core.api.Assertions.*;

import com.flashticket.user_service.exception.BusinessException;
import com.flashticket.user_service.exception.ErrorCode;
import com.flashticket.user_service.exception.GlobalExceptionHandler;
import com.flashticket.user_service.support.LogCapture;
import java.io.IOException;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.slf4j.MDC;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

@ExtendWith(OutputCaptureExtension.class)
class RcaLoggingTests {
  @AfterEach
  void clear() {
    MDC.clear();
  }

  @Test
  void stackRetainsFramesWithoutSensitiveMessages() {
    var error = new IllegalStateException("SENTINEL-secret", new IOException("SENTINEL-body"));
    error.addSuppressed(new IllegalArgumentException("SENTINEL-suppressed"));
    String stack = new SafeStackTracePrinter().printStackTraceToString(error);
    assertThat(stack).contains("IllegalStateException", "IOException", "RcaLoggingTests");
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
  void actualJsonLogsHaveRcaContextAndExcludeSensitiveValues(CapturedOutput output)
      throws IOException {
    try (var context = LogCapture.bootJsonLogging()) {
      UUID correlation = UUID.randomUUID();
      var handler = new GlobalExceptionHandler();
      MDC.put("Authorization", "SENTINEL-authorization");
      MDC.put("correlationId", "SENTINEL-stale-correlation");
      var error = new IllegalStateException("SENTINEL-error", new IOException("SENTINEL-cause"));
      var request = new MockHttpServletRequest("POST", "/orders/SENTINEL-private-path");
      request.addHeader("X-Correlation-Id", correlation.toString());
      var response = handler.handleException(error, request, new MockHttpServletResponse());
      assertThat(response.getBody().correlationId()).isEqualTo(correlation);
      assertThat(MDC.get("correlationId")).isEqualTo("SENTINEL-stale-correlation");
      var rejection = new BusinessException(ErrorCode.DATA_CONFLICT);
      handler.handleBusiness(rejection, new MockHttpServletRequest("GET", "/orders"));
      List<JsonNode> logs =
          output
              .getOut()
              .lines()
              .filter(line -> line.startsWith("{"))
              .map(line -> JsonMapper.builder().build().readTree(line))
              .filter(node -> node.has("errorCode"))
              .toList();
      assertThat(logs).hasSize(2);
      JsonNode failure = logs.getFirst();
      assertThat(failure.get("schemaVersion").isInt()).isTrue();
      assertThat(failure.get("schemaVersion").asInt()).isEqualTo(1);
      assertThat(failure.get("timestamp").asString()).endsWith("Z");
      assertThat(failure.get("severity").asString()).isEqualTo("ERROR");
      assertThat(failure.get("serviceName").asString()).isEqualTo("user-service");
      assertThat(failure.get("serviceVersion").asString()).isNotBlank();
      assertThat(failure.get("environment").asString()).isNotBlank();
      assertThat(failure.get("correlationId").asString()).isEqualTo(correlation.toString());
      assertThat(failure.get("operation").asString()).isEqualTo("http.request");
      assertThat(failure.get("httpStatus").asInt()).isEqualTo(500);
      assertThat(failure.get("httpMethod").asString()).isEqualTo("POST");
      assertThat(failure.get("stackTrace").asString())
          .contains("IllegalStateException", "IOException");
      assertThat(logs.get(1).get("severity").asString()).isEqualTo("INFO");
      assertThat(logs.get(1).get("message").asString()).contains(ErrorCode.DATA_CONFLICT.message());
      assertThat(logs.get(1).has("stackTrace")).isFalse();
      assertThat(logs.get(1).get("instanceId").asString())
          .isEqualTo(failure.get("instanceId").asString());
      output
          .getOut()
          .lines()
          .filter(line -> line.startsWith("{") && line.contains("errorCode"))
          .forEach(
              line -> assertThat(line.split("\"correlationId\":", -1).length - 1).isEqualTo(1));
      assertThat(output.getAll()).doesNotContain("SENTINEL-", "failed to append");
    }
  }
}
