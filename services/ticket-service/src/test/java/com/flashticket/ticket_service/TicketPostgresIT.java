package com.flashticket.ticket_service;

import static org.assertj.core.api.Assertions.*;

import com.flashticket.ticket_service.application.TicketService;
import com.flashticket.ticket_service.contract.TicketContracts;
import com.flashticket.ticket_service.contract.TicketContracts.*;
import com.flashticket.ticket_service.qr.QrTokens;
import com.flashticket.ticket_service.web.TicketProblem;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.core.io.FileSystemResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.JdbcTransactionManager;
import tools.jackson.databind.json.JsonMapper;

/** Explicitly opt-in; the harness creates/drops a dedicated database, never ticket_db. */
class TicketPostgresIT {
  static JdbcTemplate jdbc;
  static com.zaxxer.hikari.HikariDataSource pool;
  static final JsonMapper JSON = JsonMapper.builder().build();
  @TempDir Path temp;
  TicketService service;
  UUID event, order, charge;
  String buyer = "buyer-test", organizer = "organizer-test";

  @BeforeAll
  static void database() throws Exception {
    String url = System.getenv("TICKET_TEST_DB_URL");
    if (url == null
        || !url.matches("jdbc:postgresql://127\\.0\\.0\\.1:[0-9]+/ticket_test_[a-f0-9]+"))
      throw new IllegalStateException("Dedicated test DB required");
    var ds = new com.zaxxer.hikari.HikariDataSource();
    pool = ds;
    ds.setJdbcUrl(url);
    ds.setUsername(System.getenv("TICKET_TEST_DB_USER"));
    ds.setPassword(System.getenv("TICKET_TEST_DB_PASSWORD"));
    ds.setMaximumPoolSize(5);
    ds.setMinimumIdle(1);
    ds.setConnectionTimeout(15000);
    ds.addDataSourceProperty("connectTimeout", "10");
    ds.addDataSourceProperty("socketTimeout", "20");
    jdbc = new JdbcTemplate(ds);
    jdbc.execute("CREATE SCHEMA IF NOT EXISTS ticket_schema");
    String ddl = Files.readString(Path.of("../../docs/architecture/data/postgres/40-ticket.sql"));
    ddl =
        ddl.replaceAll("(?m)^\\\\.*$", "")
            .replace("BEGIN;", "")
            .replace("COMMIT;", "")
            .replace("SET LOCAL ROLE ticket_owner;", "")
            .replace(
                "SET LOCAL search_path = ticket_schema, pg_catalog;",
                "SET search_path = ticket_schema, pg_catalog;");
    for (String table :
        List.of(
            "event_access_snapshots",
            "issuances",
            "tickets",
            "ticket_deliveries",
            "checkin_attempts",
            "outbox_messages",
            "inbox_messages")) ddl = ddl.replaceAll("\\b" + table + "\\b", "tbl_" + table);
    try (var connection = ds.getConnection()) {
      org.springframework.jdbc.datasource.init.ScriptUtils.executeSqlScript(
          connection,
          new org.springframework.core.io.ByteArrayResource(
              ddl.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
    }
  }

  @AfterAll
  static void closePool() {
    if (pool != null) pool.close();
  }

  @BeforeEach
  void setup() throws Exception {
    jdbc.execute(
        "TRUNCATE ticket_schema.tbl_checkin_attempts,ticket_schema.tbl_ticket_deliveries,ticket_schema.tbl_tickets,ticket_schema.tbl_issuances,ticket_schema.tbl_event_access_snapshots,ticket_schema.tbl_inbox_messages,ticket_schema.tbl_outbox_messages CASCADE");
    var ring = temp.resolve("ring.json");
    Files.writeString(
        ring, "{\"keys\":{\"v1\":\"" + Base64.getEncoder().encodeToString(new byte[32]) + "\"}}");
    service =
        new TicketService(
            jdbc,
            JSON,
            new JdbcTransactionManager(jdbc.getDataSource()),
            new QrTokens(new FileSystemResource(ring), "v1"));
    event = UUID.randomUUID();
    order = UUID.randomUUID();
    charge = UUID.randomUUID();
    service.access(access("PUBLISHED", "1"));
  }

  Envelope<AccessConfiguration> access(String state, String version) {
    return new Envelope<>(
        UUID.randomUUID(),
        TicketContracts.ACCESS,
        1,
        event,
        version,
        Instant.now(),
        event,
        null,
        null,
        new AccessConfiguration(
            event,
            organizer,
            state,
            Instant.now().minusSeconds(600),
            Instant.now().plusSeconds(600),
            version));
  }

  Envelope<IssueTickets> issue() {
    var item =
        new OrderItem(
            UUID.randomUUID(), UUID.randomUUID(), "General", 2, "100", "200", null, List.of());
    var purchase =
        new PurchaseSnapshot(
            1,
            order,
            event,
            buyer,
            "buyer@example.test",
            "1",
            "2",
            Instant.now().minusSeconds(30),
            Instant.now().plusSeconds(60),
            "VND",
            "Fixture",
            Instant.now().minusSeconds(600),
            Instant.now().plusSeconds(600),
            null,
            "QUANTITY",
            null,
            "200",
            "0",
            "200",
            null,
            List.of(item));
    return new Envelope<>(
        UUID.randomUUID(),
        TicketContracts.ISSUE,
        1,
        order,
        "2",
        Instant.now(),
        order,
        null,
        null,
        new IssueTickets(order, charge, purchase));
  }

  Envelope<EventCancellation> cancellation() {
    var a = access("CANCELLED", "3").data();
    var sales =
        JSON.createObjectNode()
            .put("schemaVersion", 1)
            .put("eventId", event.toString())
            .put("sourceConfigVersion", "3")
            .put("eventStatus", "CANCELLED");
    return new Envelope<>(
        UUID.randomUUID(),
        TicketContracts.CANCELLED,
        1,
        event,
        "3",
        Instant.now(),
        event,
        null,
        null,
        new EventCancellation(event, UUID.randomUUID(), "3", Instant.now(), organizer, sales, a));
  }

  @Test
  void issueIsAtomicIdempotentAndOwnershipProtected() {
    var command = issue();
    service.issue(command);
    service.issue(command);
    assertThat(jdbc.queryForObject("SELECT count(*) FROM ticket_schema.tbl_tickets", Integer.class))
        .isEqualTo(2);
    assertThat(service.listTickets(order, buyer).tickets()).hasSize(2);
    assertThatThrownBy(() -> service.listTickets(order, "other")).isInstanceOf(TicketProblem.class);
    UUID ticket = service.listTickets(order, buyer).tickets().getFirst().ticketId();
    assertThat(service.qr(ticket, buyer)).hasSize(43);
    assertThat(service.qr(ticket, buyer)).isEqualTo(service.qr(ticket, buyer));
    assertThat(
            jdbc.queryForObject(
                "SELECT count(*) FROM ticket_schema.tbl_outbox_messages", Integer.class))
        .isEqualTo(1);
    assertThat(
            jdbc.queryForObject(
                "SELECT count(*) FROM ticket_schema.tbl_ticket_deliveries", Integer.class))
        .isEqualTo(1);
  }

  @Test
  void inputConflictAndSameMessageDifferentHashAreRejected() {
    var original = issue();
    service.issue(original);
    var changed =
        new Envelope<>(
            original.messageId(),
            original.messageType(),
            1,
            order,
            "2",
            original.occurredAt(),
            order,
            null,
            null,
            new IssueTickets(order, UUID.randomUUID(), original.data().purchase()));
    assertThatThrownBy(() -> service.issue(changed))
        .isInstanceOf(TicketContracts.InvalidContractException.class);
    assertThat(jdbc.queryForObject("SELECT count(*) FROM ticket_schema.tbl_tickets", Integer.class))
        .isEqualTo(2);
  }

  @Test
  void missingKeysRollbackAllTicketsAndAllowRetry() throws Exception {
    var command = issue();
    Files.delete(temp.resolve("ring.json"));
    assertThatThrownBy(() -> service.issue(command))
        .isInstanceOf(com.flashticket.ticket_service.qr.QrUnavailableException.class);
    assertThat(
            jdbc.queryForObject("SELECT count(*) FROM ticket_schema.tbl_issuances", Integer.class))
        .isZero();
    Files.writeString(
        temp.resolve("ring.json"),
        "{\"keys\":{\"v1\":\"" + Base64.getEncoder().encodeToString(new byte[32]) + "\"}}");
    service.issue(command);
    assertThat(service.listTickets(order, buyer).tickets()).hasSize(2);
  }

  @Test
  void checkInHasOneSuccessAndEveryReceiptIsAuditedIncludingReplay() {
    service.issue(issue());
    UUID ticket = service.listTickets(order, buyer).tickets().getFirst().ticketId();
    String token = service.qr(ticket, buyer);
    UUID key = UUID.randomUUID();
    var first = service.checkIn(event, organizer, key, token, order);
    assertThat(first.succeeded()).isTrue();
    assertThat(service.checkIn(event, organizer, key, token, order)).isEqualTo(first);
    assertThat(service.checkIn(event, organizer, UUID.randomUUID(), token, order).result())
        .isEqualTo("ALREADY_USED");
    assertThat(
            jdbc.queryForObject(
                "SELECT count(*) FROM ticket_schema.tbl_checkin_attempts", Integer.class))
        .isEqualTo(3);
    assertThat(
            jdbc.queryForObject(
                "SELECT count(*) FROM ticket_schema.tbl_checkin_attempts WHERE succeeded",
                Integer.class))
        .isEqualTo(1);
  }

  @Test
  void concurrentScansAndIssuanceRetriesDoNotDuplicateRights() throws Exception {
    var command = issue();
    try (var executor = Executors.newFixedThreadPool(4)) {
      var calls = new ArrayList<Callable<Void>>();
      for (int i = 0; i < 4; i++)
        calls.add(
            () -> {
              service.issue(command);
              return null;
            });
      for (var future : executor.invokeAll(calls)) future.get();
      UUID ticket = service.listTickets(order, buyer).tickets().getFirst().ticketId();
      String token = service.qr(ticket, buyer);
      var scans = new ArrayList<Callable<Boolean>>();
      for (int i = 0; i < 4; i++)
        scans.add(
            () -> service.checkIn(event, organizer, UUID.randomUUID(), token, order).succeeded());
      int successes = 0;
      for (var future : executor.invokeAll(scans)) if (future.get()) successes++;
      assertThat(successes).isEqualTo(1);
    }
    assertThat(jdbc.queryForObject("SELECT count(*) FROM ticket_schema.tbl_tickets", Integer.class))
        .isEqualTo(2);
  }

  @Test
  void cancellationClosesGateBeforeVoidBatchAndNeverReopensFromOldConfig() {
    service.issue(issue());
    UUID ticket = service.listTickets(order, buyer).tickets().getFirst().ticketId();
    String token = service.qr(ticket, buyer);
    service.cancel(cancellation());
    service.access(access("PUBLISHED", "2"));
    assertThat(service.checkIn(event, organizer, UUID.randomUUID(), token, order).result())
        .isEqualTo("EVENT_CANCELLED");
    assertThatThrownBy(() -> service.qr(ticket, buyer)).isInstanceOf(TicketProblem.class);
    service.voidCancelled(20);
    assertThat(
            jdbc.queryForObject(
                "SELECT count(*) FROM ticket_schema.tbl_tickets WHERE state='VOID'", Integer.class))
        .isEqualTo(2);
  }

  @Test
  void cancelledEventProducesTerminalFailureWithoutTickets() {
    service.cancel(cancellation());
    service.issue(issue());
    assertThat(service.listTickets(order, buyer).issuanceState()).isEqualTo("FAILED");
    assertThat(jdbc.queryForObject("SELECT count(*) FROM ticket_schema.tbl_tickets", Integer.class))
        .isZero();
    assertThat(
            jdbc.queryForObject(
                "SELECT payload->>'failureCode' FROM ticket_schema.tbl_outbox_messages",
                String.class))
        .isEqualTo("EVENT_CANCELLED");
  }

  @Test
  void redeliveryUsesFrozenEmailAndReusesJobForSameKey() {
    service.issue(issue());
    UUID key = UUID.randomUUID();
    assertThat(service.redeliver(order, buyer, key))
        .isEqualTo(service.redeliver(order, buyer, key));
    assertThat(
            jdbc.queryForObject(
                "SELECT count(*) FROM ticket_schema.tbl_ticket_deliveries", Integer.class))
        .isEqualTo(2);
    assertThatThrownBy(() -> service.redeliver(order, "other", key))
        .isInstanceOf(TicketProblem.class);
  }

  @Test
  void resendReceiptRemainsIdempotentAfterCancellation() {
    service.issue(issue());
    UUID key = UUID.randomUUID();
    var receipt = service.redeliver(order, buyer, key);
    service.cancel(cancellation());
    assertThat(service.redeliver(order, buyer, key)).isEqualTo(receipt);
    assertThatThrownBy(() -> service.redeliver(order, buyer, UUID.randomUUID()))
        .isInstanceOf(TicketProblem.class);
  }

  @Test
  void failureAfterFirstTicketRollsBackEntireIssuance() {
    var tokens =
        org.mockito.Mockito.spy(
            new QrTokens(new FileSystemResource(temp.resolve("ring.json")), "v1"));
    var calls = new java.util.concurrent.atomic.AtomicInteger();
    org.mockito.Mockito.doAnswer(
            call -> {
              if (calls.incrementAndGet() == 2)
                throw new com.flashticket.ticket_service.qr.QrUnavailableException();
              return call.callRealMethod();
            })
        .when(tokens)
        .issue(org.mockito.ArgumentMatchers.any(UUID.class));
    var failing =
        new TicketService(jdbc, JSON, new JdbcTransactionManager(jdbc.getDataSource()), tokens);
    assertThatThrownBy(() -> failing.issue(issue()))
        .isInstanceOf(com.flashticket.ticket_service.qr.QrUnavailableException.class);
    for (String table :
        List.of("tbl_issuances", "tbl_tickets", "tbl_ticket_deliveries", "tbl_outbox_messages"))
      assertThat(jdbc.queryForObject("SELECT count(*) FROM ticket_schema." + table, Integer.class))
          .isZero();
  }

  @Test
  void seatMapSeatedAndStandingUseFrozenSeatsAndRetryPreservesRights() {
    var original = issue();
    var p = original.data().purchase();
    UUID sector = UUID.randomUUID();
    var item = p.items().getFirst();
    var seats =
        List.of(
            new Seat(UUID.randomUUID(), "A1", "A", "1"),
            new Seat(UUID.randomUUID(), "A2", "A", "2"));
    var seatedItem =
        new OrderItem(
            item.orderItemId(),
            item.ticketTypeId(),
            item.ticketTypeName(),
            2,
            "100",
            "200",
            sector,
            seats);
    var seated =
        new PurchaseSnapshot(
            1,
            order,
            event,
            buyer,
            p.customerEmail(),
            "1",
            "2",
            p.frozenAt(),
            p.expiresAt(),
            "VND",
            p.eventTitle(),
            p.eventStartsAt(),
            p.eventEndsAt(),
            null,
            "SEAT_MAP",
            sector,
            "200",
            "0",
            "200",
            null,
            List.of(seatedItem));
    var command =
        new Envelope<>(
            UUID.randomUUID(),
            TicketContracts.ISSUE,
            1,
            order,
            "2",
            Instant.now(),
            order,
            null,
            null,
            new IssueTickets(order, charge, seated));
    service.issue(command);
    service.issue(
        new Envelope<>(
            UUID.randomUUID(),
            command.messageType(),
            1,
            order,
            "2",
            Instant.now(),
            order,
            command.messageId(),
            null,
            command.data()));
    assertThat(
            jdbc.queryForList(
                "SELECT seat_id FROM ticket_schema.tbl_tickets ORDER BY ordinal", UUID.class))
        .containsExactly(seats.get(0).seatId(), seats.get(1).seatId());
    assertThat(jdbc.queryForObject("SELECT count(*) FROM ticket_schema.tbl_tickets", Integer.class))
        .isEqualTo(2);
    assertThat(
            jdbc.queryForObject(
                "SELECT count(*) FROM ticket_schema.tbl_outbox_messages", Integer.class))
        .isEqualTo(2);
    UUID standingOrder = UUID.randomUUID();
    var standingItem =
        new OrderItem(
            UUID.randomUUID(), UUID.randomUUID(), "Standing", 2, "100", "200", sector, List.of());
    var standing =
        new PurchaseSnapshot(
            1,
            standingOrder,
            event,
            buyer,
            p.customerEmail(),
            "1",
            "2",
            p.frozenAt(),
            p.expiresAt(),
            "VND",
            p.eventTitle(),
            p.eventStartsAt(),
            p.eventEndsAt(),
            null,
            "SEAT_MAP",
            sector,
            "200",
            "0",
            "200",
            null,
            List.of(standingItem));
    service.issue(
        new Envelope<>(
            UUID.randomUUID(),
            TicketContracts.ISSUE,
            1,
            standingOrder,
            "2",
            Instant.now(),
            standingOrder,
            null,
            null,
            new IssueTickets(standingOrder, UUID.randomUUID(), standing)));
    assertThat(
            jdbc.queryForObject(
                "SELECT count(*) FROM ticket_schema.tbl_tickets WHERE seat_id IS NULL AND sector_id=?",
                Integer.class,
                sector))
        .isEqualTo(2);
  }

  @Test
  void missingAccessConfigurationRemainsRetryableWithoutRecordingSuccess() {
    var command = issue();
    jdbc.update("DELETE FROM ticket_schema.tbl_event_access_snapshots WHERE event_id=?", event);
    assertThatThrownBy(() -> service.issue(command))
        .isInstanceOfSatisfying(
            TicketProblem.class, error -> assertThat(error.status()).isEqualTo(503));
    assertThat(
            jdbc.queryForObject("SELECT count(*) FROM ticket_schema.tbl_issuances", Integer.class))
        .isZero();
    service.access(access("PUBLISHED", "2"));
    service.issue(command);
    assertThat(service.listTickets(order, buyer).tickets()).hasSize(2);
  }

  @Test
  void rejectedScansAndConflictingReplayAreAuditedWithoutRawTokens() {
    service.issue(issue());
    String token =
        service.qr(service.listTickets(order, buyer).tickets().getFirst().ticketId(), buyer);
    assertThat(service.checkIn(event, "other-organizer", UUID.randomUUID(), token, order).result())
        .isEqualTo("NOT_EVENT_OWNER");
    UUID key = UUID.randomUUID();
    assertThat(service.checkIn(event, organizer, key, "malformed-token", order).result())
        .isEqualTo("UNKNOWN_QR");
    assertThatThrownBy(() -> service.checkIn(event, organizer, key, token, order))
        .isInstanceOfSatisfying(
            TicketProblem.class,
            error -> assertThat(error.code()).isEqualTo("IDEMPOTENCY_CONFLICT"));
    assertThat(
            jdbc.queryForObject(
                "SELECT count(*) FROM ticket_schema.tbl_checkin_attempts", Integer.class))
        .isEqualTo(3);
    assertThat(
            jdbc.queryForList(
                "SELECT request_hash FROM ticket_schema.tbl_checkin_attempts", String.class))
        .allMatch(hash -> hash.matches("[a-f0-9]{64}"));
  }

  @Test
  void aScanWaitingForTicketLockCannotSucceedAfterWindowEnds() throws Exception {
    service.issue(issue());
    UUID id = service.listTickets(order, buyer).tickets().getFirst().ticketId();
    String token = service.qr(id, buyer);
    var deadline =
        jdbc.queryForObject(
                "SELECT clock_timestamp()+interval '15 seconds'", java.sql.Timestamp.class)
            .toInstant();
    jdbc.update(
        "UPDATE ticket_schema.tbl_event_access_snapshots SET event_ends_at=? WHERE event_id=?",
        java.sql.Timestamp.from(deadline),
        event);
    try (var lock = jdbc.getDataSource().getConnection();
        var executor = Executors.newSingleThreadExecutor()) {
      lock.setAutoCommit(false);
      try (var statement =
          lock.prepareStatement("SELECT id FROM ticket_schema.tbl_tickets WHERE id=? FOR UPDATE")) {
        statement.setObject(1, id);
        statement.executeQuery().close();
      }
      var pending =
          executor.submit(() -> service.checkIn(event, organizer, UUID.randomUUID(), token, order));
      boolean waiting = false;
      long waitUntil = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
      while (System.nanoTime() < waitUntil) {
        Integer blocked =
            jdbc.queryForObject(
                "SELECT count(*) FROM pg_stat_activity WHERE datname=current_database() AND wait_event_type='Lock' AND query LIKE '%FOR SHARE OF i FOR UPDATE OF t%'",
                Integer.class);
        if (blocked != null && blocked > 0) {
          waiting = true;
          break;
        }
        Thread.sleep(20);
      }
      if (!waiting) {
        lock.commit();
        throw new AssertionError("Scanner did not reach ticket lock");
      }
      // The deadline belongs to PostgreSQL; a developer laptop clock is not its authority.
      while (!Boolean.TRUE.equals(
          jdbc.queryForObject(
              "SELECT clock_timestamp() >= ?", Boolean.class, java.sql.Timestamp.from(deadline))))
        Thread.sleep(50);
      lock.commit();
      var result = pending.get(10, TimeUnit.SECONDS);
      assertThat(result.processedAt()).isAfterOrEqualTo(deadline);
      assertThat(result.result()).isEqualTo("OUTSIDE_WINDOW");
    }
  }

  @Test
  void outboxConfirmsAndReturnsControlPublicationAndRetryKeepsMessageId() {
    service.issue(issue());
    var rabbit = new RecordingRabbit();
    var publisher =
        new com.flashticket.ticket_service.worker.TicketOutboxPublisher(
            jdbc,
            JSON,
            new JdbcTransactionManager(jdbc.getDataSource()),
            rabbit,
            java.time.Duration.ofSeconds(1),
            java.time.Duration.ofSeconds(30),
            "ticket.issuance-result.v1");
    rabbit.ack = false;
    assertThat(publisher.publishBatch(1)).isZero();
    assertThat(
            jdbc.queryForObject(
                "SELECT count(*) FROM ticket_schema.tbl_outbox_messages WHERE published_at IS NOT NULL",
                Integer.class))
        .isZero();
    jdbc.update("UPDATE ticket_schema.tbl_outbox_messages SET next_attempt_at=clock_timestamp()");
    rabbit.ack = true;
    assertThat(publisher.publishBatch(1)).isEqualTo(1);
    assertThat(rabbit.sent).hasSize(2);
    assertThat(rabbit.sent.get(0).getMessageProperties().getMessageId())
        .isEqualTo(rabbit.sent.get(1).getMessageProperties().getMessageId());
    var envelope = JSON.readTree(rabbit.sent.get(1).getBody());
    assertThat(envelope.path("aggregateVersion").asText()).isEqualTo("2");
    assertThat(envelope.path("data").path("ticketCount").intValue()).isEqualTo(2);
    jdbc.update(
        "UPDATE ticket_schema.tbl_outbox_messages SET published_at=NULL,next_attempt_at=clock_timestamp()");
    rabbit.returned = true;
    assertThat(publisher.publishBatch(1)).isZero();
    assertThat(
            jdbc.queryForObject(
                "SELECT last_error_code FROM ticket_schema.tbl_outbox_messages", String.class))
        .isEqualTo("UNROUTABLE");
  }

  @Test
  void smtpFailureRetriesSameJobWithoutChangingTicketsOrExposingQr() {
    service.issue(issue());
    var mail = new RecordingMail();
    mail.fail = true;
    var worker =
        new com.flashticket.ticket_service.worker.TicketDeliveryWorker(
            jdbc,
            new JdbcTransactionManager(jdbc.getDataSource()),
            mail,
            "https://buyer.example.test",
            "tickets@example.test",
            java.time.Duration.ofSeconds(30));
    assertThat(worker.deliverBatch(1)).isZero();
    assertThat(
            jdbc.queryForObject(
                "SELECT count(*) FROM ticket_schema.tbl_ticket_deliveries WHERE finished_at IS NOT NULL",
                Integer.class))
        .isZero();
    jdbc.update("UPDATE ticket_schema.tbl_ticket_deliveries SET next_attempt_at=clock_timestamp()");
    mail.fail = false;
    assertThat(worker.deliverBatch(1)).isEqualTo(1);
    assertThat(mail.sent).hasSize(1);
    assertThat(mail.sent.getFirst().getTo()).containsExactly("buyer@example.test");
    assertThat(mail.sent.getFirst().getText())
        .contains("https://buyer.example.test/orders/" + order + "/tickets")
        .doesNotContain(
            service.qr(service.listTickets(order, buyer).tickets().getFirst().ticketId(), buyer));
    assertThat(jdbc.queryForObject("SELECT count(*) FROM ticket_schema.tbl_tickets", Integer.class))
        .isEqualTo(2);
    assertThat(
            jdbc.queryForObject(
                "SELECT count(*) FROM ticket_schema.tbl_outbox_messages", Integer.class))
        .isEqualTo(1);
  }

  @Test
  void cancellationSuppressesPendingEmailAndStaleLeaseCannotFinishAnotherAttempt() {
    service.issue(issue());
    var mail = new RecordingMail();
    var worker =
        new com.flashticket.ticket_service.worker.TicketDeliveryWorker(
            jdbc,
            new JdbcTransactionManager(jdbc.getDataSource()),
            mail,
            "https://buyer.example.test",
            "tickets@example.test",
            java.time.Duration.ofSeconds(30));
    mail.onSend =
        () -> jdbc.update("UPDATE ticket_schema.tbl_ticket_deliveries SET attempts=attempts+1");
    assertThat(worker.deliverBatch(1)).isZero();
    assertThat(
            jdbc.queryForObject(
                "SELECT count(*) FROM ticket_schema.tbl_ticket_deliveries WHERE finished_at IS NULL",
                Integer.class))
        .isEqualTo(1);
    service.cancel(cancellation());
    jdbc.update("UPDATE ticket_schema.tbl_ticket_deliveries SET next_attempt_at=clock_timestamp()");
    mail.sent.clear();
    mail.onSend = () -> {};
    worker.deliverBatch(1);
    assertThat(mail.sent).isEmpty();
    assertThat(
            jdbc.queryForObject(
                "SELECT outcome FROM ticket_schema.tbl_ticket_deliveries", String.class))
        .isEqualTo("NOT_SENT");
  }

  static final class RecordingRabbit extends org.springframework.amqp.rabbit.core.RabbitTemplate {
    boolean ack = true, returned = false;
    final List<org.springframework.amqp.core.Message> sent = new ArrayList<>();

    RecordingRabbit() {
      setExchange("business.ex");
    }

    @Override
    public void send(
        String exchange,
        String routing,
        org.springframework.amqp.core.Message message,
        org.springframework.amqp.rabbit.connection.CorrelationData correlation) {
      sent.add(message);
      if (returned)
        correlation.setReturned(
            new org.springframework.amqp.core.ReturnedMessage(
                message, 312, "NO_ROUTE", exchange, routing));
      correlation
          .getFuture()
          .complete(
              new org.springframework.amqp.rabbit.connection.CorrelationData.Confirm(
                  ack, ack ? null : "NACK"));
    }
  }

  static final class RecordingMail extends org.springframework.mail.javamail.JavaMailSenderImpl {
    boolean fail;
    Runnable onSend = () -> {};
    final List<org.springframework.mail.SimpleMailMessage> sent = new ArrayList<>();

    @Override
    public void send(org.springframework.mail.SimpleMailMessage message) {
      if (fail) throw new org.springframework.mail.MailSendException("sensitive SMTP fixture");
      sent.add(new org.springframework.mail.SimpleMailMessage(message));
      onSend.run();
    }
  }
}
