package com.flashticket.booking_service.messaging.service;

import java.util.UUID;

public interface MessagingService {
  void publishBookingMessage(UUID messageId);
}
