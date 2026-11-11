package com.flashticket.event_service.testing;

import com.zaxxer.hikari.HikariDataSource;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import javax.sql.DataSource;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.databind.json.JsonMapper;

/** Test-only control plane. No HTTP administration endpoint and no buyer-supplied flags. */
@Component
public final class RcaFaults implements AutoCloseable {
  private final boolean enabled;
  private final Path controlFile;
  private final Path auditFile;
  private final JsonMapper json;
  private final DataSource datasource;
  private final ScheduledExecutorService watchdog =
      Executors.newSingleThreadScheduledExecutor(
          Thread.ofPlatform().daemon().name("rca-lease-watchdog").factory());
  private final ExecutorService acquisition = Executors.newVirtualThreadPerTaskExecutor();
  private final Semaphore pending = new Semaphore(5);
  private final List<Connection> held = new ArrayList<>();
  private volatile Lease cached;
  private volatile UUID poolLease;

  private record Lease(
      UUID id,
      String action,
      UUID target,
      String subject,
      int value,
      Instant expires,
      long deadline) {}

  @SuppressFBWarnings(
      value = "EI_EXPOSE_REP2",
      justification = "Shares Spring infrastructure; no accessor exposes it.")
  public RcaFaults(Environment env, JsonMapper json, ObjectProvider<DataSource> datasource) {
    boolean requested = env.getProperty("FT_FAULTS_ENABLED", Boolean.class, false);
    boolean profile = env.acceptsProfiles(Profiles.of("rca-test"));
    if (requested && !profile)
      throw new IllegalStateException("Fault controls require profile rca-test");
    this.enabled = requested && profile;
    this.json = json;
    this.datasource = enabled ? datasource.getIfAvailable() : null;
    this.controlFile =
        Path.of(env.getProperty("FT_FAULT_CONTROL_FILE", "/run/fault-control/control.json"));
    this.auditFile =
        Path.of(env.getProperty("FT_FAULT_AUDIT_FILE", "/run/fault-audit/executions.jsonl"));
  }

  @PostConstruct
  public void start() {
    if (enabled && "event_service".equals("booking_service"))
      watchdog.scheduleWithFixedDelay(this::pollSafely, 0, 100, TimeUnit.MILLISECONDS);
  }

  private void pollSafely() {
    try {
      poll();
    } catch (RuntimeException ignored) {
      release();
    }
  }

  private synchronized Lease lease() {
    if (!enabled) return null;
    try {
      if (!Files.isRegularFile(controlFile) || Files.size(controlFile) > 16384) return null;
      var node = json.readTree(Files.readString(controlFile, StandardCharsets.UTF_8));
      UUID id = UUID.fromString(node.path("leaseId").asString());
      Instant expires = Instant.parse(node.path("expiresAt").asString());
      Instant now = Instant.now();
      if (!expires.isAfter(now) || expires.isAfter(now.plusSeconds(300))) return null;
      if (cached != null && cached.id().equals(id)) {
        // A lease ID is immutable. Rewriting it cannot extend its monotonic deadline.
        return System.nanoTime() < cached.deadline() ? cached : null;
      }
      String action = node.path("action").asString();
      if (!Set.of("http.error", "http.delay", "pool.hold", "ticket.fail", "off").contains(action))
        return null;
      UUID target =
          node.hasNonNull("targetId") ? UUID.fromString(node.path("targetId").asString()) : null;
      String subject = node.path("subject").asString("");
      int value = node.path("value").asInt(0);
      if (action.equals("http.delay") && (value < 1 || value > 30000)) return null;
      if (action.equals("pool.hold") && (value < 1 || value > 5)) return null;
      if (!action.equals("off") && !action.equals("pool.hold") && target == null) return null;
      cached =
          new Lease(
              id,
              action,
              target,
              subject,
              value,
              expires,
              System.nanoTime() + Duration.between(now, expires).toNanos());
      return cached;
    } catch (IOException | RuntimeException invalid) {
      return null;
    }
  }

  private static boolean matches(Lease lease, UUID target, String subject) {
    return lease != null
        && Objects.equals(target, lease.target())
        && (lease.subject().isEmpty() || lease.subject().equals(subject));
  }

  public void http(UUID target, String subject) {
    Lease lease = lease();
    if (!matches(lease, target, subject)) return;
    // Booking must match the fixture's owner, not just a path ID.
    if ("event_service".equals("booking_service") && lease.subject().isBlank()) return;
    if (lease.action().equals("http.error") && audit(lease, "http.error.executed"))
      throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "SERVICE_UNAVAILABLE");
    if (lease.action().equals("http.delay") && audit(lease, "http.delay.started")) {
      long end =
          Math.min(
              lease.deadline(), System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(lease.value()));
      try {
        while (System.nanoTime() < end) {
          Lease active = lease();
          if (active == null
              || !active.id().equals(lease.id())
              || !active.action().equals("http.delay")) break;
          TimeUnit.NANOSECONDS.sleep(
              Math.min(TimeUnit.MILLISECONDS.toNanos(25), Math.max(1, end - System.nanoTime())));
        }
      } catch (InterruptedException interrupted) {
        Thread.currentThread().interrupt();
        throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "SERVICE_UNAVAILABLE");
      }
      audit(lease, "http.delay.finished");
    }
  }

  public boolean failIssuance(UUID order, String subject) {
    Lease lease = lease();
    return matches(lease, order, subject)
        && !lease.subject().isBlank()
        && lease.action().equals("ticket.fail")
        && audit(lease, "ticket.fail.executed");
  }

  /** Separate watchdog ensures blocked acquisition cannot postpone lease cleanup. */
  public void poll() {
    if (!enabled || !"event_service".equals("booking_service")) return;
    Lease lease = lease();
    if (lease == null || !lease.action().equals("pool.hold")) {
      release();
      return;
    }
    if (lease.id().equals(poolLease)) return;
    release();
    if (datasource == null || !audit(lease, "pool.hold.started")) return;
    poolLease = lease.id();
    for (int i = 0; i < lease.value(); i++) {
      if (!pending.tryAcquire()) break;
      acquisition.submit(
          () -> {
            Connection connection = null;
            try {
              connection = datasource.getConnection();
              // No transaction and no SQL/row locks are held.
              connection.setAutoCommit(true);
              synchronized (held) {
                if (lease.id().equals(poolLease) && System.nanoTime() < lease.deadline()) {
                  held.add(connection);
                  connection = null;
                }
              }
            } catch (SQLException unavailable) {
              audit(lease, "pool.acquire.failed");
            } finally {
              if (connection != null) closeConnection(connection);
              pending.release();
            }
          });
    }
  }

  private void release() {
    poolLease = null;
    synchronized (held) {
      for (Connection connection : held) closeConnection(connection);
      held.clear();
    }
  }

  private static void closeConnection(Connection connection) {
    try {
      connection.close();
    } catch (SQLException ignored) {
      /* lease is already revoked */
    }
  }

  private synchronized boolean audit(Lease lease, String operation) {
    try {
      Path parent = auditFile.toAbsolutePath().getParent();
      if (parent != null) Files.createDirectories(parent);
      Map<String, Object> record = new LinkedHashMap<>();
      record.put("timestamp", Instant.now().toString());
      record.put("monotonicNanos", System.nanoTime());
      record.put("leaseId", lease.id().toString());
      record.put("operation", operation);
      if (lease.target() != null) record.put("targetId", lease.target().toString());
      if (datasource instanceof HikariDataSource hikari && hikari.getHikariPoolMXBean() != null) {
        var pool = hikari.getHikariPoolMXBean();
        record.put("poolName", hikari.getPoolName());
        record.put("active", pool.getActiveConnections());
        record.put("idle", pool.getIdleConnections());
        record.put("total", pool.getTotalConnections());
        record.put("pending", pool.getThreadsAwaitingConnection());
      }
      Files.writeString(
          auditFile,
          json.writeValueAsString(record) + "\n",
          StandardCharsets.UTF_8,
          StandardOpenOption.CREATE,
          StandardOpenOption.APPEND);
      return true;
    } catch (IOException | RuntimeException unavailable) {
      return false;
    }
  }

  @Override
  @PreDestroy
  public void close() {
    watchdog.shutdownNow();
    release();
    acquisition.shutdownNow();
  }
}
