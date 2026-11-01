package com.flashticket.booking_service.promotion.repository;

import com.flashticket.booking_service.entity.PromotionUsage;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PromotionUsageRepository extends JpaRepository<PromotionUsage, UUID> {}
