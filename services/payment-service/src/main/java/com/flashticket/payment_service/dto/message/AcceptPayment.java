package com.flashticket.payment_service.dto.message;

import com.flashticket.payment_service.dto.common.WireMoney;
import com.flashticket.payment_service.dto.common.WireVersion;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import java.util.UUID;

public record AcceptPayment(
    @NotNull UUID orderId,
    @NotNull UUID chargeId,
    @NotNull UUID attemptId,
    @NotNull @WireMoney(positive = true) String amount,
    @NotNull @Pattern(regexp = "VND") String currency,
    @NotNull @WireVersion String sourceOrderVersion) {}
