package com.flashticket.user_service;

import static org.assertj.core.api.Assertions.assertThat;

import com.flashticket.user_service.observability.UserLogCustomizer;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.context.annotation.Configuration;
import tools.jackson.databind.json.JsonMapper;

@ExtendWith(OutputCaptureExtension.class)
class UserObservabilityTests {
  @Configuration(proxyBeanMethods = false)
  static class LoggingProbe {}

  @Test
  void structuredLogKeepsCorrelationAndExcludesCredentialsAndExceptionBodies(
      CapturedOutput output) {
    var application = new SpringApplication(LoggingProbe.class);
    application.setWebApplicationType(WebApplicationType.NONE);
    application.setDefaultProperties(
        Map.of(
            "logging.structured.format.console",
            "logstash",
            "logging.structured.json.customizer",
            UserLogCustomizer.class.getName()));
    try (var context =
        application.run(
            "--spring.config.location=optional:file:target/logging-probe.yaml",
            "--spring.cloud.config.enabled=false",
            "--spring.main.banner-mode=off")) {
      MDC.put("correlationId", "00000000-0000-4000-8000-000000000001");
      MDC.put("Authorization", "SENTINEL-credential");
      try {
        LoggerFactory.getLogger(UserObservabilityTests.class)
            .atWarn()
            .addKeyValue("eventName", "user.role-grant")
            .addKeyValue("operation", "role-grant")
            .addKeyValue("outcome", "RETRY")
            .setCause(new RuntimeException("SENTINEL-provider-body"))
            .log("Role grant pending");
      } finally {
        MDC.clear();
      }
      var json = JsonMapper.builder().build();
      var record =
          output
              .getOut()
              .lines()
              .filter(line -> line.startsWith("{"))
              .map(json::readTree)
              .filter(
                  n ->
                      n.has("eventName") && n.get("eventName").asString().equals("user.role-grant"))
              .findFirst()
              .orElseThrow();
      assertThat(record.get("serviceName").asString()).isEqualTo("user-service");
      assertThat(record.get("severity").asString()).isEqualTo("WARN");
      assertThat(record.get("correlationId").asString())
          .isEqualTo("00000000-0000-4000-8000-000000000001");
      assertThat(output.getOut()).doesNotContain("SENTINEL-credential", "SENTINEL-provider-body");
    }
  }
}
