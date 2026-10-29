package com.flashticket.event_service.api;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.util.List;
import java.util.UUID;

public final class EventCommands {
  private EventCommands() {}

  public record Draft(
      @NotBlank String title,
      @NotNull String description,
      UUID categoryId,
      UUID venueId,
      @NotBlank String mode,
      @NotBlank String startsAt,
      @NotBlank String endsAt,
      @NotBlank String saleStartsAt,
      @NotBlank String saleEndsAt,
      @NotNull @Positive Integer purchaseLimit,
      @NotNull List<@NotNull @Valid Image> images,
      @NotNull List<@NotNull @Valid TicketType> ticketTypes,
      @NotNull List<@NotNull @Valid Promotion> promotions) {
    public Draft {
      images = images == null ? null : List.copyOf(images);
      ticketTypes = ticketTypes == null ? null : List.copyOf(ticketTypes);
      promotions = promotions == null ? null : List.copyOf(promotions);
    }
  }

  public record Save(@NotBlank String rowVersion, @NotNull @Valid Draft draft) {}

  public record Version(@NotBlank String rowVersion) {}

  public record Approval(@NotBlank String rowVersion, @NotBlank String feeRate) {}

  public record Image(
      UUID imageId, @NotBlank String objectReference, @NotNull Integer displayOrder) {}

  public record TicketType(
      UUID ticketTypeId,
      @NotBlank String name,
      @NotBlank String unitPrice,
      @NotBlank String currency,
      @NotBlank String configuredQuantity,
      String colorCode) {}

  public record Promotion(
      UUID promotionId,
      @NotBlank String code,
      @NotBlank String startsAt,
      @NotBlank String endsAt,
      @NotBlank String maximumUses,
      @NotNull @Valid Discount discount) {}

  public record Discount(@NotBlank String kind, String amount, Integer percent) {}

  public record Mode(
      @NotBlank String rowVersion,
      @NotBlank String mode,
      @NotNull Boolean confirmDeleteIncompatibleConfiguration) {}

  public record SeatTicketType(
      UUID ticketTypeId,
      @NotNull UUID sectorId,
      @NotBlank String name,
      @NotBlank String unitPrice,
      @NotBlank String currency,
      String colorCode) {}

  public record SeatTypes(
      @NotBlank String rowVersion, @NotNull List<@NotNull @Valid SeatTicketType> ticketTypes) {
    public SeatTypes {
      ticketTypes = ticketTypes == null ? null : List.copyOf(ticketTypes);
    }
  }
}
