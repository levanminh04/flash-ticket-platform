package com.flashticket.booking_service.dto.snapshot;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.flashticket.booking_service.enums.Currency;
import com.flashticket.booking_service.enums.SalesMode;
import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record PurchaseSnapshot(
    @Positive int schemaVersion,
    @NotNull UUID orderId,
    @NotNull UUID eventId,
    @NotBlank String buyerSubject,
    @NotBlank @Email String customerEmail,
    @NotBlank
        @Pattern(regexp = "^[1-9][0-9]{0,18}$")
        @DecimalMax("9223372036854775807")
        String sourceConfigVersion,
    @NotBlank
        @Pattern(regexp = "^[1-9][0-9]{0,18}$")
        @DecimalMax("9223372036854775807")
        String sourceOrderVersion,
    @NotNull Instant frozenAt,
    @NotNull Instant expiresAt,
    @NotNull Currency currency,
    @NotBlank String eventTitle,
    @NotNull Instant eventStartsAt,
    @NotNull Instant eventEndsAt,
    String venueName,
    @NotNull SalesMode mode,
    UUID sectorId,
    @NotBlank @Pattern(regexp = "^(0|[1-9][0-9]{0,18})$") String subtotal,
    @NotBlank @Pattern(regexp = "^(0|[1-9][0-9]{0,18})$") String discount,
    @NotBlank @Pattern(regexp = "^(0|[1-9][0-9]{0,18})$") String total,
    @Valid AppliedPromotionSnapshot promotion,
    @NotNull @Valid List<@NotNull @Valid Item> items) {
  @AssertTrue
  @JsonIgnore
  public boolean isValidSnapshot() {
    return com.flashticket.booking_service.validation.ContractRules.validPurchaseSnapshot(this);
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
      UUID sectorId,
      @NotNull @Valid List<@NotNull @Valid SeatSelectionSnapshot> seats) {}
}
