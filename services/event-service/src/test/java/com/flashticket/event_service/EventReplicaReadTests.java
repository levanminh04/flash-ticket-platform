package com.flashticket.event_service;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.flashticket.event_service.api.*;
import com.flashticket.event_service.application.*;
import com.flashticket.event_service.application.CancellationTransactions;
import com.flashticket.event_service.application.EventService;
import com.flashticket.event_service.domain.EventData.ImageRole;
import com.flashticket.event_service.integration.EventImageStorage;
import com.flashticket.event_service.integration.EventSummaries;
import com.flashticket.event_service.integration.HttpEventSummaries;
import com.flashticket.event_service.messaging.OutboxStore;
import com.flashticket.event_service.persistence.CancellationRepository;
import com.flashticket.event_service.persistence.EventRepository;
import com.flashticket.event_service.persistence.SeatMapRepository;
import com.flashticket.event_service.web.EventProblem;
import jakarta.persistence.EntityManagerFactory;
import java.nio.file.Path;
import java.sql.DriverManager;
import java.util.*;
import java.util.concurrent.Callable;
import org.junit.jupiter.api.*;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.ApplicationContext;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.orm.jpa.EntityManagerFactoryUtils;
import org.springframework.orm.jpa.JpaTransactionManager;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;
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

  @Autowired PlatformTransactionManager manager;
  @Autowired EntityManagerFactory entityManagerFactory;
  @Autowired ApplicationContext context;
  @Autowired SeatMapService seatMaps;
  @Autowired VenueService venues;
  @Autowired CancellationService cancellationService;
  @MockitoSpyBean SeatMapRepository maps;

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
    for (String pool : List.of("write", "read")) {
      String prefix = "app.db." + pool + ".";
      registry.add(
          prefix + "jdbc-url", pool.equals("write") ? primary::getJdbcUrl : standby::getJdbcUrl);
      registry.add(prefix + "username", () -> "event_admin");
      registry.add(prefix + "password", () -> "isolated-event");
      registry.add(prefix + "schema", () -> "event_schema");
      registry.add(prefix + "maximum-pool-size", () -> pool.equals("write") ? "5" : "2");
      registry.add(prefix + "minimum-idle", () -> pool.equals("write") ? "1" : "0");
      registry.add(prefix + "connection-timeout", () -> "5000");
      registry.add(prefix + "data-source-properties.sslmode", () -> "disable");
      registry.add(
          prefix + "data-source-properties.targetServerType",
          () -> pool.equals("write") ? "primary" : "secondary");
      registry.add(prefix + "data-source-properties.socketTimeout", () -> "5");
    }
    registry.add("app.db.write.connection-init-sql", () -> "SET ROLE event_owner");
    registry.add("app.db.read.read-only", () -> "true");
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
        statement.execute("GRANT CONNECT ON DATABASE event_db TO event_admin");
        statement.execute("GRANT USAGE ON SCHEMA event_schema TO event_admin");
        statement.execute("GRANT SELECT ON ALL TABLES IN SCHEMA event_schema TO event_admin");
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
    reset(events, requests, maps);
    ReflectionTestUtils.setField(service, "homeDiscoveryCache", null);
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
          if (row.getBoolean(1)) return;
        }
        if (System.nanoTime() >= deadline)
          throw new IllegalStateException("Standby replay deadline exceeded");
        Thread.sleep(25);
      }
    }
  }

  private List<Map<String, Object>> inspectRead(Callable<?> request) throws Exception {
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
    doAnswer(answer).when(events).homeDiscovery();
    doAnswer(answer).when(maps).load(anyList());
    doAnswer(answer).when(events).venueExists(any());
    doAnswer(answer).when(events).createVenue(any(), any(), any(), any(), any(), any());
    doAnswer(answer).when(events).updateVenue(any(), any(), any(), any(), any(), any());
    doAnswer(answer).when(events).imageReferenced(anyString());
    doAnswer(answer).when(requests).list(any(), any(), anyInt(), any());
    try {
      request.call();
    } finally {
      reset(events, requests, maps);
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

  private void assertPhysicalRole(boolean expectedReplica, Callable<?> operation) throws Exception {
    var observed = inspectRead(operation);
    assertThat(observed).isNotEmpty();
    for (var row : observed) assertThat(row.get("replica")).isEqualTo(expectedReplica);
  }

  @Test
  void approvedReadApisUsePhysicalStandby() throws Exception {
    UUID id = createPublishedEvent("Replica catalogue");
    awaitReplay();
    for (String path :
        List.of(
            "/api/v1/events/" + id,
            "/api/v1/events",
            "/api/v1/events?compact=true",
            "/api/v1/events?includeSeatMap=false",
            "/api/v1/events/home-discovery",
            "/api/v1/categories",
            "/api/v1/venues",
            "/api/v1/organizer/events",
            "/api/v1/admin/events?status=PUBLISHED")) {
      var observed =
          inspectRead(
              () ->
                  mvc.perform(
                          get(path)
                              .with(
                                  jwt()
                                      .jwt(j -> j.subject("owner-a"))
                                      .authorities(
                                          new SimpleGrantedAuthority(
                                              path.contains("/admin/")
                                                  ? "ROLE_ADMIN"
                                                  : "ROLE_ORGANIZER"))))
                      .andExpect(status().isOk()));
      assertThat(observed).as(path).isNotEmpty();
      for (var row : observed) {
        assertThat(row.get("replica")).as(path).isEqualTo(true);
        assertThat(row.get("readonly")).isEqualTo("on");
        if (!path.endsWith("categories") && !path.endsWith("venues")) {
          assertThat(row.get("isolation")).isEqualTo("repeatable read");
          assertThat(row.get("pid")).isEqualTo(observed.getFirst().get("pid"));
          assertThat(row.get("snapshot")).isEqualTo(observed.getFirst().get("snapshot"));
        }
      }
    }
  }

  @Test
  void springTransactionsRouteJdbcAndJpaOnPhysicalConnections() throws Exception {
    assertThat(context.getBeansOfType(EntityManagerFactory.class)).hasSize(1);
    assertThat(context.getBeansOfType(PlatformTransactionManager.class)).hasSize(1);
    assertThat(manager).isInstanceOf(JpaTransactionManager.class);
    assertThat(context.getBean("dataSource"))
        .isInstanceOf(org.springframework.jdbc.datasource.LazyConnectionDataSourceProxy.class);
    assertThat(context.getBean("writeDs")).isInstanceOf(com.zaxxer.hikari.HikariDataSource.class);
    assertThat(context.getBean("readDs")).isInstanceOf(com.zaxxer.hikari.HikariDataSource.class);
    for (boolean readOnly : List.of(false, true)) {
      var transaction = new TransactionTemplate(manager);
      transaction.setReadOnly(readOnly);
      transaction.executeWithoutResult(
          status -> {
            var em = EntityManagerFactoryUtils.getTransactionalEntityManager(entityManagerFactory);
            assertThat(em).isNotNull();
            var jpa =
                (Object[])
                    em.createNativeQuery("SELECT pg_is_in_recovery(), pg_backend_pid()")
                        .getSingleResult();
            var jdbcRow =
                jdbc.queryForMap("SELECT pg_is_in_recovery() AS replica, pg_backend_pid() AS pid");
            assertThat(jpa[0]).isEqualTo(readOnly);
            assertThat(jdbcRow.get("replica")).isEqualTo(readOnly);
            assertThat(((Number) jpa[1]).intValue())
                .isEqualTo(((Number) jdbcRow.get("pid")).intValue());
          });
    }
    assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
    assertThat(jdbc.queryForObject("SELECT pg_is_in_recovery()", Boolean.class)).isFalse();
  }

  @Test
  void outerWriteTransactionKeepsNestedReadOnlyCallsOnPrimary() throws Exception {
    createPublishedEvent("Outer write");
    awaitReplay();
    assertPhysicalRole(
        false,
        () ->
            new TransactionTemplate(manager)
                .execute(
                    status -> {
                      service.categories();
                      return service.listPublic(null, null, 20, null);
                    }));
  }

  @Test
  void managementAndHistoryReadsStayPrimaryDuringReplicaLag() throws Exception {
    var input =
        new EventCommands.Draft(
            "Before edit",
            "Summary",
            "Body",
            CATEGORY,
            VENUE,
            "SEAT_MAP",
            "2027-01-01T12:00:00Z",
            "2027-01-01T15:00:00Z",
            "2026-12-01T00:00:00Z",
            "2027-01-01T14:00:00Z",
            4,
            List.of(),
            List.of(),
            List.of());
    var draft = service.create("owner-a", input);
    var json = new tools.jackson.databind.json.JsonMapper();
    var map = json.readValue(SeatMapFixtures.map(false), SeatMap.class);
    seatMaps.save(draft.eventId(), "owner-a", "0", map);
    var request =
        cancellations.request(
            draft.eventId(),
            "owner-a",
            new CancellationCommands.Request("1", "Fixture request"),
            java.time.Instant.now());
    awaitReplay();
    try (var c =
            DriverManager.getConnection(standby.getJdbcUrl(), "bootstrap", "isolated-bootstrap");
        var s = c.createStatement()) {
      s.execute("SELECT pg_wal_replay_pause()");
      try {
        cancellations.review(request.requestId(), "admin", "2", false, "Reviewed now");
        UUID id = draft.eventId();
        for (String path :
            List.of(
                "/api/v1/organizer/events/" + id,
                "/api/v1/admin/events/" + id,
                "/api/v1/organizer/events/" + id + "/images",
                "/api/v1/organizer/events/" + id + "/seat-map",
                "/api/v1/organizer/events/" + id + "/cancellation-requests",
                "/api/v1/admin/events/" + id + "/cancellation-requests",
                "/api/v1/admin/cancellation-requests?status=REJECTED")) {
          assertPhysicalRole(
              false,
              () ->
                  mvc.perform(
                          get(path)
                              .with(
                                  jwt()
                                      .jwt(j -> j.subject("owner-a"))
                                      .authorities(
                                          new SimpleGrantedAuthority(
                                              path.contains("/admin/")
                                                  ? "ROLE_ADMIN"
                                                  : "ROLE_ORGANIZER"))))
                      .andExpect(status().isOk()));
        }
        assertThat(service.readOwned(id, "owner-a").rowVersion()).isEqualTo("3");
        assertThat(
                cancellations
                    .history(id, "owner-a", true, null, 20, null)
                    .items()
                    .getFirst()
                    .status())
            .isEqualTo("REJECTED");
        mvc.perform(
                get("/api/v1/organizer/events/" + id)
                    .with(
                        jwt()
                            .jwt(j -> j.subject("foreign"))
                            .authorities(new SimpleGrantedAuthority("ROLE_ORGANIZER"))))
            .andExpect(status().isNotFound());
      } finally {
        s.execute("SELECT pg_wal_replay_resume()");
      }
    }
  }

  @Test
  void cancellationPrechecksUseLatestPrimaryState() throws Exception {
    var draft =
        service.create(
            "owner-a",
            new EventCommands.Draft(
                "Cancellation latest",
                "Summary",
                "Body",
                CATEGORY,
                VENUE,
                "QUANTITY",
                "2027-01-01T12:00:00Z",
                "2027-01-01T15:00:00Z",
                "2026-12-01T00:00:00Z",
                "2027-01-01T14:00:00Z",
                4,
                List.of(),
                List.of(
                    new EventCommands.TicketType(
                        null, "Standard", "100005", "VND", "10", "#ABCDEF")),
                List.of()));
    UUID id = draft.eventId();
    awaitReplay();
    try (var c =
            DriverManager.getConnection(standby.getJdbcUrl(), "bootstrap", "isolated-bootstrap");
        var s = c.createStatement()) {
      s.execute("SELECT pg_wal_replay_pause()");
      try {
        var pending = service.submit(id, "owner-a", draft.rowVersion());
        var approved =
            service.approve(id, "admin", new EventCommands.Approval(pending.rowVersion(), "0.1"));
        var published = service.publish(id, "owner-a", approved.rowVersion());
        when(summaries.financial(id, "owner-a", true))
            .thenReturn(
                new EventSummaries.Financial(
                    1,
                    id,
                    "owner-a",
                    java.time.Instant.now().toString(),
                    true,
                    "VND",
                    "100",
                    "0",
                    "10",
                    "90"));
        assertPhysicalRole(
            false,
            () -> {
              assertThatThrownBy(
                      () -> cancellationService.direct(id, "owner-a", published.rowVersion()))
                  .isInstanceOf(EventProblem.class)
                  .hasMessage("CANCELLATION_REQUEST_REQUIRED");
              return null;
            });
        assertPhysicalRole(
            false,
            () ->
                cancellationService.request(
                    id,
                    "owner-a",
                    new CancellationCommands.Request(published.rowVersion(), "Collected orders")));
        verify(summaries, times(2)).financial(id, "owner-a", true);
      } finally {
        s.execute("SELECT pg_wal_replay_resume()");
      }
    }
  }

  @Test
  void imagePrechecksAndReferenceChecksStayPrimaryDuringLag() throws Exception {
    var draft =
        service.create(
            "owner-a",
            new EventCommands.Draft(
                "Image latest",
                "Summary",
                "Body",
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
                    new EventCommands.TicketType(
                        null, "Standard", "100005", "VND", "10", "#ABCDEF")),
                List.of()));
    awaitReplay();
    try (var c =
            DriverManager.getConnection(standby.getJdbcUrl(), "bootstrap", "isolated-bootstrap");
        var s = c.createStatement()) {
      s.execute("SELECT pg_wal_replay_pause()");
      try {
        var attached =
            images.attach(
                draft.eventId(),
                "owner-a",
                "0",
                null,
                1,
                ImageRole.GALLERY,
                "events/new-reference");
        assertThat(attached.rowVersion()).isEqualTo("1");
        assertPhysicalRole(false, () -> images.referenced("events/new-reference"));
        assertThat(images.referenced("events/new-reference")).isTrue();
        assertThatThrownBy(
                () -> images.prepare(draft.eventId(), "owner-a", "0", null, 2, ImageRole.GALLERY))
            .isInstanceOf(EventProblem.class)
            .hasMessage("STALE_EVENT_VERSION");
        verifyNoInteractions(imageStorage);
      } finally {
        s.execute("SELECT pg_wal_replay_resume()");
      }
    }
  }

  @Test
  void backgroundAndVenueOperationsUsePrimaryWithoutOuterTransaction() throws Exception {
    var draft =
        service.create(
            "owner-a",
            new EventCommands.Draft(
                "Background",
                "Summary",
                "Body",
                CATEGORY,
                VENUE,
                "SEAT_MAP",
                "2027-01-01T12:00:00Z",
                "2027-01-01T15:00:00Z",
                "2026-12-01T00:00:00Z",
                "2027-01-01T14:00:00Z",
                4,
                List.of(),
                List.of(),
                List.of()));
    var file = new MockMultipartFile("file", "image.png", "image/png", new byte[] {1});
    when(imageStorage.uploadSeatMapBackground(eq(draft.eventId()), any()))
        .thenAnswer(
            invocation -> {
              assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
              return new EventImageStorage.Uploaded("events/background", "fixture");
            });
    assertPhysicalRole(false, () -> seatMaps.uploadBackground(draft.eventId(), "owner-a", file));
    when(imageStorage.uploadVenue(any(), any()))
        .thenAnswer(
            invocation -> {
              assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
              return new EventImageStorage.Uploaded("events/venue", "fixture");
            });
    var venue = venues.create("New venue", "Address", null, null, null);
    assertThat(venue.name()).isEqualTo("New venue");
    assertPhysicalRole(
        false, () -> venues.update(venue.id(), "Updated venue", null, null, null, file));
    assertThat(venues.update(venue.id(), "Last name", null, null, null, null).name())
        .isEqualTo("Last name");
  }

  @Test
  void pausedReplicaCanReturnStaleCatalogueAndCachedHome() throws Exception {
    UUID id = createPublishedEvent("Stale public");
    awaitReplay();
    try (var c =
            DriverManager.getConnection(standby.getJdbcUrl(), "bootstrap", "isolated-bootstrap");
        var s = c.createStatement()) {
      s.execute("SELECT pg_wal_replay_pause()");
      try {
        cancellations.cancel(id, "admin", service.readAdmin(id).rowVersion(), false);
        assertThat(service.readAdmin(id).status()).isEqualTo("CANCELLED");
        assertPhysicalRole(
            true, () -> mvc.perform(get("/api/v1/events/" + id)).andExpect(status().isOk()));
        var stale = service.homeDiscovery();
        assertThat(stale.totalLiveEvents()).isEqualTo(1);
        var cachedObservations =
            inspectRead(
                () -> {
                  assertThat(service.homeDiscovery()).isEqualTo(stale);
                  return null;
                });
        assertThat(cachedObservations).isEmpty();
        createPublishedEvent("Not replayed");
        assertPhysicalRole(
            true,
            () -> {
              assertThat(service.homeDiscovery().totalLiveEvents()).isEqualTo(1);
              return null;
            });
      } finally {
        s.execute("SELECT pg_wal_replay_resume()");
      }
    }
  }

  @Test
  void replicaSqlFailureDoesNotRetryPrimary() throws Exception {
    UUID id = createPublishedEvent("Reader failure");
    awaitReplay();
    clearInvocations(events);
    doAnswer(
            invocation -> {
              assertThat(jdbc.queryForObject("SELECT pg_is_in_recovery()", Boolean.class)).isTrue();
              jdbc.execute("SET LOCAL statement_timeout=100");
              jdbc.queryForObject("SELECT pg_sleep(2)", Object.class);
              return invocation.callRealMethod();
            })
        .when(events)
        .find(id, false);
    try {
      var result =
          mvc.perform(get("/api/v1/events/" + id))
              .andExpect(status().isServiceUnavailable())
              .andExpect(jsonPath("$.code").value("STORAGE_UNAVAILABLE"))
              .andReturn();
      var cause =
          org.springframework.core.NestedExceptionUtils.getMostSpecificCause(
              result.getResolvedException());
      assertThat(cause).isInstanceOf(java.sql.SQLException.class);
      assertThat(((java.sql.SQLException) cause).getSQLState()).isEqualTo("57014");
      verify(events, times(1)).find(id, false);
    } finally {
      reset(events);
    }
    assertPhysicalRole(false, () -> service.readOwned(id, "owner-a"));
  }

  @Test
  void writesReadBackFromPrimaryAndOutboxFencingIsPreserved() throws Exception {
    var draft =
        service.create(
            "owner-a",
            new EventCommands.Draft(
                "Write readback",
                "Summary",
                "Body",
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
                    new EventCommands.TicketType(
                        null, "Standard", "100005", "VND", "10", "#ABCDEF")),
                List.of()));
    awaitReplay();
    try (var c =
            DriverManager.getConnection(standby.getJdbcUrl(), "bootstrap", "isolated-bootstrap");
        var s = c.createStatement()) {
      s.execute("SELECT pg_wal_replay_pause()");
      try {
        assertPhysicalRole(
            false,
            () -> {
              var changed =
                  service.save(
                      draft.eventId(),
                      "owner-a",
                      new EventCommands.Save(
                          "0",
                          new EventCommands.Draft(
                              "New title",
                              "Summary",
                              "Body",
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
                                  new EventCommands.TicketType(
                                      null, "Standard", "100005", "VND", "10", "#ABCDEF")),
                              List.of())));
              assertThat(changed.title()).isEqualTo("New title");
              var pending = service.submit(draft.eventId(), "owner-a", changed.rowVersion());
              var approved =
                  service.approve(
                      draft.eventId(),
                      "admin",
                      new EventCommands.Approval(pending.rowVersion(), "0.1"));
              var published = service.publish(draft.eventId(), "owner-a", approved.rowVersion());
              assertThat(published.status()).isEqualTo("PUBLISHED");
              return published;
            });
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
      } finally {
        s.execute("SELECT pg_wal_replay_resume()");
      }
    }
  }

  @Test
  void visibilitySecurityAndCursorContractsRemainCompatible() throws Exception {
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
    mvc.perform(get("/api/v1/events/" + id)).andExpect(status().isNotFound());
    awaitReplay();
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
    Thread conflictRequest =
        new Thread(
            () -> {
              try {
                outcome.set(
                    mvc.perform(get("/api/v1/events/" + id)).andReturn().getResponse().getStatus());
              } catch (Exception failure) {
                asyncFailure.set(failure);
              }
            });
    conflictRequest.start();
    try (var connection =
            DriverManager.getConnection(primary.getJdbcUrl(), "bootstrap", "isolated-bootstrap");
        var statement = connection.createStatement()) {
      try {
        assertThat(reading.await(5, java.util.concurrent.TimeUnit.SECONDS)).isTrue();
        // ACCESS EXCLUSIVE WAL buộc standby hủy transaction đang giữ snapshot cũ.
        statement.execute(
            "ALTER TABLE event_schema.tbl_events ADD COLUMN replica_conflict_fixture integer");
        conflictRequest.join(8000);
        assertThat(conflictRequest.isAlive()).isFalse();
        assertThat(asyncFailure.get()).isNull();
        assertThat(outcome.get()).as("real recovery conflict maps SQLSTATE40001").isEqualTo(409);
      } finally {
        conflictRequest.join(8000);
        statement.execute(
            "ALTER TABLE event_schema.tbl_events DROP COLUMN IF EXISTS replica_conflict_fixture");
        reset(events);
        awaitReplay();
      }
    }
  }
}
