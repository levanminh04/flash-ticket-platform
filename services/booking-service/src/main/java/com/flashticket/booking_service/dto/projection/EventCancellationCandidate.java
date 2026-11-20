package com.flashticket.booking_service.dto.projection;

import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public final class EventCancellationCandidate {
  private final UUID eventId;
  private final UUID orderId;
}
