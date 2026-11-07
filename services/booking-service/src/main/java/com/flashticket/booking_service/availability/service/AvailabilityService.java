package com.flashticket.booking_service.availability.service;

import com.flashticket.booking_service.dto.message.Envelope;
import com.flashticket.booking_service.dto.message.EventCancellation;
import com.flashticket.booking_service.dto.response.Availability;
import com.flashticket.booking_service.dto.snapshot.SalesConfiguration;
import java.util.UUID;

public interface AvailabilityService {
  Availability getAvailability(UUID eventId);

  void processEventCancellation(Envelope<EventCancellation> message);

  void synchronizeSalesConfiguration(Envelope<SalesConfiguration> message);
}
