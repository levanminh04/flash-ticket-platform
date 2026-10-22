package com.flashticket.payment_service.dto.snapshot;

import com.fasterxml.jackson.annotation.*;
import com.flashticket.payment_service.dto.common.*;
import com.flashticket.payment_service.dto.message.*;
import com.flashticket.payment_service.dto.payment.*;
import com.flashticket.payment_service.entity.enums.*;
import jakarta.validation.constraints.*;
import java.util.*;

public record PurchasePromotion(
    @NotNull UUID promotionId, @NotBlank String code, @NotNull @WireMoney String discount) {}
