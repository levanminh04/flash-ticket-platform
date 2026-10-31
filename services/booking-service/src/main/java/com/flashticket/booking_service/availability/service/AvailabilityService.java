package com.flashticket.booking_service.availability.service;

import com.flashticket.booking_service.dto.message.Envelope;
import com.flashticket.booking_service.dto.message.EventCancellation;
import com.flashticket.booking_service.dto.snapshot.SalesConfiguration;

public interface AvailabilityService {
  void processEventCancellation(Envelope<EventCancellation> message);

  void synchronizeSalesConfiguration(Envelope<SalesConfiguration> message);
}
