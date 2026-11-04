package com.flashticket.payment_service.repository;

import com.flashticket.payment_service.entity.PaymentAttempt;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PaymentAttemptRepository extends JpaRepository<PaymentAttempt, UUID> {
  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select a from PaymentAttempt a where a.id = :id")
  Optional<PaymentAttempt> findForUpdateById(@Param("id") UUID id);

  Optional<PaymentAttempt> findByOrderIdAndRequestKey(UUID orderId, UUID requestKey);

  boolean existsByOrderIdAndFinishedAtIsNull(UUID orderId);

  Optional<PaymentAttempt> findFirstByOrderIdOrderByStartedAtDescIdDesc(UUID orderId);

  Optional<PaymentAttempt> findByIdAndOrderId(UUID id, UUID orderId);

  @Query(
      """
      select count(a) from PaymentAttempt a, OrderPaymentSnapshot s
      where s.orderId = a.orderId and s.eventId = :eventId and a.finishedAt is null
      """)
  long countUnfinishedByEventId(@Param("eventId") UUID eventId);
}
