package com.flashticket.event_service.api;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import tools.jackson.databind.JsonNode;

public record SeatMap(
    Integer schemaVersion,
    UUID layoutId,
    String name,
    Integer canvasWidth,
    Integer canvasHeight,
    String backgroundImageReference,
    LayoutData layoutData,
    List<Sector> sectors) {
  public SeatMap {
    sectors = sectors == null ? null : List.copyOf(sectors);
  }

  public record LayoutData(Integer schemaVersion, List<Decoration> decorations) {
    public LayoutData {
      decorations = decorations == null ? null : List.copyOf(decorations);
    }
  }

  public record Decoration(UUID id, String label, String colorCode, JsonNode shape) {
    public Decoration {
      shape = shape == null ? null : shape.deepCopy();
    }

    @Override
    public JsonNode shape() {
      return shape == null ? null : shape.deepCopy();
    }
  }

  public record Sector(
      UUID sectorId,
      String name,
      String code,
      String kind,
      String configuredCapacity,
      Integer displayOrder,
      String colorCode,
      Geometry geometry,
      List<Seat> seats) {
    public Sector {
      seats = seats == null ? null : List.copyOf(seats);
    }
  }

  public record Geometry(
      Integer schemaVersion,
      JsonNode shape,
      SeatLayout seatLayout,
      Boolean editorVisible,
      Boolean editorLocked) {
    public Geometry {
      shape = shape == null ? null : shape.deepCopy();
    }

    @Override
    public JsonNode shape() {
      return shape == null ? null : shape.deepCopy();
    }
  }

  public record SeatLayout(
      String mode,
      Integer rows,
      Integer seatsPerRow,
      BigDecimal gapX,
      BigDecimal gapY,
      BigDecimal paddingX,
      BigDecimal paddingY,
      BigDecimal offsetX,
      BigDecimal offsetY,
      BigDecimal seatRadius,
      Integer rowStartCharCode,
      Integer seatStartNumber) {}

  public record Seat(
      UUID seatId,
      String seatLabel,
      String rowName,
      String seatNumber,
      BigDecimal coordX,
      BigDecimal coordY,
      UUID ticketTypeId,
      Boolean isHidden) {}

  public SeatMap publicView() {
    return new SeatMap(
        schemaVersion,
        layoutId,
        name,
        canvasWidth,
        canvasHeight,
        backgroundImageReference,
        layoutData,
        sectors.stream()
            .map(
                s ->
                    new Sector(
                        s.sectorId(),
                        s.name(),
                        s.code(),
                        s.kind(),
                        s.configuredCapacity(),
                        s.displayOrder(),
                        s.colorCode(),
                        s.geometry(),
                        s.seats().stream().filter(seat -> !seat.isHidden()).toList()))
            .toList());
  }
}
