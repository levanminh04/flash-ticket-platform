package com.flashticket.payment_service.refund.repository;

import com.flashticket.payment_service.entity.*;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EventRefundItemRepository
    extends JpaRepository<EventRefundItem, EventRefundItemId> {}
