package com.flashticket.booking_service.messaging.repository;

import com.flashticket.booking_service.entity.OutboxMessage;
import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;

public interface OutboxMessageRepository extends JpaRepository<OutboxMessage, UUID> {
  List<OutboxMessage>
      findByMessageTypeAndPublishedAtIsNullAndNextAttemptAtLessThanEqualOrderByOccurredAtAscMessageIdAsc(
          String messageType, Instant now, Pageable pageable);

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  Optional<OutboxMessage> findLockedByMessageId(UUID messageId);
}
