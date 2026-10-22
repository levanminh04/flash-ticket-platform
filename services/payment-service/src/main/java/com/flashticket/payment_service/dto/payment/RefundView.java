package com.flashticket.payment_service.dto.payment;

import com.fasterxml.jackson.annotation.*;
import com.flashticket.payment_service.dto.common.*;
import com.flashticket.payment_service.dto.message.*;
import com.flashticket.payment_service.dto.snapshot.*;
import com.flashticket.payment_service.entity.enums.*;
import jakarta.validation.constraints.*;
import java.util.*;

public record RefundView(
    @NotNull UUID refundId,
    @NotNull UUID chargeId,
    @NotNull @WireMoney String amount,
    @NotNull RefundState state,
    @NotNull RefundReason reason) {}
