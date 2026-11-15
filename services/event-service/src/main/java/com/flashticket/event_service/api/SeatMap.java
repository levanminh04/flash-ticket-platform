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

  // Bean properties allow legacy JSON to omit the optional editor settings.
  public static final class SeatLayout {
    public String mode;
    public Integer rows;
    public Integer seatsPerRow;
    public BigDecimal gapX;
    public BigDecimal gapY;
    public BigDecimal paddingX;
    public BigDecimal paddingY;
    public BigDecimal offsetX;
    public BigDecimal offsetY;
    public BigDecimal seatRadius;
    public Integer rowStartCharCode;
    public Integer seatStartNumber;
    public BigDecimal rotation;
    public BigDecimal curve;
    public BigDecimal stretch;

    @com.fasterxml.jackson.annotation.JsonProperty("pathBasedLayout")
    private Boolean pathBasedLayout;

    public Boolean getPathBasedLayout() {
      return pathBasedLayout;
    }

    public String rowLabelPosition;
    public String rowAlignment;
    public String outlineMode;
  }

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
