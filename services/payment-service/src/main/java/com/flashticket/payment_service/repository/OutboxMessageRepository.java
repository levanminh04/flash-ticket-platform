package com.flashticket.payment_service.repository;

import com.flashticket.payment_service.entity.OutboxMessage;
import jakarta.persistence.LockModeType;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface OutboxMessageRepository extends JpaRepository<OutboxMessage, UUID> {
  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query(
      "select m from OutboxMessage m where m.publishedAt is null and m.nextAttemptAt <= :dueAt order by m.nextAttemptAt, m.messageId")
  java.util.List<OutboxMessage> findDueForUpdate(
      @Param("dueAt") java.time.Instant dueAt, org.springframework.data.domain.Pageable pageable);

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select m from OutboxMessage m where m.messageId = :messageId")
  java.util.Optional<OutboxMessage> findForUpdateByMessageId(@Param("messageId") UUID messageId);

  java.util.Optional<OutboxMessage>
      findFirstByAggregateIdAndMessageTypeOrderByOccurredAtDescMessageIdDesc(
          UUID aggregateId, String messageType);

  java.util.Optional<OutboxMessage>
      findFirstByAggregateIdAndMessageTypeInOrderByOccurredAtDescMessageIdDesc(
          UUID aggregateId, java.util.List<String> messageTypes);
}
