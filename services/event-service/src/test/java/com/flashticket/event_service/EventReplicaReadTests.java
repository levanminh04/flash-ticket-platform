package com.flashticket.event_service;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.flashticket.event_service.api.EventCommands;
import com.flashticket.event_service.application.CancellationTransactions;
import com.flashticket.event_service.application.EventService;
import com.flashticket.event_service.integration.EventImageStorage;
import com.flashticket.event_service.integration.HttpEventSummaries;
import com.flashticket.event_service.messaging.OutboxStore;
import com.flashticket.event_service.persistence.CancellationRepository;
import com.flashticket.event_service.persistence.EventRepository;
import java.nio.file.Path;
import java.sql.DriverManager;
import java.util.*;
import java.util.concurrent.Callable;
import org.junit.jupiter.api.*;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.Network;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.MountableFile;

@SpringBootTest(
    properties = {
      "spring.autoconfigure.exclude=org.springframework.boot.amqp.autoconfigure.RabbitAutoConfiguration",
      "spring.jpa.hibernate.ddl-auto=none",
      "event.outbox.enabled=false"
    })
@AutoConfigureMockMvc
@Testcontainers
class EventReplicaReadTests {
  static Network network;
  static PostgreSQLContainer primary;
  static PostgreSQLContainer standby;
  static TimeZone originalTimeZone;
  static final UUID VENUE = UUID.fromString("00000000-0000-4000-8000-000000000001");
  static final UUID CATEGORY = UUID.fromString("00000000-0000-4000-8000-000000000002");
  @Autowired EventService service;
  @Autowired CancellationTransactions cancellations;
  @Autowired OutboxStore outbox;
  @Autowired JdbcTemplate jdbc;
  @Autowired MockMvc mvc;

  @Autowired
  @org.springframework.beans.factory.annotation.Qualifier("eventReplicaHealth")
  com.flashticket.event_service.config.EventReplicaHealth health;

  @Autowired com.flashticket.event_service.application.EventImageTransactions images;
  @MockitoBean org.springframework.security.oauth2.jwt.JwtDecoder jwtDecoder;
  @MockitoBean RabbitTemplate rabbit;
  @MockitoBean EventImageStorage imageStorage;
  @MockitoBean HttpEventSummaries summaries;
  @MockitoSpyBean EventRepository events;
  @MockitoSpyBean CancellationRepository requests;

  @DynamicPropertySource
  static void properties(DynamicPropertyRegistry registry) throws Exception {
    startPostgresPair();
    registry.add("spring.datasource.url", primary::getJdbcUrl);
    registry.add("spring.datasource.username", () -> "event_admin");
    registry.add("spring.datasource.password", () -> "isolated-event");
    registry.add("spring.datasource.hikari.connection-init-sql", () -> "SET ROLE event_owner");
    registry.add("event.read-replica.enabled", () -> "true");
    registry.add("event.read-replica.datasource.jdbc-url", standby::getJdbcUrl);
    registry.add("event.read-replica.datasource.username", () -> "event_reader");
    registry.add("event.read-replica.datasource.password", () -> "isolated-reader");
    registry.add("event.read-replica.datasource.read-only", () -> "true");
    registry.add("event.read-replica.datasource.maximum-pool-size", () -> "2");
    registry.add("event.read-replica.datasource.minimum-idle", () -> "0");
    registry.add("event.read-replica.datasource.connection-timeout", () -> "5000");
    registry.add("event.read-replica.probe-interval", () -> "1h");
    registry.add("event.read-replica.max-sample-age", () -> "5s");
    registry.add("event.read-replica.probe-query-timeout", () -> "2");
    registry.add("event.read-replica.transaction-timeout", () -> "5");
  }

  private static void startPostgresPair() throws Exception {
    if (primary != null) return;
    originalTimeZone = TimeZone.getDefault();
    TimeZone.setDefault(TimeZone.getTimeZone("UTC"));
    network = Network.newNetwork();
    primary =
        new PostgreSQLContainer("postgres:16.15")
            .withNetwork(network)
            .withNetworkAliases("event-primary")
            .withDatabaseName("event_db")
            .withUsername("bootstrap")
            .withPassword("isolated-bootstrap")
            .withCommand("postgres", "-c", "wal_level=replica", "-c", "max_wal_senders=5");
    try {
      primary.start();
      try (var connection =
              DriverManager.getConnection(primary.getJdbcUrl(), "bootstrap", "isolated-bootstrap");
          var statement = connection.createStatement()) {
        statement.execute("CREATE ROLE event_owner NOLOGIN");
        statement.execute("CREATE ROLE event_admin LOGIN NOINHERIT PASSWORD 'isolated-event'");
        statement.execute("GRANT event_owner TO event_admin");
        statement.execute("CREATE SCHEMA event_schema AUTHORIZATION event_owner");
        statement.execute("CREATE ROLE replicator REPLICATION LOGIN");
        statement.execute("CREATE ROLE event_reader LOGIN PASSWORD 'isolated-reader'");
      }
      var root = Path.of(System.getProperty("basedir", ".")).resolve("../..").normalize();
      primary.copyFileToContainer(
          MountableFile.forHostPath(root.resolve("docs/architecture/data/postgres/10-event.sql")),
          "/tmp/event.sql");
      primary.copyFileToContainer(
          MountableFile.forHostPath(root.resolve("services/99-table-prefix.sql")),
          "/tmp/prefix.sql");
      for (String file : List.of("/tmp/event.sql", "/tmp/prefix.sql")) {
        var result =
            primary.execInContainer(
                "psql", "-U", "bootstrap", "-d", "event_db", "-v", "ON_ERROR_STOP=1", "-f", file);
        assertThat(result.getExitCode()).as("canonical schema").isZero();
      }
      try (var connection =
              DriverManager.getConnection(primary.getJdbcUrl(), "bootstrap", "isolated-bootstrap");
          var statement = connection.createStatement()) {
        statement.execute("GRANT CONNECT ON DATABASE event_db TO event_reader");
        statement.execute("GRANT USAGE ON SCHEMA event_schema TO event_reader");
        statement.execute("GRANT SELECT ON ALL TABLES IN SCHEMA event_schema TO event_reader");
      }
      assertThat(
              primary
                  .execInContainer(
                      "bash",
                      "-ec",
                      "printf '\nhost replication replicator all trust\n' >> \"$PGDATA/pg_hba.conf\"; gosu postgres pg_ctl reload")
                  .getExitCode())
          .isZero();
      standby =
          new PostgreSQLContainer("postgres:16.15")
              .withNetwork(network)
              .withDatabaseName("event_db")
              .withUsername("bootstrap")
              .withPassword("isolated-bootstrap")
              .withCommand(
                  "bash",
                  "-ec",
                  "install -d -o postgres -g postgres -m 700 /var/lib/postgresql/data; gosu postgres pg_basebackup -h event-primary -U replicator -D /var/lib/postgresql/data -R -X stream; exec gosu postgres postgres -D /var/lib/postgresql/data -c hot_standby=on -c max_standby_streaming_delay=1s")
              .waitingFor(
                  Wait.forLogMessage(
                      ".*database system is ready to accept read-only connections.*\\n", 1));
      standby.start();
    } catch (Exception | AssertionError failure) {
      stopPostgresPair();
      throw failure;
    }
  }

  @AfterAll
  static void stopPostgresPair() {
    if (standby != null) standby.stop();
    if (primary != null) primary.stop();
    if (network != null) network.close();
    if (originalTimeZone != null) TimeZone.setDefault(originalTimeZone);
  }

  @BeforeEach
  void resetFixture() throws Exception {
    reset(events, requests);
    try (var connection =
            DriverManager.getConnection(standby.getJdbcUrl(), "bootstrap", "isolated-bootstrap");
        var statement = connection.createStatement()) {
      statement.execute("SELECT pg_wal_replay_resume()");
    }
    try (var connection =
            DriverManager.getConnection(primary.getJdbcUrl(), "bootstrap", "isolated-bootstrap");
        var statement = connection.createStatement()) {
      statement.execute(
          "TRUNCATE event_schema.tbl_events,event_schema.tbl_categories,event_schema.tbl_venues,event_schema.tbl_outbox_messages CASCADE");
      statement.execute(
          "INSERT INTO event_schema.tbl_venues(id,name,address) VALUES ('"
              + VENUE
              + "','Venue','Address')");
      statement.execute(
          "INSERT INTO event_schema.tbl_categories(id,name) VALUES ('" + CATEGORY + "','Music')");
    }
    awaitReplay();
  }

  private void awaitReplay() throws Exception {
    String lsn;
    try (var c =
            DriverManager.getConnection(primary.getJdbcUrl(), "bootstrap", "isolated-bootstrap");
        var s = c.createStatement();
        var row = s.executeQuery("SELECT pg_current_wal_flush_lsn()::text")) {
      row.next();
      lsn = row.getString(1);
    }
    long deadline = System.nanoTime() + java.time.Duration.ofSeconds(15).toNanos();
    try (var c =
            DriverManager.getConnection(standby.getJdbcUrl(), "bootstrap", "isolated-bootstrap");
        var s = c.createStatement()) {
      while (true) {
        try (var row = s.executeQuery("SELECT pg_last_wal_replay_lsn() >= '" + lsn + "'::pg_lsn")) {
          row.next();
          if (row.getBoolean(1)) {
            org.springframework.test.util.ReflectionTestUtils.invokeMethod(health, "refresh");
            if (health.tryBeginRead()) {
              health.finishRead();
              return;
            }
          }
        }
        if (System.nanoTime() >= deadline)
          throw new IllegalStateException("Standby replay deadline exceeded");
        Thread.sleep(25);
      }
    }
  }

  private List<Map<String, Object>> inspectRead(Callable<?> request) throws Exception {
    org.springframework.test.util.ReflectionTestUtils.invokeMethod(health, "refresh");
    var observed = new ArrayList<Map<String, Object>>();
    org.mockito.stubbing.Answer<Object> answer =
        invocation -> {
          observed.add(
              jdbc.queryForMap(
                  "SELECT pg_is_in_recovery() AS replica, pg_backend_pid() AS pid, current_setting('transaction_read_only') AS readonly, current_setting('transaction_isolation') AS isolation, pg_current_snapshot()::text AS snapshot"));
          Object value = invocation.callRealMethod();
          observed.add(
              jdbc.queryForMap(
                  "SELECT pg_is_in_recovery() AS replica, pg_backend_pid() AS pid, current_setting('transaction_read_only') AS readonly, current_setting('transaction_isolation') AS isolation, pg_current_snapshot()::text AS snapshot"));
          return value;
        };
    doAnswer(answer).when(events).find(any(), anyBoolean());
    doAnswer(answer)
        .when(events)
        .list(
            any(),
            any(),
            any(),
            any(),
            any(),
            any(),
            any(),
            any(),
            anyBoolean(),
            any(),
            any(),
            anyInt(),
            anyBoolean());
    doAnswer(answer).when(events).withChildren(anyList(), anyBoolean());
    doAnswer(answer).when(events).categories();
    doAnswer(answer).when(events).venues();
    doAnswer(answer).when(events).imageReferenced(anyString());
    doAnswer(answer).when(requests).list(any(), any(), anyInt(), any());
    try {
      request.call();
    } finally {
      reset(events, requests);
    }
    return observed;
  }

  private UUID createPublishedEvent(String title) {
    var draft =
        new EventCommands.Draft(
            title,
            "Fixture summary",
            "Description",
            CATEGORY,
            VENUE,
            "QUANTITY",
            "2027-01-01T12:00:00Z",
            "2027-01-01T15:00:00Z",
            "2026-12-01T00:00:00Z",
            "2027-01-01T14:00:00Z",
            4,
            List.of(new EventCommands.Image(null, "events/poster", 0)),
            List.of(
                new EventCommands.TicketType(null, "Standard", "100005", "VND", "10", "#ABCDEF")),
            List.of());
    var event = service.create("owner-a", draft);
    var pending = service.submit(event.eventId(), "owner-a", event.rowVersion());
    var approved =
        service.approve(
            event.eventId(), "admin", new EventCommands.Approval(pending.rowVersion(), "0.1"));
    return service.publish(event.eventId(), "owner-a", approved.rowVersion()).eventId();
  }

  @Test
  void whitelistedPayloadsUsePhysicalStandbyAndOneSnapshot() throws Exception {
    UUID id = createPublishedEvent("Replica event");
    awaitReplay();
    jdbc.update(
        "INSERT INTO event_schema.tbl_event_cancellation_requests(id,event_id,requested_by,reason) VALUES(?,?,?,?)",
        UUID.randomUUID(),
        id,
        "owner-a",
        "Fixture cancellation");
    awaitReplay();
    for (String path :
        List.of(
            "/api/v1/events/" + id,
            "/api/v1/events",
            "/api/v1/events?compact=true",
            "/api/v1/events?includeSeatMap=false",
            "/api/v1/categories",
            "/api/v1/venues",
            "/api/v1/organizer/events",
            "/api/v1/admin/events?status=PUBLISHED",
            "/api/v1/organizer/events/" + id + "/cancellation-requests",
            "/api/v1/admin/cancellation-requests",
            "/api/v1/admin/events/" + id + "/cancellation-requests")) {
      String role = path.contains("/admin/") ? "ADMIN" : "ORGANIZER";
      var observed =
          inspectRead(
              () ->
                  mvc.perform(
                          get(path)
                              .with(
                                  jwt()
                                      .jwt(
                                          org.springframework.security.oauth2.jwt.Jwt
                                              .withTokenValue("fixture")
                                              .header("alg", "RS256")
                                              .subject("owner-a")
                                              .build())
                                      .authorities(new SimpleGrantedAuthority("ROLE_" + role))))
                      .andExpect(status().isOk()));
      assertThat(observed).as(path).isNotEmpty();
      for (var row : observed) {
        assertThat(row.get("replica")).as("whitelisted physical standby: %s", path).isEqualTo(true);
        assertThat(row.get("readonly")).isEqualTo("on");
        assertThat(row.get("isolation")).isEqualTo("repeatable read");
        assertThat(row.get("pid")).isEqualTo(observed.getFirst().get("pid"));
        assertThat(row.get("snapshot")).isEqualTo(observed.getFirst().get("snapshot"));
      }
    }
    mvc.perform(get("/api/v1/events/" + id))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.title").value("Replica event"));
    mvc.perform(
            get("/api/v1/organizer/events/" + id + "/cancellation-requests")
                .with(
                    jwt()
                        .jwt(
                            org.springframework.security.oauth2.jwt.Jwt.withTokenValue("fixture")
                                .header("alg", "RS256")
                                .subject("owner-a")
                                .build())
                        .authorities(new SimpleGrantedAuthority("ROLE_ORGANIZER"))))
        .andExpect(status().isOk())
        .andExpect(
            jsonPath("$.items[0].eventRowVersion")
                .value(service.readOwned(id, "owner-a").rowVersion()));
  }

  @Test
  void managementWritesAndOutboxStayOnWriter() throws Exception {
    UUID id = createPublishedEvent("Writer event");
    awaitReplay();
    for (String path :
        List.of(
            "/api/v1/organizer/events/" + id,
            "/api/v1/admin/events/" + id,
            "/api/v1/organizer/events/" + id + "/images")) {
      String role = path.contains("/admin/") ? "ADMIN" : "ORGANIZER";
      var observed =
          inspectRead(
              () ->
                  mvc.perform(
                          get(path)
                              .with(
                                  jwt()
                                      .jwt(
                                          org.springframework.security.oauth2.jwt.Jwt
                                              .withTokenValue("fixture")
                                              .header("alg", "RS256")
                                              .subject("owner-a")
                                              .build())
                                      .authorities(new SimpleGrantedAuthority("ROLE_" + role))))
                      .andExpect(status().isOk()));
      assertThat(observed).isNotEmpty();
      for (var row : observed) assertThat(row.get("replica")).as(path).isEqualTo(false);
    }
    var directHistory =
        inspectRead(() -> cancellations.history(id, "owner-a", true, null, 20, null));
    for (var row : directHistory) assertThat(row.get("replica")).isEqualTo(false);
    var reference = inspectRead(() -> images.referenced("events/poster"));
    assertThat(reference).isNotEmpty();
    for (var row : reference) assertThat(row.get("replica")).isEqualTo(false);
    // Standby không thể UPDATE; claim/confirm/retry thật thành công chứng minh writer path.
    var first = outbox.claim(java.time.Duration.ofSeconds(30)).getFirst();
    assertThat(outbox.backlog()).isEqualTo(3);
    jdbc.update(
        "UPDATE event_schema.tbl_outbox_messages SET next_attempt_at=clock_timestamp()-interval '1 second' WHERE message_id=?",
        first.id());
    var newer = outbox.claim(java.time.Duration.ofSeconds(30)).getFirst();
    assertThat(newer.id()).isEqualTo(first.id());
    assertThat(newer.attempt()).isEqualTo(first.attempt() + 1);
    assertThat(outbox.confirmed(first)).isFalse();
    outbox.retry(first, "STALE_WORKER", java.time.Duration.ZERO);
    assertThat(outbox.confirmed(newer)).isTrue();
    assertThat(outbox.backlog()).isEqualTo(2);
  }

  @Test
  void readerIneligibilityAndAttemptFailureKeepDistinctContracts() throws Exception {
    UUID id = createPublishedEvent("Failure policy");
    awaitReplay();
    try (var connection =
            DriverManager.getConnection(standby.getJdbcUrl(), "bootstrap", "isolated-bootstrap");
        var statement = connection.createStatement()) {
      statement.execute("SELECT pg_wal_replay_pause()");
      try {
        var writerRows =
            inspectRead(() -> mvc.perform(get("/api/v1/events/" + id)).andExpect(status().isOk()));
        assertThat(writerRows).isNotEmpty();
        for (var row : writerRows) assertThat(row.get("replica")).isEqualTo(false);
      } finally {
        statement.execute("SELECT pg_wal_replay_resume()");
      }
    }
    awaitReplay();
    org.springframework.test.util.ReflectionTestUtils.invokeMethod(health, "refresh");
    assertThat(health.tryBeginRead()).isTrue();
    try {
      var busyRows =
          inspectRead(() -> mvc.perform(get("/api/v1/categories")).andExpect(status().isOk()));
      for (var row : busyRows) assertThat(row.get("replica")).isEqualTo(false);
    } finally {
      health.finishRead();
    }
    awaitReplay();
    doAnswer(
            invocation -> {
              Object resource =
                  org.springframework.transaction.support.TransactionSynchronizationManager
                      .getResource(jdbc.getDataSource());
              assertThat(
                      org.springframework.transaction.support.TransactionSynchronizationManager
                          .getCurrentTransactionName())
                  .isEqualTo("eventReplicaRead");
              assertThat(resource)
                  .isInstanceOf(org.springframework.jdbc.datasource.ConnectionHolder.class);
              assertThat(
                      ((org.springframework.jdbc.datasource.ConnectionHolder) resource)
                          .hasTimeout())
                  .isTrue();
              // Tách timeout khỏi recovery conflict; chỉ pause fixture sau reader admission.
              try (var connection =
                      DriverManager.getConnection(
                          standby.getJdbcUrl(), "bootstrap", "isolated-bootstrap");
                  var statement = connection.createStatement()) {
                statement.setQueryTimeout(2);
                statement.execute("SELECT pg_wal_replay_pause()");
                try {
                  long deadline = System.nanoTime() + java.time.Duration.ofSeconds(2).toNanos();
                  while (true) {
                    try (var row =
                        statement.executeQuery("SELECT pg_get_wal_replay_pause_state()")) {
                      assertThat(row.next()).isTrue();
                      if ("paused".equals(row.getString(1))) break;
                    }
                    assertThat(System.nanoTime())
                        .as("fixture replay pause deadline")
                        .isLessThan(deadline);
                    Thread.sleep(10);
                  }
                  assertThat(jdbc.queryForObject("SELECT pg_is_in_recovery()", Boolean.class))
                      .isTrue();
                  jdbc.queryForObject("SELECT pg_sleep(10)", Object.class);
                  return invocation.callRealMethod();
                } finally {
                  statement.execute("SELECT pg_wal_replay_resume()");
                }
              }
            })
        .when(events)
        .find(id, false);
    long start = System.nanoTime();
    try {
      var timedOut =
          mvc.perform(get("/api/v1/events/" + id))
              .andExpect(status().isServiceUnavailable())
              .andExpect(jsonPath("$.code").value("STORAGE_UNAVAILABLE"))
              .andReturn();
      var cause =
          org.springframework.core.NestedExceptionUtils.getMostSpecificCause(
              timedOut.getResolvedException());
      assertThat(cause).isInstanceOf(java.sql.SQLException.class);
      assertThat(((java.sql.SQLException) cause).getSQLState()).isEqualTo("57014");
      verify(events, times(1)).find(id, false);
      assertThat(java.time.Duration.ofNanos(System.nanoTime() - start))
          .isLessThan(java.time.Duration.ofSeconds(9));
      assertThat(health.tryBeginRead()).as("reader error invalidates sample").isFalse();
    } finally {
      reset(events);
    }
    awaitReplay();
    assertThat(health.tryBeginRead()).as("permit released after failure").isTrue();
    health.finishRead();
    // Thiếu ACL reader không được che bằng retry writer.
    try (var connection =
            DriverManager.getConnection(primary.getJdbcUrl(), "bootstrap", "isolated-bootstrap");
        var statement = connection.createStatement()) {
      statement.execute("REVOKE SELECT ON event_schema.tbl_events FROM event_reader");
      awaitReplay();
      try {
        var observations =
            inspectRead(
                () ->
                    mvc.perform(get("/api/v1/events/" + id))
                        .andExpect(status().isServiceUnavailable()));
        assertThat(observations).hasSize(1);
        assertThat(observations.getFirst().get("replica")).isEqualTo(true);
      } finally {
        statement.execute("GRANT SELECT ON event_schema.tbl_events TO event_reader");
        awaitReplay();
      }
    }
    var reading = new java.util.concurrent.CountDownLatch(1);
    var outcome = new java.util.concurrent.atomic.AtomicInteger();
    var asyncFailure = new java.util.concurrent.atomic.AtomicReference<Throwable>();
    doAnswer(
            invocation -> {
              jdbc.queryForObject(
                  "SELECT id FROM event_schema.tbl_events WHERE id=?", UUID.class, id);
              reading.countDown();
              jdbc.queryForObject("SELECT pg_sleep(10)", Object.class);
              return invocation.callRealMethod();
            })
        .when(events)
        .find(id, false);
    Thread request =
        new Thread(
            () -> {
              try {
                outcome.set(
                    mvc.perform(get("/api/v1/events/" + id)).andReturn().getResponse().getStatus());
              } catch (Exception failure) {
                asyncFailure.set(failure);
              }
            });
    request.start();
    try (var connection =
            DriverManager.getConnection(primary.getJdbcUrl(), "bootstrap", "isolated-bootstrap");
        var statement = connection.createStatement()) {
      try {
        assertThat(reading.await(5, java.util.concurrent.TimeUnit.SECONDS)).isTrue();
        // ACCESS EXCLUSIVE WAL buộc standby hủy transaction đang giữ snapshot cũ.
        statement.execute(
            "ALTER TABLE event_schema.tbl_events ADD COLUMN replica_conflict_fixture integer");
        request.join(8000);
        assertThat(request.isAlive()).isFalse();
        assertThat(asyncFailure.get()).isNull();
        assertThat(outcome.get()).as("real recovery conflict maps SQLSTATE40001").isEqualTo(409);
        assertThat(health.tryBeginRead()).isFalse();
      } finally {
        request.join(8000);
        statement.execute(
            "ALTER TABLE event_schema.tbl_events DROP COLUMN IF EXISTS replica_conflict_fixture");
        reset(events);
        awaitReplay();
      }
    }
  }

  @Test
  void visibilityOwnershipAndListCursorsRemainCompatible() throws Exception {
    UUID id = createPublishedEvent("Visible owned event");
    UUID other = createPublishedEvent("Other owner event");
    jdbc.update("UPDATE event_schema.tbl_events SET organizer_subject='owner-b' WHERE id=?", other);
    UUID request = UUID.randomUUID();
    jdbc.update(
        "INSERT INTO event_schema.tbl_event_cancellation_requests(id,event_id,requested_by,reason,status,reviewed_by,reviewed_at,review_reason) VALUES(?,?,?,?,'REJECTED','admin',clock_timestamp(),'Fixture review')",
        UUID.randomUUID(),
        id,
        "owner-a",
        "Old request");
    jdbc.update(
        "INSERT INTO event_schema.tbl_event_cancellation_requests(id,event_id,requested_by,reason) VALUES(?,?,?,?)",
        request,
        id,
        "owner-a",
        "New request");
    awaitReplay();
    var owner =
        jwt()
            .jwt(
                org.springframework.security.oauth2.jwt.Jwt.withTokenValue("fixture")
                    .header("alg", "RS256")
                    .subject("owner-a")
                    .build())
            .authorities(new SimpleGrantedAuthority("ROLE_ORGANIZER"));
    var foreign =
        jwt()
            .jwt(
                org.springframework.security.oauth2.jwt.Jwt.withTokenValue("fixture")
                    .header("alg", "RS256")
                    .subject("owner-b")
                    .build())
            .authorities(new SimpleGrantedAuthority("ROLE_ORGANIZER"));
    var admin = jwt().authorities(new SimpleGrantedAuthority("ROLE_ADMIN"));
    mvc.perform(get("/api/v1/organizer/events")).andExpect(status().isUnauthorized());
    mvc.perform(get("/api/v1/admin/events").with(owner)).andExpect(status().isForbidden());
    mvc.perform(get("/api/v1/organizer/events/" + id + "/cancellation-requests").with(foreign))
        .andExpect(status().isNotFound());
    mvc.perform(get("/api/v1/organizer/events").with(owner))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.items.length()").value(1))
        .andExpect(jsonPath("$.items[0].eventId").value(id.toString()));
    mvc.perform(get("/api/v1/admin/events").with(admin))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.items.length()").value(0));
    mvc.perform(get("/api/v1/admin/cancellation-requests").with(admin))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.items.length()").value(1))
        .andExpect(jsonPath("$.items[0].requestId").value(request.toString()));
    var first =
        mvc.perform(
                get("/api/v1/organizer/events/" + id + "/cancellation-requests")
                    .param("size", "1")
                    .with(owner))
            .andExpect(status().isOk())
            .andReturn();
    var json = new tools.jackson.databind.json.JsonMapper();
    String cursor =
        json.readTree(first.getResponse().getContentAsString()).path("nextCursor").asString();
    mvc.perform(
            get("/api/v1/organizer/events/" + id + "/cancellation-requests")
                .param("size", "1")
                .param("cursor", cursor)
                .with(owner))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.items.length()").value(1))
        .andExpect(jsonPath("$.items[0].status").value("REJECTED"));
    var publicFirst =
        mvc.perform(get("/api/v1/events").param("size", "1"))
            .andExpect(status().isOk())
            .andReturn();
    String publicCursor =
        json.readTree(publicFirst.getResponse().getContentAsString()).path("nextCursor").asString();
    var publicSecond =
        mvc.perform(get("/api/v1/events").param("size", "1").param("cursor", publicCursor))
            .andExpect(status().isOk())
            .andReturn();
    assertThat(
            json.readTree(publicFirst.getResponse().getContentAsString())
                .path("items")
                .get(0)
                .path("eventId")
                .asString())
        .isNotEqualTo(
            json.readTree(publicSecond.getResponse().getContentAsString())
                .path("items")
                .get(0)
                .path("eventId")
                .asString());
    var seated =
        service.create(
            "owner-a",
            new EventCommands.Draft(
                "Seated reader contract",
                "Fixture summary",
                "Description",
                CATEGORY,
                VENUE,
                "SEAT_MAP",
                "2027-01-01T12:00:00Z",
                "2027-01-01T15:00:00Z",
                "2026-12-01T00:00:00Z",
                "2027-01-01T14:00:00Z",
                4,
                List.of(new EventCommands.Image(null, "events/poster", 0)),
                List.of(),
                List.of()));
    String seatPath = "/api/v1/organizer/events/" + seated.eventId() + "/seat-map";
    mvc.perform(
            put(seatPath)
                .param("rowVersion", "0")
                .with(owner)
                .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                .content(SeatMapFixtures.map(false)))
        .andExpect(status().isOk());
    mvc.perform(
            put("/api/v1/organizer/events/" + seated.eventId() + "/ticket-types")
                .with(owner)
                .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                .content(SeatMapFixtures.types("1")))
        .andExpect(status().isOk());
    String ticketType =
        service
            .readOwned(seated.eventId(), "owner-a")
            .ticketTypes()
            .getFirst()
            .ticketTypeId()
            .toString();
    mvc.perform(
            put(seatPath)
                .param("rowVersion", "2")
                .with(owner)
                .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                .content(SeatMapFixtures.map(true).replace(SeatMapFixtures.TYPE, ticketType)))
        .andExpect(status().isOk());
    var managementRows =
        inspectRead(() -> mvc.perform(get(seatPath).with(owner)).andExpect(status().isOk()));
    assertThat(managementRows).isNotEmpty();
    for (var row : managementRows) assertThat(row.get("replica")).isEqualTo(false);
    var submitted = service.submit(seated.eventId(), "owner-a", "3");
    var approved =
        service.approve(
            seated.eventId(), "admin", new EventCommands.Approval(submitted.rowVersion(), "0.1"));
    service.publish(seated.eventId(), "owner-a", approved.rowVersion());
    awaitReplay();
    org.springframework.test.util.ReflectionTestUtils.invokeMethod(health, "refresh");
    mvc.perform(get("/api/v1/events").param("q", "Seated reader contract"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.items[0].seatMap.sectors[0].seats.length()").value(1));
    mvc.perform(
            get("/api/v1/events")
                .param("q", "Seated reader contract")
                .param("includeSeatMap", "false"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.items[0].seatMap").isEmpty());
    mvc.perform(get("/api/v1/events").param("q", "Seated reader contract").param("compact", "true"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.items[0].seatMap").doesNotExist())
        .andExpect(jsonPath("$.items[0].description").doesNotExist());
    // Danh sách có thể trễ; POST vẫn kiểm version mới trên writer.
    String previous =
        json.readTree(first.getResponse().getContentAsString())
            .path("items")
            .get(0)
            .path("eventRowVersion")
            .asString();
    jdbc.update("UPDATE event_schema.tbl_events SET row_version=row_version+1 WHERE id=?", id);
    mvc.perform(
            post("/api/v1/admin/cancellation-requests/" + request + "/reject")
                .with(admin)
                .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                .content("{\"rowVersion\":\"" + previous + "\",\"reason\":\"Fixture rejected\"}"))
        .andExpect(status().isConflict());
    jdbc.update("UPDATE event_schema.tbl_events SET status='CANCELLED' WHERE id=?", id);
    awaitReplay();
    org.springframework.test.util.ReflectionTestUtils.invokeMethod(health, "refresh");
    mvc.perform(get("/api/v1/events/" + id)).andExpect(status().isNotFound());
  }
}
