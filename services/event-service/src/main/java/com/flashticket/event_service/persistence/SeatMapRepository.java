package com.flashticket.event_service.persistence;

import com.flashticket.event_service.api.SeatMap;
import com.flashticket.event_service.domain.EventData;
import com.flashticket.event_service.web.EventProblem;
import java.util.*;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;
import tools.jackson.databind.json.JsonMapper;

@Repository
public class SeatMapRepository {
  private final NamedParameterJdbcTemplate jdbc;
  private final JsonMapper json;

  @edu.umd.cs.findbugs.annotations.SuppressFBWarnings(
      value = "EI_EXPOSE_REP2",
      justification =
          "Intentionally shares Spring-managed beans; no dependency accessor is exposed.")
  public SeatMapRepository(org.springframework.jdbc.core.JdbcTemplate jdbc, JsonMapper json) {
    this.jdbc = new NamedParameterJdbcTemplate(jdbc);
    this.json = json;
  }

  public Map<UUID, SeatMap> load(List<UUID> ids) {
    if (ids.isEmpty()) return Map.of();
    var params = Map.of("ids", ids);
    Map<UUID, List<SeatMap.Seat>> seats = new HashMap<>();
    jdbc.query(
        "SELECT * FROM event_schema.tbl_event_seats WHERE event_id IN (:ids) ORDER BY sector_id,seat_label,id",
        params,
        (org.springframework.jdbc.core.RowCallbackHandler)
            r ->
                seats
                    .computeIfAbsent(r.getObject("sector_id", UUID.class), k -> new ArrayList<>())
                    .add(
                        new SeatMap.Seat(
                            r.getObject("id", UUID.class),
                            r.getString("seat_label"),
                            r.getString("row_name"),
                            r.getString("seat_number"),
                            r.getBigDecimal("coord_x"),
                            r.getBigDecimal("coord_y"),
                            r.getObject("ticket_type_id", UUID.class),
                            r.getBoolean("is_hidden"))));
    Map<UUID, List<SeatMap.Sector>> sectors = new HashMap<>();
    jdbc.query(
        "SELECT * FROM event_schema.tbl_event_sectors WHERE event_id IN (:ids) ORDER BY display_order,id",
        params,
        (org.springframework.jdbc.core.RowCallbackHandler)
            r ->
                sectors
                    .computeIfAbsent(r.getObject("event_id", UUID.class), k -> new ArrayList<>())
                    .add(
                        new SeatMap.Sector(
                            r.getObject("id", UUID.class),
                            r.getString("name"),
                            r.getString("code"),
                            r.getString("sector_type"),
                            Long.toString(r.getLong("configured_capacity")),
                            r.getInt("display_order"),
                            r.getString("color_code"),
                            json.readValue(r.getString("geometry"), SeatMap.Geometry.class),
                            seats.getOrDefault(r.getObject("id", UUID.class), List.of()))));
    Map<UUID, SeatMap> result = new HashMap<>();
    jdbc.query(
        "SELECT * FROM event_schema.tbl_event_layouts WHERE event_id IN (:ids)",
        params,
        (org.springframework.jdbc.core.RowCallbackHandler)
            r ->
                result.put(
                    r.getObject("event_id", UUID.class),
                    new SeatMap(
                        1,
                        r.getObject("id", UUID.class),
                        r.getString("name"),
                        r.getInt("canvas_width"),
                        r.getInt("canvas_height"),
                        r.getString("background_image_reference"),
                        json.readValue(r.getString("layout_data"), SeatMap.LayoutData.class),
                        sectors.getOrDefault(r.getObject("event_id", UUID.class), List.of()))));
    return Map.copyOf(result);
  }

  public void save(EventData event, SeatMap map) {
    UUID id = event.id();
    if (event.seatMap() != null && !event.seatMap().layoutId().equals(map.layoutId()))
      throw EventProblem.invalid("layoutId");
    int layouts =
        jdbc.update(
            "INSERT INTO event_schema.tbl_event_layouts(id,event_id,name,canvas_width,canvas_height,background_image_reference,layout_data) VALUES(:id,:event,:name,:width,:height,:background,:data::jsonb) ON CONFLICT(id) DO UPDATE SET name=excluded.name,canvas_width=excluded.canvas_width,canvas_height=excluded.canvas_height,background_image_reference=excluded.background_image_reference,layout_data=excluded.layout_data WHERE tbl_event_layouts.event_id=excluded.event_id",
            new MapSqlParameterSource("id", map.layoutId())
                .addValue("event", id)
                .addValue("name", map.name())
                .addValue("width", map.canvasWidth())
                .addValue("height", map.canvasHeight())
                .addValue("background", map.backgroundImageReference())
                .addValue("data", json.writeValueAsString(map.layoutData())));
    if (layouts != 1) throw EventProblem.invalid("layoutId");
    jdbc.update(
        "DELETE FROM event_schema.tbl_event_seats WHERE event_id=:event", Map.of("event", id));
    var sectorIds = map.sectors().stream().map(SeatMap.Sector::sectorId).toList();
    if (sectorIds.isEmpty())
      jdbc.update(
          "DELETE FROM event_schema.tbl_event_sectors WHERE event_id=:event", Map.of("event", id));
    else
      jdbc.update(
          "DELETE FROM event_schema.tbl_event_sectors WHERE event_id=:event AND id NOT IN (:ids)",
          Map.of("event", id, "ids", sectorIds));
    for (var sector : map.sectors()) {
      int count =
          jdbc.update(
              "INSERT INTO event_schema.tbl_event_sectors(id,event_id,layout_id,name,sector_type,configured_capacity,code,display_order,color_code,geometry) VALUES(:id,:event,:layout,:name,:kind,:capacity,:code,:position,:color,:geometry::jsonb) ON CONFLICT(id) DO UPDATE SET name=excluded.name,sector_type=excluded.sector_type,configured_capacity=excluded.configured_capacity,code=excluded.code,display_order=excluded.display_order,color_code=excluded.color_code,geometry=excluded.geometry WHERE tbl_event_sectors.event_id=excluded.event_id AND tbl_event_sectors.layout_id=excluded.layout_id",
              new MapSqlParameterSource("id", sector.sectorId())
                  .addValue("event", id)
                  .addValue("layout", map.layoutId())
                  .addValue("name", sector.name())
                  .addValue("kind", sector.kind())
                  .addValue("capacity", Long.parseLong(sector.configuredCapacity()))
                  .addValue("code", sector.code())
                  .addValue("position", sector.displayOrder())
                  .addValue("color", sector.colorCode())
                  .addValue("geometry", json.writeValueAsString(sector.geometry())));
      if (count != 1) throw EventProblem.invalid("sectorId");
      for (var seat : sector.seats())
        jdbc.update(
            "INSERT INTO event_schema.tbl_event_seats(id,event_id,sector_id,seat_label,row_name,seat_number,coord_x,coord_y,ticket_type_id,is_hidden) VALUES(:id,:event,:sector,:label,:row,:number,:x,:y,:type,:hidden)",
            new MapSqlParameterSource("id", seat.seatId())
                .addValue("event", id)
                .addValue("sector", sector.sectorId())
                .addValue("label", seat.seatLabel())
                .addValue("row", seat.rowName())
                .addValue("number", seat.seatNumber())
                .addValue("x", seat.coordX())
                .addValue("y", seat.coordY())
                .addValue("type", seat.ticketTypeId())
                .addValue("hidden", seat.isHidden()));
    }
    bump(event);
  }

  public void types(EventData event, List<EventData.TicketType> types) {
    var ids = types.stream().map(EventData.TicketType::id).toList();
    if (ids.isEmpty())
      jdbc.update(
          "DELETE FROM event_schema.tbl_ticket_types WHERE event_id=:event",
          Map.of("event", event.id()));
    else
      jdbc.update(
          "DELETE FROM event_schema.tbl_ticket_types WHERE event_id=:event AND id NOT IN (:ids)",
          Map.of("event", event.id(), "ids", ids));
    for (var type : types) {
      int count =
          jdbc.update(
              "INSERT INTO event_schema.tbl_ticket_types(id,event_id,name,sales_mode,sector_id,color_code,unit_price,currency,configured_quantity) VALUES(:id,:event,:name,'SEAT_MAP',:sector,:color,:price,'VND',NULL) ON CONFLICT(id) DO UPDATE SET name=excluded.name,sector_id=excluded.sector_id,color_code=excluded.color_code,unit_price=excluded.unit_price WHERE tbl_ticket_types.event_id=excluded.event_id AND tbl_ticket_types.sales_mode='SEAT_MAP'",
              new MapSqlParameterSource("id", type.id())
                  .addValue("event", event.id())
                  .addValue("name", type.name())
                  .addValue("sector", type.sectorId())
                  .addValue("color", type.colorCode())
                  .addValue("price", type.unitPrice()));
      if (count != 1) throw EventProblem.invalid("ticketTypeId");
    }
    bump(event);
  }

  public void mode(EventData event, String mode) {
    var params = Map.of("event", event.id());
    jdbc.update("DELETE FROM event_schema.tbl_event_seats WHERE event_id=:event", params);
    jdbc.update("DELETE FROM event_schema.tbl_ticket_types WHERE event_id=:event", params);
    jdbc.update("DELETE FROM event_schema.tbl_event_sectors WHERE event_id=:event", params);
    jdbc.update("DELETE FROM event_schema.tbl_event_layouts WHERE event_id=:event", params);
    jdbc.update(
        "UPDATE event_schema.tbl_events SET sales_mode=:mode WHERE id=:event",
        Map.of("mode", mode, "event", event.id()));
    bump(event);
  }

  private void bump(EventData event) {
    int count =
        jdbc.update(
            "UPDATE event_schema.tbl_events SET row_version=row_version+1,source_config_version=source_config_version+1,updated_at=clock_timestamp() WHERE id=:id AND row_version=:version AND status='DRAFT'",
            Map.of("id", event.id(), "version", event.rowVersion()));
    if (count != 1) throw EventProblem.conflict("STALE_EVENT_VERSION");
  }
}
