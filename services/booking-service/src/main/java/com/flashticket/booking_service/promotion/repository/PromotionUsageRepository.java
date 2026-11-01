package com.flashticket.booking_service.promotion.repository;

import com.flashticket.booking_service.entity.PromotionUsage;
import com.flashticket.booking_service.enums.PromotionUsageState;
import java.util.Collection;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PromotionUsageRepository extends JpaRepository<PromotionUsage, UUID> {
  Optional<PromotionUsage> findByReservationIdAndStateIn(
      UUID reservationId, Collection<PromotionUsageState> states);

  boolean existsByPromotionPromotionIdAndBuyerSubjectAndStateIn(
      UUID promotionId, String buyerSubject, Collection<PromotionUsageState> states);
}
