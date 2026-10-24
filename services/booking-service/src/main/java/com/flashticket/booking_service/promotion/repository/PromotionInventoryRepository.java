package com.flashticket.booking_service.promotion.repository;

import com.flashticket.booking_service.entity.PromotionInventory;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PromotionInventoryRepository extends JpaRepository<PromotionInventory, UUID> {}
