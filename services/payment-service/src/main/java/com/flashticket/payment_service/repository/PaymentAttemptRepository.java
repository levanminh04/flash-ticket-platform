package com.flashticket.payment_service.repository;

import com.flashticket.payment_service.entity.PaymentAttempt;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PaymentAttemptRepository extends JpaRepository<PaymentAttempt, UUID> {
  @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
  @org.springframework.data.jpa.repository.Query("select a from PaymentAttempt a where a.id = :id")
  Optional<PaymentAttempt> findForUpdateById(
      @org.springframework.data.repository.query.Param("id") UUID id);

  Optional<PaymentAttempt> findByOrderIdAndRequestKey(UUID orderId, UUID requestKey);

  boolean existsByOrderIdAndFinishedAtIsNull(UUID orderId);

  Optional<PaymentAttempt> findFirstByOrderIdOrderByStartedAtDescIdDesc(UUID orderId);

  Optional<PaymentAttempt> findByIdAndOrderId(UUID id, UUID orderId);
}
