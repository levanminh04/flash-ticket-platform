package com.flashticket.payment_service.repository;

import com.flashticket.payment_service.entity.Charge;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ChargeRepository extends JpaRepository<Charge, UUID> {
  Optional<Charge> findFirstByAttemptId(UUID attemptId);

  boolean existsByProviderAndMerchantAccountAndProviderChargeId(
      String provider, String merchantAccount, String providerChargeId);

  @Query(
      """
      select count(c) > 0 from Charge c, OrderPaymentSnapshot s
      where s.orderId = c.orderId and s.eventId = :eventId
      """)
  boolean existsByEventId(@Param("eventId") UUID eventId);
}
