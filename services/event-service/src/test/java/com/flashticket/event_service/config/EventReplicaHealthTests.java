package com.flashticket.event_service.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

import com.zaxxer.hikari.HikariDataSource;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.*;

class EventReplicaHealthTests {
  HikariDataSource writer;
  HikariDataSource reader;
  Connection writerConnection;
  Connection readerConnection;
  Statement writerStatement;
  Statement readerStatement;
  ResultSet writerRow;
  ResultSet readerRow;
  SimpleMeterRegistry metrics;
  EventReplicaHealth health;

  @BeforeEach
  void setUp() {
    writer = mock(HikariDataSource.class);
    reader = mock(HikariDataSource.class);
    writerConnection = mock(Connection.class);
    readerConnection = mock(Connection.class);
    writerStatement = mock(Statement.class);
    readerStatement = mock(Statement.class);
    writerRow = mock(ResultSet.class);
    readerRow = mock(ResultSet.class);
    try {
      when(writer.getConnection()).thenReturn(writerConnection);
      when(reader.getConnection()).thenReturn(readerConnection);
      when(writerConnection.createStatement()).thenReturn(writerStatement);
      when(readerConnection.createStatement()).thenReturn(readerStatement);
      when(writerStatement.executeQuery(anyString())).thenReturn(writerRow);
      when(readerStatement.executeQuery(anyString())).thenReturn(readerRow);
      when(writerRow.next()).thenReturn(true);
      when(readerRow.next()).thenReturn(true);
      when(writerRow.getBoolean(1)).thenReturn(false);
      when(readerRow.getBoolean(1)).thenReturn(true);
      when(writerRow.getBoolean(2)).thenReturn(true);
      when(readerRow.getBoolean(2)).thenReturn(true);
      when(writerRow.getString(3)).thenReturn("80000000/1");
      when(readerRow.getString(3)).thenReturn("80000000/2");
    } catch (SQLException failure) {
      throw new AssertionError("JDBC mock setup failed", failure);
    }
    metrics = new SimpleMeterRegistry();
    health = new EventReplicaHealth(writer, reader, Duration.ofSeconds(5), 2, metrics);
  }

  @Test
  void acceptsOnlyUnpausedCaughtUpStandby() throws Exception {
    assertThat(health.tryBeginRead()).isFalse();
    doAnswer(
            invocation -> {
              verify(writerConnection).close();
              return readerConnection;
            })
        .when(reader)
        .getConnection();
    health.refresh();
    assertThat(health.tryBeginRead()).isTrue();
    health.finishRead();
    verify(writerStatement).setQueryTimeout(2);
    verify(readerStatement).setQueryTimeout(2);
    verify(readerConnection).close();
    assertThat(
            metrics.get("event.read.replica.probe").tag("outcome", "caught_up").counter().count())
        .isEqualTo(1);
    // Signed long comparison sai sẽ cho reader phía dưới 2^63 lớn hơn writer phía trên.
    clearInvocations(writerConnection);
    when(readerRow.getString(3)).thenReturn("7FFFFFFF/FFFFFFFF");
    health.refresh();
    assertThat(health.tryBeginRead()).isFalse();
  }

  @Test
  void rejectsUnknownBehindOrExpiredSample() throws Exception {
    for (String lsn : new String[] {null, "invalid", "0/0", "80000000/0"}) {
      when(readerRow.getString(3)).thenReturn(lsn);
      health.refresh();
      assertThat(health.tryBeginRead()).as("invalid/behind LSN %s", lsn).isFalse();
    }
    when(readerRow.getString(3)).thenReturn("80000000/2");
    when(readerRow.getBoolean(2)).thenReturn(false);
    health.refresh();
    assertThat(health.tryBeginRead()).isFalse();
    when(readerRow.getBoolean(2)).thenReturn(true);
    when(readerRow.getBoolean(1)).thenReturn(false);
    health.refresh();
    assertThat(health.tryBeginRead()).isFalse();
    when(readerRow.getBoolean(1)).thenReturn(true);
    when(writerRow.getBoolean(1)).thenReturn(true);
    health.refresh();
    assertThat(health.tryBeginRead()).isFalse();
    when(writerRow.getBoolean(1)).thenReturn(false);
    when(writerRow.wasNull()).thenReturn(true);
    health.refresh();
    assertThat(health.tryBeginRead()).isFalse();
    when(writerRow.wasNull()).thenReturn(false);
    var expired = new EventReplicaHealth(writer, reader, Duration.ofNanos(1), 2, metrics);
    expired.refresh();
    assertThat(expired.tryBeginRead()).isFalse();
    when(readerStatement.executeQuery(anyString())).thenThrow(new SQLException("probe timeout"));
    health.refresh();
    assertThat(health.tryBeginRead()).isFalse();
  }

  @Test
  void ignoresProbeStartedBeforeInvalidation() throws Exception {
    var started = new CountDownLatch(1);
    var proceed = new CountDownLatch(1);
    doAnswer(
            invocation -> {
              started.countDown();
              if (!proceed.await(5, TimeUnit.SECONDS))
                throw new SQLException("probe latch deadline");
              return readerConnection;
            })
        .when(reader)
        .getConnection();
    Thread probe = new Thread(health::refresh);
    probe.start();
    try {
      assertThat(started.await(5, TimeUnit.SECONDS)).isTrue();
      health.invalidate();
    } finally {
      proceed.countDown();
      probe.join(5000);
    }
    assertThat(probe.isAlive()).isFalse();
    assertThat(health.tryBeginRead()).isFalse();
    health.refresh();
    assertThat(health.tryBeginRead()).isTrue();
    health.finishRead();
  }

  @Test
  void refusesConcurrentReaderWithoutWaiting() throws Exception {
    health.refresh();
    assertThat(health.tryBeginRead()).isTrue();
    var admitted = new AtomicBoolean(true);
    Thread other = new Thread(() -> admitted.set(health.tryBeginRead()));
    other.start();
    other.join(2000);
    assertThat(other.isAlive()).isFalse();
    assertThat(admitted.get()).isFalse();
    health.finishRead();
    assertThat(health.tryBeginRead()).isTrue();
    health.finishRead();
    health.invalidate();
    assertThat(health.tryBeginRead()).isFalse();
  }
}
