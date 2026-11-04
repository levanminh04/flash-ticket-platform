package com.flashticket.payment_service.dto.response;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.flashticket.payment_service.dto.common.WireMoney;
import com.flashticket.payment_service.entity.enums.PaymentAttemptState;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import java.time.Instant;
import java.util.UUID;

public record PaymentStart(
    @NotNull UUID orderId,
    @NotNull UUID attemptId,
    @NotNull PaymentAttemptState attemptState,
    @NotNull @WireMoney(positive = true) String amount,
    @NotNull @Pattern(regexp = "VND") String currency,
    String paymentUrl,
    @NotNull Instant expiresAt) {
  @AssertTrue
  @JsonIgnore
  public boolean isUrlConsistent() {
    return paymentUrl == null || attemptState == PaymentAttemptState.PENDING;
  }
}
