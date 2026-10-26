package com.flashticket.ticket_service.dto.snapshot;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.flashticket.ticket_service.enums.Currency;
import com.flashticket.ticket_service.enums.SalesMode;
import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

public record PurchaseSnapshot(
    @Positive int schemaVersion,
    @NotNull UUID orderId,
    @NotNull UUID eventId,
    @NotBlank String buyerSubject,
    @NotBlank @Email @Size(max = 254) String customerEmail,
    @NotBlank @Pattern(regexp = "^[1-9][0-9]{0,18}$") @DecimalMax("9223372036854775807")
        String sourceConfigVersion,
    @NotBlank @Pattern(regexp = "^[1-9][0-9]{0,18}$") @DecimalMax("9223372036854775807")
        String sourceOrderVersion,
    @NotNull Instant frozenAt,
    @NotNull Instant expiresAt,
    @NotNull Currency currency,
    @NotBlank String eventTitle,
    @NotNull Instant eventStartsAt,
    @NotNull Instant eventEndsAt,
    @JsonProperty(required = true) String venueName,
    @NotNull SalesMode mode,
    @JsonProperty(required = true) UUID sectorId,
    @NotBlank @Pattern(regexp = "^(0|[1-9][0-9]{0,18})$") String subtotal,
    @NotBlank @Pattern(regexp = "^(0|[1-9][0-9]{0,18})$") String discount,
    @NotBlank @Pattern(regexp = "^(0|[1-9][0-9]{0,18})$") String total,
    @Valid AppliedPromotionSnapshot promotion,
    @NotNull @Valid List<@NotNull @Valid Item> items) {
  public PurchaseSnapshot {
    items = items == null ? null : Collections.unmodifiableList(new ArrayList<>(items));
  }

  @AssertTrue
  @JsonIgnore
  public boolean isValidSnapshot() {
    return com.flashticket.ticket_service.validation.ContractRules.validPurchaseSnapshot(this);
  }

  public record AppliedPromotionSnapshot(
      @NotNull UUID promotionId,
      @NotBlank String code,
      @NotBlank @Pattern(regexp = "^(0|[1-9][0-9]{0,18})$") String discount) {}

  public record Item(
      @NotNull UUID orderItemId,
      @NotNull UUID ticketTypeId,
      @NotBlank String ticketTypeName,
      @Positive int quantity,
      @NotBlank @Pattern(regexp = "^[1-9][0-9]{0,18}$") String unitPrice,
      @NotBlank @Pattern(regexp = "^(0|[1-9][0-9]{0,18})$") String lineSubtotal,
      @JsonProperty(required = true) UUID sectorId,
      @NotNull @Valid List<@NotNull @Valid SeatSelectionSnapshot> seats) {
    public Item {
      seats = seats == null ? null : Collections.unmodifiableList(new ArrayList<>(seats));
    }
  }
}
