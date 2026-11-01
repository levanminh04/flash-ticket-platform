package com.flashticket.booking_service.promotion.repository;

import com.flashticket.booking_service.entity.PromotionInventory;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;

public interface PromotionInventoryRepository extends JpaRepository<PromotionInventory, UUID> {
  @Lock(LockModeType.PESSIMISTIC_WRITE)
  Optional<PromotionInventory> findLockedByPromotionId(UUID promotionId);
}
