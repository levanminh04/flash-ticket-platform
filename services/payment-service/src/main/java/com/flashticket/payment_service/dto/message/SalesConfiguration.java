package com.flashticket.payment_service.dto.message;

import com.fasterxml.jackson.annotation.*;
import com.flashticket.payment_service.dto.common.*;
import com.flashticket.payment_service.dto.payment.*;
import com.flashticket.payment_service.dto.snapshot.*;
import com.flashticket.payment_service.entity.enums.*;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.time.Instant;
import java.util.*;

public record SalesConfiguration(
    @NotNull @Min(1) @Max(1) Integer schemaVersion,
    @NotNull UUID eventId,
    @NotBlank String organizerSubject,
    @NotNull @WireVersion String sourceConfigVersion,
    @NotNull EventState eventStatus,
    @NotBlank String eventTitle,
    @NotNull Instant eventStartsAt,
    @NotNull Instant eventEndsAt,
    String venueName,
    @NotNull PurchaseMode mode,
    @NotNull Instant saleStartsAt,
    @NotNull Instant saleEndsAt,
    @NotNull @Positive Integer purchaseLimit,
    @NotNull List<@Valid TicketTypeConfig> ticketTypes,
    @NotNull List<@Valid SectorConfig> sectors,
    @NotNull List<@Valid SeatConfig> seats,
    @NotNull List<@Valid PromotionConfig> promotions) {
  public SalesConfiguration {
    if (ticketTypes != null) ticketTypes = List.copyOf(ticketTypes);
    if (sectors != null) sectors = List.copyOf(sectors);
    if (seats != null) seats = List.copyOf(seats);
    if (promotions != null) promotions = List.copyOf(promotions);
  }

  public List<TicketTypeConfig> ticketTypes() {
    return ticketTypes == null ? null : List.copyOf(ticketTypes);
  }

  public List<SectorConfig> sectors() {
    return sectors == null ? null : List.copyOf(sectors);
  }

  public List<SeatConfig> seats() {
    return seats == null ? null : List.copyOf(seats);
  }

  public List<PromotionConfig> promotions() {
    return promotions == null ? null : List.copyOf(promotions);
  }
}
