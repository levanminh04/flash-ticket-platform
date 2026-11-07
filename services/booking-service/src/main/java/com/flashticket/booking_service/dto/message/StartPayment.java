package com.flashticket.booking_service.dto.message;

import com.flashticket.booking_service.dto.snapshot.PurchaseSnapshot;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;

public record StartPayment(
    @NotNull UUID requestId,
    @NotNull UUID requestKey,
    @NotBlank String requestHash,
    @NotNull Provider provider,
    @NotNull @Valid PurchaseSnapshot purchase) {
  public enum Provider {
    VNPAY
  }
}
