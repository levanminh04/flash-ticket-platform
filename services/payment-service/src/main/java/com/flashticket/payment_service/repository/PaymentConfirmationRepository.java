package com.flashticket.payment_service.repository;

import com.flashticket.payment_service.entity.PaymentConfirmation;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PaymentConfirmationRepository extends JpaRepository<PaymentConfirmation, UUID> {
  @Query("select c.chargeId from PaymentConfirmation c where c.orderId = :orderId")
  Optional<UUID> findChargeIdByOrderId(@Param("orderId") UUID orderId);

  @Query(
      """
      select c.orderId from PaymentConfirmation c, OrderPaymentSnapshot s
      where s.orderId = c.orderId and s.eventId = :eventId
        and (:cursor is null or c.orderId > :cursor)
      order by c.orderId
      """)
  List<UUID> findOrderIdsByEventIdAfter(
      @Param("eventId") UUID eventId, @Param("cursor") UUID cursor, Pageable pageable);
}
