package com.flashticket.event_service;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.flashticket.event_service.api.CancellationCommands;
import com.flashticket.event_service.api.EventCommands;
import com.flashticket.event_service.application.CancellationTransactions;
import com.flashticket.event_service.application.EventService;
import com.flashticket.event_service.integration.EventSummaries;
import com.flashticket.event_service.integration.HttpEventSummaries;
import com.flashticket.event_service.messaging.OutboxStore;
import com.flashticket.event_service.web.EventProblem;
import java.nio.file.Path;
import java.sql.DriverManager;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.*;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.MountableFile;
import tools.jackson.databind.json.JsonMapper;

@SpringBootTest(
    properties = {
      "spring.autoconfigure.exclude=org.springframework.boot.amqp.autoconfigure.RabbitAutoConfiguration",
      "event.outbox.enabled=false",
      "event.read-model.cancellation-max-age=1s",
      "spring.jpa.hibernate.ddl-auto=none",
      "spring.datasource.hikari.connection-init-sql=SET ROLE event_owner"
    })
@AutoConfigureMockMvc
@Testcontainers
class EventFlowTests {
  static final PostgreSQLContainer POSTGRES =
      new PostgreSQLContainer("postgres:16.15")
          .withDatabaseName("event_db")
          .withUsername("bootstrap")
          .withPassword("isolated-bootstrap");

  static {
    POSTGRES.start();
    try {
      try (var connection =
              DriverManager.getConnection(
                  POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
          var statement = connection.createStatement()) {
        statement.execute("CREATE ROLE event_owner NOLOGIN");
        statement.execute("CREATE ROLE event_admin LOGIN NOINHERIT PASSWORD 'isolated-event'");
        statement.execute("GRANT event_owner TO event_admin");
        statement.execute("CREATE SCHEMA event_schema AUTHORIZATION event_owner");
        statement.execute("GRANT CONNECT ON DATABASE event_db TO event_admin");
      }
      var root = Path.of(System.getProperty("basedir", ".")).resolve("../..").normalize();
      POSTGRES.copyFileToContainer(
          MountableFile.forHostPath(root.resolve("docs/architecture/data/postgres/10-event.sql")),
          "/tmp/event.sql");
      POSTGRES.copyFileToContainer(
          MountableFile.forHostPath(root.resolve("services/99-table-prefix.sql")),
          "/tmp/prefix.sql");
      for (String file : List.of("/tmp/event.sql", "/tmp/prefix.sql")) {
        var result =
            POSTGRES.execInContainer(
                "psql", "-U", "bootstrap", "-d", "event_db", "-v", "ON_ERROR_STOP=1", "-f", file);
        if (result.getExitCode() != 0)
          throw new IllegalStateException("Canonical test schema initialization failed");
      }
    } catch (Exception exception) {
      POSTGRES.stop();
      throw new ExceptionInInitializerError(exception);
    }
  }

  @DynamicPropertySource
  static void properties(DynamicPropertyRegistry registry) {
    registry.add(
        "spring.datasource.url",
        () ->
            POSTGRES.getJdbcUrl()
                + (POSTGRES.getJdbcUrl().contains("?") ? "&" : "?")
                + "currentSchema=event_schema");
    registry.add("spring.datasource.username", () -> "event_admin");
    registry.add("spring.datasource.password", () -> "isolated-event");
  }

  @AfterAll
  static void stop() {
    POSTGRES.stop();
  }

  @Autowired EventService service;
  @Autowired JdbcTemplate jdbc;
  @Autowired MockMvc mvc;
  @Autowired JsonMapper json;
  @MockitoBean RabbitTemplate rabbit;
  @MockitoSpyBean OutboxStore outbox;
  @MockitoSpyBean HttpEventSummaries summaries;
  @Autowired CancellationTransactions cancellationTransactions;
  static final UUID VENUE = UUID.fromString("00000000-0000-4000-8000-000000000001");
  static final UUID CATEGORY = UUID.fromString("00000000-0000-4000-8000-000000000002");

  @BeforeEach
  void clean() throws Exception {
    try (var connection =
            DriverManager.getConnection(
                POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
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
  }

  static EventCommands.Draft draft(String title) {
    return new EventCommands.Draft(
        title,
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
        List.of(new EventCommands.TicketType(null, "Standard", "100005", "VND", "10", "#ABCDEF")),
        List.of(
            new EventCommands.Promotion(
                null,
                " save10 ",
                "2026-12-01T00:00:00Z",
                "2027-01-01T14:00:00Z",
                "5",
                new EventCommands.Discount("PERCENT", null, 10))));
  }

  static RequestPostProcessor actor(String sub, String role) {
    return jwt().jwt(j -> j.subject(sub)).authorities(new SimpleGrantedAuthority("ROLE_" + role));
  }

  @Test
  void createsSeatMapDraftWithoutQuantityTicketTypes() throws Exception {
    var v = draft("Seated concert");
    var input =
        new EventCommands.Draft(
            v.title(),
            v.description(),
            v.categoryId(),
            v.venueId(),
            "SEAT_MAP",
            v.startsAt(),
            v.endsAt(),
            v.saleStartsAt(),
            v.saleEndsAt(),
            v.purchaseLimit(),
            v.images(),
            List.of(),
            v.promotions());
    mvc.perform(
            post("/api/v1/organizer/events")
                .with(actor("seat-owner", "ORGANIZER"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(input)))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.mode").value("SEAT_MAP"));
  }

  @Test
  void seatMapRoundTripFreezesAndPublishesTypedSnapshotWithoutHiddenPublicSeats() throws Exception {
    var input = draft("Seat map lifecycle");
    var event =
        service.create(
            "a",
            new EventCommands.Draft(
                input.title(),
                input.description(),
                input.categoryId(),
                input.venueId(),
                "SEAT_MAP",
                input.startsAt(),
                input.endsAt(),
                input.saleStartsAt(),
                input.saleEndsAt(),
                input.purchaseLimit(),
                input.images(),
                List.of(),
                input.promotions()));
    String path = "/api/v1/organizer/events/" + event.eventId() + "/seat-map";
    mvc.perform(
            put(path)
                .param("rowVersion", "0")
                .with(actor("a", "ORGANIZER"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(SeatMapFixtures.map(false)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.layoutId").value(SeatMapFixtures.LAYOUT));
    mvc.perform(
            put("/api/v1/organizer/events/" + event.eventId() + "/ticket-types")
                .with(actor("a", "ORGANIZER"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(SeatMapFixtures.types("1")))
        .andExpect(status().isOk());
    var current = service.readOwned(event.eventId(), "a");
    String type = current.ticketTypes().getFirst().ticketTypeId().toString();
    mvc.perform(
            post("/api/v1/organizer/events/" + event.eventId() + "/submit")
                .with(actor("a", "ORGANIZER"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"rowVersion\":\"2\"}"))
        .andExpect(status().isUnprocessableEntity());
    mvc.perform(
            put(path)
                .param("rowVersion", "2")
                .with(actor("a", "ORGANIZER"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(SeatMapFixtures.map(true).replace(SeatMapFixtures.TYPE, type)))
        .andExpect(status().isOk());
    mvc.perform(get(path).with(actor("a", "ORGANIZER")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.sectors[0].seats.length()").value(2))
        .andExpect(jsonPath("$.sectors[0].seats[1].isHidden").value(true));
    var submitted = service.submit(event.eventId(), "a", "3");
    mvc.perform(get("/api/v1/admin/events/" + event.eventId()).with(actor("admin", "ADMIN")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.seatMap.sectors[0].seats.length()").value(2));
    var approved =
        service.approve(
            event.eventId(), "admin", new EventCommands.Approval(submitted.rowVersion(), "0.1"));
    service.publish(event.eventId(), "a", approved.rowVersion());
    mvc.perform(get("/api/v1/events/" + event.eventId()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.seatMap.sectors[0].seats.length()").value(1))
        .andExpect(jsonPath("$.ticketTypes[0].configuredQuantity").isEmpty());
    String snapshot =
        jdbc.queryForObject(
            "SELECT payload::text FROM event_schema.tbl_outbox_messages WHERE message_type='event.sales-configured.v1'",
            String.class);
    var sales = json.readTree(snapshot);
    assertThat(sales.get("sectors").size()).isEqualTo(1);
    assertThat(sales.get("seats").size()).isEqualTo(1);
    mvc.perform(
            put(path)
                .param("rowVersion", "5")
                .with(actor("a", "ORGANIZER"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(SeatMapFixtures.map(false)))
        .andExpect(status().isConflict());
  }

  @Test
  void adminCancellationIsAtomicAndIdempotent() throws Exception {
    var event = service.create("a", draft("Cancelled"));
    String path = "/api/v1/admin/events/" + event.eventId() + "/cancel";
    for (int i = 0; i < 2; i++) {
      mvc.perform(
              post(path)
                  .with(actor("admin", "ADMIN"))
                  .contentType(MediaType.APPLICATION_JSON)
                  .content("{\"rowVersion\":\"0\"}"))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.status").value("CANCELLED"));
    }
    assertThat(
            jdbc.queryForObject(
                "SELECT count(*) FROM event_schema.tbl_outbox_messages WHERE message_type='event.cancelled.v1'",
                Integer.class))
        .isEqualTo(1);
  }

  @Test
  void unavailableSummaryContractDoesNotReturnFakeStatistics() throws Exception {
    var event = service.create("a", draft("Stats"));
    mvc.perform(
            get("/api/v1/organizer/events/" + event.eventId() + "/statistics")
                .with(actor("a", "ORGANIZER")))
        .andExpect(status().isServiceUnavailable())
        .andExpect(jsonPath("$.code").value("DEPENDENCY_CONTRACT_NOT_READY"));
    mvc.perform(
            get("/api/v1/organizer/events/" + event.eventId() + "/statistics")
                .with(actor("foreign", "ORGANIZER")))
        .andExpect(status().isNotFound());
  }

  void collected(UUID id, boolean collected) {
    doAnswer(
            call -> {
              assertThat(
                      org.springframework.transaction.support.TransactionSynchronizationManager
                          .isActualTransactionActive())
                  .isFalse();
              return new EventSummaries.Financial(
                  1,
                  id,
                  call.getArgument(1, String.class),
                  java.time.Instant.now().toString(),
                  collected,
                  "VND",
                  "100000",
                  "0",
                  "10000",
                  "90000");
            })
        .when(summaries)
        .financial(eq(id), anyString(), anyBoolean());
  }

  @Test
  void requestHistoryRejectResubmitAndApprovalKeepOneCancellationIdentity() throws Exception {
    var event = service.create("a", draft("Paid event"));
    collected(event.eventId(), true);
    String organizer = "/api/v1/organizer/events/" + event.eventId();
    var result =
        mvc.perform(
                post(organizer + "/cancellation-requests")
                    .with(actor("a", "ORGANIZER"))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"rowVersion\":\"0\",\"reason\":\"Weather\"}"))
            .andExpect(status().isCreated())
            .andReturn();
    String requestId =
        json.readTree(result.getResponse().getContentAsString()).get("requestId").asString();
    mvc.perform(
            post(organizer + "/cancellation-requests")
                .with(actor("a", "ORGANIZER"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"rowVersion\":\"1\",\"reason\":\"Again\"}"))
        .andExpect(status().isConflict());
    mvc.perform(
            post("/api/v1/admin/cancellation-requests/" + requestId + "/reject")
                .with(actor("admin", "ADMIN"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"rowVersion\":\"1\",\"reason\":null}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("REJECTED"));
    assertThat(service.readOwned(event.eventId(), "a").status()).isEqualTo("DRAFT");
    assertThat(
            jdbc.queryForObject(
                "SELECT count(*) FROM event_schema.tbl_outbox_messages", Integer.class))
        .isZero();
    result =
        mvc.perform(
                post(organizer + "/cancellation-requests")
                    .with(actor("a", "ORGANIZER"))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"rowVersion\":\"2\",\"reason\":\"Storm\"}"))
            .andExpect(status().isCreated())
            .andReturn();
    String nextId =
        json.readTree(result.getResponse().getContentAsString()).get("requestId").asString();
    assertThat(nextId).isNotEqualTo(requestId);
    mvc.perform(get(organizer + "/cancellation-requests").with(actor("a", "ORGANIZER")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.items.length()").value(2));
    mvc.perform(get(organizer + "/cancellation-requests").with(actor("b", "ORGANIZER")))
        .andExpect(status().isNotFound());
    for (int n = 0; n < 2; n++)
      mvc.perform(
              post("/api/v1/admin/cancellation-requests/" + nextId + "/approve")
                  .with(actor("admin", "ADMIN"))
                  .contentType(MediaType.APPLICATION_JSON)
                  .content("{\"rowVersion\":\"3\"}"))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.status").value("APPROVED"));
    assertThat(service.readOwned(event.eventId(), "a").status()).isEqualTo("CANCELLED");
    assertThat(
            jdbc.queryForObject(
                "SELECT count(*) FROM event_schema.tbl_outbox_messages", Integer.class))
        .isEqualTo(1);
    var cancelled =
        json.readTree(
            jdbc.queryForObject(
                "SELECT payload::text FROM event_schema.tbl_outbox_messages", String.class));
    assertThat(cancelled.get("sales").get("eventStatus").asString()).isEqualTo("CANCELLED");
    assertThat(cancelled.get("access").get("eventStatus").asString()).isEqualTo("CANCELLED");
    mvc.perform(
            post(organizer + "/cancellation-requests")
                .with(actor("a", "ORGANIZER"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"rowVersion\":\"4\",\"reason\":\"Again\"}"))
        .andExpect(status().isConflict());
  }

  @Test
  void directCancellationRequiresRealSummaryAndRechecksVersionAfterHttp() throws Exception {
    var event = service.create("a", draft("Direct"));
    String path = "/api/v1/organizer/events/" + event.eventId() + "/cancel";
    mvc.perform(
            post(path)
                .with(actor("a", "ORGANIZER"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"rowVersion\":\"0\"}"))
        .andExpect(status().isServiceUnavailable());
    assertThat(service.readOwned(event.eventId(), "a").status()).isEqualTo("DRAFT");
    collected(event.eventId(), true);
    mvc.perform(
            post(path)
                .with(actor("a", "ORGANIZER"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"rowVersion\":\"0\"}"))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("CANCELLATION_REQUEST_REQUIRED"));
    collected(event.eventId(), false);
    mvc.perform(
            post(path)
                .with(actor("a", "ORGANIZER"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"rowVersion\":\"0\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("CANCELLED"));
    mvc.perform(
            post(path)
                .with(actor("a", "ORGANIZER"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"rowVersion\":\"0\"}"))
        .andExpect(status().isOk());
    var racing = service.create("a", draft("Race"));
    doAnswer(
            call -> {
              assertThat(
                      org.springframework.transaction.support.TransactionSynchronizationManager
                          .isActualTransactionActive())
                  .isFalse();
              jdbc.update(
                  "UPDATE event_schema.tbl_events SET row_version=row_version+1 WHERE id=?",
                  racing.eventId());
              return new EventSummaries.Financial(
                  1,
                  racing.eventId(),
                  "a",
                  java.time.Instant.now().toString(),
                  false,
                  "VND",
                  "0",
                  "0",
                  "0",
                  "0");
            })
        .when(summaries)
        .financial(eq(racing.eventId()), eq("a"), eq(true));
    mvc.perform(
            post("/api/v1/organizer/events/" + racing.eventId() + "/cancel")
                .with(actor("a", "ORGANIZER"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"rowVersion\":\"0\"}"))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("STALE_EVENT_VERSION"));
    assertThat(service.readOwned(racing.eventId(), "a").status()).isEqualTo("DRAFT");
  }

  @Test
  void cancellationAfterStartAndOutboxFailureCannotPartiallyCancel() throws Exception {
    var event = service.create("a", draft("Late"));
    jdbc.update(
        "UPDATE event_schema.tbl_events SET starts_at=now()-interval '1 hour',checkin_starts_at=now()-interval '1 hour' WHERE id=?",
        event.eventId());
    mvc.perform(
            post("/api/v1/admin/events/" + event.eventId() + "/cancel")
                .with(actor("admin", "ADMIN"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"rowVersion\":\"0\"}"))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("EVENT_ALREADY_STARTED"));
    var future = service.create("a", draft("Rollback cancellation"));
    doThrow(
            new org.springframework.dao.DataAccessResourceFailureException(
                "Injected outbox failure"))
        .when(outbox)
        .append(any(), eq("event.cancelled.v1"), any());
    mvc.perform(
            post("/api/v1/admin/events/" + future.eventId() + "/cancel")
                .with(actor("admin", "ADMIN"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"rowVersion\":\"0\"}"))
        .andExpect(status().isServiceUnavailable());
    assertThat(service.readOwned(future.eventId(), "a").status()).isEqualTo("DRAFT");
    assertThat(service.readOwned(future.eventId(), "a").rowVersion()).isEqualTo("0");
    assertThat(
            jdbc.queryForObject(
                "SELECT count(*) FROM event_schema.tbl_outbox_messages", Integer.class))
        .isZero();
  }

  @Test
  void statisticsUsesOwnerScopedReadSummariesOutsideTransaction() throws Exception {
    var event = service.create("a", draft("Statistics"));
    collected(event.eventId(), true);
    doAnswer(
            call -> {
              assertThat(
                      org.springframework.transaction.support.TransactionSynchronizationManager
                          .isActualTransactionActive())
                  .isFalse();
              return new EventSummaries.Sales(
                  1,
                  event.eventId(),
                  "a",
                  java.time.Instant.now().minusSeconds(5).toString(),
                  "10");
            })
        .when(summaries)
        .sales(event.eventId(), "a");
    mvc.perform(
            get("/api/v1/organizer/events/" + event.eventId() + "/statistics")
                .with(actor("a", "ORGANIZER")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.purchasedTicketCount").value("10"))
        .andExpect(jsonPath("$.netAmount").value("90000"))
        .andExpect(jsonPath("$.asOf").isNotEmpty());
    mvc.perform(
            get("/api/v1/organizer/events/" + event.eventId() + "/statistics")
                .with(actor("b", "ORGANIZER")))
        .andExpect(status().isNotFound());
    assertThat(
            jdbc.queryForObject(
                "SELECT count(*) FROM event_schema.tbl_outbox_messages", Integer.class))
        .isZero();
  }

  @Test
  void modeChangeNeedsConfirmationAndBadMapCannotEraseDraft() throws Exception {
    var event = service.create("a", draft("Switch"));
    String base = "/api/v1/organizer/events/" + event.eventId();
    mvc.perform(
            post(base + "/sales-mode")
                .with(actor("a", "ORGANIZER"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    "{\"rowVersion\":\"0\",\"mode\":\"SEAT_MAP\",\"confirmDeleteIncompatibleConfiguration\":false}"))
        .andExpect(status().isConflict());
    assertThat(service.readOwned(event.eventId(), "a").ticketTypes()).hasSize(1);
    mvc.perform(
            post(base + "/sales-mode")
                .with(actor("a", "ORGANIZER"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    "{\"rowVersion\":\"0\",\"mode\":\"SEAT_MAP\",\"confirmDeleteIncompatibleConfiguration\":true}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.ticketTypes.length()").value(0));
    mvc.perform(
            put(base + "/seat-map")
                .param("rowVersion", "1")
                .with(actor("a", "ORGANIZER"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(SeatMapFixtures.map(false)))
        .andExpect(status().isOk());
    mvc.perform(
            put(base + "/seat-map")
                .param("rowVersion", "2")
                .with(actor("a", "ORGANIZER"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(SeatMapFixtures.map(false).replace("\"width\":100", "\"width\":-1")))
        .andExpect(status().isUnprocessableEntity());
    assertThat(service.readOwned(event.eventId(), "a").rowVersion()).isEqualTo("2");
    mvc.perform(get(base + "/seat-map").with(actor("a", "ORGANIZER")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.sectors[0].seats.length()").value(2));
    mvc.perform(
            post(base + "/sales-mode")
                .with(actor("a", "ORGANIZER"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    "{\"rowVersion\":\"2\",\"mode\":\"QUANTITY\",\"confirmDeleteIncompatibleConfiguration\":true}"))
        .andExpect(status().isOk());
    assertThat(
            jdbc.queryForObject(
                "SELECT count(*) FROM event_schema.tbl_event_layouts", Integer.class))
        .isZero();
    assertThat(service.readOwned(event.eventId(), "a").promotions()).hasSize(1);
  }

  @Test
  void cancellationReviewRollsBackRequestAndEventWhenOutboxFails() {
    var event = service.create("a", draft("Request rollback"));
    var request =
        cancellationTransactions.request(
            event.eventId(),
            "a",
            new CancellationCommands.Request("0", "Weather"),
            java.time.Instant.now());
    doThrow(
            new org.springframework.dao.DataAccessResourceFailureException(
                "Injected outbox failure"))
        .when(outbox)
        .append(any(), eq("event.cancelled.v1"), any());
    assertThatThrownBy(
            () -> cancellationTransactions.review(request.requestId(), "admin", "1", true, null))
        .isInstanceOf(org.springframework.dao.DataAccessException.class);
    assertThat(service.readOwned(event.eventId(), "a").status()).isEqualTo("DRAFT");
    assertThat(service.readOwned(event.eventId(), "a").rowVersion()).isEqualTo("1");
    assertThat(
            jdbc.queryForObject(
                "SELECT status FROM event_schema.tbl_event_cancellation_requests", String.class))
        .isEqualTo("PENDING");
    assertThat(
            jdbc.queryForObject(
                "SELECT count(*) FROM event_schema.tbl_outbox_messages", Integer.class))
        .isZero();
  }

  @Test
  void concurrentCancellationAndRequestDecisionsSerializeOnEvent() throws Exception {
    var event = service.create("a", draft("Double cancel"));
    var start = new CountDownLatch(1);
    try (var executor = Executors.newFixedThreadPool(2)) {
      var one =
          executor.submit(
              () -> {
                start.await();
                return cancellationTransactions.cancel(event.eventId(), "admin", "0", false);
              });
      var two =
          executor.submit(
              () -> {
                start.await();
                return cancellationTransactions.cancel(event.eventId(), "admin", "0", false);
              });
      start.countDown();
      assertThat(one.get(15, TimeUnit.SECONDS).rowVersion())
          .isEqualTo(two.get(15, TimeUnit.SECONDS).rowVersion());
    }
    assertThat(
            jdbc.queryForObject(
                "SELECT count(*) FROM event_schema.tbl_outbox_messages", Integer.class))
        .isEqualTo(1);
    var next = service.create("a", draft("Opposing reviews"));
    var request =
        cancellationTransactions.request(
            next.eventId(),
            "a",
            new CancellationCommands.Request("0", "Weather"),
            java.time.Instant.now());
    var gate = new CountDownLatch(1);
    try (var executor = Executors.newFixedThreadPool(2)) {
      var accept =
          executor.submit(
              () -> {
                gate.await();
                try {
                  cancellationTransactions.review(request.requestId(), "admin", "1", true, null);
                  return true;
                } catch (EventProblem e) {
                  return false;
                }
              });
      var reject =
          executor.submit(
              () -> {
                gate.await();
                try {
                  cancellationTransactions.review(request.requestId(), "admin", "1", false, null);
                  return true;
                } catch (EventProblem e) {
                  return false;
                }
              });
      gate.countDown();
      assertThat(accept.get(15, TimeUnit.SECONDS) ^ reject.get(15, TimeUnit.SECONDS)).isTrue();
    }
    var state =
        jdbc.queryForObject(
            "SELECT status FROM event_schema.tbl_event_cancellation_requests WHERE id=?",
            String.class,
            request.requestId());
    assertThat(service.readOwned(next.eventId(), "a").status())
        .isEqualTo("APPROVED".equals(state) ? "CANCELLED" : "DRAFT");
  }

  @Test
  void standingSectorUsesSharedCapacityAndNoSeatInventoryInSnapshot() throws Exception {
    var event = service.create("a", draft("Standing"));
    String base = "/api/v1/organizer/events/" + event.eventId();
    mvc.perform(
            post(base + "/sales-mode")
                .with(actor("a", "ORGANIZER"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    "{\"rowVersion\":\"0\",\"mode\":\"SEAT_MAP\",\"confirmDeleteIncompatibleConfiguration\":true}"))
        .andExpect(status().isOk());
    String map =
        SeatMapFixtures.map(false)
            .replace("SEATED", "STANDING")
            .replaceAll("(?s)\"seats\":\\[.*", "\"seats\":[]}]}");
    mvc.perform(
            put(base + "/seat-map")
                .param("rowVersion", "1")
                .with(actor("a", "ORGANIZER"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(map))
        .andExpect(status().isOk());
    mvc.perform(
            put(base + "/ticket-types")
                .with(actor("a", "ORGANIZER"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(SeatMapFixtures.types("2")))
        .andExpect(status().isOk());
    var submitted = service.submit(event.eventId(), "a", "3");
    var approved =
        service.approve(
            event.eventId(), "admin", new EventCommands.Approval(submitted.rowVersion(), "0.1"));
    service.publish(event.eventId(), "a", approved.rowVersion());
    var snapshot =
        json.readTree(
            jdbc.queryForObject(
                "SELECT payload::text FROM event_schema.tbl_outbox_messages WHERE message_type='event.sales-configured.v1'",
                String.class));
    assertThat(snapshot.get("sectors").get(0).get("kind").asString()).isEqualTo("STANDING");
    assertThat(snapshot.get("sectors").get(0).get("configuredCapacity").asString()).isEqualTo("2");
    assertThat(snapshot.get("seats").size()).isZero();
    assertThat(snapshot.get("ticketTypes").get(0).get("configuredQuantity").isNull()).isTrue();
  }

  @Test
  void seatInsertFailureRollsBackLayoutAndDeletedChildren() throws Exception {
    var event = service.create("a", draft("Map rollback"));
    String base = "/api/v1/organizer/events/" + event.eventId();
    mvc.perform(
            post(base + "/sales-mode")
                .with(actor("a", "ORGANIZER"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    "{\"rowVersion\":\"0\",\"mode\":\"SEAT_MAP\",\"confirmDeleteIncompatibleConfiguration\":true}"))
        .andExpect(status().isOk());
    mvc.perform(
            put(base + "/seat-map")
                .param("rowVersion", "1")
                .with(actor("a", "ORGANIZER"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(SeatMapFixtures.map(false)))
        .andExpect(status().isOk());
    try (var connection =
            DriverManager.getConnection(
                POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
        var statement = connection.createStatement()) {
      statement.execute(
          "CREATE FUNCTION event_schema.test_reject_seat() RETURNS trigger LANGUAGE plpgsql AS $$ BEGIN IF NEW.seat_label='A2' THEN RAISE EXCEPTION 'Injected seat fault'; END IF; RETURN NEW; END $$");
      statement.execute(
          "CREATE TRIGGER test_seat_fault BEFORE INSERT ON event_schema.tbl_event_seats FOR EACH ROW EXECUTE FUNCTION event_schema.test_reject_seat()");
      try {
        mvc.perform(
                put(base + "/seat-map")
                    .param("rowVersion", "2")
                    .with(actor("a", "ORGANIZER"))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(SeatMapFixtures.map(false).replace("Main hall", "Must rollback")))
            .andExpect(status().isServiceUnavailable());
      } finally {
        statement.execute("DROP TRIGGER test_seat_fault ON event_schema.tbl_event_seats");
        statement.execute("DROP FUNCTION event_schema.test_reject_seat()");
      }
    }
    assertThat(service.readOwned(event.eventId(), "a").rowVersion()).isEqualTo("2");
    mvc.perform(get(base + "/seat-map").with(actor("a", "ORGANIZER")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.name").value("Main hall"))
        .andExpect(jsonPath("$.sectors[0].seats.length()").value(2));
  }

  @Test
  void visibleSeatsCannotExceedCapacityButHiddenSeatsAreRetained() throws Exception {
    var v = draft("Capacity");
    var event =
        service.create(
            "a",
            new EventCommands.Draft(
                v.title(),
                v.description(),
                v.categoryId(),
                v.venueId(),
                "SEAT_MAP",
                v.startsAt(),
                v.endsAt(),
                v.saleStartsAt(),
                v.saleEndsAt(),
                v.purchaseLimit(),
                v.images(),
                List.of(),
                v.promotions()));
    String base = "/api/v1/organizer/events/" + event.eventId();
    String zero =
        SeatMapFixtures.map(false)
            .replace("\"configuredCapacity\":\"2\"", "\"configuredCapacity\":\"0\"");
    mvc.perform(
            put(base + "/seat-map")
                .param("rowVersion", "0")
                .with(actor("a", "ORGANIZER"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(zero))
        .andExpect(status().isOk());
    mvc.perform(
            put(base + "/ticket-types")
                .with(actor("a", "ORGANIZER"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(SeatMapFixtures.types("1")))
        .andExpect(status().isOk());
    String type =
        service.readOwned(event.eventId(), "a").ticketTypes().getFirst().ticketTypeId().toString();
    zero =
        SeatMapFixtures.map(true)
            .replace(SeatMapFixtures.TYPE, type)
            .replace("\"configuredCapacity\":\"2\"", "\"configuredCapacity\":\"0\"");
    mvc.perform(
            put(base + "/seat-map")
                .param("rowVersion", "2")
                .with(actor("a", "ORGANIZER"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(zero))
        .andExpect(status().isOk());
    mvc.perform(
            post(base + "/submit")
                .with(actor("a", "ORGANIZER"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"rowVersion\":\"3\"}"))
        .andExpect(status().isUnprocessableEntity());
    assertThat(service.readOwned(event.eventId(), "a").status()).isEqualTo("DRAFT");
    mvc.perform(
            put(base + "/seat-map")
                .param("rowVersion", "3")
                .with(actor("a", "ORGANIZER"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    zero.replace("\"configuredCapacity\":\"0\"", "\"configuredCapacity\":\"1\"")))
        .andExpect(status().isOk());
    mvc.perform(
            post(base + "/submit")
                .with(actor("a", "ORGANIZER"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"rowVersion\":\"4\"}"))
        .andExpect(status().isOk());
  }

  @Test
  void classificationMustStillBeFreshAfterWaitingForParentLockRollback() throws Exception {
    var event = service.create("a", draft("Waiting cancellation"));
    var locked = new CountDownLatch(1);
    var release = new CountDownLatch(1);
    var read = new CountDownLatch(1);
    doAnswer(
            call -> {
              assertThat(
                      org.springframework.transaction.support.TransactionSynchronizationManager
                          .isActualTransactionActive())
                  .isFalse();
              var summary =
                  new EventSummaries.Financial(
                      1,
                      event.eventId(),
                      "a",
                      java.time.Instant.now().toString(),
                      false,
                      "VND",
                      "0",
                      "0",
                      "0",
                      "0");
              read.countDown();
              return summary;
            })
        .when(summaries)
        .financial(event.eventId(), "a", true);
    try (var executor = Executors.newFixedThreadPool(2)) {
      var holder =
          executor.submit(
              () -> {
                try (var connection =
                        DriverManager.getConnection(
                            POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
                    var statement =
                        connection.prepareStatement(
                            "SELECT id FROM event_schema.tbl_events WHERE id=? FOR UPDATE")) {
                  connection.setAutoCommit(false);
                  statement.setObject(1, event.eventId());
                  try (var rows = statement.executeQuery()) {
                    assertThat(rows.next()).isTrue();
                    locked.countDown();
                    release.await(10, TimeUnit.SECONDS);
                  } finally {
                    connection.rollback();
                  }
                }
                return true;
              });
      try {
        assertThat(locked.await(5, TimeUnit.SECONDS)).isTrue();
        var cancel =
            executor.submit(
                () ->
                    mvc.perform(
                        post("/api/v1/organizer/events/" + event.eventId() + "/cancel")
                            .with(actor("a", "ORGANIZER"))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"rowVersion\":\"0\"}")));
        assertThat(read.await(5, TimeUnit.SECONDS)).isTrue();
        Thread.sleep(1200);
        release.countDown();
        cancel
            .get(10, TimeUnit.SECONDS)
            .andExpect(status().isServiceUnavailable())
            .andExpect(jsonPath("$.code").value("UPSTREAM_DATA_STALE"));
      } finally {
        release.countDown();
        holder.get(10, TimeUnit.SECONDS);
      }
    }
    assertThat(service.readOwned(event.eventId(), "a").rowVersion()).isEqualTo("0");
    assertThat(
            jdbc.queryForObject(
                "SELECT count(*) FROM event_schema.tbl_outbox_messages", Integer.class))
        .isZero();
  }

  @Test
  void httpLifecycleEnforcesRolesOwnershipAndOptimisticVersion() throws Exception {
    var response =
        mvc.perform(
                post("/api/v1/organizer/events")
                    .with(actor("http-owner", "ORGANIZER"))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(json.writeValueAsString(draft("HTTP concert"))))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.organizerSubject").value("http-owner"))
            .andReturn();
    String eventId =
        json.readTree(response.getResponse().getContentAsString()).get("eventId").asString();
    String base = "/api/v1/organizer/events/" + eventId;
    mvc.perform(get(base).with(actor("foreign-owner", "ORGANIZER")))
        .andExpect(status().isNotFound());
    mvc.perform(
            post(base + "/submit")
                .with(actor("http-owner", "BUYER"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"rowVersion\":\"0\"}"))
        .andExpect(status().isForbidden());
    mvc.perform(
            put(base)
                .with(actor("http-owner", "ORGANIZER"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    json.writeValueAsString(new EventCommands.Save("0", draft("Revised HTTP")))))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.rowVersion").value("1"));
    mvc.perform(
            post(base + "/submit")
                .with(actor("http-owner", "ORGANIZER"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"rowVersion\":\"0\"}"))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("STALE_EVENT_VERSION"));
    mvc.perform(
            post(base + "/submit")
                .with(actor("http-owner", "ORGANIZER"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"rowVersion\":\"1\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("PENDING_APPROVAL"));
    String approval = "/api/v1/admin/events/" + eventId + "/approve";
    mvc.perform(
            post(approval)
                .with(actor("http-owner", "ORGANIZER"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"rowVersion\":\"2\",\"feeRate\":\"0.1\"}"))
        .andExpect(status().isForbidden());
    mvc.perform(
            post(approval)
                .with(actor("http-admin", "ADMIN"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"rowVersion\":\"2\",\"feeRate\":\"0.1\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("APPROVED"));
    for (int attempt = 0; attempt < 2; attempt++) {
      mvc.perform(
              post(base + "/publish")
                  .with(actor("http-owner", "ORGANIZER"))
                  .contentType(MediaType.APPLICATION_JSON)
                  .content("{\"rowVersion\":\"3\"}"))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.rowVersion").value("4"));
    }
    mvc.perform(get("/api/v1/events/" + eventId))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.title").value("Revised HTTP"))
        .andExpect(jsonPath("$.organizerSubject").doesNotExist());
    assertThat(
            jdbc.queryForObject(
                "SELECT count(*) FROM event_schema.tbl_outbox_messages", Integer.class))
        .isEqualTo(3);
  }

  @Test
  void httpRejectsNonCanonicalUuidAndCarriesValidatedCorrelation() throws Exception {
    String correlation = UUID.randomUUID().toString();
    mvc.perform(get("/api/v1/events/1-1-1-1-1").header("X-Correlation-Id", correlation))
        .andExpect(status().isBadRequest())
        .andExpect(header().string("X-Correlation-Id", correlation))
        .andExpect(jsonPath("$.correlationId").value(correlation));
    mvc.perform(get("/api/v1/events").param("categoryId", "AAAAAAAAAAAAAAAAAAAAAA=="))
        .andExpect(status().isBadRequest());
    mvc.perform(
            post("/api/v1/organizer/events")
                .with(actor("a", "ORGANIZER"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    json.writeValueAsString(draft("Strict UUID"))
                        .replace(CATEGORY.toString(), "AAAAAAAAAAAAAAAAAAAAAA==")))
        .andExpect(status().isBadRequest());
    var invalidHeader =
        mvc.perform(get("/api/v1/events").header("X-Correlation-Id", "untrusted"))
            .andExpect(status().isOk())
            .andReturn();
    assertThatCode(() -> UUID.fromString(invalidHeader.getResponse().getHeader("X-Correlation-Id")))
        .doesNotThrowAnyException();
  }

  @Test
  void hiddenSeatMapDoesNotRevealDraftToPublicOrForeignOwner() throws Exception {
    var event = service.create("a", draft("Hidden seat map"));
    jdbc.update("DELETE FROM event_schema.tbl_ticket_types WHERE event_id=?", event.eventId());
    jdbc.update(
        "UPDATE event_schema.tbl_events SET sales_mode='SEAT_MAP' WHERE id=?", event.eventId());
    mvc.perform(get("/api/v1/events/" + event.eventId())).andExpect(status().isNotFound());
    mvc.perform(get("/api/v1/organizer/events/" + event.eventId()).with(actor("b", "ORGANIZER")))
        .andExpect(status().isNotFound());
    mvc.perform(get("/api/v1/organizer/events/" + event.eventId()).with(actor("a", "ORGANIZER")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.mode").value("SEAT_MAP"));
  }

  @Test
  void fullQuantityLifecycleAndPublicContract() throws Exception {
    assertThat(jdbc.queryForObject("SELECT current_user", String.class)).isEqualTo("event_owner");
    assertThat(jdbc.queryForObject("SELECT current_schema()", String.class))
        .isEqualTo("event_schema");
    var created = service.create("organizer-a", draft("Concert"));
    assertThat(created.rowVersion()).isEqualTo("0");
    assertThat(created.promotions().getFirst().code()).isEqualTo("SAVE10");
    assertThat(
            jdbc.queryForObject(
                "SELECT count(*) FROM event_schema.tbl_outbox_messages", Integer.class))
        .isZero();
    mvc.perform(get("/api/v1/events/" + created.eventId())).andExpect(status().isNotFound());
    var pending = service.submit(created.eventId(), "organizer-a", created.rowVersion());
    var approved =
        service.approve(
            created.eventId(),
            "admin-a",
            new EventCommands.Approval(pending.rowVersion(), "0.125"));
    assertThat(approved.feeRate()).isEqualTo("0.12500000");
    var published = service.publish(created.eventId(), "organizer-a", approved.rowVersion());
    assertThat(published.status()).isEqualTo("PUBLISHED");
    var replay = service.publish(created.eventId(), "organizer-a", approved.rowVersion());
    assertThat(replay.rowVersion()).isEqualTo(published.rowVersion());
    assertThat(
            jdbc.queryForObject(
                "SELECT count(*) FROM event_schema.tbl_outbox_messages", Integer.class))
        .isEqualTo(3);
    mvc.perform(get("/api/v1/events/" + created.eventId()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.ticketTypes[0].unitPrice").value("100005"))
        .andExpect(jsonPath("$.organizerSubject").doesNotExist())
        .andExpect(jsonPath("$.feeRate").doesNotExist());
    var sales =
        json.readTree(
            jdbc.queryForObject(
                "SELECT payload::text FROM event_schema.tbl_outbox_messages WHERE message_type='event.sales-configured.v1'",
                String.class));
    assertThat(sales.path("schemaVersion").intValue()).isEqualTo(1);
    assertThat(sales.path("sourceConfigVersion").stringValue())
        .isEqualTo(published.sourceConfigVersion());
    assertThat(sales.path("sectors").size()).isZero();
    assertThat(sales.path("seats").size()).isZero();
    assertThat(sales.path("ticketTypes").get(0).path("configuredQuantity").stringValue())
        .isEqualTo("10");
    assertThat(sales.path("promotions").get(0).path("maximumUsesPerUser").intValue()).isEqualTo(1);
  }

  @Test
  void editsAreOwnerOnlyAndCommercialFieldsFreezeOnSubmission() {
    var created = service.create("a", draft("Original"));
    assertThatThrownBy(
            () ->
                service.save(created.eventId(), "b", new EventCommands.Save("0", draft("Stolen"))))
        .isInstanceOf(EventProblem.class);
    service.submit(created.eventId(), "a", "0");
    assertThatThrownBy(
            () ->
                service.save(created.eventId(), "a", new EventCommands.Save("1", draft("Changed"))))
        .isInstanceOf(EventProblem.class);
    assertThat(jdbc.queryForObject("SELECT title FROM event_schema.tbl_events", String.class))
        .isEqualTo("Original");
  }

  @Test
  void returningToDraftAllowsResubmitWithoutRejectedEventState() {
    var created = service.create("a", draft("Original"));
    var pending = service.submit(created.eventId(), "a", "0");
    var returned = service.returnToDraft(created.eventId(), "admin", pending.rowVersion());
    assertThat(returned.status()).isEqualTo("DRAFT");
    var saved =
        service.save(
            created.eventId(),
            "a",
            new EventCommands.Save(returned.rowVersion(), draft("Revised")));
    assertThat(service.submit(created.eventId(), "a", saved.rowVersion()).status())
        .isEqualTo("PENDING_APPROVAL");
  }

  @Test
  void outboxFailureRollsBackApproval() {
    var created = service.create("a", draft("Original"));
    var pending = service.submit(created.eventId(), "a", "0");
    doThrow(
            new org.springframework.dao.DataAccessResourceFailureException(
                "simulated storage failure"))
        .when(outbox)
        .append(any(), any(), any());
    assertThatThrownBy(
            () ->
                service.approve(
                    created.eventId(),
                    "admin",
                    new EventCommands.Approval(pending.rowVersion(), "0.1")))
        .isInstanceOf(org.springframework.dao.DataAccessResourceFailureException.class);
    assertThat(jdbc.queryForObject("SELECT status FROM event_schema.tbl_events", String.class))
        .isEqualTo("PENDING_APPROVAL");
    assertThat(
            jdbc.queryForObject(
                "SELECT fee_rate FROM event_schema.tbl_events", java.math.BigDecimal.class))
        .isNull();
    assertThat(
            jdbc.queryForObject(
                "SELECT count(*) FROM event_schema.tbl_outbox_messages", Integer.class))
        .isZero();
  }

  @Test
  void concurrentStaleEditsHaveOnlyOneWinner() throws Exception {
    var created = service.create("a", draft("Original"));
    var gate = new CountDownLatch(1);
    try (var executor = Executors.newFixedThreadPool(2)) {
      List<Future<Boolean>> attempts = new ArrayList<>();
      for (String title : List.of("First", "Second"))
        attempts.add(
            executor.submit(
                () -> {
                  gate.await();
                  try {
                    service.save(created.eventId(), "a", new EventCommands.Save("0", draft(title)));
                    return true;
                  } catch (EventProblem problem) {
                    assertThat(problem.code()).isEqualTo("STALE_EVENT_VERSION");
                    return false;
                  }
                }));
      gate.countDown();
      int wins = 0;
      for (var attempt : attempts) if (attempt.get(15, TimeUnit.SECONDS)) wins++;
      assertThat(wins).isEqualTo(1);
    }
    assertThat(jdbc.queryForObject("SELECT row_version FROM event_schema.tbl_events", Long.class))
        .isEqualTo(1L);
    assertThat(
            jdbc.queryForObject(
                "SELECT count(*) FROM event_schema.tbl_ticket_types", Integer.class))
        .isEqualTo(1);
  }

  @Test
  void invalidReferenceCannotLeavePartialDraft() {
    var valid = draft("Bad");
    var invalid =
        new EventCommands.Draft(
            valid.title(),
            valid.description(),
            UUID.randomUUID(),
            valid.venueId(),
            valid.mode(),
            valid.startsAt(),
            valid.endsAt(),
            valid.saleStartsAt(),
            valid.saleEndsAt(),
            valid.purchaseLimit(),
            valid.images(),
            valid.ticketTypes(),
            valid.promotions());
    assertThatThrownBy(() -> service.create("a", invalid)).isInstanceOf(EventProblem.class);
    assertThat(jdbc.queryForObject("SELECT count(*) FROM event_schema.tbl_events", Integer.class))
        .isZero();
  }

  @Test
  void publicAndOwnerListsFilterBeforePagination() throws Exception {
    service.create("other", draft("Other"));
    service.create("a", draft("My draft"));
    mvc.perform(get("/api/v1/organizer/events").with(actor("a", "ORGANIZER")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.items.length()").value(1))
        .andExpect(jsonPath("$.items[0].title").value("My draft"));
    mvc.perform(get("/api/v1/events"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.items.length()").value(0));
    mvc.perform(get("/api/v1/admin/events").with(actor("a", "ORGANIZER")))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
  }

  @Test
  void strictJsonRejectsNumbersForMoneyAndUnknownFields() throws Exception {
    String request = json.writeValueAsString(draft("Strict"));
    mvc.perform(
            post("/api/v1/organizer/events")
                .with(actor("a", "ORGANIZER"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(request.replace("\"100005\"", "100005")))
        .andExpect(status().isBadRequest());
    mvc.perform(
            post("/api/v1/organizer/events")
                .with(actor("a", "ORGANIZER"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    request.substring(0, request.length() - 1)
                        + ",\"organizerSubject\":\"other\"}"))
        .andExpect(status().isBadRequest());
  }

  @Test
  void draftReplacementPreservesExistingChildIds() {
    var created = service.create("a", draft("Original"));
    var input = draft("Updated");
    var type = created.ticketTypes().getFirst();
    var image = created.images().getFirst();
    var promotion = created.promotions().getFirst();
    var replacement =
        new EventCommands.Draft(
            input.title(),
            input.description(),
            input.categoryId(),
            input.venueId(),
            input.mode(),
            input.startsAt(),
            input.endsAt(),
            input.saleStartsAt(),
            input.saleEndsAt(),
            input.purchaseLimit(),
            List.of(
                new EventCommands.Image(
                    image.imageId(), image.objectReference(), image.displayOrder())),
            List.of(
                new EventCommands.TicketType(
                    type.ticketTypeId(),
                    type.name(),
                    type.unitPrice(),
                    type.currency(),
                    type.configuredQuantity(),
                    type.colorCode())),
            List.of(
                new EventCommands.Promotion(
                    promotion.promotionId(),
                    promotion.code(),
                    promotion.startsAt().toString(),
                    promotion.endsAt().toString(),
                    promotion.maximumUses(),
                    new EventCommands.Discount(
                        promotion.discount().kind(),
                        promotion.discount().amount(),
                        promotion.discount().percent()))));
    var saved = service.save(created.eventId(), "a", new EventCommands.Save("0", replacement));
    assertThat(saved.ticketTypes().getFirst().ticketTypeId()).isEqualTo(type.ticketTypeId());
    assertThat(saved.images().getFirst().imageId()).isEqualTo(image.imageId());
    assertThat(saved.promotions().getFirst().promotionId()).isEqualTo(promotion.promotionId());
  }

  @Test
  void failureAfterChildDeletesRestoresWholeDraft() throws Exception {
    var created = service.create("a", draft("Original"));
    try (var connection =
            DriverManager.getConnection(
                POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
        var statement = connection.createStatement()) {
      statement.execute(
          "CREATE FUNCTION event_schema.test_reject_insert() RETURNS trigger LANGUAGE plpgsql AS $$ BEGIN RAISE EXCEPTION 'Injected test fault'; END $$");
      statement.execute(
          "CREATE TRIGGER test_fault BEFORE INSERT ON event_schema.tbl_promotions FOR EACH ROW EXECUTE FUNCTION event_schema.test_reject_insert()");
      try {
        assertThatThrownBy(
                () ->
                    service.save(
                        created.eventId(),
                        "a",
                        new EventCommands.Save("0", draft("Must rollback"))))
            .isInstanceOf(org.springframework.dao.DataAccessException.class);
      } finally {
        statement.execute("DROP TRIGGER test_fault ON event_schema.tbl_promotions");
        statement.execute("DROP FUNCTION event_schema.test_reject_insert()");
      }
    }
    var current = service.readOwned(created.eventId(), "a");
    assertThat(current.title()).isEqualTo("Original");
    assertThat(current.rowVersion()).isEqualTo("0");
    assertThat(current.ticketTypes().getFirst().ticketTypeId())
        .isEqualTo(created.ticketTypes().getFirst().ticketTypeId());
    assertThat(current.promotions()).hasSize(1);
  }

  @Test
  void ownerCursorHasNoDuplicatesAndDoesNotLeakOtherOwners() throws Exception {
    service.create("other", draft("Foreign"));
    for (int i = 0; i < 3; i++) service.create("a", draft("Mine " + i));
    var first = service.listOwner("a", null, 2, null);
    var second = service.listOwner("a", null, 2, first.nextCursor());
    assertThat(first.items()).hasSize(2);
    assertThat(second.items()).hasSize(1);
    assertThat(second.nextCursor()).isNull();
    var ids = new HashSet<UUID>();
    first.items().forEach(e -> ids.add(e.eventId()));
    second.items().forEach(e -> ids.add(e.eventId()));
    assertThat(ids).hasSize(3);
    assertThatThrownBy(() -> service.listOwner("a", null, 0, null))
        .isInstanceOf(EventProblem.class);
    assertThatThrownBy(() -> service.listOwner("a", null, 20, "invalid"))
        .isInstanceOf(EventProblem.class);
  }

  @Test
  void concurrentPublishDoesNotDuplicateOutbox() throws Exception {
    var created = service.create("a", draft("Concurrent"));
    var submitted = service.submit(created.eventId(), "a", "0");
    var approved =
        service.approve(
            created.eventId(), "admin", new EventCommands.Approval(submitted.rowVersion(), "0.1"));
    var gate = new CountDownLatch(1);
    try (var executor = Executors.newFixedThreadPool(2)) {
      var one =
          executor.submit(
              () -> {
                gate.await();
                return service.publish(created.eventId(), "a", approved.rowVersion());
              });
      var two =
          executor.submit(
              () -> {
                gate.await();
                return service.publish(created.eventId(), "a", approved.rowVersion());
              });
      gate.countDown();
      assertThat(one.get(15, TimeUnit.SECONDS).rowVersion())
          .isEqualTo(two.get(15, TimeUnit.SECONDS).rowVersion());
    }
    assertThat(
            jdbc.queryForObject(
                "SELECT count(*) FROM event_schema.tbl_outbox_messages", Integer.class))
        .isEqualTo(3);
  }

  @Test
  void expiredLeaseFencesOlderWorkerAndPreservesMessageIdentity() {
    var created = service.create("a", draft("Leasing"));
    var submitted = service.submit(created.eventId(), "a", "0");
    service.approve(
        created.eventId(), "admin", new EventCommands.Approval(submitted.rowVersion(), "0.1"));
    var first = outbox.claim(java.time.Duration.ofSeconds(30)).getFirst();
    assertThat(outbox.claim(java.time.Duration.ofSeconds(30))).isEmpty();
    jdbc.update(
        "UPDATE event_schema.tbl_outbox_messages SET next_attempt_at=clock_timestamp()-interval '1 second' WHERE message_id=?",
        first.id());
    var second = outbox.claim(java.time.Duration.ofSeconds(30)).getFirst();
    assertThat(second.id()).isEqualTo(first.id());
    assertThat(second.payload()).isEqualTo(first.payload());
    assertThat(second.attempt()).isEqualTo(2);
    assertThat(outbox.confirmed(first)).isFalse();
    assertThat(outbox.confirmed(second)).isTrue();
    assertThat(outbox.claim(java.time.Duration.ofSeconds(30))).isEmpty();
  }

  @Test
  void savingAndSubmittingUseTheSameParentLock() throws Exception {
    var created = service.create("a", draft("Original"));
    var gate = new CountDownLatch(1);
    try (var executor = Executors.newFixedThreadPool(2)) {
      var save =
          executor.submit(
              () -> {
                gate.await();
                try {
                  service.save(
                      created.eventId(), "a", new EventCommands.Save("0", draft("Revised")));
                  return true;
                } catch (EventProblem ex) {
                  return false;
                }
              });
      var submit =
          executor.submit(
              () -> {
                gate.await();
                try {
                  service.submit(created.eventId(), "a", "0");
                  return true;
                } catch (EventProblem ex) {
                  return false;
                }
              });
      gate.countDown();
      assertThat(save.get(15, TimeUnit.SECONDS) ^ submit.get(15, TimeUnit.SECONDS)).isTrue();
    }
    assertThat(jdbc.queryForObject("SELECT row_version FROM event_schema.tbl_events", Long.class))
        .isEqualTo(1L);
  }
}
