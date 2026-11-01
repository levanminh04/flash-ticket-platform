package com.flashticket.booking_service.availability.repository;

import com.flashticket.booking_service.entity.PurchaseLimit;
import com.flashticket.booking_service.entity.PurchaseLimitId;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PurchaseLimitRepository extends JpaRepository<PurchaseLimit, PurchaseLimitId> {}
