package com.flashticket.payment_service.dto.snapshot;

import com.flashticket.payment_service.dto.common.WireMoney;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.util.List;
import java.util.UUID;

public record PurchaseItem(
    @NotNull UUID orderItemId,
    @NotNull UUID ticketTypeId,
    @NotBlank String ticketTypeName,
    @NotNull @Positive Integer quantity,
    @NotNull @WireMoney(positive = true) String unitPrice,
    @NotNull @WireMoney(positive = true) String lineSubtotal,
    UUID sectorId,
    @NotNull List<@Valid PurchaseSeat> seats) {
  public PurchaseItem {
    if (seats != null) seats = List.copyOf(seats);
  }

  public List<PurchaseSeat> seats() {
    return seats == null ? null : List.copyOf(seats);
  }
}
