package com.flashticket.payment_service.repository;

import com.flashticket.payment_service.entity.EventRefundItem;
import com.flashticket.payment_service.entity.id.EventRefundItemId;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EventRefundItemRepository
    extends JpaRepository<EventRefundItem, EventRefundItemId> {}
