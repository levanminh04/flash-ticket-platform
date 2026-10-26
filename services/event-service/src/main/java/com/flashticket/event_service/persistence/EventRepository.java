package com.flashticket.event_service.persistence;

import com.flashticket.event_service.api.EventViews;
import com.flashticket.event_service.domain.DraftConfiguration;
import com.flashticket.event_service.domain.EventData;
import com.flashticket.event_service.web.EventProblem;
import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.*;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class EventRepository {
  private final NamedParameterJdbcTemplate jdbc;

  public EventRepository(org.springframework.jdbc.core.JdbcTemplate jdbc) {
    this.jdbc = new NamedParameterJdbcTemplate(jdbc);
  }

  private static final String SELECT =
      "SELECT e.*,v.name AS venue_name FROM event_schema.tbl_events e LEFT JOIN event_schema.tbl_venues v ON v.id=e.venue_id ";

  public Optional<EventData> find(UUID id, boolean lock) {
    var rows =
        jdbc.query(
            SELECT + "WHERE e.id=:id" + (lock ? " FOR UPDATE OF e" : ""),
            Map.of("id", id),
            (rs, n) -> row(rs));
    return rows.stream().findFirst().map(this::children);
  }

  public List<EventData> list(
      String owner,
      String status,
      String query,
      UUID category,
      Instant before,
      UUID beforeId,
      int size,
      boolean published) {
    var sql = new StringBuilder(SELECT + "WHERE e.sales_mode='QUANTITY'");
    var params = new MapSqlParameterSource();
    if (owner != null) {
      sql.append(" AND e.organizer_subject=:owner");
      params.addValue("owner", owner);
    }
    if (published) sql.append(" AND e.status='PUBLISHED'");
    else if (status != null) {
      sql.append(" AND e.status=:status");
      params.addValue("status", status);
    }
    if (query != null && !query.isBlank()) {
      sql.append(" AND e.title ILIKE :query ESCAPE '!'");
      params.addValue(
          "query",
          "%" + query.trim().replace("!", "!!").replace("%", "!%").replace("_", "!_") + "%");
    }
    if (category != null) {
      sql.append(" AND e.category_id=:category");
      params.addValue("category", category);
    }
    if (before != null) {
      sql.append(" AND (e.created_at,e.id)<(:before,:beforeId)");
      params.addValue("before", utc(before));
      params.addValue("beforeId", beforeId);
    }
    sql.append(" ORDER BY e.created_at DESC,e.id DESC LIMIT :limit");
    params.addValue("limit", size);
    return jdbc.query(sql.toString(), params, (rs, n) -> row(rs));
  }

  public List<EventData> withChildren(List<EventData> events) {
    if (events.isEmpty()) return List.of();
    var ids = events.stream().map(EventData::id).toList();
    var params = Map.of("ids", ids);
    Map<UUID, List<EventData.Image>> images = new HashMap<>();
    Map<UUID, List<EventData.TicketType>> types = new HashMap<>();
    Map<UUID, List<EventData.Promotion>> promotions = new HashMap<>();
    jdbc.query(
        "SELECT * FROM event_schema.tbl_event_images WHERE event_id IN (:ids) ORDER BY display_order,id",
        params,
        (org.springframework.jdbc.core.RowCallbackHandler)
            rs ->
                images
                    .computeIfAbsent(rs.getObject("event_id", UUID.class), k -> new ArrayList<>())
                    .add(image(rs)));
    jdbc.query(
        "SELECT * FROM event_schema.tbl_ticket_types WHERE event_id IN (:ids) ORDER BY id",
        params,
        (org.springframework.jdbc.core.RowCallbackHandler)
            rs ->
                types
                    .computeIfAbsent(rs.getObject("event_id", UUID.class), k -> new ArrayList<>())
                    .add(type(rs)));
    jdbc.query(
        "SELECT * FROM event_schema.tbl_promotions WHERE event_id IN (:ids) ORDER BY id",
        params,
        (org.springframework.jdbc.core.RowCallbackHandler)
            rs ->
                promotions
                    .computeIfAbsent(rs.getObject("event_id", UUID.class), k -> new ArrayList<>())
                    .add(promotion(rs)));
    return events.stream()
        .map(
            e ->
                copy(
                    e,
                    images.getOrDefault(e.id(), List.of()),
                    types.getOrDefault(e.id(), List.of()),
                    promotions.getOrDefault(e.id(), List.of())))
        .toList();
  }

  private EventData children(EventData event) {
    return withChildren(List.of(event)).getFirst();
  }

  public void references(DraftConfiguration draft) {
    if (draft.categoryId() != null
        && !Boolean.TRUE.equals(
            jdbc.queryForObject(
                "SELECT EXISTS(SELECT 1 FROM event_schema.tbl_categories WHERE id=:id)",
                Map.of("id", draft.categoryId()),
                Boolean.class))) throw EventProblem.invalid("categoryId");
    if (draft.venueId() != null
        && !Boolean.TRUE.equals(
            jdbc.queryForObject(
                "SELECT EXISTS(SELECT 1 FROM event_schema.tbl_venues WHERE id=:id)",
                Map.of("id", draft.venueId()),
                Boolean.class))) throw EventProblem.invalid("venueId");
  }

  public void insert(UUID id, String organizer, DraftConfiguration draft) {
    var p = parameters(id, draft).addValue("organizer", organizer);
    jdbc.update(
        "INSERT INTO event_schema.tbl_events(id,organizer_subject,category_id,venue_id,title,description,sales_mode,status,starts_at,ends_at,sale_starts_at,sale_ends_at,checkin_starts_at,checkin_ends_at,purchase_limit) VALUES(:id,:organizer,:category,:venue,:title,:description,'QUANTITY','DRAFT',:start,:end,:saleStart,:saleEnd,:start,:end,:limit)",
        p);
    replaceChildren(id, draft);
  }

  public void update(EventData existing, DraftConfiguration draft) {
    var p = parameters(existing.id(), draft).addValue("version", existing.rowVersion());
    int changed =
        jdbc.update(
            "UPDATE event_schema.tbl_events SET category_id=:category,venue_id=:venue,title=:title,description=:description,starts_at=:start,ends_at=:end,sale_starts_at=:saleStart,sale_ends_at=:saleEnd,checkin_starts_at=:start,checkin_ends_at=:end,purchase_limit=:limit,row_version=row_version+1,source_config_version=source_config_version+1,updated_at=clock_timestamp() WHERE id=:id AND row_version=:version AND status='DRAFT'",
            p);
    if (changed != 1) throw EventProblem.conflict("STALE_EVENT_VERSION");
    replaceChildren(existing.id(), draft);
  }

  public void transition(EventData existing, String next, BigDecimal fee, String actor) {
    var p =
        new MapSqlParameterSource("id", existing.id())
            .addValue("version", existing.rowVersion())
            .addValue("next", next)
            .addValue("fee", fee)
            .addValue("actor", actor);
    String review =
        "APPROVED".equals(next)
            ? ",fee_rate=:fee,approved_by=:actor,approved_at=clock_timestamp()"
            : "";
    if ("DRAFT".equals(next)) review = ",fee_rate=NULL,approved_by=NULL,approved_at=NULL";
    int count =
        jdbc.update(
            "UPDATE event_schema.tbl_events SET status=:next,row_version=row_version+1,source_config_version=source_config_version+1,updated_at=clock_timestamp()"
                + review
                + " WHERE id=:id AND row_version=:version",
            p);
    if (count != 1) throw EventProblem.conflict("STALE_EVENT_VERSION");
  }

  public List<EventViews.Lookup> categories() {
    return jdbc.query(
        "SELECT id,name FROM event_schema.tbl_categories ORDER BY name,id",
        Map.of(),
        (rs, n) ->
            new EventViews.Lookup(rs.getObject("id", UUID.class), rs.getString("name"), null));
  }

  public List<EventViews.Lookup> venues() {
    return jdbc.query(
        "SELECT id,name,address FROM event_schema.tbl_venues ORDER BY name,id",
        Map.of(),
        (rs, n) ->
            new EventViews.Lookup(
                rs.getObject("id", UUID.class), rs.getString("name"), rs.getString("address")));
  }

  private void replaceChildren(UUID id, DraftConfiguration draft) {
    jdbc.update("DELETE FROM event_schema.tbl_event_images WHERE event_id=:id", Map.of("id", id));
    jdbc.update("DELETE FROM event_schema.tbl_ticket_types WHERE event_id=:id", Map.of("id", id));
    jdbc.update("DELETE FROM event_schema.tbl_promotions WHERE event_id=:id", Map.of("id", id));
    for (var i : draft.images())
      jdbc.update(
          "INSERT INTO event_schema.tbl_event_images(id,event_id,object_reference,display_order) VALUES(:child,:event,:reference,:position)",
          new MapSqlParameterSource("child", i.id())
              .addValue("event", id)
              .addValue("reference", i.objectReference())
              .addValue("position", i.displayOrder()));
    for (var t : draft.ticketTypes())
      jdbc.update(
          "INSERT INTO event_schema.tbl_ticket_types(id,event_id,name,sales_mode,sector_id,color_code,unit_price,currency,configured_quantity) VALUES(:child,:event,:name,'QUANTITY',NULL,:color,:price,'VND',:quantity)",
          new MapSqlParameterSource("child", t.id())
              .addValue("event", id)
              .addValue("name", t.name())
              .addValue("color", t.colorCode())
              .addValue("price", t.unitPrice())
              .addValue("quantity", t.configuredQuantity()));
    for (var promotion : draft.promotions())
      jdbc.update(
          "INSERT INTO event_schema.tbl_promotions(id,event_id,code,starts_at,ends_at,discount_kind,fixed_discount,currency,discount_rate,maximum_uses,maximum_uses_per_user) VALUES(:child,:event,:code,:start,:end,:kind,:fixed,:currency,:rate,:uses,1)",
          new MapSqlParameterSource("child", promotion.id())
              .addValue("event", id)
              .addValue("code", promotion.code())
              .addValue("start", utc(promotion.startsAt()))
              .addValue("end", utc(promotion.endsAt()))
              .addValue("kind", promotion.kind())
              .addValue("fixed", promotion.fixedDiscount())
              .addValue("currency", promotion.fixedDiscount() == null ? null : "VND")
              .addValue("rate", promotion.discountRate())
              .addValue("uses", promotion.maximumUses()));
  }

  private static MapSqlParameterSource parameters(UUID id, DraftConfiguration d) {
    return new MapSqlParameterSource("id", id)
        .addValue("category", d.categoryId())
        .addValue("venue", d.venueId())
        .addValue("title", d.title())
        .addValue("description", d.description())
        .addValue("start", utc(d.startsAt()))
        .addValue("end", utc(d.endsAt()))
        .addValue("saleStart", utc(d.saleStartsAt()))
        .addValue("saleEnd", utc(d.saleEndsAt()))
        .addValue("limit", d.purchaseLimit());
  }

  private static EventData row(ResultSet r) throws SQLException {
    return new EventData(
        r.getObject("id", UUID.class),
        r.getString("organizer_subject"),
        r.getObject("category_id", UUID.class),
        r.getObject("venue_id", UUID.class),
        r.getString("venue_name"),
        r.getString("title"),
        r.getString("description"),
        r.getString("sales_mode"),
        r.getString("status"),
        time(r, "starts_at"),
        time(r, "ends_at"),
        time(r, "sale_starts_at"),
        time(r, "sale_ends_at"),
        r.getInt("purchase_limit"),
        r.getBigDecimal("fee_rate"),
        r.getString("approved_by"),
        time(r, "approved_at"),
        r.getLong("source_config_version"),
        r.getLong("row_version"),
        time(r, "created_at"),
        time(r, "updated_at"),
        List.of(),
        List.of(),
        List.of());
  }

  private static EventData copy(
      EventData e,
      List<EventData.Image> i,
      List<EventData.TicketType> t,
      List<EventData.Promotion> p) {
    return new EventData(
        e.id(),
        e.organizer(),
        e.categoryId(),
        e.venueId(),
        e.venueName(),
        e.title(),
        e.description(),
        e.mode(),
        e.status(),
        e.startsAt(),
        e.endsAt(),
        e.saleStartsAt(),
        e.saleEndsAt(),
        e.purchaseLimit(),
        e.feeRate(),
        e.approvedBy(),
        e.approvedAt(),
        e.configVersion(),
        e.rowVersion(),
        e.createdAt(),
        e.updatedAt(),
        i,
        t,
        p);
  }

  private static EventData.Image image(ResultSet r) throws SQLException {
    return new EventData.Image(
        r.getObject("id", UUID.class), r.getString("object_reference"), r.getInt("display_order"));
  }

  private static EventData.TicketType type(ResultSet r) throws SQLException {
    return new EventData.TicketType(
        r.getObject("id", UUID.class),
        r.getString("name"),
        r.getBigDecimal("unit_price"),
        r.getLong("configured_quantity"),
        r.getString("color_code"));
  }

  private static EventData.Promotion promotion(ResultSet r) throws SQLException {
    return new EventData.Promotion(
        r.getObject("id", UUID.class),
        r.getString("code"),
        time(r, "starts_at"),
        time(r, "ends_at"),
        r.getLong("maximum_uses"),
        r.getString("discount_kind"),
        r.getBigDecimal("fixed_discount"),
        r.getBigDecimal("discount_rate"));
  }

  private static Instant time(ResultSet r, String name) throws SQLException {
    var value = r.getTimestamp(name);
    return value == null ? null : value.toInstant();
  }

  public static OffsetDateTime utc(Instant time) {
    return time.atOffset(ZoneOffset.UTC);
  }
}
