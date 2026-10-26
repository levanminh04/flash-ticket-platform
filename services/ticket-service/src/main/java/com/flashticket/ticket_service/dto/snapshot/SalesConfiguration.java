package com.flashticket.ticket_service.dto.snapshot;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.flashticket.ticket_service.enums.Currency;
import com.flashticket.ticket_service.enums.EventState;
import com.flashticket.ticket_service.enums.SalesMode;
import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

public record SalesConfiguration(
    @Positive int schemaVersion,
    @NotNull UUID eventId,
    @NotBlank String organizerSubject,
    @NotBlank @Pattern(regexp = "^[1-9][0-9]{0,18}$") @DecimalMax("9223372036854775807")
        String sourceConfigVersion,
    @NotNull EventState eventStatus,
    @NotBlank String eventTitle,
    @NotNull Instant eventStartsAt,
    @NotNull Instant eventEndsAt,
    @JsonProperty(required = true) String venueName,
    @NotNull SalesMode mode,
    @NotNull Instant saleStartsAt,
    @NotNull Instant saleEndsAt,
    @Positive int purchaseLimit,
    @NotNull @Valid List<@NotNull @Valid TicketTypeConfig> ticketTypes,
    @NotNull @Valid List<@NotNull @Valid SectorConfig> sectors,
    @NotNull @Valid List<@NotNull @Valid SeatConfig> seats,
    @NotNull @Valid List<@NotNull @Valid PromotionConfig> promotions) {
  public SalesConfiguration {
    ticketTypes = immutableCopy(ticketTypes);
    sectors = immutableCopy(sectors);
    seats = immutableCopy(seats);
    promotions = immutableCopy(promotions);
  }

  private static <T> List<T> immutableCopy(List<T> values) {
    return values == null ? null : Collections.unmodifiableList(new ArrayList<>(values));
  }

  public List<TicketTypeConfig> ticketTypes() {
    return immutableCopy(ticketTypes);
  }

  public List<SectorConfig> sectors() {
    return immutableCopy(sectors);
  }

  public List<SeatConfig> seats() {
    return immutableCopy(seats);
  }

  public List<PromotionConfig> promotions() {
    return immutableCopy(promotions);
  }

  @AssertTrue
  @JsonIgnore
  public boolean isValidSnapshot() {
    return com.flashticket.ticket_service.validation.ContractRules.validSalesConfiguration(this);
  }

  public enum SectorKind {
    SEATED,
    STANDING
  }

  public record TicketTypeConfig(
      @NotNull UUID ticketTypeId,
      @NotBlank String name,
      @NotBlank @Pattern(regexp = "^[1-9][0-9]{0,18}$") String unitPrice,
      @NotNull Currency currency,
      @JsonProperty(required = true) UUID sectorId,
      @Pattern(regexp = "^(0|[1-9][0-9]{0,18})$")
          @DecimalMax("9223372036854775807")
          @JsonProperty(required = true)
          String configuredQuantity,
      @JsonProperty(required = true) String colorCode) {}

  public record SectorConfig(
      @NotNull UUID sectorId,
      @NotNull SectorKind kind,
      @NotBlank @Pattern(regexp = "^(0|[1-9][0-9]{0,18})$") @DecimalMax("9223372036854775807")
          String configuredCapacity) {}

  public record SeatConfig(
      @NotNull UUID seatId,
      @NotNull UUID sectorId,
      @NotNull UUID ticketTypeId,
      @NotBlank String seatLabel,
      @NotBlank String rowName,
      @NotBlank String seatNumber,
      boolean isHidden) {}
}
