package com.flashticket.event_service.persistence;

import com.flashticket.event_service.api.EventViews;
import com.flashticket.event_service.web.EventProblem;
import java.nio.charset.StandardCharsets;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.*;
import org.springframework.jdbc.core.namedparam.*;
import org.springframework.stereotype.Repository;

@Repository
public class CancellationRepository {
  private final NamedParameterJdbcTemplate jdbc;

  public CancellationRepository(org.springframework.jdbc.core.JdbcTemplate jdbc) {
    this.jdbc = new NamedParameterJdbcTemplate(jdbc);
  }

  public record Request(
      UUID requestId,
      UUID eventId,
      String status,
      String requestedBy,
      Instant requestedAt,
      String reason,
      String reviewedBy,
      Instant reviewedAt,
      String reviewReason,
      String eventRowVersion) {}

  private static final String SELECT =
      "SELECT r.*,e.row_version FROM event_schema.tbl_event_cancellation_requests r JOIN event_schema.tbl_events e ON e.id=r.event_id ";

  public Request find(UUID id, boolean lock) {
    return jdbc
        .query(
            SELECT + "WHERE r.id=:id" + (lock ? " FOR UPDATE OF r" : ""),
            Map.of("id", id),
            (r, n) -> row(r))
        .stream()
        .findFirst()
        .orElseThrow(EventProblem::missing);
  }

  public void create(UUID id, UUID event, String actor, String reason, Instant at) {
    jdbc.update(
        "INSERT INTO event_schema.tbl_event_cancellation_requests(id,event_id,requested_by,reason,requested_at) VALUES(:id,:event,:actor,:reason,:at)",
        new MapSqlParameterSource("id", id)
            .addValue("event", event)
            .addValue("actor", actor)
            .addValue("reason", reason)
            .addValue("at", EventRepository.utc(at)));
  }

  public boolean pending(UUID event) {
    return Boolean.TRUE.equals(
        jdbc.queryForObject(
            "SELECT EXISTS(SELECT 1 FROM event_schema.tbl_event_cancellation_requests WHERE event_id=:event AND status='PENDING')",
            Map.of("event", event),
            Boolean.class));
  }

  public void review(UUID id, String status, String admin, String reason, Instant at) {
    int count =
        jdbc.update(
            "UPDATE event_schema.tbl_event_cancellation_requests SET status=:status,reviewed_by=:admin,reviewed_at=:at,review_reason=:reason WHERE id=:id AND status='PENDING'",
            new MapSqlParameterSource("id", id)
                .addValue("status", status)
                .addValue("admin", admin)
                .addValue("at", EventRepository.utc(at))
                .addValue("reason", reason));
    if (count != 1) throw EventProblem.conflict("INVALID_CANCELLATION_REQUEST_STATE");
  }

  public void approvePending(UUID event, String admin, Instant at) {
    jdbc.update(
        "UPDATE event_schema.tbl_event_cancellation_requests SET status='APPROVED',reviewed_by=:admin,reviewed_at=:at WHERE event_id=:event AND status='PENDING'",
        Map.of("event", event, "admin", admin, "at", EventRepository.utc(at)));
  }

  public Instant now() {
    return jdbc.queryForObject(
        "SELECT clock_timestamp() AS instant",
        Map.of(),
        (r, n) -> r.getTimestamp("instant").toInstant());
  }

  public void touch(UUID event, long version) {
    int count =
        jdbc.update(
            "UPDATE event_schema.tbl_events SET row_version=row_version+1,updated_at=clock_timestamp() WHERE id=:event AND row_version=:version",
            Map.of("event", event, "version", version));
    if (count != 1) throw EventProblem.conflict("STALE_EVENT_VERSION");
  }

  public EventViews.Page<Request> list(UUID event, String status, int size, String cursor) {
    if (size < 1 || size > 100) throw new EventProblem(400, "INVALID_PAGE_SIZE", "size");
    if (status != null && !Set.of("PENDING", "APPROVED", "REJECTED").contains(status))
      throw new EventProblem(400, "INVALID_REQUEST_STATUS", "status");
    var sql = new StringBuilder(SELECT + "WHERE true");
    var params = new MapSqlParameterSource();
    if (event != null) {
      sql.append(" AND r.event_id=:event");
      params.addValue("event", event);
    }
    if (status != null) {
      sql.append(" AND r.status=:status");
      params.addValue("status", status);
    }
    if (cursor != null) {
      try {
        var fields =
            new String(Base64.getUrlDecoder().decode(cursor), StandardCharsets.UTF_8)
                .split("\\|", -1);
        if (fields.length != 2) throw new IllegalArgumentException();
        params
            .addValue("at", EventRepository.utc(Instant.parse(fields[0])))
            .addValue("id", UUID.fromString(fields[1]));
        sql.append(" AND (r.requested_at,r.id)<(:at,:id)");
      } catch (IllegalArgumentException | java.time.DateTimeException error) {
        throw new EventProblem(400, "INVALID_CURSOR", "cursor");
      }
    }
    params.addValue("limit", size + 1);
    sql.append(" ORDER BY r.requested_at DESC,r.id DESC LIMIT :limit");
    var rows = jdbc.query(sql.toString(), params, (r, n) -> row(r));
    boolean more = rows.size() > size;
    var items = rows.subList(0, Math.min(size, rows.size()));
    String next = null;
    if (more) {
      var last = items.getLast();
      next =
          Base64.getUrlEncoder()
              .withoutPadding()
              .encodeToString(
                  (last.requestedAt() + "|" + last.requestId()).getBytes(StandardCharsets.UTF_8));
    }
    return new EventViews.Page<>(items, next);
  }

  private static Request row(ResultSet r) throws SQLException {
    var at = r.getTimestamp("reviewed_at");
    return new Request(
        r.getObject("id", UUID.class),
        r.getObject("event_id", UUID.class),
        r.getString("status"),
        r.getString("requested_by"),
        r.getTimestamp("requested_at").toInstant(),
        r.getString("reason"),
        r.getString("reviewed_by"),
        at == null ? null : at.toInstant(),
        r.getString("review_reason"),
        Long.toString(r.getLong("row_version")));
  }
}
