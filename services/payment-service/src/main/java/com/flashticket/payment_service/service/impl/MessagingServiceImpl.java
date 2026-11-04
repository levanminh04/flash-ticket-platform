package com.flashticket.payment_service.service.impl;

import com.flashticket.payment_service.dto.common.*;
import com.flashticket.payment_service.entity.*;
import com.flashticket.payment_service.entity.id.InboxMessageId;
import com.flashticket.payment_service.hashing.ContractHashService;
import com.flashticket.payment_service.repository.InboxMessageRepository;
import com.flashticket.payment_service.repository.OutboxMessageRepository;
import com.flashticket.payment_service.service.MessagingService;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;

@Service
@RequiredArgsConstructor
public class MessagingServiceImpl implements MessagingService {
  private final InboxMessageRepository inboxMessageRepository;
  private final OutboxMessageRepository outboxMessageRepository;
  private final ContractCodec codec;
  private final ContractHashService hashes;

  @Override
  @Transactional(propagation = Propagation.MANDATORY)
  public boolean recordInbox(
      String consumerName, Envelope<?> incomingEnvelope, JsonNode resultPayload) {
    String incomingHash = hashes.envelopeHash(incomingEnvelope);
    var id = new InboxMessageId(consumerName, incomingEnvelope.messageId());
    var storedMessage = inboxMessageRepository.findById(id);
    if (storedMessage.isPresent()) {
      if (!incomingHash.equals(storedMessage.get().getPayloadHash()))
        throw new ContractValidationException("messageId", "MESSAGE_PAYLOAD_CONFLICT");
      return false;
    }
    inboxMessageRepository.save(
        InboxMessage.processed(id, incomingHash, Instant.now(), resultPayload));
    return true;
  }

  @Override
  @Transactional(propagation = Propagation.MANDATORY)
  public void enqueue(Envelope<?> outgoingEnvelope) {
    outboxMessageRepository.save(
        OutboxMessage.pending(outgoingEnvelope, codec.toTree(outgoingEnvelope)));
  }

  @Override
  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public List<OutboxMessage> claimOutbox(Instant now, Instant leaseUntil, int limit) {
    if (!leaseUntil.isAfter(now) || limit < 1)
      throw new IllegalArgumentException("Invalid outbox claim");
    var messages =
        outboxMessageRepository.findDueForUpdate(now, PageRequest.of(0, Math.min(limit, 100)));
    for (var message : messages) {
      message.setAttempts(Math.addExact(message.getAttempts(), 1));
      message.setNextAttemptAt(leaseUntil);
    }
    return outboxMessageRepository.saveAll(messages);
  }

  @Override
  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public void markPublished(UUID messageId, int claimedAttempt, Instant publishedAt) {
    var message = outboxMessageRepository.findForUpdateByMessageId(messageId).orElseThrow();
    // Worker cũ không được ghi đè kết quả của lease mới.
    if (message.getPublishedAt() != null || message.getAttempts() != claimedAttempt) return;
    message.setPublishedAt(publishedAt);
    message.setLastErrorCode(null);
    outboxMessageRepository.save(message);
  }

  @Override
  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public void rescheduleOutbox(
      UUID messageId, int claimedAttempt, Instant retryAt, String errorCode) {
    var message = outboxMessageRepository.findForUpdateByMessageId(messageId).orElseThrow();
    if (message.getPublishedAt() != null || message.getAttempts() != claimedAttempt) return;
    message.setNextAttemptAt(retryAt);
    message.setLastErrorCode(errorCode);
    outboxMessageRepository.save(message);
  }
}
