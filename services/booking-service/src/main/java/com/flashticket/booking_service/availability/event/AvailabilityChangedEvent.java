package com.flashticket.booking_service.availability.event;

import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public final class AvailabilityChangedEvent {
  private final UUID eventId;
}
