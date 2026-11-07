package com.flashticket.booking_service.availability.repository;

import com.flashticket.booking_service.entity.PurchaseLimit;
import com.flashticket.booking_service.entity.PurchaseLimitId;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;

public interface PurchaseLimitRepository extends JpaRepository<PurchaseLimit, PurchaseLimitId> {
  @Lock(LockModeType.PESSIMISTIC_WRITE)
  Optional<PurchaseLimit> findLockedById(PurchaseLimitId id);
}
