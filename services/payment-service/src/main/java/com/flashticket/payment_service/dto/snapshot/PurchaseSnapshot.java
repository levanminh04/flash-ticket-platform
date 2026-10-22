package com.flashticket.payment_service.dto.snapshot;

import com.fasterxml.jackson.annotation.*;
import com.flashticket.payment_service.dto.common.*;
import com.flashticket.payment_service.dto.message.*;
import com.flashticket.payment_service.dto.payment.*;
import com.flashticket.payment_service.entity.enums.*;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.time.Instant;
import java.util.*;

public record PurchaseSnapshot(
    @NotNull @Min(1) @Max(1) Integer schemaVersion,
    @NotNull UUID orderId,
    @NotNull UUID eventId,
    @NotBlank String buyerSubject,
    @NotBlank @Email String customerEmail,
    @NotNull @WireVersion String sourceConfigVersion,
    @NotNull @WireVersion String sourceOrderVersion,
    @NotNull Instant frozenAt,
    @NotNull Instant expiresAt,
    @NotNull @Pattern(regexp = "VND") String currency,
    @NotBlank String eventTitle,
    @NotNull Instant eventStartsAt,
    @NotNull Instant eventEndsAt,
    String venueName,
    @NotNull PurchaseMode mode,
    UUID sectorId,
    @NotNull @WireMoney(positive = true) String subtotal,
    @NotNull @WireMoney String discount,
    @NotNull @WireMoney(positive = true) String total,
    @Valid PurchasePromotion promotion,
    @NotEmpty List<@Valid PurchaseItem> items) {
  public PurchaseSnapshot {
    if (items != null) items = List.copyOf(items);
  }

  public List<PurchaseItem> items() {
    return items == null ? null : List.copyOf(items);
  }
}
