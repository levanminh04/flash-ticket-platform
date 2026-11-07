package com.flashticket.booking_service.dto.response;

import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public final class PaymentRequestAccepted {
  @NotNull private final UUID orderId;
  @NotNull private final UUID requestId;
  @NotNull private final Instant expiresAt;
}
