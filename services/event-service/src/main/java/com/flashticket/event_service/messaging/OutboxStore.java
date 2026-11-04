package com.flashticket.event_service.messaging;

import com.flashticket.event_service.domain.EventData;
import com.flashticket.event_service.observability.EventContext;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.json.JsonMapper;

@Repository
public class OutboxStore {
  private final JdbcTemplate jdbc;
  private final JsonMapper json;
  private final TransactionTemplate transactions;

  @edu.umd.cs.findbugs.annotations.SuppressFBWarnings(
      value = "EI_EXPOSE_REP2",
      justification =
          "Intentionally shares Spring-managed infrastructure; no dependency accessor is exposed.")
  public OutboxStore(JdbcTemplate jdbc, JsonMapper json, PlatformTransactionManager manager) {
    this.jdbc = jdbc;
    this.json = json;
    this.transactions = new TransactionTemplate(manager);
  }

  public void append(EventData event, String type, Object data) {
    jdbc.update(
        "INSERT INTO event_schema.tbl_outbox_messages(message_id,aggregate_id,aggregate_version,message_type,payload,occurred_at,correlation_id,traceparent) VALUES(?,?,?,?,?::jsonb,clock_timestamp(),?,?)",
        UUID.randomUUID(),
        event.id(),
        event.configVersion(),
        type,
        json.writeValueAsString(data),
        EventContext.correlation(event.id()),
        EventContext.traceparent());
  }

  public record Message(
      UUID id,
      UUID aggregateId,
      long version,
      String type,
      int schemaVersion,
      String payload,
      Instant occurredAt,
      UUID correlationId,
      UUID causationId,
      String traceparent,
      int attempt) {}

  public List<Message> claim(Duration lease) {
    var result =
        transactions.execute(
            status -> {
              var rows =
                  jdbc.query(
                      "SELECT * FROM event_schema.tbl_outbox_messages WHERE published_at IS NULL AND next_attempt_at<=clock_timestamp() ORDER BY occurred_at,message_id FOR UPDATE SKIP LOCKED LIMIT 1",
                      (r, n) ->
                          new Message(
                              r.getObject("message_id", UUID.class),
                              r.getObject("aggregate_id", UUID.class),
                              r.getLong("aggregate_version"),
                              r.getString("message_type"),
                              r.getInt("schema_version"),
                              r.getString("payload"),
                              r.getTimestamp("occurred_at").toInstant(),
                              r.getObject("correlation_id", UUID.class),
                              r.getObject("causation_id", UUID.class),
                              r.getString("traceparent"),
                              Math.addExact(r.getInt("attempts"), 1)));
              for (var row : rows)
                jdbc.update(
                    "UPDATE event_schema.tbl_outbox_messages SET attempts=?,next_attempt_at=clock_timestamp()+(? * interval '1 millisecond') WHERE message_id=?",
                    row.attempt(),
                    lease.toMillis(),
                    row.id());
              return rows;
            });
    return result == null ? List.of() : List.copyOf(result);
  }

  public boolean confirmed(Message message) {
    return jdbc.update(
            "UPDATE event_schema.tbl_outbox_messages SET published_at=clock_timestamp(),last_error_code=NULL WHERE message_id=? AND attempts=? AND published_at IS NULL",
            message.id(),
            message.attempt())
        == 1;
  }

  public void retry(Message message, String code, Duration delay) {
    jdbc.update(
        "UPDATE event_schema.tbl_outbox_messages SET last_error_code=?,next_attempt_at=clock_timestamp()+(? * interval '1 millisecond') WHERE message_id=? AND attempts=? AND published_at IS NULL",
        code,
        delay.toMillis(),
        message.id(),
        message.attempt());
  }

  public double backlog() {
    try {
      Long value =
          jdbc.queryForObject(
              "SELECT count(*) FROM event_schema.tbl_outbox_messages WHERE published_at IS NULL",
              Long.class);
      return value == null ? Double.NaN : value.doubleValue();
    } catch (org.springframework.dao.DataAccessException ex) {
      return Double.NaN;
    }
  }

  public double oldestAge() {
    try {
      Double value =
          jdbc.queryForObject(
              "SELECT coalesce(greatest(0,extract(epoch FROM clock_timestamp()-min(occurred_at))),0)::float8 FROM event_schema.tbl_outbox_messages WHERE published_at IS NULL",
              Double.class);
      return value == null ? Double.NaN : value;
    } catch (org.springframework.dao.DataAccessException ex) {
      return Double.NaN;
    }
  }
}
