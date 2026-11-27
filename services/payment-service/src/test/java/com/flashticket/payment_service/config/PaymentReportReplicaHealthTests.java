package com.flashticket.payment_service.config;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.zaxxer.hikari.HikariDataSource;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class PaymentReportReplicaHealthTests {
  HikariDataSource writer;
  HikariDataSource reader;
  Connection writerConnection;
  Connection readerConnection;
  Statement writerStatement;
  Statement readerStatement;
  ResultSet writerRow;
  ResultSet readerRow;
  PaymentReportReplicaHealth health;

  @BeforeEach
  void setUp() throws Exception {
    writer = mock(HikariDataSource.class);
    reader = mock(HikariDataSource.class);
    writerConnection = mock(Connection.class);
    readerConnection = mock(Connection.class);
    writerStatement = mock(Statement.class);
    readerStatement = mock(Statement.class);
    writerRow = mock(ResultSet.class);
    readerRow = mock(ResultSet.class);
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
    when(writerRow.getString(3)).thenReturn("80000000/00000001");
    when(readerRow.getString(3)).thenReturn("80000000/00000002");
    health =
        new PaymentReportReplicaHealth(
            writer, reader, Duration.ofSeconds(5), 2, new SimpleMeterRegistry());
  }

  @Test
  void permitsOnlyCaughtUpUnpausedStandbyWithExpectedRoles() throws Exception {
    assertThat(health.tryBeginRead()).isFalse();
    health.refresh();
    assertThat(health.tryBeginRead()).isTrue();
    health.finishRead();
    var order = inOrder(writerConnection, reader);
    order.verify(writerConnection).close();
    order.verify(reader).getConnection();
    verify(writerStatement).setQueryTimeout(2);
    verify(readerStatement).setQueryTimeout(2);
    for (String lsn : new String[] {"7FFFFFFF/FFFFFFFF", "0/0", "malformed", "1/100000000"}) {
      when(readerRow.getString(3)).thenReturn(lsn);
      health.refresh();
      assertThat(health.tryBeginRead()).isFalse();
    }
    when(readerRow.getString(3)).thenReturn(null);
    health.refresh();
    assertThat(health.tryBeginRead()).isFalse();
    when(readerRow.getString(3)).thenReturn("80000000/00000002");
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
    when(writer.getConnection()).thenThrow(new java.sql.SQLException("fixture connection"));
    health.refresh();
    assertThat(health.tryBeginRead()).isFalse();
  }

  @Test
  void expiredSamplesAndBusyReadersChooseWriterWithoutWaiting() throws Exception {
    health.refresh();
    assertThat(health.tryBeginRead()).isTrue();
    assertThat(health.tryBeginRead()).isFalse();
    health.finishRead();
    assertThat(health.tryBeginRead()).isTrue();
    health.finishRead();
    ReflectionTestUtils.setField(health, "maxSampleAgeNanos", 1L);
    assertThat(health.tryBeginRead()).isFalse();
  }

  @Test
  void invalidationFencesAnAlreadyRunningProbe() throws Exception {
    var started = new CountDownLatch(1);
    var release = new CountDownLatch(1);
    when(reader.getConnection())
        .thenAnswer(
            invocation -> {
              started.countDown();
              assertThat(release.await(10, TimeUnit.SECONDS)).isTrue();
              return readerConnection;
            });
    try (var pool = Executors.newSingleThreadExecutor()) {
      var pending =
          pool.submit(
              () -> {
                health.refresh();
                return null;
              });
      try {
        assertThat(started.await(10, TimeUnit.SECONDS)).isTrue();
        health.invalidate();
      } finally {
        release.countDown();
      }
      pending.get(10, TimeUnit.SECONDS);
    }
    assertThat(health.tryBeginRead()).isFalse();
    when(reader.getConnection()).thenReturn(readerConnection);
    health.refresh();
    assertThat(health.tryBeginRead()).isTrue();
    health.finishRead();
  }
}
