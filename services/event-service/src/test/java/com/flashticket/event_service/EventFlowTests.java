package com.flashticket.event_service;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.flashticket.event_service.api.EventCommands;
import com.flashticket.event_service.application.EventService;
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
  void hiddenUnsupportedModeDoesNotRevealDraftToPublicOrForeignOwner() throws Exception {
    var event = service.create("a", draft("Hidden seat map"));
    jdbc.update("DELETE FROM event_schema.tbl_ticket_types WHERE event_id=?", event.eventId());
    jdbc.update(
        "UPDATE event_schema.tbl_events SET sales_mode='SEAT_MAP' WHERE id=?", event.eventId());
    mvc.perform(get("/api/v1/events/" + event.eventId())).andExpect(status().isNotFound());
    mvc.perform(get("/api/v1/organizer/events/" + event.eventId()).with(actor("b", "ORGANIZER")))
        .andExpect(status().isNotFound());
    mvc.perform(get("/api/v1/organizer/events/" + event.eventId()).with(actor("a", "ORGANIZER")))
        .andExpect(status().isUnprocessableEntity())
        .andExpect(jsonPath("$.code").value("SALES_MODE_NOT_IMPLEMENTED"));
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
