package com.flashticket.payment_service.dto.message;

import com.flashticket.payment_service.dto.snapshot.PurchaseSnapshot;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;

public record StartPayment(
    @NotNull UUID requestId,
    @NotNull UUID requestKey,
    @NotBlank String requestHash,
    @NotNull PaymentProvider provider,
    @NotNull @Valid PurchaseSnapshot purchase) {}
