package com.flashticket.booking_service.messaging;

import com.flashticket.booking_service.exception.ErrorCode;
import com.flashticket.booking_service.messaging.repository.OutboxMessageRepository;
import com.flashticket.booking_service.messaging.service.MessagingService;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@ConditionalOnProperty(
    prefix = "booking.outbox",
    name = "enabled",
    havingValue = "true",
    matchIfMissing = true)
public class PaymentDecisionOutboxWorker {
  private final OutboxMessageRepository outboxMessageRepository;
  private final MessagingService messagingService;

  @Value("${booking.outbox.batch-size:20}")
  private int batchSize;

  @Scheduled(fixedDelayString = "${booking.outbox.poll-interval:PT2S}")
  public void publishDuePaymentDecisions() {
    // Chỉ truyền ID; service đọc lại row có khóa trong transaction riêng.
    var dueMessages =
        outboxMessageRepository
            .findByMessageTypeAndPublishedAtIsNullAndNextAttemptAtLessThanEqualOrderByOccurredAtAscMessageIdAsc(
                "booking.payment-decision.v1", Instant.now(), PageRequest.of(0, batchSize));
    for (var candidate : dueMessages) {
      if (Thread.currentThread().isInterrupted()) break;
      try {
        messagingService.publishPaymentDecision(candidate.getMessageId());
      } catch (RuntimeException exception) {
        LoggerFactory.getLogger(PaymentDecisionOutboxWorker.class)
            .warn(
                "Outbox messageId={} errorCode={}",
                candidate.getMessageId(),
                ErrorCode.from(exception));
      }
    }
  }
}
