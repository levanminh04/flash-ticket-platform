package com.flashticket.payment_service.dto.snapshot;

import com.flashticket.payment_service.dto.common.WireMoney;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;

public record PurchasePromotion(
    @NotNull UUID promotionId, @NotBlank String code, @NotNull @WireMoney String discount) {}
