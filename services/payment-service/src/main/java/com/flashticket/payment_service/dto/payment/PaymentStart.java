package com.flashticket.payment_service.dto.payment;

import com.fasterxml.jackson.annotation.*;
import com.flashticket.payment_service.dto.common.*;
import com.flashticket.payment_service.dto.message.*;
import com.flashticket.payment_service.dto.snapshot.*;
import com.flashticket.payment_service.entity.enums.*;
import jakarta.validation.constraints.*;
import java.time.Instant;
import java.util.*;

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
