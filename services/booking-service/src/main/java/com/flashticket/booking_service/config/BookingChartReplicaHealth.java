package com.flashticket.booking_service.config;

import com.zaxxer.hikari.HikariDataSource;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import java.sql.SQLException;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import org.springframework.scheduling.annotation.Scheduled;

public final class BookingChartReplicaHealth {
  private static final String WRITER_WAL_POSITION =
      """
      SELECT pg_is_in_recovery(), true,
             CASE WHEN NOT pg_is_in_recovery() THEN pg_current_wal_flush_lsn() END::text
      """;
  private static final String REPLICA_WAL_POSITION =
      """
      SELECT pg_is_in_recovery(),
             CASE WHEN pg_is_in_recovery() THEN pg_get_wal_replay_pause_state() = 'not paused'
                  ELSE false END,
             CASE WHEN pg_is_in_recovery() THEN pg_last_wal_replay_lsn() END::text
      """;
  private static final String PROBE_METRIC = "booking.sales.chart.replica.probe";

  private final HikariDataSource writer;
  private final HikariDataSource reader;
  private final long maxSampleAgeNanos;
  private final int probeQueryTimeoutSeconds;
  private final Counter caughtUpProbes;
  private final Counter behindProbes;
  private final Counter failedProbes;
  private final AtomicReference<Sample> sample = new AtomicReference<>(new Sample(false, 0L));
  private final AtomicBoolean probing = new AtomicBoolean();
  private final AtomicBoolean reading = new AtomicBoolean();

  BookingChartReplicaHealth(
      HikariDataSource writer,
      HikariDataSource reader,
      Duration maxSampleAge,
      int probeQueryTimeoutSeconds,
      MeterRegistry metrics) {
    if (maxSampleAge.isZero() || maxSampleAge.isNegative() || probeQueryTimeoutSeconds <= 0) {
      throw new IllegalArgumentException(
          "Replica probe sample age and query timeout must be positive");
    }
    this.writer = writer;
    this.reader = reader;
    this.maxSampleAgeNanos = maxSampleAge.toNanos();
    this.probeQueryTimeoutSeconds = probeQueryTimeoutSeconds;
    this.caughtUpProbes = metrics.counter(PROBE_METRIC, "outcome", "caught_up");
    this.behindProbes = metrics.counter(PROBE_METRIC, "outcome", "behind");
    this.failedProbes = metrics.counter(PROBE_METRIC, "outcome", "error");
  }

  @Scheduled(
      fixedDelayString = "${booking.sales-chart-replica.probe-interval}",
      initialDelayString = "${booking.sales-chart-replica.probe-interval}",
      scheduler = "bookingChartReplicaScheduler")
  void refresh() {
    if (!probing.compareAndSet(false, true)) return;
    var previousSample = sample.get();
    // Tuổi mẫu tính từ trước cả thời gian chờ kết nối writer.
    long sampledAtNanos = System.nanoTime();
    try {
      boolean eligible;
      try {
        long writerFlushLsn = readWalPosition(writer, false);
        // readWalPosition đã trả kết nối writer trước khi mượn từ pool reader.
        long readerReplayLsn = readWalPosition(reader, true);
        eligible = Long.compareUnsigned(readerReplayLsn, writerFlushLsn) >= 0;
      } catch (SQLException | RuntimeException failure) {
        if (sample.compareAndSet(previousSample, new Sample(false, sampledAtNanos))) {
          failedProbes.increment();
        }
        return;
      }
      // Probe bắt đầu trước invalidate không được công bố lại trạng thái cũ.
      if (sample.compareAndSet(previousSample, new Sample(eligible, sampledAtNanos))) {
        (eligible ? caughtUpProbes : behindProbes).increment();
      }
    } finally {
      probing.set(false);
    }
  }

  public boolean tryBeginRead() {
    if (!reading.compareAndSet(false, true)) return false;
    var currentSample = sample.get();
    if (currentSample.eligible()
        && System.nanoTime() - currentSample.sampledAtNanos() < maxSampleAgeNanos) {
      return true;
    }
    reading.set(false);
    return false;
  }

  public void finishRead() {
    reading.set(false);
  }

  public void invalidate() {
    sample.set(new Sample(false, 0L));
  }

  private long readWalPosition(HikariDataSource dataSource, boolean replica) throws SQLException {
    try (var connection = dataSource.getConnection();
        var statement = connection.createStatement()) {
      statement.setQueryTimeout(probeQueryTimeoutSeconds);
      try (var row = statement.executeQuery(replica ? REPLICA_WAL_POSITION : WRITER_WAL_POSITION)) {
        if (!row.next() || row.getBoolean(1) != replica || row.wasNull()) {
          throw new SQLException("Replica probe could not verify database role");
        }
        if (!row.getBoolean(2)) {
          throw new SQLException("Replica probe could not verify that replay is running");
        }
        String lsn = row.getString(3);
        if (lsn == null || !lsn.matches("[0-9A-Fa-f]{1,8}/[0-9A-Fa-f]{1,8}")) {
          throw new SQLException("Replica probe could not read a valid WAL position");
        }
        int separator = lsn.indexOf('/');
        long high = Long.parseUnsignedLong(lsn.substring(0, separator), 16);
        long low = Long.parseUnsignedLong(lsn.substring(separator + 1), 16);
        long position = (high << 32) | low;
        if (position == 0L)
          throw new SQLException("Replica probe received an invalid WAL position");
        return position;
      }
    }
  }

  private record Sample(boolean eligible, long sampledAtNanos) {}
}
