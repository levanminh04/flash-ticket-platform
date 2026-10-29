package com.flashticket.payment_service.dto.response;

import com.fasterxml.jackson.annotation.*;
import com.flashticket.payment_service.dto.snapshot.*;
import com.flashticket.payment_service.enums.*;
import com.flashticket.payment_service.enums.Currency;
import jakarta.validation.constraints.*;
import java.time.Instant;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public final class PaymentStart {
  @NotNull private final UUID orderId;
  @NotNull private final UUID attemptId;
  @NotNull private final PaymentAttemptState attemptState;

  @NotBlank
  @Pattern(regexp = "^[1-9][0-9]{0,18}$")
  private final String amount;

  @NotNull private final Currency currency;
  private final String paymentUrl;
  @NotNull private final Instant expiresAt;
}
