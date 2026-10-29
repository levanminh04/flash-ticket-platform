package com.flashticket.payment_service.exception;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.io.IOException;
import java.nio.file.Path;
import java.util.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.slf4j.MDC;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.env.EnumerablePropertySource;
import org.springframework.core.io.FileSystemResource;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.validation.BindException;
import org.springframework.validation.FieldError;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import tools.jackson.databind.json.JsonMapper;

@ExtendWith(OutputCaptureExtension.class)
class PaymentErrorLoggingTests {
  @TestConfiguration(proxyBeanMethods = false)
  static class LoggingProbe {}

  private static ConfigurableApplicationContext bootJsonLogging() throws IOException {
    var application = new SpringApplication(LoggingProbe.class);
    application.setWebApplicationType(WebApplicationType.NONE);
    var yaml =
        Path.of(System.getProperty("basedir", "."))
            .resolve(
                "../../infrastructure/config-server/src/main/resources/config/payment-service.yaml");
    var source =
        (EnumerablePropertySource<?>)
            new YamlPropertySourceLoader().load("payment", new FileSystemResource(yaml)).getFirst();
    Map<String, Object> properties = new HashMap<>();
    for (String key : source.getPropertyNames())
      if (key.startsWith("logging.")) properties.put(key, source.getProperty(key));
    application.setDefaultProperties(properties);
    return application.run(
        "--spring.config.location=optional:classpath:payment-log-isolation.yaml",
        "--spring.cloud.config.enabled=false",
        "--eureka.client.enabled=false",
        "--spring.main.banner-mode=off");
  }

  @AfterEach
  void clearMdc() {
    MDC.clear();
  }

  @Test
  void keepsProblemHeaderAndLogCorrelationConsistent(CapturedOutput output) throws Exception {
    try (var context = bootJsonLogging()) {
      var id = UUID.randomUUID();
      var request = new MockHttpServletRequest("GET", "/api/v1/orders/one/payment");
      request.addHeader("X-Correlation-Id", id.toString());
      MDC.put("correlationId", "old-context");
      var result =
          new GlobalExceptionHandler()
              .handleBusiness(new BusinessException(ErrorCode.PAYMENT_NOT_READY), request);
      assertThat(result.getHeaders().getFirst("X-Correlation-Id")).isEqualTo(id.toString());
      assertThat(result.getBody().getCorrelationId()).isEqualTo(id);
      var logs =
          output
              .getOut()
              .lines()
              .filter(line -> line.startsWith("{"))
              .map(line -> JsonMapper.builder().build().readTree(line))
              .filter(node -> node.has("errorCode"))
              .toList();
      assertThat(logs).hasSize(1);
      var log = logs.getFirst();
      assertThat(log.get("correlationId").asString()).isEqualTo(id.toString());
      assertThat(log.get("schemaVersion").asInt()).isEqualTo(1);
      assertThat(log.get("serviceName").asString()).isEqualTo("payment-service");
      assertThat(log.get("timestamp").asString()).endsWith("Z");
      assertThat(MDC.get("correlationId")).isEqualTo("old-context");
    }
  }

  @Test
  void preservesFrameworkStatusAndHeaders() throws Exception {
    var result =
        new GlobalExceptionHandler()
            .handleException(
                new HttpRequestMethodNotSupportedException("POST", List.of("GET")),
                new MockHttpServletRequest("POST", "/missing"),
                new MockHttpServletResponse());
    assertThat(result.getStatusCode().value()).isEqualTo(405);
    assertThat(result.getHeaders().getAllow()).contains(org.springframework.http.HttpMethod.GET);
    assertThat(result.getBody().getCode()).isEqualTo("METHOD_NOT_ALLOWED");
  }

  @Test
  void distinguishesInputAndOutputValidationFailures() throws Exception {
    var handler = new GlobalExceptionHandler();
    var request = new MockHttpServletRequest("POST", "/input");
    var input = new BindException(new Object(), "request");
    for (int i = 0; i < 60; i++)
      input.addError(
          new FieldError(
              "request", "email", null, false, new String[] {"Email"}, null, "SENTINEL-private"));
    var invalidInput = handler.handleValidation(input, request);
    assertThat(invalidInput.getStatusCode().value()).isEqualTo(400);
    assertThat(invalidInput.getBody().getViolations()).hasSize(50);
    var invalidOutput = mock(HandlerMethodValidationException.class);
    when(invalidOutput.isForReturnValue()).thenReturn(true);
    var response = handler.handleValidation(invalidOutput, request);
    assertThat(response.getStatusCode().value()).isEqualTo(500);
    assertThat(response.getBody().getViolations()).isEmpty();
  }

  @Test
  void redactsExceptionMessagesAndSensitiveLogFields(CapturedOutput output) throws Exception {
    try (var context = bootJsonLogging()) {
      MDC.put("Authorization", "SENTINEL-token");
      MDC.put("email", "SENTINEL-email");
      var error = new IllegalStateException("SENTINEL-SQL", new IOException("SENTINEL-secret"));
      error.addSuppressed(new IllegalArgumentException("SENTINEL-body"));
      var response =
          new GlobalExceptionHandler()
              .handleException(
                  error,
                  new MockHttpServletRequest("GET", "/SENTINEL-private-path"),
                  new MockHttpServletResponse());
      assertThat(response.getBody().getMessage()).isEqualTo(ErrorCode.INTERNAL_ERROR.message());
      var logs =
          output
              .getOut()
              .lines()
              .filter(line -> line.startsWith("{"))
              .map(line -> JsonMapper.builder().build().readTree(line))
              .filter(node -> node.has("errorCode"))
              .toList();
      assertThat(logs).hasSize(1);
      assertThat(logs.getFirst().get("stackTrace").asString())
          .contains("IllegalStateException", "IOException");
      assertThat(output.getAll()).doesNotContain("SENTINEL-", "failed to append");
    }
  }
}
