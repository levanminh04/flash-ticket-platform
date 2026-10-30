package com.flashticket.ticket_service.application;

import com.flashticket.ticket_service.contract.TicketContracts;
import com.flashticket.ticket_service.contract.TicketContracts.*;
import com.flashticket.ticket_service.qr.QrTokens;
import com.flashticket.ticket_service.web.*;
import com.flashticket.ticket_service.web.TicketViews.*;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.*;
import java.util.function.Supplier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.json.JsonMapper;

/** All business mutations are local ticket_schema transactions; no cross-owner queries. */
@Service
@ConditionalOnProperty(prefix = "ticket.features", name = "business-enabled", havingValue = "true")
public class TicketService implements TicketOperations {
  public static final String RESULT = "ticket.issuance-result.v1";
  private final JdbcTemplate jdbc;
  private final JsonMapper json;
  private final TransactionTemplate transactions;
  private final QrTokens qr;

  @edu.umd.cs.findbugs.annotations.SuppressFBWarnings(
      value = "EI_EXPOSE_REP2",
      justification = "Shares Spring-managed infrastructure; no dependency accessor is exposed.")
  public TicketService(
      JdbcTemplate jdbc, JsonMapper json, PlatformTransactionManager manager, QrTokens qr) {
    this.jdbc = jdbc;
    this.json = json;
    this.transactions = new TransactionTemplate(manager);
    this.qr = qr;
  }

  private <T> T tx(Supplier<T> work) {
    return transactions.execute(status -> work.get());
  }

  private Instant now() {
    Timestamp time = jdbc.queryForObject("SELECT clock_timestamp()", Timestamp.class);
    if (time == null) throw new TicketProblem(503, "CLOCK_UNAVAILABLE");
    return time.toInstant();
  }

  private void serialize(String key) {
    jdbc.query("SELECT pg_advisory_xact_lock(hashtextextended(?,0))", rs -> {}, key);
  }

  private static InvalidContractException conflict(String code) {
    return new InvalidContractException(code);
  }

  private static TicketProblem missing() {
    return new TicketProblem(404, "RESOURCE_NOT_FOUND");
  }

  private record Access(
      UUID id, String organizer, String state, Instant starts, Instant ends, long version) {}

  private record Issuance(
      UUID id,
      UUID order,
      UUID event,
      String buyer,
      String email,
      String state,
      String hash,
      int count,
      PurchaseSnapshot purchase) {}

  private record StoredTicket(
      UUID id,
      UUID issuance,
      UUID event,
      String holder,
      String state,
      String issuanceState,
      byte[] ciphertext,
      String keyVersion) {}

  private record Receipt(
      UUID id, UUID ticket, UUID event, String hash, String result, boolean success, Instant at) {}

  private record CheckOutcome(CheckInResult result, boolean conflict) {}

  public record IssuanceResult(
      UUID orderId,
      UUID chargeId,
      UUID issuanceId,
      String state,
      int ticketCount,
      String failureCode) {}

  public void access(Envelope<AccessConfiguration> message) {
    TicketContracts.validateAccess(message);
    tx(
        () -> {
          var a = message.data();
          boolean created = ensureEvent(a);
          Access current = event(a.eventId(), true);
          if (duplicate("ticket.access", message)) return null;
          long version = TicketContracts.version(a.sourceConfigVersion());
          if (!current.state().equals("CANCELLED") && version > current.version())
            updateEvent(a, false);
          else if (!created
              && version == current.version()
              && !current.state().equals("CANCELLED")) {
            // Compare with PostgreSQL timestamp precision, not Java nanoseconds after a JDBC round
            // trip.
            Integer equal =
                jdbc.queryForObject(
                    "SELECT count(*) FROM ticket_schema.tbl_event_access_snapshots WHERE event_id=? AND organizer_subject=? AND event_status=? AND event_starts_at=? AND event_ends_at=?",
                    Integer.class,
                    a.eventId(),
                    a.organizerSubject(),
                    a.eventStatus(),
                    Timestamp.from(a.eventStartsAt()),
                    Timestamp.from(a.eventEndsAt()));
            if (equal == null || equal != 1) throw conflict("SNAPSHOT_VERSION_CONFLICT");
          }
          inbox("ticket.access", message, null);
          return null;
        });
  }

  public void cancel(Envelope<EventCancellation> message) {
    TicketContracts.validateCancellation(message);
    tx(
        () -> {
          var a = message.data().access();
          ensureEvent(a);
          event(a.eventId(), true);
          if (duplicate("ticket.cancellation", message)) return null;
          updateEvent(a, true);
          inbox("ticket.cancellation", message, null);
          return null;
        });
  }

  private boolean ensureEvent(AccessConfiguration a) {
    return jdbc.update(
            "INSERT INTO ticket_schema.tbl_event_access_snapshots(event_id,organizer_subject,event_status,event_starts_at,event_ends_at,source_config_version,received_at) VALUES(?,?,?,?,?,?,clock_timestamp()) ON CONFLICT(event_id) DO NOTHING",
            a.eventId(),
            a.organizerSubject(),
            a.eventStatus(),
            Timestamp.from(a.eventStartsAt()),
            Timestamp.from(a.eventEndsAt()),
            TicketContracts.version(a.sourceConfigVersion()))
        == 1;
  }

  private void updateEvent(AccessConfiguration a, boolean cancellation) {
    jdbc.update(
        "UPDATE ticket_schema.tbl_event_access_snapshots SET organizer_subject=CASE WHEN source_config_version<=? THEN ? ELSE organizer_subject END,event_starts_at=CASE WHEN source_config_version<=? THEN ? ELSE event_starts_at END,event_ends_at=CASE WHEN source_config_version<=? THEN ? ELSE event_ends_at END,event_status=?,source_config_version=greatest(source_config_version,?),received_at=clock_timestamp() WHERE event_id=?",
        TicketContracts.version(a.sourceConfigVersion()),
        a.organizerSubject(),
        TicketContracts.version(a.sourceConfigVersion()),
        Timestamp.from(a.eventStartsAt()),
        TicketContracts.version(a.sourceConfigVersion()),
        Timestamp.from(a.eventEndsAt()),
        cancellation ? "CANCELLED" : a.eventStatus(),
        TicketContracts.version(a.sourceConfigVersion()),
        a.eventId());
  }

  private Access event(UUID id, boolean exclusive) {
    var rows =
        jdbc.query(
            "SELECT * FROM ticket_schema.tbl_event_access_snapshots WHERE event_id=?"
                + (exclusive ? " FOR UPDATE" : " FOR SHARE"),
            (r, n) ->
                new Access(
                    id,
                    r.getString("organizer_subject"),
                    r.getString("event_status"),
                    r.getTimestamp("event_starts_at").toInstant(),
                    r.getTimestamp("event_ends_at").toInstant(),
                    r.getLong("source_config_version")),
            id);
    return rows.isEmpty() ? null : rows.getFirst();
  }

  public void issue(Envelope<IssueTickets> message) {
    TicketContracts.validateIssue(message);
    tx(
        () -> {
          var command = message.data();
          var purchase = command.purchase();
          Access access = event(purchase.eventId(), false);
          if (access == null) throw new TicketProblem(503, "ACCESS_CONFIGURATION_PENDING");
          serialize("issuance:" + command.orderId());
          if (duplicate("ticket.issuance", message)) return null;
          String inputHash = TicketContracts.inputHash(json, command);
          Issuance issuance = issuance(command.orderId(), true);
          if (issuance != null && !issuance.hash().equals(inputHash))
            throw conflict("ISSUANCE_INPUT_CONFLICT");
          if (issuance == null) {
            UUID id = UUID.randomUUID();
            jdbc.update(
                "INSERT INTO ticket_schema.tbl_issuances(id,order_id,event_id,buyer_subject,payment_confirmation_id,customer_email,purchase_snapshot,input_hash,expected_ticket_count,state) VALUES(?,?,?,?,?,?,?::jsonb,?,?,'PENDING')",
                id,
                command.orderId(),
                purchase.eventId(),
                purchase.buyerSubject(),
                command.orderId().toString(),
                purchase.customerEmail(),
                json.writeValueAsString(purchase),
                inputHash,
                TicketContracts.ticketCount(purchase));
            issuance = issuance(command.orderId(), true);
          }
          IssuanceResult result;
          if (issuance.state().equals("PENDING")) {
            if (access.state().equals("CANCELLED")) {
              jdbc.update(
                  "UPDATE ticket_schema.tbl_issuances SET state='FAILED',failed_at=clock_timestamp(),failure_code='EVENT_CANCELLED',row_version=row_version+1 WHERE id=? AND state='PENDING'",
                  issuance.id());
              result =
                  new IssuanceResult(
                      command.orderId(),
                      command.chargeId(),
                      issuance.id(),
                      "FAILED",
                      0,
                      "EVENT_CANCELLED");
            } else {
              // All ticket inserts, completion, notification job, inbox and result outbox commit
              // together.
              for (var item : purchase.items())
                for (int ordinal = 1; ordinal <= item.quantity(); ordinal++) {
                  UUID id = UUID.randomUUID();
                  var sealed = qr.issue(id);
                  Seat seat = item.seats().isEmpty() ? null : item.seats().get(ordinal - 1);
                  var display = new LinkedHashMap<String, Object>();
                  display.put("schemaVersion", 1);
                  display.put("eventTitle", purchase.eventTitle());
                  display.put("eventStartsAt", purchase.eventStartsAt());
                  display.put("eventEndsAt", purchase.eventEndsAt());
                  display.put("venueName", purchase.venueName());
                  display.put("ticketTypeName", item.ticketTypeName());
                  display.put("seatLabel", seat == null ? null : seat.seatLabel());
                  display.put("rowName", seat == null ? null : seat.rowName());
                  display.put("seatNumber", seat == null ? null : seat.seatNumber());
                  jdbc.update(
                      "INSERT INTO ticket_schema.tbl_tickets(id,issuance_id,event_id,order_item_id,ordinal,holder_subject,ticket_type_id,sector_id,seat_id,display_snapshot,state,qr_token_ciphertext,qr_key_version,qr_token_hash) VALUES(?,?,?,?,?,?,?,?,?,?::jsonb,'VALID',?,?,?)",
                      id,
                      issuance.id(),
                      purchase.eventId(),
                      item.orderItemId(),
                      ordinal,
                      purchase.buyerSubject(),
                      item.ticketTypeId(),
                      item.sectorId(),
                      seat == null ? null : seat.seatId(),
                      json.writeValueAsString(display),
                      sealed.ciphertext(),
                      sealed.keyVersion(),
                      sealed.hash());
                }
              verifyCounts(issuance);
              jdbc.update(
                  "UPDATE ticket_schema.tbl_issuances SET state='COMPLETED',completed_at=clock_timestamp(),row_version=row_version+1 WHERE id=? AND state='PENDING'",
                  issuance.id());
              delivery(issuance, "SYSTEM", issuance.id(), "AUTO:" + issuance.id());
              result =
                  new IssuanceResult(
                      command.orderId(),
                      command.chargeId(),
                      issuance.id(),
                      "COMPLETED",
                      issuance.count(),
                      null);
            }
          } else if (issuance.state().equals("COMPLETED")) {
            verifyCounts(issuance);
            result =
                new IssuanceResult(
                    command.orderId(),
                    command.chargeId(),
                    issuance.id(),
                    "COMPLETED",
                    issuance.count(),
                    null);
          } else {
            String code =
                jdbc.queryForObject(
                    "SELECT failure_code FROM ticket_schema.tbl_issuances WHERE id=?",
                    String.class,
                    issuance.id());
            result =
                new IssuanceResult(
                    command.orderId(), command.chargeId(), issuance.id(), "FAILED", 0, code);
          }
          appendResult(message, result);
          inbox("ticket.issuance", message, result);
          return null;
        });
  }

  private void verifyCounts(Issuance issuance) {
    for (var item : issuance.purchase().items()) {
      Integer count =
          jdbc.queryForObject(
              "SELECT count(*) FROM ticket_schema.tbl_tickets WHERE issuance_id=? AND order_item_id=?",
              Integer.class,
              issuance.id(),
              item.orderItemId());
      if (count == null || count != item.quantity())
        throw new TicketProblem(503, "INCOMPLETE_ISSUANCE");
    }
    Integer total =
        jdbc.queryForObject(
            "SELECT count(*) FROM ticket_schema.tbl_tickets WHERE issuance_id=?",
            Integer.class,
            issuance.id());
    if (total == null || total != issuance.count())
      throw new TicketProblem(503, "INCOMPLETE_ISSUANCE");
  }

  private void appendResult(Envelope<IssueTickets> input, IssuanceResult result) {
    jdbc.update(
        "INSERT INTO ticket_schema.tbl_outbox_messages(message_id,aggregate_id,aggregate_version,message_type,schema_version,payload,occurred_at,correlation_id,causation_id,traceparent) VALUES(?,?,?,?,1,?::jsonb,clock_timestamp(),?,?,?)",
        UUID.randomUUID(),
        input.data().orderId(),
        TicketContracts.version(input.data().purchase().sourceOrderVersion()),
        RESULT,
        json.writeValueAsString(result),
        input.data().orderId(),
        input.messageId(),
        input.traceparent());
  }

  private boolean duplicate(String consumer, Object raw) {
    var message = (Envelope<?>) raw;
    var hashes =
        jdbc.query(
            "SELECT payload_hash FROM ticket_schema.tbl_inbox_messages WHERE consumer_name=? AND message_id=?",
            (r, n) -> r.getString(1),
            consumer,
            message.messageId());
    if (hashes.isEmpty()) return false;
    if (!hashes.getFirst().equals(TicketContracts.hash(json, message)))
      throw conflict("MESSAGE_ID_CONFLICT");
    return true;
  }

  private void inbox(String consumer, Envelope<?> message, Object response) {
    jdbc.update(
        "INSERT INTO ticket_schema.tbl_inbox_messages(consumer_name,message_id,payload_hash,response_payload) VALUES(?,?,?,?::jsonb)",
        consumer,
        message.messageId(),
        TicketContracts.hash(json, message),
        response == null ? null : json.writeValueAsString(response));
  }

  private Issuance issuance(UUID order, boolean lock) {
    var rows =
        jdbc.query(
            "SELECT * FROM ticket_schema.tbl_issuances WHERE order_id=?"
                + (lock ? " FOR UPDATE" : ""),
            (r, n) ->
                new Issuance(
                    r.getObject("id", UUID.class),
                    order,
                    r.getObject("event_id", UUID.class),
                    r.getString("buyer_subject"),
                    r.getString("customer_email"),
                    r.getString("state"),
                    r.getString("input_hash"),
                    r.getInt("expected_ticket_count"),
                    json.readValue(r.getString("purchase_snapshot"), PurchaseSnapshot.class)),
            order);
    return rows.isEmpty() ? null : rows.getFirst();
  }

  private Issuance owned(UUID order, String actor, boolean lock) {
    Issuance issuance = issuance(order, lock);
    if (issuance == null || !issuance.buyer().equals(actor)) throw missing();
    return issuance;
  }

  @Override
  public TicketList listTickets(UUID order, String actor) {
    return tx(
        () -> {
          var issuance = owned(order, actor, false);
          if (!issuance.state().equals("COMPLETED"))
            return new TicketList(issuance.state(), List.of());
          verifyCounts(issuance);
          var tickets =
              jdbc.query(
                  "SELECT * FROM ticket_schema.tbl_tickets WHERE issuance_id=? ORDER BY order_item_id,ordinal",
                  (r, n) -> {
                    var display = json.readTree(r.getString("display_snapshot"));
                    return new TicketView(
                        r.getObject("id", UUID.class),
                        r.getObject("event_id", UUID.class),
                        r.getObject("order_item_id", UUID.class),
                        r.getInt("ordinal"),
                        display.path("ticketTypeName").asText(),
                        display.path("seatLabel").isNull()
                            ? null
                            : display.path("seatLabel").asText(),
                        r.getString("state"));
                  },
                  issuance.id());
          return new TicketList("COMPLETED", tickets);
        });
  }

  @Override
  public String qr(UUID ticketId, String actor) {
    return tx(
        () -> {
          StoredTicket ticket = ticket("t.id", ticketId, false);
          if (ticket == null || !ticket.holder().equals(actor)) throw missing();
          Access access = event(ticket.event(), false);
          ticket = ticket("t.id", ticketId, true);
          if (access == null
              || access.state().equals("CANCELLED")
              || ticket.state().equals("VOID")
              || !ticket.issuanceState().equals("COMPLETED"))
            throw new TicketProblem(409, "TICKET_UNAVAILABLE");
          return qr.reveal(ticket.id(), ticket.ciphertext(), ticket.keyVersion());
        });
  }

  private StoredTicket ticket(String column, Object value, boolean lock) {
    // Column is selected only by internal code; never derived from request input.
    if (!Set.of("t.id", "t.qr_token_hash").contains(column))
      throw new IllegalArgumentException("Invalid ticket lookup");
    var rows =
        jdbc.query(
            "SELECT t.*,i.state AS issuance_state FROM ticket_schema.tbl_tickets t JOIN ticket_schema.tbl_issuances i ON i.id=t.issuance_id WHERE "
                + column
                + "=?"
                + (lock ? " FOR SHARE OF i FOR UPDATE OF t" : ""),
            (r, n) ->
                new StoredTicket(
                    r.getObject("id", UUID.class),
                    r.getObject("issuance_id", UUID.class),
                    r.getObject("event_id", UUID.class),
                    r.getString("holder_subject"),
                    r.getString("state"),
                    r.getString("issuance_state"),
                    r.getBytes("qr_token_ciphertext"),
                    r.getString("qr_key_version")),
            value);
    return rows.isEmpty() ? null : rows.getFirst();
  }

  @Override
  public DeliveryReceipt redeliver(UUID order, String actor, UUID key) {
    return tx(
        () -> {
          var issuance = owned(order, actor, false);
          Access access = event(issuance.event(), false);
          issuance = owned(order, actor, true);
          serialize("delivery:" + issuance.id() + ":" + actor + ":" + key);
          var previous =
              jdbc.query(
                  "SELECT id FROM ticket_schema.tbl_ticket_deliveries WHERE issuance_id=? AND requested_by=? AND request_key=? AND request_hash=?",
                  (r, n) -> r.getObject(1, UUID.class),
                  issuance.id(),
                  actor,
                  key,
                  QrTokens.hash("RESEND:" + order + ":" + actor));
          if (!previous.isEmpty()) return new DeliveryReceipt(previous.getFirst(), "PENDING");
          if (!issuance.state().equals("COMPLETED")
              || access == null
              || access.state().equals("CANCELLED"))
            throw new TicketProblem(409, "TICKETS_UNAVAILABLE");
          return new DeliveryReceipt(
              delivery(issuance, actor, key, "RESEND:" + order + ":" + actor), "PENDING");
        });
  }

  private UUID delivery(Issuance issuance, String actor, UUID key, String input) {
    String hash = QrTokens.hash(input);
    var existing =
        jdbc.query(
            "SELECT id,request_hash FROM ticket_schema.tbl_ticket_deliveries WHERE issuance_id=? AND requested_by=? AND request_key=?",
            (r, n) -> Map.entry(r.getObject("id", UUID.class), r.getString("request_hash")),
            issuance.id(),
            actor,
            key);
    if (!existing.isEmpty()) {
      if (!existing.getFirst().getValue().equals(hash))
        throw new TicketProblem(409, "IDEMPOTENCY_CONFLICT");
      return existing.getFirst().getKey();
    }
    UUID id = UUID.randomUUID();
    jdbc.update(
        "INSERT INTO ticket_schema.tbl_ticket_deliveries(id,issuance_id,requested_by,channel,request_key,request_hash,recipient_email) VALUES(?,?,?,'EMAIL',?,?,?)",
        id,
        issuance.id(),
        actor,
        key,
        hash,
        issuance.email());
    return id;
  }

  @Override
  public CheckInResult checkIn(
      UUID eventId, String actor, UUID key, String rawToken, UUID correlation) {
    String tokenHash = QrTokens.hash(rawToken == null ? "" : rawToken);
    String inputHash =
        TicketContracts.hash(
            json, Map.of("event", eventId, "actor", actor, "tokenHash", tokenHash));
    CheckOutcome outcome =
        tx(
            () -> {
              serialize("checkin:" + actor + ":" + key);
              var prior =
                  jdbc.query(
                      "SELECT * FROM ticket_schema.tbl_checkin_attempts WHERE actor_subject=? AND request_key=? AND replay_of IS NULL",
                      (r, n) -> receipt(r),
                      actor,
                      key);
              if (!prior.isEmpty()) {
                Receipt root = prior.getFirst();
                boolean conflict = !root.hash().equals(inputHash);
                recordCheck(
                    UUID.randomUUID(),
                    conflict ? null : root.ticket(),
                    key,
                    root.id(),
                    actor,
                    eventId,
                    inputHash,
                    conflict ? "IDEMPOTENCY_CONFLICT" : "REPLAYED",
                    false,
                    now(),
                    correlation);
                return new CheckOutcome(
                    new CheckInResult(
                        root.id(),
                        root.event(),
                        root.ticket(),
                        root.result(),
                        root.success(),
                        root.at()),
                    conflict);
              }
              Access access = event(eventId, false);
              StoredTicket ticket = null;
              String result;
              if (access == null || !access.organizer().equals(actor)) result = "NOT_EVENT_OWNER";
              else if (access.state().equals("CANCELLED")) result = "EVENT_CANCELLED";
              else {
                Instant time = now();
                if (time.isBefore(access.starts()) || time.isAfter(access.ends()))
                  result = "OUTSIDE_WINDOW";
                else {
                  ticket = ticket("t.qr_token_hash", tokenHash, false);
                  if (ticket == null) result = "UNKNOWN_QR";
                  else if (!ticket.event().equals(eventId)) result = "WRONG_EVENT";
                  else {
                    ticket = ticket("t.qr_token_hash", tokenHash, true);
                    Instant lockedTime = now();
                    if (lockedTime.isBefore(access.starts()) || lockedTime.isAfter(access.ends()))
                      result = "OUTSIDE_WINDOW";
                    else if (!ticket.issuanceState().equals("COMPLETED")) result = "NOT_ISSUED";
                    else if (ticket.state().equals("VOID")) result = "VOID";
                    else if (ticket.state().equals("USED")) result = "ALREADY_USED";
                    else {
                      int changed =
                          jdbc.update(
                              "UPDATE ticket_schema.tbl_tickets SET state='USED',used_at=clock_timestamp(),used_by=?,row_version=row_version+1 WHERE id=? AND state='VALID'",
                              actor,
                              ticket.id());
                      result = changed == 1 ? "SUCCESS" : "ALREADY_USED";
                    }
                  }
                }
              }
              UUID id = UUID.randomUUID();
              Instant at = now();
              UUID ticketId = ticket == null ? null : ticket.id();
              boolean success = result.equals("SUCCESS");
              recordCheck(
                  id,
                  ticketId,
                  key,
                  null,
                  actor,
                  eventId,
                  inputHash,
                  result,
                  success,
                  at,
                  correlation);
              return new CheckOutcome(
                  new CheckInResult(id, eventId, ticketId, result, success, at), false);
            });
    if (outcome.conflict()) throw new TicketProblem(409, "IDEMPOTENCY_CONFLICT");
    return outcome.result();
  }

  private Receipt receipt(ResultSet r) throws SQLException {
    return new Receipt(
        r.getObject("id", UUID.class),
        r.getObject("ticket_id", UUID.class),
        r.getObject("requested_event_id", UUID.class),
        r.getString("request_hash"),
        r.getString("result_code"),
        r.getBoolean("succeeded"),
        r.getTimestamp("requested_at").toInstant());
  }

  private void recordCheck(
      UUID id,
      UUID ticket,
      UUID key,
      UUID replay,
      String actor,
      UUID event,
      String hash,
      String result,
      boolean success,
      Instant at,
      UUID correlation) {
    jdbc.update(
        "INSERT INTO ticket_schema.tbl_checkin_attempts(id,ticket_id,request_key,replay_of,actor_subject,requested_event_id,request_hash,requested_at,result_code,succeeded,correlation_id) VALUES(?,?,?,?,?,?,?,?,?,?,?)",
        id,
        ticket,
        key,
        replay,
        actor,
        event,
        hash,
        Timestamp.from(at),
        result,
        success,
        correlation.toString());
  }

  public int voidCancelled(int batch) {
    return tx(
        () ->
            jdbc.update(
                "UPDATE ticket_schema.tbl_tickets SET state='VOID',row_version=row_version+1 WHERE id IN (SELECT t.id FROM ticket_schema.tbl_tickets t JOIN ticket_schema.tbl_event_access_snapshots e ON e.event_id=t.event_id WHERE e.event_status='CANCELLED' AND t.state='VALID' ORDER BY t.id FOR UPDATE OF t SKIP LOCKED LIMIT ?)",
                batch));
  }

  @Override
  public Object probe(UUID order) {
    return tx(
        () -> {
          var issuance = issuance(order, false);
          if (issuance == null) throw missing();
          if (issuance.state().equals("PENDING"))
            return Map.of("state", "PENDING", "orderId", order, "issuanceId", issuance.id());
          var rows =
              jdbc.query(
                  "SELECT response_payload FROM ticket_schema.tbl_inbox_messages WHERE consumer_name='ticket.issuance' AND response_payload->>'issuanceId'=? ORDER BY processed_at LIMIT 1",
                  (r, n) -> json.readValue(r.getString(1), IssuanceResult.class),
                  issuance.id().toString());
          if (rows.isEmpty()) throw new TicketProblem(503, "ISSUANCE_RESULT_UNAVAILABLE");
          return rows.getFirst();
        });
  }
}
