package com.flashticket.payment_service.dto.snapshot;

import com.fasterxml.jackson.annotation.*;
import com.flashticket.payment_service.enums.*;
import com.flashticket.payment_service.enums.Currency;
import com.flashticket.payment_service.validation.ContractRules;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import lombok.Getter;

@Getter
public final class SalesConfiguration {
  @NotNull
  @Min(1)
  @Max(1)
  private final Integer schemaVersion;

  @NotNull private final UUID eventId;
  @NotBlank private final String organizerSubject;

  @NotBlank
  @Pattern(regexp = "^[1-9][0-9]{0,18}$")
  @DecimalMax("9223372036854775807")
  private final String sourceConfigVersion;

  @NotNull private final EventState eventStatus;
  @NotBlank private final String eventTitle;
  @NotNull private final Instant eventStartsAt;
  @NotNull private final Instant eventEndsAt;
  private final String venueName;
  @NotNull private final SalesMode mode;
  @NotNull private final Instant saleStartsAt;
  @NotNull private final Instant saleEndsAt;
  @NotNull @Positive private final Integer purchaseLimit;
  @NotNull @Valid private final List<@NotNull TicketTypeConfig> ticketTypes;
  @NotNull @Valid private final List<@NotNull SectorConfig> sectors;
  @NotNull @Valid private final List<@NotNull SeatConfig> seats;
  @NotNull @Valid private final List<@NotNull PromotionConfig> promotions;

  @JsonCreator(mode = JsonCreator.Mode.PROPERTIES)
  public SalesConfiguration(
      @JsonProperty(value = "schemaVersion", required = true) Integer schemaVersion,
      @JsonProperty(value = "eventId", required = true) UUID eventId,
      @JsonProperty(value = "organizerSubject", required = true) String organizerSubject,
      @JsonProperty(value = "sourceConfigVersion", required = true) String sourceConfigVersion,
      @JsonProperty(value = "eventStatus", required = true) EventState eventStatus,
      @JsonProperty(value = "eventTitle", required = true) String eventTitle,
      @JsonProperty(value = "eventStartsAt", required = true) Instant eventStartsAt,
      @JsonProperty(value = "eventEndsAt", required = true) Instant eventEndsAt,
      @JsonProperty(value = "venueName", required = true) String venueName,
      @JsonProperty(value = "mode", required = true) SalesMode mode,
      @JsonProperty(value = "saleStartsAt", required = true) Instant saleStartsAt,
      @JsonProperty(value = "saleEndsAt", required = true) Instant saleEndsAt,
      @JsonProperty(value = "purchaseLimit", required = true) Integer purchaseLimit,
      @JsonProperty(value = "ticketTypes", required = true)
          List<SalesConfiguration.TicketTypeConfig> ticketTypes,
      @JsonProperty(value = "sectors", required = true)
          List<SalesConfiguration.SectorConfig> sectors,
      @JsonProperty(value = "seats", required = true) List<SalesConfiguration.SeatConfig> seats,
      @JsonProperty(value = "promotions", required = true) List<PromotionConfig> promotions) {
    this.schemaVersion = schemaVersion;
    this.eventId = eventId;
    this.organizerSubject = organizerSubject;
    this.sourceConfigVersion = sourceConfigVersion;
    this.eventStatus = eventStatus;
    this.eventTitle = eventTitle;
    this.eventStartsAt = eventStartsAt;
    this.eventEndsAt = eventEndsAt;
    this.venueName = venueName;
    this.mode = mode;
    this.saleStartsAt = saleStartsAt;
    this.saleEndsAt = saleEndsAt;
    this.purchaseLimit = purchaseLimit;
    this.ticketTypes =
        ticketTypes == null ? null : Collections.unmodifiableList(new ArrayList<>(ticketTypes));
    this.sectors = sectors == null ? null : Collections.unmodifiableList(new ArrayList<>(sectors));
    this.seats = seats == null ? null : Collections.unmodifiableList(new ArrayList<>(seats));
    this.promotions =
        promotions == null ? null : Collections.unmodifiableList(new ArrayList<>(promotions));
  }

  @AssertTrue
  @JsonIgnore
  public boolean isValidSnapshot() {
    return ContractRules.validSalesConfiguration(this);
  }

  @Getter
  public static final class TicketTypeConfig {
    @NotNull private final UUID ticketTypeId;
    @NotBlank private final String name;

    @NotBlank
    @Pattern(regexp = "^[1-9][0-9]{0,18}$")
    private final String unitPrice;

    @NotNull private final Currency currency;
    private final UUID sectorId;

    @Pattern(regexp = "^(0|[1-9][0-9]{0,18})$")
    @DecimalMax("9223372036854775807")
    private final String configuredQuantity;

    private final String colorCode;

    @JsonCreator(mode = JsonCreator.Mode.PROPERTIES)
    public TicketTypeConfig(
        @JsonProperty(value = "ticketTypeId", required = true) UUID ticketTypeId,
        @JsonProperty(value = "name", required = true) String name,
        @JsonProperty(value = "unitPrice", required = true) String unitPrice,
        @JsonProperty(value = "currency", required = true) Currency currency,
        @JsonProperty(value = "sectorId", required = true) UUID sectorId,
        @JsonProperty(value = "configuredQuantity", required = true) String configuredQuantity,
        @JsonProperty(value = "colorCode", required = true) String colorCode) {
      this.ticketTypeId = ticketTypeId;
      this.name = name;
      this.unitPrice = unitPrice;
      this.currency = currency;
      this.sectorId = sectorId;
      this.configuredQuantity = configuredQuantity;
      this.colorCode = colorCode;
    }
  }

  @Getter
  public static final class SectorConfig {
    @NotNull private final UUID sectorId;
    @NotNull @Valid private final SectorType kind;

    @NotBlank
    @Pattern(regexp = "^(0|[1-9][0-9]{0,18})$")
    @DecimalMax("9223372036854775807")
    private final String configuredCapacity;

    @JsonCreator(mode = JsonCreator.Mode.PROPERTIES)
    public SectorConfig(
        @JsonProperty(value = "sectorId", required = true) UUID sectorId,
        @JsonProperty(value = "kind", required = true) SectorType kind,
        @JsonProperty(value = "configuredCapacity", required = true) String configuredCapacity) {
      this.sectorId = sectorId;
      this.kind = kind;
      this.configuredCapacity = configuredCapacity;
    }
  }

  @Getter
  public static final class SeatConfig {
    @NotNull private final UUID seatId;
    @NotNull private final UUID sectorId;
    @NotNull private final UUID ticketTypeId;
    @NotBlank private final String seatLabel;
    @NotBlank private final String rowName;
    @NotBlank private final String seatNumber;

    @NotNull
    @JsonProperty("isHidden")
    private final Boolean isHidden;

    @JsonCreator(mode = JsonCreator.Mode.PROPERTIES)
    public SeatConfig(
        @JsonProperty(value = "seatId", required = true) UUID seatId,
        @JsonProperty(value = "sectorId", required = true) UUID sectorId,
        @JsonProperty(value = "ticketTypeId", required = true) UUID ticketTypeId,
        @JsonProperty(value = "seatLabel", required = true) String seatLabel,
        @JsonProperty(value = "rowName", required = true) String rowName,
        @JsonProperty(value = "seatNumber", required = true) String seatNumber,
        @JsonProperty(value = "isHidden", required = true) Boolean isHidden) {
      this.seatId = seatId;
      this.sectorId = sectorId;
      this.ticketTypeId = ticketTypeId;
      this.seatLabel = seatLabel;
      this.rowName = rowName;
      this.seatNumber = seatNumber;
      this.isHidden = isHidden;
    }
  }
}
