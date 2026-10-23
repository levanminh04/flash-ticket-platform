package com.flashticket.payment_service.repository;

import com.flashticket.payment_service.entity.PaymentConfirmation;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PaymentConfirmationRepository extends JpaRepository<PaymentConfirmation, UUID> {
  @org.springframework.data.jpa.repository.Query(
      "select c.chargeId from PaymentConfirmation c where c.orderId = :orderId")
  Optional<UUID> findChargeIdByOrderId(
      @org.springframework.data.repository.query.Param("orderId") UUID orderId);
}
