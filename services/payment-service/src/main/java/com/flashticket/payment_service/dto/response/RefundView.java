package com.flashticket.payment_service.dto.response;

import com.flashticket.payment_service.dto.common.WireMoney;
import com.flashticket.payment_service.entity.enums.RefundReason;
import com.flashticket.payment_service.entity.enums.RefundState;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;

public record RefundView(
    @NotNull UUID refundId,
    @NotNull UUID chargeId,
    @NotNull @WireMoney String amount,
    @NotNull RefundState state,
    @NotNull RefundReason reason) {}
