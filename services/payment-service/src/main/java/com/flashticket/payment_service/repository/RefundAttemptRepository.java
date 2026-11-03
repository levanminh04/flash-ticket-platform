package com.flashticket.payment_service.repository;

import com.flashticket.payment_service.entity.RefundAttempt;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RefundAttemptRepository extends JpaRepository<RefundAttempt, UUID> {
  Optional<RefundAttempt> findFirstByRefundIdAndFinishedAtIsNull(UUID refundId);

  Optional<RefundAttempt> findFirstByRefundIdOrderByStartedAtDescIdDesc(UUID refundId);

  boolean existsByRefundIdAndFinishedAtIsNull(UUID refundId);
}
