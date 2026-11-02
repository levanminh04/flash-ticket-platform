package com.flashticket.payment_service.dto.message;

import com.flashticket.payment_service.dto.common.WireVersion;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import java.util.UUID;

public record PromotionConfig(
    @NotNull UUID promotionId,
    @NotBlank String code,
    @NotNull Instant startsAt,
    @NotNull Instant endsAt,
    @NotNull @WireVersion String maximumUses,
    @NotNull @Min(1) @Max(1) Integer maximumUsesPerUser,
    @NotNull @Valid PromotionDiscount discount) {}
