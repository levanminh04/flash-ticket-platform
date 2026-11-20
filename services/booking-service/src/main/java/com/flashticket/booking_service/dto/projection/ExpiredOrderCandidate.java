package com.flashticket.booking_service.dto.projection;

import java.time.Instant;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public final class ExpiredOrderCandidate {
  private final UUID orderId;
  private final Instant expiresAt;
}
