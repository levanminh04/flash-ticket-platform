package com.flashticket.payment_service.dto.message;

import com.fasterxml.jackson.annotation.*;
import com.flashticket.payment_service.dto.common.*;
import com.flashticket.payment_service.dto.payment.*;
import com.flashticket.payment_service.dto.snapshot.*;
import com.flashticket.payment_service.entity.enums.*;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.util.*;

public record StartPayment(
    @NotNull UUID requestId,
    @NotNull UUID requestKey,
    @NotBlank String requestHash,
    @NotNull PaymentProvider provider,
    @NotNull @Valid PurchaseSnapshot purchase) {}
