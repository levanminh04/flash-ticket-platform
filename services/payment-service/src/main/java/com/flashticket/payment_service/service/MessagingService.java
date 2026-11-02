package com.flashticket.payment_service.service;

import com.flashticket.payment_service.dto.common.Envelope;
import com.flashticket.payment_service.entity.OutboxMessage;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import tools.jackson.databind.JsonNode;

public interface MessagingService {
  boolean recordInbox(String consumerName, Envelope<?> incomingEnvelope, JsonNode resultPayload);

  void enqueue(Envelope<?> outgoingEnvelope);

  List<OutboxMessage> claimOutbox(Instant now, Instant leaseUntil, int limit);

  void markPublished(UUID messageId, int claimedAttempt, Instant publishedAt);

  void rescheduleOutbox(UUID messageId, int claimedAttempt, Instant retryAt, String errorCode);
}
