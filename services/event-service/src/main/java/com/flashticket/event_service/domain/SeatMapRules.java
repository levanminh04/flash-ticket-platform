package com.flashticket.event_service.domain;

import com.flashticket.event_service.api.SeatMap;
import com.flashticket.event_service.web.EventProblem;
import java.math.BigDecimal;
import java.util.*;
import tools.jackson.databind.JsonNode;

public final class SeatMapRules {
  private SeatMapRules() {}

  public static void validate(SeatMap map, List<EventData.TicketType> types) {
    if (map == null) throw EventProblem.invalid("seatMap");
    require(Integer.valueOf(1).equals(map.schemaVersion()) && map.layoutId() != null, "seatMap");
    ContractRules.text(map.name(), "seatMap.name");
    require(positive(map.canvasWidth()) && positive(map.canvasHeight()), "seatMap.canvas");
    require(
        map.layoutData() != null
            && Integer.valueOf(1).equals(map.layoutData().schemaVersion())
            && map.layoutData().decorations() != null
            && map.sectors() != null,
        "seatMap.layoutData");
    var decorationIds = new HashSet<UUID>();
    for (var d : map.layoutData().decorations()) {
      if (d == null) throw EventProblem.invalid("decorations");
      require(d.id() != null && decorationIds.add(d.id()) && d.label() != null, "decorations");
      color(d.colorCode());
      shape(d.shape());
    }
    var sectorIds = new HashSet<UUID>();
    var seatIds = new HashSet<UUID>();
    var typeMap = new HashMap<UUID, EventData.TicketType>();
    types.forEach(t -> typeMap.put(t.id(), t));
    for (var sector : map.sectors()) {
      if (sector == null) throw EventProblem.invalid("sectorId");
      require(sector.sectorId() != null && sectorIds.add(sector.sectorId()), "sectorId");
      ContractRules.text(sector.name(), "sectors.name");
      require(
          Set.of("SEATED", "STANDING").contains(Objects.toString(sector.kind(), "")),
          "sectors.kind");
      ContractRules.unsigned(sector.configuredCapacity(), "configuredCapacity", true);
      require(
          sector.displayOrder() != null && sector.displayOrder() >= 0 && sector.seats() != null,
          "sectors");
      color(sector.colorCode());
      geometry(sector.geometry());
      require(!"STANDING".equals(sector.kind()) || sector.seats().isEmpty(), "sectors.seats");
      var labels = new HashSet<String>();
      for (var seat : sector.seats()) {
        if (seat == null) throw EventProblem.invalid("seats");
        require(
            seat.seatId() != null && seatIds.add(seat.seatId()) && seat.isHidden() != null,
            "seats");
        require(labels.add(ContractRules.text(seat.seatLabel(), "seatLabel")), "seatLabel");
        ContractRules.text(seat.rowName(), "rowName");
        ContractRules.text(seat.seatNumber(), "seatNumber");
        coordinate(seat.coordX());
        coordinate(seat.coordY());
        if (seat.ticketTypeId() != null) {
          var type = typeMap.get(seat.ticketTypeId());
          require(type != null && sector.sectorId().equals(type.sectorId()), "seats.ticketTypeId");
        }
      }
    }
    for (var type : types) require(sectorIds.contains(type.sectorId()), "ticketTypes.sectorId");
  }

  public static void submittable(EventData event) {
    require(event.seatMap() != null && !event.seatMap().sectors().isEmpty(), "seatMap");
    validate(event.seatMap(), event.ticketTypes());
    for (var sector : event.seatMap().sectors()) {
      if ("SEATED".equals(sector.kind())
          && sector.seats().stream().filter(seat -> !seat.isHidden()).count()
              > ContractRules.unsigned(sector.configuredCapacity(), "configuredCapacity", true))
        throw EventProblem.invalid("sectors.configuredCapacity");
      for (var seat : sector.seats())
        require(seat.isHidden() || seat.ticketTypeId() != null, "seats.ticketTypeId");
    }
  }

  private static void coordinate(BigDecimal value) {
    require(
        value != null
            && value.stripTrailingZeros().scale() <= 2
            && value.abs().compareTo(new BigDecimal("99999999.99")) <= 0,
        "seatCoordinate");
  }

  private static boolean positive(Integer value) {
    return value != null && value > 0;
  }

  public static void color(String value) {
    require(value == null || value.matches("#[0-9a-fA-F]{6}"), "colorCode");
  }

  private static void geometry(SeatMap.Geometry g) {
    if (g == null) throw EventProblem.invalid("geometry");
    require(
        Integer.valueOf(1).equals(g.schemaVersion())
            && g.editorVisible() != null
            && g.editorLocked() != null
            && g.seatLayout() != null,
        "geometry");
    shape(g.shape());
    var l = g.seatLayout();
    require(
        Set.of("grid", "arc", "fan").contains(Objects.toString(l.mode(), ""))
            && positive(l.rows())
            && positive(l.seatsPerRow())
            && l.rowStartCharCode() != null
            && Character.isValidCodePoint(l.rowStartCharCode())
            && l.seatStartNumber() != null
            && l.seatStartNumber() >= 0,
        "seatLayout");
    require(
        l.gapX() != null
            && l.gapY() != null
            && l.paddingX() != null
            && l.paddingY() != null
            && l.offsetX() != null
            && l.offsetY() != null
            && l.seatRadius() != null,
        "seatLayout");
    require(
        l.gapX().signum() >= 0
            && l.gapY().signum() >= 0
            && l.paddingX().signum() >= 0
            && l.paddingY().signum() >= 0
            && l.seatRadius().signum() > 0,
        "seatLayout");
    for (var number :
        List.of(
            l.gapX(),
            l.gapY(),
            l.paddingX(),
            l.paddingY(),
            l.offsetX(),
            l.offsetY(),
            l.seatRadius())) require(Double.isFinite(number.doubleValue()), "seatLayout");
  }

  public static void shape(JsonNode shape) {
    if (shape == null) throw EventProblem.invalid("shape");
    require(
        shape.isObject() && shape.has("shapeType") && shape.get("shapeType").isString(), "shape");
    String type = shape.get("shapeType").asString();
    Set<String> fields =
        switch (type) {
          case "rectangle", "rect", "roundedRect", "stage", "foh", "circle", "ellipse" ->
              Set.of("shapeType", "bounds");
          case "polygon" -> Set.of("shapeType", "bounds", "points");
          case "path" -> Set.of("shapeType", "bounds", "pathData");
          case "ringSection", "fan" ->
              Set.of(
                  "shapeType",
                  "bounds",
                  "cx",
                  "cy",
                  "outerRadius",
                  "innerRadius",
                  "startAngle",
                  "endAngle");
          default -> throw EventProblem.invalid("shapeType");
        };
    exact(shape, fields);
    var bounds = shape.get("bounds");
    exact(bounds, Set.of("x", "y", "width", "height"));
    number(bounds.get("x"));
    number(bounds.get("y"));
    require(number(bounds.get("width")) > 0 && number(bounds.get("height")) > 0, "bounds");
    if ("polygon".equals(type)) {
      var points = shape.get("points");
      require(points.isArray() && points.size() >= 6 && points.size() % 2 == 0, "points");
      points.forEach(SeatMapRules::number);
    } else if ("path".equals(type)) {
      var path = shape.get("pathData");
      require(path.isString() && SvgPath.valid(path.asString()), "pathData");
    } else if (Set.of("ringSection", "fan").contains(type)) {
      number(shape.get("cx"));
      number(shape.get("cy"));
      double inner = number(shape.get("innerRadius")), outer = number(shape.get("outerRadius"));
      double start = number(shape.get("startAngle")), end = number(shape.get("endAngle"));
      require(
          inner >= 0
              && outer > inner
              && (type.equals("ringSection") || inner == 0)
              && start != end
              && Math.abs(end - start) <= 360,
          "shape.angles");
    }
  }

  private static void exact(JsonNode node, Set<String> fields) {
    if (node == null) throw EventProblem.invalid("shape.fields");
    require(node.isObject() && node.size() == fields.size(), "shape.fields");
    for (String field : fields) require(node.has(field), "shape.fields");
  }

  private static double number(JsonNode node) {
    if (node == null) throw EventProblem.invalid("geometry.number");
    require(node.isNumber(), "geometry.number");
    double value = node.asDouble();
    require(Double.isFinite(value), "geometry.number");
    return value;
  }

  private static void require(boolean condition, String field) {
    if (!condition) throw EventProblem.invalid(field);
  }
}
