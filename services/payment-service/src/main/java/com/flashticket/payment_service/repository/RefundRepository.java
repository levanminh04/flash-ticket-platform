package com.flashticket.payment_service.repository;

import com.flashticket.payment_service.entity.Refund;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RefundRepository extends JpaRepository<Refund, UUID> {
  java.util.Optional<Refund> findByChargeId(UUID chargeId);

  List<Refund> findAllByOrderIdOrderByCreatedAtAscIdAsc(UUID orderId);
}
