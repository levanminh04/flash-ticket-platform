package com.flashticket.booking_service.dto.request;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import lombok.Getter;

public sealed interface OrderSelection
    permits OrderSelection.QuantitySelection, OrderSelection.SeatMapSelection {
  @JsonProperty(value = "mode", access = JsonProperty.Access.READ_ONLY)
  default String getMode() {
    return this instanceof QuantitySelection ? "QUANTITY" : "SEAT_MAP";
  }

  @Getter
  final class QuantityItem {
    @NotNull private final UUID ticketTypeId;
    @Positive private final int quantity;

    @JsonCreator(mode = JsonCreator.Mode.PROPERTIES)
    public QuantityItem(
        @JsonProperty(value = "ticketTypeId", required = true) UUID ticketTypeId,
        @JsonProperty(value = "quantity", required = true) int quantity) {
      this.ticketTypeId = ticketTypeId;
      this.quantity = quantity;
    }
  }

  @Getter
  final class QuantitySelection implements OrderSelection {
    @NotEmpty private final List<@NotNull @Valid QuantityItem> items;

    @JsonCreator(mode = JsonCreator.Mode.PROPERTIES)
    public QuantitySelection(
        @JsonProperty(value = "items", required = true) List<@NotNull @Valid QuantityItem> items) {
      this.items = items == null ? null : Collections.unmodifiableList(new ArrayList<>(items));
    }

    @AssertTrue
    @JsonIgnore
    public boolean isValidSelection() {
      return items != null
          && items.stream().allMatch(Objects::nonNull)
          && items.stream().map(QuantityItem::getTicketTypeId).distinct().count() == items.size();
    }
  }

  sealed interface SeatMapSelection extends OrderSelection
      permits SeatedSelection, StandingSelection {
    @JsonProperty(value = "kind", access = JsonProperty.Access.READ_ONLY)
    default String getKind() {
      return this instanceof SeatedSelection ? "SEATED" : "STANDING";
    }
  }

  @Getter
  final class SeatedSelection implements SeatMapSelection {
    @NotNull private final UUID sectorId;
    @NotEmpty private final List<@NotNull UUID> seatIds;

    @JsonCreator(mode = JsonCreator.Mode.PROPERTIES)
    public SeatedSelection(
        @JsonProperty(value = "sectorId", required = true) UUID sectorId,
        @JsonProperty(value = "seatIds", required = true) List<@NotNull UUID> seatIds) {
      this.sectorId = sectorId;
      this.seatIds =
          seatIds == null ? null : Collections.unmodifiableList(new ArrayList<>(seatIds));
    }

    @AssertTrue
    @JsonIgnore
    public boolean isValidSelection() {
      return seatIds != null
          && seatIds.stream().allMatch(Objects::nonNull)
          && seatIds.stream().distinct().count() == seatIds.size();
    }
  }

  @Getter
  final class StandingSelection implements SeatMapSelection {
    @NotNull private final UUID sectorId;
    @NotEmpty private final List<@NotNull @Valid QuantityItem> items;

    @JsonCreator(mode = JsonCreator.Mode.PROPERTIES)
    public StandingSelection(
        @JsonProperty(value = "sectorId", required = true) UUID sectorId,
        @JsonProperty(value = "items", required = true) List<@NotNull @Valid QuantityItem> items) {
      this.sectorId = sectorId;
      this.items = items == null ? null : Collections.unmodifiableList(new ArrayList<>(items));
    }

    @AssertTrue
    @JsonIgnore
    public boolean isValidSelection() {
      return items != null
          && items.stream().allMatch(Objects::nonNull)
          && items.stream().map(QuantityItem::getTicketTypeId).distinct().count() == items.size();
    }
  }
}
