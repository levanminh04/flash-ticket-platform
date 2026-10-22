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

public record PromotionConfig(
    @NotNull UUID promotionId,
    @NotBlank String code,
    @NotNull Instant startsAt,
    @NotNull Instant endsAt,
    @NotNull @WireVersion String maximumUses,
    @NotNull @Min(1) @Max(1) Integer maximumUsesPerUser,
    @NotNull @Valid PromotionDiscount discount) {}
