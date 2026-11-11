package com.flashticket.event_service;

import static java.util.concurrent.TimeUnit.NANOSECONDS;
import static org.assertj.core.api.Assertions.*;

import com.flashticket.event_service.testing.RcaFaults;
import java.nio.file.*;
import java.time.Instant;
import java.util.*;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.databind.json.JsonMapper;

class RcaFaultsTests {
  @TempDir Path directory;
  private final JsonMapper json = JsonMapper.builder().build();

  private MockEnvironment environment(boolean enabled) {
    var env = new MockEnvironment();
    env.setActiveProfiles("rca-test");
    env.setProperty("FT_FAULTS_ENABLED", Boolean.toString(enabled));
    env.setProperty("FT_FAULT_CONTROL_FILE", directory.resolve("control.json").toString());
    env.setProperty("FT_FAULT_AUDIT_FILE", directory.resolve("audit.jsonl").toString());
    return env;
  }

  private void control(String action, UUID target, String subject, int value, int seconds)
      throws Exception {
    Files.writeString(
        directory.resolve("control.json"),
        json.writeValueAsString(
            Map.of(
                "leaseId",
                UUID.randomUUID().toString(),
                "expiresAt",
                Instant.now().plusSeconds(seconds).toString(),
                "action",
                action,
                "targetId",
                target.toString(),
                "subject",
                subject,
                "value",
                value)));
  }

  @Test
  void disabledAndWrongProfileNeverApplyFaults() throws Exception {
    UUID target = UUID.randomUUID();
    control("http.error", target, "buyer", 0, 30);
    try (var faults =
        new RcaFaults(
            environment(false),
            json,
            new DefaultListableBeanFactory().getBeanProvider(DataSource.class))) {
      faults.http(target, "buyer");
      assertThat(faults.failIssuance(target, "buyer")).isFalse();
    }
    var env = environment(true);
    env.setActiveProfiles("production");
    assertThatThrownBy(
            () ->
                new RcaFaults(
                    env, json, new DefaultListableBeanFactory().getBeanProvider(DataSource.class)))
        .isInstanceOf(IllegalStateException.class);
    assertThat(directory.resolve("audit.jsonl")).doesNotExist();
  }

  @Test
  void httpHookRequiresFixtureAndStopsWhenControlIsRemoved() throws Exception {
    UUID target = UUID.randomUUID();
    control("http.error", target, "buyer", 0, 30);
    try (var faults =
        new RcaFaults(
            environment(true),
            json,
            new DefaultListableBeanFactory().getBeanProvider(DataSource.class))) {
      faults.http(UUID.randomUUID(), "buyer");
      faults.http(target, "other-buyer");
      assertThatThrownBy(() -> faults.http(target, "buyer"))
          .isInstanceOf(ResponseStatusException.class);
      Files.delete(directory.resolve("control.json"));
      faults.http(target, "buyer");
      assertThat(Files.readString(directory.resolve("audit.jsonl")))
          .contains("http.error.executed");
    }
  }

  @Test
  void expiredOversizedAndLongLeasesDoNotAct() throws Exception {
    UUID target = UUID.randomUUID();
    try (var faults =
        new RcaFaults(
            environment(true),
            json,
            new DefaultListableBeanFactory().getBeanProvider(DataSource.class))) {
      control("http.error", target, "buyer", 0, -1);
      faults.http(target, "buyer");
      control("http.error", target, "buyer", 0, 600);
      faults.http(target, "buyer");
      Files.writeString(directory.resolve("control.json"), "x".repeat(17000));
      faults.http(target, "buyer");
      assertThat(directory.resolve("audit.jsonl")).doesNotExist();
    }
  }

  @Test
  void issuanceFailureRequiresExactFixtureAndPrivateAudit() throws Exception {
    UUID target = UUID.randomUUID();
    control("ticket.fail", target, "buyer", 0, 30);
    try (var faults =
        new RcaFaults(
            environment(true),
            json,
            new DefaultListableBeanFactory().getBeanProvider(DataSource.class))) {
      assertThat(faults.failIssuance(target, "other-buyer")).isFalse();
      assertThat(faults.failIssuance(target, "buyer")).isTrue();
      Files.delete(directory.resolve("control.json"));
      assertThat(faults.failIssuance(target, "buyer")).isFalse();
    }
  }

  @Test
  void delayIsBoundedAndNormalPayloadProcessingCanContinue() throws Exception {
    UUID target = UUID.randomUUID();
    control("http.delay", target, "buyer", 60, 30);
    try (var faults =
        new RcaFaults(
            environment(true),
            json,
            new DefaultListableBeanFactory().getBeanProvider(DataSource.class))) {
      long start = System.nanoTime();
      faults.http(target, "buyer");
      assertThat(NANOSECONDS.toMillis(System.nanoTime() - start)).isBetween(40L, 2000L);
      assertThat(Files.readString(directory.resolve("audit.jsonl")))
          .contains("http.delay.finished");
    }
  }
}
