package com.flashticket.booking_service.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import com.flashticket.booking_service.config.BookingChartReplicaHealth;
import com.flashticket.booking_service.config.BookingDataSourceConfig;
import com.flashticket.booking_service.config.BookingJsonConfig;
import com.flashticket.booking_service.dto.response.SalesStatisticsChartView;
import com.flashticket.booking_service.entity.Order;
import com.flashticket.booking_service.enums.Currency;
import com.flashticket.booking_service.enums.SalesGrouping;
import com.flashticket.booking_service.mapper.MappingValueConverter;
import com.flashticket.booking_service.mapper.OrderMapperImpl;
import com.flashticket.booking_service.mapper.OutboxMessageMapperImpl;
import com.flashticket.booking_service.order.service.OrderViewAssembler;
import com.flashticket.booking_service.order.service.SalesStatisticsCacheService;
import com.flashticket.booking_service.order.service.impl.OrderServiceImpl;
import com.flashticket.booking_service.redis.service.RedisService;
import com.github.dockerjava.api.model.ExposedPort;
import com.github.dockerjava.api.model.PortBinding;
import com.github.dockerjava.api.model.Ports;
import com.zaxxer.hikari.HikariDataSource;
import com.zaxxer.hikari.metrics.micrometer.MicrometerMetricsTrackerFactory;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import jakarta.persistence.EntityManagerFactory;
import java.io.IOException;
import java.nio.file.Path;
import java.sql.SQLException;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.TimeZone;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import javax.sql.DataSource;
import org.awaitility.Awaitility;
import org.hibernate.cfg.JdbcSettings;
import org.hibernate.engine.spi.SessionImplementor;
import org.hibernate.resource.jdbc.spi.StatementInspector;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.convert.ApplicationConversionService;
import org.springframework.boot.hibernate.autoconfigure.HibernateJpaAutoConfiguration;
import org.springframework.boot.hibernate.autoconfigure.HibernatePropertiesCustomizer;
import org.springframework.boot.jackson.autoconfigure.JacksonAutoConfiguration;
import org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration;
import org.springframework.boot.persistence.autoconfigure.EntityScan;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.boot.transaction.autoconfigure.TransactionAutoConfiguration;
import org.springframework.boot.validation.autoconfigure.ValidationAutoConfiguration;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.orm.jpa.EntityManagerHolder;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.Network;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.utility.MountableFile;
import tools.jackson.databind.json.JsonMapper;

class BookingChartReplicaRoutingIT {
  @Test
  void streamsCommittedMarkerToPhysicalStandby() throws Exception {
    String replicationPassword = UUID.randomUUID().toString();
    // Mạng nội bộ chỉ chứa hai container test, không publish cổng ra host.
    try (var network =
            Network.builder().createNetworkCmdModifier(cmd -> cmd.withInternal(true)).build();
        var primary = primary(network, replicationPassword, false);
        var standby = standby(network, replicationPassword, false)) {
      startReplication(primary, standby);

      // Bảng và marker đều được tạo sau khi standby đã stream, không nằm trong bản backup.
      String marker = UUID.randomUUID().toString();
      String hasMarkerTable = "select to_regclass('public.booking_b4_marker') is not null";
      assertThat(psql(standby, hasMarkerTable)).isEqualTo("f");
      psql(
          primary,
          """
          begin;
          create table public.booking_b4_marker (id uuid primary key);
          insert into public.booking_b4_marker values ('%s');
          commit;
          """
              .formatted(marker));
      String readMarker = "select id from public.booking_b4_marker";
      assertThat(psql(primary, readMarker)).isEqualTo(marker);
      Awaitility.await()
          .atMost(Duration.ofSeconds(15))
          .untilAsserted(
              () -> {
                assertThat(psql(standby, hasMarkerTable)).isEqualTo("t");
                assertThat(psql(standby, readMarker)).isEqualTo(marker);
              });
    }
  }

  @Test
  void waitsForSyncReplicationWhenStandbyDisconnects() throws Exception {
    String replicationPassword = UUID.randomUUID().toString();
    try (var network =
            Network.builder().createNetworkCmdModifier(cmd -> cmd.withInternal(true)).build();
        var primary = primary(network, replicationPassword, false);
        var standby = standby(network, replicationPassword, false)) {
      startReplication(primary, standby);
      psql(primary, "create table public.booking_b4_sync_marker (id uuid primary key)");
      // Rút ngắn phát hiện mất mạng trong fixture, không đổi timeout production.
      psql(primary, "alter system set wal_sender_timeout = '3s'");
      psql(primary, "alter system set synchronous_standby_names = 'ANY 1 (*)'");
      assertThat(psql(primary, "select pg_reload_conf()")).isEqualTo("t");
      var configureStandby =
          standby.execInContainer(
              "bash",
              "-eu",
              "-c",
              "printf '\\nwal_receiver_timeout = 3s\\n' >> \"$PGDATA/postgresql.auto.conf\"");
      assertThat(configureStandby.getExitCode()).isZero();
      assertThat(psql(standby, "select pg_reload_conf()")).isEqualTo("t");
      String quorumCount =
          "select count(*) from pg_stat_replication where state = 'streaming' and sync_state = 'quorum'";
      Awaitility.await()
          .atMost(Duration.ofSeconds(15))
          .untilAsserted(
              () -> {
                assertThat(psql(primary, quorumCount)).isEqualTo("1");
                assertThat(psql(standby, "show wal_receiver_timeout")).isEqualTo("3s");
                assertThat(
                        psql(
                            standby,
                            "select to_regclass('public.booking_b4_sync_marker') is not null"))
                    .isEqualTo("t");
              });

      String marker = UUID.randomUUID().toString();
      var commit =
          new FutureTask<>(
              () ->
                  primary.execInContainer(
                      "timeout",
                      "-k",
                      "5s",
                      "60s",
                      "psql",
                      "-X",
                      "-w",
                      "-qAt",
                      "-v",
                      "ON_ERROR_STOP=1",
                      "-U",
                      "postgres",
                      "-d",
                      "booking_db",
                      "-c",
                      """
              begin;
              set local synchronous_commit = on;
              set local application_name = 'booking_b4_sync_commit';
              insert into public.booking_b4_sync_marker values ('%s');
              """
                          .formatted(marker),
                      "-c",
                      "commit",
                      "-c",
                      "select 'commit-acknowledged'"));
      var docker = primary.getDockerClient();
      String networkId = network.getId();
      String standbyId = standby.getContainerId();
      Thread commitThread = null;
      try {
        docker
            .disconnectFromNetworkCmd()
            .withNetworkId(networkId)
            .withContainerId(standbyId)
            .exec();
        try {
          Awaitility.await()
              .atMost(Duration.ofSeconds(15))
              .untilAsserted(
                  () ->
                      assertThat(psql(primary, "select count(*) from pg_stat_replication"))
                          .isEqualTo("0"));
          assertThat(psql(standby, "select pg_is_in_recovery()")).isEqualTo("t");
          commitThread = Thread.ofVirtual().start(commit);
          Awaitility.await()
              .atMost(Duration.ofSeconds(15))
              .untilAsserted(
                  () ->
                      assertThat(
                              psql(
                                  primary,
                                  """
                  select count(*) from pg_stat_activity
                  where application_name = 'booking_b4_sync_commit' and state = 'active'
                    and lower(query) = 'commit' and wait_event_type = 'IPC' and wait_event = 'SyncRep'
                  """))
                          .isEqualTo("1"));
          assertThat(commit.isDone()).as("COMMIT chưa nhận ACK từ standby").isFalse();
        } finally {
          docker.connectToNetworkCmd().withNetworkId(networkId).withContainerId(standbyId).exec();
        }

        var result = commit.get(25, TimeUnit.SECONDS);
        assertThat(result.getExitCode()).withFailMessage(result.getStderr()).isZero();
        assertThat(result.getStdout().strip()).isEqualTo("commit-acknowledged");
        // ACK flush và dữ liệu đọc được sau replay là hai kiểm tra khác nhau.
        Awaitility.await()
            .atMost(Duration.ofSeconds(15))
            .untilAsserted(
                () -> {
                  assertThat(psql(primary, quorumCount)).isEqualTo("1");
                  assertThat(psql(standby, "select id from public.booking_b4_sync_marker"))
                      .isEqualTo(marker);
                });
      } finally {
        if (commitThread != null) {
          assertThat(commitThread.join(Duration.ofSeconds(70)))
              .as("Tiến trình COMMIT fixture phải kết thúc trước khi đóng container")
              .isTrue();
        }
      }
    }
  }

  @Test
  void routesOnlyEligibleAdminChartToStandby() throws Exception {
    String replicationPassword = UUID.randomUUID().toString();
    var originalTimeZone = TimeZone.getDefault();
    // pgJDBC gửi múi giờ mặc định của JVM; dùng tên chuẩn mà image PG16 nhận được.
    TimeZone.setDefault(TimeZone.getTimeZone("UTC"));
    try (var network = Network.newNetwork();
        var primary = primary(network, replicationPassword, true);
        var standby = standby(network, replicationPassword, true)) {
      startReplication(primary, standby);
      initializeBookingSchema(primary, standby);
      bookingContext(primary, standby)
          .run(
              context -> {
                assertThat(context).hasNotFailed();
                var chart = context.getBean(SalesStatisticsCacheService.class);
                var health =
                    context.getBean("bookingChartReplicaHealth", BookingChartReplicaHealth.class);
                var date = LocalDate.of(2026, 1, 1);
                var initialWriter = chartQueryCounts(primary);
                var initialReader = chartQueryCounts(standby);

                // Chưa chạy probe: cả hai aggregate phải vào writer.
                assertThat(
                        chart
                            .getSalesStatisticsChart(null, date, date, SalesGrouping.DAY)
                            .getItems())
                    .hasSize(1);
                var afterWriter = chartQueryCounts(primary);
                assertThat(afterWriter)
                    .containsExactly(initialWriter.get(0) + 1, initialWriter.get(1) + 1);
                assertThat(chartQueryCounts(standby)).isEqualTo(initialReader);

                // Đợi replay tới mốc WAL thật rồi dùng chính probe của Booking để mở gate.
                String flushed = psql(primary, "select pg_current_wal_flush_lsn()");
                Awaitility.await()
                    .atMost(Duration.ofSeconds(15))
                    .untilAsserted(
                        () ->
                            assertThat(
                                    psql(
                                        standby,
                                        "select pg_last_wal_replay_lsn() >= '%s'::pg_lsn"
                                            .formatted(flushed)))
                                .isEqualTo("t"));
                ReflectionTestUtils.invokeMethod(health, "refresh");
                assertThat(
                        chart
                            .getSalesStatisticsChart(null, date, date, SalesGrouping.DAY)
                            .getItems())
                    .hasSize(1);
                assertThat(chartQueryCounts(primary)).isEqualTo(afterWriter);
                assertThat(chartQueryCounts(standby))
                    .containsExactly(initialReader.get(0) + 1, initialReader.get(1) + 1);
              });
    } finally {
      TimeZone.setDefault(originalTimeZone);
    }
  }

  @Test
  void routesAdminChartToWriterWhenReplicaFlagIsOff() throws Exception {
    String replicationPassword = UUID.randomUUID().toString();
    var originalTimeZone = TimeZone.getDefault();
    TimeZone.setDefault(TimeZone.getTimeZone("UTC"));
    try (var network = Network.newNetwork();
        var primary = primary(network, replicationPassword, true);
        var standby = standby(network, replicationPassword, true)) {
      startReplication(primary, standby);
      initializeBookingSchema(primary, standby);
      bookingContext(primary, standby)
          .withPropertyValues("booking.sales-chart-replica.enabled=false")
          .run(
              context -> {
                assertThat(context)
                    .hasNotFailed()
                    .doesNotHaveBean("bookingChartReaderDataSource")
                    .doesNotHaveBean("bookingChartReplicaHealth")
                    .doesNotHaveBean("bookingAdminChartReadTransactionTemplate");
                var initialWriter = chartQueryCounts(primary);
                var initialReader = chartQueryCounts(standby);
                var chart = context.getBean(SalesStatisticsCacheService.class);
                var date = LocalDate.of(2026, 1, 1);
                assertThat(
                        chart
                            .getSalesStatisticsChart(null, date, date, SalesGrouping.DAY)
                            .getItems())
                    .hasSize(1);
                assertThat(chartQueryCounts(primary))
                    .containsExactly(initialWriter.get(0) + 1, initialWriter.get(1) + 1);
                assertThat(chartQueryCounts(standby)).isEqualTo(initialReader);
              });
    } finally {
      TimeZone.setDefault(originalTimeZone);
    }
  }

  @Test
  void keepsOrganizerChartAndWriteOnWriterWithEligibleStandby() throws Exception {
    String replicationPassword = UUID.randomUUID().toString();
    var originalTimeZone = TimeZone.getDefault();
    TimeZone.setDefault(TimeZone.getTimeZone("UTC"));
    try (var network = Network.newNetwork();
        var primary = primary(network, replicationPassword, true);
        var standby = standby(network, replicationPassword, true)) {
      startReplication(primary, standby);
      initializeBookingSchema(primary, standby);
      psql(primary, "create table booking_schema.b4_writer_marker (id uuid primary key)");
      psql(primary, "grant insert on booking_schema.b4_writer_marker to booking_owner");
      bookingContext(primary, standby)
          .run(
              context -> {
                assertThat(context).hasNotFailed();
                var chart = context.getBean(SalesStatisticsCacheService.class);
                var health =
                    context.getBean("bookingChartReplicaHealth", BookingChartReplicaHealth.class);
                String flushed = psql(primary, "select pg_current_wal_flush_lsn()");
                Awaitility.await()
                    .atMost(Duration.ofSeconds(15))
                    .untilAsserted(
                        () ->
                            assertThat(
                                    psql(
                                        standby,
                                        "select pg_last_wal_replay_lsn() >= '%s'::pg_lsn"
                                            .formatted(flushed)))
                                .isEqualTo("t"));
                var initialWriter = chartQueryCounts(primary);
                var initialReader = chartQueryCounts(standby);
                ReflectionTestUtils.invokeMethod(health, "refresh");
                assertThat(health.tryBeginRead())
                    .as("Standby đủ điều kiện trước ca đối chứng")
                    .isTrue();
                health.finishRead();

                var date = LocalDate.of(2026, 1, 1);
                assertThat(
                        chart
                            .getSalesStatisticsChart("b4-organizer", date, date, SalesGrouping.DAY)
                            .getItems())
                    .hasSize(1);
                assertThat(chartQueryCounts(primary))
                    .containsExactly(initialWriter.get(0) + 1, initialWriter.get(1) + 1);
                assertThat(chartQueryCounts(standby)).isEqualTo(initialReader);

                // Transaction thường ghi qua datasource của Booking, không dùng kết nối psql.
                var jdbc = new JdbcTemplate(context.getBean(DataSource.class));
                var transaction =
                    new TransactionTemplate(context.getBean(PlatformTransactionManager.class));
                var marker = UUID.randomUUID();
                assertThat(health.tryBeginRead())
                    .as("Standby vẫn đủ điều kiện trước khi ghi")
                    .isTrue();
                health.finishRead();
                transaction.executeWithoutResult(
                    status -> {
                      assertThat(jdbc.queryForObject("select pg_is_in_recovery()", Boolean.class))
                          .isFalse();
                      assertThat(
                              jdbc.update(
                                  "insert into booking_schema.b4_writer_marker (id) values (?)",
                                  marker))
                          .isEqualTo(1);
                    });
                assertThat(psql(primary, "select id from booking_schema.b4_writer_marker"))
                    .isEqualTo(marker.toString());
              });
    } finally {
      TimeZone.setDefault(originalTimeZone);
    }
  }

  @Test
  void returnsCachedAdminChartWithoutBorrowingDatabaseConnections() throws Exception {
    String replicationPassword = UUID.randomUUID().toString();
    var originalTimeZone = TimeZone.getDefault();
    TimeZone.setDefault(TimeZone.getTimeZone("UTC"));
    try (var network = Network.newNetwork();
        var primary = primary(network, replicationPassword, true);
        var standby = standby(network, replicationPassword, true)) {
      startReplication(primary, standby);
      initializeBookingSchema(primary, standby);
      bookingContext(primary, standby)
          .withPropertyValues(
              "booking.sales-statistics-cache.ttl=PT30S",
              "booking.sales-statistics-cache.key-prefix=b4-cache-test:")
          .run(
              context -> {
                assertThat(context).hasNotFailed();
                var writerPool = context.getBean("bookingWriterDataSource", HikariDataSource.class);
                var readerPool =
                    context.getBean("bookingChartReaderDataSource", HikariDataSource.class);
                var meters = context.getBean(SimpleMeterRegistry.class);
                var tracker = new MicrometerMetricsTrackerFactory(meters);
                writerPool.setMetricsTrackerFactory(tracker);
                readerPool.setMetricsTrackerFactory(tracker);

                String flushed = psql(primary, "select pg_current_wal_flush_lsn()");
                Awaitility.await()
                    .atMost(Duration.ofSeconds(15))
                    .untilAsserted(
                        () ->
                            assertThat(
                                    psql(
                                        standby,
                                        "select pg_last_wal_replay_lsn() >= '%s'::pg_lsn"
                                            .formatted(flushed)))
                                .isEqualTo("t"));
                var health =
                    context.getBean("bookingChartReplicaHealth", BookingChartReplicaHealth.class);
                ReflectionTestUtils.invokeMethod(health, "refresh");
                assertThat(health.tryBeginRead())
                    .as("Standby đủ điều kiện trước cache hit")
                    .isTrue();
                health.finishRead();
                // Probe vừa mượn kết nối thật ở cả hai pool, nên có thể dùng counter làm đối chứng.
                var writerAcquires =
                    meters
                        .get("hikaricp.connections.acquire")
                        .tag("pool", writerPool.getPoolName())
                        .timer();
                var readerAcquires =
                    meters
                        .get("hikaricp.connections.acquire")
                        .tag("pool", readerPool.getPoolName())
                        .timer();
                assertThat(writerAcquires.count()).isPositive();
                assertThat(readerAcquires.count()).isPositive();

                var date = LocalDate.of(2026, 1, 1);
                var cachedChart =
                    new SalesStatisticsChartView(
                        date,
                        date,
                        null,
                        "Asia/Ho_Chi_Minh",
                        Instant.parse("2026-01-02T00:00:00Z"),
                        Currency.VND,
                        SalesGrouping.DAY,
                        List.of(
                            new SalesStatisticsChartView.PeriodSales(date, date, 7, 11, "420000")));
                var redis = context.getBean(RedisService.class);
                String key = "b4-cache-test:chart:v2:system:2026-01-01:2026-01-01:DAY";
                when(redis.get(key))
                    .thenReturn(context.getBean(JsonMapper.class).writeValueAsString(cachedChart));
                var chart = context.getBean(SalesStatisticsCacheService.class);
                long writerBefore = writerAcquires.count();
                long readerBefore = readerAcquires.count();
                var actual = chart.getSalesStatisticsChart(null, date, date, SalesGrouping.DAY);
                assertThat(actual).usingRecursiveComparison().isEqualTo(cachedChart);
                assertThat(writerAcquires.count()).isEqualTo(writerBefore);
                assertThat(readerAcquires.count()).isEqualTo(readerBefore);
                verify(redis).get(key);
                verifyNoMoreInteractions(redis);
              });
    } finally {
      TimeZone.setDefault(originalTimeZone);
    }
  }

  @Test
  void usesOneRepeatableReadSnapshotForBothAggregates() throws Exception {
    String replicationPassword = UUID.randomUUID().toString();
    var originalTimeZone = TimeZone.getDefault();
    TimeZone.setDefault(TimeZone.getTimeZone("UTC"));
    try (var network = Network.newNetwork();
        var primary = primary(network, replicationPassword, true);
        var standby = standby(network, replicationPassword, true)) {
      startReplication(primary, standby);
      initializeBookingSchema(primary, standby);
      var entityManagerFactory = new AtomicReference<EntityManagerFactory>();
      var observations = new ArrayList<List<String>>();
      StatementInspector inspectAggregate =
          sql -> {
            var factory = entityManagerFactory.get();
            if (factory == null
                || !sql.contains("group by")
                || !sql.contains("booking_schema.tbl_event_sales_snapshots")
                || !(sql.contains(" from booking_schema.tbl_orders ")
                    || sql.contains(" from booking_schema.tbl_order_items "))) {
              return sql;
            }
            assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isTrue();
            var holder =
                (EntityManagerHolder) TransactionSynchronizationManager.getResource(factory);
            assertThat(holder).as("EntityManager gắn với transaction báo cáo").isNotNull();
            var session = holder.getEntityManager().unwrap(SessionImplementor.class);
            var logicalConnection = session.getJdbcCoordinator().getLogicalConnection();
            // Đọc metadata trên connection JPA đã dùng, không mượn connection riêng để quan sát.
            assertThat(logicalConnection.isPhysicallyConnected()).isTrue();
            try (var statement = logicalConnection.getPhysicalConnection().createStatement();
                var row =
                    statement.executeQuery(
                        """
                    select pg_is_in_recovery()::text, pg_backend_pid()::text,
                           current_setting('transaction_isolation'),
                           current_setting('transaction_read_only'), pg_current_snapshot()::text,
                           transaction_timestamp()::text
                    """)) {
              assertThat(row.next()).isTrue();
              observations.add(
                  List.of(
                      row.getString(1),
                      row.getString(2),
                      row.getString(3),
                      row.getString(4),
                      row.getString(5),
                      row.getString(6)));
            } catch (SQLException error) {
              throw new AssertionError("Không đọc được snapshot từ connection JPA", error);
            }
            return sql;
          };
      bookingContext(primary, standby)
          .withBean(
              HibernatePropertiesCustomizer.class,
              () ->
                  properties -> properties.put(JdbcSettings.STATEMENT_INSPECTOR, inspectAggregate))
          .run(
              context -> {
                assertThat(context).hasNotFailed();
                var chart = context.getBean(SalesStatisticsCacheService.class);
                var health =
                    context.getBean("bookingChartReplicaHealth", BookingChartReplicaHealth.class);
                String flushed = psql(primary, "select pg_current_wal_flush_lsn()");
                Awaitility.await()
                    .atMost(Duration.ofSeconds(15))
                    .untilAsserted(
                        () ->
                            assertThat(
                                    psql(
                                        standby,
                                        "select pg_last_wal_replay_lsn() >= '%s'::pg_lsn"
                                            .formatted(flushed)))
                                .isEqualTo("t"));
                var initialWriter = chartQueryCounts(primary);
                var initialReader = chartQueryCounts(standby);
                entityManagerFactory.set(context.getBean(EntityManagerFactory.class));
                ReflectionTestUtils.invokeMethod(health, "refresh");
                var date = LocalDate.of(2026, 1, 1);
                assertThat(
                        chart
                            .getSalesStatisticsChart(null, date, date, SalesGrouping.DAY)
                            .getItems())
                    .hasSize(1);

                assertThat(observations).hasSize(2);
                var first = observations.getFirst();
                assertThat(first.get(0)).isEqualTo("true");
                assertThat(Integer.parseInt(first.get(1))).isPositive();
                assertThat(first.get(2)).isEqualTo("repeatable read");
                assertThat(first.get(3)).isEqualTo("on");
                assertThat(first.get(4)).isNotBlank();
                assertThat(observations.getLast()).isEqualTo(first);
                assertThat(chartQueryCounts(primary)).isEqualTo(initialWriter);
                assertThat(chartQueryCounts(standby))
                    .containsExactly(initialReader.get(0) + 1, initialReader.get(1) + 1);
              });
    } finally {
      TimeZone.setDefault(originalTimeZone);
    }
  }

  @Test
  void propagatesReaderFailureWithoutWriterRetry() throws Exception {
    String replicationPassword = UUID.randomUUID().toString();
    var originalTimeZone = TimeZone.getDefault();
    TimeZone.setDefault(TimeZone.getTimeZone("UTC"));
    try (var network = Network.newNetwork();
        var primary = primary(network, replicationPassword, true);
        var standby = standby(network, replicationPassword, true)) {
      startReplication(primary, standby);
      initializeBookingSchema(primary, standby);
      psql(
          primary,
          "create role booking_b4_denied nologin; grant usage on schema booking_schema to booking_b4_denied");
      var entityManagerFactory = new AtomicReference<EntityManagerFactory>();
      var injectFailure = new java.util.concurrent.atomic.AtomicBoolean(true);
      StatementInspector denyFirstOrderAggregate =
          sql -> {
            var factory = entityManagerFactory.get();
            if (factory == null
                || !sql.contains("group by")
                || !sql.contains("booking_schema.tbl_event_sales_snapshots")
                || !sql.contains(" from booking_schema.tbl_orders ")
                || !injectFailure.compareAndSet(true, false)) {
              return sql;
            }
            var holder =
                (EntityManagerHolder) TransactionSynchronizationManager.getResource(factory);
            assertThat(holder).isNotNull();
            var connection =
                holder
                    .getEntityManager()
                    .unwrap(SessionImplementor.class)
                    .getJdbcCoordinator()
                    .getLogicalConnection();
            assertThat(connection.isPhysicallyConnected()).isTrue();
            try (var statement = connection.getPhysicalConnection().createStatement()) {
              try (var row = statement.executeQuery("select pg_is_in_recovery()")) {
                assertThat(row.next()).isTrue();
                assertThat(row.getBoolean(1)).as("Lần đọc gặp lỗi đã chọn standby").isTrue();
              }
              // Chỉ giới hạn quyền trong transaction fixture; giữ nguyên query nghiệp vụ.
              statement.execute("set local role booking_b4_denied");
            } catch (SQLException error) {
              throw new AssertionError("Không thiết lập được lỗi quyền đọc trên fixture", error);
            }
            return sql;
          };
      bookingContext(primary, standby)
          .withPropertyValues(
              "booking.sales-statistics-cache.ttl=PT30S",
              "booking.sales-statistics-cache.key-prefix=b4-failure-test:")
          .withBean(
              HibernatePropertiesCustomizer.class,
              () ->
                  properties ->
                      properties.put(JdbcSettings.STATEMENT_INSPECTOR, denyFirstOrderAggregate))
          .run(
              context -> {
                assertThat(context).hasNotFailed();
                var writerPool = context.getBean("bookingWriterDataSource", HikariDataSource.class);
                var readerPool =
                    context.getBean("bookingChartReaderDataSource", HikariDataSource.class);
                var meters = context.getBean(SimpleMeterRegistry.class);
                var tracker = new MicrometerMetricsTrackerFactory(meters);
                writerPool.setMetricsTrackerFactory(tracker);
                readerPool.setMetricsTrackerFactory(tracker);
                var chart = context.getBean(SalesStatisticsCacheService.class);
                var redis = context.getBean(RedisService.class);
                var health =
                    context.getBean("bookingChartReplicaHealth", BookingChartReplicaHealth.class);
                var date = LocalDate.of(2026, 1, 1);
                String key = "b4-failure-test:chart:v2:system:2026-01-01:2026-01-01:DAY";
                String flushed = psql(primary, "select pg_current_wal_flush_lsn()");
                Awaitility.await()
                    .atMost(Duration.ofSeconds(15))
                    .untilAsserted(
                        () ->
                            assertThat(
                                    psql(
                                        standby,
                                        "select pg_last_wal_replay_lsn() >= '%s'::pg_lsn"
                                            .formatted(flushed)))
                                .isEqualTo("t"));
                var initialWriter = chartQueryCounts(primary);
                entityManagerFactory.set(context.getBean(EntityManagerFactory.class));
                ReflectionTestUtils.invokeMethod(health, "refresh");
                assertThat(health.tryBeginRead()).isTrue();
                health.finishRead();
                var writerAcquires =
                    meters
                        .get("hikaricp.connections.acquire")
                        .tag("pool", writerPool.getPoolName())
                        .timer();
                var readerAcquires =
                    meters
                        .get("hikaricp.connections.acquire")
                        .tag("pool", readerPool.getPoolName())
                        .timer();
                long writerBefore = writerAcquires.count();
                long readerBefore = readerAcquires.count();
                assertThat(writerBefore).isPositive();
                assertThat(readerBefore).isPositive();

                assertThatThrownBy(
                        () -> chart.getSalesStatisticsChart(null, date, date, SalesGrouping.DAY))
                    .hasRootCauseInstanceOf(SQLException.class)
                    .rootCause()
                    .satisfies(
                        error ->
                            assertThat(((SQLException) error).getSQLState()).isEqualTo("42501"));
                assertThat(injectFailure).isFalse();
                assertThat(writerAcquires.count()).isEqualTo(writerBefore);
                assertThat(readerAcquires.count()).isEqualTo(readerBefore + 1);
                assertThat(chartQueryCounts(primary)).isEqualTo(initialWriter);
                verify(redis).get(key);
                verifyNoMoreInteractions(redis);

                // Không gọi probe lại: request mới phải chọn writer, không tái sử dụng mẫu cũ.
                assertThat(
                        chart
                            .getSalesStatisticsChart(null, date, date, SalesGrouping.DAY)
                            .getItems())
                    .hasSize(1);
                assertThat(readerAcquires.count()).isEqualTo(readerBefore + 1);
                assertThat(chartQueryCounts(primary))
                    .containsExactly(initialWriter.get(0) + 1, initialWriter.get(1) + 1);
                verify(redis, org.mockito.Mockito.times(2)).get(key);
                verify(redis).set(eq(key), anyString(), eq(Duration.ofSeconds(30)));
                verifyNoMoreInteractions(redis);
              });
    } finally {
      TimeZone.setDefault(originalTimeZone);
    }
  }

  @Test
  void routesToWriterWhileReplayIsPausedAndBackToStandbyAfterCatchUp() throws Exception {
    String replicationPassword = UUID.randomUUID().toString();
    var originalTimeZone = TimeZone.getDefault();
    TimeZone.setDefault(TimeZone.getTimeZone("UTC"));
    try (var network = Network.newNetwork();
        var primary = primary(network, replicationPassword, true);
        var standby = standby(network, replicationPassword, true)) {
      startReplication(primary, standby);
      initializeBookingSchema(primary, standby);
      psql(primary, "create table public.booking_b47_marker (id uuid primary key)");
      bookingContext(primary, standby)
          .run(
              context -> {
                assertThat(context).hasNotFailed();
                var chart = context.getBean(SalesStatisticsCacheService.class);
                var health =
                    context.getBean("bookingChartReplicaHealth", BookingChartReplicaHealth.class);
                String beforePauseLsn = psql(primary, "select pg_current_wal_flush_lsn()");
                Awaitility.await()
                    .atMost(Duration.ofSeconds(15))
                    .untilAsserted(
                        () ->
                            assertThat(
                                    psql(
                                        standby,
                                        "select pg_last_wal_replay_lsn() >= '%s'::pg_lsn"
                                            .formatted(beforePauseLsn)))
                                .isEqualTo("t"));
                ReflectionTestUtils.invokeMethod(health, "refresh");
                assertThat(health.tryBeginRead()).as("Standby đủ điều kiện trước pause").isTrue();
                health.finishRead();

                String marker = UUID.randomUUID().toString();
                String readMarker = "select id from public.booking_b47_marker";
                String pauseState = "select pg_get_wal_replay_pause_state()";
                var date = LocalDate.of(2026, 1, 1);
                var initialWriter = chartQueryCounts(primary);
                var initialReader = chartQueryCounts(standby);
                String markerFlushLsn;
                try {
                  psql(standby, "select pg_wal_replay_pause()");
                  Awaitility.await()
                      .atMost(Duration.ofSeconds(15))
                      .untilAsserted(
                          () -> assertThat(psql(standby, pauseState)).isEqualTo("paused"));
                  psql(
                      primary,
                      "insert into public.booking_b47_marker values ('%s')".formatted(marker));
                  markerFlushLsn = psql(primary, "select pg_current_wal_flush_lsn()");
                  assertThat(psql(primary, readMarker)).isEqualTo(marker);
                  assertThat(psql(standby, readMarker)).isEmpty();

                  // Probe phải đóng gate dù mẫu trước pause từng hợp lệ.
                  ReflectionTestUtils.invokeMethod(health, "refresh");
                  assertThat(
                          chart
                              .getSalesStatisticsChart(null, date, date, SalesGrouping.DAY)
                              .getItems())
                      .hasSize(1);
                  assertThat(chartQueryCounts(primary))
                      .containsExactly(initialWriter.get(0) + 1, initialWriter.get(1) + 1);
                  assertThat(chartQueryCounts(standby)).isEqualTo(initialReader);
                } finally {
                  psql(standby, "select pg_wal_replay_resume()");
                }
                Awaitility.await()
                    .atMost(Duration.ofSeconds(15))
                    .untilAsserted(
                        () -> {
                          assertThat(psql(standby, pauseState)).isEqualTo("not paused");
                          assertThat(psql(standby, readMarker)).isEqualTo(marker);
                          assertThat(
                                  psql(
                                      standby,
                                      "select pg_last_wal_replay_lsn() >= '%s'::pg_lsn"
                                          .formatted(markerFlushLsn)))
                              .isEqualTo("t");
                        });
                ReflectionTestUtils.invokeMethod(health, "refresh");
                assertThat(
                        chart
                            .getSalesStatisticsChart(null, date, date, SalesGrouping.DAY)
                            .getItems())
                    .hasSize(1);
                assertThat(chartQueryCounts(primary))
                    .containsExactly(initialWriter.get(0) + 1, initialWriter.get(1) + 1);
                assertThat(chartQueryCounts(standby))
                    .containsExactly(initialReader.get(0) + 1, initialReader.get(1) + 1);
              });
    } finally {
      TimeZone.setDefault(originalTimeZone);
    }
  }

  @Test
  void routesToWriterWhenReplicaSampleExpires() throws Exception {
    String replicationPassword = UUID.randomUUID().toString();
    var originalTimeZone = TimeZone.getDefault();
    TimeZone.setDefault(TimeZone.getTimeZone("UTC"));
    try (var network = Network.newNetwork();
        var primary = primary(network, replicationPassword, true);
        var standby = standby(network, replicationPassword, true)) {
      startReplication(primary, standby);
      initializeBookingSchema(primary, standby);
      bookingContext(primary, standby)
          .withPropertyValues("booking.sales-chart-replica.max-sample-age=PT3S")
          .run(
              context -> {
                assertThat(context).hasNotFailed();
                var chart = context.getBean(SalesStatisticsCacheService.class);
                var health =
                    context.getBean("bookingChartReplicaHealth", BookingChartReplicaHealth.class);
                String flushed = psql(primary, "select pg_current_wal_flush_lsn()");
                Awaitility.await()
                    .atMost(Duration.ofSeconds(15))
                    .untilAsserted(
                        () ->
                            assertThat(
                                    psql(
                                        standby,
                                        "select pg_last_wal_replay_lsn() >= '%s'::pg_lsn"
                                            .formatted(flushed)))
                                .isEqualTo("t"));
                ReflectionTestUtils.invokeMethod(health, "refresh");
                assertThat(health.tryBeginRead()).as("Mẫu mới phải cấp được permit").isTrue();
                health.finishRead();

                // Không refresh: đợi mẫu hết tuổi, luôn nhả permit nếu lần kiểm còn hợp lệ.
                Awaitility.await()
                    .atMost(Duration.ofSeconds(10))
                    .until(
                        () -> {
                          if (!health.tryBeginRead()) return true;
                          health.finishRead();
                          return false;
                        });
                String currentFlush = psql(primary, "select pg_current_wal_flush_lsn()");
                Awaitility.await()
                    .atMost(Duration.ofSeconds(15))
                    .untilAsserted(
                        () ->
                            assertThat(
                                    psql(
                                        standby,
                                        """
                        select pg_is_in_recovery()
                           and pg_get_wal_replay_pause_state() = 'not paused'
                           and pg_last_wal_replay_lsn() >= '%s'::pg_lsn
                           and exists (select 1 from pg_stat_wal_receiver where status = 'streaming')
                        """
                                            .formatted(currentFlush)))
                                .isEqualTo("t"));

                var initialWriter = chartQueryCounts(primary);
                var initialReader = chartQueryCounts(standby);
                var date = LocalDate.of(2026, 1, 1);
                assertThat(
                        chart
                            .getSalesStatisticsChart(null, date, date, SalesGrouping.DAY)
                            .getItems())
                    .hasSize(1);
                assertThat(chartQueryCounts(primary))
                    .containsExactly(initialWriter.get(0) + 1, initialWriter.get(1) + 1);
                assertThat(chartQueryCounts(standby)).isEqualTo(initialReader);
              });
    } finally {
      TimeZone.setDefault(originalTimeZone);
    }
  }

  @Test
  void routesToWriterWhenReaderEndpointIsDown() throws Exception {
    String replicationPassword = UUID.randomUUID().toString();
    var originalTimeZone = TimeZone.getDefault();
    TimeZone.setDefault(TimeZone.getTimeZone("UTC"));
    try (var network = Network.newNetwork();
        var primary = primary(network, replicationPassword, true);
        var standby = standby(network, replicationPassword, true)) {
      startReplication(primary, standby);
      initializeBookingSchema(primary, standby);
      bookingContext(primary, standby)
          .withPropertyValues(
              "booking.sales-chart-replica.max-sample-age=PT1M",
              "booking.sales-chart-replica.datasource.connection-timeout=2000",
              "booking.sales-chart-replica.datasource.validation-timeout=1000",
              "booking.sales-chart-replica.datasource.jdbc-url=jdbc:postgresql://127.0.0.1:%d/booking_db?connectTimeout=2&socketTimeout=2"
                  .formatted(standby.getMappedPort(5432)))
          .run(
              context -> {
                assertThat(context).hasNotFailed();
                var chart = context.getBean(SalesStatisticsCacheService.class);
                var health =
                    context.getBean("bookingChartReplicaHealth", BookingChartReplicaHealth.class);
                var failedProbes =
                    context
                        .getBean(SimpleMeterRegistry.class)
                        .get("booking.sales.chart.replica.probe")
                        .tag("outcome", "error")
                        .counter();
                String flushed = psql(primary, "select pg_current_wal_flush_lsn()");
                Awaitility.await()
                    .atMost(Duration.ofSeconds(15))
                    .untilAsserted(
                        () ->
                            assertThat(
                                    psql(
                                        standby,
                                        "select pg_last_wal_replay_lsn() >= '%s'::pg_lsn"
                                            .formatted(flushed)))
                                .isEqualTo("t"));
                ReflectionTestUtils.invokeMethod(health, "refresh");
                assertThat(health.tryBeginRead())
                    .as("Standby đủ điều kiện trước khi dừng")
                    .isTrue();
                health.finishRead();
                double failuresBefore = failedProbes.count();

                // Chỉ dừng standby do test tạo; primary async vẫn phục vụ.
                standby.stop();
                assertThat(standby.isRunning()).isFalse();
                assertThat(psql(primary, "select pg_is_in_recovery()")).isEqualTo("f");
                ReflectionTestUtils.invokeMethod(health, "refresh");
                assertThat(failedProbes.count()).isEqualTo(failuresBefore + 1);
                boolean readerAllowed = health.tryBeginRead();
                if (readerAllowed) health.finishRead();
                assertThat(readerAllowed).as("Probe lỗi phải đóng đường đọc standby").isFalse();

                var initialWriter = chartQueryCounts(primary);
                var date = LocalDate.of(2026, 1, 1);
                assertThat(
                        chart
                            .getSalesStatisticsChart(null, date, date, SalesGrouping.DAY)
                            .getItems())
                    .hasSize(1);
                assertThat(chartQueryCounts(primary))
                    .containsExactly(initialWriter.get(0) + 1, initialWriter.get(1) + 1);
              });
    } finally {
      TimeZone.setDefault(originalTimeZone);
    }
  }

  @Test
  void propagatesStandbyRecoveryConflictWithoutRetry() throws Exception {
    String replicationPassword = UUID.randomUUID().toString();
    var originalTimeZone = TimeZone.getDefault();
    TimeZone.setDefault(TimeZone.getTimeZone("UTC"));
    try (var network = Network.newNetwork();
        var primary = primary(network, replicationPassword, true);
        var standby = standby(network, replicationPassword, true)) {
      startReplication(primary, standby);
      initializeBookingSchema(primary, standby);
      var configureStandby =
          standby.execInContainer(
              "bash",
              "-eu",
              "-c",
              "printf '\\nmax_standby_streaming_delay = 0\\n' >> \"$PGDATA/postgresql.auto.conf\"");
      assertThat(configureStandby.getExitCode()).isZero();
      assertThat(psql(standby, "select pg_reload_conf()")).isEqualTo("t");
      Awaitility.await()
          .atMost(Duration.ofSeconds(10))
          .untilAsserted(
              () -> assertThat(psql(standby, "show max_standby_streaming_delay")).isEqualTo("0"));
      var entityManagerFactory = new AtomicReference<EntityManagerFactory>();
      var standbyPid = new AtomicReference<Integer>();
      StatementInspector holdFirstOrderAggregate =
          sql -> {
            var factory = entityManagerFactory.get();
            if (factory == null
                || standbyPid.get() != null
                || !sql.contains("group by")
                || !sql.contains("booking_schema.tbl_event_sales_snapshots")
                || !sql.contains(" from booking_schema.tbl_orders ")) {
              return sql;
            }
            var holder =
                (EntityManagerHolder) TransactionSynchronizationManager.getResource(factory);
            assertThat(holder).isNotNull();
            var connection =
                holder
                    .getEntityManager()
                    .unwrap(SessionImplementor.class)
                    .getJdbcCoordinator()
                    .getLogicalConnection();
            assertThat(connection.isPhysicallyConnected()).isTrue();
            try (var statement = connection.getPhysicalConnection().createStatement();
                var row = statement.executeQuery("select pg_is_in_recovery(), pg_backend_pid()")) {
              assertThat(row.next()).isTrue();
              assertThat(row.getBoolean(1)).as("Lần đọc gặp xung đột đã chọn standby").isTrue();
              standbyPid.set(row.getInt(2));
            } catch (SQLException error) {
              throw new AssertionError("Không lấy được backend của báo cáo trên fixture", error);
            }
            // Giữ câu aggregate ở trạng thái active với OFFSET 0; không đổi dữ liệu trả về.
            return sql + " offset (select 0 from pg_sleep(20))";
          };
      bookingContext(primary, standby)
          .withPropertyValues(
              "booking.sales-statistics-cache.ttl=PT30S",
              "booking.sales-statistics-cache.key-prefix=b4-conflict-test:",
              "booking.sales-chart-replica.max-sample-age=PT1M",
              "booking.sales-chart-replica.reader-transaction-timeout=30")
          .withBean(
              HibernatePropertiesCustomizer.class,
              () ->
                  properties ->
                      properties.put(JdbcSettings.STATEMENT_INSPECTOR, holdFirstOrderAggregate))
          .run(
              context -> {
                assertThat(context).hasNotFailed();
                var writerPool = context.getBean("bookingWriterDataSource", HikariDataSource.class);
                var readerPool =
                    context.getBean("bookingChartReaderDataSource", HikariDataSource.class);
                var meters = context.getBean(SimpleMeterRegistry.class);
                var tracker = new MicrometerMetricsTrackerFactory(meters);
                writerPool.setMetricsTrackerFactory(tracker);
                readerPool.setMetricsTrackerFactory(tracker);
                var chart = context.getBean(SalesStatisticsCacheService.class);
                var redis = context.getBean(RedisService.class);
                var health =
                    context.getBean("bookingChartReplicaHealth", BookingChartReplicaHealth.class);
                var date = LocalDate.of(2026, 1, 1);
                String key = "b4-conflict-test:chart:v2:system:2026-01-01:2026-01-01:DAY";
                String conflictCount =
                    "select confl_lock from pg_stat_database_conflicts where datname = current_database()";
                long conflictsBefore = Long.parseLong(psql(standby, conflictCount));
                String flushed = psql(primary, "select pg_current_wal_flush_lsn()");
                Awaitility.await()
                    .atMost(Duration.ofSeconds(15))
                    .untilAsserted(
                        () ->
                            assertThat(
                                    psql(
                                        standby,
                                        "select pg_last_wal_replay_lsn() >= '%s'::pg_lsn"
                                            .formatted(flushed)))
                                .isEqualTo("t"));
                var initialWriter = chartQueryCounts(primary);
                entityManagerFactory.set(context.getBean(EntityManagerFactory.class));
                ReflectionTestUtils.invokeMethod(health, "refresh");
                assertThat(health.tryBeginRead()).isTrue();
                health.finishRead();
                var writerAcquires =
                    meters
                        .get("hikaricp.connections.acquire")
                        .tag("pool", writerPool.getPoolName())
                        .timer();
                var readerAcquires =
                    meters
                        .get("hikaricp.connections.acquire")
                        .tag("pool", readerPool.getPoolName())
                        .timer();
                long writerBefore = writerAcquires.count();
                long readerBefore = readerAcquires.count();
                assertThat(writerBefore).isPositive();
                assertThat(readerBefore).isPositive();

                var request =
                    new FutureTask<>(
                        () ->
                            catchThrowable(
                                () ->
                                    chart.getSalesStatisticsChart(
                                        null, date, date, SalesGrouping.DAY)));
                var requestThread = Thread.ofVirtual().start(request);
                Throwable failure;
                try {
                  Awaitility.await()
                      .atMost(Duration.ofSeconds(10))
                      .untilAsserted(
                          () -> {
                            assertThat(standbyPid.get()).isNotNull();
                            assertThat(
                                    psql(
                                        standby,
                                        """
                    select exists (
                      select 1 from pg_stat_activity a join pg_locks l on l.pid = a.pid
                      where a.pid = %d and a.state = 'active' and a.wait_event = 'PgSleep'
                        and l.relation = 'booking_schema.tbl_orders'::regclass
                        and l.mode = 'AccessShareLock' and l.granted)
                    """
                                            .formatted(standbyPid.get())))
                                .isEqualTo("t");
                          });
                  psql(
                      primary,
                      """
                  begin;
                  set local lock_timeout = '2s';
                  lock table booking_schema.tbl_orders in access exclusive mode;
                  commit;
                  """);
                  failure = request.get(15, TimeUnit.SECONDS);
                } finally {
                  request.cancel(true);
                  assertThat(requestThread.join(Duration.ofSeconds(35)))
                      .as("Request fixture phải kết thúc trước khi đóng context")
                      .isTrue();
                }
                assertThat(failure)
                    .hasRootCauseInstanceOf(SQLException.class)
                    .rootCause()
                    .satisfies(
                        error -> {
                          assertThat(((SQLException) error).getSQLState()).isEqualTo("40001");
                          assertThat(error.getMessage()).contains("conflict with recovery");
                        });
                assertThat(writerAcquires.count()).isEqualTo(writerBefore);
                assertThat(readerAcquires.count()).isEqualTo(readerBefore + 1);
                assertThat(chartQueryCounts(primary)).isEqualTo(initialWriter);
                verify(redis).get(key);
                verifyNoMoreInteractions(redis);

                // Đóng connection rảnh của fixture để backend flush thống kê còn giữ riêng.
                readerPool.getHikariPoolMXBean().softEvictConnections();
                Awaitility.await()
                    .atMost(Duration.ofSeconds(10))
                    .untilAsserted(
                        () ->
                            assertThat(Long.parseLong(psql(standby, conflictCount)))
                                .isEqualTo(conflictsBefore + 1));

                // Không refresh: request kế tiếp chỉ mở transaction writer.
                assertThat(
                        chart
                            .getSalesStatisticsChart(null, date, date, SalesGrouping.DAY)
                            .getItems())
                    .hasSize(1);
                assertThat(readerAcquires.count()).isEqualTo(readerBefore + 1);
                assertThat(chartQueryCounts(primary))
                    .containsExactly(initialWriter.get(0) + 1, initialWriter.get(1) + 1);
                verify(redis, org.mockito.Mockito.times(2)).get(key);
                verify(redis).set(eq(key), anyString(), eq(Duration.ofSeconds(30)));
                verifyNoMoreInteractions(redis);
              });
    } finally {
      TimeZone.setDefault(originalTimeZone);
    }
  }

  @Test
  void servesConcurrentColdAdminChartsOnWriterWhenReplicaIsIneligible() throws Exception {
    String replicationPassword = UUID.randomUUID().toString();
    var originalTimeZone = TimeZone.getDefault();
    TimeZone.setDefault(TimeZone.getTimeZone("UTC"));
    try (var network = Network.newNetwork();
        var primary = primary(network, replicationPassword, true);
        var standby = standby(network, replicationPassword, true)) {
      startReplication(primary, standby);
      initializeBookingSchema(primary, standby);
      var ttl = Duration.ofSeconds(30);
      String prefix = "b4-writer-load:";
      var runner =
          bookingContext(primary, standby)
              .withPropertyValues(
                  "booking.sales-statistics-cache.ttl=PT30S",
                  "booking.sales-statistics-cache.key-prefix=" + prefix,
                  "spring.datasource.hikari.maximum-pool-size=5",
                  "spring.datasource.hikari.connection-timeout=10000",
                  "spring.datasource.hikari.data-source-properties.connectTimeout=3",
                  "spring.datasource.hikari.data-source-properties.socketTimeout=10");
      runner.run(
          first ->
              runner.run(
                  second ->
                      runner.run(
                          third -> {
                            var contexts = List.of(first, second, third);
                            var writerTimers = new ArrayList<io.micrometer.core.instrument.Timer>();
                            var readerTimers = new ArrayList<io.micrometer.core.instrument.Timer>();
                            var writerTimeouts =
                                new ArrayList<io.micrometer.core.instrument.Counter>();
                            var warmupDay = LocalDate.of(2026, 1, 1);
                            for (var context : contexts) {
                              assertThat(context).hasNotFailed();
                              var writer =
                                  context.getBean(
                                      "bookingWriterDataSource", HikariDataSource.class);
                              var reader =
                                  context.getBean(
                                      "bookingChartReaderDataSource", HikariDataSource.class);
                              assertThat(writer.getMaximumPoolSize()).isEqualTo(5);
                              assertThat(reader.getMaximumPoolSize()).isEqualTo(2);
                              var registry = context.getBean(SimpleMeterRegistry.class);
                              var tracker = new MicrometerMetricsTrackerFactory(registry);
                              writer.setMetricsTrackerFactory(tracker);
                              reader.setMetricsTrackerFactory(tracker);
                              var health =
                                  context.getBean(
                                      "bookingChartReplicaHealth", BookingChartReplicaHealth.class);
                              assertThat(health.tryBeginRead()).isFalse();
                              // Khởi tạo meter reader ngoài đợt tải mà không tạo mẫu probe hợp lệ.
                              try (var connection = reader.getConnection()) {
                                assertThat(connection.isValid(2)).isTrue();
                              }
                              context
                                  .getBean(SalesStatisticsCacheService.class)
                                  .getSalesStatisticsChart(
                                      null, warmupDay, warmupDay, SalesGrouping.DAY);
                              clearInvocations(context.getBean(RedisService.class));
                              writerTimers.add(
                                  registry
                                      .get("hikaricp.connections.acquire")
                                      .tag("pool", writer.getPoolName())
                                      .timer());
                              readerTimers.add(
                                  registry
                                      .get("hikaricp.connections.acquire")
                                      .tag("pool", reader.getPoolName())
                                      .timer());
                              writerTimeouts.add(
                                  registry
                                      .get("hikaricp.connections.timeout")
                                      .tag("pool", writer.getPoolName())
                                      .counter());
                            }
                            var writerBefore =
                                writerTimers.stream().map(timer -> timer.count()).toList();
                            var readerBefore =
                                readerTimers.stream().map(timer -> timer.count()).toList();
                            var timeoutBefore =
                                writerTimeouts.stream().map(counter -> counter.count()).toList();
                            var msBefore =
                                writerTimers.stream()
                                    .map(timer -> timer.totalTime(TimeUnit.MILLISECONDS))
                                    .toList();
                            assertThat(writerBefore)
                                .allSatisfy(count -> assertThat(count).isPositive());
                            assertThat(readerBefore).containsExactly(1L, 1L, 1L);
                            String queryTimes =
                                """
            select coalesce(sum(total_exec_time) filter (where query ilike '%sum(%.total)%' and query ilike '% from booking_schema.tbl_orders %'), 0)
                   || ',' ||
                   coalesce(sum(total_exec_time) filter (where query ilike '%sum(%.quantity)%' and query ilike '% from booking_schema.tbl_order_items %'), 0)
            from public.pg_stat_statements
            where dbid = (select oid from pg_database where datname = current_database())
              and toplevel and lower(query) like 'select %'
              and query ilike '%group by%' and query ilike '%tbl_event_sales_snapshots%'
              and query not ilike '%pg_stat_statements%'
            """;
                            var writerQueries = chartQueryCounts(primary);
                            var standbyQueries = chartQueryCounts(standby);
                            var sqlMsBefore = psql(primary, queryTimes).split(",");
                            var ready = new CountDownLatch(18);
                            var start = new CountDownLatch(1);
                            var requests = new ArrayList<Future<Long>>();
                            var samples = new ArrayList<Long>();
                            try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
                              try {
                                for (int pod = 0; pod < contexts.size(); pod++) {
                                  var chart =
                                      contexts.get(pod).getBean(SalesStatisticsCacheService.class);
                                  for (int request = 0; request < 6; request++) {
                                    var day = warmupDay.plusDays(1L + pod * 6L + request);
                                    requests.add(
                                        executor.submit(
                                            () -> {
                                              ready.countDown();
                                              assertThat(start.await(10, TimeUnit.SECONDS))
                                                  .as("Mở cổng tải fixture")
                                                  .isTrue();
                                              long startedAt = System.nanoTime();
                                              var result =
                                                  chart.getSalesStatisticsChart(
                                                      null, day, day, SalesGrouping.DAY);
                                              long elapsed = System.nanoTime() - startedAt;
                                              assertThat(result.getFrom()).isEqualTo(day);
                                              assertThat(result.getTo()).isEqualTo(day);
                                              assertThat(result.getItems())
                                                  .singleElement()
                                                  .satisfies(
                                                      item -> {
                                                        assertThat(item.getPeriodStart())
                                                            .isEqualTo(day);
                                                        assertThat(item.getPeriodEnd())
                                                            .isEqualTo(day);
                                                        assertThat(item.getSoldOrderCount())
                                                            .isZero();
                                                        assertThat(item.getSoldTicketCount())
                                                            .isZero();
                                                      });
                                              return elapsed;
                                            }));
                                  }
                                }
                                assertThat(ready.await(10, TimeUnit.SECONDS))
                                    .as("18 lượt gọi sẵn sàng")
                                    .isTrue();
                                long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(30);
                                start.countDown();
                                for (var request : requests) {
                                  samples.add(
                                      request.get(
                                          Math.max(0, deadline - System.nanoTime()),
                                          TimeUnit.NANOSECONDS));
                                }
                              } finally {
                                requests.forEach(request -> request.cancel(true));
                                start.countDown();
                              }
                            }
                            assertThat(samples).hasSize(18);
                            assertThat(chartQueryCounts(primary))
                                .containsExactly(
                                    writerQueries.get(0) + 18, writerQueries.get(1) + 18);
                            assertThat(chartQueryCounts(standby)).isEqualTo(standbyQueries);
                            var averageBorrowMs = new ArrayList<Double>();
                            for (int pod = 0; pod < contexts.size(); pod++) {
                              var writerTimer = writerTimers.get(pod);
                              assertThat(writerTimer.count()).isEqualTo(writerBefore.get(pod) + 6);
                              assertThat(writerTimeouts.get(pod).count())
                                  .isEqualTo(timeoutBefore.get(pod));
                              assertThat(readerTimers.get(pod).count())
                                  .isEqualTo(readerBefore.get(pod));
                              averageBorrowMs.add(
                                  (writerTimer.totalTime(TimeUnit.MILLISECONDS) - msBefore.get(pod))
                                      / 6);
                              var redis = contexts.get(pod).getBean(RedisService.class);
                              for (int request = 0; request < 6; request++) {
                                var day = warmupDay.plusDays(1L + pod * 6L + request);
                                String key = prefix + "chart:v2:system:" + day + ":" + day + ":DAY";
                                verify(redis).get(key);
                                verify(redis).set(eq(key), anyString(), eq(ttl));
                              }
                              verifyNoMoreInteractions(redis);
                            }
                            var sqlMsAfter = psql(primary, queryTimes).split(",");
                            double orderSqlMs =
                                Double.parseDouble(sqlMsAfter[0])
                                    - Double.parseDouble(sqlMsBefore[0]);
                            double ticketSqlMs =
                                Double.parseDouble(sqlMsAfter[1])
                                    - Double.parseDouble(sqlMsBefore[1]);
                            samples.sort(Long::compareTo);
                            // p95 nearest-rank trên 18 mẫu chỉ mô tả fixture, không phải SLA.
                            double serviceP95Ms =
                                samples.get((int) Math.ceil(0.95 * samples.size()) - 1)
                                    / 1_000_000.0;
                            LoggerFactory.getLogger(BookingChartReplicaRoutingIT.class)
                                .info(
                                    "B4.12 local: samples={} serviceP95Ms={} writerMeanAcquireMsByPool={} orderSqlTotalMs={} ticketSqlTotalMs={} callsPerAggregate=18",
                                    samples.size(),
                                    serviceP95Ms,
                                    averageBorrowMs,
                                    orderSqlMs,
                                    ticketSqlMs);
                          })));
    } finally {
      TimeZone.setDefault(originalTimeZone);
    }
  }

  @Test
  void reportTransactionsRouteOnlyWhitelistedPayloads() throws Exception {
    String replicationPassword = UUID.randomUUID().toString();
    var originalTimeZone = TimeZone.getDefault();
    TimeZone.setDefault(TimeZone.getTimeZone("UTC"));
    try (var network = Network.newNetwork();
        var primary = primary(network, replicationPassword, true);
        var standby = standby(network, replicationPassword, true)) {
      startReplication(primary, standby);
      initializeBookingSchema(primary, standby);
      String sales =
          com.flashticket.booking_service.support.BookingFixtures.jsonMapper()
              .writeValueAsString(com.flashticket.booking_service.support.BookingFixtures.sales());
      psql(
          primary,
          """
          insert into booking_schema.tbl_event_sales_snapshots
          (event_id, organizer_subject, sales_mode, event_status, sale_starts_at, sale_ends_at,
           purchase_limit, configuration_snapshot, source_config_version, received_at)
          values ('00000000-0000-0000-0000-000000000001','organizer','QUANTITY','PUBLISHED',
                  '2001-01-01',now()+interval '1 day',5,'%s'::jsonb,1,now()),
                 ('00000000-0000-0000-0000-000000000007','organizer','QUANTITY','PUBLISHED',
                  '2001-01-01',now()+interval '1 day',5,
                  jsonb_set('%s'::jsonb,'{eventId}','"00000000-0000-0000-0000-000000000007"'),1,now())
          """
              .formatted(sales.replace("'", "''"), sales.replace("'", "''")));
      var factory = new AtomicReference<EntityManagerFactory>();
      var observations = new ArrayList<List<String>>();
      StatementInspector inspect =
          sql -> {
            if (factory.get() == null || !sql.toLowerCase().startsWith("select")) return sql;
            var holder =
                (EntityManagerHolder) TransactionSynchronizationManager.getResource(factory.get());
            if (holder == null) return sql;
            var connection =
                holder
                    .getEntityManager()
                    .unwrap(SessionImplementor.class)
                    .getJdbcCoordinator()
                    .getLogicalConnection()
                    .getPhysicalConnection();
            try (var statement = connection.createStatement();
                var row =
                    statement.executeQuery(
                        """
                select pg_is_in_recovery()::text, pg_backend_pid()::text,
                       current_setting('transaction_isolation'),
                       current_setting('transaction_read_only'), pg_current_snapshot()::text
                """)) {
              assertThat(row.next()).isTrue();
              observations.add(
                  List.of(
                      row.getString(1),
                      row.getString(2),
                      row.getString(3),
                      row.getString(4),
                      row.getString(5)));
            } catch (SQLException error) {
              throw new AssertionError("Không quan sát được connection report", error);
            }
            return sql;
          };
      bookingContext(primary, standby)
          .withUserConfiguration(
              com.flashticket.booking_service.order.service.SalesSummaryCacheService.class)
          .withPropertyValues(
              "booking.sales-organizer-report-replica.enabled=true",
              "booking.sales-event-report-replica.enabled=true",
              "booking.sales-summary-cache.ttl=PT0S")
          .withBean(
              HibernatePropertiesCustomizer.class,
              () -> properties -> properties.put(JdbcSettings.STATEMENT_INSPECTOR, inspect))
          .run(
              context -> {
                assertThat(context)
                    .hasNotFailed()
                    .hasBean("bookingSalesReportReadTransactionTemplate");
                var reports = context.getBean(SalesStatisticsCacheService.class);
                var legacy =
                    context.getBean(
                        com.flashticket.booking_service.order.service.SalesSummaryCacheService
                            .class);
                var health =
                    context.getBean("bookingChartReplicaHealth", BookingChartReplicaHealth.class);
                String flushed = psql(primary, "select pg_current_wal_flush_lsn()");
                Awaitility.await()
                    .atMost(Duration.ofSeconds(15))
                    .untilAsserted(
                        () ->
                            assertThat(
                                    psql(
                                        standby,
                                        "select pg_last_wal_replay_lsn() >= '%s'::pg_lsn"
                                            .formatted(flushed)))
                                .isEqualTo("t"));
                factory.set(context.getBean(EntityManagerFactory.class));
                var date = LocalDate.of(2026, 1, 1);
                var event = UUID.fromString("00000000-0000-0000-0000-000000000001");
                for (String report : List.of("summary", "chart", "ranking", "event", "legacy")) {
                  ReflectionTestUtils.invokeMethod(health, "refresh");
                  observations.clear();
                  Object result =
                      switch (report) {
                        case "summary" ->
                            reports.getSalesStatisticsSummary("organizer", date, date);
                        case "chart" ->
                            reports.getSalesStatisticsChart(
                                "organizer", date, date, SalesGrouping.DAY);
                        case "ranking" ->
                            reports.getEventSalesStatistics(
                                "organizer",
                                date,
                                date,
                                0,
                                1,
                                "soldRevenue",
                                org.springframework.data.domain.Sort.Direction.DESC);
                        case "event" ->
                            reports.getEventSalesStatistics("organizer", event, date, date);
                        default -> legacy.getSalesSummary("organizer", event, date, date);
                      };
                  assertThat(result).isNotNull();
                  assertThat(observations).isNotEmpty();
                  var first = observations.getFirst();
                  assertThat(first.get(0)).as(report + " đọc standby").isEqualTo("true");
                  assertThat(first.get(2)).isEqualTo("repeatable read");
                  assertThat(first.get(3)).isEqualTo("on");
                  for (var observed : observations) assertThat(observed).isEqualTo(first);
                }
                observations.clear();
                var orders =
                    context.getBean(
                        com.flashticket.booking_service.order.service.OrderService.class);
                assertThat(orders.getSalesEventOrganizerSubject(event, "organizer", false))
                    .isEqualTo("organizer");
                reports.getSalesStatisticsSummary(null, date, date);
                reports.getEventSalesStatistics(
                    null,
                    date,
                    date,
                    0,
                    1,
                    "soldRevenue",
                    org.springframework.data.domain.Sort.Direction.DESC);
                assertThat(observations).isNotEmpty();
                for (var observed : observations) assertThat(observed.get(0)).isEqualTo("false");
              });
      new ApplicationContextRunner()
          .withConfiguration(
              AutoConfigurations.of(
                  DataSourceAutoConfiguration.class, TransactionAutoConfiguration.class))
          .withUserConfiguration(BookingDataSourceConfig.class)
          .withPropertyValues(
              "spring.datasource.url=jdbc:postgresql://127.0.0.1:%d/booking_db"
                  .formatted(primary.getMappedPort(5432)),
              "spring.datasource.username=postgres",
              "spring.datasource.password=" + primary.getEnvMap().get("POSTGRES_PASSWORD"),
              "booking.sales-chart-replica.enabled=false")
          .run(
              context -> {
                assertThat(context)
                    .hasNotFailed()
                    .doesNotHaveBean("bookingSalesReportReadTransactionTemplate")
                    .doesNotHaveBean("bookingChartReaderDataSource")
                    .doesNotHaveBean("bookingChartReplicaHealth");
                assertThat(
                        new JdbcTemplate(context.getBean(DataSource.class))
                            .queryForObject("select pg_is_in_recovery()", Boolean.class))
                    .isFalse();
              });
      bookingContext(primary, standby)
          .withPropertyValues("booking.sales-chart-replica.reader-transaction-timeout=0")
          .run(context -> assertThat(context).hasFailed());
    } finally {
      TimeZone.setDefault(originalTimeZone);
    }
  }

  @Test
  void reportFailuresReleaseSharedPermitWithoutWriterRetry() throws Exception {
    String replicationPassword = UUID.randomUUID().toString();
    var originalTimeZone = TimeZone.getDefault();
    TimeZone.setDefault(TimeZone.getTimeZone("UTC"));
    try (var network = Network.newNetwork();
        var primary = primary(network, replicationPassword, true);
        var standby = standby(network, replicationPassword, true)) {
      startReplication(primary, standby);
      initializeBookingSchema(primary, standby);
      psql(
          primary,
          "create role booking_report_denied nologin; grant usage on schema booking_schema to booking_report_denied");
      var factory = new AtomicReference<EntityManagerFactory>();
      var entered = new CountDownLatch(1);
      var release = new CountDownLatch(1);
      var injectFailure = new java.util.concurrent.atomic.AtomicBoolean(true);
      var readerAttempts = new java.util.concurrent.atomic.AtomicInteger();
      var writerAttempts = new java.util.concurrent.atomic.AtomicInteger();
      StatementInspector inspect =
          sql -> {
            // Query vé cũng JOIN orders: chỉ đếm aggregate gốc để phát hiện retry writer.
            if (factory.get() == null
                || !sql.contains("booking_schema.tbl_orders")
                || sql.contains("booking_schema.tbl_order_items")) return sql;
            var holder =
                (EntityManagerHolder) TransactionSynchronizationManager.getResource(factory.get());
            var connection =
                holder
                    .getEntityManager()
                    .unwrap(SessionImplementor.class)
                    .getJdbcCoordinator()
                    .getLogicalConnection()
                    .getPhysicalConnection();
            try (var statement = connection.createStatement();
                var row = statement.executeQuery("select pg_is_in_recovery()")) {
              assertThat(row.next()).isTrue();
              if (row.getBoolean(1)) {
                readerAttempts.incrementAndGet();
                if (injectFailure.compareAndSet(true, false)) {
                  entered.countDown();
                  assertThat(release.await(15, TimeUnit.SECONDS)).isTrue();
                  statement.execute("set local role booking_report_denied");
                }
              } else writerAttempts.incrementAndGet();
            } catch (SQLException | InterruptedException error) {
              if (error instanceof InterruptedException) Thread.currentThread().interrupt();
              throw new AssertionError("Không inject được lỗi report fixture", error);
            }
            return sql;
          };
      bookingContext(primary, standby)
          .withPropertyValues(
              "booking.sales-organizer-report-replica.enabled=true",
              "booking.sales-statistics-cache.ttl=PT30S",
              "booking.sales-statistics-cache.key-prefix=ha-report:")
          .withBean(
              HibernatePropertiesCustomizer.class,
              () -> properties -> properties.put(JdbcSettings.STATEMENT_INSPECTOR, inspect))
          .run(
              context -> {
                assertThat(context)
                    .hasNotFailed()
                    .hasBean("bookingSalesReportReadTransactionTemplate");
                String flushed = psql(primary, "select pg_current_wal_flush_lsn()");
                Awaitility.await()
                    .atMost(Duration.ofSeconds(15))
                    .untilAsserted(
                        () ->
                            assertThat(
                                    psql(
                                        standby,
                                        "select pg_last_wal_replay_lsn() >= '%s'::pg_lsn"
                                            .formatted(flushed)))
                                .isEqualTo("t"));
                var reports = context.getBean(SalesStatisticsCacheService.class);
                var health =
                    context.getBean("bookingChartReplicaHealth", BookingChartReplicaHealth.class);
                factory.set(context.getBean(EntityManagerFactory.class));
                ReflectionTestUtils.invokeMethod(health, "refresh");
                var day = LocalDate.of(2026, 1, 1);
                var request =
                    new FutureTask<Throwable>(
                        () ->
                            catchThrowable(
                                () -> reports.getSalesStatisticsSummary("organizer", day, day)));
                var thread = Thread.ofVirtual().start(request);
                try {
                  assertThat(entered.await(15, TimeUnit.SECONDS))
                      .as("report giữ permit reader thật")
                      .isTrue();
                  reports.getSalesStatisticsChart(null, day, day, SalesGrouping.DAY);
                  assertThat(writerAttempts.get()).isEqualTo(1);
                  release.countDown();
                  assertThat(request.get(20, TimeUnit.SECONDS))
                      .hasRootCauseInstanceOf(SQLException.class);
                  assertThat(readerAttempts.get()).isEqualTo(1);
                  assertThat(writerAttempts.get())
                      .as("Không retry report lỗi trên writer")
                      .isEqualTo(1);
                  var redis = context.getBean(RedisService.class);
                  String scope =
                      java.util.Base64.getUrlEncoder()
                          .withoutPadding()
                          .encodeToString(
                              "organizer".getBytes(java.nio.charset.StandardCharsets.UTF_8));
                  verify(redis, org.mockito.Mockito.never())
                      .set(
                          eq("ha-report:summary:organizer:" + scope + ":" + day + ":" + day),
                          anyString(),
                          org.mockito.ArgumentMatchers.any(Duration.class));
                  assertThat(health.tryBeginRead()).as("Lỗi đã invalidate mẫu").isFalse();
                  ReflectionTestUtils.invokeMethod(health, "refresh");
                  assertThat(health.tryBeginRead()).as("Permit đã được nhả").isTrue();
                  health.finishRead();
                  assertThat(
                          reports
                              .getSalesStatisticsSummary("organizer", day, day)
                              .getSoldOrderCount())
                      .isZero();
                  assertThat(readerAttempts.get()).isGreaterThan(1);
                } finally {
                  release.countDown();
                  request.cancel(true);
                  assertThat(thread.join(Duration.ofSeconds(25))).isTrue();
                }
              });
    } finally {
      TimeZone.setDefault(originalTimeZone);
    }
  }

  @Test
  void eventReportsPreserveOwnershipAndDistinctDefaultDateRanges() throws Exception {
    String replicationPassword = UUID.randomUUID().toString();
    var originalTimeZone = TimeZone.getDefault();
    TimeZone.setDefault(TimeZone.getTimeZone("UTC"));
    try (var network = Network.newNetwork();
        var primary = primary(network, replicationPassword, true);
        var standby = standby(network, replicationPassword, true)) {
      startReplication(primary, standby);
      initializeBookingSchema(primary, standby);
      String sales =
          com.flashticket.booking_service.support.BookingFixtures.jsonMapper()
              .writeValueAsString(com.flashticket.booking_service.support.BookingFixtures.sales());
      psql(
          primary,
          """
          insert into booking_schema.tbl_event_sales_snapshots
          (event_id,organizer_subject,sales_mode,event_status,sale_starts_at,sale_ends_at,
           purchase_limit,configuration_snapshot,source_config_version,received_at)
          values ('00000000-0000-0000-0000-000000000001','organizer','QUANTITY','PUBLISHED',
                  '2001-01-01',now()+interval '1 day',5,'%s'::jsonb,1,now())
          """
              .formatted(sales.replace("'", "''")));
      bookingContext(primary, standby)
          .withUserConfiguration(
              com.flashticket.booking_service.order.service.SalesSummaryCacheService.class)
          .withPropertyValues(
              "booking.sales-event-report-replica.enabled=true",
              "booking.sales-summary-cache.ttl=PT0S")
          .run(
              context -> {
                assertThat(context)
                    .hasNotFailed()
                    .hasBean("bookingSalesReportReadTransactionTemplate");
                String flushed = psql(primary, "select pg_current_wal_flush_lsn()");
                Awaitility.await()
                    .atMost(Duration.ofSeconds(15))
                    .untilAsserted(
                        () ->
                            assertThat(
                                    psql(
                                        standby,
                                        "select pg_last_wal_replay_lsn() >= '%s'::pg_lsn"
                                            .formatted(flushed)))
                                .isEqualTo("t"));
                var health =
                    context.getBean("bookingChartReplicaHealth", BookingChartReplicaHealth.class);
                var reports = context.getBean(SalesStatisticsCacheService.class);
                var legacy =
                    context.getBean(
                        com.flashticket.booking_service.order.service.SalesSummaryCacheService
                            .class);
                var event = UUID.fromString("00000000-0000-0000-0000-000000000001");
                ReflectionTestUtils.invokeMethod(health, "refresh");
                var modern = reports.getEventSalesStatistics("organizer", event, null, null);
                assertThat(modern.getFrom()).isEqualTo(modern.getTo().withDayOfMonth(1));
                ReflectionTestUtils.invokeMethod(health, "refresh");
                assertThat(legacy.getSalesSummary("organizer", event, null, null).getFrom())
                    .isEqualTo(LocalDate.of(2001, 1, 1));
                ReflectionTestUtils.invokeMethod(health, "refresh");
                assertThatThrownBy(() -> legacy.getSalesSummary("other", event, null, null))
                    .isInstanceOf(
                        com.flashticket.booking_service.exception.BusinessException.class);
                assertThat(health.tryBeginRead())
                    .as("Lỗi ownership không cache, mẫu bị invalidate")
                    .isFalse();
                ReflectionTestUtils.invokeMethod(health, "refresh");
                assertThatThrownBy(
                        () ->
                            reports.getEventSalesStatistics(
                                "organizer", UUID.randomUUID(), null, null))
                    .isInstanceOf(
                        com.flashticket.booking_service.exception.BusinessException.class);
                assertThat(health.tryBeginRead()).isFalse();
              });
    } finally {
      TimeZone.setDefault(originalTimeZone);
    }
  }

  @TestConfiguration(proxyBeanMethods = false)
  @EntityScan(basePackageClasses = Order.class)
  @EnableJpaRepositories(basePackages = "com.flashticket.booking_service")
  static class BookingJpaFixture {}

  private static void initializeBookingSchema(
      GenericContainer<?> primary, GenericContainer<?> standby)
      throws IOException, InterruptedException {
    psql(
        primary,
        """
        create role booking_owner nologin;
        create schema booking_schema authorization booking_owner;
        create extension pg_stat_statements;
        """);
    Path repository = Path.of(System.getProperty("basedir", ".")).resolve("../..").normalize();
    primary.copyFileToContainer(
        MountableFile.forHostPath(
            repository
                .resolve("docs/architecture/data/postgres/20-booking.sql")
                .toAbsolutePath()
                .toString()),
        "/tmp/20-booking.sql");
    primary.copyFileToContainer(
        MountableFile.forHostPath(
            repository.resolve("services/99-table-prefix.sql").toAbsolutePath().toString()),
        "/tmp/99-table-prefix.sql");
    var result =
        primary.execInContainer(
            "psql",
            "-X",
            "-w",
            "-v",
            "ON_ERROR_STOP=1",
            "-U",
            "postgres",
            "-d",
            "booking_db",
            "-f",
            "/tmp/20-booking.sql",
            "-f",
            "/tmp/99-table-prefix.sql");
    assertThat(result.getExitCode()).withFailMessage(result.getStderr()).isZero();
    Awaitility.await()
        .atMost(Duration.ofSeconds(15))
        .untilAsserted(
            () ->
                assertThat(
                        psql(
                            standby, "select to_regclass('booking_schema.tbl_orders') is not null"))
                    .isEqualTo("t"));
  }

  private static ApplicationContextRunner bookingContext(
      GenericContainer<?> primary, GenericContainer<?> standby) {
    String password = primary.getEnvMap().get("POSTGRES_PASSWORD");
    return new ApplicationContextRunner()
        .withInitializer(
            context ->
                context
                    .getBeanFactory()
                    .setConversionService(ApplicationConversionService.getSharedInstance()))
        .withConfiguration(
            AutoConfigurations.of(
                DataSourceAutoConfiguration.class,
                HibernateJpaAutoConfiguration.class,
                TransactionAutoConfiguration.class,
                JacksonAutoConfiguration.class,
                ValidationAutoConfiguration.class))
        .withUserConfiguration(
            BookingJpaFixture.class,
            BookingDataSourceConfig.class,
            BookingJsonConfig.class,
            OrderServiceImpl.class,
            OrderViewAssembler.class,
            OrderMapperImpl.class,
            OutboxMessageMapperImpl.class,
            MappingValueConverter.class,
            SalesStatisticsCacheService.class)
        .withBean(SimpleMeterRegistry.class)
        .withBean(RedisService.class, () -> mock(RedisService.class))
        .withPropertyValues(
            "spring.datasource.url=jdbc:postgresql://127.0.0.1:%d/booking_db"
                .formatted(primary.getMappedPort(5432)),
            "spring.datasource.username=postgres",
            "spring.datasource.password=" + password,
            "spring.datasource.hikari.connection-init-sql=SET ROLE booking_owner",
            "spring.datasource.hikari.minimum-idle=0",
            "spring.jpa.hibernate.ddl-auto=none",
            "spring.jpa.properties.hibernate.default_schema=booking_schema",
            "spring.jpa.properties.hibernate.type.json_format_mapper=jackson3",
            "booking.reservation.hold-duration=PT15M",
            "booking.reservation.lock-timeout=5s",
            "booking.sales-statistics-cache.ttl=PT0S",
            "booking.sales-chart-replica.enabled=true",
            "booking.sales-chart-replica.probe-interval=PT1H",
            "booking.sales-chart-replica.probe-query-timeout=2",
            "booking.sales-chart-replica.max-sample-age=PT10S",
            "booking.sales-chart-replica.reader-transaction-timeout=5",
            "booking.sales-chart-replica.datasource.jdbc-url=jdbc:postgresql://127.0.0.1:%d/booking_db"
                .formatted(standby.getMappedPort(5432)),
            "booking.sales-chart-replica.datasource.username=postgres",
            "booking.sales-chart-replica.datasource.password=" + password,
            "booking.sales-chart-replica.datasource.read-only=true",
            "booking.sales-chart-replica.datasource.minimum-idle=0",
            "booking.sales-chart-replica.datasource.maximum-pool-size=2");
  }

  private static List<Long> chartQueryCounts(GenericContainer<?> postgres)
      throws IOException, InterruptedException {
    // Chỉ đếm aggregate gốc trên từng server, không dùng counter quyết định của app.
    String counts =
        psql(
            postgres,
            """
        select coalesce(sum(calls) filter (where query ilike '%sum(%.total)%' and query ilike '% from booking_schema.tbl_orders %'), 0)
               || ',' ||
               coalesce(sum(calls) filter (where query ilike '%sum(%.quantity)%' and query ilike '% from booking_schema.tbl_order_items %'), 0)
        from public.pg_stat_statements
        where dbid = (select oid from pg_database where datname = current_database())
          and toplevel and lower(query) like 'select %'
          and query ilike '%group by%' and query ilike '%tbl_event_sales_snapshots%'
          and query not ilike '%pg_stat_statements%'
        """);
    return Arrays.stream(counts.split(",")).map(Long::parseLong).toList();
  }

  private static GenericContainer<?> primary(
      Network network, String replicationPassword, boolean exposeJdbc) {
    var primary =
        new GenericContainer<>("postgres:16.15")
            .withNetwork(network)
            .withNetworkAliases("booking-b4-primary")
            .withEnv("POSTGRES_DB", "booking_db")
            .withEnv("POSTGRES_PASSWORD", UUID.randomUUID().toString())
            .withEnv("POSTGRES_INITDB_ARGS", "--auth-local=trust --auth-host=scram-sha-256")
            .withEnv("BOOKING_TEST_REPLICATION_PASSWORD", replicationPassword)
            .withCommand("postgres", "-c", "wal_level=replica", "-c", "wal_keep_size=64MB")
            .waitingFor(
                Wait.forLogMessage(".*database system is ready to accept connections.*\\n", 2));
    if (exposeJdbc) {
      primary
          .withCommand(
              "postgres",
              "-c",
              "wal_level=replica",
              "-c",
              "wal_keep_size=64MB",
              "-c",
              "shared_preload_libraries=pg_stat_statements")
          .withExposedPorts(5432)
          .withCreateContainerCmdModifier(
              cmd ->
                  cmd.getHostConfig()
                      .withPortBindings(
                          new PortBinding(
                              Ports.Binding.bindIp("127.0.0.1"), ExposedPort.tcp(5432))));
    }
    return primary;
  }

  private static GenericContainer<?> standby(
      Network network, String replicationPassword, boolean exposeJdbc) {
    var standby =
        new GenericContainer<>("postgres:16.15")
            .withNetwork(network)
            .withEnv("PGDATA", "/var/lib/postgresql/data/standby")
            .withEnv("BOOKING_TEST_REPLICATION_PASSWORD", replicationPassword)
            .withCommand(
                "bash",
                "-euc",
                """
                    install -d -m 700 -o postgres -g postgres "$PGDATA"
                    passfile=/var/lib/postgresql/data/standby.pgpass
                    umask 077
                    printf 'booking-b4-primary:5432:*:booking_b4_replicator:%s\\n' \
                      "$BOOKING_TEST_REPLICATION_PASSWORD" > "$passfile"
                    chown postgres:postgres "$passfile"
                    unset BOOKING_TEST_REPLICATION_PASSWORD
                    gosu postgres pg_basebackup \
                      --dbname="host=booking-b4-primary port=5432 user=booking_b4_replicator passfile=$passfile connect_timeout=5" \
                      --pgdata="$PGDATA" -X stream -R --no-password
                    if [ "$1" = true ]; then
                      set -- -c shared_preload_libraries=pg_stat_statements
                    else
                      set --
                    fi
                    exec docker-entrypoint.sh postgres -c hot_standby=on "$@"
                    """,
                "standby-bootstrap",
                Boolean.toString(exposeJdbc))
            .waitingFor(
                Wait.forLogMessage(
                    ".*database system is ready to accept read-only connections.*\\n", 1));
    if (exposeJdbc) {
      standby
          .withExposedPorts(5432)
          .withCreateContainerCmdModifier(
              cmd ->
                  cmd.getHostConfig()
                      .withPortBindings(
                          new PortBinding(
                              Ports.Binding.bindIp("127.0.0.1"), ExposedPort.tcp(5432))));
    }
    return standby;
  }

  private static void startReplication(GenericContainer<?> primary, GenericContainer<?> standby)
      throws IOException, InterruptedException {
    primary.start();
    assertThat(psql(primary, "select pg_is_in_recovery()")).isEqualTo("f");
    var replicationSetup =
        primary.execInContainer(
            "bash",
            "-euc",
            """
              psql -X -w -q -v ON_ERROR_STOP=1 -U postgres -d booking_db <<'SQL'
              \\getenv replication_password BOOKING_TEST_REPLICATION_PASSWORD
              CREATE ROLE booking_b4_replicator WITH LOGIN REPLICATION PASSWORD :'replication_password';
              SQL
              printf '\\nhost replication booking_b4_replicator samenet scram-sha-256\\n' >> "$PGDATA/pg_hba.conf"
              psql -X -w -q -v ON_ERROR_STOP=1 -U postgres -d booking_db -c 'SELECT pg_reload_conf()'
              """);
    assertThat(replicationSetup.getExitCode()).as("Cấu hình replication của fixture").isZero();
    standby.start();
    assertThat(psql(standby, "select pg_is_in_recovery()")).isEqualTo("t");
    Awaitility.await()
        .atMost(Duration.ofSeconds(15))
        .untilAsserted(
            () ->
                assertThat(
                        psql(
                            standby,
                            "select status from pg_stat_wal_receiver where sender_host = 'booking-b4-primary'"))
                    .isEqualTo("streaming"));
  }

  private static String psql(GenericContainer<?> postgres, String sql)
      throws IOException, InterruptedException {
    var result =
        postgres.execInContainer(
            "psql",
            "-X",
            "-w",
            "-qAt",
            "-v",
            "ON_ERROR_STOP=1",
            "-U",
            "postgres",
            "-d",
            "booking_db",
            "-c",
            sql);
    assertThat(result.getExitCode()).withFailMessage(result.getStderr()).isZero();
    return result.getStdout().strip();
  }
}
