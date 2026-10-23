package com.flashticket.payment_service.repository;
import com.flashticket.payment_service.entity.PaymentAttempt;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;
import java.util.UUID;
public interface PaymentAttemptRepository extends JpaRepository<PaymentAttempt,UUID> {
  Optional<PaymentAttempt> findFirstByOrderIdOrderByStartedAtDescIdDesc(UUID orderId);
  Optional<PaymentAttempt> findByIdAndOrderId(UUID id,UUID orderId);
}
