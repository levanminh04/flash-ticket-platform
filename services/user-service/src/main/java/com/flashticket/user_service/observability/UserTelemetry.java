package com.flashticket.user_service.observability;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Clock;
import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class UserTelemetry {
  private static final Logger LOG = LoggerFactory.getLogger(UserTelemetry.class);
  private final MeterRegistry metrics;
  private final Clock clock;

  @SuppressFBWarnings(
      value = "EI_EXPOSE_REP2",
      justification =
          "Shares Spring's registry intentionally so workflow metrics are collected by the application.")
  public UserTelemetry(MeterRegistry metrics, Clock clock) {
    this.metrics = metrics;
    this.clock = clock;
  }

  public void decision(
      String operation, String outcome, String before, String after, String reason) {
    LOG.atInfo()
        .addKeyValue("eventName", "user." + operation)
        .addKeyValue("operation", operation)
        .addKeyValue("outcome", outcome)
        .addKeyValue("stateBefore", before)
        .addKeyValue("stateAfter", after)
        .addKeyValue("reasonCode", reason)
        .log("User workflow decision");
    metrics.counter("user.workflow", "operation", operation, "outcome", outcome).increment();
  }

  public void projectionAge(Instant source) {
    metrics
        .summary("user.projection.age", "unit", "seconds")
        .record(Math.max(0, java.time.Duration.between(source, clock.instant()).toSeconds()));
  }
}
