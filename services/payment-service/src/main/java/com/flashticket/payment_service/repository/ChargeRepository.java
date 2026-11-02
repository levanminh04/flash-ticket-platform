package com.flashticket.payment_service.repository;

import com.flashticket.payment_service.entity.Charge;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ChargeRepository extends JpaRepository<Charge, UUID> {
  Optional<Charge> findFirstByAttemptId(UUID attemptId);

  boolean existsByProviderAndMerchantAccountAndProviderChargeId(
      String provider, String merchantAccount, String providerChargeId);
}
