package com.flashticket.booking_service.dto.message;

import com.flashticket.booking_service.enums.Currency;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import java.util.UUID;

public record AcceptPayment(
    @NotNull UUID orderId,
    @NotNull UUID chargeId,
    @NotNull UUID attemptId,
    @NotNull @Pattern(regexp = "^(0|[1-9][0-9]{0,18})$") String amount,
    @NotNull Currency currency,
    @NotNull @Pattern(regexp = "^[1-9][0-9]{0,18}$") @DecimalMax("9223372036854775807")
        String sourceOrderVersion) {}
